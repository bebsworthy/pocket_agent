package project

import (
	"os"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
)

func TestUpdateProjectState(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/stateproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	tests := []struct {
		name      string
		projectID string
		state     models.State
		wantErr   bool
	}{
		{
			name:      "valid state update",
			projectID: project.ID,
			state:     models.StateExecuting,
			wantErr:   false,
		},
		{
			name:      "non-existent project",
			projectID: "non-existent-12345678-1234-1234-1234",
			state:     models.StateExecuting,
			wantErr:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := manager.UpdateProjectState(tt.projectID, tt.state)
			if (err != nil) != tt.wantErr {
				t.Errorf("UpdateProjectState() error = %v, wantErr %v", err, tt.wantErr)
			}

			if !tt.wantErr {
				// Verify state was updated
				proj, _ := manager.GetProjectByID(tt.projectID)
				if proj.State != tt.state {
					t.Errorf("expected state %s, got %s", tt.state, proj.State)
				}
			}
		})
	}
}

func TestSetProjectError(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/errorproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	errorDetails := "test error occurred"

	err := manager.SetProjectError(project.ID, errorDetails)
	if err != nil {
		t.Errorf("SetProjectError() unexpected error: %v", err)
	}

	// Verify error state
	proj, _ := manager.GetProjectByID(project.ID)
	if proj.State != models.StateError {
		t.Errorf("expected state %s, got %s", models.StateError, proj.State)
	}
	if proj.ErrorDetails != errorDetails {
		t.Errorf("expected error details %q, got %q", errorDetails, proj.ErrorDetails)
	}
}

func TestUpdateProjectSession(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/sessionproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	newSessionID := "new-session-123"

	err := manager.UpdateProjectSession(project.ID, newSessionID)
	if err != nil {
		t.Errorf("UpdateProjectSession() unexpected error: %v", err)
	}

	// Verify session was updated
	proj, _ := manager.GetProjectByID(project.ID)
	if proj.SessionID != newSessionID {
		t.Errorf("expected session ID %s, got %s", newSessionID, proj.SessionID)
	}
}

func TestClearProjectSession(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project with session
	validPath := tempDir + "/clearproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)
	manager.UpdateProjectSession(project.ID, "session-to-clear")

	err := manager.ClearProjectSession(project.ID)
	if err != nil {
		t.Errorf("ClearProjectSession() unexpected error: %v", err)
	}

	// Verify session was cleared
	proj, _ := manager.GetProjectByID(project.ID)
	if proj.SessionID != "" {
		t.Errorf("expected empty session ID, got %s", proj.SessionID)
	}
}

func TestProjectSubscribers(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/subproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	// Create mock sessions
	session1 := &models.Session{ID: "session-1"}
	session2 := &models.Session{ID: "session-2"}

	// Test adding subscribers
	err := manager.AddProjectSubscriber(project.ID, session1)
	if err != nil {
		t.Errorf("AddProjectSubscriber() unexpected error: %v", err)
	}

	err = manager.AddProjectSubscriber(project.ID, session2)
	if err != nil {
		t.Errorf("AddProjectSubscriber() unexpected error: %v", err)
	}

	// Test nil session
	err = manager.AddProjectSubscriber(project.ID, nil)
	if err == nil {
		t.Error("expected error for nil session")
	}

	// Get subscribers
	subscribers, err := manager.GetProjectSubscribers(project.ID)
	if err != nil {
		t.Errorf("GetProjectSubscribers() unexpected error: %v", err)
	}
	if len(subscribers) != 2 {
		t.Errorf("expected 2 subscribers, got %d", len(subscribers))
	}

	// Remove subscriber
	err = manager.RemoveProjectSubscriber(project.ID, "session-1")
	if err != nil {
		t.Errorf("RemoveProjectSubscriber() unexpected error: %v", err)
	}

	// Verify removal
	subscribers, _ = manager.GetProjectSubscribers(project.ID)
	if len(subscribers) != 1 {
		t.Errorf("expected 1 subscriber after removal, got %d", len(subscribers))
	}
}

func TestIsProjectExecuting(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/execproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	// Initially not executing
	executing, err := manager.IsProjectExecuting(project.ID)
	if err != nil {
		t.Errorf("IsProjectExecuting() unexpected error: %v", err)
	}
	if executing {
		t.Error("expected project to not be executing initially")
	}

	// Set to executing
	manager.UpdateProjectState(project.ID, models.StateExecuting)

	executing, err = manager.IsProjectExecuting(project.ID)
	if err != nil {
		t.Errorf("IsProjectExecuting() unexpected error: %v", err)
	}
	if !executing {
		t.Error("expected project to be executing")
	}
}

