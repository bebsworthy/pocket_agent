# UI Navigation & Foundation Feature Specification - Implementation Notes
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | [Theme](./ui-navigation-theme.feat.md) | [Components](./ui-navigation-components.feat.md) | [Scaffolding](./ui-navigation-scaffolding.feat.md) | [State](./ui-navigation-state.feat.md) | [Testing](./ui-navigation-testing.feat.md) | **Implementation** | [Index](./ui-navigation-index.md)

## Critical Implementation Details

### Navigation State Preservation
- Save navigation state in SavedStateHandle for process death recovery
- Use rememberSaveable for transient UI state within screens
- Implement proper deep link handling with pending intents
- Handle navigation during configuration changes gracefully

### Theme Implementation
- Store theme preference in DataStore for persistence
- Apply theme before setContent to avoid flashing
- Handle dynamic color gracefully on older devices
- Ensure theme changes propagate to all composed content

### Gesture Navigation
- Support Android 10+ gesture navigation properly
- Handle edge-to-edge display with proper insets
- Implement predictive back gesture for Android 14+
- Ensure bottom navigation doesn't interfere with system gestures

## Performance Considerations (Android-Specific)

- **Lazy Loading**: Use LazyColumn/LazyRow for lists to reduce memory usage
- **State Hoisting**: Hoist state to appropriate level to minimize recomposition
- **Stable Keys**: Use stable keys in lists to optimize recomposition
- **Image Loading**: Use Coil with proper caching and sampling
- **Animation Performance**: Use AnimatedVisibility and animateAsState efficiently

**Purpose**: Example showing Compose performance optimizations including remember usage, derivedStateOf for computed values, and proper recomposition scope management.

```kotlin
// Performance-optimized list implementation
@Composable
fun OptimizedProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit
) {
    // Derive filtered list only when source changes
    val sortedProjects by remember(projects) {
        derivedStateOf {
            projects.sortedByDescending { it.lastActive }
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = sortedProjects,
            key = { project -> project.id }, // Stable key for recomposition
            contentType = { "project_card" } // Help compose optimize
        ) { project ->
            // Only this item recomposes when clicked
            ProjectCard(
                project = project,
                onClick = { onProjectClick(project) }
            )
        }
    }
}

// Optimized theme switching
@Composable
fun OptimizedThemeProvider(
    content: @Composable () -> Unit
) {
    // Only read theme once per recomposition
    val themeConfig by LocalThemeConfig.current.collectAsState()
    
    // Memoize theme creation
    val colorScheme = remember(themeConfig) {
        createColorScheme(themeConfig)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketAgentTypography,
        content = content
    )
}
```

## Package Structure

```
ui/
├── navigation/
│   ├── AppNavigation.kt
│   ├── ProjectNavigation.kt
│   ├── NavigationExtensions.kt
│   └── DeepLinkHandler.kt
├── theme/
│   ├── Theme.kt
│   ├── Color.kt
│   ├── Typography.kt
│   ├── Shape.kt
│   └── ThemeManager.kt
├── components/
│   ├── base/
│   │   ├── BaseScreen.kt
│   │   ├── LoadingState.kt
│   │   ├── ErrorState.kt
│   │   └── EmptyState.kt
│   ├── common/
│   │   ├── PrimaryActionButton.kt
│   │   ├── StatusChip.kt
│   │   ├── ProjectCard.kt
│   │   ├── LoadingOverlay.kt
│   │   └── ErrorBanner.kt
│   └── navigation/
│       ├── BottomNavigation.kt
│       ├── TopAppBar.kt
│       └── NavigationDrawer.kt
├── screens/
│   ├── welcome/
│   │   ├── WelcomeScreen.kt
│   │   └── WelcomeViewModel.kt
│   ├── projects/
│   │   ├── ProjectsListScreen.kt
│   │   ├── ProjectsListViewModel.kt
│   │   ├── ProjectCreationScreen.kt
│   │   └── ProjectCreationViewModel.kt
│   ├── project/
│   │   ├── dashboard/
│   │   ├── chat/
│   │   ├── files/
│   │   └── settings/
│   └── settings/
│       ├── AppSettingsScreen.kt
│       └── AppSettingsViewModel.kt
├── state/
│   ├── UiState.kt
│   ├── BaseViewModel.kt
│   └── StateExtensions.kt
└── utils/
    ├── ComposeExtensions.kt
    ├── NavigationUtils.kt
    └── AccessibilityUtils.kt
```

## Future Extensions (Android Mobile Focus)

- **Tablet Support**: Implement responsive layouts with master-detail navigation
- **Foldable Support**: Handle folding states and dual-screen layouts
- **Widget Support**: Create home screen widgets for quick project access
- **Wear OS Companion**: Extend navigation to smartwatch companion app
- **Performance**: Implement baseline profiles for faster startup
- **Security**: Add app-specific biometric lock for navigation