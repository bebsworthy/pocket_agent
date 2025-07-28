package storage

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
)

const (
	// MetadataFileName is the name of the metadata file for each project
	MetadataFileName = "metadata.json"
	// ProjectsDirName is the name of the projects directory
	ProjectsDirName = "projects"
)

// ProjectPersistence handles saving and loading project metadata
type ProjectPersistence struct {
	dataDir string
	mu      sync.Mutex
	logger  *logger.Logger
}

// NewProjectPersistence creates a new project persistence handler
func NewProjectPersistence(dataDir string) (*ProjectPersistence, error) {
	projectsDir := filepath.Join(dataDir, ProjectsDirName)

	// Create projects directory if it doesn't exist
	if err := os.MkdirAll(projectsDir, 0o755); err != nil {
		return nil, fmt.Errorf("failed to create projects directory: %w", err)
	}

	return &ProjectPersistence{
		dataDir: projectsDir,
		logger:  logger.New("info"),
	}, nil
}

// SaveProjectMetadata saves project metadata atomically
func (pp *ProjectPersistence) SaveProjectMetadata(project *models.Project) error {
	pp.mu.Lock()
	defer pp.mu.Unlock()

	// Create project directory
	projectDir := filepath.Join(pp.dataDir, project.ID)
	if err := os.MkdirAll(projectDir, 0o755); err != nil {
		return fmt.Errorf("failed to create project directory: %w", err)
	}

	// Convert to metadata
	metadata := project.ToMetadata()

	// Marshal to JSON with indentation for readability
	data, err := json.MarshalIndent(metadata, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal project metadata: %w", err)
	}

	// Write atomically using temp file + rename
	metadataPath := filepath.Join(projectDir, MetadataFileName)
	if err := writeFileAtomic(metadataPath, data, 0o644); err != nil {
		return fmt.Errorf("failed to write metadata file: %w", err)
	}

	return nil
}

// LoadProjects loads all projects from disk
func (pp *ProjectPersistence) LoadProjects() ([]*models.Project, error) {
	pp.mu.Lock()
	defer pp.mu.Unlock()

	// Read projects directory
	entries, err := os.ReadDir(pp.dataDir)
	if err != nil {
		if os.IsNotExist(err) {
			// No projects yet
			return []*models.Project{}, nil
		}
		return nil, fmt.Errorf("failed to read projects directory: %w", err)
	}

	var projects []*models.Project
	var loadErrors []error

	// Load each project
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}

		project, err := pp.loadProject(entry.Name())
		if err != nil {
			// Log error but continue loading other projects
			loadErrors = append(loadErrors, fmt.Errorf("project %s: %w", entry.Name(), err))
			pp.logger.Error("Failed to load project", "project_id", entry.Name(), "error", err)
			continue
		}

		projects = append(projects, project)
	}

	// Log summary if there were errors
	if len(loadErrors) > 0 {
		pp.logger.Warn("Some projects failed to load",
			"total", len(entries),
			"loaded", len(projects),
			"failed", len(loadErrors))
	}

	return projects, nil
}

// DeleteProjectData removes all data for a project
func (pp *ProjectPersistence) DeleteProjectData(projectID string) error {
	pp.mu.Lock()
	defer pp.mu.Unlock()

	projectDir := filepath.Join(pp.dataDir, projectID)

	// Check if directory exists
	if _, err := os.Stat(projectDir); os.IsNotExist(err) {
		return nil // Already deleted
	}

	// Remove entire project directory
	if err := os.RemoveAll(projectDir); err != nil {
		return fmt.Errorf("failed to delete project data: %w", err)
	}

	return nil
}

// loadProject loads a single project from disk
func (pp *ProjectPersistence) loadProject(projectID string) (*models.Project, error) {
	metadataPath := filepath.Join(pp.dataDir, projectID, MetadataFileName)

	// Read metadata file
	data, err := os.ReadFile(metadataPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read metadata file: %w", err)
	}

	// Unmarshal metadata
	var metadata models.ProjectMetadata
	if err := json.Unmarshal(data, &metadata); err != nil {
		return nil, fmt.Errorf("failed to unmarshal metadata: %w", err)
	}

	// Validate metadata
	if metadata.ID == "" || metadata.Path == "" {
		return nil, fmt.Errorf("invalid metadata: missing required fields")
	}

	// Create project from metadata
	project := models.FromMetadata(metadata)

	// Validate project
	if err := project.Validate(); err != nil {
		return nil, fmt.Errorf("invalid project data: %w", err)
	}

	return project, nil
}

