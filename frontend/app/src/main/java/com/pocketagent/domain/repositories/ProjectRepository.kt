package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.Project
import com.pocketagent.domain.models.entities.ProjectStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for project management.
 *
 * This interface defines the contract for managing projects and their
 * associated Claude Code sessions, including initialization, lifecycle management,
 * and repository operations.
 *
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface ProjectRepository {
    /**
     * Retrieves all projects sorted by last active time.
     *
     * @return Flow emitting a list of projects
     */
    fun getAllProjects(): Flow<Result<List<Project>>>

    /**
     * Observes projects with real-time updates.
     *
     * @return Flow emitting project list updates
     */
    fun observeProjects(): Flow<List<Project>>

    /**
     * Retrieves a project by ID.
     *
     * @param id The project ID
     * @return The project if found
     */
    suspend fun getProjectById(id: String): Result<Project?>

    /**
     * Observes a specific project for real-time updates.
     *
     * @param id The project ID
     * @return Flow emitting project updates
     */
    fun observeProject(id: String): Flow<Project?>

    /**
     * Creates a new project.
     * Validates that the server profile exists.
     *
     * @param project The project to create
     * @return The created project
     */
    suspend fun createProject(project: Project): Result<Project>

    /**
     * Updates an existing project.
     *
     * @param project The project to update
     * @return The updated project
     */
    suspend fun updateProject(project: Project): Result<Project>

    /**
     * Updates the last active timestamp for a project.
     *
     * @param id The project ID
     * @return Success or error result
     */
    suspend fun updateLastActive(id: String): Result<Unit>

    /**
     * Deletes a project and its associated messages.
     *
     * @param id The project ID to delete
     * @return Success or error result
     */
    suspend fun deleteProject(id: String): Result<Unit>

    /**
     * Batch deletes multiple projects.
     *
     * @param ids The project IDs to delete
     * @return Success or error result with failed IDs
     */
    suspend fun deleteProjects(ids: List<String>): Result<List<String>>

    /**
     * Retrieves projects by server profile ID.
     *
     * @param serverProfileId The server profile ID
     * @return List of projects hosted on the server
     */
    suspend fun getProjectsByServerProfile(serverProfileId: String): Result<List<Project>>

    /**
     * Updates the status of a project.
     *
     * @param id The project ID
     * @param status The new project status
     * @param errorMessage Optional error message for ERROR status
     * @return The updated project
     */
    suspend fun updateProjectStatus(
        id: String,
        status: ProjectStatus,
        errorMessage: String? = null,
    ): Result<Project>

    /**
     * Observes project status changes.
     *
     * @param id The project ID
     * @return Flow emitting project status updates
     */
    fun observeProjectStatus(id: String): Flow<ProjectStatus>

    /**
     * Updates the Claude session ID for a project.
     *
     * @param id The project ID
     * @param sessionId The new Claude session ID
     * @return The updated project
     */
    suspend fun updateClaudeSessionId(
        id: String,
        sessionId: String?,
    ): Result<Project>

    /**
     * Initializes a project with optional repository cloning.
     *
     * @param id The project ID
     * @param repositoryUrl Optional repository URL to clone
     * @param accessToken Optional access token for private repositories
     * @return Flow emitting initialization progress
     */
    fun initializeProject(
        id: String,
        repositoryUrl: String? = null,
        accessToken: String? = null,
    ): Flow<Result<ProjectInitializationProgress>>

    /**
     * Retrieves the most recently active projects.
     *
     * @param limit Maximum number of projects to return
     * @return List of recently active projects
     */
    suspend fun getRecentProjects(limit: Int = 5): Result<List<Project>>

    /**
     * Searches projects by name or path.
     *
     * @param query The search query
     * @return List of matching projects
     */
    suspend fun searchProjects(query: String): Result<List<Project>>

    /**
     * Gets projects by status.
     *
     * @param status The project status to filter by
     * @return List of projects with the specified status
     */
    suspend fun getProjectsByStatus(status: ProjectStatus): Result<List<Project>>

    /**
     * Gets active projects (ACTIVE status).
     *
     * @return List of active projects
     */
    suspend fun getActiveProjects(): Result<List<Project>>

    /**
     * Validates project configuration.
     *
     * @param project The project to validate
     * @return True if the project configuration is valid
     */
    suspend fun validateProject(project: Project): Result<Boolean>

    /**
     * Validates project path on server.
     *
     * @param serverProfileId The server profile ID
     * @param projectPath The project path to validate
     * @return True if path is valid and accessible
     */
    suspend fun validateProjectPath(
        serverProfileId: String,
        projectPath: String,
    ): Result<Boolean>

    /**
     * Checks if a project name already exists.
     *
     * @param name The project name to check
     * @param excludeId Optional ID to exclude from check (for updates)
     * @return True if name exists
     */
    suspend fun isNameExists(
        name: String,
        excludeId: String? = null,
    ): Result<Boolean>

    /**
     * Duplicates a project with a new name.
     *
     * @param id The project ID to duplicate
     * @param newName The new project name
     * @param newPath The new project path
     * @return The duplicated project
     */
    suspend fun duplicateProject(
        id: String,
        newName: String,
        newPath: String,
    ): Result<Project>

    /**
     * Archives a project (keeps data but marks as archived).
     *
     * @param id The project ID to archive
     * @return The archived project
     */
    suspend fun archiveProject(id: String): Result<Project>

    /**
     * Unarchives a project.
     *
     * @param id The project ID to unarchive
     * @return The unarchived project
     */
    suspend fun unarchiveProject(id: String): Result<Project>

    /**
     * Gets archived projects.
     *
     * @return List of archived projects
     */
    suspend fun getArchivedProjects(): Result<List<Project>>

    /**
     * Exports project data (without messages).
     *
     * @param id The project ID
     * @return Exported project data as JSON
     */
    suspend fun exportProject(id: String): Result<String>

    /**
     * Imports project from exported data.
     *
     * @param data The exported project data
     * @return The imported project
     */
    suspend fun importProject(data: String): Result<Project>

    /**
     * Gets project statistics.
     *
     * @return Map of project statistics
     */
    suspend fun getProjectStatistics(): Result<Map<String, Any>>

    /**
     * Synchronizes projects with encrypted storage.
     * Used for offline/online synchronization.
     *
     * @return Success or error result
     */
    suspend fun syncProjects(): Result<Unit>

    /**
     * Clears all projects (for logout/reset).
     *
     * @return Success or error result
     */
    suspend fun clearAll(): Result<Unit>
}

/**
 * Represents the progress of project initialization.
 *
 * @property step Current initialization step
 * @property progress Progress percentage (0-100)
 * @property message Status message
 * @property isComplete Whether initialization is complete
 * @property error Optional error information
 */
data class ProjectInitializationProgress(
    val step: String,
    val progress: Int,
    val message: String,
    val isComplete: Boolean = false,
    val error: String? = null,
)
