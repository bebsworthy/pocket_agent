package mocks

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// ClaudeMockExecutable helps create mock Claude executables for testing
type ClaudeMockExecutable struct {
	path      string
	tempDir   string
	config    ClaudeMockConfig
	isCreated bool
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

// WithSessionID sets the session ID to return
func (m *ClaudeMockExecutable) WithSessionID(sessionID string) *ClaudeMockExecutable {
	m.config.SessionID = sessionID
	return m
}

// WithExitCode sets the exit code
func (m *ClaudeMockExecutable) WithExitCode(code int) *ClaudeMockExecutable {
	m.config.ExitCode = code
	return m
}

// WithDelay sets response delay
func (m *ClaudeMockExecutable) WithDelay(delay time.Duration) *ClaudeMockExecutable {
	m.config.Delay = delay
	return m
}

// WithMessageCount sets the number of messages for multi-message scenario
func (m *ClaudeMockExecutable) WithMessageCount(count int) *ClaudeMockExecutable {
	m.config.MessageCount = count
	return m
}

// WithStderr sets stderr output
func (m *ClaudeMockExecutable) WithStderr(stderr string) *ClaudeMockExecutable {
	m.config.StderrOutput = stderr
	return m
}

// WithCustomResponse sets a custom JSON response
func (m *ClaudeMockExecutable) WithCustomResponse(response string) *ClaudeMockExecutable {
	m.config.CustomResponse = response
	return m
}

// Create creates the mock executable
func (m *ClaudeMockExecutable) Create() error {
	if m.isCreated {
		return fmt.Errorf("mock already created")
	}

	if err := CreateMockClaudeScript(m.path, m.config); err != nil {
		return err
	}

	m.isCreated = true
	return nil
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

// CreateQuickMock creates a simple mock executable for common scenarios
func CreateQuickMock(t *testing.T, scenario ClaudeMockScenario) (path string, cleanup func()) {
	mock := NewClaudeMockExecutable(t).WithScenario(scenario)
	path = mock.MustCreate(t)
	return path, mock.Cleanup
}

// PredefinedResponses provides common test responses
var PredefinedResponses = struct {
	SimpleSuccess    string
	MultiMessage     string
	ErrorResponse    string
	SessionContinue  string
	CodeGeneration   string
	LongExplanation  string
}{
	SimpleSuccess: `{
		"session_id": "test-session-123",
		"messages": [{
			"type": "text",
			"content": {"text": "Hello! I've successfully processed your request."}
		}]
	}`,
	
	MultiMessage: `{
		"session_id": "test-session-456",
		"messages": [
			{
				"type": "text",
				"content": {"text": "I'll help you with that. Let me break it down:"}
			},
			{
				"type": "text",
				"content": {"text": "First, we need to understand the problem."}
			},
			{
				"type": "code",
				"content": {"language": "go", "code": "func example() {\n\treturn \"example\"\n}"}
			},
			{
				"type": "text",
				"content": {"text": "This code demonstrates the solution."}
			}
		]
	}`,
	
	ErrorResponse: `{
		"session_id": "",
		"error": "Failed to process request",
		"messages": [{
			"type": "error",
			"error": "Invalid prompt format"
		}]
	}`,
	
	SessionContinue: `{
		"session_id": "existing-session-789",
		"messages": [{
			"type": "text",
			"content": {"text": "Continuing from our previous conversation..."}
		}]
	}`,
	
	CodeGeneration: `{
		"session_id": "code-gen-session",
		"messages": [
			{
				"type": "text",
				"content": {"text": "Here's the implementation you requested:"}
			},
			{
				"type": "code",
				"content": {
					"language": "go",
					"code": "package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"Hello, World!\")\n}"
				}
			}
		]
	}`,
	
	LongExplanation: `{
		"session_id": "explanation-session",
		"messages": [{
			"type": "text",
			"content": {
				"text": "This is a comprehensive explanation that covers multiple aspects of the topic. It includes detailed analysis, examples, and best practices. The response is designed to test handling of longer messages and ensure proper parsing and display. It may contain multiple paragraphs and technical details that need to be properly formatted and presented to the user. This helps test the system's ability to handle real-world Claude responses that can be quite verbose and detailed."
			}
		}]
	}`,
}

// TestResponseGenerator generates test responses dynamically
type TestResponseGenerator struct {
	sessionCounter int
}

// NewTestResponseGenerator creates a new response generator
func NewTestResponseGenerator() *TestResponseGenerator {
	return &TestResponseGenerator{}
}

// GenerateSuccess generates a success response with incrementing session IDs
func (g *TestResponseGenerator) GenerateSuccess(text string) string {
	g.sessionCounter++
	sessionID := fmt.Sprintf("test-session-%d", g.sessionCounter)
	
	builder := NewClaudeMockBuilder().
		WithSessionID(sessionID).
		WithTextMessage(text)
	
	response, _ := builder.Build()
	return response
}

// GenerateMultiStep generates a multi-step response
func (g *TestResponseGenerator) GenerateMultiStep(steps []string) string {
	g.sessionCounter++
	sessionID := fmt.Sprintf("multi-session-%d", g.sessionCounter)
	
	builder := NewClaudeMockBuilder().WithSessionID(sessionID)
	
	for _, step := range steps {
		builder.WithTextMessage(step)
	}
	
	response, _ := builder.Build()
	return response
}

// GenerateWithCode generates a response with code
func (g *TestResponseGenerator) GenerateWithCode(explanation, language, code string) string {
	g.sessionCounter++
	sessionID := fmt.Sprintf("code-session-%d", g.sessionCounter)
	
	builder := NewClaudeMockBuilder().
		WithSessionID(sessionID).
		WithTextMessage(explanation).
		WithCodeMessage(language, code)
	
	response, _ := builder.Build()
	return response
}