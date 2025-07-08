# UI Navigation & Foundation Feature Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [Navigation Framework](#navigation-framework)
   - [Theme System](#theme-system)
   - [Base UI Components](#base-ui-components)
   - [Screen Scaffolding](#screen-scaffolding)
   - [State Management](#state-management)
   - [Error Handling](#error-handling)
   - [Integration Points](#integration-points)
4. [Testing](#testing)
   - [Testing Checklist](#testing-checklist)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Critical Implementation Details](#critical-implementation-details)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The UI Navigation & Foundation feature provides the core user interface infrastructure for **Pocket Agent - a remote coding agent mobile interface**. This feature implements the navigation structure, Material Design 3 theme system, base UI components, and screen scaffolding that all other features will build upon. It establishes the visual design language, navigation patterns, and common UI components for a consistent user experience.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: MVVM with Jetpack Compose and Navigation Component
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#uiux-design-system)

This feature is designed to be implemented independently in Phase 1 as the foundation layer. It provides the UI framework that all subsequent features will utilize, ensuring consistent design patterns and navigation flows throughout the application.

## Architecture

### Technology Stack (Android-Specific)

- **UI Framework**: Jetpack Compose 1.6.0+ - Modern declarative UI toolkit
- **Navigation**: Navigation Compose 2.7.0+ - Type-safe navigation with deep linking
- **Theme System**: Material Design 3 (Material You) - Adaptive color system
- **State Management**: ViewModel + StateFlow - Reactive state management
- **Dependency Injection**: Hilt 2.50+ - Compile-time DI framework
- **Animation**: Compose Animation APIs - Smooth transitions and gestures
- **Window Management**: WindowSizeClass - Responsive layouts for different devices
- **Mobile Optimization**: Lazy loading, state restoration, configuration change handling

### Key Components

- **AppNavigation**: Central navigation graph managing all screen transitions
- **PocketAgentTheme**: Material Design 3 theme with dark/light mode support
- **BaseScreen**: Common screen scaffold with consistent layout structure
- **BottomNavigationBar**: Project-level navigation with 4 main sections
- **TopAppBar**: Contextual app bar with actions and navigation
- **CommonComponents**: Reusable UI components (buttons, cards, dialogs)
- **StateManager**: Centralized UI state management with persistence

## Components Architecture

### Navigation Framework

**Purpose**: Implements the complete navigation structure for the app, including top-level navigation between projects and project-level navigation with bottom tabs. Handles deep linking, back stack management, and state restoration across configuration changes.

```kotlin
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

// Navigation destinations using type-safe routes
// Domain models needed for navigation
data class Project(
    val id: String,
    val name: String,
    val serverProfile: ServerProfile,
    val projectPath: String,
    val lastActive: Instant,
    val claudeSessionId: String? = null,
    val scriptsFolder: String = "scripts",
    val status: ProjectStatus = ProjectStatus.ACTIVE
)

data class ServerProfile(
    val id: String,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshIdentityId: String
)

enum class ConnectionStatus {
    CONNECTED, CONNECTING, DISCONNECTED, ERROR, SHUTDOWN
}

enum class ProjectStatus {
    ACTIVE, PAUSED, ARCHIVED
}

@Serializable
sealed class Screen {
    @Serializable
    data object Welcome : Screen()
    
    @Serializable
    data object ProjectsList : Screen()
    
    @Serializable
    data class ProjectCreation(val serverId: String? = null) : Screen()
    
    @Serializable
    data object ServerManagement : Screen()
    
    @Serializable
    data object SshIdentityManagement : Screen()
    
    @Serializable
    data object AppSettings : Screen()
    
    @Serializable
    data class ProjectDetail(val projectId: String) : Screen()
}

@Serializable
sealed class ProjectScreen {
    @Serializable
    data object Dashboard : ProjectScreen()
    
    @Serializable
    data object Chat : ProjectScreen()
    
    @Serializable
    data object Files : ProjectScreen()
    
    @Serializable
    data object Settings : ProjectScreen()
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: Screen = Screen.Welcome
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 4 } },
        exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -it / 4 } },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally { -it / 4 } },
        popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally { it / 4 } }
    ) {
        composable<Screen.Welcome> {
            WelcomeScreen(
                onNavigateToProjects = { 
                    navController.navigate(Screen.ProjectsList) {
                        popUpTo(Screen.Welcome) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Screen.ProjectsList> {
            ProjectsListScreen(
                onNavigateToProject = { projectId ->
                    // Validate project exists before navigating
                    navController.navigate(Screen.ProjectDetail(projectId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCreateProject = {
                    navController.navigate(Screen.ProjectCreation())
                },
                onNavigateToServers = {
                    navController.navigate(Screen.ServerManagement)
                },
                onNavigateToSshIdentities = {
                    navController.navigate(Screen.SshIdentityManagement)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.AppSettings)
                }
            )
        }
        
        composable<Screen.ProjectCreation>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "pocketagent://create-project?serverId={serverId}"
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ProjectCreation>()
            val savedStateHandle = backStackEntry.savedStateHandle
            
            ProjectCreationScreen(
                serverId = args.serverId,
                savedStateHandle = savedStateHandle,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServers = {
                    navController.navigate(Screen.ServerManagement)
                },
                onProjectCreated = { projectId ->
                    navController.navigate(Screen.ProjectDetail(projectId)) {
                        popUpTo(Screen.ProjectsList)
                    }
                }
            )
        }
        
        composable<Screen.ServerManagement> {
            ServerManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSshIdentities = {
                    navController.navigate(Screen.SshIdentityManagement)
                }
            )
        }
        
        composable<Screen.SshIdentityManagement> {
            SshIdentityManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable<Screen.AppSettings> {
            AppSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable<Screen.ProjectDetail>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "pocketagent://project/{projectId}"
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ProjectDetail>()
            val savedStateHandle = backStackEntry.savedStateHandle
            
            // Validate project ID exists
            if (args.projectId.isNotEmpty()) {
                ProjectDetailScreen(
                    projectId = args.projectId,
                    savedStateHandle = savedStateHandle,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                // Navigate back if invalid project ID
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(
    projectId: String,
    savedStateHandle: SavedStateHandle,
    onNavigateBack: () -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    // Restore selected tab from saved state
    val selectedTab = savedStateHandle.get<ProjectScreen>("selected_tab") ?: ProjectScreen.Dashboard
    val projectNavController = rememberNavController()
    val navBackStackEntry by projectNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        bottomBar = {
            ProjectBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    projectNavController.navigate(screen) {
                        projectNavController.graph.findStartDestination()?.id?.let { startId ->
                            popUpTo(startId) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = projectNavController,
            startDestination = selectedTab,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable<ProjectScreen.Dashboard> {
                DashboardScreen(projectId = projectId)
            }
            
            composable<ProjectScreen.Chat> {
                ChatScreen(projectId = projectId)
            }
            
            composable<ProjectScreen.Files> {
                FilesScreen(projectId = projectId)
            }
            
            composable<ProjectScreen.Settings> {
                ProjectSettingsScreen(
                    projectId = projectId,
                    onNavigateToMainSettings = onNavigateBack
                )
            }
        }
    }
}

@Composable
fun ProjectBottomNavigation(
    currentRoute: String?,
    onNavigate: (ProjectScreen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == ProjectScreen.Dashboard::class.qualifiedName,
            onClick = { onNavigate(ProjectScreen.Dashboard) },
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") }
        )
        
        NavigationBarItem(
            selected = currentRoute == ProjectScreen.Chat::class.qualifiedName,
            onClick = { onNavigate(ProjectScreen.Chat) },
            icon = { Icon(Icons.Filled.Chat, contentDescription = "Chat") },
            label = { Text("Chat") }
        )
        
        NavigationBarItem(
            selected = currentRoute == ProjectScreen.Files::class.qualifiedName,
            onClick = { onNavigate(ProjectScreen.Files) },
            icon = { Icon(Icons.Filled.Folder, contentDescription = "Files") },
            label = { Text("Files") }
        )
        
        NavigationBarItem(
            selected = currentRoute == ProjectScreen.Settings::class.qualifiedName,
            onClick = { onNavigate(ProjectScreen.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
```

### Theme System

**Purpose**: Implements Material Design 3 theme system with dynamic color support, dark/light mode switching, and custom color schemes. Provides consistent visual styling across the app with proper elevation, shapes, and typography.

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Custom colors for Pocket Agent branding
object PocketAgentColors {
    val PrimaryGreenLight = Color(0xFF00C853)
    val PrimaryGreenDark = Color(0xFF00E676)
    val ErrorRed = Color(0xFFD32F2F)
    val SuccessGreen = Color(0xFF4CAF50)
    val WarningOrange = Color(0xFFFF6D00)
    val SurfaceLight = Color(0xFFF5F5F5)
    val SurfaceDark = Color(0xFF121212)
}

// Custom typography
val PocketAgentFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val PocketAgentTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PocketAgentFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

// Theme configuration
data class ThemeConfig(
    val useDynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

// Theme state management
@Composable
fun rememberThemeConfig(): MutableState<ThemeConfig> {
    return remember { mutableStateOf(ThemeConfig()) }
}

@Composable
fun PocketAgentTheme(
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeConfig.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = when {
        themeConfig.useDynamicColor && android.os.Build.VERSION.SDK_INT >= 31 -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = PocketAgentColors.PrimaryGreenDark,
            onPrimary = Color.Black,
            primaryContainer = PocketAgentColors.PrimaryGreenDark.copy(alpha = 0.12f),
            secondary = Color(0xFF03DAC6),
            surface = PocketAgentColors.SurfaceDark,
            background = Color.Black,
            error = Color(0xFFFF5252),
            onSurface = Color.White,
            onBackground = Color.White
        )
        else -> lightColorScheme(
            primary = PocketAgentColors.PrimaryGreenLight,
            onPrimary = Color.White,
            primaryContainer = PocketAgentColors.PrimaryGreenLight.copy(alpha = 0.12f),
            secondary = Color(0xFF018786),
            surface = PocketAgentColors.SurfaceLight,
            background = Color.White,
            error = PocketAgentColors.ErrorRed,
            onSurface = Color.Black,
            onBackground = Color.Black
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketAgentTypography,
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

// Extension functions for semantic colors
@Composable
fun MaterialTheme.successColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF4CAF50) else Color(0xFF2E7D32)
}

@Composable
fun MaterialTheme.warningColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFFFF6D00) else Color(0xFFE65100)
}

@Composable
fun MaterialTheme.codeBackgroundColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
}
```

### Base UI Components

**Purpose**: Provides a library of reusable UI components that maintain consistent styling and behavior throughout the app. These components serve as building blocks for feature-specific screens and ensure design consistency.

```kotlin
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant

// Primary action button with loading state
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text)
                }
            }
        }
    }
}

