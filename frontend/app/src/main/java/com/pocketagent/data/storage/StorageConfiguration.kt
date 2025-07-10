package com.pocketagent.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketagent.domain.models.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration manager for storage settings and preferences.
 * 
 * This class manages configuration settings for the encrypted storage system
 * including security settings, backup preferences, and performance options.
 */
@Singleton
class StorageConfiguration @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFERENCES_NAME = "storage_config"
        
        // Configuration keys
        private val KEY_ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        private val KEY_COMPRESSION_ENABLED = booleanPreferencesKey("compression_enabled")
        private val KEY_COMPRESSION_THRESHOLD = intPreferencesKey("compression_threshold")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_BACKUP_INTERVAL = longPreferencesKey("backup_interval")
        private val KEY_MAX_BACKUP_COUNT = intPreferencesKey("max_backup_count")
        private val KEY_INTEGRITY_CHECK_ENABLED = booleanPreferencesKey("integrity_check_enabled")
        private val KEY_STORAGE_VERSION = intPreferencesKey("storage_version")
        private val KEY_LAST_CLEANUP_TIME = longPreferencesKey("last_cleanup_time")
        private val KEY_CLEANUP_INTERVAL = longPreferencesKey("cleanup_interval")
        private val KEY_MAX_STORAGE_SIZE = longPreferencesKey("max_storage_size")
        private val KEY_SECURE_DELETE_ENABLED = booleanPreferencesKey("secure_delete_enabled")
        private val KEY_BIOMETRIC_REQUIRED = booleanPreferencesKey("biometric_required")
        private val KEY_SESSION_TIMEOUT = longPreferencesKey("session_timeout")
        
        // Default values
        private const val DEFAULT_ENCRYPTION_ENABLED = true
        private const val DEFAULT_COMPRESSION_ENABLED = true
        private const val DEFAULT_COMPRESSION_THRESHOLD = 1024 // bytes
        private const val DEFAULT_AUTO_BACKUP_ENABLED = true
        private const val DEFAULT_BACKUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
        private const val DEFAULT_MAX_BACKUP_COUNT = 3
        private const val DEFAULT_INTEGRITY_CHECK_ENABLED = true
        private const val DEFAULT_STORAGE_VERSION = 1
        private const val DEFAULT_CLEANUP_INTERVAL = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val DEFAULT_MAX_STORAGE_SIZE = 50 * 1024 * 1024L // 50 MB
        private const val DEFAULT_SECURE_DELETE_ENABLED = true
        private const val DEFAULT_BIOMETRIC_REQUIRED = false
        private const val DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000L // 30 minutes
    }
    
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)
    
    /**
     * Data class representing storage configuration.
     */
    data class StorageConfig(
        val encryptionEnabled: Boolean = DEFAULT_ENCRYPTION_ENABLED,
        val compressionEnabled: Boolean = DEFAULT_COMPRESSION_ENABLED,
        val compressionThreshold: Int = DEFAULT_COMPRESSION_THRESHOLD,
        val autoBackupEnabled: Boolean = DEFAULT_AUTO_BACKUP_ENABLED,
        val backupInterval: Long = DEFAULT_BACKUP_INTERVAL,
        val maxBackupCount: Int = DEFAULT_MAX_BACKUP_COUNT,
        val integrityCheckEnabled: Boolean = DEFAULT_INTEGRITY_CHECK_ENABLED,
        val storageVersion: Int = DEFAULT_STORAGE_VERSION,
        val lastCleanupTime: Long = 0L,
        val cleanupInterval: Long = DEFAULT_CLEANUP_INTERVAL,
        val maxStorageSize: Long = DEFAULT_MAX_STORAGE_SIZE,
        val secureDeleteEnabled: Boolean = DEFAULT_SECURE_DELETE_ENABLED,
        val biometricRequired: Boolean = DEFAULT_BIOMETRIC_REQUIRED,
        val sessionTimeout: Long = DEFAULT_SESSION_TIMEOUT
    )
    
    /**
     * Gets the current storage configuration.
     * 
     * @return Current configuration or error result
     */
    suspend fun getConfiguration(): Result<StorageConfig> {
        return try {
            val preferences = context.dataStore.data.first()
            
            val config = StorageConfig(
                encryptionEnabled = preferences[KEY_ENCRYPTION_ENABLED] ?: DEFAULT_ENCRYPTION_ENABLED,
                compressionEnabled = preferences[KEY_COMPRESSION_ENABLED] ?: DEFAULT_COMPRESSION_ENABLED,
                compressionThreshold = preferences[KEY_COMPRESSION_THRESHOLD] ?: DEFAULT_COMPRESSION_THRESHOLD,
                autoBackupEnabled = preferences[KEY_AUTO_BACKUP_ENABLED] ?: DEFAULT_AUTO_BACKUP_ENABLED,
                backupInterval = preferences[KEY_BACKUP_INTERVAL] ?: DEFAULT_BACKUP_INTERVAL,
                maxBackupCount = preferences[KEY_MAX_BACKUP_COUNT] ?: DEFAULT_MAX_BACKUP_COUNT,
                integrityCheckEnabled = preferences[KEY_INTEGRITY_CHECK_ENABLED] ?: DEFAULT_INTEGRITY_CHECK_ENABLED,
                storageVersion = preferences[KEY_STORAGE_VERSION] ?: DEFAULT_STORAGE_VERSION,
                lastCleanupTime = preferences[KEY_LAST_CLEANUP_TIME] ?: 0L,
                cleanupInterval = preferences[KEY_CLEANUP_INTERVAL] ?: DEFAULT_CLEANUP_INTERVAL,
                maxStorageSize = preferences[KEY_MAX_STORAGE_SIZE] ?: DEFAULT_MAX_STORAGE_SIZE,
                secureDeleteEnabled = preferences[KEY_SECURE_DELETE_ENABLED] ?: DEFAULT_SECURE_DELETE_ENABLED,
                biometricRequired = preferences[KEY_BIOMETRIC_REQUIRED] ?: DEFAULT_BIOMETRIC_REQUIRED,
                sessionTimeout = preferences[KEY_SESSION_TIMEOUT] ?: DEFAULT_SESSION_TIMEOUT
            )
            
            Result.Success(config)
        } catch (e: Exception) {
            Result.Error(e, "Failed to get storage configuration: ${e.message}")
        }
    }
    
    /**
     * Updates the storage configuration.
     * 
     * @param config New configuration to save
     * @return Success or error result
     */
    suspend fun updateConfiguration(config: StorageConfig): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[KEY_ENCRYPTION_ENABLED] = config.encryptionEnabled
                preferences[KEY_COMPRESSION_ENABLED] = config.compressionEnabled
                preferences[KEY_COMPRESSION_THRESHOLD] = config.compressionThreshold
                preferences[KEY_AUTO_BACKUP_ENABLED] = config.autoBackupEnabled
                preferences[KEY_BACKUP_INTERVAL] = config.backupInterval
                preferences[KEY_MAX_BACKUP_COUNT] = config.maxBackupCount
                preferences[KEY_INTEGRITY_CHECK_ENABLED] = config.integrityCheckEnabled
                preferences[KEY_STORAGE_VERSION] = config.storageVersion
                preferences[KEY_LAST_CLEANUP_TIME] = config.lastCleanupTime
                preferences[KEY_CLEANUP_INTERVAL] = config.cleanupInterval
                preferences[KEY_MAX_STORAGE_SIZE] = config.maxStorageSize
                preferences[KEY_SECURE_DELETE_ENABLED] = config.secureDeleteEnabled
                preferences[KEY_BIOMETRIC_REQUIRED] = config.biometricRequired
                preferences[KEY_SESSION_TIMEOUT] = config.sessionTimeout
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update storage configuration: ${e.message}")
        }
    }
    
    /**
     * Observes configuration changes.
     * 
     * @return Flow of configuration changes
     */
    fun observeConfiguration(): Flow<StorageConfig> {
        return context.dataStore.data.map { preferences ->
            StorageConfig(
                encryptionEnabled = preferences[KEY_ENCRYPTION_ENABLED] ?: DEFAULT_ENCRYPTION_ENABLED,
                compressionEnabled = preferences[KEY_COMPRESSION_ENABLED] ?: DEFAULT_COMPRESSION_ENABLED,
                compressionThreshold = preferences[KEY_COMPRESSION_THRESHOLD] ?: DEFAULT_COMPRESSION_THRESHOLD,
                autoBackupEnabled = preferences[KEY_AUTO_BACKUP_ENABLED] ?: DEFAULT_AUTO_BACKUP_ENABLED,
                backupInterval = preferences[KEY_BACKUP_INTERVAL] ?: DEFAULT_BACKUP_INTERVAL,
                maxBackupCount = preferences[KEY_MAX_BACKUP_COUNT] ?: DEFAULT_MAX_BACKUP_COUNT,
                integrityCheckEnabled = preferences[KEY_INTEGRITY_CHECK_ENABLED] ?: DEFAULT_INTEGRITY_CHECK_ENABLED,
                storageVersion = preferences[KEY_STORAGE_VERSION] ?: DEFAULT_STORAGE_VERSION,
                lastCleanupTime = preferences[KEY_LAST_CLEANUP_TIME] ?: 0L,
                cleanupInterval = preferences[KEY_CLEANUP_INTERVAL] ?: DEFAULT_CLEANUP_INTERVAL,
                maxStorageSize = preferences[KEY_MAX_STORAGE_SIZE] ?: DEFAULT_MAX_STORAGE_SIZE,
                secureDeleteEnabled = preferences[KEY_SECURE_DELETE_ENABLED] ?: DEFAULT_SECURE_DELETE_ENABLED,
                biometricRequired = preferences[KEY_BIOMETRIC_REQUIRED] ?: DEFAULT_BIOMETRIC_REQUIRED,
                sessionTimeout = preferences[KEY_SESSION_TIMEOUT] ?: DEFAULT_SESSION_TIMEOUT
            )
        }
    }
    
    /**
     * Resets configuration to default values.
     * 
     * @return Success or error result
     */
    suspend fun resetToDefaults(): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to reset configuration: ${e.message}")
        }
    }
    
    /**
     * Updates the last cleanup time.
     * 
     * @param timestamp The cleanup timestamp
     * @return Success or error result
     */
    suspend fun updateLastCleanupTime(timestamp: Long): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[KEY_LAST_CLEANUP_TIME] = timestamp
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update last cleanup time: ${e.message}")
        }
    }
    
    /**
     * Updates the storage version.
     * 
     * @param version The new storage version
     * @return Success or error result
     */
    suspend fun updateStorageVersion(version: Int): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[KEY_STORAGE_VERSION] = version
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update storage version: ${e.message}")
        }
    }
    
    /**
     * Checks if cleanup is needed based on configuration.
     * 
     * @return True if cleanup is needed, false otherwise
     */
    suspend fun isCleanupNeeded(): Result<Boolean> {
        return try {
            val config = getConfiguration().getOrThrow()
            val timeSinceLastCleanup = System.currentTimeMillis() - config.lastCleanupTime
            
            Result.Success(timeSinceLastCleanup >= config.cleanupInterval)
        } catch (e: Exception) {
            Result.Error(e, "Failed to check cleanup status: ${e.message}")
        }
    }
    
    /**
     * Checks if backup is needed based on configuration.
     * 
     * @param lastBackupTime The timestamp of the last backup
     * @return True if backup is needed, false otherwise
     */
    suspend fun isBackupNeeded(lastBackupTime: Long): Result<Boolean> {
        return try {
            val config = getConfiguration().getOrThrow()
            
            if (!config.autoBackupEnabled) {
                return@try Result.Success(false)
            }
            
            val timeSinceLastBackup = System.currentTimeMillis() - lastBackupTime
            
            Result.Success(timeSinceLastBackup >= config.backupInterval)
        } catch (e: Exception) {
            Result.Error(e, "Failed to check backup status: ${e.message}")
        }
    }
    
    /**
     * Validates configuration values.
     * 
     * @param config The configuration to validate
     * @return Success or error result
     */
    suspend fun validateConfiguration(config: StorageConfig): Result<Unit> {
        return try {
            require(config.compressionThreshold > 0) { "Compression threshold must be positive" }
            require(config.backupInterval > 0) { "Backup interval must be positive" }
            require(config.maxBackupCount > 0) { "Max backup count must be positive" }
            require(config.storageVersion > 0) { "Storage version must be positive" }
            require(config.cleanupInterval > 0) { "Cleanup interval must be positive" }
            require(config.maxStorageSize > 0) { "Max storage size must be positive" }
            require(config.sessionTimeout > 0) { "Session timeout must be positive" }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Configuration validation failed: ${e.message}")
        }
    }
}