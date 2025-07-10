package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.SshIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for SSH identity management.
 * 
 * This interface defines the contract for managing SSH identities,
 * including secure storage and retrieval of SSH keys with hardware-backed encryption.
 * 
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface SshIdentityRepository {
    
    /**
     * Retrieves all SSH identities ordered by name.
     * 
     * @return Flow emitting a list of SSH identities
     */
    fun getAllIdentities(): Flow<Result<List<SshIdentity>>>
    
    /**
     * Observes SSH identities with real-time updates.
     * 
     * @return Flow emitting SSH identity list updates
     */
    fun observeIdentities(): Flow<List<SshIdentity>>
    
    /**
     * Retrieves an SSH identity by ID.
     * 
     * @param id The SSH identity ID
     * @return The SSH identity if found
     */
    suspend fun getIdentityById(id: String): Result<SshIdentity?>
    
    /**
     * Observes a specific SSH identity for real-time updates.
     * 
     * @param id The SSH identity ID
     * @return Flow emitting SSH identity updates
     */
    fun observeIdentity(id: String): Flow<SshIdentity?>
    
    /**
     * Imports and creates a new SSH identity with hardware-backed encryption.
     * 
     * @param name User-friendly name for the identity
     * @param privateKey The SSH private key to import
     * @param publicKey The SSH public key
     * @param passphrase Optional passphrase for encrypted keys
     * @param description Optional description for the identity
     * @return The created SSH identity
     */
    suspend fun createIdentity(
        name: String,
        privateKey: String,
        publicKey: String,
        passphrase: String? = null,
        description: String? = null
    ): Result<SshIdentity>
    
    /**
     * Updates an existing SSH identity.
     * 
     * @param identity The SSH identity to update
     * @return The updated SSH identity
     */
    suspend fun updateIdentity(identity: SshIdentity): Result<SshIdentity>
    
    /**
     * Updates the last used timestamp for an SSH identity.
     * 
     * @param id The SSH identity ID
     * @return Success or error result
     */
    suspend fun updateLastUsed(id: String): Result<Unit>
    
    /**
     * Deletes an SSH identity.
     * Will fail if the identity is in use by server profiles.
     * 
     * @param id The SSH identity ID to delete
     * @return Success or error result
     */
    suspend fun deleteIdentity(id: String): Result<Unit>
    
    /**
     * Batch deletes multiple SSH identities.
     * Will fail if any identity is in use.
     * 
     * @param ids The SSH identity IDs to delete
     * @return Success or error result with failed IDs
     */
    suspend fun deleteIdentities(ids: List<String>): Result<List<String>>
    
    /**
     * Retrieves the private key for an SSH identity.
     * Uses the authenticated session from app launch.
     * 
     * @param id The SSH identity ID
     * @return The decrypted private key
     */
    suspend fun getPrivateKey(id: String): Result<String>
    
    /**
     * Signs data using the SSH identity's private key.
     * Used for WebSocket authentication challenge-response.
     * 
     * @param id The SSH identity ID
     * @param data The data to sign
     * @return The signature
     */
    suspend fun signData(id: String, data: ByteArray): Result<ByteArray>
    
    /**
     * Signs authentication challenge for WebSocket connection.
     * 
     * @param id The SSH identity ID
     * @param nonce The authentication nonce
     * @param timestamp The challenge timestamp
     * @return Base64 encoded signature
     */
    suspend fun signAuthChallenge(
        id: String,
        nonce: String,
        timestamp: Long
    ): Result<String>
    
    /**
     * Validates an SSH key pair.
     * 
     * @param privateKey The private key to validate
     * @param publicKey The public key to validate
     * @return True if the key pair is valid
     */
    suspend fun validateKeyPair(privateKey: String, publicKey: String): Result<Boolean>
    
    /**
     * Validates SSH private key format.
     * 
     * @param privateKey The private key to validate
     * @return True if the key format is valid
     */
    suspend fun validateKeyFormat(privateKey: String): Result<Boolean>
    
    /**
     * Extracts public key from private key.
     * 
     * @param privateKey The private key
     * @param passphrase Optional passphrase for encrypted keys
     * @return The extracted public key
     */
    suspend fun extractPublicKey(
        privateKey: String,
        passphrase: String? = null
    ): Result<String>
    
    /**
     * Generates fingerprint for a public key.
     * 
     * @param publicKey The public key
     * @return SHA256 fingerprint
     */
    suspend fun generateFingerprint(publicKey: String): Result<String>
    
    /**
     * Searches SSH identities by name or fingerprint.
     * 
     * @param query The search query
     * @return List of matching SSH identities
     */
    suspend fun searchIdentities(query: String): Result<List<SshIdentity>>
    
    /**
     * Exports SSH identity data (without private key).
     * 
     * @param id The SSH identity ID
     * @return Exported identity data as JSON
     */
    suspend fun exportIdentity(id: String): Result<String>
    
    /**
     * Imports SSH identity from exported data.
     * 
     * @param data The exported identity data
     * @return The imported SSH identity
     */
    suspend fun importIdentity(data: String): Result<SshIdentity>
    
    /**
     * Checks if an SSH identity name already exists.
     * 
     * @param name The identity name to check
     * @param excludeId Optional ID to exclude from check (for updates)
     * @return True if name exists
     */
    suspend fun isNameExists(name: String, excludeId: String? = null): Result<Boolean>
    
    /**
     * Gets usage statistics for SSH identities.
     * 
     * @return Map of identity ID to usage count
     */
    suspend fun getUsageStatistics(): Result<Map<String, Int>>
    
    /**
     * Synchronizes SSH identities with encrypted storage.
     * Used for offline/online synchronization.
     * 
     * @return Success or error result
     */
    suspend fun syncIdentities(): Result<Unit>
    
    /**
     * Clears all SSH identities (for logout/reset).
     * 
     * @return Success or error result
     */
    suspend fun clearAll(): Result<Unit>
}