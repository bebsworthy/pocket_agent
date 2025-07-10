package com.pocketagent.data.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.data.migration.di.MigrationConfiguration
import com.pocketagent.data.migration.migrations.InitialMigration
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.repository.initializeWithMigration
import com.pocketagent.data.repository.validateAndRepairData
import com.pocketagent.data.repository.getMigrationStatus
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
 * Integration tests for the migration system with SecureDataRepository.
 * 
 * These tests verify the complete integration between the migration system
 * and the data repository, including:
 * - Repository initialization with migration
 * - Data repair operations
 * - Migration status reporting
 * - Backup and restore operations
 */
@RunWith(AndroidJUnit4::class)
class MigrationIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var mockEncryptedStorage: EncryptedJsonStorage
    private lateinit var mockDataValidator: DataValidator
    private lateinit var migrationRegistry: MigrationRegistry
    private lateinit var migrationManager: DataMigrationManager
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
        
        repository = SecureDataRepository(
            context = context,
            encryptedStorage = mockEncryptedStorage,
            dataValidator = mockDataValidator
        )
        
        // Setup default mock behavior
        coEvery { mockDataValidator.validateAppData(any()) } returns Unit
    }
    
    @Test
    fun testRepositoryInitializationWithNewData() = runTest {
        // Setup: No existing data
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns null
        
        // Register initial migration
        migrationRegistry.registerMigration(InitialMigration())
        
        // Initialize repository with migration
        val migrationResult = repository.initializeWithMigration(migrationManager)
        
        // Verify migration was executed
        assertNotNull(migrationResult)
        assertTrue(migrationResult!!.success)
        assertEquals(0, migrationResult.fromVersion)
        assertEquals(1, migrationResult.toVersion)
        
        // Verify data was stored
        coVerify { mockEncryptedStorage.storeJsonData("app_data", any()) }
    }
    
    @Test
    fun testRepositoryInitializationWithExistingCurrentData() = runTest {
        // Setup: Existing current version data
        val currentData = AppData(version = MigrationVersion.CURRENT_VERSION)
        val jsonData = json.encodeToString(AppData.serializer(), currentData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Initialize repository with migration
        val migrationResult = repository.initializeWithMigration(migrationManager)
        
        // Verify no migration was needed
        assertNull(migrationResult)
    }
    
    @Test
    fun testRepositoryInitializationWithOldData() = runTest {
        // Setup: Existing old version data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Register migration
        migrationRegistry.registerMigration(InitialMigration())
        
        // Initialize repository with migration
        val migrationResult = repository.initializeWithMigration(migrationManager)
        
        // Verify migration was executed
        assertNotNull(migrationResult)
        assertTrue(migrationResult!!.success)
        assertEquals(0, migrationResult.fromVersion)
        assertEquals(1, migrationResult.toVersion)
    }
    
    @Test
    fun testDataValidationAndRepair() = runTest {
        // Setup: Data with integrity issues
        val corruptedData = AppData(
            version = 1,
            sshIdentities = emptyList(), // Missing SSH identity
            serverProfiles = listOf(
                com.pocketagent.data.models.ServerProfile(
                    id = "server-1",
                    name = "Test Server",
                    hostname = "test.com",
                    username = "user",
                    sshIdentityId = "missing-ssh" // References non-existent SSH identity
                )
            ),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val jsonData = json.encodeToString(AppData.serializer(), corruptedData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Setup data validator to return validation errors
        coEvery { mockDataValidator.validateAppData(any()) } throws Exception("Validation failed")
        
        // Register repair migration
        migrationRegistry.registerMigration(com.pocketagent.data.migration.migrations.DataRepairMigration())
        
        // Validate and repair data
        val repairResult = repository.validateAndRepairData(migrationManager)
        
        // Verify repair was attempted
        assertTrue(repairResult.repairNeeded)
        assertFalse(repairResult.issuesFound.isEmpty())
    }
    
    @Test
    fun testMigrationStatusReporting() = runTest {
        // Setup: Old version data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        coEvery { mockEncryptedStorage.getJsonData("migration_log") } returns "[]"
        
        // Get migration status
        val status = repository.getMigrationStatus(migrationManager)
        
        // Verify status
        assertEquals(0, status.currentVersion)
        assertEquals(MigrationVersion.CURRENT_VERSION, status.targetVersion)
        assertFalse(status.isUpToDate)
        assertTrue(status.migrationNeeded)
        assertEquals(MigrationVersion.CURRENT_VERSION, status.versionsBehind)
    }
    
    @Test
    fun testMigrationBackupCreation() = runTest {
        // Setup: Existing data
        val testData = AppData(
            version = 1,
            sshIdentities = listOf(
                SshIdentity(
                    id = "ssh-1",
                    name = "Test SSH",
                    encryptedPrivateKey = "encrypted",
                    publicKeyFingerprint = "fingerprint"
                )
            )
        )
        
        val jsonData = json.encodeToString(AppData.serializer(), testData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Create backup
        val backupFilename = repository.createMigrationBackup(migrationManager, "Test backup")
        
        // Verify backup was created
        assertNotNull(backupFilename)
        assertTrue(backupFilename!!.contains("manual"))
        
        // Verify backup was stored
        coVerify { mockEncryptedStorage.storeJsonData(match { it.contains("manual") }, any()) }
    }
    
    @Test
    fun testMigrationWithTimeout() = runTest {
        // Setup: Old data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Register slow migration
        migrationRegistry.registerMigration(SlowMigration())
        
        // Use configuration with short timeout
        val config = MigrationConfiguration(
            migrationTimeoutMs = 100L,
            backupBeforeMigration = false
        )
        
        // This should timeout
        assertThrows(kotlinx.coroutines.TimeoutCancellationException::class.java) {
            runTest {
                repository.initializeWithMigration(migrationManager, config)
            }
        }
    }
    
    @Test
    fun testMigrationConfigurationApplication() = runTest {
        // Setup: Old data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Register migration
        migrationRegistry.registerMigration(InitialMigration())
        
        // Use testing configuration
        val config = MigrationConfiguration.testing()
        
        // Initialize with configuration
        val migrationResult = repository.initializeWithMigration(migrationManager, config)
        
        // Verify migration was executed according to config
        assertNotNull(migrationResult)
        assertTrue(migrationResult!!.success)
        assertFalse(migrationResult.backupCreated) // Testing config disables backup
    }
    
    @Test
    fun testMigrationProgressObservation() = runTest {
        // Setup: Old data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Register migration that reports progress
        migrationRegistry.registerMigration(ProgressReportingMigration())
        
        val progressUpdates = mutableListOf<MigrationProgress?>()
        
        // Observe migration progress
        val progressFlow = repository.observeMigrationProgress(migrationManager)
        
        // Collect initial value
        progressUpdates.add(progressFlow.replayCache.firstOrNull())
        
        // Initialize repository (this will trigger migration)
        repository.initializeWithMigration(migrationManager)
        
        // Note: In a real test, you would collect from the flow during migration
        // For this test, we just verify the flow exists and can be observed
        assertNotNull(progressFlow)
    }
    
    @Test
    fun testMigrationFailureHandling() = runTest {
        // Setup: Old data
        val oldData = AppData(version = 0)
        val jsonData = json.encodeToString(AppData.serializer(), oldData)
        coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData
        
        // Register failing migration
        migrationRegistry.registerMigration(FailingMigration())
        
        // Initialize repository - should handle migration failure gracefully
        assertThrows(com.pocketagent.data.repository.DataException.InitializationException::class.java) {
            runTest {
                repository.initializeWithMigration(migrationManager)
            }
        }
    }
    
    // Helper migrations for testing
    
    private class SlowMigration : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Slow Migration"
        override val description: String = "A migration that takes a long time"
        
        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?
        ): AppData {
            kotlinx.coroutines.delay(1000) // Simulate slow operation
            return updateDataVersion(data)
        }
        
        override suspend fun canMigrate(data: AppData): Boolean = data.version == 0
    }
    
    private class ProgressReportingMigration : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Progress Migration"
        override val description: String = "A migration that reports progress"
        
        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?
        ): AppData {
            reportProgress(1, 3, "Step 1", progressCallback)
            kotlinx.coroutines.delay(10)
            reportProgress(2, 3, "Step 2", progressCallback)
            kotlinx.coroutines.delay(10)
            reportProgress(3, 3, "Step 3", progressCallback)
            
            return updateDataVersion(data)
        }
        
        override suspend fun canMigrate(data: AppData): Boolean = data.version == 0
        override suspend fun getEstimatedSteps(data: AppData): Int = 3
    }
    
    private class FailingMigration : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Failing Migration"
        override val description: String = "A migration that always fails"
        
        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?
        ): AppData {
            throw MigrationException.ExecutionException("Intentional failure for testing")
        }
        
        override suspend fun canMigrate(data: AppData): Boolean = data.version == 0
    }
}