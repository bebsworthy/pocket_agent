# UI Navigation & Foundation Feature Specification - Screen Scaffolding
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | [Theme](./ui-navigation-theme.feat.md) | [Components](./ui-navigation-components.feat.md) | **Scaffolding** | [State](./ui-navigation-state.feat.md) | [Testing](./ui-navigation-testing.feat.md) | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## Screen Scaffolding

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

## Screen Implementations

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

## Wrapper Installation Dialog

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