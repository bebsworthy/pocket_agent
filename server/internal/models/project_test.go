package models

import (
	"fmt"
	"testing"
	"time"
)

func TestNewProject(t *testing.T) {
	id := "test-id-123"
	path := "/test/path"

	project := NewProject(id, path)

	if project.ID != id {
		t.Errorf("expected ID %s, got %s", id, project.ID)
	}

	if project.Path != path {
		t.Errorf("expected path %s, got %s", path, project.Path)
	}

	if project.State != StateIdle {
		t.Errorf("expected state %s, got %s", StateIdle, project.State)
	}

	if project.Subscribers == nil {
		t.Error("expected subscribers map to be initialized")
	}

	if project.CreatedAt.IsZero() {
		t.Error("expected CreatedAt to be set")
	}

	if project.LastActive.IsZero() {
		t.Error("expected LastActive to be set")
	}
}

func TestProjectUpdateState(t *testing.T) {
	project := NewProject("test-id", "/test/path")
	oldActive := project.LastActive

	// Sleep to ensure time difference
	time.Sleep(10 * time.Millisecond)

	project.UpdateState(StateExecuting)

	if project.State != StateExecuting {
		t.Errorf("expected state %s, got %s", StateExecuting, project.State)
	}

	if !project.LastActive.After(oldActive) {
		t.Error("expected LastActive to be updated")
	}
}

func TestProjectSetError(t *testing.T) {
	project := NewProject("test-id", "/test/path")
	errorDetails := "test error occurred"

	project.SetError(errorDetails)

	if project.State != StateError {
		t.Errorf("expected state %s, got %s", StateError, project.State)
	}

	if project.ErrorDetails != errorDetails {
		t.Errorf("expected error details %s, got %s", errorDetails, project.ErrorDetails)
	}
}

func TestProjectSubscribers(t *testing.T) {
	project := NewProject("test-id", "/test/path")
	session1 := &Session{ID: "session-1"}
	session2 := &Session{ID: "session-2"}

	// Add subscribers
	project.AddSubscriber(session1)
	project.AddSubscriber(session2)

	if len(project.Subscribers) != 2 {
		t.Errorf("expected 2 subscribers, got %d", len(project.Subscribers))
	}

	// Get subscribers
	subscribers := project.GetSubscribers()
	if len(subscribers) != 2 {
		t.Errorf("expected 2 subscribers from GetSubscribers, got %d", len(subscribers))
	}

	// Remove subscriber
	project.RemoveSubscriber("session-1")

	if len(project.Subscribers) != 1 {
		t.Errorf("expected 1 subscriber after removal, got %d", len(project.Subscribers))
	}

	if _, exists := project.Subscribers["session-2"]; !exists {
		t.Error("expected session-2 to still exist")
	}
}

func TestProjectMetadataConversion(t *testing.T) {
	project := NewProject("test-id", "/test/path")
	project.SessionID = "session-123"
	project.ErrorDetails = "some error"

	// Convert to metadata
	meta := project.ToMetadata()

	if meta.ID != project.ID {
		t.Errorf("expected metadata ID %s, got %s", project.ID, meta.ID)
	}

	if meta.Path != project.Path {
		t.Errorf("expected metadata path %s, got %s", project.Path, meta.Path)
	}

	if meta.SessionID != project.SessionID {
		t.Errorf("expected metadata session ID %s, got %s", project.SessionID, meta.SessionID)
	}

	if meta.ErrorDetails != project.ErrorDetails {
		t.Errorf("expected metadata error details %s, got %s", project.ErrorDetails, meta.ErrorDetails)
	}

	// Convert from metadata
	restored := FromMetadata(meta)

	if restored.ID != meta.ID {
		t.Errorf("expected restored ID %s, got %s", meta.ID, restored.ID)
	}

	if restored.Path != meta.Path {
		t.Errorf("expected restored path %s, got %s", meta.Path, restored.Path)
	}

	if restored.SessionID != meta.SessionID {
		t.Errorf("expected restored session ID %s, got %s", meta.SessionID, restored.SessionID)
	}

	// State should always be idle after restore
	if restored.State != StateIdle {
		t.Errorf("expected restored state %s, got %s", StateIdle, restored.State)
	}

	if restored.Subscribers == nil {
		t.Error("expected subscribers map to be initialized")
	}
}

