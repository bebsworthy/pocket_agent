# WebSocket API Design

## Overview

The WebSocket API provides real-time bidirectional communication between Pocket Agent clients and the server. It manages Claude CLI executions on a per-project basis with persistent state management and multi-client support.

## Architecture

### System Architecture

```mermaid
graph TB
    subgraph "Clients"
        C1[Android Client]
        C2[React Client]
        C3[React Client 2]
    end
    
    subgraph "Server"
        WS[WebSocket Handler]
        PM[Project Manager]
        CE[Claude Executor]
        SM[Session Manager]
        
        WS --> PM
        WS --> CE
        WS --> SM
        PM --> PS[(Project Storage)]
        CE --> CP[Claude Process]
    end
    
    C1 -.->|WSS| WS
    C2 -.->|WSS| WS
    C3 -.->|WSS| WS
```

### Component Architecture

```mermaid
classDiagram
    class Server {
        +wsHandler: WebSocketHandler
        +projectMgr: ProjectManager
        +executor: ClaudeExecutor
        +sessionMgr: SessionManager
        +metricsCollector: MetricsCollector
        +Start() error
        +broadcastStats()
        +IncrementConnections()
        +DecrementConnections()
    }
    
    class WebSocketHandler {
        +projectMgr: ProjectManager
        +executor: ClaudeExecutor
        +sessions: map[string]Session
        +metricsProvider: MetricsProvider
        +HandleUpgrade(w, r)
        +RouteMessage(session, msg)
        +broadcastToProject(project, msg)
        +SetMetricsProvider(provider)
        +cleanupSession(session)
    }
    
    class MetricsProvider {
        <<interface>>
        +IncrementConnections()
        +DecrementConnections()
        +IncrementMessages()
        +IncrementErrors()
    }
    
    class ProjectManager {
        -projects: map[string]Project
        -dataDir: string
        +CreateProject(path) Project
        +DeleteProject(id) error
        +GetProjectByID(id) Project
        +UpdateProjectSession(project) error
        +loadProjects() error
        +saveProjectMetadata(project) error
    }
    
    class ClaudeExecutor {
        -activeProcesses: map[string]Cmd
        +Execute(project, cmd) Response
        +KillExecution(projectID) error
    }
    
    class Project {
        +ID: string
        +Path: string
        +SessionID: string
        +State: State
        +MessageLog: MessageLog
        +Subscribers: map[string]Session
    }
    
    class MessageLog {
        -projectID: string
        -logDir: string
        -currentFile: File
        +Append(msg) error
        +GetMessagesSince(time) []Message
        +rotateIfNeeded() error
    }
    
    Server --> WebSocketHandler
    Server --> ProjectManager
    Server --> ClaudeExecutor
    Server ..|> MetricsProvider : implements
    WebSocketHandler --> ProjectManager
    WebSocketHandler --> ClaudeExecutor
    WebSocketHandler --> MetricsProvider
    ProjectManager --> Project
    Project --> MessageLog
```

## Component Specifications

### WebSocket Handler
**Purpose**: Manages WebSocket connections and routes messages

**Responsibilities**:
- Accept WebSocket upgrade requests
- Authenticate connections (future)
- Route messages to appropriate handlers
- Manage session lifecycle
- Broadcast updates to subscribers
- Clean up dead sessions from project subscribers
- Report metrics to external providers

**Key Methods**:
```go
func (h *WebSocketHandler) RouteMessage(session *Session, msg ClientMessage) {
    switch msg.Type {
    case "execute":          // Execute Claude command
    case "project_create":   // Create new project
    case "project_delete":   // Delete project
    case "project_list":     // List all projects
    case "project_join":     // Subscribe to project
    case "project_leave":    // Unsubscribe from project
    case "agent_new_session": // Reset Claude session
    case "agent_kill":       // Kill running process
    case "get_messages":     // Retrieve message history
    }
}

// MessageHandler interface
type MessageHandler interface {
    HandleMessage(ctx context.Context, session *Session, msg *ClientMessage) error
    OnSessionCleanup(session *Session) // Called when connection is lost
}
```

