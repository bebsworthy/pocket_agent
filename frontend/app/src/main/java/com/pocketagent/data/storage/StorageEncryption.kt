package com.pocketagent.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
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
class StorageEncryption @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
        private const val MASTER_KEY_ALIAS = "pocket_agent_storage_master_key"
        private const val COMPRESSION_THRESHOLD = 1024 // Bytes
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
        val isCompressed: Boolean = false
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
    suspend fun encrypt(data: String, enableCompression: Boolean = true): Result<EncryptedData> {
        return try {
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            
            val iv = cipher.iv
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            
            // Compress data if it's large enough and compression is enabled
            val shouldCompress = enableCompression && dataBytes.size >= COMPRESSION_THRESHOLD
            val processedData = if (shouldCompress) {
                compressData(dataBytes)
            } else {
                dataBytes
            }
            
            val ciphertext = cipher.doFinal(processedData)
            
            Result.Success(EncryptedData(iv, ciphertext, shouldCompress))
        } catch (e: Exception) {
            Result.Error(e, "Failed to encrypt data: ${e.message}")
        }
    }
    
    /**
     * Decrypts data using the master key.
     * 
     * @param encryptedData The encrypted data to decrypt
     * @return Decrypted string or error result
     */
    suspend fun decrypt(encryptedData: EncryptedData): Result<String> {
        return try {
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            val spec = GCMParameterSpec(TAG_SIZE, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
            
            val decryptedData = cipher.doFinal(encryptedData.ciphertext)
            
            // Decompress if the data was compressed
            val processedData = if (encryptedData.isCompressed) {
                decompressData(decryptedData)
            } else {
                decryptedData
            }
            
            Result.Success(String(processedData, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.Error(e, "Failed to decrypt data: ${e.message}")
        }
    }
    
    /**
     * Encrypts data and returns as byte array.
     * 
     * @param data The data to encrypt
     * @param enableCompression Whether to enable compression
     * @return Encrypted data as byte array or error result
     */
    suspend fun encryptToByteArray(data: String, enableCompression: Boolean = true): Result<ByteArray> {
        return encrypt(data, enableCompression).map { it.toByteArray() }
    }
    
    /**
     * Decrypts data from byte array.
     * 
     * @param data The encrypted data as byte array
     * @return Decrypted string or error result
     */
    suspend fun decryptFromByteArray(data: ByteArray): Result<String> {
        return try {
            val encryptedData = EncryptedData.fromByteArray(data)
            decrypt(encryptedData)
        } catch (e: Exception) {
            Result.Error(e, "Failed to parse encrypted data: ${e.message}")
        }
    }
    
    /**
     * Verifies the integrity of encrypted data.
     * 
     * @param encryptedData The encrypted data to verify
     * @return True if data is valid, false otherwise
     */
    suspend fun verifyDataIntegrity(encryptedData: EncryptedData): Result<Boolean> {
        return try {
            // Attempt to decrypt to verify integrity
            val result = decrypt(encryptedData)
            Result.Success(result.isSuccess)
        } catch (e: Exception) {
            Result.Success(false)
        }
    }
    
    /**
     * Generates a new master key or retrieves existing one.
     * 
     * @return The master key
     */
    private fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        } else {
            generateMasterKey()
        }
    }
    
    /**
     * Generates a new master key in Android Keystore.
     * 
     * @return The generated master key
     */
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(false) // Storage key always accessible
            
            // Use StrongBox if available (hardware security module)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    setIsStrongBoxBacked(true)
                } catch (e: Exception) {
                    // StrongBox not available, use regular TEE
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
    suspend fun deleteMasterKey(): Result<Unit> {
        return try {
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete master key: ${e.message}")
        }
    }
    
    /**
     * Checks if the master key exists.
     * 
     * @return True if master key exists
     */
    suspend fun masterKeyExists(): Result<Boolean> {
        return try {
            Result.Success(keyStore.containsAlias(MASTER_KEY_ALIAS))
        } catch (e: Exception) {
            Result.Error(e, "Failed to check master key existence: ${e.message}")
        }
    }
}