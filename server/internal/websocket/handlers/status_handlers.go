package handlers

import (
	"context"
	"sync"
	"time"

	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
)

// StatusHandlers handles periodic status updates and broadcasts
// Requirements: 3.3, 4.4, 5.4, 6.5
type StatusHandlers struct {
	projectMgr  *project.Manager
	executor    *executor.ClaudeExecutor
	broadcast   *Broadcaster
	log         *logger.Logger
	server      ServerStats
	mu          sync.RWMutex
	stopChan    chan struct{}
	wg          sync.WaitGroup
}

// ServerStats provides server statistics
type ServerStats interface {
	GetMetrics() map[string]interface{}
}

// NewStatusHandlers creates new status handlers
func NewStatusHandlers(
	projectMgr *project.Manager,
	executor *executor.ClaudeExecutor,
	broadcast *Broadcaster,
	server ServerStats,
	log *logger.Logger,
) *StatusHandlers {
	return &StatusHandlers{
		projectMgr: projectMgr,
		executor:   executor,
		broadcast:  broadcast,
		server:     server,
		log:        log,
		stopChan:   make(chan struct{}),
	}
}

// Start begins periodic status broadcasting
func (h *StatusHandlers) Start(ctx context.Context) {
	h.mu.Lock()
	defer h.mu.Unlock()

	h.log.Info("Starting status broadcaster")

	// Start periodic stats broadcast (10s interval per requirements)
	h.wg.Add(1)
	go h.broadcastStatsLoop(ctx)
}

// Stop stops all status broadcasting
func (h *StatusHandlers) Stop() {
	h.mu.Lock()
	defer h.mu.Unlock()

	h.log.Info("Stopping status broadcaster")

	// Signal stop
	close(h.stopChan)

	// Wait for goroutines to finish
	done := make(chan struct{})
	go func() {
		h.wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		h.log.Info("Status broadcaster stopped")
	case <-time.After(5 * time.Second):
		h.log.Warn("Status broadcaster stop timeout")
	}
}

// broadcastStatsLoop periodically broadcasts server statistics
func (h *StatusHandlers) broadcastStatsLoop(ctx context.Context) {
	defer h.wg.Done()

	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	// Broadcast initial stats
	h.broadcastServerStats()

	for {
		select {
		case <-ctx.Done():
			return
		case <-h.stopChan:
			return
		case <-ticker.C:
			h.broadcastServerStats()
		}
	}
}

// broadcastServerStats broadcasts current server statistics to all connected clients
func (h *StatusHandlers) broadcastServerStats() {
	stats := h.collectServerStats()

	// Create stats message
	msg := &models.ServerMessage{
		Type: models.MessageTypeServerStats,
		Data: stats,
	}

	// Broadcast to all projects
	projects := h.projectMgr.GetAllProjects()
	for _, project := range projects {
		if len(project.Subscribers) > 0 {
			h.broadcast.BroadcastToProject(project, msg)
		}
	}

	h.log.Debug("Broadcasted server stats",
		"projects", len(projects),
		"active_executions", stats["executor"].(map[string]interface{})["active_processes"],
	)
}

// collectServerStats collects current server statistics
func (h *StatusHandlers) collectServerStats() map[string]interface{} {
	// Get server metrics
	serverMetrics := h.server.GetMetrics()

	// Get executor stats
	executorStats := h.executor.GetStats()

	// Get project stats
	projects := h.projectMgr.GetAllProjects()
	projectStats := h.collectProjectStats(projects)

	return map[string]interface{}{
		"timestamp": time.Now().Format(time.RFC3339),
		"server":    serverMetrics,
		"executor":  executorStats,
		"projects":  projectStats,
		"system": map[string]interface{}{
			"uptime": time.Since(h.getStartTime()).String(),
		},
	}
}

// collectProjectStats collects statistics about projects
func (h *StatusHandlers) collectProjectStats(projects []*models.Project) map[string]interface{} {
	totalSubscribers := 0
	stateCount := make(map[models.State]int)
	projectsWithSessions := 0

	for _, project := range projects {
		totalSubscribers += len(project.Subscribers)
		stateCount[project.State]++
		if project.SessionID != "" {
			projectsWithSessions++
		}
	}

	return map[string]interface{}{
		"total":                len(projects),
		"total_subscribers":    totalSubscribers,
		"with_sessions":        projectsWithSessions,
		"by_state":             stateCount,
		"average_subscribers":  float64(totalSubscribers) / float64(len(projects)),
	}
}

// BroadcastConnectionHealth sends connection health update to a specific session
func (h *StatusHandlers) BroadcastConnectionHealth(session *models.Session) {
	health := map[string]interface{}{
		"status":     "healthy",
		"session_id": session.ID,
		"ping":       session.LastPing.Format(time.RFC3339),
		"uptime":     time.Since(session.CreatedAt).String(),
	}

	msg := &models.ServerMessage{
		Type: models.MessageTypeConnectionHealth,
		Data: health,
	}

	if err := session.WriteJSON(msg); err != nil {
		h.log.Error("Failed to send connection health", "error", err)
	}
}

// BroadcastErrorNotification sends error notification to relevant subscribers
func (h *StatusHandlers) BroadcastErrorNotification(projectID string, err error) {
	project, projectErr := h.projectMgr.GetProjectByID(projectID)
	if projectErr != nil {
		h.log.Error("Failed to get project for error broadcast", "error", projectErr)
		return
	}

	h.broadcast.BroadcastError(project, err)
}

// getStartTime returns the server start time (stub for now)
func (h *StatusHandlers) getStartTime() time.Time {
	// This would typically be tracked by the main server
	// For now, return a time 1 hour ago
	return time.Now().Add(-1 * time.Hour)
}