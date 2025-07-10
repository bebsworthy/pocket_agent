package com.pocketagent.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents an SSH identity with encrypted private key stored in the app.
 * 
 * SSH identities are used for authentication to multiple servers and provide
 * secure access to remote development environments. The private key is encrypted
 * using the SshKeyImportManager and stored securely in the application data.
 * 
 * Entity Relationship: SSH Identity (1) → (N) Server Profile
 * 
 * @property id Unique identifier for the SSH identity
 * @property name User-friendly name for the identity (max 100 characters)
 * @property encryptedPrivateKey SSH private key encrypted by SshKeyImportManager
 * @property publicKeyFingerprint SHA256 fingerprint of the public key for verification
 * @property description Optional description of the identity usage
 * @property createdAt Timestamp when the identity was created
 * @property lastUsedAt Timestamp when the identity was last used for authentication
 */
@Serializable
data class SshIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val encryptedPrivateKey: String,
    val publicKeyFingerprint: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "SSH Identity name cannot be blank" }
        require(name.length <= 100) { "SSH Identity name too long (max 100 chars)" }
        require(encryptedPrivateKey.isNotBlank()) { "Encrypted private key cannot be blank" }
        require(publicKeyFingerprint.isNotBlank()) { "Public key fingerprint cannot be blank" }
        require(publicKeyFingerprint.matches(Regex("^[A-Fa-f0-9:]+$|^SHA256:[A-Za-z0-9+/=]+$"))) { 
            "Invalid fingerprint format. Must be hex:colon format or SHA256:base64 format" 
        }
        require(description?.length ?: 0 <= 500) { "Description too long (max 500 chars)" }
        require(createdAt > 0) { "Created timestamp must be positive" }
        require(lastUsedAt == null || lastUsedAt > 0) { "Last used timestamp must be positive if provided" }
    }
    
    /**
     * Check if this identity was used recently (within the last 30 days).
     */
    fun isRecentlyUsed(): Boolean {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        return lastUsedAt != null && lastUsedAt > thirtyDaysAgo
    }
    
    /**
     * Get the display name for the identity.
     */
    fun getDisplayName(): String = name
    
    /**
     * Get a short version of the fingerprint for display.
     */
    fun getShortFingerprint(): String {
        return when {
            publicKeyFingerprint.startsWith("SHA256:") -> {
                publicKeyFingerprint.substring(7, minOf(publicKeyFingerprint.length, 19))
            }
            publicKeyFingerprint.contains(":") -> {
                publicKeyFingerprint.split(":").take(4).joinToString(":")
            }
            else -> publicKeyFingerprint.take(12)
        }
    }
}

/**
 * Builder class for creating SshIdentity instances in tests.
 * 
 * This builder provides a fluent interface for constructing SshIdentity objects
 * with specific configurations for testing scenarios.
 */
class SshIdentityBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test SSH Identity"
    private var encryptedPrivateKey: String = "encrypted_test_key_data"
    private var publicKeyFingerprint: String = "SHA256:testfingerprint123"
    private var description: String? = null
    private var createdAt: Long = System.currentTimeMillis()
    private var lastUsedAt: Long? = null

    fun id(id: String) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun encryptedPrivateKey(key: String) = apply { this.encryptedPrivateKey = key }
    fun publicKeyFingerprint(fingerprint: String) = apply { this.publicKeyFingerprint = fingerprint }
    fun description(description: String?) = apply { this.description = description }
    fun createdAt(timestamp: Long) = apply { this.createdAt = timestamp }
    fun lastUsedAt(timestamp: Long?) = apply { this.lastUsedAt = timestamp }
    
    fun build(): SshIdentity = SshIdentity(
        id = id,
        name = name,
        encryptedPrivateKey = encryptedPrivateKey,
        publicKeyFingerprint = publicKeyFingerprint,
        description = description,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )
}

/**
 * Extension functions for SshIdentity operations.
 */

/**
 * Update the last used timestamp to current time.
 */
fun SshIdentity.markAsUsed(): SshIdentity = 
    copy(lastUsedAt = System.currentTimeMillis())

/**
 * Update the description.
 */
fun SshIdentity.withDescription(description: String?): SshIdentity = 
    copy(description = description)

/**
 * Check if the identity name matches the search query.
 */
fun SshIdentity.matchesSearch(query: String): Boolean = 
    name.contains(query, ignoreCase = true) || 
    description?.contains(query, ignoreCase = true) == true

/**
 * Get the age of the identity in days.
 */
fun SshIdentity.getAgeInDays(): Long {
    val now = System.currentTimeMillis()
    val dayInMillis = 24 * 60 * 60 * 1000
    return (now - createdAt) / dayInMillis
}

/**
 * Get the days since last use.
 */
fun SshIdentity.getDaysSinceLastUse(): Long? {
    if (lastUsedAt == null) return null
    val now = System.currentTimeMillis()
    val dayInMillis = 24 * 60 * 60 * 1000
    return (now - lastUsedAt) / dayInMillis
}

/**
 * Create a copy for export (without sensitive data).
 */
fun SshIdentity.toExportModel(): SshIdentityExport = SshIdentityExport(
    id = id,
    name = name,
    publicKeyFingerprint = publicKeyFingerprint,
    description = description,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt
)

/**
 * Export model for SSH Identity (without private key).
 */
@Serializable
data class SshIdentityExport(
    val id: String,
    val name: String,
    val publicKeyFingerprint: String,
    val description: String?,
    val createdAt: Long,
    val lastUsedAt: Long?
)

/**
 * Validation utilities for SSH Identity.
 */
object SshIdentityValidator {
    private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
    private val FINGERPRINT_HEX_REGEX = Regex("^[A-Fa-f0-9:]+$")
    private val FINGERPRINT_SHA256_REGEX = Regex("^SHA256:[A-Za-z0-9+/=]+$")
    
    /**
     * Validate SSH identity name.
     */
    fun validateName(name: String): Result<Unit> {
        return when {
            name.isBlank() -> Result.failure(IllegalArgumentException("Name cannot be blank"))
            name.length > 100 -> Result.failure(IllegalArgumentException("Name too long (max 100 chars)"))
            !VALID_NAME_REGEX.matches(name) -> Result.failure(IllegalArgumentException("Name contains invalid characters"))
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate public key fingerprint format.
     */
    fun validateFingerprint(fingerprint: String): Result<Unit> {
        return when {
            fingerprint.isBlank() -> Result.failure(IllegalArgumentException("Fingerprint cannot be blank"))
            !FINGERPRINT_HEX_REGEX.matches(fingerprint) && !FINGERPRINT_SHA256_REGEX.matches(fingerprint) -> 
                Result.failure(IllegalArgumentException("Invalid fingerprint format"))
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate description length.
     */
    fun validateDescription(description: String?): Result<Unit> {
        return when {
            description != null && description.length > 500 -> 
                Result.failure(IllegalArgumentException("Description too long (max 500 chars)"))
            else -> Result.success(Unit)
        }
    }
}

/**
 * Common SSH identity factory methods.
 */
object SshIdentityFactory {
    /**
     * Create a sample SSH identity for testing.
     */
    fun createSample(name: String = "Sample SSH Key"): SshIdentity = SshIdentityBuilder()
        .name(name)
        .encryptedPrivateKey("sample_encrypted_key_data")
        .publicKeyFingerprint("SHA256:samplefingerprint123")
        .description("Sample SSH identity for testing")
        .build()
    
    /**
     * Create multiple sample SSH identities.
     */
    fun createSamples(count: Int): List<SshIdentity> = (1..count).map { index ->
        createSample("Sample SSH Key $index")
    }
}