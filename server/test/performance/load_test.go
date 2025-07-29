package performance

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/config"
	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
	"github.com/boyd/pocket_agent/server/internal/websocket/handlers"
	"github.com/boyd/pocket_agent/server/test/mocks"
	ws "github.com/gorilla/websocket"
	"github.com/stretchr/testify/require"
)

// TestConcurrentConnections tests handling 100+ concurrent WebSocket connections
func TestConcurrentConnections(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping load test in short mode")
	}

	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	// Create fast-responding mock
	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioSuccess).
		WithDelay(10 * time.Millisecond)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	// Configure server for high load
	cfg := &config.Config{
		DataDir: filepath.Join(testDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   30 * time.Second,
			MaxProjects:      200,
		},
	}

	server := createLoadTestServer(t, cfg)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Test parameters
	numConnections := 120 // Test with 120 concurrent connections
	connectTimeout := 30 * time.Second

	// Metrics
	var successfulConnects int64
	var failedConnects int64
	var totalMessages int64
	startTime := time.Now()

	// Create connections concurrently
	var wg sync.WaitGroup
	connections := make([]*ws.Conn, 0, numConnections)
	var connMu sync.Mutex

	t.Logf("Starting %d concurrent connections...", numConnections)

	for i := 0; i < numConnections; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()

			ctx, cancel := context.WithTimeout(context.Background(), connectTimeout)
			defer cancel()

			dialer := ws.Dialer{
				HandshakeTimeout: 10 * time.Second,
			}

			conn, _, err := dialer.DialContext(ctx, wsURL, nil)
			if err != nil {
				atomic.AddInt64(&failedConnects, 1)
				t.Logf("Connection %d failed: %v", id, err)
				return
			}

			atomic.AddInt64(&successfulConnects, 1)

			connMu.Lock()
			connections = append(connections, conn)
			connMu.Unlock()

			// Send a test message
			msg := models.ClientMessage{
				Type: models.MessageTypeProjectList,
			}

			if err := conn.WriteJSON(msg); err == nil {
				atomic.AddInt64(&totalMessages, 1)
			}
		}(i)

		// Small delay to avoid thundering herd
		if i%10 == 0 {
			time.Sleep(10 * time.Millisecond)
		}
	}

	// Wait for all connections with timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Success
	case <-time.After(connectTimeout):
		t.Fatal("Timeout waiting for connections")
	}

	connectDuration := time.Since(startTime)

	// Report results
	t.Logf("Connection Results:")
	t.Logf("  Successful: %d", atomic.LoadInt64(&successfulConnects))
	t.Logf("  Failed: %d", atomic.LoadInt64(&failedConnects))
	t.Logf("  Total Time: %v", connectDuration)
	t.Logf("  Connections/sec: %.2f", float64(successfulConnects)/connectDuration.Seconds())

	// Verify we achieved target
	require.GreaterOrEqual(t, atomic.LoadInt64(&successfulConnects), int64(100),
		"Should support at least 100 concurrent connections")

	// Cleanup connections
	for _, conn := range connections {
		conn.Close()
	}
}

// TestMessageThroughput tests handling 1000+ messages per second
func TestMessageThroughput(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping throughput test in short mode")
	}

	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	// Create instant-response mock
	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioSuccess).
		WithDelay(0) // No delay for throughput test
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	cfg := &config.Config{
		DataDir: filepath.Join(testDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   30 * time.Second,
			MaxProjects:      100,
		},
	}

	server := createLoadTestServer(t, cfg)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Create persistent connections
	numConnections := 10
	connections := make([]*ws.Conn, 0, numConnections)

	for i := 0; i < numConnections; i++ {
		conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()
		connections = append(connections, conn)
	}

	// Metrics
	var messagesSent int64
	var messagesReceived int64
	var errors int64

	// Test duration
	testDuration := 5 * time.Second
	messagesPerConnection := 200 // Each connection sends this many messages

	ctx, cancel := context.WithTimeout(context.Background(), testDuration)
	defer cancel()

	startTime := time.Now()
	var wg sync.WaitGroup

	t.Logf("Starting throughput test with %d connections...", numConnections)

	// Each connection sends messages continuously
	for i, conn := range connections {
		wg.Add(1)
		go func(connID int, c *ws.Conn) {
			defer wg.Done()

			// Reader goroutine
			go func() {
				for {
					var resp models.ServerMessage
					c.SetReadDeadline(time.Now().Add(time.Second))
					if err := c.ReadJSON(&resp); err != nil {
						if ctx.Err() != nil {
							return // Context cancelled
						}
						continue
					}
					atomic.AddInt64(&messagesReceived, 1)
				}
			}()

			// Writer goroutine
			for j := 0; j < messagesPerConnection; j++ {
				select {
				case <-ctx.Done():
					return
				default:
					msg := models.ClientMessage{
						Type: models.MessageTypeProjectList,
					}

					err := c.WriteJSON(msg)
					if err != nil {
						atomic.AddInt64(&errors, 1)
						continue
					}

					atomic.AddInt64(&messagesSent, 1)

					// Small delay between messages to avoid overwhelming
					time.Sleep(5 * time.Millisecond)
				}
			}
		}(i, conn)
	}

	// Wait for completion
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Completed
	case <-ctx.Done():
		// Timeout
	}

	duration := time.Since(startTime)

	// Calculate throughput
	sentPerSecond := float64(atomic.LoadInt64(&messagesSent)) / duration.Seconds()
	receivedPerSecond := float64(atomic.LoadInt64(&messagesReceived)) / duration.Seconds()

	// Report results
	t.Logf("Throughput Results:")
	t.Logf("  Duration: %v", duration)
	t.Logf("  Messages Sent: %d (%.2f/sec)", messagesSent, sentPerSecond)
	t.Logf("  Messages Received: %d (%.2f/sec)", messagesReceived, receivedPerSecond)
	t.Logf("  Errors: %d", errors)

	// Verify throughput
	require.GreaterOrEqual(t, sentPerSecond, 1000.0,
		"Should handle at least 1000 messages per second")
}

