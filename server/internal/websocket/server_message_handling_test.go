package websocket_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
	"github.com/boyd/pocket_agent/server/internal/websocket/handlers"
	gorilla "github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestMessageHandling tests the complete message handling pipeline with real components
func TestMessageHandling(t *testing.T) {
	// Create real components
	log := logger.New("debug")
	
	// Create project manager with proper config
	projectConfig := project.Config{
		DataDir:     "/tmp/test-projects",
		MaxProjects: 100,
		Validator:   nil, // Will be set later if needed
	}
	projectMgr, err := project.NewManager(projectConfig)
	require.NoError(t, err)
	
	// Create real broadcaster
	broadcasterConfig := handlers.BroadcasterConfig{
		WriteTimeout:       5 * time.Second,
		BroadcastBuffer:    100,
		SlowClientDeadline: 10 * time.Second,
	}
	broadcaster := handlers.NewBroadcaster(broadcasterConfig, log)
	
	// Create real handlers
	projectHandlers := handlers.NewProjectHandlers(projectMgr, broadcaster, log)
	
	// Create router and register handlers
	router := websocket.NewMessageRouter(log)
	projectHandlers.RegisterHandlers(router)
	
	// Create server with real router - use random port
	config := websocket.DefaultConfig()
	config.Port = 0 // Use random available port
	server := websocket.NewServer(config, router, log)
	
	// Use httptest server for simpler testing
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Create a new session and handle the WebSocket connection
		session, err := server.HandleUpgrade(w, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		
		// Handle messages for this session
		go func() {
			defer session.Close()
			
			for {
				var msg models.ClientMessage
				if err := session.Conn.ReadJSON(&msg); err != nil {
					return
				}
				
				// Pass to router
				if err := router.HandleMessage(context.Background(), session, &msg); err != nil {
					websocket.SendError(session, err)
				}
			}
		}()
	}))
	defer ts.Close()
	
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http")
	
	t.Run("successful project creation flow", func(t *testing.T) {
		// Connect client
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		// Send project create message
		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(`{"path": "/test/project"}`),
		}
		
		err = ws.WriteJSON(createMsg)
		require.NoError(t, err)
		
		// Read response
		var response models.ServerMessage
		err = ws.ReadJSON(&response)
		require.NoError(t, err)
		
		// Verify response
		assert.Equal(t, models.MessageTypeProjectState, response.Type)
		assert.NotEmpty(t, response.ProjectID)
		
		// Verify project state data
		stateData, ok := response.Data.(map[string]interface{})
		require.True(t, ok)
		assert.Equal(t, "/test/project", stateData["path"])
		assert.Equal(t, string(models.StateIdle), stateData["state"])
		
		// Verify project was created in the manager
		createdProject, err := projectMgr.GetProject(response.ProjectID)
		require.NoError(t, err)
		assert.Equal(t, "/test/project", createdProject.Path)
	})
	
	t.Run("invalid message handling", func(t *testing.T) {
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		// Send invalid message (missing required data)
		invalidMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(`{}`), // Missing path
		}
		
		err = ws.WriteJSON(invalidMsg)
		require.NoError(t, err)
		
		// Should receive error response
		var response models.ServerMessage
		err = ws.ReadJSON(&response)
		require.NoError(t, err)
		
		assert.Equal(t, models.MessageTypeError, response.Type)
		errData, ok := response.Data.(map[string]interface{})
		require.True(t, ok)
		assert.Equal(t, string(errors.CodeValidationFailed), errData["code"])
		assert.Contains(t, errData["message"], "path is required")
	})
	
	t.Run("concurrent message handling", func(t *testing.T) {
		const numClients = 5
		const messagesPerClient = 10
		
		var wg sync.WaitGroup
		responses := make([][]models.ServerMessage, numClients)
		
		for i := 0; i < numClients; i++ {
			wg.Add(1)
			clientIdx := i
			
			go func() {
				defer wg.Done()
				
				ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
				require.NoError(t, err)
				defer ws.Close()
				
				// Send multiple messages
				for j := 0; j < messagesPerClient; j++ {
					msg := models.ClientMessage{
						Type: models.MessageTypeProjectList,
					}
					
					err = ws.WriteJSON(msg)
					require.NoError(t, err)
					
					var resp models.ServerMessage
					err = ws.ReadJSON(&resp)
					require.NoError(t, err)
					
					responses[clientIdx] = append(responses[clientIdx], resp)
				}
			}()
		}
		
		wg.Wait()
		
		// Verify all clients got responses
		for i := 0; i < numClients; i++ {
			assert.Len(t, responses[i], messagesPerClient)
			for _, resp := range responses[i] {
				assert.Equal(t, models.MessageTypeProjectState, resp.Type)
			}
		}
	})
	
	t.Run("message validation pipeline", func(t *testing.T) {
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		testCases := []struct {
			name        string
			message     models.ClientMessage
			expectError bool
			errorCode   errors.ErrorCode
		}{
			{
				name: "empty message type",
				message: models.ClientMessage{
					Type: "",
				},
				expectError: true,
				errorCode:   errors.CodeValidationFailed,
			},
			{
				name: "unknown message type",
				message: models.ClientMessage{
					Type: "unknown_type",
				},
				expectError: true,
				errorCode:   errors.CodeValidationFailed,
			},
			{
				name: "valid project list",
				message: models.ClientMessage{
					Type: models.MessageTypeProjectList,
				},
				expectError: false,
			},
		}
		
		for _, tc := range testCases {
			t.Run(tc.name, func(t *testing.T) {
				err := ws.WriteJSON(tc.message)
				require.NoError(t, err)
				
				var response models.ServerMessage
				err = ws.ReadJSON(&response)
				require.NoError(t, err)
				
				if tc.expectError {
					assert.Equal(t, models.MessageTypeError, response.Type)
					errData := response.Data.(map[string]interface{})
					assert.Equal(t, string(tc.errorCode), errData["code"])
				} else {
					assert.NotEqual(t, models.MessageTypeError, response.Type)
				}
			})
		}
	})
	
	t.Run("session state management", func(t *testing.T) {
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		// Create a project
		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(`{"path": "/test/session/project"}`),
		}
		
		err = ws.WriteJSON(createMsg)
		require.NoError(t, err)
		
		var createResp models.ServerMessage
		err = ws.ReadJSON(&createResp)
		require.NoError(t, err)
		
		projectID := createResp.ProjectID
		
		// Join the project
		joinMsg := models.ClientMessage{
			Type:      models.MessageTypeProjectJoin,
			Data:      json.RawMessage(`{"project_id": "` + projectID + `"}`),
		}
		
		err = ws.WriteJSON(joinMsg)
		require.NoError(t, err)
		
		var joinResp models.ServerMessage
		err = ws.ReadJSON(&joinResp)
		require.NoError(t, err)
		
		assert.Equal(t, models.MessageTypeProjectJoined, joinResp.Type)
		
		// Verify subsequent messages use session project
		listMsg := models.ClientMessage{
			Type: models.MessageTypeGetMessages,
			ProjectID: projectID, // Should work with session project
		}
		
		err = ws.WriteJSON(listMsg)
		require.NoError(t, err)
		
		var listResp models.ServerMessage
		err = ws.ReadJSON(&listResp)
		require.NoError(t, err)
		
		// Should get response (even if empty)
		assert.NotEqual(t, models.MessageTypeError, listResp.Type)
	})
}

