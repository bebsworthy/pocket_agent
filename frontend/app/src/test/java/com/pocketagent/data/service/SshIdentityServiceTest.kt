package com.pocketagent.data.service

import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.SshIdentityValidator
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for SshIdentityService.
 * 
 * Tests all CRUD operations, validation, encryption, parsing,
 * search/filtering, and usage tracking functionality.
 */
@DisplayName("SSH Identity Service Tests")
class SshIdentityServiceTest {

    @MockK
    private lateinit var repository: SecureDataRepository
    
    @MockK
    private lateinit var validator: SshIdentityValidator
    
    @MockK
    private lateinit var sshKeyParser: SshKeyParser
    
    @MockK
    private lateinit var sshKeyEncryption: SshKeyEncryption

    private lateinit var service: SshIdentityService
    
    // Test data
    private val testKeyPair = generateTestKeyPair()
    private val testSshIdentity = SshIdentity(
        id = "test-id-123",
        name = "Test SSH Key",
        encryptedPrivateKey = "encrypted_test_key_data",
        publicKeyFingerprint = "SHA256:testfingerprint123",
        description = "Test SSH identity"
    )
    
    private val testServerProfile = ServerProfile(
        id = "server-1",
        name = "Test Server",
        hostname = "example.com",
        username = "testuser",
        sshIdentityId = testSshIdentity.id
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = SshIdentityService(repository, validator, sshKeyParser, sshKeyEncryption)
        
        // Default mock behaviors
        setupDefaultMocks()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // CRUD Operations Tests

    @Test
    @DisplayName("Create SSH Identity - Success")
    fun testCreateSshIdentity_Success() = runTest {
        // Arrange
        val name = "New SSH Key"
        val privateKeyData = "-----BEGIN RSA PRIVATE KEY-----\ntest_data\n-----END RSA PRIVATE KEY-----"
        val description = "Test description"
        
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyEncryption.encryptPrivateKey(any()) } returns "encrypted_key_data"
        coEvery { repository.getAllSshIdentities() } returns emptyList()
        every { validator.validateForCreation(any()) } returns ValidationResult.Success
        every { validator.validateNameUniqueness(any(), any()) } returns ValidationResult.Success
        coEvery { repository.addSshIdentity(any()) } just Runs

        // Act
        val result = service.createSshIdentity(
            name = name,
            privateKeyData = privateKeyData,
            description = description
        )

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        assertEquals(name, result.getOrNull()?.name)
        assertEquals(description, result.getOrNull()?.description)
        
        coVerify { repository.addSshIdentity(any()) }
        verify { sshKeyParser.parsePrivateKey(privateKeyData, SshKeyFormat.AUTO_DETECT, null) }
        verify { sshKeyEncryption.encryptPrivateKey(testKeyPair.private) }
    }

    @Test
    @DisplayName("Create SSH Identity - Invalid Key Data")
    fun testCreateSshIdentity_InvalidKeyData() = runTest {
        // Arrange
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns null

        // Act
        val result = service.createSshIdentity(
            name = "Test Key",
            privateKeyData = "invalid_key_data"
        )

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Failed to parse private key", result.getErrorOrNull())
    }

    @Test
    @DisplayName("Create SSH Identity - Duplicate Fingerprint")
    fun testCreateSshIdentity_DuplicateFingerprint() = runTest {
        // Arrange
        val existingIdentity = testSshIdentity.copy(
            publicKeyFingerprint = "SHA256:testfingerprint123"
        )
        
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        coEvery { repository.getAllSshIdentities() } returns listOf(existingIdentity)

        // Act
        val result = service.createSshIdentity(
            name = "Test Key",
            privateKeyData = "test_data"
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.getErrorOrNull()?.contains("fingerprint already exists") == true)
    }

    @Test
    @DisplayName("Create SSH Identity - Validation Failure")
    fun testCreateSshIdentity_ValidationFailure() = runTest {
        // Arrange
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyEncryption.encryptPrivateKey(any()) } returns "encrypted_key_data"
        coEvery { repository.getAllSshIdentities() } returns emptyList()
        every { validator.validateForCreation(any()) } returns ValidationResult.Failure(
            mockk { every { message } returns "Validation error" }
        )

