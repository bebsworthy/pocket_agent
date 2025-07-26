package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gets SSH identities by name pattern.
 *
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching SSH identities
 */
suspend fun SecureDataRepository.getSshIdentitiesByNamePattern(namePattern: String): List<SshIdentity> =
    getAllSshIdentities().filter {
        it.name.contains(namePattern, ignoreCase = true)
    }

/**
 * Gets SSH identity by name.
 *
 * @param name The SSH identity name
 * @return The SSH identity or null if not found
 */
suspend fun SecureDataRepository.getSshIdentityByName(name: String): SshIdentity? = getAllSshIdentities().find { it.name == name }

/**
 * Gets the most recently used SSH identity.
 *
 * @return The most recently used SSH identity or null if none have been used
 */
suspend fun SecureDataRepository.getMostRecentSshIdentity(): SshIdentity? =
    getAllSshIdentities()
        .filter { it.lastUsedAt != null }
        .maxByOrNull { it.lastUsedAt!! }

/**
 * Updates the last used timestamp for an SSH identity.
 *
 * @param identityId The SSH identity ID
 * @throws DataException.EntityNotFoundException if identity not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateSshIdentityLastUsed(identityId: String) {
    try {
        val identity = getSshIdentityById(identityId)
        if (identity != null) {
            updateSshIdentity(identity.copy(lastUsedAt = System.currentTimeMillis()))
        } else {
            Log.w("SecureDataRepoExt", "SSH identity not found for lastUsed update: $identityId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update SSH identity last used timestamp", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for SSH identity last used update", e)
        throw DataException.ValidationException("Failed to update SSH identity last used - invalid arguments: ${e.message}", e)
    }
}

// Server Profile Extensions

/**
 * Gets server profiles by name pattern.
 *
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching server profiles
 */
suspend fun SecureDataRepository.getServerProfilesByNamePattern(namePattern: String): List<ServerProfile> =
    getAllServerProfiles().filter {
        it.name.contains(namePattern, ignoreCase = true)
    }

/**
 * Gets server profile by name.
 *
 * @param name The server profile name
 * @return The server profile or null if not found
 */
suspend fun SecureDataRepository.getServerProfileByName(name: String): ServerProfile? = getAllServerProfiles().find { it.name == name }

/**
 * Gets server profiles by hostname.
 *
 * @param hostname The hostname to match
 * @return List of server profiles with matching hostname
 */
suspend fun SecureDataRepository.getServerProfilesByHostname(hostname: String): List<ServerProfile> =
    getAllServerProfiles().filter { it.hostname == hostname }

/**
 * Gets the most recently connected server profile.
 *
 * @return The most recently connected server profile or null if none have been connected
 */
suspend fun SecureDataRepository.getMostRecentServerProfile(): ServerProfile? =
    getAllServerProfiles()
        .filter { it.lastConnectedAt != null }
        .maxByOrNull { it.lastConnectedAt!! }

/**
 * Updates the last connected timestamp for a server profile.
 *
 * @param profileId The server profile ID
 * @throws DataException.EntityNotFoundException if profile not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateServerProfileLastConnected(profileId: String) {
    try {
        val profile = getServerProfileById(profileId)
        if (profile != null) {
            updateServerProfile(profile.copy(lastConnectedAt = System.currentTimeMillis()))
        } else {
            Log.w("SecureDataRepoExt", "Server profile not found for lastConnected update: $profileId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update server profile last connected timestamp", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for server profile last connected update", e)
        throw DataException.ValidationException("Failed to update server profile last connected - invalid arguments: ${e.message}", e)
    }
}

/**
 * Updates the connection status for a server profile.
 *
 * @param profileId The server profile ID
 * @param status The new connection status
 * @throws DataException.EntityNotFoundException if profile not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateServerProfileStatus(
    profileId: String,
    status: com.pocketagent.data.models.ConnectionStatus,
) {
    try {
        val profile = getServerProfileById(profileId)
        if (profile != null) {
            updateServerProfile(profile.copy(status = status))
        } else {
            Log.w("SecureDataRepoExt", "Server profile not found for status update: $profileId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update server profile status", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for server profile status update", e)
        throw DataException.ValidationException("Failed to update server profile status - invalid arguments: ${e.message}", e)
    }
}

// Project Extensions

/**
 * Gets projects by name pattern.
 *
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching projects
 */
suspend fun SecureDataRepository.getProjectsByNamePattern(namePattern: String): List<Project> =
    getAllProjects().filter {
        it.name.contains(namePattern, ignoreCase = true)
    }

/**
 * Gets project by name.
 *
 * @param name The project name
 * @return The project or null if not found
 */
