package executor

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// createMockClaude creates a mock claude executable for testing
func createMockClaude(t *testing.T, script string) string {
	tempDir, err := os.MkdirTemp("", "claude_mock")
	if err != nil {
		t.Fatal(err)
	}

	mockPath := filepath.Join(tempDir, "claude")
	content := "#!/bin/sh\n" + script
	if err := os.WriteFile(mockPath, []byte(content), 0755); err != nil {
		t.Fatal(err)
	}

	return mockPath
}

func TestExecute(t *testing.T) {
	// Create successful mock
	successScript := `
echo '{"session_id": "test-session-123", "messages": [{"type": "text", "content": {"text": "Hello"}}]}'
`
	successPath := createMockClaude(t, successScript)
	defer os.RemoveAll(filepath.Dir(successPath))

	// Create failing mock
	failScript := `exit 1`
	failPath := createMockClaude(t, failScript)
	defer os.RemoveAll(filepath.Dir(failPath))

	// Create timeout mock
	timeoutScript := `sleep 10`
	timeoutPath := createMockClaude(t, timeoutScript)
	defer os.RemoveAll(filepath.Dir(timeoutPath))

	// Create test project
	project := &models.Project{
		ID:        "test-project",
		Path:      "/tmp",
		SessionID: "existing-session",
	}

	tests := []struct {
		name       string
		claudePath string
		project    *models.Project
		options    ExecuteOptions
		wantErr    bool
		errCode    errors.ErrorCode
		check      func(*ExecuteResult)
	}{
		{
			name:       "successful execution",
			claudePath: successPath,
			project:    project,
			options: ExecuteOptions{
				Prompt: "test prompt",
			},
			wantErr: false,
			check: func(r *ExecuteResult) {
				if r.SessionID != "test-session-123" {
					t.Errorf("expected session ID test-session-123, got %s", r.SessionID)
				}
				if len(r.Messages) != 1 {
					t.Errorf("expected 1 message, got %d", len(r.Messages))
				}
				if r.ExitCode != 0 {
					t.Errorf("expected exit code 0, got %d", r.ExitCode)
				}
			},
		},
		{
			name:       "nil project",
			claudePath: successPath,
			project:    nil,
			options: ExecuteOptions{
				Prompt: "test",
			},
			wantErr: true,
			errCode: errors.CodeValidationFailed,
		},
		{
			name:       "empty prompt",
			claudePath: successPath,
			project:    project,
			options:    ExecuteOptions{},
			wantErr:    true,
			errCode:    errors.CodeValidationFailed,
		},
		{
			name:       "execution failure",
			claudePath: failPath,
			project:    project,
			options: ExecuteOptions{
				Prompt: "test",
			},
			wantErr: true,
			errCode: errors.CodeExecutionFailed,
			check: func(r *ExecuteResult) {
				if r == nil {
					return
				}
				if r.ExitCode != 1 {
					t.Errorf("expected exit code 1, got %d", r.ExitCode)
				}
			},
		},
		{
			name:       "timeout",
			claudePath: timeoutPath,
			project:    project,
			options: ExecuteOptions{
				Prompt:  "test",
				Timeout: 100 * time.Millisecond,
			},
			wantErr: true,
			errCode: errors.CodeExecutionTimeout,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ce := &ClaudeExecutor{
				activeProcesses: make(map[string]*ProcessInfo),
				config: Config{
					ClaudePath:              tt.claudePath,
					DefaultTimeout:          5 * time.Second,
					MaxConcurrentExecutions: 10,
				},
				logger: logger.New("info"),
			}

			result, err := ce.Execute(tt.project, tt.options)
			if (err != nil) != tt.wantErr {
				t.Errorf("Execute() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errCode != "" {
				appErr, ok := err.(*errors.AppError)
				if !ok {
					t.Errorf("expected AppError, got %T", err)
				} else if appErr.Code != tt.errCode {
					t.Errorf("expected error code %s, got %s", tt.errCode, appErr.Code)
				}
			}

			if tt.check != nil {
				tt.check(result)
			}
		})
	}
}

