package websocket

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
)

// mockHandler implements MessageHandler for testing
type mockHandler struct {
	mu       sync.Mutex
	messages []*models.ClientMessage
	errors   []error
}

func (m *mockHandler) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.messages = append(m.messages, msg)
	return nil
}

func (m *mockHandler) OnSessionCleanup(session *models.Session) {
	// No-op for tests
}

func (m *mockHandler) getMessages() []*models.ClientMessage {
	m.mu.Lock()
	defer m.mu.Unlock()
	msgs := make([]*models.ClientMessage, len(m.messages))
	copy(msgs, m.messages)
	return msgs
}

func TestServerLifecycle(t *testing.T) {
	config := DefaultConfig()
	config.Port = 0 // Use random port

	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Start server in background
	go func() {
		if err := server.Start(); err != nil && !strings.Contains(err.Error(), "Server closed") {
			t.Errorf("Server start error: %v", err)
		}
	}()

	// Give server time to start
	time.Sleep(100 * time.Millisecond)

	// Stop server
	if err := server.Stop(5 * time.Second); err != nil {
		t.Fatalf("Failed to stop server: %v", err)
	}
}

func TestWebSocketUpgrade(t *testing.T) {
	// Create real WebSocket configuration
	config := DefaultConfig()
	config.PingInterval = 30 * time.Second
	config.PongTimeout = 60 * time.Second
	config.ConnectionTimeout = 5 * time.Minute
	config.MaxConnections = 100
	config.MaxConnectionsPerIP = 10
	config.RateLimitPerIP = 60

	// Create a real message router to test the upgrade process
	log := logger.New("debug")
	router := NewMessageRouter(log)
	
	// Register a simple handler for testing
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		// Simple handler that just returns success
		return session.WriteJSON(&models.ServerMessage{
			Type: models.MessageTypeProjectState,
			Data: []interface{}{},
		})
	})

	// Create server with real components
	server := NewServer(config, router, log)

	// Create test HTTP server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	// Convert http:// to ws://
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	t.Run("Successful WebSocket Upgrade", func(t *testing.T) {
		// Test real WebSocket upgrade
		dialer := websocket.Dialer{
			HandshakeTimeout: 5 * time.Second,
		}
		
		headers := http.Header{}
		headers.Set("Origin", "http://localhost:3000")
		
		ws, resp, err := dialer.Dial(wsURL, headers)
		if err != nil {
			t.Fatalf("Failed to connect: %v", err)
		}
		defer ws.Close()

		// Verify response headers
		if resp.StatusCode != http.StatusSwitchingProtocols {
			t.Errorf("Expected status %d, got %d", http.StatusSwitchingProtocols, resp.StatusCode)
		}

		// Verify connection is tracked
		var sessionFound bool
		server.sessions.Range(func(key, value interface{}) bool {
			if session, ok := value.(*models.Session); ok {
				if session.Conn == ws {
					sessionFound = true
					return false
				}
			}
			return true
		})

		if !sessionFound {
			t.Error("Session not properly tracked in server")
		}

		// Verify metrics updated
		if active := server.activeConnections; active != 1 {
			t.Errorf("Expected 1 active connection, got %d", active)
		}

		// Test message handling through upgraded connection
		msg := models.ClientMessage{
			Type: models.MessageTypeProjectList,
		}
		
		if err := ws.WriteJSON(msg); err != nil {
			t.Fatalf("Failed to send message: %v", err)
		}

		// Read response
		var response models.ServerMessage
		ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&response); err != nil {
			t.Fatalf("Failed to read response: %v", err)
		}

		if response.Type != models.MessageTypeProjectState {
			t.Errorf("Expected response type %s, got %s", models.MessageTypeProjectState, response.Type)
		}
	})

	t.Run("WebSocket Upgrade with Invalid Protocol", func(t *testing.T) {
		// Try to connect with invalid WebSocket version
		req, err := http.NewRequest("GET", ts.URL+"/ws", nil)
		if err != nil {
			t.Fatal(err)
		}
		
		req.Header.Set("Upgrade", "websocket")
		req.Header.Set("Connection", "Upgrade")
		req.Header.Set("Sec-WebSocket-Version", "99") // Invalid version
		req.Header.Set("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")

		client := &http.Client{}
		resp, err := client.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()

		// Should fail with bad request
		if resp.StatusCode == http.StatusSwitchingProtocols {
			t.Error("Should not upgrade with invalid protocol version")
		}
	})

	t.Run("Connection Cleanup on Close", func(t *testing.T) {
		// Connect
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		if err != nil {
			t.Fatalf("Failed to connect: %v", err)
		}

		// Get session count before close
		var beforeCount int
		server.sessions.Range(func(key, value interface{}) bool {
			beforeCount++
			return true
		})

		// Close connection properly
		err = ws.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, "test complete"))
		if err != nil {
			t.Logf("Error writing close message: %v", err)
		}
		ws.Close()

		// Give server time to clean up
		time.Sleep(100 * time.Millisecond)

		// Verify session cleaned up
		var afterCount int
		server.sessions.Range(func(key, value interface{}) bool {
			afterCount++
			return true
		})

		if afterCount >= beforeCount {
			t.Error("Session not cleaned up after connection close")
		}

		// Verify metrics updated
		if active := server.activeConnections; active != int64(afterCount) {
			t.Errorf("Active connections mismatch: expected %d, got %d", afterCount, active)
		}
	})
}