suspend fun SecureDataRepository.getProjectByName(name: String): Project? = getAllProjects().find { it.name == name }

/**
 * Gets projects by status.
 *
 * @param status The project status to filter by
 * @return List of projects with matching status
 */
suspend fun SecureDataRepository.getProjectsByStatus(status: ProjectStatus): List<Project> = getAllProjects().filter { it.status == status }

/**
 * Gets active projects.
 *
 * @return List of active projects
 */
suspend fun SecureDataRepository.getActiveProjects(): List<Project> = getProjectsByStatus(ProjectStatus.ACTIVE)

/**
 * Gets projects with recent activity (last 7 days).
 *
 * @return List of recently active projects
 */
suspend fun SecureDataRepository.getRecentlyActiveProjects(): List<Project> {
    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
    return getAllProjects().filter {
        (it.lastActiveAt ?: it.createdAt) > sevenDaysAgo
    }
}

/**
 * Updates the last active timestamp for a project.
 *
 * @param projectId The project ID
 * @throws DataException.EntityNotFoundException if project not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateProjectLastActive(projectId: String) {
    try {
        val project = getProjectById(projectId)
        if (project != null) {
            updateProject(project.copy(lastActiveAt = System.currentTimeMillis()))
        } else {
            Log.w("SecureDataRepoExt", "Project not found for lastActive update: $projectId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update project last active timestamp", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for project last active update", e)
        throw DataException.ValidationException("Failed to update project last active - invalid arguments: ${e.message}", e)
    }
}

/**
 * Updates the status for a project.
 *
 * @param projectId The project ID
 * @param status The new project status
 * @throws DataException.EntityNotFoundException if project not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateProjectStatus(
    projectId: String,
    status: ProjectStatus,
) {
    try {
        val project = getProjectById(projectId)
        if (project != null) {
            updateProject(project.copy(status = status))
        } else {
            Log.w("SecureDataRepoExt", "Project not found for status update: $projectId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update project status", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for project status update", e)
        throw DataException.ValidationException("Failed to update project status - invalid arguments: ${e.message}", e)
    }
}

/**
 * Updates the Claude session ID for a project.
 *
 * @param projectId The project ID
 * @param sessionId The Claude session ID
 * @throws DataException.EntityNotFoundException if project not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateProjectSessionId(
    projectId: String,
    sessionId: String?,
) {
    try {
        val project = getProjectById(projectId)
        if (project != null) {
            updateProject(project.copy(claudeSessionId = sessionId))
        } else {
            Log.w("SecureDataRepoExt", "Project not found for session ID update: $projectId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update project session ID", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for project session ID update", e)
        throw DataException.ValidationException("Failed to update project session ID - invalid arguments: ${e.message}", e)
    }
}

/**
 * Updates the last error for a project.
 *
 * @param projectId The project ID
 * @param error The error message or null to clear
 * @throws DataException.EntityNotFoundException if project not found
 * @throws DataException.ValidationException if update fails
 */
suspend fun SecureDataRepository.updateProjectError(
    projectId: String,
    error: String?,
) {
    try {
        val project = getProjectById(projectId)
        if (project != null) {
            updateProject(project.copy(lastError = error))
        } else {
            Log.w("SecureDataRepoExt", "Project not found for error update: $projectId")
        }
    } catch (e: DataException) {
        Log.e("SecureDataRepoExt", "Failed to update project error", e)
        throw e
    } catch (e: IllegalArgumentException) {
        Log.e("SecureDataRepoExt", "Invalid arguments for project error update", e)
        throw DataException.ValidationException("Failed to update project error - invalid arguments: ${e.message}", e)
    }
}

// Message Extensions

/**
 * Gets the latest message for a project.
 *
 * @param projectId The project ID
 * @return The latest message or null if no messages exist
 */
suspend fun SecureDataRepository.getLatestMessage(projectId: String): Message? = getProjectMessages(projectId, 1).lastOrNull()

/**
 * Gets message count for a project.
 *
 * @param projectId The project ID
 * @return Number of messages in the project
 */
suspend fun SecureDataRepository.getMessageCount(projectId: String): Int = loadData().messages[projectId]?.size ?: 0

/**
 * Gets messages by type for a project.
 *
 * @param projectId The project ID
 * @param type The message type to filter by
 * @return List of messages with matching type
 */
suspend fun SecureDataRepository.getMessagesByType(
    projectId: String,
    type: com.pocketagent.data.models.MessageType,
): List<Message> = getProjectMessages(projectId).filter { it.type == type }

/**
 * Gets messages since a specific timestamp for a project.
 *
 * @param projectId The project ID
 * @param since Timestamp to filter messages after
 * @return List of messages since the timestamp
 */
