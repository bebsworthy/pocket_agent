# Pocket Agent Mobile App - Mockup Design Prompt

## Project Overview

Design a mobile application mockup for "Pocket Agent" - an AI-powered coding assistant that runs on Android devices. The app allows developers to manage coding projects, interact with an AI agent (like Claude), browse files, and monitor project activities. Think of it as having a coding AI assistant in your pocket that can help you with development tasks on the go.

## Design System & Visual Guidelines

### Material Design 3
- Use Material Design 3 (Material You) components and patterns
- Support dynamic color theming on Android 12+
- Implement both light and dark theme variations
- Follow Material Design elevation and depth principles

### Color Palette

#### Light Theme
- **Primary**: #00C853 (Green)
- **On Primary**: #FFFFFF
- **Primary Container**: #C8E6C9
- **On Primary Container**: #002106
- **Secondary**: #5D6B5D
- **Background**: #FFFFFF
- **Surface**: #F5F5F5
- **Surface Variant**: #E0E0E0
- **Error**: #BA1A1A
- **On Background/Surface**: #1A1C1A
- **Outline**: #72796F

#### Dark Theme
- **Primary**: #00E676 (Bright Green)
- **On Primary**: #003910
- **Primary Container**: #005319
- **On Primary Container**: #C8E6C9
- **Secondary**: #B9CCB9
- **Background**: #000000
- **Surface**: #121212
- **Surface Variant**: #42493F
- **Error**: #FFB4AB
- **On Background/Surface**: #E1E3DF
- **Outline**: #8C9388

### Typography
- **Font Family**: Inter (with fallback to Roboto)
- **Display Large**: 57sp, -0.25 letter spacing
- **Headline Medium**: 28sp, 0 letter spacing
- **Title Large**: 22sp, 0 letter spacing
- **Body Large**: 16sp, 0.5 letter spacing
- **Label Large**: 14sp, 0.1 letter spacing

### Spacing & Layout
- Use 8dp grid system
- Standard margins: 16dp
- Card padding: 16dp
- List item padding: 16dp horizontal, 12dp vertical
- Minimum touch target: 48dp x 48dp

## Screen Sizes & Responsive Design

### Target Devices
1. **Phone (Primary)**: 360dp - 412dp width
2. **Tablet**: 600dp+ width (use 2-column layouts where appropriate)
3. **Foldable**: Support both folded and unfolded states

### Orientation
- Support both portrait and landscape
- Portrait is primary for phone screens
- Landscape should reflow content appropriately

## Navigation Architecture

```
App Entry
    │
    ├── Projects List (Home)
    │   ├── Create New Project (Dialog)
    │   └── Project Item → Project Dashboard
    │
    └── Project Dashboard (when project selected)
        ├── Chat (Default Tab)
        ├── Files Browser
        ├── Monitoring
        └── Settings
```

### Navigation Components
- **Projects List**: Uses top app bar with navigation drawer
- **Project Screens**: Bottom navigation with 4 tabs
- **Transitions**: Use Material Design shared element transitions

## Screen Specifications

### 1. Projects List Screen (Home)

**Purpose**: Display all coding projects managed by the AI agent

**Layout**:
- Top app bar with app title "Pocket Agent"
- Menu icon (hamburger) for navigation drawer
- Search icon for filtering projects
- FAB (+) for creating new project

**Main Content**:
- Vertical scrolling list of project cards
- Each card shows:
  - Project name (Title Large)
  - Project path (Body Small, secondary color)
  - Last activity timestamp
  - Status indicator (Active/Idle)
  - Connection status dot (green=connected, yellow=connecting, red=error)
  - 3-dot menu for actions (open externally, rename, archive, delete)

**Empty State**:
- Centered illustration
- "No projects yet" headline
- "Create your first project" button

### 2. Project Dashboard Screen

**Purpose**: Main workspace for interacting with a specific project

**Layout**:
- Top app bar with project name
- Back arrow to return to projects list
- Status chip showing connection state
- Bottom navigation with 4 tabs:
  1. Chat (chat icon)
  2. Files (folder icon)
  3. Monitor (activity icon)
  4. Settings (settings icon)

#### 2.1 Chat Tab (Default)

**Purpose**: AI conversation interface

**Components**:
- Message list (RecyclerView pattern)
- Message types to support:
  - User messages (right-aligned bubble, primary color)
  - Agent messages (left-aligned, surface color)
  - Code blocks (monospace font, syntax highlighting hint)
  - System messages (centered, muted)
  - Error messages (red accent)
  - Progress indicators (with percentage)
  - Permission requests (card with Allow/Deny buttons)
  - File operation summaries
  - Command execution results

**Input Area** (pinned to bottom):
- Multiline text input with "Message" hint
- Send button (arrow icon)
- Attachment button (paperclip icon)
- Voice input button (microphone icon) - show as disabled for v1

**Special Features**:
- Show "typing" indicator when agent is processing
- Auto-scroll to bottom on new messages
- Long-press to copy message text
- Code blocks should have a copy button

#### 2.2 Files Tab

**Purpose**: Browse project files and Git status

**Layout**:
- Current path breadcrumb
- File/folder list with:
  - Icon (folder/file type)
  - Name
  - Git status indicator (modified/new/deleted)
  - File size (for files)
  - Last modified date

