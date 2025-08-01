package mocks

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
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

// ClaudeMessage represents a message from Claude
type ClaudeMessage struct {
	Type    string          `json:"type"`
	Content json.RawMessage `json:"content,omitempty"`
	Error   string          `json:"error,omitempty"`
}

// ClaudeOutput represents the structured output from Claude CLI
type ClaudeOutput struct {
	SessionID string          `json:"session_id"`
	Messages  []ClaudeMessage `json:"messages"`
	Error     string          `json:"error,omitempty"`
}

// ClaudeMockConfig configures the mock behavior
type ClaudeMockConfig struct {
	Scenario       ClaudeMockScenario
	SessionID      string
	MessageCount   int
	Delay          time.Duration
	ExitCode       int
	StderrOutput   string
	CustomResponse string
}

var (
	claudeMockBinaryPath string
	buildOnce            sync.Once
)

// ensureClaudeMockBinary ensures the claude-mock binary is built
func ensureClaudeMockBinary() error {
	var buildErr error
	buildOnce.Do(func() {
		// Find the claude-mock directory
		_, filename, _, _ := runtime.Caller(0)
		serverDir := filepath.Dir(filepath.Dir(filepath.Dir(filename)))
		claudeMockDir := filepath.Join(filepath.Dir(serverDir), "claude-mock")
		claudeMockBinaryPath = filepath.Join(filepath.Dir(serverDir), "bin", "claude-mock")
		
		// Check if binary exists
		if _, err := os.Stat(claudeMockBinaryPath); os.IsNotExist(err) {
			// Build the binary
			cmd := exec.Command("make", "build")
			cmd.Dir = claudeMockDir
			if output, err := cmd.CombinedOutput(); err != nil {
				buildErr = fmt.Errorf("failed to build claude-mock: %v\nOutput: %s", err, output)
			}
		}
	})
	return buildErr
}

// CreateMockClaudeScript creates a wrapper script that uses the claude-mock binary
func CreateMockClaudeScript(scriptPath string, config ClaudeMockConfig) error {
	// Ensure binary is built
	if err := ensureClaudeMockBinary(); err != nil {
		return err
	}
	
	// Map scenario to conversation file
	_, filename, _, _ := runtime.Caller(0)
	serverDir := filepath.Dir(filepath.Dir(filepath.Dir(filename)))
	conversationsDir := filepath.Join(filepath.Dir(serverDir), "claude-mock", "conversations", "test")
	
	var conversationFile string
	var delayMs int
	
	switch config.Scenario {
	case ScenarioSuccess:
		conversationFile = filepath.Join(conversationsDir, "success.jsonl")
	case ScenarioError:
		conversationFile = filepath.Join(conversationsDir, "error.jsonl")
	case ScenarioTimeout:
		conversationFile = filepath.Join(conversationsDir, "timeout.jsonl")
		delayMs = 10000 // 10 seconds to simulate timeout
	case ScenarioInvalidJSON:
		conversationFile = filepath.Join(conversationsDir, "invalid_json.jsonl")
	case ScenarioEmpty:
		conversationFile = filepath.Join(conversationsDir, "empty.jsonl")
	case ScenarioMultiMessage:
		conversationFile = filepath.Join(conversationsDir, "multi_message.jsonl")
	case ScenarioLongResponse:
		conversationFile = filepath.Join(conversationsDir, "long_response.jsonl")
	case ScenarioSlowStream:
		conversationFile = filepath.Join(conversationsDir, "slow_stream.jsonl")
		delayMs = 500 // 500ms between messages
	default:
		conversationFile = filepath.Join(conversationsDir, "success.jsonl")
	}
	
	// Override delay if specified
	if config.Delay > 0 {
		delayMs = int(config.Delay.Milliseconds())
	} else if delayMs == 0 {
		// Default to no delay for tests
		delayMs = 0
	}
	
	// Create wrapper script that sets environment and calls claude-mock
	script := fmt.Sprintf(`#!/bin/sh
# Wrapper script for claude-mock binary
export CLAUDE_MOCK_LOG_FILE="%s"
export CLAUDE_MOCK_DELAY_MS="%d"

# Execute the claude-mock binary with all arguments
exec "%s" "$@"
`, conversationFile, delayMs, claudeMockBinaryPath)
	
	// Write script
	if err := os.WriteFile(scriptPath, []byte(script), 0755); err != nil {
		return fmt.Errorf("failed to write wrapper script: %w", err)
	}
	
	return nil
}

// ClaudeMockBuilder provides a fluent interface for building mock responses
type ClaudeMockBuilder struct {
	sessionID string
	messages  []ClaudeMessage
	error     string
}

// NewClaudeMockBuilder creates a new mock builder
func NewClaudeMockBuilder() *ClaudeMockBuilder {
	return &ClaudeMockBuilder{
		sessionID: fmt.Sprintf("mock-session-%d", time.Now().UnixNano()),
		messages:  []ClaudeMessage{},
	}
}

// WithSessionID sets the session ID
func (b *ClaudeMockBuilder) WithSessionID(id string) *ClaudeMockBuilder {
	b.sessionID = id
	return b
}

// WithTextMessage adds a text message
func (b *ClaudeMockBuilder) WithTextMessage(text string) *ClaudeMockBuilder {
	b.messages = append(b.messages, ClaudeMessage{
		Type:    "text",
		Content: json.RawMessage(fmt.Sprintf(`{"text": %q}`, text)),
	})
	return b
}

// WithCodeMessage adds a code message
func (b *ClaudeMockBuilder) WithCodeMessage(language, code string) *ClaudeMockBuilder {
	b.messages = append(b.messages, ClaudeMessage{
		Type:    "code",
		Content: json.RawMessage(fmt.Sprintf(`{"language": %q, "code": %q}`, language, code)),
	})
	return b
}

// WithError sets an error
func (b *ClaudeMockBuilder) WithError(err string) *ClaudeMockBuilder {
	b.error = err
	return b
}

// Build generates the JSON response
func (b *ClaudeMockBuilder) Build() (string, error) {
	output := ClaudeOutput{
		SessionID: b.sessionID,
		Messages:  b.messages,
		Error:     b.error,
	}

	data, err := json.Marshal(output)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

// The following functions are kept for backward compatibility but are not used with the binary approach

// GenerateClaudeResponse generates a mock Claude response based on config
func GenerateClaudeResponse(config ClaudeMockConfig) (string, error) {
	// This is kept for tests that might call it directly
	// The actual mock responses are now in the conversation files
	return "", fmt.Errorf("GenerateClaudeResponse is deprecated - use conversation files")
}

// parseClaudeOutput is kept for compatibility
func parseClaudeOutput(output string) ([]ClaudeMessage, string, error) {
	output = strings.TrimSpace(output)
	if output == "" {
		return nil, "", fmt.Errorf("empty Claude output")
	}

	var messages []ClaudeMessage
	var sessionID string

	lines := strings.Split(output, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		var obj map[string]interface{}
		if err := json.Unmarshal([]byte(line), &obj); err != nil {
			continue
		}

		msgType, _ := obj["type"].(string)
		if msgType == "system" {
			if sid, ok := obj["session_id"].(string); ok && sid != "" {
				sessionID = sid
			}
		}

		content, _ := json.Marshal(obj)
		messages = append(messages, ClaudeMessage{
			Type:    msgType,
			Content: json.RawMessage(content),
		})
	}

	return messages, sessionID, nil
}