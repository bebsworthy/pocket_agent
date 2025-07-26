package com.pocketagent.domain.models.entities

import com.pocketagent.domain.models.error.ValidationException
import java.util.UUID

/**
 * Represents a project with its associated Claude Code session.
 *
 * Projects represent individual codebases and their associated Claude Code sessions
 * running on remote servers.
 *
 * @property id Unique identifier for the project
 * @property name User-friendly name for the project
 * @property serverProfileId Reference to the server profile hosting this project
 * @property projectPath Directory path on the server where the project is located
 * @property scriptsFolder Path to the scripts folder (default: "scripts")
 * @property claudeSessionId Current Claude Code session ID
 * @property status Current project status
 * @property repositoryUrl Optional Git repository URL
 * @property createdAt Timestamp when the project was created
 * @property lastActiveAt Timestamp of last activity
 * @property isActive Whether the project is active
 * @property description Optional description for the project
 * @property settings Project-specific settings
 * @property metadata Additional metadata for the project
 * @property lastError Last error message if any
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val claudeSessionId: String? = null,
    val status: ProjectStatus = ProjectStatus.INACTIVE,
    val repositoryUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null,
    val isActive: Boolean = true,
    val description: String? = null,
    val settings: ProjectSettings = ProjectSettings(),
    val metadata: ProjectMetadata = ProjectMetadata(),
    val lastError: String? = null,
) {
    init {
        validateName(name)
        validateProjectPath(projectPath)
        validateScriptsFolder(scriptsFolder)
        validateRepositoryUrl(repositoryUrl)
    }

    /**
     * Updates the project status.
     */
    fun updateStatus(newStatus: ProjectStatus): Project = copy(status = newStatus)

    /**
     * Marks the project as active.
     */
    fun markAsActive(): Project =
        copy(
            status = ProjectStatus.ACTIVE,
            lastActiveAt = System.currentTimeMillis(),
        )

    /**
     * Marks the project as inactive.
     */
    fun markAsInactive(): Project = copy(status = ProjectStatus.INACTIVE)

    /**
     * Marks the project as having an error.
     */
    fun markAsError(error: String): Project =
        copy(
            status = ProjectStatus.ERROR,
            lastError = error,
        )

    /**
     * Clears the last error.
     */
    fun clearError(): Project = copy(lastError = null)

    /**
     * Updates the Claude session ID.
     */
    fun updateClaudeSessionId(sessionId: String?): Project = copy(claudeSessionId = sessionId)

    /**
     * Deactivates the project.
     */
    fun deactivate(): Project = copy(isActive = false)

    /**
     * Reactivates the project.
     */
    fun reactivate(): Project = copy(isActive = true)

    /**
     * Updates the description.
     */
    fun updateDescription(newDescription: String?): Project = copy(description = newDescription)

    /**
     * Updates the project settings.
     */
    fun updateSettings(newSettings: ProjectSettings): Project = copy(settings = newSettings)

    /**
     * Gets the display name for the project.
     */
    fun getDisplayName(): String = name.ifBlank { "Project $id" }

    /**
     * Gets the full project path.
     */
    fun getFullPath(): String = projectPath

    /**
     * Gets the scripts folder path.
     */
    fun getScriptsPath(): String = "$projectPath/$scriptsFolder"

    /**
     * Checks if the project is currently active.
     */
    fun isCurrentlyActive(): Boolean = status == ProjectStatus.ACTIVE

    /**
     * Checks if the project is connected.
     */
    fun isConnected(): Boolean = status in listOf(ProjectStatus.ACTIVE, ProjectStatus.CONNECTING)

    /**
     * Checks if the project has an error.
     */
    fun hasError(): Boolean = status == ProjectStatus.ERROR

    /**
     * Gets the project type based on repository URL.
     */
    fun getProjectType(): ProjectType =
        when {
            repositoryUrl == null -> ProjectType.LOCAL
            repositoryUrl.contains("github.com") -> ProjectType.GITHUB
            repositoryUrl.contains("gitlab.com") -> ProjectType.GITLAB
            repositoryUrl.contains("bitbucket.org") -> ProjectType.BITBUCKET
            else -> ProjectType.GIT
        }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MIN_NAME_LENGTH = 1
        const val MAX_PATH_LENGTH = 500
        const val MAX_SCRIPTS_FOLDER_LENGTH = 100

        private fun validateName(name: String) {
            if (name.isBlank()) {
                throw ValidationException("name", name, "Project name cannot be blank")
            }
            if (name.length > MAX_NAME_LENGTH) {
                throw ValidationException("name", name, "Project name too long (max $MAX_NAME_LENGTH chars)")
            }
            if (name.length < MIN_NAME_LENGTH) {
                throw ValidationException("name", name, "Project name too short (min $MIN_NAME_LENGTH chars)")
            }
        }

        private fun validateProjectPath(projectPath: String) {
            if (projectPath.isBlank()) {
                throw ValidationException("projectPath", projectPath, "Project path cannot be blank")
            }
            if (projectPath.length > MAX_PATH_LENGTH) {
                val message = "Project path too long (max $MAX_PATH_LENGTH chars)"
                throw ValidationException("projectPath", projectPath, message)
            }
            // Basic path validation - should start with / for absolute paths
            if (!projectPath.startsWith("/")) {
                throw ValidationException("projectPath", projectPath, "Project path should be absolute (start with /)")
            }
        }

        private fun validateScriptsFolder(scriptsFolder: String) {
            if (scriptsFolder.isBlank()) {
                throw ValidationException("scriptsFolder", scriptsFolder, "Scripts folder cannot be blank")
            }
            if (scriptsFolder.length > MAX_SCRIPTS_FOLDER_LENGTH) {
                val message = "Scripts folder too long (max $MAX_SCRIPTS_FOLDER_LENGTH chars)"
                throw ValidationException("scriptsFolder", scriptsFolder, message)
            }
            // Scripts folder should be relative (not start with /)
            if (scriptsFolder.startsWith("/")) {
                throw ValidationException("scriptsFolder", scriptsFolder, "Scripts folder should be relative (not start with /)")
            }
        }

        private fun validateRepositoryUrl(repositoryUrl: String?) {
            if (repositoryUrl != null && repositoryUrl.isNotBlank()) {
                val urlRegex = Regex("^https?://.*|^git@.*|^ssh://.*")
                if (!repositoryUrl.matches(urlRegex)) {
                    throw ValidationException("repositoryUrl", repositoryUrl, "Invalid repository URL format")
                }
            }
        }
    }
}

