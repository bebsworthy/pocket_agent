package com.pocketagent.data.service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH key encryption service for secure storage of private keys.
 * 
 * This service handles the encryption and decryption of SSH private keys
 * for secure storage in the application. It uses Android Keystore for
 * key management and AES-GCM for symmetric encryption.
 * 
 * Features:
 * - AES-256-GCM encryption for private keys
 * - Android Keystore integration for key protection
 * - Biometric authentication support
 * - Key rotation and migration support
 * - Secure key derivation
 */
@Singleton
class SshKeyEncryption @Inject constructor() {
    
    companion object {
        private const val TAG = "SshKeyEncryption"
        
        // Encryption constants
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        
        // Keystore constants
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SSH_KEY_ENCRYPTION_ALIAS = "ssh_key_encryption_master_key"
        
        // Data format constants
        private const val ENCRYPTED_DATA_VERSION = 1
        private const val HEADER_SIZE = 8 // version(4) + iv_length(4)
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }
    
    /**
     * Encrypts an SSH private key for secure storage.
     * 
     * @param privateKey The private key to encrypt
     * @return Encrypted key data as base64 string
     * @throws SecurityException if encryption fails
     */
    fun encryptPrivateKey(privateKey: PrivateKey): String {
        Log.d(TAG, "Encrypting SSH private key")
        
        try {
            // Get or create the master encryption key
            val masterKey = getOrCreateMasterKey()
            
            // Encode the private key
            val privateKeyData = privateKey.encoded
            
            // Generate IV for GCM
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)
            
            // Encrypt the private key data
            val encryptedData = cipher.doFinal(privateKeyData)
            
            // Create the final encrypted package
            val encryptedPackage = createEncryptedPackage(iv, encryptedData)
            
            // Encode to base64 for storage
            val result = Base64.getEncoder().encodeToString(encryptedPackage)
            
            Log.d(TAG, "SSH private key encrypted successfully")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt SSH private key", e)
            throw SecurityException("Failed to encrypt SSH private key", e)
        }
    }
    
    /**
     * Decrypts an SSH private key from secure storage.
     * 
     * @param encryptedKeyData Encrypted key data as base64 string
     * @return Decrypted private key
     * @throws SecurityException if decryption fails
     */
    fun decryptPrivateKey(encryptedKeyData: String): PrivateKey {
        Log.d(TAG, "Decrypting SSH private key")
        
        try {
            // Decode from base64
            val encryptedPackage = Base64.getDecoder().decode(encryptedKeyData)
            
            // Parse the encrypted package
            val (iv, encryptedData) = parseEncryptedPackage(encryptedPackage)
            
            // Get the master encryption key
            val masterKey = getOrCreateMasterKey()
            
            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)
            
            // Decrypt the private key data
            val privateKeyData = cipher.doFinal(encryptedData)
            
            // Reconstruct the private key
            val privateKey = reconstructPrivateKey(privateKeyData)
            
            Log.d(TAG, "SSH private key decrypted successfully")
            return privateKey
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt SSH private key", e)
            throw SecurityException("Failed to decrypt SSH private key", e)
        }
    }
    
    /**
     * Checks if the master encryption key exists and is valid.
     * 
     * @return true if master key is available
     */
    fun isMasterKeyAvailable(): Boolean {
        return try {
            keyStore.containsAlias(SSH_KEY_ENCRYPTION_ALIAS) && 
            keyStore.getKey(SSH_KEY_ENCRYPTION_ALIAS, null) != null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check master key availability", e)
            false
        }
    }
    
    /**
     * Recreates the master encryption key (for key rotation).
     * WARNING: This will make all previously encrypted keys inaccessible.
     * 
     * @return true if key was recreated successfully
     */
    fun recreateMasterKey(): Boolean {
        Log.d(TAG, "Recreating master encryption key")
        
        return try {
            // Delete existing key if it exists
            if (keyStore.containsAlias(SSH_KEY_ENCRYPTION_ALIAS)) {
                keyStore.deleteEntry(SSH_KEY_ENCRYPTION_ALIAS)
            }
            
            // Create new master key
            createMasterKey()
            
            Log.d(TAG, "Master encryption key recreated successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate master encryption key", e)
            false
        }
    }
    
    /**
     * Re-encrypts private key data with a new master key.
     * Used for key migration scenarios.
     * 
     * @param oldEncryptedData Previously encrypted data
     * @param oldMasterKey Previous master key
     * @return Newly encrypted data
     */
    fun reencryptPrivateKey(oldEncryptedData: String, oldMasterKey: SecretKey): String {
        Log.d(TAG, "Re-encrypting SSH private key with new master key")
        
        try {
            // Decrypt with old key
            val encryptedPackage = Base64.getDecoder().decode(oldEncryptedData)
            val (iv, encryptedData) = parseEncryptedPackage(encryptedPackage)
            
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, oldMasterKey, gcmSpec)
            
            val privateKeyData = cipher.doFinal(encryptedData)
            val privateKey = reconstructPrivateKey(privateKeyData)
            
            // Encrypt with new key
            return encryptPrivateKey(privateKey)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-encrypt SSH private key", e)
            throw SecurityException("Failed to re-encrypt SSH private key", e)
        }
    }
    
    /**
     * Validates that encrypted data can be successfully decrypted.
     * 
     * @param encryptedKeyData Encrypted key data to validate
     * @return true if data can be decrypted
     */
    fun validateEncryptedData(encryptedKeyData: String): Boolean {
        return try {
            decryptPrivateKey(encryptedKeyData)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted data validation failed", e)
            false
        }
    }
    
    // Private helper methods
    
    private fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(SSH_KEY_ENCRYPTION_ALIAS)) {
            keyStore.getKey(SSH_KEY_ENCRYPTION_ALIAS, null) as SecretKey
        } else {
            createMasterKey()
        }
    }
    
    private fun createMasterKey(): SecretKey {
        Log.d(TAG, "Creating new master encryption key in Android Keystore")
        
        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            SSH_KEY_ENCRYPTION_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(false) // Set to true for biometric auth
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    private fun createEncryptedPackage(iv: ByteArray, encryptedData: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Write version (4 bytes)
        output.write(intToBytes(ENCRYPTED_DATA_VERSION))
        
        // Write IV length (4 bytes)
        output.write(intToBytes(iv.size))
        
        // Write IV
        output.write(iv)
        
        // Write encrypted data
        output.write(encryptedData)
        
        return output.toByteArray()
    }
    
    private fun parseEncryptedPackage(encryptedPackage: ByteArray): Pair<ByteArray, ByteArray> {
        val input = ByteArrayInputStream(encryptedPackage)
        
        // Read version (4 bytes)
        val versionBytes = ByteArray(4)
        input.read(versionBytes)
        val version = bytesToInt(versionBytes)
        
        if (version != ENCRYPTED_DATA_VERSION) {
            throw IllegalArgumentException("Unsupported encrypted data version: $version")
        }
        
        // Read IV length (4 bytes)
        val ivLengthBytes = ByteArray(4)
        input.read(ivLengthBytes)
        val ivLength = bytesToInt(ivLengthBytes)
        
        // Read IV
        val iv = ByteArray(ivLength)
        input.read(iv)
        
        // Read encrypted data (remaining bytes)
        val encryptedData = input.readBytes()
        
        return Pair(iv, encryptedData)
    }
    
    private fun reconstructPrivateKey(privateKeyData: ByteArray): PrivateKey {
        // Try different key algorithms to reconstruct the private key
        val algorithms = listOf("RSA", "DSA", "EC")
        
        for (algorithm in algorithms) {
            try {
                val keyFactory = KeyFactory.getInstance(algorithm)
                val keySpec = PKCS8EncodedKeySpec(privateKeyData)
                return keyFactory.generatePrivate(keySpec)
            } catch (e: Exception) {
                // Continue to next algorithm
                Log.d(TAG, "Failed to reconstruct private key with algorithm: $algorithm")
            }
        }
        
        throw IllegalArgumentException("Unable to reconstruct private key - unsupported algorithm")
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
    
    private fun bytesToInt(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF shl 24) or
               (bytes[1].toInt() and 0xFF shl 16) or
               (bytes[2].toInt() and 0xFF shl 8) or
               (bytes[3].toInt() and 0xFF)
    }
}