// Status indicator chip
@Composable
fun StatusChip(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> Triple(
            MaterialTheme.successColor().copy(alpha = 0.12f),
            MaterialTheme.successColor(),
            "Connected"
        )
        ConnectionStatus.CONNECTING -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            "Connecting"
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Disconnected"
        )
        ConnectionStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error,
            "Error"
        )
    }
    
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

// Project card for list view
@Composable
fun ProjectCard(
    project: Project,
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    onQuickAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.serverProfile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = project.projectPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusChip(status = connectionStatus)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last active: ${project.lastActive.toRelativeTimeString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(onClick = onQuickAction) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Quick Connect",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Loading overlay
@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String = "Loading...",
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Dialog(
            onDismissRequest = { onDismiss?.invoke() },
            properties = DialogProperties(
                dismissOnBackPress = onDismiss != null,
                dismissOnClickOutside = onDismiss != null
            )
        ) {
            Card(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// Empty state component
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

// Error banner
@Composable
fun ErrorBanner(
    error: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                onRetry?.let {
                    TextButton(
                        onClick = it,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Retry")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss"
                    )
                }
            }
        }
    }
}
```

### Screen Scaffolding

**Purpose**: Provides consistent screen layout patterns with proper scaffold structure, app bars, navigation handling, and state management. Ensures all screens follow the same structural patterns for consistency.

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Base screen composable with common structure
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseScreen(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable PaddingValues.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    onNavigateBack?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back"
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        content(paddingValues)
    }
}

// Screen with loading and error states
@Composable
fun <T> StatefulScreen(
    title: String,
    viewModel: BaseViewModel<T>,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    onRetry: () -> Unit = { viewModel.retry() },
    content: @Composable (data: T) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    BaseScreen(
        title = title,
        onNavigateBack = onNavigateBack,
        actions = actions
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    LoadingState()
                }
                is UiState.Success -> {
                    content(state.data)
                }
                is UiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = onRetry
                    )
                }
                is UiState.Empty -> {
                    EmptyState(
                        icon = Icons.Default.Inbox,
                        title = "No data",
                        description = "There's nothing to show here yet"
                    )
                }
            }
        }
    }
}

// Loading state component
@Composable
fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// Error state component
@Composable
fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

// Common screen event handling
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    onEvent: (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(onEvent)
        }
    }
}

// Responsive layout helper
@Composable
fun ResponsiveLayout(
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isTablet = maxWidth > 600.dp
        val contentPadding = if (isTablet) {
            PaddingValues(horizontal = 64.dp)
        } else {
            PaddingValues(horizontal = 16.dp)
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}
```

### State Management

**Purpose**: Implements centralized UI state management using ViewModels and StateFlow, with proper error handling, loading states, and data persistence across configuration changes.

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Base UI state
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}

