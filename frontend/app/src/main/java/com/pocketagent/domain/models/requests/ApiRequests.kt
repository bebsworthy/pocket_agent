package com.pocketagent.domain.models.requests

import com.pocketagent.domain.models.entities.MessageType
import com.pocketagent.domain.models.entities.ProjectStatus
import com.pocketagent.domain.models.entities.ConnectionStatus

/**
 * Base class for all API requests.
 */
sealed class ApiRequest {
    abstract val requestId: String
    abstract val timestamp: Long
}

/**
 * Request to establish connection to a server.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property serverProfileId Server profile to connect to
 * @property projectId Project to initialize
 * @property sshKeyFingerprint SSH key fingerprint for authentication
 */
data class ConnectRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val serverProfileId: String,
    val projectId: String,
    val sshKeyFingerprint: String
) : ApiRequest()

/**
 * Request to disconnect from a server.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to disconnect from
 * @property shutdownClaude Whether to shutdown Claude Code process
 */
data class DisconnectRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val shutdownClaude: Boolean = false
) : ApiRequest()

/**
 * Request to send a message to Claude Code.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to send message to
 * @property content Message content
 * @property type Message type
 * @property attachments List of attachment IDs
 * @property metadata Additional metadata
 */
data class SendMessageRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val content: String,
    val type: MessageType = MessageType.USER_INPUT,
    val attachments: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) : ApiRequest()

/**
 * Request to execute a shell command.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to execute command in
 * @property command Command to execute
 * @property workingDirectory Working directory for command
 * @property environment Environment variables
 * @property timeout Timeout in milliseconds
 */
data class ExecuteCommandRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val command: String,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeout: Long = 30_000 // 30 seconds
) : ApiRequest()

/**
 * Request to respond to a permission request.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project the permission is for
 * @property permissionId Permission request ID
 * @property approved Whether the permission is approved
 * @property reason Optional reason for the decision
 */
data class PermissionResponseRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val permissionId: String,
    val approved: Boolean,
    val reason: String? = null
) : ApiRequest()

/**
 * Request to upload a file.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to upload file to
 * @property filename Filename
 * @property path Target path on server
 * @property content File content (base64 encoded)
 * @property overwrite Whether to overwrite existing file
 */
data class UploadFileRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val filename: String,
    val path: String,
    val content: String, // base64 encoded
    val overwrite: Boolean = false
) : ApiRequest()

/**
 * Request to download a file.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to download file from
 * @property path File path on server
 * @property encoding Encoding to use for text files
 */
data class DownloadFileRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val path: String,
    val encoding: String = "UTF-8"
) : ApiRequest()

/**
 * Request to list files in a directory.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to list files in
 * @property path Directory path
 * @property recursive Whether to list files recursively
 * @property includeHidden Whether to include hidden files
 */
data class ListFilesRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val path: String,
    val recursive: Boolean = false,
    val includeHidden: Boolean = false
) : ApiRequest()

/**
 * Request to get git status.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to get git status for
 * @property path Optional path to check status for
 */
data class GitStatusRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val path: String? = null
) : ApiRequest()

/**
 * Request to initialize a project.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to initialize
 * @property projectPath Path where project should be located
 * @property repositoryUrl Optional repository URL to clone
 * @property accessToken Optional access token for private repositories
 * @property scripts Optional initialization scripts to run
 */
data class InitializeProjectRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val projectPath: String,
    val repositoryUrl: String? = null,
    val accessToken: String? = null,
    val scripts: List<String> = emptyList()
) : ApiRequest()

/**
 * Request to get server status.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property serverProfileId Server profile to check status for
 */
data class ServerStatusRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val serverProfileId: String
) : ApiRequest()

/**
 * Request to test SSH connection.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property serverProfileId Server profile to test connection for
 * @property sshKeyFingerprint SSH key fingerprint for authentication
 */
data class TestConnectionRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val serverProfileId: String,
    val sshKeyFingerprint: String
) : ApiRequest()

/**
 * Request to get project status.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to get status for
 */
data class ProjectStatusRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String
) : ApiRequest()

/**
 * Request to cancel an operation.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property operationId Operation to cancel
 * @property reason Optional reason for cancellation
 */
data class CancelOperationRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val operationId: String,
    val reason: String? = null
) : ApiRequest()

/**
 * Request to get session history.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to get history for
 * @property limit Maximum number of messages to return
 * @property offset Offset for pagination
 * @property messageType Optional filter by message type
 */
data class SessionHistoryRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String,
    val limit: Int = 100,
    val offset: Int = 0,
    val messageType: MessageType? = null
) : ApiRequest()

/**
 * Request to authenticate with SSH key.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property serverProfileId Server profile to authenticate with
 * @property publicKeyFingerprint SSH public key fingerprint
 * @property challenge Challenge from server
 * @property signature Signature of challenge
 */
data class AuthenticateRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val serverProfileId: String,
    val publicKeyFingerprint: String,
    val challenge: String,
    val signature: String
) : ApiRequest()

/**
 * Heartbeat request to maintain connection.
 * 
 * @property requestId Unique identifier for the request
 * @property timestamp Request timestamp
 * @property projectId Project to send heartbeat for
 */
data class HeartbeatRequest(
    override val requestId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val projectId: String
) : ApiRequest()