package integration

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/config"
	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	ws "github.com/boyd/pocket_agent/server/internal/websocket"
	"github.com/boyd/pocket_agent/server/internal/websocket/handlers"
	"github.com/boyd/pocket_agent/server/test/mocks"
	"github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestWebSocketLifecycle tests the complete WebSocket connection lifecycle
func TestWebSocketLifecycle(t *testing.T) {
	// Setup test environment
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	// Create mock Claude executable
	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioSuccess).
		WithSessionID("test-session-123")
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	// Create server with mock
	server := createTestServer(t, testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	// Convert http:// to ws://
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	
	// Debug: Log the URL
	t.Logf("Test server URL: %s", ts.URL)
	t.Logf("WebSocket URL: %s", wsURL)

	t.Run("Connect and Disconnect", func(t *testing.T) {
		// Connect to server with explicit headers
		headers := http.Header{}
		headers.Add("Origin", "http://localhost")
		ws, resp, err := websocket.DefaultDialer.Dial(wsURL, headers)
		if err != nil {
			t.Logf("WebSocket dial failed: %v", err)
			if resp != nil {
				t.Logf("Response status: %s", resp.Status)
				t.Logf("Response headers: %v", resp.Header)
			}
		}
		require.NoError(t, err)
		defer ws.Close()

		// Send ping
		err = ws.WriteControl(websocket.PingMessage, []byte{}, time.Now().Add(time.Second))
		assert.NoError(t, err)

		// Close connection
		err = ws.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
		assert.NoError(t, err)
	})

	t.Run("Multiple Connections", func(t *testing.T) {
		var wg sync.WaitGroup
		connections := 5

		for i := 0; i < connections; i++ {
			wg.Add(1)
			go func(id int) {
				defer wg.Done()

				ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
				require.NoError(t, err)
				defer ws.Close()

				// Each connection sends a message
				msg := models.ClientMessage{
					Type: models.MessageTypeProjectList,
				}
				err = ws.WriteJSON(msg)
				assert.NoError(t, err)

				// Read response
				var resp models.ServerMessage
				err = ws.ReadJSON(&resp)
				assert.NoError(t, err)
				assert.Equal(t, models.MessageTypeProjectList, resp.Type)
			}(i)
		}

		wg.Wait()
	})
}

// TestProjectManagement tests project CRUD operations over WebSocket
func TestProjectManagement(t *testing.T) {
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(t).WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	server := createTestServer(t, testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	require.NoError(t, err)
	defer ws.Close()

	// Create test project directory
	projectPath := filepath.Join(testDir, "test_project")
	os.MkdirAll(projectPath, 0o755)

	t.Run("Create Project", func(t *testing.T) {
		// Send create project message
		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
		}
		err := ws.WriteJSON(createMsg)
		require.NoError(t, err)

		// Read response
		var resp models.ServerMessage
		err = ws.ReadJSON(&resp)
		require.NoError(t, err)
		assert.Equal(t, models.MessageTypeProjectState, resp.Type)

		// Verify project data
		data, ok := resp.Data.(map[string]interface{})
		require.True(t, ok)
		assert.Equal(t, projectPath, data["path"])
		assert.NotEmpty(t, data["id"])
	})

	t.Run("List Projects", func(t *testing.T) {
		listMsg := models.ClientMessage{
			Type: models.MessageTypeProjectList,
		}
		err := ws.WriteJSON(listMsg)
		require.NoError(t, err)

		var resp models.ServerMessage
		err = ws.ReadJSON(&resp)
		require.NoError(t, err)
		assert.Equal(t, models.MessageTypeProjectList, resp.Type)

		// Should have at least one project
		respData, ok := resp.Data.(map[string]interface{})
		require.True(t, ok)
		
		projects, ok := respData["projects"].([]interface{})
		require.True(t, ok)
		assert.GreaterOrEqual(t, len(projects), 1)
	})

	t.Run("Delete Project", func(t *testing.T) {
		// First create a project to delete (in case running in isolation)
		deletePath := filepath.Join(testDir, "delete_project")
		os.MkdirAll(deletePath, 0o755)
		
		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, deletePath)),
		}
		err := ws.WriteJSON(createMsg)
		require.NoError(t, err)
		
		var createResp models.ServerMessage
		err = ws.ReadJSON(&createResp)
		require.NoError(t, err)
		
		// Get the project ID from creation response
		projectData := createResp.Data.(map[string]interface{})
		projectID := projectData["id"].(string)

		// Delete project
		deleteMsg := models.ClientMessage{
			Type:      models.MessageTypeProjectDelete,
			ProjectID: projectID,
			Data:      json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
		}
		err = ws.WriteJSON(deleteMsg)
		require.NoError(t, err)

		var deleteResp models.ServerMessage
		err = ws.ReadJSON(&deleteResp)
		require.NoError(t, err)
		
		// Log the response for debugging
		if deleteResp.Type == models.MessageTypeError {
			t.Logf("Delete error response: %+v", deleteResp.Data)
		}
		
		assert.Equal(t, models.MessageTypeProjectDeleted, deleteResp.Type)
	})
}

