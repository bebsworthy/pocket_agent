package project

import (
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// UpdateProjectState updates the state of a project with persistence
func (m *Manager) UpdateProjectState(projectID string, state models.State) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Update state (thread-safe)
	project.UpdateState(state)

	// Persist the change
	if err := m.UpdateProject(project); err != nil {
		// Rollback state on persistence failure
		project.UpdateState(models.StateError)
		return err
	}

	m.logger.Info("Project state updated",
		"project_id", projectID,
		"state", state)

	return nil
}

// SetProjectError updates project to error state with details
func (m *Manager) SetProjectError(projectID string, errorDetails string) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Set error state (thread-safe)
	project.SetError(errorDetails)

	// Persist the change
	if err := m.UpdateProject(project); err != nil {
		return err
	}

	m.logger.Error("Project entered error state",
		"project_id", projectID,
		"error", errorDetails)

	return nil
}

// UpdateProjectSession updates the Claude session ID for a project
func (m *Manager) UpdateProjectSession(projectID string, sessionID string) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Lock project for update
	project.Lock()
	oldSessionID := project.SessionID
	project.SessionID = sessionID
	project.LastActive = time.Now()
	project.Unlock()

	// Persist the change
	if err := m.UpdateProject(project); err != nil {
		// Rollback on failure
		project.Lock()
		project.SessionID = oldSessionID
		project.Unlock()
		return err
	}

	m.logger.Info("Project session updated",
		"project_id", projectID,
		"old_session_id", oldSessionID,
		"new_session_id", sessionID)

	return nil
}

// ClearProjectSession clears the Claude session ID for a project
func (m *Manager) ClearProjectSession(projectID string) error {
	return m.UpdateProjectSession(projectID, "")
}

// AddProjectSubscriber adds a WebSocket session as a subscriber to a project
func (m *Manager) AddProjectSubscriber(projectID string, session *models.Session) error {
	if session == nil {
		return errors.NewValidationError("session cannot be nil")
	}

	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Add subscriber (thread-safe)
	project.AddSubscriber(session)

	m.logger.Debug("Subscriber added to project",
		"project_id", projectID,
		"session_id", session.ID,
		"subscriber_count", project.SubscriberCount())

	return nil
}

// RemoveProjectSubscriber removes a WebSocket session from project subscribers
func (m *Manager) RemoveProjectSubscriber(projectID string, sessionID string) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Remove subscriber (thread-safe)
	project.RemoveSubscriber(sessionID)

	m.logger.Debug("Subscriber removed from project",
		"project_id", projectID,
		"session_id", sessionID,
		"subscriber_count", project.SubscriberCount())

	return nil
}

// GetProjectSubscribers returns all subscribers for a project
func (m *Manager) GetProjectSubscribers(projectID string) ([]*models.Session, error) {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return nil, err
	}

	// Get subscribers (thread-safe snapshot)
	return project.GetSubscribers(), nil
}

// IsProjectExecuting checks if a project is currently executing
func (m *Manager) IsProjectExecuting(projectID string) (bool, error) {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return false, err
	}

	project.Lock()
	defer project.Unlock()

	return project.State == models.StateExecuting, nil
}

// CanProjectExecute checks if a project can start execution
func (m *Manager) CanProjectExecute(projectID string) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	project.Lock()
	defer project.Unlock()

	if project.State == models.StateExecuting {
		return errors.New(errors.CodeProcessActive,
			"project is already executing")
	}

	return nil
}

// TransitionProjectState performs a validated state transition
func (m *Manager) TransitionProjectState(projectID string, from models.State, to models.State) error {
	project, err := m.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	project.Lock()
	currentState := project.State
	project.Unlock()

	// Validate transition
	if currentState != from {
		return errors.New(errors.CodeValidationFailed,
			"invalid state transition: expected %s but was %s", from, currentState)
	}

	// Perform transition
	return m.UpdateProjectState(projectID, to)
}

// GetProjectStats returns statistics about projects
func (m *Manager) GetProjectStats() map[string]interface{} {
	m.mu.RLock()
	defer m.mu.RUnlock()

	stats := map[string]interface{}{
		"total_projects": len(m.projects),
		"max_projects":   m.maxProjects,
		"states": map[string]int{
			string(models.StateIdle):      0,
			string(models.StateExecuting): 0,
			string(models.StateError):     0,
		},
		"total_subscribers": 0,
	}

	stateMap := stats["states"].(map[string]int)

	for _, project := range m.projects {
		project.Lock()
		stateMap[string(project.State)]++
		stats["total_subscribers"] = stats["total_subscribers"].(int) + len(project.Subscribers)
		project.Unlock()
	}

	return stats
}

// AddSubscriber is a convenience method for AddProjectSubscriber
func (m *Manager) AddSubscriber(projectID string, session *models.Session) error {
	return m.AddProjectSubscriber(projectID, session)
}

// RemoveSubscriber is a convenience method for RemoveProjectSubscriber
func (m *Manager) RemoveSubscriber(projectID string, sessionID string) error {
	return m.RemoveProjectSubscriber(projectID, sessionID)
}
