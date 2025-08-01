# Research: application-base

## 1. Feature Context

### Description
Establish the core foundation for the frontend-spa module - a React Single Page Application (SPA) with absolutely no server-side rendering. This feature creates the base application structure, routing, state management, and core UI components that all other frontend features will build upon. The primary focus is mobile web browsers, not desktop usage.

### Scope
- **Layer**: Frontend (Mobile Web Client)
- **Components**: Application shell, routing, state management, WebSocket service, mobile-first UI foundation

### Architecture Reference
See [architecture.md](../../../../architecture.md) for overall system architecture.

## 2. Similar Existing Features

### Feature: Test Client Implementation
- **Location**: `test-client/src/`
- **What it does**: TypeScript WebSocket client for testing server
- **What we can reuse**:
  - Complete TypeScript message type definitions
  - WebSocket connection management patterns
  - Event-based message handling architecture
- **What to improve**:
  - Add React-specific hooks and context
  - Browser-based WebSocket instead of Node.js ws library
  - State management integration

### Feature: Server WebSocket Protocol
- **Location**: `server/internal/models/messages.go`
- **Patterns to follow**: 
  - Message type constants for client/server communication
  - Structured message format with type, project_id, and data fields
- **Lessons learned**: 
  - Server expects specific message types (project_create, project_join, etc.)
  - Project-based routing is core to the architecture

### Feature: Mobile Mockup UI Design
- **Location**: `documentation/mockups/claude-01/index.html`
- **What it shows**: Mobile-first UI design patterns
- **Design elements to implement**:
  - Project cards with status indicators
  - Tab-based navigation (Chat, Files, Status, Settings)
  - Dark/light theme support
  - Connection status visual feedback
  - Mobile-optimized touch targets
  - Bottom navigation for thumb-friendly access
- **Mobile web considerations**:
  - Viewport meta tag configuration
  - Touch gesture support
  - Mobile keyboard handling

## 3. Affected Components

### Direct Impact
| Component | Location | Purpose | Changes Needed |
|:----------|:---------|:--------|:---------------|
| Frontend Module | `frontend-spa/` | New web client | Create entire module structure |
| Component Library | `frontend-spa/src/components/ui/` | Reusable mobile UI components | Create foundational components |
| Project Model | `frontend-spa/src/types/` | Define project entity | Create TypeScript interfaces |
| Server Model | `frontend-spa/src/types/` | Define server entity | Create TypeScript interfaces |
| WebSocket Service | `frontend-spa/src/services/` | Server communication | Implement WebSocket client |
| Local Storage | `frontend-spa/src/services/` | Data persistence | Implement storage service |

### Integration Points
| System | Type | Current State | Integration Needs |
|:-------|:-----|:--------------|:------------------|
| WebSocket Server | WebSocket | Go server at port 8443 | Connect via WSS, handle JSON messages |
| Message Protocol | JSON/WebSocket | Defined in server models | Implement TypeScript types matching server |
| Project Management | WebSocket API | Server manages projects | Send project_create, project_join messages |

## 4. Technical Considerations

### New Dependencies
- [x] New dependencies required:
  - **Package**: `react-router-dom` - Client-side routing
  - **Package**: `jotai` - Atomic state management (better for real-time updates)
  - **Package**: `lucide-react` - Icon library for consistent iconography
  - **Package**: Native WebSocket API - No library needed, use browser WebSocket
  - **Version**: Latest stable versions

### Performance Impact
- **Expected Load**: Low - Single WebSocket connection per server
- **Performance Concerns**: Mobile browser limitations, battery usage, network transitions
- **Optimization Needs**: 
  - Minimize bundle size for mobile networks
  - Implement connection retry for flaky mobile connections
  - Optimize for touch interactions

### Security Considerations
- **Authentication**: None in MVP (as per architecture)
- **Authorization**: None in MVP
- **Data Sensitivity**: Project paths and server URLs stored locally
- **Vulnerabilities**: WSS required for secure connections

## 5. Implementation Constraints

### Must Follow (from architecture.md)
- Pure client-side SPA (no SSR)
- WebSocket-only communication (no REST)
- Project-based execution model
- TypeScript strict mode
- Functional components only
- Mobile-first design (not desktop-first)

### Cannot Change
- Server WebSocket protocol (must match existing)
- Message type constants from server
- Project/server relationship model

### Technical Debt in Area
- No existing frontend code to build on
- Need to establish all patterns from scratch
- Server currently has no authentication

## 6. Recommendations

### Architecture Approach
Based on the codebase analysis:
- **Pattern**: Component-based with hooks for business logic
- **Structure**: Feature folders with colocated components/hooks/types
- **Component Library**: Build reusable, mobile-first UI components
  - Atomic design principles (atoms → molecules → organisms)
  - Consistent prop interfaces
  - Built-in accessibility
  - Touch-optimized interactions
- **Integration**: Single WebSocket service with React Context for distribution

### Implementation Strategy
1. **Start with**: Vite project setup with TypeScript
2. **Build on**: Test client's TypeScript message types (already converted from Go)
3. **Reuse**: 
   - Message type definitions from `test-client/src/types/messages.ts`
   - WebSocket patterns from `test-client/src/client.ts` (adapt for browser)
   - UI patterns from mockup (directly implement mobile design)
4. **Avoid**: 
   - Desktop-specific patterns
   - Over-engineering state management initially
   - Complex responsive layouts (mobile-only for now)

### Testing Approach
- **Unit Tests**: Component testing with React Testing Library
- **Integration Tests**: Mock WebSocket for message flow testing
- **Test Data**: Use claude-mock for development

## 7. Risks and Mitigation

### Technical Risks
| Risk | Probability | Impact | Mitigation |
|:-----|:------------|:-------|:-----------|
| WebSocket connection instability | Medium | High | Implement reconnection logic |
| State synchronization issues | Medium | Medium | Use single source of truth |
| Large message handling | Low | Medium | Implement message chunking if needed |

### Integration Risks
- **Breaking Changes**: Server protocol could change
- **Data Migration**: N/A - new module
- **Rollback Plan**: N/A - new module

## 8. Next Steps

### Requirements Considerations
Based on this research, the requirements should:
- Include WebSocket reconnection handling
- Consider offline-first approach for project list
- Account for multiple projects on same server
- Define core component library structure
- Specify mobile interaction patterns

### Design Considerations
The design phase should focus on:
- Component library architecture and standards
- State management architecture with Jotai
- WebSocket service abstraction
- Mobile navigation patterns
- Touch gesture handling

---

## Research Summary

**Key Findings**:
1. Test client already provides complete TypeScript types and WebSocket patterns
2. Mobile mockup shows clear UI/UX patterns that can be adapted for web
3. Server protocol is well-defined and simpler than Android's wrapper approach

**Recommended Approach**: Build a mobile-first React SPA with TypeScript, implementing the exact UI patterns from the mockup, connecting directly to the server's WebSocket API. Focus exclusively on mobile web experience.

**Proceed to Requirements?** Yes