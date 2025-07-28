package models

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

// MessageType represents the type of WebSocket message
type MessageType string

const (
	// Client to Server message types
	MessageTypeExecute         MessageType = "execute"
	MessageTypeProjectCreate   MessageType = "project_create"
	MessageTypeProjectDelete   MessageType = "project_delete"
	MessageTypeProjectList     MessageType = "project_list"
	MessageTypeProjectJoin     MessageType = "project_join"
	MessageTypeProjectLeave    MessageType = "project_leave"
	MessageTypeAgentNewSession MessageType = "agent_new_session"
	MessageTypeAgentKill       MessageType = "agent_kill"
	MessageTypeGetMessages     MessageType = "get_messages"

	// Server to Client message types
	MessageTypeError            MessageType = "error"
	MessageTypeProjectState     MessageType = "project_state"
	MessageTypeProjectJoined    MessageType = "project_joined"
	MessageTypeProjectLeft      MessageType = "project_left"
	MessageTypeAgentMessage     MessageType = "agent_message"
	MessageTypeServerStats      MessageType = "server_stats"
	MessageTypeHealthCheck      MessageType = "health_check"
	MessageTypeProjectUpdate    MessageType = "project_update"
	MessageTypeProjectDeleted   MessageType = "project_deleted"
	MessageTypeSessionReset     MessageType = "session_reset"
	MessageTypeProcessKilled    MessageType = "process_killed"
	MessageTypeConnectionHealth MessageType = "connection_health"
)

// ClientMessage represents a message from client to server
type ClientMessage struct {
	Type      MessageType     `json:"type"`
	ProjectID string          `json:"project_id,omitempty"`
	Data      json.RawMessage `json:"data,omitempty"`
}

// ServerMessage represents a message from server to client
type ServerMessage struct {
	Type      MessageType `json:"type"`
	ProjectID string      `json:"project_id,omitempty"`
	Data      interface{} `json:"data"`
}

// ExecuteCommand contains parameters for executing Claude
type ExecuteCommand struct {
	Prompt  string         `json:"prompt"`
	Options *ClaudeOptions `json:"options,omitempty"`
}

// ClaudeOptions contains optional parameters for Claude execution
type ClaudeOptions struct {
	DangerouslySkipPermissions bool     `json:"dangerously_skip_permissions,omitempty"`
	AllowedTools               []string `json:"allowed_tools,omitempty"`
	DisallowedTools            []string `json:"disallowed_tools,omitempty"`
	MCPConfig                  string   `json:"mcp_config,omitempty"`
	AppendSystemPrompt         string   `json:"append_system_prompt,omitempty"`
	PermissionMode             string   `json:"permission_mode,omitempty"`
	Model                      string   `json:"model,omitempty"`
	FallbackModel              string   `json:"fallback_model,omitempty"`
	AddDirs                    []string `json:"add_dirs,omitempty"`
	StrictMCPConfig            bool     `json:"strict_mcp_config,omitempty"`
}

// ProjectCreateData contains data for creating a project
type ProjectCreateData struct {
	Path string `json:"path"`
}

// ProjectJoinData contains data for joining a project
type ProjectJoinData struct {
	ProjectID string `json:"project_id"`
}

// GetMessagesData contains parameters for retrieving message history
type GetMessagesData struct {
	Since time.Time `json:"since,omitempty"`
	Limit int       `json:"limit,omitempty"`
}

// ErrorData contains error information sent to clients
type ErrorData struct {
	Code    string                 `json:"code"`
	Message string                 `json:"message"`
	Details map[string]interface{} `json:"details,omitempty"`
}

// ProjectStateData contains project state information
type ProjectStateData struct {
	ID         string    `json:"id"`
	Path       string    `json:"path"`
	State      State     `json:"state"`
	SessionID  string    `json:"session_id,omitempty"`
	CreatedAt  time.Time `json:"created_at"`
	LastActive time.Time `json:"last_active"`
}

// ServerStatsData contains server statistics
type ServerStatsData struct {
	ActiveConnections int       `json:"active_connections"`
	ActiveProjects    int       `json:"active_projects"`
	Uptime            string    `json:"uptime"`
	Timestamp         time.Time `json:"timestamp"`
}

