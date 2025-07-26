package com.pocketagent.data.storage

/**
 * Base exception class for storage-related errors.
 *
 * This sealed class hierarchy provides specific exception types for different
 * storage error scenarios to enable better error handling and debugging.
 */
sealed class StorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Exception thrown when encryption/decryption operations fail.
     */
    class EncryptionException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when file operations fail.
     */
    class FileOperationException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when data validation fails.
     */
    class ValidationException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when backup operations fail.
     */
    class BackupException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage configuration is invalid.
     */
    class ConfigurationException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage quota is exceeded.
     */
    class QuotaExceededException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when attempting to access non-existent data.
     */
    class DataNotFoundException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when data integrity checks fail.
     */
    class IntegrityException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when concurrent access conflicts occur.
     */
    class ConcurrencyException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage is in an inconsistent state.
     */
    class InconsistentStateException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage format is not supported.
     */
    class UnsupportedFormatException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage migration fails.
     */
    class MigrationException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage is locked or inaccessible.
     */
    class AccessDeniedException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage operations timeout.
     */
    class TimeoutException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)

    /**
     * Exception thrown when storage is corrupted.
     */
    class CorruptionException(
        message: String,
        cause: Throwable? = null,
    ) : StorageException(message, cause)
}

/**
 * Error codes for storage operations.
 */
enum class StorageErrorCode(
    val code: String,
    val description: String,
) {
    // Encryption errors
    ENCRYPTION_FAILED("ENC_001", "Failed to encrypt data"),
    DECRYPTION_FAILED("ENC_002", "Failed to decrypt data"),
    KEY_GENERATION_FAILED("ENC_003", "Failed to generate encryption key"),
    KEY_NOT_FOUND("ENC_004", "Encryption key not found"),
    INVALID_KEY_FORMAT("ENC_005", "Invalid encryption key format"),

    // File operation errors
    FILE_NOT_FOUND("FILE_001", "File not found"),
    FILE_READ_FAILED("FILE_002", "Failed to read file"),
    FILE_WRITE_FAILED("FILE_003", "Failed to write file"),
    FILE_DELETE_FAILED("FILE_004", "Failed to delete file"),
    FILE_CORRUPTED("FILE_005", "File is corrupted"),
    FILE_LOCKED("FILE_006", "File is locked"),
    ATOMIC_WRITE_FAILED("FILE_007", "Atomic write operation failed"),
    CHECKSUM_MISMATCH("FILE_008", "File checksum mismatch"),

    // Validation errors
    INVALID_JSON_FORMAT("VAL_001", "Invalid JSON format"),
    DATA_TOO_LARGE("VAL_002", "Data exceeds size limit"),
    DATA_TOO_SMALL("VAL_003", "Data is too small"),
    STRUCTURE_INVALID("VAL_004", "Data structure is invalid"),
    SCHEMA_VIOLATION("VAL_005", "Data violates schema"),
    INTEGRITY_CHECK_FAILED("VAL_006", "Data integrity check failed"),

    // Backup errors
    BACKUP_CREATION_FAILED("BAK_001", "Failed to create backup"),
    BACKUP_RESTORE_FAILED("BAK_002", "Failed to restore backup"),
    BACKUP_NOT_FOUND("BAK_003", "Backup file not found"),
    BACKUP_CORRUPTED("BAK_004", "Backup file is corrupted"),
    BACKUP_VERSION_MISMATCH("BAK_005", "Backup version mismatch"),

    // Configuration errors
    INVALID_CONFIG("CFG_001", "Invalid configuration"),
    CONFIG_NOT_FOUND("CFG_002", "Configuration not found"),
    CONFIG_PARSE_ERROR("CFG_003", "Configuration parsing error"),

    // Quota errors
    STORAGE_QUOTA_EXCEEDED("QTA_001", "Storage quota exceeded"),
    FILE_COUNT_LIMIT_EXCEEDED("QTA_002", "File count limit exceeded"),
    BACKUP_QUOTA_EXCEEDED("QTA_003", "Backup quota exceeded"),

    // Concurrency errors
    CONCURRENT_MODIFICATION("CON_001", "Concurrent modification detected"),
    LOCK_ACQUISITION_FAILED("CON_002", "Failed to acquire lock"),
    OPERATION_TIMEOUT("CON_003", "Operation timed out"),

    // State errors
    INCONSISTENT_STATE("STA_001", "Storage is in inconsistent state"),
    MIGRATION_REQUIRED("STA_002", "Storage migration required"),
    INITIALIZATION_FAILED("STA_003", "Storage initialization failed"),

    // Access errors
    ACCESS_DENIED("ACC_001", "Access denied"),
    AUTHENTICATION_REQUIRED("ACC_002", "Authentication required"),
    PERMISSION_DENIED("ACC_003", "Permission denied"),

    // System errors
    INSUFFICIENT_STORAGE("SYS_001", "Insufficient storage space"),
    SYSTEM_ERROR("SYS_002", "System error occurred"),
    UNKNOWN_ERROR("SYS_999", "Unknown error occurred"),
    ;

    companion object {
        fun fromCode(code: String): StorageErrorCode? = values().find { it.code == code }
    }
}

/**
 * Enhanced exception with error codes and additional context.
 */
