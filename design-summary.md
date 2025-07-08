# Pocket Agent Mobile App - Visual Design Summary

## Overview
Pocket Agent is a native Android mobile application that enables developers to remotely control Claude Code instances running on development servers. The app follows Material Design 3 principles with a developer-focused aesthetic.

## Design System

### Visual Language

#### Color Palette

**Primary Colors**
- Light Mode: Dark green (#00C853) - for actions and highlights
- Dark Mode: Bright green (#00E676) - for actions and highlights

**Background Colors**
- Light Mode: White (#FFFFFF)
- Dark Mode: True black (#000000) - optimized for OLED power savings

**Surface Colors**
- Light Mode: Light gray (#F5F5F5) for content areas
- Dark Mode: Dark gray (#121212) for content areas

**Semantic Colors**
- Error: Red (#D32F2F light / #FF5252 dark) for errors and warnings
- Success: Green variants (#4CAF50 light / #4CAF50 dark) for completed operations
- Warning: Orange (#E65100 light / #FF6D00 dark) for warnings
- Code Background: #F5F5F5 (light) / #1E1E1E (dark)

**Connection State Colors**
- Connected: Green dot with success color
- Connecting: Amber dot with primary color
- Disconnected: Gray dot with surface variant color
- Error: Red dot with error color

**Action Colors**
- Destructive: Red
- Confirmation: Green
- Information: Blue

#### Typography
- Font Family: Inter (Regular, Medium, SemiBold, Bold)
- Headers: Clear hierarchy with weight variations
- Body text: Optimized for readability (16sp base)
- Code/Terminal: Monospace font for technical content
- Emphasis through weight and color, not size alone

**Type Scale**
- Display Large: 57sp, Bold, line height 64sp
- Headline Medium: 28sp, SemiBold, line height 36sp
- Title Large: 22sp, Medium, line height 28sp
- Body Large: 16sp, Normal, line height 24sp
- Label Large: 14sp, Medium, line height 20sp

#### Spacing System
- Base unit: 8dp grid
- Screen edges: 16dp padding (standard)
- Component spacing: 8dp between related items, 16dp between sections
- Touch targets: Minimum 48dp for accessibility
- Tablet layouts: 64dp horizontal padding

#### Elevation & Depth
- Cards: Subtle elevation for grouping (default Material3 elevation)
- Dialogs: Higher elevation for focus
- Bottom sheets: Slide from bottom with scrim
- Floating Action Buttons: Highest elevation

### Component Specifications

#### Buttons
- **Primary Button**: Filled, 48dp height, primary color background
- **Secondary Button**: Outlined for alternative actions
- **Text Button**: For less prominent actions
- **Icon Buttons**: For toolbar actions
- **Extended FAB**: For primary screen actions with icon and text

#### Cards
- **Project Cards**: Display connection status, last activity, server name
- **Quick Action Cards**: 2-column grid, icon + title + type indicator
- **Status Cards**: Real-time information display with live updates
- **Rounded corners**: 8dp (medium) for cards, 16dp (large) for dialogs

#### Lists
- **Single-line**: Simple selections
- **Two-line**: Title and description
- **Three-line**: Additional metadata
- Leading icons/avatars with 20dp size
- Trailing actions/indicators

#### Input Fields
- Outlined text fields with labels
- Helper text for guidance
- Error states with explanations
- Password fields with visibility toggle
- Dropdown menus for selections

#### Status Indicators
- **Status Chips**: Pill-shaped with 8dp colored dot, text label
- **Connection Card**: Large status indicator with server info
- **Progress Bars**: Linear for determinate, circular for indeterminate
- **Loading States**: Skeleton screens for lists, spinners for actions

### Navigation Structure

#### Top-Level Navigation
1. **Welcome Screen** → Projects List (one-time)
2. **Projects List** → Main hub for all projects
3. **Server Management** → Configure development servers
4. **SSH Identity Management** → Import and manage SSH keys
5. **App Settings** → Global application preferences

#### Project-Level Navigation (Bottom Tabs)
1. **Dashboard** - Overview and quick actions
2. **Chat** - Claude interaction interface
3. **Files** - File browser with Git status
4. **Settings** - Project-specific settings

### Screen Specifications

#### Projects List Screen
- **Top App Bar**: App name/logo, search icon, settings icon, overflow menu
- **Project Cards**: 
  - Bold project name
  - Server name subtitle
  - Connection status chip (top right)
  - Last activity timestamp
  - Quick connect button (if disconnected)
- **Empty State**: Friendly illustration, "No projects yet" message, create button
- **FAB**: "+" button for new project (bottom right)

#### Dashboard Screen
- **Connection Card**: Large status indicator, server name, uptime/downtime, connect/disconnect button
- **Quick Actions Grid**: 2-column layout, action cards with icon/name/type
- **Recent Activity**: Last 5 Claude interactions with timestamp
- **Progress Tracking**: Timeline view, hierarchical display, real-time updates

#### Chat Screen
- **Message List**: Right-aligned user messages, left-aligned Claude messages
- **Message Types**:
  - User messages: Primary color background, right-aligned
  - Claude messages: Surface color background, left-aligned
  - System messages: Centered, muted text, smaller font
  - Permission requests: Elevated card with countdown timer
  - Progress updates: Progress bar with percentage
  - Error messages: Red accent with expandable details
  - Code blocks: Monospace font, syntax highlighting, copy button
  - Command execution: Terminal style with exit code
- **Input Area**: Multiline text field, send button, voice input toggle

#### Files Browser Screen
- **Path Breadcrumb**: Scrollable, current directory
- **File List**: Icons, names, Git status indicators (M/A/D/?), sizes, modified dates
- **Git Status Colors**: Modified (amber), Added (green), Deleted (red), Untracked (gray)
- **View Toggle**: List/grid view options
- **FAB**: Quick actions menu

### Responsive Design

#### Phone Layouts (Default)
- Single column layouts
- Bottom navigation for project screens
- Full-width components
- Collapsible sections

#### Tablet Adjustments
- Two-column layouts where beneficial
- Side navigation option
- Expanded quick actions grid (3-4 columns)
- Master-detail patterns
- 600dp breakpoint

#### Landscape Orientation
- Adjusted layouts for wider screens
- Side-by-side panels
- Optimized input areas
- Maintained thumb reach

#### Foldable Support
- Adaptive layouts for different postures
- Table-top mode for chat
- Book mode for file browser
- Proper hinge avoidance

### Interaction Patterns

#### Navigation
- Bottom navigation for project-level screens
- Top app bar with contextual actions
- Back navigation always available
- Swipe gestures for tab switching

#### Feedback
- Loading states with progress indicators
- Success/error snackbars (4-second auto-dismiss)
- Haptic feedback for important actions
- Animated transitions between states (fade in/out)

#### Gestures
- Swipe to refresh in lists
- Long press for contextual menus
- Pinch to zoom in file viewer
- Pull-down for quick actions

### Common UI Components

#### Empty States
- Friendly illustrations (64dp icons)
- Clear message explaining the empty state
- Action button to resolve (when applicable)
- Consistent across all screens

#### Loading States
- Skeleton screens for lists
- Circular progress indicators (48dp) for actions
- Meaningful loading messages
- Cancel option for long operations

#### Error States
- Clear error messages with icon
- Suggested actions
- Retry buttons
- Contact support option

#### Dialogs
- Clear titles
- Concise descriptions
- Maximum 2 actions
- Destructive actions in red
- 16dp rounded corners

#### Snackbars
- Brief confirmation messages
- Undo actions when applicable
- Auto-dismiss after 4 seconds
- Single action button maximum

### Accessibility Requirements

#### Visual
- Color contrast ratios meet WCAG AA
- Icons accompanied by text labels
- Focus indicators visible
- Text scalable to 200%
- Minimum 44dp touch targets

#### Navigation
- All functions keyboard accessible
- Logical tab order
- Skip links where appropriate
- Clear focus management

#### Screen Reader
- Meaningful content descriptions
- Proper heading hierarchy
- Form labels and hints
- State changes announced
- Live regions for dynamic content

#### Motor Accessibility
- Large touch targets (minimum 48dp)
- Multiple ways to perform each action
- Extended timeouts for users who need more time
- Gesture alternatives

### Special Features

#### Voice Integration
- Voice input button in chat
- TTS for responses (smart summarization)
- Voice indicator on messages
- Transcription confidence display

#### Background Monitoring
- Persistent foreground service
- Notification categories:
  - Permission requests (high priority)
  - Task completion (standard)
  - Error conditions (alert)
  - Progress updates (ongoing)

#### Theme System
- Light/Dark mode selection
- System default option
- Dynamic color support (Android 12+)
- Optional time-based switching
- Material You integration

### Animation & Motion
- Fade transitions: 300ms default
- Scale animations for buttons: 150ms
- Slide animations for sheets: 350ms
- Progress animations: Smooth, continuous
- Loading spinners: Material Design standard

This design system ensures consistency across the entire Pocket Agent mobile application while maintaining Material Design 3 compliance and developer-focused usability.