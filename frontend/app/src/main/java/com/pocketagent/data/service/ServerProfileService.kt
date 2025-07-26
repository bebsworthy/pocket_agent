package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.markAsConnected
import com.pocketagent.data.models.toExportModel
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.service.serviceFailure
import com.pocketagent.data.service.serviceSuccess
import com.pocketagent.data.validation.validators.ServerProfileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
class ServerProfileService
    @Inject
    constructor(
        private val repository: SecureDataRepository,
        private val validator: ServerProfileValidator,
        private val sshIdentityService: SshIdentityService,
        private val connectionTester: ServerConnectionTester,
        private val networkValidator: NetworkConfigurationValidator,
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
         * @param request Server profile creation request containing all necessary parameters
         * @return Result with created server profile or error
         */
        suspend fun createServerProfile(request: CreateServerProfileRequest): ServiceResult<ServerProfile> {
            Log.d(TAG, "Creating server profile: ${request.name}")

            return try {
                val result = withContext(Dispatchers.Default) {
                    validateAndCreateServerProfile(request)
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create server profile", e)
                serviceFailure("Failed to create server profile: ${e.message}")
            }
        }
        
        /**
         * Validates all requirements and creates the server profile.
         */
        private suspend fun validateAndCreateServerProfile(request: CreateServerProfileRequest): ServiceResult<ServerProfile> {
            // Validate SSH identity exists
            val sshIdentityResult = sshIdentityService.getSshIdentity(request.sshIdentityId)
            if (sshIdentityResult.isFailure) {
                val errorMessage = sshIdentityResult.getErrorOrNull()
                return serviceFailure("SSH Identity not found: $errorMessage")
            }

            // Create the server profile
            val profile = ServerProfile(
                name = request.name,
                hostname = request.hostname,
                port = request.port,
                username = request.username,
                sshIdentityId = request.sshIdentityId,
                wrapperPort = request.wrapperPort,
            )

            // Perform all validations
            val validationError = performServerProfileValidations(request, profile)
            if (validationError != null) return validationError

            // Test connection and determine final profile status
            val finalProfile = if (request.testConnection) {
                val connectionResult = testConnection(profile)
                when (connectionResult.connectionStatus) {
                    ConnectionTestResult.Status.SUCCESS -> profile.markAsConnected()
                    ConnectionTestResult.Status.FAILED -> profile.copy(status = ConnectionStatus.ERROR)
                    ConnectionTestResult.Status.TIMEOUT -> profile.copy(status = ConnectionStatus.ERROR)
                }
            } else {
                profile
            }

            // Save to repository
            repository.addServerProfile(finalProfile)

            // Mark SSH identity as used
            sshIdentityService.markAsUsed(request.sshIdentityId)

            Log.d(TAG, "Server profile created successfully: ${request.name}")
            return serviceSuccess(finalProfile)
        }
        
        /**
         * Performs comprehensive validation for server profile creation.
         */
        private suspend fun performServerProfileValidations(
            request: CreateServerProfileRequest,
            profile: ServerProfile
        ): ServiceResult<ServerProfile>? {
            // Validate the profile
            val validationResult = validator.validateForCreation(profile)
            if (validationResult.isFailure()) {
                val errorMessage = validationResult.getFirstErrorMessage() ?: "Validation failed"
                return serviceFailure("Validation failed: $errorMessage")
            }

            val existingProfiles = repository.getAllServerProfiles()
            
            // Check name uniqueness
            val nameValidation = validator.validateNameUniqueness(
                request.name,
                existingProfiles.map { it.name },
            )
            if (nameValidation.isFailure()) {
                return serviceFailure("Server profile name already exists")
            }

            // Check hostname/port uniqueness
            val hostPortPairs = existingProfiles.map { it.hostname to it.port }
            val hostnamePortValidation = validator.validateHostnamePortUniqueness(
                request.hostname,
                request.port,
                hostPortPairs,
            )
            if (hostnamePortValidation.isFailure()) {
                return serviceFailure("Server with same hostname and port already exists")
            }

            // Validate network configuration
            val networkValidation = networkValidator.validateConfiguration(
                request.hostname,
                request.port,
                request.wrapperPort,
            )
            if (networkValidation.isFailure()) {
                val errorMessage = networkValidation.getFirstErrorMessage() ?: "Network validation failed"
                return serviceFailure("Network configuration invalid: $errorMessage")
            }

            return null
        }

        /**
         * Retrieves a server profile by ID.
         */
        suspend fun getServerProfile(id: String): ServiceResult<ServerProfile> {
            Log.d(TAG, "Getting server profile: $id")

            return try {
                val profile = repository.getServerProfileById(id)
                if (profile != null) {
                    serviceSuccess(profile)
                } else {
                    serviceFailure("Server profile not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get server profile", e)
                serviceFailure("Failed to retrieve server profile: ${e.message}")
            }
        }

        /**
         * Updates an existing server profile.
         */
        suspend fun updateServerProfile(request: ServerProfileUpdateRequest): ServiceResult<ServerProfile> {
            Log.d(TAG, "Updating server profile: ${request.id}")

            return try {
                val existing =
                    repository.getServerProfileById(request.id)
                        ?: return serviceFailure("Server profile not found")

                // Validate SSH identity change
                val sshValidationResult = validateSshIdentityChange(request.sshIdentityId, existing.sshIdentityId)
                if (sshValidationResult != null) return sshValidationResult

                val updated = buildUpdatedProfile(
                    ProfileUpdateParams(
                        existing = existing,
                        name = request.name,
                        hostname = request.hostname,
                        port = request.port,
                        username = request.username,
                        sshIdentityId = request.sshIdentityId,
                        wrapperPort = request.wrapperPort,
                    )
                )

                // Validate the update
                val validationResult = validator.validateForUpdate(existing, updated)
                if (validationResult.isFailure()) {
                    val errorMessage = validationResult.getFirstErrorMessage() ?: "Validation failed"
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Check uniqueness constraints
                val uniquenessResult = validateUniquenessConstraints(
                    UniquenessValidationParams(
                        id = request.id,
                        name = request.name,
                        hostname = request.hostname,
                        port = request.port,
                        existing = existing,
                        updated = updated,
                    )
                )
                if (uniquenessResult != null) return uniquenessResult

                // Validate network configuration if needed
                val networkResult = validateNetworkConfigurationIfChanged(
                    request.hostname, request.port, request.wrapperPort, existing, updated
                )
                if (networkResult != null) return networkResult

                // Test connection and update status if needed
                val finalProfile =
                    processConnectionTestingIfNeeded(
                        request.testConnectionAfterUpdate,
                        request.hostname,
                        request.port,
                        request.username,
                        request.sshIdentityId,
                        existing,
                        updated,
                    )

                repository.updateServerProfile(finalProfile)

                // Update SSH identity usage if changed
                updateSshIdentityUsageIfChanged(request.sshIdentityId, existing.sshIdentityId)

                Log.d(TAG, "Server profile updated successfully: ${request.id}")
                serviceSuccess(finalProfile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update server profile", e)
                serviceFailure("Failed to update server profile: ${e.message}")
            }
        }

        /**
         * Deletes a server profile with dependency checking.
         */
        suspend fun deleteServerProfile(id: String): ServiceResult<Unit> {
            Log.d(TAG, "Deleting server profile: $id")

            return try {
                val profile =
                    repository.getServerProfileById(id)
                        ?: return serviceFailure("Server profile not found")

                // Check for dependencies
                val projects = repository.getProjectsForServer(id)
                if (projects.isNotEmpty()) {
                    val projectNames = projects.take(3).joinToString(", ") { it.name }
                    val ellipsis = if (projects.size > 3) "..." else ""
                    val message = "Cannot delete server profile. It is used by ${projects.size} project(s): $projectNames$ellipsis"
                    return serviceFailure(message)
                }

                repository.deleteServerProfile(id)

                Log.d(TAG, "Server profile deleted successfully: $id")
                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete server profile", e)
                serviceFailure("Failed to delete server profile: ${e.message}")
            }
        }

        /**
         * Lists all server profiles with optional filtering and sorting.
         */
        suspend fun listServerProfiles(
            sortBy: ServerProfileSortBy = ServerProfileSortBy.NAME,
            ascending: Boolean = true,
            includeDisconnected: Boolean = true,
            sshIdentityId: String? = null,
        ): ServiceResult<List<ServerProfile>> {
            Log.d(TAG, "Listing server profiles")

            return try {
                val profiles = repository.getAllServerProfiles()

                // Filter by SSH identity if specified
                val filtered =
                    if (sshIdentityId != null) {
                        profiles.filter { it.sshIdentityId == sshIdentityId }
                    } else {
                        profiles
                    }

                // Filter disconnected if requested
                val statusFiltered =
                    if (!includeDisconnected) {
                        val excludedStatuses = setOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)
                        filtered.filter { it.status !in excludedStatuses }
                    } else {
                        filtered
                    }

                // Sort the results
                val sorted =
                    when (sortBy) {
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

                serviceSuccess(sorted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list server profiles", e)
                serviceFailure("Failed to list server profiles: ${e.message}")
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
                        ServerConnectionTester.ConnectionTestConfig(
                            hostname = profile.hostname,
                            port = profile.port,
                            username = profile.username,
                            sshIdentityId = profile.sshIdentityId,
                            wrapperPort = profile.wrapperPort,
                            timeoutMs = CONNECTION_TIMEOUT_MS,
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                ConnectionTestResult(
                    connectionStatus = ConnectionTestResult.Status.FAILED,
                    message = "Connection test failed: ${e.message}",
                    responseTimeMs = 0,
                    errorDetails = e.message,
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
                        errorDetails = e.message,
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
            updateTimestamp: Boolean = true,
        ): ServiceResult<ServerProfile> {
            Log.d(TAG, "Updating connection status for profile: $profileId")

            return try {
                val profile =
                    repository.getServerProfileById(profileId)
                        ?: return serviceFailure("Server profile not found")

                val updated =
                    if (updateTimestamp && status == ConnectionStatus.CONNECTED) {
                        profile.copy(
                            status = status,
                            lastConnectedAt = System.currentTimeMillis(),
                        )
                    } else {
                        profile.copy(status = status)
                    }

                repository.updateServerProfile(updated)
                serviceSuccess(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update connection status", e)
                serviceFailure("Failed to update connection status: ${e.message}")
            }
        }

        // Search and Filtering

        /**
         * Searches server profiles by name, hostname, or username.
         */
        suspend fun searchServerProfiles(
            query: String,
            limit: Int = DEFAULT_SEARCH_LIMIT,
        ): ServiceResult<List<ServerProfile>> {
            Log.d(TAG, "Searching server profiles: $query")

            return try {
                if (query.isBlank()) {
                    return serviceSuccess(emptyList())
                }

                val allProfiles = repository.getAllServerProfiles()
                val searchResults =
                    allProfiles
                        .filter { profile ->
                            profile.name.contains(query, ignoreCase = true) ||
                                profile.hostname.contains(query, ignoreCase = true) ||
                                profile.username.contains(query, ignoreCase = true) ||
                                profile.getConnectionString().contains(query, ignoreCase = true)
                        }.take(limit)

                serviceSuccess(searchResults)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search server profiles", e)
                serviceFailure("Failed to search server profiles: ${e.message}")
            }
        }

        /**
         * Filters server profiles by various criteria.
         */
        suspend fun filterServerProfiles(criteria: ServerProfileFilterCriteria): ServiceResult<List<ServerProfile>> {
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
                    val usedProfileIds =
                        repository
                            .getAllProjects()
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

                serviceSuccess(filtered)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to filter server profiles", e)
                serviceFailure("Failed to filter server profiles: ${e.message}")
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
                        averageSessionDuration = 0L, // Would need session tracking
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
                serviceSuccess(profiles)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get profiles for SSH identity", e)
                serviceFailure("Failed to get profiles for SSH identity: ${e.message}")
            }
        }

        /**
         * Gets projects associated with a server profile.
         */
        suspend fun getProjectsForProfile(profileId: String): ServiceResult<List<com.pocketagent.data.models.Project>> {
            Log.d(TAG, "Getting projects for profile: $profileId")

            return try {
                val projects = repository.getProjectsForServer(profileId)
                serviceSuccess(projects)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get projects for profile", e)
                serviceFailure("Failed to get projects for profile: ${e.message}")
            }
        }

        // Network Configuration and Validation

        /**
         * Validates network configuration for a server profile.
         */
        suspend fun validateNetworkConfiguration(
            hostname: String,
            sshPort: Int,
            wrapperPort: Int,
        ): ServiceResult<NetworkValidationReport> {
            Log.d(TAG, "Validating network configuration: $hostname:$sshPort/$wrapperPort")

            return try {
                withContext(Dispatchers.IO) {
                    val report = networkValidator.validateConfiguration(hostname, sshPort, wrapperPort)
                    val networkReport =
                        NetworkValidationReport(
                            isValid = report.isSuccess(),
                            hostnameResolvable = networkValidator.canResolveHostname(hostname),
                            sshPortReachable = networkValidator.isPortReachable(hostname, sshPort),
                            wrapperPortReachable = networkValidator.isPortReachable(hostname, wrapperPort),
                            recommendations = networkValidator.getRecommendations(hostname, sshPort, wrapperPort),
                        )
                    serviceSuccess(networkReport)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate network configuration", e)
                serviceFailure("Failed to validate network configuration: ${e.message}")
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
        fun observeServerProfilesWithUsage(): Flow<List<ServerProfileWithUsage>> =
            combine(
                repository.observeServerProfiles(),
                repository.observeProjects(),
            ) { profiles, projects ->
                profiles.map { profile ->
                    val relatedProjects = projects.filter { it.serverProfileId == profile.id }
                    val activeProjects = relatedProjects.filter { it.status.name != "INACTIVE" }

                    ServerProfileWithUsage(
                        profile = profile,
                        usageStats =
                            ServerProfileUsageStats(
                                projectCount = relatedProjects.size,
                                activeProjectCount = activeProjects.size,
                                lastProjectActivity = relatedProjects.mapNotNull { it.lastActiveAt }.maxOrNull(),
                                totalConnections = relatedProjects.size,
                                averageSessionDuration = 0L,
                            ),
                    )
                }
            }.flowOn(Dispatchers.Default)

        /**
         * Observable flow of connection statuses for all profiles.
         */
        fun observeConnectionStatuses(): Flow<Map<String, ConnectionStatus>> =
            repository.observeServerProfiles().map { profiles ->
                profiles.associate { it.id to it.status }
            }

        // Helper methods for updateServerProfile complexity reduction

        private suspend fun validateSshIdentityChange(
            sshIdentityId: String?,
            existingSshIdentityId: String,
        ): ServiceResult<ServerProfile>? {
            if (sshIdentityId != null && sshIdentityId != existingSshIdentityId) {
                val sshIdentityResult = sshIdentityService.getSshIdentity(sshIdentityId)
                if (sshIdentityResult.isFailure) {
                    return serviceFailure("SSH Identity not found: ${sshIdentityResult.getErrorOrNull()}")
                }
            }
            return null
        }

        private fun buildUpdatedProfile(params: ProfileUpdateParams): ServerProfile =
            params.existing.copy(
                name = params.name ?: params.existing.name,
                hostname = params.hostname ?: params.existing.hostname,
                port = params.port ?: params.existing.port,
                username = params.username ?: params.existing.username,
                sshIdentityId = params.sshIdentityId ?: params.existing.sshIdentityId,
                wrapperPort = params.wrapperPort ?: params.existing.wrapperPort,
            )

        private suspend fun validateUniquenessConstraints(
            params: UniquenessValidationParams,
        ): ServiceResult<ServerProfile>? {
            // Check name uniqueness if name changed
            if (isNameChanged(params.name, params.existing.name)) {
                val nameResult = validateNameUniqueness(params.id, params.name!!)
                if (nameResult != null) return nameResult
            }

            // Check hostname/port uniqueness if either changed
            if (isHostnameOrPortChanged(params.hostname, params.port, params.existing)) {
                val hostnamePortResult = validateHostnamePortUniqueness(params.id, params.updated)
                if (hostnamePortResult != null) return hostnamePortResult
            }

            return null
        }

        private fun isNameChanged(
            name: String?,
            existingName: String,
        ): Boolean = name != null && name != existingName

        private fun isHostnameOrPortChanged(
            hostname: String?,
            port: Int?,
            existing: ServerProfile,
        ): Boolean =
            (hostname != null && hostname != existing.hostname) ||
                (port != null && port != existing.port)

        private suspend fun validateNameUniqueness(
            id: String,
            name: String,
        ): ServiceResult<ServerProfile>? {
            val existingProfiles = repository.getAllServerProfiles()
            val nameValidation =
                validator.validateNameUniqueness(
                    name,
                    existingProfiles.map { it.name },
                    excludeId = id,
                )
            if (nameValidation.isFailure()) {
                return serviceFailure("Server profile name already exists")
            }
            return null
        }

        private suspend fun validateHostnamePortUniqueness(
            id: String,
            updated: ServerProfile,
        ): ServiceResult<ServerProfile>? {
            val existingProfiles = repository.getAllServerProfiles()
            val hostnamePortValidation =
                validator.validateHostnamePortUniqueness(
                    updated.hostname,
                    updated.port,
                    existingProfiles.map { it.hostname to it.port },
                    excludeId = id,
                )
            if (hostnamePortValidation.isFailure()) {
                return serviceFailure("Server with same hostname and port already exists")
            }
            return null
        }

        private suspend fun validateNetworkConfigurationIfChanged(
            hostname: String?,
            port: Int?,
            wrapperPort: Int?,
            existing: ServerProfile,
            updated: ServerProfile,
        ): ServiceResult<ServerProfile>? {
            if (isNetworkConfigurationChanged(hostname, port, wrapperPort, existing)) {
                val networkValidation =
                    networkValidator.validateConfiguration(
                        updated.hostname,
                        updated.port,
                        updated.wrapperPort,
                    )
                if (networkValidation.isFailure()) {
                    val errorMsg = networkValidation.getFirstErrorMessage() ?: "Network validation failed"
                    return serviceFailure("Network configuration invalid: $errorMsg")
                }
            }
            return null
        }

        private fun isNetworkConfigurationChanged(
            hostname: String?,
            port: Int?,
            wrapperPort: Int?,
            existing: ServerProfile,
        ): Boolean =
            (hostname != null && hostname != existing.hostname) ||
                (port != null && port != existing.port) ||
                (wrapperPort != null && wrapperPort != existing.wrapperPort)

        private suspend fun processConnectionTestingIfNeeded(
            testConnectionAfterUpdate: Boolean,
            hostname: String?,
            port: Int?,
            username: String?,
            sshIdentityId: String?,
            existing: ServerProfile,
            updated: ServerProfile,
        ): ServerProfile {
            val testParams = ConnectionTestParams(
                testConnectionAfterUpdate = testConnectionAfterUpdate,
                hostname = hostname,
                port = port,
                username = username,
                sshIdentityId = sshIdentityId,
                existing = existing,
            )
            if (shouldTestConnection(testParams)) {
                return performConnectionTestAndUpdateStatus(updated)
            }
            return updated
        }

        private fun shouldTestConnection(params: ConnectionTestParams): Boolean =
            params.testConnectionAfterUpdate &&
                (
                    (params.hostname != null && params.hostname != params.existing.hostname) ||
                        (params.port != null && params.port != params.existing.port) ||
                        (params.username != null && params.username != params.existing.username) ||
                        (params.sshIdentityId != null && params.sshIdentityId != params.existing.sshIdentityId)
                )

        private suspend fun performConnectionTestAndUpdateStatus(updated: ServerProfile): ServerProfile {
            val connectionResult = testConnection(updated)
            return when (connectionResult.connectionStatus) {
                ConnectionTestResult.Status.SUCCESS -> updated.markAsConnected()
                ConnectionTestResult.Status.FAILED -> updated.copy(status = ConnectionStatus.ERROR)
                ConnectionTestResult.Status.TIMEOUT -> updated.copy(status = ConnectionStatus.ERROR)
            }
        }

        private suspend fun updateSshIdentityUsageIfChanged(
            sshIdentityId: String?,
            existingSshIdentityId: String,
        ) {
            if (sshIdentityId != null && sshIdentityId != existingSshIdentityId) {
                sshIdentityService.markAsUsed(sshIdentityId)
            }
        }

        // Import/Export

        /**
         * Exports server profile configuration (without sensitive data).
         */
        suspend fun exportProfile(profileId: String): ServiceResult<String> {
            Log.d(TAG, "Exporting server profile: $profileId")

            return try {
                val profile =
                    repository.getServerProfileById(profileId)
                        ?: return serviceFailure("Server profile not found")

                val exportData = profile.toExportModel()
                val jsonData =
                    kotlinx.serialization.json.Json.encodeToString(
                        com.pocketagent.data.models.ServerProfileExport
                            .serializer(),
                        exportData,
                    )

                serviceSuccess(jsonData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export server profile", e)
                serviceFailure("Failed to export server profile: ${e.message}")
            }
        }

        /**
         * Imports server profile configuration.
         */
        suspend fun importProfile(
            jsonData: String,
            sshIdentityMapping: Map<String, String> = emptyMap(),
        ): ServiceResult<ServerProfile> {
            Log.d(TAG, "Importing server profile")

            return try {
                val exportData =
                    kotlinx.serialization.json.Json.decodeFromString(
                        com.pocketagent.data.models.ServerProfileExport
                            .serializer(),
                        jsonData,
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
                    wrapperPort = exportData.wrapperPort,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import server profile", e)
                serviceFailure("Failed to import server profile: ${e.message}")
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
    USAGE_COUNT,
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
    val portRange: IntRange? = null,
)

/**
 * Connection test result.
 */
data class ConnectionTestResult(
    val connectionStatus: Status,
    val message: String,
    val responseTimeMs: Long,
    val errorDetails: String? = null,
    val additionalInfo: Map<String, String> = emptyMap(),
) {
    enum class Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
    }
}

/**
 * Parameters for connection test evaluation.
 */
data class ConnectionTestParams(
    val testConnectionAfterUpdate: Boolean,
    val hostname: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val sshIdentityId: String? = null,
    val existing: ServerProfile,
)

/**
 * Parameters for uniqueness constraint validation.
 */
data class UniquenessValidationParams(
    val id: String,
    val name: String?,
    val hostname: String?,
    val port: Int?,
    val existing: ServerProfile,
    val updated: ServerProfile,
)

/**
 * Parameters for building updated server profile.
 */
data class ProfileUpdateParams(
    val existing: ServerProfile,
    val name: String? = null,
    val hostname: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val sshIdentityId: String? = null,
    val wrapperPort: Int? = null,
)

/**
 * Parameters for server profile update operation.
 */
data class ServerProfileUpdateRequest(
    val id: String,
    val name: String? = null,
    val hostname: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val sshIdentityId: String? = null,
    val wrapperPort: Int? = null,
    val description: String? = null,
    val testConnectionAfterUpdate: Boolean = false,
)

/**
 * Network validation report.
 */
data class NetworkValidationReport(
    val isValid: Boolean,
    val hostnameResolvable: Boolean,
    val sshPortReachable: Boolean,
    val wrapperPortReachable: Boolean,
    val recommendations: List<String>,
)

/**
 * Usage statistics for a server profile.
 */
data class ServerProfileUsageStats(
    val projectCount: Int,
    val activeProjectCount: Int,
    val lastProjectActivity: Long?,
    val totalConnections: Int,
    val averageSessionDuration: Long,
)

/**
 * Server profile with usage statistics.
 */
data class ServerProfileWithUsage(
    val profile: ServerProfile,
    val usageStats: ServerProfileUsageStats,
)

/**
 * Request data class for creating a new server profile.
 */
data class CreateServerProfileRequest(
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshIdentityId: String,
    val wrapperPort: Int = 8080,
    val description: String? = null,
    val testConnection: Boolean = false,
)

/**
 * Request data class for updating an existing server profile.
 */
data class UpdateServerProfileRequest(
    val id: String,
    val name: String? = null,
    val hostname: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val sshIdentityId: String? = null,
    val wrapperPort: Int? = null,
    val description: String? = null,
    val testConnectionAfterUpdate: Boolean = false,
)
