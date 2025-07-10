package com.pocketagent.mobile.data.local

import android.content.Context
import com.pocketagent.data.storage.BackupManager
import com.pocketagent.data.storage.FileStorageManager
import com.pocketagent.data.storage.StorageConfiguration
import com.pocketagent.data.storage.StorageEncryption
import com.pocketagent.data.storage.StorageExceptionFactory
import com.pocketagent.data.storage.StorageValidator
import com.pocketagent.domain.models.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for encrypted JSON storage operations.
 * 
 * This interface defines the contract for storing and retrieving encrypted JSON data
 * using the device's secure storage mechanisms.
 */
interface EncryptedJsonStorage {
    
    /**
     * Stores encrypted JSON data with the specified key.
     * 
     * @param key The storage key
     * @param jsonData The JSON data to store
     */
    suspend fun storeJsonData(key: String, jsonData: String)
    
    /**
     * Retrieves encrypted JSON data by key.
     * 
     * @param key The storage key
     * @return The decrypted JSON data or null if not found
     */
    suspend fun getJsonData(key: String): String?
    
    /**
     * Deletes encrypted JSON data by key.
     * 
     * @param key The storage key
     */
    suspend fun deleteJsonData(key: String)
    
    /**
     * Clears all encrypted JSON data.
     */
    suspend fun clearAllData()
    
    /**
     * Checks if data exists for the specified key.
     * 
     * @param key The storage key
     * @return True if data exists, false otherwise
     */
    suspend fun hasData(key: String): Boolean
    
    /**
     * Observes changes to a specific key.
     * 
     * @param key The storage key to observe
     * @return Flow of JSON data changes
     */
    fun observeJsonData(key: String): Flow<String?>
    
    /**
     * Creates a backup of all stored data.
     * 
     * @return Backup filename or null if failed
     */
    suspend fun createBackup(): String?
    
    /**
     * Restores data from a backup.
     * 
     * @param backupFilename The backup file to restore from
     * @return True if restoration was successful
     */
    suspend fun restoreBackup(backupFilename: String): Boolean
    
    /**
     * Validates the integrity of stored data.
     * 
     * @return Validation report
     */
    suspend fun validateStorage(): StorageValidator.ValidationReport
    
    /**
     * Gets storage statistics.
     * 
     * @return Storage statistics
     */
    suspend fun getStorageStats(): StorageStats
    
    /**
     * Data class for storage statistics.
     */
    data class StorageStats(
        val totalFiles: Int,
        val totalSize: Long,
        val lastModified: Long,
        val backupCount: Int,
        val backupSize: Long,
        val isHealthy: Boolean
    )
}

/**
 * Implementation of encrypted JSON storage using Android's secure storage mechanisms.
 * 
 * This class provides secure storage for JSON data using encryption and the Android Keystore.
 */