// GetProjectDirectory returns the directory path for a project
func (pp *ProjectPersistence) GetProjectDirectory(projectID string) string {
	return filepath.Join(pp.dataDir, projectID)
}

// writeFileAtomic writes data to a file atomically using rename
func writeFileAtomic(path string, data []byte, perm os.FileMode) error {
	// Create temp file in same directory
	dir := filepath.Dir(path)
	tempFile, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}
	tempPath := tempFile.Name()

	// Clean up temp file on error
	defer func() {
		if tempFile != nil {
			tempFile.Close()
			os.Remove(tempPath)
		}
	}()

	// Write data
	if _, err := tempFile.Write(data); err != nil {
		return fmt.Errorf("failed to write data: %w", err)
	}

	// Sync to disk
	if err := tempFile.Sync(); err != nil {
		return fmt.Errorf("failed to sync file: %w", err)
	}

	// Close before rename
	if err := tempFile.Close(); err != nil {
		return fmt.Errorf("failed to close temp file: %w", err)
	}
	tempFile = nil // Prevent defer cleanup

	// Set permissions
	if err := os.Chmod(tempPath, perm); err != nil {
		os.Remove(tempPath)
		return fmt.Errorf("failed to set permissions: %w", err)
	}

	// Atomically rename
	if err := os.Rename(tempPath, path); err != nil {
		os.Remove(tempPath)
		return fmt.Errorf("failed to rename file: %w", err)
	}

	return nil
}

// CorruptionRecovery attempts to recover from corrupted project data
type CorruptionRecovery struct {
	persistence *ProjectPersistence
}

// NewCorruptionRecovery creates a new corruption recovery handler
func NewCorruptionRecovery(persistence *ProjectPersistence) *CorruptionRecovery {
	return &CorruptionRecovery{
		persistence: persistence,
	}
}

// RecoverProject attempts to recover a corrupted project
func (cr *CorruptionRecovery) RecoverProject(projectID string) error {
	projectDir := cr.persistence.GetProjectDirectory(projectID)
	metadataPath := filepath.Join(projectDir, MetadataFileName)

	// Check for backup files
	backupPath := metadataPath + ".backup"
	if _, err := os.Stat(backupPath); err == nil {
		// Try to restore from backup
		if err := os.Rename(backupPath, metadataPath); err != nil {
			return fmt.Errorf("failed to restore from backup: %w", err)
		}
		logger.New("info").Info("Recovered project from backup", "project_id", projectID)
		return nil
	}

	// Check for temp files that might contain valid data
	dir := filepath.Dir(metadataPath)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return fmt.Errorf("failed to read directory: %w", err)
	}

	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".tmp-") {
			tmpPath := filepath.Join(dir, entry.Name())

			// Try to read and validate temp file
			data, err := os.ReadFile(tmpPath)
			if err != nil {
				continue
			}

			var metadata models.ProjectMetadata
			if err := json.Unmarshal(data, &metadata); err != nil {
				continue
			}

			// If valid, use it
			if metadata.ID == projectID && metadata.Path != "" {
				if err := os.Rename(tmpPath, metadataPath); err != nil {
					continue
				}
				logger.New("info").Info("Recovered project from temp file", "project_id", projectID)
				return nil
			}
		}
	}

	return fmt.Errorf("unable to recover project %s", projectID)
}

// CreateBackup creates a backup of project metadata before updates
func (cr *CorruptionRecovery) CreateBackup(projectID string) error {
	projectDir := cr.persistence.GetProjectDirectory(projectID)
	metadataPath := filepath.Join(projectDir, MetadataFileName)
	backupPath := metadataPath + ".backup"

	// Copy current file to backup
	data, err := os.ReadFile(metadataPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil // No existing file to backup
		}
		return fmt.Errorf("failed to read metadata: %w", err)
	}

	if err := os.WriteFile(backupPath, data, 0o644); err != nil {
		return fmt.Errorf("failed to create backup: %w", err)
	}

	return nil
}
