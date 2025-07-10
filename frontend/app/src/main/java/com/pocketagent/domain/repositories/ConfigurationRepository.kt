package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application configuration and preferences.
 * 
 * This interface defines the contract for managing app configuration,
 * user preferences, and settings with encrypted storage support.
 * 
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface ConfigurationRepository {
    
    /**
     * Gets a configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The configuration value
     */
    suspend fun getString(key: String, defaultValue: String? = null): Result<String?>
    
    /**
     * Sets a configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setString(key: String, value: String): Result<Unit>
    
    /**
     * Gets a boolean configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The boolean value
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Result<Boolean>
    
    /**
     * Sets a boolean configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setBoolean(key: String, value: Boolean): Result<Unit>
    
    /**
     * Gets an integer configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The integer value
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Result<Int>
    
    /**
     * Sets an integer configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setInt(key: String, value: Int): Result<Unit>
    
    /**
     * Gets a long configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The long value
     */
    suspend fun getLong(key: String, defaultValue: Long = 0L): Result<Long>
    
    /**
     * Sets a long configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setLong(key: String, value: Long): Result<Unit>
    
    /**
     * Gets a float configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The float value
     */
    suspend fun getFloat(key: String, defaultValue: Float = 0f): Result<Float>
    
    /**
     * Sets a float configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setFloat(key: String, value: Float): Result<Unit>
    
    /**
     * Gets a string set configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value if key doesn't exist
     * @return The string set value
     */
    suspend fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Result<Set<String>>
    
    /**
     * Sets a string set configuration value.
     * 
     * @param key The configuration key
     * @param value The value to set
     * @return Success or error result
     */
    suspend fun setStringSet(key: String, value: Set<String>): Result<Unit>
    
    /**
     * Checks if a configuration key exists.
     * 
     * @param key The configuration key
     * @return True if the key exists
     */
    suspend fun contains(key: String): Result<Boolean>
    
    /**
     * Removes a configuration key.
     * 
     * @param key The configuration key
     * @return Success or error result
     */
    suspend fun remove(key: String): Result<Unit>
    
    /**
     * Gets all configuration keys.
     * 
     * @return Set of all configuration keys
     */
    suspend fun getAllKeys(): Result<Set<String>>
    
    /**
     * Observes a configuration value for changes.
     * 
     * @param key The configuration key
     * @return Flow emitting value changes
     */
    fun observeString(key: String): Flow<String?>
    
    /**
     * Observes a boolean configuration value for changes.
     * 
     * @param key The configuration key
     * @return Flow emitting boolean value changes
     */
    fun observeBoolean(key: String): Flow<Boolean>
    
    /**
     * Observes an integer configuration value for changes.
     * 
     * @param key The configuration key
     * @return Flow emitting integer value changes
     */
    fun observeInt(key: String): Flow<Int>
    
    /**
     * Gets user preferences.
     * 
     * @return User preferences
     */
    suspend fun getUserPreferences(): Result<UserPreferences>
    
    /**
     * Sets user preferences.
     * 
     * @param preferences The user preferences
     * @return Success or error result
     */
    suspend fun setUserPreferences(preferences: UserPreferences): Result<Unit>
    
    /**
     * Observes user preferences for changes.
     * 
     * @return Flow emitting user preferences updates
     */
    fun observeUserPreferences(): Flow<UserPreferences>
    
    /**
     * Gets app settings.
     * 
     * @return App settings
     */
    suspend fun getAppSettings(): Result<AppSettings>
    
    /**
     * Sets app settings.
     * 
     * @param settings The app settings
     * @return Success or error result
     */
    suspend fun setAppSettings(settings: AppSettings): Result<Unit>
    
    /**
     * Observes app settings for changes.
     * 
     * @return Flow emitting app settings updates
     */
    fun observeAppSettings(): Flow<AppSettings>
    
    /**
     * Gets security settings.
     * 
     * @return Security settings
     */
    suspend fun getSecuritySettings(): Result<SecuritySettings>
    
    /**
     * Sets security settings.
     * 
     * @param settings The security settings
     * @return Success or error result
     */
    suspend fun setSecuritySettings(settings: SecuritySettings): Result<Unit>
    
    /**
     * Observes security settings for changes.
     * 
     * @return Flow emitting security settings updates
     */
    fun observeSecuritySettings(): Flow<SecuritySettings>
    
    /**
     * Gets communication settings.
     * 
     * @return Communication settings
     */
    suspend fun getCommunicationSettings(): Result<CommunicationSettings>
    
    /**
     * Sets communication settings.
     * 
     * @param settings The communication settings
     * @return Success or error result
     */
    suspend fun setCommunicationSettings(settings: CommunicationSettings): Result<Unit>
    
    /**
     * Observes communication settings for changes.
     * 
     * @return Flow emitting communication settings updates
     */
    fun observeCommunicationSettings(): Flow<CommunicationSettings>
    
    /**
     * Gets developer settings.
     * 
     * @return Developer settings
     */
    suspend fun getDeveloperSettings(): Result<DeveloperSettings>
    
    /**
     * Sets developer settings.
     * 
     * @param settings The developer settings
     * @return Success or error result
     */
    suspend fun setDeveloperSettings(settings: DeveloperSettings): Result<Unit>
    
    /**
     * Observes developer settings for changes.
     * 
     * @return Flow emitting developer settings updates
     */
    fun observeDeveloperSettings(): Flow<DeveloperSettings>
    
    /**
     * Resets all settings to defaults.
     * 
     * @return Success or error result
     */
    suspend fun resetToDefaults(): Result<Unit>
    
    /**
     * Exports all configuration data.
     * 
     * @param includeSecrets Whether to include encrypted secrets
     * @return Exported configuration as JSON
     */
    suspend fun exportConfiguration(includeSecrets: Boolean = false): Result<String>
    
    /**
     * Imports configuration from exported data.
     * 
     * @param data The exported configuration data
     * @param overwrite Whether to overwrite existing settings
     * @return Success or error result
     */
    suspend fun importConfiguration(data: String, overwrite: Boolean = false): Result<Unit>
    
    /**
     * Backs up configuration to encrypted storage.
     * 
     * @return Success or error result
     */
    suspend fun backupConfiguration(): Result<Unit>
    
    /**
     * Restores configuration from backup.
     * 
     * @return Success or error result
     */
    suspend fun restoreConfiguration(): Result<Unit>
    
    /**
     * Validates configuration integrity.
     * 
     * @return Validation result
     */
    suspend fun validateConfiguration(): Result<ConfigurationValidationResult>
    
    /**
     * Migrates configuration from old version.
     * 
     * @param fromVersion The version to migrate from
     * @return Success or error result
     */
    suspend fun migrateConfiguration(fromVersion: Int): Result<Unit>
    
    /**
     * Gets configuration metadata.
     * 
     * @return Configuration metadata
     */
    suspend fun getConfigurationMetadata(): Result<ConfigurationMetadata>
    
    /**
     * Synchronizes configuration with cloud backup.
     * 
     * @return Success or error result
     */
    suspend fun syncConfiguration(): Result<Unit>
    
    /**
     * Clears all configuration data.
     * 
     * @return Success or error result
     */
    suspend fun clearAll(): Result<Unit>
}

