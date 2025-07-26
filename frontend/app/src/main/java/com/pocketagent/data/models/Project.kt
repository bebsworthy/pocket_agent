package com.pocketagent.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a Claude Code project with its associated session.
 *
 * Projects represent individual codebases and their associated Claude Code sessions
 * running on remote servers. Each project is linked to a specific server profile
 * and contains metadata about the project path, status, and activity.
 *
 * Entity Relationship: SSH Identity (1) → (N) Server Profile → (N) Project
 *
 * @property id Unique identifier for the project
 * @property name User-friendly name for the project (max 100 characters)
 * @property serverProfileId Reference to the server profile hosting this project
 * @property projectPath Directory path on the server where the project is located
 * @property scriptsFolder Path to the scripts folder relative to projectPath (default: "scripts")
 * @property claudeSessionId Current Claude Code session ID
 * @property status Current project status
 * @property createdAt Timestamp when the project was created
 * @property lastActiveAt Timestamp of last activity
 * @property repositoryUrl Optional Git repository URL
 * @property lastError Last error message if status is ERROR
 */
@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val claudeSessionId: String? = null,
    val status: ProjectStatus = ProjectStatus.INACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null,
    val repositoryUrl: String? = null,
    val lastError: String? = null,
) {
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_PATH_LENGTH = 255
        const val MAX_SCRIPTS_FOLDER_LENGTH = 50
        const val MAX_REPOSITORY_URL_LENGTH = 500
        const val HOURS_24_IN_MILLIS = 24 * 60 * 60 * 1000L
        const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
    }

    init {
        require(name.isNotBlank()) { "Project name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Project name too long (max $MAX_NAME_LENGTH chars)" }
        require(serverProfileId.isNotBlank()) { "Server profile ID cannot be blank" }
        require(projectPath.isNotBlank()) { "Project path cannot be blank" }
        require(projectPath.startsWith("/")) { "Project path must be absolute (start with /)" }
        require(scriptsFolder.isNotBlank()) { "Scripts folder cannot be blank" }
        require(!scriptsFolder.startsWith("/")) { "Scripts folder must be relative (not start with /)" }
        require(claudeSessionId?.isNotBlank() != false) { "Claude session ID cannot be blank if provided" }
        require(createdAt > 0) { "Created timestamp must be positive" }
        require(lastActiveAt == null || lastActiveAt > 0) {
            "Last active timestamp must be positive if provided"
        }
        require(repositoryUrl?.isNotBlank() != false) { "Repository URL cannot be blank if provided" }
        require(lastError?.isNotBlank() != false) { "Last error cannot be blank if provided" }
    }

    /**
     * Check if this project was active recently (within the last 24 hours).
     */
    fun isRecentlyActive(): Boolean {
        val twentyFourHoursAgo = System.currentTimeMillis() - HOURS_24_IN_MILLIS
        return lastActiveAt != null && lastActiveAt > twentyFourHoursAgo
    }

    /**
     * Get the full scripts path.
     */
    fun getScriptsPath(): String = "$projectPath/$scriptsFolder"

    /**
     * Check if the project is currently active.
     */
    fun isActive(): Boolean = status == ProjectStatus.ACTIVE

    /**
     * Check if the project is in an error state.
     */
    fun hasError(): Boolean = status == ProjectStatus.ERROR

    /**
     * Check if the project has a Claude session.
     */
    fun hasClaudeSession(): Boolean = claudeSessionId != null

    /**
     * Get the display name for the project.
     */
    fun getDisplayName(): String = name

    /**
     * Get the project folder name from the path.
     */
    fun getFolderName(): String = projectPath.substringAfterLast("/")

    /**
     * Check if the project has a repository URL.
     */
    fun hasRepository(): Boolean = repositoryUrl != null
}

/**
 * Represents the status of a project and its Claude Code session.
 *
 * The project status tracks the current state of the Claude Code session
 * and helps the UI provide appropriate feedback to users.
 */
@Serializable
enum class ProjectStatus {
    /** No active Claude Code session */
    INACTIVE,

    /** Currently establishing Claude Code session */
    CONNECTING,

    /** Claude Code session is active and ready */
    ACTIVE,

    /** Claude Code session is shutting down */
    DISCONNECTED,

    /** Project connection failed or encountered an error */
    ERROR,

    ;

    /**
     * Check if this status represents an active connection attempt.
     */
    fun isConnecting(): Boolean = this == CONNECTING

    /**
     * Check if this status represents an active session.
     */
    fun isActive(): Boolean = this == ACTIVE

    /**
     * Check if this status represents an error state.
     */
    fun isError(): Boolean = this == ERROR