// TestClaudeExecution tests Claude command execution with mock
func TestClaudeExecution(t *testing.T) {
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	// Create different mock scenarios
	scenarios := []struct {
		name     string
		scenario mocks.ClaudeMockScenario
		check    func(t *testing.T, resp models.ServerMessage, err error)
	}{
		{
			name:     "Successful Execution",
			scenario: mocks.ScenarioSuccess,
			check: func(t *testing.T, resp models.ServerMessage, err error) {
				require.NoError(t, err)
				assert.Equal(t, models.MessageTypeAgentMessage, resp.Type)
				// The data should be a Claude message
				data, ok := resp.Data.(map[string]interface{})
				require.True(t, ok)
				// Claude messages have 'type' and 'content' fields
				assert.Equal(t, "text", data["type"])
				content, ok := data["content"].(map[string]interface{})
				require.True(t, ok)
				assert.NotEmpty(t, content["text"])
			},
		},
		{
			name:     "Error Response",
			scenario: mocks.ScenarioError,
			check: func(t *testing.T, resp models.ServerMessage, err error) {
				require.NoError(t, err)
				if resp.Type != models.MessageTypeError {
					t.Logf("Expected error type but got: %s, data: %+v", resp.Type, resp.Data)
				}
				assert.Equal(t, models.MessageTypeError, resp.Type)
			},
		},
		{
			name:     "Timeout",
			scenario: mocks.ScenarioTimeout,
			check: func(t *testing.T, resp models.ServerMessage, err error) {
				// Should timeout and return error
				require.NoError(t, err)
				assert.Equal(t, models.MessageTypeError, resp.Type)
				data, ok := resp.Data.(map[string]interface{})
				require.True(t, ok)
				assert.Contains(t, data["message"], "timed out")
			},
		},
	}

	for _, tc := range scenarios {
		t.Run(tc.name, func(t *testing.T) {
			// Create mock for this scenario
			mock := mocks.NewClaudeMockExecutable(t).WithScenario(tc.scenario)
			claudePath := mock.MustCreate(t)
			defer mock.Cleanup()
			
			// Verify the mock executable exists
			if _, err := os.Stat(claudePath); err != nil {
				t.Fatalf("Mock executable not found at %s: %v", claudePath, err)
			}

			// Create server with appropriate timeout for each test
			timeout := 2 * time.Second // Default timeout
			if tc.scenario == mocks.ScenarioTimeout {
				timeout = 100 * time.Millisecond // Short timeout for timeout test
			}
			
			cfg := &config.Config{
				DataDir:  filepath.Join(testDir, "data"),
				Port:     0,
				Execution: config.ExecutionConfig{
					ClaudeBinaryPath: claudePath,
					CommandTimeout:   timeout,
					MaxProjects:      100,
				},
			}

			server := createTestServerWithConfig(t, cfg)
			ts := httptest.NewServer(server)
			defer ts.Close()

			wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
			ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
			require.NoError(t, err)
			defer ws.Close()

			// Create project
			projectPath := filepath.Join(testDir, tc.name)
			os.MkdirAll(projectPath, 0o755)

			createMsg := models.ClientMessage{
				Type: models.MessageTypeProjectCreate,
				Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
			}
			err = ws.WriteJSON(createMsg)
			require.NoError(t, err)

			var createResp models.ServerMessage
			err = ws.ReadJSON(&createResp)
			require.NoError(t, err)

			projectData := createResp.Data.(map[string]interface{})
			projectID := projectData["id"].(string)
			
			// Join the project before executing
			joinMsg := models.ClientMessage{
				Type: models.MessageTypeProjectJoin,
				Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
			}
			err = ws.WriteJSON(joinMsg)
			require.NoError(t, err)
			
			var joinResp models.ServerMessage
			err = ws.ReadJSON(&joinResp)
			require.NoError(t, err)
			// The join operation might return project_state or project_join
			if joinResp.Type != models.MessageTypeProjectJoin && joinResp.Type != models.MessageTypeProjectState {
				t.Errorf("Expected project_join or project_state, got %s", joinResp.Type)
			}

			// Execute Claude command first
			executeMsg := models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: projectID,
				Data:      json.RawMessage(`{"prompt": "Test prompt"}`),
			}
			err = ws.WriteJSON(executeMsg)
			require.NoError(t, err)

			// Read response with timeout
			ws.SetReadDeadline(time.Now().Add(5 * time.Second))
			
			// Read messages until we get the expected type or timeout
			var executeResp models.ServerMessage
			messageReceived := false
			for i := 0; i < 10; i++ { // Try up to 10 messages
				var msg models.ServerMessage
				err = ws.ReadJSON(&msg)
				if err != nil {
					tc.check(t, executeResp, err)
					return
				}
				
				// Log the message type for debugging
				t.Logf("Received message type: %s, data: %v", msg.Type, msg.Data)
				
				// Skip project state updates and other intermediate messages
				if msg.Type == models.MessageTypeProjectState || 
				   msg.Type == models.MessageTypeProjectUpdate {
					continue
				}
				
				// Check if this is an execute confirmation (should not happen)
				if msg.Type == models.MessageTypeExecute {
					t.Errorf("Received unexpected execute message echo")
					continue
				}
				
				// We're looking for agent_message or error
				if msg.Type == models.MessageTypeAgentMessage || 
				   msg.Type == models.MessageTypeError {
					executeResp = msg
					messageReceived = true
					break
				}
			}
			
			if messageReceived {
				tc.check(t, executeResp, nil)
			} else {
				tc.check(t, executeResp, fmt.Errorf("no response received after reading %d messages", 10))
			}
		})
	}
}

