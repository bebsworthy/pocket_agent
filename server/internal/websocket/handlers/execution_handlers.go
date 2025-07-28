package handlers

import (
	"context"
	"encoding/json"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
)

// ExecutionHandlers provides handlers for execution-related WebSocket messages
type ExecutionHandlers struct {
	projectMgr *project.Manager
	executor   *executor.ClaudeExecutor
	log        *logger.Logger
	broadcast  *Broadcaster
}

// NewExecutionHandlers creates new execution handlers
func NewExecutionHandlers(projectMgr *project.Manager, executor *executor.ClaudeExecutor, broadcast *Broadcaster, log *logger.Logger) *ExecutionHandlers {
	return &ExecutionHandlers{
		projectMgr: projectMgr,
		executor:   executor,
		log:        log,
		broadcast:  broadcast,
	}
}

// HandleExecute handles Claude execution requests
// Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
func (h *ExecutionHandlers) HandleExecute(ctx context.Context, session *models.Session, data json.RawMessage) error {
	var req executor.ExecuteCommand
	if err := json.Unmarshal(data, &req); err != nil {
		return errors.Wrap(err, errors.CodeValidationFailed, "invalid execute request")
	}

	// Get project ID from session or request
	projectID := session.GetProject()
	if projectID == "" {
		return errors.New(errors.CodeValidationFailed, "not subscribed to a project")
	}

	if req.Prompt == "" {
		return errors.New(errors.CodeValidationFailed, "prompt is required")
	}

	h.log.Info("Executing Claude command",
		"session_id", session.ID,
		"project_id", projectID,
		"prompt_length", len(req.Prompt),
	)

	// Get project
	project, err := h.projectMgr.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Update project state to EXECUTING
	if err := h.projectMgr.UpdateProjectState(projectID, models.StateExecuting); err != nil {
		return err
	}

	// Broadcast state change to all subscribers
	h.broadcast.BroadcastProjectState(project)

	// Execute Claude command asynchronously
	go h.executeClaudeCommand(ctx, session, project, req)

	// Send immediate acknowledgment
	return websocket.SendSuccess(session, models.MessageTypeExecute, map[string]interface{}{
		"project_id": projectID,
		"status":     "started",
		"timestamp":  time.Now().Format(time.RFC3339),
	})
}

// executeClaudeCommand runs Claude execution and handles results
func (h *ExecutionHandlers) executeClaudeCommand(ctx context.Context, session *models.Session, project *models.Project, req executor.ExecuteCommand) {
	startTime := time.Now()

	// Execute Claude
	response, err := h.executor.Execute(project, req)

	// Update project state based on result
	newState := models.StateIdle
	if err != nil {
		newState = models.StateError
		h.log.Error("Claude execution failed",
			"project_id", project.ID,
			"error", err,
			"duration", time.Since(startTime),
		)

		// Broadcast error to subscribers
		h.broadcast.BroadcastError(project, err)
	} else {
		h.log.Info("Claude execution completed",
			"project_id", project.ID,
			"session_id", response.SessionID,
			"duration", time.Since(startTime),
		)

		// Update project session ID if changed
		if response.SessionID != "" && response.SessionID != project.SessionID {
			if err := h.projectMgr.UpdateProjectSession(project.ID, response.SessionID); err != nil {
				h.log.Error("Failed to update project session", "error", err)
			}
		}

		// Broadcast Claude messages to all subscribers
		for _, msg := range response.Messages {
			h.broadcast.BroadcastClaudeMessage(project, msg)
		}
	}

	// Update project state
	if err := h.projectMgr.UpdateProjectState(project.ID, newState); err != nil {
		h.log.Error("Failed to update project state", "error", err)
	}

	// Get updated project and broadcast final state
	if updatedProject, err := h.projectMgr.GetProjectByID(project.ID); err == nil {
		h.broadcast.BroadcastProjectState(updatedProject)
	}
}