/**
 * Represents the status of a project and its Claude Code session.
 */
enum class ProjectStatus {
    INACTIVE,
    CONNECTING,
    ACTIVE,
    DISCONNECTED,
    ERROR,
}

/**
 * Represents the type of project based on repository.
 */
enum class ProjectType {
    LOCAL,
    GIT,
    GITHUB,
    GITLAB,
    BITBUCKET,
}

/**
 * Project-specific settings.
 *
 * @property maxTurns Maximum number of turns in a Claude conversation
 * @property allowedTools List of allowed tools for Claude
 * @property autoApprovePatterns List of patterns that auto-approve actions
 * @property notificationsEnabled Whether notifications are enabled
 * @property backgroundMonitoring Whether background monitoring is enabled
 * @property autoSave Whether to auto-save conversations
 * @property maxMessageHistory Maximum number of messages to keep
 * @property customPrompts Custom prompts for the project
 */
data class ProjectSettings(
    val maxTurns: Int = DEFAULT_MAX_TURNS,
    val allowedTools: List<String> = emptyList(),
    val autoApprovePatterns: List<String> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val backgroundMonitoring: Boolean = true,
    val autoSave: Boolean = true,
    val maxMessageHistory: Int = DEFAULT_MAX_MESSAGE_HISTORY,
    val customPrompts: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_MAX_TURNS = 50
        const val DEFAULT_MAX_MESSAGE_HISTORY = 1000
    }
}

/**
 * Metadata associated with a project.
 *
 * @property tags User-defined tags for organizing projects
 * @property language Primary programming language
 * @property framework Framework or technology stack
 * @property environment Environment type
 * @property size Project size estimate
 * @property collaborators List of collaborators
 * @property lastBackup Last backup timestamp
 * @property statistics Project statistics
 */
