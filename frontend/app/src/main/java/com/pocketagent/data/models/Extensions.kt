package com.pocketagent.data.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Extension functions for common data model operations.
 * 
 * This file provides utility functions for working with data models,
 * including serialization, validation, filtering, and transformations.
 */

// ========== AppData Extensions ==========

/**
 * Validate all relationships in the AppData structure.
 */
fun AppData.validateRelationships(): Result<Unit> {
    return try {
        // This will throw if validation fails
        AppData(
            version = version,
            sshIdentities = sshIdentities,
            serverProfiles = serverProfiles,
            projects = projects,
            messages = messages,
            lastModified = lastModified,
            metadata = metadata
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Get all active projects across all servers.
 */
fun AppData.getActiveProjects(): List<Project> = 
    projects.filter { it.status == ProjectStatus.ACTIVE }

/**
 * Get all connected servers.
 */
fun AppData.getConnectedServers(): List<ServerProfile> = 
    serverProfiles.filter { it.status == ConnectionStatus.CONNECTED }

/**
 * Get recently used SSH identities (used in last 30 days).
 */
fun AppData.getRecentlyUsedIdentities(): List<SshIdentity> = 
    sshIdentities.filter { it.isRecentlyUsed() }

/**
 * Get projects with errors.
 */
fun AppData.getProjectsWithErrors(): List<Project> = 
    projects.filter { it.status == ProjectStatus.ERROR }

/**
 * Get servers with errors.
 */
fun AppData.getServersWithErrors(): List<ServerProfile> = 
    serverProfiles.filter { it.status == ConnectionStatus.ERROR }

/**
 * Get total message count across all projects.
 */
fun AppData.getTotalMessageCount(): Int = 
    messages.values.sumOf { it.size }

/**
 * Get projects by status.
 */
fun AppData.getProjectsByStatus(status: ProjectStatus): List<Project> = 
    projects.filter { it.status == status }

/**
 * Get servers by status.
 */
fun AppData.getServersByStatus(status: ConnectionStatus): List<ServerProfile> = 
    serverProfiles.filter { it.status == status }

/**
 * Find SSH identity by name.
 */
fun AppData.findSshIdentityByName(name: String): SshIdentity? = 
    sshIdentities.find { it.name.equals(name, ignoreCase = true) }

/**
 * Find server profile by name.
 */
fun AppData.findServerProfileByName(name: String): ServerProfile? = 
    serverProfiles.find { it.name.equals(name, ignoreCase = true) }

/**
 * Find project by name.
 */
fun AppData.findProjectByName(name: String): Project? = 
    projects.find { it.name.equals(name, ignoreCase = true) }

/**
 * Get orphaned server profiles (referencing non-existent SSH identities).
 */
fun AppData.getOrphanedServerProfiles(): List<ServerProfile> {
    val identityIds = sshIdentities.map { it.id }.toSet()
    return serverProfiles.filter { it.sshIdentityId !in identityIds }
}

/**
 * Get orphaned projects (referencing non-existent server profiles).
 */
fun AppData.getOrphanedProjects(): List<Project> {
    val serverIds = serverProfiles.map { it.id }.toSet()
    return projects.filter { it.serverProfileId !in serverIds }
}

/**
 * Get unused SSH identities (not referenced by any server profile).
 */
fun AppData.getUnusedSshIdentities(): List<SshIdentity> {
    val usedIdentityIds = serverProfiles.map { it.sshIdentityId }.toSet()
    return sshIdentities.filter { it.id !in usedIdentityIds }
}

/**
 * Get unused server profiles (not referenced by any project).
 */
fun AppData.getUnusedServerProfiles(): List<ServerProfile> {
    val usedServerIds = projects.map { it.serverProfileId }.toSet()
    return serverProfiles.filter { it.id !in usedServerIds }
}

/**
 * Clean up orphaned data (remove references to non-existent entities).
 */
fun AppData.cleanupOrphanedData(): AppData {
    val identityIds = sshIdentities.map { it.id }.toSet()
    val cleanedServers = serverProfiles.filter { it.sshIdentityId in identityIds }
    
    val serverIds = cleanedServers.map { it.id }.toSet()
    val cleanedProjects = projects.filter { it.serverProfileId in serverIds }
    
    val projectIds = cleanedProjects.map { it.id }.toSet()
    val cleanedMessages = messages.filterKeys { it in projectIds || it == "system" }
    
    return copy(
        serverProfiles = cleanedServers,
        projects = cleanedProjects,
        messages = cleanedMessages,
        lastModified = System.currentTimeMillis()
    )
}

// ========== Collection Extensions ==========

/**
 * Filter SSH identities by search query.
 */
fun List<SshIdentity>.filterByQuery(query: String): List<SshIdentity> = 
    filter { it.matchesSearch(query) }

/**
 * Filter server profiles by search query.
 */
fun List<ServerProfile>.filterByQuery(query: String): List<ServerProfile> = 
    filter { it.matchesSearch(query) }

/**
 * Filter projects by search query.
 */
fun List<Project>.filterByQuery(query: String): List<Project> = 
    filter { it.matchesSearch(query) }

/**
 * Filter messages by search query.
 */
fun List<Message>.filterByQuery(query: String): List<Message> = 
    filter { it.matchesSearch(query) }

/**
 * Sort SSH identities by usage (recently used first).
 */
fun List<SshIdentity>.sortByUsage(): List<SshIdentity> = 
    sortedWith(compareByDescending<SshIdentity> { it.lastUsedAt ?: 0 }
        .thenBy { it.name })

/**
 * Sort server profiles by connection status and last connected.
 */
fun List<ServerProfile>.sortByStatus(): List<ServerProfile> = 
    sortedWith(compareBy<ServerProfile> { it.status.ordinal }
        .thenByDescending { it.lastConnectedAt ?: 0 }
        .thenBy { it.name })

/**
 * Sort projects by activity (recently active first).
 */
fun List<Project>.sortByActivity(): List<Project> = 
    sortedWith(compareByDescending<Project> { it.lastActiveAt ?: 0 }
        .thenBy { it.name })

/**
 * Sort messages by timestamp (oldest first).
 */
fun List<Message>.sortByTimestamp(): List<Message> = 
    sortedBy { it.timestamp }

/**
 * Get only recently used SSH identities.
 */
fun List<SshIdentity>.recentlyUsed(): List<SshIdentity> = 
    filter { it.isRecentlyUsed() }

/**
 * Get only connected server profiles.
 */
fun List<ServerProfile>.connected(): List<ServerProfile> = 
    filter { it.status == ConnectionStatus.CONNECTED }

/**
 * Get only active projects.
 */
fun List<Project>.active(): List<Project> = 
    filter { it.status == ProjectStatus.ACTIVE }

/**
 * Get only error messages.
 */
fun List<Message>.errors(): List<Message> = 
    filter { it.type == MessageType.ERROR_MESSAGE }

/**
 * Get only user messages.
 */
fun List<Message>.userMessages(): List<Message> = 
    filter { it.type == MessageType.USER_INPUT }

/**
 * Get only Claude messages.
 */
fun List<Message>.claudeMessages(): List<Message> = 
    filter { it.type == MessageType.CLAUDE_RESPONSE }

// ========== Serialization Extensions ==========

/**
 * Serialize to JSON string.
 */
fun AppData.toJson(): String = Json.encodeToString(this)

/**
 * Deserialize from JSON string.
 */
fun String.toAppData(): AppData = Json.decodeFromString(this)

/**
 * Serialize SSH identity to JSON.
 */
fun SshIdentity.toJson(): String = Json.encodeToString(this)

/**
 * Serialize server profile to JSON.
 */
fun ServerProfile.toJson(): String = Json.encodeToString(this)

/**
 * Serialize project to JSON.
 */
fun Project.toJson(): String = Json.encodeToString(this)

/**
 * Serialize message to JSON.
 */
fun Message.toJson(): String = Json.encodeToString(this)

// ========== Transformation Extensions ==========

/**
 * Transform AppData to export format (without sensitive data).
 */
fun AppData.toExportFormat(): AppDataExport = AppDataExport(
    version = version,
    sshIdentities = sshIdentities.map { it.toExportModel() },
    serverProfiles = serverProfiles.map { it.toExportModel() },
    projects = projects.map { it.toExportModel() },
    metadata = metadata,
    exportedAt = System.currentTimeMillis()
)

/**
 * Export format for AppData (without sensitive information).
 */
@kotlinx.serialization.Serializable
data class AppDataExport(
    val version: Int,
    val sshIdentities: List<SshIdentityExport>,
    val serverProfiles: List<ServerProfileExport>,
    val projects: List<ProjectExport>,
    val metadata: AppMetadata,
    val exportedAt: Long
)

/**
 * Create a summary of the current data state.
 */
fun AppData.createSummary(): DataStateSummary = DataStateSummary(
    totalSshIdentities = sshIdentities.size,
    totalServerProfiles = serverProfiles.size,
    totalProjects = projects.size,
    totalMessages = getTotalMessageCount(),
    activeSessions = getActiveProjects().size,
    connectedServers = getConnectedServers().size,
    recentActivity = getRecentProjects().size,
    hasErrors = getProjectsWithErrors().isNotEmpty() || getServersWithErrors().isNotEmpty(),
    lastModified = lastModified
)

/**
 * Data state summary for dashboard display.
 */
data class DataStateSummary(
    val totalSshIdentities: Int,
    val totalServerProfiles: Int,
    val totalProjects: Int,
    val totalMessages: Int,
    val activeSessions: Int,
    val connectedServers: Int,
    val recentActivity: Int,
    val hasErrors: Boolean,
    val lastModified: Long
)

// ========== Validation Extensions ==========

/**
 * Validate SSH identity data.
 */
fun SshIdentity.validate(): Result<Unit> {
    return try {
        SshIdentityValidator.validateName(name).getOrThrow()
        SshIdentityValidator.validateFingerprint(publicKeyFingerprint).getOrThrow()
        SshIdentityValidator.validateDescription(description).getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Validate server profile data.
 */
fun ServerProfile.validate(): Result<Unit> {
    return try {
        ServerProfileValidator.validateName(name).getOrThrow()
        ServerProfileValidator.validateHostname(hostname).getOrThrow()
        ServerProfileValidator.validateUsername(username).getOrThrow()
        ServerProfileValidator.validatePort(port).getOrThrow()
        ServerProfileValidator.validatePort(wrapperPort).getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Validate project data.
 */
fun Project.validate(): Result<Unit> {
    return try {
        ProjectValidator.validateName(name).getOrThrow()
        ProjectValidator.validateProjectPath(projectPath).getOrThrow()
        ProjectValidator.validateScriptsFolder(scriptsFolder).getOrThrow()
        ProjectValidator.validateRepositoryUrl(repositoryUrl).getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Validate message data.
 */
fun Message.validate(): Result<Unit> {
    return try {
        MessageValidator.validateContent(content).getOrThrow()
        MessageValidator.validateMetadata(metadata).getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// ========== Utility Extensions ==========

/**
 * Check if AppData is empty.
 */
fun AppData.isCompletelyEmpty(): Boolean = 
    sshIdentities.isEmpty() && serverProfiles.isEmpty() && projects.isEmpty() && messages.isEmpty()

/**
 * Get the most recently modified timestamp across all entities.
 */
fun AppData.getLastActivityTimestamp(): Long {
    val timestamps = listOfNotNull(
        lastModified,
        sshIdentities.maxOfOrNull { it.lastUsedAt ?: it.createdAt },
        serverProfiles.maxOfOrNull { it.lastConnectedAt ?: it.createdAt },
        projects.maxOfOrNull { it.lastActiveAt ?: it.createdAt },
        messages.values.flatten().maxOfOrNull { it.timestamp }
    )
    return timestamps.maxOrNull() ?: 0
}

/**
 * Create a deep copy of AppData with updated timestamp.
 */
fun AppData.updateTimestamp(): AppData = 
    copy(lastModified = System.currentTimeMillis())

/**
 * Get entity count by type.
 */
fun AppData.getEntityCounts(): Map<String, Int> = mapOf(
    "ssh_identities" to sshIdentities.size,
    "server_profiles" to serverProfiles.size,
    "projects" to projects.size,
    "messages" to getTotalMessageCount()
)

/**
 * Check if any entity needs attention (has errors, is inactive, etc.).
 */
fun AppData.needsAttention(): Boolean = 
    getProjectsWithErrors().isNotEmpty() || 
    getServersWithErrors().isNotEmpty() ||
    getOrphanedProjects().isNotEmpty() ||
    getOrphanedServerProfiles().isNotEmpty()

/**
 * Get health status of the data.
 */
fun AppData.getHealthStatus(): DataHealthStatus = when {
    needsAttention() -> DataHealthStatus.NEEDS_ATTENTION
    isCompletelyEmpty() -> DataHealthStatus.EMPTY
    else -> DataHealthStatus.HEALTHY
}

/**
 * Data health status enumeration.
 */
enum class DataHealthStatus {
    HEALTHY,
    NEEDS_ATTENTION,
    EMPTY
}