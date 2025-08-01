// Configuration types
export interface MCPConfig {
  [key: string]: unknown;
}

// Connection types
export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting' | 'error';
export type ConnectionState = ConnectionStatus; // Alias for compatibility

// Client to Server Messages
export interface ClientMessage {
  type: string;
  project_id?: string;
  data?: Record<string, unknown>;
}

export interface ProjectCreateMessage extends ClientMessage {
  type: 'project_create';
  data: {
    path: string;
  };
}

export interface ExecuteMessage extends ClientMessage {
  type: 'execute';
  project_id: string;
  data: {
    prompt: string;
    options?: {
      model?: string;
      dangerously_skip_permissions?: boolean;
      allowed_tools?: string[];
      disallowed_tools?: string[];
      permission_mode?: string;
      mcp_config?: MCPConfig;
      append_system_prompt?: string;
      fallback_model?: string;
      add_dirs?: string[];
      strict_mcp_config?: boolean;
    };
  };
}

export interface ProjectJoinMessage extends ClientMessage {
  type: 'project_join';
  data: {
    project_id: string;
  };
}

export interface ProjectLeaveMessage extends ClientMessage {
  type: 'project_leave';
  project_id: string;
}

export interface ProjectDeleteMessage extends ClientMessage {
  type: 'project_delete';
  project_id: string;
}

export interface ProjectListMessage extends ClientMessage {
  type: 'project_list';
}

export interface GetMessagesMessage extends ClientMessage {
  type: 'get_messages';
  project_id: string;
  data: {
    since?: string;
    limit?: number;
  };
}

export interface AgentKillMessage extends ClientMessage {
  type: 'agent_kill';
  project_id: string;
}

export interface AgentNewSessionMessage extends ClientMessage {
  type: 'agent_new_session';
  project_id: string;
}

// Server to Client Messages
export interface ServerMessage {
  type: string;
  project_id?: string;
  data: Record<string, unknown>;
  timestamp?: string;
}

export interface ProjectStateMessage extends ServerMessage {
  type: 'project_state';
  project_id: string;
  data: {
    id: string;
    path: string;
    state: 'IDLE' | 'EXECUTING' | 'ERROR';
    session_id?: string | null;
    created_at: string;
    last_active: string;
  };
}

export interface ProjectListResponseMessage extends ServerMessage {
  type: 'project_list_response';
  data: {
    projects: Array<{
      id: string;
      path: string;
      state: string;
      created_at: string;
      last_active: string;
      session_id?: string;
    }>;
  };
}

export interface ProjectDeletedMessage extends ServerMessage {
  type: 'project_deleted';
  project_id: string;
}

export interface ProjectJoinedMessage extends ServerMessage {
  type: 'project_joined';
  project_id: string;
}

export interface ProjectLeftMessage extends ServerMessage {
  type: 'project_left';
  project_id: string;
}

export interface AgentMessage extends ServerMessage {
  type: 'agent_message';
  project_id: string;
  data: {
    type: string;
    [key: string]: unknown;
  };
}

export interface ErrorMessage extends ServerMessage {
  type: 'error';
  data: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
}

export interface SessionResetMessage extends ServerMessage {
  type: 'session_reset';
  project_id: string;
}

export interface MessagesResponseMessage extends ServerMessage {
  type: 'messages_response';
  project_id: string;
  data: {
    messages: Array<{
      timestamp: string;
      direction: 'client' | 'claude';
      message: string | Record<string, unknown>;
    }>;
  };
}

export interface HealthCheckMessage extends ServerMessage {
  type: 'health_check';
  data: {
    status: 'healthy' | 'degraded' | 'unhealthy';
    uptime: number;
    version: string;
    connections: {
      active: number;
      limit: number;
    };
    projects: {
      count: number;
      limit: number;
      executing: number;
    };
    resources: {
      cpu_percent: number;
      memory_mb: number;
      goroutines: number;
      disk_free_gb: number;
    };
    claude: {
      available: boolean;
      version: string;
    };
  };
}

export interface ServerStatsMessage extends ServerMessage {
  type: 'server_stats';
  data: {
    connections: number;
    projects: number;
    executions: {
      active: number;
      total: number;
      success: number;
      failed: number;
      avg_duration_ms: number;
    };
    messages: {
      sent: number;
      received: number;
      rate_per_sec: number;
    };
    uptime: number;
  };
}

// Enhanced project creation messages for Task 8: WebSocket project creation integration
// Note: Using the correct ProjectCreateMessage interface above

export interface ProjectCreatedMessage extends ServerMessage {
  type: 'project_created';
  data: {
    project: {
      id: string;
      name: string;
      path: string;
      created_at: string;
      state: string;
      session_id?: string;
    };
    server_id: string;
    created_at: string;
  };
}

export interface ProjectCreationErrorMessage extends ServerMessage {
  type: 'project_creation_error';
  data: {
    error_code: string;
    message: string;
    details?: Record<string, unknown>;
    server_id: string;
  };
}

// Additional server message types based on server specification
export interface ProjectUpdateMessage extends ServerMessage {
  type: 'project_update';
  project_id: string;
  data: {
    update_type: string;
    details?: Record<string, unknown>;
  };
}

export interface ProcessKilledMessage extends ServerMessage {
  type: 'process_killed';
  project_id: string;
  data: {
    process_id?: string;
    reason?: string;
    exit_code?: number;
  };
}

export interface ConnectionHealthMessage extends ServerMessage {
  type: 'connection_health';
  data: {
    status: 'healthy' | 'degraded' | 'unhealthy';
    latency_ms?: number;
    last_heartbeat?: string;
    connection_quality?: number;
  };
}

// Error codes
export enum ErrorCode {
  INVALID_MESSAGE = 'INVALID_MESSAGE',
  INVALID_PATH = 'INVALID_PATH',
  PROJECT_NESTING = 'PROJECT_NESTING',
  PROJECT_NOT_FOUND = 'PROJECT_NOT_FOUND',
  PROJECT_LIMIT = 'PROJECT_LIMIT',
  EXECUTION_TIMEOUT = 'EXECUTION_TIMEOUT',
  CLAUDE_NOT_FOUND = 'CLAUDE_NOT_FOUND',
  PROCESS_ACTIVE = 'PROCESS_ACTIVE',
  RESOURCE_LIMIT = 'RESOURCE_LIMIT',
  INTERNAL_ERROR = 'INTERNAL_ERROR',
  // Project creation specific error codes
  PROJECT_CREATION_FAILED = 'PROJECT_CREATION_FAILED',
  PROJECT_VALIDATION_ERROR = 'PROJECT_VALIDATION_ERROR',
  PROJECT_PATH_EXISTS = 'PROJECT_PATH_EXISTS',
  PROJECT_NAME_TAKEN = 'PROJECT_NAME_TAKEN',
  SERVER_UNAVAILABLE = 'SERVER_UNAVAILABLE',
  CONNECTION_TIMEOUT = 'CONNECTION_TIMEOUT',
  PERMISSION_DENIED = 'PERMISSION_DENIED',
}
