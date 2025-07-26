package com.pocketagent.mobile.data.local

import android.content.Context
import android.util.Log
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
    suspend fun storeJsonData(
        key: String,
        jsonData: String,
    )

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
        val isHealthy: Boolean,
    )
}

/**
 * Implementation of encrypted JSON storage using Android's secure storage mechanisms.
 *
 * This class provides secure storage for JSON data using encryption and the Android Keystore.
 */
@Singleton
class EncryptedJsonStorageImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val storageEncryption: StorageEncryption,
        private val fileStorageManager: FileStorageManager,
        private val storageConfiguration: StorageConfiguration,
        private val backupManager: BackupManager,
        private val storageValidator: StorageValidator,
    ) : EncryptedJsonStorage {
        private val accessMutex = Mutex()
        private val dataChangeNotifiers = mutableMapOf<String, MutableStateFlow<String?>>()

        companion object {
            private const val TAG = "EncryptedJsonStorage"
            private const val FILE_EXTENSION = ".json.enc"
            private const val CLEANUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
            private var lastCleanupTime = 0L
        }

        /**
         * Stores encrypted JSON data with the specified key.
         */
        override suspend fun storeJsonData(
            key: String,
            jsonData: String,
        ) {
            accessMutex.withLock {
                try {
                    validateKey(key)
                    val config = storageConfiguration.getConfiguration().getOrThrow()

                    // Validate and encrypt JSON data
                    val encryptedData = validateAndEncryptJsonData(jsonData, config, key)

                    // Store encrypted data
                    storeEncryptedData(key, encryptedData, jsonData)
                } catch (e: StorageException) {
                    handleStorageException(e, key)
                } catch (e: SecurityException) {
                    handleSecurityException(e, key)
                } catch (e: java.io.IOException) {
                    handleIOException(e, key)
                } catch (e: IllegalArgumentException) {
                    handleIllegalArgumentException(e, key)
                } catch (e: Exception) {
                    handleGenericException(e, key)
                }
            }
        }

        /**
         * Validates and encrypts JSON data.
         */
        private suspend fun validateAndEncryptJsonData(
            jsonData: String,
            config: StorageConfiguration.Configuration,
            key: String,
        ): ByteArray {
            // Validate JSON data
            val validationResult = storageValidator.validateJsonData(jsonData)
            if (!validationResult.isSuccess) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.INVALID_JSON_FORMAT,
                    message = "JSON validation failed",
                    details = (validationResult as Result.Error).message,
                )
            }

            val validation = validationResult.getOrThrow()
            if (!validation.isValid) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.INVALID_JSON_FORMAT,
                    message = validation.message,
                    details = validation.details,
                )
            }

            // Encrypt data
            val encryptResult =
                storageEncryption.encryptToByteArray(
                    data = jsonData,
                    enableCompression = config.compressionEnabled,
                )

            if (!encryptResult.isSuccess) {
                throw StorageExceptionFactory.encryptionFailed(
                    message = "Failed to encrypt JSON data",
                    cause = (encryptResult as Result.Error).exception,
                    context = mapOf("key" to key),
                )
            }

            return encryptResult.getOrThrow()
        }

        /**
         * Stores encrypted data to file system.
         */
        private suspend fun storeEncryptedData(
            key: String,
            encryptedData: ByteArray,
            originalJsonData: String,
        ) {
            // Check storage quota
            checkStorageQuota(encryptedData.size.toLong())

            // Store encrypted data
            val filename = keyToFilename(key)
            val storeResult = fileStorageManager.writeFileAtomic(filename, encryptedData)

            if (!storeResult.isSuccess) {
                throw StorageExceptionFactory.fileOperationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_WRITE_FAILED,
                    message = "Failed to write encrypted file",
                    filename = filename,
                    cause = (storeResult as Result.Error).exception,
                )
            }

            // Notify observers and perform maintenance
            notifyDataChange(key, originalJsonData)
            performMaintenanceIfNeeded()
        }

        /**
         * Handles storage exceptions during store operations.
         */
        private fun handleStorageException(
            e: StorageException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Storage exception during store operation for key: $key", e)
            throw e
        }

        /**
         * Handles security exceptions during store operations.
         */
        private fun handleSecurityException(
            e: SecurityException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Security exception during store operation for key: $key", e)
            throw StorageExceptionFactory.accessDenied(
                message = "Security error during store operation",
                resource = keyToFilename(key),
            )
        }

        /**
         * Handles IO exceptions during store operations.
         */
        private fun handleIOException(
            e: java.io.IOException,
            key: String,
        ): Nothing {
            Log.e(TAG, "IO exception during store operation for key: $key", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_WRITE_FAILED,
                message = "IO error during store operation",
                filename = keyToFilename(key),
                cause = e,
            )
        }

        /**
         * Handles illegal argument exceptions during store operations.
         */
        private fun handleIllegalArgumentException(
            e: IllegalArgumentException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Invalid argument during store operation for key: $key", e)
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Invalid argument during store operation",
                cause = e,
            )
        }

        /**
         * Handles generic exceptions during store operations.
         */
        private fun handleGenericException(
            e: Exception,
            key: String,
        ): Nothing {
            Log.e(TAG, "Unexpected error during store operation for key: $key", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_WRITE_FAILED,
                message = "Unexpected error during store operation",
                filename = keyToFilename(key),
                cause = e,
            )
        }

        /**
         * Retrieves encrypted JSON data by key.
         */
        override suspend fun getJsonData(key: String): String? {
            return accessMutex.withLock {
                try {
                    validateKey(key)
                    val filename = keyToFilename(key)

                    if (!fileExists(filename)) {
                        return@withLock null
                    }

                    val encryptedData = readEncryptedFile(filename)
                    val jsonData = decryptJsonData(encryptedData, key)
                    validateDecryptedJson(jsonData)

                    return@withLock jsonData
                } catch (e: StorageException) {
                    handleRetrieveStorageException(e, key)
                } catch (e: SecurityException) {
                    handleRetrieveSecurityException(e, key)
                } catch (e: java.io.IOException) {
                    handleRetrieveIOException(e, key)
                } catch (e: IllegalArgumentException) {
                    handleRetrieveArgumentException(e, key)
                } catch (e: Exception) {
                    handleRetrieveGenericException(e, key)
                }
            }
        }

        /**
         * Checks if file exists for retrieval.
         */
        private suspend fun fileExists(filename: String): Boolean {
            val existsResult = fileStorageManager.fileExists(filename)
            return existsResult.isSuccess && existsResult.getOrThrow()
        }

        /**
         * Reads encrypted file data.
         */
        private suspend fun readEncryptedFile(filename: String): ByteArray {
            val readResult = fileStorageManager.readFile(filename, verifyChecksum = true)
            if (!readResult.isSuccess) {
                throw StorageExceptionFactory.fileOperationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                    message = "Failed to read encrypted file",
                    filename = filename,
                    cause = (readResult as Result.Error).exception,
                )
            }
            return readResult.getOrThrow()
        }

        /**
         * Decrypts JSON data from encrypted bytes.
         */
        private suspend fun decryptJsonData(
            encryptedData: ByteArray,
            key: String,
        ): String {
            val decryptResult = storageEncryption.decryptFromByteArray(encryptedData)
            if (!decryptResult.isSuccess) {
                throw StorageExceptionFactory.decryptionFailed(
                    message = "Failed to decrypt JSON data",
                    cause = (decryptResult as Result.Error).exception,
                    context = mapOf("key" to key),
                )
            }
            return decryptResult.getOrThrow()
        }

        /**
         * Validates decrypted JSON data.
         */
        private suspend fun validateDecryptedJson(jsonData: String) {
            val validationResult = storageValidator.validateJsonData(jsonData)
            if (!validationResult.isSuccess) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                    message = "Decrypted data validation failed",
                    details = (validationResult as Result.Error).message,
                )
            }
        }

        /**
         * Handles storage exceptions during retrieve operation.
         */
        private fun handleRetrieveStorageException(
            e: StorageException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Storage exception during retrieve operation for key: $key", e)
            throw e
        }

        /**
         * Handles security exceptions during retrieve operation.
         */
        private fun handleRetrieveSecurityException(
            e: SecurityException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Security exception during retrieve operation for key: $key", e)
            throw StorageExceptionFactory.accessDenied(
                message = "Security error during retrieve operation",
                resource = keyToFilename(key),
            )
        }

        /**
         * Handles IO exceptions during retrieve operation.
         */
        private fun handleRetrieveIOException(
            e: java.io.IOException,
            key: String,
        ): Nothing {
            Log.e(TAG, "IO exception during retrieve operation for key: $key", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                message = "IO error during retrieve operation",
                filename = keyToFilename(key),
                cause = e,
            )
        }

        /**
         * Handles argument exceptions during retrieve operation.
         */
        private fun handleRetrieveArgumentException(
            e: IllegalArgumentException,
            key: String,
        ): Nothing {
            Log.e(TAG, "Invalid argument during retrieve operation for key: $key", e)
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Invalid argument during retrieve operation",
                cause = e,
            )
        }

        /**
         * Handles generic exceptions during retrieve operation.
         */
        private fun handleRetrieveGenericException(
            e: Exception,
            key: String,
        ): Nothing {
            Log.e(TAG, "Unexpected error during retrieve operation for key: $key", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                message = "Unexpected error during retrieve operation",
                filename = keyToFilename(key),
                cause = e,
            )
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
                            cause = (deleteResult as Result.Error).exception,
                        )
                    }

                    // Notify observers
                    notifyDataChange(key, null)
                } catch (e: StorageException) {
                    Log.e(TAG, "Storage exception during delete operation for key: $key", e)
                    throw e
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception during delete operation for key: $key", e)
                    throw StorageExceptionFactory.accessDenied(
                        message = "Security error during delete operation",
                        resource = keyToFilename(key),
                    )
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "IO exception during delete operation for key: $key", e)
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "IO error during delete operation",
                        filename = keyToFilename(key),
                        cause = e,
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid argument during delete operation for key: $key", e)
                    throw StorageExceptionFactory.validationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                        message = "Invalid argument during delete operation",
                        cause = e,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during delete operation for key: $key", e)
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "Unexpected error during delete operation",
                        filename = keyToFilename(key),
                        cause = e,
                    )
                }
            }
        }

        /**
         * Clears all encrypted JSON data.
         */
        override suspend fun clearAllData() {
            accessMutex.withLock {
                try {
                    val encryptedFiles = getEncryptedFilesForClearing()
                    deleteAllEncryptedFiles(encryptedFiles)
                    notifyAllObserversOfClear()
                } catch (e: StorageException) {
                    handleClearOperationException(e)
                } catch (e: SecurityException) {
                    handleClearSecurityException(e)
                } catch (e: java.io.IOException) {
                    handleClearIOException(e)
                } catch (e: IllegalArgumentException) {
                    handleClearArgumentException(e)
                } catch (e: Exception) {
                    handleClearGenericException(e)
                }
            }
        }

        /**
         * Gets list of encrypted files for clearing operation.
         */
        private suspend fun getEncryptedFilesForClearing(): List<String> {
            val filesResult = fileStorageManager.listFiles()
            if (!filesResult.isSuccess) {
                throw StorageExceptionFactory.fileOperationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_READ_FAILED,
                    message = "Failed to list files for clearing",
                    filename = "all",
                    cause = (filesResult as Result.Error).exception,
                )
            }

            val files = filesResult.getOrThrow()
            return files.filter { it.endsWith(FILE_EXTENSION) }
        }

        /**
         * Deletes all encrypted files.
         */
        private suspend fun deleteAllEncryptedFiles(encryptedFiles: List<String>) {
            for (filename in encryptedFiles) {
                val deleteResult = fileStorageManager.deleteFile(filename)
                if (!deleteResult.isSuccess) {
                    throw StorageExceptionFactory.fileOperationFailed(
                        errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                        message = "Failed to delete file during clear operation",
                        filename = filename,
                        cause = (deleteResult as Result.Error).exception,
                    )
                }
            }
        }

        /**
         * Notifies all observers of the clear operation.
         */
        private fun notifyAllObserversOfClear() {
            dataChangeNotifiers.keys.forEach { key ->
                notifyDataChange(key, null)
            }
        }

        /**
         * Handles storage exceptions during clear operation.
         */
        private fun handleClearOperationException(e: StorageException): Nothing {
            Log.e(TAG, "Storage exception during clear operation", e)
            throw e
        }

        /**
         * Handles security exceptions during clear operation.
         */
        private fun handleClearSecurityException(e: SecurityException): Nothing {
            Log.e(TAG, "Security exception during clear operation", e)
            throw StorageExceptionFactory.accessDenied(
                message = "Security error during clear operation",
                resource = "all",
            )
        }

        /**
         * Handles IO exceptions during clear operation.
         */
        private fun handleClearIOException(e: java.io.IOException): Nothing {
            Log.e(TAG, "IO exception during clear operation", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                message = "IO error during clear operation",
                filename = "all",
                cause = e,
            )
        }

        /**
         * Handles argument exceptions during clear operation.
         */
        private fun handleClearArgumentException(e: IllegalArgumentException): Nothing {
            Log.e(TAG, "Invalid argument during clear operation", e)
            throw StorageExceptionFactory.validationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                message = "Invalid argument during clear operation",
                cause = e,
            )
        }

        /**
         * Handles generic exceptions during clear operation.
         */
        private fun handleClearGenericException(e: Exception): Nothing {
            Log.e(TAG, "Unexpected error during clear operation", e)
            throw StorageExceptionFactory.fileOperationFailed(
                errorCode = com.pocketagent.data.storage.StorageErrorCode.FILE_DELETE_FAILED,
                message = "Unexpected error during clear operation",
                filename = "all",
                cause = e,
            )
        }

        /**
         * Checks if data exists for the specified key.
         */
        override suspend fun hasData(key: String): Boolean =
            try {
                validateKey(key)

                val filename = keyToFilename(key)
                val existsResult = fileStorageManager.fileExists(filename)
                existsResult.getOrNull() ?: false
            } catch (e: StorageException) {
                Log.w(TAG, "Storage exception during hasData check for key: $key", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception during hasData check for key: $key", e)
                false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid argument during hasData check for key: $key", e)
                false
            } catch (e: java.io.IOException) {
                Log.w(TAG, "IO exception during hasData check for key: $key", e)
                false
            }

        /**
         * Observes changes to a specific key.
         */
        override fun observeJsonData(key: String): Flow<String?> =
            getOrCreateNotifier(key).map { data ->
                if (data != null) {
                    data
                } else {
                    // Load current data if not cached
                    try {
                        getJsonData(key)
                    } catch (e: StorageException) {
                        Log.w(TAG, "Storage exception during data loading for key: $key", e)
                        null
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Security exception during data loading for key: $key", e)
                        null
                    } catch (e: java.io.IOException) {
                        Log.w(TAG, "IO exception during data loading for key: $key", e)
                        null
                    }
                }
            }

        /**
         * Creates a backup of all stored data.
         */
        override suspend fun createBackup(): String? =
            try {
                val backupResult = backupManager.createBackup()
                backupResult.getOrNull()
            } catch (e: StorageException) {
                Log.e(TAG, "Storage exception during backup creation", e)
                null
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during backup creation", e)
                null
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO exception during backup creation", e)
                null
            }

        /**
         * Restores data from a backup.
         */
        override suspend fun restoreBackup(backupFilename: String): Boolean =
            try {
                val restoreResult = backupManager.restoreBackup(backupFilename)
                restoreResult.isSuccess
            } catch (e: StorageException) {
                Log.e(TAG, "Storage exception during backup restoration", e)
                false
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during backup restoration", e)
                false
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO exception during backup restoration", e)
                false
            }

        /**
         * Validates the integrity of stored data.
         */
        override suspend fun validateStorage(): StorageValidator.ValidationReport =
            try {
                val validationResult = storageValidator.validateAllStorageFiles()
                validationResult.getOrThrow()
            } catch (e: StorageException) {
                Log.e(TAG, "Storage exception during storage validation", e)
                StorageValidator.ValidationReport(
                    isValid = false,
                    results =
                        listOf(
                            StorageValidator.ValidationResult(
                                isValid = false,
                                severity = StorageValidator.ValidationSeverity.ERROR,
                                message = "Storage validation failed: ${e.message}",
                                details = e.cause?.message,
                            ),
                        ),
                    checkedFiles = 0,
                    totalSize = 0,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during storage validation", e)
                StorageValidator.ValidationReport(
                    isValid = false,
                    results =
                        listOf(
                            StorageValidator.ValidationResult(
                                isValid = false,
                                severity = StorageValidator.ValidationSeverity.ERROR,
                                message = "Security error during storage validation: ${e.message}",
                                details = e.cause?.message,
                            ),
                        ),
                    checkedFiles = 0,
                    totalSize = 0,
                )
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO exception during storage validation", e)
                StorageValidator.ValidationReport(
                    isValid = false,
                    results =
                        listOf(
                            StorageValidator.ValidationResult(
                                isValid = false,
                                severity = StorageValidator.ValidationSeverity.ERROR,
                                message = "IO error during storage validation: ${e.message}",
                                details = e.cause?.message,
                            ),
                        ),
                    checkedFiles = 0,
                    totalSize = 0,
                )
            }

        /**
         * Gets storage statistics.
         */
        override suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats =
            try {
                collectStorageStats()
            } catch (e: StorageException) {
                Log.e(TAG, "Storage exception during getStorageStats", e)
                createEmptyStorageStats()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during getStorageStats", e)
                createEmptyStorageStats()
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO exception during getStorageStats", e)
                createEmptyStorageStats()
            }

        /**
         * Collects storage statistics from various sources.
         */
        private suspend fun collectStorageStats(): EncryptedJsonStorage.StorageStats {
            val encryptedFiles = getEncryptedFilesList()
            val sizes = collectStorageAndBackupSizes()
            val backupInfo = collectBackupInfo()
            val validationReport = validateStorage()
            val lastModified = findLastModifiedTime(encryptedFiles)

            return EncryptedJsonStorage.StorageStats(
                totalFiles = encryptedFiles.size,
                totalSize = sizes.storageSize,
                lastModified = lastModified,
                backupCount = backupInfo.count,
                backupSize = backupInfo.size,
                isHealthy = validationReport.isValid,
            )
        }

        /**
         * Gets list of encrypted files.
         */
        private suspend fun getEncryptedFilesList(): List<String> {
            val filesResult = fileStorageManager.listFiles()
            val files = filesResult.getOrNull() ?: emptyList()
            return files.filter { it.endsWith(FILE_EXTENSION) }
        }

        /**
         * Data class for storage sizes.
         */
        private data class StorageSizes(
            val storageSize: Long,
            val backupSize: Long,
        )

        /**
         * Collects storage and backup sizes.
         */
        private suspend fun collectStorageAndBackupSizes(): StorageSizes {
            val totalSizeResult = fileStorageManager.getStorageSize()
            val totalSize = totalSizeResult.getOrNull() ?: 0L

            val backupSizeResult = backupManager.getBackupSize()
            val backupSize = backupSizeResult.getOrNull() ?: 0L

            return StorageSizes(totalSize, backupSize)
        }

        /**
         * Data class for backup information.
         */
        private data class BackupInfo(
            val count: Int,
            val size: Long,
        )

        /**
         * Collects backup information.
         */
        private suspend fun collectBackupInfo(): BackupInfo {
            val backupListResult = backupManager.listBackups()
            val backupCount = backupListResult.getOrNull()?.size ?: 0

            val backupSizeResult = backupManager.getBackupSize()
            val backupSize = backupSizeResult.getOrNull() ?: 0L

            return BackupInfo(backupCount, backupSize)
        }

        /**
         * Finds the last modified time among encrypted files.
         */
        private suspend fun findLastModifiedTime(encryptedFiles: List<String>): Long {
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
            return lastModified
        }

        /**
         * Creates empty storage stats for error cases.
         */
        private fun createEmptyStorageStats(): EncryptedJsonStorage.StorageStats {
            return EncryptedJsonStorage.StorageStats(
                totalFiles = 0,
                totalSize = 0,
                lastModified = 0,
                backupCount = 0,
                backupSize = 0,
                isHealthy = false,
            )
        }

        /**
         * Validates a storage key.
         */
        private fun validateKey(key: String) {
            if (key.isBlank()) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                    message = "Storage key cannot be blank",
                )
            }

            if (key.length > 100) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                    message = "Storage key too long (max 100 characters)",
                )
            }

            if (!key.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                throw StorageExceptionFactory.validationFailed(
                    errorCode = com.pocketagent.data.storage.StorageErrorCode.STRUCTURE_INVALID,
                    message = "Storage key contains invalid characters",
                )
            }
        }

        /**
         * Converts a key to a filename.
         */
        private fun keyToFilename(key: String): String = "$key$FILE_EXTENSION"

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
                        maxSize = config.maxStorageSize,
                    )
                }
            }
        }

        /**
         * Gets or creates a data change notifier for a key.
         */
        private fun getOrCreateNotifier(key: String): MutableStateFlow<String?> =
            dataChangeNotifiers.getOrPut(key) { MutableStateFlow(null) }

        /**
         * Notifies observers of data changes.
         */
        private fun notifyDataChange(
            key: String,
            data: String?,
        ) {
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
                } catch (e: StorageException) {
                    Log.w(TAG, "Storage exception during maintenance cleanup", e)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Security exception during maintenance cleanup", e)
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "IO exception during maintenance cleanup", e)
                }
            }
        }
    }