/**
 * Represents user preferences.
 */
data class UserPreferences(
    val theme: Theme = Theme.SYSTEM,
    val language: String = "en",
    val fontSize: FontSize = FontSize.MEDIUM,
    val codeHighlighting: Boolean = true,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val autoSave: Boolean = true,
    val notifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showWelcomeScreen: Boolean = true,
    val confirmExitApp: Boolean = true,
    val confirmDeleteOperations: Boolean = true,
    val enableAnalytics: Boolean = true,
    val enableCrashReporting: Boolean = true
)

/**
 * Represents app settings.
 */
data class AppSettings(
    val autoUpdateEnabled: Boolean = true,
    val backgroundDataEnabled: Boolean = true,
    val lowDataModeEnabled: Boolean = false,
    val maxCacheSize: Long = 100 * 1024 * 1024, // 100MB
    val maxLogSize: Long = 10 * 1024 * 1024, // 10MB
    val logLevel: LogLevel = LogLevel.INFO,
    val enabledFeatures: Set<String> = emptySet(),
    val betaFeaturesEnabled: Boolean = false,
    val debugModeEnabled: Boolean = false,
    val performanceMonitoringEnabled: Boolean = false,
    val memoryOptimizationEnabled: Boolean = true,
    val batteryOptimizationEnabled: Boolean = true,
    val networkOptimizationEnabled: Boolean = true
)

