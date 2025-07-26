package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.ConnectionStatus
import com.pocketagent.domain.models.entities.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for communication with Claude Code instances.
 *
 * This interface defines the contract for managing WebSocket connections,
 * real-time communication with remote Claude Code sessions, and SSH authentication
 * for secure connections.
 *
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface CommunicationRepository {
    /**
     * Connects to a Claude Code session using SSH authentication.
     *
     * @param projectId The project ID
     * @param serverProfileId The server profile ID
     * @return Success or error result
     */
    suspend fun connect(
        projectId: String,
        serverProfileId: String,
    ): Result<Unit>

    /**
     * Reconnects to a Claude Code session with exponential backoff.
     *
     * @param projectId The project ID
     * @param maxRetries Maximum number of retry attempts
     * @return Success or error result
     */
    suspend fun reconnect(
        projectId: String,
        maxRetries: Int = 3,
    ): Result<Unit>

    /**
     * Disconnects from a Claude Code session.
     *
     * @param projectId The project ID
     * @param shutdown Whether to shutdown Claude Code processes
     * @return Success or error result
     */
    suspend fun disconnect(
        projectId: String,
        shutdown: Boolean = false,
    ): Result<Unit>

    /**
     * Disconnects all active connections.
     *
     * @return Success or error result
     */
    suspend fun disconnectAll(): Result<Unit>

    /**
     * Sends a message to Claude Code.
     *
     * @param projectId The project ID
     * @param message The message to send
     * @return Success or error result
     */
    suspend fun sendMessage(
        projectId: String,
        message: String,
    ): Result<Unit>

    /**
     * Sends a command to Claude Code.
     *
     * @param projectId The project ID
     * @param command The command to execute
     * @return Success or error result
     */
    suspend fun sendCommand(
        projectId: String,
        command: String,
    ): Result<Unit>

    /**
     * Sends a file upload request to Claude Code.
     *
     * @param projectId The project ID
     * @param filePath The file path to upload
     * @param fileContent The file content
     * @return Success or error result
     */
    suspend fun sendFileUpload(
        projectId: String,
        filePath: String,
        fileContent: ByteArray,
    ): Result<Unit>

    /**
     * Sends a file download request to Claude Code.
     *
     * @param projectId The project ID
     * @param filePath The file path to download
     * @return The file content
     */
    suspend fun sendFileDownload(
        projectId: String,
        filePath: String,
    ): Result<ByteArray>

    /**
     * Responds to a permission request.
     *
     * @param projectId The project ID
     * @param messageId The message ID requesting permission
     * @param approved Whether the permission is approved
     * @return Success or error result
     */
    suspend fun respondToPermissionRequest(
        projectId: String,
        messageId: String,
        approved: Boolean,
    ): Result<Unit>

    /**
     * Sends a batch of permission responses.
     *
     * @param projectId The project ID
     * @param responses Map of message ID to approval status
     * @return Success or error result
     */
    suspend fun sendPermissionResponses(
        projectId: String,
        responses: Map<String, Boolean>,
    ): Result<Unit>

    /**
     * Gets connection status for a project.
     *
     * @param projectId The project ID
     * @return Flow emitting connection status updates
     */
    fun getConnectionStatus(projectId: String): Flow<ConnectionStatus>

    /**
     * Observes connection status for all projects.
     *
     * @return Flow emitting map of project ID to connection status
     */
    fun observeAllConnectionStatuses(): Flow<Map<String, ConnectionStatus>>

    /**
     * Gets incoming messages for a project.
     *
     * @param projectId The project ID
     * @return Flow emitting incoming messages
     */
    fun getIncomingMessages(projectId: String): Flow<Message>

    /**
     * Gets permission requests for a project.
     *
     * @param projectId The project ID
     * @return Flow emitting permission requests
     */
    fun getPermissionRequests(projectId: String): Flow<Message>

    /**
     * Observes permission requests for all projects.
     *
     * @return Flow emitting map of project ID to permission request count
     */
    fun observeAllPermissionRequests(): Flow<Map<String, Int>>

    /**
     * Initializes a project with optional repository cloning.
     *
     * @param projectId The project ID
     * @param projectPath The project path on the server
     * @param repositoryUrl Optional repository URL to clone
     * @param accessToken Optional access token for private repositories
     * @return Flow emitting initialization progress
     */
    fun initializeProject(
        projectId: String,
        projectPath: String,
        repositoryUrl: String? = null,
        accessToken: String? = null,
    ): Flow<Result<InitializationProgress>>

    /**
     * Gets Claude Code session health status.
     *
     * @param projectId The project ID
     * @return Session health information
     */
    suspend fun getSessionHealth(projectId: String): Result<SessionHealth>

    /**
     * Gets health status for all active sessions.
     *
     * @return Map of project ID to session health
     */
    suspend fun getAllSessionHealth(): Result<Map<String, SessionHealth>>

    /**
     * Ping the Claude Code session to check connectivity.
     *
     * @param projectId The project ID
     * @return Success if connection is healthy
     */
    suspend fun ping(projectId: String): Result<Unit>

    /**
     * Ping all active sessions.
     *
     * @return Map of project ID to ping success status
     */
    suspend fun pingAll(): Result<Map<String, Boolean>>

    /**
     * Gets network statistics for a project.
     *
     * @param projectId The project ID
     * @return Network statistics
     */
    suspend fun getNetworkStats(projectId: String): Result<NetworkStats>

    /**
     * Observes network statistics for real-time updates.
     *
     * @param projectId The project ID
     * @return Flow emitting network statistics updates
     */
    fun observeNetworkStats(projectId: String): Flow<NetworkStats>

    /**
     * Sets up automatic reconnection for a project.
     *
     * @param projectId The project ID
     * @param enabled Whether to enable auto-reconnection
     * @return Success or error result
     */
    suspend fun setAutoReconnect(
        projectId: String,
        enabled: Boolean,
    ): Result<Unit>

    /**
     * Gets the WebSocket connection state for a project.
     *
     * @param projectId The project ID
     * @return WebSocket connection state
     */
    suspend fun getWebSocketState(projectId: String): Result<WebSocketState>

    /**
     * Observes WebSocket state changes for a project.
     *
     * @param projectId The project ID
     * @return Flow emitting WebSocket state updates
     */
    fun observeWebSocketState(projectId: String): Flow<WebSocketState>

    /**
     * Sends a heartbeat to maintain connection.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun sendHeartbeat(projectId: String): Result<Unit>

    /**
     * Gets message queue status for offline messages.
     *
     * @param projectId The project ID
     * @return Message queue information
     */
    suspend fun getMessageQueueStatus(projectId: String): Result<MessageQueueStatus>

    /**
     * Clears the message queue for a project.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun clearMessageQueue(projectId: String): Result<Unit>

    /**
     * Gets SSH authentication status for a project.
     *
     * @param projectId The project ID
     * @return SSH authentication information
     */
    suspend fun getSshAuthStatus(projectId: String): Result<SshAuthStatus>

    /**
     * Refreshes SSH authentication for a project.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun refreshSshAuth(projectId: String): Result<Unit>

    /**
     * Gets active connection count.
     *
     * @return Number of active connections
     */
    suspend fun getActiveConnectionCount(): Result<Int>

    /**
     * Gets communication statistics.
     *
     * @return Communication statistics
     */
    suspend fun getCommunicationStats(): Result<CommunicationStats>

    /**
     * Exports communication logs for a project.
     *
     * @param projectId The project ID
     * @param format Export format (json, text)
     * @return Exported logs
     */
    suspend fun exportCommunicationLogs(
        projectId: String,
        format: String = "json",
    ): Result<String>

    /**
     * Clears communication logs for a project.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun clearCommunicationLogs(projectId: String): Result<Unit>

    /**
     * Clears all communication data (for logout/reset).
     *
     * @return Success or error result
     */
    suspend fun clearAllCommunicationData(): Result<Unit>
}