// TestMultiClientBroadcast tests message broadcasting to multiple clients
func TestMultiClientBroadcast(t *testing.T) {
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioMultiMessage).
		WithMessageCount(3)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	server := createTestServer(t, testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Create project first
	ws1, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	require.NoError(t, err)
	defer ws1.Close()

	projectPath := filepath.Join(testDir, "broadcast_test")
	os.MkdirAll(projectPath, 0o755)

	createMsg := models.ClientMessage{
		Type: models.MessageTypeProjectCreate,
		Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
	}
	err = ws1.WriteJSON(createMsg)
	require.NoError(t, err)

	var createResp models.ServerMessage
	err = ws1.ReadJSON(&createResp)
	require.NoError(t, err)

	projectData := createResp.Data.(map[string]interface{})
	projectID := projectData["id"].(string)
	
	// First client needs to join the project too
	joinMsg1 := models.ClientMessage{
		Type: models.MessageTypeProjectJoin,
		Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
	}
	err = ws1.WriteJSON(joinMsg1)
	require.NoError(t, err)
	
	var joinResp1 models.ServerMessage
	err = ws1.ReadJSON(&joinResp1)
	require.NoError(t, err)

	// Connect second client and join project
	ws2, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	require.NoError(t, err)
	defer ws2.Close()

	joinMsg := models.ClientMessage{
		Type: models.MessageTypeProjectJoin,
		Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
	}
	err = ws2.WriteJSON(joinMsg)
	require.NoError(t, err)

	var joinResp models.ServerMessage
	err = ws2.ReadJSON(&joinResp)
	require.NoError(t, err)
	// The join operation might return project_joined or project_state
	if joinResp.Type != models.MessageTypeProjectJoined && joinResp.Type != models.MessageTypeProjectState {
		t.Errorf("Expected project_joined or project_state, got %s", joinResp.Type)
	}

	// Client 1 executes command
	executeMsg := models.ClientMessage{
		Type:      models.MessageTypeExecute,
		ProjectID: projectID,
		Data:      json.RawMessage(`{"prompt": "Broadcast test"}`),
	}
	err = ws1.WriteJSON(executeMsg)
	require.NoError(t, err)

	// Both clients should receive messages
	var wg sync.WaitGroup
	messages1 := make([]models.ServerMessage, 0)
	messages2 := make([]models.ServerMessage, 0)
	var mu sync.Mutex

	// Read messages from client 1
	wg.Add(1)
	go func() {
		defer wg.Done()
		// Read up to 10 messages looking for agent messages
		for i := 0; i < 10; i++ {
			var msg models.ServerMessage
			ws1.SetReadDeadline(time.Now().Add(2 * time.Second))
			if err := ws1.ReadJSON(&msg); err != nil {
				break
			}
			// Only collect relevant messages (not project state updates)
			if msg.Type == models.MessageTypeAgentMessage || 
			   msg.Type == models.MessageTypeError {
				mu.Lock()
				messages1 = append(messages1, msg)
				mu.Unlock()
			}
		}
	}()

	// Read messages from client 2
	wg.Add(1)
	go func() {
		defer wg.Done()
		// Read up to 10 messages looking for agent messages
		for i := 0; i < 10; i++ {
			var msg models.ServerMessage
			ws2.SetReadDeadline(time.Now().Add(2 * time.Second))
			if err := ws2.ReadJSON(&msg); err != nil {
				break
			}
			// Only collect relevant messages (not project state updates)
			if msg.Type == models.MessageTypeAgentMessage || 
			   msg.Type == models.MessageTypeError {
				mu.Lock()
				messages2 = append(messages2, msg)
				mu.Unlock()
			}
		}
	}()

	wg.Wait()

	// Both clients should have received messages
	t.Logf("Client 1 received %d messages", len(messages1))
	t.Logf("Client 2 received %d messages", len(messages2))
	
	// Log the messages for debugging
	for i, msg := range messages1 {
		t.Logf("Client 1 message %d: type=%s", i, msg.Type)
	}
	for i, msg := range messages2 {
		t.Logf("Client 2 message %d: type=%s", i, msg.Type)
	}
	
	assert.Greater(t, len(messages1), 0, "Client 1 should receive messages")
	assert.Greater(t, len(messages2), 0, "Client 2 should receive messages")
}

