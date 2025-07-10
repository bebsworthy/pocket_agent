package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.validation.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for SSH Identity entities.
 * 
 * Provides field-level, entity-level, and business rule validation for SSH identities.
 * Supports both synchronous and asynchronous validation scenarios.
 */
@Singleton
class SshIdentityValidator @Inject constructor() {
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_DESCRIPTION_LENGTH = 500
        private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
        private val FINGERPRINT_HEX_REGEX = Regex("^[A-Fa-f0-9:]+$")
        private val FINGERPRINT_SHA256_REGEX = Regex("^SHA256:[A-Za-z0-9+/=]+$")
    }
    
    /**
     * Validate a complete SSH identity entity.
     */
    fun validate(identity: SshIdentity): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Field-level validations
        builder.addResult(validateId(identity.id))
        builder.addResult(validateName(identity.name))
        builder.addResult(validateEncryptedPrivateKey(identity.encryptedPrivateKey))
        builder.addResult(validatePublicKeyFingerprint(identity.publicKeyFingerprint))
        builder.addResult(validateDescription(identity.description))
        builder.addResult(validateCreatedAt(identity.createdAt))
        builder.addResult(validateLastUsedAt(identity.lastUsedAt))
        
        // Entity-level validations
        builder.addResult(validateBusinessRules(identity))
        
        return builder.build()
    }
    
    /**
     * Validate SSH identity ID.
     */
    fun validateId(id: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("id"))
            .addRule(
                { it.isNotEmpty() },
                "SSH identity ID cannot be empty",
                "id"
            )
            .build()
            .validate(id)
    }
    
    /**
     * Validate SSH identity name.
     */
    fun validateName(name: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("name", "SSH identity name cannot be blank"))
            .addRule(CommonValidationRules.stringLength("name", max = MAX_NAME_LENGTH))
            .addRule(
                CommonValidationRules.regexPattern(
                    "name",
                    VALID_NAME_REGEX,
                    "SSH identity name contains invalid characters. Only letters, numbers, spaces, hyphens, underscores, and brackets are allowed"
                )
            )
            .addRule(
                { !it.startsWith(" ") && !it.endsWith(" ") },
                "SSH identity name cannot start or end with spaces",
                "name"
            )
            .build()
            .validate(name)
    }
    
    /**
     * Validate encrypted private key.
     */
    fun validateEncryptedPrivateKey(encryptedKey: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("encryptedPrivateKey", "Encrypted private key cannot be blank"))
            .addRule(
                { it.length >= 10 },
                "Encrypted private key appears to be too short",
                "encryptedPrivateKey"
            )
            .addRule(
                { it.length <= 10000 },
                "Encrypted private key appears to be too long (max 10000 characters)",
                "encryptedPrivateKey"
            )
            .build()
            .validate(encryptedKey)
    }
    
    /**
     * Validate public key fingerprint.
     */
    fun validatePublicKeyFingerprint(fingerprint: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("publicKeyFingerprint", "Public key fingerprint cannot be blank"))
            .addRule(
                { FINGERPRINT_HEX_REGEX.matches(it) || FINGERPRINT_SHA256_REGEX.matches(it) },
                "Public key fingerprint must be in hex:colon format (e.g., 'ab:cd:ef:12') or SHA256:base64 format (e.g., 'SHA256:abcd1234')",
                "publicKeyFingerprint"
            )
            .addRule(
                { fingerprint ->
                    when {
                        fingerprint.startsWith("SHA256:") -> fingerprint.length >= 50 // SHA256: + base64
                        fingerprint.contains(":") -> fingerprint.split(":").size >= 8 // At least 8 hex segments
                        else -> false
                    }
                },
                "Public key fingerprint appears to be incomplete",
                "publicKeyFingerprint"
            )
            .build()
            .validate(fingerprint)
    }
    
    /**
     * Validate optional description.
     */
    fun validateDescription(description: String?): ValidationResult {
        return if (description == null) {
            ValidationResult.Success
        } else {
            ValidationRuleBuilder<String>()
                .addRule(CommonValidationRules.stringLength("description", max = MAX_DESCRIPTION_LENGTH))
                .addRule(
                    { !it.trim().isEmpty() },
                    "Description cannot be only whitespace",
                    "description"
                )
                .build()
                .validate(description)
        }
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
     * Validate optional last used timestamp.
     */
    fun validateLastUsedAt(lastUsedAt: Long?): ValidationResult {
        return if (lastUsedAt == null) {
            ValidationResult.Success
        } else {
            ValidationRuleBuilder<Long>()
                .addRule(CommonValidationRules.positiveTimestamp("lastUsedAt"))
                .addRule(
                    { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                    "Last used timestamp cannot be in the future",
                    "lastUsedAt"
                )
                .build()
                .validate(lastUsedAt)
        }
    }
    
    /**
     * Validate business rules for SSH identity.
     */
    fun validateBusinessRules(identity: SshIdentity): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Validate timestamp relationship
        if (identity.lastUsedAt != null && identity.lastUsedAt < identity.createdAt) {
            builder.addBusinessRuleError(
                "Last used timestamp cannot be before creation timestamp",
                "lastUsedAt",
                "INVALID_TIMESTAMP_ORDER"
            )
        }
        
        // Validate fingerprint format consistency
        if (identity.publicKeyFingerprint.startsWith("SHA256:") && identity.publicKeyFingerprint.length < 50) {
            builder.addBusinessRuleError(
                "SHA256 fingerprint appears to be truncated",
                "publicKeyFingerprint",
                "TRUNCATED_FINGERPRINT"
            )
        }
        
        // Validate name doesn't look like a fingerprint
        if (FINGERPRINT_HEX_REGEX.matches(identity.name) || identity.name.startsWith("SHA256:")) {
            builder.addBusinessRuleError(
                "SSH identity name should not look like a fingerprint",
                "name",
                "NAME_LOOKS_LIKE_FINGERPRINT"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Validate for creation (additional checks for new entities).
     */
    fun validateForCreation(identity: SshIdentity): ValidationResult {
        val baseValidation = validate(identity)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional creation-specific validations
        val now = System.currentTimeMillis()
        
        // Created timestamp should be recent (within last hour)
        if (Math.abs(now - identity.createdAt) > 3600000) {
            builder.addBusinessRuleError(
                "Created timestamp should be recent for new SSH identities",
                "createdAt",
                "CREATION_TIMESTAMP_NOT_RECENT"
            )
        }
        
        // New identity should not have lastUsedAt set
        if (identity.lastUsedAt != null) {
            builder.addBusinessRuleError(
                "New SSH identity should not have last used timestamp set",
                "lastUsedAt",
                "NEW_IDENTITY_ALREADY_USED"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Validate for update (additional checks for existing entities).
     */
    fun validateForUpdate(original: SshIdentity, updated: SshIdentity): ValidationResult {
        val baseValidation = validate(updated)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional update-specific validations
        
        // ID should not change
        if (original.id != updated.id) {
            builder.addBusinessRuleError(
                "SSH identity ID cannot be changed during update",
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
        
        // Last used timestamp should not go backwards
        if (original.lastUsedAt != null && updated.lastUsedAt != null && updated.lastUsedAt < original.lastUsedAt) {
            builder.addBusinessRuleError(
                "Last used timestamp cannot go backwards",
                "lastUsedAt",
                "LAST_USED_TIMESTAMP_BACKWARDS"
            )
        }
        
        // Encrypted private key changes should be validated more strictly
        if (original.encryptedPrivateKey != updated.encryptedPrivateKey) {
            // Fingerprint should also change if private key changes
            if (original.publicKeyFingerprint == updated.publicKeyFingerprint) {
                builder.addBusinessRuleError(
                    "Public key fingerprint should change when private key changes",
                    "publicKeyFingerprint",
                    "FINGERPRINT_MISMATCH_AFTER_KEY_CHANGE"
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate SSH identity name uniqueness against a list of existing names.
     */
    fun validateNameUniqueness(name: String, existingNames: List<String>, excludeId: String? = null): ValidationResult {
        val normalizedName = name.trim().lowercase()
        val conflictingNames = existingNames.filter { existingName ->
            existingName.trim().lowercase() == normalizedName
        }
        
        return if (conflictingNames.isNotEmpty()) {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "SSH identity name '$name' already exists",
                    "name",
                    "DUPLICATE_NAME"
                )
            )
        } else {
            ValidationResult.Success
        }
    }
    
    /**
     * Validate SSH identity fingerprint uniqueness against a list of existing fingerprints.
     */
    fun validateFingerprintUniqueness(fingerprint: String, existingFingerprints: List<String>, excludeId: String? = null): ValidationResult {
        val conflictingFingerprints = existingFingerprints.filter { existingFingerprint ->
            existingFingerprint == fingerprint
        }
        
        return if (conflictingFingerprints.isNotEmpty()) {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "SSH key with fingerprint '$fingerprint' already exists",
                    "publicKeyFingerprint",
                    "DUPLICATE_FINGERPRINT"
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
            "description" -> validateDescription(value as? String)
            "publicKeyFingerprint" -> validatePublicKeyFingerprint(value as? String ?: "")
            "encryptedPrivateKey" -> validateEncryptedPrivateKey(value as? String ?: "")
            else -> ValidationResult.Success
        }
    }
}
