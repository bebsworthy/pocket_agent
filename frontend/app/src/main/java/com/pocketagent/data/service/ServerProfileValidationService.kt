package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.validators.ServerProfileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized service for Server Profile validation operations.
 *
 * This service extracts complex validation logic from ServerProfileService to improve
 * maintainability and separation of concerns. It handles:
 * - Server profile creation validation
 * - Server profile update validation
 * - Network connectivity validation
 * - Hostname and port validation
 * - SSH identity association validation
 * - Name uniqueness validation
 * - Business rule validation
 */
@Singleton
class ServerProfileValidationService
    @Inject
    constructor(
        private val repository: SecureDataRepository,
        private val validator: ServerProfileValidator,
        private val sshIdentityService: SshIdentityService,
        private val connectionTester: ServerConnectionTester,
        private val networkValidator: NetworkConfigurationValidator,
    ) {
        companion object {
            private const val TAG = "ServerProfileValidationService"
            private const val CONNECTION_TIMEOUT_MS = 10000L
            private const val MAX_PROFILES_PER_IDENTITY = 20
            private const val MIN_PORT = 1
            private const val MAX_PORT = 65535
        }

        /**
         * Validates a server profile for creation.
         *
         * @param profile The server profile to validate
         * @param testConnection Whether to test network connectivity
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForCreation(
            profile: ServerProfile,
            testConnection: Boolean = false,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating server profile for creation: ${profile.name}")

            return try {
                // Basic profile validation
                val validationResult = validator.validateForCreation(profile)
                if (validationResult.isFailure()) {
                    val errorMessage = validationResult.getFirstErrorMessage()
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Validate SSH identity exists
                val sshIdentityValidation = validateSshIdentityExists(profile.sshIdentityId)
                if (sshIdentityValidation.isFailure()) {
                    return sshIdentityValidation
                }

                // Check name uniqueness
                val nameValidation = validateNameUniqueness(profile.name)
                if (nameValidation.isFailure()) {
                    return nameValidation
                }

                // Check hostname/port uniqueness
                val hostnameValidation = validateHostnamePortUniqueness(profile.hostname, profile.port)
                if (hostnameValidation.isFailure()) {
                    return hostnameValidation
                }

                // Validate network configuration
                val networkValidation = validateNetworkConfiguration(profile)
                if (networkValidation.isFailure()) {
                    return networkValidation
                }

                // Check SSH identity usage limits
                val usageLimitValidation = validateSshIdentityUsageLimits(profile.sshIdentityId)
                if (usageLimitValidation.isFailure()) {
                    return usageLimitValidation
                }

                // Test connection if requested
                if (testConnection) {
                    val connectionValidation = validateConnection(profile)
                    if (connectionValidation.isFailure()) {
                        return connectionValidation
                    }
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate server profile for creation", e)
                serviceFailure("Validation failed: ${e.message}")
            }
        }

        /**
         * Validates a server profile update.
         *
         * @param existing The existing server profile
         * @param updated The updated server profile
         * @param testConnection Whether to test network connectivity
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForUpdate(
            existing: ServerProfile,
            updated: ServerProfile,
            testConnection: Boolean = false,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating server profile for update: ${existing.id}")

            return try {
                // Basic profile validation
                val validationResult = validator.validateForUpdate(existing, updated)
                if (validationResult.isFailure()) {
                    val errorMessage = validationResult.getFirstErrorMessage()
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Validate changes that affect identity and uniqueness
                val identityValidation = validateUpdateIdentityChanges(existing, updated)
                if (identityValidation.isFailure) {
                    return identityValidation
                }

                // Validate changes that affect network configuration
                val networkValidation = validateUpdateNetworkChanges(existing, updated)
                if (networkValidation.isFailure) {
                    return networkValidation
                }

                // Test connection if requested and connection details changed
                val connectionValidation = validateUpdateConnectionIfNeeded(existing, updated, testConnection)
                if (connectionValidation.isFailure) {
                    return connectionValidation
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate server profile for update", e)
                serviceFailure("Validation failed: ${e.message}")
            }
        }

        /**
         * Validates identity and uniqueness changes in server profile update.
         */
        private suspend fun validateUpdateIdentityChanges(
            existing: ServerProfile,
            updated: ServerProfile,
        ): ServiceResult<Unit> {
            // Validate SSH identity exists if changed
            if (updated.sshIdentityId != existing.sshIdentityId) {
                val sshIdentityValidation = validateSshIdentityExists(updated.sshIdentityId)
                if (sshIdentityValidation.isFailure()) {
                    return sshIdentityValidation
                }
            }

            // Check name uniqueness if name changed
            if (updated.name != existing.name) {
                val nameValidation = validateNameUniqueness(updated.name, excludeId = existing.id)
                if (nameValidation.isFailure()) {
                    return nameValidation
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates network configuration changes in server profile update.
         */
        private suspend fun validateUpdateNetworkChanges(
            existing: ServerProfile,
            updated: ServerProfile,
        ): ServiceResult<Unit> {
            val hostnameChanged = updated.hostname != existing.hostname
            val portChanged = updated.port != existing.port

            // Check hostname/port uniqueness if changed
            if (hostnameChanged || portChanged) {
                val hostnameValidation =
                    validateHostnamePortUniqueness(
                        updated.hostname,
                        updated.port,
                        excludeId = existing.id,
                    )
                if (hostnameValidation.isFailure()) {
                    return hostnameValidation
                }
            }

            // Validate network configuration if network settings changed
            if (hostnameChanged || portChanged || updated.wrapperPort != existing.wrapperPort) {
                val networkValidation = validateNetworkConfiguration(updated)
                if (networkValidation.isFailure()) {
                    return networkValidation
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates connection if needed based on changes.
         */
        private suspend fun validateUpdateConnectionIfNeeded(
            existing: ServerProfile,
            updated: ServerProfile,
            testConnection: Boolean,
        ): ServiceResult<Unit> {
            if (!testConnection) {
                return serviceSuccess(Unit)
            }

            val connectionDetailsChanged = hasConnectionDetailsChanged(existing, updated)

            if (connectionDetailsChanged) {
                val connectionValidation = validateConnection(updated)
                if (connectionValidation.isFailure()) {
                    return connectionValidation
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Checks if connection details have changed.
         */
        private fun hasConnectionDetailsChanged(
            existing: ServerProfile,
            updated: ServerProfile,
        ): Boolean =
            updated.hostname != existing.hostname ||
                updated.port != existing.port ||
                updated.username != existing.username ||
                updated.sshIdentityId != existing.sshIdentityId

        /**
         * Validates that an SSH identity exists.
         *
         * @param sshIdentityId The SSH identity ID to validate
         * @return ServiceResult indicating success or validation errors
         */
        private suspend fun validateSshIdentityExists(sshIdentityId: String): ServiceResult<Unit> =
            try {
                val sshIdentityResult = sshIdentityService.getSshIdentity(sshIdentityId)
                if (sshIdentityResult.isFailure) {
                    val errorMessage = sshIdentityResult.getErrorOrNull()
                    serviceFailure("SSH Identity not found: $errorMessage")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate SSH identity exists", e)
                serviceFailure("Failed to validate SSH identity: ${e.message}")
            }

        /**
         * Validates server profile name uniqueness.
         *
         * @param name The name to validate
         * @param excludeId Optional profile ID to exclude from uniqueness check
         * @return ServiceResult indicating success or validation errors
         */
        private suspend fun validateNameUniqueness(
            name: String,
            excludeId: String? = null,
        ): ServiceResult<Unit> =
            try {
                val existingProfiles = repository.getAllServerProfiles()
                val nameValidation =
                    validator.validateNameUniqueness(
                        name,
                        existingProfiles.map { it.name },
                        excludeId = excludeId,
                    )

                if (nameValidation.isFailure()) {
                    serviceFailure("Server profile name already exists")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate name uniqueness", e)
                serviceFailure("Failed to validate name uniqueness: ${e.message}")
            }

        /**
         * Validates hostname and port uniqueness.
         *
         * @param hostname The hostname to validate
         * @param port The port to validate
         * @param excludeId Optional profile ID to exclude from uniqueness check
         * @return ServiceResult indicating success or validation errors
         */
        private suspend fun validateHostnamePortUniqueness(
            hostname: String,
            port: Int,
            excludeId: String? = null,
        ): ServiceResult<Unit> =
            try {
                val existingProfiles = repository.getAllServerProfiles()
                val hostPortPairs =
                    existingProfiles
                        .filter { excludeId == null || it.id != excludeId }
                        .map { it.hostname to it.port }

                val hostPortValidation =
                    validator.validateHostnamePortUniqueness(
                        hostname,
                        port,
                        hostPortPairs,
                        excludeId = excludeId,
                    )

                if (hostPortValidation.isFailure()) {
                    serviceFailure("Server with same hostname and port already exists")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate hostname/port uniqueness", e)
                serviceFailure("Failed to validate hostname/port uniqueness: ${e.message}")
            }

        /**
         * Validates network configuration.
         *
         * @param profile The server profile to validate
         * @return ServiceResult indicating success or validation errors
         */
        private suspend fun validateNetworkConfiguration(profile: ServerProfile): ServiceResult<Unit> {
            return try {
                withContext(Dispatchers.IO) {
                    // Validate hostname format
                    val hostnameValidation = validateHostnameFormat(profile.hostname)
                    if (hostnameValidation.isFailure()) {
                        return@withContext hostnameValidation
                    }

                    // Validate port ranges
                    val portValidation = validatePortRanges(profile.port, profile.wrapperPort)
                    if (portValidation.isFailure()) {
                        return@withContext portValidation
                    }

                    // Validate network configuration using the network validator
                    val networkValidation =
                        networkValidator.validateServerConfiguration(
                            profile.hostname,
                            profile.port,
                            profile.wrapperPort,
                        )
                    if (networkValidation.isFailure()) {
                        val errorMessage = networkValidation.getFirstErrorMessage()
                        return@withContext serviceFailure("Network configuration invalid: $errorMessage")
                    }

                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate network configuration", e)
                serviceFailure("Failed to validate network configuration: ${e.message}")
            }
        }

        /**
         * Validates hostname format.
         */
        private fun validateHostnameFormat(hostname: String): ServiceResult<Unit> {
            return try {
                // Basic hostname validation
                if (hostname.isBlank()) {
                    return serviceFailure("Hostname cannot be blank")
                }

                if (hostname.length > 253) {
                    return serviceFailure("Hostname is too long (max 253 characters)")
                }

                // Check for valid hostname format (simplified)
                val hostnameRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$")
                if (!hostname.matches(hostnameRegex)) {
                    return serviceFailure("Invalid hostname format")
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate hostname format", e)
                serviceFailure("Failed to validate hostname format: ${e.message}")
            }
        }

        /**
         * Validates port ranges.
         */
        private fun validatePortRanges(
            sshPort: Int,
            wrapperPort: Int,
        ): ServiceResult<Unit> {
            return try {
                if (sshPort !in MIN_PORT..MAX_PORT) {
                    return serviceFailure("SSH port must be between $MIN_PORT and $MAX_PORT")
                }

                if (wrapperPort !in MIN_PORT..MAX_PORT) {
                    return serviceFailure("Wrapper port must be between $MIN_PORT and $MAX_PORT")
                }

                if (sshPort == wrapperPort) {
                    return serviceFailure("SSH port and wrapper port cannot be the same")
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate port ranges", e)
                serviceFailure("Failed to validate port ranges: ${e.message}")
            }
        }

        /**
         * Validates SSH identity usage limits.
         */
        private suspend fun validateSshIdentityUsageLimits(sshIdentityId: String): ServiceResult<Unit> =
            try {
                val existingProfiles = repository.getAllServerProfiles()
                val profilesUsingIdentity = existingProfiles.filter { it.sshIdentityId == sshIdentityId }

                if (profilesUsingIdentity.size >= MAX_PROFILES_PER_IDENTITY) {
                    val message = "Maximum number of server profiles ($MAX_PROFILES_PER_IDENTITY) reached for this SSH identity"
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate SSH identity usage limits", e)
                serviceFailure("Failed to validate SSH identity usage limits: ${e.message}")
            }

        /**
         * Validates network connectivity.
         *
         * @param profile The server profile to test
         * @return ServiceResult indicating success or connection errors
         */
        private suspend fun validateConnection(profile: ServerProfile): ServiceResult<Unit> =
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Testing connection to ${profile.hostname}:${profile.port}")

                    val connectionResult =
                        connectionTester.testConnection(
                            profile.hostname,
                            profile.port,
                            CONNECTION_TIMEOUT_MS,
                        )

                    if (connectionResult.isFailure()) {
                        val errorMessage = connectionResult.getErrorOrNull()
                        serviceFailure("Connection test failed: $errorMessage")
                    } else {
                        serviceSuccess(Unit)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate connection", e)
                serviceFailure("Failed to validate connection: ${e.message}")
            }

        /**
         * Validates if a server profile can be deleted.
         *
         * @param profile The server profile to validate for deletion
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForDeletion(profile: ServerProfile): ServiceResult<Unit> =
            try {
                // Check if profile is in use by projects
                val projects = repository.getProjectsForServer(profile.id)
                if (projects.isNotEmpty()) {
                    val projectNames = projects.map { it.name }
                    val message = "Server profile is in use by projects: ${projectNames.joinToString(", ")}"
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate server profile for deletion", e)
                serviceFailure("Failed to validate server profile for deletion: ${e.message}")
            }
    }
