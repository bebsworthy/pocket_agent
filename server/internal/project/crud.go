package project

import (
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// CreateProject creates a new project with validation
func (m *Manager) CreateProject(path string) (*models.Project, error) {
	// Validate path using Phase 2.5 validators
	if err := m.validator.ValidatePath(path); err != nil {
		return nil, err
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// Check project limit (Requirement 10.2)
	if len(m.projects) >= m.maxProjects {
		return nil, errors.NewResourceLimitError("projects", m.maxProjects, len(m.projects))
	}

	// Get existing paths for nesting validation
	existingPaths := make([]string, 0, len(m.projects))
	for _, p := range m.projects {
		existingPaths = append(existingPaths, p.Path)
	}

	// Validate nesting (Requirements 2.3, 2.6)
	if err := m.validator.ValidateProjectNesting(path, existingPaths); err != nil {
		return nil, err
	}

	// Generate new project ID
	projectID := m.generateProjectID()

	// Create new project instance
	project := models.NewProject(projectID, path)

	// Create message log for the project
	messageLog, err := m.storageFactory.CreateMessageLog(projectID)
	if err != nil {
		return nil, errors.Wrap(err, errors.CodeInternalError, "failed to create message log")
	}
	project.MessageLog = messageLog

	// Save to persistence layer
	if err := m.persistence.SaveProjectMetadata(project); err != nil {
		// Clean up message log if save fails
		if cleanupErr := messageLog.Close(); cleanupErr != nil {
			m.logger.Error("Failed to cleanup message log after save failure",
				"project_id", projectID,
				"error", cleanupErr)
		}
		return nil, errors.Wrap(err, errors.CodeFileOperation, "failed to save project metadata")
	}

	// Add to in-memory collection
	m.projects[projectID] = project

	m.logger.Info("Project created successfully",
		"project_id", projectID,
		"path", path)

	return project, nil
}

// DeleteProject deletes a project with cleanup
func (m *Manager) DeleteProject(projectID string) error {
	if err := m.validator.ValidateProjectID(projectID); err != nil {
		return err
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// Find the project
	project, exists := m.projects[projectID]
	if !exists {
		return errors.NewProjectNotFoundError(projectID)
	}

	// Check if project is currently executing (Requirement 2.6)
	project.Lock()
	if project.State == models.StateExecuting {
		project.Unlock()
		return errors.New(errors.CodeProcessActive,
			"cannot delete project while execution is active")
	}
	project.Unlock()

	// Close message log
	if project.MessageLog != nil {
		if err := project.MessageLog.Close(); err != nil {
			m.logger.Error("Failed to close message log during deletion",
				"project_id", projectID,
				"error", err)
		}
	}

	// Delete from persistence layer
	if err := m.persistence.DeleteProjectData(projectID); err != nil {
		return errors.Wrap(err, errors.CodeFileOperation, "failed to delete project data")
	}

	// Remove from in-memory collection
	delete(m.projects, projectID)

	m.logger.Info("Project deleted successfully", "project_id", projectID)

	return nil
}

// GetProjectByID retrieves a project by its ID
func (m *Manager) GetProjectByID(projectID string) (*models.Project, error) {
	if err := m.validator.ValidateProjectID(projectID); err != nil {
		return nil, err
	}

	m.mu.RLock()
	defer m.mu.RUnlock()

	project, exists := m.projects[projectID]
	if !exists {
		return nil, errors.NewProjectNotFoundError(projectID)
	}

	return project, nil
}

// GetProject is an alias for GetProjectByID for backward compatibility
func (m *Manager) GetProject(projectID string) (*models.Project, error) {
	return m.GetProjectByID(projectID)
}

// GetProjectByPath retrieves a project by its path
func (m *Manager) GetProjectByPath(path string) (*models.Project, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, project := range m.projects {
		if project.Path == path {
			return project, nil
		}
	}

	return nil, errors.New(errors.CodeProjectNotFound,
		"project not found for path: %s", path)
}

// UpdateProject updates project metadata and persists changes
func (m *Manager) UpdateProject(project *models.Project) error {
	if project == nil {
		return errors.NewValidationError("project cannot be nil")
	}

	if err := project.Validate(); err != nil {
		return errors.NewValidationError(err.Error())
	}

	m.mu.RLock()
	defer m.mu.RUnlock()

	// Verify project exists
	if _, exists := m.projects[project.ID]; !exists {
		return errors.NewProjectNotFoundError(project.ID)
	}

	// Update last active time
	project.LastActive = time.Now()

	// Save to persistence layer
	if err := m.persistence.SaveProjectMetadata(project); err != nil {
		return errors.Wrap(err, errors.CodeFileOperation, "failed to save project metadata")
	}

	m.logger.Debug("Project updated",
		"project_id", project.ID,
		"session_id", project.SessionID,
		"state", project.State)

	return nil
}
