package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for security and authentication operations.
 *
 * This interface defines the contract for managing security operations,
 * including biometric authentication, encryption, token management, and
 * security auditing with hardware-backed security features.
 *
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface SecurityRepository {
    /**
     * Checks if biometric authentication is available.
     *
     * @return Biometric availability status
     */
    suspend fun getBiometricStatus(): Result<BiometricStatus>

    /**
     * Performs biometric authentication for app launch.
     *
     * @param title Title for the biometric prompt
     * @param subtitle Subtitle for the biometric prompt
     * @param description Optional description for the prompt
     * @return True if authentication is successful
     */
    suspend fun authenticateWithBiometrics(
        title: String,
        subtitle: String,
        description: String? = null,
    ): Result<Boolean>

    /**
     * Authenticates with device credentials as fallback.
     *
     * @param title Title for the authentication prompt
     * @param subtitle Subtitle for the authentication prompt
     * @return True if authentication is successful
     */
    suspend fun authenticateWithDeviceCredentials(
        title: String,
        subtitle: String,
    ): Result<Boolean>

    /**
     * Unlocks the app data encryption key with biometric authentication.
     * Called once at app launch.
     *
     * @return True if data key is unlocked
     */
    suspend fun unlockDataKey(): Result<Boolean>

    /**
     * Locks the app and clears cached sensitive data.
     * Called when app goes to background.
     *
     * @return Success or error result
     */
    suspend fun lockApp(): Result<Unit>

    /**
     * Observes authentication state changes.
     *
     * @return Flow emitting authentication state updates
     */
    fun observeAuthState(): Flow<AuthenticationState>

    /**
     * Checks if the current session is authenticated.
     *
     * @return True if session is authenticated
     */
    suspend fun isAuthenticated(): Result<Boolean>

    /**
     * Encrypts data using Android Keystore.
     *
     * @param data The data to encrypt
     * @param keyAlias The keystore alias
     * @param requireBiometric Whether the key requires biometric authentication
     * @return Encrypted data
     */
    suspend fun encryptData(
        data: ByteArray,
        keyAlias: String,
        requireBiometric: Boolean = false,
    ): Result<ByteArray>

    /**
     * Decrypts data using Android Keystore.
     * Uses authenticated session from app launch.
     *
     * @param encryptedData The encrypted data
     * @param keyAlias The keystore alias
     * @return Decrypted data
     */
    suspend fun decryptData(
        encryptedData: ByteArray,
        keyAlias: String,
    ): Result<ByteArray>

    /**
     * Generates a new encryption key in Android Keystore.
     *
     * @param keyAlias The keystore alias
     * @param requireBiometric Whether the key requires biometric authentication
     * @param useStrongBox Whether to use hardware security module if available
     * @return Success or error result
     */
    suspend fun generateKey(
        keyAlias: String,
        requireBiometric: Boolean = true,
        useStrongBox: Boolean = true,
    ): Result<Unit>

    /**
     * Deletes a key from Android Keystore.
     *
     * @param keyAlias The keystore alias
     * @return Success or error result
     */
    suspend fun deleteKey(keyAlias: String): Result<Unit>

    /**
     * Checks if a key exists in Android Keystore.
     *
     * @param keyAlias The keystore alias
     * @return True if the key exists
     */
    suspend fun keyExists(keyAlias: String): Result<Boolean>

    /**
     * Gets security status of the device.
     *
     * @return Device security status
     */
    suspend fun getDeviceSecurityStatus(): Result<DeviceSecurityStatus>

    /**
     * Validates security requirements for the app.
     *
     * @return Security validation result
     */
    suspend fun validateSecurityRequirements(): Result<SecurityValidationResult>

    /**
     * Stores a secure token in the token vault.
     *
     * @param service The service type
     * @param token The token to store
     * @param customServiceName Optional custom service name
     * @param expiresAt Optional expiration timestamp
     * @return Success or error result
     */
    suspend fun storeToken(
        service: TokenService,
        token: String,
        customServiceName: String? = null,
        expiresAt: Long? = null,
    ): Result<Unit>

    /**
     * Retrieves a secure token from the token vault.
     * Uses authenticated session from app launch.
     *
     * @param service The service type
     * @param customServiceName Optional custom service name
     * @return The stored token data
     */
    suspend fun getToken(
        service: TokenService,
        customServiceName: String? = null,
    ): Result<TokenData?>

    /**
     * Deletes a token from the token vault.
     *
     * @param service The service type
     * @param customServiceName Optional custom service name
     * @return Success or error result
     */
    suspend fun deleteToken(
        service: TokenService,
        customServiceName: String? = null,
    ): Result<Unit>

    /**
     * Lists all stored token services.
     *
     * @return List of service names
     */
    suspend fun getStoredTokenServices(): Result<List<String>>

    /**
     * Observes stored token services for real-time updates.
     *
     * @return Flow emitting service list updates
     */
    fun observeStoredTokenServices(): Flow<List<String>>

    /**
     * Clears all stored tokens.
     *
     * @return Success or error result
     */
    suspend fun clearAllTokens(): Result<Unit>

    /**
     * Validates a certificate for a hostname.
     *
     * @param hostname The hostname to validate
     * @param certificateData The certificate data
     * @return Certificate validation result
     */
    suspend fun validateCertificate(
        hostname: String,
        certificateData: ByteArray,
    ): Result<CertificateValidationResult>

    /**
     * Allows a self-signed certificate for a hostname.
     *
     * @param hostname The hostname
     * @return Success or error result
     */
    suspend fun allowSelfSignedCertificate(hostname: String): Result<Unit>

    /**
     * Revokes approval for a self-signed certificate.
     *
     * @param hostname The hostname
     * @return Success or error result
     */
    suspend fun revokeSelfSignedApproval(hostname: String): Result<Unit>

    /**
     * Gets security audit logs.
     *
     * @param limit Maximum number of logs to return
     * @return List of audit log entries
     */
    suspend fun getAuditLogs(limit: Int = 100): Result<List<SecurityAuditLog>>

    /**
     * Observes security audit logs for real-time updates.
     *
     * @return Flow emitting audit log updates
     */
    fun observeAuditLogs(): Flow<List<SecurityAuditLog>>

    /**
     * Logs a security event.
     *
     * @param eventType The type of security event
     * @param details Event details
     * @param success Whether the event was successful
     * @param errorCode Optional error code
     * @return Success or error result
     */
    suspend fun logSecurityEvent(
        eventType: SecurityEventType,
        details: String,
        success: Boolean,
        errorCode: String? = null,
    ): Result<Unit>

    /**
     * Exports security audit logs.
     *
     * @return Encrypted audit log data
     */
    suspend fun exportAuditLogs(): Result<ByteArray>

    /**
     * Clears old audit logs based on retention policy.
     *
     * @return Number of logs cleared
     */
    suspend fun clearOldAuditLogs(): Result<Int>

    /**
     * Migrates from unencrypted to encrypted storage.
     *
     * @return Success or error result
     */
    suspend fun migrateToEncryptedStorage(): Result<Unit>

    /**
     * Backs up security configuration.
     *
     * @return Encrypted backup data
     */
    suspend fun backupSecurityConfig(): Result<ByteArray>

    /**
     * Restores security configuration from backup.
     *
     * @param backupData The encrypted backup data
     * @return Success or error result
     */
    suspend fun restoreSecurityConfig(backupData: ByteArray): Result<Unit>

    /**
     * Clears all security data (for logout/reset).
     *
     * @return Success or error result
     */
    suspend fun clearAllSecurityData(): Result<Unit>
}

