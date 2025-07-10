package com.pocketagent.domain.models.responses

import com.pocketagent.domain.models.entities.*

/**
 * Base class for all API responses.
 */
sealed class ApiResponse {
    abstract val requestId: String
    abstract val timestamp: Long
    abstract val success: Boolean
}

/**
 * Response to a connect request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the connection was successful
 * @property sessionId Session ID if successful
 * @property projectStatus Current project status
 * @property errorMessage Error message if failed
 * @property serverInfo Server information
 */
data class ConnectResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val sessionId: String? = null,
    val projectStatus: ProjectStatus = ProjectStatus.INACTIVE,
    val errorMessage: String? = null,
    val serverInfo: ServerInfo? = null
) : ApiResponse()

/**
 * Response to a disconnect request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the disconnection was successful
 * @property projectStatus Final project status
 * @property errorMessage Error message if failed
 */
data class DisconnectResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val projectStatus: ProjectStatus = ProjectStatus.INACTIVE,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a send message request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the message was sent successfully
 * @property messageId Message ID if successful
 * @property claudeResponse Claude's response if any
 * @property errorMessage Error message if failed
 */
data class SendMessageResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val messageId: String? = null,
    val claudeResponse: Message? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to an execute command request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the command was executed successfully
 * @property output Command output
 * @property exitCode Exit code of the command
 * @property errorMessage Error message if failed
 * @property executionTime Execution time in milliseconds
 */
data class ExecuteCommandResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val output: String? = null,
    val exitCode: Int? = null,
    val errorMessage: String? = null,
    val executionTime: Long? = null
) : ApiResponse()

/**
 * Response to a permission response request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the permission response was processed successfully
 * @property permissionId Permission ID that was responded to
 * @property actionResult Result of the approved action if any
 * @property errorMessage Error message if failed
 */
data class PermissionResponseResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val permissionId: String,
    val actionResult: String? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a file upload request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the file was uploaded successfully
 * @property filePath Path where file was uploaded
 * @property fileSize Size of uploaded file
 * @property checksum File checksum for verification
 * @property errorMessage Error message if failed
 */
data class UploadFileResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val checksum: String? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a file download request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the file was downloaded successfully
 * @property content File content (base64 encoded)
 * @property fileSize Size of downloaded file
 * @property mimeType MIME type of the file
 * @property lastModified Last modified timestamp
 * @property errorMessage Error message if failed
 */
data class DownloadFileResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val content: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val lastModified: Long? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a list files request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the files were listed successfully
 * @property files List of files in the directory
 * @property totalCount Total number of files
 * @property errorMessage Error message if failed
 */
data class ListFilesResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val files: List<FileInfo> = emptyList(),
    val totalCount: Int = 0,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a git status request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the git status was retrieved successfully
 * @property status Git status information
 * @property errorMessage Error message if failed
 */
data class GitStatusResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val status: GitStatus? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to an initialize project request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the project was initialized successfully
 * @property projectPath Path where project was initialized
 * @property repositoryCloned Whether repository was cloned
 * @property scriptsExecuted Number of scripts executed
 * @property errorMessage Error message if failed
 */
data class InitializeProjectResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val projectPath: String? = null,
    val repositoryCloned: Boolean = false,
    val scriptsExecuted: Int = 0,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a server status request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the server status was retrieved successfully
 * @property status Server connection status
 * @property serverInfo Server information
 * @property errorMessage Error message if failed
 */
data class ServerStatusResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED,
    val serverInfo: ServerInfo? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a test connection request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the connection test was successful
 * @property latency Connection latency in milliseconds
 * @property serverInfo Server information
 * @property errorMessage Error message if failed
 */
data class TestConnectionResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val latency: Long? = null,
    val serverInfo: ServerInfo? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a project status request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the project status was retrieved successfully
 * @property status Project status
 * @property sessionId Current session ID
 * @property lastActivity Last activity timestamp
 * @property errorMessage Error message if failed
 */
data class ProjectStatusResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val status: ProjectStatus = ProjectStatus.INACTIVE,
    val sessionId: String? = null,
    val lastActivity: Long? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a cancel operation request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the operation was cancelled successfully
 * @property operationId Operation that was cancelled
 * @property errorMessage Error message if failed
 */
