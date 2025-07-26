package com.pocketagent.data.coroutines.examples

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketagent.data.coroutines.CoroutineDispatchers
import com.pocketagent.data.coroutines.CoroutineErrorHandler
import com.pocketagent.data.coroutines.CoroutineScopes
import com.pocketagent.data.coroutines.ErrorHandlingUtils
import com.pocketagent.data.coroutines.FlowConfiguration
import com.pocketagent.data.coroutines.ViewModelScopeExtensions.launchWithErrorHandling
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.di.qualifiers.ApplicationScope
import com.pocketagent.di.qualifiers.WebSocketScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ================================
// UI State definitions for examples
// ================================

/**
 * Example usage of coroutine configuration in different application layers.
 * These examples demonstrate best practices for using the coroutine framework.
 */

sealed class ProjectUiState {
    object Loading : ProjectUiState()

    data class Success(
        val projects: List<Project>,
    ) : ProjectUiState()

    data class Error(
        val message: String,
    ) : ProjectUiState()
}

sealed class ProjectEvent {
    data class ProjectsLoaded(
        val projects: List<Project>,
    ) : ProjectEvent()

    data class LoadingFailed(
        val error: Throwable,
    ) : ProjectEvent()

    data class CreationFailed(
        val error: Throwable,
    ) : ProjectEvent()

    data class ProjectCreated(
        val project: Project,
    ) : ProjectEvent()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()

    object Connecting : ConnectionState()

    object Connected : ConnectionState()

    object Disconnecting : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()
}

data class WebSocketMessage(
    val content: String,
)

// ================================
// Repository Layer Example
// ================================

/**
 * Example repository showing proper coroutine usage with error handling.
 */
@Singleton
class ProjectRepository
    @Inject
    constructor(
        private val dispatchers: CoroutineDispatchers,
        private val errorHandler: CoroutineErrorHandler,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val _projects = FlowConfiguration.createStateFlow<List<Project>>(emptyList())
        val projects = _projects.asStateFlow()

        /**
         * Loads projects with proper error handling and dispatcher usage.
         */
        suspend fun loadProjects(): Result<List<Project>> =
            withContext(dispatchers.io) {
                ErrorHandlingUtils.safeCall(
                    onError = { throwable ->
                        Result.failure(throwable)
                    },
                ) {
                    // Simulate network/database call
                    val projectList =
                        listOf(
                            Project(
                                id = "1",
                                name = "Sample Project",
                                serverProfileId = "server1",
                                projectPath = "/home/user/project",
                                scriptsFolder = "scripts",
                                claudeSessionId = null,
                                status = ProjectStatus.ACTIVE,
                                createdAt = System.currentTimeMillis(),
                                lastActiveAt = System.currentTimeMillis(),
                            ),
                        )

                    _projects.value = projectList
                    Result.success(projectList)
                }
            }

        /**
         * Observes project changes with flow optimizations.
         */
        fun observeProjects(): Flow<List<Project>> =
            projects
                .map { projectList ->
                    // Apply any transformations
                    projectList.sortedBy { it.name }
                }.catch { throwable ->
                    // Handle errors in the flow
                    emit(emptyList())
                }

        /**
         * Creates a new project with background processing.
         */
        fun createProject(project: Project) {
            applicationScope.launch {
                withContext(dispatchers.io) {
                    try {
                        // Simulate project creation
                        val updatedProjects = _projects.value + project
                        _projects.value = updatedProjects
                    } catch (throwable: Throwable) {
                        // Handle error
                        android.util.Log.e("ProjectRepository", "Failed to create project", throwable)
                    }
                }
            }
        }
    }

// ================================
// ViewModel Layer Example
// ================================

/**
 * Example ViewModel showing proper coroutine usage with lifecycle management.
 */
class ProjectViewModel
    @Inject
    constructor(
        private val projectRepository: ProjectRepository,
        private val dispatchers: CoroutineDispatchers,
        private val coroutineScopes: CoroutineScopes,
    ) : ViewModel() {
        private val _uiState = FlowConfiguration.createStateFlow<ProjectUiState>(ProjectUiState.Loading)
        val uiState = _uiState.asStateFlow()

        private val _events = FlowConfiguration.createSharedFlow<ProjectEvent>()
        val events = _events.asSharedFlow()

        /**
         * Loads projects with proper error handling and UI state management.
         */
        fun loadProjects() {
            launchWithErrorHandling(
                onError = { throwable ->
                    _uiState.value = ProjectUiState.Error(throwable.message ?: "Unknown error")
                    _events.tryEmit(ProjectEvent.LoadingFailed(throwable))
                },
            ) {
                _uiState.value = ProjectUiState.Loading

                val result = projectRepository.loadProjects()

                result.fold(
                    onSuccess = { projects ->
                        _uiState.value = ProjectUiState.Success(projects)
                        _events.emit(ProjectEvent.ProjectsLoaded(projects))
                    },
                    onFailure = { throwable ->
                        _uiState.value = ProjectUiState.Error(throwable.message ?: "Unknown error")
                        _events.emit(ProjectEvent.LoadingFailed(throwable))
                    },
                )
            }
        }

        /**
         * Starts observing project changes.
         */
        fun startObservingProjects() {
            viewModelScope.launch {
                projectRepository.observeProjects().collect { projects ->
                    _uiState.value = ProjectUiState.Success(projects)
                }
            }
        }

        /**
         * Creates a new project with validation.
         */
        fun createProject(
            name: String,
            serverProfileId: String,
            projectPath: String,
        ) {
            launchWithErrorHandling(
                onError = { throwable ->
                    _events.tryEmit(ProjectEvent.CreationFailed(throwable))
                },
            ) {
                // Validate input
                require(name.isNotBlank()) { "Project name cannot be empty" }

                val project =
                    Project(
                        id = generateId(),
                        name = name,
                        serverProfileId = serverProfileId,
                        projectPath = projectPath,
                        scriptsFolder = "scripts",
                        claudeSessionId = null,
                        status = ProjectStatus.ACTIVE,
                        createdAt = System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis(),
                    )

                projectRepository.createProject(project)
                _events.emit(ProjectEvent.ProjectCreated(project))
            }
        }

        private fun generateId(): String = System.currentTimeMillis().toString()
    }

