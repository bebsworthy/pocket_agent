package com.pocketagent.common.constants

/**
 * Validation-related constants used throughout the application.
 *
 * Centralizes all validation-related magic numbers including
 * length limits, validation thresholds, and format constraints.
 */
object ValidationConstants {
    // SSH key validation
    const val MIN_SSH_KEY_SIZE_BITS = 2048

    // RSA key sizes
    const val RSA_VALID_KEY_SIZE_2048 = 2048
    const val RSA_VALID_KEY_SIZE_3072 = 3072
    const val RSA_VALID_KEY_SIZE_4096 = 4096

    // ECDSA key sizes
    const val ECDSA_VALID_KEY_SIZE_256 = 256
    const val ECDSA_VALID_KEY_SIZE_384 = 384
    const val ECDSA_VALID_KEY_SIZE_521 = 521

    // Ed25519 key size
    const val ED25519_VALID_KEY_SIZE = 256

    // DSA key sizes
    const val DSA_VALID_KEY_SIZE_1024 = 1024
    const val DSA_VALID_KEY_SIZE_2048 = 2048
    const val DSA_VALID_KEY_SIZE_3072 = 3072

    // String length validation
    const val MIN_STRING_LENGTH = 1
    const val MAX_EMAIL_LENGTH = 256
    const val MAX_HOSTNAME_LENGTH = 255
    const val MAX_USERNAME_LENGTH = 64
    const val MAX_SSH_USERNAME_LENGTH = 32
    const val MAX_NAME_LENGTH = 100
    const val MIN_NAME_LENGTH = 1
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MAX_PATH_LENGTH = 4096
    const val MAX_ERROR_MESSAGE_LENGTH = 1000

    // Port validation
    const val MIN_PORT_NUMBER = 1
    const val MAX_PORT_NUMBER = 65535

    // Default ports for validation
    const val DEFAULT_SSH_PORT = 22
    const val DEFAULT_HTTP_PORT = 80
    const val DEFAULT_HTTPS_PORT = 443

    // Battery validation
    const val MIN_BATTERY_THRESHOLD = 1
    const val MAX_BATTERY_THRESHOLD = 100

    // Language code validation
    const val MIN_LANGUAGE_CODE_LENGTH = 2
    const val MAX_LANGUAGE_CODE_LENGTH = 10

    // UUID validation pattern components
    const val UUID_SEGMENT_LENGTH_8 = 8
    const val UUID_SEGMENT_LENGTH_4 = 4
    const val UUID_SEGMENT_LENGTH_12 = 12

    // Regex pattern length limits
    const val EMAIL_MAX_LOCAL_PART = 256
    const val EMAIL_MAX_DOMAIN_PART = 64
    const val EMAIL_MAX_SUBDOMAIN_PART = 25
    const val HOSTNAME_MAX_SEGMENT_LENGTH = 61
    const val USERNAME_MAX_LENGTH = 31 // 32 total with first char

    // Content size validation
    const val MAX_MESSAGE_CONTENT_LENGTH = 100_000 // 100KB
    const val MAX_ATTACHMENT_CONTENT_LENGTH = 1_000_000 // 1MB
    const val MAX_ATTACHMENT_NAME_LENGTH = 255
    const val MAX_MIME_TYPE_LENGTH = 100

    // Collection size validation
    const val MAX_TOOLS_LIST_SIZE = 100
    const val MAX_AUTO_APPROVE_PATTERNS_SIZE = 50
    const val MIN_PROJECT_TURNS = 1
    const val MAX_PROJECT_TURNS = 1000
    const val MIN_MESSAGE_HISTORY = 100
    const val MAX_MESSAGE_HISTORY = 10000

    // Timeout validation ranges (in milliseconds)
    const val MIN_TIMEOUT_MS = 1000L // 1 second
    const val MAX_CONNECTION_TIMEOUT_MS = 300_000L // 5 minutes
    const val MAX_READ_WRITE_TIMEOUT_MS = 600_000L // 10 minutes

    // Retry validation
    const val MIN_RETRY_COUNT = 0
    const val MAX_RETRY_COUNT = 10

    // File size validation (in bytes)
    const val MIN_FILE_SIZE = 0L
    const val MAX_SINGLE_FILE_SIZE = 10 * 1024 * 1024L // 10MB

    // Custom prompt validation
    const val MAX_CUSTOM_PROMPT_KEY_LENGTH = 100
    const val MAX_CUSTOM_PROMPT_VALUE_LENGTH = 10000

    // SSH fingerprint display length
    const val SSH_FINGERPRINT_DISPLAY_LENGTH = 16
}
