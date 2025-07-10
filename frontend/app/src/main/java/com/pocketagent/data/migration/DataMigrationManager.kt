package com.pocketagent.data.migration

import android.content.Context
import android.util.Log
import com.pocketagent.data.models.AppData
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive data migration manager for handling versioned data migrations.
 * 
 * This manager provides:
 * - Automatic migration detection and execution
 * - Backup creation before migration
 * - Migration rollback support
 * - Data integrity validation during migration
 * - Progress tracking and logging
 * - Integration with encrypted storage system
 */
@Singleton
class DataMigrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedStorage: EncryptedJsonStorage,
    private val migrationRegistry: MigrationRegistry,
    private val dataValidator: DataValidator
) {
    
    companion object {
        private const val TAG = "DataMigrationManager"
        private const val MIGRATION_BACKUP_PREFIX = "migration_backup_"
        private const val MIGRATION_LOG_KEY = "migration_log"
        private const val MAX_ROLLBACK_ATTEMPTS = 3
    }
    
    private val migrationMutex = Mutex()
    private val _migrationProgress = MutableStateFlow<MigrationProgress?>(null)
    private val _isMigrating = MutableStateFlow(false)
    
    /**
     * Observable flow of current migration progress.
     */
    val migrationProgress: Flow<MigrationProgress?> = _migrationProgress.asStateFlow()
    
    /**
     * Observable flow indicating if a migration is currently in progress.
     */
    val isMigrating: Flow<Boolean> = _isMigrating.asStateFlow()
    
    private val progressObservers = mutableListOf<MigrationProgressObserver>()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    
    /**
     * Adds a migration progress observer.
     */
    fun addProgressObserver(observer: MigrationProgressObserver) {
        progressObservers.add(observer)
    }
    
    /**
     * Removes a migration progress observer.
     */
    fun removeProgressObserver(observer: MigrationProgressObserver) {
        progressObservers.remove(observer)
    }
    
    /**
     * Checks if migration is needed for the given data.
     * 
     * @param data The application data to check
     * @return true if migration is needed, false otherwise
     */
    suspend fun isMigrationNeeded(data: AppData): Boolean {
        val currentVersion = data.version
        val targetVersion = MigrationVersion.CURRENT_VERSION
        
        return currentVersion < targetVersion &&
                migrationRegistry.hasMigrationPath(currentVersion, targetVersion)
    }
    
    /**
     * Migrates the application data to the latest version.
     * 
     * @param data The current application data
     * @param createBackup Whether to create a backup before migration
     * @param forceValidation Whether to force validation even if migration seems unnecessary
     * @return MigrationResult indicating the outcome of the migration
     */
    suspend fun migrateToLatest(
        data: AppData,
        createBackup: Boolean = true,
        forceValidation: Boolean = false
    ): MigrationResult {
        return migrateToVersion(data, MigrationVersion.CURRENT_VERSION, createBackup, forceValidation)
    }
    
    /**
     * Migrates the application data to a specific version.
     * 
     * @param data The current application data
     * @param targetVersion The target version to migrate to
     * @param createBackup Whether to create a backup before migration
     * @param forceValidation Whether to force validation even if migration seems unnecessary
     * @return MigrationResult indicating the outcome of the migration
     */
    suspend fun migrateToVersion(
        data: AppData,
        targetVersion: Int,
        createBackup: Boolean = true,
        forceValidation: Boolean = false
    ): MigrationResult {
        return migrationMutex.withLock {
            withContext(Dispatchers.IO) {
                executeMigration(data, targetVersion, createBackup, forceValidation)
            }
        }
    }
    
    /**
     * Rolls back the last migration if possible.
     * 
     * @param data The current application data
     * @param backupFilename The backup to restore from (optional)
     * @return MigrationResult indicating the outcome of the rollback
     */
    suspend fun rollbackLastMigration(
        data: AppData,
        backupFilename: String? = null
    ): MigrationResult {
        return migrationMutex.withLock {
            withContext(Dispatchers.IO) {
                executeRollback(data, backupFilename)
            }
        }
    }
    
    /**
     * Validates the integrity of application data.
     * 
     * @param data The data to validate
     * @return ValidationResult indicating any issues found
     */
    suspend fun validateDataIntegrity(data: AppData): DataIntegrityResult {
        return try {
            // Validate using the data validator
            dataValidator.validateAppData(data)
            
            // Additional migration-specific validations
            val warnings = mutableListOf<String>()
            
            // Check version consistency
            if (data.version > MigrationVersion.CURRENT_VERSION) {
                warnings.add("Data version ${data.version} is newer than supported version ${MigrationVersion.CURRENT_VERSION}")
            }
            
            // Check for orphaned data
            val orphanedMessages = data.messages.keys.filter { projectId ->
                projectId != "system" && data.projects.none { it.id == projectId }
            }
            if (orphanedMessages.isNotEmpty()) {
                warnings.add("Found orphaned messages for projects: ${orphanedMessages.joinToString(", ")}")
            }
            
            DataIntegrityResult(
                isValid = true,
                errors = emptyList(),
                warnings = warnings
            )
        } catch (e: Exception) {
            Log.e(TAG, "Data integrity validation failed", e)
            DataIntegrityResult(
                isValid = false,
                errors = listOf("Data validation failed: ${e.message}"),
                warnings = emptyList()
            )
        }
    }
    
    /**
     * Gets the migration history log.
     * 
     * @return List of historical migration results
     */
    suspend fun getMigrationHistory(): List<MigrationLogEntry> {
        return try {
            val logJson = encryptedStorage.getJsonData(MIGRATION_LOG_KEY)
            if (logJson != null) {
                json.decodeFromString<List<MigrationLogEntry>>(logJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load migration history", e)
            emptyList()
        }
    }
    
    /**
     * Clears the migration history log.
     */
    suspend fun clearMigrationHistory() {
        try {
            encryptedStorage.deleteJsonData(MIGRATION_LOG_KEY)
            Log.d(TAG, "Migration history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear migration history", e)
        }
    }
    
    /**
     * Creates a manual backup of the current data.
     * 
     * @param data The data to backup
     * @param description A description for the backup
     * @return The backup filename if successful, null otherwise
     */
    suspend fun createManualBackup(data: AppData, description: String): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val backupFilename = "${MIGRATION_BACKUP_PREFIX}manual_${timestamp}.backup"
            
            // Store the backup
            val dataJson = json.encodeToString(AppData.serializer(), data)
            encryptedStorage.storeJsonData(backupFilename, dataJson)
            
            // Log the backup
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = timestamp,
                    fromVersion = data.version,
                    toVersion = data.version,
                    success = true,
                    duration = 0,
                    description = "Manual backup: $description",
                    backupFilename = backupFilename
                )
            )
            
            Log.d(TAG, "Manual backup created: $backupFilename")
            backupFilename
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create manual backup", e)
            null
        }
    }
    
    /**
     * Lists available backup files.
     * 
     * @return List of backup filenames
     */
    suspend fun listBackups(): List<String> {
        return try {
            val history = getMigrationHistory()
            history.mapNotNull { it.backupFilename }.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list backups", e)
            emptyList()
        }
    }
    
    /**
     * Executes the migration process.
     */
    private suspend fun executeMigration(
        data: AppData,
        targetVersion: Int,
        createBackup: Boolean,
        forceValidation: Boolean
    ): MigrationResult {
        val startTime = System.currentTimeMillis()
        val currentVersion = data.version
        
        try {
            _isMigrating.value = true
            
            Log.i(TAG, "Starting migration from version $currentVersion to $targetVersion")
            progressObservers.forEach { it.onMigrationStarted(currentVersion, targetVersion) }
            
            // Validate initial data
            if (forceValidation || currentVersion < targetVersion) {
                val integrityResult = validateDataIntegrity(data)
                if (!integrityResult.isValid) {
                    throw MigrationException.ValidationException(
                        "Data integrity validation failed: ${integrityResult.errors.joinToString(", ")}"
                    )
                }
            }
            
            // Check if migration is needed
            if (currentVersion == targetVersion && !forceValidation) {
                val duration = System.currentTimeMillis() - startTime
                return MigrationResult.success(
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    message = "No migration needed - already at target version",
                    executionTimeMs = duration
                )
            }
            
            // Find migration path
            val migrationPath = migrationRegistry.findMigrationPath(currentVersion, targetVersion)
            if (migrationPath.isEmpty()) {
                throw MigrationException.MigrationNotFoundException(
                    "No migration path found from version $currentVersion to $targetVersion"
                )
            }
            
            // Create backup if requested
            var backupFilename: String? = null
            if (createBackup) {
                backupFilename = createMigrationBackup(data)
                if (backupFilename == null) {
                    Log.w(TAG, "Failed to create backup, continuing with migration")
                }
            }
            
            // Execute migrations
            var currentData = data
            val totalSteps = migrationPath.sumOf { it.getEstimatedSteps(currentData) }
            var currentStep = 0
            
            for (migration in migrationPath) {
                Log.d(TAG, "Executing migration: ${migration.name}")
                
                // Validate migration can be applied
                if (!migration.canMigrate(currentData)) {
                    throw MigrationException.ValidationException(
                        "Migration ${migration.name} cannot be applied to current data"
                    )
                }
                
                // Execute the migration
                val migratedData = migration.migrate(currentData) { progress ->
                    val adjustedProgress = MigrationProgress(
                        currentStep = currentStep + progress.currentStep,
                        totalSteps = totalSteps,
                        stepDescription = "${migration.name}: ${progress.stepDescription}"
                    )
                    _migrationProgress.value = adjustedProgress
                    progressObservers.forEach { it.onProgressUpdated(adjustedProgress) }
                }
                
                // Validate the migration result
                if (!migration.validateMigration(currentData, migratedData)) {
                    throw MigrationException.ValidationException(
                        "Migration validation failed for ${migration.name}"
                    )
                }
                
                currentData = migratedData
                currentStep += migration.getEstimatedSteps(data)
            }
            
            // Final validation
            val finalIntegrityResult = validateDataIntegrity(currentData)
            if (!finalIntegrityResult.isValid) {
                throw MigrationException.ValidationException(
                    "Final data integrity validation failed: ${finalIntegrityResult.errors.joinToString(", ")}"
                )
            }
            
            val duration = System.currentTimeMillis() - startTime
            val result = MigrationResult.success(
                fromVersion = currentVersion,
                toVersion = targetVersion,
                message = "Migration completed successfully",
                executionTimeMs = duration,
                backupCreated = backupFilename != null,
                backupFilename = backupFilename
            )
            
            // Log the migration
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = startTime,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = true,
                    duration = duration,
                    description = "Migration completed successfully",
                    backupFilename = backupFilename
                )
            )
            
            Log.i(TAG, "Migration completed successfully in ${duration}ms")
            progressObservers.forEach { it.onMigrationCompleted(result) }
            
            return result
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val result = MigrationResult.failure(
                fromVersion = currentVersion,
                toVersion = targetVersion,
                message = "Migration failed: ${e.message}",
                executionTimeMs = duration,
                exception = e
            )
            
            // Log the failure
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = startTime,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = false,
                    duration = duration,
                    description = "Migration failed: ${e.message}",
                    errorDetails = e.stackTraceToString()
                )
            )
            
            Log.e(TAG, "Migration failed after ${duration}ms", e)
            progressObservers.forEach { it.onMigrationFailed(result) }
            
            return result
            
        } finally {
            _isMigrating.value = false
            _migrationProgress.value = null
        }
    }
    
    /**
     * Executes a rollback operation.
     */
    private suspend fun executeRollback(
        data: AppData,
        backupFilename: String?
    ): MigrationResult {
        val startTime = System.currentTimeMillis()
        val currentVersion = data.version
        
        try {
            _isMigrating.value = true
            
            Log.i(TAG, "Starting rollback from version $currentVersion")
            
            // Find the appropriate backup
            val rollbackFilename = backupFilename ?: findLatestBackup()
            if (rollbackFilename == null) {
                throw MigrationException.RollbackException("No backup file available for rollback")
            }
            
            progressObservers.forEach { it.onRollbackStarted(currentVersion, 0) }
            
            // Load backup data
            val backupJson = encryptedStorage.getJsonData(rollbackFilename)
                ?: throw MigrationException.RollbackException("Backup file not found or corrupted")
            
            val backupData = json.decodeFromString(AppData.serializer(), backupJson)
            val targetVersion = backupData.version
            
            // Validate backup data
            val integrityResult = validateDataIntegrity(backupData)
            if (!integrityResult.isValid) {
                throw MigrationException.RollbackException(
                    "Backup data integrity validation failed: ${integrityResult.errors.joinToString(", ")}"
                )
            }
            
            val duration = System.currentTimeMillis() - startTime
            val result = MigrationResult.success(
                fromVersion = currentVersion,
                toVersion = targetVersion,
                message = "Rollback completed successfully",
                executionTimeMs = duration,
                backupCreated = false,
                backupFilename = rollbackFilename
            )
            
            // Log the rollback
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = startTime,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = true,
                    duration = duration,
                    description = "Rollback completed successfully",
                    backupFilename = rollbackFilename
                )
            )
            
            Log.i(TAG, "Rollback completed successfully in ${duration}ms")
            progressObservers.forEach { it.onRollbackCompleted(result) }
            
            return result
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val result = MigrationResult.failure(
                fromVersion = currentVersion,
                toVersion = 0,
                message = "Rollback failed: ${e.message}",
                executionTimeMs = duration,
                exception = e
            )
            
            Log.e(TAG, "Rollback failed after ${duration}ms", e)
            progressObservers.forEach { it.onRollbackFailed(result) }
            
            return result
            
        } finally {
            _isMigrating.value = false
            _migrationProgress.value = null
        }
    }
    
    /**
     * Creates a backup before migration.
     */
    private suspend fun createMigrationBackup(data: AppData): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val backupFilename = "${MIGRATION_BACKUP_PREFIX}v${data.version}_${timestamp}.backup"
            
            val dataJson = json.encodeToString(AppData.serializer(), data)
            encryptedStorage.storeJsonData(backupFilename, dataJson)
            
            Log.d(TAG, "Migration backup created: $backupFilename")
            backupFilename
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create migration backup", e)
            null
        }
    }
    
    /**
     * Finds the latest backup file.
     */
    private suspend fun findLatestBackup(): String? {
        return try {
            val history = getMigrationHistory()
            history.sortedByDescending { it.timestamp }
                .firstOrNull { it.backupFilename != null }
                ?.backupFilename
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find latest backup", e)
            null
        }
    }
    
    /**
     * Logs a migration event to the history.
     */
    private suspend fun logMigrationEvent(entry: MigrationLogEntry) {
        try {
            val currentHistory = getMigrationHistory().toMutableList()
            currentHistory.add(entry)
            
            // Keep only the last 50 entries
            val trimmedHistory = currentHistory.takeLast(50)
            
            val historyJson = json.encodeToString<List<MigrationLogEntry>>(trimmedHistory)
            encryptedStorage.storeJsonData(MIGRATION_LOG_KEY, historyJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log migration event", e)
        }
    }
}

/**
 * Result of data integrity validation.
 */
data class DataIntegrityResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * Entry in the migration history log.
 */
@kotlinx.serialization.Serializable
data class MigrationLogEntry(
    val timestamp: Long,
    val fromVersion: Int,
    val toVersion: Int,
    val success: Boolean,
    val duration: Long,
    val description: String,
    val backupFilename: String? = null,
    val errorDetails: String? = null
)