func TestOriginValidation(t *testing.T) {
	tests := []struct {
		name           string
		allowedOrigins []string
		requestOrigin  string
		expectAllow    bool
	}{
		{
			name:           "Allow all origins",
			allowedOrigins: []string{"*"},
			requestOrigin:  "http://example.com",
			expectAllow:    true,
		},
		{
			name:           "Allow specific origin",
			allowedOrigins: []string{"http://localhost:3000"},
			requestOrigin:  "http://localhost:3000",
			expectAllow:    true,
		},
		{
			name:           "Block unauthorized origin",
			allowedOrigins: []string{"http://localhost:3000"},
			requestOrigin:  "http://evil.com",
			expectAllow:    false,
		},
		{
			name:           "Allow prefix match",
			allowedOrigins: []string{"http://localhost"},
			requestOrigin:  "http://localhost:3000",
			expectAllow:    true,
		},
		{
			name:           "Allow no origin header",
			allowedOrigins: []string{"http://localhost"},
			requestOrigin:  "",
			expectAllow:    true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			config := DefaultConfig()
			config.AllowedOrigins = tt.allowedOrigins

			handler := &mockHandler{}
			log := logger.New("debug")
			server := NewServer(config, handler, log)

			req := httptest.NewRequest("GET", "/ws", nil)
			if tt.requestOrigin != "" {
				req.Header.Set("Origin", tt.requestOrigin)
			}

			result := server.checkOrigin(req)
			if result != tt.expectAllow {
				t.Errorf("Expected %v, got %v", tt.expectAllow, result)
			}
		})
	}
}

func TestRateLimiting(t *testing.T) {
	config := DefaultConfig()
	config.RateLimitPerIP = 2 // 2 connections per minute

	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// First connection should succeed
	ws1, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("First connection failed: %v", err)
	}
	defer ws1.Close()

	// Second connection should succeed
	ws2, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Second connection failed: %v", err)
	}
	defer ws2.Close()

	// Third connection should fail (rate limited)
	ws3, resp, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err == nil {
		ws3.Close()
		t.Fatal("Third connection should have failed")
	}

	if resp.StatusCode != http.StatusTooManyRequests {
		t.Errorf("Expected status %d, got %d", http.StatusTooManyRequests, resp.StatusCode)
	}
}

func TestConnectionLimit(t *testing.T) {
	config := DefaultConfig()
	config.MaxConnections = 2

	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Create max connections
	connections := make([]*websocket.Conn, config.MaxConnections)
	for i := 0; i < config.MaxConnections; i++ {
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		if err != nil {
			t.Fatalf("Connection %d failed: %v", i+1, err)
		}
		connections[i] = ws
		defer ws.Close()
	}

	// Next connection should fail
	ws, resp, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err == nil {
		ws.Close()
		t.Fatal("Connection should have failed due to limit")
	}

	if resp.StatusCode != http.StatusTooManyRequests {
		t.Errorf("Expected status %d, got %d", http.StatusTooManyRequests, resp.StatusCode)
	}
}

