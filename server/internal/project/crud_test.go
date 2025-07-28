package project

import (
	"os"
	"strings"
	"testing"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
)

func setupTestManager(t *testing.T) (*Manager, string) {
	tempDir, err := os.MkdirTemp("", "project_crud_test")
	if err != nil {
		t.Fatal(err)
	}

	// Create a test directory that exists
	testPath := tempDir + "/testproject"
	if err := os.MkdirAll(testPath, 0o755); err != nil {
		t.Fatal(err)
	}

	manager, err := NewManager(Config{
		DataDir:     tempDir,
		MaxProjects: 10,
	})
	if err != nil {
		os.RemoveAll(tempDir)
		t.Fatal(err)
	}

	return manager, tempDir
}

func TestCreateProject(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create a valid test path
	validPath := tempDir + "/validproject"
	if err := os.MkdirAll(validPath, 0o755); err != nil {
		t.Fatal(err)
	}

	tests := []struct {
		name    string
		path    string
		setup   func()
		wantErr bool
		errCode errors.ErrorCode
	}{
		{
			name:    "valid project",
			path:    validPath,
			wantErr: false,
		},
		{
			name:    "invalid path",
			path:    "../relative/path",
			wantErr: true,
			errCode: errors.CodeInvalidPath,
		},
		{
			name:    "non-existent path",
			path:    "/this/does/not/exist",
			wantErr: true,
			errCode: errors.CodeInvalidPath,
		},
		{
			name: "project limit reached",
			path: validPath,
			setup: func() {
				// Fill up to max projects
				for i := 0; i < manager.maxProjects; i++ {
					testPath := tempDir + "/project" + string(rune(i))
					os.MkdirAll(testPath, 0o755)
					p := models.NewProject(manager.generateProjectID(), testPath)
					manager.projects[p.ID] = p
				}
			},
			wantErr: true,
			errCode: errors.CodeResourceLimit,
		},
		{
			name: "nesting violation - same path",
			path: validPath,
			setup: func() {
				// Add a project at the same path
				p := models.NewProject("existing", validPath)
				manager.projects[p.ID] = p
			},
			wantErr: true,
			errCode: errors.CodeProjectExists,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Reset manager state
			manager.projects = make(map[string]*models.Project)

			if tt.setup != nil {
				tt.setup()
			}

			project, err := manager.CreateProject(tt.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("CreateProject() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errCode != "" {
				appErr, ok := err.(*errors.AppError)
				if !ok {
					t.Errorf("expected AppError, got %T", err)
				} else if appErr.Code != tt.errCode {
					t.Errorf("expected error code %s, got %s", tt.errCode, appErr.Code)
				}
			}

			if !tt.wantErr && project != nil {
				// Verify project was created correctly
				if project.Path != tt.path {
					t.Errorf("expected path %s, got %s", tt.path, project.Path)
				}
				if project.ID == "" {
					t.Error("expected project ID to be set")
				}
				if project.MessageLog == nil {
					t.Error("expected MessageLog to be initialized")
				}

				// Verify project was added to manager
				if _, exists := manager.projects[project.ID]; !exists {
					t.Error("project was not added to manager")
				}
			}
		})
	}
}

func TestDeleteProject(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	tests := []struct {
		name      string
		setupFunc func() (string, func())
		wantErr   bool
		errCode   errors.ErrorCode
	}{
		{
			name: "valid deletion",
			setupFunc: func() (string, func()) {
				validPath := tempDir + "/deleteproject1"
				os.MkdirAll(validPath, 0o755)
				project, _ := manager.CreateProject(validPath)
				return project.ID, nil
			},
			wantErr: false,
		},
		{
			name: "invalid project ID",
			setupFunc: func() (string, func()) {
				return "", nil
			},
			wantErr: true,
			errCode: errors.CodeValidationFailed,
		},
		{
			name: "non-existent project",
			setupFunc: func() (string, func()) {
				return "12345678-1234-1234-1234-123456789012", nil
			},
			wantErr: true,
			errCode: errors.CodeProjectNotFound,
		},
		{
			name: "project executing",
			setupFunc: func() (string, func()) {
				validPath := tempDir + "/deleteproject2"
				os.MkdirAll(validPath, 0o755)
				project, _ := manager.CreateProject(validPath)
				// Simulate executing state
				project.UpdateState(models.StateExecuting)
				return project.ID, nil
			},
			wantErr: true,
			errCode: errors.CodeProcessActive,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			projectID, cleanup := tt.setupFunc()
			if cleanup != nil {
				defer cleanup()
			}

			err := manager.DeleteProject(projectID)
			if (err != nil) != tt.wantErr {
				t.Errorf("DeleteProject() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errCode != "" {
				appErr, ok := err.(*errors.AppError)
				if !ok {
					t.Errorf("expected AppError, got %T", err)
				} else if appErr.Code != tt.errCode {
					t.Errorf("expected error code %s, got %s", tt.errCode, appErr.Code)
				}
			}

			if !tt.wantErr {
				// Verify project was removed
				if _, exists := manager.projects[projectID]; exists {
					t.Error("project was not removed from manager")
				}
			}
		})
	}
}

