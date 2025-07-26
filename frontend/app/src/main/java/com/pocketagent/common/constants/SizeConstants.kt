package com.pocketagent.common.constants

/**
 * Size-related constants used throughout the application.
 *
 * Centralizes all size-related magic numbers including byte sizes,
 * buffer sizes, and data limits to improve maintainability.
 */
object SizeConstants {
    // Basic byte units
    const val BYTES_PER_KB = 1024L
    const val BYTES_PER_MB = BYTES_PER_KB * 1024L
    const val BYTES_PER_GB = BYTES_PER_MB * 1024L

    // File size thresholds for display formatting
    const val SIZE_DISPLAY_KB_THRESHOLD = 1024L
    const val SIZE_DISPLAY_MB_THRESHOLD = BYTES_PER_MB
    const val SIZE_DISPLAY_GB_THRESHOLD = BYTES_PER_GB

    // Buffer and chunk sizes
    const val FILE_CHUNK_SIZE_BYTES = 8192 // 8KB chunks for file operations
    const val DEFAULT_BUFFER_SIZE = 4096 // 4KB default buffer
    const val LARGE_BUFFER_SIZE = 16384 // 16KB for large operations
    const val COMPRESSION_THRESHOLD_BYTES = 1024 // Compress files larger than 1KB

    // Cache sizes
    const val DEFAULT_MAX_CACHE_SIZE = 100 * BYTES_PER_MB // 100MB default cache
    const val DEFAULT_MAX_LOG_SIZE = 10 * BYTES_PER_MB // 10MB log files

    // File size limits
    const val MAX_FILE_SIZE_BYTES = 10 * BYTES_PER_MB // 10MB max file size
    const val MAX_MESSAGE_CONTENT_SIZE = 100_000 // 100KB max message content
    const val MAX_ATTACHMENT_CONTENT_SIZE = 1_000_000 // 1MB max attachment content

    // SSH key sizes (in bits)
    const val RSA_MIN_KEY_SIZE_BITS = 2048
    const val RSA_RECOMMENDED_KEY_SIZE_BITS = 4096
    const val RSA_MAX_KEY_SIZE_BITS = 8192
    const val ECDSA_MIN_KEY_SIZE_BITS = 256
    const val ECDSA_RECOMMENDED_KEY_SIZE_BITS = 384
    const val ECDSA_MAX_KEY_SIZE_BITS = 521
    const val ED25519_KEY_SIZE_BITS = 256
    const val DSA_MIN_KEY_SIZE_BITS = 1024
    const val DSA_MAX_KEY_SIZE_BITS = 3072

    // Text length limits
    const val MAX_NAME_LENGTH = 100
    const val MIN_NAME_LENGTH = 1
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MAX_HOSTNAME_LENGTH = 255
    const val MAX_USERNAME_LENGTH = 64
    const val MAX_USERNAME_LENGTH_SSH = 32
    const val MAX_PATH_LENGTH = 4096
    const val MAX_ERROR_MESSAGE_LENGTH = 1000
    const val MAX_CUSTOM_PROMPT_KEY_LENGTH = 100
    const val MAX_CUSTOM_PROMPT_VALUE_LENGTH = 10000
    const val MAX_MIME_TYPE_LENGTH = 100
    const val MAX_ATTACHMENT_NAME_LENGTH = 255

    // Collection size limits
    const val MAX_ALLOWED_TOOLS = 100
    const val MAX_AUTO_APPROVE_PATTERNS = 50
    const val MAX_TURNS = 1000
    const val MIN_TURNS = 1
    const val MAX_MESSAGE_HISTORY = 10000
    const val MIN_MESSAGE_HISTORY = 100
    const val DEFAULT_MAX_BATCH_SIZE = 10
    const val DEFAULT_MESSAGE_QUEUE_SIZE = 100
    const val DEFAULT_MAX_BACKUP_COUNT = 10

    // Binary data sizes
    const val INT_BYTE_SIZE = 4
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BYTES = 16
    const val DEFAULT_SALT_LENGTH_BYTES = 32
    const val ENCRYPTED_DATA_HEADER_SIZE_BYTES = 8

    // Number formatting thresholds
    const val NUMBER_FORMAT_THOUSAND_THRESHOLD = 1_000L
    const val NUMBER_FORMAT_MILLION_THRESHOLD = 1_000_000L

    // Activity and recent items limits
    const val DEFAULT_RECENT_ACTIVITY_LIMIT = 10
}
