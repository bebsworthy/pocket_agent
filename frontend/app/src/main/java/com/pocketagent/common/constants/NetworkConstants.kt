package com.pocketagent.common.constants

/**
 * Network-related constants used throughout the application.
 *
 * Centralizes all network-related magic numbers including ports,
 * timeouts, and connection limits to improve maintainability.
 */
object NetworkConstants {
    // Standard ports
    const val DEFAULT_SSH_PORT = 22
    const val DEFAULT_HTTP_PORT = 80
    const val DEFAULT_HTTPS_PORT = 443
    const val DEFAULT_WRAPPER_PORT = 8080

    // Port ranges
    const val MIN_PORT_NUMBER = 1
    const val MAX_PORT_NUMBER = 65535

    // Connection timeouts (in milliseconds)
    const val DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L // 30 seconds
    const val DEFAULT_READ_TIMEOUT_MS = 30_000L // 30 seconds
    const val DEFAULT_WRITE_TIMEOUT_MS = 30_000L // 30 seconds
    const val EXTENDED_CONNECTION_TIMEOUT_MS = 60_000L // 60 seconds
    const val DNS_TIMEOUT_MS = 5_000L // 5 seconds

    // Connection timeouts (in seconds for configuration)
    const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30
    const val DEFAULT_READ_TIMEOUT_SECONDS = 60
    const val DEFAULT_WRITE_TIMEOUT_SECONDS = 60
    const val MIN_CONNECTION_TIMEOUT_SECONDS = 1
    const val MAX_CONNECTION_TIMEOUT_SECONDS = 300 // 5 minutes

    // Keep-alive settings
    const val DEFAULT_PING_INTERVAL_MS = 30_000L // 30 seconds
    const val DEFAULT_KEEP_ALIVE_INTERVAL_MS = 60_000L // 1 minute
    const val DEFAULT_KEEP_ALIVE_INTERVAL_SECONDS = 30
    const val MIN_KEEP_ALIVE_INTERVAL_MS = 1_000L // 1 second
    const val MAX_KEEP_ALIVE_INTERVAL_MS = 600_000L // 10 minutes
    const val DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60

    // Retry and reconnection settings
    const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
    const val DEFAULT_RETRY_COUNT = 2
    const val DEFAULT_ERROR_RETRY_COUNT = 3
    const val DEFAULT_MAX_RETRIES = 3
    const val MIN_MAX_RETRIES = 0
    const val MAX_MAX_RETRIES = 10

    // Retry delays (in milliseconds)
    const val RETRY_DELAY_MS = 500L
    const val RECONNECT_BASE_DELAY_MS = 1_000L // 1 second
    const val ERROR_BACKOFF_BASE_DELAY_MS = 1_000L // 1 second
    const val DEFAULT_RECONNECT_BACKOFF_SECONDS = 5

    // Connection limits
    const val MAX_CONCURRENT_CONNECTIONS = 5

    // HTTP status codes
    const val HTTP_SUCCESS_RANGE_START = 200
    const val HTTP_SUCCESS_RANGE_END = 299
    const val HTTP_NOT_FOUND = 404

    // Server connection settings defaults
    const val SERVER_DEFAULT_CONNECTION_TIMEOUT = 30_000L // 30 seconds
    const val SERVER_DEFAULT_KEEP_ALIVE_INTERVAL = 60_000L // 1 minute
    const val SERVER_DEFAULT_MAX_RETRIES = 3

    // Validation ranges for timeouts
    const val MIN_TIMEOUT_MS = 1_000L // 1 second minimum
    const val MAX_TIMEOUT_MS = 300_000L // 5 minutes maximum
    const val MIN_READ_TIMEOUT_MS = 1_000L
    const val MAX_READ_TIMEOUT_MS = 600_000L // 10 minutes
    const val MIN_WRITE_TIMEOUT_MS = 1_000L
    const val MAX_WRITE_TIMEOUT_MS = 600_000L // 10 minutes
}
