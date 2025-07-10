package com.pocketagent.data.repository

import com.pocketagent.data.models.Message
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extension functions for SecureDataRepository to provide additional utility methods.
 * 
 * These extensions provide convenient access patterns and common operations
 * that build upon the core repository functionality.
 */

// SSH Identity Extensions

/**
 * Gets SSH identities by name pattern.
 * 
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching SSH identities
 */
suspend fun SecureDataRepository.getSshIdentitiesByNamePattern(namePattern: String): List<SshIdentity> {
    return getAllSshIdentities().filter { 
        it.name.contains(namePattern, ignoreCase = true) 
    }
}

/**
 * Gets SSH identity by name.
 * 
 * @param name The SSH identity name
 * @return The SSH identity or null if not found
 */
suspend fun SecureDataRepository.getSshIdentityByName(name: String): SshIdentity? {
    return getAllSshIdentities().find { it.name == name }
}

/**
 * Gets the most recently used SSH identity.
 * 
 * @return The most recently used SSH identity or null if none have been used
 */
suspend fun SecureDataRepository.getMostRecentSshIdentity(): SshIdentity? {
    return getAllSshIdentities()
        .filter { it.lastUsedAt != null }
        .maxByOrNull { it.lastUsedAt!! }
}

/**
 * Updates the last used timestamp for an SSH identity.
 * 
 * @param identityId The SSH identity ID
 */
suspend fun SecureDataRepository.updateSshIdentityLastUsed(identityId: String) {
    val identity = getSshIdentityById(identityId)
    if (identity != null) {
        updateSshIdentity(identity.copy(lastUsedAt = System.currentTimeMillis()))
    }
}

// Server Profile Extensions

/**
 * Gets server profiles by name pattern.
 * 
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching server profiles
 */
suspend fun SecureDataRepository.getServerProfilesByNamePattern(namePattern: String): List<ServerProfile> {
    return getAllServerProfiles().filter { 
        it.name.contains(namePattern, ignoreCase = true) 
    }
}

/**
 * Gets server profile by name.
 * 
 * @param name The server profile name
 * @return The server profile or null if not found
 */
suspend fun SecureDataRepository.getServerProfileByName(name: String): ServerProfile? {
    return getAllServerProfiles().find { it.name == name }
}

/**
 * Gets server profiles by hostname.
 * 
 * @param hostname The hostname to match
 * @return List of server profiles with matching hostname
 */
suspend fun SecureDataRepository.getServerProfilesByHostname(hostname: String): List<ServerProfile> {
    return getAllServerProfiles().filter { it.hostname == hostname }
}

/**
 * Gets the most recently connected server profile.
 * 
 * @return The most recently connected server profile or null if none have been connected
 */
suspend fun SecureDataRepository.getMostRecentServerProfile(): ServerProfile? {
    return getAllServerProfiles()
        .filter { it.lastConnectedAt != null }
        .maxByOrNull { it.lastConnectedAt!! }
}

/**
 * Updates the last connected timestamp for a server profile.
 * 
 * @param profileId The server profile ID
 */
suspend fun SecureDataRepository.updateServerProfileLastConnected(profileId: String) {
    val profile = getServerProfileById(profileId)
    if (profile != null) {
        updateServerProfile(profile.copy(lastConnectedAt = System.currentTimeMillis()))
    }
}

/**
 * Updates the connection status for a server profile.
 * 
 * @param profileId The server profile ID
 * @param status The new connection status
 */
suspend fun SecureDataRepository.updateServerProfileStatus(
    profileId: String, 
    status: com.pocketagent.data.models.ConnectionStatus
) {
    val profile = getServerProfileById(profileId)
    if (profile != null) {
        updateServerProfile(profile.copy(status = status))
    }
}

// Project Extensions

/**
 * Gets projects by name pattern.
 * 
 * @param namePattern Pattern to match (case-insensitive)
 * @return List of matching projects
 */
suspend fun SecureDataRepository.getProjectsByNamePattern(namePattern: String): List<Project> {
    return getAllProjects().filter { 
        it.name.contains(namePattern, ignoreCase = true) 
    }
}

/**
 * Gets project by name.
 * 
 * @param name The project name
 * @return The project or null if not found
 */
suspend fun SecureDataRepository.getProjectByName(name: String): Project? {
    return getAllProjects().find { it.name == name }
}

/**
 * Gets projects by status.
 * 
 * @param status The project status to filter by
 * @return List of projects with matching status
 */
suspend fun SecureDataRepository.getProjectsByStatus(status: ProjectStatus): List<Project> {
    return getAllProjects().filter { it.status == status }
}

/**
 * Gets active projects.
 * 
 * @return List of active projects
 */
suspend fun SecureDataRepository.getActiveProjects(): List<Project> {
    return getProjectsByStatus(ProjectStatus.ACTIVE)
}

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
 */
