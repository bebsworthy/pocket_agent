# WebSocket API Implementation Tasks (Updated)

## Implementation Tasks

### Phase 1: Core Infrastructure
> Recommended agent: go-developer

- [ ] 1. **Set up Go module and project structure**
  - Create server/ directory with Go module
  - Set up internal/ package structure
  - Add .gitignore for Go projects
  - Create main.go entry point
  - _Requirements: 1.1, 1.2_

- [ ] 2. **Implement configuration management**
  - Create Config struct with all settings
  - Implement LoadConfig with env variable support
  - Add validation for required settings
  - Create config/config.go
  - _Requirements: 8.1, 10.1_

- [ ] 3. **Set up structured logging**
  - Choose and integrate logging library (zap/logrus)
  - Create logger initialization
  - Define log levels and formats
  - Add correlation ID support
  - _Requirements: 9.1, 9.4_

### Phase 2: Data Models and Storage
> Recommended agent: go-developer

- [ ] 4. **Implement core data models**
  - Create models/project.go with Project struct
  - Create models/session.go with Session struct
  - Create models/messages.go with message types
  - Add validation methods
  - _Requirements: 2.1, 3.1, 6.1_

- [ ] 5. **Implement message log with rotation**
  - Create MessageLog struct and methods
  - Implement file rotation logic
  - Add atomic write operations
  - Create query methods for history
  - _Requirements: 7.1, 7.2, 10.1_

- [ ] 6. **Implement project persistence**
  - Create ProjectMetadata struct
  - Implement saveProjectMetadata with atomic writes
  - Implement loadProjects for startup
  - Add corruption recovery
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

### Phase 2.5: Cross-cutting Concerns (NEW - moved earlier)
> Recommended agent: go-developer

- [ ] 7. **Implement comprehensive error handling**
  - Create error types and codes (INVALID_PATH, PROJECT_NOT_FOUND, etc.)
  - Add error context and details structure
  - Implement error response formatting
  - Add panic recovery middleware
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [ ] 8. **Add input validation framework**
  - Path validation and sanitization functions
  - Message size limit enforcement (1MB default)
  - Parameter validation helpers
  - JSON schema validation
  - _Requirements: 2.2, 9.5, 10.1_

### Phase 3: Project Management
> Recommended agent: go-developer

- [ ] 9. **Implement ProjectManager core**
  - Create ProjectManager struct
  - Implement NewProjectManager with initialization
  - Add project collection management
  - Implement path validation using Phase 2.5 validators
  - _Requirements: 2.1, 2.2, 2.3_

- [ ] 10. **Implement project CRUD operations**
  - Implement CreateProject with validation
  - Implement DeleteProject with cleanup
  - Implement GetProjectByID and GetProject
  - Add nesting validation
  - _Requirements: 2.1, 2.3, 2.5, 2.6_

- [ ] 11. **Add project state management**
  - Implement state transitions
  - Add subscriber management
  - Implement UpdateProjectSession
  - Add concurrent access control
  - _Requirements: 3.3, 6.1, 6.3_

### Phase 4: Claude Execution
> Recommended agent: go-developer

- [ ] 12. **Implement ClaudeExecutor base**
  - Create ClaudeExecutor struct
  - Add process tracking map
  - Implement process lifecycle management
  - Add timeout context creation
  - _Requirements: 3.1, 3.5, 5.1_

- [ ] 13. **Implement Execute method**
  - Build Claude command arguments
  - Execute with timeout and tracking
  - Capture stdout AND stderr separately
  - Parse Claude JSON output
  - Update project session ID
  - _Requirements: 3.1, 3.2, 3.4, 3.6_

- [ ] 14. **Implement KillExecution method**
  - Find active process by project ID
  - Send kill signal to process
  - Clean up process tracking
  - Update project state
  - _Requirements: 5.1, 5.2, 5.3_

### Phase 5: WebSocket Communication
> Recommended agent: go-websocket-specialist

- [ ] 15. **Set up WebSocket server**
  - Integrate Gorilla WebSocket
  - Implement HandleUpgrade method
  - Set up TLS configuration
  - Configure write/read timeouts
  - _Requirements: 1.1, 1.2_

- [ ] 16. **Implement WebSocket security**
  - Add origin header validation
  - Implement connection rate limiting
  - Add max connections per IP
  - Validate upgrade requests
  - _Requirements: Security, 1.1_

