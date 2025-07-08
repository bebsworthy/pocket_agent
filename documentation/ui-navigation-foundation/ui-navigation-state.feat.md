# UI Navigation & Foundation Feature Specification - State Management
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | [Theme](./ui-navigation-theme.feat.md) | [Components](./ui-navigation-components.feat.md) | [Scaffolding](./ui-navigation-scaffolding.feat.md) | **State** | [Testing](./ui-navigation-testing.feat.md) | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## State Management

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

## Error Handling

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

## Deep Link Handler

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

## Repository Interfaces

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