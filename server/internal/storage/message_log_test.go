package storage

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
)

func TestMessageLog(t *testing.T) {
	// Create temp directory for tests
	tempDir, err := os.MkdirTemp("", "msglog_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	t.Run("NewMessageLog", func(t *testing.T) {
		ml, err := NewMessageLog("test-project", tempDir)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Check that log directory was created
		logDir := filepath.Join(tempDir, "test-project")
		if _, err := os.Stat(logDir); os.IsNotExist(err) {
			t.Error("Log directory was not created")
		}
	})

	t.Run("Append and GetMessagesSince", func(t *testing.T) {
		ml, err := NewMessageLog("test-project", tempDir)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Create test messages
		now := time.Now()
		messages := []models.TimestampedMessage{
			{
				Timestamp: now.Add(-2 * time.Hour),
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"text":"Hello"}`),
				},
				Direction: "client",
			},
			{
				Timestamp: now.Add(-1 * time.Hour),
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"text":"World"}`),
				},
				Direction: "claude",
			},
			{
				Timestamp: now,
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"text":"Test"}`),
				},
				Direction: "client",
			},
		}

		// Append messages
		for _, msg := range messages {
			if err := ml.Append(msg); err != nil {
				t.Fatalf("Failed to append message: %v", err)
			}
		}

		// Test GetMessagesSince
		testCases := []struct {
			name     string
			since    time.Time
			expected int
		}{
			{
				name:     "All messages",
				since:    now.Add(-3 * time.Hour),
				expected: 3,
			},
			{
				name:     "Last two messages",
				since:    now.Add(-90 * time.Minute),
				expected: 2,
			},
			{
				name:     "Last message only",
				since:    now.Add(-30 * time.Minute),
				expected: 1,
			},
			{
				name:     "No messages",
				since:    now.Add(1 * time.Hour),
				expected: 0,
			},
		}

		for _, tc := range testCases {
			t.Run(tc.name, func(t *testing.T) {
				msgs, err := ml.GetMessagesSince(tc.since)
				if err != nil {
					t.Fatalf("Failed to get messages: %v", err)
				}
				if len(msgs) != tc.expected {
					t.Errorf("Expected %d messages, got %d", tc.expected, len(msgs))
				}
			})
		}
	})

	t.Run("Message ordering", func(t *testing.T) {
		ml, err := NewMessageLog("test-project-order", tempDir)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Append messages in reverse chronological order
		now := time.Now()
		for i := 5; i >= 0; i-- {
			msg := models.TimestampedMessage{
				Timestamp: now.Add(time.Duration(i) * time.Minute),
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"index":` + string(rune('0'+i)) + `}`),
				},
				Direction: "client",
			}
			if err := ml.Append(msg); err != nil {
				t.Fatalf("Failed to append message: %v", err)
			}
		}

		// Get all messages
		msgs, err := ml.GetMessagesSince(now.Add(-10 * time.Minute))
		if err != nil {
			t.Fatalf("Failed to get messages: %v", err)
		}

		// Verify they are sorted chronologically
		for i := 1; i < len(msgs); i++ {
			if msgs[i-1].Timestamp.After(msgs[i].Timestamp) {
				t.Error("Messages are not sorted chronologically")
			}
		}
	})

	t.Run("Stats", func(t *testing.T) {
		ml, err := NewMessageLog("test-project-stats", tempDir)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Initial stats
		count, size, _ := ml.GetStats()
		if count != 0 || size != 0 {
			t.Error("Initial stats should be zero")
		}

		// Append a message
		msg := models.TimestampedMessage{
			Timestamp: time.Now(),
			Message: models.ClaudeMessage{
				Type:    "text",
				Content: json.RawMessage(`{"text":"Test message"}`),
			},
			Direction: "client",
		}
		if err := ml.Append(msg); err != nil {
			t.Fatalf("Failed to append message: %v", err)
		}

		// Check stats updated
		count, size, _ = ml.GetStats()
		if count != 1 {
			t.Errorf("Expected count 1, got %d", count)
		}
		if size == 0 {
			t.Error("File size should be greater than 0")
		}
	})
}

func TestMessageLogRotation(t *testing.T) {
	// This test would require mocking time or reducing rotation limits
	// For now, we'll test the basic rotation logic
	t.Run("Multiple files", func(t *testing.T) {
		tempDir, err := os.MkdirTemp("", "msglog_rotation_*")
		if err != nil {
			t.Fatalf("Failed to create temp dir: %v", err)
		}
		defer os.RemoveAll(tempDir)

		ml, err := NewMessageLog("test-rotation", tempDir)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Manually create additional log files to simulate rotation
		logDir := filepath.Join(tempDir, "test-rotation")
		oldFile := filepath.Join(logDir, "messages_2024-01-01_12-00-00.jsonl")

		// Write a test message to old file
		oldMsg := models.TimestampedMessage{
			Timestamp: time.Now().Add(-24 * time.Hour),
			Message: models.ClaudeMessage{
				Type:    "text",
				Content: json.RawMessage(`{"text":"Old message"}`),
			},
			Direction: "client",
		}
		data, _ := json.Marshal(oldMsg)
		if err := os.WriteFile(oldFile, append(data, '\n'), 0o644); err != nil {
			t.Fatalf("Failed to write old file: %v", err)
		}

		// Write new message
		newMsg := models.TimestampedMessage{
			Timestamp: time.Now(),
			Message: models.ClaudeMessage{
				Type:    "text",
				Content: json.RawMessage(`{"text":"New message"}`),
			},
			Direction: "client",
		}
		if err := ml.Append(newMsg); err != nil {
			t.Fatalf("Failed to append message: %v", err)
		}

		// Get all messages
		msgs, err := ml.GetMessagesSince(time.Now().Add(-48 * time.Hour))
		if err != nil {
			t.Fatalf("Failed to get messages: %v", err)
		}

		// Should have both messages
		if len(msgs) != 2 {
			t.Errorf("Expected 2 messages, got %d", len(msgs))
		}
	})
}