// Base view model with common functionality
abstract class BaseViewModel<T> : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<T>>(UiState.Loading)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    
    protected fun setState(state: UiState<T>) {
        _uiState.value = state
    }
    
    protected fun emitEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
    
    protected fun <R> Flow<R>.executeWithState(
        onSuccess: (R) -> T,
        onError: (Throwable) -> String = { it.message ?: "Unknown error" },
        context: CoroutineContext = EmptyCoroutineContext
    ) {
        viewModelScope.launch(context) {
            this@executeWithState
                .onStart { setState(UiState.Loading) }
                .catch { throwable ->
                    setState(UiState.Error(onError(throwable), throwable))
                }
                .collect { result ->
                    setState(UiState.Success(onSuccess(result)))
                }
        }
    }
    
    abstract fun retry()
}

// UI events
sealed class UiEvent {
    data class ShowSnackbar(val message: String, val action: SnackbarAction? = null) : UiEvent()
    data class Navigate(val destination: Screen) : UiEvent()
    object NavigateBack : UiEvent()
}

data class SnackbarAction(
    val label: String,
    val action: () -> Unit
)

// State helpers
fun <T> UiState<T>.getDataOrNull(): T? = (this as? UiState.Success)?.data

fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

fun <T> UiState<T>.isEmpty(): Boolean = this is UiState.Empty

// State transformations
fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Loading -> UiState.Loading
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> UiState.Error(message, cause)
    is UiState.Empty -> UiState.Empty
}

// Combine multiple states
fun <T1, T2, R> combineStates(
    state1: UiState<T1>,
    state2: UiState<T2>,
    transform: (T1, T2) -> R
): UiState<R> = when {
    state1 is UiState.Loading || state2 is UiState.Loading -> UiState.Loading
    state1 is UiState.Error -> UiState.Error(state1.message, state1.cause)
    state2 is UiState.Error -> UiState.Error(state2.message, state2.cause)
    state1 is UiState.Empty || state2 is UiState.Empty -> UiState.Empty
    state1 is UiState.Success && state2 is UiState.Success -> {
        UiState.Success(transform(state1.data, state2.data))
    }
    else -> UiState.Loading
}

// Example view model implementation
@HiltViewModel
class ProjectsListViewModel @Inject constructor(
    private val secureDataRepository: SecureDataRepository,
    private val connectionManager: ConnectionManager
) : BaseViewModel<ProjectsListState>() {
    
    init {
        loadProjects()
    }
    
    override fun retry() {
        loadProjects()
    }
    
    private fun loadProjects() {
        combine(
            secureDataRepository.getAllProjects(),
            connectionManager.connectionStates
        ) { projects, connectionStates ->
            ProjectsListState(
                projects = projects.map { project ->
                    ProjectWithStatus(
                        project = project,
                        connectionStatus = connectionStates[project.id] ?: ConnectionStatus.DISCONNECTED
                    )
                }
            )
        }.executeWithState(
            onSuccess = { it },
            onError = { "Failed to load projects: ${it.message}" }
        )
    }
    
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
                .onSuccess {
                    emitEvent(UiEvent.ShowSnackbar("Project deleted"))
                }
                .onFailure { error ->
                    emitEvent(UiEvent.ShowSnackbar("Failed to delete project"))
                }
        }
    }
}

data class ProjectsListState(
    val projects: List<ProjectWithStatus>
)

data class ProjectWithStatus(
    val project: Project,
    val connectionStatus: ConnectionStatus
)
```

### Error Handling

**Purpose**: Implements comprehensive error handling throughout the UI layer, including network errors, validation errors, and unexpected exceptions. Provides user-friendly error messages and recovery options.

```kotlin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// UI-specific exceptions
sealed class UiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ValidationException(message: String) : UiException(message)
    class NavigationException(message: String) : UiException(message)
    class PermissionException(message: String) : UiException(message)
}

