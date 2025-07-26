package com.pocketagent.data.service

/**
 * Exception hierarchy for service layer operations.
 *
 * These exceptions provide specific error types for different service operation scenarios
 * to enable better error handling and debugging in the service layer.
 */
sealed class ServiceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Exception thrown when SSH operations fail.
     */
    class SshOperationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when server connection operations fail.
     */
    class ServerConnectionException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when project operations fail.
     */
    class ProjectOperationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when network configuration validation fails.
     */
    class NetworkConfigurationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when service initialization fails.
     */
    class ServiceInitializationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when authentication operations fail.
     */
    class AuthenticationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when encryption/decryption operations fail.
     */
    class CryptographyException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when data parsing operations fail.
     */
    class ParseException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when service configuration is invalid.
     */
    class ConfigurationException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when service operations timeout.
     */
    class TimeoutException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when service is in an invalid state.
     */
    class InvalidStateException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)

    /**
     * Exception thrown when external dependency fails.
     */
    class ExternalDependencyException(
        message: String,
        cause: Throwable? = null,
    ) : ServiceException(message, cause)
}

/**
 * Specific exceptions for security manager operations.
 */
sealed class SecurityManagerException(
    message: String,
    cause: Throwable? = null,
) : ServiceException(message, cause) {
    /**
     * Exception thrown when key operations fail.
     */
    class KeyOperationException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)

    /**
     * Exception thrown when encryption operations fail.
     */
    class EncryptionException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)

    /**
     * Exception thrown when decryption operations fail.
     */
    class DecryptionException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)

    /**
     * Exception thrown when biometric authentication fails.
     */
    class BiometricAuthException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)

    /**
     * Exception thrown when key generation fails.
     */
    class KeyGenerationException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)

    /**
     * Exception thrown when key deletion fails.
     */
    class KeyDeletionException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityManagerException(message, cause)
}

/**
 * Specific exceptions for application initialization.
 */
sealed class ApplicationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Exception thrown when security provider initialization fails.
     */
    class SecurityProviderException(
        message: String,
        cause: Throwable? = null,
    ) : ApplicationException(message, cause)

    /**
     * Exception thrown when dependency injection fails.
     */
    class DependencyInjectionException(
        message: String,
        cause: Throwable? = null,
    ) : ApplicationException(message, cause)

    /**
     * Exception thrown when application configuration fails.
     */
    class ConfigurationException(
        message: String,
        cause: Throwable? = null,
    ) : ApplicationException(message, cause)

    /**
     * Exception thrown when external services fail during startup.
     */
    class ExternalServiceException(
        message: String,
        cause: Throwable? = null,
    ) : ApplicationException(message, cause)
}
