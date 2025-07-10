package com.pocketagent.domain.models.entities

import com.pocketagent.domain.models.error.ValidationException
import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents an SSH identity with encrypted private key stored in the app.
 * 
 * SSH identities are used for authentication to multiple servers and provide
 * secure access to remote development environments.
 * 
 * @property id Unique identifier for the SSH identity
 * @property name User-friendly name for the identity
 * @property encryptedPrivateKey Encrypted SSH private key data
 * @property publicKeyFingerprint SSH public key fingerprint for verification
 * @property description Optional description for the identity
 * @property createdAt Timestamp when the identity was created
 * @property lastUsedAt Timestamp when the identity was last used
 * @property isActive Whether the identity is active
 * @property keyType Type of SSH key (RSA, ECDSA, Ed25519)
 * @property keySize Size of the key in bits
 * @property metadata Additional metadata for the identity
 */
data class SshIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val encryptedPrivateKey: String,
    val publicKeyFingerprint: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val isActive: Boolean = true,
    val keyType: SshKeyType = SshKeyType.RSA,
    val keySize: Int = 2048,
    val metadata: SshIdentityMetadata = SshIdentityMetadata()
) {
    init {
        validateName(name)
        validateFingerprint(publicKeyFingerprint)
        validateKeySize(keySize, keyType)
        validateEncryptedPrivateKey(encryptedPrivateKey)
    }
    
    /**
     * Updates the last used timestamp.
     */
    fun markAsUsed(): SshIdentity = copy(lastUsedAt = System.currentTimeMillis())
    
    /**
     * Deactivates the SSH identity.
     */
    fun deactivate(): SshIdentity = copy(isActive = false)
    
    /**
     * Reactivates the SSH identity.
     */
    fun reactivate(): SshIdentity = copy(isActive = true)
    
    /**
     * Updates the description.
     */
    fun updateDescription(newDescription: String?): SshIdentity = copy(description = newDescription)
    
    /**
     * Checks if the identity is expired based on metadata.
     */
    fun isExpired(): Boolean = metadata.expiresAt?.let { it < System.currentTimeMillis() } ?: false
    
    /**
     * Gets the display name for the identity.
     */
    fun getDisplayName(): String = name.ifBlank { "SSH Identity $id" }
    
    /**
     * Gets the short fingerprint for display.
     */
    fun getShortFingerprint(): String = publicKeyFingerprint.takeLast(16)
    
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MIN_NAME_LENGTH = 1
        
        private fun validateName(name: String) {
            if (name.isBlank()) {
                throw ValidationException("name", name, "SSH Identity name cannot be blank")
            }
            if (name.length > MAX_NAME_LENGTH) {
                throw ValidationException("name", name, "SSH Identity name too long (max $MAX_NAME_LENGTH chars)")
            }
            if (name.length < MIN_NAME_LENGTH) {
                throw ValidationException("name", name, "SSH Identity name too short (min $MIN_NAME_LENGTH chars)")
            }
        }
        
        private fun validateFingerprint(fingerprint: String) {
            val fingerprintRegex = Regex("^(SHA256:|MD5:)?[A-Fa-f0-9:]+$")
            if (!fingerprint.matches(fingerprintRegex)) {
                throw ValidationException("publicKeyFingerprint", fingerprint, "Invalid fingerprint format")
            }
        }
        
        private fun validateKeySize(size: Int, type: SshKeyType) {
            val validSizes = when (type) {
                SshKeyType.RSA -> listOf(2048, 3072, 4096)
                SshKeyType.ECDSA -> listOf(256, 384, 521)
                SshKeyType.Ed25519 -> listOf(256)
                SshKeyType.DSA -> listOf(1024, 2048, 3072)
            }
            if (size !in validSizes) {
                throw ValidationException("keySize", size, "Invalid key size $size for type $type. Valid sizes: $validSizes")
            }
        }
        
        private fun validateEncryptedPrivateKey(encryptedKey: String) {
            if (encryptedKey.isBlank()) {
                throw ValidationException("encryptedPrivateKey", encryptedKey, "Encrypted private key cannot be blank")
            }
            // Additional validation could be added here for encrypted key format
        }
    }
}

/**
 * Enum representing different SSH key types.
 */
enum class SshKeyType {
    RSA,
    ECDSA,
    Ed25519,
    DSA
}

/**
 * Metadata associated with an SSH identity.
 * 
 * @property tags User-defined tags for organizing identities
 * @property source Source of the key (imported, generated, etc.)
 * @property expiresAt Optional expiration timestamp
 * @property usageCount Number of times the key has been used
 * @property lastServerUsed Last server where the key was used
 * @property isBackedUp Whether the key is backed up
 */
data class SshIdentityMetadata(
    val tags: List<String> = emptyList(),
    val source: SshKeySource = SshKeySource.IMPORTED,
    val expiresAt: Long? = null,
    val usageCount: Int = 0,
    val lastServerUsed: String? = null,
    val isBackedUp: Boolean = false
)

/**
 * Enum representing the source of an SSH key.
 */
enum class SshKeySource {
    IMPORTED,
    GENERATED,
    RESTORED
}

/**
 * Builder class for creating SSH identities with validation.
 */
class SshIdentityBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = ""
    private var encryptedPrivateKey: String = ""
    private var publicKeyFingerprint: String = ""
    private var description: String? = null
    private var createdAt: Long = System.currentTimeMillis()
    private var lastUsedAt: Long? = null
    private var isActive: Boolean = true
    private var keyType: SshKeyType = SshKeyType.RSA
    private var keySize: Int = 2048
    private var metadata: SshIdentityMetadata = SshIdentityMetadata()
    
    fun id(id: String) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun encryptedPrivateKey(key: String) = apply { this.encryptedPrivateKey = key }
    fun publicKeyFingerprint(fingerprint: String) = apply { this.publicKeyFingerprint = fingerprint }
    fun description(description: String?) = apply { this.description = description }
    fun createdAt(createdAt: Long) = apply { this.createdAt = createdAt }
    fun lastUsedAt(lastUsedAt: Long?) = apply { this.lastUsedAt = lastUsedAt }
    fun isActive(isActive: Boolean) = apply { this.isActive = isActive }
    fun keyType(keyType: SshKeyType) = apply { this.keyType = keyType }
    fun keySize(keySize: Int) = apply { this.keySize = keySize }
    fun metadata(metadata: SshIdentityMetadata) = apply { this.metadata = metadata }
    
    fun build(): SshIdentity = SshIdentity(
        id = id,
        name = name,
        encryptedPrivateKey = encryptedPrivateKey,
        publicKeyFingerprint = publicKeyFingerprint,
        description = description,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        isActive = isActive,
        keyType = keyType,
        keySize = keySize,
        metadata = metadata
    )
}

/**
 * Extension functions for SshIdentity.
 */
fun SshIdentity.toBuilder(): SshIdentityBuilder = SshIdentityBuilder()
    .id(id)
    .name(name)
    .encryptedPrivateKey(encryptedPrivateKey)
    .publicKeyFingerprint(publicKeyFingerprint)
    .description(description)
    .createdAt(createdAt)
    .lastUsedAt(lastUsedAt)
    .isActive(isActive)
    .keyType(keyType)
    .keySize(keySize)
    .metadata(metadata)

/**
 * Creates a new SSH identity builder.
 */
fun sshIdentity(block: SshIdentityBuilder.() -> Unit): SshIdentity = 
    SshIdentityBuilder().apply(block).build()