        // Act
        val result = service.createSshIdentity(
            name = "Test Key",
            privateKeyData = "test_data"
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.getErrorOrNull()?.contains("Validation failed") == true)
    }

    @Test
    @DisplayName("Get SSH Identity - Success")
    fun testGetSshIdentity_Success() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity

        // Act
        val result = service.getSshIdentity("test-id")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(testSshIdentity, result.getOrNull())
    }

    @Test
    @DisplayName("Get SSH Identity - Not Found")
    fun testGetSshIdentity_NotFound() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("non-existent") } returns null

        // Act
        val result = service.getSshIdentity("non-existent")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("SSH identity not found", result.getErrorOrNull())
    }

    @Test
    @DisplayName("Update SSH Identity - Success")
    fun testUpdateSshIdentity_Success() = runTest {
        // Arrange
        val newName = "Updated SSH Key"
        val newDescription = "Updated description"
        
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity
        every { validator.validateForUpdate(any(), any()) } returns ValidationResult.Success
        coEvery { repository.getAllSshIdentities() } returns listOf(testSshIdentity)
        every { validator.validateNameUniqueness(any(), any(), any()) } returns ValidationResult.Success
        coEvery { repository.updateSshIdentity(any()) } just Runs

        // Act
        val result = service.updateSshIdentity(
            id = "test-id",
            name = newName,
            description = newDescription
        )

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(newName, result.getOrNull()?.name)
        assertEquals(newDescription, result.getOrNull()?.description)
        
        coVerify { repository.updateSshIdentity(any()) }
    }

    @Test
    @DisplayName("Update SSH Identity - Not Found")
    fun testUpdateSshIdentity_NotFound() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("non-existent") } returns null

        // Act
        val result = service.updateSshIdentity(
            id = "non-existent",
            name = "New Name"
        )

        // Assert
        assertTrue(result.isFailure)
        assertEquals("SSH identity not found", result.getErrorOrNull())
    }

    @Test
    @DisplayName("Delete SSH Identity - Success")
    fun testDeleteSshIdentity_Success() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity
        coEvery { repository.getServerProfilesForIdentity("test-id") } returns emptyList()
        coEvery { repository.deleteSshIdentity("test-id") } just Runs

        // Act
        val result = service.deleteSshIdentity("test-id")

        // Assert
        assertTrue(result.isSuccess)
        coVerify { repository.deleteSshIdentity("test-id") }
    }

    @Test
    @DisplayName("Delete SSH Identity - Has Dependencies")
    fun testDeleteSshIdentity_HasDependencies() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity
        coEvery { repository.getServerProfilesForIdentity("test-id") } returns listOf(testServerProfile)

        // Act
        val result = service.deleteSshIdentity("test-id")

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.getErrorOrNull()?.contains("used by") == true)
        
        coVerify(exactly = 0) { repository.deleteSshIdentity(any()) }
    }

    // Search and Filtering Tests

    @Test
    @DisplayName("List SSH Identities - Success")
    fun testListSshIdentities_Success() = runTest {
        // Arrange
        val identities = listOf(
            testSshIdentity,
            testSshIdentity.copy(id = "id-2", name = "SSH Key 2"),
            testSshIdentity.copy(id = "id-3", name = "SSH Key 3")
        )
        
        coEvery { repository.getAllSshIdentities() } returns identities
        coEvery { repository.getAllServerProfiles() } returns emptyList()

        // Act
        val result = service.listSshIdentities()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(identities.size, result.getOrNull()?.size)
    }

    @Test
    @DisplayName("Search SSH Identities - By Name")
    fun testSearchSshIdentities_ByName() = runTest {
        // Arrange
        val identities = listOf(
            testSshIdentity.copy(name = "Work SSH Key"),
            testSshIdentity.copy(id = "id-2", name = "Personal SSH Key"),
            testSshIdentity.copy(id = "id-3", name = "Test Key")
        )
        
        coEvery { repository.getAllSshIdentities() } returns identities

        // Act
        val result = service.searchSshIdentities("Work")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Work SSH Key", result.getOrNull()?.first()?.name)
    }

    @Test
    @DisplayName("Search SSH Identities - By Description")
    fun testSearchSshIdentities_ByDescription() = runTest {
        // Arrange
        val identities = listOf(
            testSshIdentity.copy(description = "Production server access"),
            testSshIdentity.copy(id = "id-2", description = "Development server access"),
            testSshIdentity.copy(id = "id-3", description = null)
        )
        
        coEvery { repository.getAllSshIdentities() } returns identities

        // Act
        val result = service.searchSshIdentities("Production")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Production server access", result.getOrNull()?.first()?.description)
    }

    @Test
    @DisplayName("Filter SSH Identities - Recently Used Only")
    fun testFilterSshIdentities_RecentlyUsedOnly() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val recentTime = now - (10 * 24 * 60 * 60 * 1000) // 10 days ago
        val oldTime = now - (40 * 24 * 60 * 60 * 1000) // 40 days ago
        
        val identities = listOf(
            testSshIdentity.copy(lastUsedAt = recentTime),
            testSshIdentity.copy(id = "id-2", lastUsedAt = oldTime),
            testSshIdentity.copy(id = "id-3", lastUsedAt = null)
        )
        
        coEvery { repository.getAllSshIdentities() } returns identities

        // Act
        val result = service.filterSshIdentities(
            SshIdentityFilterCriteria(recentlyUsedOnly = true)
        )

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals(recentTime, result.getOrNull()?.first()?.lastUsedAt)
    }

    // Key Management Tests

    @Test
    @DisplayName("Import SSH Key - Success")
    fun testImportSshKey_Success() = runTest {
        // Arrange
        val keyData = "-----BEGIN RSA PRIVATE KEY-----\ntest_data\n-----END RSA PRIVATE KEY-----"
        
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyEncryption.encryptPrivateKey(any()) } returns "encrypted_key_data"
        coEvery { repository.getAllSshIdentities() } returns emptyList()
        every { validator.validateForCreation(any()) } returns ValidationResult.Success
        every { validator.validateNameUniqueness(any(), any()) } returns ValidationResult.Success
        coEvery { repository.addSshIdentity(any()) } just Runs

        // Act
        val result = service.importSshKey(
            name = "Imported Key",
            keyData = keyData,
            format = SshKeyFormat.PEM
        )

        // Assert
        assertTrue(result.isSuccess)
        verify { sshKeyParser.parsePrivateKey(keyData, SshKeyFormat.PEM, null) }
    }

    @Test
    @DisplayName("Export SSH Key - Public Key Only")
    fun testExportSshKey_PublicOnly() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity
        every { sshKeyParser.extractPublicKey(any()) } returns testKeyPair.public
        every { sshKeyParser.formatPublicKey(any(), any()) } returns "ssh-rsa AAAAB3... test@example.com"

        // Act
        val result = service.exportSshKey(
            id = "test-id",
            format = SshKeyFormat.OPENSSH,
            includePrivateKey = false
        )

        // Assert
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.startsWith("ssh-rsa") == true)
        
        verify { sshKeyParser.formatPublicKey(testKeyPair.public, SshKeyFormat.OPENSSH) }
        verify(exactly = 0) { sshKeyEncryption.decryptPrivateKey(any()) }
    }

    @Test
    @DisplayName("Generate SSH Key - Success")
    fun testGenerateSshKey_Success() = runTest {
        // Arrange
        every { sshKeyParser.generateKeyPair(any(), any()) } returns testKeyPair
        every { sshKeyParser.formatPrivateKey(any(), any()) } returns "generated_private_key_data"
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyEncryption.encryptPrivateKey(any()) } returns "encrypted_generated_key"
        coEvery { repository.getAllSshIdentities() } returns emptyList()
        every { validator.validateForCreation(any()) } returns ValidationResult.Success
        every { validator.validateNameUniqueness(any(), any()) } returns ValidationResult.Success
        coEvery { repository.addSshIdentity(any()) } just Runs

        // Act
        val result = service.generateSshKey(
            name = "Generated Key",
            keyType = SshKeyType.RSA,
            keySize = 2048
        )

        // Assert
        assertTrue(result.isSuccess)
        verify { sshKeyParser.generateKeyPair(SshKeyType.RSA, 2048) }
    }

    @Test
    @DisplayName("Validate SSH Key - Success")
    fun testValidateSshKey_Success() = runTest {
        // Arrange
        val keyData = "test_key_data"
        
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyParser.isPrivateKeyEncrypted(any()) } returns false
        every { sshKeyParser.detectFormat(any()) } returns SshKeyFormat.PEM

        // Act
        val result = service.validateSshKey(keyData)

        // Assert
        assertTrue(result.isSuccess)
        val keyInfo = result.getOrNull()
        assertNotNull(keyInfo)
        assertEquals(SshKeyType.RSA, keyInfo.keyType)
        assertFalse(keyInfo.isEncrypted)
        assertEquals(SshKeyFormat.PEM, keyInfo.format)
    }

    // Usage Tracking Tests

    @Test
    @DisplayName("Mark SSH Identity As Used - Success")
    fun testMarkAsUsed_Success() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById("test-id") } returns testSshIdentity
        coEvery { repository.updateSshIdentity(any()) } just Runs

        // Act
        val result = service.markAsUsed("test-id")

        // Assert
        assertTrue(result.isSuccess)
        
        coVerify { 
            repository.updateSshIdentity(
                match { identity ->
                    identity.id == "test-id" && identity.lastUsedAt != null
                }
            )
        }
    }

    @Test
    @DisplayName("Get Usage Statistics - Success")
    fun testGetUsageStatistics_Success() = runTest {
        // Arrange
        val serverProfiles = listOf(
            testServerProfile,
            testServerProfile.copy(id = "server-2", sshIdentityId = testSshIdentity.id)
        )
        
        coEvery { repository.getAllServerProfiles() } returns serverProfiles
        coEvery { repository.getAllProjects() } returns emptyList()
        coEvery { repository.getAllSshIdentities() } returns listOf(testSshIdentity)

        // Act
        val result = service.getUsageStatistics()

        // Assert
        assertTrue(result.isNotEmpty())
        val stats = result[testSshIdentity.id]
        assertNotNull(stats)
        assertEquals(2, stats.serverProfileCount)
        assertEquals(0, stats.projectCount)
    }

    // Error Handling Tests

    @Test
    @DisplayName("Repository Exception Handling")
    fun testRepositoryExceptionHandling() = runTest {
        // Arrange
        coEvery { repository.getSshIdentityById(any()) } throws RuntimeException("Database error")

        // Act
        val result = service.getSshIdentity("test-id")

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.getErrorOrNull()?.contains("Failed to retrieve SSH identity") == true)
    }

    @Test
    @DisplayName("Encryption Exception Handling")
    fun testEncryptionExceptionHandling() = runTest {
        // Arrange
        every { sshKeyParser.parsePrivateKey(any(), any(), any()) } returns testKeyPair
        every { sshKeyEncryption.encryptPrivateKey(any()) } throws SecurityException("Encryption failed")

        // Act
        val result = service.createSshIdentity(
            name = "Test Key",
            privateKeyData = "test_data"
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.getErrorOrNull()?.contains("Failed to create SSH identity") == true)
    }

    // Observable Flow Tests

    @Test
    @DisplayName("Observe SSH Identities - Flow")
    fun testObserveSshIdentities() = runTest {
        // Arrange
        val identitiesFlow = mockk<kotlinx.coroutines.flow.Flow<List<SshIdentity>>>()
        every { repository.observeSshIdentities() } returns identitiesFlow

        // Act
        val result = service.observeSshIdentities()

        // Assert
        assertEquals(identitiesFlow, result)
    }

    // Helper Methods

    private fun setupDefaultMocks() {
        // Mock validation results as success by default
        every { validator.validateForCreation(any()) } returns ValidationResult.Success
        every { validator.validateForUpdate(any(), any()) } returns ValidationResult.Success
        every { validator.validateNameUniqueness(any(), any(), any()) } returns ValidationResult.Success
        
        // Mock fingerprint generation in service
        mockkStatic("java.security.MessageDigest")
        every { java.security.MessageDigest.getInstance("SHA-256") } returns mockk {
            every { digest(any()) } returns "test_fingerprint_hash".toByteArray()
        }
        
        mockkStatic("java.util.Base64")
        every { java.util.Base64.getEncoder() } returns mockk {
            every { encodeToString(any()) } returns "test_base64_encoded"
        }
    }

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }
}

/**
 * Test utilities for SSH Identity Service testing.
 */
object SshIdentityServiceTestUtils {
    
    /**
     * Creates a test SSH identity with optional parameters.
     */
    fun createTestSshIdentity(
        id: String = "test-id",
        name: String = "Test SSH Key",
        fingerprint: String = "SHA256:testfingerprint",
        description: String? = "Test description"
    ): SshIdentity {
        return SshIdentity(
            id = id,
            name = name,
            encryptedPrivateKey = "encrypted_test_key_data",
            publicKeyFingerprint = fingerprint,
            description = description
        )
    }
    
    /**
     * Creates a test server profile with optional parameters.
     */
    fun createTestServerProfile(
        id: String = "server-id",
        name: String = "Test Server",
        sshIdentityId: String = "test-ssh-id"
    ): ServerProfile {
        return ServerProfile(
            id = id,
            name = name,
            hostname = "example.com",
            username = "testuser",
            sshIdentityId = sshIdentityId
        )
    }
    
    /**
     * Creates test RSA key data.
     */
    fun createTestRsaKeyData(): String {
        return """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef...
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
    }
    
    /**
     * Creates test OpenSSH key data.
     */
    fun createTestOpenSshKeyData(): String {
        return """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAA...
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    }
}