data class CancelOperationResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val operationId: String,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a session history request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the session history was retrieved successfully
 * @property messages List of messages
 * @property totalCount Total number of messages
 * @property hasMore Whether there are more messages
 * @property errorMessage Error message if failed
 */
data class SessionHistoryResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val messages: List<Message> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to an authenticate request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the authentication was successful
 * @property sessionToken Session token if successful
 * @property expiresAt Session expiration timestamp
 * @property errorMessage Error message if failed
 */
data class AuthenticateResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val sessionToken: String? = null,
    val expiresAt: Long? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Response to a heartbeat request.
 * 
 * @property requestId Original request ID
 * @property timestamp Response timestamp
 * @property success Whether the heartbeat was successful
 * @property serverTime Server timestamp
 * @property errorMessage Error message if failed
 */
data class HeartbeatResponse(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val success: Boolean,
    val serverTime: Long? = null,
    val errorMessage: String? = null
) : ApiResponse()

/**
 * Server information model.
 * 
 * @property hostname Server hostname
 * @property version Server version
 * @property uptime Server uptime in seconds
 * @property load Server load average
 * @property memory Memory usage information
 * @property disk Disk usage information
 * @property capabilities Server capabilities
 */
data class ServerInfo(
    val hostname: String,
    val version: String,
    val uptime: Long,
    val load: List<Double> = emptyList(),
    val memory: MemoryInfo? = null,
    val disk: DiskInfo? = null,
    val capabilities: List<String> = emptyList()
)

/**
 * Memory usage information.
 * 
 * @property total Total memory in bytes
 * @property used Used memory in bytes
 * @property free Free memory in bytes
 * @property available Available memory in bytes
 */
data class MemoryInfo(
    val total: Long,
    val used: Long,
    val free: Long,
    val available: Long
)

/**
 * Disk usage information.
 * 
 * @property total Total disk space in bytes
 * @property used Used disk space in bytes
 * @property free Free disk space in bytes
 * @property available Available disk space in bytes
 */
data class DiskInfo(
    val total: Long,
    val used: Long,
    val free: Long,
    val available: Long
)

/**
 * File information model.
 * 
 * @property name File name
 * @property path Full file path
 * @property size File size in bytes
 * @property isDirectory Whether this is a directory
 * @property lastModified Last modified timestamp
 * @property permissions File permissions
 * @property owner File owner
 * @property group File group
 * @property mimeType MIME type if known
 */
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null,
    val mimeType: String? = null
)

/**
 * Git status information.
 * 
 * @property branch Current branch
 * @property ahead Number of commits ahead
 * @property behind Number of commits behind
 * @property modified List of modified files
 * @property staged List of staged files
 * @property untracked List of untracked files
 * @property deleted List of deleted files
 * @property hasChanges Whether there are any changes
 */
data class GitStatus(
    val branch: String,
    val ahead: Int = 0,
    val behind: Int = 0,
    val modified: List<String> = emptyList(),
    val staged: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val hasChanges: Boolean = false
)

/**
 * Permission request model.
 * 
 * @property id Permission request ID
 * @property projectId Project ID
 * @property tool Tool requesting permission
 * @property action Action being requested
 * @property description Description of the action
 * @property risk Risk level of the action
 * @property timestamp Request timestamp
 * @property timeout Timeout for the request
 * @property metadata Additional metadata
 */
data class PermissionRequest(
    val id: String,
    val projectId: String,
    val tool: String,
    val action: String,
    val description: String,
    val risk: RiskLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val timeout: Long = 30_000, // 30 seconds
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Risk levels for permission requests.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Real-time event from the server.
 * 
 * @property eventId Event ID
 * @property projectId Project ID
 * @property type Event type
 * @property timestamp Event timestamp
 * @property data Event data
 */
data class ServerEvent(
    val eventId: String,
    val projectId: String,
    val type: EventType,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, Any> = emptyMap()
)

/**
 * Event types for real-time events.
 */
enum class EventType {
    MESSAGE_RECEIVED,
    STATUS_CHANGED,
    ERROR_OCCURRED,
    PERMISSION_REQUESTED,
    OPERATION_COMPLETED,
    FILE_CHANGED,
    GIT_STATUS_CHANGED
}