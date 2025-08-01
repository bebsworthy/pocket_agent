package handlers

import (
	"context"

	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
)

// Config contains configuration for all handlers
type Config struct {
	ProjectManager  *project.Manager
	Executor        *executor.ClaudeExecutor
	Logger          *logger.Logger
	BroadcastConfig BroadcasterConfig
	ClaudePath      string
	DataDir         string
}

// Handlers aggregates all WebSocket handlers
type Handlers struct {
	Project   *ProjectHandlers
	Execution *ExecutionHandlers
	Query     *QueryHandlers
	Status    *StatusHandlers
	Health    *HealthHandlers
	Broadcast *Broadcaster
}

// NewHandlers creates all handlers with dependencies
func NewHandlers(config Config, server ServerStats) *Handlers {
	// Create broadcaster
	broadcast := NewBroadcaster(config.BroadcastConfig, config.Logger)

	// Create individual handlers
	projectHandlers := NewProjectHandlers(config.ProjectManager, broadcast, config.Logger)
	executionHandlers := NewExecutionHandlers(config.ProjectManager, config.Executor, broadcast, config.Logger)
	queryHandlers := NewQueryHandlers(config.ProjectManager, config.Logger)
	statusHandlers := NewStatusHandlers(config.ProjectManager, config.Executor, broadcast, server, config.Logger)
	healthHandlers := NewHealthHandlers(config.ClaudePath, config.DataDir, config.Logger)

	return &Handlers{
		Project:   projectHandlers,
		Execution: executionHandlers,
		Query:     queryHandlers,
		Status:    statusHandlers,
		Health:    healthHandlers,
		Broadcast: broadcast,
	}
}

// RegisterAll registers all handlers with the router
func (h *Handlers) RegisterAll(router *websocket.MessageRouter) {
	h.Project.RegisterHandlers(router)
	h.Execution.RegisterHandlers(router)
	h.Query.RegisterHandlers(router)
	h.Health.RegisterHandlers(router)
}

// Start starts any background tasks (like status broadcasting)
func (h *Handlers) Start(ctx context.Context) {
	// Start status broadcasting
	h.Status.Start(ctx)
}

// Stop stops all background tasks
func (h *Handlers) Stop() {
	h.Status.Stop()
}

// HandleMessage implements the MessageHandler interface by using a router
func (h *Handlers) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	// Create a router and register all handlers
	router := websocket.NewMessageRouter(h.Project.log)
	h.RegisterAll(router)

	// Route the message
	return router.HandleMessage(ctx, session, msg)
}

// OnSessionCleanup implements the MessageHandler interface to clean up session resources
func (h *Handlers) OnSessionCleanup(session *models.Session) {
	// Get the project ID the session was subscribed to
	projectID := session.GetProject()
	if projectID == "" {
		// Session wasn't subscribed to any project
		return
	}

	// Remove the session from the project's subscribers
	if err := h.Project.projectMgr.RemoveSubscriber(projectID, session.ID); err != nil {
		// Log but don't fail - the project might have been deleted
		h.Project.log.Debug("Failed to remove subscriber during cleanup",
			"session_id", session.ID,
			"project_id", projectID,
			"error", err,
		)
	} else {
		h.Project.log.Info("Removed disconnected session from project",
			"session_id", session.ID,
			"project_id", projectID,
		)
	}
}
