package storage

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
)

func TestProjectPersistence(t *testing.T) {
	// Create temp directory for tests
	tempDir, err := os.MkdirTemp("", "proj_persist_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	t.Run("NewProjectPersistence", func(t *testing.T) {
		pp, err := NewProjectPersistence(tempDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Check that projects directory was created
		projectsDir := filepath.Join(tempDir, ProjectsDirName)
		if _, err := os.Stat(projectsDir); os.IsNotExist(err) {
			t.Error("Projects directory was not created")
		}

		_ = pp // Use the variable
	})

	t.Run("SaveAndLoadProject", func(t *testing.T) {
		pp, err := NewProjectPersistence(tempDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Create test project
		project := models.NewProject("test-id-123", "/test/path")
		project.SessionID = "session-456"
		project.SetError("test error")

		// Save project
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Load projects
		projects, err := pp.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load projects: %v", err)
		}

		// Verify loaded project
		if len(projects) != 1 {
			t.Fatalf("Expected 1 project, got %d", len(projects))
		}

		loaded := projects[0]
		if loaded.ID != project.ID {
			t.Errorf("ID mismatch: expected %s, got %s", project.ID, loaded.ID)
		}
		if loaded.Path != project.Path {
			t.Errorf("Path mismatch: expected %s, got %s", project.Path, loaded.Path)
		}
		if loaded.SessionID != project.SessionID {
			t.Errorf("SessionID mismatch: expected %s, got %s", project.SessionID, loaded.SessionID)
		}
		if loaded.State != models.StateIdle {
			t.Errorf("State should be IDLE after load, got %s", loaded.State)
		}
	})

	t.Run("MultipleProjects", func(t *testing.T) {
		// Use a fresh subdirectory to avoid interference
		multiDir := filepath.Join(tempDir, "multi")
		pp, err := NewProjectPersistence(multiDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Create multiple projects
		projects := []*models.Project{
			models.NewProject("proj-1", "/path/1"),
			models.NewProject("proj-2", "/path/2"),
			models.NewProject("proj-3", "/path/3"),
		}

		// Save all projects
		for _, p := range projects {
			if err := pp.SaveProjectMetadata(p); err != nil {
				t.Fatalf("Failed to save project %s: %v", p.ID, err)
			}
		}

		// Load projects
		loaded, err := pp.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load projects: %v", err)
		}

		if len(loaded) != len(projects) {
			t.Errorf("Expected %d projects, got %d", len(projects), len(loaded))
		}
	})

	t.Run("DeleteProject", func(t *testing.T) {
		pp, err := NewProjectPersistence(tempDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		// Create and save project
		project := models.NewProject("delete-test", "/test/delete")
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Verify it exists
		projects, _ := pp.LoadProjects()
		if len(projects) == 0 {
			t.Fatal("Project was not saved")
		}

		// Delete project
		if err := pp.DeleteProjectData(project.ID); err != nil {
			t.Fatalf("Failed to delete project: %v", err)
		}

		// Verify it's gone
		projects, _ = pp.LoadProjects()
		for _, p := range projects {
			if p.ID == project.ID {
				t.Error("Project was not deleted")
			}
		}

		// Delete again should not error
		if err := pp.DeleteProjectData(project.ID); err != nil {
			t.Error("Deleting non-existent project should not error")
		}
	})

	t.Run("AtomicWrite", func(t *testing.T) {
		// Use a fresh subdirectory
		atomicDir := filepath.Join(tempDir, "atomic")
		pp, err := NewProjectPersistence(atomicDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}

		project := models.NewProject("atomic-test", "/test/atomic")

		// Save initial version
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Update and save again
		project.SessionID = "new-session"
		project.LastActive = time.Now()
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to update project: %v", err)
		}

		// Load and verify
		projects, _ := pp.LoadProjects()
		if len(projects) != 1 || projects[0].SessionID != "new-session" {
			t.Error("Atomic update failed")
		}
	})
}

func TestCorruptionRecovery(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "corruption_test_*")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	pp, err := NewProjectPersistence(tempDir)
	if err != nil {
		t.Fatalf("Failed to create project persistence: %v", err)
	}

	cr := NewCorruptionRecovery(pp)

	t.Run("RecoverFromBackup", func(t *testing.T) {
		// Create project
		project := models.NewProject("backup-test", "/test/backup")
		if err := pp.SaveProjectMetadata(project); err != nil {
			t.Fatalf("Failed to save project: %v", err)
		}

		// Create backup
		if err := cr.CreateBackup(project.ID); err != nil {
			t.Fatalf("Failed to create backup: %v", err)
		}

		// Corrupt the main file
		projectDir := pp.GetProjectDirectory(project.ID)
		metadataPath := filepath.Join(projectDir, MetadataFileName)
		if err := os.WriteFile(metadataPath, []byte("corrupted"), 0o644); err != nil {
			t.Fatalf("Failed to corrupt file: %v", err)
		}

		// Try to load - should fail
		projects, _ := pp.LoadProjects()
		if len(projects) != 0 {
			t.Error("Should not load corrupted project")
		}

		// Recover
		if err := cr.RecoverProject(project.ID); err != nil {
			t.Fatalf("Failed to recover project: %v", err)
		}

		// Load again - should work
		projects, err = pp.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load after recovery: %v", err)
		}
		if len(projects) != 1 {
			t.Error("Project not recovered")
		}
	})

	t.Run("RecoverFromTempFile", func(t *testing.T) {
		// Create fresh pp for this test
		tempRecoverDir := filepath.Join(tempDir, "temp-recover")
		ppTemp, err := NewProjectPersistence(tempRecoverDir)
		if err != nil {
			t.Fatalf("Failed to create project persistence: %v", err)
		}
		crTemp := NewCorruptionRecovery(ppTemp)

		// Create project
		project := models.NewProject("temp-test", "/test/temp")
		projectDir := ppTemp.GetProjectDirectory(project.ID)

		// Create project directory
		if err := os.MkdirAll(projectDir, 0o755); err != nil {
			t.Fatalf("Failed to create project dir: %v", err)
		}

		// Create a valid temp file with the naming pattern expected by writeFileAtomic
		metadata := project.ToMetadata()
		data, _ := json.Marshal(metadata)
		tempPath := filepath.Join(projectDir, ".tmp-123456") // Match the pattern from CreateTemp
		if err := os.WriteFile(tempPath, data, 0o644); err != nil {
			t.Fatalf("Failed to create temp file: %v", err)
		}

		// Try to recover
		if err := crTemp.RecoverProject(project.ID); err != nil {
			t.Fatalf("Failed to recover from temp: %v", err)
		}

		// Verify recovery
		projects, err := ppTemp.LoadProjects()
		if err != nil {
			t.Fatalf("Failed to load after recovery: %v", err)
		}
		if len(projects) != 1 {
			t.Error("Project not recovered from temp file")
		}
	})

	t.Run("NoRecoveryPossible", func(t *testing.T) {
		// Try to recover non-existent project
		err := cr.RecoverProject("non-existent")
		if err == nil {
			t.Error("Should fail to recover non-existent project")
		}
	})
}
