package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.validation.CommonValidationRules
import com.pocketagent.data.validation.ValidationError
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.ValidationResultBuilder
import com.pocketagent.data.validation.ValidationRuleBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for Project entities.
 *
 * Provides field-level, entity-level, and business rule validation for projects.
 * Supports both synchronous and asynchronous validation scenarios.
 */
@Singleton
class ProjectValidator
    @Inject
    constructor() {
        companion object {
            private const val MAX_NAME_LENGTH = 100
            private const val MAX_PATH_LENGTH = 500
            private const val MAX_SCRIPTS_FOLDER_LENGTH = 100
            private const val MAX_REPOSITORY_URL_LENGTH = 1000
            private const val MAX_LAST_ERROR_LENGTH = 1000
            private const val MAX_CLAUDE_SESSION_ID_LENGTH = 100

            private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
            private val PATH_REGEX = Regex("^[a-zA-Z0-9/\\\\._\\-~]+$")
            private val SCRIPTS_FOLDER_REGEX = Regex("^[a-zA-Z0-9._\\-]+$")
            private val URL_REGEX = Regex("^https?://.*")
        }

        /**
         * Validate a complete project entity.
         */
        fun validate(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Field-level validations
            builder.addResult(validateId(project.id))
            builder.addResult(validateName(project.name))
            builder.addResult(validateServerProfileId(project.serverProfileId))
            builder.addResult(validateProjectPath(project.projectPath))
            builder.addResult(validateScriptsFolder(project.scriptsFolder))
            builder.addResult(validateClaudeSessionId(project.claudeSessionId))
            builder.addResult(validateStatus(project.status))
            builder.addResult(validateCreatedAt(project.createdAt))
            builder.addResult(validateLastActiveAt(project.lastActiveAt))
            builder.addResult(validateRepositoryUrl(project.repositoryUrl))
            builder.addResult(validateLastError(project.lastError))

            // Entity-level validations
            builder.addResult(validateBusinessRules(project))

            return builder.build()
        }

        /**
         * Validate project ID.
         */
        fun validateId(id: String): ValidationResult =
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.notBlank("id"))
                .addRule(
                    { it.isNotEmpty() },
                    "Project ID cannot be empty",
                    "id",
                ).build()
                .validate(id)

        /**
         * Validate project name.
         */
        fun validateName(name: String): ValidationResult =
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.notBlank("name", "Project name cannot be blank"))
                .addRule(CommonValidationRules.stringLength("name", max = MAX_NAME_LENGTH))
                .addRule(
                    CommonValidationRules.regexPattern(
                        "name",
                        VALID_NAME_REGEX,
                        "Project name contains invalid characters. Only letters, numbers, spaces, hyphens, underscores, and brackets are allowed",
                    ),
                ).addRule(
                    { !it.startsWith(" ") && !it.endsWith(" ") },
                    "Project name cannot start or end with spaces",
                    "name",
                ).addRule(
                    { name ->
                        // Check for reserved project names
                        val reserved = listOf("system", "temp", "tmp", "cache", "logs", "admin", "root", "config")
                        !reserved.contains(name.lowercase())
                    },
                    "Project name '$name' is reserved. Please choose a different name",
                    "name",
                ).build()
                .validate(name)

        /**
         * Validate server profile ID.
         */
        fun validateServerProfileId(serverProfileId: String): ValidationResult =
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.notBlank("serverProfileId", "Server profile ID cannot be blank"))
                .addRule(
                    { it.isNotEmpty() },
                    "Server profile must be specified",
                    "serverProfileId",
                ).build()
                .validate(serverProfileId)

        /**
         * Validate project path.
         */
        fun validateProjectPath(projectPath: String): ValidationResult =
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.notBlank("projectPath", "Project path cannot be blank"))
                .addRule(CommonValidationRules.stringLength("projectPath", max = MAX_PATH_LENGTH))
                .addRule(
                    CommonValidationRules.regexPattern(
                        "projectPath",
                        PATH_REGEX,
                        "Project path contains invalid characters. Only letters, numbers, forward/back slashes, dots, underscores, hyphens, and tildes are allowed",
                    ),
                ).addRule(
                    { path ->
                        // Basic path validation
                        !path.contains("//") && !path.contains("\\\\") && !path.contains("..")
                    },
                    "Project path contains invalid sequences (double slashes or dot-dot)",
                    "projectPath",
                ).addRule(
                    { path ->
                        // Should not be root paths
                        path != "/" && path != "\\" && path != "C:\\" && path != "~"
                    },
                    "Project path cannot be a root directory",
                    "projectPath",
                ).addRule(
                    { path ->
                        // Should not end with slash unless it's just a single character
                        if (path.length > 1) {
                            !path.endsWith("/") && !path.endsWith("\\")
                        } else {
                            true
                        }
                    },
                    "Project path should not end with a slash",
                    "projectPath",
                ).build()
                .validate(projectPath)

        /**
         * Validate scripts folder.
         */
        fun validateScriptsFolder(scriptsFolder: String): ValidationResult =
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.notBlank("scriptsFolder", "Scripts folder cannot be blank"))
                .addRule(CommonValidationRules.stringLength("scriptsFolder", max = MAX_SCRIPTS_FOLDER_LENGTH))
                .addRule(
                    CommonValidationRules.regexPattern(
                        "scriptsFolder",
                        SCRIPTS_FOLDER_REGEX,
                        "Scripts folder contains invalid characters. Only letters, numbers, dots, underscores, and hyphens are allowed",
                    ),
                ).addRule(
                    { !it.startsWith(".") },
                    "Scripts folder cannot start with a dot",
                    "scriptsFolder",
                ).addRule(
                    { folder ->
                        // Should not be reserved folder names
                        val reserved = listOf(
                            "system", "tmp", "temp", "cache", "logs", "config", 
                            "bin", "usr", "var", "etc"
                        )
                        !reserved.contains(folder.lowercase())
                    },
                    "Scripts folder name '$scriptsFolder' is reserved. Please choose a different name",
                    "scriptsFolder",
                ).build()
                .validate(scriptsFolder)

        /**
         * Validate optional Claude session ID.
         */
        fun validateClaudeSessionId(claudeSessionId: String?): ValidationResult =
            if (claudeSessionId == null) {
                ValidationResult.Success
            } else {
                ValidationRuleBuilder<String>()
                    .addRule(CommonValidationRules.notBlank("claudeSessionId", "Claude session ID cannot be blank if provided"))
                    .addRule(CommonValidationRules.stringLength("claudeSessionId", max = MAX_CLAUDE_SESSION_ID_LENGTH))
                    .addRule(
                        { it.trim() == it },
                        "Claude session ID cannot have leading or trailing whitespace",
                        "claudeSessionId",
                    ).build()
                    .validate(claudeSessionId)
            }

        /**
         * Validate project status.
         */
        fun validateStatus(status: ProjectStatus): ValidationResult {
            // All enum values are valid, but we can add business logic
            return ValidationResult.Success
        }

        /**
         * Validate created timestamp.
         */
        fun validateCreatedAt(createdAt: Long): ValidationResult =
            ValidationRuleBuilder<Long>()
                .addRule(CommonValidationRules.positiveTimestamp("createdAt"))
                .addRule(
                    { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                    "Created timestamp cannot be in the future",
                    "createdAt",
                ).build()
                .validate(createdAt)

        /**
         * Validate optional last active timestamp.
         */
        fun validateLastActiveAt(lastActiveAt: Long?): ValidationResult =
            if (lastActiveAt == null) {
                ValidationResult.Success
            } else {
                ValidationRuleBuilder<Long>()
                    .addRule(CommonValidationRules.positiveTimestamp("lastActiveAt"))
                    .addRule(
                        { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                        "Last active timestamp cannot be in the future",
                        "lastActiveAt",
                    ).build()
                    .validate(lastActiveAt)
            }

        /**
         * Validate optional repository URL.
         */
        fun validateRepositoryUrl(repositoryUrl: String?): ValidationResult =
            if (repositoryUrl == null) {
                ValidationResult.Success
            } else {
                ValidationRuleBuilder<String>()
                    .addRule(CommonValidationRules.notBlank("repositoryUrl", "Repository URL cannot be blank if provided"))
                    .addRule(CommonValidationRules.stringLength("repositoryUrl", max = MAX_REPOSITORY_URL_LENGTH))
                    .addRule(
                        CommonValidationRules.regexPattern(
                            "repositoryUrl",
                            URL_REGEX,
                            "Repository URL must start with http:// or https://",
                        ),
                    ).addRule(
                        { url ->
                            // Additional URL validation
                            try {
                                val uri = java.net.URI(url)
                                uri.host != null && uri.scheme in listOf("http", "https")
                            } catch (e: Exception) {
                                false
                            }
                        },
                        "Repository URL is not a valid URL",
                        "repositoryUrl",
                    ).build()
                    .validate(repositoryUrl)
            }

        /**
         * Validate optional last error message.
         */
        fun validateLastError(lastError: String?): ValidationResult =
            if (lastError == null) {
                ValidationResult.Success
            } else {
                ValidationRuleBuilder<String>()
                    .addRule(CommonValidationRules.stringLength("lastError", max = MAX_LAST_ERROR_LENGTH))
                    .addRule(
                        { !it.trim().isEmpty() },
                        "Last error cannot be only whitespace",
                        "lastError",
                    ).build()
                    .validate(lastError)
            }

        /**
         * Validate business rules for project.
         */
        fun validateBusinessRules(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Validate timestamp relationship
            builder.addResult(validateTimestampConsistency(project))

            // Validate status consistency
            builder.addResult(validateStatusConsistency(project))

            // Validate path and folder relationships
            builder.addResult(validatePathRelationships(project))

            // Validate repository URL patterns
            builder.addResult(validateRepositoryUrlPatterns(project))

            return builder.build()
        }

        /**
         * Validates timestamp consistency for project.
         */
        private fun validateTimestampConsistency(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            if (project.lastActiveAt != null && project.lastActiveAt < project.createdAt) {
                builder.addBusinessRuleError(
                    "Last active timestamp cannot be before creation timestamp",
                    "lastActiveAt",
                    "INVALID_TIMESTAMP_ORDER",
                )
            }

            return builder.build()
        }

        /**
         * Validates status consistency for project.
         */
        private fun validateStatusConsistency(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            when (project.status) {
                ProjectStatus.INACTIVE -> {
                    builder.addResult(validateInactiveStatusConsistency(project))
                }
                ProjectStatus.ACTIVE -> {
                    builder.addResult(validateActiveStatusConsistency(project))
                }
                ProjectStatus.ERROR -> {
                    builder.addResult(validateErrorStatusConsistency(project))
                }
                ProjectStatus.CONNECTING, ProjectStatus.DISCONNECTED -> {
                    // These are transitional states, no specific requirements
                }
            }

            return builder.build()
        }

        /**
         * Validates inactive status consistency.
         */
        private fun validateInactiveStatusConsistency(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Inactive projects should not have active Claude sessions
            if (project.claudeSessionId != null) {
                builder.addBusinessRuleError(
                    "Inactive project should not have an active Claude session",
                    "claudeSessionId",
                    "STATUS_SESSION_INCONSISTENCY",
                )
            }

            return builder.build()
        }

        /**
         * Validates active status consistency.
         */
        private fun validateActiveStatusConsistency(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Active projects should have been active recently
            if (project.lastActiveAt != null) {
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                if (project.lastActiveAt < oneHourAgo) {
                    builder.addBusinessRuleError(
                        "Project marked as ACTIVE but last activity was more than 1 hour ago",
                        "lastActiveAt",
                        "STATUS_ACTIVITY_INCONSISTENCY",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validates error status consistency.
         */
        private fun validateErrorStatusConsistency(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Error projects should have an error message
            if (project.lastError.isNullOrBlank()) {
                builder.addBusinessRuleError(
                    "Project marked as ERROR but no error message is provided",
                    "lastError",
                    "STATUS_ERROR_INCONSISTENCY",
                )
            }

            return builder.build()
        }

        /**
         * Validates path and folder relationships.
         */
        private fun validatePathRelationships(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            // Validate path and scripts folder relationship
            if (project.projectPath.contains(project.scriptsFolder)) {
                builder.addBusinessRuleError(
                    "Scripts folder should not be part of the project path",
                    "scriptsFolder",
                    "SCRIPTS_FOLDER_IN_PATH",
                )
            }

            // Validate name doesn't contain path information
            if (project.name.contains("/") || project.name.contains("\\")) {
                builder.addBusinessRuleError(
                    "Project name should not contain path separators",
                    "name",
                    "NAME_CONTAINS_PATH",
                )
            }

            return builder.build()
        }

        /**
         * Validates repository URL patterns.
         */
        private fun validateRepositoryUrlPatterns(project: Project): ValidationResult {
            val builder = ValidationResultBuilder()

            project.repositoryUrl?.let { url ->
                val commonPatterns =
                    listOf(
                        "github.com",
                        "gitlab.com",
                        "bitbucket.org",
                        "dev.azure.com",
                        "sourceforge.net",
                    )

                val isCommonProvider =
                    commonPatterns.any { pattern ->
                        url.contains(pattern, ignoreCase = true)
                    }

                if (!isCommonProvider && !url.contains("localhost") && !url.contains("127.0.0.1")) {
                    // Just a warning, not an error
                    builder.addCustomError(
                        "Repository URL does not match common Git hosting providers. Please verify the URL is correct",
                        "repositoryUrl",
                        "UNCOMMON_REPOSITORY_PROVIDER",
                    )
                }
            }

            return builder.build()
        }

        /**
         * Validate for creation (additional checks for new entities).
         */
        fun validateForCreation(project: Project): ValidationResult {
            val baseValidation = validate(project)
            val builder = ValidationResultBuilder().addResult(baseValidation)

            // Additional creation-specific validations
            val now = System.currentTimeMillis()

            // Created timestamp should be recent (within last hour)
            if (Math.abs(now - project.createdAt) > 3600000) {
                builder.addBusinessRuleError(
                    "Created timestamp should be recent for new projects",
                    "createdAt",
                    "CREATION_TIMESTAMP_NOT_RECENT",
                )
            }

            // New project should not have lastActiveAt set
            if (project.lastActiveAt != null) {
                builder.addBusinessRuleError(
                    "New project should not have last active timestamp set",
                    "lastActiveAt",
                    "NEW_PROJECT_ALREADY_ACTIVE",
                )
            }

            // New project should have INACTIVE status
            if (project.status != ProjectStatus.INACTIVE) {
                builder.addBusinessRuleError(
                    "New project should have INACTIVE status",
                    "status",
                    "NEW_PROJECT_INVALID_STATUS",
                )
            }

            // New project should not have Claude session
            if (project.claudeSessionId != null) {
                builder.addBusinessRuleError(
                    "New project should not have Claude session ID set",
                    "claudeSessionId",
                    "NEW_PROJECT_HAS_SESSION",
                )
            }

            // New project should not have error
            if (project.lastError != null) {
                builder.addBusinessRuleError(
                    "New project should not have error message set",
                    "lastError",
                    "NEW_PROJECT_HAS_ERROR",
                )
            }

            return builder.build()
        }

        /**
         * Validate for update (additional checks for existing entities).
         */
        fun validateForUpdate(
            original: Project,
            updated: Project,
        ): ValidationResult {
            val baseValidation = validate(updated)
            val builder = ValidationResultBuilder().addResult(baseValidation)

            // Additional update-specific validations

            // ID should not change
            if (original.id != updated.id) {
                builder.addBusinessRuleError(
                    "Project ID cannot be changed during update",
                    "id",
                    "ID_CHANGE_NOT_ALLOWED",
                )
            }

            // Created timestamp should not change
            if (original.createdAt != updated.createdAt) {
                builder.addBusinessRuleError(
                    "Created timestamp cannot be changed during update",
                    "createdAt",
                    "CREATION_TIMESTAMP_CHANGE_NOT_ALLOWED",
                )
            }

            // Last active timestamp should not go backwards
            if (original.lastActiveAt != null && updated.lastActiveAt != null && updated.lastActiveAt < original.lastActiveAt) {
                builder.addBusinessRuleError(
                    "Last active timestamp cannot go backwards",
                    "lastActiveAt",
                    "LAST_ACTIVE_TIMESTAMP_BACKWARDS",
                )
            }

            // Status transitions should be valid
            builder.addResult(validateStatusTransition(original.status, updated.status))

            // Server profile should not change if project is active
            if (original.serverProfileId != updated.serverProfileId && updated.status == ProjectStatus.ACTIVE) {
                builder.addBusinessRuleError(
                    "Cannot change server profile while project is active",
                    "serverProfileId",
                    "SERVER_CHANGE_WHILE_ACTIVE",
                )
            }

            return builder.build()
        }

        /**
         * Validate project status transitions.
         */
        fun validateStatusTransition(
            fromStatus: ProjectStatus,
            toStatus: ProjectStatus,
        ): ValidationResult {
            val validTransitions =
                mapOf(
                    ProjectStatus.INACTIVE to
                        setOf(
                            ProjectStatus.CONNECTING,
                            ProjectStatus.INACTIVE,
                        ),
                    ProjectStatus.CONNECTING to
                        setOf(
                            ProjectStatus.ACTIVE,
                            ProjectStatus.ERROR,
                            ProjectStatus.DISCONNECTED,
                            ProjectStatus.INACTIVE,
                            ProjectStatus.CONNECTING,
                        ),
                    ProjectStatus.ACTIVE to
                        setOf(
                            ProjectStatus.DISCONNECTED,
                            ProjectStatus.ERROR,
                            ProjectStatus.CONNECTING,
                            ProjectStatus.ACTIVE,
                        ),
                    ProjectStatus.DISCONNECTED to
                        setOf(
                            ProjectStatus.CONNECTING,
                            ProjectStatus.INACTIVE,
                            ProjectStatus.ERROR,
                            ProjectStatus.DISCONNECTED,
                        ),
                    ProjectStatus.ERROR to
                        setOf(
                            ProjectStatus.CONNECTING,
                            ProjectStatus.INACTIVE,
                            ProjectStatus.DISCONNECTED,
                            ProjectStatus.ERROR,
                        ),
                )

            val allowedTransitions = validTransitions[fromStatus] ?: emptySet()

            return if (toStatus in allowedTransitions) {
                ValidationResult.Success
            } else {
                ValidationResult.Failure(
                    ValidationError.businessRuleError(
                        "Invalid status transition from $fromStatus to $toStatus",
                        "status",
                        "INVALID_STATUS_TRANSITION",
                    ),
                )
            }
        }

        /**
         * Validate project name uniqueness against a list of existing names.
         */
        fun validateNameUniqueness(
            name: String,
            existingNames: List<String>,
            excludeId: String? = null,
        ): ValidationResult {
            val normalizedName = name.trim().lowercase()
            val conflictingNames =
                existingNames.filter { existingName ->
                    existingName.trim().lowercase() == normalizedName
                }

            return if (conflictingNames.isNotEmpty()) {
                ValidationResult.Failure(
                    ValidationError.businessRuleError(
                        "Project name '$name' already exists",
                        "name",
                        "DUPLICATE_NAME",
                    ),
                )
            } else {
                ValidationResult.Success
            }
        }

        /**
         * Validate project path uniqueness on the same server.
         */
        fun validateProjectPathUniqueness(
            projectPath: String,
            serverProfileId: String,
            existingProjects: List<Pair<String, String>>, // (path, serverProfileId)
            excludeId: String? = null,
        ): ValidationResult {
            val conflictingProjects =
                existingProjects.filter { (existingPath, existingServerId) ->
                    existingPath.equals(projectPath, ignoreCase = true) && existingServerId == serverProfileId
                }

            return if (conflictingProjects.isNotEmpty()) {
                ValidationResult.Failure(
                    ValidationError.businessRuleError(
                        "Project with path '$projectPath' already exists on this server",
                        "projectPath",
                        "DUPLICATE_PROJECT_PATH",
                    ),
                )
            } else {
                ValidationResult.Success
            }
        }

        /**
         * Quick validation for UI field updates (less comprehensive).
         */
        fun validateField(
            field: String,
            value: Any?,
        ): ValidationResult =
            when (field) {
                "name" -> validateName(value as? String ?: "")
                "projectPath" -> validateProjectPath(value as? String ?: "")
                "scriptsFolder" -> validateScriptsFolder(value as? String ?: "")
                "repositoryUrl" -> validateRepositoryUrl(value as? String)
                "serverProfileId" -> validateServerProfileId(value as? String ?: "")
                "claudeSessionId" -> validateClaudeSessionId(value as? String)
                "lastError" -> validateLastError(value as? String)
                else -> ValidationResult.Success
            }
    }