/**
 * Exception thrown when SSH key encryption/decryption operations fail.
 */
class SshKeyEncryptionException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)

/**
 * Configuration for SSH key encryption settings.
 */
data class SshKeyEncryptionConfig(
    val requireBiometricAuth: Boolean = false,
    val keyValidityDuration: Int = 300, // seconds
    val allowKeyBackup: Boolean = false
)

/**
 * Information about encrypted SSH key data.
 */
data class EncryptedKeyInfo(
    val version: Int,
    val encryptionAlgorithm: String,
    val keySize: Int,
    val createdAt: Long,
    val isBackupEnabled: Boolean
)

/**
 * Helper object for SSH key encryption utilities.
 */
object SshKeyEncryptionUtils {
    
    /**
     * Generates a secure random salt for key derivation.
     */
    fun generateSalt(length: Int = 32): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }
    
    /**
     * Securely wipes sensitive data from memory.
     */
    fun secureWipe(data: ByteArray) {
        data.fill(0)
    }
    
    /**
     * Securely wipes sensitive data from memory.
     */
    fun secureWipe(data: CharArray) {
        data.fill(0.toChar())
    }
    
    /**
     * Validates encryption algorithm strength.
     */
    fun isStrongEncryption(algorithm: String, keySize: Int): Boolean {
        return when (algorithm.uppercase()) {
            "AES" -> keySize >= 256
            "RSA" -> keySize >= 2048
            "EC" -> keySize >= 256
            else -> false
        }
    }
    
    /**
     * Gets recommended key size for an algorithm.
     */
    fun getRecommendedKeySize(algorithm: String): Int {
        return when (algorithm.uppercase()) {
            "AES" -> 256
            "RSA" -> 4096
            "EC" -> 384
            "ED25519" -> 256
            else -> 256
        }
    }
}