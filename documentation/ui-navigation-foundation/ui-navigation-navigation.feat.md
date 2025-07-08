# UI Navigation & Foundation Feature Specification - Navigation Framework
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | **Navigation** | [Theme](./ui-navigation-theme.feat.md) | [Components](./ui-navigation-components.feat.md) | [Scaffolding](./ui-navigation-scaffolding.feat.md) | [State](./ui-navigation-state.feat.md) | [Testing](./ui-navigation-testing.feat.md) | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## Navigation Framework

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