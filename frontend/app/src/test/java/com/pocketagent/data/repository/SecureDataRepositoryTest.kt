package com.pocketagent.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.MessageType
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SecureDataRepository.
 *
 * These tests verify the correct behavior of the SecureDataRepository including:
 * - Data operations (CRUD)
 * - Validation and constraint enforcement
 * - Error handling
 * - Caching behavior
 * - Observable flows
 */
@RunWith(AndroidJUnit4::class)
class SecureDataRepositoryTest {
    private lateinit var repository: SecureDataRepository
    private lateinit var mockEncryptedStorage: EncryptedJsonStorage
    private lateinit var dataValidator: DataValidator
    private lateinit var context: Context

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEncryptedStorage = mockk()
        dataValidator = DataValidator()

        // Setup default mock behavior
        coEvery { mockEncryptedStorage.getJsonData(any()) } returns null
        coEvery { mockEncryptedStorage.storeJsonData(any(), any()) } returns Unit
        coEvery { mockEncryptedStorage.clearAllData() } returns Unit
        coEvery { mockEncryptedStorage.createBackup() } returns "backup_20240101_120000.json"
        coEvery { mockEncryptedStorage.restoreBackup(any()) } returns true
        coEvery { mockEncryptedStorage.getStorageStats() } returns
            mockk {
                every { totalFiles } returns 1
                every { totalSize } returns 1024L
                every { lastModified } returns System.currentTimeMillis()
                every { backupCount } returns 3
                every { backupSize } returns 3072L
                every { isHealthy } returns true
            }
        coEvery { mockEncryptedStorage.validateStorage() } returns
            mockk {
                every { isValid } returns true
            }

