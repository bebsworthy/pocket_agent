package handlers

import (
	"context"
	"encoding/json"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/websocket"
)

// QueryHandlers provides handlers for query-related WebSocket messages
type QueryHandlers struct {
	projectMgr *project.Manager
	log        *logger.Logger
}

// NewQueryHandlers creates new query handlers
func NewQueryHandlers(projectMgr *project.Manager, log *logger.Logger) *QueryHandlers {
	return &QueryHandlers{
		projectMgr: projectMgr,
		log:        log,
	}
}

// HandleGetMessages handles message history retrieval requests
// Requirements: 7.1, 7.2, 7.3, 7.4
func (h *QueryHandlers) HandleGetMessages(ctx context.Context, session *models.Session, data json.RawMessage) error {
	var req struct {
		ProjectID string `json:"project_id"`
		Since     string `json:"since"`     // RFC3339 timestamp
		Limit     int    `json:"limit"`     // Max messages to return
		Offset    int    `json:"offset"`    // For pagination
		Direction string `json:"direction"` // "all", "client", "claude" (default: "all")
	}

	if err := json.Unmarshal(data, &req); err != nil {
		return errors.Wrap(err, errors.CodeValidationFailed, "invalid get messages request")
	}

	// Get project ID from request or session
	projectID := req.ProjectID
	if projectID == "" {
		projectID = session.GetProject()
	}

	if projectID == "" {
		return errors.New(errors.CodeValidationFailed, "project_id is required")
	}

	// Parse timestamp
	var sinceTime time.Time
	if req.Since != "" {
		var err error
		sinceTime, err = time.Parse(time.RFC3339, req.Since)
		if err != nil {
			return errors.New(errors.CodeValidationFailed, "invalid timestamp format, use RFC3339").
				WithDetail("since", req.Since)
		}
	}

	// Set defaults
	if req.Limit <= 0 || req.Limit > 1000 {
		req.Limit = 100 // Default limit
	}

	if req.Direction == "" {
		req.Direction = "all"
	}

	h.log.Debug("Retrieving message history",
		"session_id", session.ID,
		"project_id", projectID,
		"since", req.Since,
		"limit", req.Limit,
		"offset", req.Offset,
		"direction", req.Direction,
	)

	// Get project
	project, err := h.projectMgr.GetProjectByID(projectID)
	if err != nil {
		return err
	}

	// Get messages from message log
	messages, err := project.MessageLog.GetMessagesSince(sinceTime)
	if err != nil {
		return errors.Wrap(err, errors.CodeInternalError, "failed to retrieve messages")
	}

	// Filter by direction if specified
	filteredMessages := h.filterMessages(messages, req.Direction)

	// Apply pagination
	totalMessages := len(filteredMessages)
	start := req.Offset
	if start >= totalMessages {
		start = totalMessages
	}

	end := start + req.Limit
	if end > totalMessages {
		end = totalMessages
	}

	paginatedMessages := filteredMessages[start:end]

	h.log.Info("Retrieved message history",
		"session_id", session.ID,
		"project_id", projectID,
		"total_messages", totalMessages,
		"returned_messages", len(paginatedMessages),
		"offset", req.Offset,
		"has_more", end < totalMessages,
	)

	// Build response
	response := map[string]interface{}{
		"project_id": projectID,
		"messages":   paginatedMessages,
		"metadata": map[string]interface{}{
			"total":    totalMessages,
			"offset":   req.Offset,
			"limit":    req.Limit,
			"has_more": end < totalMessages,
			"since":    req.Since,
		},
	}

	return websocket.SendSuccess(session, models.MessageTypeGetMessages, response)
}

// filterMessages filters messages by direction
func (h *QueryHandlers) filterMessages(messages []models.TimestampedMessage, direction string) []models.TimestampedMessage {
	if direction == "all" {
		return messages
	}

	filtered := make([]models.TimestampedMessage, 0, len(messages))
	for _, msg := range messages {
		if msg.Direction == direction {
			filtered = append(filtered, msg)
		}
	}

	return filtered
}

// RegisterHandlers registers all query handlers with the router
func (h *QueryHandlers) RegisterHandlers(router *websocket.MessageRouter) {
	router.Register(models.MessageTypeGetMessages, h.HandleGetMessages)
}
