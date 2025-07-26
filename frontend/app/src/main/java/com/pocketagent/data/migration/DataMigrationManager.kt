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
class DataMigrationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val encryptedStorage: EncryptedJsonStorage,
        private val migrationRegistry: MigrationRegistry,
        private val dataValidator: DataValidator,
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
        private val json =
            Json {
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
            forceValidation: Boolean = false,
        ): MigrationResult = migrateToVersion(data, MigrationVersion.CURRENT_VERSION, createBackup, forceValidation)

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
            forceValidation: Boolean = false,
        ): MigrationResult =
            migrationMutex.withLock {
                withContext(Dispatchers.IO) {
                    executeMigration(data, targetVersion, createBackup, forceValidation)
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
            backupFilename: String? = null,
        ): MigrationResult =
            migrationMutex.withLock {
                withContext(Dispatchers.IO) {
                    executeRollback(data, backupFilename)
                }
            }

        /**
         * Validates the integrity of application data.
         *
         * @param data The data to validate
         * @return ValidationResult indicating any issues found
         */
        suspend fun validateDataIntegrity(data: AppData): DataIntegrityResult =
            try {
                // Validate using the data validator
                dataValidator.validateAppData(data)

                // Additional migration-specific validations
                val warnings = mutableListOf<String>()

                // Check version consistency
                if (data.version > MigrationVersion.CURRENT_VERSION) {
                    warnings.add("Data version ${data.version} is newer than supported version ${MigrationVersion.CURRENT_VERSION}")
                }

                // Check for orphaned data
                val orphanedMessages =
                    data.messages.keys.filter { projectId ->
                        projectId != "system" && data.projects.none { it.id == projectId }
                    }
                if (orphanedMessages.isNotEmpty()) {
                    warnings.add("Found orphaned messages for projects: ${orphanedMessages.joinToString(", ")}")
                }

                DataIntegrityResult(
                    isValid = true,
                    errors = emptyList(),
                    warnings = warnings,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Data integrity validation failed", e)
                DataIntegrityResult(
                    isValid = false,
                    errors = listOf("Data validation failed: ${e.message}"),
                    warnings = emptyList(),
                )
            }

        /**
         * Gets the migration history log.
         *
         * @return List of historical migration results
         */
        suspend fun getMigrationHistory(): List<MigrationLogEntry> =
            try {
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
        suspend fun createManualBackup(
            data: AppData,
            description: String,
        ): String? =
            try {
                val timestamp = System.currentTimeMillis()
                val backupFilename = "${MIGRATION_BACKUP_PREFIX}manual_$timestamp.backup"

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
                        backupFilename = backupFilename,
                    ),
                )

                Log.d(TAG, "Manual backup created: $backupFilename")
                backupFilename
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create manual backup", e)
                null
            }

        /**
         * Lists available backup files.
         *
         * @return List of backup filenames
         */
        suspend fun listBackups(): List<String> =
            try {
                val history = getMigrationHistory()
                history.mapNotNull { it.backupFilename }.distinct()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list backups", e)
                emptyList()
            }

        /**
         * Executes the migration process.
         */
        private suspend fun executeMigration(
            data: AppData,
            targetVersion: Int,
            createBackup: Boolean,
            forceValidation: Boolean,
        ): MigrationResult {
            val startTime = System.currentTimeMillis()
            val currentVersion = data.version

            try {
                _isMigrating.value = true

                Log.i(TAG, "Starting migration from version $currentVersion to $targetVersion")
                progressObservers.forEach { it.onMigrationStarted(currentVersion, targetVersion) }

                // Validate initial data if needed
                performInitialDataValidation(data, currentVersion, targetVersion, forceValidation)

                // Check if migration is actually needed
                val skipResult = checkIfMigrationCanBeSkipped(currentVersion, targetVersion, forceValidation, startTime)
                if (skipResult != null) return skipResult

                // Find and validate migration path
                val migrationPath = findAndValidateMigrationPath(currentVersion, targetVersion)

                // Create backup if requested
                val backupFilename = handleBackupCreation(data, createBackup)

                // Execute all migrations in the path
                val finalData = executeMigrationSteps(data, migrationPath)

                // Perform final validation
                performFinalDataValidation(finalData)

                // Create and log successful result
                return createSuccessfulMigrationResult(
                    startTime,
                    currentVersion,
                    targetVersion,
                    backupFilename,
                )
            } catch (e: Exception) {
                return handleMigrationFailure(
                    e,
                    startTime,
                    currentVersion,
                    targetVersion,
                )
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
            backupFilename: String?,
        ): MigrationResult {
            val startTime = System.currentTimeMillis()
            val currentVersion = data.version

            try {
                _isMigrating.value = true
                Log.i(TAG, "Starting rollback from version $currentVersion")

                val rollbackFilename = findRollbackFile(backupFilename)
                notifyRollbackStarted(currentVersion)

                val backupData = loadAndValidateBackupData(rollbackFilename)
                val targetVersion = backupData.version

                val result = createSuccessfulRollbackResult(
                    currentVersion = currentVersion,
                    targetVersion = targetVersion,
                    duration = System.currentTimeMillis() - startTime,
                    rollbackFilename = rollbackFilename,
                )

                logSuccessfulRollback(startTime, currentVersion, targetVersion, result.executionTimeMs, rollbackFilename)
                notifyRollbackCompleted(result)

                return result
            } catch (e: Exception) {
                return handleRollbackFailure(e, currentVersion, startTime)
            } finally {
                _isMigrating.value = false
                _migrationProgress.value = null
            }
        }

        /**
         * Finds the rollback file to use.
         */
        private suspend fun findRollbackFile(backupFilename: String?): String {
            val rollbackFilename = backupFilename ?: findLatestBackup()
            if (rollbackFilename == null) {
                throw MigrationException.RollbackException("No backup file available for rollback")
            }
            return rollbackFilename
        }

        /**
         * Notifies observers that rollback has started.
         */
        private fun notifyRollbackStarted(currentVersion: Int) {
            progressObservers.forEach { it.onRollbackStarted(currentVersion, 0) }
        }

        /**
         * Loads and validates backup data.
         */
        private suspend fun loadAndValidateBackupData(rollbackFilename: String): AppData {
            val backupJson = encryptedStorage.getJsonData(rollbackFilename)
                ?: throw MigrationException.RollbackException("Backup file not found or corrupted")

            val backupData = json.decodeFromString(AppData.serializer(), backupJson)
            validateBackupDataIntegrity(backupData)
            return backupData
        }

        /**
         * Validates backup data integrity.
         */
        private suspend fun validateBackupDataIntegrity(backupData: AppData) {
            val integrityResult = validateDataIntegrity(backupData)
            if (!integrityResult.isValid) {
                throw MigrationException.RollbackException(
                    "Backup data integrity validation failed: ${integrityResult.errors.joinToString(", ")}",
                )
            }
        }

        /**
         * Creates a successful rollback result.
         */
        private fun createSuccessfulRollbackResult(
            currentVersion: Int,
            targetVersion: Int,
            duration: Long,
            rollbackFilename: String,
        ): MigrationResult {
            return MigrationResult.success(
                SuccessfulMigrationConfig(
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    message = "Rollback completed successfully",
                    executionTimeMs = duration,
                    backupCreated = false,
                    backupFilename = rollbackFilename,
                )
            )
        }

        /**
         * Logs successful rollback operation.
         */
        private suspend fun logSuccessfulRollback(
            startTime: Long,
            currentVersion: Int,
            targetVersion: Int,
            duration: Long,
            rollbackFilename: String,
        ) {
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = startTime,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = true,
                    duration = duration,
                    description = "Rollback completed successfully",
                    backupFilename = rollbackFilename,
                ),
            )
            Log.i(TAG, "Rollback completed successfully in ${duration}ms")
        }

        /**
         * Notifies observers that rollback completed.
         */
        private fun notifyRollbackCompleted(result: MigrationResult) {
            progressObservers.forEach { it.onRollbackCompleted(result) }
        }

        /**
         * Handles rollback failure.
         */
        private fun handleRollbackFailure(
            exception: Exception,
            currentVersion: Int,
            startTime: Long,
        ): MigrationResult {
            val duration = System.currentTimeMillis() - startTime
            val result = MigrationResult.failure(
                FailedMigrationConfig(
                    fromVersion = currentVersion,
                    toVersion = 0,
                    message = "Rollback failed: ${exception.message}",
                    executionTimeMs = duration,
                    exception = exception,
                )
            )

            Log.e(TAG, "Rollback failed after ${duration}ms", exception)
            progressObservers.forEach { it.onRollbackFailed(result) }

            return result
        }

        /**
         * Creates a backup before migration.
         */
        private suspend fun createMigrationBackup(data: AppData): String? =
            try {
                val timestamp = System.currentTimeMillis()
                val backupFilename = "${MIGRATION_BACKUP_PREFIX}v${data.version}_$timestamp.backup"

                val dataJson = json.encodeToString(AppData.serializer(), data)
                encryptedStorage.storeJsonData(backupFilename, dataJson)

                Log.d(TAG, "Migration backup created: $backupFilename")
                backupFilename
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create migration backup", e)
                null
            }

        /**
         * Finds the latest backup file.
         */
        private suspend fun findLatestBackup(): String? =
            try {
                val history = getMigrationHistory()
                history
                    .sortedByDescending { it.timestamp }
                    .firstOrNull { it.backupFilename != null }
                    ?.backupFilename
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find latest backup", e)
                null
            }

        // Helper methods for executeMigration complexity reduction

        private suspend fun performInitialDataValidation(
            data: AppData,
            currentVersion: Int,
            targetVersion: Int,
            forceValidation: Boolean,
        ) {
            if (forceValidation || currentVersion < targetVersion) {
                val integrityResult = validateDataIntegrity(data)
                if (!integrityResult.isValid) {
                    throw MigrationException.ValidationException(
                        "Data integrity validation failed: ${integrityResult.errors.joinToString(", ")}",
                    )
                }
            }
        }

        private fun checkIfMigrationCanBeSkipped(
            currentVersion: Int,
            targetVersion: Int,
            forceValidation: Boolean,
            startTime: Long,
        ): MigrationResult? {
            if (currentVersion == targetVersion && !forceValidation) {
                val duration = System.currentTimeMillis() - startTime
                return MigrationResult.success(
                    SuccessfulMigrationConfig(
                        fromVersion = currentVersion,
                        toVersion = targetVersion,
                        message = "No migration needed - already at target version",
                        executionTimeMs = duration,
                    )
                )
            }
            return null
        }

        private fun findAndValidateMigrationPath(
            currentVersion: Int,
            targetVersion: Int,
        ): List<DataMigration> {
            val migrationPath = migrationRegistry.findMigrationPath(currentVersion, targetVersion)
            if (migrationPath.isEmpty()) {
                throw MigrationException.MigrationNotFoundException(
                    "No migration path found from version $currentVersion to $targetVersion",
                )
            }
            return migrationPath
        }

        private suspend fun handleBackupCreation(
            data: AppData,
            createBackup: Boolean,
        ): String? {
            if (!createBackup) return null

            val backupFilename = createMigrationBackup(data)
            if (backupFilename == null) {
                Log.w(TAG, "Failed to create backup, continuing with migration")
            }
            return backupFilename
        }

        private suspend fun executeMigrationSteps(
            data: AppData,
            migrationPath: List<DataMigration>,
        ): AppData {
            var currentData = data
            val totalSteps = migrationPath.sumOf { it.getEstimatedSteps(currentData) }
            var currentStep = 0

            for (migration in migrationPath) {
                currentData = executeSingleMigration(migration, currentData, currentStep, totalSteps)
                currentStep += migration.getEstimatedSteps(data)
            }

            return currentData
        }

        private suspend fun executeSingleMigration(
            migration: DataMigration,
            currentData: AppData,
            currentStep: Int,
            totalSteps: Int,
        ): AppData {
            Log.d(TAG, "Executing migration: ${migration.name}")

            // Validate migration can be applied
            if (!migration.canMigrate(currentData)) {
                throw MigrationException.ValidationException(
                    "Migration ${migration.name} cannot be applied to current data",
                )
            }

            // Execute the migration with progress tracking
            val migratedData =
                migration.migrate(currentData) { progress ->
                    updateMigrationProgress(migration, progress, currentStep, totalSteps)
                }

            // Validate the migration result
            if (!migration.validateMigration(currentData, migratedData)) {
                throw MigrationException.ValidationException(
                    "Migration validation failed for ${migration.name}",
                )
            }

            return migratedData
        }

        private fun updateMigrationProgress(
            migration: DataMigration,
            progress: MigrationProgress,
            currentStep: Int,
            totalSteps: Int,
        ) {
            val adjustedProgress =
                MigrationProgress(
                    currentStep = currentStep + progress.currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "${migration.name}: ${progress.stepDescription}",
                )
            _migrationProgress.value = adjustedProgress
            progressObservers.forEach { it.onProgressUpdated(adjustedProgress) }
        }

        private suspend fun performFinalDataValidation(finalData: AppData) {
            val finalIntegrityResult = validateDataIntegrity(finalData)
            if (!finalIntegrityResult.isValid) {
                throw MigrationException.ValidationException(
                    "Final data integrity validation failed: ${finalIntegrityResult.errors.joinToString(", ")}",
                )
            }
        }

        private suspend fun createSuccessfulMigrationResult(
            startTime: Long,
            currentVersion: Int,
            targetVersion: Int,
            backupFilename: String?,
        ): MigrationResult {
            val duration = System.currentTimeMillis() - startTime
            val result =
                MigrationResult.success(
                    SuccessfulMigrationConfig(
                        fromVersion = currentVersion,
                        toVersion = targetVersion,
                        message = "Migration completed successfully",
                        executionTimeMs = duration,
                        backupCreated = backupFilename != null,
                        backupFilename = backupFilename,
                    )
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
                    backupFilename = backupFilename,
                ),
            )

            Log.i(TAG, "Migration completed successfully in ${duration}ms")
            progressObservers.forEach { it.onMigrationCompleted(result) }

            return result
        }

        private suspend fun handleMigrationFailure(
            exception: Exception,
            startTime: Long,
            currentVersion: Int,
            targetVersion: Int,
        ): MigrationResult {
            val duration = System.currentTimeMillis() - startTime
            val result =
                MigrationResult.failure(
                    FailedMigrationConfig(
                        fromVersion = currentVersion,
                        toVersion = targetVersion,
                        message = "Migration failed: ${exception.message}",
                        executionTimeMs = duration,
                        exception = exception,
                    )
                )

            // Log the failure
            logMigrationEvent(
                MigrationLogEntry(
                    timestamp = startTime,
                    fromVersion = currentVersion,
                    toVersion = targetVersion,
                    success = false,
                    duration = duration,
                    description = "Migration failed: ${exception.message}",
                    errorDetails = exception.stackTraceToString(),
                ),
            )

            Log.e(TAG, "Migration failed after ${duration}ms", exception)
            progressObservers.forEach { it.onMigrationFailed(result) }

            return result
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
    val warnings: List<String>,
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
    val errorDetails: String? = null,
)