func TestGetProjectByID(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test projects
	validPath := tempDir + "/getproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	tests := []struct {
		name      string
		projectID string
		wantErr   bool
		errCode   errors.ErrorCode
	}{
		{
			name:      "valid project",
			projectID: project.ID,
			wantErr:   false,
		},
		{
			name:      "empty ID",
			projectID: "",
			wantErr:   true,
			errCode:   errors.CodeValidationFailed,
		},
		{
			name:      "invalid ID format",
			projectID: "short",
			wantErr:   true,
			errCode:   errors.CodeValidationFailed,
		},
		{
			name:      "non-existent project",
			projectID: "12345678-1234-1234-1234-123456789012",
			wantErr:   true,
			errCode:   errors.CodeProjectNotFound,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := manager.GetProjectByID(tt.projectID)
			if (err != nil) != tt.wantErr {
				t.Errorf("GetProjectByID() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errCode != "" {
				appErr, ok := err.(*errors.AppError)
				if !ok {
					t.Errorf("expected AppError, got %T", err)
				} else if appErr.Code != tt.errCode {
					t.Errorf("expected error code %s, got %s", tt.errCode, appErr.Code)
				}
			}

			if !tt.wantErr && result != nil {
				if result.ID != tt.projectID {
					t.Errorf("expected project ID %s, got %s", tt.projectID, result.ID)
				}
			}
		})
	}
}

func TestGetProject(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/aliasproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	// GetProject should be an alias for GetProjectByID
	result1, err1 := manager.GetProject(project.ID)
	result2, err2 := manager.GetProjectByID(project.ID)

	if err1 != err2 {
		t.Error("GetProject and GetProjectByID should return same error")
	}

	if result1 != result2 {
		t.Error("GetProject and GetProjectByID should return same result")
	}
}

func TestGetProjectByPath(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test projects
	path1 := tempDir + "/project1"
	path2 := tempDir + "/project2"
	os.MkdirAll(path1, 0o755)
	os.MkdirAll(path2, 0o755)

	project1, _ := manager.CreateProject(path1)
	manager.CreateProject(path2)

	tests := []struct {
		name    string
		path    string
		wantID  string
		wantErr bool
	}{
		{
			name:    "existing path",
			path:    path1,
			wantID:  project1.ID,
			wantErr: false,
		},
		{
			name:    "non-existent path",
			path:    "/not/exist",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := manager.GetProjectByPath(tt.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("GetProjectByPath() error = %v, wantErr %v", err, tt.wantErr)
			}

			if !tt.wantErr && result != nil {
				if result.ID != tt.wantID {
					t.Errorf("expected project ID %s, got %s", tt.wantID, result.ID)
				}
			}
		})
	}
}

func TestUpdateProject(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create test project
	validPath := tempDir + "/updateproject"
	os.MkdirAll(validPath, 0o755)
	project, _ := manager.CreateProject(validPath)

	tests := []struct {
		name    string
		project *models.Project
		modify  func(*models.Project)
		wantErr bool
		errCode errors.ErrorCode
	}{
		{
			name:    "valid update",
			project: project,
			modify: func(p *models.Project) {
				p.SessionID = "new-session-id"
			},
			wantErr: false,
		},
		{
			name:    "nil project",
			project: nil,
			wantErr: true,
			errCode: errors.CodeValidationFailed,
		},
		{
			name:    "invalid project - empty ID",
			project: &models.Project{Path: "/test"},
			wantErr: true,
			errCode: errors.CodeValidationFailed,
		},
		{
			name:    "non-existent project",
			project: models.NewProject("non-existent-12345678-1234-1234-1234", "/test"),
			wantErr: true,
			errCode: errors.CodeProjectNotFound,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			projectToUpdate := tt.project
			if tt.modify != nil && projectToUpdate != nil {
				tt.modify(projectToUpdate)
			}

			err := manager.UpdateProject(projectToUpdate)
			if (err != nil) != tt.wantErr {
				t.Errorf("UpdateProject() error = %v, wantErr %v", err, tt.wantErr)
			}

			if err != nil && tt.errCode != "" {
				appErr, ok := err.(*errors.AppError)
				if !ok {
					t.Errorf("expected AppError, got %T", err)
				} else if appErr.Code != tt.errCode {
					t.Errorf("expected error code %s, got %s", tt.errCode, appErr.Code)
				}
			}

			if !tt.wantErr && projectToUpdate != nil {
				// Verify LastActive was updated
				if projectToUpdate.LastActive.IsZero() {
					t.Error("expected LastActive to be updated")
				}
			}
		})
	}
}

func TestProjectNestingValidation(t *testing.T) {
	manager, tempDir := setupTestManager(t)
	defer os.RemoveAll(tempDir)

	// Create directory structure
	parent := tempDir + "/parent"
	child := tempDir + "/parent/child"
	sibling := tempDir + "/sibling"

	os.MkdirAll(parent, 0o755)
	os.MkdirAll(child, 0o755)
	os.MkdirAll(sibling, 0o755)

	// Create parent project
	manager.CreateProject(parent)

	tests := []struct {
		name       string
		path       string
		shouldFail bool
		errMsg     string
	}{
		{
			name:       "child of existing",
			path:       child,
			shouldFail: true,
			errMsg:     "child of existing",
		},
		{
			name:       "sibling allowed",
			path:       sibling,
			shouldFail: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := manager.CreateProject(tt.path)
			if (err != nil) != tt.shouldFail {
				t.Errorf("CreateProject() error = %v, shouldFail %v", err, tt.shouldFail)
			}

			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("expected error containing %q, got %q", tt.errMsg, err.Error())
			}
		})
	}
}
