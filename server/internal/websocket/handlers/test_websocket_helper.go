package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
)

// testWebSocketServer creates a test WebSocket server and client connection
type testWebSocketServer struct {
	server          *httptest.Server
	serverConn      *websocket.Conn
	clientConn      *websocket.Conn
	receivedMessages []interface{}
	mu              sync.Mutex
	connMu          sync.Mutex  // Separate mutex for connection access
}

// newTestWebSocketServer creates a test WebSocket server
func newTestWebSocketServer(t *testing.T) *testWebSocketServer {
	tws := &testWebSocketServer{
		receivedMessages: make([]interface{}, 0),
	}
	
	// Create test server
	tws.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		}
		
		var err error
		tws.connMu.Lock()
		tws.serverConn, err = upgrader.Upgrade(w, r, nil)
		tws.connMu.Unlock()
		if err != nil {
			t.Fatalf("Failed to upgrade: %v", err)
		}
		
		// Read messages in background
		go func() {
			for {
				var msg interface{}
				err := tws.serverConn.ReadJSON(&msg)
				if err != nil {
					return
				}
				
				tws.mu.Lock()
				tws.receivedMessages = append(tws.receivedMessages, msg)
				tws.mu.Unlock()
			}
		}()
	}))
	
	// Create client connection
	wsURL := strings.Replace(tws.server.URL, "http", "ws", 1)
	var err error
	tws.clientConn, _, err = websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Failed to dial: %v", err)
	}
	
	return tws
}

// Close cleans up the test server
func (tws *testWebSocketServer) Close() {
	if tws.clientConn != nil {
		tws.clientConn.Close()
	}
	tws.connMu.Lock()
	if tws.serverConn != nil {
		tws.serverConn.Close()
	}
	tws.connMu.Unlock()
	tws.server.Close()
}

// GetClientConn returns the client connection for use in sessions
func (tws *testWebSocketServer) GetClientConn() *websocket.Conn {
	return tws.clientConn
}

// GetReceivedMessages returns messages received by the server
func (tws *testWebSocketServer) GetReceivedMessages() []interface{} {
	tws.mu.Lock()
	defer tws.mu.Unlock()
	return append([]interface{}{}, tws.receivedMessages...)
}

// createTestSessionWithWebSocket creates a session with a real test WebSocket connection
func createTestSessionWithWebSocket(t *testing.T, id string) (*models.Session, *testWebSocketServer) {
	tws := newTestWebSocketServer(t)
	session := models.NewSession(id, tws.GetClientConn())
	return session, tws
}