**Connection Lifecycle**:
1. Accept WebSocket upgrade
2. Set read deadline to `PingInterval + PongTimeout` (prevents premature timeout)
3. Register session and increment metrics
4. Handle messages until disconnection
5. On disconnect: Call `OnSessionCleanup` to remove from project subscribers
6. Clean up resources and decrement metrics

### Project Manager
**Purpose**: Manages project lifecycle and persistence

**Responsibilities**:
- Create/delete projects with validation
- Persist project metadata to disk
- Load projects on server startup
- Enforce no-nesting rule
- Manage project state transitions

**Persistence Strategy**:
- Atomic writes using temp file + rename
- JSON metadata files for quick loading
- Graceful handling of corrupted files

### Claude Executor
**Purpose**: Manages Claude CLI process execution

**Responsibilities**:
- Execute Claude commands with timeout
- Track active processes for cancellation
- Parse Claude JSON output
- Handle process lifecycle
- Enforce sequential execution per project
- Use project's existing MessageLog (no new logs per execution)

**Execution Flow**:
1. Acquire project lock (mutex)
2. Build command arguments
3. Create context with timeout
4. Execute and track process
5. Parse output and update session
6. Log messages to project's MessageLog
7. Clean up and release lock

### Message Log
**Purpose**: Persistent message storage with rotation

**Responsibilities**:
- Append messages atomically
- Rotate logs by size and time
- Query messages by timestamp
- Handle concurrent access
- Maintain chronological order
- Lazy initialization (prevent empty file creation)
- Reuse across multiple executions per project

**Rotation Strategy**:
- New file each day at midnight
- New file after 100MB or 10,000 messages
- Filename: `messages_YYYY-MM-DD_HH-MM-SS.jsonl`
- Symlink/pointer to latest file
- Skip rotation for empty files

## Data Models

### Core Models

```go
type Project struct {
    ID          string              // UUID
    Path        string              // Absolute filesystem path
    SessionID   string              // Claude session ID
    State       State               // IDLE, EXECUTING, ERROR
    CreatedAt   time.Time
    LastActive  time.Time
    MessageLog  *MessageLog
    Subscribers map[string]*Session // Active subscribers
    mu          sync.Mutex          // Project-level lock
}

type State string
const (
    IDLE      State = "IDLE"
    EXECUTING State = "EXECUTING"
    ERROR     State = "ERROR"
)

type Session struct {
    ID         string
    Conn       *websocket.Conn
    CreatedAt  time.Time
    LastPing   time.Time
}

type ProjectMetadata struct {
    ID         string    `json:"id"`
    Path       string    `json:"path"`
    SessionID  string    `json:"session_id"`
    CreatedAt  time.Time `json:"created_at"`
    LastActive time.Time `json:"last_active"`
}

type TimestampedMessage struct {
    Timestamp time.Time     `json:"timestamp"`
    Message   ClaudeMessage `json:"message"`
    Direction string        `json:"direction"` // "client" or "claude"
}
```

### Message Protocol

```go
// Client to Server
type ClientMessage struct {
    Type      string      `json:"type"`
    ProjectID string      `json:"project_id,omitempty"`
    Data      interface{} `json:"data"`
}

// Server to Client
type ServerMessage struct {
    Type      string      `json:"type"`
    ProjectID string      `json:"project_id,omitempty"`
    Data      interface{} `json:"data"`
}

// Command Messages
type ExecuteCommand struct {
    Prompt  string         `json:"prompt"`
    Options *ClaudeOptions `json:"options,omitempty"`
}

type ClaudeOptions struct {
    DangerouslySkipPermissions bool     `json:"dangerously_skip_permissions,omitempty"`
    AllowedTools              []string  `json:"allowed_tools,omitempty"`
    DisallowedTools           []string  `json:"disallowed_tools,omitempty"`
    MCPConfig                 string    `json:"mcp_config,omitempty"`
    AppendSystemPrompt        string    `json:"append_system_prompt,omitempty"`
    PermissionMode            string    `json:"permission_mode,omitempty"`
    Model                     string    `json:"model,omitempty"`
    FallbackModel             string    `json:"fallback_model,omitempty"`
    AddDirs                   []string  `json:"add_dirs,omitempty"`
    StrictMCPConfig           bool      `json:"strict_mcp_config,omitempty"`
}

// Update Messages
type UpdateMessage struct {
    UpdateType string      `json:"update_type"`
    Data       interface{} `json:"data"`
}
```

