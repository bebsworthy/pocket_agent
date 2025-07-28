package project

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/storage"
	"github.com/boyd/pocket_agent/server/internal/validation"
)

func TestNewManager(t *testing.T) {
	// Create temp directory
	tempDir, err := os.MkdirTemp("", "project_manager_test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	tests := []struct {
		name    string
		config  Config
		wantErr bool
		errMsg  string
	}{
		{
			name: "valid config",
			config: Config{
				DataDir:     tempDir,
				MaxProjects: 10,
			},
			wantErr: false,
		},
		{
			name: "empty data dir",
			config: Config{
				DataDir: "",
			},
			wantErr: true,
			errMsg:  "data directory cannot be empty",
		},
		{
			name: "default max projects",
			config: Config{
				DataDir:     tempDir,
				MaxProjects: 0, // Should default to 100
			},
			wantErr: false,
		},
		{
			name: "with custom validator",
			config: Config{
				DataDir:     tempDir,
				MaxProjects: 50,
				Validator:   validation.NewValidator(),
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			manager, err := NewManager(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewManager() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && err.Error() != tt.errMsg {
				t.Errorf("expected error %q, got %q", tt.errMsg, err.Error())
			}
			if manager != nil && tt.config.MaxProjects == 0 {
				if manager.maxProjects != 100 {
					t.Errorf("expected default maxProjects 100, got %d", manager.maxProjects)
				}
			}
		})
	}
}

func TestManagerLoadProjects(t *testing.T) {
	// Create temp directory
	tempDir, err := os.MkdirTemp("", "project_load_test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	// Create persistence and save test projects
	persistence, err := storage.NewProjectPersistence(tempDir)
	if err != nil {
		t.Fatal(err)
	}

	// Create test projects with valid UUIDs
	project1 := models.NewProject("11111111-1111-1111-1111-111111111111", "/test/path1")
	project2 := models.NewProject("22222222-2222-2222-2222-222222222222", "/test/path2")

	// Save projects
	if err := persistence.SaveProjectMetadata(project1); err != nil {
		t.Fatal(err)
	}
	if err := persistence.SaveProjectMetadata(project2); err != nil {
		t.Fatal(err)
	}

	// Create manager - should load projects
	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		t.Fatal(err)
	}

	// Verify projects were loaded
	if count := manager.GetProjectCount(); count != 2 {
		t.Errorf("expected 2 projects loaded, got %d", count)
	}

	// Verify projects are accessible
	proj1, err := manager.GetProjectByID("11111111-1111-1111-1111-111111111111")
	if err != nil {
		t.Errorf("failed to get project 1: %v", err)
	}
	if proj1 != nil && proj1.Path != "/test/path1" {
		t.Errorf("expected path /test/path1, got %s", proj1.Path)
	}

	proj2, err := manager.GetProjectByID("22222222-2222-2222-2222-222222222222")
	if err != nil {
		t.Errorf("failed to get project 2: %v", err)
	}
	if proj2 != nil && proj2.Path != "/test/path2" {
		t.Errorf("expected path /test/path2, got %s", proj2.Path)
	}
}

func TestGetAllProjects(t *testing.T) {
	tempDir, _ := os.MkdirTemp("", "project_getall_test")
	defer os.RemoveAll(tempDir)

	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		t.Fatal(err)
	}

	// Add some projects via internal map (simulating loaded projects)
	manager.projects["p1"] = models.NewProject("p1", "/path1")
	manager.projects["p2"] = models.NewProject("p2", "/path2")
	manager.projects["p3"] = models.NewProject("p3", "/path3")

	projects := manager.GetAllProjects()
	if len(projects) != 3 {
		t.Errorf("expected 3 projects, got %d", len(projects))
	}

	// Verify it's a snapshot (modifications don't affect internal state)
	projects[0].Path = "/modified/path"

	// Get projects again
	projects2 := manager.GetAllProjects()
	for _, p := range projects2 {
		if p.Path == "/modified/path" {
			t.Error("GetAllProjects should return a snapshot, not references")
		}
	}
}

func TestGetExistingPaths(t *testing.T) {
	tempDir, _ := os.MkdirTemp("", "project_paths_test")
	defer os.RemoveAll(tempDir)

	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		t.Fatal(err)
	}

	// Add some projects
	manager.projects["p1"] = models.NewProject("p1", "/path/one")
	manager.projects["p2"] = models.NewProject("p2", "/path/two")
	manager.projects["p3"] = models.NewProject("p3", "/path/three")

	paths := manager.GetExistingPaths()
	if len(paths) != 3 {
		t.Errorf("expected 3 paths, got %d", len(paths))
	}

	// Verify all paths are included
	pathMap := make(map[string]bool)
	for _, p := range paths {
		pathMap[p] = true
	}

	expectedPaths := []string{"/path/one", "/path/two", "/path/three"}
	for _, expected := range expectedPaths {
		if !pathMap[expected] {
			t.Errorf("expected path %s not found", expected)
		}
	}
}

func TestGenerateProjectID(t *testing.T) {
	tempDir, _ := os.MkdirTemp("", "project_id_test")
	defer os.RemoveAll(tempDir)

	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		t.Fatal(err)
	}

	// Generate multiple IDs
	ids := make(map[string]bool)
	for i := 0; i < 100; i++ {
		id := manager.generateProjectID()

		// Check it's a valid UUID format (36 chars)
		if len(id) != 36 {
			t.Errorf("invalid ID length: %d", len(id))
		}

		// Check uniqueness
		if ids[id] {
			t.Errorf("duplicate ID generated: %s", id)
		}
		ids[id] = true
	}
}

func TestManagerConcurrency(t *testing.T) {
	tempDir, _ := os.MkdirTemp("", "project_concurrent_test")
	defer os.RemoveAll(tempDir)

	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		t.Fatal(err)
	}

	// Add initial projects
	for i := 0; i < 10; i++ {
		id := manager.generateProjectID()
		manager.projects[id] = models.NewProject(id, filepath.Join("/test", id))
	}

	// Concurrent operations
	done := make(chan bool)

	// Reader 1
	go func() {
		for i := 0; i < 100; i++ {
			_ = manager.GetProjectCount()
		}
		done <- true
	}()

	// Reader 2
	go func() {
		for i := 0; i < 100; i++ {
			_ = manager.GetAllProjects()
		}
		done <- true
	}()

	// Reader 3
	go func() {
		for i := 0; i < 100; i++ {
			_ = manager.GetExistingPaths()
		}
		done <- true
	}()

	// Wait for all
	for i := 0; i < 3; i++ {
		<-done
	}

	// No panic means success
}
