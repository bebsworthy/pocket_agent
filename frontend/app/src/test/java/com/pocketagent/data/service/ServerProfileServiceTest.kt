package com.pocketagent.data.service

import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.ValidationError
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ServerProfileValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for ServerProfileService.
 *
 * Tests cover all CRUD operations, validation, connection testing,
 * search/filtering, and error handling scenarios.
 */
class ServerProfileServiceTest {
    private lateinit var service: ServerProfileService
    private lateinit var mockRepository: SecureDataRepository
    private lateinit var mockValidator: ServerProfileValidator
    private lateinit var mockSshIdentityService: SshIdentityService
    private lateinit var mockConnectionTester: ServerConnectionTester
    private lateinit var mockNetworkValidator: NetworkConfigurationValidator

    private val testSshIdentity =
        SshIdentity(
            id = "ssh-1",
            name = "Test Key",
            encryptedPrivateKey = "encrypted-key-data",
            publicKeyFingerprint = "SHA256:test-fingerprint",
        )

    private val testServerProfile =
        ServerProfile(
            id = "server-1",
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = "ssh-1",
            wrapperPort = 8080,
        )

    @BeforeEach
    fun setup() {
        mockRepository = mockk()
        mockValidator = mockk()
        mockSshIdentityService = mockk()
        mockConnectionTester = mockk()
        mockNetworkValidator = mockk()

        service =
            ServerProfileService(
                repository = mockRepository,
                validator = mockValidator,
                sshIdentityService = mockSshIdentityService,
                connectionTester = mockConnectionTester,
                networkValidator = mockNetworkValidator,
            )
    }

    // CRUD Operations Tests

    @Test
    fun `createServerProfile should create profile successfully`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns ServiceResult.success(testSshIdentity)
            every { mockValidator.validateForCreation(any()) } returns ValidationResult.Success
            every { mockValidator.validateNameUniqueness(any(), any()) } returns ValidationResult.Success
            every { mockValidator.validateHostnamePortUniqueness(any(), any(), any()) } returns ValidationResult.Success
            every { mockNetworkValidator.validateConfiguration(any(), any(), any()) } returns ValidationResult.Success
            coEvery { mockRepository.getAllServerProfiles() } returns emptyList()
            coEvery { mockRepository.addServerProfile(any()) } just Runs
            coEvery { mockSshIdentityService.markAsUsed("ssh-1") } returns ServiceResult.success(Unit)

            // Act
            val result =
                service.createServerProfile(
                    name = "Test Server",
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                    wrapperPort = 8080,
                )

            // Assert
            assertTrue(result.isSuccess)
            val profile = result.getOrNull()!!
            assertEquals("Test Server", profile.name)
            assertEquals("test.example.com", profile.hostname)
            assertEquals(22, profile.port)
            assertEquals("testuser", profile.username)
            assertEquals("ssh-1", profile.sshIdentityId)
            assertEquals(8080, profile.wrapperPort)