## Message Flow Diagrams

### Project Creation and Execution

```mermaid
sequenceDiagram
    participant C as Client
    participant WS as WebSocket Handler
    participant PM as Project Manager
    participant CE as Claude Executor
    participant CLI as Claude CLI
    
    C->>WS: project_create
    WS->>PM: CreateProject(path)
    PM->>PM: Validate path
    PM->>PM: Generate ID
    PM->>PM: Save metadata
    PM-->>WS: Project
    WS-->>C: project_state update
    
    C->>WS: project_join
    WS->>PM: Add subscriber
    WS-->>C: project_joined
    
    C->>WS: execute command
    WS->>CE: Execute(project, cmd)
    CE->>CE: Acquire lock
    CE->>CLI: claude -p "prompt"
    CLI-->>CE: JSON output
    CE->>CE: Parse & update session
    CE->>PM: Save session ID
    CE-->>WS: Messages
    WS-->>C: agent_message
```

### Multi-Client Broadcasting

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant WS as WebSocket Handler
    participant P as Project
    
    C1->>WS: project_join(proj_123)
    WS->>P: Add subscriber C1
    
    C2->>WS: project_join(proj_123)
    WS->>P: Add subscriber C2
    
    C1->>WS: execute command
    WS->>P: Broadcast to subscribers
    P-->>C1: agent_message
    P-->>C2: agent_message
    
    Note over C1,C2: Both clients receive updates
```

## Error Handling

### Error Categories

1. **Validation Errors**
   - Invalid paths (traversal, relative, non-existent)
   - Project nesting violations
   - Message format errors
   - Invalid parameters

2. **Execution Errors**
   - Claude CLI not found
   - Process timeout
   - Process crash
   - JSON parsing errors

3. **Resource Errors**
   - Disk space exhaustion
   - Too many projects
   - Connection limits
   - Memory constraints

4. **System Errors**
   - File operation failures
   - Network errors
   - Corruption recovery
   - Unexpected panics

### Error Response Format

```go
type ErrorResponse struct {
    Type      string `json:"type"`      // "error"
    ProjectID string `json:"project_id,omitempty"`
    Data      struct {
        Code    string                 `json:"code"`
        Message string                 `json:"message"`
        Details map[string]interface{} `json:"details,omitempty"`
    } `json:"data"`
}
```

### Error Codes

- `INVALID_PATH`: Path validation failed
- `PROJECT_NESTING`: Project would nest with existing
- `PROJECT_NOT_FOUND`: Project ID not found
- `EXECUTION_TIMEOUT`: Claude execution exceeded timeout
- `CLAUDE_NOT_FOUND`: Claude CLI not installed
- `PROCESS_ACTIVE`: Cannot perform operation while executing
- `RESOURCE_LIMIT`: Resource limit exceeded
- `INTERNAL_ERROR`: Unexpected server error

## Metrics Integration

### Overview
The WebSocket server integrates with the main server's metrics collection system to provide unified monitoring and observability.

### Implementation
```go
// MetricsProvider interface allows external metrics collection
type MetricsProvider interface {
    IncrementConnections()
    DecrementConnections()
    IncrementMessages()
    IncrementErrors()
}