/**
 * Represents security settings.
 */
data class SecuritySettings(
    val biometricAuthEnabled: Boolean = true,
    val deviceCredentialFallbackEnabled: Boolean = true,
    val sessionTimeoutMinutes: Int = 30,
    val autoLockEnabled: Boolean = true,
    val autoLockDelayMinutes: Int = 5,
    val certificatePinningEnabled: Boolean = true,
    val allowSelfSignedCertificates: Boolean = false,
    val auditLoggingEnabled: Boolean = true,
    val securityNotificationsEnabled: Boolean = true,
    val rootDetectionEnabled: Boolean = true,
    val allowInsecureConnections: Boolean = false,
    val encryptionLevel: EncryptionLevel = EncryptionLevel.HIGH,
    val keyRotationEnabled: Boolean = true,
    val keyRotationIntervalDays: Int = 30,
    val backupEncryptionEnabled: Boolean = true
)

/**
 * Represents communication settings.
 */
data class CommunicationSettings(
    val connectionTimeoutSeconds: Int = 30,
    val readTimeoutSeconds: Int = 60,
    val writeTimeoutSeconds: Int = 60,
    val keepAliveEnabled: Boolean = true,
    val keepAliveIntervalSeconds: Int = 30,
    val maxReconnectAttempts: Int = 3,
    val reconnectBackoffSeconds: Int = 5,
    val compressionEnabled: Boolean = true,
    val batchMessagesEnabled: Boolean = true,
    val maxBatchSize: Int = 10,
    val messageQueueSize: Int = 100,
    val heartbeatIntervalSeconds: Int = 60,
    val enableNetworkLogging: Boolean = false,
    val enableWebSocketLogging: Boolean = false,
    val networkOptimizationEnabled: Boolean = true,
    val adaptiveCompressionEnabled: Boolean = true
)

/**
 * Represents developer settings.
 */
data class DeveloperSettings(
    val debugLoggingEnabled: Boolean = false,
    val verboseLoggingEnabled: Boolean = false,
    val showPerformanceMetrics: Boolean = false,
    val showMemoryUsage: Boolean = false,
    val showNetworkStats: Boolean = false,
    val enableStrictMode: Boolean = false,
    val enableLayoutInspector: Boolean = false,
    val enableNetworkInspector: Boolean = false,
    val enableDatabaseInspector: Boolean = false,
    val mockDataEnabled: Boolean = false,
    val simulateNetworkErrors: Boolean = false,
    val simulateSlowNetwork: Boolean = false,
    val enableBetaFeatures: Boolean = false,
    val enableExperimentalFeatures: Boolean = false,
    val customApiEndpoint: String? = null,
    val customWebSocketEndpoint: String? = null,
    val overrideSecurityChecks: Boolean = false
)

/**
 * Represents theme options.
 */
enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Represents font size options.
 */
enum class FontSize {
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

/**
 * Represents log level options.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Represents encryption level options.
 */
enum class EncryptionLevel {
    BASIC,
    STANDARD,
    HIGH,
    MAXIMUM
}

/**
 * Represents configuration validation result.
 */
sealed class ConfigurationValidationResult {
    object Valid : ConfigurationValidationResult()
    data class Invalid(val errors: List<String>) : ConfigurationValidationResult()
    data class Warning(val warnings: List<String>) : ConfigurationValidationResult()
}

/**
 * Represents configuration metadata.
 */
data class ConfigurationMetadata(
    val version: Int,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val deviceId: String,
    val appVersion: String,
    val migrationHistory: List<Int>,
    val backupCount: Int,
    val totalSize: Long,
    val isEncrypted: Boolean,
    val checksumValid: Boolean
)