# WebSocket API Feature Context

## Feature Overview
**Feature Name**: WebSocket API
**Module**: Server
**Description**: Real-time WebSocket communication layer for Pocket Agent server that manages Claude CLI executions on a per-project basis with persistent state management.

## Business Context
The Pocket Agent system requires a robust server component that can:
- Accept WebSocket connections from multiple clients (Android and React)
- Manage Claude CLI executions with project-based isolation
- Maintain conversation state across client disconnections
- Support multi-client access to the same project
- Persist state across server restarts

## User Needs
- **Developers** need to execute Claude commands from their mobile devices or web browsers
- **Users** need their Claude conversations to persist across disconnections
- **Teams** need multiple clients to monitor the same Claude session
- **System Administrators** need the server to recover gracefully from restarts

## Success Criteria
- WebSocket server accepts and manages multiple client connections
- Projects maintain state across client and server restarts
- Claude executions are properly isolated by project
- Multiple clients can subscribe to project updates
- System handles errors gracefully without data loss

## Constraints
- Must work with Claude CLI's execution model (not a daemon)
- No REST API - pure WebSocket communication
- Must support Linux, macOS, and Windows
- MVP implementation without authentication

## Related Features
- Communication Layer (cross-module) - defines message protocol
- Frontend integration features (future)

---
*Feature: WebSocket API*
*Module: Server*
*Created: 2025-01-27*