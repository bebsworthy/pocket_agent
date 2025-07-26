package com.pocketagent.data.security

import android.util.Log
import com.pocketagent.data.service.SecurityManagerException
import com.pocketagent.domain.models.Result
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

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
    abstract suspend fun encrypt(
        data: String,
        keyAlias: String,
    ): Result<ByteArray>

    /**
     * Decrypts data using the specified key.
     *
     * @param encryptedData The encrypted data
     * @param keyAlias The key alias for decryption
     * @return Decrypted data or error result
     */
    abstract suspend fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String,
    ): Result<String>

    /**
     * Generates a new key with the specified alias.
     *
     * @param keyAlias The alias for the new key
     * @param requireBiometric Whether the key requires biometric authentication
     * @return Success or error result
     */
    abstract suspend fun generateKey(
        keyAlias: String,
        requireBiometric: Boolean,
    ): Result<Unit>

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
    protected suspend fun <T> handleSecurityOperation(operation: suspend () -> T): Result<T> =
        try {
            Result.Success(operation())
        } catch (e: InvalidKeyException) {
            Log.e("BaseSecurityManager", "Invalid key used in security operation", e)
            Result.Error(
                SecurityManagerException.KeyOperationException("Invalid key: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: NoSuchAlgorithmException) {
            Log.e("BaseSecurityManager", "Algorithm not available for security operation", e)
            Result.Error(
                SecurityManagerException.EncryptionException("Algorithm not available: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: NoSuchPaddingException) {
            Log.e("BaseSecurityManager", "Padding scheme not available for security operation", e)
            Result.Error(
                SecurityManagerException.EncryptionException("Padding not available: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: IllegalBlockSizeException) {
            Log.e("BaseSecurityManager", "Illegal block size in security operation", e)
            Result.Error(
                SecurityManagerException.EncryptionException("Illegal block size: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: BadPaddingException) {
            Log.e("BaseSecurityManager", "Bad padding in security operation", e)
            Result.Error(
                SecurityManagerException.DecryptionException("Bad padding: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: SecurityException) {
            Log.e("BaseSecurityManager", "Security exception in security operation", e)
            Result.Error(
                SecurityManagerException.KeyOperationException("Security error: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: IllegalArgumentException) {
            Log.e("BaseSecurityManager", "Invalid argument in security operation", e)
            Result.Error(
                SecurityManagerException.KeyOperationException("Invalid argument: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        } catch (e: RuntimeException) {
            Log.e("BaseSecurityManager", "Runtime exception in security operation", e)
            Result.Error(
                SecurityManagerException.KeyOperationException("Runtime error: ${e.message}", e),
                "Security operation failed: ${e.message}",
            )
        }
}