func TestCanProjectExecute(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/canexecproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	// Initially can execute
	err := manager.CanProjectExecute(project.ID)
	if err != nil {
		t.Errorf("CanProjectExecute() unexpected error: %v", err)
	}

	// Set to executing
	manager.UpdateProjectState(project.ID, models.StateExecuting)

	// Now cannot execute
	err = manager.CanProjectExecute(project.ID)
	if err == nil {
		t.Error("expected error when project is already executing")
	}
	appErr, ok := err.(*errors.AppError)
	if !ok || appErr.Code != errors.CodeProcessActive {
		t.Errorf("expected ProcessActive error, got %v", err)
	}
}

func TestTransitionProjectState(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/transproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	tests := []struct {
		name    string
		from    models.State
		to      models.State
		wantErr bool
	}{
		{
			name:    "valid transition",
			from:    models.StateIdle,
			to:      models.StateExecuting,
			wantErr: false,
		},
		{
			name:    "invalid from state",
			from:    models.StateError,
			to:      models.StateExecuting,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Reset to idle state
			manager.UpdateProjectState(project.ID, models.StateIdle)

			err := manager.TransitionProjectState(project.ID, tt.from, tt.to)
			if (err != nil) != tt.wantErr {
				t.Errorf("TransitionProjectState() error = %v, wantErr %v", err, tt.wantErr)
			}

			if !tt.wantErr {
				// Verify transition occurred
				proj, _ := manager.GetProjectByID(project.ID)
				if proj.State != tt.to {
					t.Errorf("expected state %s, got %s", tt.to, proj.State)
				}
			}
		})
	}
}

func TestGetProjectStats(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test projects in different states
	for i := 0; i < 3; i++ {
		path := tempDir + "/statproject" + string(rune('0'+i))
		os.MkdirAll(path, 0o755)
		project, _ := manager.CreateProject(path)

		// Set different states
		switch i {
		case 0:
			manager.UpdateProjectState(project.ID, models.StateIdle)
		case 1:
			manager.UpdateProjectState(project.ID, models.StateExecuting)
		case 2:
			manager.SetProjectError(project.ID, "test error")
		}

		// Add a subscriber to one project
		if i == 0 {
			session := &models.Session{ID: "stats-session"}
			manager.AddProjectSubscriber(project.ID, session)
		}
	}

	stats := manager.GetProjectStats()

	// Verify stats
	if total, ok := stats["total_projects"].(int); !ok || total != 3 {
		t.Errorf("expected total_projects 3, got %v", stats["total_projects"])
	}

	if max, ok := stats["max_projects"].(int); !ok || max != manager.maxProjects {
		t.Errorf("expected max_projects %d, got %v", manager.maxProjects, stats["max_projects"])
	}

	states, ok := stats["states"].(map[string]int)
	if !ok {
		t.Fatal("expected states map in stats")
	}

	if states[string(models.StateIdle)] != 1 {
		t.Errorf("expected 1 idle project, got %d", states[string(models.StateIdle)])
	}
	if states[string(models.StateExecuting)] != 1 {
		t.Errorf("expected 1 executing project, got %d", states[string(models.StateExecuting)])
	}
	if states[string(models.StateError)] != 1 {
		t.Errorf("expected 1 error project, got %d", states[string(models.StateError)])
	}

	if subscribers, ok := stats["total_subscribers"].(int); !ok || subscribers != 1 {
		t.Errorf("expected total_subscribers 1, got %v", stats["total_subscribers"])
	}
}

func TestStateConcurrency(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/concproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	// Test concurrent state updates
	var wg sync.WaitGroup
	states := []models.State{models.StateIdle, models.StateExecuting, models.StateError}

	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			state := states[idx%len(states)]
			manager.UpdateProjectState(project.ID, state)
		}(i)
	}

	// Test concurrent subscriber operations
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			session := &models.Session{ID: "concurrent-" + string(rune('0'+idx))}
			manager.AddProjectSubscriber(project.ID, session)
			time.Sleep(10 * time.Millisecond)
			manager.RemoveProjectSubscriber(project.ID, session.ID)
		}(i)
	}

	// Test concurrent reads
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			manager.IsProjectExecuting(project.ID)
			manager.GetProjectSubscribers(project.ID)
			manager.GetProjectStats()
		}()
	}

	wg.Wait()
	// No panic means success
}