        repository = SecureDataRepository(context, mockEncryptedStorage, dataValidator)
    }

    @Test
    fun `initialize should load existing data when available`() =
        runTest {
            // Given
            val existingData =
                AppData(
                    sshIdentities = listOf(createTestSshIdentity()),
                    serverProfiles = listOf(createTestServerProfile()),
                    projects = listOf(createTestProject()),
                )
            val jsonData = json.encodeToString(AppData.serializer(), existingData)
            coEvery { mockEncryptedStorage.getJsonData("app_data") } returns jsonData

            // When
            repository.initialize()

            // Then
            val loadedData = repository.loadData()
            assertEquals(1, loadedData.sshIdentities.size)
            assertEquals(1, loadedData.serverProfiles.size)
            assertEquals(1, loadedData.projects.size)
        }

    @Test
    fun `initialize should create empty data when no existing data`() =
        runTest {
            // Given
            coEvery { mockEncryptedStorage.getJsonData("app_data") } returns null

            // When
            repository.initialize()

            // Then
            val loadedData = repository.loadData()
            assertTrue(loadedData.sshIdentities.isEmpty())
            assertTrue(loadedData.serverProfiles.isEmpty())
            assertTrue(loadedData.projects.isEmpty())
        }

    @Test
    fun `addSshIdentity should add new identity successfully`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()

            // When
            repository.addSshIdentity(identity)

            // Then
            val identities = repository.getAllSshIdentities()
            assertEquals(1, identities.size)
            assertEquals(identity, identities[0])

            coVerify { mockEncryptedStorage.storeJsonData("app_data", any()) }
        }

    @Test
    fun `addSshIdentity should throw exception for duplicate name`() =
        runTest {
            // Given
            repository.initialize()
            val identity1 = createTestSshIdentity(name = "Test Key")
            val identity2 = createTestSshIdentity(name = "Test Key", id = "different-id")

            repository.addSshIdentity(identity1)

            // When & Then
            assertFailsWith<DataException.DuplicateNameException> {
                repository.addSshIdentity(identity2)
            }
        }

    @Test
    fun `updateSshIdentity should update existing identity`() =
        runTest {
            // Given
            repository.initialize()
            val original = createTestSshIdentity()
            repository.addSshIdentity(original)

            val updated = original.copy(name = "Updated Name")

            // When
            repository.updateSshIdentity(updated)

            // Then
            val retrieved = repository.getSshIdentityById(original.id)
            assertEquals("Updated Name", retrieved?.name)
        }

    @Test
    fun `updateSshIdentity should throw exception for non-existent identity`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()

            // When & Then
            assertFailsWith<DataException.EntityNotFoundException> {
                repository.updateSshIdentity(identity)
            }
        }

    @Test
    fun `deleteSshIdentity should remove identity successfully`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            repository.addSshIdentity(identity)

            // When
            repository.deleteSshIdentity(identity.id)

            // Then
            val retrieved = repository.getSshIdentityById(identity.id)
            assertNull(retrieved)
        }

    @Test
    fun `deleteSshIdentity should throw exception when identity is in use`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val serverProfile = createTestServerProfile(sshIdentityId = identity.id)

            repository.addSshIdentity(identity)
            repository.addServerProfile(serverProfile)

            // When & Then
            assertFailsWith<DataException.ConstraintViolationException> {
                repository.deleteSshIdentity(identity.id)
            }
        }

    @Test
    fun `addServerProfile should add new profile successfully`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)

            repository.addSshIdentity(identity)

            // When
            repository.addServerProfile(profile)

            // Then
            val profiles = repository.getAllServerProfiles()
            assertEquals(1, profiles.size)
            assertEquals(profile, profiles[0])
        }

    @Test
    fun `addServerProfile should throw exception when SSH identity not found`() =
        runTest {
            // Given
            repository.initialize()
            val profile = createTestServerProfile(sshIdentityId = "non-existent-id")

            // When & Then
            assertFailsWith<DataException.ConstraintViolationException> {
                repository.addServerProfile(profile)
            }
        }

    @Test
    fun `deleteServerProfile should throw exception when profile is in use`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project)

            // When & Then
            assertFailsWith<DataException.ConstraintViolationException> {
                repository.deleteServerProfile(profile.id)
            }
        }

    @Test
    fun `addProject should add new project successfully`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)

            // When
            repository.addProject(project)

            // Then
            val projects = repository.getAllProjects()
            assertEquals(1, projects.size)
            assertEquals(project, projects[0])
        }

    @Test
    fun `addProject should throw exception when server profile not found`() =
        runTest {
            // Given
            repository.initialize()
            val project = createTestProject(serverProfileId = "non-existent-id")

            // When & Then
            assertFailsWith<DataException.ConstraintViolationException> {
                repository.addProject(project)
            }
        }

    @Test
    fun `deleteProject should remove project and its messages`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)
            val message = createTestMessage()

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project)
            repository.addMessage(project.id, message)

            // When
            repository.deleteProject(project.id)

            // Then
            val projects = repository.getAllProjects()
            assertTrue(projects.isEmpty())

            val messages = repository.getProjectMessages(project.id)
            assertTrue(messages.isEmpty())
        }

    @Test
    fun `addMessage should add message to project`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)
            val message = createTestMessage()

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project)

            // When
            repository.addMessage(project.id, message)

            // Then
            val messages = repository.getProjectMessages(project.id)
            assertEquals(1, messages.size)
            assertEquals(message, messages[0])
        }

    @Test
    fun `addMessage should limit messages per project`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project)

            // Add more than max messages
            repeat(1005) { i ->
                val message = createTestMessage(id = "msg-$i", content = "Message $i")
                repository.addMessage(project.id, message)
            }

            // When
            val messages = repository.getProjectMessages(project.id)

            // Then
            assertEquals(1000, messages.size) // Should be limited to 1000
        }

    @Test
    fun `searchProjects should return matching projects`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project1 = createTestProject(serverProfileId = profile.id, name = "Web Project")
            val project2 = createTestProject(serverProfileId = profile.id, name = "Mobile App", id = "project-2")
            val project3 = createTestProject(serverProfileId = profile.id, name = "Backend API", id = "project-3")

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project1)
            repository.addProject(project2)
            repository.addProject(project3)

            // When
            val results = repository.searchProjects("project")

            // Then
            assertEquals(1, results.size)
            assertEquals("Web Project", results[0].name)
        }

    @Test
    fun `getProjectsForServer should return projects for specific server`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile1 = createTestServerProfile(sshIdentityId = identity.id, name = "Server 1")
            val profile2 = createTestServerProfile(sshIdentityId = identity.id, name = "Server 2", id = "server-2")
            val project1 = createTestProject(serverProfileId = profile1.id, name = "Project 1")
            val project2 = createTestProject(serverProfileId = profile2.id, name = "Project 2", id = "project-2")

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile1)
            repository.addServerProfile(profile2)
            repository.addProject(project1)
            repository.addProject(project2)

            // When
            val results = repository.getProjectsForServer(profile1.id)

            // Then
            assertEquals(1, results.size)
            assertEquals("Project 1", results[0].name)
        }

    @Test
    fun `observeProjects should emit updates when projects change`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)

            // When
            val flow = repository.observeProjects()

            // Initially empty
            val initial = flow.first()
            assertTrue(initial.isEmpty())

            // Add project
            repository.addProject(project)

            // Then
            val updated = flow.first()
            assertEquals(1, updated.size)
            assertEquals(project, updated[0])
        }

    @Test
    fun `exportData should return JSON representation of data`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            repository.addSshIdentity(identity)

            // When
            val exported = repository.exportData()

            // Then
            assertTrue(exported.isNotEmpty())
            assertTrue(exported.contains("Test SSH Key"))
        }

    @Test
    fun `importData should load data from JSON`() =
        runTest {
            // Given
            repository.initialize()
            val data = AppData(sshIdentities = listOf(createTestSshIdentity()))
            val jsonData = json.encodeToString(AppData.serializer(), data)

            // When
            repository.importData(jsonData)

            // Then
            val identities = repository.getAllSshIdentities()
            assertEquals(1, identities.size)
            assertEquals("Test SSH Key", identities[0].name)
        }

    @Test
    fun `createBackup should return backup filename`() =
        runTest {
            // Given
            repository.initialize()

            // When
            val backupFile = repository.createBackup()

            // Then
            assertNotNull(backupFile)
            assertTrue(backupFile.contains("backup_"))

            coVerify { mockEncryptedStorage.createBackup() }
        }

    @Test
    fun `restoreBackup should restore data and reinitialize`() =
        runTest {
            // Given
            repository.initialize()
            val backupFile = "backup_20240101_120000.json"

            // When
            val success = repository.restoreBackup(backupFile)

            // Then
            assertTrue(success)
            coVerify { mockEncryptedStorage.restoreBackup(backupFile) }
        }

    @Test
    fun `clearAllData should remove all data`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            repository.addSshIdentity(identity)

            // When
            repository.clearAllData()

            // Then
            coVerify { mockEncryptedStorage.clearAllData() }
        }

    @Test
    fun `getDataSummary should return correct statistics`() =
        runTest {
            // Given
            repository.initialize()
            val identity = createTestSshIdentity()
            val profile = createTestServerProfile(sshIdentityId = identity.id)
            val project = createTestProject(serverProfileId = profile.id)
            val message = createTestMessage()

            repository.addSshIdentity(identity)
            repository.addServerProfile(profile)
            repository.addProject(project)
            repository.addMessage(project.id, message)

            // When
            val summary = repository.getDataSummary()

            // Then
            assertEquals(1, summary.sshIdentityCount)
            assertEquals(1, summary.serverProfileCount)
            assertEquals(1, summary.projectCount)
            assertEquals(1, summary.totalMessageCount)
            assertTrue(summary.lastModified > 0)
        }

    // Helper methods for creating test data

    private fun createTestSshIdentity(
        id: String = "test-ssh-id",
        name: String = "Test SSH Key",
        encryptedPrivateKey: String = "encrypted-private-key-data",
        publicKeyFingerprint: String = "SHA256:abc123def456",
    ): SshIdentity =
        SshIdentity(
            id = id,
            name = name,
            encryptedPrivateKey = encryptedPrivateKey,
            publicKeyFingerprint = publicKeyFingerprint,
            description = "Test SSH identity",
        )

    private fun createTestServerProfile(
        id: String = "test-server-id",
        name: String = "Test Server",
        hostname: String = "test.example.com",
        port: Int = 22,
        username: String = "testuser",
        sshIdentityId: String = "test-ssh-id",
        wrapperPort: Int = 8080,
    ): ServerProfile =
        ServerProfile(
            id = id,
            name = name,
            hostname = hostname,
            port = port,
            username = username,
            sshIdentityId = sshIdentityId,
            wrapperPort = wrapperPort,
            status = ConnectionStatus.NEVER_CONNECTED,
        )

    private fun createTestProject(
        id: String = "test-project-id",
        name: String = "Test Project",
        serverProfileId: String = "test-server-id",
        projectPath: String = "/home/user/projects/test",
        scriptsFolder: String = "scripts",
    ): Project =
        Project(
            id = id,
            name = name,
            serverProfileId = serverProfileId,
            projectPath = projectPath,
            scriptsFolder = scriptsFolder,
            status = ProjectStatus.INACTIVE,
        )

    private fun createTestMessage(
        id: String = "test-message-id",
        content: String = "Test message content",
        type: MessageType = MessageType.USER_INPUT,
    ): Message =
        Message(
            id = id,
            content = content,
            type = type,
            timestamp = System.currentTimeMillis(),
        )
}
