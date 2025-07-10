package com.pocketagent.data.security

import com.pocketagent.domain.models.Result

/**
 * Base class for security managers.
 * 
 * This abstract class provides common functionality for security operations
 * including encryption, decryption, and key management.
 */
abstract class BaseSecurityManager {
    
    /**
     * Encrypts data using the specified key.
     * 
     * @param data The data to encrypt
     * @param keyAlias The key alias for encryption
     * @return Encrypted data or error result
     */
    abstract suspend fun encrypt(data: String, keyAlias: String): Result<ByteArray>
    
    /**
     * Decrypts data using the specified key.
     * 
     * @param encryptedData The encrypted data
     * @param keyAlias The key alias for decryption
     * @return Decrypted data or error result
     */
    abstract suspend fun decrypt(encryptedData: ByteArray, keyAlias: String): Result<String>
    
    /**
     * Generates a new key with the specified alias.
     * 
     * @param keyAlias The alias for the new key
     * @param requireBiometric Whether the key requires biometric authentication
     * @return Success or error result
     */
    abstract suspend fun generateKey(keyAlias: String, requireBiometric: Boolean): Result<Unit>
    
    /**
     * Deletes a key with the specified alias.
     * 
     * @param keyAlias The alias of the key to delete
     * @return Success or error result
     */
    abstract suspend fun deleteKey(keyAlias: String): Result<Unit>
    
    /**
     * Checks if a key exists with the specified alias.
     * 
     * @param keyAlias The key alias to check
     * @return True if the key exists
     */
    abstract suspend fun keyExists(keyAlias: String): Result<Boolean>
    
    /**
     * Handles security exceptions and converts them to Result.Error.
     * 
     * @param operation The security operation to execute
     * @return Result wrapped operation result
     */
    protected suspend fun <T> handleSecurityOperation(operation: suspend () -> T): Result<T> {
        return try {
            Result.Success(operation())
        } catch (e: Exception) {
            Result.Error(e, "Security operation failed: ${e.message}")
        }
    }
}