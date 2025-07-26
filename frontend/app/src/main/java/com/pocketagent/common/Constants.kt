package com.pocketagent.common

/**
 * Centralized constants for the Pocket Agent application.
 *
 * This object contains all constant values used across the application,
 * organized by functional area to avoid magic numbers and improve maintainability.
 */
object Constants {
    /**
     * Encryption and cryptographic constants.
     */
    object Encryption {
        /** AES key size in bits for strong encryption */
        const val AES_KEY_SIZE_BITS = 256

        /** RSA minimum key size in bits for strong encryption */
        const val RSA_MIN_KEY_SIZE_BITS = 2048

        /** RSA recommended key size in bits */
        const val RSA_RECOMMENDED_KEY_SIZE_BITS = 4096

        /** Elliptic curve minimum key size in bits */
        const val EC_MIN_KEY_SIZE_BITS = 256

        /** Elliptic curve recommended key size in bits */
        const val EC_RECOMMENDED_KEY_SIZE_BITS = 384

        /** Ed25519 key size in bits */
        const val ED25519_KEY_SIZE_BITS = 256

        /** GCM initialization vector length in bytes */
        const val GCM_IV_LENGTH_BYTES = 12

        /** GCM authentication tag length in bytes */
        const val GCM_TAG_LENGTH_BYTES = 16

        /** GCM authentication tag length in bits */
        const val GCM_TAG_LENGTH_BITS = 128

        /** Default salt length for key derivation in bytes */
        const val DEFAULT_SALT_LENGTH_BYTES = 32

        /** Default biometric authentication validity duration in seconds */
        const val DEFAULT_BIOMETRIC_VALIDITY_SECONDS = 300

        /** Encrypted data format version */
        const val ENCRYPTED_DATA_VERSION = 1

        /** Header size for encrypted data packages in bytes */
        const val ENCRYPTED_DATA_HEADER_SIZE_BYTES = 8

        /** Default compression threshold in bytes */
        const val COMPRESSION_THRESHOLD_BYTES = 1024
    }

    /**
     * Network and connection constants.
     */
    object Network {
        /** Default connection timeout in milliseconds */
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L

        /** Default read timeout in milliseconds */
        const val DEFAULT_READ_TIMEOUT_MS = 30_000L

        /** Default write timeout in milliseconds */
        const val DEFAULT_WRITE_TIMEOUT_MS = 30_000L

        /** Default ping interval in milliseconds */
        const val DEFAULT_PING_INTERVAL_MS = 30_000L

        /** Extended connection timeout for slow networks in milliseconds */
        const val EXTENDED_CONNECTION_TIMEOUT_MS = 60_000L

        /** DNS resolution timeout in milliseconds */
        const val DNS_TIMEOUT_MS = 5_000L

        /** Default maximum reconnection attempts */
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5

        /** Base delay for exponential backoff reconnection in milliseconds */
        const val RECONNECT_BASE_DELAY_MS = 1_000L

        /** Maximum number of concurrent connections for testing */
        const val MAX_CONCURRENT_CONNECTIONS = 5

        /** Default retry count for connection attempts */
        const val DEFAULT_RETRY_COUNT = 2

        /** Default retry count for error handling */
        const val DEFAULT_ERROR_RETRY_COUNT = 3

        /** Delay between retry attempts in milliseconds */
        const val RETRY_DELAY_MS = 500L

        /** Base delay for exponential backoff in error handling in milliseconds */
        const val ERROR_BACKOFF_BASE_DELAY_MS = 1_000L
    }

    /**
     * Storage and backup constants.
     */
    object Storage {
        /** Maximum backup age in days */
        const val MAX_BACKUP_AGE_DAYS = 30

        /** Backup format version */
        const val BACKUP_VERSION = 1

        /** Storage format version */
        const val STORAGE_VERSION = 1

        /** Default maximum number of backups to keep */
        const val DEFAULT_MAX_BACKUP_COUNT = 10

        /** File chunk size for large file operations in bytes */
        const val FILE_CHUNK_SIZE_BYTES = 8192

        /** Maximum file size for single operations in bytes (10MB) */
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L
    }

    /**
     * Time and date constants.
     */
    object Time {
        /** Milliseconds per second */
        const val MILLIS_PER_SECOND = 1_000L

        /** Seconds per minute */
        const val SECONDS_PER_MINUTE = 60L

        /** Minutes per hour */
        const val MINUTES_PER_HOUR = 60L

        /** Hours per day */
        const val HOURS_PER_DAY = 24L

        /** Milliseconds per minute */
        const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE

        /** Milliseconds per hour */
        const val MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR

        /** Milliseconds per day */
        const val MILLIS_PER_DAY = MILLIS_PER_HOUR * HOURS_PER_DAY
    }

    /**
     * Android system constants.
     */
    object Android {
        /** Android API level for notification channels (API 26) */
        const val API_LEVEL_NOTIFICATION_CHANNELS = 26

        /** Android API level for StrongBox support (API 28) */
        const val API_LEVEL_STRONGBOX = 28

        /** Android API level for foreground services (API 26) */
        const val API_LEVEL_FOREGROUND_SERVICES = 26
    }

    /**
     * Notification constants.
     */
    object Notifications {
        /** Default notification ID for background monitoring service */
        const val BACKGROUND_MONITORING_NOTIFICATION_ID = 1

        /** Base notification ID for connection alerts */
        const val CONNECTION_ALERT_NOTIFICATION_ID_BASE = 1000

        /** Base notification ID for security alerts */
        const val SECURITY_ALERT_NOTIFICATION_ID_BASE = 2000
    }

    /**
     * Validation constants.
     */
    object Validation {
        /** Minimum SSH key size in bits */
        const val MIN_SSH_KEY_SIZE_BITS = 2048

        /** Maximum allowed hostname length */
        const val MAX_HOSTNAME_LENGTH = 255

        /** Maximum allowed username length */
        const val MAX_USERNAME_LENGTH = 64

        /** Minimum port number */
        const val MIN_PORT_NUMBER = 1

        /** Maximum port number */
        const val MAX_PORT_NUMBER = 65535

        /** Default SSH port */
        const val DEFAULT_SSH_PORT = 22

        /** Default HTTP port */
        const val DEFAULT_HTTP_PORT = 80

        /** Default HTTPS port */
        const val DEFAULT_HTTPS_PORT = 443
    }

    /**
     * Binary data constants.
     */
    object Binary {
        /** Byte array size for integer conversion */
        const val INT_BYTE_SIZE = 4

        /** Bit shift for first byte */
        const val BYTE_SHIFT_24 = 24

        /** Bit shift for second byte */
        const val BYTE_SHIFT_16 = 16

        /** Bit shift for third byte */
        const val BYTE_SHIFT_8 = 8

        /** Byte mask for unsigned conversion */
        const val UNSIGNED_BYTE_MASK = 0xFF

        /** HTTP success response code range start */
        const val HTTP_SUCCESS_RANGE_START = 200

        /** HTTP success response code range end */
        const val HTTP_SUCCESS_RANGE_END = 299

        /** HTTP not found response code */
        const val HTTP_NOT_FOUND = 404
    }
}
