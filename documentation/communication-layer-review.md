# Communication Layer Review Report

## Overview
This report reviews the coherence of the communication-layer feature documentation against the new WebSocket API feature specification.

## Major Inconsistencies Found

### 1. Claude References
- **Issue**: All files reference "Claude Code" instead of "Claude CLI"  
- **Reality**: We now understand it's Claude CLI (`claude -p <prompt>`) that executes and exits
- **Files Affected**: All communication-layer files

### 2. Authentication Model
- **Issue**: SSH key-based authentication is core to the design
- **Reality**: MVP has NO authentication (moved to future work per websocket-api spec)
- **Files Affected**: 
  - context.md (lines 13, 31, 44, 88-89)
  - integration.md (lines 23, 44, 106-113, 242-252)
  - server/design.md (lines 17, 60-64)
  - server/requirements.md (Story 2 entirely)

### 3. REST API References
- **Issue**: REST API endpoints mentioned throughout
- **Reality**: WebSocket-only architecture, NO REST API
- **Files Affected**:
  - server/design.md (lines 6, 25, 39, 92-97)
  - server architecture integration points

### 4. Project-Based Execution Model
- **Issue**: No mention of project-based organization
- **Reality**: Projects are CORE to the new design
- **Missing Concepts**:
  - Project creation/deletion/management
  - Project-specific message history
  - Sequential execution per project
  - Project path validation

### 5. Persistence and Storage
- **Issue**: No mention of server persistence
- **Reality**: Server persists projects and messages to disk
- **Missing Features**:
  - Project metadata persistence
  - Message log rotation (time and size based)
  - Server restart recovery
  - Atomic file operations

### 6. Missing Core Features
The communication-layer documentation completely lacks:
- `agent_new_session` command support
- File-based message storage with rotation
- Project subscription model (join/leave)
- Multi-client project-based broadcasting
- Process timeout mechanism (5 minutes)
- Platform restrictions (Unix/POSIX only)

### 7. Outdated Message Protocol
- **Issue**: Message types don't match WebSocket API spec
- **Missing Types**:
  - `project_create`, `project_delete`, `project_list`
  - `project_join`, `project_leave`
  - `execute`, `agent_new_session`, `agent_kill`
  - `session_reset` update type

## Specific File Analysis

### communication-layer/context.md
- Lines 11-18: Outdated problem statement
- Lines 88-91: SSH authentication mentioned
- No mention of project-based architecture

### communication-layer/integration.md
- Lines 43-65: Outdated message types
- Lines 106-113: SSH authentication flow
- Lines 120-125: REST API references
- No project management messages

### communication-layer/modules/server/design.md
- Line 6: "REST API" in technology stack
- Lines 17, 60-64: SSH authentication components
- Lines 92-97: REST API section
- Lines 108-155: Outdated data models (no Project)
- No persistence layer mentioned

### communication-layer/modules/server/requirements.md
- Story 2 (lines 17-28): Entire SSH authentication story
- Story 3 (lines 29-40): No project concept
- Story 6 (lines 65-76): Outdated message protocol
- Missing stories for:
  - Project management
  - Persistence
  - New session creation
  - Platform compatibility

## Recommendations

### 1. Complete Rewrite Needed
The communication-layer feature documentation needs a complete update to align with the WebSocket API specification. The current documentation represents an outdated architecture.

### 2. Priority Updates
1. Remove all REST API references
2. Remove SSH authentication (move to future work)
3. Add project-based execution model throughout
4. Add persistence and message rotation
5. Update message protocol to match WebSocket API
6. Add Claude CLI execution model details
7. Remove Windows support, focus on Unix/POSIX

### 3. Use WebSocket API Spec as Source of Truth
The `/documentation/modules/server/features/websocket-api/` specification should be the authoritative source. The communication-layer feature should be updated to match it exactly.

### 4. Consider Deprecation
Given the extensive changes needed, consider deprecating the communication-layer feature documentation and referring users to the WebSocket API feature specification instead.

---
*Review Complete*
*Feature: Communication Layer*
*Date: 2025-01-27*