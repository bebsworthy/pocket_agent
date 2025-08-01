package handlers

import (
	"encoding/json"
	"sync"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/validation"
)

// testSessionWithCapture wraps a session and captures WriteJSON calls
type testSessionWithCapture struct {
	*models.Session
	mu          sync.Mutex
	WrittenData []interface{}
	WriteError  error
}

// newTestSessionWithCapture creates a session that captures writes
func newTestSessionWithCapture(id string) *testSessionWithCapture {
	// Create a real session but with nil connection
	// This is safe because we'll override WriteJSON
	session := &models.Session{
		ID:        id,
		CreatedAt: time.Now(),
		LastPing:  time.Now(),
		Conn:      nil, // We'll handle writes ourselves
	}

	return &testSessionWithCapture{
		Session:     session,
		WrittenData: make([]interface{}, 0),
	}
}

// WriteJSON captures writes
func (s *testSessionWithCapture) WriteJSON(v interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.WriteError != nil {
		return s.WriteError
	}

	s.WrittenData = append(s.WrittenData, v)
	return nil
}

// TestSessionWrapper wraps models.Session for testing and intercepts WriteJSON
type TestSessionWrapper struct {
	session     *models.Session
	mu          sync.Mutex
	WrittenData []interface{}
	WriteError  error
}

// NewTestSession creates a wrapped test session without a connection
func NewTestSession(id string) *TestSessionWrapper {
	// Create session without connection - handlers will handle nil conn
	session := &models.Session{
		ID:        id,
		CreatedAt: time.Now(),
		LastPing:  time.Now(),
	}
	return &TestSessionWrapper{
		session:     session,
		WrittenData: make([]interface{}, 0),
	}
}

// WriteJSON captures the data being written
func (s *TestSessionWrapper) WriteJSON(v interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.WriteError != nil {
		return s.WriteError
	}

	s.WrittenData = append(s.WrittenData, v)
	return nil
}

// GetSession returns the wrapped session that will use our WriteJSON
func (s *TestSessionWrapper) GetSession() *models.Session {
	// We need to return a session that uses our WriteJSON method
	// Since Go doesn't support dynamic method override, we'll use a different approach
	return s.session
}

// ID returns the session ID
func (s *TestSessionWrapper) GetID() string {
	return s.session.ID
}

// GetProject returns the current project ID
func (s *TestSessionWrapper) GetProject() string {
	return s.session.GetProject()
}

// SetProject sets the current project ID
func (s *TestSessionWrapper) SetProject(projectID string) {
	s.session.SetProject(projectID)
}

// GetWrittenResponses returns all captured responses
func (s *TestSessionWrapper) GetWrittenResponses() []interface{} {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.WrittenData
}

// GetLastResponse returns the most recent response
func (s *TestSessionWrapper) GetLastResponse() interface{} {
	s.mu.Lock()
	defer s.mu.Unlock()

	if len(s.WrittenData) == 0 {
		return nil
	}
	return s.WrittenData[len(s.WrittenData)-1]
}

// ClearResponses clears all captured responses
func (s *TestSessionWrapper) ClearResponses() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.WrittenData = make([]interface{}, 0)
}

// TestBroadcaster wraps the real broadcaster and captures broadcasts
type TestBroadcaster struct {
	*Broadcaster
	mu               sync.Mutex
	ProjectUpdates   []*models.Project
	ProjectDeletions []*models.Project
}

// NewTestBroadcaster creates a new test broadcaster
func NewTestBroadcaster(log *logger.Logger) *TestBroadcaster {
	config := DefaultBroadcasterConfig()
	return &TestBroadcaster{
		Broadcaster:      NewBroadcaster(config, log),
		ProjectUpdates:   make([]*models.Project, 0),
		ProjectDeletions: make([]*models.Project, 0),
	}
}

// BroadcastProjectUpdate captures project update broadcasts
func (b *TestBroadcaster) BroadcastProjectUpdate(project *models.Project) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.ProjectUpdates = append(b.ProjectUpdates, project)
	// Don't call the real method in tests - we just want to capture the call
}

// BroadcastProjectDeletion captures project deletion broadcasts
func (b *TestBroadcaster) BroadcastProjectDeletion(project *models.Project) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.ProjectDeletions = append(b.ProjectDeletions, project)
	// Don't call the real method in tests - we just want to capture the call
}

// GetProjectUpdates returns all captured project updates
func (b *TestBroadcaster) GetProjectUpdates() []*models.Project {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.ProjectUpdates
}

// GetProjectDeletions returns all captured project deletions
func (b *TestBroadcaster) GetProjectDeletions() []*models.Project {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.ProjectDeletions
}

// createTestProjectManager creates a real project manager with in-memory storage
func createTestProjectManager(t *testing.T) (*project.Manager, func()) {
	// Create temporary directory for test
	tempDir := t.TempDir()

	config := project.Config{
		DataDir:     tempDir,
		MaxProjects: 100,
		Validator:   validation.NewValidator(),
	}

	manager, err := project.NewManager(config)
	if err != nil {
		t.Fatalf("Failed to create project manager: %v", err)
	}

	cleanup := func() {
		// Cleanup is handled by t.TempDir()
	}

	return manager, cleanup
}

// createTestSession creates a real session without connection for testing
func createTestSession(id string) *models.Session {
	return &models.Session{
		ID:        id,
		CreatedAt: time.Now(),
		LastPing:  time.Now(),
	}
}

// parseResponse is a helper to parse JSON responses
func parseResponse(t *testing.T, response interface{}) map[string]interface{} {
	// Convert response to JSON and back to ensure it's a map
	data, err := json.Marshal(response)
	if err != nil {
		t.Fatalf("Failed to marshal response: %v", err)
	}

	var result map[string]interface{}
	if err := json.Unmarshal(data, &result); err != nil {
		t.Fatalf("Failed to unmarshal response: %v", err)
	}

	return result
}

// assertErrorResponse checks if a response is an error with expected code
func assertErrorResponse(t *testing.T, response interface{}, expectedCode string) {
	parsed := parseResponse(t, response)

	if parsed["type"] != "error" {
		t.Errorf("Expected error response, got type: %v", parsed["type"])
	}

	if errorData, ok := parsed["error"].(map[string]interface{}); ok {
		if errorData["code"] != expectedCode {
			t.Errorf("Expected error code %s, got: %v", expectedCode, errorData["code"])
		}
	} else {
		t.Error("Response missing error data")
	}
}

// assertSuccessResponse checks if a response is successful
func assertSuccessResponse(t *testing.T, response interface{}, expectedType string) map[string]interface{} {
	parsed := parseResponse(t, response)

	if parsed["type"] != expectedType {
		t.Errorf("Expected response type %s, got: %v", expectedType, parsed["type"])
	}

	if parsed["error"] != nil {
		t.Errorf("Unexpected error in response: %v", parsed["error"])
	}

	return parsed
}
