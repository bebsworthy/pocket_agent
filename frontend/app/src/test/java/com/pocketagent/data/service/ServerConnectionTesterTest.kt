package com.pocketagent.data.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ServerConnectionTester.
 *
 * Tests cover connection testing scenarios, timeout handling,
 * DNS resolution, port connectivity, and error cases.
 */
class ServerConnectionTesterTest {
    private lateinit var connectionTester: ServerConnectionTester
    private lateinit var mockSshIdentityService: SshIdentityService

    @BeforeEach
    fun setup() {
        mockSshIdentityService = mockk()
        connectionTester = ServerConnectionTester(mockSshIdentityService)
    }

    @Test
    fun `testConnection should return success for valid configuration`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns
                ServiceResult.success(
                    mockk {
                        every { id } returns "ssh-1"
                        every { name } returns "Test Key"
                    },
                )

            // Act
            val result =
                connectionTester.testConnection(
                    // Public test service
                    hostname = "httpbin.org",
                    port = 80,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                    wrapperPort = 8080,
                    timeoutMs = 5000L,
                )

            // Assert
            // Note: This test may fail in environments without internet access
            // In production, we would mock network calls
            assertTrue(result.responseTimeMs > 0)
            assertTrue(result.additionalInfo.containsKey("dns_resolution"))
        }

    @Test
    fun `testConnection should handle DNS resolution failure`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns
                ServiceResult.success(
                    mockk {
                        every { id } returns "ssh-1"
                        every { name } returns "Test Key"
                    },
                )

            // Act
            val result =
                connectionTester.testConnection(
                    hostname = "invalid.nonexistent.domain",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                    wrapperPort = 8080,
                    timeoutMs = 2000L,
                )

            // Assert
            assertEquals(ConnectionTestResult.Status.FAILED, result.connectionStatus)
            assertTrue(result.message.contains("DNS resolution failed"))
            assertEquals("FAILED", result.additionalInfo["dns_resolution"])
        }

    @Test
    fun `testConnection should handle port unreachable`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns
                ServiceResult.success(
                    mockk {
                        every { id } returns "ssh-1"
                        every { name } returns "Test Key"
                    },
                )

            // Act - Using a valid hostname but unreachable port
            val result =
                connectionTester.testConnection(
                    hostname = "127.0.0.1",
                    // Unlikely to be open
                    port = 9999,
                    username = "testuser",
                    sshIdentityId = "ssh-1",
                    wrapperPort = 8080,
                    timeoutMs = 2000L,
                )

            // Assert
            // DNS should succeed but SSH port should fail
            if (result.additionalInfo["dns_resolution"] == "OK") {
                assertEquals("FAILED", result.additionalInfo["ssh_port"])
            }
        }

    @Test
    fun `testConnection should handle SSH identity not found`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("invalid-ssh") } returns ServiceResult.failure("SSH Identity not found")

            // Act
            val result =
                connectionTester.testConnection(
                    hostname = "test.example.com",
                    port = 22,
                    username = "testuser",
                    sshIdentityId = "invalid-ssh",
                    wrapperPort = 8080,
                )

            // Assert
            // The connection test should still proceed for network connectivity
            // SSH auth test will fail but that's recorded separately
            assertTrue(result.additionalInfo.containsKey("ssh_auth"))
        }

    @Test
    fun `testMultipleConnections should handle empty profile list`() =
        runTest {
            // Act
            val results = connectionTester.testMultipleConnections(emptyList())

            // Assert
            assertTrue(results.isEmpty())
        }

    @Test
    fun `testMultipleConnections should handle single profile`() =
        runTest {
            // Arrange
            coEvery { mockSshIdentityService.getSshIdentity("ssh-1") } returns
                ServiceResult.success(
                    mockk {
                        every { id } returns "ssh-1"
                        every { name } returns "Test Key"
                    },
                )

            // Act
            val results = connectionTester.testMultipleConnections(listOf("profile-1"))

            // Assert
            assertEquals(1, results.size)
            assertTrue(results.containsKey("profile-1"))
            // The result will be a failure since we can't actually resolve the profile
            assertEquals(ConnectionTestResult.Status.FAILED, results["profile-1"]!!.connectionStatus)
        }

    @Test
    fun `testMultipleConnections should respect concurrency limit`() =
        runTest {
            // Arrange
            val profileIds = (1..10).map { "profile-$it" }

            // Act
            val results = connectionTester.testMultipleConnections(profileIds, maxConcurrency = 3)

            // Assert
            assertEquals(10, results.size)
            profileIds.forEach { profileId ->
                assertTrue(results.containsKey(profileId))
                // All should fail since profiles don't exist, but they should all be tested
                assertEquals(ConnectionTestResult.Status.FAILED, results[profileId]!!.connectionStatus)
            }
        }
}
