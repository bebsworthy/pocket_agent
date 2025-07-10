package com.pocketagent.data.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.data.migration.migrations.DataRepairMigration
import com.pocketagent.data.migration.migrations.InitialMigration
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Comprehensive tests for the data migration system.
 *
 * These tests verify the functionality of:
 * - Migration version management
 * - Migration registry operations
 * - Migration execution and rollback
 * - Data integrity validation
 * - Progress reporting
 * - Error handling and recovery
 */
@RunWith(AndroidJUnit4::class)
class MigrationSystemTest {
    private lateinit var context: Context
    private lateinit var mockEncryptedStorage: EncryptedJsonStorage
    private lateinit var mockDataValidator: DataValidator
    private lateinit var migrationRegistry: MigrationRegistry
    private lateinit var migrationManager: DataMigrationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEncryptedStorage = mockk(relaxed = true)
        mockDataValidator = mockk(relaxed = true)
        migrationRegistry = MigrationRegistry()

        migrationManager =
            DataMigrationManager(
                context = context,
                encryptedStorage = mockEncryptedStorage,
                migrationRegistry = migrationRegistry,
                dataValidator = mockDataValidator,
            )

        // Setup default mock behavior
        coEvery { mockDataValidator.validateAppData(any()) } returns Unit
    }

    @Test
    fun testMigrationVersionComparison() {
        val version1 = MigrationVersion(1, "Initial", "First version")
        val version2 = MigrationVersion(2, "Update", "Second version")
        val version1Duplicate = MigrationVersion(1, "Another", "Same version number")

        assertTrue(version1 < version2)
        assertFalse(version2 < version1)
        assertEquals(0, version1.compareTo(version1Duplicate))

        assertEquals("v1: Initial", version1.toString())
    }

    @Test
    fun testMigrationRegistryBasicOperations() =
        runTest {
            val migration = InitialMigration()

            // Register migration
            migrationRegistry.registerMigration(migration)

            // Find migration
            val foundMigration = migrationRegistry.findMigration(0, 1)
            assertNotNull(foundMigration)
            assertEquals(migration.name, foundMigration?.name)

            // Get all migrations
            val allMigrations = migrationRegistry.getAllMigrations()
            assertEquals(1, allMigrations.size)
            assertEquals(migration.name, allMigrations[0].name)
        }

    @Test
    fun testMigrationRegistryDuplicateRegistration() =
        runTest {
            val migration1 = InitialMigration()
            val migration2 = TestMigration(0, 1, "Duplicate")

            migrationRegistry.registerMigration(migration1)

            // Should throw exception when registering duplicate
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { migrationRegistry.registerMigration(migration2) }
            }
        }

    @Test
    fun testMigrationPathFinding() =
        runTest {
            // Register a chain of migrations: 0->1->2->3
            migrationRegistry.registerMigration(TestMigration(0, 1, "Step 1"))
            migrationRegistry.registerMigration(TestMigration(1, 2, "Step 2"))
            migrationRegistry.registerMigration(TestMigration(2, 3, "Step 3"))

            // Test direct path
            val directPath = migrationRegistry.findMigrationPath(0, 1)
            assertEquals(1, directPath.size)
            assertEquals("Step 1", directPath[0].name)

            // Test multi-step path
            val multiStepPath = migrationRegistry.findMigrationPath(0, 3)
            assertEquals(3, multiStepPath.size)
            assertEquals("Step 1", multiStepPath[0].name)
            assertEquals("Step 2", multiStepPath[1].name)
            assertEquals("Step 3", multiStepPath[2].name)

            // Test no path available
            val noPath = migrationRegistry.findMigrationPath(0, 5)
            assertTrue(noPath.isEmpty())
        }

    @Test
    fun testInitialMigrationExecution() =
        runTest {
            val migration = InitialMigration()
            val emptyData = AppData(version = 0)

            val result = migration.migrate(emptyData)

            assertEquals(1, result.version)
            assertTrue(result.sshIdentities.isEmpty())
            assertTrue(result.serverProfiles.isEmpty())
            assertTrue(result.projects.isEmpty())
            assertTrue(result.messages.isEmpty())
        }

    @Test
    fun testDataRepairMigration() =
        runTest {
            val migration = DataRepairMigration()

            // Create data with orphaned relationships
            val orphanedSshIdentity =
                SshIdentity(
                    id = "ssh-1",
                    name = "Test SSH",
                    encryptedPrivateKey = "encrypted",
                    publicKeyFingerprint = "fingerprint",
                )

            val validSshIdentity =
                SshIdentity(
                    id = "ssh-2",
                    name = "Valid SSH",
                    encryptedPrivateKey = "encrypted",
                    publicKeyFingerprint = "fingerprint",
                )

            val orphanedServerProfile =
                ServerProfile(
                    id = "server-1",
                    name = "Orphaned Server",
                    hostname = "orphaned.com",
                    username = "user",
                    // This SSH identity doesn't exist
                    sshIdentityId = "non-existent-ssh",
                )

            val validServerProfile =
                ServerProfile(
                    id = "server-2",
                    name = "Valid Server",
                    hostname = "valid.com",
                    username = "user",
                    sshIdentityId = "ssh-2",
                )

            val orphanedProject =
                Project(
                    id = "project-1",
                    name = "Orphaned Project",
                    projectPath = "/path",
                    // This server doesn't exist
                    serverProfileId = "non-existent-server",
                )

            val validProject =
                Project(
                    id = "project-2",
                    name = "Valid Project",
                    projectPath = "/path",
                    serverProfileId = "server-2",
                )

            val corruptedData =
                AppData(
                    version = 1,
                    // orphanedSshIdentity is not included
                    sshIdentities = listOf(validSshIdentity),
                    serverProfiles = listOf(orphanedServerProfile, validServerProfile),
                    projects = listOf(orphanedProject, validProject),
                    messages =
                        mapOf(
                            "project-2" to emptyList(),
                            // Orphaned messages
                            "non-existent-project" to emptyList(),
                        ),
                )

            val repairedData = migration.migrate(corruptedData)

            // Verify orphaned relationships were removed
            assertEquals(1, repairedData.serverProfiles.size)
            assertEquals("Valid Server", repairedData.serverProfiles[0].name)

            assertEquals(1, repairedData.projects.size)
            assertEquals("Valid Project", repairedData.projects[0].name)

            assertEquals(1, repairedData.messages.size)
            assertTrue(repairedData.messages.containsKey("project-2"))
            assertFalse(repairedData.messages.containsKey("non-existent-project"))
        }

    @Test
    fun testMigrationManagerMigrationDetection() =
        runTest {
            // Setup migration registry
            migrationRegistry.registerMigration(TestMigration(1, 2, "Upgrade"))

            // Test data that needs migration
            val oldData = AppData(version = 1)
            assertTrue(migrationManager.isMigrationNeeded(oldData))

            // Test data that doesn't need migration
            val currentData = AppData(version = MigrationVersion.CURRENT_VERSION)
            assertFalse(migrationManager.isMigrationNeeded(currentData))
        }

    @Test
    fun testMigrationProgressReporting() =
        runTest {
            val migration = TestMigrationWithProgress()
            val data = AppData(version = 0)

            val progressUpdates = mutableListOf<MigrationProgress>()

            migration.migrate(data) { progress ->
                progressUpdates.add(progress)
            }

            // Verify progress was reported
            assertTrue(progressUpdates.isNotEmpty())
            assertEquals(1, progressUpdates[0].currentStep)
            assertEquals(3, progressUpdates[0].totalSteps)
            assertTrue(progressUpdates.last().currentStep <= progressUpdates.last().totalSteps)
        }

    @Test
    fun testMigrationValidationFailure() =
        runTest {
            val migration = TestMigration(1, 2, "Test")
            val invalidData = AppData(version = 0) // Wrong version

            assertThrows(MigrationException.InvalidVersionException::class.java) {
                runBlocking { migration.migrate(invalidData) }
            }
        }

    @Test
    fun testMigrationManagerBackupCreation() =
        runTest {
            // Setup mocks
            coEvery { mockEncryptedStorage.storeJsonData(any(), any()) } returns Unit
            coEvery { mockEncryptedStorage.getJsonData(any()) } returns null

            val testData = AppData(version = 1)
            val backupFilename = migrationManager.createManualBackup(testData, "Test backup")

            assertNotNull(backupFilename)
            assertTrue(backupFilename!!.contains("manual"))

            // Verify backup was stored
            coVerify { mockEncryptedStorage.storeJsonData(match { it.contains("manual") }, any()) }
        }

    @Test
    fun testMigrationHistoryLogging() =
        runTest {
            // Setup mocks
            coEvery { mockEncryptedStorage.getJsonData("migration_log") } returns null
            coEvery { mockEncryptedStorage.storeJsonData("migration_log", any()) } returns Unit

            // Setup migration
            migrationRegistry.registerMigration(TestMigration(1, 2, "Test Migration"))

            val testData = AppData(version = 1)
            val result = migrationManager.migrateToVersion(testData, 2, false)

            assertTrue(result.success)

            // Verify history was logged
            coVerify { mockEncryptedStorage.storeJsonData("migration_log", any()) }
        }

    @Test
    fun testDataIntegrityValidation() =
        runTest {
            val validData =
                AppData(
                    version = 1,
                    sshIdentities =
                        listOf(
                            SshIdentity(
                                id = "ssh-1",
                                name = "Test SSH",
                                encryptedPrivateKey = "encrypted",
                                publicKeyFingerprint = "fingerprint",
                            ),
                        ),
                    serverProfiles =
                        listOf(
                            ServerProfile(
                                id = "server-1",
                                name = "Test Server",
                                hostname = "test.com",
                                username = "user",
                                sshIdentityId = "ssh-1",
                            ),
                        ),
                    projects =
                        listOf(
                            Project(
                                id = "project-1",
                                name = "Test Project",
                                projectPath = "/path",
                                serverProfileId = "server-1",
                            ),
                        ),
                )

            val integrityResult = migrationManager.validateDataIntegrity(validData)

            assertTrue(integrityResult.isValid)
            assertTrue(integrityResult.errors.isEmpty())
        }

    // Helper classes for testing

    private class TestMigration(
        override val fromVersion: Int,
        override val toVersion: Int,
        override val name: String,
    ) : BaseDataMigration() {
        override val description: String = "Test migration from $fromVersion to $toVersion"

        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            validateDataVersion(data)
            return updateDataVersion(data)
        }

        override suspend fun canMigrate(data: AppData): Boolean {
            return data.version == fromVersion
        }
    }

    private class TestMigrationWithProgress : BaseDataMigration() {
        override val fromVersion: Int = 0
        override val toVersion: Int = 1
        override val name: String = "Progress Test"
        override val description: String = "Tests progress reporting"

        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            reportProgress(1, 3, "Step 1", progressCallback)
            reportProgress(2, 3, "Step 2", progressCallback)
            reportProgress(3, 3, "Step 3", progressCallback)

            return updateDataVersion(data)
        }

        override suspend fun canMigrate(data: AppData): Boolean = true

        override suspend fun getEstimatedSteps(data: AppData): Int = 3
    }
}
