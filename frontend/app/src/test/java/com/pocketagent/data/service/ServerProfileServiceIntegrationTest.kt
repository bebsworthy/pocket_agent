package com.pocketagent.data.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.validators.ServerProfileValidator
import com.pocketagent.data.validation.validators.SshIdentityValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for ServerProfileService with real dependencies.
 * 
 * These tests verify that the service works correctly with actual validator
 * and repository implementations, testing the full service integration.
 */
class ServerProfileServiceIntegrationTest {
    
    private lateinit var service: ServerProfileService
    private lateinit var repository: SecureDataRepository
    private lateinit var context: Context
    private lateinit var mockEncryptedStorage: EncryptedJsonStorage
    
    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEncryptedStorage = mockk {
            coEvery { getJsonData(any()) } returns null
            coEvery { storeJsonData(any(), any()) } just Runs
            every { getStorageStats() } returns mockk {
                every { isValid } returns true
                every { totalSize } returns 0L
                every { fileCount } returns 0
            }
            coEvery { validateStorage() } returns mockk {
                every { isValid } returns true
            }
        }
        
        // Create real validator instances
        val dataValidator = DataValidator()
        val serverProfileValidator = ServerProfileValidator()
        val sshIdentityValidator = SshIdentityValidator()
        
        // Create real repository
        repository = SecureDataRepository(
            context = context,
            encryptedStorage = mockEncryptedStorage,
            dataValidator = dataValidator
        )
        
        // Create mock services for dependencies
        val mockSshKeyParser = mockk<SshKeyParser>()
        val mockSshKeyEncryption = mockk<SshKeyEncryption>()
        val sshIdentityService = SshIdentityService(
            repository = repository,
            validator = sshIdentityValidator,
            sshKeyParser = mockSshKeyParser,
            sshKeyEncryption = mockSshKeyEncryption
        )
        
        val connectionTester = ServerConnectionTester(sshIdentityService)
        val networkValidator = NetworkConfigurationValidator()
        
