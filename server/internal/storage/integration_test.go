package storage

import (
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
)

func TestStorageIntegration(t *testing.T) {
	// Create temp directory
	tempDir, err := os.MkdirTemp("", "storage_integration_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	t.Run("FactoryIntegration", func(t *testing.T) {
		factory := NewFactory(tempDir)

		// Ensure directories
		if err := factory.EnsureDirectories(); err != nil {
			t.Fatalf("Failed to ensure directories: %v", err)
		}

		// Create project persistence
		pp, err := factory.CreateProjectPersistence()
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Create and save a project
		project := models.NewProject("test-project", "/test/path")
		project.SessionID = "test-session"

		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Create message log for the project
		ml, err := factory.CreateMessageLog(project.ID)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}
		defer ml.Close()

		// Write some messages
		messages := []models.TimestampedMessage{
			{
				Timestamp: time.Now(),
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"text":"Hello from client"}`),
				},
				Direction: "client",
			},
			{
				Timestamp: time.Now().Add(1 * time.Second),
				Message: models.ClaudeMessage{
					Type:    "text",
					Content: json.RawMessage(`{"text":"Hello from Claude"}`),
				},
				Direction: "claude",
			},
		}

		for _, msg := range messages {
			if err := ml.Append(msg); err != nil {
				t.Fatalf("Failed to append message: %v", err)
			}
		}

		// Load projects
		projects, err := pp.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load projects: %v", err)
		}

		if len(projects) != 1 {
			t.Errorf("Expected 1 project, got %d", len(projects))
		}

		// Get messages
		msgs, err := ml.GetMessagesSince(time.Now().Add(-1 * time.Hour))
		if err != nil {
			t.Fatalf("Failed to get messages: %v", err)
		}

		if len(msgs) != 2 {
			t.Errorf("Expected 2 messages, got %d", len(msgs))
		}
	})

	t.Run("ProjectLifecycle", func(t *testing.T) {
		factory := NewFactory(tempDir)
		factory.EnsureDirectories()

		pp, _ := factory.CreateProjectPersistence()
		cr := NewCorruptionRecovery(pp)

		// Create project
		project := models.NewProject("lifecycle-test", "/lifecycle/path")

		// Save
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Create message log
		ml, err := factory.CreateMessageLog(project.ID)
		if err != nil {
			t.Fatalf("Failed to create message log: %v", err)
		}

		// Add messages
		msg := models.TimestampedMessage{
			Timestamp: time.Now(),
			Message: models.ClaudeMessage{
				Type:    "execution",
				Content: json.RawMessage(`{"status":"started"}`),
			},
			Direction: "claude",
		}
		if err := ml.Append(msg); err != nil {
			t.Fatalf("Failed to append message: %v", err)
		}

		ml.Close()

		// Update project
		project.SessionID = "new-session"
		project.UpdateState(models.StateExecuting)

		// Create backup before update
		cr.CreateBackup(project.ID)

		// Save update
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to update project: %v", err)
		}

		// Simulate restart - create new instances
		factory2 := NewFactory(tempDir)
		pp2, err := factory2.CreateProjectPersistence()
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Load projects
		projects, err := pp2.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load projects after restart: %v", err)
		}

		// Find our project
		var loaded *models.Project
		for _, p := range projects {
			if p.ID == project.ID {
				loaded = p
				break
			}
		}

		if loaded == nil {
			t.Fatal("Project not found after restart")
		}

		if loaded.SessionID != "new-session" {
			t.Error("Session ID not persisted")
		}

		// Re-open message log
		ml2, err := factory2.CreateMessageLog(loaded.ID)
		if err != nil {
			t.Fatalf("Failed to reopen message log: %v", err)
		}
		defer ml2.Close()

		// Verify messages persisted
		msgs, err := ml2.GetMessagesSince(time.Now().Add(-1 * time.Hour))
		if err != nil {
			t.Errorf("Failed to get messages: %v", err)
		}
		if len(msgs) != 1 {
			t.Errorf("Messages not persisted: expected 1, got %d", len(msgs))
		}

		// Delete project
		if err := pp2.DeleteProjectData(project.ID); err != nil {
			t.Fatalf("Failed to delete project: %v", err)
		}

		// Verify deletion
		projects, _ = pp2.LoadProjects()
		found := false
		for _, p := range projects {
			if p.ID == project.ID {
				found = true
				break
			}
		}
		if found {
			t.Error("Project not deleted")
		}
	})

	t.Run("ConcurrentAccess", func(t *testing.T) {
		factory := NewFactory(tempDir)
		factory.EnsureDirectories()

		pp, _ := factory.CreateProjectPersistence()

		// Create project
		project := models.NewProject("concurrent-test", "/concurrent/path")
		pp.SaveProjectMetadata(project)

		// Create message log
		ml, _ := factory.CreateMessageLog(project.ID)
		defer ml.Close()

		// Concurrent writes
		done := make(chan bool, 2)

		// Writer 1
		go func() {
			for i := 0; i < 10; i++ {
				msg := models.TimestampedMessage{
					Timestamp: time.Now(),
					Message: models.ClaudeMessage{
						Type:    "text",
						Content: json.RawMessage(`{"writer":1}`),
					},
					Direction: "client",
				}
				ml.Append(msg)
			}
			done <- true
		}()

		// Writer 2
		go func() {
			for i := 0; i < 10; i++ {
				msg := models.TimestampedMessage{
					Timestamp: time.Now(),
					Message: models.ClaudeMessage{
						Type:    "text",
						Content: json.RawMessage(`{"writer":2}`),
					},
					Direction: "client",
				}
				ml.Append(msg)
			}
			done <- true
		}()

		// Wait for completion
		<-done
		<-done

		// Verify all messages written
		msgs, _ := ml.GetMessagesSince(time.Now().Add(-1 * time.Hour))
		if len(msgs) != 20 {
			t.Errorf("Expected 20 messages, got %d", len(msgs))
		}
	})
}
