package com.pocketagent.data.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.data.migration.di.MigrationConfiguration
import com.pocketagent.data.migration.migrations.InitialMigration
import com.pocketagent.data.migration.migrations.DataRepairMigration
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.Project
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end workflow tests for the migration system.
 * 
 * These tests demonstrate complete migration workflows from start to finish,
 * simulating real-world scenarios that users might encounter.
 */
@RunWith(AndroidJUnit4::class)
class MigrationWorkflowTest {
    
    private lateinit var context: Context
    private lateinit var mockEncryptedStorage: EncryptedJsonStorage
    private lateinit var mockDataValidator: DataValidator
    private lateinit var migrationRegistry: MigrationRegistry
    private lateinit var migrationManager: DataMigrationManager
    private lateinit var migrationHelper: MigrationHelper
    private lateinit var repository: SecureDataRepository
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEncryptedStorage = mockk(relaxed = true)
        mockDataValidator = mockk(relaxed = true)
        migrationRegistry = MigrationRegistry()
        
        migrationManager = DataMigrationManager(
            context = context,
            encryptedStorage = mockEncryptedStorage,
            migrationRegistry = migrationRegistry,
            dataValidator = mockDataValidator
        )
        
        migrationHelper = MigrationHelper(
            migrationManager = migrationManager,
            migrationConfiguration = MigrationConfiguration.testing()
        )
        
        repository = SecureDataRepository(
            context = context,
            encryptedStorage = mockEncryptedStorage,
            dataValidator = mockDataValidator
        )
        
