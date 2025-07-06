# Screen Design & User Interface Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Application Flow Diagram](#application-flow-diagram)
3. [Design System](#design-system)
   - [Visual Language](#visual-language)
   - [Component Library](#component-library)
   - [Interaction Patterns](#interaction-patterns)
4. [Screen Specifications](#screen-specifications)
   - [Splash Screen](#splash-screen)
   - [Onboarding Flow](#onboarding-flow)
   - [Projects List Screen](#projects-list-screen)
   - [Project Creation Screen](#project-creation-screen)
   - [Server Management Screen](#server-management-screen)
   - [SSH Identity Management Screen](#ssh-identity-management-screen)
   - [Project Dashboard Screen](#project-dashboard-screen)
   - [Chat Screen](#chat-screen)
   - [Files Browser Screen](#files-browser-screen)
   - [Project Settings Screen](#project-settings-screen)
   - [App Settings Screen](#app-settings-screen)
   - [Permission Dialog](#permission-dialog)
   - [Connection Status Sheet](#connection-status-sheet)
5. [Common Components](#common-components)
6. [Accessibility Requirements](#accessibility-requirements)
7. [Responsive Design Guidelines](#responsive-design-guidelines)

## Overview

This specification provides detailed design guidelines for every screen in the Pocket Agent application. Each screen section includes user actions, displayed information, visual hierarchy, and interaction patterns. The design follows Material Design 3 principles while maintaining a developer-focused aesthetic that prioritizes efficiency and clarity.

## Application Flow Diagram

```
App Launch
    │
    ├─→ Splash Screen
    │       │
    │       ├─→ First Launch? ─→ Yes ─→ Onboarding Flow
    │       │                              │
    │       │                              ├─→ Welcome
    │       │                              ├─→ SSH Key Import
    │       │                              ├─→ First Server Setup
    │       │                              └─→ Projects List (Empty)
    │       │
    │       └─→ No ─→ Projects List
    │
Projects List (Home)
    │
    ├─→ Create Project → Project Creation
    │                        │
    │                        ├─→ Server Selection
    │                        ├─→ Project Configuration
    │                        └─→ Project Dashboard
    │
    ├─→ Select Project → Project Dashboard
    │                        │
    │                        ├─→ Chat (Bottom Nav)
    │                        ├─→ Files (Bottom Nav)
    │                        └─→ Settings (Bottom Nav)
    │
    ├─→ Settings → App Settings
    │                 │
    │                 ├─→ Theme Settings
    │                 ├─→ Voice Settings
    │                 ├─→ Notification Settings
    │                 └─→ About
    │
    └─→ Manage → Server Management
                    │
                    └─→ SSH Identity Management
```

## Design System

### Visual Language

**Color Palette**
- Primary: Developer-friendly blue-green tones
- Surface colors adapt to dark/light theme
- Accent colors for different connection states:
  - Connected: Green
  - Connecting: Amber
  - Disconnected: Gray
  - Error: Red
- Semantic colors for actions:
  - Destructive: Red
  - Confirmation: Green
  - Information: Blue

**Typography**
- Headers: Clear hierarchy with weight variations
- Body text: Optimized for readability
- Code/Terminal: Monospace font for technical content
- Emphasis through weight and color, not size alone

**Spacing System**
- Base unit: 8dp grid
- Consistent padding: 16dp for screen edges
- Component spacing: 8dp between related items, 16dp between sections
- Touch targets: Minimum 48dp for accessibility

**Elevation & Depth**
- Cards: Subtle elevation for grouping
- Dialogs: Higher elevation for focus
- Bottom sheets: Slide from bottom with scrim
- Floating Action Buttons: Highest elevation

### Component Library

**Buttons**
- Primary: Filled button for main actions
- Secondary: Outlined button for alternative actions
- Text button: For less prominent actions
- Icon buttons: For toolbar actions
- Extended FAB: For primary screen actions

**Cards**
- Project cards: Display connection status, last activity
- Quick action cards: Icon, title, and description
- Status cards: Real-time information display

**Lists**
- Single-line: Simple selections
- Two-line: Title and description
- Three-line: Additional metadata
- Leading icons/avatars
- Trailing actions/indicators

**Input Fields**
- Outlined text fields with labels
- Helper text for guidance
- Error states with explanations
- Password fields with visibility toggle
- Dropdown menus for selections

### Interaction Patterns

**Navigation**
- Bottom navigation for project-level screens
- Top app bar with contextual actions
- Back navigation always available
- Swipe gestures for tab switching

**Feedback**
- Loading states with progress indicators
- Success/error snackbars
- Haptic feedback for important actions
- Animated transitions between states

**Gestures**
- Swipe to refresh in lists
- Long press for contextual menus
- Pinch to zoom in file viewer
- Pull-down for quick actions

## Screen Specifications

### Splash Screen

**Purpose**: Brief loading screen shown during app initialization

**Visual Elements**:
- Centered app logo
- App name below logo
- Subtle loading indicator at bottom
- Gradient or solid background matching theme

**User Actions**:
- None (automatic progression)

**Information Displayed**:
- App branding
- Version number (small, bottom corner)

**Duration**: 1-2 seconds maximum

### Onboarding Flow

#### Welcome Screen

**Purpose**: Introduce the app to first-time users

**Visual Elements**:
- Illustration showing app concept
- Welcome headline
- Brief description (2-3 lines)
- "Get Started" primary button
- "Skip" text button (top right)

**User Actions**:
- Tap "Get Started" to continue
- Tap "Skip" to go directly to empty projects list

**Information Displayed**:
- App value proposition
- Key features (3 bullet points)

#### SSH Key Import Screen

**Purpose**: Guide users to import their first SSH key

**Visual Elements**:
- Instructional illustration
- Step-by-step guide
- "Import SSH Key" primary button
- "I'll do this later" text button

**User Actions**:
- Tap "Import SSH Key" to open file picker
- Select key file from device storage
- Enter passphrase if encrypted
- Tap "I'll do this later" to skip

**Information Displayed**:
- Why SSH keys are needed
- Supported key formats
- Security assurance

#### First Server Setup Screen

**Purpose**: Add first server profile

**Visual Elements**:
- Form fields:
  - Server name (text input)
  - Hostname (text input)
  - Port (number input, default 22)
  - Username (text input)
  - SSH key (dropdown selector)
- "Test Connection" secondary button
- "Save & Continue" primary button

**User Actions**:
- Fill in server details
- Select SSH key from imported keys
- Test connection (optional)
- Save server profile

**Information Displayed**:
- Field labels and hints
- Connection test results
- Error messages if connection fails

### Projects List Screen

**Purpose**: Main screen showing all projects

**Visual Elements**:
- Top app bar:
  - App name/logo (left)
  - Search icon
  - Settings icon
  - Overflow menu (Manage Servers)
- Project list:
  - Card for each project
  - Empty state illustration when no projects
- Floating Action Button: "+" for new project

**Project Card Contents**:
- Project name (bold)
- Server name (subtitle)
- Connection status indicator:
  - Green dot: Connected
  - Gray dot: Disconnected
  - Amber dot: Connecting
  - Red dot: Error
- Last activity timestamp
- Quick connect button (if disconnected)

**User Actions**:
- Tap project card to open project
- Tap quick connect to establish connection
- Long press for context menu:
  - Connect/Disconnect
  - Shutdown Claude
  - Edit project
  - Delete project
- Tap FAB to create project
- Tap search to filter projects
- Tap settings for app settings
- Pull down to refresh

**Information Displayed**:
- Number of projects
- Connection states
- Last activity times
- Search results when searching

**Empty State**:
- Friendly illustration
- "No projects yet" message
- "Create your first project" button

### Project Creation Screen

**Purpose**: Create new project in multiple steps

#### Step 1: Server Selection

**Visual Elements**:
- List of available servers
- Each server shows:
  - Server name
  - Hostname
  - Connection status
- "Add New Server" option at bottom

**User Actions**:
- Select existing server
- Tap "Add New Server" to create new
- Tap "Next" to continue

**Information Displayed**:
- Available servers
- Server details
- Current selection highlight

#### Step 2: Project Configuration

**Visual Elements**:
- Form fields:
  - Project name (text input)
  - Project path on server (text input with browse button)
  - Scripts folder (text input, default: "scripts")
  - Claude session name (optional)
- Repository section:
  - "Clone from repository" checkbox
  - Repository URL (when checked)
  - Access token (for private repos)

**User Actions**:
- Enter project details
- Toggle repository cloning
- Browse server directories
- Create project

**Information Displayed**:
- Field validations
- Path suggestions
- Repository cloning status

### Server Management Screen

**Purpose**: Manage server profiles

**Visual Elements**:
- Top app bar with back button
- Server list:
  - Server name
  - Hostname and port
  - Number of projects
  - SSH key used
  - Edit icon
  - Delete icon (if no projects)
- FAB for adding server

**User Actions**:
- Tap server to edit
- Tap delete to remove (with confirmation)
- Tap FAB to add new server
- Tap SSH keys to manage identities

**Information Displayed**:
- Server details
- Usage statistics
- SSH key associations

### SSH Identity Management Screen

**Purpose**: Manage imported SSH keys

**Visual Elements**:
- Top app bar with back button
- SSH key list:
  - Key name
  - Key fingerprint
  - Import date
  - Number of servers using
  - Delete icon (if unused)
- FAB for importing key

**User Actions**:
- Tap key to view details
- Tap delete to remove (with confirmation)
- Tap FAB to import new key
- Long press to copy fingerprint

**Information Displayed**:
- Key details
- Security information
- Usage across servers

### Project Dashboard Screen

**Purpose**: Project overview and quick actions

**Visual Elements**:
- Top app bar:
  - Project name
  - Connection status
  - Connect/disconnect button
  - Overflow menu
- Connection card:
  - Large status indicator
  - Server name
  - Uptime/downtime
  - "Connect" or "Disconnect" button
- Quick actions section:
  - Grid of action cards (2 columns)
  - Each card: icon, name, type indicator
- Recent activity:
  - Last 5 Claude interactions
  - Timestamp and preview
- Bottom navigation bar

**User Actions**:
- Tap connection card to view details
- Tap quick action to execute
- Long press action for details
- Tap activity item to go to chat
- Navigate via bottom bar

**Information Displayed**:
- Connection statistics
- Available quick actions
- Recent activity summary
- Resource usage (if connected)

### Chat Screen

**Purpose**: Main interaction with Claude

**Visual Elements**:
- Top app bar:
  - Back/Up button
  - "Chat" title
  - Voice input toggle
  - Clear chat button
- Message list:
  - User messages (right-aligned, colored)
  - Claude messages (left-aligned)
  - Typing indicator
  - Permission request cards
  - Progress indicators
  - Code blocks with syntax highlighting
- Input area:
  - Text field with multiline support
  - Send button
  - Voice input button (when enabled)
  - Attachment button (future)

**Message Types**:

1. **User Message**
   - Visual: Right-aligned, primary color background, timestamp
   - Content: User prompts, questions, or commands
   - Metadata: Delivery status indicator

2. **Claude Response Message**
   - Visual: Left-aligned, surface color background, timestamp
   - Content: Claude's answers with markdown support
   - Features: Code blocks, lists, inline code

3. **System Message**
   - Visual: Centered, muted text, smaller font, system icon
   - Content: Connection events, session status
   - Examples: "Connected to project", "Session resumed"

4. **Permission Request Card**
   - Visual: Elevated card with tool icon, countdown timer
   - Content: Tool name, action description, affected resources
   - Actions: Allow/Deny buttons, expandable details
   - States: Pending (with timer), Approved (green), Denied (red)

5. **Progress Update Message**
   - Visual: Progress bar, percentage, task description
   - Content: Current operation, time remaining
   - Updates: Real-time without creating new messages

6. **Error Message**
   - Visual: Red accent, error icon, expandable stack trace
   - Content: Error description, suggested fixes
   - Actions: Retry button, view logs link

7. **Code Block Message**
   - Visual: Monospace font, syntax highlighting, language badge
   - Features: Copy button, line numbers, horizontal scroll
   - Actions: Full-screen view, copy code

8. **Sub-Agent Status Message**
   - Visual: Indented with agent icon, progress indicator
   - Content: Agent name (PLANNING, RUNNING, etc.), current task
   - States: Active (animated), Completed (checkmark), Failed (X)

9. **Task Completion Message**
   - Visual: Success indicator, summary with collapsible details
   - Content: Accomplishments, modified files, test results
   - TTS: May contain <speak> tags for voice summary

10. **Typing Indicator**
    - Visual: Animated dots at bottom of chat
    - Variants: "Claude is thinking...", "Analyzing code...", "Running tests..."

11. **File Reference Message**
    - Visual: File icon, path badge, code preview
    - Content: Clickable file path, line numbers
    - Actions: Open in file browser, expand preview

12. **Command Execution Message**
    - Visual: Terminal-style with command header
    - Content: Command, output (stdout/stderr), exit code
    - Features: Collapsible output, copy command, re-run

13. **Voice Input Message**
    - Visual: Microphone icon, transcription confidence
    - Content: Same as user message with voice indicator
    - Features: Edit transcription option

14. **Connection Status Card**
    - Visual: Expandable status card with metrics
    - Content: SSH status, WebSocket state, latency
    - Actions: Reconnect, diagnostics

15. **Quick Action Result**
    - Visual: Action icon, execution time, status
    - Content: Action name, result summary
    - States: Running (spinner), Success (green), Failed (red)

**User Actions**:
- Type and send messages
- Tap voice button for speech input
- Scroll through history
- Copy message text
- Tap code blocks to copy
- Respond to permission requests
- Retry failed messages
- Clear chat history

**Information Displayed**:
- Conversation history
- Real-time responses
- Permission requests
- Task progress
- Error messages
- Connection status

### Files Browser Screen

**Purpose**: Browse project files and git status

**Visual Elements**:
- Top app bar:
  - Current path breadcrumb
  - View toggle (list/grid)
  - Sort button
- File list:
  - File/folder icons
  - Names
  - Git status indicators:
    - Modified (M)
    - Added (A)
    - Deleted (D)
    - Untracked (?)
  - File sizes
  - Last modified
- Path breadcrumb (scrollable)
- FAB for quick actions menu

**User Actions**:
- Tap folder to navigate
- Tap file to view details
- Long press for context menu:
  - Open in Claude
  - Copy path
  - Git actions
- Tap breadcrumb to navigate up
- Pull to refresh
- Pinch to change view size

**Information Displayed**:
- Directory structure
- File details
- Git status
- File permissions
- Symbolic links

### Project Settings Screen

**Purpose**: Configure project-specific settings

**Visual Elements**:
- Top app bar with back button
- Settings sections:
  - Connection settings
  - Claude preferences
  - Quick actions management
  - Notification preferences
  - Advanced options
- Each section expandable

**Connection Settings**:
- Server (read-only)
- Project path
- Scripts folder location
- Auto-connect toggle
- Keep-alive interval

**Claude Preferences**:
- Default model selection
- Context window size
- Custom instructions
- Temperature settings

**User Actions**:
- Modify settings
- Test changes
- Reset to defaults
- Export/import settings

**Information Displayed**:
- Current values
- Descriptions
- Validation messages

### App Settings Screen

**Purpose**: Global app configuration

**Visual Elements**:
- Top app bar with back button
- Settings categories:
  - Appearance
  - Voice & Audio
  - Notifications
  - Security
  - Data & Storage
  - About

**Appearance**:
- Theme selection (Light/Dark/System)
- Color customization
- Font size
- Code editor theme

**Voice & Audio**:
- Voice input toggle
- TTS settings
- Voice selection
- Speed controls
- Smart summarization

**Notifications**:
- Permission requests
- Task completion
- Connection events
- Sound preferences

**Security**:
- Biometric lock
- Auto-lock timeout
- SSH key encryption
- Token vault settings

**User Actions**:
- Toggle switches
- Select from options
- Adjust sliders
- Test settings
- Clear data
- Export logs

**Information Displayed**:
- Current selections
- Storage usage
- Version information
- Legal notices

### Permission Dialog

**Purpose**: Handle Claude permission requests

**Visual Elements**:
- Dialog overlay with scrim
- Icon indicating tool type
- Title: "Permission Request"
- Description of requested action
- File paths or commands
- Timer showing remaining time
- Two buttons: Deny and Allow

**User Actions**:
- Tap Allow to approve
- Tap Deny to reject
- Tap outside to dismiss (counts as deny)
- Expand details if truncated

**Information Displayed**:
- Tool requesting permission
- Exact action to be performed
- Affected files/resources
- Time remaining to respond

### Connection Status Sheet

**Purpose**: Detailed connection information

**Visual Elements**:
- Bottom sheet design
- Connection diagram:
  - Phone → SSH → Server → Claude
  - Status indicators at each point
- Statistics:
  - Connection uptime
  - Messages exchanged
  - Data transferred
  - Latency
- Logs section (expandable)
- Action buttons

**User Actions**:
- Swipe down to dismiss
- Tap "Reconnect" if disconnected
- Tap "View Logs" for details
- Copy connection info
- Run diagnostics

**Information Displayed**:
- Real-time connection path
- Performance metrics
- Error messages
- Diagnostic results

## Common Components

### Empty States
- Friendly illustrations
- Clear message explaining the empty state
- Action button to resolve (when applicable)
- Consistent across all screens

### Loading States
- Skeleton screens for lists
- Progress indicators for actions
- Meaningful loading messages
- Cancel option for long operations

### Error States
- Clear error messages
- Suggested actions
- Retry buttons
- Contact support option

### Snackbars
- Brief confirmation messages
- Undo actions when applicable
- Auto-dismiss after 4 seconds
- Single action button maximum

### Dialogs
- Clear titles
- Concise descriptions
- Maximum 2 actions
- Destructive actions in red

## Accessibility Requirements

**Visual**:
- Color contrast ratios meet WCAG AA
- Icons accompanied by text labels
- Focus indicators visible
- Text scalable to 200%

**Navigation**:
- All functions keyboard accessible
- Logical tab order
- Skip links where appropriate
- Clear focus management

**Screen Reader**:
- Meaningful content descriptions
- Proper heading hierarchy
- Form labels and hints
- State changes announced

**Interaction**:
- Touch targets minimum 48dp
- Sufficient time for actions
- Gesture alternatives
- Error prevention

## Responsive Design Guidelines

**Phone Layouts** (Default):
- Single column layouts
- Bottom navigation
- Full-width components
- Collapsible sections

**Tablet Adjustments**:
- Two-column layouts where beneficial
- Side navigation option
- Expanded quick actions grid
- Master-detail patterns

**Landscape Orientation**:
- Adjusted layouts for wider screens
- Side-by-side panels
- Optimized input areas
- Maintained thumb reach

**Foldable Support**:
- Adaptive layouts for different postures
- Table-top mode for chat
- Book mode for file browser
- Proper hinge avoidance