// Error message provider
object ErrorMessageProvider {
    fun getMessage(throwable: Throwable, context: android.content.Context): String {
        return when (throwable) {
            is CancellationException -> "Operation cancelled"
            is TimeoutCancellationException -> "Operation timed out"
            is UnknownHostException -> "No internet connection"
            is IOException -> "Network error occurred"
            is SSLException -> "Secure connection failed"
            is SecurityException -> "Permission denied"
            is IllegalArgumentException -> "Invalid input: ${throwable.message}"
            is IllegalStateException -> "Invalid operation: ${throwable.message}"
            is UiException.ValidationException -> throwable.message ?: "Validation failed"
            is UiException.NavigationException -> "Navigation error"
            is UiException.PermissionException -> "Permission required: ${throwable.message}"
            else -> throwable.message ?: "An unexpected error occurred"
        }
    }
}

// Global error handler composable
@Composable
fun GlobalErrorHandler(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val errorHandler = remember {
        Thread.UncaughtExceptionHandler { _, throwable ->
            coroutineScope.launch {
                val message = ErrorMessageProvider.getMessage(throwable, context)
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Details",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }
    
    DisposableEffect(errorHandler) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(errorHandler)
        
        onDispose {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        content()
    }
}

// Error boundary composable
@Composable
fun ErrorBoundary(
    fallback: @Composable (error: Throwable, retry: () -> Unit) -> Unit = { error, retry ->
        ErrorState(
            error = error.message ?: "Unknown error",
            onRetry = retry
        )
    },
    content: @Composable () -> Unit
) {
    var error by remember { mutableStateOf<Throwable?>(null) }
    var key by remember { mutableStateOf(0) }
    
    if (error != null) {
        fallback(error!!) {
            error = null
            key++
        }
    } else {
        key(key) {
            try {
                content()
            } catch (e: Throwable) {
                error = e
            }
        }
    }
}

// Time formatting extension
fun Instant.toRelativeTimeString(): String {
    val now = Instant.now()
    val duration = java.time.Duration.between(this, now)
    
    return when {
        duration.seconds < 60 -> "Just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(
            this.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        )
    }
}

// Retry policy
class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Long = 1000L,
    val maxDelay: Long = 10000L,
    val factor: Double = 2.0
) {
    fun calculateDelay(attempt: Int): Long {
        val delay = initialDelay * factor.pow(attempt - 1).toLong()
        return delay.coerceAtMost(maxDelay)
    }
    
    fun shouldRetry(attempt: Int, error: Throwable): Boolean {
        return attempt < maxAttempts && error !is CancellationException
    }
}

// Extension function for error handling in ViewModels
suspend fun <T> retryWithPolicy(
    policy: RetryPolicy = RetryPolicy(),
    operation: suspend () -> T
): Result<T> {
    var lastError: Throwable? = null
    
    repeat(policy.maxAttempts) { attempt ->
        try {
            return Result.success(operation())
        } catch (e: Throwable) {
            lastError = e
            
            if (!policy.shouldRetry(attempt + 1, e)) {
                return Result.failure(e)
            }
            
            if (attempt < policy.maxAttempts - 1) {
                kotlinx.coroutines.delay(policy.calculateDelay(attempt + 1))
            }
        }
    }
    
    return Result.failure(lastError ?: Exception("Unknown error"))
}
```

### Integration Points

**Purpose**: Defines how the UI Navigation & Foundation feature integrates with the rest of the application through dependency injection, providing all necessary UI components and navigation infrastructure to other features.

```kotlin
import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

// Navigation module
@Module
@InstallIn(ActivityComponent::class)
object NavigationModule {
    
    @Provides
    @ActivityScoped
    fun provideNavController(): NavHostController {
        return NavHostController(LocalContext.current)
    }
}

// Theme module
@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    
    @Provides
    @Singleton
    fun provideThemeConfig(
        preferencesManager: PreferencesManager
    ): ThemeConfig {
        return ThemeConfig(
            useDynamicColor = preferencesManager.useDynamicColors,
            themeMode = preferencesManager.themeMode
        )
    }
}

// UI components module
@Module
@InstallIn(SingletonComponent::class)
abstract class UiComponentsModule {
    
    @Binds
    abstract fun bindErrorHandler(
        implementation: DefaultErrorHandler
    ): ErrorHandler
    
    @Binds
    abstract fun bindSnackbarManager(
        implementation: SnackbarManagerImpl
    ): SnackbarManager
}

// Composition locals for UI context
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

val LocalThemeConfig = staticCompositionLocalOf<ThemeConfig> {
    error("No ThemeConfig provided")
}

// Root composition provider
@Composable
fun PocketAgentApp(
    navController: NavHostController,
    themeConfig: ThemeConfig
) {
    val snackbarHostState = remember { SnackbarHostState() }
    
    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalSnackbarHostState provides snackbarHostState,
        LocalThemeConfig provides themeConfig
    ) {
        PocketAgentTheme(themeConfig = themeConfig) {
            GlobalErrorHandler {
                AppNavigation(navController = navController)
            }
        }
    }
}

// Navigation extensions for features
fun NavHostController.navigateToProject(projectId: String) {
    navigate(Screen.ProjectDetail(projectId))
}

fun NavHostController.navigateToChat(projectId: String) {
    navigate(Screen.ProjectDetail(projectId)) {
        graph.findStartDestination()?.id?.let { startId ->
            popUpTo(startId) {
                saveState = true
            }
        }
    }
    // Navigate to chat tab within project
    currentBackStackEntry?.savedStateHandle?.set("selected_tab", ProjectScreen.Chat)
}

// Theme extensions for features
@Composable
fun rememberThemeMode(): ThemeMode {
    return LocalThemeConfig.current.themeMode
}

@Composable
fun updateThemeMode(mode: ThemeMode) {
    val config = LocalThemeConfig.current
    // Update theme through preferences
}

// Default error handler implementation
class DefaultErrorHandler @Inject constructor(
    private val context: Context
) : ErrorHandler {
    override fun handleError(throwable: Throwable) {
        val message = ErrorMessageProvider.getMessage(throwable, context)
        // Log error and show user-friendly message
    }
}

interface ErrorHandler {
    fun handleError(throwable: Throwable)
}

// Snackbar manager implementation
class SnackbarManagerImpl : SnackbarManager {
    override suspend fun showSnackbar(
        message: String,
        actionLabel: String?,
        duration: SnackbarDuration
    ): SnackbarResult {
        // Implementation
        return SnackbarResult.Dismissed
    }
}

