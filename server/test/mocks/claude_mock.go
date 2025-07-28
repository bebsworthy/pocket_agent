package mocks

import (
	"encoding/json"
	"flag"
	"fmt"
	"math/rand"
	"os"
	"strings"
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

// GenerateClaudeResponse generates a mock Claude response based on config
func GenerateClaudeResponse(config ClaudeMockConfig) (string, error) {
	switch config.Scenario {
	case ScenarioSuccess:
		return generateSuccessResponse(config)
	case ScenarioError:
		return generateErrorResponse(config)
	case ScenarioTimeout:
		// Sleep forever (will be killed by timeout)
		time.Sleep(time.Hour)
		return "", nil
	case ScenarioInvalidJSON:
		return "This is not valid JSON { broken", nil
	case ScenarioEmpty:
		return "", nil
	case ScenarioPartialJSON:
		// Return partial JSON that gets cut off
		return `{"session_id": "test-123", "messages": [{"type": "text", "cont`, nil
	case ScenarioMultiMessage:
		return generateMultiMessageResponse(config)
	case ScenarioLongResponse:
		return generateLongResponse(config)
	case ScenarioSlowStream:
		return generateSlowStreamResponse(config)
	default:
		if config.CustomResponse != "" {
			return config.CustomResponse, nil
		}
		return generateSuccessResponse(config)
	}
}

func generateSuccessResponse(config ClaudeMockConfig) (string, error) {
	sessionID := config.SessionID
	if sessionID == "" {
		sessionID = fmt.Sprintf("session-%d", time.Now().Unix())
	}

	messages := []ClaudeMessage{
		{
			Type:    "text",
			Content: json.RawMessage(`{"text": "Hello! I successfully processed your request."}`),
		},
	}

	output := ClaudeOutput{
		SessionID: sessionID,
		Messages:  messages,
	}

	data, err := json.Marshal(output)
	if err != nil {
		return "", err
	}

	if config.Delay > 0 {
		time.Sleep(config.Delay)
	}

	return string(data), nil
}

func generateErrorResponse(config ClaudeMockConfig) (string, error) {
	output := ClaudeOutput{
		SessionID: config.SessionID,
		Error:     "Claude encountered an error processing your request",
		Messages: []ClaudeMessage{
			{
				Type:  "error",
				Error: "Failed to process prompt",
			},
		},
	}

	data, err := json.Marshal(output)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

func generateMultiMessageResponse(config ClaudeMockConfig) (string, error) {
	sessionID := config.SessionID
	if sessionID == "" {
		sessionID = fmt.Sprintf("session-%d", time.Now().Unix())
	}

	messageCount := config.MessageCount
	if messageCount == 0 {
		messageCount = 5
	}

	messages := make([]ClaudeMessage, 0, messageCount)
	for i := 0; i < messageCount; i++ {
		messages = append(messages, ClaudeMessage{
			Type:    "text",
			Content: json.RawMessage(fmt.Sprintf(`{"text": "Message %d of %d"}`, i+1, messageCount)),
		})
	}

	// Add a code block message
	messages = append(messages, ClaudeMessage{
		Type:    "code",
		Content: json.RawMessage(`{"language": "go", "code": "func main() {\n\tfmt.Println(\"Hello, World!\")\n}"}`),
	})

	output := ClaudeOutput{
		SessionID: sessionID,
		Messages:  messages,
	}

	data, err := json.Marshal(output)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

func generateLongResponse(config ClaudeMockConfig) (string, error) {
	sessionID := config.SessionID
	if sessionID == "" {
		sessionID = fmt.Sprintf("session-%d", time.Now().Unix())
	}

	// Generate a very long text response
	var longText strings.Builder
	longText.WriteString("This is a very long response. ")
	for i := 0; i < 1000; i++ {
		longText.WriteString(fmt.Sprintf("Line %d: Lorem ipsum dolor sit amet, consectetur adipiscing elit. ", i))
	}

	messages := []ClaudeMessage{
		{
			Type:    "text",
			Content: json.RawMessage(fmt.Sprintf(`{"text": %q}`, longText.String())),
		},
	}

	output := ClaudeOutput{
		SessionID: sessionID,
		Messages:  messages,
	}

	data, err := json.Marshal(output)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

func generateSlowStreamResponse(config ClaudeMockConfig) (string, error) {
	sessionID := config.SessionID
	if sessionID == "" {
		sessionID = fmt.Sprintf("session-%d", time.Now().Unix())
	}

	// Simulate streaming by outputting parts slowly
	parts := []string{
		`{"session_id": "` + sessionID + `",`,
		`"messages": [`,
		`{"type": "text",`,
		`"content": {"text": "Thinking..."}}`,
		`]}`,
	}

	delay := config.Delay
	if delay == 0 {
		delay = 100 * time.Millisecond
	}

	var result strings.Builder
	for _, part := range parts {
		result.WriteString(part)
		time.Sleep(delay)
	}

	return result.String(), nil
}

// MockClaudeExecutable is a main function for creating a standalone mock executable
func MockClaudeExecutable() {
	var (
		scenario     string
		sessionID    string
		exitCode     int
		delay        int
		messageCount int
		stderr       string
		customJSON   string
	)

	// Parse command line flags
	flag.StringVar(&scenario, "scenario", "success", "Response scenario")
	flag.StringVar(&sessionID, "session", "", "Session ID to use")
	flag.IntVar(&exitCode, "exit", 0, "Exit code")
	flag.IntVar(&delay, "delay", 0, "Delay in milliseconds")
	flag.IntVar(&messageCount, "messages", 1, "Number of messages")
	flag.StringVar(&stderr, "stderr", "", "Stderr output")
	flag.StringVar(&customJSON, "json", "", "Custom JSON response")

	// Parse Claude CLI flags (to make it compatible)
	var (
		projectPath string
		prompt      string
		sessionFlag string
	)
	flag.StringVar(&projectPath, "p", "", "Project path")
	flag.StringVar(&prompt, "prompt", "", "Prompt")
	flag.StringVar(&sessionFlag, "c", "", "Session ID")

	flag.Parse()

	// If session flag is provided, use it
	if sessionFlag != "" && sessionID == "" {
		sessionID = sessionFlag
	}

	// If using environment variable for scenario
	if envScenario := os.Getenv("CLAUDE_MOCK_SCENARIO"); envScenario != "" {
		scenario = envScenario
	}

	config := ClaudeMockConfig{
		Scenario:       ClaudeMockScenario(scenario),
		SessionID:      sessionID,
		MessageCount:   messageCount,
		Delay:          time.Duration(delay) * time.Millisecond,
		ExitCode:       exitCode,
		StderrOutput:   stderr,
		CustomResponse: customJSON,
	}

	// Generate response
	response, err := GenerateClaudeResponse(config)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error generating response: %v\n", err)
		os.Exit(1)
	}

	// Output stderr if configured
	if config.StderrOutput != "" {
		fmt.Fprint(os.Stderr, config.StderrOutput)
	}

	// Output response
	if response != "" {
		fmt.Print(response)
	}

	// Exit with configured code
	os.Exit(config.ExitCode)
}

// CreateMockClaudeScript creates a shell script that acts as a mock Claude executable
func CreateMockClaudeScript(scriptPath string, config ClaudeMockConfig) error {
	script := fmt.Sprintf(`#!/bin/sh
# Mock Claude CLI for testing
# This script simulates Claude CLI behavior for deterministic testing

SCENARIO="%s"
SESSION_ID="%s"
EXIT_CODE=%d
DELAY=%d
MESSAGE_COUNT=%d

# Parse arguments
while [ $# -gt 0 ]; do
  case "$1" in
    -c) SESSION_ID="$2"; shift 2 ;;
    -p) PROJECT_PATH="$2"; shift 2 ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    *) shift ;;
  esac
done

# Generate response based on scenario
case "$SCENARIO" in
  "success")
    if [ -z "$SESSION_ID" ]; then
      SESSION_ID="session-$(date +%%s)"
    fi
    echo '{"session_id": "'$SESSION_ID'", "messages": [{"type": "text", "content": {"text": "Mock response"}}]}'
    ;;
  "error")
    echo '{"error": "Mock error", "messages": [{"type": "error", "error": "Failed"}]}'
    exit 1
    ;;
  "timeout")
    sleep 3600
    ;;
  "invalid_json")
    echo "Invalid JSON {"
    ;;
  "empty")
    # Output nothing
    ;;
  *)
    echo '{"session_id": "default", "messages": [{"type": "text", "content": {"text": "Default mock"}}]}'
    ;;
esac

exit $EXIT_CODE
`, config.Scenario, config.SessionID, config.ExitCode, int(config.Delay.Milliseconds()), config.MessageCount)

	// Write script to file
	if err := os.WriteFile(scriptPath, []byte(script), 0o755); err != nil {
		return fmt.Errorf("failed to write mock script: %w", err)
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
		sessionID: fmt.Sprintf("mock-session-%d", rand.Int63()),
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
