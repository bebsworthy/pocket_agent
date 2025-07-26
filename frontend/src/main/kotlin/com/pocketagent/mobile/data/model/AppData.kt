package com.pocketagent.mobile.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.util.UUID

/**
 * Root data model containing all app entities
 */
@Serializable
internal data class AppData(
    val version: Int = 1,
    val sshIdentities: List<SshIdentity> = emptyList(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val projects: List<Project> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap(), // projectId -> messages
    val lastModified: Long = System.currentTimeMillis(),
    val metadata: AppMetadata = AppMetadata()
)

/**
 * Application metadata
 */
@Serializable
internal data class AppMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = UUID.randomUUID().toString(),
    val backupEnabled: Boolean = true,
    val appVersion: String = "1.0.0",
    val dataSchemaVersion: Int = 1
)

/**
 * SSH Identity model representing an imported SSH private key
 */
@Serializable
internal data class SshIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val encryptedPrivateKey: String, // Already encrypted by SshKeyImportManager
    val publicKeyFingerprint: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "SSH Identity name cannot be blank" }
        require(name.length <= 100) { "SSH Identity name too long (max 100 chars)" }
        require(publicKeyFingerprint.matches(Regex("^[A-Fa-f0-9:]+$"))) { 
            "Invalid fingerprint format" 
        }
    }
}

/**
 * Server Profile model representing a development server connection
 */
@Serializable
internal data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshIdentityId: String,
    val wrapperPort: Int = 8080,
    val lastConnectedAt: Long? = null,
    val status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null
) {
    init {
        require(name.isNotBlank()) { "Server profile name cannot be blank" }
        require(name.length <= 100) { "Server profile name too long (max 100 chars)" }
        require(hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))) { "Invalid hostname" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(wrapperPort in 1..65535) { "Wrapper port must be between 1 and 65535" }
        require(username.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid username" }
    }
}

/**
 * Connection status enumeration
 */
@Serializable
enum class ConnectionStatus {
    NEVER_CONNECTED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Project model representing a Claude Code session
 */
@Serializable
internal data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val claudeSessionId: String? = null,
    val status: ProjectStatus = ProjectStatus.INACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null,
    val repositoryUrl: String? = null,
    val lastError: String? = null,
    val description: String? = null,
    val settings: ProjectSettings = ProjectSettings()
) {
    init {
        require(name.isNotBlank()) { "Project name cannot be blank" }
        require(name.length <= 100) { "Project name too long (max 100 chars)" }
        require(projectPath.isNotBlank()) { "Project path cannot be blank" }
        require(scriptsFolder.isNotBlank()) { "Scripts folder cannot be blank" }
    }
}

/**
 * Project status enumeration
 */
@Serializable
enum class ProjectStatus {
    INACTIVE,
    CONNECTING,
    ACTIVE,
    DISCONNECTED,
    ERROR
}

/**
 * Project-specific settings
 */
@Serializable
internal data class ProjectSettings(
    val maxTurns: Int = 50,
    val allowedTools: List<String> = emptyList(),
    val autoApprovePatterns: List<String> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val backgroundMonitoring: Boolean = true
)

/**
 * Message model for chat history
 */
@Serializable
internal data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<MessageAttachment> = emptyList()
) {
    init {
        require(content.isNotBlank()) { "Message content cannot be blank" }
    }
}

/**
 * Message type enumeration
 */
@Serializable
enum class MessageType {
    USER_INPUT,
    CLAUDE_RESPONSE,
    SYSTEM_MESSAGE,
    ERROR_MESSAGE,
    STATUS_UPDATE,
    PERMISSION_REQUEST,
    PERMISSION_RESPONSE
}

/**
 * Message attachment model
 */
@Serializable
internal data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val content: String,
    val size: Long = 0,
    val mimeType: String? = null
)

/**
 * Attachment type enumeration
 */
@Serializable
enum class AttachmentType {
    FILE_REFERENCE,
    CODE_SNIPPET,
    COMMAND_OUTPUT,
    ERROR_LOG
}