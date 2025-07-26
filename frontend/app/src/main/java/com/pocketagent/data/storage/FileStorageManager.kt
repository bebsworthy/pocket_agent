package com.pocketagent.data.storage

import android.content.Context
import com.pocketagent.domain.models.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for file system operations with atomic writes and backup support.
 *
 * This class provides thread-safe file operations with atomic write capabilities
 * and automatic backup management for critical data files.
 */
@Singleton
class FileStorageManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val STORAGE_DIR = "encrypted_storage"
            private const val TEMP_SUFFIX = ".tmp"
            private const val BACKUP_SUFFIX = ".backup"
            private const val CHECKSUM_SUFFIX = ".checksum"
            private const val MAX_BACKUP_COUNT = 3
        }

        private val storageDir: File by lazy {
            File(context.filesDir, STORAGE_DIR).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }

        private val writeMutex = Mutex()
        private val _fileChanges = MutableStateFlow<String?>(null)

        /**
         * Flow that emits file changes.
         */
        val fileChanges: Flow<String?> = _fileChanges.asStateFlow()

        /**
         * Data class representing file metadata.
         */
        data class FileMetadata(
            val name: String,
            val size: Long,
            val lastModified: Long,
            val checksum: String,
            val exists: Boolean,
        )

        /**
         * Writes data to a file atomically.
         *
         * This method ensures that either the entire write operation succeeds or fails,
         * preventing partial writes that could corrupt data.
         *
         * @param filename The name of the file to write
         * @param data The data to write
         * @return Success or error result
         */
        suspend fun writeFileAtomic(
            filename: String,
            data: ByteArray,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                writeMutex.withLock {
                    try {
                        val targetFile = File(storageDir, filename)
                        val tempFile = File(storageDir, "$filename$TEMP_SUFFIX")
                        val backupFile = File(storageDir, "$filename$BACKUP_SUFFIX")

                        // Create backup of existing file if it exists
                        if (targetFile.exists()) {
                            targetFile.copyTo(backupFile, overwrite = true)
                        }

                        // Write to temporary file
                        tempFile.writeBytes(data)

                        // Verify written data
                        val writtenData = tempFile.readBytes()
                        if (!writtenData.contentEquals(data)) {
                            tempFile.delete()
                            throw IOException("Data verification failed during atomic write")
                        }

                        // Atomic rename
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        } else {
                            // Fallback for older Android versions
                            if (!tempFile.renameTo(targetFile)) {
                                tempFile.delete()
                                throw IOException("Failed to rename temporary file to target file")
                            }
                        }

                        // Write checksum
                        writeChecksum(filename, data)

                        // Notify file change
                        _fileChanges.emit(filename)

                        Result.Success(Unit)
                    } catch (e: StorageException) {
                        android.util.Log.e("FileStorageManager", "Storage exception during atomic write", e)
                        Result.Error(e, "Storage error during atomic write: ${e.message}")
                    } catch (e: SecurityException) {
                        android.util.Log.e("FileStorageManager", "Security exception during atomic write", e)
                        Result.Error(e, "Security error during atomic write: ${e.message}")
                    } catch (e: java.io.IOException) {
                        android.util.Log.e("FileStorageManager", "IO exception during atomic write", e)
                        Result.Error(e, "IO error during atomic write: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        android.util.Log.e("FileStorageManager", "Invalid argument during atomic write", e)
                        Result.Error(e, "Invalid argument during atomic write: ${e.message}")
                    } catch (e: IllegalStateException) {
                        android.util.Log.e("FileStorageManager", "Invalid state during atomic write", e)
                        Result.Error(e, "Invalid state during atomic write: ${e.message}")
                    }
                }
            }

        /**
         * Reads data from a file with integrity verification.
         *
         * @param filename The name of the file to read
         * @param verifyChecksum Whether to verify file integrity
         * @return The file data or error result
         */
        suspend fun readFile(
            filename: String,
            verifyChecksum: Boolean = true,
        ): Result<ByteArray> {
            return withContext(Dispatchers.IO) {
                try {
                    val file = File(storageDir, filename)

                    if (!file.exists()) {
                        return@withContext Result.Error(
                            FileNotFoundException("File not found: $filename"),
                            "File not found",
                        )
                    }

                    val data = file.readBytes()

                    // Verify checksum if requested
                    if (verifyChecksum) {
                        val checksumResult = verifyFileChecksum(filename, data)
                        if (checksumResult.getOrNull() != true) {
                            return@withContext Result.Error(
                                IOException("Checksum verification failed"),
                                "File integrity check failed",
                            )
                        }
                    }

                    Result.Success(data)
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception during file read", e)
                    Result.Error(e, "Storage error during file read: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception during file read", e)
                    Result.Error(e, "Security error during file read: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception during file read", e)
                    Result.Error(e, "IO error during file read: ${e.message}")
                } catch (e: java.io.FileNotFoundException) {
                    android.util.Log.w("FileStorageManager", "File not found during read", e)
                    Result.Error(e, "File not found: ${e.message}")
                }
            }
        }

        /**
         * Deletes a file and its associated backup and checksum files.
         *
         * @param filename The name of the file to delete
         * @return Success or error result
         */
        suspend fun deleteFile(filename: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                writeMutex.withLock {
                    try {
                        val file = File(storageDir, filename)
                        val backupFile = File(storageDir, "$filename$BACKUP_SUFFIX")
                        val checksumFile = File(storageDir, "$filename$CHECKSUM_SUFFIX")

                        var deleted = false

                        if (file.exists()) {
                            file.delete()
                            deleted = true
                        }

                        if (backupFile.exists()) {
                            backupFile.delete()
                        }

                        if (checksumFile.exists()) {
                            checksumFile.delete()
                        }

                        if (deleted) {
                            _fileChanges.emit(filename)
                        }

                        Result.Success(Unit)
                    } catch (e: StorageException) {
                        android.util.Log.e("FileStorageManager", "Storage exception during file deletion", e)
                        Result.Error(e, "Storage error during file deletion: ${e.message}")
                    } catch (e: SecurityException) {
                        android.util.Log.e("FileStorageManager", "Security exception during file deletion", e)
                        Result.Error(e, "Security error during file deletion: ${e.message}")
                    } catch (e: java.io.IOException) {
                        android.util.Log.e("FileStorageManager", "IO exception during file deletion", e)
                        Result.Error(e, "IO error during file deletion: ${e.message}")
                    }
                }
            }

        /**
         * Checks if a file exists.
         *
         * @param filename The name of the file to check
         * @return True if file exists, false otherwise
         */
        suspend fun fileExists(filename: String): Result<Boolean> =
            withContext(Dispatchers.IO) {
                try {
                    val file = File(storageDir, filename)
                    Result.Success(file.exists())
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception checking file existence", e)
                    Result.Error(e, "Storage error checking file existence: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception checking file existence", e)
                    Result.Error(e, "Security error checking file existence: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception checking file existence", e)
                    Result.Error(e, "IO error checking file existence: ${e.message}")
                }
            }

        /**
         * Gets metadata for a file.
         *
         * @param filename The name of the file
         * @return File metadata or error result
         */
        suspend fun getFileMetadata(filename: String): Result<FileMetadata> {
            return withContext(Dispatchers.IO) {
                try {
                    val file = File(storageDir, filename)

                    if (!file.exists()) {
                        return@withContext Result.Success(
                            FileMetadata(
                                name = filename,
                                size = 0,
                                lastModified = 0,
                                checksum = "",
                                exists = false,
                            ),
                        )
                    }

                    val checksum = readChecksum(filename).getOrNull() ?: ""

                    Result.Success(
                        FileMetadata(
                            name = filename,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            checksum = checksum,
                            exists = true,
                        ),
                    )
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception getting file metadata", e)
                    Result.Error(e, "Storage error getting file metadata: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception getting file metadata", e)
                    Result.Error(e, "Security error getting file metadata: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception getting file metadata", e)
                    Result.Error(e, "IO error getting file metadata: ${e.message}")
                }
            }
        }

        /**
         * Lists all files in the storage directory.
         *
         * @return List of file names or error result
         */
        suspend fun listFiles(): Result<List<String>> =
            withContext(Dispatchers.IO) {
                try {
                    val files =
                        storageDir
                            .listFiles()
                            ?.filter { it.isFile }
                            ?.filter { !it.name.endsWith(TEMP_SUFFIX) }
                            ?.filter { !it.name.endsWith(BACKUP_SUFFIX) }
                            ?.filter { !it.name.endsWith(CHECKSUM_SUFFIX) }
                            ?.map { it.name }
                            ?: emptyList()

                    Result.Success(files.sorted())
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception listing files", e)
                    Result.Error(e, "Storage error listing files: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception listing files", e)
                    Result.Error(e, "Security error listing files: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception listing files", e)
                    Result.Error(e, "IO error listing files: ${e.message}")
                }
            }

        /**
         * Restores a file from backup.
         *
         * @param filename The name of the file to restore
         * @return Success or error result
         */
        suspend fun restoreFromBackup(filename: String): Result<Unit> {
            return withContext(Dispatchers.IO) {
                writeMutex.withLock {
                    try {
                        val targetFile = File(storageDir, filename)
                        val backupFile = File(storageDir, "$filename$BACKUP_SUFFIX")

                        if (!backupFile.exists()) {
                            return@withLock Result.Error(
                                FileNotFoundException("Backup file not found: $filename"),
                                "Backup file not found",
                            )
                        }

                        backupFile.copyTo(targetFile, overwrite = true)

                        // Update checksum
                        val data = targetFile.readBytes()
                        writeChecksum(filename, data)

                        _fileChanges.emit(filename)

                        Result.Success(Unit)
                    } catch (e: StorageException) {
                        android.util.Log.e("FileStorageManager", "Storage exception restoring from backup", e)
                        Result.Error(e, "Storage error restoring from backup: ${e.message}")
                    } catch (e: SecurityException) {
                        android.util.Log.e("FileStorageManager", "Security exception restoring from backup", e)
                        Result.Error(e, "Security error restoring from backup: ${e.message}")
                    } catch (e: java.io.IOException) {
                        android.util.Log.e("FileStorageManager", "IO exception restoring from backup", e)
                        Result.Error(e, "IO error restoring from backup: ${e.message}")
                    } catch (e: java.io.FileNotFoundException) {
                        android.util.Log.e("FileStorageManager", "Backup file not found during restore", e)
                        Result.Error(e, "Backup file not found: ${e.message}")
                    }
                }
            }
        }

        /**
         * Cleans up old backup files.
         *
         * @return Success or error result
         */
        suspend fun cleanupBackups(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val backupFiles =
                        storageDir
                            .listFiles()
                            ?.filter { it.name.endsWith(BACKUP_SUFFIX) }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()

                    // Keep only the most recent backups
                    backupFiles.drop(MAX_BACKUP_COUNT).forEach { file ->
                        file.delete()
                    }

                    Result.Success(Unit)
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception cleaning up backups", e)
                    Result.Error(e, "Storage error cleaning up backups: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception cleaning up backups", e)
                    Result.Error(e, "Security error cleaning up backups: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception cleaning up backups", e)
                    Result.Error(e, "IO error cleaning up backups: ${e.message}")
                }
            }

        /**
         * Gets the size of the storage directory.
         *
         * @return Total size in bytes or error result
         */
        suspend fun getStorageSize(): Result<Long> =
            withContext(Dispatchers.IO) {
                try {
                    val totalSize =
                        storageDir
                            .walkTopDown()
                            .filter { it.isFile }
                            .map { it.length() }
                            .sum()

                    Result.Success(totalSize)
                } catch (e: StorageException) {
                    android.util.Log.e("FileStorageManager", "Storage exception calculating storage size", e)
                    Result.Error(e, "Storage error calculating storage size: ${e.message}")
                } catch (e: SecurityException) {
                    android.util.Log.e("FileStorageManager", "Security exception calculating storage size", e)
                    Result.Error(e, "Security error calculating storage size: ${e.message}")
                } catch (e: java.io.IOException) {
                    android.util.Log.e("FileStorageManager", "IO exception calculating storage size", e)
                    Result.Error(e, "IO error calculating storage size: ${e.message}")
                }
            }

        /**
         * Writes a checksum file for the given data.
         *
         * @param filename The name of the file
         * @param data The file data
         */
        private fun writeChecksum(
            filename: String,
            data: ByteArray,
        ) {
            val checksum = calculateChecksum(data)
            val checksumFile = File(storageDir, "$filename$CHECKSUM_SUFFIX")
            checksumFile.writeText(checksum)
        }

        /**
         * Reads the checksum for a file.
         *
         * @param filename The name of the file
         * @return The checksum string or error result
         */
        private fun readChecksum(filename: String): Result<String> =
            try {
                val checksumFile = File(storageDir, "$filename$CHECKSUM_SUFFIX")
                if (checksumFile.exists()) {
                    Result.Success(checksumFile.readText().trim())
                } else {
                    Result.Error(FileNotFoundException("Checksum file not found"), "Checksum file not found")
                }
            } catch (e: StorageException) {
                android.util.Log.w("FileStorageManager", "Storage exception reading checksum", e)
                Result.Error(e, "Storage error reading checksum: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.w("FileStorageManager", "Security exception reading checksum", e)
                Result.Error(e, "Security error reading checksum: ${e.message}")
            } catch (e: java.io.IOException) {
                android.util.Log.w("FileStorageManager", "IO exception reading checksum", e)
                Result.Error(e, "IO error reading checksum: ${e.message}")
            } catch (e: java.io.FileNotFoundException) {
                Result.Error(e, "Checksum file not found: ${e.message}")
            }

        /**
         * Verifies the checksum of file data.
         *
         * @param filename The name of the file
         * @param data The file data
         * @return True if checksum matches, false otherwise
         */
        private fun verifyFileChecksum(
            filename: String,
            data: ByteArray,
        ): Result<Boolean> =
            try {
                val storedChecksum = readChecksum(filename).getOrNull()
                if (storedChecksum != null) {
                    val calculatedChecksum = calculateChecksum(data)
                    Result.Success(storedChecksum == calculatedChecksum)
                } else {
                    Result.Success(false)
                }
            } catch (e: StorageException) {
                android.util.Log.e("FileStorageManager", "Storage exception verifying checksum", e)
                Result.Error(e, "Storage error verifying checksum: ${e.message}")
            } catch (e: SecurityException) {
                android.util.Log.e("FileStorageManager", "Security exception verifying checksum", e)
                Result.Error(e, "Security error verifying checksum: ${e.message}")
            } catch (e: java.security.NoSuchAlgorithmException) {
                android.util.Log.e("FileStorageManager", "Algorithm exception verifying checksum", e)
                Result.Error(e, "Algorithm error verifying checksum: ${e.message}")
            } catch (e: java.io.IOException) {
                android.util.Log.e("FileStorageManager", "IO exception verifying checksum", e)
                Result.Error(e, "IO error verifying checksum: ${e.message}")
            }

        /**
         * Calculates SHA-256 checksum for data.
         *
         * @param data The data to calculate checksum for
         * @return The checksum as hex string
         */
        private fun calculateChecksum(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
