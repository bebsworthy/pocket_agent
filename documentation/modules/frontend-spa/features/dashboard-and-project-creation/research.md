# Dashboard and Project Creation - Research

## Research Summary

The dashboard feature can be built using **existing patterns and components** from the application-base foundation, with **additional components needed** for mockup-specific patterns. The research has been updated after reviewing the official mockup at `/documentation/mockups/claude-01/index.html`.

## Similar Features Analysis

### 1. ProjectCard Component Pattern (Ready to Use)
**Location**: `src/components/ui/organisms/ProjectCard.tsx`

**Key Features**:
- Displays project with server connection status using `StatusIndicator`
- Mobile-optimized touch targets and interactions
- Handles press events for navigation to project detail
- Shows last active time with smart formatting (hours/days ago)
- Connection status banners for disconnected state
- Action buttons with event propagation control

**Integration Pattern**:
```typescript
<ProjectCard
  project={project}
  server={server}
  onPress={() => navigate(`/project/${project.id}`)}
  onDisconnect={() => handleDisconnect(server.id)}
  onSettings={() => handleSettings(project.id)}
/>
```

### 2. ServerForm Component Pattern (Ready to Use)
**Location**: `src/components/ui/organisms/ServerForm.tsx`

**Key Features**:
- Modal-based form with mobile-optimized layout
- WebSocket URL validation (supports ws://, wss://, IPv6)
- Live connection testing functionality
- Comprehensive error handling and validation
- Escape key handling for modal dismissal

**Integration Pattern**:
```typescript
<ServerForm
  onSubmit={handleAddServer}
  onCancel={() => setShowForm(false)}
  initialValues={editingServer}
  isEditing={!!editingServer}
/>
```

### 3. EmptyState Component Pattern (Ready to Use)
**Location**: `src/components/ui/organisms/EmptyState.tsx`

**Key Features**:
- Predefined presets for common scenarios
- `EmptyStatePresets.noProjects()` and `EmptyStatePresets.noServers()`
- Mobile-optimized sizing and spacing
- Automatic focus management for accessibility

**Integration Pattern**:
```typescript
// No projects state
<EmptyState {...EmptyStatePresets.noProjects(() => setShowAddProject(true))} />

// No servers state  
<EmptyState {...EmptyStatePresets.noServers(() => setShowAddServer(true))} />
```

## State Management Patterns (Ready to Use)

### Project Management
**Hook**: `useProjects()` from `src/store/hooks/useProjects.ts`

**Available Operations**:
```typescript
const {
  projects,                    // All projects array
  hasProjects,                // Boolean for empty state logic
  projectCount,               // Count for display
  isLoading,                  // Loading state
  addProject,                 // Create new project
  removeProject,              // Delete project  
  selectProject,              // Navigate to project
  getProjectsByServer,        // Filter by server
} = useProjects();
```

### Server Management  
**Hook**: `useServers()` from `src/store/hooks/useServers.ts`

**Available Operations**:
```typescript
const {
  servers,                    // All servers array
  serversWithStatus,          // Servers with connection status
  hasServers,                 // Boolean for empty state logic
  connectedCount,             // Connected servers count
  addServer,                  // Create new server
  removeServer,               // Delete server
  updateConnectionStatus,     // Update connection state
  getConnectedServers,        // Filter connected only
  isServerConnected,          // Check individual server status
} = useServers();
```

### WebSocket Integration
**Hook**: `useWebSocket(serverId, url)` from `src/store/hooks/useWebSocket.ts`

**Real-time Features**:
- Automatic reconnection with exponential backoff
- Connection status updates (connected/disconnected/connecting/error)
- Project state synchronization with server
- Message queuing for offline scenarios

## Component Architecture Integration

### **Mockup-Based Dashboard Layout Structure**
**Source**: `/documentation/mockups/claude-01/index.html`

```
Projects List Screen (Main Dashboard):
├── App Bar
│   ├── Menu/Back Icon (ti-menu-2 / ti-arrow-left) 
│   ├── Title ("Pocket Agent" / Project Name)
│   └── Theme Toggle (ti-sun / ti-moon)
├── Projects Section Header ("Projects")
├── Project Cards List
│   ├── Enhanced ProjectCard with git stats
│   ├── Connection status icons (colored)
│   └── Notification badges
├── Empty State (when no projects)
│   ├── Folder-off icon
│   ├── "No projects yet" message  
│   └── "Create Project" button
└── FAB (+ button) for new project creation
```

### **Project Detail Screen** (Individual Project)
```
Project Screen:
├── App Bar (with back arrow + project name)
├── Segmented Control Tabs
│   ├── Chat (ti-message-circle)
│   ├── Files (ti-folder) 
│   ├── Monitor (ti-activity)
│   └── Settings (ti-settings)
└── Tab Content (full height with chat input)

### Mobile-First Responsive Design
**Established Patterns** (from `tailwind.config.js`):
- Container: `max-w-md mx-auto` (mobile-first container)
- Touch targets: `.touch-target` utility class (44px minimum)
- Spacing: Mobile-optimized spacing scale
- Typography: Mobile-optimized font sizes

## WebSocket Message Protocol Integration

### Project Operations
**Available Message Types** (from `src/types/messages.ts`):
- `project_create` - Create new project
- `project_list` - Get all projects
- `project_join` - Subscribe to project updates
- `project_delete` - Delete project

### Server Status Updates  
**Real-time Message Types**:
- `project_state` - Project status changes (IDLE/EXECUTING/ERROR)
- `error` - Connection and operation errors
- `health_status` - Server health and metrics

### Connection Management
**Established Pattern**:
```typescript
// WebSocket context provides server management
const context = useWebSocketContext();
const service = context.getService(serverId);
```

## Technical Constraints and Requirements

### 1. Mobile-First Design Constraints
- **Target Range**: 320px-428px (primary), responsive up to 1024px
- **Touch Targets**: 44px minimum (enforced by Tailwind utilities)
- **Safe Areas**: iOS safe area inset support configured
- **Performance**: 60fps scrolling, <16ms touch response

### 2. State Management Requirements
- **Persistence**: All projects/servers automatically persisted to localStorage
- **Reactivity**: Fine-grained updates using Jotai atomic state
- **Error Handling**: Comprehensive error boundaries for storage operations
- **Optimistic Updates**: UI updates immediately, syncs with server

### 3. WebSocket Integration Requirements
- **Connection Management**: Must handle server disconnections gracefully
- **Message Queuing**: Queue operations during disconnection
- **Status Display**: Real-time connection status in UI
- **Reconnection**: Automatic reconnection with exponential backoff

### 4. Component Library Constraints
- **Atomic Design**: Must use atoms → molecules → organisms hierarchy
- **Accessibility**: WCAG 2.1 AA compliance (ARIA labels, focus management)
- **Theme Support**: Light/dark/system theme compatibility
- **Icon Library**: Lucide React icons only (consistent with existing)

## Security and Validation Patterns

### Input Validation (Established)
- WebSocket URL validation supports ws://, wss://, and IPv6 formats
- Server name validation (minimum 2 characters, required)
- Input sanitization for XSS prevention

### Error Handling (Established)
- Service-specific error boundaries (WebSocket, Storage)
- Connection timeout handling (10 second limit)
- Graceful degradation for offline scenarios

## Performance Considerations

### Bundle Size Impact
- **Current Bundle**: ~260KB (target <500KB)
- **New Components**: Dashboard page will be lazy-loaded
- **State Overhead**: Minimal (Jotai atomic state)
- **WebSocket Overhead**: <2MB per connection (per architecture docs)

### Runtime Performance
- **Component Rerendering**: Minimal due to Jotai's fine-grained reactivity
- **List Rendering**: Use React.memo for ProjectCard components
- **State Updates**: Batch updates where possible

## **Mockup Analysis: Additional Components Needed**

### **New Components Required** (Not in Application Base)

1. **Enhanced ProjectCard** ⚠️
   - Connection status colored icons (green/yellow/gray)
   - **Modification**: Extend existing `ProjectCard.tsx`
   - **Note**: Git stats and notification badges excluded from this feature

2. **FAB (Floating Action Button)** ⚠️
   - Fixed position with primary color
   - Icon: plus (ti-plus)
   - **Location**: Should be `src/components/ui/atoms/FAB.tsx`

3. **Project Creation Modal** ⚠️
   - Full-screen modal form
   - Project name, path, server selection
   - Server dropdown with "Add New Server" option
   - **Could extend**: ServerForm pattern for consistency

### **Mockup-Specific State Requirements**

1. **Enhanced Project Data Model**
   - **Note**: Git stats and notification badges excluded from this feature
   - Will use existing Project data model from application-base
   - Connection status display enhancement only

## Integration Risks and Mitigation

### Low Risk Areas ✅
- **Core State Management**: useProjects/useServers hooks ready
- **WebSocket Protocol**: Types and message handling complete
- **Base Component Library**: Atoms and molecules exist

### **Medium Risk Areas** ⚠️
- **Enhanced ProjectCard**: Extend existing component for connection status colors
- **Project Creation Flow**: Modal integration with server selection
- **Server Selection UX**: "Add New Server" inline with project creation

### **Updated Mitigation Strategies**
1. **Phase 1**: Build core dashboard layout with existing components
2. **Phase 2**: Add FilterChips and enhanced ProjectCard connection status
3. **Phase 3**: Add project creation modal with server management
4. **Test Early**: Mockup behavior with interaction testing

## **Updated Implementation Approach** (Post-Mockup)

### **Phase 1: Core Projects List Screen**
1. Replace placeholder Dashboard.tsx with mockup-based layout
2. Integrate useProjects hooks with basic project cards
3. Add EmptyState with mockup styling (folder-off icon)
4. Add FAB component for project creation

### **Phase 2: Enhanced Display**
1. Enhance ProjectCard with connection status colored icons

### **Phase 3: Project Creation Flow**
1. Build project creation modal following mockup design
2. Integrate server selection with "Add New Server" option
3. Connect to existing ServerForm patterns
4. Add form validation and error handling

### **Phase 4: Navigation and Polish**
1. Add project detail navigation (segmented tabs)
2. Implement swipe gestures and mobile interactions
3. Add theme toggle integration
4. Polish animations and transitions

## **Updated Next Steps**

This research has been **significantly updated** after reviewing the official mockup. The implementation requires **more new components** than initially assessed.

**Critical Finding**: The mockup shows a different navigation pattern and additional UI components not covered in the initial research.

**Proceed to**: `/spec:3_requirements dashboard-and-project-creation`

The requirements phase should **prioritize**:
- **Mockup-accurate layout and component requirements**
- **Connection status visual enhancements (colored icons)**
- **Project creation modal with server selection UX**
- **Navigation patterns between projects list and detail screens**

**Excluded from this feature** (future features):
- Git branch and changes display
- Notification badges and counts