// WebSocket server accepts a metrics provider
func (s *Server) SetMetricsProvider(provider MetricsProvider)
```

### Metrics Tracked
- **Connection Metrics**: Active connections, total connections
- **Message Metrics**: Messages sent/received, broadcast performance
- **Error Metrics**: Connection errors, message parsing errors
- **Performance Metrics**: Message routing latency, broadcast duration

## Interactive Test Client

### Overview
A TypeScript CLI client for interactive testing and debugging of the WebSocket API.

### Features
- **Real-time Interaction**: Send prompts and receive Claude responses
- **Visual State Indicators**: 
  - 🟢 Green: IDLE (ready for commands)
  - 🟡 Yellow: EXECUTING (Claude is processing)
  - 🔴 Red: ERROR or disconnected
- **Permission Modes**: Switch between modes with Tab key
  - `default`: Standard permission handling
  - `acceptEdits`: Auto-accept file edits
  - `bypassPermissions`: Skip all permission prompts
  - `plan`: Enable planning mode
- **Debug Mode**: Toggle JSON message display with `json on/off`
- **Commands**: help, status, clear, exit/quit

### Usage
```bash
cd test-client
npm run interactive
```

## Testing Strategy

### Unit Testing
- Path validation edge cases
- Message parsing and routing
- Concurrent operations
- File rotation logic
- Error scenarios
- Session cleanup on disconnect

### Integration Testing
- WebSocket connection lifecycle
- Multi-client synchronization
- Server restart recovery
- Process timeout handling
- Message persistence
- 30-second timeout prevention
- Dead subscriber cleanup

### Interactive Testing
- Use the TypeScript test client for manual testing
- Test connection stability over time
- Verify multi-client broadcasting
- Test permission mode switching
- Validate state transitions

### Load Testing
- 100+ concurrent connections
- Rapid project creation/deletion
- Message broadcasting performance
- Memory usage under load
- Disk I/O performance

### Platform Testing
- Linux process management
- macOS security permissions
- Unix signal handling
- POSIX compliance verification

## Security Considerations

### Input Validation
- Strict path validation to prevent injection
- Message size limits (1MB default)
- Parameter sanitization
- JSON schema validation

### Process Security
- Execute with minimal privileges
- Resource limits on processes
- Timeout enforcement
- Process isolation

### Data Security
- No sensitive data in logs
- Atomic file operations
- Graceful corruption handling
- Future: SSH authentication

## Performance Optimizations

### Connection Management
- Goroutine per connection
- Non-blocking message broadcast
- Efficient subscriber maps
- Connection pooling

### File Operations
- Buffered writes
- Async log rotation
- Indexed message queries
- Memory-mapped files (future)

### Process Management
- Process pool (future)
- Command caching (future)
- Output streaming
- Resource monitoring

## Known Issues and Fixes

### Fixed Issues

1. **30-Second Connection Timeout**
   - **Issue**: Connections closed exactly 30 seconds after establishment
   - **Cause**: Initial read deadline set to `PongTimeout` only
   - **Fix**: Set initial deadline to `PingInterval + PongTimeout`
   - **File**: `server/internal/websocket/server.go`

2. **Metrics Not Reporting Connections**
   - **Issue**: Server metrics always showed 0 connections
   - **Cause**: WebSocket server didn't propagate metrics to main server
   - **Fix**: Implemented `MetricsProvider` interface for metrics integration
   - **Files**: `server/internal/websocket/metrics.go`, `server/internal/server.go`

3. **Excessive Log File Creation**
   - **Issue**: New log file created for each execution
   - **Cause**: Executor creating new MessageLog instances
   - **Fix**: Reuse project's existing MessageLog
   - **File**: `server/internal/executor/execute.go`

4. **Dead Subscriber Cleanup**
   - **Issue**: Disconnected sessions remained in project subscriber lists
   - **Cause**: `cleanupSession` didn't remove from projects
   - **Fix**: Added `OnSessionCleanup` to MessageHandler interface
   - **Files**: `server/internal/websocket/server.go`, `handlers/handlers.go`

### Message Type Corrections
- Changed `agent.message` to `agent_message`
- Changed `project.state` to `project_state`
- Ensures consistency with underscore naming convention

---
*Design Complete*
*Feature: WebSocket API*
*Module: Server*