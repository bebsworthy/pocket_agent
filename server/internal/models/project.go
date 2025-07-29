package models

import (
	"fmt"
	"sync"
	"time"
)

// MessageLogger defines the interface for message logging operations
type MessageLogger interface {
	Append(msg TimestampedMessage) error
	GetMessagesSince(since time.Time) ([]TimestampedMessage, error)
	Close() error
}

// State represents the current state of a project
type State string

const (
	// StateIdle indicates the project is not executing
	StateIdle State = "IDLE"
	// StateExecuting indicates the project is currently executing Claude
	StateExecuting State = "EXECUTING"
	// StateError indicates the project encountered an error
	StateError State = "ERROR"
)

// Project represents a Claude project with its associated state and metadata
type Project struct {
	// ID is the unique identifier for the project (UUID)
	ID string `json:"id"`
	// Path is the absolute filesystem path for the project
	Path string `json:"path"`
	// SessionID is the Claude session identifier for conversation continuity
	SessionID string `json:"session_id,omitempty"`
	// State represents the current execution state
	State State `json:"state"`
	// CreatedAt is when the project was created
	CreatedAt time.Time `json:"created_at"`
	// LastActive is when the project was last used
	LastActive time.Time `json:"last_active"`
	// MessageLog handles persistent message storage
	MessageLog MessageLogger `json:"-"`
	// Subscribers contains all active WebSocket sessions watching this project
	Subscribers map[string]*Session `json:"-"`
	// mu provides thread-safe access to the project
	mu sync.RWMutex `json:"-"`
	// ErrorDetails contains information about the last error if State is ERROR
	ErrorDetails string `json:"error_details,omitempty"`
}

// ProjectMetadata contains the persistent data for a project
type ProjectMetadata struct {
	ID           string    `json:"id"`
	Path         string    `json:"path"`
	SessionID    string    `json:"session_id,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
	LastActive   time.Time `json:"last_active"`
	ErrorDetails string    `json:"error_details,omitempty"`
}

// NewProject creates a new project instance
func NewProject(id, path string) *Project {
	now := time.Now()
	return &Project{
		ID:          id,
		Path:        path,
		State:       StateIdle,
		CreatedAt:   now,
		LastActive:  now,
		Subscribers: make(map[string]*Session),
	}
}

// Lock acquires the project mutex for exclusive access
func (p *Project) Lock() {
	p.mu.Lock()
}

// Unlock releases the project mutex
func (p *Project) Unlock() {
	p.mu.Unlock()
}

// RLock acquires the project mutex for read access
func (p *Project) RLock() {
	p.mu.RLock()
}

// RUnlock releases the project read mutex
func (p *Project) RUnlock() {
	p.mu.RUnlock()
}

// UpdateState safely updates the project state
func (p *Project) UpdateState(state State) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.State = state
	p.LastActive = time.Now()
}

// SetError updates the project to error state with details
func (p *Project) SetError(details string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.State = StateError
	p.ErrorDetails = details
	p.LastActive = time.Now()
}

// AddSubscriber adds a session to the project's subscribers
func (p *Project) AddSubscriber(session *Session) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.Subscribers[session.ID] = session
}

// RemoveSubscriber removes a session from the project's subscribers
func (p *Project) RemoveSubscriber(sessionID string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delete(p.Subscribers, sessionID)
}

// GetSubscribers returns a snapshot of current subscribers to avoid holding locks
// Note: The returned sessions should be treated as read-only snapshots
func (p *Project) GetSubscribers() []*Session {
	p.mu.Lock()
	defer p.mu.Unlock()

	// Create a defensive copy of the subscriber list
	subscribers := make([]*Session, 0, len(p.Subscribers))
	for _, session := range p.Subscribers {
		// We return the session pointers themselves as they have their own locks
		// Callers must use the session's thread-safe methods
		subscribers = append(subscribers, session)
	}
	return subscribers
}

// HasSubscriber checks if a session is subscribed to this project
func (p *Project) HasSubscriber(sessionID string) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	_, exists := p.Subscribers[sessionID]
	return exists
}

// SubscriberCount returns the number of active subscribers
func (p *Project) SubscriberCount() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.Subscribers)
}

// ToMetadata converts a Project to ProjectMetadata for persistence
func (p *Project) ToMetadata() ProjectMetadata {
	p.mu.Lock()
	defer p.mu.Unlock()

	return ProjectMetadata{
		ID:           p.ID,
		Path:         p.Path,
		SessionID:    p.SessionID,
		CreatedAt:    p.CreatedAt,
		LastActive:   p.LastActive,
		ErrorDetails: p.ErrorDetails,
	}
}

// FromMetadata creates a Project from persisted metadata
func FromMetadata(meta ProjectMetadata) *Project {
	return &Project{
		ID:           meta.ID,
		Path:         meta.Path,
		SessionID:    meta.SessionID,
		State:        StateIdle, // Always start as idle after restart
		CreatedAt:    meta.CreatedAt,
		LastActive:   meta.LastActive,
		ErrorDetails: meta.ErrorDetails,
		Subscribers:  make(map[string]*Session),
	}
}

// Copy creates a deep copy of the project for safe external access
// Note: This does not copy MessageLog or Subscribers as they are not meant to be shared
func (p *Project) Copy() *Project {
	p.mu.RLock()
	defer p.mu.RUnlock()
	
	return &Project{
		ID:           p.ID,
		Path:         p.Path,
		SessionID:    p.SessionID,
		State:        p.State,
		CreatedAt:    p.CreatedAt,
		LastActive:   p.LastActive,
		ErrorDetails: p.ErrorDetails,
		// MessageLog is not copied - it's a reference to the storage layer
		// Subscribers are not copied - they belong to the original project
		Subscribers: make(map[string]*Session), // Empty map for the copy
	}
}

// Validate checks if the project data is valid
func (p *Project) Validate() error {
	if p.ID == "" {
		return fmt.Errorf("project ID cannot be empty")
	}
	if p.Path == "" {
		return fmt.Errorf("project path cannot be empty")
	}
	return nil
}
