package com.pocketagent.data.migration

import android.util.Log
import com.pocketagent.data.migration.di.MigrationConfiguration
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.repository.createMigrationBackup
import com.pocketagent.data.repository.getMigrationStatus
import com.pocketagent.data.repository.initializeWithMigration
import com.pocketagent.data.repository.validateAndRepairData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that provides simplified access to the migration system.
 *
 * This class serves as a high-level interface for common migration operations,
 * making it easier for application code to interact with the migration system
 * without needing to understand the internal details.
 */
@Singleton
class MigrationHelper
    @Inject
    constructor(
        private val migrationManager: DataMigrationManager,
        private val migrationConfiguration: MigrationConfiguration,
    ) {
        companion object {
            private const val TAG = "MigrationHelper"
        }

        /**
         * Initializes a repository with automatic migration support.
         *
         * This is the recommended way to initialize repositories that need
         * migration support. It handles all the complexity of checking for
         * needed migrations and executing them automatically.
         *
         * @param repository The repository to initialize
         * @param showProgress Callback to show migration progress to the user
         * @return MigrationSummary with details about any migration performed
         */
        suspend fun initializeRepositoryWithMigration(
            repository: SecureDataRepository,
            showProgress: ((MigrationProgress) -> Unit)? = null,
        ): MigrationSummary {
            Log.d(TAG, "Initializing repository with migration support")

            return try {
                // Add progress observer if provided
                if (showProgress != null) {
                    val observer = ProgressCallbackObserver(showProgress)
                    migrationManager.addProgressObserver(observer)
                }

                // Initialize with migration
                val migrationResult =
                    repository.initializeWithMigration(
                        migrationManager,
                        migrationConfiguration,
                    )

                // Create summary
                val summary =
                    if (migrationResult != null) {
                        MigrationSummary(
                            migrationPerformed = true,
                            success = migrationResult.success,
                            fromVersion = migrationResult.fromVersion,
                            toVersion = migrationResult.toVersion,
                            executionTimeMs = migrationResult.executionTimeMs,
                            backupCreated = migrationResult.backupCreated,
                            backupFilename = migrationResult.backupFilename,
                            message = migrationResult.message,
                            error = migrationResult.exception?.message,
                        )
                    } else {
                        MigrationSummary(
                            migrationPerformed = false,
                            success = true,
                            message = "Repository initialized successfully - no migration needed",
                        )
                    }

                Log.d(TAG, "Repository initialization completed: ${summary.message}")
                summary
            } catch (e: Exception) {
                Log.e(TAG, "Repository initialization failed", e)

                MigrationSummary(
                    migrationPerformed = false,
                    success = false,
                    message = "Repository initialization failed",
                    error = e.message,
                )
            }
        }

        /**
         * Performs a data health check and repairs any issues found.
         *
         * This method can be called periodically to ensure data integrity
         * and repair any corruption or inconsistencies that may have occurred.
         *
         * @param repository The repository to check and repair
         * @return HealthCheckResult with details about the health check
         */
        suspend fun performDataHealthCheck(repository: SecureDataRepository): HealthCheckResult {
            Log.d(TAG, "Performing data health check")

            return try {
                val repairResult = repository.validateAndRepairData(migrationManager)

                HealthCheckResult(
                    isHealthy = !repairResult.repairNeeded || repairResult.isFullyRepaired,
                    issuesFound = repairResult.issuesFound.size,
                    issuesRepaired = repairResult.issuesRepaired.size,
                    unrepairedIssues = repairResult.issuesFound - repairResult.issuesRepaired.toSet(),
                    repairPerformed = repairResult.repairNeeded,
                    message =
                        when {
                            !repairResult.repairNeeded -> "Data is healthy - no issues found"
                            repairResult.isFullyRepaired -> "Data issues found and successfully repaired"
                            repairResult.hasUnrepairedIssues -> "Some data issues could not be repaired"
                            else -> "Data repair attempted"
                        },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Data health check failed", e)

                HealthCheckResult(
                    isHealthy = false,
                    issuesFound = 1,
                    issuesRepaired = 0,
                    unrepairedIssues = listOf("Health check failed: ${e.message}"),
                    repairPerformed = false,
                    message = "Health check failed due to error",
                )
            }
        }

        /**
         * Creates a backup of the current data.
         *
         * This creates a backup that's compatible with the migration system
         * and can be used for rollback operations if needed.
         *
         * @param repository The repository to backup
         * @param description A description for the backup
         * @return BackupResult with details about the backup operation
         */
        suspend fun createDataBackup(
            repository: SecureDataRepository,
            description: String = "Manual backup",
        ): BackupResult {
            Log.d(TAG, "Creating data backup: $description")

            return try {
                val backupFilename = repository.createMigrationBackup(migrationManager, description)

                if (backupFilename != null) {
                    BackupResult(
                        success = true,
                        backupFilename = backupFilename,
                        message = "Backup created successfully",
                    )
                } else {
                    BackupResult(
                        success = false,
                        message = "Failed to create backup",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup creation failed", e)

                BackupResult(
                    success = false,
                    message = "Backup creation failed: ${e.message}",
                )
            }
        }

        /**
         * Gets the current migration status for a repository.
         *
         * @param repository The repository to check
         * @return MigrationStatusInfo with current status details
         */
        suspend fun getMigrationStatus(repository: SecureDataRepository): MigrationStatusInfo {
            return try {
                val status = repository.getMigrationStatus(migrationManager)
                val history = migrationManager.getMigrationHistory()

                MigrationStatusInfo(
                    currentVersion = status.currentVersion,
                    targetVersion = status.targetVersion,
                    isUpToDate = status.isUpToDate,
                    migrationNeeded = status.migrationNeeded,
                    versionsBehind = status.versionsBehind,
                    lastMigrationTimestamp = status.lastMigrationTimestamp,
                    lastMigrationSuccess = status.lastMigrationSuccess,
                    migrationHistoryCount = history.size,
                    recentMigrations =
                        history.takeLast(5).map { entry ->
                            MigrationHistoryEntry(
                                timestamp = entry.timestamp,
                                fromVersion = entry.fromVersion,
                                toVersion = entry.toVersion,
                                success = entry.success,
                                description = entry.description,
                                duration = entry.duration,
                            )
                        },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get migration status", e)

                MigrationStatusInfo(
                    currentVersion = -1,
                    targetVersion = MigrationVersion.CURRENT_VERSION,
                    isUpToDate = false,
                    migrationNeeded = true,
                    versionsBehind = -1,
                    lastMigrationTimestamp = null,
                    lastMigrationSuccess = null,
                    migrationHistoryCount = 0,
                    recentMigrations = emptyList(),
                )
            }
        }

        /**
         * Observes migration progress.
         *
         * @return Flow of migration progress updates
         */
        fun observeMigrationProgress(): Flow<MigrationProgress?> {
            return migrationManager.migrationProgress
        }

        /**
         * Checks if a migration is currently in progress.
         *
         * @return Flow of boolean indicating if migration is in progress
         */
        fun isMigrationInProgress(): Flow<Boolean> {
            return migrationManager.isMigrating
        }

        /**
         * Waits for any current migration to complete.
         *
         * This method can be used to ensure that no migration is running
         * before performing other operations.
         */
        suspend fun waitForMigrationCompletion() {
            migrationManager.isMigrating.first { !it }
        }

        /**
         * Lists available backup files.
         *
         * @return List of available backup filenames
         */
        suspend fun listAvailableBackups(): List<String> {
            return try {
                migrationManager.listBackups()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list backups", e)
                emptyList()
            }
        }

        /**
         * Gets migration history.
         *
         * @return List of migration history entries
         */
        suspend fun getMigrationHistory(): List<MigrationHistoryEntry> {
            return try {
                migrationManager.getMigrationHistory().map { entry ->
                    MigrationHistoryEntry(
                        timestamp = entry.timestamp,
                        fromVersion = entry.fromVersion,
                        toVersion = entry.toVersion,
                        success = entry.success,
                        description = entry.description,
                        duration = entry.duration,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get migration history", e)
                emptyList()
            }
        }

        private class ProgressCallbackObserver(
            private val callback: (MigrationProgress) -> Unit,
        ) : MigrationProgressObserver {
            override fun onMigrationStarted(
                fromVersion: Int,
                toVersion: Int,
            ) {
                // Not used for callback observer
            }

            override fun onProgressUpdated(progress: MigrationProgress) {
                callback(progress)
            }

            override fun onMigrationCompleted(result: MigrationResult) {
                // Not used for callback observer
            }

            override fun onMigrationFailed(result: MigrationResult) {
                // Not used for callback observer
            }

            override fun onRollbackStarted(
                fromVersion: Int,
                toVersion: Int,
            ) {
                // Not used for callback observer
            }

            override fun onRollbackCompleted(result: MigrationResult) {
                // Not used for callback observer
            }

            override fun onRollbackFailed(result: MigrationResult) {
                // Not used for callback observer
            }
        }
    }

/**
 * Summary of a migration operation.
 */
data class MigrationSummary(
    val migrationPerformed: Boolean,
    val success: Boolean,
    val fromVersion: Int = 0,
    val toVersion: Int = 0,
    val executionTimeMs: Long = 0,
    val backupCreated: Boolean = false,
    val backupFilename: String? = null,
    val message: String,
    val error: String? = null,
)

/**
 * Result of a data health check operation.
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val issuesFound: Int,
    val issuesRepaired: Int,
    val unrepairedIssues: List<String>,
    val repairPerformed: Boolean,
    val message: String,
)

/**
 * Result of a backup operation.
 */
data class BackupResult(
    val success: Boolean,
    val backupFilename: String? = null,
    val message: String,
)

/**
 * Migration status information.
 */
data class MigrationStatusInfo(
    val currentVersion: Int,
    val targetVersion: Int,
    val isUpToDate: Boolean,
    val migrationNeeded: Boolean,
    val versionsBehind: Int,
    val lastMigrationTimestamp: Long?,
    val lastMigrationSuccess: Boolean?,
    val migrationHistoryCount: Int,
    val recentMigrations: List<MigrationHistoryEntry>,
)

/**
 * Migration history entry.
 */
data class MigrationHistoryEntry(
    val timestamp: Long,
    val fromVersion: Int,
    val toVersion: Int,
    val success: Boolean,
    val description: String,
    val duration: Long,
)
