package com.pocketagent.domain.models.entities

import com.pocketagent.domain.models.error.ValidationException
import java.util.UUID

/**
 * Represents a server profile for connecting to remote development environments.
 * 
 * Server profiles define connection endpoints and authentication details for
 * accessing remote servers where Claude Code instances run.
 * 
 * @property id Unique identifier for the server profile
 * @property name User-friendly name for the server
 * @property hostname Server hostname or IP address
 * @property port SSH port number (default: 22)
 * @property username SSH username for authentication
 * @property sshIdentityId Reference to the SSH identity for authentication
 * @property wrapperPort Port for the wrapper service (default: 8080)
 * @property lastConnectedAt Timestamp of last successful connection
 * @property status Current connection status
 * @property createdAt Timestamp when the profile was created
 * @property isActive Whether the server profile is active
 * @property description Optional description for the server
 * @property connectionSettings Additional connection settings
 * @property metadata Additional metadata for the server profile
 */
data class ServerProfile(
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
    val isActive: Boolean = true,
    val description: String? = null,
    val connectionSettings: ServerConnectionSettings = ServerConnectionSettings(),
    val metadata: ServerProfileMetadata = ServerProfileMetadata()
) {
    init {
        validateName(name)
        validateHostname(hostname)
        validatePort(port)
        validateWrapperPort(wrapperPort)
        validateUsername(username)
    }
    
    /**
     * Updates the connection status.
     */
    fun updateStatus(newStatus: ConnectionStatus): ServerProfile = copy(status = newStatus)
    
    /**
     * Marks the server as connected.
     */
    fun markAsConnected(): ServerProfile = copy(
        status = ConnectionStatus.CONNECTED,
        lastConnectedAt = System.currentTimeMillis()
    )
    
    /**
     * Marks the server as disconnected.
     */
    fun markAsDisconnected(): ServerProfile = copy(status = ConnectionStatus.DISCONNECTED)
    
    /**
     * Marks the server as having an error.
     */
    fun markAsError(): ServerProfile = copy(status = ConnectionStatus.ERROR)
    
    /**
     * Deactivates the server profile.
     */
    fun deactivate(): ServerProfile = copy(isActive = false)
    
    /**
     * Reactivates the server profile.
     */
    fun reactivate(): ServerProfile = copy(isActive = true)
    
    /**
     * Updates the description.
     */
    fun updateDescription(newDescription: String?): ServerProfile = copy(description = newDescription)
    
    /**
     * Gets the display name for the server.
     */
    fun getDisplayName(): String = name.ifBlank { "Server $id" }
    
    /**
     * Gets the connection string for display.
     */
    fun getConnectionString(): String = "$username@$hostname:$port"
    
    /**
     * Checks if the server is currently connected.
     */
    fun isConnected(): Boolean = status == ConnectionStatus.CONNECTED
    
    /**
     * Checks if the server is in an error state.
     */
    fun hasError(): Boolean = status == ConnectionStatus.ERROR
    
    /**
     * Gets the wrapper service URL.
     */
    fun getWrapperUrl(): String = "https://$hostname:$wrapperPort"
    
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MIN_NAME_LENGTH = 1
        const val MAX_HOSTNAME_LENGTH = 255
        const val MAX_USERNAME_LENGTH = 32
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        
        private fun validateName(name: String) {
            if (name.isBlank()) {
                throw ValidationException("name", name, "Server profile name cannot be blank")
            }
            if (name.length > MAX_NAME_LENGTH) {
                throw ValidationException("name", name, "Server profile name too long (max $MAX_NAME_LENGTH chars)")
            }
            if (name.length < MIN_NAME_LENGTH) {
                throw ValidationException("name", name, "Server profile name too short (min $MIN_NAME_LENGTH chars)")
            }
        }
        
        private fun validateHostname(hostname: String) {
            if (hostname.isBlank()) {
                throw ValidationException("hostname", hostname, "Hostname cannot be blank")
            }
            if (hostname.length > MAX_HOSTNAME_LENGTH) {
                throw ValidationException("hostname", hostname, "Hostname too long (max $MAX_HOSTNAME_LENGTH chars)")
            }
            val hostnameRegex = Regex("^[a-zA-Z0-9.-]+$")
            if (!hostname.matches(hostnameRegex)) {
                throw ValidationException("hostname", hostname, "Invalid hostname format")
            }
        }
        
        private fun validatePort(port: Int) {
            if (port !in MIN_PORT..MAX_PORT) {
                throw ValidationException("port", port, "Port must be between $MIN_PORT and $MAX_PORT")
            }
        }
        
        private fun validateWrapperPort(wrapperPort: Int) {
            if (wrapperPort !in MIN_PORT..MAX_PORT) {
                throw ValidationException("wrapperPort", wrapperPort, "Wrapper port must be between $MIN_PORT and $MAX_PORT")
            }
        }
        
        private fun validateUsername(username: String) {
            if (username.isBlank()) {
                throw ValidationException("username", username, "Username cannot be blank")
            }
            if (username.length > MAX_USERNAME_LENGTH) {
                throw ValidationException("username", username, "Username too long (max $MAX_USERNAME_LENGTH chars)")
            }
            val usernameRegex = Regex("^[a-zA-Z0-9_-]+$")
            if (!username.matches(usernameRegex)) {
                throw ValidationException("username", username, "Invalid username format")
            }
        }
    }
}