interface SnackbarManager {
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short
    ): SnackbarResult
}
```

### Screen Implementations

**Purpose**: Basic screen implementations that serve as the foundation for feature-specific development. These provide the necessary structure and navigation hooks for each major screen in the application.

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle

// Welcome Screen
@Composable
fun WelcomeScreen(
    onNavigateToProjects: () -> Unit
) {
    BaseScreen(
        title = ""
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_pocket_agent_logo),
                contentDescription = "Pocket Agent Logo",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Welcome to Pocket Agent",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Control Claude Code from your mobile device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            PrimaryActionButton(
                text = "Get Started",
                onClick = onNavigateToProjects,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Projects List Screen
@Composable
fun ProjectsListScreen(
    onNavigateToProject: (String) -> Unit,
    onNavigateToCreateProject: () -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToSshIdentities: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ProjectsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    BaseScreen(
        title = "Projects",
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateProject
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new project")
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Loading -> LoadingState()
            is UiState.Empty -> EmptyState(
                icon = Icons.Default.FolderOpen,
                title = "No projects yet",
                description = "Create your first project to get started",
                actionText = "Create Project",
                onAction = onNavigateToCreateProject
            )
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.data.projects) { projectWithStatus ->
                        ProjectCard(
                            project = projectWithStatus.project,
                            connectionStatus = projectWithStatus.connectionStatus,
                            onClick = { onNavigateToProject(projectWithStatus.project.id) },
                            onQuickAction = { 
                                viewModel.quickConnect(projectWithStatus.project.id)
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedCard(
                            onClick = onNavigateToServers,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Manage Servers")
                            }
                        }
                    }
                    
                    item {
                        OutlinedCard(
                            onClick = onNavigateToSshIdentities,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Manage SSH Keys")
                            }
                        }
                    }
                }
            }
            is UiState.Error -> ErrorState(
                error = state.message,
                onRetry = { viewModel.retry() }
            )
        }
    }
}

// Stub screens for navigation targets
@Composable
fun ProjectCreationScreen(
    serverId: String?,
    savedStateHandle: SavedStateHandle,
    onNavigateBack: () -> Unit,
    onNavigateToServers: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    BaseScreen(
        title = "Create Project",
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        // Implementation will be in project management feature
        Text("Project Creation Wizard")
    }
}

@Composable
fun ServerManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSshIdentities: () -> Unit
) {
    BaseScreen(
        title = "Servers",
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        // Implementation will be in server management feature
        Text("Server Management")
    }
}

@Composable
fun SshIdentityManagementScreen(
    onNavigateBack: () -> Unit
) {
    BaseScreen(
        title = "SSH Keys",
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        // Implementation will be in security feature
        Text("SSH Key Management")
    }
}

@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit
) {
    BaseScreen(
        title = "Settings",
        onNavigateBack = onNavigateBack
    ) { paddingValues ->
        // Implementation will include theme settings
        Text("App Settings")
    }
}

// Project-level screens
@Composable
fun DashboardScreen(
    projectId: String
) {
    // Implementation will be in dashboard feature
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Dashboard for project: $projectId")
    }
}

@Composable
fun ChatScreen(
    projectId: String
) {
    // Implementation will be in chat feature
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Chat for project: $projectId")
    }
}

@Composable
fun FilesScreen(
    projectId: String
) {
    // Implementation will be in files feature
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Files for project: $projectId")
    }
}

@Composable
fun ProjectSettingsScreen(
    projectId: String,
    onNavigateToMainSettings: () -> Unit
) {
    // Implementation will be in project settings feature
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings for project: $projectId")
    }
}
```

### Wrapper Installation Dialog

**Purpose**: Dialog component for guiding users through wrapper service installation when connecting to a server that doesn't have the wrapper installed or has an outdated version.

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun WrapperInstallationDialog(
    serverName: String,
    currentVersion: String? = null,
    requiredVersion: String,
    installCommand: String = "curl -sSL https://install.claude-wrapper.dev | sh",
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (currentVersion == null) {
                        "Wrapper Installation Required"
                    } else {
                        "Wrapper Update Available"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (currentVersion == null) {
                        "The Claude Code wrapper service is not installed on $serverName."
                    } else {
                        "The wrapper on $serverName needs to be updated from v$currentVersion to v$requiredVersion."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Installation command:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = installCommand,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "The app will run this command automatically when you click Install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onInstall) {
                        Text(if (currentVersion == null) "Install" else "Update")
                    }
                }
            }
        }
    }
}

// Wrapper installation progress dialog
@Composable
fun WrapperInstallationProgressDialog(
    progress: WrapperInstallationProgress,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { 
            if (progress is WrapperInstallationProgress.Completed) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (progress) {
                    is WrapperInstallationProgress.Installing -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Installing wrapper service...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress.percentage / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is WrapperInstallationProgress.Completed -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.successColor()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Installation completed!",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("Continue")
                        }
                    }
                    is WrapperInstallationProgress.Error -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Installation failed",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
                
                if (progress is WrapperInstallationProgress.Installing && progress.output.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        color = MaterialTheme.codeBackgroundColor(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = progress.output,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class WrapperInstallationProgress {
    data class Installing(val percentage: Int, val output: String) : WrapperInstallationProgress()
    object Completed : WrapperInstallationProgress()
    data class Error(val message: String) : WrapperInstallationProgress()
}
```

### Theme Persistence

**Purpose**: DataStore implementation for persisting theme preferences across app restarts, ensuring user's theme choice is maintained.

```kotlin
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

@Singleton
class ThemePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
    
    val themeConfigFlow: Flow<ThemeConfig> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ThemeConfig(
                themeMode = ThemeMode.valueOf(
                    preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
                ),
                useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true
            )
        }
    
    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
    
    suspend fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }
}

