# Pocket Agent Server

[![CI](https://github.com/boyd/pocket_agent/actions/workflows/ci.yml/badge.svg)](https://github.com/boyd/pocket_agent/actions/workflows/ci.yml)
[![Go Report Card](https://goreportcard.com/badge/github.com/boyd/pocket_agent/server)](https://goreportcard.com/report/github.com/boyd/pocket_agent/server)
[![codecov](https://codecov.io/gh/boyd/pocket_agent/branch/main/graph/badge.svg)](https://codecov.io/gh/boyd/pocket_agent)
[![Go Reference](https://pkg.go.dev/badge/github.com/boyd/pocket_agent/server.svg)](https://pkg.go.dev/github.com/boyd/pocket_agent/server)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A high-performance WebSocket server for Pocket Agent that enables remote execution of Claude CLI commands with real-time streaming, multi-client support, and persistent project management.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [API Documentation](#api-documentation)
- [Operational Guide](#operational-guide)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [Security](#security)
- [Performance](#performance)
- [Deployment](#deployment)
- [License](#license)

## Features

- **WebSocket API**: Real-time bidirectional communication over secure WebSocket connections
- **Project Management**: Create and manage isolated project contexts with persistent state
- **Multi-Client Support**: Multiple clients can subscribe to and monitor the same project
- **Session Persistence**: Claude conversation sessions persist across reconnections
- **Message History**: Full conversation history with automatic log rotation
- **Process Control**: Execute, monitor, and cancel Claude CLI processes
- **Resource Management**: Built-in limits and monitoring to prevent resource exhaustion
- **Cross-Platform**: Supports Linux and macOS with proper signal handling

## Requirements

- Go 1.21 or later
- Claude CLI installed and accessible in PATH
- Linux or macOS operating system
- TLS certificate and key for secure WebSocket connections
- Minimum 2GB RAM
- 10GB available disk space for logs

## Installation

### From Source

```bash
# Clone the repository
git clone https://github.com/boyd/pocket_agent.git
cd pocket_agent/server

# Install dependencies
make deps

# Install development tools
make install-tools

# Build the server
make build

# Run tests
make test

# Install to system (optional)
sudo make install
```

### Using Docker

```bash
# Build the Docker image
docker build -t pocket-agent-server .

# Run with Docker Compose
docker-compose up -d
```

### Binary Releases

Download pre-built binaries from the [releases page](https://github.com/boyd/pocket_agent/releases).

## Configuration

The server can be configured via environment variables, configuration file, or command-line flags.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PA_SERVER_HOST` | Server bind address | `0.0.0.0` |
| `PA_SERVER_PORT` | Server port | `8443` |
| `PA_TLS_CERT` | Path to TLS certificate | `./certs/server.crt` |
| `PA_TLS_KEY` | Path to TLS key | `./certs/server.key` |
| `PA_DATA_DIR` | Data storage directory | `./data` |
| `PA_LOG_LEVEL` | Log level (debug/info/warn/error) | `info` |
| `PA_LOG_FORMAT` | Log format (json/text) | `json` |
| `PA_MAX_CONNECTIONS` | Maximum concurrent connections | `100` |
| `PA_MAX_PROJECTS` | Maximum number of projects | `100` |
| `PA_MESSAGE_SIZE_LIMIT` | Maximum WebSocket message size | `1048576` (1MB) |
| `PA_EXECUTION_TIMEOUT` | Claude execution timeout | `5m` |
| `PA_IDLE_TIMEOUT` | Connection idle timeout | `5m` |
| `PA_PING_INTERVAL` | WebSocket ping interval | `30s` |
| `PA_PONG_TIMEOUT` | Pong response timeout | `30s` |
| `PA_LOG_ROTATION_SIZE` | Log file rotation size | `104857600` (100MB) |
| `PA_LOG_ROTATION_COUNT` | Max messages before rotation | `10000` |

### Configuration File

Create a `config.yaml` file:

```yaml
server:
  host: 0.0.0.0
  port: 8443
  tls:
    cert: ./certs/server.crt
    key: ./certs/server.key

data:
  dir: ./data

logging:
  level: info
  format: json
  output: stdout  # stdout, stderr, or file path

limits:
  max_connections: 100
  max_projects: 100
  message_size: 1048576
  
timeouts:
  execution: 5m
  idle: 5m
  ping_interval: 30s
  pong_timeout: 30s

log_rotation:
  size: 104857600
  count: 10000

claude:
  binary: claude  # Path to Claude CLI
  default_model: claude-3-5-sonnet-20241022
  allowed_tools:
    - read
    - write
    - run
```

### Command-Line Flags

```bash
./bin/pocket-agent-server \
  --config config.yaml \
  --host 0.0.0.0 \
  --port 8443 \
  --data-dir ./data \
  --log-level debug
```

## Quick Start

### 1. Generate TLS Certificates

For development:
```bash
make certs
```

For production, use proper certificates from a CA.

### 2. Start the Server

```bash
./bin/pocket-agent-server
```

### 3. Connect via WebSocket

JavaScript example:
```javascript
const ws = new WebSocket('wss://localhost:8443/ws');

ws.on('open', () => {
  console.log('Connected to server');
  
  // Create a project
  ws.send(JSON.stringify({
    type: 'project_create',
    data: { path: '/path/to/project' }
  }));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data);
  console.log('Received:', msg);
  
  if (msg.type === 'project_state' && msg.data.state === 'IDLE') {
    // Execute a command
    ws.send(JSON.stringify({
      type: 'execute',
      project_id: msg.project_id,
      data: {
        prompt: 'Create a hello world program in Go'
      }
    }));
  }
});

ws.on('error', (err) => {
  console.error('WebSocket error:', err);
});

ws.on('close', () => {
  console.log('Disconnected from server');
});
```

## Architecture

The server follows a hexagonal architecture pattern with clear separation of concerns.

### System Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Android   │     │    React    │     │   React 2   │
│   Client    │     │   Client    │     │   Client    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
                           │
                      WSS (8443)
                           │
┌──────────────────────────┴──────────────────────────┐
│                   Server Process                     │
├──────────────────────────────────────────────────────┤
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │ WebSocket  │  │   Project   │  │    Claude    │ │
│  │  Handler   │──│   Manager   │  │   Executor   │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
│         │               │                  │         │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐ │
│  │  Session   │  │   Message   │  │   Process    │ │
│  │  Manager   │  │     Log     │  │   Manager    │ │
│  └────────────┘  └─────────────┘  └──────────────┘ │
└──────────────────────────────────────────────────────┘
                           │
                    ┌──────┴──────┐
                    │  File System │
                    │  (./data/)   │
                    └──────────────┘
```

### Component Responsibilities

1. **WebSocket Handler**
   - Accepts WebSocket connections
   - Routes messages to appropriate handlers
   - Manages session lifecycle
   - Broadcasts updates to subscribers

2. **Project Manager**
   - Creates and validates projects
   - Persists project metadata
   - Enforces project limits
   - Manages project state transitions

3. **Claude Executor**
   - Executes Claude CLI commands
   - Manages process lifecycle
   - Parses Claude output
   - Enforces timeouts

4. **Session Manager**
   - Tracks active connections
   - Manages project subscriptions
   - Handles connection health checks
   - Cleans up on disconnect

5. **Message Log**
   - Stores conversation history
   - Implements log rotation
   - Provides message queries
   - Ensures atomic writes

## API Documentation

### WebSocket Endpoint

- **URL**: `wss://server:8443/ws`
- **Protocol**: WebSocket with JSON messages
- **Subprotocol**: None required
- **Authentication**: None (MVP)

### Message Format

All messages follow this structure:

```typescript
// Client to Server
interface ClientMessage {
  type: string;
  project_id?: string;
  data?: any;
  request_id?: string;  // Optional for request tracking
}

// Server to Client
interface ServerMessage {
  type: string;
  project_id?: string;
  data: any;
  request_id?: string;  // Echoed from request if provided
  timestamp: string;    // ISO 8601 format
}
```

### Message Types

#### Project Management

**Create Project**
```json
{
  "type": "project_create",
  "data": {
    "path": "/absolute/path/to/project"
  }
}

// Response
{
  "type": "project_state",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "state": "IDLE",
    "path": "/absolute/path/to/project",
    "created_at": "2024-01-01T12:00:00Z",
    "session_id": null
  }
}
```

**List Projects**
```json
{
  "type": "project_list"
}

// Response
{
  "type": "project_list_response",
  "data": {
    "projects": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "path": "/path/to/project",
        "state": "IDLE",
        "created_at": "2024-01-01T12:00:00Z",
        "last_active": "2024-01-01T13:00:00Z",
        "session_id": "claude-session-123"
      }
    ]
  }
}
```

**Delete Project**
```json
{
  "type": "project_delete",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}

// Response
{
  "type": "project_deleted",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Join Project** (Subscribe to updates)
```json
{
  "type": "project_join",
  "data": {
    "project_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}

// Response
{
  "type": "project_joined",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Leave Project** (Unsubscribe)
```json
{
  "type": "project_leave",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}

// Response
{
  "type": "project_left",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Claude Execution

**Execute Command**
```json
{
  "type": "execute",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "prompt": "Create a hello world program in Go",
    "options": {
      "model": "claude-3-5-sonnet-20241022",
      "dangerously_skip_permissions": false,
      "allowed_tools": ["read", "write", "run"],
      "disallowed_tools": [],
      "permission_mode": "default",
      "mcp_config": null,
      "append_system_prompt": null,
      "fallback_model": null,
      "add_dirs": [],
      "strict_mcp_config": false
    }
  }
}

// Responses (streamed)
{
  "type": "project_state",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "state": "EXECUTING"
  }
}

{
  "type": "agent_message",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "type": "message_start",
    "message": {
      "id": "msg_123",
      "type": "message",
      "role": "assistant",
      "model": "claude-3-5-sonnet-20241022"
    }
  }
}

// ... more agent_message events ...

{
  "type": "project_state",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "state": "IDLE",
    "session_id": "claude-session-456"
  }
}
```

**Kill Execution**
```json
{
  "type": "agent_kill",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}

// Response
{
  "type": "project_state",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "state": "IDLE"
  }
}
```

**New Session** (Clear conversation context)
```json
{
  "type": "agent_new_session",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}

// Response
{
  "type": "session_reset",
  "project_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Message History

**Get Messages**
```json
{
  "type": "get_messages",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "since": "2024-01-01T00:00:00Z",
    "limit": 100  // Optional, default 1000
  }
}

// Response
{
  "type": "messages_response",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "messages": [
      {
        "timestamp": "2024-01-01T12:00:00Z",
        "direction": "client",
        "message": {
          "type": "execute",
          "data": {
            "prompt": "Create a hello world program"
          }
        }
      },
      {
        "timestamp": "2024-01-01T12:00:01Z",
        "direction": "claude",
        "message": {
          "type": "message_start",
          "message": {
            "id": "msg_123",
            "type": "message",
            "role": "assistant"
          }
        }
      }
    ]
  }
}
```

### Error Responses

All errors follow this format:

```json
{
  "type": "error",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "data": {
    "code": "PROJECT_NOT_FOUND",
    "message": "Project not found: 550e8400-e29b-41d4-a716-446655440000",
    "details": {
      "project_id": "550e8400-e29b-41d4-a716-446655440000"
    }
  },
  "request_id": "req-123",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Codes

| Code | Description | HTTP Equivalent |
|------|-------------|-----------------|
| `INVALID_MESSAGE` | Message format or schema invalid | 400 |
| `INVALID_PATH` | Path validation failed | 400 |
| `PROJECT_NESTING` | Project would nest with existing | 409 |
| `PROJECT_NOT_FOUND` | Project ID not found | 404 |
| `PROJECT_LIMIT` | Maximum projects exceeded | 429 |
| `EXECUTION_TIMEOUT` | Claude execution exceeded timeout | 504 |
| `CLAUDE_NOT_FOUND` | Claude CLI not installed | 503 |
| `PROCESS_ACTIVE` | Cannot perform operation while executing | 409 |
| `RESOURCE_LIMIT` | Resource limit exceeded | 429 |
| `INTERNAL_ERROR` | Unexpected server error | 500 |

## Operational Guide

### Pre-Deployment Checklist

1. **System Requirements**
   - Verify Go 1.21+ installed
   - Confirm Claude CLI accessible
   - Check disk space (minimum 10GB)
   - Ensure adequate RAM (minimum 2GB)

2. **Network Configuration**
   - Open firewall port 8443
   - Configure TLS certificates
   - Set up DNS if needed
   - Configure reverse proxy (optional)

3. **Security Setup**
   - Generate strong TLS certificates
   - Set appropriate file permissions
   - Create dedicated service user
   - Configure system limits

### Starting the Server

1. **Development Mode**
   ```bash
   # With debug logging
   PA_LOG_LEVEL=debug ./bin/pocket-agent-server
   ```

2. **Production Mode**
   ```bash
   # As systemd service
   sudo systemctl start pocket-agent-server
   
   # With custom config
   ./bin/pocket-agent-server --config /etc/pocket-agent/config.yaml
   ```

3. **Docker Mode**
   ```bash
   docker run -d \
     -p 8443:8443 \
     -v /opt/pocket-agent/data:/data \
     -v /opt/pocket-agent/certs:/certs:ro \
     pocket-agent-server:latest
   ```

### Graceful Shutdown

The server supports graceful shutdown to ensure data integrity:

1. Send SIGTERM signal:
   ```bash
   kill -TERM $(pidof pocket-agent-server)
   ```

2. Server will:
   - Stop accepting new connections
   - Wait for active executions to complete (max 30s)
   - Save all project metadata
   - Flush message logs
   - Close all connections
   - Exit cleanly

### Backup and Recovery

1. **What to Backup**
   - Data directory (`./data/`)
   - Configuration files
   - TLS certificates
   - Server logs

2. **Backup Script Example**
   ```bash
   #!/bin/bash
   BACKUP_DIR="/backup/pocket-agent/$(date +%Y%m%d)"
   mkdir -p "$BACKUP_DIR"
   
   # Stop server for consistency
   systemctl stop pocket-agent-server
   
   # Backup data
   tar -czf "$BACKUP_DIR/data.tar.gz" /opt/pocket-agent/data
   cp /etc/pocket-agent/config.yaml "$BACKUP_DIR/"
   
   # Restart server
   systemctl start pocket-agent-server
   ```

3. **Recovery Process**
   ```bash
   # Stop server
   systemctl stop pocket-agent-server
   
   # Restore data
   tar -xzf /backup/pocket-agent/20240101/data.tar.gz -C /
   
   # Start server
   systemctl start pocket-agent-server
   ```

### Log Management

1. **Log Locations**
   - Server logs: Configured output (stdout/file)
   - Message logs: `./data/projects/{id}/logs/`
   - Access logs: If reverse proxy configured

2. **Log Rotation**
   - Automatic rotation at 100MB or 10,000 messages
   - Filename format: `messages_YYYY-MM-DD_HH-MM-SS.jsonl`
   - Old logs are not deleted automatically

3. **Log Analysis**
   ```bash
   # Count messages by type
   jq -r '.message.type' messages_*.jsonl | sort | uniq -c
   
   # Find errors
   jq 'select(.level == "error")' server.log
   
   # Extract execution durations
   jq -r 'select(.msg == "Execution completed") | .duration_ms' server.log
   ```

### Maintenance Tasks

1. **Daily**
   - Monitor disk usage
   - Check error logs
   - Verify Claude CLI availability

2. **Weekly**
   - Archive old message logs
   - Review resource usage trends
   - Update monitoring dashboards

3. **Monthly**
   - Clean up orphaned projects
   - Review and update limits
   - Performance analysis
   - Security audit

## Monitoring

### Health Check

WebSocket-based health endpoint:

```javascript
// Health check client
const ws = new WebSocket('wss://localhost:8443/ws');

ws.on('open', () => {
  ws.send(JSON.stringify({ type: 'health' }));
});

ws.on('message', (data) => {
  const health = JSON.parse(data);
  console.log('Health status:', health);
});

// Response format
{
  "type": "health_status",
  "data": {
    "status": "healthy",  // healthy, degraded, unhealthy
    "uptime": 3600,       // seconds
    "version": "1.0.0",
    "connections": {
      "active": 10,
      "limit": 100
    },
    "projects": {
      "count": 5,
      "limit": 100,
      "executing": 1
    },
    "resources": {
      "cpu_percent": 15.5,
      "memory_mb": 256,
      "goroutines": 42,
      "disk_free_gb": 45.2
    },
    "claude": {
      "available": true,
      "version": "0.6.12"
    }
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Metrics

The server periodically broadcasts metrics (every 10 seconds) to all connected clients:

```json
{
  "type": "server_stats",
  "data": {
    "connections": 10,
    "projects": 5,
    "executions": {
      "active": 1,
      "total": 150,
      "success": 145,
      "failed": 5,
      "avg_duration_ms": 1234
    },
    "messages": {
      "sent": 10000,
      "received": 5000,
      "rate_per_sec": 5.2
    },
    "uptime": 3600
  }
}
```

### Prometheus Metrics (Future)

Planned metrics endpoint at `/metrics`:
- `pocket_agent_connections_active`
- `pocket_agent_projects_total`
- `pocket_agent_executions_total`
- `pocket_agent_execution_duration_seconds`
- `pocket_agent_messages_total`
- `pocket_agent_errors_total`

### Logging

Structured logging format:

```json
{
  "timestamp": "2024-01-01T12:00:00.123Z",
  "level": "info",
  "msg": "Execution completed",
  "correlation_id": "req-123",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "session_id": "claude-789",
  "duration_ms": 1234,
  "exit_code": 0,
  "fields": {
    "prompt_length": 50,
    "response_length": 500
  }
}
```

Log levels:
- `debug`: Detailed debugging information
- `info`: Normal operational messages
- `warn`: Warning conditions
- `error`: Error conditions
- `fatal`: Fatal errors causing shutdown

## Troubleshooting

### Common Issues

#### WebSocket Connection Fails

**Symptoms**: Client cannot connect, connection immediately closes

**Causes and Solutions**:

1. **TLS Certificate Issues**
   ```bash
   # Check certificate validity
   openssl s_client -connect localhost:8443 -servername localhost
   
   # Verify certificate paths
   ls -la ./certs/
   
   # Check certificate expiration
   openssl x509 -in ./certs/server.crt -noout -dates
   ```

2. **Port Already in Use**
   ```bash
   # Find process using port
   lsof -i :8443
   
   # Kill existing process or use different port
   PA_SERVER_PORT=8444 ./bin/pocket-agent-server
   ```

3. **Firewall Blocking**
   ```bash
   # Check firewall rules (Linux)
   sudo iptables -L -n | grep 8443
   
   # Add firewall rule
   sudo iptables -A INPUT -p tcp --dport 8443 -j ACCEPT
   ```

#### Claude Execution Fails

**Symptoms**: Executions fail immediately or timeout

**Causes and Solutions**:

1. **Claude CLI Not Found**
   ```bash
   # Check Claude installation
   which claude
   
   # Add to PATH or configure binary path
   export PATH=$PATH:/path/to/claude
   
   # Or in config
   claude:
     binary: /absolute/path/to/claude
   ```

2. **Permission Issues**
   ```bash
   # Check project directory permissions
   ls -la /path/to/project
   
   # Ensure server user has access
   sudo -u pocket-agent claude -p "test"
   ```

3. **Resource Limits**
   ```bash
   # Check system limits
   ulimit -a
   
   # Increase limits if needed
   ulimit -n 4096  # file descriptors
   ulimit -u 2048  # processes
   ```

#### High Memory Usage

**Symptoms**: Server consuming excessive memory

**Causes and Solutions**:

1. **Large Message Backlogs**
   ```bash
   # Check message log sizes
   du -sh ./data/projects/*/logs/
   
   # Archive old logs
   find ./data/projects/*/logs -name "*.jsonl" -mtime +30 -exec gzip {} \;
   ```

2. **Connection Leaks**
   ```bash
   # Monitor goroutines
   curl http://localhost:6060/debug/pprof/goroutine?debug=1
   
   # Restart server if needed
   systemctl restart pocket-agent-server
   ```

3. **Memory Profiling**
   ```bash
   # Enable profiling
   PA_ENABLE_PPROF=true ./bin/pocket-agent-server
   
   # Capture heap profile
   go tool pprof http://localhost:6060/debug/pprof/heap
   ```

#### Message History Missing

**Symptoms**: Cannot retrieve historical messages

**Causes and Solutions**:

1. **Log Files Corrupted**
   ```bash
   # Check log file integrity
   for f in ./data/projects/*/logs/*.jsonl; do
     jq empty < "$f" || echo "Corrupted: $f"
   done
   
   # Move corrupted files
   mkdir ./data/corrupted
   mv corrupted.jsonl ./data/corrupted/
   ```

2. **Disk Space Issues**
   ```bash
   # Check disk space
   df -h ./data
   
   # Clean up old logs
   find ./data -name "*.jsonl" -mtime +90 -delete
   ```

### Debug Mode

Enable comprehensive debugging:

```bash
# Maximum debug output
PA_LOG_LEVEL=debug \
PA_LOG_FORMAT=text \
PA_ENABLE_PPROF=true \
PA_TRACE_ENABLED=true \
./bin/pocket-agent-server
```

Debug endpoints (when pprof enabled):
- `http://localhost:6060/debug/pprof/` - Profile index
- `http://localhost:6060/debug/pprof/goroutine` - Goroutine stack traces
- `http://localhost:6060/debug/pprof/heap` - Heap profile
- `http://localhost:6060/debug/pprof/trace?seconds=30` - Execution trace

### Performance Tuning

1. **Connection Limits**
   ```yaml
   limits:
     max_connections: 200  # Increase if needed
     connection_rate: 10   # Connections per second
   ```

2. **Message Buffer Sizes**
   ```yaml
   websocket:
     read_buffer: 4096
     write_buffer: 4096
     message_buffer: 100  # Per-connection queue
   ```

3. **Execution Concurrency**
   ```yaml
   execution:
     max_concurrent: 10   # Parallel executions
     queue_size: 100      # Pending executions
   ```

4. **System Tuning**
   ```bash
   # Increase file descriptors
   echo "* soft nofile 65536" >> /etc/security/limits.conf
   echo "* hard nofile 65536" >> /etc/security/limits.conf
   
   # TCP tuning
   sysctl -w net.core.somaxconn=1024
   sysctl -w net.ipv4.tcp_max_syn_backlog=1024
   ```

## Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/boyd/pocket_agent.git
cd pocket_agent/server

# Install dependencies
go mod download

# Run tests
make test

# Run with race detector
make test-race

# Build binary
make build

# Run linters
make lint

# Format code
make fmt
```

### Project Structure

```
server/
├── cmd/
│   └── server/              # Application entry point
│       └── main.go
├── internal/                # Private application code
│   ├── config/             # Configuration management
│   │   ├── config.go
│   │   └── validation.go
│   ├── domain/             # Business entities
│   │   ├── project.go
│   │   ├── session.go
│   │   └── message.go
│   ├── application/        # Use cases
│   │   ├── project_service.go
│   │   ├── claude_service.go
│   │   └── session_service.go
│   ├── infrastructure/     # External interfaces
│   │   ├── websocket/      # WebSocket implementation
│   │   │   ├── handler.go
│   │   │   ├── router.go
│   │   │   └── session.go
│   │   ├── storage/        # Persistence layer
│   │   │   ├── project_repository.go
│   │   │   └── message_log.go
│   │   └── claude/         # Claude CLI integration
│   │       ├── executor.go
│   │       └── parser.go
│   └── server/             # Server setup
│       ├── server.go
│       └── middleware.go
├── pkg/                    # Public packages
│   ├── logger/
│   ├── errors/
│   └── validation/
├── test/                   # Test files
│   ├── integration/
│   ├── mocks/
│   └── fixtures/
└── docs/                   # Documentation
    ├── api/
    ├── architecture/
    └── deployment/
```

### Testing

```bash
# Unit tests
go test ./...

# Integration tests
go test -tags=integration ./test/integration

# Specific package
go test -v ./internal/infrastructure/websocket

# With coverage
go test -cover -coverprofile=coverage.out ./...
go tool cover -html=coverage.out

# Benchmarks
go test -bench=. ./...

# Mock generation
go generate ./...
```

### Code Quality

Pre-commit hooks:
```bash
# Install hooks
make pre-commit-install

# Run manually
make pre-commit
```

Linting rules (`.golangci.yml`):
```yaml
linters:
  enable:
    - gofmt
    - golint
    - govet
    - ineffassign
    - misspell
    - unconvert
    - goconst
    - gocyclo
    - gosec
```

## Security

### Security Features

1. **Input Validation**
   - Path traversal prevention
   - Message size limits
   - Parameter sanitization
   - JSON schema validation

2. **Process Isolation**
   - Claude runs in separate process
   - Limited resource allocation
   - No shell injection possible
   - Timeout enforcement

3. **Data Protection**
   - Atomic file operations
   - No sensitive data in logs
   - TLS encryption required
   - Secure random IDs

### Security Best Practices

1. **TLS Configuration**
   ```yaml
   tls:
     min_version: "1.2"
     cipher_suites:
       - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
       - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
   ```

2. **File Permissions**
   ```bash
   # Data directory
   chmod 750 /opt/pocket-agent/data
   chown pocket-agent:pocket-agent /opt/pocket-agent/data
   
   # Config files
   chmod 640 /etc/pocket-agent/config.yaml
   chown root:pocket-agent /etc/pocket-agent/config.yaml
   
   # TLS certificates
   chmod 600 /opt/pocket-agent/certs/server.key
   chmod 644 /opt/pocket-agent/certs/server.crt
   ```

3. **System Hardening**
   ```bash
   # Create dedicated user
   useradd -r -s /bin/false pocket-agent
   
   # Set resource limits
   echo "pocket-agent soft nproc 100" >> /etc/security/limits.conf
   echo "pocket-agent hard nproc 200" >> /etc/security/limits.conf
   ```

### Security Checklist

- [ ] TLS certificates from trusted CA
- [ ] Strong cipher suites configured
- [ ] File permissions properly set
- [ ] Dedicated service user created
- [ ] Resource limits configured
- [ ] Firewall rules in place
- [ ] Regular security updates
- [ ] Log monitoring enabled
- [ ] Backup encryption enabled

## Performance

### Performance Characteristics

- **Connection Handling**: 100+ concurrent WebSocket connections
- **Message Routing**: < 10ms latency for message routing
- **Execution Throughput**: 10+ Claude executions per minute
- **Message Rate**: 1000+ messages per second
- **Memory Usage**: ~2MB per idle connection
- **CPU Usage**: < 5% with 100 connections (idle)

### Benchmarks

Run performance benchmarks:
```bash
# Message routing benchmark
go test -bench=BenchmarkMessageRouting ./internal/infrastructure/websocket

# Execution benchmark
go test -bench=BenchmarkClaudeExecution ./internal/infrastructure/claude

# Load test
make load-test
```

### Optimization Tips

1. **Enable Message Compression**
   ```yaml
   websocket:
     enable_compression: true
     compression_level: 6
   ```

2. **Tune Buffer Sizes**
   ```yaml
   websocket:
     read_buffer: 8192
     write_buffer: 8192
   ```

3. **Connection Pooling** (Future)
   ```yaml
   connection_pool:
     size: 10
     idle_timeout: 30s
   ```

## Deployment

### Docker Deployment

See [Docker Deployment Guide](./docs/deployment/docker.md) for detailed instructions.

Quick start:
```bash
# Build image
docker build -t pocket-agent-server .

# Run container
docker run -d \
  --name pocket-agent \
  -p 8443:8443 \
  -v $(pwd)/data:/data \
  -v $(pwd)/certs:/certs:ro \
  pocket-agent-server
```

### Systemd Deployment

See [Systemd Deployment Guide](./docs/deployment/systemd.md) for detailed instructions.

### Kubernetes Deployment (Future)

Planned Kubernetes support with:
- StatefulSet for data persistence
- Service for load balancing
- ConfigMap for configuration
- Secret for TLS certificates
- HorizontalPodAutoscaler for scaling

### High Availability (Future)

Planned HA features:
- Redis for shared state
- Multiple server instances
- Load balancer support
- Session affinity
- Automatic failover

## License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/boyd/pocket_agent/issues)
- **Discussions**: [GitHub Discussions](https://github.com/boyd/pocket_agent/discussions)
- **Documentation**: [Full Documentation](https://pocket-agent.dev/docs)
- **Community**: [Discord Server](https://discord.gg/pocket-agent)

---

For more information, visit the [main project repository](https://github.com/boyd/pocket_agent).