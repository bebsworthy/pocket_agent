package executor

import (
	"context"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
)

func TestNewClaudeExecutor(t *testing.T) {
	// Create a mock claude executable
	tempDir, err := os.MkdirTemp("", "executor_test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	mockClaude := filepath.Join(tempDir, "claude")
	if err := os.WriteFile(mockClaude, []byte("#!/bin/sh\necho mock"), 0755); err != nil {
		t.Fatal(err)
	}

	// Add temp dir to PATH for testing
	oldPath := os.Getenv("PATH")
	os.Setenv("PATH", tempDir+":"+oldPath)
	defer os.Setenv("PATH", oldPath)

	tests := []struct {
		name    string
		config  Config
		wantErr bool
		check   func(*ClaudeExecutor)
	}{
		{
			name:    "default config",
			config:  DefaultConfig(),
			wantErr: false,
			check: func(ce *ClaudeExecutor) {
				if ce.config.DefaultTimeout != 5*time.Minute {
					t.Errorf("expected default timeout 5m, got %v", ce.config.DefaultTimeout)
				}
				if ce.config.MaxConcurrentExecutions != 10 {
					t.Errorf("expected max concurrent 10, got %d", ce.config.MaxConcurrentExecutions)
				}
			},
		},
		{
			name: "custom config",
			config: Config{
				ClaudePath:              mockClaude,
				DefaultTimeout:          10 * time.Minute,
				MaxConcurrentExecutions: 5,
			},
			wantErr: false,
			check: func(ce *ClaudeExecutor) {
				if ce.config.DefaultTimeout != 10*time.Minute {
					t.Errorf("expected timeout 10m, got %v", ce.config.DefaultTimeout)
				}
				if ce.config.MaxConcurrentExecutions != 5 {
					t.Errorf("expected max concurrent 5, got %d", ce.config.MaxConcurrentExecutions)
				}
			},
		},
		{
			name: "claude not found",
			config: Config{
				ClaudePath: "/does/not/exist/claude",
			},
			wantErr: true,
		},
		{
			name: "zero values get defaults",
			config: Config{
				ClaudePath:              mockClaude,
				DefaultTimeout:          0,
				MaxConcurrentExecutions: 0,
			},
			wantErr: false,
			check: func(ce *ClaudeExecutor) {
				if ce.config.DefaultTimeout != 5*time.Minute {
					t.Errorf("expected default timeout 5m, got %v", ce.config.DefaultTimeout)
				}
				if ce.config.MaxConcurrentExecutions != 10 {
					t.Errorf("expected max concurrent 10, got %d", ce.config.MaxConcurrentExecutions)
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ce, err := NewClaudeExecutor(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewClaudeExecutor() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err == nil && tt.check != nil {
				tt.check(ce)
			}

			if err != nil && tt.wantErr {
				appErr, ok := err.(*errors.AppError)
				if ok && appErr.Code != errors.CodeClaudeNotFound {
					t.Errorf("expected CodeClaudeNotFound, got %s", appErr.Code)
				}
			}
		})
	}
}

func TestProcessTracking(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			MaxConcurrentExecutions: 2,
		},
	}

	// Test registering a process
	info1 := &ProcessInfo{
		ProjectID: "project-1",
		StartTime: time.Now(),
	}

	err := ce.registerProcess("project-1", info1)
	if err != nil {
		t.Errorf("registerProcess() unexpected error: %v", err)
	}

	// Test duplicate registration
	err = ce.registerProcess("project-1", info1)
	if err == nil {
		t.Error("expected error for duplicate registration")
	}
	appErr, _ := err.(*errors.AppError)
	if appErr.Code != errors.CodeProcessActive {
		t.Errorf("expected CodeProcessActive, got %s", appErr.Code)
	}

	// Test max concurrent limit
	info2 := &ProcessInfo{ProjectID: "project-2"}
	ce.registerProcess("project-2", info2)

	info3 := &ProcessInfo{ProjectID: "project-3"}
	err = ce.registerProcess("project-3", info3)
	if err == nil {
		t.Error("expected error for exceeding concurrent limit")
	}
	appErr, _ = err.(*errors.AppError)
	if appErr.Code != errors.CodeResourceLimit {
		t.Errorf("expected CodeResourceLimit, got %s", appErr.Code)
	}

	// Test unregistering
	ce.unregisterProcess("project-1")
	if _, exists := ce.activeProcesses["project-1"]; exists {
		t.Error("process was not unregistered")
	}

	// Test getting process
	proc, err := ce.getProcess("project-2")
	if err != nil {
		t.Errorf("getProcess() unexpected error: %v", err)
	}
	if proc != info2 {
		t.Error("getProcess returned wrong info")
	}

	// Test getting non-existent process
	_, err = ce.getProcess("non-existent")
	if err == nil {
		t.Error("expected error for non-existent process")
	}
}

func TestGetActiveProcessCount(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
	}

	if count := ce.GetActiveProcessCount(); count != 0 {
		t.Errorf("expected 0 active processes, got %d", count)
	}

	ce.activeProcesses["p1"] = &ProcessInfo{}
	ce.activeProcesses["p2"] = &ProcessInfo{}

	if count := ce.GetActiveProcessCount(); count != 2 {
		t.Errorf("expected 2 active processes, got %d", count)
	}
}

