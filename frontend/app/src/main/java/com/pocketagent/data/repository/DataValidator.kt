package com.pocketagent.data.repository

import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.BusinessRuleValidator
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy validator class for data integrity and constraint validation.
 *
 * This class provides validation methods for ensuring data consistency
 * and enforcing business rules across the application's data layer.
 *
 * @deprecated Use RepositoryValidationService for new code. This class is maintained
 * for backward compatibility with existing repository code.
 */
@Deprecated(
    "Use RepositoryValidationService for comprehensive validation",
    ReplaceWith("RepositoryValidationService"),
)
@Singleton
class DataValidator
    @Inject
    constructor(
        private val repositoryValidationService: RepositoryValidationService,
        private val businessRuleValidator: BusinessRuleValidator,
    ) {
        /**
         * Validates the complete AppData structure for consistency.
         *
         * @param data The AppData to validate
         * @throws DataException.ValidationException if validation fails
         */
        fun validateAppData(data: AppData) {
            // Use the new validation framework
            val result =
                runBlocking {
                    repositoryValidationService.validateAppDataForSave(data)
                }

            if (result.isFailure()) {
                throw DataException.ValidationException(
                    result.getFirstErrorMessage() ?: "Validation failed",
                )
            }

            // Fallback to legacy validation for backward compatibility
            try {
                validateLegacyConstraints(data)
            } catch (e: Exception) {
                throw DataException.ValidationException("Legacy validation failed: ${e.message}")
            }
        }

        /**
         * Legacy validation constraints for backward compatibility.
         */
        private fun validateLegacyConstraints(data: AppData) {
            // Validate entity name uniqueness
            validateNameUniqueness(data)

            // Validate entity relationships
            validateEntityRelationships(data)

            // Validate entity constraints
            validateEntityConstraints(data)

            // Validate data limits
            validateDataLimits(data)

            // Validate message constraints
            validateMessageConstraints(data)
        }

        /**
         * Validates SSH identity data.
         *
         * @param identity The SSH identity to validate
         * @throws DataException.ValidationException if validation fails
         */
        fun validateSshIdentity(identity: SshIdentity) {
            require(identity.name.isNotBlank()) { "SSH Identity name cannot be blank" }
            require(identity.name.length <= 100) { "SSH Identity name too long (max 100 chars)" }
            require(identity.encryptedPrivateKey.isNotBlank()) { "SSH Identity private key cannot be blank" }
            require(identity.publicKeyFingerprint.isNotBlank()) { "SSH Identity fingerprint cannot be blank" }
            require(identity.publicKeyFingerprint.matches(Regex("^[A-Fa-f0-9:]+$"))) {
                "Invalid SSH key fingerprint format"
            }

            // Validate description if provided
            identity.description?.let { desc ->
                require(desc.length <= 500) { "SSH Identity description too long (max 500 chars)" }
            }
        }

        /**
         * Validates server profile data.
         *
         * @param profile The server profile to validate
         * @throws DataException.ValidationException if validation fails
         */
        fun validateServerProfile(profile: ServerProfile) {
            require(profile.name.isNotBlank()) { "Server profile name cannot be blank" }
            require(profile.name.length <= 100) { "Server profile name too long (max 100 chars)" }
            require(profile.hostname.isNotBlank()) { "Server hostname cannot be blank" }
            require(profile.hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
                "Invalid hostname format"
            }
            require(profile.port in 1..65535) { "Port must be between 1 and 65535" }
            require(profile.username.isNotBlank()) { "Username cannot be blank" }
            require(profile.username.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                "Invalid username format"
            }
            require(profile.wrapperPort in 1..65535) { "Wrapper port must be between 1 and 65535" }
            require(profile.sshIdentityId.isNotBlank()) { "SSH Identity ID cannot be blank" }
        }

        /**
         * Validates project data.
         *
         * @param project The project to validate
         * @throws DataException.ValidationException if validation fails
         */
        fun validateProject(project: Project) {
            require(project.name.isNotBlank()) { "Project name cannot be blank" }
            require(project.name.length <= 100) { "Project name too long (max 100 chars)" }
            require(project.projectPath.isNotBlank()) { "Project path cannot be blank" }
            require(project.projectPath.length <= 500) { "Project path too long (max 500 chars)" }
            require(project.scriptsFolder.isNotBlank()) { "Scripts folder cannot be blank" }
            require(project.scriptsFolder.length <= 100) { "Scripts folder too long (max 100 chars)" }
            require(project.serverProfileId.isNotBlank()) { "Server profile ID cannot be blank" }

            // Validate optional fields
            project.repositoryUrl?.let { url ->
                require(url.isNotBlank()) { "Repository URL cannot be blank if provided" }
                require(url.length <= 1000) { "Repository URL too long (max 1000 chars)" }
                require(url.matches(Regex("^https?://.*"))) { "Repository URL must start with http:// or https://" }
            }

            project.lastError?.let { error ->
                require(error.length <= 1000) { "Last error message too long (max 1000 chars)" }
            }

            project.claudeSessionId?.let { sessionId ->
                require(sessionId.isNotBlank()) { "Claude session ID cannot be blank if provided" }
                require(sessionId.length <= 100) { "Claude session ID too long (max 100 chars)" }
            }
        }

        /**
         * Validates message data.
         *
         * @param message The message to validate
         * @throws DataException.ValidationException if validation fails
         */
        fun validateMessage(message: Message) {
            require(message.content.isNotBlank()) { "Message content cannot be blank" }
            require(message.content.length <= 50000) { "Message content too long (max 50000 chars)" }
            require(message.id.isNotBlank()) { "Message ID cannot be blank" }
            require(message.timestamp > 0) { "Message timestamp must be positive" }

            // Validate metadata
            message.metadata.forEach { (key, value) ->
                require(key.isNotBlank()) { "Metadata key cannot be blank" }
                require(key.length <= 100) { "Metadata key too long (max 100 chars)" }
                require(value.length <= 1000) { "Metadata value too long (max 1000 chars)" }
            }
        }

        /**
         * Validates that entity names are unique within their respective collections.
         */
        private fun validateNameUniqueness(data: AppData) {
            // Check SSH identity name uniqueness
            val identityNames = data.sshIdentities.map { it.name }
            require(identityNames.size == identityNames.toSet().size) {
                "Duplicate SSH identity names found"
            }

            // Check server profile name uniqueness
            val serverNames = data.serverProfiles.map { it.name }
            require(serverNames.size == serverNames.toSet().size) {
                "Duplicate server profile names found"
            }

            // Check project name uniqueness
            val projectNames = data.projects.map { it.name }
            require(projectNames.size == projectNames.toSet().size) {
                "Duplicate project names found"
            }

            // Check for ID uniqueness
            val identityIds = data.sshIdentities.map { it.id }
            require(identityIds.size == identityIds.toSet().size) {
                "Duplicate SSH identity IDs found"
            }

            val serverIds = data.serverProfiles.map { it.id }
            require(serverIds.size == serverIds.toSet().size) {
                "Duplicate server profile IDs found"
            }

            val projectIds = data.projects.map { it.id }
            require(projectIds.size == projectIds.toSet().size) {
                "Duplicate project IDs found"
            }
        }

        /**
         * Validates entity relationships (foreign key constraints).
         */
        private fun validateEntityRelationships(data: AppData) {
            val identityIds = data.sshIdentities.map { it.id }.toSet()
            val serverIds = data.serverProfiles.map { it.id }.toSet()
            val projectIds = data.projects.map { it.id }.toSet()

            // Validate server profile -> SSH identity relationships
            data.serverProfiles.forEach { server ->
                require(server.sshIdentityId in identityIds) {
                    "Server profile '${server.name}' references non-existent SSH identity '${server.sshIdentityId}'"
                }
            }

            // Validate project -> server profile relationships
            data.projects.forEach { project ->
                require(project.serverProfileId in serverIds) {
                    "Project '${project.name}' references non-existent server profile '${project.serverProfileId}'"
                }
            }

            // Validate message -> project relationships
            data.messages.keys.forEach { projectId ->
                require(projectId in projectIds || projectId == "system") {
                    "Messages reference non-existent project '$projectId'"
                }
            }
        }

        /**
         * Validates individual entity constraints.
         */
        private fun validateEntityConstraints(data: AppData) {
            // Validate each SSH identity
            data.sshIdentities.forEach { identity ->
                try {
                    validateSshIdentity(identity)
                } catch (e: IllegalArgumentException) {
                    throw DataException.ValidationException("SSH identity '${identity.name}' validation failed: ${e.message}")
                }
            }

            // Validate each server profile
            data.serverProfiles.forEach { profile ->
                try {
                    validateServerProfile(profile)
                } catch (e: IllegalArgumentException) {
                    throw DataException.ValidationException("Server profile '${profile.name}' validation failed: ${e.message}")
                }
            }

            // Validate each project
            data.projects.forEach { project ->
                try {
                    validateProject(project)
                } catch (e: IllegalArgumentException) {
                    throw DataException.ValidationException("Project '${project.name}' validation failed: ${e.message}")
                }
            }

            // Validate each message
            data.messages.values.flatten().forEach { message ->
                try {
                    validateMessage(message)
                } catch (e: IllegalArgumentException) {
                    throw DataException.ValidationException("Message '${message.id}' validation failed: ${e.message}")
                }
            }
        }

        /**
         * Validates data size limits.
         */
        private fun validateDataLimits(data: AppData) {
            require(data.sshIdentities.size <= 50) { "Maximum 50 SSH identities allowed" }
            require(data.serverProfiles.size <= 100) { "Maximum 100 server profiles allowed" }
            require(data.projects.size <= 200) { "Maximum 200 projects allowed" }

            // Check message limits per project
            data.messages.forEach { (projectId, messages) ->
                require(messages.size <= 1000) {
                    "Project '$projectId' has too many messages (max 1000)"
                }
            }

            // Check total message count
            val totalMessages = data.messages.values.sumOf { it.size }
            require(totalMessages <= 10000) { "Maximum 10000 total messages allowed" }
        }

        /**
         * Validates message-specific constraints.
         */
        private fun validateMessageConstraints(data: AppData) {
            data.messages.forEach { (projectId, messages) ->
                // Validate message ID uniqueness within project
                val messageIds = messages.map { it.id }
                require(messageIds.size == messageIds.toSet().size) {
                    "Duplicate message IDs found in project '$projectId'"
                }

                // Validate message timestamps are in order
                val timestamps = messages.map { it.timestamp }
                require(timestamps == timestamps.sorted()) {
                    "Messages in project '$projectId' are not in chronological order"
                }

                // Validate metadata size
                messages.forEach { message ->
                    require(message.metadata.size <= 20) {
                        "Message '${message.id}' has too many metadata entries (max 20)"
                    }
                }
            }
        }

        /**
         * Checks if a name is valid for entity naming.
         *
         * @param name The name to validate
         * @return true if valid, false otherwise
         */
        fun isValidEntityName(name: String): Boolean {
            return name.isNotBlank() &&
                name.length <= 100 &&
                name.matches(Regex("^[a-zA-Z0-9 _.-]+$"))
        }

        /**
         * Checks if a hostname is valid.
         *
         * @param hostname The hostname to validate
         * @return true if valid, false otherwise
         */
        fun isValidHostname(hostname: String): Boolean {
            return hostname.isNotBlank() &&
                hostname.length <= 253 &&
                hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))
        }

        /**
         * Checks if a port number is valid.
         *
         * @param port The port number to validate
         * @return true if valid, false otherwise
         */
        fun isValidPort(port: Int): Boolean {
            return port in 1..65535
        }
    }
