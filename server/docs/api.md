# WebSocket API Documentation

## Overview

The Pocket Agent Server provides a WebSocket API for managing Claude CLI executions. All communication uses JSON messages over a persistent WebSocket connection.

## Connection

### Endpoint
```
wss://server:8443/ws
```

### Connection Flow
1. Client establishes WebSocket connection
2. Server accepts connection and creates session
3. Client sends commands
4. Server responds with results or broadcasts updates
5. Connection maintained with periodic pings

## Message Format

All messages follow this structure:

```typescript
interface Message {
  type: string;           // Message type identifier
  project_id?: string;    // Optional project ID
  data: any;             // Message-specific data
}
```

## Message Types

### Project Management

#### Create Project
**Request:**
```json
{
  "type": "project_create",
  "data": {
    "path": "/absolute/path/to/project"
  }
}
```

**Response:**
```json
{
  "type": "project_state",
  "project_id": "uuid-here",
  "data": {
    "state": "IDLE",
    "path": "/absolute/path/to/project",
    "created_at": "2024-01-01T12:00:00Z"
  }
}
```

#### List Projects
**Request:**
```json
{
  "type": "project_list"
}
```

**Response:**
```json
{
  "type": "project_list",
  "data": {
    "projects": [
      {
        "id": "uuid-here",
        "path": "/path/to/project",
        "state": "IDLE",
        "session_id": "claude-session-id",
        "created_at": "2024-01-01T12:00:00Z",
        "last_active": "2024-01-01T13:00:00Z"
      }
    ]
  }
}
```

#### Delete Project
**Request:**
```json
{
  "type": "project_delete",
  "project_id": "uuid-here"
}
```

**Response:**
```json
{
  "type": "project_deleted",
  "project_id": "uuid-here"
}
```

### Project Subscription

#### Join Project
**Request:**
```json
{
  "type": "project_join",
  "data": {
    "project_id": "uuid-here"
  }
}
```

**Response:**
```json
{
  "type": "project_joined",
  "project_id": "uuid-here"
}
```

#### Leave Project
**Request:**
```json
{
  "type": "project_leave",
  "project_id": "uuid-here"
}
```

**Response:**
```json
{
  "type": "project_left",
  "project_id": "uuid-here"
}
```

### Claude Execution

#### Execute Command
**Request:**
```json
{
  "type": "execute",
  "project_id": "uuid-here",
  "data": {
    "prompt": "Create a hello world program",
    "options": {
      "model": "claude-3.5-sonnet",
      "permission_mode": "auto",
      "allowed_tools": ["read", "write", "bash"]
    }
  }
}
```

**Broadcast to all subscribers:**
```json
{
  "type": "agent_message",
  "project_id": "uuid-here",
  "data": {
    "timestamp": "2024-01-01T12:00:00Z",
    "direction": "claude",
    "message": {
      // Claude response format
    }
  }
}
```

#### New Session
**Request:**
```json
{
  "type": "agent_new_session",
  "project_id": "uuid-here"
}
```

**Response:**
```json
{
  "type": "session_reset",
  "project_id": "uuid-here"
}
```

#### Kill Execution
**Request:**
```json
{
  "type": "agent_kill",
  "project_id": "uuid-here"
}
```

**Response:**
```json
{
  "type": "execution_killed",
  "project_id": "uuid-here"
}
```

### Message History

#### Get Messages
**Request:**
```json
{
  "type": "get_messages",
  "project_id": "uuid-here",
  "data": {
    "since": "2024-01-01T12:00:00Z"
  }
}
```

**Response:**
```json
{
  "type": "message_history",
  "project_id": "uuid-here",
  "data": {
    "messages": [
      {
        "timestamp": "2024-01-01T12:00:00Z",
        "direction": "client",
        "message": { /* message content */ }
      }
    ]
  }
}
```

## Error Handling

All errors follow this format:

```json
{
  "type": "error",
  "project_id": "uuid-here",
  "data": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": {
      // Optional additional context
    }
  }
}
```

### Error Codes

| Code | Description |
|------|-------------|
| `INVALID_PATH` | Path validation failed |
| `PROJECT_NESTING` | Project would nest with existing |
| `PROJECT_NOT_FOUND` | Project ID not found |
| `EXECUTION_TIMEOUT` | Claude execution exceeded timeout |
| `CLAUDE_NOT_FOUND` | Claude CLI not installed |
| `PROCESS_ACTIVE` | Cannot perform operation while executing |
| `RESOURCE_LIMIT` | Resource limit exceeded |
| `INTERNAL_ERROR` | Unexpected server error |

## Connection Management

### Ping/Pong
The server sends WebSocket ping frames every 5 minutes. Clients must respond with pong frames within 30 seconds or the connection will be closed.

### Reconnection
Clients should implement exponential backoff when reconnecting:
- Initial delay: 1 second
- Max delay: 30 seconds
- Backoff factor: 2

## Examples

### Complete Workflow Example

```javascript
// 1. Connect to WebSocket
const ws = new WebSocket('wss://server:8443/ws');

// 2. Create a project
ws.send(JSON.stringify({
  type: 'project_create',
  data: { path: '/home/user/myproject' }
}));

// 3. Join the project to receive updates
ws.send(JSON.stringify({
  type: 'project_join',
  data: { project_id: 'received-uuid' }
}));

// 4. Execute Claude command
ws.send(JSON.stringify({
  type: 'execute',
  project_id: 'received-uuid',
  data: {
    prompt: 'Analyze this codebase and suggest improvements'
  }
}));

// 5. Handle incoming messages
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  switch (msg.type) {
    case 'agent_message':
      console.log('Claude says:', msg.data.message);
      break;
    case 'error':
      console.error('Error:', msg.data.message);
      break;
  }
};
```

## Rate Limits

- Max 100 projects per server
- Max 10 executions per minute per project
- Max 1MB message size
- Max 10,000 messages per log file

## Best Practices

1. **Always validate responses** - Check for error messages
2. **Handle disconnections gracefully** - Implement reconnection logic
3. **Subscribe before executing** - Ensure you receive all updates
4. **Clean up resources** - Leave projects when done
5. **Respect rate limits** - Don't overwhelm the server