    /**
     * Check if this status represents a disconnected state.
     */
    fun isDisconnected(): Boolean = this == DISCONNECTED || this == INACTIVE

    /**
     * Get a user-friendly description of the status.
     */
    fun getDescription(): String =
        when (this) {
            INACTIVE -> "Inactive"
            CONNECTING -> "Connecting..."
            ACTIVE -> "Active"
            DISCONNECTED -> "Disconnected"
            ERROR -> "Error"
        }
}

/**
 * Builder class for creating Project instances in tests.
 *
 * This builder provides a fluent interface for constructing Project objects
 * with specific configurations for testing scenarios.
 */
class ProjectBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test Project"
    private var serverProfileId: String = UUID.randomUUID().toString()
    private var projectPath: String = "/home/user/test-project"
    private var scriptsFolder: String = "scripts"
    private var claudeSessionId: String? = null
    private var status: ProjectStatus = ProjectStatus.INACTIVE
    private var createdAt: Long = System.currentTimeMillis()
    private var lastActiveAt: Long? = null
    private var repositoryUrl: String? = null
    private var lastError: String? = null

    fun id(id: String) = apply { this.id = id }

    fun name(name: String) = apply { this.name = name }

    fun serverProfileId(serverProfileId: String) = apply { this.serverProfileId = serverProfileId }

    fun projectPath(projectPath: String) = apply { this.projectPath = projectPath }

    fun scriptsFolder(scriptsFolder: String) = apply { this.scriptsFolder = scriptsFolder }

    fun claudeSessionId(claudeSessionId: String?) = apply { this.claudeSessionId = claudeSessionId }

    fun status(status: ProjectStatus) = apply { this.status = status }

    fun createdAt(timestamp: Long) = apply { this.createdAt = timestamp }

    fun lastActiveAt(timestamp: Long?) = apply { this.lastActiveAt = timestamp }

    fun repositoryUrl(repositoryUrl: String?) = apply { this.repositoryUrl = repositoryUrl }

    fun lastError(lastError: String?) = apply { this.lastError = lastError }

    fun build(): Project =
        Project(
            id = id,
            name = name,
            serverProfileId = serverProfileId,
            projectPath = projectPath,
            scriptsFolder = scriptsFolder,
            claudeSessionId = claudeSessionId,
            status = status,
            createdAt = createdAt,
            lastActiveAt = lastActiveAt,
            repositoryUrl = repositoryUrl,
            lastError = lastError,
        )
}

/**
 * Update the project status.
 */
fun Project.withStatus(status: ProjectStatus): Project =
    copy(status = status, lastError = if (status != ProjectStatus.ERROR) null else lastError)

/**
 * Mark as active with current timestamp.
 */
fun Project.markAsActive(claudeSessionId: String? = null): Project =
    copy(
        status = ProjectStatus.ACTIVE,
        claudeSessionId = claudeSessionId ?: this.claudeSessionId,
        lastActiveAt = System.currentTimeMillis(),
        lastError = null,
    )

/**
 * Mark as inactive.
 */
fun Project.markAsInactive(): Project =
    copy(
        status = ProjectStatus.INACTIVE,
        claudeSessionId = null,
        lastError = null,
    )

/**
 * Mark as connecting.
 */
fun Project.markAsConnecting(): Project =
    copy(
        status = ProjectStatus.CONNECTING,
        lastError = null,
    )

/**
 * Mark as disconnected.
 */
fun Project.markAsDisconnected(): Project =
    copy(
        status = ProjectStatus.DISCONNECTED,
        claudeSessionId = null,
        lastError = null,
    )

/**
 * Mark as error state with error message.
 */
fun Project.markAsError(error: String): Project =
    copy(
        status = ProjectStatus.ERROR,
        lastError = error,
    )

/**
 * Update the last active timestamp.
 */
fun Project.updateLastActive(): Project = copy(lastActiveAt = System.currentTimeMillis())

/**
 * Set the repository URL.
 */
fun Project.withRepositoryUrl(url: String?): Project = copy(repositoryUrl = url)

/**
 * Check if the project matches the search query.
 */
fun Project.matchesSearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        projectPath.contains(query, ignoreCase = true) ||
        repositoryUrl?.contains(query, ignoreCase = true) == true

/**
 * Get the age of the project in days.
 */
fun Project.getAgeInDays(): Long {
    val now = System.currentTimeMillis()
    return (now - createdAt) / Project.DAY_IN_MILLIS
}

/**
 * Get the days since last activity.
 */
fun Project.getDaysSinceLastActivity(): Long? {
    if (lastActiveAt == null) return null
    val now = System.currentTimeMillis()
    return (now - lastActiveAt) / Project.DAY_IN_MILLIS
}