// TestResourceLimits tests server behavior at resource limits
func TestResourceLimits(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping resource limit test in short mode")
	}

	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(t).WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	// Configure with low limits for testing
	cfg := &config.Config{
		DataDir: filepath.Join(testDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   30 * time.Second,
			MaxProjects:      5, // Low limit
		},
	}

	server := createLoadTestServer(t, cfg)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	t.Run("Connection Limit", func(t *testing.T) {
		connections := make([]*ws.Conn, 0)
		defer func() {
			for _, c := range connections {
				c.Close()
			}
		}()

		// Try to exceed connection limit
		maxConnections := 10 // Default test limit
		for i := 0; i < maxConnections+5; i++ {
			conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
			if err != nil {
				// Expected to fail after limit
				t.Logf("Connection %d rejected (expected): %v", i+1, err)
				break
			}
			connections = append(connections, conn)
		}

		// Should have reasonable connection count
		require.LessOrEqual(t, len(connections), maxConnections+2,
			"Should enforce reasonable connection limit")
	})

	t.Run("Project Limit", func(t *testing.T) {
		conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()

		successfulProjects := 0

		// Try to create more projects than limit
		for i := 0; i < cfg.Execution.MaxProjects+5; i++ {
			projectPath := filepath.Join(testDir, fmt.Sprintf("project_%d", i))
			os.MkdirAll(projectPath, 0o755)

			msg := models.ClientMessage{
				Type: models.MessageTypeProjectCreate,
				Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
			}

			err := conn.WriteJSON(msg)
			require.NoError(t, err)

			var resp models.ServerMessage
			err = conn.ReadJSON(&resp)
			require.NoError(t, err)

			if resp.Type == models.MessageTypeProjectState {
				successfulProjects++
			} else if resp.Type == models.MessageTypeError {
				t.Logf("Project %d rejected (expected): %v", i+1, resp.Data)
				break
			}
		}

		// Should have exactly MaxProjects
		require.LessOrEqual(t, successfulProjects, cfg.Execution.MaxProjects,
			"Should enforce project count limit")
	})
}

