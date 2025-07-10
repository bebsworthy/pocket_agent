package com.pocketagent.data.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for testing server connections and validating network configurations.
 * 
 * This class provides comprehensive connection testing capabilities including:
 * - SSH port connectivity testing
 * - Wrapper service reachability
 * - Network latency measurement
 * - Concurrent connection testing
 * - Connection health monitoring
 * - DNS resolution validation
 * 
 * The service uses efficient networking techniques and proper timeout handling
 * to provide reliable connection diagnostics for server profiles.
 */
@Singleton
class ServerConnectionTester @Inject constructor(
    private val sshIdentityService: SshIdentityService,
    private val serverProfileService: ServerProfileService
) {
    
    companion object {
        private const val TAG = "ServerConnectionTester"
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val DNS_TIMEOUT_MS = 5000L
        private const val CONNECTION_RETRY_COUNT = 2
        private const val WRAPPER_SERVICE_PATH = "/health"
    }
    
    /**
     * Tests connection to a server profile with comprehensive diagnostics.
     */
    suspend fun testConnection(
        hostname: String,
        port: Int,
        username: String,
        sshIdentityId: String,
        wrapperPort: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ConnectionTestResult {
        Log.d(TAG, "Testing connection to $hostname:$port")
        
        return try {
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val results = mutableMapOf<String, String>()
                var overallStatus = ConnectionTestResult.Status.SUCCESS
                var errorMessage = ""
                
                // Test DNS resolution
                val dnsResult = testDnsResolution(hostname)
                results["dns_resolution"] = if (dnsResult.success) "OK" else "FAILED"
                if (!dnsResult.success) {
                    overallStatus = ConnectionTestResult.Status.FAILED
                    errorMessage = "DNS resolution failed: ${dnsResult.error}"
                }
                
                // Test SSH port connectivity
                if (overallStatus == ConnectionTestResult.Status.SUCCESS) {
                    val sshResult = testPortConnectivity(hostname, port, timeoutMs)
                    results["ssh_port"] = if (sshResult.success) "OK" else "FAILED"
                    results["ssh_response_time"] = "${sshResult.responseTimeMs}ms"
                    
                    if (!sshResult.success) {
                        overallStatus = ConnectionTestResult.Status.FAILED
                        errorMessage = "SSH port unreachable: ${sshResult.error}"
                    }
                }
                
                // Test wrapper port connectivity
                if (overallStatus == ConnectionTestResult.Status.SUCCESS) {
                    val wrapperResult = testWrapperService(hostname, wrapperPort, timeoutMs)
                    results["wrapper_service"] = if (wrapperResult.success) "OK" else "FAILED"
                    results["wrapper_response_time"] = "${wrapperResult.responseTimeMs}ms"
                    
                    if (!wrapperResult.success) {
                        overallStatus = ConnectionTestResult.Status.FAILED
                        errorMessage = "Wrapper service unreachable: ${wrapperResult.error}"
                    }
                }
                
                // Test SSH authentication if SSH port is reachable
                if (overallStatus == ConnectionTestResult.Status.SUCCESS) {
                    val authResult = testSshAuthentication(hostname, port, username, sshIdentityId, timeoutMs)
                    results["ssh_auth"] = if (authResult.success) "OK" else "FAILED"
                    
                    if (!authResult.success) {
                        // SSH auth failure doesn't necessarily mean connection failure
                        // The server is reachable, just auth might be misconfigured
                        results["ssh_auth_note"] = "Authentication failed but server is reachable"
                    }
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                val successMessage = "Connection test completed successfully"
                
                ConnectionTestResult(
                    connectionStatus = overallStatus,
                    message = if (overallStatus == ConnectionTestResult.Status.SUCCESS) successMessage else errorMessage,
                    responseTimeMs = totalTime,
                    errorDetails = if (overallStatus != ConnectionTestResult.Status.SUCCESS) errorMessage else null,
                    additionalInfo = results
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Connection test timed out for $hostname:$port")
            ConnectionTestResult(
                connectionStatus = ConnectionTestResult.Status.TIMEOUT,
                message = "Connection test timed out",
                responseTimeMs = timeoutMs,
                errorDetails = "Test exceeded ${timeoutMs}ms timeout"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed for $hostname:$port", e)
            ConnectionTestResult(
                connectionStatus = ConnectionTestResult.Status.FAILED,
                message = "Connection test failed: ${e.message}",
                responseTimeMs = 0,
                errorDetails = e.message
            )
        }
    }
    
    /**
     * Tests connections to multiple server profiles concurrently.
     */
    suspend fun testMultipleConnections(
        profileIds: List<String>,
        maxConcurrency: Int = 5
    ): Map<String, ConnectionTestResult> {
        Log.d(TAG, "Testing connections to ${profileIds.size} profiles")
        
        return try {
            withContext(Dispatchers.IO) {
                val results = ConcurrentHashMap<String, ConnectionTestResult>()
                val semaphore = Semaphore(maxConcurrency)
                
                profileIds.map { profileId ->
                    async {
                        semaphore.withPermit {
                            try {
                                // Get profile from repository
                                val profileResult = serverProfileService.getServerProfile(profileId)
                                val profile = profileResult.getOrNull()
                                if (profile != null) {
                                    val result = testConnection(
                                        hostname = profile.hostname,
                                        port = profile.port,
                                        username = profile.username,
                                        sshIdentityId = profile.sshIdentityId,
                                        wrapperPort = profile.wrapperPort
                                    )
                                    results[profileId] = result
                                } else {
                                    results[profileId] = ConnectionTestResult(
                                        connectionStatus = ConnectionTestResult.Status.FAILED,
                                        message = "Profile not found",
                                        responseTimeMs = 0,
                                        errorDetails = "Server profile $profileId not found"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to test profile $profileId", e)
                                results[profileId] = ConnectionTestResult(
                                    connectionStatus = ConnectionTestResult.Status.FAILED,
                                    message = "Test failed: ${e.message}",
                                    responseTimeMs = 0,
                                    errorDetails = e.message
                                )
                            }
                        }
                    }
                }.awaitAll()
                
                results.toMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multiple connection test failed", e)
            profileIds.associateWith { 
                ConnectionTestResult(
                    connectionStatus = ConnectionTestResult.Status.FAILED,
                    message = "Batch test failed: ${e.message}",
                    responseTimeMs = 0,
                    errorDetails = e.message
                )
            }
        }
    }
    
    /**
     * Tests DNS resolution for a hostname.
     */
    private suspend fun testDnsResolution(hostname: String): TestResult {
        return try {
            withTimeout(DNS_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    val address = InetAddress.getByName(hostname)
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "DNS resolution for $hostname: ${address.hostAddress} (${responseTime}ms)")
                    TestResult(true, responseTime, "Resolved to ${address.hostAddress}")
                }
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "DNS resolution failed for $hostname", e)
            TestResult(false, 0, "Unknown host: $hostname")
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "DNS resolution timed out for $hostname")
            TestResult(false, DNS_TIMEOUT_MS, "DNS resolution timed out")
        } catch (e: Exception) {
            Log.e(TAG, "DNS resolution error for $hostname", e)
            TestResult(false, 0, "DNS error: ${e.message}")
        }
    }
    
    /**
     * Tests port connectivity using socket connection.
     */
    private suspend fun testPortConnectivity(
        hostname: String, 
        port: Int, 
        timeoutMs: Long
    ): TestResult {
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    var lastError: Exception? = null
                    
                    // Retry connection attempts
                    repeat(CONNECTION_RETRY_COUNT) { attempt ->
                        try {
                            Socket().use { socket ->
                                socket.connect(
                                    InetSocketAddress(hostname, port),
                                    (timeoutMs / CONNECTION_RETRY_COUNT).toInt()
                                )
                                val responseTime = System.currentTimeMillis() - startTime
                                Log.d(TAG, "Port $port on $hostname is reachable (${responseTime}ms)")
                                return@withContext TestResult(true, responseTime, "Port is reachable")
                            }
                        } catch (e: Exception) {
                            lastError = e
                            if (attempt < CONNECTION_RETRY_COUNT - 1) {
                                delay(500) // Brief delay between retries
                            }
                        }
                    }
                    
                    // All attempts failed
                    val responseTime = System.currentTimeMillis() - startTime
                    Log.w(TAG, "Port $port on $hostname is not reachable after $CONNECTION_RETRY_COUNT attempts")
                    TestResult(false, responseTime, "Port unreachable: ${lastError?.message}")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Port connectivity test timed out for $hostname:$port")
            TestResult(false, timeoutMs, "Connection timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Port connectivity test failed for $hostname:$port", e)
            TestResult(false, 0, "Connection error: ${e.message}")
        }
    }
    
    /**
     * Tests wrapper service availability using HTTP request.
     */
    private suspend fun testWrapperService(
        hostname: String, 
        port: Int, 
        timeoutMs: Long
    ): TestResult {
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    
                    try {
                        val url = URL("http://$hostname:$port$WRAPPER_SERVICE_PATH")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = (timeoutMs / 2).toInt()
                        connection.readTimeout = (timeoutMs / 2).toInt()
                        
                        val responseCode = connection.responseCode
                        val responseTime = System.currentTimeMillis() - startTime
                        
                        connection.disconnect()
                        
                        when (responseCode) {
                            in 200..299 -> {
                                Log.d(TAG, "Wrapper service on $hostname:$port is healthy (${responseTime}ms)")
                                TestResult(true, responseTime, "Service is healthy (HTTP $responseCode)")
                            }
                            404 -> {
                                // 404 is acceptable - service is running but health endpoint might not exist
                                Log.d(TAG, "Wrapper service on $hostname:$port is running (404 on health check)")
                                TestResult(true, responseTime, "Service is running (HTTP $responseCode)")
                            }
                            else -> {
                                Log.w(TAG, "Wrapper service on $hostname:$port returned HTTP $responseCode")
                                TestResult(false, responseTime, "Service returned HTTP $responseCode")
                            }
                        }
                    } catch (e: ConnectException) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.w(TAG, "Wrapper service on $hostname:$port is not reachable")
                        TestResult(false, responseTime, "Service not reachable: ${e.message}")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Wrapper service test timed out for $hostname:$port")
            TestResult(false, timeoutMs, "Service test timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Wrapper service test failed for $hostname:$port", e)
            TestResult(false, 0, "Service test error: ${e.message}")
        }
    }
    
    /**
     * Tests SSH authentication (simplified - would need full SSH implementation).
     */
    private suspend fun testSshAuthentication(
        hostname: String,
        port: Int,
        username: String,
        sshIdentityId: String,
        timeoutMs: Long
    ): TestResult {
        return try {
            // For now, we'll just verify the SSH identity exists
            // In a full implementation, this would attempt SSH handshake
            val identityResult = sshIdentityService.getSshIdentity(sshIdentityId)
            
            if (identityResult.isSuccess) {
                Log.d(TAG, "SSH identity found for authentication test")
                TestResult(true, 0, "SSH identity is available (full auth test not implemented)")
            } else {
                Log.w(TAG, "SSH identity not found for authentication test")
                TestResult(false, 0, "SSH identity not found: ${identityResult.getErrorOrNull()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSH authentication test failed", e)
            TestResult(false, 0, "Auth test error: ${e.message}")
        }
    }
    
    /**
     * Gets a server profile by ID (simplified - would inject repository in real implementation).
     */
    private suspend fun getServerProfile(profileId: String): com.pocketagent.data.models.ServerProfile? {
        // This is a simplified implementation
        // In the actual implementation, this would inject the repository
        return try {
            // For now, return null - this would be replaced with actual repository call
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get server profile", e)
            null
        }
    }
    
    /**
     * Internal test result structure.
     */
    private data class TestResult(
        val success: Boolean,
        val responseTimeMs: Long,
        val error: String
    )
}