suspend fun SecureDataRepository.updateProjectLastActive(projectId: String) {
    val project = getProjectById(projectId)
    if (project != null) {
        updateProject(project.copy(lastActiveAt = System.currentTimeMillis()))
    }
}

/**
 * Updates the status for a project.
 * 
 * @param projectId The project ID
 * @param status The new project status
 */
suspend fun SecureDataRepository.updateProjectStatus(projectId: String, status: ProjectStatus) {
    val project = getProjectById(projectId)
    if (project != null) {
        updateProject(project.copy(status = status))
    }
}

/**
 * Updates the Claude session ID for a project.
 * 
 * @param projectId The project ID
 * @param sessionId The Claude session ID
 */
suspend fun SecureDataRepository.updateProjectSessionId(projectId: String, sessionId: String?) {
    val project = getProjectById(projectId)
    if (project != null) {
        updateProject(project.copy(claudeSessionId = sessionId))
    }
}

/**
 * Updates the last error for a project.
 * 
 * @param projectId The project ID
 * @param error The error message or null to clear
 */
suspend fun SecureDataRepository.updateProjectError(projectId: String, error: String?) {
    val project = getProjectById(projectId)
    if (project != null) {
        updateProject(project.copy(lastError = error))
    }
}

// Message Extensions

/**
 * Gets the latest message for a project.
 * 
 * @param projectId The project ID
 * @return The latest message or null if no messages exist
 */
suspend fun SecureDataRepository.getLatestMessage(projectId: String): Message? {
    return getProjectMessages(projectId, 1).lastOrNull()
}

/**
 * Gets message count for a project.
 * 
 * @param projectId The project ID
 * @return Number of messages in the project
 */
suspend fun SecureDataRepository.getMessageCount(projectId: String): Int {
    return loadData().messages[projectId]?.size ?: 0
}

/**
 * Gets messages by type for a project.
 * 
 * @param projectId The project ID
 * @param type The message type to filter by
 * @return List of messages with matching type
 */
suspend fun SecureDataRepository.getMessagesByType(
    projectId: String, 
    type: com.pocketagent.data.models.MessageType
): List<Message> {
    return getProjectMessages(projectId).filter { it.type == type }
}

/**
 * Gets messages since a specific timestamp for a project.
 * 
 * @param projectId The project ID
 * @param since Timestamp to filter messages after
 * @return List of messages since the timestamp
 */
suspend fun SecureDataRepository.getMessagesSince(projectId: String, since: Long): List<Message> {
    return getProjectMessages(projectId).filter { it.timestamp > since }
}

/**
 * Checks if a project has any messages.
 * 
 * @param projectId The project ID
 * @return true if project has messages, false otherwise
 */
suspend fun SecureDataRepository.hasMessages(projectId: String): Boolean {
    return getMessageCount(projectId) > 0
}

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
fun SecureDataRepository.observeSshIdentitiesFiltered(
    filter: (SshIdentity) -> Boolean
): Flow<List<SshIdentity>> {
    return observeSshIdentities().map { identities ->
        identities.filter(filter)
    }
}

/**
 * Observes server profiles with filtering.
 * 
 * @param filter Function to filter server profiles
 * @return Flow of filtered server profiles
 */
fun SecureDataRepository.observeServerProfilesFiltered(
    filter: (ServerProfile) -> Boolean
): Flow<List<ServerProfile>> {
    return observeServerProfiles().map { profiles ->
        profiles.filter(filter)
    }
}

/**
 * Observes projects with filtering.
 * 
 * @param filter Function to filter projects
 * @return Flow of filtered projects
 */
fun SecureDataRepository.observeProjectsFiltered(
    filter: (Project) -> Boolean
): Flow<List<Project>> {
    return observeProjects().map { projects ->
        projects.filter(filter)
    }
}

/**
 * Observes active projects.
 * 
 * @return Flow of active projects
 */
fun SecureDataRepository.observeActiveProjects(): Flow<List<Project>> {
    return observeProjectsFiltered { it.status == ProjectStatus.ACTIVE }
}

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
fun SecureDataRepository.observeMessageCount(projectId: String): Flow<Int> {
    return observeProjectMessages(projectId).map { messages ->
        messages.size
    }
}

/**
 * Observes data summary statistics.
 * 
 * @return Flow of data summary
 */
fun SecureDataRepository.observeDataSummary(): Flow<SecureDataRepository.DataSummary> {
    return dataFlow.map { data ->
        if (data != null) {
            SecureDataRepository.DataSummary(
                sshIdentityCount = data.sshIdentities.size,
                serverProfileCount = data.serverProfiles.size,
                projectCount = data.projects.size,
                totalMessageCount = data.messages.values.sumOf { it.size },
                lastModified = data.lastModified
            )
        } else {
            SecureDataRepository.DataSummary(
                sshIdentityCount = 0,
                serverProfileCount = 0,
                projectCount = 0,
                totalMessageCount = 0,
                lastModified = 0L
            )
        }
    }
}