func TestBuildCommandArgs(t *testing.T) {
	ce := &ClaudeExecutor{}

	project := &models.Project{
		ID:        "test-project",
		Path:      "/test/path",
		SessionID: "test-session",
	}

	tests := []struct {
		name     string
		project  *models.Project
		options  ExecuteOptions
		expected []string
	}{
		{
			name:    "basic command",
			project: &models.Project{Path: "/test/path"},
			options: ExecuteOptions{
				Prompt: "Hello Claude",
			},
			expected: []string{"-p", "/test/path", "Hello Claude"},
		},
		{
			name:    "with session ID",
			project: project,
			options: ExecuteOptions{
				Prompt: "Hello",
			},
			expected: []string{"-c", "test-session", "-p", "/test/path", "Hello"},
		},
		{
			name:    "with all options",
			project: project,
			options: ExecuteOptions{
				Prompt:                     "Test",
				DangerouslySkipPermissions: true,
				AllowedTools:               []string{"tool1", "tool2"},
				DisallowedTools:            []string{"bad1", "bad2"},
				MCPConfig:                  "config.json",
				AppendSystemPrompt:         "system prompt",
				PermissionMode:             "auto",
				Model:                      "claude-3",
				FallbackModel:              "claude-2",
				AddDirs:                    []string{"/dir1", "/dir2"},
				StrictMCPConfig:            true,
			},
			expected: []string{
				"-c", "test-session",
				"-p", "/test/path",
				"--dangerously-skip-permissions",
				"--allowed-tools", "tool1,tool2",
				"--disallowed-tools", "bad1,bad2",
				"--mcp-config", "config.json",
				"--append-system-prompt", "system prompt",
				"--permission-mode", "auto",
				"--model", "claude-3",
				"--fallback-model", "claude-2",
				"--add-dir", "/dir1",
				"--add-dir", "/dir2",
				"--strict-mcp-config",
				"Test",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			args := ce.buildCommandArgs(tt.project, tt.options)

			if len(args) != len(tt.expected) {
				t.Errorf("expected %d args, got %d\nExpected: %v\nGot: %v",
					len(tt.expected), len(args), tt.expected, args)
				return
			}

			for i, arg := range args {
				if arg != tt.expected[i] {
					t.Errorf("arg[%d]: expected %q, got %q", i, tt.expected[i], arg)
				}
			}
		})
	}
}

func TestParseClaudeOutput(t *testing.T) {
	ce := &ClaudeExecutor{logger: logger.New("info")}

	tests := []struct {
		name          string
		output        string
		wantMessages  int
		wantSessionID string
		wantErr       bool
		errMsg        string
	}{
		{
			name: "valid JSON output",
			output: `{
				"session_id": "session-123",
				"messages": [
					{"type": "text", "content": {"text": "Hello"}},
					{"type": "text", "content": {"text": "World"}}
				]
			}`,
			wantMessages:  2,
			wantSessionID: "session-123",
			wantErr:       false,
		},
		{
			name:    "empty output",
			output:  "",
			wantErr: true,
			errMsg:  "empty Claude output",
		},
		{
			name:    "invalid JSON",
			output:  "{invalid json}",
			wantErr: true,
			errMsg:  "failed to parse JSON",
		},
		{
			name: "JSON with prefix/suffix",
			output: `Some prefix text
			{"session_id": "extracted-123", "messages": []}
			Some suffix`,
			wantMessages:  0,
			wantSessionID: "extracted-123",
			wantErr:       false,
		},
		{
			name: "error in output",
			output: `{
				"error": "Claude encountered an error",
				"messages": []
			}`,
			wantErr: true,
			errMsg:  "Claude reported error",
		},
		{
			name:    "no JSON in output",
			output:  "Just plain text without JSON",
			wantErr: true,
			errMsg:  "no valid JSON found",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			messages, sessionID, err := ce.parseClaudeOutput(tt.output)

			if (err != nil) != tt.wantErr {
				t.Errorf("parseClaudeOutput() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("expected error containing %q, got %q", tt.errMsg, err.Error())
			}

			if !tt.wantErr {
				if len(messages) != tt.wantMessages {
					t.Errorf("expected %d messages, got %d", tt.wantMessages, len(messages))
				}
				if sessionID != tt.wantSessionID {
					t.Errorf("expected session ID %q, got %q", tt.wantSessionID, sessionID)
				}
			}
		})
	}
}