- [ ] 17. **Implement session management**
  - Create WebSocket session handler
  - Implement ping/pong heartbeat (30s interval)
  - Add connection timeout (5 min idle)
  - Clean up on disconnect
  - _Requirements: 1.3, 1.4, 1.5_

- [ ] 18. **Implement message routing**
  - Create RouteMessage dispatcher
  - Parse incoming ClientMessage
  - Route to appropriate handlers
  - Add error handling with Phase 2.5 errors
  - _Requirements: 3.1, 4.1, 5.1, 6.1_

### Phase 6: Message Handlers
> Recommended agent: go-websocket-specialist

- [ ] 19. **Implement project handlers**
  - handleProjectCreate with validation
  - handleProjectList with metadata
  - handleProjectDelete with checks
  - handleProjectJoin/Leave
  - _Requirements: 2.1, 2.4, 2.5, 6.1, 6.3_

- [ ] 20. **Implement execution handlers**
  - handleExecute with state updates
  - handleAgentNewSession
  - handleAgentKill
  - Add proper error responses
  - _Requirements: 3.1, 4.1, 5.1_

- [ ] 21. **Implement query handlers**
  - handleGetMessages with timestamp
  - Add message filtering
  - Implement pagination support
  - Return sorted results
  - _Requirements: 7.1, 7.3, 7.4_

### Phase 7: Broadcasting and Updates
> Recommended agent: go-websocket-specialist

- [ ] 22. **Implement broadcast system**
  - Create broadcastToProject method
  - Add subscriber notification
  - Implement write timeout for slow clients
  - Add buffered channels for backpressure
  - _Requirements: 3.3, 6.2, 6.4, 10.5_

- [ ] 23. **Implement status updates**
  - broadcastProjectState changes
  - Periodic stats broadcasting (10s interval)
  - Connection health updates
  - Error notifications
  - _Requirements: 3.3, 4.4, 5.4, 6.5_

- [ ] 24. **Implement health check system**
  - WebSocket-based health endpoint
  - System resource checks (CPU, memory, disk)
  - Claude CLI availability check
  - Return structured health status
  - _Requirements: Monitoring, Operations_

### Phase 8: Server Infrastructure
> Recommended agent: go-developer

- [ ] 25. **Implement main server structure**
  - Create Server struct
  - Implement NewServer initialization
  - Add component wiring
  - Set up graceful shutdown
  - _Requirements: 1.1, 8.1_

- [ ] 26. **Add resource management**
  - Implement connection limits (100 default)
  - Add project count limits (100 default)
  - Monitor resource usage (memory, goroutines)
  - Add cleanup routines
  - _Requirements: 10.2, 10.3, 10.4_

- [ ] 27. **Implement metrics collection**
  - Execution duration tracking
  - Message throughput counters
  - Active connection gauges
  - Resource usage metrics
  - _Requirements: Performance, Monitoring_

### Phase 9: Platform Support
> Recommended agent: go-developer

- [ ] 28. **Add Unix platform compatibility**
  - Linux: signal handling, process groups
  - macOS: handle security permissions
  - Ensure POSIX compliance
  - Test on Linux and macOS
  - _Requirements: Platform compatibility_

### Phase 10: Testing
> Recommended agent: go-test-engineer

- [ ] 29. **Create Claude CLI mock for testing**
  - Parse example-claude-interactive-output for test data
  - Create mock executable that simulates Claude responses
  - Support different response scenarios (success, error, timeout)
  - Ensure deterministic output for tests
  - **MUST NOT use real Claude API**
  - _Requirements: Testing without real Claude_

- [ ] 30. **Create unit tests**
  - Test path validation edge cases
  - Test message parsing
  - Test file operations
  - Test concurrent access
  - _Requirements: All validation criteria_

- [ ] 31. **Create integration tests with Claude mock**
  - Use Claude mock from task 29
  - Test WebSocket lifecycle
  - Test server restart recovery
  - Test multi-client synchronization
  - Test error scenarios
  - **FORBIDDEN: Do NOT use real Claude instance in tests**
  - _Requirements: 1.1-1.5, 6.1-6.5, 8.1-8.5_