        // Create the service under test
        service = ServerProfileService(
            repository = repository,
            validator = serverProfileValidator,
            sshIdentityService = sshIdentityService,
            connectionTester = connectionTester,
            networkValidator = networkValidator
        )
    }
    
    @Test
    fun `full CRUD workflow should work correctly`() = runTest {
        // Initialize repository
        repository.initialize()
        
        // Step 1: Create an SSH identity first (required for server profile)
        val sshIdentity = SshIdentity(
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted-key-data",
            publicKeyFingerprint = "SHA256:test-fingerprint"
        )
        repository.addSshIdentity(sshIdentity)
        
        // Step 2: Create a server profile
        val createResult = service.createServerProfile(
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = sshIdentity.id,
            wrapperPort = 8080,
            description = "Test server for integration testing"
        )
        
        assertTrue(createResult.isSuccess, "Server profile creation should succeed")
        val createdProfile = createResult.getOrNull()!!
        assertEquals("Test Server", createdProfile.name)
        assertEquals("test.example.com", createdProfile.hostname)
        assertEquals(ConnectionStatus.NEVER_CONNECTED, createdProfile.status)
        
        // Step 3: Read the server profile
        val readResult = service.getServerProfile(createdProfile.id)
        assertTrue(readResult.isSuccess, "Server profile read should succeed")
        assertEquals(createdProfile, readResult.getOrNull())
        
        // Step 4: Update the server profile
        val updateResult = service.updateServerProfile(
            id = createdProfile.id,
            name = "Updated Test Server",
            hostname = "updated.example.com"
        )
        assertTrue(updateResult.isSuccess, "Server profile update should succeed")
        val updatedProfile = updateResult.getOrNull()!!
        assertEquals("Updated Test Server", updatedProfile.name)
        assertEquals("updated.example.com", updatedProfile.hostname)
        assertEquals("testuser", updatedProfile.username) // Unchanged
        
        // Step 5: List server profiles
        val listResult = service.listServerProfiles()
        assertTrue(listResult.isSuccess, "Server profile list should succeed")
        val profiles = listResult.getOrNull()!!
        assertEquals(1, profiles.size)
        assertEquals(updatedProfile.id, profiles[0].id)
        
        // Step 6: Search server profiles
        val searchResult = service.searchServerProfiles("Updated")
        assertTrue(searchResult.isSuccess, "Server profile search should succeed")
        val searchProfiles = searchResult.getOrNull()!!
        assertEquals(1, searchProfiles.size)
        assertEquals(updatedProfile.id, searchProfiles[0].id)
        
        // Step 7: Get usage statistics
        val usageStats = service.getUsageStatistics(listOf(updatedProfile.id))
        assertTrue(usageStats.containsKey(updatedProfile.id))
        val stats = usageStats[updatedProfile.id]!!
        assertEquals(0, stats.projectCount) // No projects created yet
        
        // Step 8: Delete the server profile
        val deleteResult = service.deleteServerProfile(updatedProfile.id)
        assertTrue(deleteResult.isSuccess, "Server profile deletion should succeed")
        
        // Step 9: Verify deletion
        val deletedReadResult = service.getServerProfile(updatedProfile.id)
        assertTrue(deletedReadResult.isFailure, "Reading deleted profile should fail")
        
        // Step 10: Verify list is empty
        val emptyListResult = service.listServerProfiles()
        assertTrue(emptyListResult.isSuccess, "Empty list should succeed")
        assertTrue(emptyListResult.getOrNull()!!.isEmpty(), "List should be empty after deletion")
    }
    
    @Test
    fun `validation workflow should work correctly`() = runTest {
        // Initialize repository
        repository.initialize()
        
        // Create SSH identity
        val sshIdentity = SshIdentity(
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted-key-data",
            publicKeyFingerprint = "SHA256:test-fingerprint"
        )
        repository.addSshIdentity(sshIdentity)
        
        // Test 1: Invalid name should fail
        val invalidNameResult = service.createServerProfile(
            name = "", // Invalid empty name
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = sshIdentity.id
        )
        assertTrue(invalidNameResult.isFailure, "Empty name should fail validation")
        
        // Test 2: Invalid hostname should fail
        val invalidHostnameResult = service.createServerProfile(
            name = "Test Server",
            hostname = "", // Invalid empty hostname
            port = 22,
            username = "testuser",
            sshIdentityId = sshIdentity.id
        )
        assertTrue(invalidHostnameResult.isFailure, "Empty hostname should fail validation")
        
        // Test 3: Invalid port should fail
        val invalidPortResult = service.createServerProfile(
            name = "Test Server",
            hostname = "test.example.com",
            port = 0, // Invalid port
            username = "testuser",
            sshIdentityId = sshIdentity.id
        )
        assertTrue(invalidPortResult.isFailure, "Invalid port should fail validation")
        
        // Test 4: Same SSH and wrapper port should fail
        val samePortResult = service.createServerProfile(
            name = "Test Server",
            hostname = "test.example.com",
            port = 8080,
            username = "testuser",
            sshIdentityId = sshIdentity.id,
            wrapperPort = 8080 // Same as SSH port
        )
        assertTrue(samePortResult.isFailure, "Same SSH and wrapper port should fail validation")
        
        // Test 5: Non-existent SSH identity should fail
        val invalidSshResult = service.createServerProfile(
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = "non-existent-ssh-id"
        )
        assertTrue(invalidSshResult.isFailure, "Non-existent SSH identity should fail validation")
    }
    
    @Test
    fun `network validation should work correctly`() = runTest {
        // Test valid configuration
        val validResult = service.validateNetworkConfiguration("example.com", 22, 8080)
        assertTrue(validResult.isSuccess, "Valid configuration should pass")
        val validReport = validResult.getOrNull()!!
        assertTrue(validReport.isValid)
        
        // Test invalid hostname
        val invalidHostnameResult = service.validateNetworkConfiguration("", 22, 8080)
        assertTrue(invalidHostnameResult.isSuccess, "Validation should succeed even with invalid hostname")
        val invalidHostnameReport = invalidHostnameResult.getOrNull()!!
        assertTrue(!invalidHostnameReport.isValid || invalidHostnameReport.recommendations.isNotEmpty())
        
        // Test localhost configuration
        val localhostResult = service.validateNetworkConfiguration("localhost", 22, 8080)
        assertTrue(localhostResult.isSuccess, "Localhost validation should succeed")
        val localhostReport = localhostResult.getOrNull()!!
        assertTrue(localhostReport.recommendations.isNotEmpty()) // Should have recommendations
    }
    
    @Test
    fun `filtering and sorting should work correctly`() = runTest {
        // Initialize repository and create test data
        repository.initialize()
        
        val sshIdentity = SshIdentity(
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted-key-data",
            publicKeyFingerprint = "SHA256:test-fingerprint"
        )
        repository.addSshIdentity(sshIdentity)
        
        // Create multiple server profiles
        val profiles = listOf(
            "Alpha Server" to "alpha.example.com",
            "Beta Server" to "beta.example.com",
            "Charlie Server" to "charlie.example.com"
        )
        
        val createdProfiles = mutableListOf<ServerProfile>()
        for ((name, hostname) in profiles) {
            val result = service.createServerProfile(
                name = name,
                hostname = hostname,
                port = 22,
                username = "testuser",
                sshIdentityId = sshIdentity.id
            )
            assertTrue(result.isSuccess)
            createdProfiles.add(result.getOrNull()!!)
        }
        
        // Test sorting by name
        val sortedResult = service.listServerProfiles(sortBy = ServerProfileSortBy.NAME)
        assertTrue(sortedResult.isSuccess)
        val sortedProfiles = sortedResult.getOrNull()!!
        assertEquals("Alpha Server", sortedProfiles[0].name)
        assertEquals("Beta Server", sortedProfiles[1].name)
        assertEquals("Charlie Server", sortedProfiles[2].name)
        
        // Test filtering by SSH identity
        val filteredResult = service.listServerProfiles(sshIdentityId = sshIdentity.id)
        assertTrue(filteredResult.isSuccess)
        val filteredProfiles = filteredResult.getOrNull()!!
        assertEquals(3, filteredProfiles.size)
        assertTrue(filteredProfiles.all { it.sshIdentityId == sshIdentity.id })
        
        // Test search
        val searchResult = service.searchServerProfiles("Alpha")
        assertTrue(searchResult.isSuccess)
        val searchProfiles = searchResult.getOrNull()!!
        assertEquals(1, searchProfiles.size)
        assertEquals("Alpha Server", searchProfiles[0].name)
    }
}