// TestMessageHandlingErrors tests error scenarios in message handling
func TestMessageHandlingErrors(t *testing.T) {
	log := logger.New("debug")
	
	// Create router with handler that always errors
	router := websocket.NewMessageRouter(log)
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		return errors.New(errors.CodeInternalError, "simulated error")
	})
	
	config := websocket.DefaultConfig()
	server := websocket.NewServer(config, router, log)
	
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		session, err := server.HandleUpgrade(w, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		
		go func() {
			defer session.Close()
			
			for {
				var msg models.ClientMessage
				if err := session.Conn.ReadJSON(&msg); err != nil {
					return
				}
				
				if err := router.HandleMessage(context.Background(), session, &msg); err != nil {
					websocket.SendError(session, err)
				}
			}
		}()
	}))
	defer ts.Close()
	
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	
	t.Run("handler error returns error message", func(t *testing.T) {
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		msg := models.ClientMessage{
			Type: models.MessageTypeProjectList,
		}
		
		err = ws.WriteJSON(msg)
		require.NoError(t, err)
		
		var response models.ServerMessage
		err = ws.ReadJSON(&response)
		require.NoError(t, err)
		
		assert.Equal(t, models.MessageTypeError, response.Type)
		errData := response.Data.(map[string]interface{})
		assert.Equal(t, string(errors.CodeInternalError), errData["code"])
		assert.Contains(t, errData["message"], "simulated error")
	})
	
	t.Run("panic recovery", func(t *testing.T) {
		// Add panic handler
		panicRouter := websocket.NewMessageRouter(log)
		panicRouter.Register(models.MessageTypeProjectCreate, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
			panic("simulated panic")
		})
		
		// Create dispatcher with recovery middleware
		dispatcher := websocket.NewMessageDispatcher(panicRouter, log)
		dispatcher.Use(websocket.RecoveryMiddleware(log))
		
		panicServer := websocket.NewServer(config, dispatcher, log)
		panicTS := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			session, err := panicServer.HandleUpgrade(w, r)
			if err != nil {
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
			
			go func() {
				defer session.Close()
				
				for {
					var msg models.ClientMessage
					if err := session.Conn.ReadJSON(&msg); err != nil {
						return
					}
					
					if err := dispatcher.HandleMessage(context.Background(), session, &msg); err != nil {
						websocket.SendError(session, err)
					}
				}
			}()
		}))
		defer panicTS.Close()
		
		panicURL := "ws" + strings.TrimPrefix(panicTS.URL, "http") + "/ws"
		
		ws, _, err := gorilla.DefaultDialer.Dial(panicURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		msg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(`{"path": "/test"}`),
		}
		
		err = ws.WriteJSON(msg)
		require.NoError(t, err)
		
		var response models.ServerMessage
		err = ws.ReadJSON(&response)
		require.NoError(t, err)
		
		assert.Equal(t, models.MessageTypeError, response.Type)
		errData := response.Data.(map[string]interface{})
		assert.Equal(t, string(errors.CodeInternalError), errData["code"])
	})
}

