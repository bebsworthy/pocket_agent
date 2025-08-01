# WebSocket API Requirements

## User Stories

### Story 1: WebSocket Connection Management
**As a** client application developer  
**I want** to connect to the server via WebSocket  
**So that** I can execute Claude commands remotely

#### Acceptance Criteria
1.1 WHEN a client connects to wss://server:8443/ws THEN the server SHALL accept the WebSocket upgrade request  
1.2 WHEN multiple clients connect THEN the server SHALL maintain separate session contexts  
1.3 WHEN a client disconnects THEN the server SHALL clean up the session without affecting other clients  
1.4 IF the connection is idle for 5 minutes THEN the server SHALL send a ping to check connection health  
1.5 IF a client doesn't respond to ping within 30 seconds THEN the server SHALL terminate the connection  
1.6 WHEN a connection is established THEN the initial read deadline SHALL be set to PingInterval + PongTimeout to prevent premature timeouts  
1.7 WHEN a session is cleaned up THEN the server SHALL remove the session from all project subscriber lists  
1.8 WHEN connections are created or destroyed THEN the server SHALL update metrics accordingly

### Story 2: Project Management
**As a** developer  
**I want** to create and manage projects  
**So that** I can organize my Claude conversations by codebase

#### Acceptance Criteria
2.1 WHEN I send `{"type": "project_create", "data": {"path": "/absolute/path"}}` THEN the server SHALL create a new project  
2.2 IF the path contains ".." or is not absolute THEN the server SHALL reject with security error  
2.3 IF the path would nest with existing project THEN the server SHALL reject with nesting error  
2.4 WHEN I send `{"type": "project_list"}` THEN the server SHALL return all projects with metadata  
2.5 WHEN I send `{"type": "project_delete", "project_id": "id"}` THEN the server SHALL delete the project and all data  
2.6 IF a project is currently executing THEN the server SHALL prevent deletion

### Story 3: Claude Execution
**As a** developer  
**I want** to execute Claude commands within a project context  
**So that** Claude maintains conversation history

#### Acceptance Criteria
3.1 WHEN I send `{"type": "execute", "project_id": "id", "data": {"prompt": "text"}}` THEN the server SHALL execute Claude  
3.2 IF a session ID exists for the project THEN the server SHALL use `-c session_id` flag  
3.3 WHEN Claude execution starts THEN the server SHALL broadcast state change to all project subscribers  
3.4 WHEN Claude outputs messages THEN the server SHALL parse JSON and broadcast to subscribers  
3.5 IF execution exceeds 5 minutes THEN the server SHALL timeout and kill the process  
3.6 WHEN execution completes THEN the server SHALL save the session ID for future use

### Story 4: Session Management
**As a** developer  
**I want** to start fresh Claude sessions  
**So that** I can reset context when needed

#### Acceptance Criteria
4.1 WHEN I send `{"type": "agent_new_session", "project_id": "id"}` THEN the server SHALL clear the session ID  
4.2 WHEN the session is reset THEN the server SHALL log the event with timestamp  
4.3 WHEN the next execution occurs THEN the server SHALL NOT use `-c` flag  
4.4 WHEN session is reset THEN all subscribers SHALL receive update notification

### Story 5: Process Control
**As a** developer  
**I want** to cancel running Claude executions  
**So that** I can stop runaway or unwanted processes

#### Acceptance Criteria
5.1 WHEN I send `{"type": "agent_kill", "project_id": "id"}` THEN the server SHALL terminate the Claude process  
5.2 IF no execution is active THEN the server SHALL return appropriate error  
5.3 WHEN process is killed THEN the server SHALL update project state to IDLE  
5.4 WHEN process is killed THEN all subscribers SHALL receive notification

### Story 6: Multi-Client Subscription
**As a** team member  
**I want** multiple clients to monitor the same project  
**So that** we can collaborate on Claude sessions

#### Acceptance Criteria
6.1 WHEN I send `{"type": "project_join", "data": {"project_id": "id"}}` THEN the server SHALL add me as subscriber  
6.2 WHEN subscribed THEN I SHALL receive all project updates and messages  
6.3 WHEN I send `{"type": "project_leave", "project_id": "id"}` THEN the server SHALL remove subscription  
6.4 WHEN any client executes in the project THEN all subscribers SHALL receive the messages  
6.5 IF a project is deleted THEN all subscribers SHALL receive deletion notification