// HealthCheckData contains health check information
type HealthCheckData struct {
	Status      string                 `json:"status"` // "healthy", "degraded", "unhealthy"
	ClaudeReady bool                   `json:"claude_ready"`
	Resources   map[string]interface{} `json:"resources"`
	Timestamp   time.Time              `json:"timestamp"`
}

// TimestampedMessage represents a message with timestamp and direction
type TimestampedMessage struct {
	Timestamp time.Time     `json:"timestamp"`
	Message   ClaudeMessage `json:"message"`
	Direction string        `json:"direction"` // "client" or "claude"
}

// ClaudeMessage represents a message from Claude CLI
type ClaudeMessage struct {
	Type    string          `json:"type"`
	Content json.RawMessage `json:"content,omitempty"`
	Error   string          `json:"error,omitempty"`
}

// MessageLog is now implemented in the storage package
// This remains here for backward compatibility and as part of the Project struct
type MessageLog struct {
	// Deprecated: Use storage.MessageLog instead
	projectID    string
	logDir       string
	currentFile  string
	messageCount int
	fileSize     int64
	mu           sync.Mutex
}

// Validate validates a ClientMessage
func (m *ClientMessage) Validate() error {
	if m.Type == "" {
		return fmt.Errorf("message type cannot be empty")
	}

	// Validate specific message types
	switch m.Type {
	case MessageTypeExecute:
		if m.ProjectID == "" {
			return fmt.Errorf("project_id required for execute")
		}
		var cmd ExecuteCommand
		if err := json.Unmarshal(m.Data, &cmd); err != nil {
			return fmt.Errorf("invalid execute command data: %v", err)
		}
		if cmd.Prompt == "" {
			return fmt.Errorf("prompt cannot be empty")
		}

	case MessageTypeProjectCreate:
		var data ProjectCreateData
		if err := json.Unmarshal(m.Data, &data); err != nil {
			return fmt.Errorf("invalid project create data: %v", err)
		}
		if data.Path == "" {
			return fmt.Errorf("path cannot be empty")
		}

	case MessageTypeProjectDelete, MessageTypeAgentNewSession, MessageTypeAgentKill:
		if m.ProjectID == "" {
			return fmt.Errorf("project_id required for %s", m.Type)
		}

	case MessageTypeProjectJoin:
		var data ProjectJoinData
		if err := json.Unmarshal(m.Data, &data); err != nil {
			return fmt.Errorf("invalid project join data: %v", err)
		}
		if data.ProjectID == "" {
			return fmt.Errorf("project_id cannot be empty")
		}

	case MessageTypeProjectLeave:
		if m.ProjectID == "" {
			return fmt.Errorf("project_id required for project_leave")
		}

	case MessageTypeGetMessages:
		if m.ProjectID == "" {
			return fmt.Errorf("project_id required for get_messages")
		}
	}

	return nil
}

// NewErrorMessage creates an error message for the client
func NewErrorMessage(projectID, code, message string, details map[string]interface{}) ServerMessage {
	return ServerMessage{
		Type:      MessageTypeError,
		ProjectID: projectID,
		Data: ErrorData{
			Code:    code,
			Message: message,
			Details: details,
		},
	}
}

// NewProjectStateMessage creates a project state update message
func NewProjectStateMessage(project *Project) ServerMessage {
	return ServerMessage{
		Type:      MessageTypeProjectState,
		ProjectID: project.ID,
		Data: ProjectStateData{
			ID:         project.ID,
			Path:       project.Path,
			State:      project.State,
			SessionID:  project.SessionID,
			CreatedAt:  project.CreatedAt,
			LastActive: project.LastActive,
		},
	}
}

// NewServerStatsMessage creates a server statistics message
func NewServerStatsMessage(connections, projects int, uptime time.Duration) ServerMessage {
	return ServerMessage{
		Type: MessageTypeServerStats,
		Data: ServerStatsData{
			ActiveConnections: connections,
			ActiveProjects:    projects,
			Uptime:            uptime.String(),
			Timestamp:         time.Now(),
		},
	}
}
