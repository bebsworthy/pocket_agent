package com.pocketagent.testing

/**
 * Configuration constants for testing framework.
 */
object TestConfiguration {
    // Test timeouts
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val LONG_TIMEOUT_MS = 10000L
    const val SHORT_TIMEOUT_MS = 1000L

    // Test data constants
    const val TEST_PROJECT_ID = "test-project-id"
    const val TEST_SERVER_ID = "test-server-id"
    const val TEST_SSH_KEY_ID = "test-ssh-key-id"
    const val TEST_SESSION_ID = "test-session-id"

    // Test server URLs
    const val TEST_WEBSOCKET_URL = "ws://localhost:8080/ws"
    const val TEST_SERVER_HOST = "localhost"
    const val TEST_SERVER_PORT = 8080

    // Test user data
    const val TEST_USERNAME = "testuser"
    const val TEST_PROJECT_NAME = "Test Project"
    const val TEST_SERVER_NAME = "Test Server"
    const val TEST_SSH_KEY_NAME = "Test SSH Key"

    // Test file paths
    const val TEST_PROJECT_PATH = "/home/testuser/test-project"
    const val TEST_SCRIPTS_FOLDER = "scripts"

    // Test message content
    const val TEST_COMMAND = "test command"
    const val TEST_CLAUDE_RESPONSE = "Test Claude response"
    const val TEST_ERROR_MESSAGE = "Test error message"

    // Test SSH key data
    const val TEST_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC7VzYt..."
    const val TEST_PRIVATE_KEY =
        "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7W8bA8L5tJfMR\n...\n-----END PRIVATE KEY-----"

    // Test battery levels
    const val TEST_BATTERY_NORMAL = 60
    const val TEST_BATTERY_LOW = 25
    const val TEST_BATTERY_CRITICAL = 10
    const val TEST_BATTERY_CHARGING = 80

    // Test notification data
    const val TEST_NOTIFICATION_TITLE = "Test Notification"
    const val TEST_NOTIFICATION_CONTENT = "Test notification content"
    const val TEST_PERMISSION_REQUEST_ID = "test-permission-123"

    // Test WebSocket message types
    const val MESSAGE_TYPE_COMMAND = "command"
    const val MESSAGE_TYPE_CLAUDE_RESPONSE = "claude_response"
    const val MESSAGE_TYPE_PERMISSION_REQUEST = "permission_request"
    const val MESSAGE_TYPE_PERMISSION_RESPONSE = "permission_response"
    const val MESSAGE_TYPE_AUTH_CHALLENGE = "auth_challenge"
    const val MESSAGE_TYPE_AUTH_RESPONSE = "auth_response"
    const val MESSAGE_TYPE_AUTH_SUCCESS = "auth_success"

    // Test connection states
    const val STATE_CONNECTED = "CONNECTED"
    const val STATE_CONNECTING = "CONNECTING"
    const val STATE_DISCONNECTED = "DISCONNECTED"
    const val STATE_ERROR = "ERROR"

    // Test coverage thresholds
    const val MIN_COVERAGE_PERCENTAGE = 70.0
    const val TARGET_COVERAGE_PERCENTAGE = 80.0

    // Test performance thresholds
    const val MAX_STARTUP_TIME_MS = 3000L
    const val MAX_RESPONSE_TIME_MS = 1000L
    const val MAX_MEMORY_USAGE_MB = 100L

    // Test retry configuration
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 1000L

    // Test environment flags
    const val IS_CI_ENVIRONMENT = System.getenv("CI") == "true"
    const val IS_DEBUG_MODE = System.getProperty("debug.tests") == "true"

    /**
     * Creates a test configuration for different environments.
     */
    fun createTestConfig(isCI: Boolean = IS_CI_ENVIRONMENT): TestConfig =
        TestConfig(
            timeoutMs = if (isCI) LONG_TIMEOUT_MS else DEFAULT_TIMEOUT_MS,
            retryAttempts = if (isCI) MAX_RETRY_ATTEMPTS else 1,
            enableLogging = !isCI,
            parallelExecution = !isCI,
        )
}

/**
 * Configuration for test execution.
 */
data class TestConfig(
    val timeoutMs: Long,
    val retryAttempts: Int,
    val enableLogging: Boolean,
    val parallelExecution: Boolean,
)

/**
 * Test environment setup and configuration.
 */
object TestEnvironment {
    /**
     * Sets up test environment for unit tests.
     */
    fun setupUnitTestEnvironment() {
        // Set system properties for testing
        System.setProperty("robolectric.offline", "true")
        System.setProperty("robolectric.logging", "stdout")

        // Configure test logging
        if (TestConfiguration.IS_DEBUG_MODE) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
        }
    }

    /**
     * Sets up test environment for instrumentation tests.
     */
    fun setupInstrumentationTestEnvironment() {
        // Configure instrumentation test environment
        // This would include setting up test database, clearing app data, etc.
    }

    /**
     * Cleans up test environment after tests.
     */
    fun cleanupTestEnvironment() {
        // Clean up any test resources
        // This would include closing connections, clearing caches, etc.
    }
}

/**
 * Test assertion helpers for common validation patterns.
 */
object TestAssertions {
    /**
     * Asserts that a value is within expected time range.
     */
    fun assertTimeWithinRange(
        actual: Long,
        expected: Long,
        toleranceMs: Long = 100,
    ) {
        val diff = kotlin.math.abs(actual - expected)
        assert(diff <= toleranceMs) {
            "Time difference ($diff ms) exceeds tolerance ($toleranceMs ms). Expected: $expected, Actual: $actual"
        }
    }

    /**
     * Asserts that a collection contains all expected items.
     */
    fun <T> assertContainsAll(
        actual: Collection<T>,
        expected: Collection<T>,
    ) {
        assert(actual.containsAll(expected)) {
            "Collection does not contain all expected items. Missing: ${expected - actual.toSet()}"
        }
    }

    /**
     * Asserts that a string matches a pattern.
     */
    fun assertMatches(
        actual: String,
        pattern: String,
    ) {
        assert(actual.matches(Regex(pattern))) {
            "String '$actual' does not match pattern '$pattern'"
        }
    }

    /**
     * Asserts that a value is within percentage range.
     */
    fun assertWithinPercentage(
        actual: Double,
        expected: Double,
        percentage: Double = 5.0,
    ) {
        val tolerance = expected * (percentage / 100.0)
        val diff = kotlin.math.abs(actual - expected)
        assert(diff <= tolerance) {
            "Value difference ($diff) exceeds $percentage% tolerance ($tolerance). Expected: $expected, Actual: $actual"
        }
    }
}