// ================================
// WebSocket Service Example
// ================================

/**
 * Example WebSocket service showing proper coroutine usage with connection management.
 */
@Singleton
class WebSocketService
    @Inject
    constructor(
        private val dispatchers: CoroutineDispatchers,
        @WebSocketScope private val webSocketScope: CoroutineScope,
        private val errorHandler: CoroutineErrorHandler,
    ) {
        private val _connectionState = FlowConfiguration.createStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val connectionState = _connectionState.asStateFlow()

        private val _messages = FlowConfiguration.createSharedFlow<WebSocketMessage>()
        val messages = _messages.asSharedFlow()

        /**
         * Connects to WebSocket with proper error handling.
         */
        fun connect(url: String) {
            webSocketScope.launch {
                val handler =
                    errorHandler.createWebSocketExceptionHandler(
                        onConnectionError = {
                            _connectionState.value = ConnectionState.Error("Connection failed")
                        },
                        onTimeoutError = {
                            _connectionState.value = ConnectionState.Error("Connection timeout")
                        },
                    )

                try {
                    withContext(dispatchers.io + handler) {
                        _connectionState.value = ConnectionState.Connecting

                        // Simulate WebSocket connection
                        // In real implementation, this would use OkHttp WebSocket
                        _connectionState.value = ConnectionState.Connected

                        // Start message listening
                        startMessageListening()
                    }
                } catch (throwable: Throwable) {
                    _connectionState.value = ConnectionState.Error(throwable.message ?: "Unknown error")
                }
            }
        }

        /**
         * Sends a message through the WebSocket.
         */
        fun sendMessage(message: WebSocketMessage) {
            webSocketScope.launch {
                try {
                    withContext(dispatchers.io) {
                        // Simulate sending message
                        android.util.Log.d("WebSocketService", "Sending message: ${message.content}")
                    }
                } catch (throwable: Throwable) {
                    android.util.Log.e("WebSocketService", "Failed to send message", throwable)
                }
            }
        }

        /**
         * Starts listening for incoming messages.
         */
        private fun startMessageListening() {
            webSocketScope.launch {
                val messageFlow =
                    flow {
                        // Simulate receiving messages
                        repeat(10) { index ->
                            kotlinx.coroutines.delay(1000)
                            emit(WebSocketMessage("Message $index"))
                        }
                    }

                messageFlow
                    .catch { throwable ->
                        _connectionState.value = ConnectionState.Error(throwable.message ?: "Message error")
                    }.collect { message ->
                        _messages.emit(message)
                    }
            }
        }

        /**
         * Disconnects from WebSocket.
         */
        fun disconnect() {
            webSocketScope.launch {
                _connectionState.value = ConnectionState.Disconnecting

                // Simulate disconnection
                kotlinx.coroutines.delay(100)

                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

// ================================
// Data Classes and Sealed Classes
// ================================

// ================================
// Use Case Example
// ================================

/**
 * Example use case showing proper coroutine usage with business logic.
 */
@Singleton
class CreateProjectUseCase
    @Inject
    constructor(
        private val projectRepository: ProjectRepository,
        private val dispatchers: CoroutineDispatchers,
        private val errorHandler: CoroutineErrorHandler,
    ) {
        /**
         * Creates a project with validation and error handling.
         */
        suspend fun execute(request: CreateProjectRequest): Result<Project> =
            withContext(dispatchers.default) {
                ErrorHandlingUtils.safeCall(
                    onError = { throwable ->
                        Result.failure(throwable)
                    },
                ) {
                    // Validate request
                    validateRequest(request)

                    // Create project
                    val project =
                        Project(
                            id = generateId(),
                            name = request.name,
                            serverProfileId = request.serverProfileId,
                            projectPath = request.projectPath,
                            scriptsFolder = request.scriptsFolder ?: "scripts",
                            claudeSessionId = null,
                            status = ProjectStatus.ACTIVE,
                            createdAt = System.currentTimeMillis(),
                            lastActiveAt = System.currentTimeMillis(),
                        )

                    // Save project
                    projectRepository.createProject(project)

                    Result.success(project)
                }
            }

        private fun validateRequest(request: CreateProjectRequest) {
            require(request.name.isNotBlank()) { "Project name cannot be empty" }

            require(request.serverProfileId.isNotBlank()) { "Server profile ID cannot be empty" }

            require(request.projectPath.isNotBlank()) { "Project path cannot be empty" }
        }

        private fun generateId(): String = "project_${System.currentTimeMillis()}"
    }

/**
 * Request for creating a project.
 */
data class CreateProjectRequest(
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String? = null,
)
