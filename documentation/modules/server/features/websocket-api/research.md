# WebSocket API Research

## Research Summary

### Claude CLI Execution Model
Claude CLI operates as a command-line tool that:
- Executes with `claude -p <prompt>` and exits
- Maintains conversation context via `-c <session_id>` flag
- Outputs JSON messages to stdout
- Supports various options for model selection, permissions, and tools

### Key Technical Findings

#### 1. Project-Based Organization
- Projects identified by filesystem paths (e.g., `/wip/project1`)
- No nesting allowed between project paths
- Each project maintains its own Claude session ID
- Sequential execution within projects, parallel across projects

#### 2. Message Protocol
Based on analysis of `claude-code-sdk-messages.doc.md`:
- Claude outputs JSON messages: `assistant`, `user`, `result`, `system`
- Session ID included in all messages
- No built-in progress tracking (must parse from text)
- Cost and duration metrics in result messages

#### 3. Persistence Requirements
- Projects and session IDs must survive server restarts
- Message history stored in rotating log files
- Metadata saved as JSON for quick recovery
- File-based storage to handle large message volumes

#### 4. WebSocket Patterns
- Pure WebSocket server (no REST endpoints)
- Message routing based on project context
- Subscription model for efficient broadcasting
- Client can join/leave projects dynamically

### Integration Points

#### Claude CLI Integration
```bash
# Initial execution
claude -p "prompt" [options]

# Continued conversation
claude -p "prompt" -c session_id [options]
```

#### File System Structure
```
data/
└── projects/
    └── {project_id}/
        ├── metadata.json
        └── logs/
            ├── messages_YYYY-MM-DD_HH-MM-SS.jsonl
            └── latest.jsonl -> current_file
```

### Identified Risks

1. **Security Risks**
   - Command injection via project paths
   - Process execution vulnerabilities
   - Resource exhaustion attacks

2. **Operational Risks**
   - Claude process timeouts
   - Zombie processes
   - Disk space exhaustion
   - Memory leaks from subscriber maps

3. **Platform Risks**
   - macOS security permission prompts
   - Linux process group handling
   - File locking semantics across Unix systems

### Design Considerations

1. **Process Management**
   - Must track active processes for cancellation
   - Implement timeout mechanism (5 minutes default)
   - Clean shutdown of all processes

2. **Connection Management**
   - WebSocket ping/pong for health
   - Automatic cleanup of stale connections
   - Backpressure for slow clients

3. **Data Integrity**
   - Atomic file operations
   - Corruption recovery
   - Log rotation strategy

### Technology Recommendations

Based on the Go technology stack:
- **WebSocket**: Gorilla WebSocket (mature, well-tested)
- **Process Management**: os/exec with context
- **Logging**: Structured logging with zap or logrus
- **File Operations**: Use atomic writes with temp + rename

---
*Research Complete*
*Feature: WebSocket API*
*Module: Server*