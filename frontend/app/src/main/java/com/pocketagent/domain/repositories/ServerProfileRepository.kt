package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.ConnectionStatus
import com.pocketagent.domain.models.entities.ServerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for server profile management.
 *
 * This interface defines the contract for managing server profiles,
 * including CRUD operations, connection state management, and SSH authentication.
 *
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface ServerProfileRepository {
    /**
     * Retrieves all server profiles ordered by name.
     *
     * @return Flow emitting a list of server profiles
     */
    fun getAllProfiles(): Flow<Result<List<ServerProfile>>>

    /**
     * Observes server profiles with real-time updates.
     *
     * @return Flow emitting server profile list updates
     */
    fun observeProfiles(): Flow<List<ServerProfile>>

    /**
     * Retrieves a server profile by ID.
     *
     * @param id The server profile ID
     * @return The server profile if found
     */
    suspend fun getProfileById(id: String): Result<ServerProfile?>

    /**
     * Observes a specific server profile for real-time updates.
     *
     * @param id The server profile ID
     * @return Flow emitting server profile updates
     */
    fun observeProfile(id: String): Flow<ServerProfile?>

    /**
     * Creates a new server profile.
     * Validates that the SSH identity exists.
     *
     * @param profile The server profile to create
     * @return The created server profile
     */
    suspend fun createProfile(profile: ServerProfile): Result<ServerProfile>

    /**
     * Updates an existing server profile.
     *
     * @param profile The server profile to update
     * @return The updated server profile
     */
    suspend fun updateProfile(profile: ServerProfile): Result<ServerProfile>

    /**
     * Updates the last connected timestamp for a server profile.
     *
     * @param id The server profile ID
     * @return Success or error result
     */
    suspend fun updateLastConnected(id: String): Result<Unit>

    /**
     * Deletes a server profile.
     * Will fail if the profile is in use by projects.
     *
     * @param id The server profile ID to delete
     * @return Success or error result
     */
    suspend fun deleteProfile(id: String): Result<Unit>

    /**
     * Batch deletes multiple server profiles.
     * Will fail if any profile is in use.
     *
     * @param ids The server profile IDs to delete
     * @return Success or error result with failed IDs
     */
    suspend fun deleteProfiles(ids: List<String>): Result<List<String>>

    /**
     * Tests connection to a server profile.
     * Validates SSH key authentication and network connectivity.
     *
     * @param id The server profile ID
     * @return Connection test result with details
     */
    suspend fun testConnection(id: String): Result<ConnectionTestResult>

    /**
     * Tests SSH authentication without full connection.
     *
     * @param id The server profile ID
     * @return True if SSH authentication is successful
     */
    suspend fun testAuthentication(id: String): Result<Boolean>

    /**
     * Retrieves server profiles by SSH identity ID.
     *
     * @param sshIdentityId The SSH identity ID
     * @return List of server profiles using the SSH identity
     */
    suspend fun getProfilesBySshIdentity(sshIdentityId: String): Result<List<ServerProfile>>

    /**
     * Updates the connection status of a server profile.
     *
     * @param id The server profile ID
     * @param status The new connection status
     * @return The updated server profile
     */
    suspend fun updateConnectionStatus(
        id: String,
        status: ConnectionStatus,
    ): Result<ServerProfile>

    /**
     * Observes connection status changes for a server profile.
     *
     * @param id The server profile ID
     * @return Flow emitting connection status updates
     */
    fun observeConnectionStatus(id: String): Flow<ConnectionStatus>

    /**
     * Searches server profiles by name or hostname.
     *
     * @param query The search query
     * @return List of matching server profiles
     */
    suspend fun searchProfiles(query: String): Result<List<ServerProfile>>

    /**
     * Gets server profiles sorted by last connected time.
     *
     * @param limit Maximum number of profiles to return
     * @return List of recently connected server profiles
     */
    suspend fun getRecentProfiles(limit: Int = 5): Result<List<ServerProfile>>

    /**
     * Validates server profile configuration.
     *
     * @param profile The server profile to validate
     * @return True if the profile configuration is valid
     */
    suspend fun validateProfile(profile: ServerProfile): Result<Boolean>

    /**
     * Validates hostname format and reachability.
     *
     * @param hostname The hostname to validate
     * @return True if hostname is valid and reachable
     */
    suspend fun validateHostname(hostname: String): Result<Boolean>

    /**
     * Validates port availability.
     *
     * @param hostname The hostname
     * @param port The port number
     * @return True if port is available
     */
    suspend fun validatePort(
        hostname: String,
        port: Int,
    ): Result<Boolean>

    /**
     * Exports server profile data (without sensitive information).
     *
     * @param id The server profile ID
     * @return Exported profile data as JSON
     */
    suspend fun exportProfile(id: String): Result<String>

    /**
     * Imports server profile from exported data.
     *
     * @param data The exported profile data
     * @return The imported server profile
     */
    suspend fun importProfile(data: String): Result<ServerProfile>

    /**
     * Checks if a server profile name already exists.
     *
     * @param name The profile name to check
     * @param excludeId Optional ID to exclude from check (for updates)
     * @return True if name exists
     */
    suspend fun isNameExists(
        name: String,
        excludeId: String? = null,
    ): Result<Boolean>

    /**
     * Gets connection statistics for server profiles.
     *
     * @return Map of profile ID to connection count
     */
    suspend fun getConnectionStatistics(): Result<Map<String, Int>>

    /**
     * Duplicates a server profile with a new name.
     *
     * @param id The server profile ID to duplicate
     * @param newName The new profile name
     * @return The duplicated server profile
     */
    suspend fun duplicateProfile(
        id: String,
        newName: String,
    ): Result<ServerProfile>

    /**
     * Synchronizes server profiles with encrypted storage.
     * Used for offline/online synchronization.
     *
     * @return Success or error result
     */
    suspend fun syncProfiles(): Result<Unit>

    /**
     * Clears all server profiles (for logout/reset).
     *
     * @return Success or error result
     */
    suspend fun clearAll(): Result<Unit>
}

/**
 * Represents the result of a connection test.
 *
 * @property success Whether the connection was successful
 * @property message Status message
 * @property responseTime Connection response time in milliseconds
 * @property serverVersion Optional server version information
 * @property sshKeyAccepted Whether SSH key authentication was accepted
 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val responseTime: Long,
    val serverVersion: String? = null,
    val sshKeyAccepted: Boolean = false,
)