// TestMemoryUsage monitors memory usage under load
func TestMemoryUsage(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping memory test in short mode")
	}

	testDir := setupTestEnvironment(t)
	defer os.RemoveAll(testDir)

	// Create mock that generates large responses
	mock := mocks.NewClaudeMockExecutable(t).
		WithScenario(mocks.ScenarioLongResponse)
	claudePath := mock.MustCreate(t)
	defer mock.Cleanup()

	cfg := &config.Config{
		DataDir: filepath.Join(testDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   30 * time.Second,
			MaxProjects:      50,
		},
	}

	server := createLoadTestServer(t, cfg)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Get initial memory stats
	var initialMem runtime.MemStats
	runtime.GC()
	runtime.ReadMemStats(&initialMem)

	// Create connections and projects
	numOperations := 20
	connections := make([]*ws.Conn, 0, numOperations)

	for i := 0; i < numOperations; i++ {
		conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
		require.NoError(t, err)
		defer conn.Close()
		connections = append(connections, conn)

		// Create project
		projectPath := filepath.Join(testDir, fmt.Sprintf("mem_test_%d", i))
		os.MkdirAll(projectPath, 0o755)

		createMsg := models.ClientMessage{
			Type: models.MessageTypeProjectCreate,
			Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
		}
		conn.WriteJSON(createMsg)

		var resp models.ServerMessage
		conn.ReadJSON(&resp)
	}

	// Execute commands to generate memory load
	var wg sync.WaitGroup
	for i, conn := range connections {
		wg.Add(1)
		go func(idx int, c *ws.Conn) {
			defer wg.Done()

			// Get project list to find our project
			listMsg := models.ClientMessage{
				Type: models.MessageTypeProjectList,
			}
			c.WriteJSON(listMsg)

			var listResp models.ServerMessage
			if err := c.ReadJSON(&listResp); err != nil {
				return
			}

			// Execute command (will generate large response)
			if projects, ok := listResp.Data.([]interface{}); ok && len(projects) > 0 {
				if proj, ok := projects[0].(map[string]interface{}); ok {
					if projectID, ok := proj["id"].(string); ok {
						execMsg := models.ClientMessage{
							Type:      models.MessageTypeExecute,
							ProjectID: projectID,
							Data:      json.RawMessage(`{"prompt": "Generate large response"}`),
						}
						c.WriteJSON(execMsg)

						// Read response
						var execResp models.ServerMessage
						c.ReadJSON(&execResp)
					}
				}
			}
		}(i, conn)
	}

	wg.Wait()

	// Get final memory stats
	var finalMem runtime.MemStats
	runtime.GC()
	runtime.ReadMemStats(&finalMem)

	// Calculate memory growth
	heapGrowth := int64(finalMem.HeapAlloc) - int64(initialMem.HeapAlloc)
	heapGrowthMB := float64(heapGrowth) / (1024 * 1024)

	t.Logf("Memory Usage:")
	t.Logf("  Initial Heap: %.2f MB", float64(initialMem.HeapAlloc)/(1024*1024))
	t.Logf("  Final Heap: %.2f MB", float64(finalMem.HeapAlloc)/(1024*1024))
	t.Logf("  Heap Growth: %.2f MB", heapGrowthMB)
	t.Logf("  Goroutines: %d", runtime.NumGoroutine())

	// Verify reasonable memory usage
	require.Less(t, heapGrowthMB, 500.0,
		"Memory growth should be reasonable (< 500MB)")
}

// Benchmark tests

func BenchmarkWebSocketConnection(b *testing.B) {
	testDir := setupBenchEnvironment(b)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(&testing.T{}).
		WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(&testing.T{})
	defer mock.Cleanup()

	server := createBenchServer(testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
			if err != nil {
				b.Fatal(err)
			}
			conn.Close()
		}
	})
}

func BenchmarkMessageRouting(b *testing.B) {
	testDir := setupBenchEnvironment(b)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(&testing.T{}).
		WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(&testing.T{})
	defer mock.Cleanup()

	server := createBenchServer(testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	conn, _, err := ws.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		b.Fatal(err)
	}
	defer conn.Close()

	msg := models.ClientMessage{
		Type: models.MessageTypeProjectList,
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if err := conn.WriteJSON(msg); err != nil {
			b.Fatal(err)
		}

		var resp models.ServerMessage
		if err := conn.ReadJSON(&resp); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkConcurrentBroadcast(b *testing.B) {
	testDir := setupBenchEnvironment(b)
	defer os.RemoveAll(testDir)

	mock := mocks.NewClaudeMockExecutable(&testing.T{}).
		WithScenario(mocks.ScenarioSuccess)
	claudePath := mock.MustCreate(&testing.T{})
	defer mock.Cleanup()

	server := createBenchServer(testDir, claudePath)
	ts := httptest.NewServer(server)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	// Create a project first
	conn, _, _ := ws.DefaultDialer.Dial(wsURL, nil)
	projectPath := filepath.Join(testDir, "bench_project")
	os.MkdirAll(projectPath, 0o755)

	createMsg := models.ClientMessage{
		Type: models.MessageTypeProjectCreate,
		Data: json.RawMessage(fmt.Sprintf(`{"path": "%s"}`, projectPath)),
	}
	conn.WriteJSON(createMsg)

	var createResp models.ServerMessage
	conn.ReadJSON(&createResp)
	projectData := createResp.Data.(map[string]interface{})
	projectID := projectData["id"].(string)
	conn.Close()

	// Create multiple subscribers
	numSubscribers := 10
	subscribers := make([]*ws.Conn, 0, numSubscribers)

	for i := 0; i < numSubscribers; i++ {
		subConn, _, _ := ws.DefaultDialer.Dial(wsURL, nil)
		joinMsg := models.ClientMessage{
			Type: models.MessageTypeProjectJoin,
			Data: json.RawMessage(fmt.Sprintf(`{"project_id": "%s"}`, projectID)),
		}
		subConn.WriteJSON(joinMsg)
		var joinResp models.ServerMessage
		subConn.ReadJSON(&joinResp)
		subscribers = append(subscribers, subConn)
	}

	defer func() {
		for _, s := range subscribers {
			s.Close()
		}
	}()

	// Publisher connection
	pubConn, _, _ := ws.DefaultDialer.Dial(wsURL, nil)
	defer pubConn.Close()

	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			// Send update
			updateMsg := models.ClientMessage{
				Type:      models.MessageTypeExecute,
				ProjectID: projectID,
				Data:      json.RawMessage(`{"prompt": "bench test"}`),
			}
			pubConn.WriteJSON(updateMsg)
		}
	})
}

