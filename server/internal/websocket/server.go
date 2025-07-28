package websocket

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
)

// Config holds WebSocket server configuration
type Config struct {
	// Server settings
	Port         int
	TLSCert      string
	TLSKey       string
	ReadTimeout  time.Duration
	WriteTimeout time.Duration

	// Connection settings
	MaxConnections      int
	MaxConnectionsPerIP int
	ConnectionTimeout   time.Duration
	PingInterval        time.Duration
	PongTimeout         time.Duration

	// Security settings
	AllowedOrigins []string
	RateLimitPerIP int // connections per minute

	// Message settings
	MaxMessageSize int64
	BufferSize     int
}

// DefaultConfig returns default WebSocket configuration
func DefaultConfig() Config {
	return Config{
		Port:                8443,
		ReadTimeout:         10 * time.Second,
		WriteTimeout:        10 * time.Second,
		MaxConnections:      1000,
		MaxConnectionsPerIP: 10,
		ConnectionTimeout:   5 * time.Minute,
		PingInterval:        30 * time.Second,
		PongTimeout:         60 * time.Second,
		AllowedOrigins:      []string{"*"}, // Should be restricted in production
		RateLimitPerIP:      60,            // 60 connections per minute per IP
		MaxMessageSize:      1024 * 1024,   // 1MB
		BufferSize:          1024,
	}
}

// Server represents the WebSocket server
type Server struct {
	config     Config
	upgrader   websocket.Upgrader
	sessions   sync.Map // map[string]*models.Session
	handler    MessageHandler
	log        *logger.Logger
	httpServer *http.Server

	// Metrics
	activeConnections int64
	totalConnections  int64

	// Rate limiting
	connRateLimiter *RateLimiter
	ipConnections   sync.Map // map[string]int

	// Lifecycle
	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

// MessageHandler handles WebSocket messages
type MessageHandler interface {
	HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error
}

// NewServer creates a new WebSocket server
func NewServer(config Config, handler MessageHandler, log *logger.Logger) *Server {
	ctx, cancel := context.WithCancel(context.Background())

	s := &Server{
		config:          config,
		handler:         handler,
		log:             log,
		ctx:             ctx,
		cancel:          cancel,
		connRateLimiter: NewRateLimiter(config.RateLimitPerIP, time.Minute),
	}

	// Configure upgrader
	s.upgrader = websocket.Upgrader{
		ReadBufferSize:  config.BufferSize,
		WriteBufferSize: config.BufferSize,
		CheckOrigin:     s.checkOrigin,
		Error: func(w http.ResponseWriter, r *http.Request, status int, reason error) {
			s.log.Error("WebSocket upgrade error",
				"status", status,
				"reason", reason,
				"remote", r.RemoteAddr,
			)
		},
	}

	return s
}

// Start starts the WebSocket server
func (s *Server) Start() error {
	mux := http.NewServeMux()
	mux.HandleFunc("/ws", s.handleWebSocket)
	mux.HandleFunc("/health", s.handleHealth)

	s.httpServer = &http.Server{
		Addr:         fmt.Sprintf(":%d", s.config.Port),
		Handler:      mux,
		ReadTimeout:  s.config.ReadTimeout,
		WriteTimeout: s.config.WriteTimeout,
	}

	// Configure TLS if certificates are provided
	if s.config.TLSCert != "" && s.config.TLSKey != "" {
		tlsConfig := &tls.Config{
			MinVersion: tls.VersionTLS12,
			CipherSuites: []uint16{
				tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
				tls.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
				tls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
				tls.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
			},
		}
		s.httpServer.TLSConfig = tlsConfig

		s.log.Info("Starting WebSocket server with TLS",
			"port", s.config.Port,
			"cert", s.config.TLSCert,
		)

		return s.httpServer.ListenAndServeTLS(s.config.TLSCert, s.config.TLSKey)
	}

	s.log.Info("Starting WebSocket server",
		"port", s.config.Port,
	)

	return s.httpServer.ListenAndServe()
}

// Stop gracefully stops the server
func (s *Server) Stop(timeout time.Duration) error {
	s.log.Info("Stopping WebSocket server")

	// Cancel context to signal shutdown
	s.cancel()

	// Close all active sessions
	s.sessions.Range(func(key, value interface{}) bool {
		if session, ok := value.(*models.Session); ok {
			s.closeSession(session, websocket.CloseGoingAway, "Server shutting down")
		}
		return true
	})

	// Shutdown HTTP server
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	if err := s.httpServer.Shutdown(ctx); err != nil {
		return errors.Wrap(err, errors.CodeInternalError, "failed to shutdown server")
	}

	// Wait for all goroutines to finish
	done := make(chan struct{})
	go func() {
		s.wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		s.log.Info("WebSocket server stopped gracefully")
		return nil
	case <-ctx.Done():
		return errors.New(errors.CodeExecutionTimeout, "server shutdown timeout")
	}
}

// HandleUpgrade upgrades HTTP connection to WebSocket
func (s *Server) HandleUpgrade(w http.ResponseWriter, r *http.Request) (*models.Session, error) {
	// Extract client IP
	clientIP := s.getClientIP(r)

	// Check rate limit
	if !s.connRateLimiter.Allow(clientIP) {
		return nil, errors.New(errors.CodeResourceLimit, "connection rate limit exceeded").
			WithDetail("ip", clientIP)
	}

	// Check max connections
	if atomic.LoadInt64(&s.activeConnections) >= int64(s.config.MaxConnections) {
		return nil, errors.New(errors.CodeConnectionLimit, "maximum connections reached").
			WithDetail("limit", s.config.MaxConnections)
	}

	// Check max connections per IP
	if !s.checkIPConnectionLimit(clientIP) {
		return nil, errors.New(errors.CodeConnectionLimit, "maximum connections per IP reached").
			WithDetail("ip", clientIP).
			WithDetail("limit", s.config.MaxConnectionsPerIP)
	}

	// Upgrade connection
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return nil, errors.Wrap(err, errors.CodeWebSocketError, "failed to upgrade connection")
	}

	// Configure connection
	conn.SetReadLimit(s.config.MaxMessageSize)
	conn.SetReadDeadline(time.Now().Add(s.config.PongTimeout))
	conn.SetPongHandler(func(string) error {
		conn.SetReadDeadline(time.Now().Add(s.config.PongTimeout))
		return nil
	})

	// Create session
	sessionID := generateSessionID()
	session := models.NewSession(sessionID, conn)

	// Store session
	s.sessions.Store(sessionID, session)

	// Update metrics
	atomic.AddInt64(&s.activeConnections, 1)
	atomic.AddInt64(&s.totalConnections, 1)
	s.incrementIPConnections(clientIP)

	s.log.Info("WebSocket connection established",
		"session_id", sessionID,
		"remote", clientIP,
		"active_connections", atomic.LoadInt64(&s.activeConnections),
	)

	return session, nil
}

