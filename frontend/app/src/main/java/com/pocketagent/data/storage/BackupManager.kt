package com.pocketagent.data.storage

import android.content.Context
import com.pocketagent.domain.models.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for creating and restoring backups of encrypted storage data.
 *
 * This class provides backup and recovery functionality for the encrypted storage system,
 * including automatic backups, backup validation, and restoration capabilities.
 */
@Singleton
class BackupManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fileStorageManager: FileStorageManager,
        private val storageConfiguration: StorageConfiguration,
        private val storageEncryption: StorageEncryption,
    ) {
        companion object {
            private const val BACKUP_DIR = "backups"
            private const val BACKUP_EXTENSION = ".pab" // Pocket Agent Backup
            private const val METADATA_FILE = "backup_metadata.json"
            private const val DATA_DIR = "data"
            private const val BACKUP_VERSION = 1
            private const val DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
            private const val MAX_BACKUP_AGE_DAYS = 30
        }

        private val backupDir: File by lazy {
            File(context.filesDir, BACKUP_DIR).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }

        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

        /**
         * Data class representing backup metadata.
         */
        @Serializable
        data class BackupMetadata(
            val version: Int,
            val timestamp: Long,
            val deviceId: String,
            val appVersion: String,
            val storageVersion: Int,
            val fileCount: Int,
            val totalSize: Long,
            val checksum: String,
            val isEncrypted: Boolean,
        )

        /**
         * Data class representing backup information.
         */
        data class BackupInfo(
            val filename: String,
            val metadata: BackupMetadata,
            val size: Long,
            val isValid: Boolean,
        )

        /**
         * Creates a backup of all storage data.
         *
         * @param includeMetadata Whether to include metadata in the backup
         * @return Backup file path or error result
         */
        suspend fun createBackup(includeMetadata: Boolean = true): Result<String> {
            return withContext(Dispatchers.IO) {
                try {
                    val (config, files) = validateAndPrepareBackupData()
                    val backupInfo = createBackupInfo()
                    val tempDir = createTemporaryBackupDirectory(backupInfo.timestamp)

                    try {
                        val totalSize = copyFilesToBackupDirectory(files, tempDir)
                        val metadata = createBackupMetadata(
                            config = config,
                            files = files,
                            totalSize = totalSize,
                            timestamp = backupInfo.timestamp,
                        )

                        if (includeMetadata) {
                            writeBackupMetadata(metadata, tempDir)
                        }

                        createZipArchive(tempDir, backupInfo.backupFile)
                        cleanupOldBackups()

                        Result.Success(backupInfo.filename)
                    } finally {
                        tempDir.deleteRecursively()
                    }
                } catch (e: StorageException) {
                    handleBackupCreationException(e, "Storage")
                } catch (e: SecurityException) {
                    handleBackupCreationException(e, "Security")
                } catch (e: java.io.IOException) {
                    handleBackupCreationException(e, "IO")
                } catch (e: IllegalArgumentException) {
                    handleBackupCreationException(e, "Invalid argument")
                } catch (e: IllegalStateException) {
                    handleBackupCreationException(e, "Invalid state")
                }
            }
        }

        /**
         * Data class for backup information.
         */
        private data class BackupCreationInfo(
            val filename: String,
            val backupFile: File,
            val timestamp: Long,
        )

        /**
         * Validates and prepares backup data.
         */
        private suspend fun validateAndPrepareBackupData(): Pair<StorageConfiguration.Configuration, List<String>> {
            val config = storageConfiguration.getConfiguration().getOrThrow()
            val files = fileStorageManager.listFiles().getOrThrow()

            check(files.isNotEmpty()) { "No files to backup" }

            return Pair(config, files)
        }

        /**
         * Creates backup information.
         */
        private fun createBackupInfo(): BackupCreationInfo {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val backupFilename = "backup_${dateFormat.format(Date(timestamp))}$BACKUP_EXTENSION"
            val backupFile = File(backupDir, backupFilename)

            return BackupCreationInfo(backupFilename, backupFile, timestamp)
        }

        /**
         * Creates temporary directory for backup preparation.
         */
        private fun createTemporaryBackupDirectory(timestamp: Long): File {
            val tempDir = File(context.cacheDir, "backup_temp_$timestamp")
            tempDir.mkdirs()
            return tempDir
        }

        /**
         * Copies files to backup directory.
         */
        private suspend fun copyFilesToBackupDirectory(
            files: List<String>,
            tempDir: File,
        ): Long {
            val dataDir = File(tempDir, DATA_DIR)
            dataDir.mkdirs()

            var totalSize = 0L
            for (filename in files) {
                val fileData = fileStorageManager.readFile(filename).getOrThrow()
                val destFile = File(dataDir, filename)
                destFile.writeBytes(fileData)
                totalSize += fileData.size
            }
            return totalSize
        }

        /**
         * Creates backup metadata.
         */
        private suspend fun createBackupMetadata(
            config: StorageConfiguration.Configuration,
            files: List<String>,
            totalSize: Long,
            timestamp: Long,
        ): BackupMetadata {
            return BackupMetadata(
                version = BACKUP_VERSION,
                timestamp = timestamp,
                deviceId = getDeviceId(),
                appVersion = getAppVersion(),
                storageVersion = config.storageVersion,
                fileCount = files.size,
                totalSize = totalSize,
                checksum = calculateBackupChecksum(files),
                isEncrypted = true,
            )
        }

        /**
         * Writes backup metadata to file.
         */
        private fun writeBackupMetadata(
            metadata: BackupMetadata,
            tempDir: File,
        ) {
            val metadataFile = File(tempDir, METADATA_FILE)
            metadataFile.writeText(json.encodeToString(metadata))
        }

        /**
         * Handles backup creation exceptions.
         */
        private fun handleBackupCreationException(
            e: Exception,
            type: String,
        ): Result<String> {
            android.util.Log.e("BackupManager", "$type exception during backup creation", e)
            return Result.Error(e, "$type error during backup creation: ${e.message}")
        }

        /**
         * Restores data from a backup file.
         *
         * @param backupFilename The backup file to restore from
         * @param validateChecksum Whether to validate backup integrity
         * @return Success or error result
         */
        suspend fun restoreBackup(
            backupFilename: String,
            validateChecksum: Boolean = true,
        ): Result<Unit> {
            return withContext(Dispatchers.IO) {
                try {
                    val backupFile = validateBackupFileExists(backupFilename)
                    val tempDir = createTemporaryRestoreDirectory()

                    try {
                        extractZipArchive(backupFile, tempDir)
                        val metadata = readAndValidateBackupMetadata(tempDir)

                        if (validateChecksum) {
                            validateBackupIntegrity(tempDir, metadata)
                        }

                        restoreFilesFromBackup(tempDir)
                        updateStorageConfiguration(metadata)

                        Result.Success(Unit)
                    } finally {
                        tempDir.deleteRecursively()
                    }
                } catch (e: StorageException) {
                    handleRestoreException(e, "Storage")
                } catch (e: SecurityException) {
                    handleRestoreException(e, "Security")
                } catch (e: java.io.IOException) {
                    handleRestoreException(e, "IO")
                } catch (e: IllegalArgumentException) {
                    handleRestoreException(e, "Invalid argument")
                } catch (e: IllegalStateException) {
                    handleRestoreException(e, "Invalid state")
                }
            }
        }

        /**
         * Validates that backup file exists.
         */
        private fun validateBackupFileExists(backupFilename: String): File {
            val backupFile = File(backupDir, backupFilename)
            require(backupFile.exists()) { "Backup file not found: $backupFilename" }
            return backupFile
        }

        /**
         * Creates temporary directory for restore operation.
         */
        private fun createTemporaryRestoreDirectory(): File {
            val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            return tempDir
        }

        /**
         * Reads and validates backup metadata.
         */
        private fun readAndValidateBackupMetadata(tempDir: File): BackupMetadata {
            val metadataFile = File(tempDir, METADATA_FILE)
            check(metadataFile.exists()) { "Backup metadata not found" }
            return json.decodeFromString<BackupMetadata>(metadataFile.readText())
        }

        /**
         * Validates backup integrity using checksum.
         */
        private suspend fun validateBackupIntegrity(
            tempDir: File,
            metadata: BackupMetadata,
        ) {
            val dataDir = File(tempDir, DATA_DIR)
            val files = dataDir.listFiles()?.map { it.name } ?: emptyList()
            val calculatedChecksum = calculateBackupChecksum(files)

            check(calculatedChecksum == metadata.checksum) { "Backup checksum validation failed" }
        }

        /**
         * Restores files from backup.
         */
        private suspend fun restoreFilesFromBackup(tempDir: File) {
            val dataDir = File(tempDir, DATA_DIR)
            val files = dataDir.listFiles() ?: emptyArray()

            for (file in files) {
                val fileData = file.readBytes()
                fileStorageManager.writeFileAtomic(file.name, fileData).getOrThrow()
            }
        }

        /**
         * Updates storage configuration after restore.
         */
        private suspend fun updateStorageConfiguration(metadata: BackupMetadata) {
            storageConfiguration.updateStorageVersion(metadata.storageVersion)
        }

        /**
         * Handles restore exceptions.
         */
        private fun handleRestoreException(
            e: Exception,
            type: String,
        ): Result<Unit> {
            android.util.Log.e("BackupManager", "$type exception during backup restoration", e)
            return Result.Error(e, "$type error during backup restoration: ${e.message}")
        }

        /**
         * Lists all available backups.
         *
         * @return List of backup information or error result
         */
        suspend fun listBackups(): Result<List<BackupInfo>> =
            withContext(Dispatchers.IO) {
                try {
                    val backupFiles =
                        backupDir
                            .listFiles()
                            ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()

                    val backupInfos =
                        backupFiles.mapNotNull { file ->
                            try {
                                val metadata = getBackupMetadata(file.name).getOrNull()
                                if (metadata != null) {
                                    BackupInfo(
                                        filename = file.name,
                                        metadata = metadata,
                                        size = file.length(),
                                        isValid = validateBackupFile(file.name).getOrNull() == true,
                                    )
                                } else {
                                    null
                                }
                            } catch (e: StorageException) {
                                android.util.Log.w("BackupManager", "Storage exception reading backup metadata for ${file.name}", e)
                                null
                            } catch (e: SecurityException) {
                                android.util.Log.w("BackupManager", "Security exception reading backup metadata for ${file.name}", e)
                                null
                            } catch (e: java.io.IOException) {
                                android.util.Log.w("BackupManager", "IO exception reading backup metadata for ${file.name}", e)
                                null
                            } catch (e: kotlinx.serialization.SerializationException) {
                                android.util.Log.w("BackupManager", "Serialization exception reading backup metadata for ${file.name}", e)
                                null
                            }
                        }

                    Result.Success(backupInfos)
                } catch (e: StorageException) {
                    android.util.Log.e("BackupManager", "Storage exception during backup listing", e)
                    Result.Error(e, "Storage error during backup listing: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("BackupManager", "Security exception during backup listing", e)
                    Result.Error(e, "Security error during backup listing: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("BackupManager", "IO exception during backup listing", e)
                    Result.Error(e, "IO error during backup listing: ${e.message}")
                }
            }

        /**
         * Deletes a backup file.
         *
         * @param backupFilename The backup file to delete
         * @return Success or error result
         */
        suspend fun deleteBackup(backupFilename: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val backupFile = File(backupDir, backupFilename)
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }

                    Result.Success(Unit)
                } catch (e: StorageException) {
                    android.util.Log.e("BackupManager", "Storage exception during backup deletion", e)
                    Result.Error(e, "Storage error during backup deletion: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("BackupManager", "Security exception during backup deletion", e)
                    Result.Error(e, "Security error during backup deletion: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("BackupManager", "IO exception during backup deletion", e)
                    Result.Error(e, "IO error during backup deletion: ${e.message}")
                }
            }

        /**
         * Gets metadata for a backup file.
         *
         * @param backupFilename The backup file name
         * @return Backup metadata or error result
         */
        suspend fun getBackupMetadata(backupFilename: String): Result<BackupMetadata> {
            return withContext(Dispatchers.IO) {
                try {
                    val backupFile = File(backupDir, backupFilename)
                    if (!backupFile.exists()) {
                        return@withContext Result.Error(
                            IllegalArgumentException("Backup file not found"),
                            "Backup file not found",
                        )
                    }

                    val tempDir = File(context.cacheDir, "metadata_temp_${System.currentTimeMillis()}")
                    tempDir.mkdirs()

                    try {
                        extractZipArchive(backupFile, tempDir)

                        val metadataFile = File(tempDir, METADATA_FILE)
                        if (!metadataFile.exists()) {
                            return@withContext Result.Error(
                                IllegalStateException("Metadata not found in backup"),
                                "Invalid backup format",
                            )
                        }

                        val metadata = json.decodeFromString<BackupMetadata>(metadataFile.readText())
                        Result.Success(metadata)
                    } finally {
                        tempDir.deleteRecursively()
                    }
                } catch (e: StorageException) {
                    android.util.Log.e("BackupManager", "Storage exception getting backup metadata", e)
                    Result.Error(e, "Storage error getting backup metadata: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("BackupManager", "Security exception getting backup metadata", e)
                    Result.Error(e, "Security error getting backup metadata: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("BackupManager", "IO exception getting backup metadata", e)
                    Result.Error(e, "IO error getting backup metadata: ${e.message}")
                } catch (e: kotlinx.serialization.SerializationException) {
                    android.util.Log.e("BackupManager", "Serialization exception getting backup metadata", e)
                    Result.Error(e, "Serialization error getting backup metadata: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("BackupManager", "Invalid argument getting backup metadata", e)
                    Result.Error(e, "Invalid argument getting backup metadata: ${e.message}")
                } catch (e: IllegalStateException) {
                    android.util.Log.e("BackupManager", "Invalid state getting backup metadata", e)
                    Result.Error(e, "Invalid state getting backup metadata: ${e.message}")
                }
            }
        }

        /**
         * Validates a backup file.
         *
         * @param backupFilename The backup file to validate
         * @return True if valid, false otherwise
         */
        suspend fun validateBackupFile(backupFilename: String): Result<Boolean> {
            return withContext(Dispatchers.IO) {
                try {
                    val metadata =
                        getBackupMetadata(backupFilename).getOrNull()
                            ?: return@withContext Result.Success(false)

                    // Basic validation
                    if (metadata.version != BACKUP_VERSION) {
                        return@withContext Result.Success(false)
                    }

                    // Additional validation logic can be added here for future requirements

                    Result.Success(true)
                } catch (e: StorageException) {
                    android.util.Log.w("BackupManager", "Storage exception during backup validation", e)
                    Result.Success(false)
                } catch (e: SecurityException) {
                    android.util.Log.w("BackupManager", "Security exception during backup validation", e)
                    Result.Success(false)
                } catch (e: java.io.IOException) {
                    android.util.Log.w("BackupManager", "IO exception during backup validation", e)
                    Result.Success(false)
                } catch (e: kotlinx.serialization.SerializationException) {
                    android.util.Log.w("BackupManager", "Serialization exception during backup validation", e)
                    Result.Success(false)
                }
            }
        }

        /**
         * Cleans up old backup files based on configuration.
         *
         * @return Success or error result
         */
        suspend fun cleanupOldBackups(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val config = storageConfiguration.getConfiguration().getOrThrow()
                    val backupFiles =
                        backupDir
                            .listFiles()
                            ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()

                    // Remove backups exceeding max count
                    val backupsToDelete = backupFiles.drop(config.maxBackupCount)

                    // Remove backups older than max age
                    val maxAgeMillis = MAX_BACKUP_AGE_DAYS * 24 * 60 * 60 * 1000L
                    val cutoffTime = System.currentTimeMillis() - maxAgeMillis

                    for (file in backupFiles) {
                        if (file.lastModified() < cutoffTime || file in backupsToDelete) {
                            file.delete()
                        }
                    }

                    Result.Success(Unit)
                } catch (e: StorageException) {
                    android.util.Log.e("BackupManager", "Storage exception during backup cleanup", e)
                    Result.Error(e, "Storage error during backup cleanup: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("BackupManager", "Security exception during backup cleanup", e)
                    Result.Error(e, "Security error during backup cleanup: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("BackupManager", "IO exception during backup cleanup", e)
                    Result.Error(e, "IO error during backup cleanup: ${e.message}")
                }
            }

        /**
         * Gets the total size of all backups.
         *
         * @return Total size in bytes or error result
         */
        suspend fun getBackupSize(): Result<Long> =
            withContext(Dispatchers.IO) {
                try {
                    val totalSize =
                        backupDir
                            .listFiles()
                            ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) }
                            ?.sumOf { it.length() }
                            ?: 0L

                    Result.Success(totalSize)
                } catch (e: StorageException) {
                    android.util.Log.e("BackupManager", "Storage exception calculating backup size", e)
                    Result.Error(e, "Storage error calculating backup size: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("BackupManager", "Security exception calculating backup size", e)
                    Result.Error(e, "Security error calculating backup size: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("BackupManager", "IO exception calculating backup size", e)
                    Result.Error(e, "IO error calculating backup size: ${e.message}")
                }
            }

        /**
         * Creates a ZIP archive from a directory.
         *
         * @param sourceDir Source directory
         * @param outputFile Output ZIP file
         */
        private fun createZipArchive(
            sourceDir: File,
            outputFile: File,
        ) {
            ZipOutputStream(outputFile.outputStream()).use { zipOut ->
                sourceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(sourceDir).path
                        val entry = ZipEntry(relativePath)
                        zipOut.putNextEntry(entry)
                        file.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
        }

        /**
         * Extracts a ZIP archive to a directory.
         *
         * @param zipFile ZIP file to extract
         * @param outputDir Output directory
         */
        private fun extractZipArchive(
            zipFile: File,
            outputDir: File,
        ) {
            ZipInputStream(zipFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val file = File(outputDir, entry.name)

                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            zipIn.copyTo(output)
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }

        /**
         * Calculates checksum for backup validation.
         *
         * @param files List of files to include in checksum
         * @return Calculated checksum
         */
        private suspend fun calculateBackupChecksum(files: List<String>): String =
            try {
                val combinedData =
                    files
                        .sorted()
                        .map { filename ->
                            val fileData = fileStorageManager.readFile(filename).getOrNull()
                            if (fileData != null) {
                                fileData.joinToString("") { "%02x".format(it) }
                            } else {
                                ""
                            }
                        }.joinToString("")

                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(combinedData.toByteArray())
                hash.joinToString("") { "%02x".format(it) }
            } catch (e: StorageException) {
                android.util.Log.w("BackupManager", "Storage exception calculating backup checksum", e)
                "checksum_error_storage"
            } catch (e: SecurityException) {
                android.util.Log.w("BackupManager", "Security exception calculating backup checksum", e)
                "checksum_error_security"
            } catch (e: java.security.NoSuchAlgorithmException) {
                android.util.Log.w("BackupManager", "Algorithm exception calculating backup checksum", e)
                "checksum_error_algorithm"
            } catch (e: java.io.IOException) {
                android.util.Log.w("BackupManager", "IO exception calculating backup checksum", e)
                "checksum_error_io"
            }

        /**
         * Gets a device identifier.
         *
         * @return Device identifier
         */
        private fun getDeviceId(): String =
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ) ?: "unknown"

        /**
         * Gets the app version.
         *
         * @return App version string
         */
        private fun getAppVersion(): String =
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "unknown"
            } catch (e: SecurityException) {
                android.util.Log.w("BackupManager", "Security exception getting app version", e)
                "unknown_security"
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                android.util.Log.w("BackupManager", "Package not found getting app version", e)
                "unknown_package"
            }
    }