// TestServerRestart tests that projects persist across server restarts
func TestServerRestart(t *testing.T) {
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(t).WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	dataDir := filepath.Join(testDir, "data")
	projectPath := filepath.Join(testDir, "persistent_project")
	os.MkdirAll(projectPath, 0o755)

	var projectID string
	var sessionID string

	// First server instance
	{
		cfg := &config.Config{
			DataDir: dataDir,
			Port:    0,
			Execution: config.ExecutionConfig{
				ClaudeBinaryPath: claudePath,
				MaxProjects:      100,
			},
		}

		server := createTestServerWithConfig(t, cfg)
		ts := httptest.NewServer(server)

		wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)

		// Create project
		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
		}
		err = ws.WriteJSON(createMsg)
		require.NoError(t, err)

		var createResp models.ServerMessage
		err = ws.ReadJSON(&createResp)
		require.NoError(t, err)

		projectData := createResp.Data.(map[string]interface{})
		projectID = projectData["id"].(string)
		
		// Join the project before executing
		joinMsg := models.ClientMessage{
			Type: models.MessageTypeProjectJoin,
			Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
		}
		err = ws.WriteJSON(joinMsg)
		require.NoError(t, err)
		
		var joinResp models.ServerMessage
		err = ws.ReadJSON(&joinResp)
		require.NoError(t, err)

		// Execute to get session ID
		executeMsg := models.ClientMessage{
			Type:      models.MessageTypeExecute,
			ProjectID: projectID,
			Data:      json.RawMessage(`{"prompt": "Test"}`),
		}
		err = ws.WriteJSON(executeMsg)
		require.NoError(t, err)

		var execResp models.ServerMessage
		err = ws.ReadJSON(&execResp)
		require.NoError(t, err)

		if execData, ok := execResp.Data.(map[string]interface{}); ok {
			if sid, ok := execData["session_id"].(string); ok {
				sessionID = sid
			}
		}

		ws.Close()
		ts.Close()
	}

	// Second server instance
	{
		cfg := &config.Config{
			DataDir: dataDir,
			Port:    0,
			Execution: config.ExecutionConfig{
				ClaudeBinaryPath: claudePath,
				MaxProjects:      100,
			},
		}

		server := createTestServerWithConfig(t, cfg)
		ts := httptest.NewServer(server)
		defer ts.Close()

		wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
		ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer ws.Close()

		// List projects - should find the previous one
		listMsg := models.ClientMessage{
			Type: models.MessageTypeProjectList,
		}
		err = ws.WriteJSON(listMsg)
		require.NoError(t, err)

		var listResp models.ServerMessage
		err = ws.ReadJSON(&listResp)
		require.NoError(t, err)

		respData, ok := listResp.Data.(map[string]interface{})
		require.True(t, ok)
		
		projects, ok := respData["projects"].([]interface{})
		require.True(t, ok)
		require.Greater(t, len(projects), 0)

		// Find our project
		found := false
		for _, p := range projects {
			proj := p.(map[string]interface{})
			if proj["id"] == projectID {
				found = true
				// Session ID should be preserved
				if sid, ok := proj["session_id"].(string); ok && sid != "" {
					assert.Equal(t, sessionID, sid)
				}
				break
			}
		}
		assert.True(t, found, "Project should persist across restart")
	}
}

