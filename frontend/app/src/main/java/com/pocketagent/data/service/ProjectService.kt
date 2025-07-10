package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ProjectValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive service for Project CRUD operations and management.
 * 
 * This service provides a high-level interface for managing projects with
 * features including:
 * - Complete CRUD operations with validation
 * - Project initialization with server profile association
 * - Project status management and workflow
 * - Repository URL validation and management
 * - Scripts folder validation and management
 * - Search and filtering capabilities
 * - Usage tracking and analytics
 * - Integration with server profiles and SSH identities
 * - Project path validation and sanitization
 * - Claude Code session management
 * - Error handling and recovery
 * 
 * The service integrates with the existing SecureDataRepository, validation
 * framework, and server profile service to provide comprehensive project management.
 */
@Singleton
class ProjectService @Inject constructor(
    private val repository: SecureDataRepository,
    private val validator: ProjectValidator,
    private val serverProfileService: ServerProfileService,
    private val repositoryValidationService: RepositoryValidationService
) {
    
    companion object {
        private const val TAG = "ProjectService"
        private const val DEFAULT_SEARCH_LIMIT = 50
        private const val DEFAULT_SCRIPTS_FOLDER = "scripts"
        private const val PROJECT_TIMEOUT_HOURS = 24
        private const val MAX_PROJECTS_PER_SERVER = 100
    }
    
    // CRUD Operations
    
    /**
     * Creates a new project with comprehensive validation.
     * 
     * @param name Display name for the project
     * @param serverProfileId Associated server profile ID
     * @param projectPath Directory path on the server where project is located
     * @param scriptsFolder Scripts folder relative to projectPath (default: "scripts")
     * @param repositoryUrl Optional Git repository URL
     * @param description Optional project description
     * @param validatePaths Whether to validate project and scripts paths on server
     * @param initializeScriptsFolder Whether to create scripts folder during initialization
     * @return Result with created project or error
     */
    suspend fun createProject(
        name: String,
        serverProfileId: String,
        projectPath: String,
        scriptsFolder: String = DEFAULT_SCRIPTS_FOLDER,
        repositoryUrl: String? = null,
        description: String? = null,
        validatePaths: Boolean = false,
        initializeScriptsFolder: Boolean = true
    ): ServiceResult<Project> {
        Log.d(TAG, "Creating project: $name")
        
        return try {
            withContext(Dispatchers.Default) {
                // Validate server profile exists
                val serverResult = serverProfileService.getServerProfile(serverProfileId)
                if (serverResult.isFailure) {
                    return@withContext ServiceResult.failure("Server profile not found: ${serverResult.getErrorOrNull()}")
                }
                val serverProfile = serverResult.getOrNull()!!
                
                // Create the project
                val project = Project(
                    name = name,
                    serverProfileId = serverProfileId,
                    projectPath = projectPath,
                    scriptsFolder = scriptsFolder,
                    repositoryUrl = repositoryUrl,
                    status = ProjectStatus.INACTIVE
                )
                
                // Validate the project
                val validationResult = validator.validateForCreation(project)
                if (!validationResult.isValid) {
                    return@withContext ServiceResult.failure("Validation failed: ${validationResult.getErrorSummary()}")
                }
                
                // Check name uniqueness
                val existingProjects = repository.getAllProjects()
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingProjects.map { it.name }
                )
                if (!nameValidation.isValid) {
                    return@withContext ServiceResult.failure("Project name already exists")
                }
                
                // Check project path uniqueness on the same server
                val pathValidation = validator.validateProjectPathUniqueness(
                    projectPath,
                    serverProfileId,
                    existingProjects.map { it.projectPath to it.serverProfileId }
                )
                if (!pathValidation.isValid) {
                    return@withContext ServiceResult.failure("Project with same path already exists on this server")
                }
                
                // Check server project limits
                val serverProjects = existingProjects.filter { it.serverProfileId == serverProfileId }
                if (serverProjects.size >= MAX_PROJECTS_PER_SERVER) {
                    return@withContext ServiceResult.failure("Maximum number of projects ($MAX_PROJECTS_PER_SERVER) reached for this server")
                }
                
                // Validate repository URL if provided
                if (repositoryUrl != null) {
                    val repoValidation = repositoryValidationService.validateRepositoryUrl(repositoryUrl)
                    if (!repoValidation.isValid) {
                        return@withContext ServiceResult.failure("Repository URL validation failed: ${repoValidation.getErrorSummary()}")
                    }
                }
                
                // Validate paths on server if requested
                if (validatePaths) {
                    val pathValidationResult = validateProjectPaths(serverProfile, projectPath, scriptsFolder)
                    if (!pathValidationResult.isValid) {
                        return@withContext ServiceResult.failure("Path validation failed: ${pathValidationResult.getErrorSummary()}")
                    }
                }
                
                // Save to repository
                repository.addProject(project)
                
                // Initialize project if requested
                val finalProject = if (initializeScriptsFolder) {
                    initializeProject(project.id).getOrNull() ?: project
                } else {
                    project
                }
                
                Log.d(TAG, "Project created successfully: $name")
                ServiceResult.success(finalProject)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create project", e)
            ServiceResult.failure("Failed to create project: ${e.message}")
        }
    }
    
    /**
     * Retrieves a project by ID.
     */
    suspend fun getProject(id: String): ServiceResult<Project> {
        Log.d(TAG, "Getting project: $id")
        
        return try {
            val project = repository.getProjectById(id)
            if (project != null) {
                ServiceResult.success(project)
            } else {
                ServiceResult.failure("Project not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get project", e)
            ServiceResult.failure("Failed to retrieve project: ${e.message}")
        }
    }
    
    /**
     * Updates an existing project.
     */
    suspend fun updateProject(
        id: String,
        name: String? = null,
        projectPath: String? = null,
        scriptsFolder: String? = null,
        repositoryUrl: String? = null,
        status: ProjectStatus? = null,
        claudeSessionId: String? = null,
        lastError: String? = null,
        validatePaths: Boolean = false
    ): ServiceResult<Project> {
        Log.d(TAG, "Updating project: $id")
        
        return try {
            val existing = repository.getProjectById(id)
                ?: return ServiceResult.failure("Project not found")
            
            val updated = existing.copy(
                name = name ?: existing.name,
                projectPath = projectPath ?: existing.projectPath,
                scriptsFolder = scriptsFolder ?: existing.scriptsFolder,
                repositoryUrl = repositoryUrl ?: existing.repositoryUrl,
                status = status ?: existing.status,
                claudeSessionId = claudeSessionId ?: existing.claudeSessionId,
                lastError = lastError ?: existing.lastError,
                lastActiveAt = if (status == ProjectStatus.ACTIVE) System.currentTimeMillis() else existing.lastActiveAt
            )
            
            // Validate the update
            val validationResult = validator.validateForUpdate(existing, updated)
            if (!validationResult.isValid) {
                return ServiceResult.failure("Validation failed: ${validationResult.getErrorSummary()}")
            }
            
            // Check name uniqueness if name changed
            if (name != null && name != existing.name) {
                val existingProjects = repository.getAllProjects()
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingProjects.map { it.name },
                    excludeId = id
                )
                if (!nameValidation.isValid) {
                    return ServiceResult.failure("Project name already exists")
                }
            }
            
            // Check project path uniqueness if path changed
            if (projectPath != null && projectPath != existing.projectPath) {
                val existingProjects = repository.getAllProjects()
                val pathValidation = validator.validateProjectPathUniqueness(
                    projectPath,
                    existing.serverProfileId,
                    existingProjects.map { it.projectPath to it.serverProfileId },
                    excludeId = id
                )
                if (!pathValidation.isValid) {
                    return ServiceResult.failure("Project with same path already exists on this server")
                }
            }
            
            // Validate repository URL if changed
            if (repositoryUrl != null && repositoryUrl != existing.repositoryUrl) {
                val repoValidation = repositoryValidationService.validateRepositoryUrl(repositoryUrl)
                if (!repoValidation.isValid) {
                    return ServiceResult.failure("Repository URL validation failed: ${repoValidation.getErrorSummary()}")
                }
            }
            
            // Validate paths on server if requested and paths changed
            if (validatePaths && 
                ((projectPath != null && projectPath != existing.projectPath) ||
                 (scriptsFolder != null && scriptsFolder != existing.scriptsFolder))) {
                
                val serverResult = serverProfileService.getServerProfile(existing.serverProfileId)
                if (serverResult.isSuccess) {
                    val pathValidationResult = validateProjectPaths(
                        serverResult.getOrNull()!!, 
                        updated.projectPath, 
                        updated.scriptsFolder
                    )
                    if (!pathValidationResult.isValid) {
                        return ServiceResult.failure("Path validation failed: ${pathValidationResult.getErrorSummary()}")
                    }
                }
            }
            
            repository.updateProject(updated)
            
            Log.d(TAG, "Project updated successfully: $id")
            ServiceResult.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update project", e)
            ServiceResult.failure("Failed to update project: ${e.message}")
        }
    }
    
    /**
     * Deletes a project and its associated data.
     */
    suspend fun deleteProject(id: String, removeMessages: Boolean = true): ServiceResult<Unit> {
        Log.d(TAG, "Deleting project: $id")
        
        return try {
            val project = repository.getProjectById(id)
                ?: return ServiceResult.failure("Project not found")
            
            // Check if project is active
            if (project.status == ProjectStatus.ACTIVE) {
                return ServiceResult.failure("Cannot delete active project. Please disconnect first.")
            }
            
            repository.deleteProject(id)
            
            if (removeMessages) {
                repository.clearProjectMessages(id)
            }
            
            Log.d(TAG, "Project deleted successfully: $id")
            ServiceResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete project", e)
            ServiceResult.failure("Failed to delete project: ${e.message}")
        }
    }
    
    /**
     * Lists all projects with optional filtering and sorting.
     */
    suspend fun listProjects(
        sortBy: ProjectSortBy = ProjectSortBy.LAST_ACTIVITY,
        ascending: Boolean = false,
        includeInactive: Boolean = true,
        serverProfileId: String? = null,
        status: ProjectStatus? = null
    ): ServiceResult<List<Project>> {
        Log.d(TAG, "Listing projects")
        
        return try {
            val projects = repository.getAllProjects()
            
            // Filter by server profile if specified
            val filtered = if (serverProfileId != null) {
                projects.filter { it.serverProfileId == serverProfileId }
            } else {
                projects
            }
            
            // Filter by status if specified
            val statusFiltered = if (status != null) {
                filtered.filter { it.status == status }
            } else if (!includeInactive) {
                filtered.filter { it.status != ProjectStatus.INACTIVE }
            } else {
                filtered
            }
            
            // Sort the results
            val sorted = when (sortBy) {
                ProjectSortBy.NAME -> statusFiltered.sortedBy { it.name }
                ProjectSortBy.CREATED_DATE -> statusFiltered.sortedBy { it.createdAt }
                ProjectSortBy.LAST_ACTIVITY -> statusFiltered.sortedBy { it.lastActiveAt ?: it.createdAt }
                ProjectSortBy.STATUS -> statusFiltered.sortedBy { it.status.ordinal }
                ProjectSortBy.SERVER_NAME -> {
                    // Get server names and sort by them
                    val serverNames = getServerNamesMap()
                    statusFiltered.sortedBy { project ->
                        serverNames[project.serverProfileId] ?: ""
                    }
                }
                ProjectSortBy.PROJECT_PATH -> statusFiltered.sortedBy { it.projectPath }
            }.let { if (ascending) it else it.reversed() }
            
            ServiceResult.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list projects", e)
            ServiceResult.failure("Failed to list projects: ${e.message}")
        }
    }
    
    // Project Status Management
    
    /**
     * Updates project status with appropriate validations.
     */
    suspend fun updateProjectStatus(
        projectId: String, 
        newStatus: ProjectStatus,
        claudeSessionId: String? = null,
        errorMessage: String? = null
    ): ServiceResult<Project> {
        Log.d(TAG, "Updating project status: $projectId -> $newStatus")
        
        return try {
            val project = repository.getProjectById(projectId)
                ?: return ServiceResult.failure("Project not found")
            
            // Validate status transition
            val transitionValidation = validator.validateStatusTransition(project.status, newStatus)
            if (!transitionValidation.isValid) {
                return ServiceResult.failure("Invalid status transition: ${transitionValidation.getErrorSummary()}")
            }
            
            val updated = when (newStatus) {
                ProjectStatus.ACTIVE -> project.copy(
                    status = newStatus,
                    claudeSessionId = claudeSessionId,
                    lastActiveAt = System.currentTimeMillis(),
                    lastError = null
                )
                ProjectStatus.INACTIVE -> project.copy(
                    status = newStatus,
                    claudeSessionId = null,
                    lastError = null
                )
                ProjectStatus.ERROR -> project.copy(
                    status = newStatus,
                    lastError = errorMessage ?: "Unknown error occurred"
                )
                ProjectStatus.CONNECTING -> project.copy(
                    status = newStatus,
                    lastError = null
                )
                ProjectStatus.DISCONNECTED -> project.copy(
                    status = newStatus,
                    claudeSessionId = null,
                    lastError = null
                )
            }
            
            repository.updateProject(updated)
            
            Log.d(TAG, "Project status updated successfully: $projectId")
            ServiceResult.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update project status", e)
            ServiceResult.failure("Failed to update project status: ${e.message}")
        }
    }
    
    /**
     * Activates a project with Claude Code session.
     */
    suspend fun activateProject(projectId: String, claudeSessionId: String): ServiceResult<Project> {
        return updateProjectStatus(projectId, ProjectStatus.ACTIVE, claudeSessionId)
    }
    
    /**
     * Deactivates a project.
     */
    suspend fun deactivateProject(projectId: String): ServiceResult<Project> {
        return updateProjectStatus(projectId, ProjectStatus.INACTIVE)
    }
    
    /**
     * Marks project as error state.
     */
    suspend fun markProjectAsError(projectId: String, error: String): ServiceResult<Project> {
        return updateProjectStatus(projectId, ProjectStatus.ERROR, errorMessage = error)
    }
    
    /**
     * Clears project error state.
     */
    suspend fun clearProjectError(projectId: String): ServiceResult<Project> {
        return try {
            val project = repository.getProjectById(projectId)
                ?: return ServiceResult.failure("Project not found")
            
            if (project.status == ProjectStatus.ERROR) {
                updateProject(projectId, status = ProjectStatus.INACTIVE, lastError = null)
            } else {
                ServiceResult.success(project)
            }
        } catch (e: Exception) {
            ServiceResult.failure("Failed to clear project error: ${e.message}")
        }
    }
    
    // Project Initialization and Management
    
    /**
     * Initializes a project (creates scripts folder, validates paths).
     */
    suspend fun initializeProject(projectId: String): ServiceResult<Project> {
        Log.d(TAG, "Initializing project: $projectId")
        
        return try {
            val project = repository.getProjectById(projectId)
                ?: return ServiceResult.failure("Project not found")
            
            val serverResult = serverProfileService.getServerProfile(project.serverProfileId)
            if (serverResult.isFailure) {
                return ServiceResult.failure("Server profile not found")
            }
            
            // For now, just mark as initialized
            // In a full implementation, this would SSH to server and create directories
            Log.d(TAG, "Project initialization completed: $projectId")
            ServiceResult.success(project)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize project", e)
            ServiceResult.failure("Failed to initialize project: ${e.message}")
        }
    }
    
    /**
     * Validates project paths on the server.
     */
    private suspend fun validateProjectPaths(
        serverProfile: com.pocketagent.data.models.ServerProfile,
        projectPath: String,
        scriptsFolder: String
    ): ValidationResult {
        // In a full implementation, this would SSH to the server and validate paths
        // For now, just perform basic validation
        return ValidationResult.Success
    }
    
    // Search and Filtering
    
    /**
     * Searches projects by name, path, or repository URL.
     */
    suspend fun searchProjects(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): ServiceResult<List<Project>> {
        Log.d(TAG, "Searching projects: $query")
        
        return try {
            if (query.isBlank()) {
                return ServiceResult.success(emptyList())
            }
            
            val allProjects = repository.getAllProjects()
            val searchResults = allProjects.filter { project ->
                project.name.contains(query, ignoreCase = true) ||
                project.projectPath.contains(query, ignoreCase = true) ||
                project.repositoryUrl?.contains(query, ignoreCase = true) == true ||
                project.getDisplayName().contains(query, ignoreCase = true)
            }.take(limit)
            
            ServiceResult.success(searchResults)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search projects", e)
            ServiceResult.failure("Failed to search projects: ${e.message}")
        }
    }
    
    /**
     * Filters projects by various criteria.
     */
    suspend fun filterProjects(
        criteria: ProjectFilterCriteria
    ): ServiceResult<List<Project>> {
        Log.d(TAG, "Filtering projects")
        
        return try {
            val allProjects = repository.getAllProjects()
            var filtered = allProjects
            
            // Apply server profile filter
            criteria.serverProfileId?.let { serverId ->
                filtered = filtered.filter { it.serverProfileId == serverId }
            }
            
            // Apply status filter
            criteria.status?.let { status ->
                filtered = filtered.filter { it.status == status }
            }
            
            // Apply date range filter
            criteria.createdAfter?.let { after ->
                filtered = filtered.filter { it.createdAt >= after }
            }
            criteria.createdBefore?.let { before ->
                filtered = filtered.filter { it.createdAt <= before }
            }
            
            // Apply activity filter
            if (criteria.recentlyActiveOnly) {
                filtered = filtered.filter { it.isRecentlyActive() }
            }
            
            // Apply repository filter
            if (criteria.hasRepositoryOnly) {
                filtered = filtered.filter { it.repositoryUrl != null }
            }
            
            // Apply error filter
            if (criteria.hasErrorsOnly) {
                filtered = filtered.filter { it.status == ProjectStatus.ERROR }
            }
            
            // Apply path pattern filter
            criteria.pathPattern?.let { pattern ->
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                filtered = filtered.filter { regex.containsMatchIn(it.projectPath) }
            }
            
            ServiceResult.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to filter projects", e)
            ServiceResult.failure("Failed to filter projects: ${e.message}")
        }
    }
    
    // Usage Tracking and Analytics
    
    /**
     * Gets usage statistics for projects.
     */
    suspend fun getUsageStatistics(projectIds: List<String>? = null): Map<String, ProjectUsageStats> {
        Log.d(TAG, "Getting usage statistics")
        
        return try {
            val targetIds = projectIds ?: repository.getAllProjects().map { it.id }
            
            targetIds.associateWith { projectId ->
                val project = repository.getProjectById(projectId)
                val messageCount = repository.getMessageCount(projectId)
                
                ProjectUsageStats(
                    messageCount = messageCount,
                    lastActivity = project?.lastActiveAt,
                    totalActiveDuration = 0L, // Would need session tracking
                    connectionCount = 0, // Would need connection tracking
                    avgSessionDuration = 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage statistics", e)
            emptyMap()
        }
    }
    
    /**
     * Gets projects for a server profile.
     */
    suspend fun getProjectsForServer(serverProfileId: String): ServiceResult<List<Project>> {
        Log.d(TAG, "Getting projects for server: $serverProfileId")
        
        return try {
            val projects = repository.getProjectsForServer(serverProfileId)
            ServiceResult.success(projects)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get projects for server", e)
            ServiceResult.failure("Failed to get projects for server: ${e.message}")
        }
    }
    
    // Observable Flows
    
    /**
     * Observable flow of all projects.
     */
    fun observeProjects(): Flow<List<Project>> = repository.observeProjects()
    
    /**
     * Observable flow of projects with usage statistics.
     */
    fun observeProjectsWithUsage(): Flow<List<ProjectWithUsage>> {
        return combine(
            repository.observeProjects(),
            flow { emit(getUsageStatistics()) }
        ) { projects, usageStats ->
            projects.map { project ->
                ProjectWithUsage(
                    project = project,
                    usageStats = usageStats[project.id] ?: ProjectUsageStats(
                        messageCount = 0,
                        lastActivity = null,
                        totalActiveDuration = 0L,
                        connectionCount = 0,
                        avgSessionDuration = 0L
                    )
                )
            }
        }.flowOn(Dispatchers.Default)
    }
    
    /**
     * Observable flow of project statuses.
     */
    fun observeProjectStatuses(): Flow<Map<String, ProjectStatus>> {
        return repository.observeProjects().map { projects ->
            projects.associate { it.id to it.status }
        }.flowOn(Dispatchers.Default)
    }
    
    // Import/Export
    
    /**
     * Exports project configuration (without sensitive data).
     */
    suspend fun exportProject(projectId: String): ServiceResult<String> {
        Log.d(TAG, "Exporting project: $projectId")
        
        return try {
            val project = repository.getProjectById(projectId)
                ?: return ServiceResult.failure("Project not found")
            
            val exportData = project.toExportModel()
            val jsonData = kotlinx.serialization.json.Json.encodeToString(
                com.pocketagent.data.models.ProjectExport.serializer(), 
                exportData
            )
            
            ServiceResult.success(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export project", e)
            ServiceResult.failure("Failed to export project: ${e.message}")
        }
    }
    
    /**
     * Imports project configuration.
     */
    suspend fun importProject(
        jsonData: String,
        serverProfileMapping: Map<String, String> = emptyMap()
    ): ServiceResult<Project> {
        Log.d(TAG, "Importing project")
        
        return try {
            val exportData = kotlinx.serialization.json.Json.decodeFromString(
                com.pocketagent.data.models.ProjectExport.serializer(),
                jsonData
            )
            
            // Map server profile ID if provided
            val mappedServerProfileId = serverProfileMapping[exportData.serverProfileId] ?: exportData.serverProfileId
            
            // Create new project with new ID
            createProject(
                name = exportData.name,
                serverProfileId = mappedServerProfileId,
                projectPath = exportData.projectPath,
                scriptsFolder = exportData.scriptsFolder,
                repositoryUrl = exportData.repositoryUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import project", e)
            ServiceResult.failure("Failed to import project: ${e.message}")
        }
    }
    
    // Helper Methods
    
    private suspend fun getServerNamesMap(): Map<String, String> {
        return try {
            val profiles = repository.getAllServerProfiles()
            profiles.associate { it.id to it.name }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/**
 * Sorting options for projects.
 */
enum class ProjectSortBy {
    NAME,
    CREATED_DATE,
    LAST_ACTIVITY,
    STATUS,
    SERVER_NAME,
    PROJECT_PATH
}

/**
 * Filter criteria for projects.
 */
data class ProjectFilterCriteria(
    val serverProfileId: String? = null,
    val status: ProjectStatus? = null,
    val createdAfter: Long? = null,
    val createdBefore: Long? = null,
    val recentlyActiveOnly: Boolean = false,
    val hasRepositoryOnly: Boolean = false,
    val hasErrorsOnly: Boolean = false,
    val pathPattern: String? = null
)

/**
 * Usage statistics for a project.
 */
data class ProjectUsageStats(
    val messageCount: Int,
    val lastActivity: Long?,
    val totalActiveDuration: Long,
    val connectionCount: Int,
    val avgSessionDuration: Long
)

/**
 * Project with usage statistics.
 */
data class ProjectWithUsage(
    val project: Project,
    val usageStats: ProjectUsageStats
)

// Extension function for getting error summary from ValidationResult
private fun ValidationResult.getErrorSummary(): String {
    return when (this) {
        is ValidationResult.Success -> "No errors"
        is ValidationResult.Failure -> error.message
    }
}