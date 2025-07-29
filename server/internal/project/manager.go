package project

import (
	"fmt"
	"sync"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/storage"
	"github.com/boyd/pocket_agent/server/internal/validation"
	"github.com/google/uuid"
)

// Manager handles project lifecycle and operations
type Manager struct {
	// projects is the in-memory collection of active projects
	projects map[string]*models.Project
	// mu protects concurrent access to the projects map
	mu sync.RWMutex
	// persistence handles saving/loading project metadata
	persistence *storage.ProjectPersistence
	// storageFactory creates storage components
	storageFactory *storage.Factory
	// validator provides path and nesting validation
	validator *validation.Validator
	// logger for operational logging
	logger *logger.Logger
	// maxProjects is the maximum number of projects allowed
	maxProjects int
}

// Config contains configuration for the ProjectManager
type Config struct {
	DataDir     string
	MaxProjects int
	Validator   *validation.Validator
}

// NewManager creates a new ProjectManager with initialization
func NewManager(config Config) (*Manager, error) {
	if config.DataDir == "" {
		return nil, fmt.Errorf("data directory cannot be empty")
	}

	if config.MaxProjects <= 0 {
		config.MaxProjects = 100 // Default from requirements
	}

	if config.Validator == nil {
		config.Validator = validation.NewValidator()
	}

	// Initialize storage factory
	storageFactory := storage.NewFactory(config.DataDir)
	if err := storageFactory.EnsureDirectories(); err != nil {
		return nil, fmt.Errorf("failed to ensure storage directories: %w", err)
	}

	// Initialize persistence layer
	persistence, err := storageFactory.CreateProjectPersistence()
	if err != nil {
		return nil, fmt.Errorf("failed to initialize project persistence: %w", err)
	}

	manager := &Manager{
		projects:       make(map[string]*models.Project),
		persistence:    persistence,
		storageFactory: storageFactory,
		validator:      config.Validator,
		logger:         logger.New("info"),
		maxProjects:    config.MaxProjects,
	}

	// Load existing projects from disk
	if err := manager.loadProjects(); err != nil {
		return nil, fmt.Errorf("failed to load projects: %w", err)
	}

	return manager, nil
}

// loadProjects loads all projects from persistence layer
func (m *Manager) loadProjects() error {
	projects, err := m.persistence.LoadProjects()
	if err != nil {
		return fmt.Errorf("failed to load projects from disk: %w", err)
	}

	m.logger.Info("Loading projects from disk", "count", len(projects))

	// Initialize each project with message log
	for _, project := range projects {
		// Create message log for the project
		messageLog, err := m.storageFactory.CreateMessageLog(project.ID)
		if err != nil {
			m.logger.Error("Failed to create message log for project",
				"project_id", project.ID,
				"error", err)
			continue
		}

		project.MessageLog = messageLog
		m.projects[project.ID] = project

		m.logger.Debug("Loaded project",
			"project_id", project.ID,
			"path", project.Path,
			"session_id", project.SessionID)
	}

	m.logger.Info("Projects loaded successfully", "total", len(m.projects))
	return nil
}

// GetProjectCount returns the current number of projects
func (m *Manager) GetProjectCount() int {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return len(m.projects)
}

// GetAllProjects returns a snapshot of all projects
func (m *Manager) GetAllProjects() []*models.Project {
	m.mu.RLock()
	defer m.mu.RUnlock()

	projects := make([]*models.Project, 0, len(m.projects))
	for _, project := range m.projects {
		// Create a deep copy to prevent external modifications
		projects = append(projects, project.Copy())
	}

	return projects
}

// GetExistingPaths returns all existing project paths for nesting validation
func (m *Manager) GetExistingPaths() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()

	paths := make([]string, 0, len(m.projects))
	for _, project := range m.projects {
		paths = append(paths, project.Path)
	}

	return paths
}

// generateProjectID generates a new unique project ID
func (m *Manager) generateProjectID() string {
	return uuid.New().String()
}
