package handlers

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/validation"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// MockProjectManager is a mock implementation of project.Manager
type MockProjectManager struct {
	mock.Mock
}

func (m *MockProjectManager) CreateProject(path string) (*models.Project, error) {
	args := m.Called(path)
	if args.Get(0) == nil {
		return nil, args.Error(1)
	}
	return args.Get(0).(*models.Project), args.Error(1)
}

func (m *MockProjectManager) DeleteProject(projectID string) error {
	args := m.Called(projectID)
	return args.Error(0)
}

func (m *MockProjectManager) GetProjectByID(projectID string) (*models.Project, error) {
	args := m.Called(projectID)
	if args.Get(0) == nil {
		return nil, args.Error(1)
	}
	return args.Get(0).(*models.Project), args.Error(1)
}

func (m *MockProjectManager) GetAllProjects() []*models.Project {
	args := m.Called()
	if args.Get(0) == nil {
		return nil
	}
	return args.Get(0).([]*models.Project)
}

func (m *MockProjectManager) AddSubscriber(projectID string, session *models.Session) error {
	args := m.Called(projectID, session)
	return args.Error(0)
}

func (m *MockProjectManager) RemoveSubscriber(projectID string, sessionID string) error {
	args := m.Called(projectID, sessionID)
	return args.Error(0)
}

// MockBroadcaster is a mock implementation of Broadcaster
type MockBroadcaster struct {
	mock.Mock
}

func (m *MockBroadcaster) BroadcastProjectUpdate(project *models.Project) {
	m.Called(project)
}

func (m *MockBroadcaster) BroadcastProjectDeletion(project *models.Project) {
	m.Called(project)
}

// MockSession is a mock implementation of models.Session
type MockSession struct {
	mock.Mock
	ID        string
	ProjectID string
}

func (m *MockSession) GetProject() string {
	return m.ProjectID
}

func (m *MockSession) SetProject(projectID string) {
	m.ProjectID = projectID
}

func (m *MockSession) WriteJSON(v interface{}) error {
	args := m.Called(v)
	return args.Error(0)
}

func TestHandleProjectCreate(t *testing.T) {
	ctx := context.Background()
	log := logger.New("debug")

	t.Run("Success", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}
		project := models.NewProject("proj1", "/test/path")

		mockPM.On("CreateProject", "/test/path").Return(project, nil)
		mockBC.On("BroadcastProjectUpdate", project).Return()
		session.On("WriteJSON", mock.Anything).Return(nil)

		// Execute
		data, _ := json.Marshal(map[string]string{"path": "/test/path"})
		err := handler.HandleProjectCreate(ctx, session, data)

		// Assert
		assert.NoError(t, err)
		mockPM.AssertExpectations(t)
		mockBC.AssertExpectations(t)
		session.AssertExpectations(t)
	})

	t.Run("Empty Path", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}

		// Execute
		data, _ := json.Marshal(map[string]string{"path": ""})
		err := handler.HandleProjectCreate(ctx, session, data)

		// Assert
		assert.Error(t, err)
		appErr := err.(*errors.AppError)
		assert.Equal(t, errors.CodeValidationFailed, appErr.Code)
	})

	t.Run("Project Creation Fails", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}

		mockPM.On("CreateProject", "/test/path").Return(nil, errors.NewValidationError("invalid path"))

		// Execute
		data, _ := json.Marshal(map[string]string{"path": "/test/path"})
		err := handler.HandleProjectCreate(ctx, session, data)

		// Assert
		assert.Error(t, err)
		mockPM.AssertExpectations(t)
	})
}