/**
 * Create a copy for export.
 */
fun Project.toExportModel(): ProjectExport =
    ProjectExport(
        id = id,
        name = name,
        serverProfileId = serverProfileId,
        projectPath = projectPath,
        scriptsFolder = scriptsFolder,
        repositoryUrl = repositoryUrl,
        createdAt = createdAt,
    )

/**
 * Export model for Project (without runtime status).
 */
@Serializable
data class ProjectExport(
    val id: String,
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String,
    val repositoryUrl: String?,
    val createdAt: Long,
)

/**
 * Validation utilities for Project.
 */
object ProjectValidator {
    private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
    private val VALID_PATH_REGEX = Regex("^/[a-zA-Z0-9/_.-]+$")
    private val VALID_FOLDER_REGEX = Regex("^[a-zA-Z0-9_.-]+$")

    /**
     * Validate project name.
     */
    fun validateName(name: String): Result<Unit> =
        when {
            name.isBlank() -> Result.failure(IllegalArgumentException("Name cannot be blank"))
            name.length > Project.MAX_NAME_LENGTH ->
                Result.failure(
                    IllegalArgumentException("Name too long (max ${Project.MAX_NAME_LENGTH} chars)"),
                )
            !VALID_NAME_REGEX.matches(name) -> Result.failure(IllegalArgumentException("Name contains invalid characters"))
            else -> Result.success(Unit)
        }

    /**
     * Validate project path.
     */
    fun validateProjectPath(path: String): Result<Unit> =
        when {
            path.isBlank() -> Result.failure(IllegalArgumentException("Project path cannot be blank"))
            !path.startsWith("/") -> Result.failure(IllegalArgumentException("Project path must be absolute"))
            !VALID_PATH_REGEX.matches(path) -> Result.failure(IllegalArgumentException("Invalid project path format"))
            path.length > Project.MAX_PATH_LENGTH ->
                Result.failure(
                    IllegalArgumentException("Project path too long (max ${Project.MAX_PATH_LENGTH} chars)"),
                )
            else -> Result.success(Unit)
        }

    /**
     * Validate scripts folder.
     */
    fun validateScriptsFolder(folder: String): Result<Unit> =
        when {
            folder.isBlank() -> Result.failure(IllegalArgumentException("Scripts folder cannot be blank"))
            folder.startsWith("/") -> Result.failure(IllegalArgumentException("Scripts folder must be relative"))
            !VALID_FOLDER_REGEX.matches(folder) -> Result.failure(IllegalArgumentException("Invalid scripts folder format"))
            folder.length > Project.MAX_SCRIPTS_FOLDER_LENGTH ->
                Result.failure(
                    IllegalArgumentException("Scripts folder name too long (max ${Project.MAX_SCRIPTS_FOLDER_LENGTH} chars)"),
                )
            else -> Result.success(Unit)
        }

    /**
     * Validate repository URL.
     */
    fun validateRepositoryUrl(url: String?): Result<Unit> {
        if (url == null) return Result.success(Unit)
        return when {
            url.isBlank() -> Result.failure(IllegalArgumentException("Repository URL cannot be blank if provided"))
            !url.startsWith("https://") && !url.startsWith("git@") ->
                Result.failure(IllegalArgumentException("Repository URL must start with https:// or git@"))
            url.length > Project.MAX_REPOSITORY_URL_LENGTH ->
                Result.failure(
                    IllegalArgumentException("Repository URL too long (max ${Project.MAX_REPOSITORY_URL_LENGTH} chars)"),
                )
            else -> Result.success(Unit)
        }
    }
}

/**
 * Common project factory methods.
 */
object ProjectFactory {
    /**
     * Create a sample project for testing.
     */
    fun createSample(
        name: String = "Sample Project",
        serverProfileId: String = UUID.randomUUID().toString(),
    ): Project =
        ProjectBuilder()
            .name(name)
            .serverProfileId(serverProfileId)
            .projectPath("/home/user/sample-project")
            .repositoryUrl("https://github.com/user/sample-project.git")
            .build()

    /**
     * Create multiple sample projects.
     */
    fun createSamples(
        count: Int,
        serverProfileId: String,
    ): List<Project> =
        (1..count).map { index ->
            createSample("Sample Project $index", serverProfileId)
        }

    /**
     * Create a project with specific path.
     */
    fun createWithPath(
        name: String,
        serverProfileId: String,
        projectPath: String,
    ): Project =
        ProjectBuilder()
            .name(name)
            .serverProfileId(serverProfileId)
            .projectPath(projectPath)
            .build()
}
