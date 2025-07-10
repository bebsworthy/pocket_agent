package com.pocketagent.data.migration.migrations

import com.pocketagent.data.migration.BaseDataMigration
import com.pocketagent.data.migration.MigrationException
import com.pocketagent.data.migration.MigrationProgress
import com.pocketagent.data.models.AppData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initial migration that sets up the base data structure.
 * 
 * This migration is used when no existing data is found and creates
 * the initial data structure with default values.
 */
@Singleton
class InitialMigration @Inject constructor() : BaseDataMigration() {
    
    override val fromVersion: Int = 0
    override val toVersion: Int = 1
    override val name: String = "Initial Setup"
    override val description: String = "Creates the initial data structure with empty collections"
    override val isReversible: Boolean = false
    
    override suspend fun migrate(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        reportProgress(1, 3, "Validating input data", progressCallback)
        
        // This migration should only be applied to version 0 (empty/new data)
        if (data.version != 0) {
            throw MigrationException.InvalidVersionException(
                "Initial migration can only be applied to version 0 data, got version ${data.version}"
            )
        }
        
        reportProgress(2, 3, "Creating initial data structure", progressCallback)
        
        // Create the initial data structure
        val initialData = AppData(
            version = toVersion,
            sshIdentities = emptyList(),
            serverProfiles = emptyList(),
            projects = emptyList(),
            messages = emptyMap(),
            lastModified = System.currentTimeMillis(),
            metadata = data.metadata.copy(
                createdAt = if (data.metadata.createdAt == 0L) System.currentTimeMillis() else data.metadata.createdAt
            )
        )
        
        reportProgress(3, 3, "Initial migration completed", progressCallback)
        
        return initialData
    }
    
    override suspend fun canMigrate(data: AppData): Boolean {
        return data.version == 0
    }
    
    override suspend fun getEstimatedSteps(data: AppData): Int = 3
    
    override suspend fun validateMigration(originalData: AppData, migratedData: AppData): Boolean {
        return super.validateMigration(originalData, migratedData) &&
                migratedData.sshIdentities.isEmpty() &&
                migratedData.serverProfiles.isEmpty() &&
                migratedData.projects.isEmpty() &&
                migratedData.messages.isEmpty()
    }
}

/**
 * Migration from version 1 to version 2 (future migration example).
 * 
 * This is an example of how future migrations would be structured.
 * Currently not implemented since version 2 doesn't exist yet.
 */
@Singleton
class Version1To2Migration @Inject constructor() : BaseDataMigration() {
    
    override val fromVersion: Int = 1
    override val toVersion: Int = 2
    override val name: String = "Add User Preferences"
    override val description: String = "Adds user preferences and settings to the data structure"
    override val isReversible: Boolean = true
    
    override suspend fun migrate(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        validateDataVersion(data)
        
        reportProgress(1, 4, "Validating existing data", progressCallback)
        
        // Validate that we can migrate this data
        if (!canMigrate(data)) {
            throw MigrationException.ValidationException("Cannot migrate data from version ${data.version}")
        }
        
        reportProgress(2, 4, "Adding user preferences structure", progressCallback)
        
        // In a real migration, this would add new fields to the data structure
        // For now, we just update the version since v2 doesn't exist yet
        val migratedData = updateDataVersion(data)
        
        reportProgress(3, 4, "Validating migrated data", progressCallback)
        
        // Additional validation for version 2 specific requirements would go here
        
        reportProgress(4, 4, "Migration to version 2 completed", progressCallback)
        
        return migratedData
    }
    
    override suspend fun canMigrate(data: AppData): Boolean {
        return data.version == fromVersion
    }
    
    override suspend fun rollback(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        reportProgress(1, 3, "Validating rollback data", progressCallback)
        
        if (data.version != toVersion) {
            throw MigrationException.RollbackException(
                "Cannot rollback from version ${data.version}, expected version $toVersion"
            )
        }
        
        reportProgress(2, 3, "Removing version 2 features", progressCallback)
        
        // In a real rollback, this would remove version 2 specific fields
        val rolledBackData = data.copy(
            version = fromVersion,
            lastModified = System.currentTimeMillis()
        )
        
        reportProgress(3, 3, "Rollback to version 1 completed", progressCallback)
        
        return rolledBackData
    }
    
    override suspend fun getEstimatedSteps(data: AppData): Int = 4
}

/**
 * Emergency data repair migration.
 * 
 * This migration can be used to repair corrupted data or fix data integrity issues.
 * It's designed to be idempotent and can be run multiple times safely.
 */
@Singleton
class DataRepairMigration @Inject constructor() : BaseDataMigration() {
    
    override val fromVersion: Int = 1
    override val toVersion: Int = 1
    override val name: String = "Data Repair"
    override val description: String = "Repairs data integrity issues and fixes corrupted relationships"
    override val isReversible: Boolean = false
    
    override suspend fun migrate(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        reportProgress(1, 5, "Analyzing data integrity", progressCallback)
        
        if (data.version != fromVersion) {
            throw MigrationException.InvalidVersionException(
                "Data repair can only be applied to version $fromVersion data"
            )
        }
        
        reportProgress(2, 5, "Repairing entity relationships", progressCallback)
        
        // Remove orphaned server profiles (referencing non-existent SSH identities)
        val validIdentityIds = data.sshIdentities.map { it.id }.toSet()
        val repairedServerProfiles = data.serverProfiles.filter { 
            it.sshIdentityId in validIdentityIds 
        }
        
        reportProgress(3, 5, "Repairing project references", progressCallback)
        
        // Remove orphaned projects (referencing non-existent server profiles)
        val validServerIds = repairedServerProfiles.map { it.id }.toSet()
        val repairedProjects = data.projects.filter { 
            it.serverProfileId in validServerIds 
        }
        
        reportProgress(4, 5, "Cleaning up orphaned messages", progressCallback)
        
        // Remove messages for non-existent projects
        val validProjectIds = repairedProjects.map { it.id }.toSet()
        val repairedMessages = data.messages.filterKeys { projectId ->
            projectId == "system" || projectId in validProjectIds
        }
        
        reportProgress(5, 5, "Data repair completed", progressCallback)
        
        return data.copy(
            serverProfiles = repairedServerProfiles,
            projects = repairedProjects,
            messages = repairedMessages,
            lastModified = System.currentTimeMillis()
        )
    }
    
    override suspend fun canMigrate(data: AppData): Boolean {
        return data.version == fromVersion
    }
    
    override suspend fun getEstimatedSteps(data: AppData): Int = 5
    
    override suspend fun validateMigration(originalData: AppData, migratedData: AppData): Boolean {
        return super.validateMigration(originalData, migratedData) &&
                // Ensure no orphaned relationships remain
                migratedData.serverProfiles.all { server ->
                    migratedData.sshIdentities.any { it.id == server.sshIdentityId }
                } &&
                migratedData.projects.all { project ->
                    migratedData.serverProfiles.any { it.id == project.serverProfileId }
                } &&
                migratedData.messages.keys.all { projectId ->
                    projectId == "system" || migratedData.projects.any { it.id == projectId }
                }
    }
}