class DetailedStorageException(
    val errorCode: StorageErrorCode,
    message: String,
    cause: Throwable? = null,
    val context: Map<String, Any> = emptyMap(),
) : StorageException(message, cause) {
    override val message: String
        get() = "[${errorCode.code}] $message"

    /**
     * Creates a copy of this exception with additional context.
     */
    fun withContext(
        key: String,
        value: Any,
    ): DetailedStorageException =
        DetailedStorageException(
            errorCode = errorCode,
            message = super.message ?: "Unknown error",
            cause = cause,
            context = context + (key to value),
        )

    /**
     * Gets context value by key.
     */
    fun <T> getContext(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return context[key] as? T
    }

    /**
     * Formats the exception for logging.
     */
    fun toLogString(): String =
        buildString {
            append("StorageException: ")
            append(message)
            if (context.isNotEmpty()) {
                append(" [Context: ")
                append(context.entries.joinToString(", ") { "${it.key}=${it.value}" })
                append("]")
            }
            if (cause != null) {
                append(" [Cause: ${cause!!.message}]")
            }
        }
}

/**
 * Utility class for creating storage exceptions with context.
 */
object StorageExceptionFactory {
    /**
     * Creates an encryption exception.
     */
    fun encryptionFailed(
        message: String,
        cause: Throwable? = null,
        context: Map<String, Any> = emptyMap(),
    ): DetailedStorageException =
        DetailedStorageException(
            errorCode = StorageErrorCode.ENCRYPTION_FAILED,
            message = message,
            cause = cause,
            context = context,
        )

    /**
     * Creates a decryption exception.
     */
    fun decryptionFailed(
        message: String,
        cause: Throwable? = null,
        context: Map<String, Any> = emptyMap(),
    ): DetailedStorageException =
        DetailedStorageException(
            errorCode = StorageErrorCode.DECRYPTION_FAILED,
            message = message,
            cause = cause,
            context = context,
        )

    /**
     * Creates a file operation exception.
     */
    fun fileOperationFailed(
        errorCode: StorageErrorCode,
        message: String,
        filename: String,
        cause: Throwable? = null,
    ): DetailedStorageException =
        DetailedStorageException(
            errorCode = errorCode,
            message = message,
            cause = cause,
            context = mapOf("filename" to filename),
        )

    /**
     * Creates a validation exception.
     */
    fun validationFailed(
        errorCode: StorageErrorCode,
        message: String,
        details: String? = null,
        cause: Throwable? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (details != null) {
            context["details"] = details
        }

        return DetailedStorageException(
            errorCode = errorCode,
            message = message,
            cause = cause,
            context = context,
        )
    }

    /**
     * Creates a backup exception.
     */
    fun backupFailed(
        errorCode: StorageErrorCode,
        message: String,
        backupFile: String? = null,
        cause: Throwable? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (backupFile != null) {
            context["backupFile"] = backupFile
        }

        return DetailedStorageException(
            errorCode = errorCode,
            message = message,
            cause = cause,
            context = context,
        )
    }

    /**
     * Creates a configuration exception.
     */
    fun configurationError(
        message: String,
        configKey: String? = null,
        cause: Throwable? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (configKey != null) {
            context["configKey"] = configKey
        }

        return DetailedStorageException(
            errorCode = StorageErrorCode.INVALID_CONFIG,
            message = message,
            cause = cause,
            context = context,
        )
    }

    /**
     * Creates a quota exceeded exception.
     */
    fun quotaExceeded(
        errorCode: StorageErrorCode,
        message: String,
        currentSize: Long,
        maxSize: Long,
    ): DetailedStorageException =
        DetailedStorageException(
            errorCode = errorCode,
            message = message,
            context =
                mapOf(
                    "currentSize" to currentSize,
                    "maxSize" to maxSize,
                ),
        )

    /**
     * Creates a concurrency exception.
     */
    fun concurrencyError(
        message: String,
        resource: String? = null,
        cause: Throwable? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (resource != null) {
            context["resource"] = resource
        }

        return DetailedStorageException(
            errorCode = StorageErrorCode.CONCURRENT_MODIFICATION,
            message = message,
            cause = cause,
            context = context,
        )
    }

    /**
     * Creates an access denied exception.
     */
    fun accessDenied(
        message: String,
        resource: String? = null,
        requiredPermission: String? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (resource != null) {
            context["resource"] = resource
        }
        if (requiredPermission != null) {
            context["requiredPermission"] = requiredPermission
        }

        return DetailedStorageException(
            errorCode = StorageErrorCode.ACCESS_DENIED,
            message = message,
            context = context,
        )
    }

    /**
     * Creates a corruption exception.
     */
    fun dataCorrupted(
        message: String,
        filename: String? = null,
        expectedChecksum: String? = null,
        actualChecksum: String? = null,
    ): DetailedStorageException {
        val context = mutableMapOf<String, Any>()
        if (filename != null) {
            context["filename"] = filename
        }
        if (expectedChecksum != null) {
            context["expectedChecksum"] = expectedChecksum
        }
        if (actualChecksum != null) {
            context["actualChecksum"] = actualChecksum
        }

        return DetailedStorageException(
            errorCode = StorageErrorCode.FILE_CORRUPTED,
            message = message,
            context = context,
        )
    }
}