// HandleAgentNewSession handles session reset requests
// Requirements: 4.1, 4.2, 4.3, 4.4
func (h *ExecutionHandlers) HandleAgentNewSession(ctx context.Context, session *models.Session, data json.RawMessage) error {
	// Get project ID from session or request
	projectID := session.GetProject()
	if projectID == "" {
		var req struct {
			ProjectID string `json:"project_id"`
		}
		if err := json.Unmarshal(data, &req); err == nil && req.ProjectID != "" {
			projectID = req.ProjectID
		}
	}

	if projectID == "" {
		return errors.New(errors.CodeValidationFailed, "project_id is required")
	}

	h.log.Info("Resetting Claude session",
		"session_id", session.ID,
		"project_id", projectID,
	)

	// Get project
	project, err := h.projectMgr.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Check if project is currently executing
	if project.State == models.StateExecuting {
		return errors.New(errors.CodeProcessActive, "cannot reset session while executing")
	}

	// Clear session ID
	oldSessionID := project.SessionID
	if err := h.projectMgr.UpdateProjectSession(projectID, ""); err != nil {
		return err
	}

	h.log.Info("Claude session reset",
		"session_id", session.ID,
		"project_id", projectID,
		"old_session_id", oldSessionID,
		"timestamp", time.Now().Format(time.RFC3339),
	)

	// Get updated project
	updatedProject, err := h.projectMgr.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Broadcast update to all subscribers
	h.broadcast.BroadcastProjectState(updatedProject)
	h.broadcast.BroadcastSessionReset(updatedProject)

	// Send success response
	return websocket.SendSuccess(session, models.MessageTypeAgentNewSession, map[string]interface{}{
		"project_id":     projectID,
		"old_session_id": oldSessionID,
		"status":         "reset",
		"timestamp":      time.Now().Format(time.RFC3339),
	})
}

// HandleAgentKill handles process termination requests
// Requirements: 5.1, 5.2, 5.3, 5.4
func (h *ExecutionHandlers) HandleAgentKill(ctx context.Context, session *models.Session, data json.RawMessage) error {
	// Get project ID from session or request
	projectID := session.GetProject()
	if projectID == "" {
		var req struct {
			ProjectID string `json:"project_id"`
		}
		if err := json.Unmarshal(data, &req); err == nil && req.ProjectID != "" {
			projectID = req.ProjectID
		}
	}

	if projectID == "" {
		return errors.New(errors.CodeValidationFailed, "project_id is required")
	}

	h.log.Info("Killing Claude process",
		"session_id", session.ID,
		"project_id", projectID,
	)

	// Kill the process
	if err := h.executor.KillExecution(projectID); err != nil {
		// Check if it's because no process is active
		if appErr, ok := err.(*errors.AppError); ok && appErr.Code == errors.CodeProcessNotFound {
			return errors.New(errors.CodeProcessNotFound, "no active execution for project").
				WithDetail("project_id", projectID)
		}
		return err
	}

	// Update project state to IDLE
	if err := h.projectMgr.UpdateProjectState(projectID, models.StateIdle); err != nil {
		h.log.Error("Failed to update project state after kill", "error", err)
	}

	h.log.Info("Claude process killed",
		"session_id", session.ID,
		"project_id", projectID,
		"timestamp", time.Now().Format(time.RFC3339),
	)

	// Get updated project
	project, err := h.projectMgr.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Broadcast update to all subscribers
	h.broadcast.BroadcastProjectState(project)
	h.broadcast.BroadcastProcessKilled(project)

	// Send success response
	return websocket.SendSuccess(session, models.MessageTypeAgentKill, map[string]interface{}{
		"project_id": projectID,
		"status":     "killed",
		"timestamp":  time.Now().Format(time.RFC3339),
	})
}

// RegisterHandlers registers all execution handlers with the router
func (h *ExecutionHandlers) RegisterHandlers(router *websocket.MessageRouter) {
	router.Register(models.MessageTypeExecute, h.HandleExecute)
	router.Register(models.MessageTypeAgentNewSession, h.HandleAgentNewSession)
	router.Register(models.MessageTypeAgentKill, h.HandleAgentKill)
}