func TestPingPong(t *testing.T) {
	config := DefaultConfig()
	config.PingInterval = 100 * time.Millisecond
	config.PongTimeout = 200 * time.Millisecond

	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Connect with pong handler
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer ws.Close()

	pongReceived := make(chan struct{})
	ws.SetPingHandler(func(appData string) error {
		close(pongReceived)
		return ws.WriteControl(websocket.PongMessage, nil, time.Now().Add(time.Second))
	})

	// Start reader to process pings
	go func() {
		for {
			_, _, err := ws.ReadMessage()
			if err != nil {
				return
			}
		}
	}()

	// Wait for ping
	select {
	case <-pongReceived:
		// Success
	case <-time.After(500 * time.Millisecond):
		t.Fatal("Ping not received in time")
	}
}

func TestMessageHandling(t *testing.T) {
	// Create real WebSocket configuration
	config := DefaultConfig()
	log := logger.New("debug")
	
	// Create message dispatcher with middleware
	router := NewMessageRouter(log)
	dispatcher := NewMessageDispatcher(router, log)
	
	// Add real middleware
	dispatcher.Use(LoggingMiddleware(log))
	dispatcher.Use(RecoveryMiddleware(log))
	dispatcher.Use(ValidationMiddleware())
	
	// Track handled messages
	handledMessages := make(chan *models.ClientMessage, 10)
	
	// Register handlers for different message types
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		handledMessages <- &models.ClientMessage{Type: models.MessageTypeProjectList}
		return SendSuccess(session, models.MessageTypeProjectState, []interface{}{})
	})
	
	router.Register(models.MessageTypeProjectCreate, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		var createData struct {
			Path string `json:"path"`
		}
		if err := json.Unmarshal(data, &createData); err != nil {
			return err
		}
		handledMessages <- &models.ClientMessage{Type: models.MessageTypeProjectCreate}
		return SendProjectState(session, &models.Project{
			ID:   "test-project-id",
			Path: createData.Path,
		})
	})

	// Create server with dispatcher
	server := NewServer(config, dispatcher, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	t.Run("Message Routing and Middleware", func(t *testing.T) {
		// Connect
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		if err != nil {
			t.Fatalf("Failed to connect: %v", err)
		}
		defer ws.Close()

		// Test ProjectList message
		msg1 := models.ClientMessage{
			Type: models.MessageTypeProjectList,
		}

		if err := ws.WriteJSON(msg1); err != nil {
			t.Fatalf("Failed to send message: %v", err)
		}

		// Read response
		var resp1 models.ServerMessage
		ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&resp1); err != nil {
			t.Fatalf("Failed to read response: %v", err)
		}

		if resp1.Type != models.MessageTypeProjectState {
			t.Errorf("Expected response type %s, got %s", models.MessageTypeProjectState, resp1.Type)
		}

		// Verify handler was called
		select {
		case handled := <-handledMessages:
			if handled.Type != models.MessageTypeProjectList {
				t.Errorf("Expected handled message type %s, got %s", models.MessageTypeProjectList, handled.Type)
			}
		case <-time.After(time.Second):
			t.Error("Handler not called for ProjectList message")
		}

		// Test ProjectCreate message
		msg2 := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(`{"path": "/test/project"}`),
		}

		if err := ws.WriteJSON(msg2); err != nil {
			t.Fatalf("Failed to send message: %v", err)
		}

		// Read response
		var resp2 models.ServerMessage
		ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&resp2); err != nil {
			t.Fatalf("Failed to read response: %v", err)
		}

		if resp2.Type != models.MessageTypeProjectState {
			t.Errorf("Expected response type %s, got %s", models.MessageTypeProjectState, resp2.Type)
		}

		// Verify project data in response
		if projectData, ok := resp2.Data.(map[string]interface{}); ok {
			if projectData["path"] != "/test/project" {
				t.Errorf("Expected project path /test/project, got %v", projectData["path"])
			}
		}
	})

	t.Run("Invalid Message Handling", func(t *testing.T) {
		// Connect
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		if err != nil {
			t.Fatalf("Failed to connect: %v", err)
		}
		defer ws.Close()

		// Send invalid message (empty type)
		invalidMsg := models.ClientMessage{
			Type: "",
		}

		if err := ws.WriteJSON(invalidMsg); err != nil {
			t.Fatalf("Failed to send message: %v", err)
		}

		// Should receive error response
		var errorResp models.ServerMessage
		ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&errorResp); err != nil {
			t.Fatalf("Failed to read response: %v", err)
		}

		if errorResp.Type != models.MessageTypeError {
			t.Errorf("Expected error response, got %s", errorResp.Type)
		}

		// Send unknown message type
		unknownMsg := models.ClientMessage{
			Type: "unknown_type",
		}

		if err := ws.WriteJSON(unknownMsg); err != nil {
			t.Fatalf("Failed to send message: %v", err)
		}

		// Should receive error response
		var errorResp2 models.ServerMessage
		ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		if err := ws.ReadJSON(&errorResp2); err != nil {
			t.Fatalf("Failed to read response: %v", err)
		}

		if errorResp2.Type != models.MessageTypeError {
			t.Errorf("Expected error response for unknown type, got %s", errorResp2.Type)
		}
	})

	t.Run("Concurrent Message Handling", func(t *testing.T) {
		// Connect multiple clients
		numClients := 5
		clients := make([]*websocket.Conn, numClients)
		
		for i := 0; i < numClients; i++ {
			ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
			if err != nil {
				t.Fatalf("Failed to connect client %d: %v", i, err)
			}
			defer ws.Close()
			clients[i] = ws
		}

		// Send messages concurrently
		var wg sync.WaitGroup
		for i, ws := range clients {
			wg.Add(1)
			go func(idx int, conn *websocket.Conn) {
				defer wg.Done()
				
				msg := models.ClientMessage{
					Type: models.MessageTypeProjectList,
				}
				
				if err := conn.WriteJSON(msg); err != nil {
					t.Errorf("Client %d: Failed to send message: %v", idx, err)
					return
				}

				var resp models.ServerMessage
				conn.SetReadDeadline(time.Now().Add(2 * time.Second))
				if err := conn.ReadJSON(&resp); err != nil {
					t.Errorf("Client %d: Failed to read response: %v", idx, err)
					return
				}

				if resp.Type != models.MessageTypeProjectState {
					t.Errorf("Client %d: Expected response type %s, got %s", idx, models.MessageTypeProjectState, resp.Type)
				}
			}(i, ws)
		}

		wg.Wait()
	})
}

