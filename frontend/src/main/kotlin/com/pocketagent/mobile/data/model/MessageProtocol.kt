package com.pocketagent.mobile.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

/**
 * Base interface for all WebSocket messages
 */
@Serializable
sealed class WebSocketMessage {
    abstract val type: String
    abstract val id: String
    abstract val timestamp: Long
}

/**
 * Authentication Messages
 */
@Serializable
@SerialName("auth_challenge")
internal data class AuthChallenge(
    override val type: String = "auth_challenge",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val nonce: String,
    val serverVersion: String
) : WebSocketMessage()

@Serializable
@SerialName("auth_response")
internal data class AuthResponse(
    override val type: String = "auth_response",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val publicKey: String,
    val signature: String,
    val clientVersion: String,
    val sessionId: String? = null
) : WebSocketMessage()

@Serializable
@SerialName("auth_success")
internal data class AuthSuccess(
    override val type: String = "auth_success",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val expiresAt: Long
) : WebSocketMessage()

@Serializable
@SerialName("auth_failure")
internal data class AuthFailure(
    override val type: String = "auth_failure",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val reason: String,
    val retryAllowed: Boolean = true
) : WebSocketMessage()

/**
 * Command Messages (Mobile → Wrapper)
 */
@Serializable
@SerialName("command")
internal data class CommandMessage(
    override val type: String = "command",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val isShellCommand: Boolean = false,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap()
) : WebSocketMessage()

@Serializable
@SerialName("project_init")
internal data class ProjectInitMessage(
    override val type: String = "project_init",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val projectPath: String,
    val repositoryUrl: String? = null,
    val accessToken: String? = null,
    val branch: String? = null
) : WebSocketMessage()

/**
 * Response Messages (Wrapper → Mobile)
 */
@Serializable
@SerialName("claude_response")
internal data class ClaudeResponse(
    override val type: String = "claude_response",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val isPartial: Boolean = false,
    val conversationId: String,
    val metadata: Map<String, String> = emptyMap()
) : WebSocketMessage()

@Serializable
@SerialName("command_output")
internal data class CommandOutput(
    override val type: String = "command_output",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val commandId: String,
    val output: String,
    val exitCode: Int? = null,
    val isPartial: Boolean = false,
    val executionTime: Long? = null
) : WebSocketMessage()

@Serializable
@SerialName("progress_update")
internal data class ProgressUpdate(
    override val type: String = "progress_update",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val operation: String,
    val progress: Int, // 0-100
    val subOperations: List<SubOperation> = emptyList(),
    val estimatedCompletion: Long? = null
) : WebSocketMessage()

@Serializable
internal data class SubOperation(
    val name: String,
    val status: SubOperationStatus,
    val progress: Int = 0,
    val error: String? = null
)

@Serializable
enum class SubOperationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Permission Messages
 */
@Serializable
@SerialName("permission_request")
internal data class PermissionRequest(
    override val type: String = "permission_request",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val tool: String,
    val action: String,
    val details: Map<String, String> = emptyMap(),
    val timeout: Int = 300, // seconds
    val defaultAction: String? = null
) : WebSocketMessage()

@Serializable
@SerialName("permission_response")
internal data class PermissionResponse(
    override val type: String = "permission_response",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val requestId: String,
    val approved: Boolean,
    val remember: Boolean = false,
    val reason: String? = null
) : WebSocketMessage()

/**
 * Session Management Messages
 */
@Serializable
@SerialName("session_resume")
internal data class SessionResume(
    override val type: String = "session_resume",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val lastMessageId: String? = null,
    val lastMessageTimestamp: Long? = null
) : WebSocketMessage()

@Serializable
@SerialName("session_status")
internal data class SessionStatus(
    override val type: String = "session_status",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val status: String, // "running", "completed", "error", "paused"
    val totalTurns: Int = 0,
    val executionTime: Double = 0.0,
    val error: String? = null
) : WebSocketMessage()

@Serializable
@SerialName("session_terminate")
internal data class SessionTerminate(
    override val type: String = "session_terminate",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val reason: String? = null
) : WebSocketMessage()

/**
 * Project initialization messages
 */
@Serializable
@SerialName("clone_progress")
internal data class CloneProgress(
    override val type: String = "clone_progress",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val percentage: Int,
    val status: String,
    val error: String? = null
) : WebSocketMessage()

@Serializable
@SerialName("project_init_complete")
internal data class ProjectInitComplete(
    override val type: String = "project_init_complete",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val sessionId: String? = null,
    val error: String? = null
) : WebSocketMessage()

/**
 * Error and Status Messages
 */
@Serializable
@SerialName("error")
internal data class ErrorMessage(
    override val type: String = "error",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val error: String,
    val code: String? = null,
    val recoverable: Boolean = true,
    val context: Map<String, String> = emptyMap()
) : WebSocketMessage()

@Serializable
@SerialName("heartbeat")
internal data class Heartbeat(
    override val type: String = "heartbeat",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val clientStatus: String = "active"
) : WebSocketMessage()

@Serializable
@SerialName("pong")
internal data class Pong(
    override val type: String = "pong",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val originalId: String
) : WebSocketMessage()

/**
 * File operation messages
 */
@Serializable
@SerialName("file_list_request")
internal data class FileListRequest(
    override val type: String = "file_list_request",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val path: String,
    val recursive: Boolean = false,
    val includeHidden: Boolean = false
) : WebSocketMessage()

@Serializable
@SerialName("file_list_response")
internal data class FileListResponse(
    override val type: String = "file_list_response",
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    val requestId: String,
    val files: List<FileInfo>,
    val error: String? = null
) : WebSocketMessage()

@Serializable
internal data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String? = null,
    val gitStatus: String? = null
)