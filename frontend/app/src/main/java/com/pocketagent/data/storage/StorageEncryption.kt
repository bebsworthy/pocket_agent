package com.pocketagent.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pocketagent.common.Constants
import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.map
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for encrypting and decrypting storage data using Android Keystore.
 *
 * This service provides secure encryption/decryption operations for JSON storage
 * using AES-256-GCM with hardware-backed keys when available.
 */
@Singleton
class StorageEncryption
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private val KEY_SIZE = Constants.Encryption.AES_KEY_SIZE_BITS
            private val IV_SIZE = Constants.Encryption.GCM_IV_LENGTH_BYTES
            private val TAG_SIZE = Constants.Encryption.GCM_TAG_LENGTH_BITS
            private const val MASTER_KEY_ALIAS = "pocket_agent_storage_master_key"
            private val COMPRESSION_THRESHOLD = Constants.Encryption.COMPRESSION_THRESHOLD_BYTES // Bytes
        }

        private val keyStore: KeyStore by lazy {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        }

        private val secureRandom = SecureRandom()

        /**
         * Encrypted data wrapper containing IV and ciphertext.
         */
        data class EncryptedData(
            val iv: ByteArray,
            val ciphertext: ByteArray,
            val isCompressed: Boolean = false,
        ) {
            fun toByteArray(): ByteArray {
                val result = ByteArray(1 + IV_SIZE + ciphertext.size)
                result[0] = if (isCompressed) 1.toByte() else 0.toByte()
                System.arraycopy(iv, 0, result, 1, IV_SIZE)
                System.arraycopy(ciphertext, 0, result, 1 + IV_SIZE, ciphertext.size)
                return result
            }

            companion object {
                fun fromByteArray(data: ByteArray): EncryptedData {
                    if (data.size < 1 + IV_SIZE) {
                        throw IllegalArgumentException("Invalid encrypted data format")
                    }

                    val isCompressed = data[0] == 1.toByte()
                    val iv = data.sliceArray(1 until 1 + IV_SIZE)
                    val ciphertext = data.sliceArray(1 + IV_SIZE until data.size)

                    return EncryptedData(iv, ciphertext, isCompressed)
                }
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as EncryptedData

                if (!iv.contentEquals(other.iv)) return false
                if (!ciphertext.contentEquals(other.ciphertext)) return false
                if (isCompressed != other.isCompressed) return false

                return true
            }

            override fun hashCode(): Int {
                var result = iv.contentHashCode()
                result = 31 * result + ciphertext.contentHashCode()
                result = 31 * result + isCompressed.hashCode()
                return result
            }
        }

        /**
         * Encrypts data using the master key.
         *
         * @param data The data to encrypt
         * @param enableCompression Whether to enable compression for large data
         * @return Encrypted data or error result
         */
        suspend fun encrypt(
            data: String,
            enableCompression: Boolean = true,
        ): Result<EncryptedData> =
            try {
                val masterKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, masterKey)

                val iv = cipher.iv
                val dataBytes = data.toByteArray(Charsets.UTF_8)

                // Compress data if it's large enough and compression is enabled
                val shouldCompress = enableCompression && dataBytes.size >= COMPRESSION_THRESHOLD
                val processedData =
                    if (shouldCompress) {
                        compressData(dataBytes)
                    } else {
                        dataBytes
                    }

                val ciphertext = cipher.doFinal(processedData)

                Result.Success(EncryptedData(iv, ciphertext, shouldCompress))
            } catch (e: StorageException) {
                android.util.Log.e("StorageEncryption", "Storage exception during encryption", e)
                Result.Error(e, "Storage error during encryption: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.e("StorageEncryption", "Security exception during encryption", e)
                Result.Error(e, "Security error during encryption: ${e.message}")
            } catch (e: javax.crypto.BadPaddingException) {
                android.util.Log.e("StorageEncryption", "Bad padding exception during encryption", e)
                Result.Error(e, "Encryption padding error: ${e.message}")
            } catch (e: javax.crypto.IllegalBlockSizeException) {
                android.util.Log.e("StorageEncryption", "Illegal block size exception during encryption", e)
                Result.Error(e, "Encryption block size error: ${e.message}")
            } catch (e: java.security.InvalidKeyException) {
                android.util.Log.e("StorageEncryption", "Invalid key exception during encryption", e)
                Result.Error(e, "Invalid encryption key: ${e.message}")
            } catch (e: java.security.NoSuchAlgorithmException) {
                android.util.Log.e("StorageEncryption", "Algorithm exception during encryption", e)
                Result.Error(e, "Encryption algorithm error: ${e.message}")
            } catch (e: java.io.IOException) {
                android.util.Log.e("StorageEncryption", "IO exception during encryption", e)
                Result.Error(e, "IO error during encryption: ${e.message}")
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("StorageEncryption", "Invalid argument during encryption", e)
                Result.Error(e, "Invalid argument during encryption: ${e.message}")
            }

        /**
         * Decrypts data using the master key.
         *
         * @param encryptedData The encrypted data to decrypt
         * @return Decrypted string or error result
         */
        suspend fun decrypt(encryptedData: EncryptedData): Result<String> =
            try {
                val masterKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)

                val spec = GCMParameterSpec(TAG_SIZE, encryptedData.iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

                val decryptedData = cipher.doFinal(encryptedData.ciphertext)

                // Decompress if the data was compressed
                val processedData =
                    if (encryptedData.isCompressed) {
                        decompressData(decryptedData)
                    } else {
                        decryptedData
                    }

                Result.Success(String(processedData, Charsets.UTF_8))
            } catch (e: StorageException) {
                android.util.Log.e("StorageEncryption", "Storage exception during decryption", e)
                Result.Error(e, "Storage error during decryption: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.e("StorageEncryption", "Security exception during decryption", e)
                Result.Error(e, "Security error during decryption: ${e.message}")
            } catch (e: javax.crypto.BadPaddingException) {
                android.util.Log.e("StorageEncryption", "Bad padding exception during decryption", e)
                Result.Error(e, "Decryption padding error: ${e.message}")
            } catch (e: javax.crypto.IllegalBlockSizeException) {
                android.util.Log.e("StorageEncryption", "Illegal block size exception during decryption", e)
                Result.Error(e, "Decryption block size error: ${e.message}")
            } catch (e: javax.crypto.AEADBadTagException) {
                android.util.Log.e("StorageEncryption", "Authentication tag exception during decryption", e)
                Result.Error(e, "Decryption authentication error: ${e.message}")
            } catch (e: java.security.InvalidKeyException) {
                android.util.Log.e("StorageEncryption", "Invalid key exception during decryption", e)
                Result.Error(e, "Invalid decryption key: ${e.message}")
            } catch (e: java.security.NoSuchAlgorithmException) {
                android.util.Log.e("StorageEncryption", "Algorithm exception during decryption", e)
                Result.Error(e, "Decryption algorithm error: ${e.message}")
            } catch (e: java.io.IOException) {
                android.util.Log.e("StorageEncryption", "IO exception during decryption", e)
                Result.Error(e, "IO error during decryption: ${e.message}")
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("StorageEncryption", "Invalid argument during decryption", e)
                Result.Error(e, "Invalid argument during decryption: ${e.message}")
            }

        /**
         * Encrypts data and returns as byte array.
         *
         * @param data The data to encrypt
         * @param enableCompression Whether to enable compression
         * @return Encrypted data as byte array or error result
         */
        suspend fun encryptToByteArray(
            data: String,
            enableCompression: Boolean = true,
        ): Result<ByteArray> = encrypt(data, enableCompression).map { it.toByteArray() }

        /**
         * Decrypts data from byte array.
         *
         * @param data The encrypted data as byte array
         * @return Decrypted string or error result
         */
        suspend fun decryptFromByteArray(data: ByteArray): Result<String> =
            try {
                val encryptedData = EncryptedData.fromByteArray(data)
                decrypt(encryptedData)
            } catch (e: StorageException) {
                android.util.Log.e("StorageEncryption", "Storage exception parsing encrypted data", e)
                Result.Error(e, "Storage error parsing encrypted data: ${e.message}")
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("StorageEncryption", "Invalid argument parsing encrypted data", e)
                Result.Error(e, "Invalid encrypted data format: ${e.message}")
            } catch (e: IndexOutOfBoundsException) {
                android.util.Log.e("StorageEncryption", "Index out of bounds parsing encrypted data", e)
                Result.Error(e, "Corrupted encrypted data: ${e.message}")
            }

        /**
         * Verifies the integrity of encrypted data.
         *
         * @param encryptedData The encrypted data to verify
         * @return True if data is valid, false otherwise
         */
        suspend fun verifyDataIntegrity(encryptedData: EncryptedData): Result<Boolean> =
            try {
                // Attempt to decrypt to verify integrity
                val result = decrypt(encryptedData)
                Result.Success(result.isSuccess)
            } catch (e: StorageException) {
                android.util.Log.w("StorageEncryption", "Storage exception verifying data integrity", e)
                Result.Success(false)
            } catch (e: SecurityException) {
                android.util.Log.w("StorageEncryption", "Security exception verifying data integrity", e)
                Result.Success(false)
            } catch (e: javax.crypto.BadPaddingException) {
                android.util.Log.w("StorageEncryption", "Bad padding exception verifying data integrity", e)
                Result.Success(false)
            } catch (e: javax.crypto.IllegalBlockSizeException) {
                android.util.Log.w("StorageEncryption", "Illegal block size exception verifying data integrity", e)
                Result.Success(false)
            } catch (e: javax.crypto.AEADBadTagException) {
                android.util.Log.w("StorageEncryption", "Authentication tag exception verifying data integrity", e)
                Result.Success(false)
            }

        /**
         * Generates a new master key or retrieves existing one.
         *
         * @return The master key
         */
        private fun getOrCreateMasterKey(): SecretKey =
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
            } else {
                generateMasterKey()
            }

        /**
         * Generates a new master key in Android Keystore.
         *
         * @return The generated master key
         */
        private fun generateMasterKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)

            val keyGenParameterSpec =
                KeyGenParameterSpec
                    .Builder(
                        MASTER_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).apply {
                        setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        setKeySize(KEY_SIZE)
                        setUserAuthenticationRequired(false) // Storage key always accessible

                        // Use StrongBox if available (hardware security module)
                        if (android.os.Build.VERSION.SDK_INT >= Constants.Android.API_LEVEL_STRONGBOX) {
                            try {
                                setIsStrongBoxBacked(true)
                            } catch (e: SecurityException) {
                                android.util.Log.i("StorageEncryption", "StrongBox not available, using regular TEE", e)
                            } catch (e: java.security.InvalidAlgorithmParameterException) {
                                android.util.Log.i("StorageEncryption", "StrongBox parameters not supported, using regular TEE", e)
                            }
                        }
                    }.build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        /**
         * Compresses data using GZIP.
         *
         * @param data The data to compress
         * @return Compressed data
         */
        private fun compressData(data: ByteArray): ByteArray {
            val outputStream = ByteArrayOutputStream()
            GZIPOutputStream(outputStream).use { gzip ->
                gzip.write(data)
            }
            return outputStream.toByteArray()
        }

        /**
         * Decompresses GZIP data.
         *
         * @param data The compressed data
         * @return Decompressed data
         */
        private fun decompressData(data: ByteArray): ByteArray {
            val outputStream = ByteArrayOutputStream()
            GZIPInputStream(data.inputStream()).use { gzip ->
                gzip.copyTo(outputStream)
            }
            return outputStream.toByteArray()
        }

        /**
         * Deletes the master key.
         *
         * @return Success or error result
         */
        suspend fun deleteMasterKey(): Result<Unit> =
            try {
                if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MASTER_KEY_ALIAS)
                }
                Result.Success(Unit)
            } catch (e: StorageException) {
                android.util.Log.e("StorageEncryption", "Storage exception deleting master key", e)
                Result.Error(e, "Storage error deleting master key: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.e("StorageEncryption", "Security exception deleting master key", e)
                Result.Error(e, "Security error deleting master key: ${e.message}")
            } catch (e: java.security.KeyStoreException) {
                android.util.Log.e("StorageEncryption", "KeyStore exception deleting master key", e)
                Result.Error(e, "KeyStore error deleting master key: ${e.message}")
            }

        /**
         * Checks if the master key exists.
         *
         * @return True if master key exists
         */
        suspend fun masterKeyExists(): Result<Boolean> =
            try {
                Result.Success(keyStore.containsAlias(MASTER_KEY_ALIAS))
            } catch (e: StorageException) {
                android.util.Log.e("StorageEncryption", "Storage exception checking master key existence", e)
                Result.Error(e, "Storage error checking master key existence: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.e("StorageEncryption", "Security exception checking master key existence", e)
                Result.Error(e, "Security error checking master key existence: ${e.message}")
            } catch (e: java.security.KeyStoreException) {
                android.util.Log.e("StorageEncryption", "KeyStore exception checking master key existence", e)
                Result.Error(e, "KeyStore error checking master key existence: ${e.message}")
            }
    }