/**
 * Represents the connection status of a server profile.
 */
enum class ConnectionStatus {
    NEVER_CONNECTED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * Connection settings for a server profile.
 * 
 * @property connectionTimeout Timeout for SSH connections in milliseconds
 * @property keepAliveInterval Keep-alive interval in milliseconds
 * @property maxRetries Maximum number of connection retries
 * @property useCompression Whether to use SSH compression
 * @property strictHostKeyChecking Whether to enforce strict host key checking
 */
data class ServerConnectionSettings(
    val connectionTimeout: Long = 30_000, // 30 seconds
    val keepAliveInterval: Long = 60_000, // 1 minute
    val maxRetries: Int = 3,
    val useCompression: Boolean = true,
    val strictHostKeyChecking: Boolean = true
)

/**
 * Metadata associated with a server profile.
 * 
 * @property tags User-defined tags for organizing servers
 * @property environment Environment type (dev, staging, prod)
 * @property region Server region or location
 * @property capabilities Server capabilities
 * @property osType Operating system type
 * @property notes Additional notes about the server
 * @property lastError Last error message if any
 */
data class ServerProfileMetadata(
    val tags: List<String> = emptyList(),
    val environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
    val region: String? = null,
    val capabilities: List<String> = emptyList(),
    val osType: OsType = OsType.LINUX,
    val notes: String? = null,
    val lastError: String? = null
)

/**
 * Enum representing server environments.
 */
enum class ServerEnvironment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    TESTING
}

/**
 * Enum representing operating system types.
 */
enum class OsType {
    LINUX,
    MACOS,
    WINDOWS,
    UNKNOWN
}

/**
 * Builder class for creating server profiles with validation.
 */
class ServerProfileBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = ""
    private var hostname: String = ""
    private var port: Int = 22
    private var username: String = ""
    private var sshIdentityId: String = ""
    private var wrapperPort: Int = 8080
    private var lastConnectedAt: Long? = null
    private var status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED
    private var createdAt: Long = System.currentTimeMillis()
    private var isActive: Boolean = true
    private var description: String? = null
    private var connectionSettings: ServerConnectionSettings = ServerConnectionSettings()
    private var metadata: ServerProfileMetadata = ServerProfileMetadata()
    
    fun id(id: String) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun hostname(hostname: String) = apply { this.hostname = hostname }
    fun port(port: Int) = apply { this.port = port }
    fun username(username: String) = apply { this.username = username }
    fun sshIdentityId(sshIdentityId: String) = apply { this.sshIdentityId = sshIdentityId }
    fun wrapperPort(wrapperPort: Int) = apply { this.wrapperPort = wrapperPort }
    fun lastConnectedAt(lastConnectedAt: Long?) = apply { this.lastConnectedAt = lastConnectedAt }
    fun status(status: ConnectionStatus) = apply { this.status = status }
    fun createdAt(createdAt: Long) = apply { this.createdAt = createdAt }
    fun isActive(isActive: Boolean) = apply { this.isActive = isActive }
    fun description(description: String?) = apply { this.description = description }
    fun connectionSettings(settings: ServerConnectionSettings) = apply { this.connectionSettings = settings }
    fun metadata(metadata: ServerProfileMetadata) = apply { this.metadata = metadata }
    
    fun build(): ServerProfile = ServerProfile(
        id = id,
        name = name,
        hostname = hostname,
        port = port,
        username = username,
        sshIdentityId = sshIdentityId,
        wrapperPort = wrapperPort,
        lastConnectedAt = lastConnectedAt,
        status = status,
        createdAt = createdAt,
        isActive = isActive,
        description = description,
        connectionSettings = connectionSettings,
        metadata = metadata
    )
}

/**
 * Extension functions for ServerProfile.
 */
fun ServerProfile.toBuilder(): ServerProfileBuilder = ServerProfileBuilder()
    .id(id)
    .name(name)
    .hostname(hostname)
    .port(port)
    .username(username)
    .sshIdentityId(sshIdentityId)
    .wrapperPort(wrapperPort)
    .lastConnectedAt(lastConnectedAt)
    .status(status)
    .createdAt(createdAt)
    .isActive(isActive)
    .description(description)
    .connectionSettings(connectionSettings)
    .metadata(metadata)

/**
 * Creates a new server profile builder.
 */
fun serverProfile(block: ServerProfileBuilder.() -> Unit): ServerProfile = 
    ServerProfileBuilder().apply(block).build()