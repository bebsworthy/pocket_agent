package com.pocketagent.data.validation

import com.pocketagent.data.models.*
import com.pocketagent.data.validation.validators.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that integrates the validation framework with repository operations.
 * 
 * This service provides a unified interface for validating entities before
 * repository operations and handles both synchronous and asynchronous validation.
 */
@Singleton
class RepositoryValidationService @Inject constructor(
    private val sshIdentityValidator: SshIdentityValidator,
    private val serverProfileValidator: ServerProfileValidator,
    private val projectValidator: ProjectValidator,
    private val messageValidator: MessageValidator,
    private val businessRuleValidator: BusinessRuleValidator,
    private val asyncValidator: AsyncValidator
) {
    
    /**
     * Validate SSH identity for creation.
     */
    suspend fun validateSshIdentityForCreation(
        identity: SshIdentity,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation
        builder.addResult(sshIdentityValidator.validateForCreation(identity))
        
        // Uniqueness validation
        val existingNames = existingData.sshIdentities.map { it.name }
        builder.addResult(sshIdentityValidator.validateNameUniqueness(identity.name, existingNames))
        
        val existingFingerprints = existingData.sshIdentities.map { it.publicKeyFingerprint }
        builder.addResult(sshIdentityValidator.validateFingerprintUniqueness(identity.publicKeyFingerprint, existingFingerprints))
        
        builder.build()
    }
    
    /**
     * Validate SSH identity for update.
     */
    suspend fun validateSshIdentityForUpdate(
        original: SshIdentity,
        updated: SshIdentity,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation and update-specific checks
        builder.addResult(sshIdentityValidator.validateForUpdate(original, updated))
        
        // Uniqueness validation (excluding current entity)
        if (original.name != updated.name) {
            val existingNames = existingData.sshIdentities
                .filter { it.id != updated.id }
                .map { it.name }
            builder.addResult(sshIdentityValidator.validateNameUniqueness(updated.name, existingNames, updated.id))
        }
        
        if (original.publicKeyFingerprint != updated.publicKeyFingerprint) {
            val existingFingerprints = existingData.sshIdentities
                .filter { it.id != updated.id }
                .map { it.publicKeyFingerprint }
            builder.addResult(sshIdentityValidator.validateFingerprintUniqueness(updated.publicKeyFingerprint, existingFingerprints, updated.id))
        }
        
        builder.build()
    }
    
    /**
     * Validate SSH identity for deletion.
     */
    suspend fun validateSshIdentityForDeletion(
        identityId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        businessRuleValidator.validateEntityDeletion("sshidentity", identityId, existingData)
    }
    
    /**
     * Validate server profile for creation.
     */
    suspend fun validateServerProfileForCreation(
        profile: ServerProfile,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation
        builder.addResult(serverProfileValidator.validateForCreation(profile))
        
        // Relationship validation
        val sshIdentityExists = existingData.sshIdentities.any { it.id == profile.sshIdentityId }
        if (!sshIdentityExists) {
            builder.addRelationshipError(
                "SSH identity '${profile.sshIdentityId}' does not exist",
                "sshIdentityId",
                "SSH_IDENTITY_NOT_FOUND"
            )
        }
        
        // Uniqueness validation
        val existingNames = existingData.serverProfiles.map { it.name }
        builder.addResult(serverProfileValidator.validateNameUniqueness(profile.name, existingNames))
        
        val existingHostnamePorts = existingData.serverProfiles.map { it.hostname to it.port }
        builder.addResult(serverProfileValidator.validateHostnamePortUniqueness(
            profile.hostname, profile.port, existingHostnamePorts
        ))
        
        builder.build()
    }
    
    /**
     * Validate server profile for update.
     */
    suspend fun validateServerProfileForUpdate(
        original: ServerProfile,
        updated: ServerProfile,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation and update-specific checks
        builder.addResult(serverProfileValidator.validateForUpdate(original, updated))
        
        // Relationship validation
        val sshIdentityExists = existingData.sshIdentities.any { it.id == updated.sshIdentityId }
        if (!sshIdentityExists) {
            builder.addRelationshipError(
                "SSH identity '${updated.sshIdentityId}' does not exist",
                "sshIdentityId",
                "SSH_IDENTITY_NOT_FOUND"
            )
        }
        
        // Uniqueness validation (excluding current entity)
        if (original.name != updated.name) {
            val existingNames = existingData.serverProfiles
                .filter { it.id != updated.id }
                .map { it.name }
            builder.addResult(serverProfileValidator.validateNameUniqueness(updated.name, existingNames, updated.id))
        }
        
        if (original.hostname != updated.hostname || original.port != updated.port) {
            val existingHostnamePorts = existingData.serverProfiles
                .filter { it.id != updated.id }
                .map { it.hostname to it.port }
            builder.addResult(serverProfileValidator.validateHostnamePortUniqueness(
                updated.hostname, updated.port, existingHostnamePorts, updated.id
            ))
        }
        
        builder.build()
    }
    
    /**
     * Validate server profile for deletion.
     */
    suspend fun validateServerProfileForDeletion(
        profileId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        businessRuleValidator.validateEntityDeletion("serverprofile", profileId, existingData)
    }
    
    /**
     * Validate project for creation.
     */
    suspend fun validateProjectForCreation(
        project: Project,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation
        builder.addResult(projectValidator.validateForCreation(project))
        
        // Relationship validation
        val serverProfileExists = existingData.serverProfiles.any { it.id == project.serverProfileId }
        if (!serverProfileExists) {
            builder.addRelationshipError(
                "Server profile '${project.serverProfileId}' does not exist",
                "serverProfileId",
                "SERVER_PROFILE_NOT_FOUND"
            )
        }
        
        // Uniqueness validation
        val existingNames = existingData.projects.map { it.name }
        builder.addResult(projectValidator.validateNameUniqueness(project.name, existingNames))
        
        val existingProjectPaths = existingData.projects
            .filter { it.serverProfileId == project.serverProfileId }
            .map { it.projectPath to it.serverProfileId }
        builder.addResult(projectValidator.validateProjectPathUniqueness(
            project.projectPath, project.serverProfileId, existingProjectPaths
        ))
        
        builder.build()
    }
    
    /**
     * Validate project for update.
     */
    suspend fun validateProjectForUpdate(
        original: Project,
        updated: Project,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation and update-specific checks
        builder.addResult(projectValidator.validateForUpdate(original, updated))
        
        // Relationship validation
        val serverProfileExists = existingData.serverProfiles.any { it.id == updated.serverProfileId }
        if (!serverProfileExists) {
            builder.addRelationshipError(
                "Server profile '${updated.serverProfileId}' does not exist",
                "serverProfileId",
                "SERVER_PROFILE_NOT_FOUND"
            )
        }
        
        // Uniqueness validation (excluding current entity)
        if (original.name != updated.name) {
            val existingNames = existingData.projects
                .filter { it.id != updated.id }
                .map { it.name }
            builder.addResult(projectValidator.validateNameUniqueness(updated.name, existingNames, updated.id))
        }
        
        if (original.projectPath != updated.projectPath || original.serverProfileId != updated.serverProfileId) {
            val existingProjectPaths = existingData.projects
                .filter { it.id != updated.id && it.serverProfileId == updated.serverProfileId }
                .map { it.projectPath to it.serverProfileId }
            builder.addResult(projectValidator.validateProjectPathUniqueness(
                updated.projectPath, updated.serverProfileId, existingProjectPaths, updated.id
            ))
        }
        
        builder.build()
    }
    
    /**
     * Validate project for deletion.
     */
    suspend fun validateProjectForDeletion(
        projectId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        businessRuleValidator.validateEntityDeletion("project", projectId, existingData)
    }
    
    /**
     * Validate message for creation.
     */
    suspend fun validateMessageForCreation(
        message: Message,
        projectId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation
        builder.addResult(messageValidator.validateForCreation(message))
        
        // Relationship validation
        if (projectId != "system") {
            val projectExists = existingData.projects.any { it.id == projectId }
            if (!projectExists) {
                builder.addRelationshipError(
                    "Project '$projectId' does not exist",
                    "projectId",
                    "PROJECT_NOT_FOUND"
                )
            }
        }
        
        // Uniqueness validation within project
        val existingMessageIds = existingData.messages[projectId]?.map { it.id } ?: emptyList()
        builder.addResult(messageValidator.validateIdUniqueness(message.id, existingMessageIds))
        
        builder.build()
    }
    
    /**
     * Validate message for update.
     */
    suspend fun validateMessageForUpdate(
        original: Message,
        updated: Message,
        projectId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic field validation and update-specific checks
        builder.addResult(messageValidator.validateForUpdate(original, updated))
        
        // Uniqueness validation within project (excluding current message)
        if (original.id != updated.id) {
            val existingMessageIds = existingData.messages[projectId]
                ?.filter { it.id != original.id }
                ?.map { it.id } ?: emptyList()
            builder.addResult(messageValidator.validateIdUniqueness(updated.id, existingMessageIds, original.id))
        }
        
        builder.build()
    }
    
    /**
     * Validate message batch for creation.
     */
    suspend fun validateMessageBatchForCreation(
        messages: List<Message>,
        projectId: String,
        existingData: AppData
    ): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Validate batch consistency
        builder.addResult(messageValidator.validateMessageBatch(messages))
        
        // Validate each message for creation
        messages.forEach { message ->
            builder.addResult(validateMessageForCreation(message, projectId, existingData))
        }
        
        builder.build()
    }
    
    /**
     * Validate complete app data for consistency.
     */
    suspend fun validateCompleteAppData(data: AppData): ValidationResult = withContext(Dispatchers.Default) {
        businessRuleValidator.validateAppData(data)
    }
    
    /**
     * Validate app data before save operation.
     */
    suspend fun validateAppDataForSave(data: AppData): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Validate all entities individually
        data.sshIdentities.forEach { identity ->
            builder.addResult(sshIdentityValidator.validate(identity))
        }
        
        data.serverProfiles.forEach { profile ->
            builder.addResult(serverProfileValidator.validate(profile))
        }
        
        data.projects.forEach { project ->
            builder.addResult(projectValidator.validate(project))
        }
        
        data.messages.values.flatten().forEach { message ->
            builder.addResult(messageValidator.validate(message))
        }
        
        // Validate business rules and relationships
        builder.addResult(businessRuleValidator.validateAppData(data))
        
        builder.build()
    }
    
    /**
     * Validate field update for real-time validation.
     */
    suspend fun validateFieldUpdate(
        entityType: String,
        field: String,
        value: Any?,
        context: Map<String, Any> = emptyMap()
    ): ValidationResult = withContext(Dispatchers.Default) {
        when (entityType.lowercase()) {
            "sshidentity" -> sshIdentityValidator.validateField(field, value)
            "serverprofile" -> serverProfileValidator.validateField(field, value)
            "project" -> projectValidator.validateField(field, value)
            "message" -> messageValidator.validateField(field, value)
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validate with async database constraints.
     */
    suspend fun validateWithAsyncConstraints(
        validation: suspend () -> ValidationResult,
        asyncConstraints: List<suspend () -> ValidationResult> = emptyList()
    ): ValidationResult {
        return asyncValidator.validateParallel(
            validation,
            *asyncConstraints.toTypedArray()
        )
    }
    
    /**
     * Validate repository URL format and accessibility.
     */
    suspend fun validateRepositoryUrl(url: String): ValidationResult = withContext(Dispatchers.Default) {
        val builder = ValidationResultBuilder()
        
        // Basic URL format validation
        if (url.isBlank()) {
            builder.addFieldError("Repository URL cannot be blank", "repositoryUrl", "BLANK_URL")
            return@withContext builder.build()
        }
        
        // Check URL format
        try {
            val uri = java.net.URI(url)
            if (uri.scheme == null) {
                builder.addFieldError("Repository URL must include scheme (https://)", "repositoryUrl", "MISSING_SCHEME")
            }
            if (uri.host == null) {
                builder.addFieldError("Repository URL must include hostname", "repositoryUrl", "MISSING_HOST")
            }
        } catch (e: Exception) {
            builder.addFieldError("Invalid URL format: ${e.message}", "repositoryUrl", "INVALID_FORMAT")
        }
        
        // Validate common Git repository patterns
        val validPatterns = listOf(
            Regex("^https://github\\.com/[\\w\\-_]+/[\\w\\-_.]+(\\.git)?$"),
            Regex("^https://gitlab\\.com/[\\w\\-_]+/[\\w\\-_.]+(\\.git)?$"),
            Regex("^https://bitbucket\\.org/[\\w\\-_]+/[\\w\\-_.]+(\\.git)?$"),
            Regex("^git@github\\.com:[\\w\\-_]+/[\\w\\-_.]+\\.git$"),
            Regex("^git@gitlab\\.com:[\\w\\-_]+/[\\w\\-_.]+\\.git$"),
            Regex("^https://.*\\.git$") // Generic Git URL
        )
        
        val isValidPattern = validPatterns.any { it.matches(url) }
        if (!isValidPattern && !url.contains("localhost") && !url.contains("127.0.0.1")) {
            builder.addCustomError(
                "Repository URL does not match common Git hosting patterns. Please verify the URL is correct.",
                "repositoryUrl",
                "UNCOMMON_PATTERN"
            )
        }
        
        builder.build()
    }
    
    /**
     * Create async uniqueness validation rule.
     */
    fun createAsyncUniquenessValidator(
        entityType: String,
        field: String,
        value: String,
        excludeId: String? = null,
        dataProvider: suspend () -> AppData
    ): suspend () -> ValidationResult = {
        val data = dataProvider()
        when (entityType.lowercase()) {
            "sshidentity" -> when (field) {
                "name" -> {
                    val existingNames = data.sshIdentities
                        .filter { it.id != excludeId }
                        .map { it.name }
                    sshIdentityValidator.validateNameUniqueness(value, existingNames, excludeId)
                }
                "publicKeyFingerprint" -> {
                    val existingFingerprints = data.sshIdentities
                        .filter { it.id != excludeId }
                        .map { it.publicKeyFingerprint }
                    sshIdentityValidator.validateFingerprintUniqueness(value, existingFingerprints, excludeId)
                }
                else -> ValidationResult.Success
            }
            "serverprofile" -> when (field) {
                "name" -> {
                    val existingNames = data.serverProfiles
                        .filter { it.id != excludeId }
                        .map { it.name }
                    serverProfileValidator.validateNameUniqueness(value, existingNames, excludeId)
                }
                else -> ValidationResult.Success
            }
            "project" -> when (field) {
                "name" -> {
                    val existingNames = data.projects
                        .filter { it.id != excludeId }
                        .map { it.name }
                    projectValidator.validateNameUniqueness(value, existingNames, excludeId)
                }
                else -> ValidationResult.Success
            }
            else -> ValidationResult.Success
        }
    }
}
