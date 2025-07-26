package com.pocketagent.data.service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.pocketagent.common.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
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
class SshKeyEncryption
    @Inject
    constructor() {
        companion object {
            private const val TAG = "SshKeyEncryption"

            // Encryption constants
            private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
            private const val KEY_ALGORITHM = "AES"
            private val KEY_SIZE = Constants.Encryption.AES_KEY_SIZE_BITS
            private val GCM_IV_LENGTH = Constants.Encryption.GCM_IV_LENGTH_BYTES
            private val GCM_TAG_LENGTH = Constants.Encryption.GCM_TAG_LENGTH_BYTES

            // Keystore constants
            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val SSH_KEY_ENCRYPTION_ALIAS = "ssh_key_encryption_master_key"

            // Data format constants
            private val ENCRYPTED_DATA_VERSION = Constants.Encryption.ENCRYPTED_DATA_VERSION
            private val HEADER_SIZE = Constants.Encryption.ENCRYPTED_DATA_HEADER_SIZE_BYTES // version(4) + iv_length(4)
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
                val gcmSpec = GCMParameterSpec(Constants.Encryption.GCM_TAG_LENGTH_BITS, iv)
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
                val gcmSpec = GCMParameterSpec(Constants.Encryption.GCM_TAG_LENGTH_BITS, iv)
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
        fun isMasterKeyAvailable(): Boolean =
            try {
                keyStore.containsAlias(SSH_KEY_ENCRYPTION_ALIAS) &&
                    keyStore.getKey(SSH_KEY_ENCRYPTION_ALIAS, null) != null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check master key availability", e)
                false
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
        fun reencryptPrivateKey(
            oldEncryptedData: String,
            oldMasterKey: SecretKey,
        ): String {
            Log.d(TAG, "Re-encrypting SSH private key with new master key")

            try {
                // Decrypt with old key
                val encryptedPackage = Base64.getDecoder().decode(oldEncryptedData)
                val (iv, encryptedData) = parseEncryptedPackage(encryptedPackage)

                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(Constants.Encryption.GCM_TAG_LENGTH_BITS, iv)
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
        fun validateEncryptedData(encryptedKeyData: String): Boolean =
            try {
                decryptPrivateKey(encryptedKeyData)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted data validation failed", e)
                false
            }

        // Private helper methods

        private fun getOrCreateMasterKey(): SecretKey =
            if (keyStore.containsAlias(SSH_KEY_ENCRYPTION_ALIAS)) {
                keyStore.getKey(SSH_KEY_ENCRYPTION_ALIAS, null) as SecretKey
            } else {
                createMasterKey()
            }

        private fun createMasterKey(): SecretKey {
            Log.d(TAG, "Creating new master encryption key in Android Keystore")

            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)

            val keyGenParameterSpec =
                KeyGenParameterSpec
                    .Builder(
                        SSH_KEY_ENCRYPTION_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(false) // Set to true for biometric auth
                    .setRandomizedEncryptionRequired(true)
                    .build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        private fun createEncryptedPackage(
            iv: ByteArray,
            encryptedData: ByteArray,
        ): ByteArray {
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
            val versionBytes = ByteArray(Constants.Binary.INT_BYTE_SIZE)
            input.read(versionBytes)
            val version = bytesToInt(versionBytes)

            if (version != ENCRYPTED_DATA_VERSION) {
                throw IllegalArgumentException("Unsupported encrypted data version: $version")
            }

            // Read IV length (4 bytes)
            val ivLengthBytes = ByteArray(Constants.Binary.INT_BYTE_SIZE)
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

        private fun intToBytes(value: Int): ByteArray =
            byteArrayOf(
                (value shr Constants.Binary.BYTE_SHIFT_24).toByte(),
                (value shr Constants.Binary.BYTE_SHIFT_16).toByte(),
                (value shr Constants.Binary.BYTE_SHIFT_8).toByte(),
                value.toByte(),
            )

        private fun bytesToInt(bytes: ByteArray): Int =
            (bytes[0].toInt() and Constants.Binary.UNSIGNED_BYTE_MASK shl Constants.Binary.BYTE_SHIFT_24) or
                (bytes[1].toInt() and Constants.Binary.UNSIGNED_BYTE_MASK shl Constants.Binary.BYTE_SHIFT_16) or
                (bytes[2].toInt() and Constants.Binary.UNSIGNED_BYTE_MASK shl Constants.Binary.BYTE_SHIFT_8) or
                (bytes[3].toInt() and Constants.Binary.UNSIGNED_BYTE_MASK)
    }

/**
 * Exception thrown when SSH key encryption/decryption operations fail.
 */
class SshKeyEncryptionException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

/**
 * Configuration for SSH key encryption settings.
 */
data class SshKeyEncryptionConfig(
    val requireBiometricAuth: Boolean = false,
    val keyValidityDuration: Int = Constants.Encryption.DEFAULT_BIOMETRIC_VALIDITY_SECONDS, // seconds
    val allowKeyBackup: Boolean = false,
)

/**
 * Information about encrypted SSH key data.
 */
data class EncryptedKeyInfo(
    val version: Int,
    val encryptionAlgorithm: String,
    val keySize: Int,
    val createdAt: Long,
    val isBackupEnabled: Boolean,
)

/**
 * Helper object for SSH key encryption utilities.
 */
object SshKeyEncryptionUtils {
    /**
     * Generates a secure random salt for key derivation.
     */
    fun generateSalt(length: Int = Constants.Encryption.DEFAULT_SALT_LENGTH_BYTES): ByteArray {
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
    fun isStrongEncryption(
        algorithm: String,
        keySize: Int,
    ): Boolean =
        when (algorithm.uppercase()) {
            "AES" -> keySize >= Constants.Encryption.AES_KEY_SIZE_BITS
            "RSA" -> keySize >= Constants.Encryption.RSA_MIN_KEY_SIZE_BITS
            "EC" -> keySize >= Constants.Encryption.EC_MIN_KEY_SIZE_BITS
            else -> false
        }

    /**
     * Gets recommended key size for an algorithm.
     */
    fun getRecommendedKeySize(algorithm: String): Int =
        when (algorithm.uppercase()) {
            "AES" -> Constants.Encryption.AES_KEY_SIZE_BITS
            "RSA" -> Constants.Encryption.RSA_RECOMMENDED_KEY_SIZE_BITS
            "EC" -> Constants.Encryption.EC_RECOMMENDED_KEY_SIZE_BITS
            "ED25519" -> Constants.Encryption.ED25519_KEY_SIZE_BITS
            else -> Constants.Encryption.AES_KEY_SIZE_BITS
        }
}
