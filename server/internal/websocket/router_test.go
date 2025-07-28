package websocket

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	appErrors "github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestMessageRouter(t *testing.T) {
	t.Run("successful routing", func(t *testing.T) {
		log := logger.New("debug")
		router := NewMessageRouter(log)

		// Create a handler that tracks execution details
		var handlerCtx context.Context
		var handlerSession *models.Session
		var handlerData json.RawMessage
		handled := false
		
		router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
			handled = true
			handlerCtx = ctx
			handlerSession = session
			handlerData = data
			
			// Simulate real work
			return SendSuccess(session, models.MessageTypeProjectState, map[string]interface{}{
				"projects": []interface{}{},
			})
		})

		// Create test session with write capability
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			upgrader := websocket.Upgrader{}
			conn, _ := upgrader.Upgrade(w, r, nil)
			defer conn.Close()
			
			// Keep connection open for test
			time.Sleep(100 * time.Millisecond)
		}))
		defer server.Close()
		
		wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
		conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()
		
		session := models.NewSession("test-session", conn)

		// Test with actual message data
		msgData := json.RawMessage(`{"filter": "active"}`)
		msg := &models.ClientMessage{
			Type: models.MessageTypeProjectList,
			Data: msgData,
		}

		// Create context with value to verify propagation
		ctx := context.WithValue(context.Background(), "test-key", "test-value")
		
		err = router.HandleMessage(ctx, session, msg)
		assert.NoError(t, err)
		assert.True(t, handled, "Handler should have been called")
		
		// Verify context propagation
		assert.Equal(t, "test-value", handlerCtx.Value("test-key"))
		
		// Verify session was passed correctly
		assert.Equal(t, session, handlerSession)
		
		// Verify data was passed correctly
		assert.Equal(t, msgData, handlerData)
	})
	
	t.Run("middleware integration", func(t *testing.T) {
		log := logger.New("debug")
		router := NewMessageRouter(log)
		dispatcher := NewMessageDispatcher(router, log)
		
		// Track middleware execution order
		executionOrder := []string{}
		
		// Add multiple middleware
		dispatcher.Use(func(next MessageHandler) MessageHandler {
			return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
				executionOrder = append(executionOrder, "middleware1-before")
				err := next.HandleMessage(ctx, session, msg)
				executionOrder = append(executionOrder, "middleware1-after")
				return err
			})
		})
		
		dispatcher.Use(func(next MessageHandler) MessageHandler {
			return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
				executionOrder = append(executionOrder, "middleware2-before")
				err := next.HandleMessage(ctx, session, msg)
				executionOrder = append(executionOrder, "middleware2-after")
				return err
			})
		})
		
		// Register handler
		router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
			executionOrder = append(executionOrder, "handler")
			return nil
		})
		
		session := &models.Session{ID: "test"}
		msg := &models.ClientMessage{Type: models.MessageTypeProjectList}
		
		err := dispatcher.HandleMessage(context.Background(), session, msg)
		assert.NoError(t, err)
		
		// Verify execution order
		expected := []string{
			"middleware1-before",
			"middleware2-before",
			"handler",
			"middleware2-after",
			"middleware1-after",
		}
		assert.Equal(t, expected, executionOrder)
	})
	
	t.Run("error propagation", func(t *testing.T) {
		log := logger.New("debug")
		router := NewMessageRouter(log)
		
		testErr := errors.New("handler error")
		router.Register(models.MessageTypeProjectCreate, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
			return testErr
		})
		
		session := &models.Session{ID: "test"}
		msg := &models.ClientMessage{Type: models.MessageTypeProjectCreate}
		
		err := router.HandleMessage(context.Background(), session, msg)
		assert.Equal(t, testErr, err)
	})
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

// mockNextHandler records what messages are passed to it
type mockNextHandler struct {
	called      bool
	receivedMsg *models.ClientMessage
	receivedSes *models.Session
	returnErr   error
}

func (m *mockNextHandler) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	m.called = true
	m.receivedMsg = msg
	m.receivedSes = session
	return m.returnErr
}

