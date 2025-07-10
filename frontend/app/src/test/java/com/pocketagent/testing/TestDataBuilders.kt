package com.pocketagent.testing

import java.time.Instant
import java.util.UUID

/**
 * Test data builders for creating test entities with sensible defaults.
 */

/**
 * Builder for SSH Identity entities.
 */
class SshIdentityTestBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test SSH Key"
    private var publicKey: String = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC7VzYt..."
    private var encryptedPrivateKey: ByteArray = "encrypted_private_key".toByteArray()
    private var keyAlias: String = "test_key_alias"
    private var createdAt: Instant = Instant.now()

    fun id(id: String) = apply { this.id = id }

    fun name(name: String) = apply { this.name = name }

    fun publicKey(publicKey: String) = apply { this.publicKey = publicKey }

    fun encryptedPrivateKey(key: ByteArray) = apply { this.encryptedPrivateKey = key }

    fun keyAlias(alias: String) = apply { this.keyAlias = alias }

    fun createdAt(instant: Instant) = apply { this.createdAt = instant }

    fun build(): SshIdentityEntity {
        return SshIdentityEntity(
            id = id,
            name = name,
            publicKey = publicKey,
            encryptedPrivateKey = encryptedPrivateKey,
            keyAlias = keyAlias,
            createdAt = createdAt,
        )
    }
}

/**
 * Builder for Server Profile entities.
 */
class ServerProfileTestBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test Server"
    private var hostname: String = "test.example.com"
    private var port: Int = 22
    private var username: String = "testuser"
    private var sshIdentityId: String = UUID.randomUUID().toString()
    private var lastConnected: Instant = Instant.now()
    private var status: String = "DISCONNECTED"

    fun id(id: String) = apply { this.id = id }

    fun name(name: String) = apply { this.name = name }

    fun hostname(hostname: String) = apply { this.hostname = hostname }

    fun port(port: Int) = apply { this.port = port }

    fun username(username: String) = apply { this.username = username }

    fun sshIdentityId(sshIdentityId: String) = apply { this.sshIdentityId = sshIdentityId }

    fun lastConnected(instant: Instant) = apply { this.lastConnected = instant }

    fun status(status: String) = apply { this.status = status }

    fun build(): ServerProfileEntity {
        return ServerProfileEntity(
            id = id,
            name = name,
            hostname = hostname,
            port = port,
            username = username,
            sshIdentityId = sshIdentityId,
            lastConnected = lastConnected,
            status = status,
        )
    }
}

/**
 * Builder for Project entities.
 */
class ProjectTestBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test Project"
    private var serverProfileId: String = UUID.randomUUID().toString()
    private var projectPath: String = "/home/user/test-project"
    private var scriptsFolder: String = "scripts"
    private var claudeSessionId: String? = null
    private var status: String = "DISCONNECTED"
    private var created: Instant = Instant.now()
    private var lastActive: Instant = Instant.now()

    fun id(id: String) = apply { this.id = id }

    fun name(name: String) = apply { this.name = name }

    fun serverProfileId(serverProfileId: String) = apply { this.serverProfileId = serverProfileId }

    fun projectPath(path: String) = apply { this.projectPath = path }

    fun scriptsFolder(folder: String) = apply { this.scriptsFolder = folder }

    fun claudeSessionId(sessionId: String?) = apply { this.claudeSessionId = sessionId }

    fun status(status: String) = apply { this.status = status }

    fun created(instant: Instant) = apply { this.created = instant }

    fun lastActive(instant: Instant) = apply { this.lastActive = instant }

    fun build(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            serverProfileId = serverProfileId,
            projectPath = projectPath,
            scriptsFolder = scriptsFolder,
            claudeSessionId = claudeSessionId,
            status = status,
            created = created,
            lastActive = lastActive,
        )
    }
}

/**
 * Builder for Message entities.
 */
class MessageTestBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var projectId: String = UUID.randomUUID().toString()
    private var content: String = "Test message content"
    private var type: String = "USER"
    private var timestamp: Instant = Instant.now()
    private var isPartial: Boolean = false
    private var metadata: Map<String, String> = emptyMap()

    fun id(id: String) = apply { this.id = id }

    fun projectId(projectId: String) = apply { this.projectId = projectId }

    fun content(content: String) = apply { this.content = content }

    fun type(type: String) = apply { this.type = type }

    fun timestamp(instant: Instant) = apply { this.timestamp = instant }

    fun isPartial(partial: Boolean) = apply { this.isPartial = partial }

    fun metadata(metadata: Map<String, String>) = apply { this.metadata = metadata }

    fun build(): MessageEntity {
        return MessageEntity(
            id = id,
            projectId = projectId,
            content = content,
            type = type,
            timestamp = timestamp,
            isPartial = isPartial,
            metadata = metadata,
        )
    }
}

/**
 * Builder for WebSocket Message Protocol entities.
 */
class WebSocketMessageTestBuilder {
    private var type: String = "command"
    private var id: String = UUID.randomUUID().toString()
    private var timestamp: Long = System.currentTimeMillis()
    private var payload: Map<String, Any> = emptyMap()

    fun type(type: String) = apply { this.type = type }

    fun id(id: String) = apply { this.id = id }

    fun timestamp(timestamp: Long) = apply { this.timestamp = timestamp }

    fun payload(payload: Map<String, Any>) = apply { this.payload = payload }

    fun buildCommandMessage(
        command: String,
        isShellCommand: Boolean = false,
    ): Map<String, Any> {
        return mapOf(
            "type" to "command",
            "id" to id,
            "timestamp" to timestamp,
            "command" to command,
            "isShellCommand" to isShellCommand,
        )
    }

    fun buildClaudeResponse(
        content: String,
        conversationId: String,
    ): Map<String, Any> {
        return mapOf(
            "type" to "claude_response",
            "id" to id,
            "timestamp" to timestamp,
            "content" to content,
            "conversationId" to conversationId,
            "isPartial" to false,
        )
    }

    fun buildPermissionRequest(
        tool: String,
        action: String,
        details: Map<String, Any> = emptyMap(),
        risk: String = "MEDIUM",
    ): Map<String, Any> {
        return mapOf(
            "type" to "permission_request",
            "id" to id,
            "timestamp" to timestamp,
            "tool" to tool,
            "action" to action,
            "details" to details,
            "risk" to risk,
            "timeout" to 300,
        )
    }

    fun buildPermissionResponse(
        requestId: String,
        approved: Boolean,
    ): Map<String, Any> {
        return mapOf(
            "type" to "permission_response",
            "id" to id,
            "timestamp" to timestamp,
            "requestId" to requestId,
            "approved" to approved,
        )
    }

    fun buildAuthChallenge(
        nonce: String,
        serverVersion: String = "1.0.0",
    ): Map<String, Any> {
        return mapOf(
            "type" to "auth_challenge",
            "nonce" to nonce,
            "timestamp" to timestamp,
            "serverVersion" to serverVersion,
        )
    }

    fun buildAuthResponse(
        publicKey: String,
        signature: String,
        sessionId: String? = null,
    ): Map<String, Any> {
        return mapOf(
            "type" to "auth_response",
            "publicKey" to publicKey,
            "signature" to signature,
            "clientVersion" to "1.0.0",
            "sessionId" to sessionId,
        )
    }

    fun buildAuthSuccess(sessionId: String): Map<String, Any> {
        return mapOf(
            "type" to "auth_success",
            "sessionId" to sessionId,
            // 24 hours
            "expiresAt" to (timestamp + 86400000),
        )
    }
}

/**
 * Builder for Battery State entities.
 */
class BatteryStateTestBuilder {
    private var percentage: Int = 50
    private var isCharging: Boolean = false
    private var isPowerSaveMode: Boolean = false
    private var level: String = "NORMAL"

    fun percentage(percentage: Int) = apply { this.percentage = percentage }

    fun isCharging(charging: Boolean) = apply { this.isCharging = charging }

    fun isPowerSaveMode(powerSave: Boolean) = apply { this.isPowerSaveMode = powerSave }

    fun level(level: String) = apply { this.level = level }

    fun build(): BatteryStateEntity {
        return BatteryStateEntity(
            percentage = percentage,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            level = level,
        )
    }
}