// TestConcurrentOperations tests concurrent access and race conditions
func TestConcurrentOperations(t *testing.T) {
	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioSuccess).
		WithDelay(50 * time.Millisecond) // Add delay to increase chance of races
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	server := createTestServer(t, testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Create multiple projects concurrently
	numProjects := 10
	var wg sync.WaitGroup
	projectIDs := make([]string, 0, numProjects)
	var mu sync.Mutex

	for i := 0; i < numProjects; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()

			ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
			if err != nil {
				t.Logf("Failed to connect: %v", err)
				return
			}
			defer ws.Close()

			projectPath := filepath.Join(testDir, fmt.Sprintf("concurrent_project_%d", idx))
			os.MkdirAll(projectPath, 0o755)

			createMsg := models.ClientMessage{
				Type: models.MessageTypeProjectCreate,
				Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
			}

			if err := ws.WriteJSON(createMsg); err != nil {
				t.Logf("Failed to send create message: %v", err)
				return
			}

			var resp models.ServerMessage
			if err := ws.ReadJSON(&resp); err != nil {
				t.Logf("Failed to read response: %v", err)
				return
			}

			if resp.Type == models.MessageTypeProjectState {
				if data, ok := resp.Data.(map[string]interface{}); ok {
					if id, ok := data["id"].(string); ok {
						mu.Lock()
						projectIDs = append(projectIDs, id)
						mu.Unlock()
					}
				}
			}
		}(i)
	}

	wg.Wait()

	// All projects should be created
	assert.Equal(t, numProjects, len(projectIDs))

	// Test concurrent executions on same project
	if len(projectIDs) > 0 {
		targetProjectID := projectIDs[0]
		numClients := 5

		for i := 0; i < numClients; i++ {
			wg.Add(1)
			go func(idx int) {
				defer wg.Done()

				ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
				if err != nil {
					return
				}
				defer ws.Close()

				// Join project
				joinMsg := models.ClientMessage{
					Type: models.MessageTypeProjectJoin,
					Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, targetProjectID)),
				}
				ws.WriteJSON(joinMsg)

				// Try to execute concurrently
				executeMsg := models.ClientMessage{
					Type:      models.MessageTypeExecute,
					ProjectID: targetProjectID,
					Data:      json.RawMessage(fmt.Sprintf(`{"prompt": "Concurrent test %d"}`, idx)),
				}
				ws.WriteJSON(executeMsg)

				// Read response
				ws.SetReadDeadline(time.Now().Add(5 * time.Second))
				var resp models.ServerMessage
				ws.ReadJSON(&resp)
			}(i)
		}

		wg.Wait()
	}
}

