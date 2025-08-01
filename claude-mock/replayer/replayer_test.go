package replayer

import (
	"bytes"
	"encoding/json"
	"io"
	"os"
	"strings"
	"testing"
)

func TestReplayer(t *testing.T) {
	// Create a temporary log file
	tmpFile, err := os.CreateTemp("", "test-log-*.jsonl")
	if err != nil {
		t.Fatal(err)
	}
	defer os.Remove(tmpFile.Name())

	// Write test data
	testData := []string{
		`{"timestamp":"2025-08-01T10:00:00Z","message":{"type":"system","session_id":"test-123","content":{"session_id":"test-123"}},"direction":"claude"}`,
		`{"timestamp":"2025-08-01T10:00:01Z","message":{"type":"assistant","content":{"text":"Hello!"}},"direction":"claude"}`,
		`{"timestamp":"2025-08-01T10:00:02Z","message":{"type":"user","content":{"text":"Hi there"}},"direction":"client"}`,
		`{"timestamp":"2025-08-01T10:00:03Z","message":{"type":"assistant","content":{"text":"How can I help?"}},"direction":"claude"}`,
	}

	for _, line := range testData {
		if _, err := tmpFile.WriteString(line + "\n"); err != nil {
			t.Fatal(err)
		}
	}
	tmpFile.Close()

	// Create replayer
	r, err := New(tmpFile.Name())
	if err != nil {
		t.Fatal(err)
	}

	// Set fast speed for testing
	os.Setenv("CLAUDE_MOCK_DELAY_MS", "1")
	defer os.Unsetenv("CLAUDE_MOCK_DELAY_MS")

	// Capture stdout
	oldStdout := os.Stdout
	pr, pw, _ := os.Pipe()
	os.Stdout = pw

	// Run replay
	go func() {
		if err := r.Replay(); err != nil {
			t.Error(err)
		}
		pw.Close()
	}()

	// Read output
	var buf bytes.Buffer
	io.Copy(&buf, pr)
	os.Stdout = oldStdout

	// Parse output lines
	lines := strings.Split(strings.TrimSpace(buf.String()), "\n")
	if len(lines) != 3 { // Should have 3 Claude messages
		t.Errorf("Expected 3 output lines, got %d", len(lines))
	}

	// Verify first message is system
	var firstMsg map[string]interface{}
	if err := json.Unmarshal([]byte(lines[0]), &firstMsg); err != nil {
		t.Fatal(err)
	}
	if firstMsg["type"] != "system" {
		t.Errorf("Expected first message type to be 'system', got %v", firstMsg["type"])
	}

	// Verify second message is assistant
	var secondMsg map[string]interface{}
	if err := json.Unmarshal([]byte(lines[1]), &secondMsg); err != nil {
		t.Fatal(err)
	}
	if secondMsg["type"] != "assistant" {
		t.Errorf("Expected second message type to be 'assistant', got %v", secondMsg["type"])
	}
}

func TestReplayerWithSessionID(t *testing.T) {
	// Create a temporary log file
	tmpFile, err := os.CreateTemp("", "test-log-*.jsonl")
	if err != nil {
		t.Fatal(err)
	}
	defer os.Remove(tmpFile.Name())

	// Write test data with a system message
	testData := `{"timestamp":"2025-08-01T10:00:00Z","message":{"type":"system","content":{"session_id":"old-session"}},"direction":"claude"}`
	tmpFile.WriteString(testData + "\n")
	tmpFile.Close()

	// Create replayer with custom session ID
	r, err := New(tmpFile.Name())
	if err != nil {
		t.Fatal(err)
	}
	r.SetSessionID("new-session-123")

	// Capture stdout
	oldStdout := os.Stdout
	pr, pw, _ := os.Pipe()
	os.Stdout = pw

	// Run replay
	go func() {
		if err := r.Replay(); err != nil {
			t.Error(err)
		}
		pw.Close()
	}()

	// Read output
	var buf bytes.Buffer
	io.Copy(&buf, pr)
	os.Stdout = oldStdout

	// Parse output
	var msg map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &msg); err != nil {
		t.Fatal(err)
	}

	// Verify session ID was updated
	if msg["session_id"] != "new-session-123" {
		t.Errorf("Expected session_id to be 'new-session-123', got %v", msg["session_id"])
	}

	// Check content also has updated session ID
	if content, ok := msg["content"].(map[string]interface{}); ok {
		if content["session_id"] != "new-session-123" {
			t.Errorf("Expected content.session_id to be 'new-session-123', got %v", content["session_id"])
		}
	}
}