/**
 * Factory object for creating test data with predefined scenarios.
 */
object TestDataFactory {
    /**
     * Creates a typical SSH identity for testing.
     */
    fun createSshIdentity(
        name: String = "Test SSH Key",
        keyAlias: String = "test_key",
    ): SshIdentityEntity {
        return SshIdentityTestBuilder()
            .name(name)
            .keyAlias(keyAlias)
            .build()
    }

    /**
     * Creates a typical server profile for testing.
     */
    fun createServerProfile(
        name: String = "Test Server",
        hostname: String = "test.example.com",
        sshIdentityId: String = UUID.randomUUID().toString(),
    ): ServerProfileEntity {
        return ServerProfileTestBuilder()
            .name(name)
            .hostname(hostname)
            .sshIdentityId(sshIdentityId)
            .build()
    }

    /**
     * Creates a typical project for testing.
     */
    fun createProject(
        name: String = "Test Project",
        serverProfileId: String = UUID.randomUUID().toString(),
        status: String = "DISCONNECTED",
    ): ProjectEntity {
        return ProjectTestBuilder()
            .name(name)
            .serverProfileId(serverProfileId)
            .status(status)
            .build()
    }

    /**
     * Creates a user message for testing.
     */
    fun createUserMessage(
        content: String = "Test user message",
        projectId: String = UUID.randomUUID().toString(),
    ): MessageEntity {
        return MessageTestBuilder()
            .content(content)
            .type("USER")
            .projectId(projectId)
            .build()
    }

    /**
     * Creates a Claude response message for testing.
     */
    fun createClaudeMessage(
        content: String = "Test Claude response",
        projectId: String = UUID.randomUUID().toString(),
    ): MessageEntity {
        return MessageTestBuilder()
            .content(content)
            .type("CLAUDE")
            .projectId(projectId)
            .build()
    }

    /**
     * Creates a low battery state for testing.
     */
    fun createLowBatteryState(): BatteryStateEntity {
        return BatteryStateTestBuilder()
            .percentage(25)
            .isCharging(false)
            .level("LOW")
            .build()
    }

    /**
     * Creates a charging battery state for testing.
     */
    fun createChargingBatteryState(): BatteryStateEntity {
        return BatteryStateTestBuilder()
            .percentage(75)
            .isCharging(true)
            .level("CHARGING")
            .build()
    }

    /**
     * Creates a complete project setup with related entities.
     */
    fun createCompleteProjectSetup(): ProjectSetup {
        val sshIdentity = createSshIdentity()
        val serverProfile = createServerProfile(sshIdentityId = sshIdentity.id)
        val project = createProject(serverProfileId = serverProfile.id, status = "CONNECTED")
        val messages =
            listOf(
                createUserMessage(content = "Hello Claude", projectId = project.id),
                createClaudeMessage(content = "Hello! How can I help you?", projectId = project.id),
            )

        return ProjectSetup(
            sshIdentity = sshIdentity,
            serverProfile = serverProfile,
            project = project,
            messages = messages,
        )
    }
}

/**
 * Data class for complete project setup.
 */
data class ProjectSetup(
    val sshIdentity: SshIdentityEntity,
    val serverProfile: ServerProfileEntity,
    val project: ProjectEntity,
    val messages: List<MessageEntity>,
)

// Placeholder entity classes - these would be replaced with actual domain models
data class SshIdentityEntity(
    val id: String,
    val name: String,
    val publicKey: String,
    val encryptedPrivateKey: ByteArray,
    val keyAlias: String,
    val createdAt: Instant,
)

data class ServerProfileEntity(
    val id: String,
    val name: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val sshIdentityId: String,
    val lastConnected: Instant,
    val status: String,
)

data class ProjectEntity(
    val id: String,
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String,
    val claudeSessionId: String?,
    val status: String,
    val created: Instant,
    val lastActive: Instant,
)

data class MessageEntity(
    val id: String,
    val projectId: String,
    val content: String,
    val type: String,
    val timestamp: Instant,
    val isPartial: Boolean,
    val metadata: Map<String, String>,
)

data class BatteryStateEntity(
    val percentage: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
    val level: String,
)

