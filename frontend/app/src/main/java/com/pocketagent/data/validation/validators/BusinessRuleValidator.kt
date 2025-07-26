package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.MessageType
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.ValidationResultBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validator for business rules and cross-entity constraints.
 *
 * This validator handles complex business logic that spans multiple entities
 * and enforces application-specific rules beyond basic field validation.
 */
@Singleton
class BusinessRuleValidator
    @Inject
    constructor() {
        /**
         * Validate complete application data for business rule compliance.
         */
        fun validateAppData(data: AppData): ValidationResult {
            val builder = ValidationResultBuilder()

            // Validate entity relationships
            builder.addResult(validateEntityRelationships(data))

            // Validate business constraints
            builder.addResult(validateBusinessConstraints(data))

            // Validate data consistency
            builder.addResult(validateDataConsistency(data))

            // Validate resource limits
            builder.addResult(validateResourceLimits(data))

            return builder.build()
        }

        /**
         * Validate entity relationships and foreign key constraints.
         */
        fun validateEntityRelationships(data: AppData): ValidationResult {
            val builder = ValidationResultBuilder()

            val identityIds = data.sshIdentities.map { it.id }.toSet()
            val serverIds = data.serverProfiles.map { it.id }.toSet()
            val projectIds = data.projects.map { it.id }.toSet()

            // Validate server profile -> SSH identity relationships
            data.serverProfiles.forEach { server ->
                if (server.sshIdentityId !in identityIds) {
                    builder.addRelationshipError(
                        "Server profile '${server.name}' references non-existent SSH identity '${server.sshIdentityId}'",
                        "serverProfileId",
                        "MISSING_SSH_IDENTITY",
                    )
                }
            }

            // Validate project -> server profile relationships
            data.projects.forEach { project ->
                if (project.serverProfileId !in serverIds) {
                    builder.addRelationshipError(
                        "Project '${project.name}' references non-existent server profile '${project.serverProfileId}'",
                        "serverProfileId",
                        "MISSING_SERVER_PROFILE",
                    )
                }
            }

            // Validate message -> project relationships
            data.messages.keys.forEach { projectId ->
                if (projectId != "system" && projectId !in projectIds) {
                    builder.addRelationshipError(
                        "Messages reference non-existent project '$projectId'",
                        "projectId",
                        "MISSING_PROJECT",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validate business-specific constraints.
         */
        fun validateBusinessConstraints(data: AppData): ValidationResult {
            val builder = ValidationResultBuilder()

            // SSH Identity business rules
            builder.addResult(validateSshIdentityBusinessRules(data.sshIdentities))

            // Server Profile business rules
            builder.addResult(validateServerProfileBusinessRules(data.serverProfiles, data.sshIdentities))

            // Project business rules
            builder.addResult(validateProjectBusinessRules(data.projects, data.serverProfiles))

            // Message business rules
            builder.addResult(validateMessageBusinessRules(data.messages, data.projects))

            return builder.build()
        }

        /**
         * Validate SSH identity business rules.
         */
        fun validateSshIdentityBusinessRules(identities: List<SshIdentity>): ValidationResult {
            val builder = ValidationResultBuilder()

            // Check for duplicate names (case-insensitive)
            val nameGroups = identities.groupBy { it.name.lowercase().trim() }
            nameGroups.filter { it.value.size > 1 }.forEach { (name, duplicates) ->
                builder.addBusinessRuleError(
                    "Duplicate SSH identity name '$name' found in ${duplicates.map { it.id }}",
                    "name",
                    "DUPLICATE_SSH_IDENTITY_NAME",
                )
            }

            // Check for duplicate fingerprints
            val fingerprintGroups = identities.groupBy { it.publicKeyFingerprint }
            fingerprintGroups.filter { it.value.size > 1 }.forEach { (fingerprint, duplicates) ->
                builder.addBusinessRuleError(
                    "Duplicate SSH key fingerprint '$fingerprint' found in ${duplicates.map { it.id }}",
                    "publicKeyFingerprint",
                    "DUPLICATE_SSH_FINGERPRINT",
                )
            }

            // Check for suspicious patterns
            identities.forEach { identity ->
                // Check if name looks like a fingerprint
                if (identity.name.matches(Regex("^[A-Fa-f0-9:]+$")) || identity.name.startsWith("SHA256:")) {
                    builder.addBusinessRuleError(
                        "SSH identity name '${identity.name}' looks like a fingerprint",
                        "name",
                        "NAME_LOOKS_LIKE_FINGERPRINT",
                    )
                }

                // Check for very old identities that might be stale
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                if (identity.lastUsedAt != null && identity.lastUsedAt < thirtyDaysAgo) {
                    builder.addCustomError(
                        "SSH identity '${identity.name}' has not been used in over 30 days",
                        "lastUsedAt",
                        "STALE_SSH_IDENTITY",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validate server profile business rules.
         */
        fun validateServerProfileBusinessRules(
            profiles: List<ServerProfile>,
            identities: List<SshIdentity>,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            // Check for duplicate names (case-insensitive)
            val nameGroups = profiles.groupBy { it.name.lowercase().trim() }
            nameGroups.filter { it.value.size > 1 }.forEach { (name, duplicates) ->
                builder.addBusinessRuleError(
                    "Duplicate server profile name '$name' found in ${duplicates.map { it.id }}",
                    "name",
                    "DUPLICATE_SERVER_PROFILE_NAME",
                )
            }

            // Check for duplicate hostname:port combinations
            val hostPortGroups = profiles.groupBy { "${it.hostname.lowercase()}:${it.port}" }
            hostPortGroups.filter { it.value.size > 1 }.forEach { (hostPort, duplicates) ->
                builder.addBusinessRuleError(
                    "Duplicate hostname:port combination '$hostPort' found in ${duplicates.map { it.name }}",
                    "hostname",
                    "DUPLICATE_HOSTNAME_PORT",
                )
            }

            // Check for port conflicts within same hostname
            val hostGroups = profiles.groupBy { it.hostname.lowercase() }
            hostGroups.forEach { (hostname, hostProfiles) ->
                val portConflicts = mutableSetOf<Int>()
                hostProfiles.forEach { profile ->
                    if (profile.port == profile.wrapperPort) {
                        builder.addBusinessRuleError(
                            "Server profile '${profile.name}' has SSH port (${profile.port}) same as wrapper port",
                            "wrapperPort",
                            "PORT_CONFLICT_SAME_PROFILE",
                        )
                    }

                    // Check for conflicts with other profiles on same host
                    hostProfiles.filter { it.id != profile.id }.forEach { otherProfile ->
                        if (profile.port == otherProfile.wrapperPort || profile.wrapperPort == otherProfile.port) {
                            builder.addBusinessRuleError(
                                "Port conflict between '${profile.name}' and '${otherProfile.name}' on host '$hostname'",
                                "port",
                                "PORT_CONFLICT_BETWEEN_PROFILES",
                            )
                        }
                    }
                }
            }

            // Check SSH identity usage
            val identityUsage = profiles.groupBy { it.sshIdentityId }
            identities.forEach { identity ->
                val usage = identityUsage[identity.id]?.size ?: 0
                if (usage == 0) {
                    builder.addCustomError(
                        "SSH identity '${identity.name}' is not used by any server profiles",
                        "sshIdentityId",
                        "UNUSED_SSH_IDENTITY",
                    )
                } else if (usage > 10) {
                    builder.addCustomError(
                        "SSH identity '${identity.name}' is used by $usage server profiles (consider creating separate identities)",
                        "sshIdentityId",
                        "OVERUSED_SSH_IDENTITY",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validate project business rules.
         */
        fun validateProjectBusinessRules(
            projects: List<Project>,
            serverProfiles: List<ServerProfile>,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            // Check for duplicate names (case-insensitive)
            validateProjectNameUniqueness(projects, builder)

            // Check for duplicate project paths per server
            validateProjectPathUniqueness(projects, serverProfiles, builder)

            // Check for suspicious project configurations
            validateProjectConfigurations(projects, builder)

            // Check server profile usage
            validateServerProfileUsage(projects, serverProfiles, builder)

            return builder.build()
        }

        /**
         * Validate message business rules.
         */
        fun validateMessageBusinessRules(
            messagesByProject: Map<String, List<Message>>,
            projects: List<Project>,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            messagesByProject.forEach { (projectId, messages) ->
                val project = projects.find { it.id == projectId }
                val projectName = project?.name ?: "Unknown"

                // Validate message ordering
                builder.addResult(validateMessageOrdering(messages, projectName))

                // Check for conversation flow issues
                builder.addResult(validateConversationFlow(messages, projectName))

                // Check message distribution
                builder.addResult(validateMessageDistribution(messages, projectName))
            }

            return builder.build()
        }

        /**
         * Validates message ordering within a project.
         */
        private fun validateMessageOrdering(
            messages: List<Message>,
            projectName: String,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            val sortedMessages = messages.sortedBy { it.timestamp }
            if (messages != sortedMessages) {
                builder.addBusinessRuleError(
                    "Messages in project '$projectName' are not in chronological order",
                    "timestamp",
                    "MESSAGES_OUT_OF_ORDER",
                )
            }

            return builder.build()
        }

        /**
         * Validates conversation flow within a project.
         */
        private fun validateConversationFlow(
            messages: List<Message>,
            projectName: String,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            for (i in 1 until messages.size) {
                val prev = messages[i - 1]
                val curr = messages[i]

                // Check for excessive user inputs without responses
                if (prev.type == MessageType.USER_INPUT && curr.type == MessageType.USER_INPUT) {
                    val timeDiff = curr.timestamp - prev.timestamp
                    if (timeDiff < 5000) { // Less than 5 seconds
                        builder.addCustomError(
                            "Consecutive user inputs in project '$projectName' are very close together",
                            "type",
                            "RAPID_USER_INPUTS",
                        )
                    }
                }

                // Check for unreasonably long Claude responses
                if (curr.type == MessageType.CLAUDE_RESPONSE && curr.content.length > 20000) {
                    builder.addCustomError(
                        "Very long Claude response in project '$projectName' (${curr.content.length} chars)",
                        "content",
                        "VERY_LONG_RESPONSE",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validates message distribution within a project.
         */
        private fun validateMessageDistribution(
            messages: List<Message>,
            projectName: String,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            val messageTypes = messages.groupBy { it.type }
            val userMessages = messageTypes[MessageType.USER_INPUT]?.size ?: 0
            val claudeMessages = messageTypes[MessageType.CLAUDE_RESPONSE]?.size ?: 0

            if (userMessages > 0 && claudeMessages == 0) {
                builder.addCustomError(
                    "Project '$projectName' has user messages but no Claude responses",
                    "type",
                    "NO_CLAUDE_RESPONSES",
                )
            } else if (claudeMessages > userMessages * 2) {
                builder.addCustomError(
                    "Project '$projectName' has disproportionate Claude responses ($claudeMessages) to user inputs ($userMessages)",
                    "type",
                    "DISPROPORTIONATE_RESPONSES",
                )
            }

            return builder.build()
        }

        /**
         * Validate data consistency across the application.
         */
        fun validateDataConsistency(data: AppData): ValidationResult {
            val builder = ValidationResultBuilder()

            // Check timestamp consistency
            val now = System.currentTimeMillis()
            val oneYearAgo = now - (365 * 24 * 60 * 60 * 1000L)

            // All entities should have reasonable creation dates
            data.sshIdentities.forEach { identity ->
                if (identity.createdAt < oneYearAgo) {
                    builder.addCustomError(
                        "SSH identity '${identity.name}' has very old creation date",
                        "createdAt",
                        "VERY_OLD_ENTITY",
                    )
                }
            }

            // Check for orphaned data
            val allEntityIds =
                (
                    data.sshIdentities.map { it.id } +
                        data.serverProfiles.map { it.id } +
                        data.projects.map { it.id }
                ).toSet()

            if (allEntityIds.size != (data.sshIdentities.size + data.serverProfiles.size + data.projects.size)) {
                builder.addBusinessRuleError(
                    "Duplicate entity IDs found across different entity types",
                    "id",
                    "DUPLICATE_ENTITY_IDS",
                )
            }

            return builder.build()
        }

        /**
         * Validate resource limits and quotas.
         */
        fun validateResourceLimits(data: AppData): ValidationResult {
            val builder = ValidationResultBuilder()

            // Check entity count limits
            if (data.sshIdentities.size > 50) {
                builder.addBusinessRuleError(
                    "Too many SSH identities (${data.sshIdentities.size}, max 50)",
                    "sshIdentities",
                    "SSH_IDENTITY_LIMIT_EXCEEDED",
                )
            }

            if (data.serverProfiles.size > 100) {
                builder.addBusinessRuleError(
                    "Too many server profiles (${data.serverProfiles.size}, max 100)",
                    "serverProfiles",
                    "SERVER_PROFILE_LIMIT_EXCEEDED",
                )
            }

            if (data.projects.size > 200) {
                builder.addBusinessRuleError(
                    "Too many projects (${data.projects.size}, max 200)",
                    "projects",
                    "PROJECT_LIMIT_EXCEEDED",
                )
            }

            // Check message limits
            val totalMessages = data.messages.values.sumOf { it.size }
            if (totalMessages > 10000) {
                builder.addBusinessRuleError(
                    "Too many total messages ($totalMessages, max 10000)",
                    "messages",
                    "MESSAGE_LIMIT_EXCEEDED",
                )
            }

            data.messages.forEach { (projectId, messages) ->
                if (messages.size > 1000) {
                    val project = data.projects.find { it.id == projectId }
                    builder.addBusinessRuleError(
                        "Too many messages in project '${project?.name ?: projectId}' (${messages.size}, max 1000)",
                        "messages",
                        "PROJECT_MESSAGE_LIMIT_EXCEEDED",
                    )
                }
            }

            return builder.build()
        }

        // Helper methods for validateProjectBusinessRules complexity reduction

        private fun validateProjectNameUniqueness(
            projects: List<Project>,
            builder: ValidationResultBuilder,
        ) {
            val nameGroups = projects.groupBy { it.name.lowercase().trim() }
            nameGroups.filter { it.value.size > 1 }.forEach { (name, duplicates) ->
                builder.addBusinessRuleError(
                    "Duplicate project name '$name' found in ${duplicates.map { it.id }}",
                    "name",
                    "DUPLICATE_PROJECT_NAME",
                )
            }
        }

        private fun validateProjectPathUniqueness(
            projects: List<Project>,
            serverProfiles: List<ServerProfile>,
            builder: ValidationResultBuilder,
        ) {
            val serverProjectGroups = projects.groupBy { it.serverProfileId }
            serverProjectGroups.forEach { (serverId, serverProjects) ->
                val serverProfile = serverProfiles.find { it.id == serverId }
                val serverName = serverProfile?.name ?: "Unknown"

                val pathGroups = serverProjects.groupBy { it.projectPath.lowercase() }
                pathGroups.filter { it.value.size > 1 }.forEach { (path, duplicates) ->
                    builder.addBusinessRuleError(
                        "Duplicate project path '$path' on server '$serverName' found in ${duplicates.map { it.name }}",
                        "projectPath",
                        "DUPLICATE_PROJECT_PATH_ON_SERVER",
                    )
                }
            }
        }

        private fun validateProjectConfigurations(
            projects: List<Project>,
            builder: ValidationResultBuilder,
        ) {
            projects.forEach { project ->
                validateActiveProjectActivity(project, builder)
                validateErrorProjectMessage(project, builder)
                validateProjectNamePathConsistency(project, builder)
            }
        }

        private fun validateActiveProjectActivity(
            project: Project,
            builder: ValidationResultBuilder,
        ) {
            if (project.status == ProjectStatus.ACTIVE) {
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                if (project.lastActiveAt == null || project.lastActiveAt < oneHourAgo) {
                    builder.addBusinessRuleError(
                        "Project '${project.name}' is marked as ACTIVE but has no recent activity",
                        "status",
                        "ACTIVE_PROJECT_NO_ACTIVITY",
                    )
                }
            }
        }

        private fun validateErrorProjectMessage(
            project: Project,
            builder: ValidationResultBuilder,
        ) {
            if (project.status == ProjectStatus.ERROR && project.lastError.isNullOrBlank()) {
                builder.addBusinessRuleError(
                    "Project '${project.name}' is marked as ERROR but has no error message",
                    "lastError",
                    "ERROR_PROJECT_NO_MESSAGE",
                )
            }
        }

        private fun validateProjectNamePathConsistency(
            project: Project,
            builder: ValidationResultBuilder,
        ) {
            val pathBaseName =
                project.projectPath
                    .split("/", "\\")
                    .lastOrNull()
                    ?.lowercase()
            val projectNameNormalized = project.name.lowercase().replace("\\s+".toRegex(), "")

            if (isNamePathMismatch(pathBaseName, projectNameNormalized)) {
                builder.addCustomError(
                    "Project name '${project.name}' does not match path basename '$pathBaseName'",
                    "name",
                    "NAME_PATH_MISMATCH",
                )
            }
        }

        private fun isNamePathMismatch(
            pathBaseName: String?,
            projectNameNormalized: String,
        ): Boolean =
            pathBaseName != null &&
                pathBaseName != projectNameNormalized &&
                !pathBaseName.contains(projectNameNormalized) &&
                !projectNameNormalized.contains(pathBaseName)

        private fun validateServerProfileUsage(
            projects: List<Project>,
            serverProfiles: List<ServerProfile>,
            builder: ValidationResultBuilder,
        ) {
            val serverUsage = projects.groupBy { it.serverProfileId }
            serverProfiles.forEach { server ->
                val usage = serverUsage[server.id]?.size ?: 0
                validateServerUsagePatterns(server, usage, builder)
            }
        }

        private fun validateServerUsagePatterns(
            server: ServerProfile,
            usage: Int,
            builder: ValidationResultBuilder,
        ) {
            if (usage == 0 && server.status != ConnectionStatus.NEVER_CONNECTED) {
                builder.addCustomError(
                    "Server profile '${server.name}' has connection history but no projects",
                    "serverProfileId",
                    "CONNECTED_SERVER_NO_PROJECTS",
                )
            } else if (usage > 20) {
                builder.addCustomError(
                    "Server profile '${server.name}' has $usage projects (consider organizing)",
                    "serverProfileId",
                    "MANY_PROJECTS_ON_SERVER",
                )
            }
        }

        /**
         * Validate that an entity can be safely deleted.
         */
        fun validateEntityDeletion(
            entityType: String,
            entityId: String,
            data: AppData,
        ): ValidationResult {
            val builder = ValidationResultBuilder()

            when (entityType.lowercase()) {
                "sshidentity" -> {
                    val dependentServers = data.serverProfiles.filter { it.sshIdentityId == entityId }
                    if (dependentServers.isNotEmpty()) {
                        builder.addBusinessRuleError(
                            "Cannot delete SSH identity: used by server profiles ${dependentServers.map { it.name }}",
                            "sshIdentityId",
                            "SSH_IDENTITY_IN_USE",
                        )
                    }
                }
                "serverprofile" -> {
                    val dependentProjects = data.projects.filter { it.serverProfileId == entityId }
                    if (dependentProjects.isNotEmpty()) {
                        builder.addBusinessRuleError(
                            "Cannot delete server profile: used by projects ${dependentProjects.map { it.name }}",
                            "serverProfileId",
                            "SERVER_PROFILE_IN_USE",
                        )
                    }
                }
                "project" -> {
                    val hasMessages = data.messages.containsKey(entityId)
                    if (hasMessages) {
                        builder.addCustomError(
                            "Project has messages that will be deleted",
                            "projectId",
                            "PROJECT_HAS_MESSAGES",
                        )
                    }
                }
            }

            return builder.build()
        }
    }
