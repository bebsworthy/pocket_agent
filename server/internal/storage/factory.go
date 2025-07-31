package storage

import (
	"fmt"
	"os"
	"path/filepath"
)

// Factory creates storage components for the application
type Factory struct {
	dataDir string
}

// NewFactory creates a new storage factory
func NewFactory(dataDir string) *Factory {
	return &Factory{
		dataDir: dataDir,
	}
}

// CreateMessageLog creates a new message log for a project
func (f *Factory) CreateMessageLog(projectID string) (*MessageLog, error) {
	projectDir := filepath.Join(f.dataDir, "projects", projectID)
	return NewMessageLog(projectID, projectDir)
}

// CreateProjectPersistence creates a new project persistence handler
func (f *Factory) CreateProjectPersistence() (*ProjectPersistence, error) {
	return NewProjectPersistence(f.dataDir)
}

// GetProjectLogDir returns the log directory for a project
func (f *Factory) GetProjectLogDir(projectID string) string {
	return filepath.Join(f.dataDir, "projects", projectID, "logs")
}

// EnsureDirectories creates all required storage directories
func (f *Factory) EnsureDirectories() error {
	dirs := []string{
		f.dataDir,
		filepath.Join(f.dataDir, ProjectsDirName),
	}

	for _, dir := range dirs {
		if err := ensureDir(dir); err != nil {
			return fmt.Errorf("failed to create directory %s: %w", dir, err)
		}
	}

	return nil
}

// ensureDir creates a directory if it doesn't exist
func ensureDir(dir string) error {
	return os.MkdirAll(dir, 0o755)
}
