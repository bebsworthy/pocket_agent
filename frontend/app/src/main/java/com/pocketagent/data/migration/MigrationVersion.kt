package com.pocketagent.data.migration

/**
 * Represents a data migration version.
 * 
 * This class defines the version information for data migrations, allowing
 * the migration system to determine which migrations need to be applied
 * and in what order.
 * 
 * @property version The numeric version number
 * @property name A human-readable name for this version
 * @property description A description of what this migration does
 */
data class MigrationVersion(
    val version: Int,
    val name: String,
    val description: String
) : Comparable<MigrationVersion> {
    
    init {
        require(version >= 1) { "Migration version must be at least 1" }
        require(name.isNotBlank()) { "Migration name cannot be blank" }
        require(description.isNotBlank()) { "Migration description cannot be blank" }
    }
    
    override fun compareTo(other: MigrationVersion): Int {
        return version.compareTo(other.version)
    }
    
    override fun toString(): String {
        return "v$version: $name"
    }
    
    companion object {
        /**
         * The current data version that the application expects.
         */
        const val CURRENT_VERSION = 1
        
        /**
         * Creates a migration version instance.
         */
        fun create(version: Int, name: String, description: String): MigrationVersion {
            return MigrationVersion(version, name, description)
        }
        
        /**
         * Gets the current migration version.
         */
        fun current(): MigrationVersion {
            return MigrationVersion(
                version = CURRENT_VERSION,
                name = "Initial Version",
                description = "Initial data structure with SSH identities, server profiles, and projects"
            )
        }
    }
}

/**
 * Represents the result of a migration operation.
 * 
 * @property success Whether the migration was successful
 * @property fromVersion The version migrated from
 * @property toVersion The version migrated to
 * @property message A message describing the result
 * @property executionTimeMs The time taken to execute the migration in milliseconds
 * @property backupCreated Whether a backup was created before migration
 * @property backupFilename The filename of the backup (if created)
 * @property exception The exception that occurred (if any)
 */
data class MigrationResult(
    val success: Boolean,
    val fromVersion: Int,
    val toVersion: Int,
    val message: String,
    val executionTimeMs: Long,
    val backupCreated: Boolean = false,
    val backupFilename: String? = null,
    val exception: Throwable? = null
) {
    
    /**
     * Creates a successful migration result.
     */
    companion object {
        fun success(
            fromVersion: Int,
            toVersion: Int,
            message: String,
            executionTimeMs: Long,
            backupCreated: Boolean = false,
            backupFilename: String? = null
        ): MigrationResult {
            return MigrationResult(
                success = true,
                fromVersion = fromVersion,
                toVersion = toVersion,
                message = message,
                executionTimeMs = executionTimeMs,
                backupCreated = backupCreated,
                backupFilename = backupFilename
            )
        }
        
        /**
         * Creates a failed migration result.
         */
        fun failure(
            fromVersion: Int,
            toVersion: Int,
            message: String,
            executionTimeMs: Long,
            exception: Throwable? = null,
            backupCreated: Boolean = false,
            backupFilename: String? = null
        ): MigrationResult {
            return MigrationResult(
                success = false,
                fromVersion = fromVersion,
                toVersion = toVersion,
                message = message,
                executionTimeMs = executionTimeMs,
                backupCreated = backupCreated,
                backupFilename = backupFilename,
                exception = exception
            )
        }
    }
}

/**
 * Represents migration progress information.
 * 
 * @property currentStep The current step being executed
 * @property totalSteps The total number of steps in the migration
 * @property stepDescription A description of the current step
 * @property progressPercent The progress as a percentage (0-100)
 */
data class MigrationProgress(
    val currentStep: Int,
    val totalSteps: Int,
    val stepDescription: String,
    val progressPercent: Int = (currentStep * 100) / totalSteps.coerceAtLeast(1)
) {
    
    init {
        require(currentStep >= 0) { "Current step must be non-negative" }
        require(totalSteps >= 0) { "Total steps must be non-negative" }
        require(currentStep <= totalSteps) { "Current step cannot exceed total steps" }
        require(stepDescription.isNotBlank()) { "Step description cannot be blank" }
    }
    
    /**
     * Creates the next progress step.
     */
    fun nextStep(stepDescription: String): MigrationProgress {
        return copy(
            currentStep = currentStep + 1,
            stepDescription = stepDescription
        )
    }
    
    /**
     * Checks if the migration is complete.
     */
    fun isComplete(): Boolean = currentStep >= totalSteps
    
    override fun toString(): String {
        return "Step $currentStep/$totalSteps ($progressPercent%): $stepDescription"
    }
}