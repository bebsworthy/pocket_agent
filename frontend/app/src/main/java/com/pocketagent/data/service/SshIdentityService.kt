package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.service.serviceFailure
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.SshIdentityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive service for SSH Identity CRUD operations and management.
 * 
 * This service provides a high-level interface for managing SSH identities with
 * features including:
 * - Complete CRUD operations with validation
 * - SSH key format parsing and conversion
 * - Fingerprint generation and validation
 * - Secure key storage and encryption
 * - Import/export capabilities
 * - Search and filtering
 * - Usage tracking and statistics
 * - Relationship management with server profiles
 * 
 * The service integrates with the existing SecureDataRepository and validation
 * framework to provide comprehensive SSH identity management.
 */
@Singleton
class SshIdentityService @Inject constructor(
    private val repository: SecureDataRepository,
    private val validator: SshIdentityValidator,
    private val sshKeyParser: SshKeyParser,
    private val sshKeyEncryption: SshKeyEncryption
) {
    
    companion object {
        private const val TAG = "SshIdentityService"
        private const val DEFAULT_SEARCH_LIMIT = 50
    }
    
    // CRUD Operations
    
    /**
     * Creates a new SSH identity with comprehensive validation.
     * 
     * @param name Display name for the identity
     * @param privateKeyData Raw private key data (PEM, OpenSSH, etc.)
     * @param keyFormat Format of the private key data
     * @param passphrase Optional passphrase for encrypted keys
     * @param description Optional description
     * @return Result with created SSH identity or error
     */
    suspend fun createSshIdentity(
        name: String,
        privateKeyData: String,
        keyFormat: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
        passphrase: String? = null,
        description: String? = null
    ): ServiceResult<SshIdentity> {
        Log.d(TAG, "Creating SSH identity: $name")
        
        return try {
            withContext(Dispatchers.Default) {
                // Parse and validate the private key
                val keyPair = sshKeyParser.parsePrivateKey(privateKeyData, keyFormat, passphrase)
                    ?: return@withContext serviceFailure("Failed to parse private key")
                
                // Generate public key fingerprint
                val fingerprint = generateFingerprint(keyPair.public)
                
                // Check for duplicate fingerprint
                val existingIdentities = repository.getAllSshIdentities()
                if (existingIdentities.any { it.publicKeyFingerprint == fingerprint }) {
                    return@withContext serviceFailure("SSH key with this fingerprint already exists")
                }
                
                // Encrypt the private key for storage
                val encryptedPrivateKey = sshKeyEncryption.encryptPrivateKey(keyPair.private)
                
                // Create the SSH identity
                val identity = SshIdentity(
                    name = name,
                    encryptedPrivateKey = encryptedPrivateKey,
                    publicKeyFingerprint = fingerprint,
                    description = description
                )
                
                // Validate the identity
                val validationResult = validator.validateForCreation(identity)
                if (!validationResult.isSuccess()) {
                    return@withContext serviceFailure("Validation failed: ${validationResult.getErrorSummary()}")
                }
                
                // Check name uniqueness
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingIdentities.map { it.name }
                )
                if (!nameValidation.isSuccess()) {
                    return@withContext serviceFailure("Name already exists")
                }
                
                // Save to repository
                repository.addSshIdentity(identity)
                
                Log.d(TAG, "SSH identity created successfully: $name")
                ServiceResult.success(identity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SSH identity", e)
            serviceFailure("Failed to create SSH identity: ${e.message}")
        }
    }
    
    /**
     * Retrieves an SSH identity by ID.
     */
    suspend fun getSshIdentity(id: String): ServiceResult<SshIdentity> {
        Log.d(TAG, "Getting SSH identity: $id")
        
        return try {
            val identity = repository.getSshIdentityById(id)
            if (identity != null) {
                ServiceResult.success(identity)
            } else {
                serviceFailure("SSH identity not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SSH identity", e)
            serviceFailure("Failed to retrieve SSH identity: ${e.message}")
        }
    }
    
    /**
     * Updates an existing SSH identity.
     */
    suspend fun updateSshIdentity(
        id: String,
        name: String? = null,
        description: String? = null
    ): ServiceResult<SshIdentity> {
        Log.d(TAG, "Updating SSH identity: $id")
        
        return try {
            val existing = repository.getSshIdentityById(id)
                ?: return serviceFailure("SSH identity not found")
            
            val updated = existing.copy(
                name = name ?: existing.name,
                description = description ?: existing.description
            )
            
            // Validate the update
            val validationResult = validator.validateForUpdate(existing, updated)
            if (!validationResult.isSuccess()) {
                return serviceFailure("Validation failed: ${validationResult.getErrorSummary()}")
            }
            
            // Check name uniqueness if name changed
            if (name != null && name != existing.name) {
                val existingIdentities = repository.getAllSshIdentities()
                val nameValidation = validator.validateNameUniqueness(
                    name, 
                    existingIdentities.map { it.name },
                    excludeId = id
                )
                if (!nameValidation.isSuccess()) {
                    return serviceFailure("Name already exists")
                }
            }
            
            repository.updateSshIdentity(updated)
            
            Log.d(TAG, "SSH identity updated successfully: $id")
            ServiceResult.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update SSH identity", e)
            serviceFailure("Failed to update SSH identity: ${e.message}")
        }
    }
    
    /**
     * Deletes an SSH identity with dependency checking.
     */
    suspend fun deleteSshIdentity(id: String): ServiceResult<Unit> {
        Log.d(TAG, "Deleting SSH identity: $id")
        
        return try {
            val identity = repository.getSshIdentityById(id)
                ?: return serviceFailure("SSH identity not found")
            
            // Check for dependencies
            val serverProfiles = repository.getServerProfilesForIdentity(id)
            if (serverProfiles.isNotEmpty()) {
                return serviceFailure(
                    "Cannot delete SSH identity. It is used by ${serverProfiles.size} server profile(s): ${
                        serverProfiles.take(3).joinToString(", ") { it.name }
                    }${if (serverProfiles.size > 3) "..." else ""}"
                )
            }
            
            repository.deleteSshIdentity(id)
            
            Log.d(TAG, "SSH identity deleted successfully: $id")
            ServiceResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SSH identity", e)
            serviceFailure("Failed to delete SSH identity: ${e.message}")
        }
    }
    
    /**
     * Lists all SSH identities with optional filtering and sorting.
     */
    suspend fun listSshIdentities(
        sortBy: SshIdentitySortBy = SshIdentitySortBy.NAME,
        ascending: Boolean = true,
        includeUnused: Boolean = true
    ): ServiceResult<List<SshIdentity>> {
        Log.d(TAG, "Listing SSH identities")
        
        return try {
            val identities = repository.getAllSshIdentities()
            
            // Filter unused if requested
            val filtered = if (!includeUnused) {
                val usedIdentityIds = repository.getAllServerProfiles()
                    .map { it.sshIdentityId }
                    .toSet()
                identities.filter { it.id in usedIdentityIds }
            } else {
                identities
            }
            
            // Sort the results
            val sorted = when (sortBy) {
                SshIdentitySortBy.NAME -> filtered.sortedBy { it.name }
                SshIdentitySortBy.CREATED_DATE -> filtered.sortedBy { it.createdAt }
                SshIdentitySortBy.LAST_USED -> filtered.sortedBy { it.lastUsedAt ?: 0 }
                SshIdentitySortBy.USAGE_COUNT -> {
                    // Get usage counts and sort by them
                    val usageCounts = getUsageStatistics(filtered.map { it.id })
                    filtered.sortedBy { identity ->
                        usageCounts[identity.id]?.serverProfileCount ?: 0
                    }
                }
            }.let { if (ascending) it else it.reversed() }
            
            ServiceResult.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list SSH identities", e)
            serviceFailure("Failed to list SSH identities: ${e.message}")
        }
    }
    
    // Search and Filtering
    
    /**
     * Searches SSH identities by name, description, or fingerprint.
     */
    suspend fun searchSshIdentities(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): ServiceResult<List<SshIdentity>> {
        Log.d(TAG, "Searching SSH identities: $query")
        
        return try {
            if (query.isBlank()) {
                return ServiceResult.success(emptyList())
            }
            
            val allIdentities = repository.getAllSshIdentities()
            val searchResults = allIdentities.filter { identity ->
                identity.name.contains(query, ignoreCase = true) ||
                identity.description?.contains(query, ignoreCase = true) == true ||
                identity.publicKeyFingerprint.contains(query, ignoreCase = true) ||
                identity.getShortFingerprint().contains(query, ignoreCase = true)
            }.take(limit)
            
            ServiceResult.success(searchResults)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search SSH identities", e)
            serviceFailure("Failed to search SSH identities: ${e.message}")
        }
    }
    
    /**
     * Filters SSH identities by various criteria.
     */
    suspend fun filterSshIdentities(
        criteria: SshIdentityFilterCriteria
    ): ServiceResult<List<SshIdentity>> {
        Log.d(TAG, "Filtering SSH identities")
        
        return try {
            val allIdentities = repository.getAllSshIdentities()
            var filtered = allIdentities
            
            // Apply date range filter
            criteria.createdAfter?.let { after ->
                filtered = filtered.filter { it.createdAt >= after }
            }
            criteria.createdBefore?.let { before ->
                filtered = filtered.filter { it.createdAt <= before }
            }
            
            // Apply usage filter
            if (criteria.recentlyUsedOnly) {
                filtered = filtered.filter { it.isRecentlyUsed() }
            }
            
            // Apply unused filter
            if (criteria.unusedOnly) {
                val usedIdentityIds = repository.getAllServerProfiles()
                    .map { it.sshIdentityId }
                    .toSet()
                filtered = filtered.filter { it.id !in usedIdentityIds }
            }
            
            // Apply name pattern filter
            criteria.namePattern?.let { pattern ->
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                filtered = filtered.filter { regex.containsMatchIn(it.name) }
            }
            
            ServiceResult.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to filter SSH identities", e)
            serviceFailure("Failed to filter SSH identities: ${e.message}")
        }
    }
    
    // Key Management
    
    /**
     * Imports an SSH key from various formats.
     */
    suspend fun importSshKey(
        name: String,
        keyData: String,
        format: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
        passphrase: String? = null,
        description: String? = null
    ): ServiceResult<SshIdentity> {
        Log.d(TAG, "Importing SSH key: $name")
        
        return createSshIdentity(name, keyData, format, passphrase, description)
    }
    
    /**
     * Exports an SSH key to various formats.
     */
    suspend fun exportSshKey(
        id: String,
        format: SshKeyFormat = SshKeyFormat.OPENSSH,
        includePrivateKey: Boolean = false
    ): ServiceResult<String> {
        Log.d(TAG, "Exporting SSH key: $id")
        
        return try {
            val identity = repository.getSshIdentityById(id)
                ?: return serviceFailure("SSH identity not found")
            
            if (includePrivateKey) {
                // Decrypt and export private key
                val privateKey = sshKeyEncryption.decryptPrivateKey(identity.encryptedPrivateKey)
                val exported = sshKeyParser.formatPrivateKey(privateKey, format)
                ServiceResult.success(exported)
            } else {
                // Export public key only
                val publicKey = sshKeyParser.extractPublicKey(identity.encryptedPrivateKey)
                    ?: return serviceFailure("Failed to extract public key")
                val exported = sshKeyParser.formatPublicKey(publicKey, format)
                ServiceResult.success(exported)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export SSH key", e)
            serviceFailure("Failed to export SSH key: ${e.message}")
        }
    }
    
    /**
     * Generates a new SSH key pair.
     */
    suspend fun generateSshKey(
        name: String,
        keyType: SshKeyType = SshKeyType.RSA,
        keySize: Int = 2048,
        description: String? = null
    ): ServiceResult<SshIdentity> {
        Log.d(TAG, "Generating SSH key: $name")
        
        return try {
            withContext(Dispatchers.Default) {
                val keyPair = sshKeyParser.generateKeyPair(keyType, keySize)
                val privateKeyData = sshKeyParser.formatPrivateKey(keyPair.private, SshKeyFormat.OPENSSH)
                
                createSshIdentity(name, privateKeyData, SshKeyFormat.OPENSSH, null, description)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate SSH key", e)
            serviceFailure("Failed to generate SSH key: ${e.message}")
        }
    }
    
    /**
     * Validates an SSH key format and integrity.
     */
    suspend fun validateSshKey(
        keyData: String,
        format: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
        passphrase: String? = null
    ): ServiceResult<SshKeyInfo> {
        Log.d(TAG, "Validating SSH key")
        
        return try {
            withContext(Dispatchers.Default) {
                val keyPair = sshKeyParser.parsePrivateKey(keyData, format, passphrase)
                    ?: return@withContext serviceFailure("Invalid SSH key format")
                
                val fingerprint = generateFingerprint(keyPair.public)
                val keyInfo = SshKeyInfo(
                    keyType = detectKeyType(keyPair.public),
                    keySize = getKeySize(keyPair.public),
                    fingerprint = fingerprint,
                    isEncrypted = sshKeyParser.isPrivateKeyEncrypted(keyData),
                    format = sshKeyParser.detectFormat(keyData)
                )
                
                ServiceResult.success(keyInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate SSH key", e)
            serviceFailure("Failed to validate SSH key: ${e.message}")
        }
    }
    
    // Usage Tracking
    
    /**
     * Marks an SSH identity as used (updates lastUsedAt timestamp).
     */
    suspend fun markAsUsed(id: String): ServiceResult<Unit> {
        Log.d(TAG, "Marking SSH identity as used: $id")
        
        return try {
            val identity = repository.getSshIdentityById(id)
                ?: return serviceFailure("SSH identity not found")
            
            val updated = identity.copy(lastUsedAt = System.currentTimeMillis())
            repository.updateSshIdentity(updated)
            
            ServiceResult.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark SSH identity as used", e)
            serviceFailure("Failed to update usage: ${e.message}")
        }
    }
    
    /**
     * Gets usage statistics for SSH identities.
     */
    suspend fun getUsageStatistics(identityIds: List<String>? = null): Map<String, SshIdentityUsageStats> {
        Log.d(TAG, "Getting usage statistics")
        
        return try {
            val serverProfiles = repository.getAllServerProfiles()
            val projects = repository.getAllProjects()
            
            val targetIds = identityIds ?: repository.getAllSshIdentities().map { it.id }
            
            targetIds.associateWith { identityId ->
                val relatedServers = serverProfiles.filter { it.sshIdentityId == identityId }
                val relatedProjects = projects.filter { project ->
                    relatedServers.any { it.id == project.serverProfileId }
                }
                
                SshIdentityUsageStats(
                    serverProfileCount = relatedServers.size,
                    projectCount = relatedProjects.size,
                    lastConnectionAt = relatedServers.mapNotNull { it.lastConnectedAt }.maxOrNull(),
                    recentConnections = relatedServers.count { server ->
                        server.lastConnectedAt != null && 
                        server.lastConnectedAt > System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 days
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage statistics", e)
            emptyMap()
        }
    }
    
    // Observable Flows
    
    /**
     * Observable flow of all SSH identities.
     */
    fun observeSshIdentities(): Flow<List<SshIdentity>> = repository.observeSshIdentities()
    
    /**
     * Observable flow of SSH identities with usage statistics.
     */
    fun observeSshIdentitiesWithUsage(): Flow<List<SshIdentityWithUsage>> {
        return combine(
            repository.observeSshIdentities(),
            repository.observeServerProfiles(),
            repository.observeProjects()
        ) { identities, serverProfiles, projects ->
            identities.map { identity ->
                val relatedServers = serverProfiles.filter { it.sshIdentityId == identity.id }
                val relatedProjects = projects.filter { project ->
                    relatedServers.any { it.id == project.serverProfileId }
                }
                
                SshIdentityWithUsage(
                    identity = identity,
                    usageStats = SshIdentityUsageStats(
                        serverProfileCount = relatedServers.size,
                        projectCount = relatedProjects.size,
                        lastConnectionAt = relatedServers.mapNotNull { it.lastConnectedAt }.maxOrNull(),
                        recentConnections = relatedServers.count { server ->
                            server.lastConnectedAt != null && 
                            server.lastConnectedAt > System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                        }
                    )
                )
            }
        }.flowOn(Dispatchers.Default)
    }
    
    // Private Helper Methods
    
    private fun generateFingerprint(publicKey: PublicKey): String {
        return try {
            val encoded = publicKey.encoded
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(encoded)
            "SHA256:${Base64.getEncoder().encodeToString(hash)}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate fingerprint", e)
            "INVALID:${System.currentTimeMillis()}"
        }
    }
    
    private fun detectKeyType(publicKey: PublicKey): SshKeyType {
        return when (publicKey) {
            is RSAPublicKey -> SshKeyType.RSA
            else -> {
                when (publicKey.algorithm) {
                    "DSA" -> SshKeyType.DSA
                    "EC" -> SshKeyType.ECDSA
                    "EdDSA" -> SshKeyType.ED25519
                    else -> SshKeyType.UNKNOWN
                }
            }
        }
    }
    
    private fun getKeySize(publicKey: PublicKey): Int {
        return when (publicKey) {
            is RSAPublicKey -> publicKey.modulus.bitLength()
            else -> 0 // Unknown or not applicable
        }
    }
}

/**
 * SSH key formats supported for import/export.
 */
enum class SshKeyFormat {
    OPENSSH,        // OpenSSH format (ssh-rsa, ssh-ed25519, etc.)
    PEM,            // PEM format (BEGIN RSA PRIVATE KEY)
    PKCS8,          // PKCS#8 format (BEGIN PRIVATE KEY)
    PUTTY,          // PuTTY private key format (.ppk)
    AUTO_DETECT     // Automatically detect format
}

/**
 * SSH key types supported.
 */
enum class SshKeyType {
    RSA,
    DSA,
    ECDSA,
    ED25519,
    UNKNOWN
}

/**
 * Sorting options for SSH identities.
 */
enum class SshIdentitySortBy {
    NAME,
    CREATED_DATE,
    LAST_USED,
    USAGE_COUNT
}

/**
 * Filter criteria for SSH identities.
 */
data class SshIdentityFilterCriteria(
    val createdAfter: Long? = null,
    val createdBefore: Long? = null,
    val recentlyUsedOnly: Boolean = false,
    val unusedOnly: Boolean = false,
    val namePattern: String? = null
)

/**
 * SSH key information from validation.
 */
data class SshKeyInfo(
    val keyType: SshKeyType,
    val keySize: Int,
    val fingerprint: String,
    val isEncrypted: Boolean,
    val format: SshKeyFormat
)

/**
 * Usage statistics for an SSH identity.
 */
data class SshIdentityUsageStats(
    val serverProfileCount: Int,
    val projectCount: Int,
    val lastConnectionAt: Long?,
    val recentConnections: Int
)

/**
 * SSH identity with usage statistics.
 */
data class SshIdentityWithUsage(
    val identity: SshIdentity,
    val usageStats: SshIdentityUsageStats
)

private fun ValidationResult.getErrorSummary(): String {
    return when (this) {
        is ValidationResult.Success -> "No errors"
        is ValidationResult.Failure -> errors.joinToString("; ") { it.message }
    }
}