func TestHandleProjectList(t *testing.T) {
	ctx := context.Background()
	log := logger.New("debug")

	t.Run("Success", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}
		projects := []*models.Project{
			models.NewProject("proj1", "/test/path1"),
			models.NewProject("proj2", "/test/path2"),
		}

		mockPM.On("GetAllProjects").Return(projects)
		session.On("WriteJSON", mock.Anything).Return(nil)

		// Execute
		err := handler.HandleProjectList(ctx, session, nil)

		// Assert
		assert.NoError(t, err)
		mockPM.AssertExpectations(t)
		session.AssertExpectations(t)
	})
}

func TestHandleProjectDelete(t *testing.T) {
	ctx := context.Background()
	log := logger.New("debug")

	t.Run("Success with ProjectID", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}
		project := models.NewProject("proj1", "/test/path")

		mockPM.On("GetProjectByID", "proj1").Return(project, nil)
		mockPM.On("DeleteProject", "proj1").Return(nil)
		mockBC.On("BroadcastProjectDeletion", project).Return()
		session.On("WriteJSON", mock.Anything).Return(nil)

		// Execute
		data, _ := json.Marshal(map[string]string{"project_id": "proj1"})
		err := handler.HandleProjectDelete(ctx, session, data)

		// Assert
		assert.NoError(t, err)
		mockPM.AssertExpectations(t)
		mockBC.AssertExpectations(t)
		session.AssertExpectations(t)
	})

	t.Run("Project Not Found", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}

		mockPM.On("GetProjectByID", "proj1").Return(nil, errors.NewProjectNotFoundError("proj1"))

		// Execute
		data, _ := json.Marshal(map[string]string{"project_id": "proj1"})
		err := handler.HandleProjectDelete(ctx, session, data)

		// Assert
		assert.Error(t, err)
		mockPM.AssertExpectations(t)
	})
}

func TestHandleProjectJoin(t *testing.T) {
	ctx := context.Background()
	log := logger.New("debug")

	t.Run("Success", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}
		project := models.NewProject("proj1", "/test/path")

		mockPM.On("GetProjectByID", "proj1").Return(project, nil)
		mockPM.On("AddSubscriber", "proj1", session).Return(nil)
		session.On("WriteJSON", mock.Anything).Return(nil).Times(2)

		// Execute
		data, _ := json.Marshal(map[string]string{"project_id": "proj1"})
		err := handler.HandleProjectJoin(ctx, session, data)

		// Assert
		assert.NoError(t, err)
		assert.Equal(t, "proj1", session.ProjectID)
		mockPM.AssertExpectations(t)
		session.AssertExpectations(t)
	})

	t.Run("Empty ProjectID", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1"}

		// Execute
		data, _ := json.Marshal(map[string]string{"project_id": ""})
		err := handler.HandleProjectJoin(ctx, session, data)

		// Assert
		assert.Error(t, err)
		appErr := err.(*errors.AppError)
		assert.Equal(t, errors.CodeValidationFailed, appErr.Code)
	})
}

func TestHandleProjectLeave(t *testing.T) {
	ctx := context.Background()
	log := logger.New("debug")

	t.Run("Success with Session Project", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1", ProjectID: "proj1"}

		mockPM.On("RemoveSubscriber", "proj1", "session1").Return(nil)
		session.On("WriteJSON", mock.Anything).Return(nil)

		// Execute
		err := handler.HandleProjectLeave(ctx, session, nil)

		// Assert
		assert.NoError(t, err)
		assert.Equal(t, "", session.ProjectID)
		mockPM.AssertExpectations(t)
		session.AssertExpectations(t)
	})

	t.Run("No Project Subscription", func(t *testing.T) {
		// Setup
		mockPM := new(MockProjectManager)
		mockBC := new(MockBroadcaster)
		handler := NewProjectHandlers(mockPM, mockBC, log)

		session := &MockSession{ID: "session1", ProjectID: ""}

		// Execute
		err := handler.HandleProjectLeave(ctx, session, nil)

		// Assert
		assert.Error(t, err)
		appErr := err.(*errors.AppError)
		assert.Equal(t, errors.CodeValidationFailed, appErr.Code)
	})
}