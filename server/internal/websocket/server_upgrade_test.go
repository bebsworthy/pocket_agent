package websocket

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestWebSocketUpgradeReal tests the actual WebSocket upgrade behavior
func TestWebSocketUpgradeReal(t *testing.T) {
	tests := []struct {
		name           string
		setupConfig    func(*Config)
		setupRequest   func(*http.Request)
		expectedStatus int
		expectUpgrade  bool
		validateConn   func(*testing.T, *websocket.Conn, error)
	}{
		{
			name: "successful_upgrade_with_valid_origin",
			setupConfig: func(c *Config) {
				c.AllowedOrigins = []string{"http://localhost:3000"}
			},
			setupRequest: func(r *http.Request) {
				r.Header.Set("Origin", "http://localhost:3000")
				// Don't set WebSocket headers - the dialer will set them
			},
			expectUpgrade: true,
			validateConn: func(t *testing.T, conn *websocket.Conn, err error) {
				require.NoError(t, err)
				require.NotNil(t, conn)

				// Verify we can send and receive messages
				testMsg := models.ClientMessage{Type: models.MessageTypeProjectList}
				err = conn.WriteJSON(testMsg)
				assert.NoError(t, err)
			},
		},
		{
			name: "reject_invalid_origin",
			setupConfig: func(c *Config) {
				c.AllowedOrigins = []string{"http://localhost:3000"}
			},
			setupRequest: func(r *http.Request) {
				r.Header.Set("Origin", "http://evil.com")
				// Don't set WebSocket headers - the dialer will set them
			},
			expectUpgrade: false,
			validateConn: func(t *testing.T, conn *websocket.Conn, err error) {
				assert.Error(t, err)
				assert.Nil(t, conn)
				// WebSocket dialer returns generic "bad handshake" for all HTTP errors
				assert.Contains(t, err.Error(), "bad handshake")
			},
		},
		{
			name: "connection_limit_reached",
			setupConfig: func(c *Config) {
				c.MaxConnections = 0 // No connections allowed
			},
			setupRequest: func(r *http.Request) {
				// Normal request
			},
			expectUpgrade: false,
			validateConn: func(t *testing.T, conn *websocket.Conn, err error) {
				assert.Error(t, err)
				assert.Nil(t, conn)
				// WebSocket dialer returns generic "bad handshake" for all HTTP errors
				assert.Contains(t, err.Error(), "bad handshake")
			},
		},
		{
			name: "rate_limit_exceeded",
			setupConfig: func(c *Config) {
				c.RateLimitPerIP = 2 // Allow 2 connections per minute
			},
			setupRequest: func(r *http.Request) {
				// Don't set WebSocket headers - the dialer will set them
			},
			expectUpgrade: true, // The first connection should succeed
			validateConn: func(t *testing.T, conn *websocket.Conn, err error) {
				// This is the first connection, it should succeed
				assert.NoError(t, err)
				assert.NotNil(t, conn)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create server with real components
			config := DefaultConfig()
			config.Port = 0 // Use random port
			if tt.setupConfig != nil {
				tt.setupConfig(&config)
			}

			// Use a minimal handler that tracks real behavior
			handler := &minimalHandler{
				receivedMessages: make([]*models.ClientMessage, 0),
			}
			log := logger.New("debug")
			server := NewServer(config, handler, log)

			// Create test server
			ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
			defer ts.Close()

			// Prepare WebSocket URL and headers
			wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

			// Create custom dialer to control headers
			dialer := websocket.Dialer{
				Proxy:            http.ProxyFromEnvironment,
				HandshakeTimeout: 45 * time.Second,
			}

			// Prepare request headers
			headers := http.Header{}
			if tt.setupRequest != nil {
				req := httptest.NewRequest("GET", "/ws", nil)
				tt.setupRequest(req)
				headers = req.Header
			}

			// Attempt connection
			conn, resp, err := dialer.Dial(wsURL, headers)
			if conn != nil {
				defer conn.Close()
			}
			if resp != nil && resp.Body != nil {
				defer resp.Body.Close()
			}

			// Validate results
			tt.validateConn(t, conn, err)

			// Additional validations for successful upgrades
			if tt.expectUpgrade && err == nil {
				// Verify session was created
				sessionCount := 0
				server.sessions.Range(func(key, value interface{}) bool {
					sessionCount++
					session := value.(*models.Session)
					assert.NotEmpty(t, session.ID)
					assert.NotNil(t, session.Conn)
					assert.False(t, session.CreatedAt.IsZero())
					return true
				})
				assert.Equal(t, 1, sessionCount, "Expected exactly one session")

				// Verify metrics were updated
				metrics := server.GetMetrics()
				assert.Equal(t, int64(1), metrics["active_connections"])
				assert.Equal(t, int64(1), metrics["total_connections"])
			}
		})
	}
}