func TestProjectValidate(t *testing.T) {
	tests := []struct {
		name    string
		project *Project
		wantErr bool
	}{
		{
			name:    "valid project",
			project: NewProject("test-id", "/test/path"),
			wantErr: false,
		},
		{
			name:    "empty ID",
			project: &Project{Path: "/test/path"},
			wantErr: true,
		},
		{
			name:    "empty path",
			project: &Project{ID: "test-id"},
			wantErr: true,
		},
		{
			name:    "both empty",
			project: &Project{},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.project.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestProjectCopy(t *testing.T) {
	// Create original project
	original := NewProject("test-id", "/test/path")
	original.SessionID = "session-123"
	original.State = StateExecuting
	original.ErrorDetails = "some error"
	original.CreatedAt = time.Now().Add(-1 * time.Hour)
	original.LastActive = time.Now().Add(-5 * time.Minute)

	// Add a subscriber to verify it's not copied
	session := &Session{ID: "session1"}
	original.AddSubscriber(session)

	// Create copy
	copy := original.Copy()

	// Verify all fields are copied correctly
	if copy.ID != original.ID {
		t.Errorf("ID not copied: got %s, want %s", copy.ID, original.ID)
	}
	if copy.Path != original.Path {
		t.Errorf("Path not copied: got %s, want %s", copy.Path, original.Path)
	}
	if copy.SessionID != original.SessionID {
		t.Errorf("SessionID not copied: got %s, want %s", copy.SessionID, original.SessionID)
	}
	if copy.State != original.State {
		t.Errorf("State not copied: got %s, want %s", copy.State, original.State)
	}
	if copy.ErrorDetails != original.ErrorDetails {
		t.Errorf("ErrorDetails not copied: got %s, want %s", copy.ErrorDetails, original.ErrorDetails)
	}
	if !copy.CreatedAt.Equal(original.CreatedAt) {
		t.Errorf("CreatedAt not copied: got %v, want %v", copy.CreatedAt, original.CreatedAt)
	}
	if !copy.LastActive.Equal(original.LastActive) {
		t.Errorf("LastActive not copied: got %v, want %v", copy.LastActive, original.LastActive)
	}

	// Verify subscribers are not copied
	if len(copy.Subscribers) != 0 {
		t.Errorf("Subscribers should be empty in copy, got %d", len(copy.Subscribers))
	}

	// Verify copy is independent - modify copy and check original is unchanged
	copy.Path = "/modified/path"
	copy.State = StateError

	if original.Path == "/modified/path" {
		t.Error("Modifying copy affected original path")
	}
	if original.State == StateError {
		t.Error("Modifying copy affected original state")
	}

	// Verify original still has its subscriber
	if !original.HasSubscriber("session1") {
		t.Error("Original lost its subscriber")
	}
}

func TestProjectCopyThreadSafety(t *testing.T) {
	// Create project
	project := NewProject("test-id", "/test/path")
	project.State = StateExecuting

	// Concurrently copy and modify
	done := make(chan bool)

	// Multiple readers copying
	for i := 0; i < 5; i++ {
		go func() {
			for j := 0; j < 100; j++ {
				copy := project.Copy()
				if copy.ID != project.ID {
					t.Error("Copy corrupted during concurrent access")
				}
			}
			done <- true
		}()
	}

	// Writer modifying state
	go func() {
		for j := 0; j < 100; j++ {
			if j%2 == 0 {
				project.UpdateState(StateIdle)
			} else {
				project.UpdateState(StateExecuting)
			}
		}
		done <- true
	}()

	// Wait for all goroutines
	for i := 0; i < 6; i++ {
		<-done
	}
}

func TestProjectConcurrency(t *testing.T) {
	project := NewProject("test-id", "/test/path")

	// Test concurrent access to subscribers
	done := make(chan bool)

	// Goroutine 1: Add subscribers
	go func() {
		for i := 0; i < 100; i++ {
			session := &Session{ID: fmt.Sprintf("session-%d", i)}
			project.AddSubscriber(session)
		}
		done <- true
	}()

	// Goroutine 2: Remove subscribers
	go func() {
		for i := 0; i < 50; i++ {
			project.RemoveSubscriber(fmt.Sprintf("session-%d", i))
		}
		done <- true
	}()

	// Goroutine 3: Get subscribers
	go func() {
		for i := 0; i < 100; i++ {
			_ = project.GetSubscribers()
		}
		done <- true
	}()

	// Goroutine 4: Update state
	go func() {
		states := []State{StateIdle, StateExecuting, StateError}
		for i := 0; i < 100; i++ {
			project.UpdateState(states[i%3])
		}
		done <- true
	}()

	// Wait for all goroutines
	for i := 0; i < 4; i++ {
		<-done
	}

	// Verify data integrity
	subscribers := project.GetSubscribers()
	if len(subscribers) > 100 {
		t.Errorf("expected at most 100 subscribers, got %d", len(subscribers))
	}
}