// TestMessageHandlingPerformance tests performance aspects
func TestMessageHandlingPerformance(t *testing.T) {
	log := logger.New("error") // Less logging for performance tests
	
	// Create fast handler
	router := websocket.NewMessageRouter(log)
	router.Register(models.MessageTypeProjectList, func(ctx context.Context, session *models.Session, data json.RawMessage) error {
		// Return empty list quickly
		return websocket.SendSuccess(session, models.MessageTypeProjectState, []interface{}{})
	})
	
	config := websocket.DefaultConfig()
	server := websocket.NewServer(config, router, log)
	
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		session, err := server.HandleUpgrade(w, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		
		go func() {
			defer session.Close()
			
			for {
				var msg models.ClientMessage
				if err := session.Conn.ReadJSON(&msg); err != nil {
					return
				}
				
				if err := router.HandleMessage(context.Background(), session, &msg); err != nil {
					websocket.SendError(session, err)
				}
			}
		}()
	}))
	defer ts.Close()
	
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	
	t.Run("message throughput", func(t *testing.T) {
		ws, _, err := gorilla.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()
		
		const numMessages = 1000
		start := time.Now()
		
		// Send messages in parallel with reading
		errChan := make(chan error, 1)
		go func() {
			for i := 0; i < numMessages; i++ {
				msg := models.ClientMessage{
					Type: models.MessageTypeProjectList,
				}
				if err := ws.WriteJSON(msg); err != nil {
					errChan <- err
					return
				}
			}
			close(errChan)
		}()
		
		// Read responses
		responsesReceived := 0
		for responsesReceived < numMessages {
			var response models.ServerMessage
			if err := ws.ReadJSON(&response); err != nil {
				t.Fatalf("Failed to read response: %v", err)
			}
			responsesReceived++
		}
		
		// Check for write errors
		select {
		case err := <-errChan:
			if err != nil {
				t.Fatalf("Write error: %v", err)
			}
		default:
		}
		
		elapsed := time.Since(start)
		messagesPerSecond := float64(numMessages) / elapsed.Seconds()
		
		t.Logf("Processed %d messages in %v (%.0f msg/sec)", numMessages, elapsed, messagesPerSecond)
		
		// Should handle at least 100 messages per second
		assert.Greater(t, messagesPerSecond, 100.0)
	})
}

// Test helpers
// Using real components so no test helpers needed