        // Setup default mock behavior
        coEvery { mockDataValidator.validateAppData(any()) } returns Unit
        coEvery { mockEncryptedStorage.getJsonData("migration_log") } returns "[]"
    }
    
    @Test
    fun testCompleteNewUserWorkflow() = runTest {
        // Scenario: New user first app launch
        
        // Setup: No existing data
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns null
        
        // Register required migrations
        migrationRegistry.registerMigration(InitialMigration())
        
        // Initialize repository for new user
        val summary = migrationHelper.initializeRepositoryWithMigration(repository)
        
        // Verify new user initialization
        assertTrue(summary.success)
        assertTrue(summary.migrationPerformed)
        assertEquals(0, summary.fromVersion)
        assertEquals(1, summary.toVersion)
        assertFalse(summary.backupCreated) // Testing config disables backup
        
        // Verify data was stored
        coVerify { mockEncryptedStorage.storeJsonData("app_data", any()) }
        
        // Verify migration status
        val status = migrationHelper.getMigrationStatus(repository)
        assertEquals(1, status.currentVersion)
        assertTrue(status.isUpToDate)
        assertFalse(status.migrationNeeded)
    }
    
    @Test
    fun testExistingUserUpgradeWorkflow() = runTest {
        // Scenario: Existing user upgrading from older version
        
        // Setup: Existing version 0 data with some content
        val existingData = AppData(
            version = 0,
            sshIdentities = listOf(
                SshIdentity(
                    id = "ssh-1",
                    name = "Legacy SSH Key",
                    encryptedPrivateKey = "encrypted_legacy_key",
                    publicKeyFingerprint = "legacy:fingerprint"
                )
            ),
            serverProfiles = listOf(
                ServerProfile(
                    id = "server-1",
                    name = "Legacy Server",
                    hostname = "legacy.example.com",
                    username = "user",
                    sshIdentityId = "ssh-1"
                )
            ),
            projects = listOf(
                Project(
                    id = "project-1",
                    name = "Legacy Project",
                    projectPath = "/legacy/path",
                    serverProfileId = "server-1"
                )
            )
        )
        
        val existingJson = json.encodeToString(AppData.serializer(), existingData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns existingJson
        
        // Register migrations
        migrationRegistry.registerMigration(InitialMigration())
        
        // Initialize repository with migration
        val summary = migrationHelper.initializeRepositoryWithMigration(repository)
        
        // Verify upgrade was successful
        assertTrue(summary.success)
        assertTrue(summary.migrationPerformed)
        assertEquals(0, summary.fromVersion)
        assertEquals(1, summary.toVersion)
        assertTrue(summary.executionTimeMs > 0)
        
        // Verify data was preserved during migration
        // (In a real scenario, we would verify the migrated data structure)
        
        // Verify migration was logged
        coVerify { mockEncryptedStorage.storeJsonData("migration_log", any()) }
    }
    
    @Test
    fun testDataCorruptionDetectionAndRepairWorkflow() = runTest {
        // Scenario: User has corrupted data that needs repair
        
        // Setup: Corrupted data with orphaned relationships
        val corruptedData = AppData(
            version = 1,
            sshIdentities = listOf(
                SshIdentity(
                    id = "ssh-1",
                    name = "Valid SSH",
                    encryptedPrivateKey = "encrypted",
                    publicKeyFingerprint = "valid:fingerprint"
                )
            ),
            serverProfiles = listOf(
                ServerProfile(
                    id = "server-1",
                    name = "Valid Server",
                    hostname = "valid.com",
                    username = "user",
                    sshIdentityId = "ssh-1"
                ),
                ServerProfile(
                    id = "server-2",
                    name = "Orphaned Server",
                    hostname = "orphaned.com",
                    username = "user",
                    sshIdentityId = "missing-ssh" // This SSH identity doesn't exist
                )
            ),
            projects = listOf(
                Project(
                    id = "project-1",
                    name = "Valid Project",
                    projectPath = "/valid/path",
                    serverProfileId = "server-1"
                ),
                Project(
                    id = "project-2",
                    name = "Orphaned Project",
                    projectPath = "/orphaned/path",
                    serverProfileId = "missing-server" // This server doesn't exist
                )
            )
        )
        
        val corruptedJson = json.encodeToString(AppData.serializer(), corruptedData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns corruptedJson
        
        // Register repair migration
        migrationRegistry.registerMigration(DataRepairMigration())
        
        // Perform health check (this should detect and repair issues)
        val healthResult = migrationHelper.performDataHealthCheck(repository)
        
        // Verify corruption was detected and repaired
        assertFalse(healthResult.isHealthy) // Issues were found
        assertTrue(healthResult.repairPerformed)
        assertTrue(healthResult.issuesRepaired > 0)
        
        // In a real scenario, the repair migration would have cleaned up orphaned data
    }
    
    @Test
    fun testBackupAndRestoreWorkflow() = runTest {
        // Scenario: User creates backup before major operation, then restores
        
        // Setup: Existing data
        val testData = AppData(
            version = 1,
            sshIdentities = listOf(
                SshIdentity(
                    id = "ssh-1",
                    name = "Important SSH Key",
                    encryptedPrivateKey = "important_key",
                    publicKeyFingerprint = "important:fingerprint"
                )
            )
        )
        
        val testJson = json.encodeToString(AppData.serializer(), testData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns testJson
        
        // Step 1: Create backup before major operation
        val backupResult = migrationHelper.createDataBackup(
            repository = repository,
            description = "Before major update"
        )
        
        assertTrue(backupResult.success)
        assertNotNull(backupResult.backupFilename)
        assertTrue(backupResult.backupFilename!!.contains("manual"))
        
        // Verify backup was created
        coVerify { 
            mockEncryptedStorage.storeJsonData(
                match { it.contains("manual") }, 
                any()
            ) 
        }
        
        // Step 2: List available backups
        val backups = migrationHelper.listAvailableBackups()
        // In a real scenario, this would return the actual backup files
        
        // Step 3: Simulate restore operation
        // Setup mock for backup restore
        coEvery { 
            mockEncryptedStorage.getJsonData(match { it.contains("manual") }) 
        } returns testJson
        
        val restoreResult = repository.restoreFromMigrationBackup(
            migrationManager = migrationManager,
            backupFilename = backupResult.backupFilename!!
        )
        
        assertTrue(restoreResult.success)
        assertEquals("Backup restoration completed successfully", restoreResult.message)
    }
    
    @Test
    fun testProgressReportingWorkflow() = runTest {
        // Scenario: User wants to see migration progress
        
        // Setup: Old data that needs migration
        val oldData = AppData(version = 0)
        val oldJson = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns oldJson
        
        // Register migration that reports progress
        migrationRegistry.registerMigration(ProgressTestMigration())
        
        val progressUpdates = mutableListOf<MigrationProgress>()
        
        // Initialize with progress callback
        val summary = migrationHelper.initializeRepositoryWithMigration(repository) { progress ->
            progressUpdates.add(progress)
        }
        
        // Verify migration completed successfully
        assertTrue(summary.success)
        assertTrue(summary.migrationPerformed)
        
        // Verify progress was reported
        assertTrue(progressUpdates.isNotEmpty())
        
        // Verify progress sequence
        val firstProgress = progressUpdates.first()
        assertEquals(1, firstProgress.currentStep)
        assertTrue(firstProgress.totalSteps > 0)
        
        val lastProgress = progressUpdates.last()
        assertTrue(lastProgress.currentStep <= lastProgress.totalSteps)
    }
    
    @Test
    fun testMigrationFailureAndRecoveryWorkflow() = runTest {
        // Scenario: Migration fails and needs recovery
        
        // Setup: Data that will cause migration to fail
        val problemData = AppData(version = 0)
        val problemJson = json.encodeToString(AppData.serializer(), problemData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns problemJson
        
        // Register failing migration
        migrationRegistry.registerMigration(FailingTestMigration())
        
        // Attempt initialization (should fail)
        val summary = migrationHelper.initializeRepositoryWithMigration(repository)
        
        // Verify migration failed
        assertFalse(summary.success)
        assertTrue(summary.migrationPerformed)
        assertNotNull(summary.error)
        assertTrue(summary.error!!.contains("Intentional test failure"))
        
        // Recovery: Clear registry and add working migration
        migrationRegistry.clearMigrations()
        migrationRegistry.registerMigration(InitialMigration())
        
        // Retry initialization
        val retryCoEvery = migrationHelper.initializeRepositoryWithMigration(repository)
        
        // Verify recovery was successful
        assertTrue(retryCoEvery.success)
        assertTrue(retryCoEvery.migrationPerformed)
    }
    
    @Test
    fun testMigrationStatusMonitoringWorkflow() = runTest {
        // Scenario: App monitoring migration status for dashboard/admin features
        
        // Setup: Current data
        val currentData = AppData(version = 1)
        val currentJson = json.encodeToString(AppData.serializer(), currentData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns currentJson
        
        // Setup migration history
        val historyJson = json.encodeToString<List<MigrationLogEntry>>(
            listOf(
                MigrationLogEntry(
                    timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
                    fromVersion = 0,
                    toVersion = 1,
                    success = true,
                    duration = 1500,
                    description = "Initial migration completed"
                )
            )
        )
        coEvery { mockEncryptedStorage.getJsonData("migration_log") } returns historyJson
        
        // Get migration status
        val status = migrationHelper.getMigrationStatus(repository)
        
        // Verify status information
        assertEquals(1, status.currentVersion)
        assertEquals(MigrationVersion.CURRENT_VERSION, status.targetVersion)
        assertTrue(status.isUpToDate)
        assertFalse(status.migrationNeeded)
        assertEquals(0, status.versionsBehind)
        assertNotNull(status.lastMigrationTimestamp)
        assertTrue(status.lastMigrationSuccess == true)
        assertEquals(1, status.recentMigrations.size)
        
        // Verify migration history
        val history = migrationHelper.getMigrationHistory()
        assertEquals(1, history.size)
        assertTrue(history[0].success)
        assertEquals("Initial migration completed", history[0].description)
    }
    
    // Helper test migrations
    
    private class ProgressTestMigration : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Progress Test Migration"
        override val description: String = "Migration that reports detailed progress"
        
        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?
        ): AppData {
            reportProgress(1, 5, "Initializing migration", progressCallback)
            kotlinx.coroutines.delay(10)
            
            reportProgress(2, 5, "Processing SSH identities", progressCallback)
            kotlinx.coroutines.delay(10)
            
            reportProgress(3, 5, "Processing server profiles", progressCallback)
            kotlinx.coroutines.delay(10)
            
            reportProgress(4, 5, "Processing projects", progressCallback)
            kotlinx.coroutines.delay(10)
            
            reportProgress(5, 5, "Finalizing migration", progressCallback)
            
            return updateDataVersion(data)
        }
        
        override suspend fun canMigrate(data: AppData): Boolean = data.version == 0
        override suspend fun getEstimatedSteps(data: AppData): Int = 5
    }
    
    private class FailingTestMigration : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Failing Test Migration"
        override val description: String = "Migration that always fails for testing"
        
        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?
        ): AppData {
            throw MigrationException.ExecutionException("Intentional test failure")
        }
        
        override suspend fun canMigrate(data: AppData): Boolean = data.version == 0
    }
}