**Actions**:
- Tap folder to navigate
- Tap file to view (bottom sheet with preview)
- Long press for context menu
- Pull-to-refresh for updates

#### 2.3 Monitor Tab

**Purpose**: Show background agent activities

**Components**:
- Active processes card:
  - Process name
  - Status (running/stopped)
  - CPU/Memory usage bars
  - Start/stop button

- Recent activities list:
  - Timestamp
  - Activity description
  - Status icon

- Resource usage card:
  - Battery impact
  - Network usage
  - Storage used

#### 2.4 Settings Tab

**Purpose**: Project-specific settings

**Sections**:
- Connection settings
  - Server URL
  - Authentication status
  - Reconnect button
  
- Permissions
  - File access level
  - Command execution
  - Auto-approve settings

- Theme
  - Light/Dark/System toggle
  - Dynamic colors toggle

- About
  - Project info
  - Agent version
  - Debug logs button

### 3. Quick Action Sheets

**Chat Context Menu** (bottom sheet):
- Copy message
- Share message
- Report issue
- Delete (for user messages)

**File Context Menu** (bottom sheet):
- Open in editor
- Share file
- Show in system file manager
- Git actions (if applicable)

### 4. Dialogs

**Create New Project**:
- Project name input
- Project path input (with folder picker)
- Create button
- Cancel button

**Permission Request**:
- Icon representing permission type
- Title: "Permission Required"
- Description of what agent wants to do
- File/command details in monospace
- Allow/Deny buttons
- "Remember this choice" checkbox

**Error Dialog**:
- Error icon
- Error title
- Technical details (expandable)
- Retry/Dismiss buttons

## Component Specifications

### Buttons
- **Primary**: Filled button with primary color
- **Secondary**: Outlined button
- **Text**: Text-only button for less emphasis
- **Icon**: 48dp touch target for toolbar actions
- **FAB**: 56dp with 16dp margin from edges

### Cards
- 4dp elevation (8dp when raised)
- 12dp corner radius
- 16dp internal padding
- 8dp margin between cards

### Chips
- Status chips: Icon + text
- Use semantic colors (green=active, yellow=warning, red=error)
- 32dp height

### Progress Indicators
- Linear progress for known progress
- Circular for indeterminate
- Always show percentage when known

### Message Bubbles
- Max width: 85% of screen
- 16dp padding
- 16dp corner radius (except corner pointing to sender)
- Code blocks: 4dp corner radius, monospace font

## Interaction Patterns

### Gestures
- Swipe down to refresh (where applicable)
- Swipe to dismiss notifications
- Long press for context menus
- Pinch to zoom in file viewer

### Feedback
- Ripple effects on all touchable elements
- Haptic feedback on important actions
- Loading states for all async operations
- Success/error snackbars for actions

### Animations
- Use Material Design standard durations
- Fade transitions between screens
- Slide up for bottom sheets
- Scale + fade for FAB actions

## Accessibility Considerations

- All interactive elements must have content descriptions
- Color alone should not convey information (use icons/text too)
- Support screen readers (TalkBack)
- Maintain WCAG AA contrast ratios
- Support keyboard navigation where applicable
- Announce state changes to screen readers

## Special States

### Loading States
- Show skeleton screens while loading content
- Progress indicators for long operations
- Maintain layout stability (no jumping)

### Empty States
- Friendly illustrations
- Clear explanation of why empty
- Action button to resolve (if applicable)

### Error States
- Clear error messages
- Actionable recovery options
- Technical details available but collapsed

### Offline State
- Banner indicating offline status
- Disabled states for features requiring connection
- Queue actions for when connection returns

## Platform-Specific Considerations

### Android-Specific
- Support back gesture navigation
- Respect system status bar color
- Support predictive back animations (Android 14+)
- Edge-to-edge content display
- Support Dynamic Color (Material You) on Android 12+

### Notification Design
- Use Android notification channels
- Show ongoing notification for background service
- Include quick actions in notifications
- Follow Android notification guidelines

## Design Deliverables Needed

1. **High-fidelity mockups** for all screens in both light and dark themes
2. **Interactive prototype** showing main user flows
3. **Component library** with all reusable elements
4. **Responsive layouts** for phone and tablet
5. **State variations** (empty, loading, error) for each screen
6. **Micro-interactions** and animation specifications
7. **Accessibility annotations** for development

## Key User Flows to Prototype

1. **First Launch**:
   - Splash screen → Empty projects list → Create first project → Project dashboard

2. **Typical Usage**:
   - Projects list → Select project → Chat with agent → View file changes → Monitor background tasks

3. **Error Handling**:
   - Connection lost → Error banner → Retry connection → Success feedback

4. **Permission Flow**:
   - Agent requests permission → Permission dialog → User decision → Action proceeds/blocked

## Additional Notes

- Maintain consistency with Material Design 3 guidelines throughout
- Ensure smooth performance with large chat histories (virtualization)
- Design should feel professional but approachable
- Consider developers as the primary audience
- Support one-handed usage on phones where possible
- Prepare for future voice interaction features (v2)

## Reference Materials

- Material Design 3 Guidelines: https://m3.material.io
- Material Theme Builder for color palettes
- Android Design Guidelines for platform-specific patterns
- Consider similar apps: GitHub Mobile, Termux, AI chat apps

---

This mockup should represent a professional, efficient, and modern development tool that developers would want to use daily for managing their coding projects with AI assistance.