@Singleton
class EncryptedJsonStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageEncryption: StorageEncryption,
    private val fileStorageManager: FileStorageManager,
    private val storageConfiguration: StorageConfiguration,
    private val backupManager: BackupManager,
    private val storageValidator: StorageValidator
) : EncryptedJsonStorage {
    
    private val accessMutex = Mutex()
    private val dataChangeNotifiers = mutableMapOf<String, MutableStateFlow<String?>>()
    
    companion object {
        private const val FILE_EXTENSION = ".json.enc"
        private const val CLEANUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
        private var lastCleanupTime = 0L
    }
    
    /**
     * Stores encrypted JSON data with the specified key.
     */
    override suspend fun storeJsonData(key: String, jsonData: String) {
        accessMutex.withLock {
            try {
                validateKey(key)
                
                // Get configuration
                val config = storageConfiguration.getConfiguration().getOrThrow()
                
                // Validate JSON data
                val validationResult = storageValidator.validateJsonData(jsonData)
                if (!validationResult.isSuccess) {
                    throw StorageExceptionFactory.validationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.INVALID_JSON_FORMAT,
                        message = "JSON validation failed",
                        details = (validationResult as Result.Error).message
                    )
                }
                
                val validation = validationResult.getOrThrow()
                if (!validation.isValid) {
                    throw StorageExceptionFactory.validationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.INVALID_JSON_FORMAT,
                        message = validation.message,
                        details = validation.details
                    )
                }
                
                // Encrypt data
                val encryptResult = storageEncryption.encryptToByteArray(
                    data = jsonData,
                    enableCompression = config.compressionEnabled
                )
                
                if (!encryptResult.isSuccess) {
                    throw StorageExceptionFactory.encryptionFailed(
                        message = "Failed to encrypt JSON data",
                        cause = (encryptResult as Result.Error).exception,
                        context = mapOf("key" to key)
                    )
                }
                
                val encryptedData = encryptResult.getOrThrow()
                
                // Check storage quota
                checkStorageQuota(encryptedData.size)
                
                // Store encrypted data
                val filename = keyToFilename(key)
                val storeResult = fileStorageManager.writeFileAtomic(filename, encryptedData)
                
                if (!storeResult.isSuccess) {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_WRITE_FAILED,
                        message = "Failed to write encrypted file",
                        filename = filename,
                        cause = (storeResult as Result.Error).exception
                    )
                }
                
                // Notify observers
                notifyDataChange(key, jsonData)
                
                // Perform maintenance if needed
                performMaintenanceIfNeeded()
                
            } catch (e: Exception) {
                if (e is com.pocketagent.data.storage.DetailedStorageException) {
                    throw e
                } else {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_WRITE_FAILED,
                        message = "Unexpected error during store operation",
                        filename = keyToFilename(key),
                        cause = e
                    )
                }
            }
        }
    }
    
    /**
     * Retrieves encrypted JSON data by key.
     */
    override suspend fun getJsonData(key: String): String? {
        return accessMutex.withLock {
            try {
                validateKey(key)
                
                val filename = keyToFilename(key)
                
                // Check if file exists
                val existsResult = fileStorageManager.fileExists(filename)
                if (!existsResult.isSuccess || !existsResult.getOrThrow()) {
                    return@withLock null
                }
                
                // Read encrypted data
                val readResult = fileStorageManager.readFile(filename, verifyChecksum = true)
                if (!readResult.isSuccess) {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                        message = "Failed to read encrypted file",
                        filename = filename,
                        cause = (readResult as Result.Error).exception
                    )
                }
                
                val encryptedData = readResult.getOrThrow()
                
                // Decrypt data
                val decryptResult = storageEncryption.decryptFromByteArray(encryptedData)
                if (!decryptResult.isSuccess) {
                    throw StorageExceptionFactory.decryptionFailed(
                        message = "Failed to decrypt JSON data",
                        cause = (decryptResult as Result.Error).exception,
                        context = mapOf("key" to key)
                    )
                }
                
                val jsonData = decryptResult.getOrThrow()
                
                // Validate decrypted JSON
                val validationResult = storageValidator.validateJsonData(jsonData)
                if (!validationResult.isSuccess) {
                    throw StorageExceptionFactory.validationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                        message = "Decrypted data validation failed",
                        details = (validationResult as Result.Error).message
                    )
                }
                
                return@withLock jsonData
                
            } catch (e: Exception) {
                if (e is com.pocketagent.data.storage.DetailedStorageException) {
                    throw e
                } else {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                        message = "Unexpected error during retrieve operation",
                        filename = keyToFilename(key),
                        cause = e
                    )
                }
            }
        }
    }
    
    /**
     * Deletes encrypted JSON data by key.
     */
    override suspend fun deleteJsonData(key: String) {
        accessMutex.withLock {
            try {
                validateKey(key)
                
                val filename = keyToFilename(key)
                val deleteResult = fileStorageManager.deleteFile(filename)
                
                if (!deleteResult.isSuccess) {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "Failed to delete encrypted file",
                        filename = filename,
                        cause = (deleteResult as Result.Error).exception
                    )
                }
                
                // Notify observers
                notifyDataChange(key, null)
                
            } catch (e: Exception) {
                if (e is com.pocketagent.data.storage.DetailedStorageException) {
                    throw e
                } else {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "Unexpected error during delete operation",
                        filename = keyToFilename(key),
                        cause = e
                    )
                }
            }
        }
    }
    
    /**
     * Clears all encrypted JSON data.
     */
    override suspend fun clearAllData() {
        accessMutex.withLock {
            try {
                val filesResult = fileStorageManager.listFiles()
                if (!filesResult.isSuccess) {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                        message = "Failed to list files for clearing",
                        filename = "all",
                        cause = (filesResult as Result.Error).exception
                    )
                }
                
                val files = filesResult.getOrThrow()
                val encryptedFiles = files.filter { it.endsWith(FILE_EXTENSION) }
                
                for (filename in encryptedFiles) {
                    val deleteResult = fileStorageManager.deleteFile(filename)
                    if (!deleteResult.isSuccess) {
                        throw StorageExceptionFactory.fileOperationFailed(
                            errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                            message = "Failed to delete file during clear operation",
                            filename = filename,
                            cause = (deleteResult as Result.Error).exception
                        )
                    }
                }
                
                // Notify all observers
                dataChangeNotifiers.keys.forEach { key ->
                    notifyDataChange(key, null)
                }
                
            } catch (e: Exception) {
                if (e is com.pocketagent.data.storage.DetailedStorageException) {
                    throw e
                } else {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "Unexpected error during clear operation",
                        filename = "all",
                        cause = e
                    )
                }
            }
        }
    }
    
    /**
     * Checks if data exists for the specified key.
     */
    override suspend fun hasData(key: String): Boolean {
        return try {
            validateKey(key)
            
            val filename = keyToFilename(key)
            val existsResult = fileStorageManager.fileExists(filename)
            existsResult.getOrNull() ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Observes changes to a specific key.
     */
    override fun observeJsonData(key: String): Flow<String?> {
        return getOrCreateNotifier(key).map { data ->
            if (data != null) {
                data
            } else {
                // Load current data if not cached
                try {
                    runCatching { getJsonData(key) }.getOrNull()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Creates a backup of all stored data.
     */
    override suspend fun createBackup(): String? {
        return try {
            val backupResult = backupManager.createBackup()
            backupResult.getOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Restores data from a backup.
     */
    override suspend fun restoreBackup(backupFilename: String): Boolean {
        return try {
            val restoreResult = backupManager.restoreBackup(backupFilename)
            restoreResult.isSuccess
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates the integrity of stored data.
     */
    override suspend fun validateStorage(): StorageValidator.ValidationReport {
        return try {
            val validationResult = storageValidator.validateAllStorageFiles()
            validationResult.getOrThrow()
        } catch (e: Exception) {
            StorageValidator.ValidationReport(
                isValid = false,
                results = listOf(
                    StorageValidator.ValidationResult(
                        isValid = false,
                        severity = StorageValidator.ValidationSeverity.ERROR,
                        message = "Storage validation failed",
                        details = e.message
                    )
                ),
                checkedFiles = 0,
                totalSize = 0
            )
        }
    }
    
    /**
     * Gets storage statistics.
     */
    override suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats {
        return try {
            val filesResult = fileStorageManager.listFiles()
            val files = filesResult.getOrNull() ?: emptyList()
            val encryptedFiles = files.filter { it.endsWith(FILE_EXTENSION) }
            
            val totalSizeResult = fileStorageManager.getStorageSize()
            val totalSize = totalSizeResult.getOrNull() ?: 0L
            
            val backupSizeResult = backupManager.getBackupSize()
            val backupSize = backupSizeResult.getOrNull() ?: 0L
            
            val backupListResult = backupManager.listBackups()
            val backupCount = backupListResult.getOrNull()?.size ?: 0
            
            val validationReport = validateStorage()
            
            // Find last modified time
            var lastModified = 0L
            for (filename in encryptedFiles) {
                val metadataResult = fileStorageManager.getFileMetadata(filename)
                if (metadataResult.isSuccess) {
                    val metadata = metadataResult.getOrThrow()
                    if (metadata.lastModified > lastModified) {
                        lastModified = metadata.lastModified
                    }
                }
            }
            
            EncryptedJsonStorage.StorageStats(
                totalFiles = encryptedFiles.size,
                totalSize = totalSize,
                lastModified = lastModified,
                backupCount = backupCount,
                backupSize = backupSize,
                isHealthy = validationReport.isValid
            )
        } catch (e: Exception) {
            EncryptedJsonStorage.StorageStats(
                totalFiles = 0,
                totalSize = 0,
                lastModified = 0,
                backupCount = 0,
                backupSize = 0,
                isHealthy = false
            )
        }
    }
    
    /**
     * Validates a storage key.
     */
    private fun validateKey(key: String) {
        if (key.isBlank()) {
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Storage key cannot be blank"
            )
        }
        
        if (key.length > 100) {
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Storage key too long (max 100 characters)"
            )
        }
        
        if (!key.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Storage key contains invalid characters"
            )
        }
    }
    
    /**
     * Converts a key to a filename.
     */
    private fun keyToFilename(key: String): String {
        return "$key$FILE_EXTENSION"
    }
    
    /**
     * Checks storage quota.
     */
    private suspend fun checkStorageQuota(additionalSize: Long) {
        val config = storageConfiguration.getConfiguration().getOrThrow()
        val currentSizeResult = fileStorageManager.getStorageSize()
        
        if (currentSizeResult.isSuccess) {
            val currentSize = currentSizeResult.getOrThrow()
            val totalSize = currentSize + additionalSize
            
            if (totalSize > config.maxStorageSize) {
                throw StorageExceptionFactory.quotaExceeded(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.STORAGE_QUOTA_EXCEEDED,
                    message = "Storage quota would be exceeded",
                    currentSize = totalSize,
                    maxSize = config.maxStorageSize
                )
            }
        }
    }
    
    /**
     * Gets or creates a data change notifier for a key.
     */
    private fun getOrCreateNotifier(key: String): MutableStateFlow<String?> {
        return dataChangeNotifiers.getOrPut(key) { MutableStateFlow(null) }
    }
    
    /**
     * Notifies observers of data changes.
     */
    private fun notifyDataChange(key: String, data: String?) {
        dataChangeNotifiers[key]?.value = data
    }
    
    /**
     * Performs maintenance tasks if needed.
     */
    private suspend fun performMaintenanceIfNeeded() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastCleanupTime >= CLEANUP_INTERVAL) {
            try {
                // Cleanup old backups
                backupManager.cleanupOldBackups()
                
                // Update cleanup time
                storageConfiguration.updateLastCleanupTime(currentTime)
                
                lastCleanupTime = currentTime
            } catch (e: Exception) {
                // Log error but don't fail the main operation
            }
        }
    }
}