func TestValidationMiddleware(t *testing.T) {
	middleware := ValidationMiddleware()

	tests := []struct {
		name           string
		msg            *models.ClientMessage
		session        *models.Session
		expectCalled   bool
		expectErr      bool
		expectedErrMsg string
	}{
		{
			name: "Valid execute message with project in session",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":"test"}`),
			},
			session: &models.Session{
				ID:        "test-session",
				ProjectID: "project-123",
			},
			expectCalled: true,
			expectErr:    false,
		},
		{
			name: "Valid execute message with project only in message",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":"test"}`),
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled: true,
			expectErr:    false,
		},
		{
			name: "Execute message without project fails validation",
			msg: &models.ClientMessage{
				Type: models.MessageTypeExecute,
				Data: json.RawMessage(`{"prompt":"test"}`),
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled:   false,
			expectErr:      true,
			expectedErrMsg: "project_id required",
		},
		{
			name: "Message with empty type fails basic validation",
			msg: &models.ClientMessage{
				Type: "",
				Data: json.RawMessage(`{}`),
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled:   false,
			expectErr:      true,
			expectedErrMsg: "message type cannot be empty",
		},
		{
			name: "Execute message with invalid data fails validation",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`invalid json`),
			},
			session: &models.Session{
				ID:        "test-session",
				ProjectID: "project-123",
			},
			expectCalled:   false,
			expectErr:      true,
			expectedErrMsg: "invalid execute command data",
		},
		{
			name: "Execute message with empty prompt fails validation",
			msg: &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":""}`),
			},
			session: &models.Session{
				ID:        "test-session",
				ProjectID: "project-123",
			},
			expectCalled:   false,
			expectErr:      true,
			expectedErrMsg: "prompt cannot be empty",
		},
		{
			name: "ProjectList message doesn't require project",
			msg: &models.ClientMessage{
				Type: models.MessageTypeProjectList,
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled: true,
			expectErr:    false,
		},
		{
			name: "AgentKill message requires project",
			msg: &models.ClientMessage{
				Type: models.MessageTypeAgentKill,
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled:   false,
			expectErr:      true,
			expectedErrMsg: "project_id required",
		},
		{
			name: "Next handler error is propagated",
			msg: &models.ClientMessage{
				Type: models.MessageTypeProjectList,
			},
			session: &models.Session{
				ID: "test-session",
			},
			expectCalled: true,
			expectErr:    true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create mock next handler
			mockNext := &mockNextHandler{}
			if tt.name == "Next handler error is propagated" {
				mockNext.returnErr = errors.New("handler error")
			}

			// Apply middleware
			handler := middleware(mockNext)

			// Execute
			err := handler.HandleMessage(context.Background(), tt.session, tt.msg)

			// Verify error expectation
			if (err != nil) != tt.expectErr {
				t.Errorf("Expected error: %v, got: %v", tt.expectErr, err)
			}

			// Verify error message if expected
			if tt.expectErr && tt.expectedErrMsg != "" && err != nil {
				if !strings.Contains(err.Error(), tt.expectedErrMsg) {
					t.Errorf("Expected error to contain '%s', got: %v", tt.expectedErrMsg, err)
				}
			}

			// Verify next handler was called as expected
			if mockNext.called != tt.expectCalled {
				t.Errorf("Expected next handler called: %v, actual: %v", tt.expectCalled, mockNext.called)
			}

			// If next was called, verify the same message was passed
			if mockNext.called {
				if mockNext.receivedMsg != tt.msg {
					t.Error("Next handler received different message instance")
				}
				if mockNext.receivedSes != tt.session {
					t.Error("Next handler received different session instance")
				}
			}
		})
	}
}

// TestValidationMiddlewareConcurrency verifies thread safety
func TestValidationMiddlewareConcurrency(t *testing.T) {
	middleware := ValidationMiddleware()

	// Create a session with a project
	session := &models.Session{
		ID:        "test-session",
		ProjectID: "project-123",
	}

	// Mock handler that sleeps to increase chance of race conditions
	mockNext := &mockNextHandler{}
	handler := middleware(mockNext)

	// Run multiple goroutines
	const numGoroutines = 10
	done := make(chan bool, numGoroutines)

	for i := 0; i < numGoroutines; i++ {
		go func(id int) {
			msg := &models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: "project-123",
				Data:      json.RawMessage(`{"prompt":"test"}`),
			}

			err := handler.HandleMessage(context.Background(), session, msg)
			if err != nil {
				t.Errorf("Goroutine %d: unexpected error: %v", id, err)
			}
			done <- true
		}(i)
	}

	// Wait for all goroutines
	for i := 0; i < numGoroutines; i++ {
		<-done
	}
}

// TestValidationMiddlewareRealMessageTypes tests all message types that require validation
func TestValidationMiddlewareRealMessageTypes(t *testing.T) {
	middleware := ValidationMiddleware()

	// Message types that require a project
	projectRequiredTypes := []models.MessageType{
		models.MessageTypeExecute,
		models.MessageTypeProjectDelete,
		models.MessageTypeAgentNewSession,
		models.MessageTypeAgentKill,
		models.MessageTypeProjectLeave,
		models.MessageTypeGetMessages,
	}

	for _, msgType := range projectRequiredTypes {
		t.Run(string(msgType)+" requires project", func(t *testing.T) {
			mockNext := &mockNextHandler{}
			handler := middleware(mockNext)

			// Create message without project
			msg := &models.ClientMessage{
				Type: msgType,
			}

			// Session without project
			session := &models.Session{
				ID: "test-session",
			}

			err := handler.HandleMessage(context.Background(), session, msg)
			if err == nil {
				t.Errorf("Expected error for %s without project", msgType)
			}
			if !strings.Contains(err.Error(), "project_id required") {
				t.Errorf("Expected project_id required error, got: %v", err)
			}
			if mockNext.called {
				t.Error("Next handler should not be called for invalid message")
			}
		})
	}
}

