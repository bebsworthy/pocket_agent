package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.migration.DataMigrationManager
import com.pocketagent.data.migration.MigrationResult
import com.pocketagent.data.migration.MigrationVersion
import com.pocketagent.data.migration.di.MigrationConfiguration
import com.pocketagent.data.models.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Extension functions for SecureDataRepository to integrate with the migration system.
 *
 * These extensions provide seamless integration between the data repository
 * and the migration framework, ensuring data is automatically migrated
 * when needed while maintaining backward compatibility.
 */

private const val TAG = "RepositoryMigration"

/**
 * Initializes the repository with automatic migration support.
 *
 * This method should be called instead of the regular initialize() method
 * when migration support is needed. It will automatically detect if
 * migration is required and execute it before normal initialization.
 *
 * @param migrationManager The migration manager to use
 * @param configuration Migration configuration settings
 * @return MigrationResult indicating the outcome of any migration performed
 */
suspend fun SecureDataRepository.initializeWithMigration(
    migrationManager: DataMigrationManager,
    configuration: MigrationConfiguration = MigrationConfiguration.default(),
): MigrationResult? {
    Log.d(TAG, "Initializing repository with migration support")

    return try {
        // First, try to load existing data
        val currentData =
            try {
                loadData()
            } catch (e: Exception) {
                Log.d(TAG, "No existing data found or failed to load, starting with empty data")
                AppData(version = 0) // Version 0 indicates new/empty data
            }

        Log.d(TAG, "Current data version: ${currentData.version}, target version: ${MigrationVersion.CURRENT_VERSION}")

        // Check if migration is needed
        val migrationResult =
            if (migrationManager.isMigrationNeeded(currentData)) {
                Log.i(TAG, "Migration required from version ${currentData.version} to ${MigrationVersion.CURRENT_VERSION}")

                if (configuration.migrationTimeoutMs > 0) {
                    withTimeout(configuration.migrationTimeoutMs) {
                        migrationManager.migrateToLatest(
                            data = currentData,
                            createBackup = configuration.backupBeforeMigration,
                            forceValidation = configuration.validateAfterMigration,
                        )
                    }
                } else {
                    migrationManager.migrateToLatest(
                        data = currentData,
                        createBackup = configuration.backupBeforeMigration,
                        forceValidation = configuration.validateAfterMigration,
                    )
                }
            } else {
                Log.d(TAG, "No migration needed")
                null
            }

        // Initialize the repository normally
        initialize()

        Log.d(TAG, "Repository initialization with migration completed successfully")
        migrationResult
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize repository with migration", e)
        throw DataException.InitializationException("Repository initialization with migration failed", e)
    }
}

/**
 * Validates and potentially repairs the current data.
 *
 * This method can be used to check data integrity and repair
 * any issues found. It's useful for maintenance operations.
 *
 * @param migrationManager The migration manager to use for repairs
 * @return DataRepairResult indicating what was found and repaired
 */
