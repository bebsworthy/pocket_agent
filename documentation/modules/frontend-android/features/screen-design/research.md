# Screen Design - Research

## Executive Summary

This research provides a comprehensive analysis of design patterns and technical considerations for the Pocket Agent mobile interface. Key findings indicate that successful mobile developer tools share common patterns of clear information hierarchy, efficient navigation, and thoughtful touch interactions. The research recommends implementing Material Design 3 with custom developer-focused enhancements, using a hub-and-spoke navigation model, and prioritizing accessibility from the outset. The analysis of industry-leading apps like GitHub Mobile, Termux, and Working Copy reveals that developers expect professional aesthetics combined with powerful functionality, even on mobile devices. Critical technical decisions include using Jetpack Compose for implementation, adopting dynamic color theming for Android 12+, and ensuring all touch targets meet the 48dp minimum for accessibility.

The research demonstrates that careful attention to loading states, error handling, and responsive design will be crucial for creating a professional developer experience on mobile. By following Material Design 3 principles while adding developer-specific customizations, Pocket Agent can deliver an interface that feels both familiar and uniquely suited to AI-assisted development workflows.

## Mobile Developer Tool Design Patterns

### Industry Analysis

Research into successful mobile developer tools reveals key patterns:

1. **GitHub Mobile**
   - Clean, scannable lists for repositories
   - Clear status indicators for CI/CD
   - Efficient code review interface
   - Quick actions for common tasks

2. **Termux**
   - Terminal-first interface
   - Gesture-based controls
   - Customizable appearance
   - Efficient text input methods

3. **Working Copy**
   - File browser with git integration
   - Syntax highlighting for code
   - Diff visualization
   - Touch-optimized interactions

4. **Slack**
   - Real-time messaging patterns
   - Permission request handling
   - Status indicators
   - Thread organization

### Design Pattern Research

#### Information Architecture

The app's structure follows a hub-and-spoke model:

```
Projects List (Hub)
├── Project Dashboard (Spoke)
│   ├── Chat (Sub-spoke)
│   ├── Files (Sub-spoke)
│   └── Settings (Sub-spoke)
├── Create Project (Spoke)
├── Server Management (Spoke)
└── App Settings (Spoke)
```

This architecture provides:
- Clear mental model
- Predictable navigation
- Minimal depth (max 3 levels)
- Easy return to home

#### Screen Layout Patterns

##### List-Detail Pattern
```
┌─────────────────┐
│   App Bar       │
├─────────────────┤
│                 │
│   List Items    │
│                 │
│                 │
├─────────────────┤
│      FAB        │
└─────────────────┘
```

Used for:
- Projects list
- Server management
- SSH key management

##### Dashboard Pattern
```
┌─────────────────┐
│   App Bar       │
├─────────────────┤
│  Status Card    │
├─────────────────┤
│  Quick Actions  │
│    (Grid)       │
├─────────────────┤
│ Recent Activity │
├─────────────────┤
│   Bottom Nav    │
└─────────────────┘
```

Provides at-a-glance information and quick access to functions.

##### Chat Pattern
```
┌─────────────────┐
│   App Bar       │
├─────────────────┤
│                 │
│   Messages      │
│                 │
│                 │
├─────────────────┤
│   Input Area    │
└─────────────────┘
```

Optimized for real-time communication with Claude.

### Material Design 3 Research

#### Color System

Material You introduces dynamic color:

```kotlin
// Dynamic color extraction
val dynamicColor = DynamicColors.from(context)
val primary = dynamicColor.primary
val onPrimary = dynamicColor.onPrimary
val primaryContainer = dynamicColor.primaryContainer
```