func TestSendHelpers(t *testing.T) {
	t.Run("message construction and sending", func(t *testing.T) {
		// Create a test double that captures messages
		sentMessages := make([]models.ServerMessage, 0)
		var mu sync.Mutex
		
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			upgrader := websocket.Upgrader{}
			conn, _ := upgrader.Upgrade(w, r, nil)
			defer conn.Close()

			// Capture messages
			for {
				var msg models.ServerMessage
				if err := conn.ReadJSON(&msg); err != nil {
					return
				}
				
				mu.Lock()
				sentMessages = append(sentMessages, msg)
				mu.Unlock()
			}
		}))
		defer server.Close()

		wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
		conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()

		session := models.NewSession("test-session", conn)

		// Test SendProjectState with complete project data
		project := &models.Project{
			ID:         "project-123",
			Path:       "/test/path",
			State:      models.StateIdle,
			CreatedAt:  time.Now(),
			LastActive: time.Now(),
		}

		err = SendProjectState(session, project)
		assert.NoError(t, err)
		
		// Give time for message to be captured
		time.Sleep(50 * time.Millisecond)
		
		mu.Lock()
		require.Len(t, sentMessages, 1)
		stateMsg := sentMessages[0]
		mu.Unlock()
		
		// Verify message structure
		assert.Equal(t, models.MessageTypeProjectState, stateMsg.Type)
		assert.Equal(t, project.ID, stateMsg.ProjectID)
		
		// Verify project data serialization
		projectData, ok := stateMsg.Data.(map[string]interface{})
		require.True(t, ok)
		assert.Equal(t, project.ID, projectData["id"])
		assert.Equal(t, project.Path, projectData["path"])
		assert.Equal(t, string(project.State), projectData["state"])
	})
	
	t.Run("error transformation", func(t *testing.T) {
		sentMessages := make([]models.ServerMessage, 0)
		var mu sync.Mutex
		
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			upgrader := websocket.Upgrader{}
			conn, _ := upgrader.Upgrade(w, r, nil)
			defer conn.Close()

			for {
				var msg models.ServerMessage
				if err := conn.ReadJSON(&msg); err != nil {
					return
				}
				
				mu.Lock()
				sentMessages = append(sentMessages, msg)
				mu.Unlock()
			}
		}))
		defer server.Close()

		wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
		conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()

		session := models.NewSession("test", conn)

		// Test different error types
		testCases := []struct {
			name        string
			err         error
			expectCode  string
			expectMsg   string
		}{
			{
				name:       "standard error",
				err:        errors.New("standard error message"),
				expectCode: "INTERNAL_ERROR",
				expectMsg:  "standard error message",
			},
			{
				name:       "app error with code",
				err:        &appErrors.AppError{Code: appErrors.ErrorCode("CUSTOM_CODE"), Message: "custom message"},
				expectCode: "CUSTOM_CODE",
				expectMsg:  "custom message",
			},
		}
		
		for _, tc := range testCases {
			t.Run(tc.name, func(t *testing.T) {
				mu.Lock()
				sentMessages = sentMessages[:0] // Clear
				mu.Unlock()
				
				err := SendError(session, tc.err)
				assert.NoError(t, err)
				
				time.Sleep(50 * time.Millisecond)
				
				mu.Lock()
				require.Len(t, sentMessages, 1)
				errMsg := sentMessages[0]
				mu.Unlock()
				
				assert.Equal(t, models.MessageTypeError, errMsg.Type)
				
				errData, ok := errMsg.Data.(map[string]interface{})
				require.True(t, ok)
				assert.Equal(t, tc.expectCode, errData["code"])
				assert.Equal(t, tc.expectMsg, errData["message"])
			})
		}
	})
	
	t.Run("concurrent sends", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			upgrader := websocket.Upgrader{}
			conn, _ := upgrader.Upgrade(w, r, nil)
			defer conn.Close()
			
			// Discard messages
			for {
				var msg models.ServerMessage
				if err := conn.ReadJSON(&msg); err != nil {
					return
				}
			}
		}))
		defer server.Close()

		wsURL := "ws" + strings.TrimPrefix(server.URL, "http")
		conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()

		session := models.NewSession("test", conn)

		// Send messages concurrently
		var wg sync.WaitGroup
		const numGoroutines = 10
		
		for i := 0; i < numGoroutines; i++ {
			wg.Add(1)
			go func(id int) {
				defer wg.Done()
				
				// Alternate between different message types
				if id%2 == 0 {
					err := SendSuccess(session, models.MessageTypeProjectState, map[string]interface{}{
						"id": id,
					})
					assert.NoError(t, err)
				} else {
					err := SendError(session, errors.New("concurrent error"))
					assert.NoError(t, err)
				}
			}(i)
		}
		
		wg.Wait()
	})
}