// Updated PreferencesManager interface
interface PreferencesManager {
    val themeMode: ThemeMode
    val useDynamicColors: Boolean
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUseDynamicColors(enabled: Boolean)
}

@Singleton
class PreferencesManagerImpl @Inject constructor(
    private val themePreferencesDataStore: ThemePreferencesDataStore
) : PreferencesManager {
    
    private var cachedThemeMode: ThemeMode = ThemeMode.SYSTEM
    private var cachedUseDynamicColors: Boolean = true
    
    init {
        // Initialize cached values from DataStore
        // This would be done in a coroutine scope in actual implementation
    }
    
    override val themeMode: ThemeMode
        get() = cachedThemeMode
    
    override val useDynamicColors: Boolean
        get() = cachedUseDynamicColors
    
    override suspend fun setThemeMode(mode: ThemeMode) {
        cachedThemeMode = mode
        themePreferencesDataStore.updateThemeMode(mode)
    }
    
    override suspend fun setUseDynamicColors(enabled: Boolean) {
        cachedUseDynamicColors = enabled
        themePreferencesDataStore.updateDynamicColorPreference(enabled)
    }
}
```

### Accessibility Components

**Purpose**: Enhanced accessibility support including screen reader announcements, focus management, and keyboard navigation helpers to ensure the app is fully accessible to all users.

```kotlin
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

// Screen reader announcement helper
@Composable
fun ScreenReaderAnnouncement(
    message: String,
    polite: Boolean = true
) {
    val context = LocalContext.current
    
    LaunchedEffect(message) {
        context.announceForAccessibility(message, polite)
    }
}

fun Context.announceForAccessibility(
    message: String,
    polite: Boolean = true
) {
    val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (accessibilityManager.isEnabled) {
        val event = AccessibilityEvent.obtain().apply {
            eventType = if (polite) {
                AccessibilityEvent.TYPE_ANNOUNCEMENT
            } else {
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            }
            className = Context::class.java.name
            packageName = packageName
            text.add(message)
        }
        accessibilityManager.sendAccessibilityEvent(event)
    }
}

// Focus management for keyboard navigation
@Composable
fun FocusableGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .semantics {
                role = Role.RadioButton
                focused = true
            }
    ) {
        content()
    }
}

// Keyboard navigation helper
@Composable
fun KeyboardNavigationHandler(
    onEscape: () -> Unit = {},
    onEnter: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.onKeyEvent { keyEvent ->
            when (keyEvent.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_ESCAPE -> {
                    onEscape()
                    true
                }
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    onEnter()
                    true
                }
                else -> false
            }
        }
    ) {
        content()
    }
}

// Accessibility-enhanced list item
@Composable
fun AccessibleListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = buildString {
                append(title)
                subtitle?.let { append(", $it") }
            }
            role = Role.Button
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            leadingContent?.invoke()
            
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            trailingContent?.invoke()
        }
    }
}

// Live region for dynamic content updates
@Composable
fun LiveRegion(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        }
    ) {
        Text(text = text)
    }
}

// Skip to content button for screen readers
@Composable
fun SkipToContentButton(
    onSkip: () -> Unit
) {
    TextButton(
        onClick = onSkip,
        modifier = Modifier
            .semantics {
                contentDescription = "Skip to main content"
            }
            .focusable()
    ) {
        Text("Skip to content")
    }
}
```

### Deep Link Handler

**Purpose**: Implements deep link handling for navigating directly to specific screens from external sources like notifications or share intents.

```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController

@Composable
fun HandleDeepLinks(
    navController: NavHostController
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        // Handle initial deep link from intent
        val intent = (context as? androidx.activity.ComponentActivity)?.intent
        intent?.let { handleIntent(it, navController) }
    }
    
    // Handle new intents while app is running
    DisposableEffect(context) {
        val activity = context as? androidx.activity.ComponentActivity
        val callback = { intent: Intent ->
            handleIntent(intent, navController)
        }
        
        activity?.addOnNewIntentListener(callback)
        
        onDispose {
            activity?.removeOnNewIntentListener(callback)
        }
    }
}

private fun handleIntent(
    intent: Intent,
    navController: NavHostController
) {
    when (intent.action) {
        Intent.ACTION_VIEW -> {
            intent.data?.let { uri ->
                handleDeepLink(uri, navController)
            }
        }
        "com.pocketagent.ACTION_OPEN_PROJECT" -> {
            val projectId = intent.getStringExtra("project_id")
            projectId?.let {
                navController.navigate(Screen.ProjectDetail(it)) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                    }
                }
            }
        }
        "com.pocketagent.ACTION_OPEN_CHAT" -> {
            val projectId = intent.getStringExtra("project_id")
            projectId?.let {
                navController.navigateToChat(it)
            }
        }
    }
}

private fun handleDeepLink(
    uri: Uri,
    navController: NavHostController
) {
    when (uri.host) {
        "project" -> {
            val projectId = uri.pathSegments.firstOrNull()
            projectId?.let {
                navController.navigate(Screen.ProjectDetail(it)) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                    }
                }
            }
        }
        "create-project" -> {
            val serverId = uri.getQueryParameter("serverId")
            navController.navigate(Screen.ProjectCreation(serverId))
        }
        "settings" -> {
            navController.navigate(Screen.AppSettings)
        }
    }
}

// Extension function for ComponentActivity
fun androidx.activity.ComponentActivity.addOnNewIntentListener(
    listener: (Intent) -> Unit
) {
    // Implementation would use activity callbacks
}

fun androidx.activity.ComponentActivity.removeOnNewIntentListener(
    listener: (Intent) -> Unit
) {
    // Implementation would remove activity callbacks
}
```

### Repository Interfaces

**Purpose**: Define the repository interfaces referenced in the navigation components, providing the contract for data access that will be implemented by the data layer.

```kotlin
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Connection manager interface
interface ConnectionManager {
    val connectionStates: Flow<Map<String, ConnectionStatus>>
    suspend fun connect(projectId: String): Result<Unit>
    suspend fun disconnect(projectId: String): Result<Unit>
    suspend fun shutdown(projectId: String): Result<Unit>
    fun getConnectionStatus(projectId: String): ConnectionStatus
}