// Helper functions

func setupTestEnvironment(t *testing.T) string {
	tempDir, err := os.MkdirTemp("", "load_test_*")
	require.NoError(t, err)
	return tempDir
}

func setupBenchEnvironment(b *testing.B) string {
	tempDir, err := os.MkdirTemp("", "bench_*")
	if err != nil {
		b.Fatal(err)
	}
	return tempDir
}

func createLoadTestServer(t *testing.T, cfg *config.Config) http.Handler {
	log := logger.New("error") // Less verbose for load tests

	// Create project manager
	projectMgrCfg := project.Config{
		DataDir:     cfg.DataDir,
		MaxProjects: cfg.Execution.MaxProjects,
	}
	projectMgr, err := project.NewManager(projectMgrCfg)
	require.NoError(t, err)
	
	// Create executor
	executorCfg := executor.Config{
		ClaudePath:              cfg.Execution.ClaudeBinaryPath,
		DefaultTimeout:          cfg.Execution.CommandTimeout,
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
	}
	
	// Create server stats mock
	serverStats := &mockServerStats{}
	
	// Create all handlers
	allHandlers := handlers.NewHandlers(handlerCfg, serverStats)
	
	// Create WebSocket server
	wsCfg := websocket.Config{
		ReadTimeout:     10 * time.Minute,
		WriteTimeout:    10 * time.Second,
		PingInterval:    5 * time.Minute,
		PongTimeout:     30 * time.Second,
		MaxMessageSize:  1024 * 1024,
		BufferSize:      1024,
		MaxConnections:  1000,
	}
	
	wsServer := websocket.NewServer(wsCfg, allHandlers, log)

	mux := http.NewServeMux()
	mux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		_, err := wsServer.HandleUpgrade(w, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
	})

	return mux
}

func createBenchServer(dataDir, claudePath string) http.Handler {
	cfg := &config.Config{
		DataDir: filepath.Join(dataDir, "data"),
		Port:    0,
		Execution: config.ExecutionConfig{
			ClaudeBinaryPath: claudePath,
			CommandTimeout:   30 * time.Second,
			MaxProjects:      1000,
		},
	}

	log := logger.New("error")
	
	// Create project manager
	projectMgrCfg := project.Config{
		DataDir:     cfg.DataDir,
		MaxProjects: cfg.Execution.MaxProjects,
	}
	projectMgr, _ := project.NewManager(projectMgrCfg)
	
	// Create executor
	executorCfg := executor.Config{
		ClaudePath:              cfg.Execution.ClaudeBinaryPath,
		DefaultTimeout:          cfg.Execution.CommandTimeout,
		MaxConcurrentExecutions: 10,
	}
	claudeExec, _ := executor.NewClaudeExecutor(executorCfg)
	
	// Create handlers
	handlerCfg := handlers.Config{
		ProjectManager:  projectMgr,
		Executor:        claudeExec,
		Logger:          log,
		ClaudePath:      cfg.Execution.ClaudeBinaryPath,
		DataDir:         cfg.DataDir,
	}
	
	// Create server stats mock
	serverStats := &mockServerStats{}
	
	// Create all handlers
	allHandlers := handlers.NewHandlers(handlerCfg, serverStats)
	
	// Create WebSocket server
	wsCfg := websocket.Config{
		ReadTimeout:     10 * time.Minute,
		WriteTimeout:    10 * time.Second,
		PingInterval:    5 * time.Minute,
		PongTimeout:     30 * time.Second,
		MaxMessageSize:  1024 * 1024,
		BufferSize:      1024,
		MaxConnections:  1000,
	}
	
	wsServer := websocket.NewServer(wsCfg, allHandlers, log)

	mux := http.NewServeMux()
	mux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		_, err := wsServer.HandleUpgrade(w, r)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
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