            coVerify { mockRepository.addServerProfile(any()) }
            coVerify { mockSshIdentityService.markAsUsed("ssh-1") }
        }

    @Test
    fun `createServerProfile should fail when SSH identity not found`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("invalid-ssh") } returns ServiceResult.failure("SSH Identity not found")

            // Act
            val result =
                service.createServerProfile(
                    name = "Test Server",
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "invalid-ssh",
                )

            // Assert
            assertTrue(result.isFailure)
            assertEquals("SSH Identity not found: SSH Identity not found", result.getErrorOrNull())
        }

    @Test
    fun `createServerProfile should fail when validation fails`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns ServiceResult.success(testSshIdentity)
            every { mockValidator.validateForCreation(any()) } returns
                ValidationResult.Failure(
                    ValidationError.fieldError("Invalid name", "name"),
                )

            // Act
            val result =
                service.createServerProfile(
                    name = "",
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                )

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.getErrorOrNull()!!.contains("Validation failed"))
        }

    @Test
    fun `createServerProfile should fail when name already exists`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns ServiceResult.success(testSshIdentity)
            every { mockValidator.validateForCreation(any()) } returns ValidationResult.Success
            every { mockValidator.validateNameUniqueness(any(), any()) } returns
                ValidationResult.Failure(
                    ValidationError.businessRuleError("Name already exists", "name", "DUPLICATE_NAME"),
                )
            coEvery { mockRepository.getAllServerProfiles() } returns listOf(testServerProfile)

            // Act
            val result =
                service.createServerProfile(
                    name = "Test Server",
                    hostname = "different.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                )

            // Assert
            assertTrue(result.isFailure)
            assertEquals("Server profile name already exists", result.getErrorOrNull())
        }

    @Test
    fun `createServerProfile with connection test should update status on success`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns ServiceResult.success(testSshIdentity)
            every { mockValidator.validateForCreation(any()) } returns ValidationResult.Success
            every { mockValidator.validateNameUniqueness(any(), any()) } returns ValidationResult.Success
            every { mockValidator.validateHostnamePortUniqueness(any(), any(), any()) } returns ValidationResult.Success
            every { mockNetworkValidator.validateConfiguration(any(), any(), any()) } returns ValidationResult.Success
            coEvery { mockRepository.getAllServerProfiles() } returns emptyList()
            coEvery { mockRepository.addServerProfile(any()) } just Runs
            coEvery { mockSshIdentityService.markAsUsed("ssh-1") } returns ServiceResult.success(Unit)
            coEvery { mockConnectionTester.testConnection(any(), any(), any(), any(), any(), any()) } returns
                ConnectionTestResult(
                    connectionStatus = ConnectionTestResult.Status.SUCCESS,
                    message = "Connection successful",
                    responseTimeMs = 500,
                )

            // Act
            val result =
                service.createServerProfile(
                    name = "Test Server",
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                    testConnection = true,
                )

            // Assert
            assertTrue(result.isSuccess)
            val profile = result.getOrNull()!!
            assertEquals(ConnectionStatus.CONNECTED, profile.status)
            assertTrue(profile.lastConnectedAt!! > 0)
        }

    @Test
    fun `getServerProfile should return profile when found`() =
        runTest {
            // Arrange
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile

            // Act
            val result = service.getServerProfile("server-1")

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(testServerProfile, result.getOrNull())
        }

    @Test
    fun `getServerProfile should return failure when not found`() =
        runTest {
            // Arrange
            coEvery { mockRepository.getServerProfileById("invalid-id") } returns null

            // Act
            val result = service.getServerProfile("invalid-id")

            // Assert
            assertTrue(result.isFailure)
            assertEquals("Server profile not found", result.getErrorOrNull())
        }

    @Test
    fun `updateServerProfile should update profile successfully`() =
        runTest {
            // Arrange
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile
            every { mockValidator.validateForUpdate(any(), any()) } returns ValidationResult.Success
            coEvery { mockRepository.updateServerProfile(any()) } just Runs

            // Act
            val result = service.updateServerProfile("server-1", name = "Updated Server")

            // Assert
            assertTrue(result.isSuccess)
            val updated = result.getOrNull()!!
            assertEquals("Updated Server", updated.name)
            assertEquals(testServerProfile.hostname, updated.hostname) // Other fields unchanged

            coVerify { mockRepository.updateServerProfile(any()) }
        }

    @Test
    fun `updateServerProfile should validate SSH identity when changed`() =
        runTest {
            // Arrange
            val newSshIdentity = testSshIdentity.copy(id = "ssh-2")
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile
            coEvery { mockSshIdentityService.getSshIdentity("ssh-2") } returns ServiceResult.success(newSshIdentity)
            every { mockValidator.validateForUpdate(any(), any()) } returns ValidationResult.Success
            coEvery { mockRepository.updateServerProfile(any()) } just Runs
            coEvery { mockSshIdentityService.markAsUsed("ssh-2") } returns ServiceResult.success(Unit)

            // Act
            val result = service.updateServerProfile("server-1", sshIdentityId = "ssh-2")

            // Assert
            assertTrue(result.isSuccess)
            coVerify { mockSshIdentityService.getSshIdentity("ssh-2") }
            coVerify { mockSshIdentityService.markAsUsed("ssh-2") }
        }

    @Test
    fun `deleteServerProfile should delete successfully when no dependencies`() =
        runTest {
            // Arrange
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile
            coEvery { mockRepository.getProjectsForServer("server-1") } returns emptyList()
            coEvery { mockRepository.deleteServerProfile("server-1") } just Runs

            // Act
            val result = service.deleteServerProfile("server-1")

            // Assert
            assertTrue(result.isSuccess)
            coVerify { mockRepository.deleteServerProfile("server-1") }
        }

    @Test
    fun `deleteServerProfile should fail when profile has dependencies`() =
        runTest {
            // Arrange
            val mockProject =
                mockk<com.pocketagent.data.models.Project> {
                    every { name } returns "Test Project"
                }
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile
            coEvery { mockRepository.getProjectsForServer("server-1") } returns listOf(mockProject)

            // Act
            val result = service.deleteServerProfile("server-1")

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.getErrorOrNull()!!.contains("Cannot delete server profile"))
            assertTrue(result.getErrorOrNull()!!.contains("Test Project"))
        }

    // List and Search Tests

    @Test
    fun `listServerProfiles should return sorted profiles`() =
        runTest {
            // Arrange
            val profile1 = testServerProfile.copy(id = "1", name = "Alpha Server")
            val profile2 = testServerProfile.copy(id = "2", name = "Beta Server")
            val profile3 = testServerProfile.copy(id = "3", name = "Charlie Server")

            coEvery { mockRepository.getAllServerProfiles() } returns listOf(profile3, profile1, profile2)

            // Act
            val result = service.listServerProfiles(sortBy = ServerProfileSortBy.NAME)

            // Assert
            assertTrue(result.isSuccess)
            val profiles = result.getOrNull()!!
            assertEquals(3, profiles.size)
            assertEquals("Alpha Server", profiles[0].name)
            assertEquals("Beta Server", profiles[1].name)
            assertEquals("Charlie Server", profiles[2].name)
        }

    @Test
    fun `listServerProfiles should filter by SSH identity`() =
        runTest {
            // Arrange
            val profile1 = testServerProfile.copy(id = "1", sshIdentityId = "ssh-1")
            val profile2 = testServerProfile.copy(id = "2", sshIdentityId = "ssh-2")
            val profile3 = testServerProfile.copy(id = "3", sshIdentityId = "ssh-1")

            coEvery { mockRepository.getAllServerProfiles() } returns listOf(profile1, profile2, profile3)

            // Act
            val result = service.listServerProfiles(sshIdentityId = "ssh-1")

            // Assert
            assertTrue(result.isSuccess)
            val profiles = result.getOrNull()!!
            assertEquals(2, profiles.size)
            assertTrue(profiles.all { it.sshIdentityId == "ssh-1" })
        }

    @Test
    fun `searchServerProfiles should find matching profiles`() =
        runTest {
            // Arrange
            val profile1 = testServerProfile.copy(name = "Production Server", hostname = "prod.example.com")
            val profile2 = testServerProfile.copy(name = "Test Server", hostname = "test.example.com")
            val profile3 = testServerProfile.copy(name = "Development Server", hostname = "dev.example.com")

            coEvery { mockRepository.getAllServerProfiles() } returns listOf(profile1, profile2, profile3)

            // Act - search by name
            val nameResult = service.searchServerProfiles("Test")

            // Assert
            assertTrue(nameResult.isSuccess)
            val nameProfiles = nameResult.getOrNull()!!
            assertEquals(1, nameProfiles.size)
            assertEquals("Test Server", nameProfiles[0].name)

            // Act - search by hostname
            val hostnameResult = service.searchServerProfiles("prod")

            // Assert
            assertTrue(hostnameResult.isSuccess)
            val hostnameProfiles = hostnameResult.getOrNull()!!
            assertEquals(1, hostnameProfiles.size)
            assertEquals("prod.example.com", hostnameProfiles[0].hostname)
        }

    @Test
    fun `searchServerProfiles should return empty for blank query`() =
        runTest {
            // Act
            val result = service.searchServerProfiles("")

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `filterServerProfiles should apply all criteria`() =
        runTest {
            // Arrange
            val now = System.currentTimeMillis()
            val profile1 =
                testServerProfile.copy(
                    id = "1",
                    sshIdentityId = "ssh-1",
                    status = ConnectionStatus.CONNECTED,
                    createdAt = now - 1000,
                )
            val profile2 =
                testServerProfile.copy(
                    id = "2",
                    sshIdentityId = "ssh-2",
                    status = ConnectionStatus.DISCONNECTED,
                    createdAt = now - 2000,
                )
            val profile3 =
                testServerProfile.copy(
                    id = "3",
                    sshIdentityId = "ssh-1",
                    status = ConnectionStatus.CONNECTED,
                    createdAt = now - 3000,
                )

            coEvery { mockRepository.getAllServerProfiles() } returns listOf(profile1, profile2, profile3)

            // Act
            val criteria =
                ServerProfileFilterCriteria(
                    sshIdentityId = "ssh-1",
                    connectionStatus = ConnectionStatus.CONNECTED,
                    createdAfter = now - 1500,
                )
            val result = service.filterServerProfiles(criteria)

            // Assert
            assertTrue(result.isSuccess)
            val profiles = result.getOrNull()!!
            assertEquals(1, profiles.size)
            assertEquals("1", profiles[0].id)
        }

    // Connection Testing Tests

    @Test
    fun `testConnection should return success result`() =
        runTest {
            // Arrange
            val expectedResult =
                ConnectionTestResult(
                    connectionStatus = ConnectionTestResult.Status.SUCCESS,
                    message = "Connection successful",
                    responseTimeMs = 500,
                )
            coEvery {
                mockConnectionTester.testConnection(
                    "test.example.com", 22, "testuser", "ssh-1", 8080, any(),
                )
            } returns expectedResult

            // Act
            val result = service.testConnection(testServerProfile)

            // Assert
            assertEquals(expectedResult, result)
        }

    @Test
    fun `testMultipleConnections should test all profiles`() =
        runTest {
            // Arrange
            val expectedResults =
                mapOf(
                    "server-1" to
                        ConnectionTestResult(
                            connectionStatus = ConnectionTestResult.Status.SUCCESS,
                            message = "Connection successful",
                            responseTimeMs = 500,
                        ),
                    "server-2" to
                        ConnectionTestResult(
                            connectionStatus = ConnectionTestResult.Status.FAILED,
                            message = "Connection failed",
                            responseTimeMs = 0,
                        ),
                )
            coEvery { mockConnectionTester.testMultipleConnections(listOf("server-1", "server-2"), 5) } returns expectedResults

            // Act
            val results = service.testMultipleConnections(listOf("server-1", "server-2"))

            // Assert
            assertEquals(expectedResults, results)
        }

    @Test
    fun `updateConnectionStatus should update profile status`() =
        runTest {
            // Arrange
            coEvery { mockRepository.getServerProfileById("server-1") } returns testServerProfile
            coEvery { mockRepository.updateServerProfile(any()) } just Runs

            // Act
            val result = service.updateConnectionStatus("server-1", ConnectionStatus.CONNECTED)

            // Assert
            assertTrue(result.isSuccess)
            val updated = result.getOrNull()!!
            assertEquals(ConnectionStatus.CONNECTED, updated.status)
            assertTrue(updated.lastConnectedAt!! > 0)
        }

    // Usage Statistics Tests

    @Test
    fun `getUsageStatistics should return correct stats`() =
        runTest {
            // Arrange
            val mockProject1 =
                mockk<com.pocketagent.data.models.Project> {
                    every { serverProfileId } returns "server-1"
                    every { status } returns
                        mockk {
                            every { name } returns "ACTIVE"
                        }
                    every { lastActiveAt } returns System.currentTimeMillis()
                }
            val mockProject2 =
                mockk<com.pocketagent.data.models.Project> {
                    every { serverProfileId } returns "server-1"
                    every { status } returns
                        mockk {
                            every { name } returns "INACTIVE"
                        }
                    every { lastActiveAt } returns System.currentTimeMillis() - 1000
                }

            coEvery { mockRepository.getAllProjects() } returns listOf(mockProject1, mockProject2)

            // Act
            val stats = service.getUsageStatistics(listOf("server-1"))

            // Assert
            assertTrue(stats.containsKey("server-1"))
            val serverStats = stats["server-1"]!!
            assertEquals(2, serverStats.projectCount)
            assertEquals(1, serverStats.activeProjectCount)
            assertTrue(serverStats.lastProjectActivity!! > 0)
        }

    // Network Validation Tests

    @Test
    fun `validateNetworkConfiguration should return validation report`() =
        runTest {
            // Arrange
            every { mockNetworkValidator.validateConfiguration("test.example.com", 22, 8080) } returns ValidationResult.Success
            coEvery { mockNetworkValidator.canResolveHostname("test.example.com") } returns true
            coEvery { mockNetworkValidator.isPortReachable("test.example.com", 22) } returns true
            coEvery { mockNetworkValidator.isPortReachable("test.example.com", 8080) } returns false
            every { mockNetworkValidator.getRecommendations("test.example.com", 22, 8080) } returns
                listOf("Consider using firewall-friendly ports")

            // Act
            val result = service.validateNetworkConfiguration("test.example.com", 22, 8080)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertTrue(report.isValid)
            assertTrue(report.hostnameResolvable)
            assertTrue(report.sshPortReachable)
            assertFalse(report.wrapperPortReachable)
            assertEquals(1, report.recommendations.size)
        }

    // Error Handling Tests

    @Test
    fun `service should handle repository exceptions gracefully`() =
        runTest {
            // Arrange
            coEvery {
                mockRepository.getServerProfileById("server-1")
            } throws DataException.CorruptedDataException("Data corrupted", RuntimeException())

            // Act
            val result = service.getServerProfile("server-1")

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.getErrorOrNull()!!.contains("Failed to retrieve server profile"))
        }

    @Test
    fun `service should handle validation exceptions gracefully`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns ServiceResult.success(testSshIdentity)
            every { mockValidator.validateForCreation(any()) } throws RuntimeException("Validation error")

            // Act
            val result =
                service.createServerProfile(
                    name = "Test Server",
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                )

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.getErrorOrNull()!!.contains("Failed to create server profile"))
        }
}
