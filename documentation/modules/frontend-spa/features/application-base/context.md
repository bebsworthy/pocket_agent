# Frontend SPA Application Base

## Feature Context

**Feature Name**: application-base  
**Module**: frontend-spa (NEW MODULE)  
**Type**: Single-module feature

**Description**: 
Establish the core foundation for the frontend-spa module - a React Single Page Application (SPA) with absolutely no server-side rendering. This feature creates the base application structure, routing, state management, and core UI components that all other frontend features will build upon.

## Technical Stack
- **Framework**: React (SPA only, no SSR)
- **Build Tool**: Vite
- **Styling**: TailwindCSS
- **UI Components**: shadcn/ui
- **State Management**: TBD during design phase
- **Router**: TBD during design phase

## Core Functionality

### Dashboard (Main View)
- Display list of projects
- Project model includes:
  - name (string)
  - path (string)
  - id (string)
  - serverId (reference to Server)
- Server model includes:
  - name (string)
  - websocketUrl (string)
  - isConnected (boolean)
- User actions:
  - Add new project (with server selection/creation)
  - View saved projects list
  - View connected servers (active WebSocket connections)
  - Disconnect from servers

### Project Page
- Accessed by clicking on a project from dashboard
- URL pattern: `/project/:projectId`
- Single WebSocket connection per server (shared across multiple projects on same server)
- Tab-based interface with placeholders for:
  - **Chat**: Communication with Claude on server (future feature)
  - **Files**: Browse server files (future feature)
  - **Status**: Monitor project and server status (future feature)
  - **Settings**: Edit project settings (future feature)

### Core Infrastructure
- Application shell with routing
- WebSocket connection management
- Local storage for project/server persistence
- Basic error handling and loading states
- Responsive layout structure

## Design Reference
Mockups available at: `/Users/boyd/wip/pocket_agent/documentation/mockups/claude-01`

## Scope Boundaries
This feature includes:
- ✅ Basic application structure and build setup
- ✅ Dashboard with project/server management UI
- ✅ Project page with tab navigation structure
- ✅ WebSocket connection management
- ✅ Local data persistence

This feature does NOT include:
- ❌ Actual chat functionality (separate feature)
- ❌ File browsing implementation (separate feature)
- ❌ Status monitoring details (separate feature)
- ❌ Advanced settings (separate feature)
- ❌ Authentication (if needed, separate feature)

## Next Steps
Run `/spec:2_research application-base --module frontend-spa` to analyze the codebase and existing patterns that might influence the frontend implementation.