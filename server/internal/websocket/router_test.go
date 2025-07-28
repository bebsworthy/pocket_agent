package websocket

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
)

func TestMessageRouter(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)

	// Register test handler
	handled := false
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		handled = true
		return nil
	})

	// Create test session
	session := &models.Session{
		ID: "test-session",
	}

	// Test registered message type
	msg := &models.ClientMessage{
		Type: models.MessageTypeProjectList,
	}

	err := router.HandleMessage(context.Background(), session, msg)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}

	if !handled {
		t.Error("Handler was not called")
	}
}

func TestMessageRouterUnknownType(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)

	// Create test session
	session := &models.Session{
		ID: "test-session",
	}

	// Test unregistered message type
	msg := &models.ClientMessage{
		Type: "unknown_type",
	}

	err := router.HandleMessage(context.Background(), session, msg)
	if err == nil {
		t.Error("Expected error for unknown message type")
	}
}

func TestMessageRouterHandlerError(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)

	// Register handler that returns error
	testErr := errors.New("test error")
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		return testErr
	})

	// Create test session
	session := &models.Session{
		ID: "test-session",
	}

	msg := &models.ClientMessage{
		Type: models.MessageTypeProjectList,
	}

	err := router.HandleMessage(context.Background(), session, msg)
	if err == nil {
		t.Error("Expected error from handler")
	}
}

func TestMessageDispatcher(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)
	dispatcher := NewMessageDispatcher(router, log)

	// Add test middleware
	middlewareCalled := false
	dispatcher.Use(func(next MessageHandler) MessageHandler {
		return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
			middlewareCalled = true
			return next.HandleMessage(ctx, session, msg)
		})
	})

	// Register handler
	handlerCalled := false
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		handlerCalled = true
		return nil
	})

	// Create test session
	session := &models.Session{
		ID: "test-session",
	}

	msg := &models.ClientMessage{
		Type: models.MessageTypeProjectList,
	}

	err := dispatcher.HandleMessage(context.Background(), session, msg)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}

	if !middlewareCalled {
		t.Error("Middleware was not called")
	}

	if !handlerCalled {
		t.Error("Handler was not called")
	}
}

func TestRecoveryMiddleware(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)
	dispatcher := NewMessageDispatcher(router, log)

	// Add recovery middleware
	dispatcher.Use(RecoveryMiddleware(log))

	// Register handler that panics
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		panic("test panic")
	})

	// Create test session
	session := &models.Session{
		ID: "test-session",
	}

	msg := &models.ClientMessage{
		Type: models.MessageTypeProjectList,
	}

	// Should not panic
	err := dispatcher.HandleMessage(context.Background(), session, msg)
	if err == nil {
		t.Error("Expected error from panic recovery")
	}
}

func TestValidationMiddleware(t *testing.T) {
	log := logger.New("debug")
	router := NewMessageRouter(log)
	dispatcher := NewMessageDispatcher(router, log)

	// Add validation middleware
	dispatcher.Use(ValidationMiddleware())

	// Register handler
	router.Register(models.MessageTypeExecute, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		return nil
	})

	tests := []struct {
		name      string
		msg       *models.ClientMessage
		session   *models.Session
		expectErr bool
	}{
		{
			name: "Valid message with project in session",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":"test"}`),
			},
			session: &models.Session{
				ID:        "test-session",
				ProjectID: "project-123",
			},
			expectErr: false,
		},
		{
			name: "Valid message with project in message",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":"test"}`),
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectErr: false,
		},
		{
			name: "Invalid message without project",
			msg: &models.ClientMessage{
				Type: models.MessageTypeExecute,
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := dispatcher.HandleMessage(context.Background(), tt.session, tt.msg)
			if (err != nil) != tt.expectErr {
				t.Errorf("Expected error: %v, got: %v", tt.expectErr, err)
			}
		})
	}
}

func TestSendHelpers(t *testing.T) {
	// Create mock connection
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, _ := upgrader.Upgrade(w, r, nil)
		defer conn.Close()

		// Read messages sent by helpers
		for {
			var msg models.ServerMessage
			if err := conn.ReadJSON(&msg); err != nil {
				return
			}

			// Echo back for verification
			conn.WriteJSON(msg)
		}
	}))
	defer server.Close()

	// Connect
	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer conn.Close()

	session := models.NewSession("test", conn)

	// Test SendProjectState
	project := &models.Project{
		ID:   "project-123",
		Path: "/test/path",
	}

	if err := SendProjectState(session, project); err != nil {
		t.Errorf("SendProjectState failed: %v", err)
	}

	// Verify message
	var stateMsg models.ServerMessage
	if err := conn.ReadJSON(&stateMsg); err != nil {
		t.Errorf("Failed to read state message: %v", err)
	}

	if stateMsg.Type != models.MessageTypeProjectState {
		t.Errorf("Expected type %s, got %s", models.MessageTypeProjectState, stateMsg.Type)
	}

	// Test SendError
	testErr := errors.New("test error")
	if err := SendError(session, testErr); err != nil {
		t.Errorf("SendError failed: %v", err)
	}

	// Verify error message
	var errMsg models.ServerMessage
	if err := conn.ReadJSON(&errMsg); err != nil {
		t.Errorf("Failed to read error message: %v", err)
	}

	if errMsg.Type != models.MessageTypeError {
		t.Errorf("Expected type %s, got %s", models.MessageTypeError, errMsg.Type)
	}
}