- [ ] 33. **Create load and performance tests**
  - Test 100+ concurrent connections
  - Test message throughput (1000/sec)
  - Test resource limits
  - Benchmark critical paths
  - _Requirements: Performance Requirements_

### Phase 11: Documentation
> Recommended agent: go-developer

- [ ] 34. **Create operational documentation**
  - Write comprehensive README.md
  - Document all configuration options
  - Add troubleshooting guide
  - Create architecture diagrams
  - _Requirements: Documentation_

- [ ] 35. **Create deployment documentation**
  - Docker deployment guide
  - Systemd service configuration
  - TLS certificate setup
  - Monitoring setup guide
  - _Requirements: Deployment_

### Phase 12: Deployment Preparation
> Recommended agent: go-developer

- [ ] 36. **Create deployment artifacts**
  - Create multi-stage Dockerfile
  - Add docker-compose.yml
  - Create systemd service files
  - Add health check scripts
  - _Requirements: 1.1, Deployment_

- [ ] 37. **Prepare release automation**
  - Create build scripts
  - Add version management
  - Create release checklist
  - Set up CI/CD pipeline
  - _Requirements: Deployment_

## Agent Recommendations Summary

### Specialized Agents Available

1. **go-developer** - Primary implementation agent
   - Phases: 1, 2, 2.5, 3, 4, 8, 9, 11, 12
   - Tasks: Core infrastructure, data models, project management, Claude execution, server setup

2. **go-websocket-specialist** - WebSocket expert
   - Phases: 5, 6, 7
   - Tasks: WebSocket server, message handlers, broadcasting system

3. **go-test-engineer** - Testing specialist
   - Phase: 10
   - Tasks: Unit tests, integration tests, load tests, Claude CLI mock

4. **system-architect** - Architecture review (optional)
   - Use for design validation and architectural decisions
   - Read-only access for system analysis

### Execution Strategy

When using `/spec:6_execute <task-id>`:
- Tasks will automatically use the recommended agent based on phase
- For architecture review, explicitly specify: `/spec:6_execute --agent system-architect`
- Agents work best when given complete phase context

## Task Dependencies (Updated)

```
Phase 1 (1-3) ──┐
                ├──→ Phase 2 (4-6) ──→ Phase 2.5 (7-8) ──┐
                │                                         │
                │                    ┌────────────────────┘
                │                    ↓
                │              Phase 3 (9-11) ──┐
                │                               ├──→ Phase 6 (19-21)
                │              Phase 4 (12-14) ─┘           │
                │                    ↓                      ↓
                └──→ Phase 5 (15-18) ──────────────→ Phase 7 (22-24)
                                                           ↓
                                                    Phase 8 (25-27)
                                                           ↓
                                                    Phase 9 (28-29)
                                                           ↓
                                                    Phase 10 (30-32)
                                                           ↓
                                                    Phase 11 (33-34)
                                                           ↓
                                                    Phase 12 (35-36)
```

Key Dependencies:
- Error handling (7-8) must be done before any implementation
- Platform support (28-29) should be considered throughout
- Testing (30-32) can begin as soon as components are ready
- Documentation (33-34) should be updated throughout

## Effort Estimation

| Phase | Tasks | Estimated Days | Complexity |
|-------|-------|----------------|------------|
| Phase 1 | 1-3 | 2 days | Low |
| Phase 2 | 4-6 | 3 days | Medium |
| Phase 2.5 | 7-8 | 2 days | Medium |
| Phase 3 | 9-11 | 3 days | Medium |
| Phase 4 | 12-14 | 3 days | High |
| Phase 5 | 15-18 | 3 days | Medium |
| Phase 6 | 19-21 | 3 days | Medium |
| Phase 7 | 22-24 | 3 days | High |
| Phase 8 | 25-27 | 3 days | High |
| Phase 9 | 28 | 1 day | Medium |
| Phase 10 | 29-33 | 6 days | Medium |
| Phase 11 | 34-35 | 2 days | Low |
| Phase 12 | 36-37 | 2 days | Low |
| **Total** | **37 tasks** | **~36 days** | - |

---
*Total Tasks: 37*
*Feature: WebSocket API*
*Module: Server*
*Version: 2.1*
*Platform: Linux and macOS only*
*Testing: MUST use Claude mock, FORBIDDEN to use real Claude API*