/**
 * Represents the progress of project initialization.
 *
 * @property step Current initialization step
 * @property progress Progress percentage (0-100)
 * @property message Status message
 * @property isComplete Whether initialization is complete
 * @property error Optional error information
 */
data class InitializationProgress(
    val step: String,
    val progress: Int,
    val message: String,
    val isComplete: Boolean = false,
    val error: String? = null,
)

/**
 * Represents the health status of a Claude Code session.
 *
 * @property isHealthy Whether the session is healthy
 * @property uptime Session uptime in milliseconds
 * @property memoryUsage Memory usage in MB
 * @property cpuUsage CPU usage percentage
 * @property lastActivity Timestamp of last activity
 * @property connectionLatency Connection latency in milliseconds
 */
data class SessionHealth(
    val isHealthy: Boolean,
    val uptime: Long,
    val memoryUsage: Long,
    val cpuUsage: Double,
    val lastActivity: Long,
    val connectionLatency: Long,
)

/**
 * Represents network statistics for a connection.
 *
 * @property bytesReceived Total bytes received
 * @property bytesSent Total bytes sent
 * @property messagesReceived Total messages received
 * @property messagesSent Total messages sent
 * @property averageLatency Average message latency
 * @property connectionUptime Connection uptime in milliseconds
 */
data class NetworkStats(
    val bytesReceived: Long,
    val bytesSent: Long,
    val messagesReceived: Long,
    val messagesSent: Long,
    val averageLatency: Long,
    val connectionUptime: Long,
)

/**
 * Represents WebSocket connection state.
 */
enum class WebSocketState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    ERROR,
}

/**
 * Represents message queue status.
 *
 * @property queueSize Number of queued messages
 * @property maxQueueSize Maximum queue size
 * @property isProcessing Whether the queue is being processed
 */
data class MessageQueueStatus(
    val queueSize: Int,
    val maxQueueSize: Int,
    val isProcessing: Boolean,
)

/**
 * Represents SSH authentication status.
 *
 * @property isAuthenticated Whether SSH authentication is valid
 * @property keyFingerprint SSH key fingerprint
 * @property authTime Authentication timestamp
 * @property expiresAt Optional expiration timestamp
 */
data class SshAuthStatus(
    val isAuthenticated: Boolean,
    val keyFingerprint: String,
    val authTime: Long,
    val expiresAt: Long? = null,
)

/**
 * Represents overall communication statistics.
 *
 * @property activeConnections Number of active connections
 * @property totalMessages Total messages processed
 * @property totalDataTransferred Total data transferred in bytes
 * @property averageResponseTime Average response time
 * @property uptimePercentage Uptime percentage
 */
data class CommunicationStats(
    val activeConnections: Int,
    val totalMessages: Long,
    val totalDataTransferred: Long,
    val averageResponseTime: Long,
    val uptimePercentage: Double,
)