// Navigation manager interface
@Singleton
class NavigationManager @Inject constructor() {
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()
    
    fun navigateToProjects() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateToProjects)
    }
    
    fun navigateToProject(projectId: String) {
        _navigationEvents.tryEmit(NavigationEvent.NavigateToProject(projectId))
    }
    
    fun navigateBack() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateBack)
    }
    
    fun navigateToHome() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateToHome)
    }
    
    fun navigateToHelp() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateToHelp)
    }
    
    fun navigateToSettings() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateToSettings)
    }
    
    sealed class NavigationEvent {
        object NavigateToProjects : NavigationEvent()
        data class NavigateToProject(val projectId: String) : NavigationEvent()
        object NavigateBack : NavigationEvent()
        object NavigateToHome : NavigationEvent()
        object NavigateToHelp : NavigationEvent()
        object NavigateToSettings : NavigationEvent()
    }
}

// View model implementation
@HiltViewModel
class ProjectsListViewModel @Inject constructor(
    private val secureDataRepository: SecureDataRepository,
    private val connectionManager: ConnectionManager
) : BaseViewModel<ProjectsListState>() {
    
    init {
        loadProjects()
    }
    
    override fun retry() {
        loadProjects()
    }
    
    private fun loadProjects() {
        combine(
            secureDataRepository.getAllProjects(),
            connectionManager.connectionStates
        ) { projects, connectionStates ->
            ProjectsListState(
                projects = projects.map { project ->
                    ProjectWithStatus(
                        project = project,
                        connectionStatus = connectionStates[project.id] ?: ConnectionStatus.DISCONNECTED
                    )
                }
            )
        }.executeWithState(
            onSuccess = { it },
            onError = { "Failed to load projects: ${it.message}" }
        )
    }
    
    fun quickConnect(projectId: String) {
        viewModelScope.launch {
            connectionManager.connect(projectId)
                .onSuccess {
                    emitEvent(UiEvent.Navigate(Screen.ProjectDetail(projectId)))
                }
                .onFailure { error ->
                    emitEvent(
                        UiEvent.ShowSnackbar(
                            message = "Failed to connect: ${error.message}",
                            action = SnackbarAction("Retry") {
                                quickConnect(projectId)
                            }
                        )
                    )
                }
        }
    }
    
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
                .onSuccess {
                    emitEvent(UiEvent.ShowSnackbar("Project deleted"))
                }
                .onFailure { error ->
                    emitEvent(UiEvent.ShowSnackbar("Failed to delete project"))
                }
        }
    }
}

// Helper classes
data class ProjectsListState(
    val projects: List<ProjectWithStatus>
)

data class ProjectWithStatus(
    val project: Project,
    val connectionStatus: ConnectionStatus
)

// Project detail view model
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val secureDataRepository: SecureDataRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {
    
    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""
    
    val project = secureDataRepository.getProject(projectId)
    val connectionStatus = connectionManager.connectionStates.map { states ->
        states[projectId] ?: ConnectionStatus.DISCONNECTED
    }
}
```

## Testing

### Testing Checklist

**Purpose**: Comprehensive testing checklist to ensure all aspects of the UI Navigation & Foundation feature are properly tested, including navigation flows, theme switching, component behavior, and accessibility.

```kotlin
/**
 * UI Navigation & Foundation Testing Checklist:
 * 
 * Navigation Tests:
 * 1. [ ] Test navigation between all top-level screens
 * 2. [ ] Test project-level tab navigation
 * 3. [ ] Test deep linking to specific screens
 * 4. [ ] Test back navigation and up navigation
 * 5. [ ] Test navigation state restoration after process death
 * 6. [ ] Test navigation with arguments passing
 * 7. [ ] Test navigation animations and transitions
 * 
 * Theme Tests:
 * 8. [ ] Test light/dark theme switching
 * 9. [ ] Test dynamic color support on Android 12+
 * 10. [ ] Test theme persistence across app restarts
 * 11. [ ] Test custom color application
 * 
 * Component Tests:
 * 12. [ ] Test all base UI components in isolation
 * 13. [ ] Test component states (enabled, disabled, loading)
 * 14. [ ] Test component interactions and callbacks
 * 15. [ ] Test component accessibility labels
 * 
 * Screen Tests:
 * 16. [ ] Test screen scaffolding structure
 * 17. [ ] Test screen state management (loading, error, success)
 * 18. [ ] Test screen orientation changes
 * 19. [ ] Test screen keyboard handling
 * 
 * Integration Tests:
 * 20. [ ] Test navigation with real data
 * 21. [ ] Test theme changes with all screens
 * 22. [ ] Test error handling across navigation
 * 23. [ ] Test memory leaks in navigation
 * 
 * Accessibility Tests:
 * 24. [ ] Test with TalkBack enabled
 * 25. [ ] Test with large text sizes
 * 26. [ ] Test with reduced animations
 * 27. [ ] Test keyboard navigation
 * 
 * Performance Tests:
 * 28. [ ] Test navigation performance with many screens
 * 29. [ ] Test theme switching performance
 * 30. [ ] Test component rendering performance
 */
