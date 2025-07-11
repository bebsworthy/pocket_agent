package com.pocketagent.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Root data model containing all application entities.
 *
 * This is the main data container for the Pocket Agent application, holding all
 * SSH identities, server profiles, projects, and messages in a single encrypted
 * JSON structure. The data is stored encrypted on device storage for security.
 *
 * @property version Data format version for migration compatibility
 * @property sshIdentities List of SSH identity configurations
 * @property serverProfiles List of server connection profiles
 * @property projects List of Claude Code projects
 * @property messages Project messages mapped by project ID
 * @property lastModified Timestamp of last data modification
 * @property metadata Additional application metadata
 */
@Serializable
data class AppData(
    val version: Int = 1,
    val sshIdentities: List<SshIdentity> = emptyList(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val projects: List<Project> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap(), // projectId -> messages
    val lastModified: Long = System.currentTimeMillis(),
    val metadata: AppMetadata = AppMetadata(),
) {
    init {
        require(version >= 1) { "Data version must be at least 1" }
        require(sshIdentities.size <= 50) { "Maximum 50 SSH identities allowed" }
        require(serverProfiles.size <= 100) { "Maximum 100 server profiles allowed" }
        require(projects.size <= 200) { "Maximum 200 projects allowed" }

        // Validate entity relationships
        val identityIds = sshIdentities.map { it.id }.toSet()
        serverProfiles.forEach { profile ->
            require(profile.sshIdentityId in identityIds) {
                "Server profile '${profile.name}' references non-existent SSH identity '${profile.sshIdentityId}'"
            }
        }

        val serverIds = serverProfiles.map { it.id }.toSet()
        projects.forEach { project ->
            require(project.serverProfileId in serverIds) {
                "Project '${project.name}' references non-existent server profile '${project.serverProfileId}'"
            }
        }

        // Validate message references
        val projectIds = projects.map { it.id }.toSet()
        messages.keys.forEach { projectId ->
            require(projectId in projectIds || projectId == "system") {
                "Messages reference non-existent project '$projectId'"
            }
        }
    }

    /**
     * Get total count of all entities.
     */
    fun getTotalEntityCount(): Int = sshIdentities.size + serverProfiles.size + projects.size
}

/**
 * Application metadata containing device and backup information.
 *
 * This metadata is stored alongside the main application data to provide
 * additional context and configuration for data management.
 *
 * @property createdAt Timestamp when the app data was first created
 * @property deviceId Unique identifier for this device installation
 * @property backupEnabled Whether automatic backup is enabled
 * @property dataSize Approximate size of the data in bytes
 * @property lastBackup Timestamp of last successful backup
 */
@Serializable
data class AppMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = UUID.randomUUID().toString(),
    val backupEnabled: Boolean = true,
    val dataSize: Long = 0,
    val lastBackup: Long? = null,
) {
    init {
        require(deviceId.isNotBlank()) { "Device ID cannot be blank" }
        require(dataSize >= 0) { "Data size cannot be negative" }
    }
}

/**
 * Builder class for creating AppData instances in tests.
 *
 * This builder provides a fluent interface for constructing AppData objects
 * with specific configurations for testing scenarios.
 */
class AppDataBuilder {
    private var version: Int = 1
    private var sshIdentities: MutableList<SshIdentity> = mutableListOf()
    private var serverProfiles: MutableList<ServerProfile> = mutableListOf()
    private var projects: MutableList<Project> = mutableListOf()
    private var messages: MutableMap<String, List<Message>> = mutableMapOf()
    private var lastModified: Long = System.currentTimeMillis()
    private var metadata: AppMetadata = AppMetadata()

    fun version(version: Int) = apply { this.version = version }

    fun addSshIdentity(identity: SshIdentity) =
        apply {
            this.sshIdentities.add(identity)
        }

    fun addServerProfile(profile: ServerProfile) =
        apply {
            this.serverProfiles.add(profile)
        }

    fun addProject(project: Project) =
        apply {
            this.projects.add(project)
        }

    fun addMessages(
        projectId: String,
        messageList: List<Message>,
    ) = apply {
        this.messages[projectId] = messageList
    }

    fun lastModified(timestamp: Long) =
        apply {
            this.lastModified = timestamp
        }

    fun metadata(metadata: AppMetadata) =
        apply {
            this.metadata = metadata
        }

    fun build(): AppData =
        AppData(
            version = version,
            sshIdentities = sshIdentities.toList(),
            serverProfiles = serverProfiles.toList(),
            projects = projects.toList(),
            messages = messages.toMap(),
            lastModified = lastModified,
            metadata = metadata,
        )
}

/**
 * Extension functions for AppData operations.
 */

/**
 * Get all SSH identities sorted by name.
 */
fun AppData.getSshIdentitiesSorted(): List<SshIdentity> = sshIdentities.sortedBy { it.name }

/**
 * Get all server profiles sorted by name.
 */
fun AppData.getServerProfilesSorted(): List<ServerProfile> = serverProfiles.sortedBy { it.name }

/**
 * Get all projects sorted by last activity.
 */
fun AppData.getProjectsSorted(): List<Project> = projects.sortedByDescending { it.lastActiveAt ?: it.createdAt }

/**
 * Get projects for a specific server profile.
 */
fun AppData.getProjectsForServer(serverProfileId: String): List<Project> = projects.filter { it.serverProfileId == serverProfileId }

/**
 * Get server profiles for a specific SSH identity.
 */
fun AppData.getServerProfilesForIdentity(sshIdentityId: String): List<ServerProfile> =
    serverProfiles.filter { it.sshIdentityId == sshIdentityId }

/**
 * Get recent projects with activity in the last 30 days.
 */
fun AppData.getRecentProjects(limit: Int = 10): List<Project> {
    val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
    return projects
        .filter { (it.lastActiveAt ?: it.createdAt) > thirtyDaysAgo }
        .sortedByDescending { it.lastActiveAt ?: it.createdAt }
        .take(limit)
}

/**
 * Search projects by name or path.
 */
fun AppData.searchProjects(query: String): List<Project> =
    projects.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.projectPath.contains(query, ignoreCase = true)
    }

/**
 * Get messages for a specific project.
 */
fun AppData.getProjectMessages(projectId: String): List<Message> = messages[projectId] ?: emptyList()


/**
 * Check if the data contains any entities.
 */
fun AppData.isEmpty(): Boolean = sshIdentities.isEmpty() && serverProfiles.isEmpty() && projects.isEmpty()

/**
 * Get a summary of data statistics.
 */
fun AppData.getDataSummary(): DataSummary =
    DataSummary(
        sshIdentityCount = sshIdentities.size,
        serverProfileCount = serverProfiles.size,
        projectCount = projects.size,
        totalMessageCount = messages.values.sumOf { it.size },
        lastModified = lastModified,
    )

/**
 * Data summary statistics.
 */
data class DataSummary(
    val sshIdentityCount: Int,
    val serverProfileCount: Int,
    val projectCount: Int,
    val totalMessageCount: Int,
    val lastModified: Long,
)