// Helper functions

func setupTestEnvironment(t *testing.T) string {
	tempDir, err := os.MkdirTemp("", "websocket_test_*")
	require.NoError(t, err)
	return tempDir
}

func createTestServer(t *testing.T, dataDir, claudePath string) http.Handler {
	cfg := &config.Config{
		DataDir: filepath.Join(dataDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   5 * time.Second,
			MaxProjects:      100,
		},
	}
	return createTestServerWithConfig(t, cfg)
}

func createTestServerWithConfig(t *testing.T, cfg *config.Config) http.Handler {
	// Create logger
	log := logger.New("debug")

	// Create components
	projectMgrCfg := project.Config{
		DataDir:     cfg.DataDir,
		MaxProjects: cfg.Execution.MaxProjects,
	}
	projectMgr, err := project.NewManager(projectMgrCfg)
	require.NoError(t, err)

	executorCfg := executor.Config{
		ClaudePath:              cfg.Execution.ClaudeBinaryPath,
		DefaultTimeout:          cfg.Execution.CommandTimeout.Get(),
		MaxConcurrentExecutions: 10,
	}
	claudeExec, err := executor.NewClaudeExecutor(executorCfg)
	require.NoError(t, err)

	// Create handlers
	handlerCfg := handlers.Config{
		ProjectManager:  projectMgr,
		Executor:        claudeExec,
		Logger:          log,
		ClaudePath:      cfg.Execution.ClaudeBinaryPath,
		DataDir:         cfg.DataDir,
		BroadcastConfig: handlers.BroadcasterConfig{
			WriteTimeout:       10 * time.Second,
			BroadcastBuffer:    100,
			SlowClientDeadline: 5 * time.Second,
		},
	}
	
	// Create server stats mock
	serverStats := &mockServerStats{}
	
	// Create all handlers
	allHandlers := handlers.NewHandlers(handlerCfg, serverStats)
	
	// Create WebSocket server config
	wsCfg := ws.Config{
		ReadTimeout:         10 * time.Minute,
		WriteTimeout:        10 * time.Second,
		PingInterval:        5 * time.Minute,
		PongTimeout:         30 * time.Second,
		MaxMessageSize:      1024 * 1024,
		BufferSize:          1024,
		MaxConnections:      100,
		MaxConnectionsPerIP: 10, // Allow multiple connections from same IP in tests
		RateLimitPerIP:      60, // 60 connections per minute per IP
		AllowedOrigins:      []string{"*"},
		ConnectionTimeout:   5 * time.Minute,
	}
	
	// Create server with handlers as the message handler
	wsServer := ws.NewServer(wsCfg, allHandlers, log)

	// Create HTTP mux
	mux := http.NewServeMux()
	mux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		log.Debug("WebSocket request received", "method", r.Method, "url", r.URL.String())
		wsServer.HandleWebSocket(w, r)
	})

	return mux
}

// mockServerStats implements handlers.ServerStats interface
type mockServerStats struct{}

func (m *mockServerStats) GetMetrics() map[string]interface{} {
	return map[string]interface{}{
		"active_connections": 0,
		"total_messages":     int64(0),
		"uptime":             time.Duration(0),
	}
}