func TestExecuteWithCallback(t *testing.T) {
	// Create mock that returns multiple messages
	script := `
echo '{"session_id": "cb-session", "messages": [
	{"type": "text", "content": {"text": "Message 1"}},
	{"type": "text", "content": {"text": "Message 2"}},
	{"type": "text", "content": {"text": "Message 3"}}
]}'
`
	mockPath := createMockClaude(t, script)
	defer os.RemoveAll(filepath.Dir(mockPath))

	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			ClaudePath:              mockPath,
			DefaultTimeout:          5 * time.Second,
			MaxConcurrentExecutions: 10,
		},
		logger: logger.New("info"),
	}

	project := &models.Project{
		ID:   "callback-project",
		Path: "/tmp",
	}

	options := ExecuteOptions{
		Prompt: "test with callback",
	}

	// Track callback invocations
	var callbackMessages []models.ClaudeMessage
	callback := func(msg models.ClaudeMessage) {
		callbackMessages = append(callbackMessages, msg)
	}

	result, err := ce.ExecuteWithCallback(project, options, callback)
	if err != nil {
		t.Errorf("ExecuteWithCallback() unexpected error: %v", err)
	}

	// Verify callback was called for each message
	if len(callbackMessages) != 3 {
		t.Errorf("expected callback called 3 times, got %d", len(callbackMessages))
	}

	// Verify messages match result
	if len(result.Messages) != len(callbackMessages) {
		t.Error("callback messages don't match result messages")
	}

	// Test with nil callback (should not panic)
	_, err = ce.ExecuteWithCallback(project, options, nil)
	if err != nil {
		t.Errorf("ExecuteWithCallback() with nil callback failed: %v", err)
	}
}

func TestExecuteConcurrentLimit(t *testing.T) {
	// Mock that sleeps to simulate long execution
	script := `
sleep 0.5
echo '{"session_id": "test", "messages": []}'
`
	mockPath := createMockClaude(t, script)
	defer os.RemoveAll(filepath.Dir(mockPath))

	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			ClaudePath:              mockPath,
			DefaultTimeout:          2 * time.Second,
			MaxConcurrentExecutions: 2,
		},
		logger: logger.New("info"),
	}

	// Start two executions (should succeed)
	project1 := &models.Project{ID: "p1", Path: "/tmp"}
	project2 := &models.Project{ID: "p2", Path: "/tmp"}

	done := make(chan error, 2)
	go func() {
		_, err := ce.Execute(project1, ExecuteOptions{Prompt: "test"})
		done <- err
	}()
	go func() {
		_, err := ce.Execute(project2, ExecuteOptions{Prompt: "test"})
		done <- err
	}()

	// Wait a bit for processes to register
	time.Sleep(100 * time.Millisecond)

	// Try third execution (should fail)
	project3 := &models.Project{ID: "p3", Path: "/tmp"}
	_, err := ce.Execute(project3, ExecuteOptions{Prompt: "test"})

	if err == nil {
		t.Error("expected error for exceeding concurrent limit")
	} else {
		appErr, _ := err.(*errors.AppError)
		if appErr.Code != errors.CodeResourceLimit {
			t.Errorf("expected CodeResourceLimit, got %s", appErr.Code)
		}
	}

	// Wait for first two to complete
	<-done
	<-done
}

func TestExecuteProcessCleanup(t *testing.T) {
	// Mock that completes quickly
	script := `echo '{"session_id": "cleanup", "messages": []}'`
	mockPath := createMockClaude(t, script)
	defer os.RemoveAll(filepath.Dir(mockPath))

	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		config: Config{
			ClaudePath:              mockPath,
			DefaultTimeout:          5 * time.Second,
			MaxConcurrentExecutions: 10,
		},
		logger: logger.New("info"),
	}

	project := &models.Project{ID: "cleanup-test", Path: "/tmp"}

	// Execute
	_, err := ce.Execute(project, ExecuteOptions{Prompt: "test"})
	if err != nil {
		t.Errorf("Execute() unexpected error: %v", err)
	}

	// Verify process was cleaned up
	if ce.GetActiveProcessCount() != 0 {
		t.Error("expected process to be cleaned up after execution")
	}
}