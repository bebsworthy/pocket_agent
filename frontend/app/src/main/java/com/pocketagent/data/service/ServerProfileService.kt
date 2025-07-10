package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ServerProfileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive service for Server Profile CRUD operations and management.
 * 
 * This service provides a high-level interface for managing server profiles with
 * features including:
 * - Complete CRUD operations with validation
 * - Connection testing and validation
 * - SSH identity association and management
 * - Server status tracking and monitoring
 * - Network configuration validation
 * - Search and filtering capabilities
 * - Usage tracking and relationship management with projects
 * - Import/export capabilities
 * 
 * The service integrates with the existing SecureDataRepository, validation
 * framework, and SSH identity service to provide comprehensive server management.
 */
@Singleton
class ServerProfileService @Inject constructor(
    private val repository: SecureDataRepository,
    private val validator: ServerProfileValidator,
    private val sshIdentityService: SshIdentityService,
    private val connectionTester: ServerConnectionTester,
    private val networkValidator: NetworkConfigurationValidator
) {
    
    companion object {
        private const val TAG = "ServerProfileService"
        private const val DEFAULT_SEARCH_LIMIT = 50
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val MAX_CONCURRENT_TESTS = 5
    }
    
    // CRUD Operations
    
    /**
     * Creates a new server profile with comprehensive validation.
     * 
     * @param name Display name for the server profile
     * @param hostname Server hostname or IP address
     * @param port SSH port number (default: 22)
     * @param username SSH username
     * @param sshIdentityId Associated SSH identity ID
     * @param wrapperPort Port for Claude Code wrapper service (default: 8080)
     * @param description Optional description
     * @param testConnection Whether to test connection before creating
     * @return Result with created server profile or error
     */
    suspend fun createServerProfile(
        name: String,
        hostname: String,
        port: Int = 22,
        username: String,
        sshIdentityId: String,
        wrapperPort: Int = 8080,
        description: String? = null,
        testConnection: Boolean = false
    ): ServiceResult<ServerProfile> {
        Log.d(TAG, "Creating server profile: $name")
        
        return try {
            withContext(Dispatchers.Default) {
                // Validate SSH identity exists
                val sshIdentityResult = sshIdentityService.getSshIdentity(sshIdentityId)
                if (sshIdentityResult.isFailure) {
                    return@withContext ServiceResult.failure("SSH Identity not found: ${sshIdentityResult.getErrorOrNull()}")
                }
                
                // Create the server profile
                val profile = ServerProfile(
                    name = name,
                    hostname = hostname,
                    port = port,
                    username = username,
                    sshIdentityId = sshIdentityId,
                    wrapperPort = wrapperPort
                )
                
                // Validate the profile
                val validationResult = validator.validateForCreation(profile)
                if (!validationResult.isValid) {
                    return@withContext ServiceResult.failure("Validation failed: ${validationResult.getErrorSummary()}")
                }
                
                // Check name uniqueness
                val existingProfiles = repository.getAllServerProfiles()
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingProfiles.map { it.name }
                )
                if (!nameValidation.isValid) {
                    return@withContext ServiceResult.failure("Server profile name already exists")
                }
                
                // Check hostname/port uniqueness
                val hostnamePortValidation = validator.validateHostnamePortUniqueness(
                    hostname,
                    port,
                    existingProfiles.map { it.hostname to it.port }
                )
                if (!hostnamePortValidation.isValid) {
                    return@withContext ServiceResult.failure("Server with same hostname and port already exists")
                }
                
                // Validate network configuration
                val networkValidation = networkValidator.validateConfiguration(hostname, port, wrapperPort)
                if (!networkValidation.isValid) {
                    return@withContext ServiceResult.failure("Network configuration invalid: ${networkValidation.getErrorSummary()}")
                }
                
                // Test connection if requested
                var finalProfile = profile
                if (testConnection) {
                    val connectionResult = testConnection(profile)
                    finalProfile = when (connectionResult.connectionStatus) {
                        ConnectionTestResult.Status.SUCCESS -> profile.markAsConnected()
                        ConnectionTestResult.Status.FAILED -> profile.copy(status = ConnectionStatus.ERROR)
                        ConnectionTestResult.Status.TIMEOUT -> profile.copy(status = ConnectionStatus.ERROR)
                    }
                }
                
                // Save to repository
                repository.addServerProfile(finalProfile)
                
                // Mark SSH identity as used
                sshIdentityService.markAsUsed(sshIdentityId)
                
                Log.d(TAG, "Server profile created successfully: $name")
                ServiceResult.success(finalProfile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create server profile", e)
            ServiceResult.failure("Failed to create server profile: ${e.message}")
        }
    }
    
    /**
     * Retrieves a server profile by ID.
     */
    suspend fun getServerProfile(id: String): ServiceResult<ServerProfile> {
        Log.d(TAG, "Getting server profile: $id")
        
        return try {
            val profile = repository.getServerProfileById(id)
            if (profile != null) {
                ServiceResult.success(profile)
            } else {
                ServiceResult.failure("Server profile not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get server profile", e)
            ServiceResult.failure("Failed to retrieve server profile: ${e.message}")
        }
    }
    
    /**
     * Updates an existing server profile.
     */
    suspend fun updateServerProfile(
        id: String,
        name: String? = null,
        hostname: String? = null,
        port: Int? = null,
        username: String? = null,
        sshIdentityId: String? = null,
        wrapperPort: Int? = null,
        description: String? = null,
        testConnectionAfterUpdate: Boolean = false
    ): ServiceResult<ServerProfile> {
        Log.d(TAG, "Updating server profile: $id")
        
        return try {
            val existing = repository.getServerProfileById(id)
                ?: return ServiceResult.failure("Server profile not found")
            
            // Validate SSH identity if it's being changed
            if (sshIdentityId != null && sshIdentityId != existing.sshIdentityId) {
                val sshIdentityResult = sshIdentityService.getSshIdentity(sshIdentityId)
                if (sshIdentityResult.isFailure) {
                    return ServiceResult.failure("SSH Identity not found: ${sshIdentityResult.getErrorOrNull()}")
                }
            }
            
            val updated = existing.copy(
                name = name ?: existing.name,
                hostname = hostname ?: existing.hostname,
                port = port ?: existing.port,
                username = username ?: existing.username,
                sshIdentityId = sshIdentityId ?: existing.sshIdentityId,
                wrapperPort = wrapperPort ?: existing.wrapperPort
            )
            
            // Validate the update
            val validationResult = validator.validateForUpdate(existing, updated)
            if (!validationResult.isValid) {
                return ServiceResult.failure("Validation failed: ${validationResult.getErrorSummary()}")
            }
            
            // Check name uniqueness if name changed
            if (name != null && name != existing.name) {
                val existingProfiles = repository.getAllServerProfiles()
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingProfiles.map { it.name },
                    excludeId = id
                )
                if (!nameValidation.isValid) {
                    return ServiceResult.failure("Server profile name already exists")
                }
            }
            
            // Check hostname/port uniqueness if either changed
            if ((hostname != null && hostname != existing.hostname) || 
                (port != null && port != existing.port)) {
                val existingProfiles = repository.getAllServerProfiles()
                val hostnamePortValidation = validator.validateHostnamePortUniqueness(
                    updated.hostname,
                    updated.port,
                    existingProfiles.map { it.hostname to it.port },
                    excludeId = id
                )
                if (!hostnamePortValidation.isValid) {
                    return ServiceResult.failure("Server with same hostname and port already exists")
                }
            }
            
            // Validate network configuration if connection details changed
            if ((hostname != null && hostname != existing.hostname) ||
                (port != null && port != existing.port) ||
                (wrapperPort != null && wrapperPort != existing.wrapperPort)) {
                val networkValidation = networkValidator.validateConfiguration(
                    updated.hostname, 
                    updated.port, 
                    updated.wrapperPort
                )
                if (!networkValidation.isValid) {
                    return ServiceResult.failure("Network configuration invalid: ${networkValidation.getErrorSummary()}")
                }
            }
            
            var finalProfile = updated
            
            // Test connection if requested and connection details changed
            if (testConnectionAfterUpdate && 
                ((hostname != null && hostname != existing.hostname) ||
                 (port != null && port != existing.port) ||
                 (username != null && username != existing.username) ||
                 (sshIdentityId != null && sshIdentityId != existing.sshIdentityId))) {
                
                val connectionResult = testConnection(updated)
                finalProfile = when (connectionResult.connectionStatus) {
                    ConnectionTestResult.Status.SUCCESS -> updated.markAsConnected()
                    ConnectionTestResult.Status.FAILED -> updated.copy(status = ConnectionStatus.ERROR)
                    ConnectionTestResult.Status.TIMEOUT -> updated.copy(status = ConnectionStatus.ERROR)
                }
            }
            
            repository.updateServerProfile(finalProfile)
            
            // Mark SSH identity as used if it was changed
            if (sshIdentityId != null && sshIdentityId != existing.sshIdentityId) {
                sshIdentityService.markAsUsed(sshIdentityId)
            }
            
            Log.d(TAG, "Server profile updated successfully: $id")
            ServiceResult.success(finalProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server profile", e)
            ServiceResult.failure("Failed to update server profile: ${e.message}")
        }
    }
    
    /**
     * Deletes a server profile with dependency checking.
     */
    suspend fun deleteServerProfile(id: String): ServiceResult<Unit> {
        Log.d(TAG, "Deleting server profile: $id")
        
        return try {
            val profile = repository.getServerProfileById(id)
                ?: return ServiceResult.failure("Server profile not found")
            
            // Check for dependencies
            val projects = repository.getProjectsForServer(id)
            if (projects.isNotEmpty()) {
                return ServiceResult.failure(
                    "Cannot delete server profile. It is used by ${projects.size} project(s): ${
                        projects.take(3).joinToString(", ") { it.name }
                    }${if (projects.size > 3) "..." else ""}"
                )
            }
            
            repository.deleteServerProfile(id)
            
            Log.d(TAG, "Server profile deleted successfully: $id")
            ServiceResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete server profile", e)
            ServiceResult.failure("Failed to delete server profile: ${e.message}")
        }
    }
    
    /**
     * Lists all server profiles with optional filtering and sorting.
     */
    suspend fun listServerProfiles(
        sortBy: ServerProfileSortBy = ServerProfileSortBy.NAME,
        ascending: Boolean = true,
        includeDisconnected: Boolean = true,
        sshIdentityId: String? = null
    ): ServiceResult<List<ServerProfile>> {
        Log.d(TAG, "Listing server profiles")
        
        return try {
            val profiles = repository.getAllServerProfiles()
            
            // Filter by SSH identity if specified
            val filtered = if (sshIdentityId != null) {
                profiles.filter { it.sshIdentityId == sshIdentityId }
            } else {
                profiles
            }
            
            // Filter disconnected if requested
            val statusFiltered = if (!includeDisconnected) {
                filtered.filter { it.status != ConnectionStatus.DISCONNECTED && it.status != ConnectionStatus.ERROR }
            } else {
                filtered
            }
            
            // Sort the results
            val sorted = when (sortBy) {
                ServerProfileSortBy.NAME -> statusFiltered.sortedBy { it.name }
                ServerProfileSortBy.HOSTNAME -> statusFiltered.sortedBy { it.hostname }
                ServerProfileSortBy.CREATED_DATE -> statusFiltered.sortedBy { it.createdAt }
                ServerProfileSortBy.LAST_CONNECTED -> statusFiltered.sortedBy { it.lastConnectedAt ?: 0 }
                ServerProfileSortBy.STATUS -> statusFiltered.sortedBy { it.status.ordinal }
                ServerProfileSortBy.USAGE_COUNT -> {
                    // Get usage counts and sort by them
                    val usageCounts = getUsageStatistics(statusFiltered.map { it.id })
                    statusFiltered.sortedBy { profile ->
                        usageCounts[profile.id]?.projectCount ?: 0
                    }
                }
            }.let { if (ascending) it else it.reversed() }
            
            ServiceResult.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list server profiles", e)
            ServiceResult.failure("Failed to list server profiles: ${e.message}")
        }
    }
    
    // Connection Testing
    
    /**
     * Tests connection to a server profile.
     */
    suspend fun testConnection(profile: ServerProfile): ConnectionTestResult {
        Log.d(TAG, "Testing connection to: ${profile.name}")
        
        return try {
            withContext(Dispatchers.IO) {
                connectionTester.testConnection(
                    hostname = profile.hostname,
                    port = profile.port,
                    username = profile.username,
                    sshIdentityId = profile.sshIdentityId,
                    wrapperPort = profile.wrapperPort,
                    timeoutMs = CONNECTION_TIMEOUT_MS
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            ConnectionTestResult(
                connectionStatus = ConnectionTestResult.Status.FAILED,
                message = "Connection test failed: ${e.message}",
                responseTimeMs = 0,
                errorDetails = e.message
            )
        }
    }
    
    /**
     * Tests connections to multiple server profiles concurrently.
     */
    suspend fun testMultipleConnections(profileIds: List<String>): Map<String, ConnectionTestResult> {
        Log.d(TAG, "Testing connections to ${profileIds.size} profiles")
        
        return try {
            withContext(Dispatchers.IO) {
                connectionTester.testMultipleConnections(profileIds, MAX_CONCURRENT_TESTS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multiple connection test failed", e)
            profileIds.associateWith { 
                ConnectionTestResult(
                    connectionStatus = ConnectionTestResult.Status.FAILED,
                    message = "Test failed: ${e.message}",
                    responseTimeMs = 0,
                    errorDetails = e.message
                )
            }
        }
    }
    
    /**
     * Updates server profile status based on connection test.
     */
    suspend fun updateConnectionStatus(
        profileId: String, 
        status: ConnectionStatus,
        updateTimestamp: Boolean = true
    ): ServiceResult<ServerProfile> {
        Log.d(TAG, "Updating connection status for profile: $profileId")
        
        return try {
            val profile = repository.getServerProfileById(profileId)
                ?: return ServiceResult.failure("Server profile not found")
            
            val updated = if (updateTimestamp && status == ConnectionStatus.CONNECTED) {
                profile.copy(
                    status = status,
                    lastConnectedAt = System.currentTimeMillis()
                )
            } else {
                profile.copy(status = status)
            }
            
            repository.updateServerProfile(updated)
            ServiceResult.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update connection status", e)
            ServiceResult.failure("Failed to update connection status: ${e.message}")
        }
    }
    
    // Search and Filtering
    
    /**
     * Searches server profiles by name, hostname, or username.
     */
    suspend fun searchServerProfiles(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): ServiceResult<List<ServerProfile>> {
        Log.d(TAG, "Searching server profiles: $query")
        
        return try {
            if (query.isBlank()) {
                return ServiceResult.success(emptyList())
            }
            
            val allProfiles = repository.getAllServerProfiles()
            val searchResults = allProfiles.filter { profile ->
                profile.name.contains(query, ignoreCase = true) ||
                profile.hostname.contains(query, ignoreCase = true) ||
                profile.username.contains(query, ignoreCase = true) ||
                profile.getConnectionString().contains(query, ignoreCase = true)
            }.take(limit)
            
            ServiceResult.success(searchResults)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search server profiles", e)
            ServiceResult.failure("Failed to search server profiles: ${e.message}")
        }
    }
    
    /**
     * Filters server profiles by various criteria.
     */
    suspend fun filterServerProfiles(
        criteria: ServerProfileFilterCriteria
    ): ServiceResult<List<ServerProfile>> {
        Log.d(TAG, "Filtering server profiles")
        
        return try {
            val allProfiles = repository.getAllServerProfiles()
            var filtered = allProfiles
            
            // Apply SSH identity filter
            criteria.sshIdentityId?.let { identityId ->
                filtered = filtered.filter { it.sshIdentityId == identityId }
            }
            
            // Apply status filter
            criteria.connectionStatus?.let { status ->
                filtered = filtered.filter { it.status == status }
            }
            
            // Apply date range filter
            criteria.createdAfter?.let { after ->
                filtered = filtered.filter { it.createdAt >= after }
            }
            criteria.createdBefore?.let { before ->
                filtered = filtered.filter { it.createdAt <= before }
            }
            
            // Apply connection filter
            if (criteria.recentlyConnectedOnly) {
                filtered = filtered.filter { it.isRecentlyConnected() }
            }
            
            // Apply unused filter
            if (criteria.unusedOnly) {
                val usedProfileIds = repository.getAllProjects()
                    .map { it.serverProfileId }
                    .toSet()
                filtered = filtered.filter { it.id !in usedProfileIds }
            }
            
            // Apply hostname pattern filter
            criteria.hostnamePattern?.let { pattern ->
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                filtered = filtered.filter { regex.containsMatchIn(it.hostname) }
            }
            
            // Apply port range filter
            criteria.portRange?.let { range ->
                filtered = filtered.filter { it.port in range }
            }
            
            ServiceResult.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to filter server profiles", e)
            ServiceResult.failure("Failed to filter server profiles: ${e.message}")
        }
    }
    
    // Usage Tracking and Relationship Management
    
    /**
     * Gets usage statistics for server profiles.
     */
    suspend fun getUsageStatistics(profileIds: List<String>? = null): Map<String, ServerProfileUsageStats> {
        Log.d(TAG, "Getting usage statistics")
        
        return try {
            val projects = repository.getAllProjects()
            val targetIds = profileIds ?: repository.getAllServerProfiles().map { it.id }
            
            targetIds.associateWith { profileId ->
                val relatedProjects = projects.filter { it.serverProfileId == profileId }
                val activeProjects = relatedProjects.filter { it.status.name != "INACTIVE" }
                
                ServerProfileUsageStats(
                    projectCount = relatedProjects.size,
                    activeProjectCount = activeProjects.size,
                    lastProjectActivity = relatedProjects.mapNotNull { it.lastActiveAt }.maxOrNull(),
                    totalConnections = relatedProjects.size, // Simplified for now
                    averageSessionDuration = 0L // Would need session tracking
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage statistics", e)
            emptyMap()
        }
    }
    
    /**
     * Gets server profiles associated with an SSH identity.
     */
    suspend fun getProfilesForSshIdentity(sshIdentityId: String): ServiceResult<List<ServerProfile>> {
        Log.d(TAG, "Getting profiles for SSH identity: $sshIdentityId")
        
        return try {
            val profiles = repository.getServerProfilesForIdentity(sshIdentityId)
            ServiceResult.success(profiles)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profiles for SSH identity", e)
            ServiceResult.failure("Failed to get profiles for SSH identity: ${e.message}")
        }
    }
    
    /**
     * Gets projects associated with a server profile.
     */
    suspend fun getProjectsForProfile(profileId: String): ServiceResult<List<com.pocketagent.data.models.Project>> {
        Log.d(TAG, "Getting projects for profile: $profileId")
        
        return try {
            val projects = repository.getProjectsForServer(profileId)
            ServiceResult.success(projects)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get projects for profile", e)
            ServiceResult.failure("Failed to get projects for profile: ${e.message}")
        }
    }
    
    // Network Configuration and Validation
    
    /**
     * Validates network configuration for a server profile.
     */
    suspend fun validateNetworkConfiguration(
        hostname: String,
        sshPort: Int,
        wrapperPort: Int
    ): ServiceResult<NetworkValidationReport> {
        Log.d(TAG, "Validating network configuration: $hostname:$sshPort/$wrapperPort")
        
        return try {
            withContext(Dispatchers.IO) {
                val report = networkValidator.validateConfiguration(hostname, sshPort, wrapperPort)
                val networkReport = NetworkValidationReport(
                    isValid = report.isValid,
                    hostnameResolvable = networkValidator.canResolveHostname(hostname),
                    sshPortReachable = networkValidator.isPortReachable(hostname, sshPort),
                    wrapperPortReachable = networkValidator.isPortReachable(hostname, wrapperPort),
                    recommendations = networkValidator.getRecommendations(hostname, sshPort, wrapperPort)
                )
                ServiceResult.success(networkReport)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate network configuration", e)
            ServiceResult.failure("Failed to validate network configuration: ${e.message}")
        }
    }
    
    // Observable Flows
    
    /**
     * Observable flow of all server profiles.
     */
    fun observeServerProfiles(): Flow<List<ServerProfile>> = repository.observeServerProfiles()
    
    /**
     * Observable flow of server profiles with usage statistics.
     */
    fun observeServerProfilesWithUsage(): Flow<List<ServerProfileWithUsage>> {
        return combine(
            repository.observeServerProfiles(),
            repository.observeProjects()
        ) { profiles, projects ->
            profiles.map { profile ->
                val relatedProjects = projects.filter { it.serverProfileId == profile.id }
                val activeProjects = relatedProjects.filter { it.status.name != "INACTIVE" }
                
                ServerProfileWithUsage(
                    profile = profile,
                    usageStats = ServerProfileUsageStats(
                        projectCount = relatedProjects.size,
                        activeProjectCount = activeProjects.size,
                        lastProjectActivity = relatedProjects.mapNotNull { it.lastActiveAt }.maxOrNull(),
                        totalConnections = relatedProjects.size,
                        averageSessionDuration = 0L
                    )
                )
            }
        }.flowOn(Dispatchers.Default)
    }
    
    /**
     * Observable flow of connection statuses for all profiles.
     */
    fun observeConnectionStatuses(): Flow<Map<String, ConnectionStatus>> {
        return repository.observeServerProfiles().map { profiles ->
            profiles.associate { it.id to it.status }
        }.flowOn(Dispatchers.Default)
    }
    
    // Import/Export
    
    /**
     * Exports server profile configuration (without sensitive data).
     */
    suspend fun exportProfile(profileId: String): ServiceResult<String> {
        Log.d(TAG, "Exporting server profile: $profileId")
        
        return try {
            val profile = repository.getServerProfileById(profileId)
                ?: return ServiceResult.failure("Server profile not found")
            
            val exportData = profile.toExportModel()
            val jsonData = kotlinx.serialization.json.Json.encodeToString(
                com.pocketagent.data.models.ServerProfileExport.serializer(), 
                exportData
            )
            
            ServiceResult.success(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export server profile", e)
            ServiceResult.failure("Failed to export server profile: ${e.message}")
        }
    }
    
    /**
     * Imports server profile configuration.
     */
    suspend fun importProfile(
        jsonData: String,
        sshIdentityMapping: Map<String, String> = emptyMap()
    ): ServiceResult<ServerProfile> {
        Log.d(TAG, "Importing server profile")
        
        return try {
            val exportData = kotlinx.serialization.json.Json.decodeFromString(
                com.pocketagent.data.models.ServerProfileExport.serializer(),
                jsonData
            )
            
            // Map SSH identity ID if provided
            val mappedSshIdentityId = sshIdentityMapping[exportData.sshIdentityId] ?: exportData.sshIdentityId
            
            // Create new profile with new ID
            createServerProfile(
                name = exportData.name,
                hostname = exportData.hostname,
                port = exportData.port,
                username = exportData.username,
                sshIdentityId = mappedSshIdentityId,
                wrapperPort = exportData.wrapperPort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import server profile", e)
            ServiceResult.failure("Failed to import server profile: ${e.message}")
        }
    }
}

/**
 * Sorting options for server profiles.
 */
enum class ServerProfileSortBy {
    NAME,
    HOSTNAME,
    CREATED_DATE,
    LAST_CONNECTED,
    STATUS,
    USAGE_COUNT
}

/**
 * Filter criteria for server profiles.
 */
data class ServerProfileFilterCriteria(
    val sshIdentityId: String? = null,
    val connectionStatus: ConnectionStatus? = null,
    val createdAfter: Long? = null,
    val createdBefore: Long? = null,
    val recentlyConnectedOnly: Boolean = false,
    val unusedOnly: Boolean = false,
    val hostnamePattern: String? = null,
    val portRange: IntRange? = null
)

/**
 * Connection test result.
 */
data class ConnectionTestResult(
    val connectionStatus: Status,
    val message: String,
    val responseTimeMs: Long,
    val errorDetails: String? = null,
    val additionalInfo: Map<String, String> = emptyMap()
) {
    enum class Status {
        SUCCESS, FAILED, TIMEOUT
    }
}

/**
 * Network validation report.
 */
data class NetworkValidationReport(
    val isValid: Boolean,
    val hostnameResolvable: Boolean,
    val sshPortReachable: Boolean,
    val wrapperPortReachable: Boolean,
    val recommendations: List<String>
)

/**
 * Usage statistics for a server profile.
 */
data class ServerProfileUsageStats(
    val projectCount: Int,
    val activeProjectCount: Int,
    val lastProjectActivity: Long?,
    val totalConnections: Int,
    val averageSessionDuration: Long
)

/**
 * Server profile with usage statistics.
 */
data class ServerProfileWithUsage(
    val profile: ServerProfile,
    val usageStats: ServerProfileUsageStats
)

// Extension function for getting error summary from ValidationResult
private fun ValidationResult.getErrorSummary(): String {
    return when (this) {
        is ValidationResult.Success -> "No errors"
        is ValidationResult.Failure -> error.message
    }
}