data class ProjectMetadata(
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val framework: String? = null,
    val environment: ProjectEnvironment = ProjectEnvironment.DEVELOPMENT,
    val size: ProjectSize = ProjectSize.SMALL,
    val collaborators: List<String> = emptyList(),
    val lastBackup: Long? = null,
    val statistics: ProjectStatistics = ProjectStatistics(),
)

/**
 * Enum representing project environments.
 */
enum class ProjectEnvironment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    TESTING,
}

/**
 * Enum representing project sizes.
 */
enum class ProjectSize {
    SMALL,
    MEDIUM,
    LARGE,
    ENTERPRISE,
}

/**
 * Project statistics.
 *
 * @property totalMessages Total number of messages
 * @property totalSessions Total number of sessions
 * @property lastSessionDuration Duration of last session in minutes
 * @property totalTimeSpent Total time spent in minutes
 * @property avgSessionDuration Average session duration in minutes
 * @property scriptsExecuted Number of scripts executed
 * @property errorsOccurred Number of errors occurred
 */
data class ProjectStatistics(
    val totalMessages: Int = 0,
    val totalSessions: Int = 0,
    val lastSessionDuration: Long = 0,
    val totalTimeSpent: Long = 0,
    val avgSessionDuration: Long = 0,
    val scriptsExecuted: Int = 0,
    val errorsOccurred: Int = 0,
)

/**
 * Builder class for creating projects with validation.
 */
class ProjectBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = ""
    private var serverProfileId: String = ""
    private var projectPath: String = ""
    private var scriptsFolder: String = "scripts"
    private var claudeSessionId: String? = null
    private var status: ProjectStatus = ProjectStatus.INACTIVE
    private var repositoryUrl: String? = null
    private var createdAt: Long = System.currentTimeMillis()
    private var lastActiveAt: Long? = null
    private var isActive: Boolean = true
    private var description: String? = null
    private var settings: ProjectSettings = ProjectSettings()
    private var metadata: ProjectMetadata = ProjectMetadata()
    private var lastError: String? = null

    fun id(id: String) = apply { this.id = id }

    fun name(name: String) = apply { this.name = name }

    fun serverProfileId(serverProfileId: String) = apply { this.serverProfileId = serverProfileId }

    fun projectPath(projectPath: String) = apply { this.projectPath = projectPath }

    fun scriptsFolder(scriptsFolder: String) = apply { this.scriptsFolder = scriptsFolder }

    fun claudeSessionId(claudeSessionId: String?) = apply { this.claudeSessionId = claudeSessionId }

    fun status(status: ProjectStatus) = apply { this.status = status }

    fun repositoryUrl(repositoryUrl: String?) = apply { this.repositoryUrl = repositoryUrl }

    fun createdAt(createdAt: Long) = apply { this.createdAt = createdAt }

    fun lastActiveAt(lastActiveAt: Long?) = apply { this.lastActiveAt = lastActiveAt }

    fun isActive(isActive: Boolean) = apply { this.isActive = isActive }

    fun description(description: String?) = apply { this.description = description }

    fun settings(settings: ProjectSettings) = apply { this.settings = settings }

    fun metadata(metadata: ProjectMetadata) = apply { this.metadata = metadata }

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
            repositoryUrl = repositoryUrl,
            createdAt = createdAt,
            lastActiveAt = lastActiveAt,
            isActive = isActive,
            description = description,
            settings = settings,
            metadata = metadata,
            lastError = lastError,
        )
}

/**
 * Extension functions for Project.
 */
fun Project.toBuilder(): ProjectBuilder =
    ProjectBuilder()
        .id(id)
        .name(name)
        .serverProfileId(serverProfileId)
        .projectPath(projectPath)
        .scriptsFolder(scriptsFolder)
        .claudeSessionId(claudeSessionId)
        .status(status)
        .repositoryUrl(repositoryUrl)
        .createdAt(createdAt)
        .lastActiveAt(lastActiveAt)
        .isActive(isActive)
        .description(description)
        .settings(settings)
        .metadata(metadata)
        .lastError(lastError)

/**
 * Creates a new project builder.
 */
fun project(block: ProjectBuilder.() -> Unit): Project = ProjectBuilder().apply(block).build()
