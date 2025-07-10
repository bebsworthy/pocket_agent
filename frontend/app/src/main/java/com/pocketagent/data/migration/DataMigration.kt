package com.pocketagent.data.migration

import com.pocketagent.data.models.AppData
import kotlinx.coroutines.flow.Flow

/**
 * Interface for data migration operations.
 * 
 * This interface defines the contract for data migrations, allowing
 * the migration system to execute migrations in a consistent manner.
 * Each migration is responsible for transforming data from one version
 * to the next while maintaining data integrity.
 */
interface DataMigration {
    
    /**
     * The version this migration upgrades from.
     */
    val fromVersion: Int
    
    /**
     * The version this migration upgrades to.
     */
    val toVersion: Int
    
    /**
     * A human-readable name for this migration.
     */
    val name: String
    
    /**
     * A description of what this migration does.
     */
    val description: String
    
    /**
     * Whether this migration can be reversed (rollback supported).
     */
    val isReversible: Boolean get() = false
    
    /**
     * Executes the migration on the provided data.
     * 
     * @param data The current application data to migrate
     * @param progressCallback Optional callback to report migration progress
     * @return The migrated application data
     * @throws MigrationException if migration fails
     */
    suspend fun migrate(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)? = null
    ): AppData
    
    /**
     * Validates that the data can be migrated by this migration.
     * 
     * @param data The data to validate
     * @return true if the data can be migrated, false otherwise
     */
    suspend fun canMigrate(data: AppData): Boolean
    
    /**
     * Reverses the migration (if supported).
     * 
     * @param data The migrated data to reverse
     * @param progressCallback Optional callback to report rollback progress
     * @return The data reverted to the previous version
     * @throws MigrationException if rollback fails or is not supported
     */
    suspend fun rollback(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)? = null
    ): AppData {
        throw MigrationException.RollbackNotSupportedException(
            "Migration $name (v$fromVersion -> v$toVersion) does not support rollback"
        )
    }
    
    /**
     * Gets the estimated number of steps this migration will take.
     * This is used for progress reporting.
     * 
     * @param data The data to be migrated
     * @return The estimated number of steps
     */
    suspend fun getEstimatedSteps(data: AppData): Int = 1
    
    /**
     * Validates the migrated data to ensure it's correct.
     * 
     * @param originalData The original data before migration
     * @param migratedData The data after migration
     * @return true if the migration was successful, false otherwise
     */
    suspend fun validateMigration(originalData: AppData, migratedData: AppData): Boolean = true
}

/**
 * Base implementation of DataMigration with common functionality.
 * 
 * This abstract class provides common implementations for migration
 * operations and utility methods that can be shared across different
 * migration implementations.
 */
abstract class BaseDataMigration : DataMigration {
    
    /**
     * Reports progress during migration.
     */
    protected fun reportProgress(
        currentStep: Int,
        totalSteps: Int,
        stepDescription: String,
        progressCallback: ((MigrationProgress) -> Unit)?
    ) {
        progressCallback?.invoke(
            MigrationProgress(
                currentStep = currentStep,
                totalSteps = totalSteps,
                stepDescription = stepDescription
            )
        )
    }
    
    /**
     * Validates that the data version matches the expected from version.
     */
    protected fun validateDataVersion(data: AppData) {
        if (data.version != fromVersion) {
            throw MigrationException.InvalidVersionException(
                "Expected data version $fromVersion, but got ${data.version}"
            )
        }
    }
    
    /**
     * Updates the data version to the target version.
     */
    protected fun updateDataVersion(data: AppData): AppData {
        return data.copy(
            version = toVersion,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Validates basic data integrity after migration.
     */
    override suspend fun validateMigration(originalData: AppData, migratedData: AppData): Boolean {
        return try {
            // Check that the version was updated
            migratedData.version == toVersion &&
            // Check that data counts are reasonable
            migratedData.sshIdentities.size >= 0 &&
            migratedData.serverProfiles.size >= 0 &&
            migratedData.projects.size >= 0 &&
            // Check that timestamp was updated
            migratedData.lastModified >= originalData.lastModified
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Exception types for migration operations.
 */
sealed class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Thrown when a migration cannot be executed due to invalid data version.
     */
    class InvalidVersionException(message: String) : MigrationException(message)
    
    /**
     * Thrown when migration validation fails.
     */
    class ValidationException(message: String, cause: Throwable? = null) : MigrationException(message, cause)
    
    /**
     * Thrown when a migration fails during execution.
     */
    class ExecutionException(message: String, cause: Throwable? = null) : MigrationException(message, cause)
    
    /**
     * Thrown when rollback is requested but not supported.
     */
    class RollbackNotSupportedException(message: String) : MigrationException(message)
    
    /**
     * Thrown when rollback fails.
     */
    class RollbackException(message: String, cause: Throwable? = null) : MigrationException(message, cause)
    
    /**
     * Thrown when a required migration is not found.
     */
    class MigrationNotFoundException(message: String) : MigrationException(message)
    
    /**
     * Thrown when migration data is corrupted.
     */
    class CorruptedDataException(message: String, cause: Throwable? = null) : MigrationException(message, cause)
}

/**
 * Interface for observing migration progress.
 */
interface MigrationProgressObserver {
    
    /**
     * Called when migration starts.
     */
    fun onMigrationStarted(fromVersion: Int, toVersion: Int)
    
    /**
     * Called when migration progress is updated.
     */
    fun onProgressUpdated(progress: MigrationProgress)
    
    /**
     * Called when migration completes successfully.
     */
    fun onMigrationCompleted(result: MigrationResult)
    
    /**
     * Called when migration fails.
     */
    fun onMigrationFailed(result: MigrationResult)
    
    /**
     * Called when rollback starts.
     */
    fun onRollbackStarted(fromVersion: Int, toVersion: Int)
    
    /**
     * Called when rollback completes.
     */
    fun onRollbackCompleted(result: MigrationResult)
    
    /**
     * Called when rollback fails.
     */
    fun onRollbackFailed(result: MigrationResult)
}

/**
 * Default implementation of MigrationProgressObserver that logs to Android Log.
 */
class LoggingMigrationProgressObserver : MigrationProgressObserver {
    
    companion object {
        private const val TAG = "DataMigration"
    }
    
    override fun onMigrationStarted(fromVersion: Int, toVersion: Int) {
        android.util.Log.i(TAG, "Starting migration from version $fromVersion to $toVersion")
    }
    
    override fun onProgressUpdated(progress: MigrationProgress) {
        android.util.Log.d(TAG, "Migration progress: $progress")
    }
    
    override fun onMigrationCompleted(result: MigrationResult) {
        android.util.Log.i(TAG, "Migration completed successfully: ${result.message} (${result.executionTimeMs}ms)")
    }
    
    override fun onMigrationFailed(result: MigrationResult) {
        android.util.Log.e(TAG, "Migration failed: ${result.message} (${result.executionTimeMs}ms)", result.exception)
    }
    
    override fun onRollbackStarted(fromVersion: Int, toVersion: Int) {
        android.util.Log.i(TAG, "Starting rollback from version $fromVersion to $toVersion")
    }
    
    override fun onRollbackCompleted(result: MigrationResult) {
        android.util.Log.i(TAG, "Rollback completed successfully: ${result.message} (${result.executionTimeMs}ms)")
    }
    
    override fun onRollbackFailed(result: MigrationResult) {
        android.util.Log.e(TAG, "Rollback failed: ${result.message} (${result.executionTimeMs}ms)", result.exception)
    }
}