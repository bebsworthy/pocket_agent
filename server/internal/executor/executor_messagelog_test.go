package executor

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// mockMessageLog tracks append calls
type mockMessageLog struct {
	appendCount int
	messages    []models.TimestampedMessage
	closed      bool
}

func (m *mockMessageLog) Append(msg models.TimestampedMessage) error {
	m.appendCount++
	m.messages = append(m.messages, msg)
	return nil
}

func (m *mockMessageLog) GetMessagesSince(since time.Time) ([]models.TimestampedMessage, error) {
	var result []models.TimestampedMessage
	for _, msg := range m.messages {
		if msg.Timestamp.After(since) {
			result = append(result, msg)
		}
	}
	return result, nil
}

func (m *mockMessageLog) Close() error {
	m.closed = true
	return nil
}

// TestExecutorUsesProjectMessageLog verifies that the executor uses the project's existing MessageLog
func TestExecutorUsesProjectMessageLog(t *testing.T) {
	tempDir := t.TempDir()

	// Create test Claude binary
	claudeBin := filepath.Join(tempDir, "claude")
	createMockClaudeBinary(t, claudeBin, `{"type":"message_start"}
{"type":"content_block_start","content_block":{"type":"text"}}
{"type":"content_block_delta","delta":{"text":"Hello"}}
{"type":"content_block_stop"}
{"type":"message_stop"}`)

	config := Config{
		ClaudePath:              claudeBin,
		DefaultTimeout:          5 * time.Second,
		MaxConcurrentExecutions: 1,
	}

	executor, err := NewClaudeExecutor(config)
	require.NoError(t, err)
	defer executor.Shutdown(context.Background())

	// Create a mock message log
	mockLog := &mockMessageLog{}

	// Create a project with the mock message log
	project := &models.Project{
		ID:         "test-project",
		Path:       tempDir,
		State:      models.StateIdle,
		MessageLog: mockLog,
	}

	// Execute multiple times
	numExecutions := 3
	for i := 0; i < numExecutions; i++ {
		cmd := ExecuteCommand{
			Prompt: "Test prompt",
		}

		result, err := executor.Execute(project, cmd)
		require.NoError(t, err)
		assert.NotNil(t, result)

		// Wait a bit between executions
		time.Sleep(100 * time.Millisecond)
	}

	// Verify the mock log was used
	assert.True(t, mockLog.appendCount > 0, "MessageLog should have been used")
	assert.False(t, mockLog.closed, "MessageLog should NOT have been closed")

	// Each execution should append at least 2 messages (user prompt + Claude response)
	expectedMinMessages := numExecutions * 2
	assert.GreaterOrEqual(t, mockLog.appendCount, expectedMinMessages,
		"Should have logged at least %d messages for %d executions", expectedMinMessages, numExecutions)
}

// TestExecutorHandlesNilMessageLog verifies executor works when MessageLog is nil
func TestExecutorHandlesNilMessageLog(t *testing.T) {
	tempDir := t.TempDir()

	// Create test Claude binary
	claudeBin := filepath.Join(tempDir, "claude")
	createMockClaudeBinary(t, claudeBin, `{"type":"message_start"}
{"type":"content_block_start","content_block":{"type":"text"}}
{"type":"content_block_delta","delta":{"text":"Hello"}}
{"type":"content_block_stop"}
{"type":"message_stop"}`)

	config := Config{
		ClaudePath:              claudeBin,
		DefaultTimeout:          5 * time.Second,
		MaxConcurrentExecutions: 1,
	}

	executor, err := NewClaudeExecutor(config)
	require.NoError(t, err)
	defer executor.Shutdown(context.Background())

	// Create a project with nil MessageLog
	project := &models.Project{
		ID:         "test-project",
		Path:       tempDir,
		State:      models.StateIdle,
		MessageLog: nil, // Explicitly nil
	}

	cmd := ExecuteCommand{
		Prompt: "Test prompt",
	}

	// Should not panic or error
	result, err := executor.Execute(project, cmd)
	require.NoError(t, err)
	assert.NotNil(t, result)
}

// createMockClaudeBinary creates a mock executable that outputs the given response
func createMockClaudeBinary(t *testing.T, path, response string) {
	script := `#!/bin/bash
echo '` + response + `'
`
	err := os.WriteFile(path, []byte(script), 0o755)
	require.NoError(t, err)
}
