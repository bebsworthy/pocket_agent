package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.validation.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for Server Profile entities.
 * 
 * Provides field-level, entity-level, and business rule validation for server profiles.
 * Supports both synchronous and asynchronous validation scenarios.
 */
@Singleton
class ServerProfileValidator @Inject constructor() {
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_HOSTNAME_LENGTH = 253
        private const val MAX_USERNAME_LENGTH = 32
        private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
        private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9.-]+$")
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_.-]+$")
        private val IP_ADDRESS_REGEX = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
    }
    
    /**
     * Validate a complete server profile entity.
     */
    fun validate(profile: ServerProfile): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Field-level validations
        builder.addResult(validateId(profile.id))
        builder.addResult(validateName(profile.name))
        builder.addResult(validateHostname(profile.hostname))
        builder.addResult(validatePort(profile.port))
        builder.addResult(validateUsername(profile.username))
        builder.addResult(validateSshIdentityId(profile.sshIdentityId))
        builder.addResult(validateWrapperPort(profile.wrapperPort))
        builder.addResult(validateLastConnectedAt(profile.lastConnectedAt))
        builder.addResult(validateStatus(profile.status))
        builder.addResult(validateCreatedAt(profile.createdAt))
        
        // Entity-level validations
        builder.addResult(validateBusinessRules(profile))
        
        return builder.build()
    }
    
    /**
     * Validate server profile ID.
     */
    fun validateId(id: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("id"))
            .addRule(
                { it.isNotEmpty() },
                "Server profile ID cannot be empty",
                "id"
            )
            .build()
            .validate(id)
    }
    
    /**
     * Validate server profile name.
     */
    fun validateName(name: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("name", "Server profile name cannot be blank"))
            .addRule(CommonValidationRules.stringLength("name", max = MAX_NAME_LENGTH))
            .addRule(
                CommonValidationRules.regexPattern(
                    "name",
                    VALID_NAME_REGEX,
                    "Server profile name contains invalid characters. Only letters, numbers, spaces, hyphens, underscores, and brackets are allowed"
                )
            )
            .addRule(
                { !it.startsWith(" ") && !it.endsWith(" ") },
                "Server profile name cannot start or end with spaces",
                "name"
            )
            .build()
            .validate(name)
    }
    
    /**
     * Validate hostname.
     */
    fun validateHostname(hostname: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("hostname", "Hostname cannot be blank"))
            .addRule(CommonValidationRules.stringLength("hostname", max = MAX_HOSTNAME_LENGTH))
            .addRule(
                { hostname ->
                    HOSTNAME_REGEX.matches(hostname) || IP_ADDRESS_REGEX.matches(hostname)
                },
                "Hostname must be a valid domain name or IP address. Only letters, numbers, dots, and hyphens are allowed",
                "hostname"
            )
            .addRule(
                { !it.startsWith(".") && !it.endsWith(".") },
                "Hostname cannot start or end with a dot",
                "hostname"
            )
            .addRule(
                { !it.contains("..") },
                "Hostname cannot contain consecutive dots",
                "hostname"
            )
            .addRule(
                { hostname ->
                    // If it's an IP address, validate it properly
                    if (IP_ADDRESS_REGEX.matches(hostname)) {
                        val parts = hostname.split(".")
                        parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
                    } else {
                        true
                    }
                },
                "Invalid IP address format",
                "hostname"
            )
            .build()
            .validate(hostname)
    }
    
    /**
     * Validate SSH port.
     */
    fun validatePort(port: Int): ValidationResult {
        return ValidationRuleBuilder<Int>()
            .addRule(CommonValidationRules.port("port"))
            .addRule(
                { it != 80 && it != 443 },
                "Port $port is typically used for HTTP/HTTPS, not SSH. Consider using port 22 for SSH",
                "port"
            )
            .build()
            .validate(port)
    }
    
    /**
     * Validate username.
     */
    fun validateUsername(username: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("username", "Username cannot be blank"))
            .addRule(CommonValidationRules.stringLength("username", max = MAX_USERNAME_LENGTH))
            .addRule(
                CommonValidationRules.regexPattern(
                    "username",
                    USERNAME_REGEX,
                    "Username contains invalid characters. Only letters, numbers, underscores, dots, and hyphens are allowed"
                )
            )
            .addRule(
                { !it.startsWith("-") && !it.endsWith("-") },
                "Username cannot start or end with a hyphen",
                "username"
            )
            .addRule(
                { !it.startsWith(".") },
                "Username cannot start with a dot",
                "username"
            )
            .addRule(
                { username ->
                    // Check for reserved usernames
                    val reserved = listOf("root", "admin", "administrator", "system", "daemon", "bin", "sys", "sync", "nobody")
                    !reserved.contains(username.lowercase())
                },
                "Username '$username' is a reserved system username. Consider using a different username",
                "username"
            )
            .build()
            .validate(username)
    }
    
    /**
     * Validate SSH identity ID.
     */
    fun validateSshIdentityId(sshIdentityId: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("sshIdentityId", "SSH identity ID cannot be blank"))
            .addRule(
                { it.isNotEmpty() },
                "SSH identity ID must be specified",
                "sshIdentityId"
            )
            .build()
            .validate(sshIdentityId)
    }
    
    /**
     * Validate wrapper port.
     */
    fun validateWrapperPort(wrapperPort: Int): ValidationResult {
        return ValidationRuleBuilder<Int>()
            .addRule(CommonValidationRules.port("wrapperPort"))
            .addRule(
                { it >= 1024 },
                "Wrapper port should be >= 1024 to avoid conflicts with system services",
                "wrapperPort"
            )
            .build()
            .validate(wrapperPort)
    }
    
    /**
     * Validate optional last connected timestamp.
     */
    fun validateLastConnectedAt(lastConnectedAt: Long?): ValidationResult {
        return if (lastConnectedAt == null) {
            ValidationResult.Success
        } else {
            ValidationRuleBuilder<Long>()
                .addRule(CommonValidationRules.positiveTimestamp("lastConnectedAt"))
                .addRule(
                    { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                    "Last connected timestamp cannot be in the future",
                    "lastConnectedAt"
                )
                .build()
                .validate(lastConnectedAt)
        }
    }
    
    /**
     * Validate connection status.
     */
    fun validateStatus(status: ConnectionStatus): ValidationResult {
        // All enum values are valid, but we can add business logic
        return ValidationResult.Success
    }
    
    /**
     * Validate created timestamp.
     */
    fun validateCreatedAt(createdAt: Long): ValidationResult {
        return ValidationRuleBuilder<Long>()
            .addRule(CommonValidationRules.positiveTimestamp("createdAt"))
            .addRule(
                { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                "Created timestamp cannot be in the future",
                "createdAt"
            )
            .build()
            .validate(createdAt)
    }
    
    /**
     * Validate business rules for server profile.
     */
    fun validateBusinessRules(profile: ServerProfile): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Validate timestamp relationship
        if (profile.lastConnectedAt != null && profile.lastConnectedAt < profile.createdAt) {
            builder.addBusinessRuleError(
                "Last connected timestamp cannot be before creation timestamp",
                "lastConnectedAt",
                "INVALID_TIMESTAMP_ORDER"
            )
        }
        
        // Validate port conflicts
        if (profile.port == profile.wrapperPort) {
            builder.addBusinessRuleError(
                "SSH port and wrapper port cannot be the same",
                "wrapperPort",
                "PORT_CONFLICT"
            )
        }
        
        // Validate connection status consistency
        when (profile.status) {
            ConnectionStatus.NEVER_CONNECTED -> {
                if (profile.lastConnectedAt != null) {
                    builder.addBusinessRuleError(
                        "Status is NEVER_CONNECTED but lastConnectedAt is set",
                        "status",
                        "STATUS_TIMESTAMP_INCONSISTENCY"
                    )
                }
            }
            ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                if (profile.lastConnectedAt == null) {
                    builder.addBusinessRuleError(
                        "Status indicates connection history but lastConnectedAt is not set",
                        "lastConnectedAt",
                        "STATUS_TIMESTAMP_INCONSISTENCY"
                    )
                }
            }
            ConnectionStatus.CONNECTING -> {
                // Connecting status is transitional, no timestamp requirements
            }
        }
        
        // Validate hostname doesn't look like a username
        if (profile.hostname.length <= 20 && USERNAME_REGEX.matches(profile.hostname) && !profile.hostname.contains(".")) {
            builder.addBusinessRuleError(
                "Hostname '$${profile.hostname}' looks like a username. Did you mean to put this in the username field?",
                "hostname",
                "HOSTNAME_LOOKS_LIKE_USERNAME"
            )
        }
        
        // Validate name doesn't contain hostname (potential duplicate info)
        if (profile.name.contains(profile.hostname, ignoreCase = true)) {
            builder.addBusinessRuleError(
                "Server profile name should not contain the hostname. Consider using a more descriptive name",
                "name",
                "NAME_CONTAINS_HOSTNAME"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Validate for creation (additional checks for new entities).
     */
    fun validateForCreation(profile: ServerProfile): ValidationResult {
        val baseValidation = validate(profile)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional creation-specific validations
        val now = System.currentTimeMillis()
        
        // Created timestamp should be recent (within last hour)
        if (Math.abs(now - profile.createdAt) > 3600000) {
            builder.addBusinessRuleError(
                "Created timestamp should be recent for new server profiles",
                "createdAt",
                "CREATION_TIMESTAMP_NOT_RECENT"
            )
        }
        
        // New profile should not have lastConnectedAt set
        if (profile.lastConnectedAt != null) {
            builder.addBusinessRuleError(
                "New server profile should not have last connected timestamp set",
                "lastConnectedAt",
                "NEW_PROFILE_ALREADY_CONNECTED"
            )
        }
        
        // New profile should have NEVER_CONNECTED status
        if (profile.status != ConnectionStatus.NEVER_CONNECTED) {
            builder.addBusinessRuleError(
                "New server profile should have NEVER_CONNECTED status",
                "status",
                "NEW_PROFILE_INVALID_STATUS"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Validate for update (additional checks for existing entities).
     */
    fun validateForUpdate(original: ServerProfile, updated: ServerProfile): ValidationResult {
        val baseValidation = validate(updated)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional update-specific validations
        
        // ID should not change
        if (original.id != updated.id) {
            builder.addBusinessRuleError(
                "Server profile ID cannot be changed during update",
                "id",
                "ID_CHANGE_NOT_ALLOWED"
            )
        }
        
        // Created timestamp should not change
        if (original.createdAt != updated.createdAt) {
            builder.addBusinessRuleError(
                "Created timestamp cannot be changed during update",
                "createdAt",
                "CREATION_TIMESTAMP_CHANGE_NOT_ALLOWED"
            )
        }
        
        // Last connected timestamp should not go backwards
        if (original.lastConnectedAt != null && updated.lastConnectedAt != null && updated.lastConnectedAt < original.lastConnectedAt) {
            builder.addBusinessRuleError(
                "Last connected timestamp cannot go backwards",
                "lastConnectedAt",
                "LAST_CONNECTED_TIMESTAMP_BACKWARDS"
            )
        }
        
        // Status transitions should be valid
        builder.addResult(validateStatusTransition(original.status, updated.status))
        
        return builder.build()
    }
    
    /**
     * Validate connection status transitions.
     */
    fun validateStatusTransition(fromStatus: ConnectionStatus, toStatus: ConnectionStatus): ValidationResult {
        val validTransitions = mapOf(
            ConnectionStatus.NEVER_CONNECTED to setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.NEVER_CONNECTED
            ),
            ConnectionStatus.CONNECTING to setOf(
                ConnectionStatus.CONNECTED,
                ConnectionStatus.ERROR,
                ConnectionStatus.DISCONNECTED,
                ConnectionStatus.CONNECTING
            ),
            ConnectionStatus.CONNECTED to setOf(
                ConnectionStatus.DISCONNECTED,
                ConnectionStatus.ERROR,
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTED
            ),
            ConnectionStatus.DISCONNECTED to setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTED,
                ConnectionStatus.ERROR,
                ConnectionStatus.DISCONNECTED
            ),
            ConnectionStatus.ERROR to setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTED,
                ConnectionStatus.DISCONNECTED,
                ConnectionStatus.ERROR
            )
        )
        
        val allowedTransitions = validTransitions[fromStatus] ?: emptySet()
        
        return if (toStatus in allowedTransitions) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "Invalid status transition from $fromStatus to $toStatus",
                    "status",
                    "INVALID_STATUS_TRANSITION"
                )
            )
        }
    }
    
    /**
     * Validate server profile name uniqueness against a list of existing names.
     */
    fun validateNameUniqueness(name: String, existingNames: List<String>, excludeId: String? = null): ValidationResult {
        val normalizedName = name.trim().lowercase()
        val conflictingNames = existingNames.filter { existingName ->
            existingName.trim().lowercase() == normalizedName
        }
        
        return if (conflictingNames.isNotEmpty()) {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "Server profile name '$name' already exists",
                    "name",
                    "DUPLICATE_NAME"
                )
            )
        } else {
            ValidationResult.Success
        }
    }
    
    /**
     * Validate hostname and port combination uniqueness.
     */
    fun validateHostnamePortUniqueness(
        hostname: String,
        port: Int,
        existingProfiles: List<Pair<String, Int>>,
        excludeId: String? = null
    ): ValidationResult {
        val conflictingProfiles = existingProfiles.filter { (existingHostname, existingPort) ->
            existingHostname.equals(hostname, ignoreCase = true) && existingPort == port
        }
        
        return if (conflictingProfiles.isNotEmpty()) {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "Server profile with hostname '$hostname' and port $port already exists",
                    "hostname",
                    "DUPLICATE_HOSTNAME_PORT"
                )
            )
        } else {
            ValidationResult.Success
        }
    }
    
    /**
     * Quick validation for UI field updates (less comprehensive).
     */
    fun validateField(field: String, value: Any?): ValidationResult {
        return when (field) {
            "name" -> validateName(value as? String ?: "")
            "hostname" -> validateHostname(value as? String ?: "")
            "port" -> validatePort(value as? Int ?: 0)
            "username" -> validateUsername(value as? String ?: "")
            "wrapperPort" -> validateWrapperPort(value as? Int ?: 0)
            "sshIdentityId" -> validateSshIdentityId(value as? String ?: "")
            else -> ValidationResult.Success
        }
    }
}