/**
 * Represents biometric authentication status.
 */
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE,
}

/**
 * Represents authentication state.
 */
enum class AuthenticationState {
    LOCKED,
    AUTHENTICATED,
    AUTHENTICATION_FAILED,
}

/**
 * Represents device security status.
 */
data class DeviceSecurityStatus(
    val isDeviceSecure: Boolean,
    val hasBiometricHardware: Boolean,
    val hasBiometricEnrolled: Boolean,
    val hasStrongBoxSupport: Boolean,
    val isRooted: Boolean,
)

/**
 * Represents security validation result.
 */
sealed class SecurityValidationResult {
    object Success : SecurityValidationResult()

    data class Warning(
        val message: String,
    ) : SecurityValidationResult()

    data class Error(
        val message: String,
    ) : SecurityValidationResult()
}

/**
 * Represents token service types.
 */
enum class TokenService(
    val displayName: String,
) {
    GITHUB("GitHub"),
    GITLAB("GitLab"),
    CUSTOM("Custom Git Server"),
}

/**
 * Represents stored token data.
 */
data class TokenData(
    val service: String,
    val tokenValue: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
)

/**
 * Represents certificate validation result.
 */
sealed class CertificateValidationResult {
    object Valid : CertificateValidationResult()

    data class Invalid(
        val reason: String,
    ) : CertificateValidationResult()

    data class RequiresUserApproval(
        val certificateData: ByteArray,
    ) : CertificateValidationResult()
}

/**
 * Represents security event types.
 */
enum class SecurityEventType {
    TOKEN_ACCESS,
    TOKEN_ADDED,
    TOKEN_DELETED,
    SSH_KEY_IMPORT,
    SSH_KEY_ACCESS,
    SSH_KEY_DELETED,
    BIOMETRIC_AUTH,
    APP_LOCKED,
    APP_UNLOCKED,
    PERMISSION_REQUEST,
    SECURITY_VALIDATION,
    CERTIFICATE_VALIDATION,
}

/**
 * Represents a security audit log entry.
 */
data class SecurityAuditLog(
    val id: String,
    val eventType: SecurityEventType,
    val eventDetails: String,
    val success: Boolean,
    val timestamp: Long,
    val errorCode: String? = null,
)
