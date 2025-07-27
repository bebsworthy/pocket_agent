# Communication Layer - Cross-Module Context

## Overview

The Communication Layer provides the foundation for real-time, bidirectional communication between Pocket Agent client applications (Android mobile, React web) and the Claude CLI execution server running on development machines. This cross-module feature enables developers to interact with Claude CLI from any supported client device through a project-based execution model with persistent message history.

## Business Context

### Problem Statement

Developers need to manage and monitor their Claude CLI executions organized by project while away from their development machines. The Claude CLI (`claude -p <prompt>`) executes and exits, requiring session management for context continuity. This creates several challenges:

- **Project Organization**: Need to organize Claude executions by project directory with isolated contexts
- **Session Continuity**: Claude CLI exits after each execution, requiring session ID management
- **Execution Persistence**: Commands and responses must be persisted across server restarts
- **Multi-Client Access**: Multiple clients need to observe the same project execution
- **Platform Limitations**: Server runs on Unix/POSIX systems only (Linux and macOS)

### Target Users

1. **Mobile Developers**: Need to monitor long-running tasks or respond to Claude while commuting or in meetings
2. **Web Users**: Prefer browser-based access for quick interactions or when mobile app isn't available
3. **Team Collaborators**: Multiple developers working on the same project need shared visibility
4. **Remote Workers**: Need access to project-specific Claude sessions from various locations
5. **Platform Users**: Developers on Linux or macOS systems (Windows not supported)

### Business Value

- **Increased Productivity**: Developers can maintain workflow continuity regardless of device or location
- **Faster Response Times**: Critical permissions can be approved immediately from any client
- **Project Isolation**: Each project maintains its own execution context and message history
- **Persistent History**: All messages persisted to disk with automatic rotation (1GB/30 days)
- **Server Resilience**: Projects and sessions survive server restarts
- **Platform Focus**: Optimized for Unix/POSIX systems without Windows complexity

## Cross-Module Architecture

### Module Responsibilities

#### Server Module
- **WebSocket Server**: Provides the central communication hub (no REST API)
- **Project Management**: Creates, deletes, and manages project-based executions
- **Claude Execution**: Manages Claude CLI process lifecycle with timeouts
- **Message Persistence**: Stores all messages with file rotation
- **Multi-Client Broadcasting**: Broadcasts updates to all project subscribers

#### Frontend-Android Module
- **Mobile Client**: Native Android WebSocket client optimized for mobile networks
- **Background Connectivity**: Maintains connections during app backgrounding
- **Mobile UX**: Touch-optimized interface for permission approvals
- **Offline Handling**: Queues messages and handles disconnections gracefully
- **Battery Optimization**: Manages connection frequency based on battery state

#### Frontend-React Module
- **Web Client**: Browser-based WebSocket client for desktop/tablet access
- **Responsive Design**: Adapts to different screen sizes and input methods
- **Browser Compatibility**: Works across modern browsers with WebSocket support
- **Session Persistence**: Maintains sessions across browser tabs and refreshes
- **Real-time Updates**: Provides instant feedback and notifications in browser

## Integration Scenarios

### Project-Based Execution
A developer creates a project for `/home/user/myapp`, executes Claude commands that maintain context through session IDs, with all messages persisted to disk.

### Multi-Client Project Monitoring  
Multiple team members join the same project, receiving real-time updates as Claude executes commands, with full message history available.

### Server Restart Recovery
Server crashes and restarts, automatically reloading all projects with their session IDs intact, allowing seamless continuation of work.

## Success Criteria

### Cross-Module Integration
1. **Protocol Consistency**: WebSocket-only protocol (no REST API) across all clients
2. **Project Model**: All modules understand project-based execution model
3. **Message Persistence**: Server maintains complete history accessible to all clients
4. **Platform Compatibility**: Server on Linux/macOS, clients on any platform

### Platform-Specific Performance
- **Mobile**: Sub-3-second authentication, 99% uptime during normal networks
- **Web**: Instant connection establishment, real-time updates without refresh
- **Server**: Handle 100+ concurrent connections, sub-100ms message routing

## Module Integration Points

### Shared Protocol
- **Message Format**: JSON-based WebSocket protocol (no REST endpoints)
- **Project Operations**: Create, delete, list, join, leave projects
- **Execution Commands**: Execute, new session, kill active execution
- **Message History**: Query historical messages with timestamp filtering

### Platform-Specific Adaptations
- **Mobile**: Connection persistence during network changes
- **Web**: Browser security model compliance  
- **Server**: Unix/POSIX only (Linux and macOS), no Windows support
- **Testing**: Mock Claude CLI required (FORBIDDEN: real Claude API in tests)

---

*This is the cross-module context for Communication Layer spanning server, frontend-android, and frontend-react modules.*