suspend fun SecureDataRepository.validateAndRepairData(migrationManager: DataMigrationManager): DataRepairResult {
    Log.d(TAG, "Starting data validation and repair")

    return try {
        val currentData = loadData()

        // Validate data integrity
        val integrityResult = migrationManager.validateDataIntegrity(currentData)

        if (integrityResult.isValid) {
            Log.d(TAG, "Data validation passed")
            if (integrityResult.warnings.isNotEmpty()) {
                Log.w(TAG, "Data validation warnings: ${integrityResult.warnings}")
            }

            DataRepairResult(
                repairNeeded = false,
                issuesFound = integrityResult.warnings,
                issuesRepaired = emptyList(),
                migrationResult = null,
            )
        } else {
            Log.w(TAG, "Data validation failed, attempting repair")

            // Attempt to run data repair migration
            val repairResult =
                migrationManager.migrateToVersion(
                    data = currentData,
                    targetVersion = currentData.version, // Same version repair
                    createBackup = true,
                    forceValidation = true,
                )

            if (repairResult.success) {
                Log.i(TAG, "Data repair completed successfully")

                // Reload and save the repaired data
                initialize()

                DataRepairResult(
                    repairNeeded = true,
                    issuesFound = integrityResult.errors,
                    issuesRepaired = integrityResult.errors,
                    migrationResult = repairResult,
                )
            } else {
                Log.e(TAG, "Data repair failed: ${repairResult.message}")

                DataRepairResult(
                    repairNeeded = true,
                    issuesFound = integrityResult.errors,
                    issuesRepaired = emptyList(),
                    migrationResult = repairResult,
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Data validation and repair failed", e)

        DataRepairResult(
            repairNeeded = true,
            issuesFound = listOf("Validation failed: ${e.message}"),
            issuesRepaired = emptyList(),
            migrationResult = null,
        )
    }
}

/**
 * Creates a backup of the current data with migration support.
 *
 * This creates a backup that's compatible with the migration system
 * and can be used for rollback operations.
 *
 * @param migrationManager The migration manager to use
 * @param description A description for the backup
 * @return The backup filename if successful, null otherwise
 */
suspend fun SecureDataRepository.createMigrationBackup(
    migrationManager: DataMigrationManager,
    description: String = "Manual backup",
): String? {
    Log.d(TAG, "Creating migration-compatible backup")

    return try {
        val currentData = loadData()
        migrationManager.createManualBackup(currentData, description)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create migration backup", e)
        null
    }
}

/**
 * Restores data from a migration backup.
 *
 * @param migrationManager The migration manager to use
 * @param backupFilename The backup file to restore from
 * @return MigrationResult indicating the outcome of the restore operation
 */
suspend fun SecureDataRepository.restoreFromMigrationBackup(
    migrationManager: DataMigrationManager,
    backupFilename: String,
): MigrationResult {
    Log.d(TAG, "Restoring from migration backup: $backupFilename")

    return try {
        val currentData = loadData()
        val rollbackResult = migrationManager.rollbackLastMigration(currentData, backupFilename)

        if (rollbackResult.success) {
            // Reinitialize the repository to load the restored data
            initialize()
            Log.i(TAG, "Successfully restored from backup")
        } else {
            Log.e(TAG, "Failed to restore from backup: ${rollbackResult.message}")
        }

        rollbackResult
    } catch (e: Exception) {
        Log.e(TAG, "Error during backup restoration", e)

        MigrationResult.failure(
            fromVersion = 0,
            toVersion = 0,
            message = "Backup restoration failed: ${e.message}",
            executionTimeMs = 0,
            exception = e,
        )
    }
}

/**
 * Gets migration status information for the current data.
 *
 * @param migrationManager The migration manager to use
 * @return MigrationStatus with current version and migration information
 */
suspend fun SecureDataRepository.getMigrationStatus(migrationManager: DataMigrationManager): MigrationStatus {
    return try {
        val currentData = loadData()
        val isUpToDate = !migrationManager.isMigrationNeeded(currentData)
        val history = migrationManager.getMigrationHistory()
        val lastMigration = history.maxByOrNull { it.timestamp }

        MigrationStatus(
            currentVersion = currentData.version,
            targetVersion = MigrationVersion.CURRENT_VERSION,
            isUpToDate = isUpToDate,
            migrationNeeded = !isUpToDate,
            lastMigrationTimestamp = lastMigration?.timestamp,
            lastMigrationSuccess = lastMigration?.success,
            migrationHistoryCount = history.size,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get migration status", e)

        MigrationStatus(
            currentVersion = -1,
            targetVersion = MigrationVersion.CURRENT_VERSION,
            isUpToDate = false,
            migrationNeeded = true,
            lastMigrationTimestamp = null,
            lastMigrationSuccess = null,
            migrationHistoryCount = 0,
        )
    }
}

/**
 * Observes migration progress.
 *
 * @param migrationManager The migration manager to observe
 * @return Flow of migration progress updates
 */
fun SecureDataRepository.observeMigrationProgress(
    migrationManager: DataMigrationManager,
): Flow<com.pocketagent.data.migration.MigrationProgress?> {
    return migrationManager.migrationProgress
}

/**
 * Checks if a migration is currently in progress.
 *
 * @param migrationManager The migration manager to check
 * @return Flow of boolean indicating if migration is in progress
 */
fun SecureDataRepository.isMigrationInProgress(migrationManager: DataMigrationManager): Flow<Boolean> {
    return migrationManager.isMigrating
}

/**
 * Result of data repair operations.
 */
data class DataRepairResult(
    val repairNeeded: Boolean,
    val issuesFound: List<String>,
    val issuesRepaired: List<String>,
    val migrationResult: MigrationResult?,
) {
    val isFullyRepaired: Boolean
        get() = repairNeeded && issuesFound.size == issuesRepaired.size

    val hasUnrepairedIssues: Boolean
        get() = issuesFound.size > issuesRepaired.size
}

/**
 * Migration status information.
 */
data class MigrationStatus(
    val currentVersion: Int,
    val targetVersion: Int,
    val isUpToDate: Boolean,
    val migrationNeeded: Boolean,
    val lastMigrationTimestamp: Long?,
    val lastMigrationSuccess: Boolean?,
    val migrationHistoryCount: Int,
) {
    val versionsBehind: Int
        get() = if (migrationNeeded) targetVersion - currentVersion else 0

    val isVersionNewer: Boolean
        get() = currentVersion > targetVersion
}

/**
 * Extension to check if the repository needs migration without loading all data.
 *
 * This is a lightweight check that can be used during app startup to determine
 * if migration UI should be shown to the user.
 */
suspend fun SecureDataRepository.quickMigrationCheck(): Boolean {
    return try {
        // Quick check by looking at the data version only
        val dataFlow = dataFlow.first()
        val currentVersion = dataFlow?.version ?: 0
        currentVersion < MigrationVersion.CURRENT_VERSION
    } catch (e: Exception) {
        Log.w(TAG, "Quick migration check failed, assuming migration needed", e)
        true // Assume migration is needed if we can't determine version
    }
}