// handleWebSocket handles WebSocket upgrade requests
func (s *Server) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	session, err := s.HandleUpgrade(w, r)
	if err != nil {
		appErr := err.(*errors.AppError)
		http.Error(w, appErr.Message, http.StatusTooManyRequests)
		return
	}

	// Handle connection in goroutine
	s.wg.Add(1)
	go s.handleConnection(session)
}

// handleConnection manages a WebSocket connection lifecycle
func (s *Server) handleConnection(session *models.Session) {
	defer s.wg.Done()
	defer s.cleanupSession(session)

	// Start ping ticker
	pingTicker := time.NewTicker(s.config.PingInterval)
	defer pingTicker.Stop()

	// Connection timeout timer
	timeoutTimer := time.NewTimer(s.config.ConnectionTimeout)
	defer timeoutTimer.Stop()

	// Message channel
	msgChan := make(chan *models.ClientMessage, 10)
	errChan := make(chan error, 1)

	// Start message reader
	s.wg.Add(1)
	go s.readMessages(session, msgChan, errChan)

	for {
		select {
		case <-s.ctx.Done():
			s.closeSession(session, websocket.CloseGoingAway, "Server shutting down")
			return

		case msg := <-msgChan:
			// Reset timeout on activity
			timeoutTimer.Reset(s.config.ConnectionTimeout)
			session.UpdatePing()

			// Handle message
			if err := s.handler.HandleMessage(s.ctx, session, msg); err != nil {
				s.handleError(session, err)
			}

		case err := <-errChan:
			if err != nil {
				s.log.Error("WebSocket read error",
					"session_id", session.ID,
					"error", err,
				)
			}
			return

		case <-pingTicker.C:
			if err := session.SendPing(); err != nil {
				s.log.Error("Failed to send ping",
					"session_id", session.ID,
					"error", err,
				)
				return
			}

		case <-timeoutTimer.C:
			s.log.Info("Session timeout",
				"session_id", session.ID,
				"timeout", s.config.ConnectionTimeout,
			)
			s.closeSession(session, websocket.CloseGoingAway, "Connection timeout")
			return
		}
	}
}

