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
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStorageManager: FileStorageManager,
    private val storageConfiguration: StorageConfiguration,
    private val storageEncryption: StorageEncryption
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
    
    private val json = Json {
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
        val isEncrypted: Boolean
    )
    
    /**
     * Data class representing backup information.
     */
    data class BackupInfo(
        val filename: String,
        val metadata: BackupMetadata,
        val size: Long,
        val isValid: Boolean
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
                val config = storageConfiguration.getConfiguration().getOrThrow()
                val files = fileStorageManager.listFiles().getOrThrow()
                
                if (files.isEmpty()) {
                    return@withContext Result.Error(
                        IllegalStateException("No files to backup"),
                        "No data to backup"
                    )
                }
                
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                val backupFilename = "backup_${dateFormat.format(Date(timestamp))}$BACKUP_EXTENSION"
                val backupFile = File(backupDir, backupFilename)
                
                // Create temporary directory for backup preparation
                val tempDir = File(context.cacheDir, "backup_temp_$timestamp")
                tempDir.mkdirs()
                
                try {
                    // Copy all data files to temp directory
                    val dataDir = File(tempDir, DATA_DIR)
                    dataDir.mkdirs()
                    
                    var totalSize = 0L
                    for (filename in files) {
                        val fileData = fileStorageManager.readFile(filename).getOrThrow()
                        val destFile = File(dataDir, filename)
                        destFile.writeBytes(fileData)
                        totalSize += fileData.size
                    }
                    
                    // Create metadata
                    val metadata = BackupMetadata(
                        version = BACKUP_VERSION,
                        timestamp = timestamp,
                        deviceId = getDeviceId(),
                        appVersion = getAppVersion(),
                        storageVersion = config.storageVersion,
                        fileCount = files.size,
                        totalSize = totalSize,
                        checksum = calculateBackupChecksum(files),
                        isEncrypted = true
                    )
                    
                    if (includeMetadata) {
                        val metadataFile = File(tempDir, METADATA_FILE)
                        metadataFile.writeText(json.encodeToString(metadata))
                    }
                    
                    // Create ZIP archive
                    createZipArchive(tempDir, backupFile)
                    
                    // Clean up old backups
                    cleanupOldBackups()
                    
                    Result.Success(backupFilename)
                } finally {
                    // Clean up temp directory
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Result.Error(e, "Failed to create backup: ${e.message}")
            }
        }
    }
    
    /**
     * Restores data from a backup file.
     * 
     * @param backupFilename The backup file to restore from
     * @param validateChecksum Whether to validate backup integrity
     * @return Success or error result
     */
    suspend fun restoreBackup(backupFilename: String, validateChecksum: Boolean = true): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val backupFile = File(backupDir, backupFilename)
                if (!backupFile.exists()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Backup file not found: $backupFilename"),
                        "Backup file not found"
                    )
                }
                
                // Create temporary directory for extraction
                val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                
                try {
                    // Extract ZIP archive
                    extractZipArchive(backupFile, tempDir)
                    
                    // Read metadata
                    val metadataFile = File(tempDir, METADATA_FILE)
                    if (!metadataFile.exists()) {
                        return@withContext Result.Error(
                            IllegalStateException("Backup metadata not found"),
                            "Invalid backup format"
                        )
                    }
                    
                    val metadata = json.decodeFromString<BackupMetadata>(metadataFile.readText())
                    
                    // Validate backup
                    if (validateChecksum) {
                        val dataDir = File(tempDir, DATA_DIR)
                        val files = dataDir.listFiles()?.map { it.name } ?: emptyList()
                        val calculatedChecksum = calculateBackupChecksum(files)
                        
                        if (calculatedChecksum != metadata.checksum) {
                            return@withContext Result.Error(
                                IllegalStateException("Backup checksum validation failed"),
                                "Backup integrity check failed"
                            )
                        }
                    }
                    
                    // Restore files
                    val dataDir = File(tempDir, DATA_DIR)
                    val files = dataDir.listFiles() ?: emptyArray()
                    
                    for (file in files) {
                        val fileData = file.readBytes()
                        fileStorageManager.writeFileAtomic(file.name, fileData).getOrThrow()
                    }
                    
                    // Update storage version if needed
                    storageConfiguration.updateStorageVersion(metadata.storageVersion)
                    
                    Result.Success(Unit)
                } finally {
                    // Clean up temp directory
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Result.Error(e, "Failed to restore backup: ${e.message}")
            }
        }
    }
    
    /**
     * Lists all available backups.
     * 
     * @return List of backup information or error result
     */
    suspend fun listBackups(): Result<List<BackupInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val backupFiles = backupDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                
                val backupInfos = backupFiles.mapNotNull { file ->
                    try {
                        val metadata = getBackupMetadata(file.name).getOrNull()
                        if (metadata != null) {
                            BackupInfo(
                                filename = file.name,
                                metadata = metadata,
                                size = file.length(),
                                isValid = validateBackupFile(file.name).getOrNull() == true
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                Result.Success(backupInfos)
            } catch (e: Exception) {
                Result.Error(e, "Failed to list backups: ${e.message}")
            }
        }
    }
    
    /**
     * Deletes a backup file.
     * 
     * @param backupFilename The backup file to delete
     * @return Success or error result
     */
    suspend fun deleteBackup(backupFilename: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val backupFile = File(backupDir, backupFilename)
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e, "Failed to delete backup: ${e.message}")
            }
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
                        "Backup file not found"
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
                            "Invalid backup format"
                        )
                    }
                    
                    val metadata = json.decodeFromString<BackupMetadata>(metadataFile.readText())
                    Result.Success(metadata)
                } finally {
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Result.Error(e, "Failed to get backup metadata: ${e.message}")
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
                val metadata = getBackupMetadata(backupFilename).getOrNull()
                    ?: return@withContext Result.Success(false)
                
                // Basic validation
                if (metadata.version != BACKUP_VERSION) {
                    return@withContext Result.Success(false)
                }
                
                // TODO: Add more validation logic here
                
                Result.Success(true)
            } catch (e: Exception) {
                Result.Success(false)
            }
        }
    }
    
    /**
     * Cleans up old backup files based on configuration.
     * 
     * @return Success or error result
     */
    suspend fun cleanupOldBackups(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val config = storageConfiguration.getConfiguration().getOrThrow()
                val backupFiles = backupDir.listFiles()
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
            } catch (e: Exception) {
                Result.Error(e, "Failed to cleanup old backups: ${e.message}")
            }
        }
    }
    
    /**
     * Gets the total size of all backups.
     * 
     * @return Total size in bytes or error result
     */
    suspend fun getBackupSize(): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val totalSize = backupDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) }
                    ?.sumOf { it.length() }
                    ?: 0L
                
                Result.Success(totalSize)
            } catch (e: Exception) {
                Result.Error(e, "Failed to calculate backup size: ${e.message}")
            }
        }
    }
    
    /**
     * Creates a ZIP archive from a directory.
     * 
     * @param sourceDir Source directory
     * @param outputFile Output ZIP file
     */
    private fun createZipArchive(sourceDir: File, outputFile: File) {
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
    private fun extractZipArchive(zipFile: File, outputDir: File) {
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
    private suspend fun calculateBackupChecksum(files: List<String>): String {
        return try {
            val combinedData = files.sorted().joinToString("") { filename ->
                val fileData = fileStorageManager.readFile(filename).getOrNull()
                if (fileData != null) {
                    fileData.joinToString("") { "%02x".format(it) }
                } else {
                    ""
                }
            }
            
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combinedData.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "checksum_error"
        }
    }
    
    /**
     * Gets a device identifier.
     * 
     * @return Device identifier
     */
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
    
    /**
     * Gets the app version.
     * 
     * @return App version string
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}