Fallback colors for brand consistency:
- Primary: Blue-green (#00C853)
- Error: Red (#D32F2F)
- Success: Green (#4CAF50)
- Warning: Orange (#FF6D00)

#### Typography Scale

Research shows optimal sizes for mobile:

```
Display Large: 57sp (Splash screen)
Headline Medium: 28sp (Screen titles)
Title Large: 22sp (Section headers)
Body Large: 16sp (Main content)
Label Large: 14sp (Buttons)
Label Medium: 12sp (Captions)
```

#### Component Elevation

Material 3 elevation strategy:
- Level 0: Surface
- Level 1: Cards, bottom sheets
- Level 2: Elevated buttons
- Level 3: FABs
- Level 4: Navigation drawers
- Level 5: Dialogs, menus

### Touch Target Research

#### Minimum Sizes

Accessibility guidelines mandate:
- Minimum touch target: 48dp × 48dp
- Minimum spacing between targets: 8dp
- Preferred target size: 56dp × 56dp

#### Gesture Zones

```
┌─────────────────┐
│  Status Bar     │ <- System
├─────────────────┤
│                 │
│  Content Area   │ <- App gestures
│                 │
├─────────────────┤
│  Navigation     │ <- System gestures
└─────────────────┘
```

Avoid gesture conflicts with system navigation.

### Message Type Design Research

#### Visual Differentiation Strategy

Each message type has distinct visual characteristics:

1. **Position**: User (right), Claude (left), System (center)
2. **Color**: Brand colors for user, neutral for Claude, muted for system
3. **Shape**: Rounded corners with directional tails
4. **Elevation**: Cards for interactive elements
5. **Icons**: Contextual icons for message types

#### Permission Request Design

 Research shows effective permission requests:
- **Prominent display**: Elevated cards draw attention
- **Clear actions**: Binary choice (Allow/Deny)
- **Time pressure**: Countdown creates urgency
- **Context**: Show exact action and resources

### Code Display Research

#### Syntax Highlighting

Optimal color schemes for mobile:

```kotlin
val codeTheme = CodeTheme(
    keyword = Color(0xFF859900),      // Green
    string = Color(0xFF2AA198),       // Cyan
    comment = Color(0xFF93A1A1),      // Gray
    function = Color(0xFF268BD2),     // Blue
    number = Color(0xFFD33682),       // Magenta
    background = Color(0xFF002B36)    // Dark background
)
```

#### Code Block Features

 Essential features identified:
- Line numbers for reference
- Horizontal scrolling for long lines
- Copy button for quick access
- Language badge for context
- Syntax highlighting for readability

### Navigation Pattern Research

#### Bottom Navigation

Optimal configuration:
- 3-5 items maximum (using 4)
- Icons with labels
- Clear active state
- Consistent across project screens

#### Screen Transition Research

Material Design motion:
- Forward: Slide in from right (300ms)
- Backward: Slide out to right (250ms)
- Fade through for peer transitions
- Shared element for continuity

### Error State Research

#### Effective Error Communication

1. **What went wrong**: Clear, non-technical message
2. **Why it happened**: Brief explanation
3. **How to fix it**: Actionable steps
4. **Recovery options**: Retry, contact support

#### Visual Hierarchy for Errors

```
┌─────────────────┐
│    [Error Icon] │ <- Immediate recognition
├─────────────────┤
│   Error Title   │ <- What happened
├─────────────────┤
│   Description   │ <- Why and how to fix
├─────────────────┤
│ [Retry] [Help]  │ <- Recovery actions
└─────────────────┘
```

### Loading State Research

#### Progressive Loading Strategy

1. **Skeleton Screens**: Show layout structure
2. **Shimmer Effects**: Indicate loading progress
3. **Progressive Disclosure**: Load critical content first
4. **Optimistic UI**: Show expected state immediately

### Accessibility Research

#### Screen Reader Optimization

```kotlin
// Semantic descriptions
Modifier.semantics {
    contentDescription = "Project ${project.name}, ${status}, last active ${time}"
    role = Role.Button
    stateDescription = if (isConnected) "Connected" else "Disconnected"
}
```

#### Focus Management

Key principles:
- Logical tab order
- Focus restoration after navigation
- Clear focus indicators
- Keyboard shortcuts for power users

### Performance Considerations

#### List Optimization

```kotlin
// Efficient list rendering
LazyColumn {
    items(
        items = projects,
        key = { it.id }, // Stable keys
        contentType = { "project" } // Type hints
    ) { project ->
        ProjectCard(project)
    }
}
```

#### Image Loading

- Lazy loading for off-screen content
- Appropriate image sizes
- Caching strategy
- Placeholder displays

### Device-Specific Research

#### Screen Size Adaptations

```kotlin
@Composable
fun AdaptiveLayout() {
    BoxWithConstraints {
        when {
            maxWidth < 360.dp -> CompactLayout()
            maxWidth < 600.dp -> MediumLayout()
            else -> ExpandedLayout()
        }
    }
}
```

#### Foldable Support

Considerations for foldable devices:
- Hinge avoidance
- Table-top mode for chat
- Book mode for file browser
- Continuity across postures

## Codebase Analysis - UI Component Paths

### Existing UI Structure

Based on the codebase architecture, the UI components should be organized as follows:

```
app/src/main/java/com/pocketagent/
├── ui/
│   ├── theme/
│   │   ├── Color.kt          # Color definitions
│   │   ├── Theme.kt          # Material3 theme setup
│   │   ├── Type.kt           # Typography definitions
│   │   └── Spacing.kt        # Spacing constants
│   ├── screens/
│   │   ├── splash/
│   │   │   └── SplashScreen.kt
│   │   ├── onboarding/
│   │   │   ├── OnboardingScreen.kt
│   │   │   ├── WelcomePage.kt
│   │   │   ├── SshKeyImportPage.kt
│   │   │   └── FirstServerPage.kt
│   │   ├── projects/
│   │   │   ├── ProjectsListScreen.kt
│   │   │   ├── ProjectDashboardScreen.kt
│   │   │   └── CreateProjectScreen.kt
│   │   ├── chat/
│   │   │   ├── ChatScreen.kt
│   │   │   └── ChatViewModel.kt
│   │   ├── files/
│   │   │   ├── FileBrowserScreen.kt
│   │   │   └── FileViewerScreen.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt
│   │       └── ServerManagementScreen.kt
│   ├── components/
│   │   ├── common/
│   │   │   ├── EmptyState.kt
│   │   │   ├── LoadingContent.kt
│   │   │   ├── ErrorContent.kt
│   │   │   └── BottomNavigation.kt
│   │   ├── project/
│   │   │   ├── ProjectCard.kt
│   │   │   ├── ConnectionStatusChip.kt
│   │   │   └── QuickActionGrid.kt
│   │   ├── chat/
│   │   │   ├── UserMessage.kt
│   │   │   ├── ClaudeMessage.kt
│   │   │   ├── SystemMessage.kt
│   │   │   ├── PermissionRequestCard.kt
│   │   │   ├── CodeBlock.kt
│   │   │   └── ChatInputBar.kt
│   │   ├── files/
│   │   │   ├── FileListItem.kt
│   │   │   ├── PathBreadcrumb.kt
│   │   │   └── GitStatusBadge.kt
│   │   └── dialogs/
│   │       ├── PermissionDialog.kt
│   │       ├── ConfirmationDialog.kt
│   │       └── BottomSheetContent.kt
│   └── utils/
│       ├── Extensions.kt      # UI extension functions
│       ├── TimeFormatters.kt  # Time formatting utilities
│       └── IconMappers.kt     # Tool to icon mapping
```

### Integration Points

1. **Navigation Integration**
   - Screens will integrate with the Navigation component from UI Navigation Foundation
   - Each screen will have a corresponding navigation route
   - Deep linking support for quick actions

2. **ViewModel Integration**
   - Each screen will have an associated ViewModel
   - ViewModels will use the Data Layer for state management
   - Compose State will be used for UI state

3. **Theme Integration**
   - Material3 theme will be provided at the app level
   - Dynamic theming will be supported for Android 12+
   - Custom color tokens for developer-specific needs

4. **Dependency Injection**
   - Hilt will be used for dependency injection
   - ViewModels will be injected using @HiltViewModel
   - Navigation will use Hilt navigation compose

### Risk Assessment

1. **Performance Risks**
   - **Risk**: Complex layouts may cause frame drops
   - **Mitigation**: Use LazyColumn/LazyRow for lists, profile with Layout Inspector
   - **Severity**: Medium

2. **Accessibility Risks**
   - **Risk**: Touch targets too small for some users
   - **Mitigation**: Enforce 48dp minimum, test with TalkBack
   - **Severity**: High

3. **Theme Consistency Risks**
   - **Risk**: Custom components may not follow theme
   - **Mitigation**: Create strict component guidelines, regular design reviews
   - **Severity**: Low

4. **Device Fragmentation Risks**
   - **Risk**: UI may not work well on all screen sizes
   - **Mitigation**: Test on multiple devices, use responsive layouts
   - **Severity**: Medium

5. **State Management Risks**
   - **Risk**: Complex state in chat screen may cause issues
   - **Mitigation**: Use proper state hoisting, consider state machines
   - **Severity**: Medium

### Conclusion

The research demonstrates that successful mobile developer tools share common patterns: clear information hierarchy, efficient navigation, thoughtful touch interactions, and respect for platform conventions. The Pocket Agent design synthesizes these patterns while adding unique elements for AI-assisted development workflows. The codebase structure supports a clean separation of concerns with dedicated packages for screens, components, and utilities, ensuring maintainability and scalability as the feature set grows.