package handlers

import (
	"context"
	"encoding/json"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
)

// ProjectHandlers provides handlers for project-related WebSocket messages
type ProjectHandlers struct {
	projectMgr *project.Manager
	log        *logger.Logger
	broadcast  *Broadcaster
}

// NewProjectHandlers creates new project handlers
func NewProjectHandlers(projectMgr *project.Manager, broadcast *Broadcaster, log *logger.Logger) *ProjectHandlers {
	return &ProjectHandlers{
		projectMgr: projectMgr,
		log:        log,
		broadcast:  broadcast,
	}
}

// HandleProjectCreate handles project creation requests
// Requirements: 2.1, 2.2, 2.3
func (h *ProjectHandlers) HandleProjectCreate(ctx context.Context, session *models.Session, data json.RawMessage) error {
	var req struct {
		Path string `json:"path"`
	}

	if err := json.Unmarshal(data, &req); err != nil {
		return errors.Wrap(err, errors.CodeValidationFailed, "invalid project create request")
	}

	if req.Path == "" {
		return errors.New(errors.CodeValidationFailed, "path is required")
	}

	h.log.Info("Creating project", "session_id", session.ID, "path", req.Path)

	// Create project
	project, err := h.projectMgr.CreateProject(req.Path)
	if err != nil {
		return err
	}

	h.log.Info("Project created successfully",
		"session_id", session.ID,
		"project_id", project.ID,
		"path", project.Path,
	)

	// Send project state to creator
	if err := websocket.SendProjectState(session, project); err != nil {
		h.log.Error("Failed to send project state", "error", err)
	}

	// Broadcast to all clients
	h.broadcast.BroadcastProjectUpdate(project)

	return nil
}

// HandleProjectList handles project list requests
// Requirements: 2.4
func (h *ProjectHandlers) HandleProjectList(ctx context.Context, session *models.Session, data json.RawMessage) error {
	h.log.Debug("Listing projects", "session_id", session.ID)

	projects := h.projectMgr.GetAllProjects()

	// Create response with metadata
	type projectInfo struct {
		ID         string                 `json:"id"`
		Path       string                 `json:"path"`
		State      models.State           `json:"state"`
		SessionID  string                 `json:"session_id,omitempty"`
		CreatedAt  string                 `json:"created_at"`
		LastActive string                 `json:"last_active"`
		Metadata   map[string]interface{} `json:"metadata,omitempty"`
	}

	projectList := make([]projectInfo, 0, len(projects))
	for _, p := range projects {
		info := projectInfo{
			ID:         p.ID,
			Path:       p.Path,
			State:      p.State,
			SessionID:  p.SessionID,
			CreatedAt:  p.CreatedAt.Format("2006-01-02T15:04:05Z"),
			LastActive: p.LastActive.Format("2006-01-02T15:04:05Z"),
			Metadata: map[string]interface{}{
				"subscriber_count": len(p.Subscribers),
			},
		}
		projectList = append(projectList, info)
	}

	response := map[string]interface{}{
		"projects": projectList,
		"total":    len(projectList),
	}

	return websocket.SendSuccess(session, models.MessageTypeProjectList, response)
}

// HandleProjectDelete handles project deletion requests
// Requirements: 2.5, 2.6
func (h *ProjectHandlers) HandleProjectDelete(ctx context.Context, session *models.Session, data json.RawMessage) error {
	var req struct {
		ProjectID string `json:"project_id"`
	}

	// First try to use project_id from message
	if err := json.Unmarshal(data, &req); err != nil {
		return errors.Wrap(err, errors.CodeValidationFailed, "invalid project delete request")
	}

	// If not in message, use session's current project
	if req.ProjectID == "" {
		req.ProjectID = session.GetProject()
	}

	if req.ProjectID == "" {
		return errors.New(errors.CodeValidationFailed, "project_id is required")
	}

	h.log.Info("Deleting project", "session_id", session.ID, "project_id", req.ProjectID)

	// Get project before deletion for broadcast
	project, err := h.projectMgr.GetProjectByID(req.ProjectID)
	if err != nil {
		return err
	}

	// Delete project
	if err := h.projectMgr.DeleteProject(req.ProjectID); err != nil {
		return err
	}

	h.log.Info("Project deleted successfully",
		"session_id", session.ID,
		"project_id", req.ProjectID,
	)

	// Notify all subscribers about deletion
	h.broadcast.BroadcastProjectDeletion(project)

	// Send success to requester
	return websocket.SendSuccess(session, models.MessageTypeProjectDelete, map[string]string{
		"project_id": req.ProjectID,
		"status":     "deleted",
	})
}