suspend fun SecureDataRepository.getMessagesSince(
    projectId: String,
    since: Long,
): List<Message> = getProjectMessages(projectId).filter { it.timestamp > since }

/**
 * Checks if a project has any messages.
 *
 * @param projectId The project ID
 * @return true if project has messages, false otherwise
 */
suspend fun SecureDataRepository.hasMessages(projectId: String): Boolean = getMessageCount(projectId) > 0

// Relationship Extensions

/**
 * Gets server profiles with their associated SSH identity.
 *
 * @return List of pairs containing server profile and SSH identity
 */
suspend fun SecureDataRepository.getServerProfilesWithIdentity(): List<Pair<ServerProfile, SshIdentity>> {
    val identities = getAllSshIdentities().associateBy { it.id }
    return getAllServerProfiles().mapNotNull { profile ->
        identities[profile.sshIdentityId]?.let { identity ->
            profile to identity
        }
    }
}

/**
 * Gets projects with their associated server profile.
 *
 * @return List of pairs containing project and server profile
 */
suspend fun SecureDataRepository.getProjectsWithServerProfile(): List<Pair<Project, ServerProfile>> {
    val profiles = getAllServerProfiles().associateBy { it.id }
    return getAllProjects().mapNotNull { project ->
        profiles[project.serverProfileId]?.let { profile ->
            project to profile
        }
    }
}

/**
 * Gets the full hierarchy: project -> server profile -> SSH identity.
 *
 * @return List of triples containing project, server profile, and SSH identity
 */
suspend fun SecureDataRepository.getProjectHierarchy(): List<Triple<Project, ServerProfile, SshIdentity>> {
    val identities = getAllSshIdentities().associateBy { it.id }
    val profiles = getAllServerProfiles().associateBy { it.id }

    return getAllProjects().mapNotNull { project ->
        val profile = profiles[project.serverProfileId]
        val identity = profile?.let { identities[it.sshIdentityId] }

        if (profile != null && identity != null) {
            Triple(project, profile, identity)
        } else {
            null
        }
    }
}

// Observable Extensions

/**
 * Observes SSH identities with filtering.
 *
 * @param filter Function to filter SSH identities
 * @return Flow of filtered SSH identities
 */
fun SecureDataRepository.observeSshIdentitiesFiltered(filter: (SshIdentity) -> Boolean): Flow<List<SshIdentity>> =
    observeSshIdentities().map { identities ->
        identities.filter(filter)
    }

/**
 * Observes server profiles with filtering.
 *
 * @param filter Function to filter server profiles
 * @return Flow of filtered server profiles
 */
fun SecureDataRepository.observeServerProfilesFiltered(filter: (ServerProfile) -> Boolean): Flow<List<ServerProfile>> =
    observeServerProfiles().map { profiles ->
        profiles.filter(filter)
    }

/**
 * Observes projects with filtering.
 *
 * @param filter Function to filter projects
 * @return Flow of filtered projects
 */
fun SecureDataRepository.observeProjectsFiltered(filter: (Project) -> Boolean): Flow<List<Project>> =
    observeProjects().map { projects ->
        projects.filter(filter)
    }

/**
 * Observes active projects.
 *
 * @return Flow of active projects
 */
fun SecureDataRepository.observeActiveProjects(): Flow<List<Project>> = observeProjectsFiltered { it.status == ProjectStatus.ACTIVE }

/**
 * Observes recent projects.
 *
 * @param days Number of days to consider as recent
 * @return Flow of recent projects
 */
fun SecureDataRepository.observeRecentProjects(days: Int = 7): Flow<List<Project>> {
    val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
    return observeProjectsFiltered {
        (it.lastActiveAt ?: it.createdAt) > cutoffTime
    }
}

/**
 * Observes message count for a project.
 *
 * @param projectId The project ID
 * @return Flow of message count
 */
fun SecureDataRepository.observeMessageCount(projectId: String): Flow<Int> =
    observeProjectMessages(projectId).map { messages ->
        messages.size
    }

/**
 * Observes data summary statistics.
 *
 * @return Flow of data summary
 */
fun SecureDataRepository.observeDataSummary(): Flow<SecureDataRepository.DataSummary> =
    dataFlow.map { data ->
        if (data != null) {
            SecureDataRepository.DataSummary(
                sshIdentityCount = data.sshIdentities.size,
                serverProfileCount = data.serverProfiles.size,
                projectCount = data.projects.size,
                totalMessageCount = data.messages.values.sumOf { it.size },
                lastModified = data.lastModified,
            )
        } else {
            SecureDataRepository.DataSummary(
                sshIdentityCount = 0,
                serverProfileCount = 0,
                projectCount = 0,
                totalMessageCount = 0,
                lastModified = 0L,
            )
        }
    }