func TestIsProjectExecuting(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
	}

	if ce.IsProjectExecuting("project-1") {
		t.Error("expected project to not be executing")
	}

	ce.activeProcesses["project-1"] = &ProcessInfo{}

	if !ce.IsProjectExecuting("project-1") {
		t.Error("expected project to be executing")
	}
}

func TestCreateTimeoutContext(t *testing.T) {
	ce := &ClaudeExecutor{
		config: Config{
			DefaultTimeout: 5 * time.Second,
		},
	}

	// Test with custom timeout
	ctx, cancel := ce.createTimeoutContext(2 * time.Second)
	defer cancel()

	deadline, ok := ctx.Deadline()
	if !ok {
		t.Error("expected context to have deadline")
	}

	timeUntilDeadline := time.Until(deadline)
	if timeUntilDeadline > 2*time.Second || timeUntilDeadline < 1*time.Second {
		t.Errorf("unexpected deadline: %v", timeUntilDeadline)
	}

	// Test with zero timeout (should use default)
	ctx2, cancel2 := ce.createTimeoutContext(0)
	defer cancel2()

	deadline2, _ := ctx2.Deadline()
	timeUntilDeadline2 := time.Until(deadline2)
	if timeUntilDeadline2 > 5*time.Second || timeUntilDeadline2 < 4*time.Second {
		t.Errorf("expected default timeout, got: %v", timeUntilDeadline2)
	}
}

func TestCleanupProcess(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	ctx, cancel := context.WithCancel(context.Background())
	info := &ProcessInfo{
		ProjectID: "test-project",
		StartTime: time.Now(),
		Cancel:    cancel,
	}

	ce.activeProcesses["test-project"] = info

	// Cleanup should cancel context and unregister
	ce.cleanupProcess("test-project", info)

	// Check context was cancelled
	select {
	case <-ctx.Done():
		// Success
	default:
		t.Error("expected context to be cancelled")
	}

	// Check process was unregistered
	if _, exists := ce.activeProcesses["test-project"]; exists {
		t.Error("process was not unregistered during cleanup")
	}
}

func TestGetStats(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			MaxConcurrentExecutions: 10,
			DefaultTimeout:          5 * time.Minute,
		},
	}

	ce.activeProcesses["p1"] = &ProcessInfo{ProjectID: "p1"}
	ce.activeProcesses["p2"] = &ProcessInfo{ProjectID: "p2"}

	stats := ce.GetStats()

	if active, ok := stats["active_processes"].(int); !ok || active != 2 {
		t.Errorf("expected active_processes 2, got %v", stats["active_processes"])
	}

	if max, ok := stats["max_concurrent_executions"].(int); !ok || max != 10 {
		t.Errorf("expected max_concurrent_executions 10, got %v", stats["max_concurrent_executions"])
	}

	if timeout, ok := stats["default_timeout"].(string); !ok || timeout != "5m0s" {
		t.Errorf("expected default_timeout 5m0s, got %v", stats["default_timeout"])
	}

	projects, ok := stats["active_projects"].([]string)
	if !ok || len(projects) != 2 {
		t.Errorf("expected 2 active projects, got %v", stats["active_projects"])
	}
}

func TestShutdown(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Add some mock processes
	ctx1, cancel1 := context.WithCancel(context.Background())
	ctx2, cancel2 := context.WithCancel(context.Background())

	ce.activeProcesses["p1"] = &ProcessInfo{Cancel: cancel1}
	ce.activeProcesses["p2"] = &ProcessInfo{Cancel: cancel2}

	// Start a goroutine to clear processes after contexts are cancelled
	go func() {
		<-ctx1.Done()
		<-ctx2.Done()
		ce.activeProcesses = make(map[string]*ProcessInfo)
	}()

	// Shutdown with timeout
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	err := ce.Shutdown(shutdownCtx)
	if err != nil {
		t.Errorf("Shutdown() unexpected error: %v", err)
	}

	// Verify all processes were cleaned up
	if len(ce.activeProcesses) != 0 {
		t.Errorf("expected 0 active processes after shutdown, got %d", len(ce.activeProcesses))
	}
}

func TestShutdownTimeout(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Add a process that won't terminate
	ce.activeProcesses["stuck"] = &ProcessInfo{
		Cancel: func() {}, // No-op cancel
	}

	// Shutdown with very short timeout
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
	defer cancel()

	err := ce.Shutdown(shutdownCtx)
	if err == nil {
		t.Error("expected timeout error")
	}
	if err.Error() != "shutdown timeout: 1 processes still active" {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestConcurrentOperations(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			MaxConcurrentExecutions: 100,
		},
		logger: logger.New("info"),
	}

	var wg sync.WaitGroup

	// Concurrent registrations
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			projectID := string(rune('a' + id))
			info := &ProcessInfo{
				ProjectID: projectID,
				StartTime: time.Now(),
			}
			ce.registerProcess(projectID, info)
		}(i)
	}

	// Concurrent reads
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			ce.GetActiveProcessCount()
			ce.GetStats()
		}()
	}

	// Concurrent unregistrations
	for i := 0; i < 25; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			time.Sleep(10 * time.Millisecond)
			projectID := string(rune('a' + id))
			ce.unregisterProcess(projectID)
		}(i)
	}

	wg.Wait()

	// Verify state consistency
	count := ce.GetActiveProcessCount()
	if count != 25 {
		t.Errorf("expected 25 active processes after concurrent ops, got %d", count)
	}
}