// HandleProjectJoin handles project subscription requests
// Requirements: 6.1, 6.2
func (h *ProjectHandlers) HandleProjectJoin(ctx context.Context, session *models.Session, data json.RawMessage) error {
	var req struct {
		ProjectID string `json:"project_id"`
	}

	if err := json.Unmarshal(data, &req); err != nil {
		return errors.Wrap(err, errors.CodeValidationFailed, "invalid project join request")
	}

	if req.ProjectID == "" {
		return errors.New(errors.CodeValidationFailed, "project_id is required")
	}

	h.log.Info("Joining project", "session_id", session.ID, "project_id", req.ProjectID)

	// Get project
	project, err := h.projectMgr.GetProjectByID(req.ProjectID)
	if err != nil {
		return err
	}

	// Add subscriber
	if err := h.projectMgr.AddSubscriber(req.ProjectID, session); err != nil {
		return err
	}

	// Update session's current project
	session.SetProject(req.ProjectID)

	h.log.Info("Joined project successfully",
		"session_id", session.ID,
		"project_id", req.ProjectID,
		"subscriber_count", len(project.Subscribers),
	)

	// Send current project state
	if err := websocket.SendProjectState(session, project); err != nil {
		h.log.Error("Failed to send project state", "error", err)
	}

	// Send success confirmation
	return websocket.SendSuccess(session, models.MessageTypeProjectJoin, map[string]interface{}{
		"project_id": req.ProjectID,
		"state":      project.State,
		"session_id": project.SessionID,
	})
}

// HandleProjectLeave handles project unsubscribe requests
// Requirements: 6.3
func (h *ProjectHandlers) HandleProjectLeave(ctx context.Context, session *models.Session, data json.RawMessage) error {
	projectID := session.GetProject()
	if projectID == "" {
		// Try to get from message data
		var req struct {
			ProjectID string `json:"project_id"`
		}
		if err := json.Unmarshal(data, &req); err == nil && req.ProjectID != "" {
			projectID = req.ProjectID
		}
	}

	if projectID == "" {
		return errors.New(errors.CodeValidationFailed, "not subscribed to any project")
	}

	h.log.Info("Leaving project", "session_id", session.ID, "project_id", projectID)

	// Remove subscriber
	if err := h.projectMgr.RemoveSubscriber(projectID, session.ID); err != nil {
		// Log but don't fail - subscriber might already be removed
		h.log.Warn("Failed to remove subscriber", "error", err)
	}

	// Clear session's current project
	session.SetProject("")

	h.log.Info("Left project successfully",
		"session_id", session.ID,
		"project_id", projectID,
	)

	// Send success confirmation
	return websocket.SendSuccess(session, models.MessageTypeProjectLeave, map[string]string{
		"project_id": projectID,
		"status":     "left",
	})
}

// RegisterHandlers registers all project handlers with the router
func (h *ProjectHandlers) RegisterHandlers(router *websocket.MessageRouter) {
	router.Register(models.MessageTypeProjectCreate, h.HandleProjectCreate)
	router.Register(models.MessageTypeProjectList, h.HandleProjectList)
	router.Register(models.MessageTypeProjectDelete, h.HandleProjectDelete)
	router.Register(models.MessageTypeProjectJoin, h.HandleProjectJoin)
	router.Register(models.MessageTypeProjectLeave, h.HandleProjectLeave)
}