// readMessages reads messages from WebSocket connection
func (s *Server) readMessages(session *models.Session, msgChan chan<- *models.ClientMessage, errChan chan<- error) {
	defer s.wg.Done()
	defer close(msgChan)
	defer close(errChan)

	for {
		var msg models.ClientMessage
		err := session.Conn.ReadJSON(&msg)
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				errChan <- err
			}
			return
		}

		// Validate message
		if err := msg.Validate(); err != nil {
			s.handleError(session, errors.Wrap(err, errors.CodeValidationFailed, "invalid message"))
			continue
		}

		select {
		case msgChan <- &msg:
		case <-s.ctx.Done():
			return
		}
	}
}

// handleError sends error message to client
func (s *Server) handleError(session *models.Session, err error) {
	appErr, ok := err.(*errors.AppError)
	if !ok {
		appErr = errors.NewInternalError(err)
	}

	errMsg := models.NewErrorMessage(
		session.GetProject(),
		string(appErr.Code),
		appErr.Message,
		appErr.Details,
	)

	if writeErr := session.WriteJSON(errMsg); writeErr != nil {
		s.log.Error("Failed to send error message",
			"session_id", session.ID,
			"error", writeErr,
		)
	}
}

// closeSession closes a WebSocket session
func (s *Server) closeSession(session *models.Session, code int, reason string) {
	deadline := time.Now().Add(time.Second)
	session.Conn.SetWriteDeadline(deadline)
	session.Conn.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(code, reason))
	session.Close()
}

// cleanupSession cleans up after a disconnected session
func (s *Server) cleanupSession(session *models.Session) {
	// Use LoadAndDelete for atomic operation to prevent race conditions
	if _, loaded := s.sessions.LoadAndDelete(session.ID); !loaded {
		// Session was already cleaned up, avoid double cleanup
		return
	}

	atomic.AddInt64(&s.activeConnections, -1)

	// Clean up IP connection count
	if conn := session.Conn; conn != nil {
		if addr := conn.RemoteAddr(); addr != nil {
			ip := extractIP(addr.String())
			s.decrementIPConnections(ip)
		}
	}

	s.log.Info("WebSocket connection closed",
		"session_id", session.ID,
		"active_connections", atomic.LoadInt64(&s.activeConnections),
	)
}

// checkOrigin validates WebSocket origin
func (s *Server) checkOrigin(r *http.Request) bool {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return true // Allow connections without origin (e.g., native apps)
	}

	// Check against allowed origins
	for _, allowed := range s.config.AllowedOrigins {
		if allowed == "*" {
			return true
		}
		if strings.HasPrefix(origin, allowed) {
			return true
		}
	}

	s.log.Warn("Rejected connection from unauthorized origin",
		"origin", origin,
		"remote", r.RemoteAddr,
	)

	return false
}

// getClientIP extracts client IP from request
func (s *Server) getClientIP(r *http.Request) string {
	// Check X-Forwarded-For header
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		ips := strings.Split(xff, ",")
		if len(ips) > 0 {
			return strings.TrimSpace(ips[0])
		}
	}

	// Check X-Real-IP header
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return xri
	}

	// Fall back to RemoteAddr
	return extractIP(r.RemoteAddr)
}

// extractIP extracts IP address from address string
func extractIP(addr string) string {
	host, _, err := net.SplitHostPort(addr)
	if err != nil {
		return addr
	}
	return host
}

// checkIPConnectionLimit checks if IP has reached connection limit
func (s *Server) checkIPConnectionLimit(ip string) bool {
	if val, ok := s.ipConnections.Load(ip); ok {
		count := val.(int)
		return count < s.config.MaxConnectionsPerIP
	}
	return true
}

// incrementIPConnections increments connection count for IP
func (s *Server) incrementIPConnections(ip string) {
	s.ipConnections.Store(ip, s.getIPConnectionCount(ip)+1)
}

// decrementIPConnections decrements connection count for IP
func (s *Server) decrementIPConnections(ip string) {
	count := s.getIPConnectionCount(ip)
	if count > 1 {
		s.ipConnections.Store(ip, count-1)
	} else {
		s.ipConnections.Delete(ip)
	}
}

// getIPConnectionCount gets current connection count for IP
func (s *Server) getIPConnectionCount(ip string) int {
	if val, ok := s.ipConnections.Load(ip); ok {
		return val.(int)
	}
	return 0
}

// handleHealth handles health check requests
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, `{"status":"healthy","connections":%d}`, atomic.LoadInt64(&s.activeConnections))
}

// GetMetrics returns server metrics
func (s *Server) GetMetrics() map[string]interface{} {
	return map[string]interface{}{
		"active_connections": atomic.LoadInt64(&s.activeConnections),
		"total_connections":  atomic.LoadInt64(&s.totalConnections),
	}
}

// generateSessionID generates a unique session ID
func generateSessionID() string {
	return fmt.Sprintf("ws_%d_%d", time.Now().UnixNano(), time.Now().Nanosecond())
}
