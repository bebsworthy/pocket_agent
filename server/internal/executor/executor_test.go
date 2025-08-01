package executor

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
)

func TestNewClaudeExecutor(t *testing.T) {
	// Create a mock claude executable
	tempDir, err := os.MkdirTemp("", "executor_test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	mockClaude := filepath.Join(tempDir, "claude")
	if err := os.WriteFile(mockClaude, []byte("#!/bin/sh\necho mock"), 0o755); err != nil {
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
	// Create mock CLI that simulates real Claude behavior
	mockCLI := createAdvancedMockCLI(t)
	defer os.RemoveAll(filepath.Dir(mockCLI))

	// Setup executor with real components and proper logger
	config := Config{
		ClaudePath:              mockCLI,
		MaxConcurrentExecutions: 5, // Increase to avoid conflicts between tests
		DefaultTimeout:          5 * time.Second,
	}

	executor, err := NewClaudeExecutor(config)
	if err != nil {
		t.Fatal(err)
	}

	// Set logger if needed
	if executor.logger == nil {
		executor.logger = logger.New("debug")
	}

	t.Run("real process lifecycle with streaming output", func(t *testing.T) {
		// Create test project directory
		projectPath, err := os.MkdirTemp("", "test-project")
		if err != nil {
			t.Fatal(err)
		}
		defer os.RemoveAll(projectPath)

		// Create test file in project
		testFile := filepath.Join(projectPath, "test.go")
		os.WriteFile(testFile, []byte(`package main
func main() {
	println("Hello")
}`), 0o644)

		// Execute command with real streaming
		cmd := ExecuteCommand{
			Prompt: "analyze test.go file",
		}

		// Track output messages
		outputReceived := make([]string, 0)
		var outputMu sync.Mutex

		// Create project with mock models.Project
		project := &models.Project{
			ID:   "project-1",
			Path: projectPath,
		}

		// Start execution with goroutine to capture streaming output
		result := make(chan struct {
			Result *ExecuteResult
			Error  error
		}, 1)

		go func() {
			res, err := executor.Execute(project, cmd)
			result <- struct {
				Result *ExecuteResult
				Error  error
			}{Result: res, Error: err}

			// Capture output if available
			if res != nil && res.Stdout != "" {
				outputMu.Lock()
				outputReceived = append(outputReceived, res.Stdout)
				outputMu.Unlock()
			}
		}()

		// Allow process to start
		time.Sleep(200 * time.Millisecond)

		// Verify process is registered with correct project ID
		if !executor.IsProjectExecuting("project-1") {
			t.Error("expected project to be executing")
		}

		// Check active process info
		stats := executor.GetStats()
		activeProjects, ok := stats["active_projects"].([]string)
		if !ok || len(activeProjects) != 1 || activeProjects[0] != "project-1" {
			t.Errorf("expected active_projects to contain project-1, got %v", stats["active_projects"])
		}

		// Wait for completion
		res := <-result
		if res.Error != nil {
			t.Errorf("unexpected error: %v", res.Error)
		}

		// Verify result
		if res.Result == nil {
			t.Error("expected non-nil result")
		} else {
			if res.Result.SessionID == "" {
				t.Error("expected session ID in result")
			}
			if res.Result.Stdout == "" && len(res.Result.Messages) == 0 {
				t.Error("expected output or messages in result")
			}
			if res.Result.ExitCode != 0 {
				t.Errorf("expected exit code 0, got %d", res.Result.ExitCode)
			}
		}

		// Verify process was cleaned up
		if executor.IsProjectExecuting("project-1") {
			t.Error("expected process to be cleaned up after completion")
		}

		// Verify final state
		if count := executor.GetActiveProcessCount(); count != 0 {
			t.Errorf("expected 0 active processes after cleanup, got %d", count)
		}
	})

	t.Run("real concurrent execution with resource limits", func(t *testing.T) {
		// Create a separate executor with limit of 2 for this test
		testConfig := Config{
			ClaudePath:              mockCLI,
			MaxConcurrentExecutions: 2, // Test limit
			DefaultTimeout:          5 * time.Second,
		}

		testExecutor, err := NewClaudeExecutor(testConfig)
		if err != nil {
			t.Fatal(err)
		}

		// Create test project directories
		projects := make([]*models.Project, 3)
		for i := 0; i < 3; i++ {
			path, err := os.MkdirTemp("", fmt.Sprintf("test-project-%d", i))
			if err != nil {
				t.Fatal(err)
			}
			defer os.RemoveAll(path)

			projects[i] = &models.Project{
				ID:   fmt.Sprintf("concurrent-project-%d", i+1),
				Path: path,
			}
		}

		// Use contexts to control execution lifetime
		ctx1, cancel1 := context.WithCancel(context.Background())
		ctx2, cancel2 := context.WithCancel(context.Background())
		defer cancel1()
		defer cancel2()

		// Start two blocking executions that will run until cancelled
		started := make(chan int, 2)
		execErrors := make(chan error, 2)

		for i := 0; i < 2; i++ {
			go func(idx int, ctx context.Context) {
				options := ExecuteOptions{
					Prompt:  "task that will be cancelled",
					Timeout: 10 * time.Second,
				}

				started <- idx

				_, err := testExecutor.ExecuteWithContext(ctx, projects[idx].ID, projects[idx].Path, options)
				execErrors <- err
			}(i, []context.Context{ctx1, ctx2}[i])
		}

		// Wait for both to start
		<-started
		<-started
		time.Sleep(100 * time.Millisecond)

		// Verify both are running
		activeCount := testExecutor.GetActiveProcessCount()
		if activeCount != 2 {
			t.Errorf("expected 2 active processes, got %d", activeCount)
		}

		// Check that both projects are marked as executing
		if !testExecutor.IsProjectExecuting("concurrent-project-1") {
			t.Error("expected concurrent-project-1 to be executing")
		}
		if !testExecutor.IsProjectExecuting("concurrent-project-2") {
			t.Error("expected concurrent-project-2 to be executing")
		}

		// Try to start third (should fail due to limit)
		cmd := ExecuteCommand{
			Prompt: "test third concurrent request",
		}

		_, err = testExecutor.Execute(projects[2], cmd)
		if err == nil {
			t.Error("expected error for exceeding concurrent limit")
		}

		// Verify it's a resource limit error
		appErr, ok := err.(*errors.AppError)
		if !ok || appErr.Code != errors.CodeResourceLimit {
			t.Errorf("expected CodeResourceLimit, got %v", err)
		}

		// Cancel one execution to free up a slot
		cancel1()

		// Wait for cancellation to complete
		<-execErrors

		// Wait a bit longer for cleanup to happen
		time.Sleep(200 * time.Millisecond)

		// Wait for process count to decrease
		// Note: We just need to ensure we can eventually run the third process
		maxRetries := 20
		for i := 0; i < maxRetries; i++ {
			if testExecutor.GetActiveProcessCount() < 2 {
				break
			}
			time.Sleep(100 * time.Millisecond)
		}

		// Now the third should succeed
		res, err := testExecutor.Execute(projects[2], cmd)
		if err != nil {
			t.Errorf("third execution should succeed after slot freed: %v", err)
		}
		if res == nil || (res.Stdout == "" && len(res.Messages) == 0) {
			t.Error("expected valid result from third execution")
		}

		// Clean up remaining execution
		cancel2()
		<-execErrors

		// Ensure final cleanup
		time.Sleep(100 * time.Millisecond)
	})

	t.Run("duplicate execution prevention with real project state", func(t *testing.T) {
		// Create project directory
		projectPath, err := os.MkdirTemp("", "test-dup-project")
		if err != nil {
			t.Fatal(err)
		}
		defer os.RemoveAll(projectPath)

		projectID := "project-dup"

		// Start a blocking execution
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		started := make(chan bool, 1)
		done := make(chan error, 1)

		go func() {
			options := ExecuteOptions{
				Prompt:  "task that will be cancelled",
				Timeout: 10 * time.Second,
			}

			started <- true
			_, err := executor.ExecuteWithContext(ctx, projectID, projectPath, options)
			done <- err
		}()

		// Wait for first to start
		<-started
		time.Sleep(100 * time.Millisecond)

		// Verify it's executing
		if !executor.IsProjectExecuting(projectID) {
			t.Error("expected first execution to be active")
		}

		// Try duplicate execution - should fail
		options := ExecuteOptions{
			Prompt:  "duplicate attempt",
			Timeout: 1 * time.Second,
		}

		_, err = executor.ExecuteWithProject(projectID, projectPath, options)
		if err == nil {
			t.Error("expected error for duplicate execution")
		}

		appErr, ok := err.(*errors.AppError)
		if !ok || appErr.Code != errors.CodeProcessActive {
			t.Errorf("expected CodeProcessActive, got %v", err)
		}

		// Cancel the first execution
		cancel()

		// Wait for cleanup
		<-done
		time.Sleep(200 * time.Millisecond)

		// Wait for cleanup to complete
		maxRetries := 20
		for i := 0; i < maxRetries; i++ {
			if !executor.IsProjectExecuting(projectID) {
				break
			}
			time.Sleep(100 * time.Millisecond)
		}

		// Verify cleanup eventually happened
		if executor.IsProjectExecuting(projectID) {
			t.Error("expected process to be cleaned up after cancellation")
		}

		// Now a new execution should succeed
		project := &models.Project{
			ID:   projectID,
			Path: projectPath,
		}
		cmd := ExecuteCommand{
			Prompt: "new execution after cleanup",
		}

		res, err := executor.Execute(project, cmd)
		if err != nil {
			t.Errorf("should be able to execute after cleanup: %v", err)
		}
		if res == nil || (res.Stdout == "" && len(res.Messages) == 0) {
			t.Error("expected valid result")
		}
	})

	t.Run("context cancellation with real process cleanup", func(t *testing.T) {
		// Create project directory
		projectPath, err := os.MkdirTemp("", "test-cancel-project")
		if err != nil {
			t.Fatal(err)
		}
		defer os.RemoveAll(projectPath)

		projectID := "project-cancel"

		// Create cancellable context
		ctx, cancel := context.WithCancel(context.Background())

		// Track process state
		processStarted := make(chan bool)
		processDone := make(chan error, 1)

		// Start long-running execution
		go func() {
			options := ExecuteOptions{
				Prompt:  "task that will be cancelled",
				Timeout: 10 * time.Second,
			}

			// Signal start
			processStarted <- true

			res, err := executor.ExecuteWithContext(ctx, projectID, projectPath, options)
			if err != nil {
				processDone <- err
			} else if res != nil && (res.Stdout != "" || len(res.Messages) > 0) {
				processDone <- fmt.Errorf("unexpected successful completion with output")
			} else {
				processDone <- nil
			}
		}()

		// Wait for process to start
		<-processStarted
		time.Sleep(200 * time.Millisecond)

		// Verify it's running
		if !executor.IsProjectExecuting(projectID) {
			t.Error("expected project to be executing before cancellation")
		}

		// Cancel the context
		cancel()

		// Wait for error with timeout
		select {
		case err := <-processDone:
			if err == nil {
				t.Error("expected cancellation error, got nil")
			} else if !strings.Contains(err.Error(), "cancel") && !strings.Contains(err.Error(), "context") {
				t.Errorf("expected cancellation error, got: %v", err)
			}
		case <-time.After(2 * time.Second):
			t.Error("timeout waiting for cancellation to complete")
		}

		// Wait for cleanup to complete
		maxRetries := 20
		cleaned := false
		for i := 0; i < maxRetries; i++ {
			if !executor.IsProjectExecuting(projectID) {
				cleaned = true
				break
			}
			time.Sleep(100 * time.Millisecond)
		}

		if !cleaned {
			t.Error("expected process to be cleaned up after cancellation")
		}

		// Verify we can execute on the same project again
		project := &models.Project{
			ID:   projectID,
			Path: projectPath,
		}
		cmd := ExecuteCommand{
			Prompt: "new execution after cancel",
		}

		res, err := executor.Execute(project, cmd)
		if err != nil {
			t.Errorf("should be able to execute after cancellation: %v", err)
		}
		if res == nil || (res.Stdout == "" && len(res.Messages) == 0) {
			t.Error("expected valid result after re-execution")
		}
	})
}

// createMockCLI creates a mock Claude CLI executable for testing
func createMockCLI(t *testing.T) string {
	tempDir, err := os.MkdirTemp("", "mock_claude")
	if err != nil {
		t.Fatal(err)
	}

	mockCLI := filepath.Join(tempDir, "claude")

	// Create a simple script that simulates Claude behavior
	script := `#!/bin/bash
# Mock Claude CLI for testing

# Parse arguments
while [[ $# -gt 0 ]]; do
	case $1 in
		--project-dir)
			PROJECT_DIR="$2"
			shift 2
			;;
		*)
			shift
			;;
	esac
done

# Read prompt from stdin
PROMPT=$(cat)

# Simulate different behaviors based on prompt
case "$PROMPT" in
	*"sleep for test"*)
		sleep 1
		echo "Sleeping for test"
		;;
	*"will be cancelled"*)
		sleep 10  # Long sleep to allow cancellation
		echo "Should not see this"
		;;
	*"long running"*)
		sleep 2
		echo "Long running task completed"
		;;
	*)
		echo "Mock response to: $PROMPT"
		echo "Project directory: $PROJECT_DIR"
		;;
esac

# Simulate some output
echo "Mock execution complete"
`

	if err := os.WriteFile(mockCLI, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}

	return mockCLI
}

// createAdvancedMockCLI creates a more sophisticated mock Claude CLI for testing real behaviors
func createAdvancedMockCLI(t *testing.T) string {
	tempDir, err := os.MkdirTemp("", "advanced_mock_claude")
	if err != nil {
		t.Fatal(err)
	}

	mockCLI := filepath.Join(tempDir, "claude")

	// Create an advanced mock that simulates streaming output and real Claude behaviors
	script := `#!/bin/bash
# Advanced Mock Claude CLI for testing real component interactions

# Parse arguments
PROJECT_DIR=""
SESSION_ID=""
while [[ $# -gt 0 ]]; do
	case $1 in
		-p)
			# -p flag doesn't have a value in new format
			shift
			;;
		-c)
			SESSION_ID="$2"
			shift 2
			;;
		--verbose|--output-format)
			# Skip these flags
			if [ "$1" = "--output-format" ]; then
				shift 2
			else
				shift
			fi
			;;
		*)
			shift
			;;
	esac
done

# Read prompt from stdin
PROMPT=$(cat)

# Generate session ID if not provided
if [ -z "$SESSION_ID" ]; then
	SESSION_ID="mock-session-$(date +%s)-$$"
fi

# Debug: log the prompt to stderr
echo "DEBUG: Received prompt: '$PROMPT' from stdin" >&2

# Function to collect messages for final output
MESSAGES=()
add_message() {
	local msg_type="$1"
	local content="$2"
	MESSAGES+=("{\"type\":\"$msg_type\",\"content\":{\"text\":\"$content\"}}")
}

# Function to output final JSON result in streaming format
output_result() {
	echo "{\"type\":\"system\",\"session_id\":\"$SESSION_ID\"}"
	for msg in "${MESSAGES[@]}"; do
		echo "$msg"
	done
}

# Simulate different behaviors based on prompt
case "$PROMPT" in
	*"analyze"*".go file"*)
		# Simulate analyzing a Go file - with brief delay before output
		sleep 0.5  # Brief delay to allow process tracking
		add_message "assistant" "Analyzing Go file in project directory..."
		if [ -n "$PROJECT_DIR" ] && [ -d "$PROJECT_DIR" ]; then
			# Look for .go files
			GO_FILES=$(find "$PROJECT_DIR" -name "*.go" -type f 2>/dev/null | head -5)
			if [ -n "$GO_FILES" ]; then
				add_message "assistant" "Found Go files in project:"
				for file in $GO_FILES; do
					add_message "assistant" "- $(basename "$file")"
				done
			fi
		fi
		add_message "assistant" "Analysis complete. The code appears to be well-structured."
		output_result
		;;
		
	*"analyze main"*"with delay"*)
		# Simulate analyzing with delay for concurrent testing
		sleep 2  # Delay before output to test concurrency
		add_message "assistant" "Starting analysis..."
		add_message "assistant" "Analyzing main.go file..."
		add_message "assistant" "Found package declaration and comments."
		add_message "assistant" "Analysis finished successfully."
		output_result
		;;
		
	*"long running task"*)
		# Simulate a long-running task
		sleep 3  # Long delay before output to allow duplicate detection
		add_message "assistant" "Starting long-running task..."
		add_message "assistant" "Task completed after delay."
		add_message "assistant" "Long running task completed."
		output_result
		;;
		
	*"task that will be cancelled"*|"")
		# Simulate a task that runs forever (to test cancellation)
		# Run forever until killed - no output
		echo "DEBUG: Running infinite loop for cancellation test" >&2
		while true; do
			sleep 0.5
			echo -n "." >&2  # Progress indicator to stderr
		done
		;;
		
	*"sleep for 1 second"*)
		# Sleep command for testing concurrency
		sleep 1
		add_message "assistant" "Slept for 1 second"
		add_message "assistant" "Sleep completed"
		output_result
		;;
		
	*"should fail"*)
		# Simulate a failure
		sleep 0.1  # Brief delay
		echo "{\"type\":\"system\",\"session_id\":\"$SESSION_ID\"}"
		echo "{\"type\":\"error\",\"message\":\"Task failed due to simulated error\"}"
		exit 1
		;;
		
	*"new execution after cancel"*)
		# Quick task to verify execution works after cancellation
		sleep 0.1  # Brief delay
		add_message "assistant" "Executing new task after cancellation."
		add_message "assistant" "Task completed successfully."
		output_result
		;;
		
	*)
		# Default response
		sleep 0.1  # Brief delay
		add_message "assistant" "Processing request..."
		add_message "assistant" "Received prompt: $PROMPT"
		if [ -n "$PROJECT_DIR" ]; then
			add_message "assistant" "Working in project: $PROJECT_DIR"
		fi
		add_message "assistant" "Mock execution complete."
		output_result
		;;
esac

# Exit successfully unless we intentionally failed
exit 0
`

	if err := os.WriteFile(mockCLI, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}

	return mockCLI
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