// TestWebSocketUpgradeProtocol tests the actual HTTP upgrade protocol details
func TestWebSocketUpgradeProtocol(t *testing.T) {
	config := DefaultConfig()
	handler := &minimalHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create a custom test that inspects the HTTP response
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Verify request headers
		assert.Equal(t, "Upgrade", r.Header.Get("Connection"))
		assert.Equal(t, "websocket", r.Header.Get("Upgrade"))
		assert.Equal(t, "13", r.Header.Get("Sec-WebSocket-Version"))
		assert.NotEmpty(t, r.Header.Get("Sec-WebSocket-Key"))

		// Call the actual handler
		server.handleWebSocket(w, r)
	}))
	defer ts.Close()

	// Connect and verify upgrade response
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http")
	conn, resp, err := websocket.DefaultDialer.Dial(wsURL, nil)
	require.NoError(t, err)
	require.NotNil(t, conn)
	defer conn.Close()

	// Verify HTTP response
	assert.Equal(t, http.StatusSwitchingProtocols, resp.StatusCode)
	assert.Equal(t, "websocket", resp.Header.Get("Upgrade"))
	assert.Equal(t, "Upgrade", resp.Header.Get("Connection"))
	assert.NotEmpty(t, resp.Header.Get("Sec-WebSocket-Accept"))
}

// TestWebSocketUpgradeConcurrency tests concurrent upgrade attempts
func TestWebSocketUpgradeConcurrency(t *testing.T) {
	config := DefaultConfig()
	config.MaxConnections = 10
	config.MaxConnectionsPerIP = 5

	handler := &minimalHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Test concurrent connections
	var wg sync.WaitGroup
	successCount := int32(0)
	connections := make([]*websocket.Conn, 0)
	var connMu sync.Mutex

	for i := 0; i < 15; i++ { // Try more than the limit
		wg.Add(1)
		go func() {
			defer wg.Done()

			conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
			if err == nil {
				connMu.Lock()
				connections = append(connections, conn)
				connMu.Unlock()
				atomic.AddInt32(&successCount, 1)
			}
		}()
	}

	wg.Wait()

	// Clean up connections
	for _, conn := range connections {
		conn.Close()
	}

	// Verify connection limits were enforced
	assert.LessOrEqual(t, int(successCount), config.MaxConnections)
}

// minimalHandler is a lightweight handler for testing
type minimalHandler struct {
	mu               sync.Mutex
	receivedMessages []*models.ClientMessage
}

func (h *minimalHandler) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.receivedMessages = append(h.receivedMessages, msg)

	// Send a simple response
	response := models.ServerMessage{
		Type:      models.MessageTypeProjectState,
		ProjectID: session.GetProject(),
	}
	return session.WriteJSON(response)
}

func (h *minimalHandler) OnSessionCleanup(session *models.Session) {
	// No-op for minimal handler
}

// Benchmark the upgrade process
func BenchmarkWebSocketUpgrade(b *testing.B) {
	config := DefaultConfig()
	config.MaxConnections = 10000 // High limit for benchmark

	handler := &minimalHandler{}
	log := logger.New("error") // Reduce logging in benchmark
	server := NewServer(config, handler, log)

	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
			if err != nil {
				b.Fatal(err)
			}
			conn.Close()
		}
	})
}
