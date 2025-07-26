package com.pocketagent.common.constants

/**
 * Time-related constants used throughout the application.
 *
 * Centralizes all time-related magic numbers to improve maintainability
 * and avoid duplication of hardcoded time values.
 */
object TimeConstants {
    // Basic time units
    const val MILLIS_PER_SECOND = 1_000L
    const val SECONDS_PER_MINUTE = 60L
    const val MINUTES_PER_HOUR = 60L
    const val HOURS_PER_DAY = 24L
    const val DAYS_PER_WEEK = 7L
    const val DAYS_PER_MONTH = 30L
    const val DAYS_PER_YEAR = 365L

    // Computed time units
    const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE
    const val MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR
    const val MILLIS_PER_DAY = MILLIS_PER_HOUR * HOURS_PER_DAY
    const val MILLIS_PER_WEEK = MILLIS_PER_DAY * DAYS_PER_WEEK
    const val MILLIS_PER_MONTH = MILLIS_PER_DAY * DAYS_PER_MONTH
    const val MILLIS_PER_YEAR = MILLIS_PER_DAY * DAYS_PER_YEAR

    // Time intervals for time ago calculations
    const val TIME_AGO_JUST_NOW_THRESHOLD = 60_000L // 1 minute
    const val TIME_AGO_MINUTES_THRESHOLD = 3_600_000L // 1 hour
    const val TIME_AGO_HOURS_THRESHOLD = 86_400_000L // 1 day
    const val TIME_AGO_DAYS_THRESHOLD = 2_592_000_000L // 30 days

    // SSH key expiration defaults
    const val DEFAULT_SSH_KEY_EXPIRY_WARNING_DAYS = 30
    const val SSH_KEY_FINGERPRINT_DISPLAY_LENGTH = 16

    // Session and authentication timeouts
    const val DEFAULT_SESSION_TIMEOUT_MINUTES = 30
    const val DEFAULT_AUTO_LOCK_DELAY_MINUTES = 5
    const val DEFAULT_BIOMETRIC_VALIDITY_SECONDS = 300 // 5 minutes

    // Key rotation and backup intervals
    const val DEFAULT_KEY_ROTATION_INTERVAL_DAYS = 30
    const val MAX_BACKUP_AGE_DAYS = 30

    // Validation time ranges
    const val VALIDATION_ONE_YEAR_AGO = MILLIS_PER_YEAR
    const val VALIDATION_ONE_YEAR_FROM_NOW = MILLIS_PER_YEAR

    // Format patterns
    const val TIMESTAMP_FORMAT_PATTERN = "MMM dd, yyyy HH:mm"

    // Preview limits
    const val DEFAULT_PREVIEW_MAX_LENGTH = 100
}