### Story 7: Message History
**As a** developer  
**I want** to retrieve past messages  
**So that** I can review conversation history after reconnecting

#### Acceptance Criteria
7.1 WHEN I send `{"type": "get_messages", "project_id": "id", "data": {"since": "timestamp"}}` THEN the server SHALL return messages after that time  
7.2 WHEN messages are stored THEN they SHALL include timestamp and direction (client/claude)  
7.3 IF log files are rotated THEN the server SHALL search across multiple files  
7.4 WHEN retrieving history THEN the server SHALL return messages in chronological order

### Story 8: Server Persistence
**As a** system administrator  
**I want** the server to persist state across restarts  
**So that** projects and sessions survive server maintenance

#### Acceptance Criteria
8.1 WHEN the server starts THEN it SHALL load all projects from `data/projects/*/metadata.json`  
8.2 WHEN project state changes THEN the server SHALL save metadata atomically  
8.3 WHEN server restarts THEN all project session IDs SHALL be preserved  
8.4 IF metadata is corrupted THEN the server SHALL log error and skip that project  
8.5 WHEN clients reconnect after restart THEN they SHALL see all previous projects

### Story 9: Error Handling
**As a** client developer  
**I want** clear and consistent error messages  
**So that** I can handle errors appropriately

#### Acceptance Criteria
9.1 WHEN an error occurs THEN the server SHALL return `{"type": "error", "project_id": "id", "data": {"error": "message"}}`  
9.2 WHEN Claude CLI is not found THEN the error SHALL indicate installation required  
9.3 WHEN a project is not found THEN the error SHALL include the project ID  
9.4 IF an internal error occurs THEN the server SHALL NOT leak system paths or sensitive info  
9.5 WHEN validation fails THEN the error SHALL describe what was invalid

### Story 10: Resource Management
**As a** system administrator  
**I want** the server to manage resources responsibly  
**So that** it doesn't exhaust system resources

#### Acceptance Criteria
10.1 WHEN log files reach 100MB or 10,000 messages THEN the server SHALL rotate to a new file  
10.2 WHEN total projects exceed 100 THEN the server SHALL reject new project creation  
10.3 WHEN a connection is closed THEN the server SHALL clean up all associated resources  
10.4 IF disk space is low THEN the server SHALL handle gracefully without data corruption  
10.5 WHEN broadcasting messages THEN the server SHALL handle slow clients without blocking others  
10.6 WHEN a project is created THEN a single MessageLog SHALL be created and reused for all executions  
10.7 WHEN log rotation occurs THEN empty files SHALL be skipped to prevent proliferation

## Non-Functional Requirements

### Performance Requirements
- WebSocket message routing: < 10ms latency
- Project list retrieval: < 100ms for 100 projects  
- Message history query: < 500ms for 1000 messages
- Support 100+ concurrent connections
- Handle 10+ executions per minute

### Security Requirements
- Validate all paths to prevent injection attacks
- Sanitize all user input before logging
- Run Claude processes with restricted permissions
- No authentication for MVP (future work)

### Reliability Requirements
- Graceful handling of Claude process crashes
- Automatic cleanup of zombie processes
- Recovery from corrupted metadata files
- Atomic file operations to prevent partial writes

### Compatibility Requirements
- Support Linux and macOS
- Ensure POSIX compliance
- Handle Unix signal processing
- Compatible with Claude CLI updates

### Story 11: Interactive Testing
**As a** developer  
**I want** an interactive CLI test client  
**So that** I can test and debug the WebSocket API

#### Acceptance Criteria
11.1 WHEN I run the interactive client THEN it SHALL connect to the WebSocket server  
11.2 WHEN the project is IDLE THEN I SHALL be able to type prompts for Claude  
11.3 WHEN I press Tab THEN the client SHALL cycle through permission modes  
11.4 WHEN I type "json on" THEN the client SHALL display raw JSON messages  
11.5 WHEN the connection state changes THEN the prompt SHALL show visual indicators (🟢/🟡/🔴)  
11.6 WHEN messages are received THEN they SHALL be displayed in real-time

---
*Requirements: 11 stories, 56 acceptance criteria*
*Feature: WebSocket API*
*Module: Server*