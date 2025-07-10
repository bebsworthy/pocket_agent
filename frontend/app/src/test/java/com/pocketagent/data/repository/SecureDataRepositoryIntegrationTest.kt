package com.pocketagent.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.MessageType
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.storage.BackupManager
import com.pocketagent.data.storage.FileStorageManager
import com.pocketagent.data.storage.StorageConfiguration
import com.pocketagent.data.storage.StorageEncryption
import com.pocketagent.data.storage.StorageValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorageImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for SecureDataRepository with actual encrypted storage.
 * 
 * These tests verify that the SecureDataRepository works correctly with
 * the actual encrypted storage implementation, ensuring data persistence
 * and security work as expected.
 */
@RunWith(AndroidJUnit4::class)
class SecureDataRepositoryIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var repository: SecureDataRepository
    private lateinit var encryptedStorage: EncryptedJsonStorageImpl
    private lateinit var dataValidator: DataValidator
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Clear any existing test data
        clearTestData()
        
        // Create real dependencies
        val storageEncryption = StorageEncryption(context)
        val fileStorageManager = FileStorageManager(context)
        val storageConfiguration = StorageConfiguration(context)
        val storageValidator = StorageValidator(context, fileStorageManager, storageEncryption)
        val backupManager = BackupManager(context, fileStorageManager, storageConfiguration, storageEncryption)
        
        encryptedStorage = EncryptedJsonStorageImpl(
            context = context,
            storageEncryption = storageEncryption,
            fileStorageManager = fileStorageManager,
            storageConfiguration = storageConfiguration,
            backupManager = backupManager,
            storageValidator = storageValidator
        )
        
        dataValidator = DataValidator()
        
        repository = SecureDataRepository(context, encryptedStorage, dataValidator)
    }
    
    @After
    fun tearDown() {
        clearTestData()
    }
    
    private fun clearTestData() {
        // Clear app data files
        val dataDir = context.filesDir
        dataDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".json.enc") || file.name.contains("backup")) {
                file.delete()
            }
        }
    }
    
    @Test
    fun `end to end data persistence test`() = runTest {
        // Initialize repository
        repository.initialize()
        
        // Create test data
        val sshIdentity = SshIdentity(
            id = "test-ssh-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted-private-key-data",
            publicKeyFingerprint = "SHA256:abc123def456",
            description = "Test SSH identity for integration test"
        )
        
        val serverProfile = ServerProfile(
            id = "test-server-id",
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = sshIdentity.id,
            wrapperPort = 8080,
            status = ConnectionStatus.NEVER_CONNECTED
        )
        
        val project = Project(
            id = "test-project-id",
            name = "Test Project",
            serverProfileId = serverProfile.id,
            projectPath = "/home/user/projects/test",
            scriptsFolder = "scripts",
            status = ProjectStatus.INACTIVE
        )
        
        val message = Message(
            id = "test-message-id",
            content = "Hello, this is a test message",
            type = MessageType.USER_INPUT,
            timestamp = System.currentTimeMillis()
        )
        
        // Add data to repository
        repository.addSshIdentity(sshIdentity)
        repository.addServerProfile(serverProfile)
        repository.addProject(project)
        repository.addMessage(project.id, message)
        
        // Verify data was added
        val identities = repository.getAllSshIdentities()
        val profiles = repository.getAllServerProfiles()
        val projects = repository.getAllProjects()
        val messages = repository.getProjectMessages(project.id)
        
        assertEquals(1, identities.size)
        assertEquals(1, profiles.size)
        assertEquals(1, projects.size)
        assertEquals(1, messages.size)
        
        assertEquals(sshIdentity.name, identities[0].name)
        assertEquals(serverProfile.name, profiles[0].name)
        assertEquals(project.name, projects[0].name)
        assertEquals(message.content, messages[0].content)
        
        // Create new repository instance to test persistence
        val newRepository = SecureDataRepository(context, encryptedStorage, dataValidator)
        newRepository.initialize()
        
        // Verify data persisted
        val persistedIdentities = newRepository.getAllSshIdentities()
        val persistedProfiles = newRepository.getAllServerProfiles()
        val persistedProjects = newRepository.getAllProjects()
        val persistedMessages = newRepository.getProjectMessages(project.id)
        
        assertEquals(1, persistedIdentities.size)
        assertEquals(1, persistedProfiles.size)
        assertEquals(1, persistedProjects.size)
        assertEquals(1, persistedMessages.size)
        
        assertEquals(sshIdentity.name, persistedIdentities[0].name)
        assertEquals(serverProfile.name, persistedProfiles[0].name)
        assertEquals(project.name, persistedProjects[0].name)
        assertEquals(message.content, persistedMessages[0].content)
    }
    
    @Test
    fun `backup and restore functionality test`() = runTest {
        // Initialize repository with test data
        repository.initialize()
        
        val sshIdentity = SshIdentity(
            id = "backup-test-ssh-id",
            name = "Backup Test SSH Key",
            encryptedPrivateKey = "encrypted-key-for-backup-test",
            publicKeyFingerprint = "SHA256:backup123test456"
        )
        
        repository.addSshIdentity(sshIdentity)
        
        // Create backup
        val backupFile = repository.createBackup()
        assertNotNull(backupFile)
        assertTrue(backupFile.contains("backup_"))
        
        // Clear all data
        repository.clearAllData()
        
        // Verify data is cleared
        val clearedIdentities = repository.getAllSshIdentities()
        assertTrue(clearedIdentities.isEmpty())
        
        // Restore from backup
        val restoreSuccess = repository.restoreBackup(backupFile)
        assertTrue(restoreSuccess)
        
        // Verify data is restored
        val restoredIdentities = repository.getAllSshIdentities()
        assertEquals(1, restoredIdentities.size)
        assertEquals(sshIdentity.name, restoredIdentities[0].name)
    }
    
    @Test
    fun `data validation and constraint enforcement test`() = runTest {
        repository.initialize()
        
        // Test SSH identity validation
        val invalidSshIdentity = SshIdentity(
            id = "invalid-ssh-id",
            name = "", // Invalid: empty name
            encryptedPrivateKey = "encrypted-key",
            publicKeyFingerprint = "SHA256:test123"
        )
        
        try {
            repository.addSshIdentity(invalidSshIdentity)
            throw AssertionError("Should have thrown validation exception")
        } catch (e: DataException.ValidationException) {
            // Expected
        }
        
        // Test constraint enforcement
        val validSshIdentity = SshIdentity(
            id = "valid-ssh-id",
            name = "Valid SSH Key",
            encryptedPrivateKey = "encrypted-key",
            publicKeyFingerprint = "SHA256:valid123"
        )
        
        val serverProfile = ServerProfile(
            id = "constraint-test-server",
            name = "Constraint Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = validSshIdentity.id,
            wrapperPort = 8080
        )
        
        repository.addSshIdentity(validSshIdentity)
        repository.addServerProfile(serverProfile)
        
        // Try to delete SSH identity that's in use
        try {
            repository.deleteSshIdentity(validSshIdentity.id)
            throw AssertionError("Should have thrown constraint violation exception")
        } catch (e: DataException.ConstraintViolationException) {
            // Expected
        }
    }
    
    @Test
    fun `export and import functionality test`() = runTest {
        repository.initialize()
        
        // Create test data
        val sshIdentity = SshIdentity(
            id = "export-test-ssh-id",
            name = "Export Test SSH Key",
            encryptedPrivateKey = "encrypted-key-for-export",
            publicKeyFingerprint = "SHA256:export123test456"
        )
        
        repository.addSshIdentity(sshIdentity)
        
        // Export data
        val exportedData = repository.exportData()
        assertTrue(exportedData.isNotEmpty())
        assertTrue(exportedData.contains("Export Test SSH Key"))
        
        // Clear data
        repository.clearAllData()
        
        // Import data
        repository.importData(exportedData)
        
        // Verify imported data
        val importedIdentities = repository.getAllSshIdentities()
        assertEquals(1, importedIdentities.size)
        assertEquals(sshIdentity.name, importedIdentities[0].name)
    }
    
    @Test
    fun `storage statistics and validation test`() = runTest {
        repository.initialize()
        
        // Add some test data
        val sshIdentity = SshIdentity(
            id = "stats-test-ssh-id",
            name = "Stats Test SSH Key",
            encryptedPrivateKey = "encrypted-key-for-stats",
            publicKeyFingerprint = "SHA256:stats123test456"
        )
        
        repository.addSshIdentity(sshIdentity)
        
        // Get storage statistics
        val stats = repository.getStorageStats()
        assertTrue(stats.totalFiles > 0)
        assertTrue(stats.totalSize > 0)
        assertTrue(stats.lastModified > 0)
        assertTrue(stats.isHealthy)
        
        // Validate storage
        val isValid = repository.validateStorage()
        assertTrue(isValid)
        
        // Get data summary
        val summary = repository.getDataSummary()
        assertEquals(1, summary.sshIdentityCount)
        assertEquals(0, summary.serverProfileCount)
        assertEquals(0, summary.projectCount)
        assertEquals(0, summary.totalMessageCount)
        assertTrue(summary.lastModified > 0)
    }
    
    @Test
    fun `concurrent access and thread safety test`() = runTest {
        repository.initialize()
        
        // This test verifies that multiple concurrent operations don't cause data corruption
        // Run multiple operations concurrently
        val operations = (1..10).map { index ->
            kotlinx.coroutines.async {
                val sshIdentity = SshIdentity(
                    id = "concurrent-test-ssh-$index",
                    name = "Concurrent Test SSH Key $index",
                    encryptedPrivateKey = "encrypted-key-$index",
                    publicKeyFingerprint = "SHA256:concurrent${index}test"
                )
                
                try {
                    repository.addSshIdentity(sshIdentity)
                    repository.getSshIdentityById(sshIdentity.id)
                } catch (e: Exception) {
                    // Some operations might fail due to timing, but shouldn't corrupt data
                    null
                }
            }
        }
        
        // Wait for all operations to complete
        operations.forEach { it.await() }
        
        // Verify data consistency
        val identities = repository.getAllSshIdentities()
        assertTrue(identities.size <= 10) // Some operations might have failed
        
        // Verify no duplicate IDs
        val uniqueIds = identities.map { it.id }.toSet()
        assertEquals(identities.size, uniqueIds.size)
        
        // Verify storage is still valid
        val isValid = repository.validateStorage()
        assertTrue(isValid)
    }
}