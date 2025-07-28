package handlers

import (
	"sync"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// Broadcaster handles broadcasting messages to project subscribers
// Requirements: 3.3, 6.2, 6.4, 6.5, 10.5
type Broadcaster struct {
	log                *logger.Logger
	writeTimeout       time.Duration
	broadcastBuffer    int
	slowClientDeadline time.Duration
}

// BroadcasterConfig contains broadcaster configuration
type BroadcasterConfig struct {
	WriteTimeout       time.Duration
	BroadcastBuffer    int
	SlowClientDeadline time.Duration
}

// DefaultBroadcasterConfig returns default broadcaster configuration
func DefaultBroadcasterConfig() BroadcasterConfig {
	return BroadcasterConfig{
		WriteTimeout:       10 * time.Second,
		BroadcastBuffer:    100,
		SlowClientDeadline: 5 * time.Second,
	}
}

// NewBroadcaster creates a new broadcaster
func NewBroadcaster(config BroadcasterConfig, log *logger.Logger) *Broadcaster {
	return &Broadcaster{
		log:                log,
		writeTimeout:       config.WriteTimeout,
		broadcastBuffer:    config.BroadcastBuffer,
		slowClientDeadline: config.SlowClientDeadline,
	}
}

// BroadcastToProject sends a message to all project subscribers
// Requirements: 6.2, 6.4, 10.5
func (b *Broadcaster) BroadcastToProject(project *models.Project, msg *models.ServerMessage) {
	if project == nil || msg == nil {
		return
	}

	// Set project ID in message
	msg.ProjectID = project.ID

	// Get snapshot of subscribers to avoid holding lock during broadcast
	subscribers := b.getSubscriberSnapshot(project)

	b.log.Debug("Broadcasting to project",
		"project_id", project.ID,
		"message_type", msg.Type,
		"subscriber_count", len(subscribers),
	)

	// Use WaitGroup to track broadcast completion
	var wg sync.WaitGroup
	wg.Add(len(subscribers))

	// Broadcast to each subscriber in separate goroutine
	for sessionID, session := range subscribers {
		go b.sendToSubscriber(&wg, sessionID, session, msg)
	}

	// Wait for all broadcasts to complete or timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		b.log.Debug("Broadcast completed", "project_id", project.ID)
	case <-time.After(b.writeTimeout * 2):
		b.log.Warn("Broadcast timeout", "project_id", project.ID)
	}
}

// sendToSubscriber sends a message to a single subscriber with timeout
func (b *Broadcaster) sendToSubscriber(wg *sync.WaitGroup, sessionID string, session *models.Session, msg *models.ServerMessage) {
	defer wg.Done()

	// Create channel for write result
	done := make(chan error, 1)

	// Attempt write in goroutine
	go func() {
		done <- session.WriteJSON(msg)
	}()

	// Wait for write completion or timeout
	select {
	case err := <-done:
		if err != nil {
			b.log.Error("Failed to send to subscriber",
				"session_id", sessionID,
				"error", err,
			)
		}
	case <-time.After(b.slowClientDeadline):
		b.log.Warn("Slow client detected, skipping",
			"session_id", sessionID,
			"deadline", b.slowClientDeadline,
		)
	}
}

// getSubscriberSnapshot returns a snapshot of project subscribers
func (b *Broadcaster) getSubscriberSnapshot(project *models.Project) map[string]*models.Session {
	project.RLock()
	defer project.RUnlock()

	snapshot := make(map[string]*models.Session, len(project.Subscribers))
	for id, session := range project.Subscribers {
		snapshot[id] = session
	}
	return snapshot
}

// BroadcastProjectState broadcasts project state update
// Requirements: 3.3, 6.4
func (b *Broadcaster) BroadcastProjectState(project *models.Project) {
	msg := models.NewProjectStateMessage(project)
	b.BroadcastToProject(project, msg)
}

// BroadcastProjectUpdate broadcasts project update notification
func (b *Broadcaster) BroadcastProjectUpdate(project *models.Project) {
	msg := &models.ServerMessage{
		Type:      models.MessageTypeProjectUpdate,
		ProjectID: project.ID,
		Data: map[string]interface{}{
			"project_id": project.ID,
			"state":      project.State,
			"path":       project.Path,
			"timestamp":  time.Now().Format(time.RFC3339),
		},
	}
	b.BroadcastToProject(project, msg)
}

// BroadcastProjectDeletion broadcasts project deletion notification
// Requirements: 6.5
func (b *Broadcaster) BroadcastProjectDeletion(project *models.Project) {
	msg := &models.ServerMessage{
		Type:      models.MessageTypeProjectDeleted,
		ProjectID: project.ID,
		Data: map[string]interface{}{
			"project_id": project.ID,
			"reason":     "project_deleted",
			"timestamp":  time.Now().Format(time.RFC3339),
		},
	}
	b.BroadcastToProject(project, msg)
}

// BroadcastClaudeMessage broadcasts Claude message to subscribers
// Requirements: 3.4, 6.4
func (b *Broadcaster) BroadcastClaudeMessage(project *models.Project, claudeMsg models.ClaudeMessage) {
	msg := &models.ServerMessage{
		Type:      models.MessageTypeAgentMessage,
		ProjectID: project.ID,
		Data:      claudeMsg,
	}
	b.BroadcastToProject(project, msg)
}

// BroadcastError broadcasts error to project subscribers
// Requirements: 5.4
func (b *Broadcaster) BroadcastError(project *models.Project, err error) {
	appErr, ok := err.(*errors.AppError)
	if !ok {
		appErr = errors.NewInternalError(err)
	}

	msg := models.NewErrorMessage(
		project.ID,
		string(appErr.Code),
		appErr.Message,
		appErr.Details,
	)

	b.BroadcastToProject(project, msg)
}

// BroadcastSessionReset broadcasts session reset notification
// Requirements: 4.4
func (b *Broadcaster) BroadcastSessionReset(project *models.Project) {
	msg := &models.ServerMessage{
		Type:      models.MessageTypeSessionReset,
		ProjectID: project.ID,
		Data: map[string]interface{}{
			"project_id": project.ID,
			"reason":     "session_reset",
			"timestamp":  time.Now().Format(time.RFC3339),
		},
	}
	b.BroadcastToProject(project, msg)
}

// BroadcastProcessKilled broadcasts process killed notification
// Requirements: 5.4
func (b *Broadcaster) BroadcastProcessKilled(project *models.Project) {
	msg := &models.ServerMessage{
		Type:      models.MessageTypeProcessKilled,
		ProjectID: project.ID,
		Data: map[string]interface{}{
			"project_id": project.ID,
			"reason":     "process_killed",
			"timestamp":  time.Now().Format(time.RFC3339),
		},
	}
	b.BroadcastToProject(project, msg)
}