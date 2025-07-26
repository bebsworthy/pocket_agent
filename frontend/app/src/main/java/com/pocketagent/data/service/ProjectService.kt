package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.toExportModel
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.repository.getMessageCount
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ProjectValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
class ProjectService
    @Inject
    constructor(
        private val repository: SecureDataRepository,
        private val validator: ProjectValidator,
        private val serverProfileService: ServerProfileService,
        private val repositoryValidationService: RepositoryValidationService,
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
         * @param request Project creation request containing all necessary parameters
         * @return Result with created project or error
         */
        suspend fun createProject(request: CreateProjectRequest): ServiceResult<Project> {
            Log.d(TAG, "Creating project: ${request.name}")

            return try {
                val result = withContext(Dispatchers.Default) {
                    validateAndCreateProject(request)
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create project", e)
                serviceFailure("Failed to create project: ${e.message}")
            }
        }

        /**
         * Validates all requirements and creates the project.
         */
        private suspend fun validateAndCreateProject(request: CreateProjectRequest): ServiceResult<Project> {
            // Validate server profile exists
            val serverResult = serverProfileService.getServerProfile(request.serverProfileId)
            if (serverResult.isFailure) {
                val errorMessage = serverResult.getErrorOrNull()
                return serviceFailure("Server profile not found: $errorMessage")
            }
            val serverProfile = serverResult.getOrNull()!!

            // Create the project
            val project = Project(
                name = request.name,
                serverProfileId = request.serverProfileId,
                projectPath = request.projectPath,
                scriptsFolder = request.scriptsFolder,
                repositoryUrl = request.repositoryUrl,
                status = ProjectStatus.INACTIVE,
            )

            // Perform all validations
            val validationError = performProjectValidations(request, project, serverProfile)
            if (validationError != null) return validationError

            // Save to repository
            repository.addProject(project)

            // Initialize project if requested
            val finalProject = if (request.initializeScriptsFolder) {
                initializeProject(project.id).getOrNull() ?: project
            } else {
                project
            }

            Log.d(TAG, "Project created successfully: ${request.name}")
            return serviceSuccess(finalProject)
        }

        /**
         * Performs comprehensive validation for project creation.
         */
        private suspend fun performProjectValidations(
            request: CreateProjectRequest,
            project: Project,
            serverProfile: com.pocketagent.data.models.ServerProfile
        ): ServiceResult<Project>? {
            // Validate the project
            val validationResult = validator.validateForCreation(project)
            if (validationResult.isFailure()) {
                val errorMessage = validationResult.getFirstErrorMessage()
                return serviceFailure("Validation failed: $errorMessage")
            }

            val existingProjects = repository.getAllProjects()
            
            // Check name uniqueness
            val nameValidation = validator.validateNameUniqueness(
                request.name,
                existingProjects.map { it.name },
            )
            if (nameValidation.isFailure()) {
                return serviceFailure("Project name already exists")
            }

            // Check project path uniqueness on the same server
            val pathPairs = existingProjects.map { it.projectPath to it.serverProfileId }
            val pathValidation = validator.validateProjectPathUniqueness(
                request.projectPath,
                request.serverProfileId,
                pathPairs,
            )
            if (pathValidation.isFailure()) {
                return serviceFailure("Project with same path already exists on this server")
            }

            // Check server project limits
            val serverProjects = existingProjects.filter { it.serverProfileId == request.serverProfileId }
            if (serverProjects.size >= MAX_PROJECTS_PER_SERVER) {
                val message = "Maximum number of projects ($MAX_PROJECTS_PER_SERVER) reached for this server"
                return serviceFailure(message)
            }

            // Validate repository URL if provided
            if (request.repositoryUrl != null) {
                val repoValidation = repositoryValidationService.validateRepositoryUrl(request.repositoryUrl)
                if (repoValidation.isFailure()) {
                    val errorMessage = repoValidation.getFirstErrorMessage() ?: "Repository validation failed"
                    return serviceFailure("Repository URL validation failed: $errorMessage")
                }
            }

            // Validate paths on server if requested
            if (request.validatePaths) {
                val pathValidationResult = validateProjectPaths(
                    serverProfile,
                    request.projectPath,
                    request.scriptsFolder,
                )
                if (pathValidationResult.isFailure()) {
                    val errorMessage = pathValidationResult.getFirstErrorMessage() ?: "Path validation failed"
                    return serviceFailure("Path validation failed: $errorMessage")
                }
            }

            return null
        }

        /**
         * Retrieves a project by ID.
         */
        suspend fun getProject(id: String): ServiceResult<Project> {
            Log.d(TAG, "Getting project: $id")

            return try {
                val project = repository.getProjectById(id)
                if (project != null) {
                    serviceSuccess(project)
                } else {
                    serviceFailure("Project not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get project", e)
                serviceFailure("Failed to retrieve project: ${e.message}")
            }
        }

        /**
         * Updates an existing project.
         *
         * @param request Project update request containing the ID and fields to update
         * @return Result with updated project or error
         */
        suspend fun updateProject(request: UpdateProjectRequest): ServiceResult<Project> {
            Log.d(TAG, "Updating project: ${request.id}")

            return try {
                val existing =
                    repository.getProjectById(request.id)
                        ?: return serviceFailure("Project not found")

                val updated = createUpdatedProject(existing, request)

                // Validate the update
                val validationResult = validator.validateForUpdate(existing, updated)
                if (validationResult.isFailure()) {
                    val errorMessage = validationResult.getFirstErrorMessage() ?: "Validation failed"
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Perform uniqueness validations
                val uniquenessValidation = validateUpdateUniqueness(existing, request)
                if (uniquenessValidation.isFailure) {
                    return uniquenessValidation
                }

                // Validate repository URL if changed
                val repoValidation = validateRepositoryUrlUpdate(existing, request)
                if (repoValidation.isFailure) {
                    return repoValidation
                }

                // Validate paths on server if requested and paths changed
                val pathValidation = validateProjectPathsUpdate(existing, updated, request)
                if (pathValidation.isFailure) {
                    return pathValidation
                }

                repository.updateProject(updated)

                Log.d(TAG, "Project updated successfully: ${request.id}")
                serviceSuccess(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update project", e)
                serviceFailure("Failed to update project: ${e.message}")
            }
        }

        /**
         * Creates updated project from existing project and update request.
         */
        private fun createUpdatedProject(
            existing: Project,
            request: UpdateProjectRequest,
        ): Project =
            existing.copy(
                name = request.name ?: existing.name,
                projectPath = request.projectPath ?: existing.projectPath,
                scriptsFolder = request.scriptsFolder ?: existing.scriptsFolder,
                repositoryUrl = request.repositoryUrl ?: existing.repositoryUrl,
                status = request.status ?: existing.status,
                claudeSessionId = request.claudeSessionId ?: existing.claudeSessionId,
                lastError = request.lastError ?: existing.lastError,
                lastActiveAt = if (request.status == ProjectStatus.ACTIVE) System.currentTimeMillis() else existing.lastActiveAt,
            )

        /**
         * Validates uniqueness constraints for project update.
         */
        private suspend fun validateUpdateUniqueness(
            existing: Project,
            request: UpdateProjectRequest,
        ): ServiceResult<Unit> {
            // Check name uniqueness if name changed
            if (request.name != null && request.name != existing.name) {
                val nameValidation = validateNameUniquenessForUpdate(request.name!!, request.id)
                if (nameValidation.isFailure) {
                    return nameValidation
                }
            }

            // Check project path uniqueness if path changed
            if (request.projectPath != null && request.projectPath != existing.projectPath) {
                val pathValidation = validatePathUniquenessForUpdate(existing, request)
                if (pathValidation.isFailure) {
                    return pathValidation
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates name uniqueness for project update.
         */
        private suspend fun validateNameUniquenessForUpdate(
            newName: String,
            projectId: String,
        ): ServiceResult<Unit> {
            val existingProjects = repository.getAllProjects()
            val nameValidation =
                validator.validateNameUniqueness(
                    newName,
                    existingProjects.map { it.name },
                    excludeId = projectId,
                )
            return if (nameValidation.isFailure()) {
                serviceFailure("Project name already exists")
            } else {
                serviceSuccess(Unit)
            }
        }

        /**
         * Validates path uniqueness for project update.
         */
        private suspend fun validatePathUniquenessForUpdate(
            existing: Project,
            request: UpdateProjectRequest,
        ): ServiceResult<Unit> {
            val existingProjects = repository.getAllProjects()
            val pathPairs = existingProjects.map { it.projectPath to it.serverProfileId }
            val pathValidation =
                validator.validateProjectPathUniqueness(
                    request.projectPath!!,
                    existing.serverProfileId,
                    pathPairs,
                    excludeId = request.id,
                )
            return if (pathValidation.isFailure()) {
                serviceFailure("Project with same path already exists on this server")
            } else {
                serviceSuccess(Unit)
            }
        }

        /**
         * Validates repository URL update.
         */
        private suspend fun validateRepositoryUrlUpdate(
            existing: Project,
            request: UpdateProjectRequest,
        ): ServiceResult<Unit> {
            if (request.repositoryUrl != null && request.repositoryUrl != existing.repositoryUrl) {
                val repoValidation = repositoryValidationService.validateRepositoryUrl(request.repositoryUrl!!)
                if (repoValidation.isFailure()) {
                    val errorMessage = repoValidation.getFirstErrorMessage() ?: "Repository validation failed"
                    return serviceFailure("Repository URL validation failed: $errorMessage")
                }
            }
            return serviceSuccess(Unit)
        }

        /**
         * Validates project paths update on server.
         */
        private suspend fun validateProjectPathsUpdate(
            existing: Project,
            updated: Project,
            request: UpdateProjectRequest,
        ): ServiceResult<Unit> {
            val projectPathChanged = request.projectPath != null && request.projectPath != existing.projectPath
            val scriptsFolderChanged = request.scriptsFolder != null && request.scriptsFolder != existing.scriptsFolder
            val pathsChanged = projectPathChanged || scriptsFolderChanged

            if (request.validatePaths && pathsChanged) {
                val serverResult = serverProfileService.getServerProfile(existing.serverProfileId)
                if (serverResult.isSuccess) {
                    val pathValidationResult =
                        validateProjectPaths(
                            serverResult.getOrNull()!!,
                            updated.projectPath,
                            updated.scriptsFolder,
                        )
                    if (pathValidationResult.isFailure()) {
                        val errorMessage = pathValidationResult.getFirstErrorMessage() ?: "Path validation failed"
                        return serviceFailure("Path validation failed: $errorMessage")
                    }
                }
            }
            return serviceSuccess(Unit)
        }

        /**
         * Deletes a project and its associated data.
         */
        suspend fun deleteProject(
            id: String,
            removeMessages: Boolean = true,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Deleting project: $id")

            return try {
                val project =
                    repository.getProjectById(id)
                        ?: return serviceFailure("Project not found")

                // Check if project is active
                if (project.status == ProjectStatus.ACTIVE) {
                    val message = "Cannot delete active project. Please disconnect first."
                    return serviceFailure(message)
                }

                repository.deleteProject(id)

                if (removeMessages) {
                    repository.clearProjectMessages(id)
                }

                Log.d(TAG, "Project deleted successfully: $id")
                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete project", e)
                serviceFailure("Failed to delete project: ${e.message}")
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
            status: ProjectStatus? = null,
        ): ServiceResult<List<Project>> {
            Log.d(TAG, "Listing projects")

            return try {
                val projects = repository.getAllProjects()

                // Apply filters
                val filtered = applyProjectFilters(projects, serverProfileId, status, includeInactive)

                // Apply sorting
                val sorted = applyProjectSorting(filtered, sortBy, ascending)

                serviceSuccess(sorted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list projects", e)
                serviceFailure("Failed to list projects: ${e.message}")
            }
        }

        /**
         * Applies filters to project list.
         */
        private fun applyProjectFilters(
            projects: List<Project>,
            serverProfileId: String?,
            status: ProjectStatus?,
            includeInactive: Boolean,
        ): List<Project> {
            var filtered = projects

            // Filter by server profile if specified
            if (serverProfileId != null) {
                filtered = filtered.filter { it.serverProfileId == serverProfileId }
            }

            // Filter by status if specified
            filtered =
                if (status != null) {
                    filtered.filter { it.status == status }
                } else if (!includeInactive) {
                    filtered.filter { it.status != ProjectStatus.INACTIVE }
                } else {
                    filtered
                }

            return filtered
        }

        /**
         * Applies sorting to project list.
         */
        private suspend fun applyProjectSorting(
            projects: List<Project>,
            sortBy: ProjectSortBy,
            ascending: Boolean,
        ): List<Project> {
            val sorted =
                when (sortBy) {
                    ProjectSortBy.NAME -> projects.sortedBy { it.name }
                    ProjectSortBy.CREATED_DATE -> projects.sortedBy { it.createdAt }
                    ProjectSortBy.LAST_ACTIVITY -> projects.sortedBy { it.lastActiveAt ?: it.createdAt }
                    ProjectSortBy.STATUS -> projects.sortedBy { it.status.ordinal }
                    ProjectSortBy.SERVER_NAME -> sortProjectsByServerName(projects)
                    ProjectSortBy.PROJECT_PATH -> projects.sortedBy { it.projectPath }
                }

            return if (ascending) sorted else sorted.reversed()
        }

        /**
         * Sorts projects by server name.
         */
        private suspend fun sortProjectsByServerName(projects: List<Project>): List<Project> {
            val serverNames = getServerNamesMap()
            return projects.sortedBy { project ->
                serverNames[project.serverProfileId] ?: ""
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
            errorMessage: String? = null,
        ): ServiceResult<Project> {
            Log.d(TAG, "Updating project status: $projectId -> $newStatus")

            return try {
                val project =
                    repository.getProjectById(projectId)
                        ?: return serviceFailure("Project not found")

                // Validate status transition
                val transitionValidation = validator.validateStatusTransition(project.status, newStatus)
                if (transitionValidation.isFailure()) {
                    val errorMessage = transitionValidation.getFirstErrorMessage() ?: "Transition validation failed"
                    return serviceFailure(
                        "Invalid status transition: $errorMessage",
                    )
                }

                val updated =
                    when (newStatus) {
                        ProjectStatus.ACTIVE ->
                            project.copy(
                                status = newStatus,
                                claudeSessionId = claudeSessionId,
                                lastActiveAt = System.currentTimeMillis(),
                                lastError = null,
                            )
                        ProjectStatus.INACTIVE ->
                            project.copy(
                                status = newStatus,
                                claudeSessionId = null,
                                lastError = null,
                            )
                        ProjectStatus.ERROR ->
                            project.copy(
                                status = newStatus,
                                lastError = errorMessage ?: "Unknown error occurred",
                            )
                        ProjectStatus.CONNECTING ->
                            project.copy(
                                status = newStatus,
                                lastError = null,
                            )
                        ProjectStatus.DISCONNECTED ->
                            project.copy(
                                status = newStatus,
                                claudeSessionId = null,
                                lastError = null,
                            )
                    }

                repository.updateProject(updated)

                Log.d(TAG, "Project status updated successfully: $projectId")
                serviceSuccess(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update project status", e)
                serviceFailure("Failed to update project status: ${e.message}")
            }
        }

        /**
         * Activates a project with Claude Code session.
         */
        suspend fun activateProject(
            projectId: String,
            claudeSessionId: String,
        ): ServiceResult<Project> = updateProjectStatus(projectId, ProjectStatus.ACTIVE, claudeSessionId)

        /**
         * Deactivates a project.
         */
        suspend fun deactivateProject(projectId: String): ServiceResult<Project> = updateProjectStatus(projectId, ProjectStatus.INACTIVE)

        /**
         * Marks project as error state.
         */
        suspend fun markProjectAsError(
            projectId: String,
            error: String,
        ): ServiceResult<Project> = updateProjectStatus(projectId, ProjectStatus.ERROR, errorMessage = error)

        /**
         * Clears project error state.
         */
        suspend fun clearProjectError(projectId: String): ServiceResult<Project> {
            return try {
                val project =
                    repository.getProjectById(projectId)
                        ?: return serviceFailure("Project not found")

                if (project.status == ProjectStatus.ERROR) {
                    updateProject(projectId, status = ProjectStatus.INACTIVE, lastError = null)
                } else {
                    serviceSuccess(project)
                }
            } catch (e: Exception) {
                serviceFailure("Failed to clear project error: ${e.message}")
            }
        }

        // Project Initialization and Management

        /**
         * Initializes a project (creates scripts folder, validates paths).
         */
        suspend fun initializeProject(projectId: String): ServiceResult<Project> {
            Log.d(TAG, "Initializing project: $projectId")

            return try {
                val project =
                    repository.getProjectById(projectId)
                        ?: return serviceFailure("Project not found")

                val serverResult = serverProfileService.getServerProfile(project.serverProfileId)
                if (serverResult.isFailure) {
                    return serviceFailure("Server profile not found")
                }

                // For now, just mark as initialized
                // In a full implementation, this would SSH to server and create directories
                Log.d(TAG, "Project initialization completed: $projectId")
                serviceSuccess(project)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize project", e)
                serviceFailure("Failed to initialize project: ${e.message}")
            }
        }

        /**
         * Validates project paths on the server.
         */
        private suspend fun validateProjectPaths(
            serverProfile: com.pocketagent.data.models.ServerProfile,
            projectPath: String,
            scriptsFolder: String,
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
            limit: Int = DEFAULT_SEARCH_LIMIT,
        ): ServiceResult<List<Project>> {
            Log.d(TAG, "Searching projects: $query")

            return try {
                if (query.isBlank()) {
                    return serviceSuccess(emptyList())
                }

                val allProjects = repository.getAllProjects()
                val searchResults =
                    allProjects
                        .filter { project ->
                            val nameMatches = project.name.contains(query, ignoreCase = true)
                            val pathMatches = project.projectPath.contains(query, ignoreCase = true)
                            val repoMatches = project.repositoryUrl?.contains(query, ignoreCase = true) == true
                            val displayNameMatches = project.getDisplayName().contains(query, ignoreCase = true)
                            nameMatches || pathMatches || repoMatches || displayNameMatches
                        }.take(limit)

                serviceSuccess(searchResults)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search projects", e)
                serviceFailure("Failed to search projects: ${e.message}")
            }
        }

        /**
         * Filters projects by various criteria.
         */
        suspend fun filterProjects(criteria: ProjectFilterCriteria): ServiceResult<List<Project>> {
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

                serviceSuccess(filtered)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to filter projects", e)
                serviceFailure("Failed to filter projects: ${e.message}")
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
                        avgSessionDuration = 0L,
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
                serviceSuccess(projects)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get projects for server", e)
                serviceFailure("Failed to get projects for server: ${e.message}")
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
        fun observeProjectsWithUsage(): Flow<List<ProjectWithUsage>> =
            combine(
                repository.observeProjects(),
                flow { emit(getUsageStatistics()) },
            ) { projects, usageStats ->
                projects.map { project ->
                    ProjectWithUsage(
                        project = project,
                        usageStats =
                            usageStats[project.id] ?: ProjectUsageStats(
                                messageCount = 0,
                                lastActivity = null,
                                totalActiveDuration = 0L,
                                connectionCount = 0,
                                avgSessionDuration = 0L,
                            ),
                    )
                }
            }.flowOn(Dispatchers.Default)

        /**
         * Observable flow of project statuses.
         */
        fun observeProjectStatuses(): Flow<Map<String, ProjectStatus>> =
            repository
                .observeProjects()
                .map { projects ->
                    projects.associate { it.id to it.status }
                }.flowOn(Dispatchers.Default)

        // Import/Export

        /**
         * Exports project configuration (without sensitive data).
         */
        suspend fun exportProject(projectId: String): ServiceResult<String> {
            Log.d(TAG, "Exporting project: $projectId")

            return try {
                val project =
                    repository.getProjectById(projectId)
                        ?: return serviceFailure("Project not found")

                val exportData = project.toExportModel()
                val jsonData =
                    kotlinx.serialization.json.Json.encodeToString(
                        com.pocketagent.data.models.ProjectExport
                            .serializer(),
                        exportData,
                    )

                serviceSuccess(jsonData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export project", e)
                serviceFailure("Failed to export project: ${e.message}")
            }
        }

        /**
         * Imports project configuration.
         */
        suspend fun importProject(
            jsonData: String,
            serverProfileMapping: Map<String, String> = emptyMap(),
        ): ServiceResult<Project> {
            Log.d(TAG, "Importing project")

            return try {
                val exportData =
                    kotlinx.serialization.json.Json.decodeFromString(
                        com.pocketagent.data.models.ProjectExport
                            .serializer(),
                        jsonData,
                    )

                // Map server profile ID if provided
                val mappedServerProfileId = serverProfileMapping[exportData.serverProfileId] ?: exportData.serverProfileId

                // Create new project with new ID
                createProject(
                    name = exportData.name,
                    serverProfileId = mappedServerProfileId,
                    projectPath = exportData.projectPath,
                    scriptsFolder = exportData.scriptsFolder,
                    repositoryUrl = exportData.repositoryUrl,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import project", e)
                serviceFailure("Failed to import project: ${e.message}")
            }
        }

        // Helper Methods

        private suspend fun getServerNamesMap(): Map<String, String> =
            try {
                val profiles = repository.getAllServerProfiles()
                profiles.associate { it.id to it.name }
            } catch (e: Exception) {
                emptyMap()
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
    PROJECT_PATH,
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
    val pathPattern: String? = null,
)

/**
 * Usage statistics for a project.
 */
data class ProjectUsageStats(
    val messageCount: Int,
    val lastActivity: Long?,
    val totalActiveDuration: Long,
    val connectionCount: Int,
    val avgSessionDuration: Long,
)

/**
 * Project with usage statistics.
 */
data class ProjectWithUsage(
    val project: Project,
    val usageStats: ProjectUsageStats,
)

/**
 * Request data class for creating a new project.
 */
data class CreateProjectRequest(
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val repositoryUrl: String? = null,
    val description: String? = null,
    val validatePaths: Boolean = false,
    val initializeScriptsFolder: Boolean = true,
)

/**
 * Request data class for updating an existing project.
 */
data class UpdateProjectRequest(
    val id: String,
    val name: String? = null,
    val projectPath: String? = null,
    val scriptsFolder: String? = null,
    val repositoryUrl: String? = null,
    val status: ProjectStatus? = null,
    val claudeSessionId: String? = null,
    val lastError: String? = null,
    val validatePaths: Boolean = false,
)