```

### Unit Tests

**Purpose**: Example unit tests for navigation logic, theme configuration, and UI state management, demonstrating proper testing patterns for Compose UI components.

```kotlin
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assert

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navController: NavHostController
    
    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = rememberNavController()
            AppNavigation(navController = navController)
        }
    }
    
    @Test
    fun navigateToProjectsList_fromWelcome() {
        // Given: On welcome screen
        composeTestRule.onNodeWithText("Welcome to Pocket Agent").assertIsDisplayed()
        
        // When: Click get started
        composeTestRule.onNodeWithText("Get Started").performClick()
        
        // Then: Navigate to projects list
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
        assert(navController.currentDestination?.route == Screen.ProjectsList::class.qualifiedName)
    }
    
    @Test
    fun navigateToProjectDetail_withTabs() {
        // Given: Navigate to project detail
        navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // Then: Bottom navigation is displayed
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
    
    @Test
    fun switchBetweenProjectTabs() {
        // Given: In project detail screen
        navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // When: Click chat tab
        composeTestRule.onNodeWithText("Chat").performClick()
        
        // Then: Chat screen is displayed
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // When: Click files tab
        composeTestRule.onNodeWithText("Files").performClick()
        
        // Then: Files screen is displayed
        composeTestRule.onNodeWithContentDescription("File browser").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun themeSwitching_lightToDark() {
        var themeConfig = ThemeConfig(themeMode = ThemeMode.LIGHT)
        
        composeTestRule.setContent {
            PocketAgentTheme(themeConfig = themeConfig) {
                BaseScreen(title = "Test") {
                    // Content
                }
            }
        }
        
        // Verify light theme colors
        // ... color assertions
        
        // Switch to dark theme
        themeConfig = ThemeConfig(themeMode = ThemeMode.DARK)
        
        composeTestRule.setContent {
            PocketAgentTheme(themeConfig = themeConfig) {
                BaseScreen(title = "Test") {
                    // Content
                }
            }
        }
        
        // Verify dark theme colors
        // ... color assertions
    }
}

@RunWith(AndroidJUnit4::class)
class ComponentTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun primaryActionButton_states() {
        var clicked = false
        
        composeTestRule.setContent {
            PrimaryActionButton(
                text = "Action",
                onClick = { clicked = true },
                loading = false
            )
        }
        
        // Test enabled state
        composeTestRule.onNodeWithText("Action").assertIsEnabled()
        composeTestRule.onNodeWithText("Action").performClick()
        assert(clicked)
        
        // Test loading state
        composeTestRule.setContent {
            PrimaryActionButton(
                text = "Action",
                onClick = { },
                loading = true
            )
        }
        
        composeTestRule.onNode(hasProgressBarRangeInfo()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertDoesNotExist()
    }
    
    @Test
    fun statusChip_displaysCorrectly() {
        composeTestRule.setContent {
            Column {
                StatusChip(status = ConnectionStatus.CONNECTED)
                StatusChip(status = ConnectionStatus.CONNECTING)
                StatusChip(status = ConnectionStatus.DISCONNECTED)
                StatusChip(status = ConnectionStatus.ERROR)
            }
        }
        
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connecting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    }
}
```

### Integration Tests

**Purpose**: Integration tests demonstrating full navigation flows, theme persistence, and component interaction with real ViewModels and navigation controllers.

```kotlin
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun fullNavigationFlow_fromWelcomeToProject() {
        // Start at welcome screen
        composeRule.onNodeWithText("Welcome to Pocket Agent").assertIsDisplayed()
        
        // Navigate to projects
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        
        // Create new project
        composeRule.onNodeWithContentDescription("Create new project").performClick()
        composeRule.waitForIdle()
        
        // Fill project details
        composeRule.onNodeWithText("Project Name").performTextInput("Test Project")
        composeRule.onNodeWithText("Next").performClick()
        
        // Select server
        composeRule.onNodeWithText("Select Server").performClick()
        composeRule.onNodeWithText("Development Server").performClick()
        
        // Complete creation
        composeRule.onNodeWithText("Create Project").performClick()
        composeRule.waitForIdle()
        
        // Verify navigation to project detail
        composeRule.onNodeWithText("Test Project").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
    }
    
    @Test
    fun navigationStatePersistence_acrossConfigChange() {
        // Navigate to specific screen
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        
        // Navigate to a project
        composeRule.onNodeWithText("Sample Project").performClick()
        composeRule.waitForIdle()
        
        // Switch to chat tab
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.waitForIdle()
        
        // Rotate device
        composeRule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        
        // Verify still on chat tab
        composeRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // Rotate back
        composeRule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()
        
        // Verify still on chat tab
        composeRule.onNodeWithContentDescription("Chat input field").assertExists()
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ThemeIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun themePersistence_acrossAppRestart() {
        // Navigate to settings
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        
        // Change to dark theme
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.waitForIdle()
        
        // Restart activity
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        
        // Verify dark theme is still applied
        // This would require checking actual theme values
        // which would be done through custom assertions
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccessibilityIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun navigation_withTalkBackEnabled() {
        // Enable accessibility services in test
        composeRule.onRoot().performTouchInput {
            // Simulate TalkBack gestures
        }
        
        // Verify all content has proper descriptions
        composeRule.onAllNodes(hasContentDescription()).assertCountEquals(expectedAccessibleElements)
        
        // Test navigation with keyboard
        // ... keyboard navigation tests
    }
}
```

## Implementation Notes (Android Mobile)

### Critical Implementation Details

#### Navigation State Preservation
- Save navigation state in SavedStateHandle for process death recovery
- Use rememberSaveable for transient UI state within screens
- Implement proper deep link handling with pending intents
- Handle navigation during configuration changes gracefully

#### Theme Implementation
- Store theme preference in DataStore for persistence
- Apply theme before setContent to avoid flashing
- Handle dynamic color gracefully on older devices
- Ensure theme changes propagate to all composed content

#### Gesture Navigation
- Support Android 10+ gesture navigation properly
- Handle edge-to-edge display with proper insets
- Implement predictive back gesture for Android 14+
- Ensure bottom navigation doesn't interfere with system gestures

### Performance Considerations (Android-Specific)

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

### Package Structure

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

### Future Extensions (Android Mobile Focus)

- **Tablet Support**: Implement responsive layouts with master-detail navigation
- **Foldable Support**: Handle folding states and dual-screen layouts
- **Widget Support**: Create home screen widgets for quick project access
- **Wear OS Companion**: Extend navigation to smartwatch companion app
- **Performance**: Implement baseline profiles for faster startup
- **Security**: Add app-specific biometric lock for navigation
- **Accessibility**: Enhanced screen reader navigation patterns
- **Platform Updates**: Prepare for Android 15 predictive back animations