func TestInvalidMessage(t *testing.T) {
	config := DefaultConfig()
	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Connect
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer ws.Close()

	// Send invalid message (empty type)
	msg := models.ClientMessage{
		Type: "",
	}

	if err := ws.WriteJSON(msg); err != nil {
		t.Fatalf("Failed to send message: %v", err)
	}

	// Should receive error response
	var response models.ServerMessage
	if err := ws.ReadJSON(&response); err != nil {
		t.Fatalf("Failed to read response: %v", err)
	}

	if response.Type != models.MessageTypeError {
		t.Errorf("Expected error response, got %s", response.Type)
	}
}

func TestConnectionTimeout(t *testing.T) {
	config := DefaultConfig()
	config.ConnectionTimeout = 200 * time.Millisecond

	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleWebSocket))
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Connect
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer ws.Close()

	// Don't send any messages, wait for timeout
	time.Sleep(300 * time.Millisecond)

	// Connection should be closed
	_, _, err = ws.ReadMessage()
	if err == nil {
		t.Fatal("Expected connection to be closed")
	}
}

func TestHealthEndpoint(t *testing.T) {
	config := DefaultConfig()
	handler := &mockHandler{}
	log := logger.New("debug")
	server := NewServer(config, handler, log)

	// Create test server
	ts := httptest.NewServer(http.HandlerFunc(server.handleHealth))
	defer ts.Close()

	// Make health check request
	resp, err := http.Get(ts.URL + "/health")
	if err != nil {
		t.Fatalf("Health check failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("Expected status %d, got %d", http.StatusOK, resp.StatusCode)
	}

	if ct := resp.Header.Get("Content-Type"); ct != "application/json" {
		t.Errorf("Expected content type application/json, got %s", ct)
	}
}
