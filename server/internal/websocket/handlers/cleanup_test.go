package handlers

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/validation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestOnSessionCleanupRemovesFromProject tests that OnSessionCleanup properly removes sessions from projects
func TestOnSessionCleanupRemovesFromProject(t *testing.T) {
	// Setup
	tempDir := t.TempDir()
	validator := validation.NewValidator()
	projectCfg := project.Config{
		DataDir:     tempDir,
		MaxProjects: 10,
		Validator:   validator,
	}
	projectMgr, err := project.NewManager(projectCfg)
	require.NoError(t, err)

	// Create handlers
	config := Config{
		ProjectManager:  projectMgr,
		Logger:          logger.New("error"),
		BroadcastConfig: DefaultBroadcasterConfig(),
	}
	handlers := NewHandlers(config, nil)

	// Create a project
	projectPath := filepath.Join(tempDir, "test-project")
	os.MkdirAll(projectPath, 0o755)
	project, err := projectMgr.CreateProject(projectPath)
	require.NoError(t, err)

	// Create multiple sessions
	sessions := make([]*models.Session, 3)
	for i := 0; i < 3; i++ {
		session := &models.Session{
			ID:        fmt.Sprintf("session-%d", i),
			CreatedAt: time.Now(),
			LastPing:  time.Now(),
		}
		sessions[i] = session

		// Add to project
		session.SetProject(project.ID)
		err = projectMgr.AddSubscriber(project.ID, session)
		require.NoError(t, err)
	}

	// Verify all sessions are subscribed
	assert.Equal(t, 3, project.SubscriberCount())

	// Clean up one session
	handlers.OnSessionCleanup(sessions[0])

	// Verify session was removed
	assert.Equal(t, 2, project.SubscriberCount())
	assert.False(t, project.HasSubscriber(sessions[0].ID))
	assert.True(t, project.HasSubscriber(sessions[1].ID))
	assert.True(t, project.HasSubscriber(sessions[2].ID))

	// Clean up remaining sessions
	handlers.OnSessionCleanup(sessions[1])
	handlers.OnSessionCleanup(sessions[2])

	// Verify all sessions removed
	assert.Equal(t, 0, project.SubscriberCount())
}

// TestOnSessionCleanupHandlesNoProject tests cleanup when session has no project
func TestOnSessionCleanupHandlesNoProject(t *testing.T) {
	// Setup
	tempDir := t.TempDir()
	validator := validation.NewValidator()
	projectCfg := project.Config{
		DataDir:     tempDir,
		MaxProjects: 10,
		Validator:   validator,
	}
	projectMgr, err := project.NewManager(projectCfg)
	require.NoError(t, err)

	// Create handlers
	config := Config{
		ProjectManager:  projectMgr,
		Logger:          logger.New("error"),
		BroadcastConfig: DefaultBroadcasterConfig(),
	}
	handlers := NewHandlers(config, nil)

	// Create session with no project
	session := &models.Session{
		ID:        "no-project-session",
		CreatedAt: time.Now(),
		LastPing:  time.Now(),
		ProjectID: "", // No project
	}

	// Should not panic
	handlers.OnSessionCleanup(session)
}

// TestOnSessionCleanupHandlesDeletedProject tests cleanup when project was deleted
func TestOnSessionCleanupHandlesDeletedProject(t *testing.T) {
	// Setup
	tempDir := t.TempDir()
	validator := validation.NewValidator()
	projectCfg := project.Config{
		DataDir:     tempDir,
		MaxProjects: 10,
		Validator:   validator,
	}
	projectMgr, err := project.NewManager(projectCfg)
	require.NoError(t, err)

	// Create handlers
	config := Config{
		ProjectManager:  projectMgr,
		Logger:          logger.New("error"),
		BroadcastConfig: DefaultBroadcasterConfig(),
	}
	handlers := NewHandlers(config, nil)

	// Create a project
	projectPath := filepath.Join(tempDir, "test-project")
	os.MkdirAll(projectPath, 0o755)
	project, err := projectMgr.CreateProject(projectPath)
	require.NoError(t, err)

	// Create session and add to project
	session := &models.Session{
		ID:        "session-1",
		CreatedAt: time.Now(),
		LastPing:  time.Now(),
	}
	session.SetProject(project.ID)
	err = projectMgr.AddSubscriber(project.ID, session)
	require.NoError(t, err)

	// Delete the project
	err = projectMgr.DeleteProject(project.ID)
	require.NoError(t, err)

	// Cleanup should handle gracefully (project no longer exists)
	handlers.OnSessionCleanup(session)
	// Should not panic or error
}

// TestConcurrentSessionCleanup tests concurrent cleanup operations
func TestConcurrentSessionCleanup(t *testing.T) {
	// Setup
	tempDir := t.TempDir()
	validator := validation.NewValidator()
	projectCfg := project.Config{
		DataDir:     tempDir,
		MaxProjects: 10,
		Validator:   validator,
	}
	projectMgr, err := project.NewManager(projectCfg)
	require.NoError(t, err)

	// Create handlers
	config := Config{
		ProjectManager:  projectMgr,
		Logger:          logger.New("error"),
		BroadcastConfig: DefaultBroadcasterConfig(),
	}
	handlers := NewHandlers(config, nil)

	// Create a project
	projectPath := filepath.Join(tempDir, "test-project")
	os.MkdirAll(projectPath, 0o755)
	project, err := projectMgr.CreateProject(projectPath)
	require.NoError(t, err)

	// Create many sessions
	numSessions := 50
	sessions := make([]*models.Session, numSessions)
	for i := 0; i < numSessions; i++ {
		session := &models.Session{
			ID:        fmt.Sprintf("session-%d", i),
			CreatedAt: time.Now(),
			LastPing:  time.Now(),
		}
		sessions[i] = session

		// Add to project
		session.SetProject(project.ID)
		err = projectMgr.AddSubscriber(project.ID, session)
		require.NoError(t, err)
	}

	// Verify all sessions are subscribed
	assert.Equal(t, numSessions, project.SubscriberCount())

	// Concurrently clean up all sessions
	var wg sync.WaitGroup
	for _, session := range sessions {
		wg.Add(1)
		go func(s *models.Session) {
			defer wg.Done()
			handlers.OnSessionCleanup(s)
		}(session)
	}

	wg.Wait()

	// Verify all sessions were removed
	assert.Equal(t, 0, project.SubscriberCount())
}
