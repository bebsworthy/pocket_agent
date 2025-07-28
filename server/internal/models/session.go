package models

import (
	"fmt"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// Session represents an active WebSocket connection
type Session struct {
	// ID is the unique identifier for the session
	ID string `json:"id"`
	// Conn is the underlying WebSocket connection
	Conn *websocket.Conn `json:"-"`
	// CreatedAt is when the session was established
	CreatedAt time.Time `json:"created_at"`
	// LastPing is when the last ping was sent/received
	LastPing time.Time `json:"last_ping"`
	// ProjectID is the currently joined project, if any
	ProjectID string `json:"project_id,omitempty"`
	// mu provides thread-safe access to the session
	mu sync.Mutex `json:"-"`
	// writeMu ensures only one goroutine writes at a time
	writeMu sync.Mutex `json:"-"`
}

// NewSession creates a new session instance
func NewSession(id string, conn *websocket.Conn) *Session {
	now := time.Now()
	return &Session{
		ID:        id,
		Conn:      conn,
		CreatedAt: now,
		LastPing:  now,
	}
}

// UpdatePing updates the last ping timestamp
func (s *Session) UpdatePing() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.LastPing = time.Now()
}

// SetProject sets the current project for this session
func (s *Session) SetProject(projectID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.ProjectID = projectID
}

// GetProject returns the current project ID
func (s *Session) GetProject() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.ProjectID
}

// IsExpired checks if the session has been idle too long
func (s *Session) IsExpired(timeout time.Duration) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return time.Since(s.LastPing) > timeout
}

// WriteJSON sends a JSON message to the client with write lock
func (s *Session) WriteJSON(v interface{}) error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	// Check if connection is still valid
	if s.Conn == nil {
		return fmt.Errorf("connection is closed")
	}

	// Set write deadline to prevent blocking on slow clients
	deadline := time.Now().Add(10 * time.Second)
	if err := s.Conn.SetWriteDeadline(deadline); err != nil {
		return fmt.Errorf("failed to set write deadline: %w", err)
	}

	return s.Conn.WriteJSON(v)
}

// WriteMessage sends a message to the client with write lock
func (s *Session) WriteMessage(messageType int, data []byte) error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	// Check if connection is still valid
	if s.Conn == nil {
		return fmt.Errorf("connection is closed")
	}

	// Set write deadline to prevent blocking on slow clients
	deadline := time.Now().Add(10 * time.Second)
	if err := s.Conn.SetWriteDeadline(deadline); err != nil {
		return fmt.Errorf("failed to set write deadline: %w", err)
	}

	return s.Conn.WriteMessage(messageType, data)
}

// Close closes the WebSocket connection
func (s *Session) Close() error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	if s.Conn == nil {
		return nil
	}

	err := s.Conn.Close()
	s.Conn = nil // Mark as closed
	return err
}

// SendPing sends a ping message to check connection health
func (s *Session) SendPing() error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	// Check if connection is still valid
	if s.Conn == nil {
		return fmt.Errorf("connection is closed")
	}

	deadline := time.Now().Add(5 * time.Second)
	if err := s.Conn.SetWriteDeadline(deadline); err != nil {
		return fmt.Errorf("failed to set write deadline: %w", err)
	}

	return s.Conn.WriteMessage(websocket.PingMessage, nil)
}

// Validate checks if the session data is valid
func (s *Session) Validate() error {
	if s.ID == "" {
		return fmt.Errorf("session ID cannot be empty")
	}
	if s.Conn == nil {
		return fmt.Errorf("session connection cannot be nil")
	}
	return nil
}
