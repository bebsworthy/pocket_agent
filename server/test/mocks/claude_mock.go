package mocks

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"
)

// ClaudeMockScenario represents different response scenarios
type ClaudeMockScenario string

const (
	ScenarioSuccess      ClaudeMockScenario = "success"
	ScenarioError        ClaudeMockScenario = "error"
	ScenarioTimeout      ClaudeMockScenario = "timeout"
	ScenarioInvalidJSON  ClaudeMockScenario = "invalid_json"
	ScenarioEmpty        ClaudeMockScenario = "empty"
	ScenarioPartialJSON  ClaudeMockScenario = "partial_json"
	ScenarioMultiMessage ClaudeMockScenario = "multi_message"
	ScenarioLongResponse ClaudeMockScenario = "long_response"
	ScenarioSlowStream   ClaudeMockScenario = "slow_stream"
)

// ClaudeMockConfig configures the mock behavior
type ClaudeMockConfig struct {
	Scenario ClaudeMockScenario
	Delay    time.Duration
}

// ClaudeMockExecutable helps create mock Claude executables for testing
type ClaudeMockExecutable struct {
	path    string
	tempDir string
	config  ClaudeMockConfig
}

// NewClaudeMockExecutable creates a new mock executable helper
func NewClaudeMockExecutable(t *testing.T) *ClaudeMockExecutable {
	tempDir, err := os.MkdirTemp("", "claude_mock_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}

	return &ClaudeMockExecutable{
		tempDir: tempDir,
		path:    filepath.Join(tempDir, "claude"),
		config: ClaudeMockConfig{
			Scenario: ScenarioSuccess,
		},
	}
}

// WithScenario sets the response scenario
func (m *ClaudeMockExecutable) WithScenario(scenario ClaudeMockScenario) *ClaudeMockExecutable {
	m.config.Scenario = scenario
	return m
}

// WithDelay sets response delay
func (m *ClaudeMockExecutable) WithDelay(delay time.Duration) *ClaudeMockExecutable {
	m.config.Delay = delay
	return m
}

// Create creates the mock executable
func (m *ClaudeMockExecutable) Create() error {
	return CreateMockClaudeScript(m.path, m.config)
}

// Path returns the path to the mock executable
func (m *ClaudeMockExecutable) Path() string {
	return m.path
}

// Cleanup removes the temporary directory
func (m *ClaudeMockExecutable) Cleanup() {
	if m.tempDir != "" {
		os.RemoveAll(m.tempDir)
	}
}

// MustCreate creates the mock or fails the test
func (m *ClaudeMockExecutable) MustCreate(t *testing.T) string {
	if err := m.Create(); err != nil {
		t.Fatalf("Failed to create mock: %v", err)
	}
	return m.path
}

// CreateMockClaudeScript creates a wrapper script that uses the claude-mock binary
func CreateMockClaudeScript(scriptPath string, config ClaudeMockConfig) error {
	// Find paths relative to this file
	_, filename, _, _ := runtime.Caller(0)
	root := filepath.Dir(filepath.Dir(filepath.Dir(filepath.Dir(filename))))
	claudeMockBinary := filepath.Join(root, "bin", "claude-mock")
	conversationsDir := filepath.Join(root, "claude-mock", "conversations", "test")
	
	// Map scenario to conversation file
	conversationFile := filepath.Join(conversationsDir, string(config.Scenario)+".jsonl")
	
	// Set delay based on scenario
	delayMs := int(config.Delay.Milliseconds())
	if delayMs == 0 {
		// Default delays for specific scenarios
		switch config.Scenario {
		case ScenarioTimeout:
			delayMs = 2000 // 2 seconds (reduced from 10s)
		case ScenarioSlowStream:
			delayMs = 500 // 500ms between messages
		}
	}
	
	// Create wrapper script
	script := fmt.Sprintf(`#!/bin/sh
export CLAUDE_MOCK_LOG_FILE="%s"
export CLAUDE_MOCK_DELAY_MS="%d"
exec "%s" "$@"
`, conversationFile, delayMs, claudeMockBinary)
	
	return os.WriteFile(scriptPath, []byte(script), 0755)
}