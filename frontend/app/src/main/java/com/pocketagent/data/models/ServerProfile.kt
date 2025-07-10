package com.pocketagent.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a server profile for connecting to remote development environments.
 * 
 * Server profiles define connection endpoints and authentication details for
 * accessing remote servers where Claude Code instances run. Each profile is
 * associated with a specific SSH identity for authentication.
 * 
 * Entity Relationship: SSH Identity (1) → (N) Server Profile → (N) Project
 * 
 * @property id Unique identifier for the server profile
 * @property name User-friendly name for the server (max 100 characters)
 * @property hostname Server hostname or IP address
 * @property port SSH port number (default: 22, valid range: 1-65535)
 * @property username SSH username for authentication
 * @property sshIdentityId Reference to the SSH identity for authentication
 * @property wrapperPort Port for the Claude Code wrapper service (default: 8080)
 * @property lastConnectedAt Timestamp of last successful connection
 * @property status Current connection status
 * @property createdAt Timestamp when the profile was created
 */
@Serializable
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
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Server profile name cannot be blank" }
        require(name.length <= 100) { "Server profile name too long (max 100 chars)" }
        require(hostname.isNotBlank()) { "Hostname cannot be blank" }
        require(hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))) { 
            "Invalid hostname format. Only alphanumeric characters, dots, and hyphens allowed" 
        }
        require(port in 1..65535) { "SSH port must be between 1 and 65535" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(username.matches(Regex("^[a-zA-Z0-9_-]+$"))) { 
            "Invalid username format. Only alphanumeric characters, underscores, and hyphens allowed" 
        }
        require(sshIdentityId.isNotBlank()) { "SSH identity ID cannot be blank" }
        require(wrapperPort in 1..65535) { "Wrapper port must be between 1 and 65535" }
        require(port != wrapperPort) { "SSH port and wrapper port cannot be the same" }
        require(createdAt > 0) { "Created timestamp must be positive" }
        require(lastConnectedAt == null || lastConnectedAt > 0) { 
            "Last connected timestamp must be positive if provided" 
        }
    }
    
    /**
     * Check if this server was connected recently (within the last 24 hours).
     */
    fun isRecentlyConnected(): Boolean {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return lastConnectedAt != null && lastConnectedAt > twentyFourHoursAgo
    }
    
    /**
     * Get the full connection string for display.
     */
    fun getConnectionString(): String = "$username@$hostname:$port"
    
    /**
     * Get the wrapper service URL.
     */
    fun getWrapperUrl(): String = "http://$hostname:$wrapperPort"
    
    /**
     * Check if the server is currently connected.
     */
    fun isConnected(): Boolean = status == ConnectionStatus.CONNECTED
    
    /**
     * Check if the server is in an error state.
     */
    fun hasError(): Boolean = status == ConnectionStatus.ERROR
    
    /**
     * Get the display name for the server.
     */
    fun getDisplayName(): String = name
}

/**
 * Represents the connection status of a server profile.
 * 
 * The connection status tracks the current state of the connection to the
 * remote server and helps the UI provide appropriate feedback to users.
 */
@Serializable
enum class ConnectionStatus {
    /** Never attempted connection */
    NEVER_CONNECTED,
    /** Previously connected but currently disconnected */
    DISCONNECTED,
    /** Currently attempting to connect */
    CONNECTING,
    /** Successfully connected and ready */
    CONNECTED,
    /** Connection failed or encountered an error */
    ERROR;
    
    /**
     * Check if this status represents an active connection attempt.
     */
    fun isConnecting(): Boolean = this == CONNECTING
    
    /**
     * Check if this status represents a successful connection.
     */
    fun isConnected(): Boolean = this == CONNECTED
    
    /**
     * Check if this status represents an error state.
     */
    fun isError(): Boolean = this == ERROR
    
    /**
     * Get a user-friendly description of the status.
     */
    fun getDescription(): String = when (this) {
        NEVER_CONNECTED -> "Never connected"
        DISCONNECTED -> "Disconnected"
        CONNECTING -> "Connecting..."
        CONNECTED -> "Connected"
        ERROR -> "Connection error"
    }
}

/**
 * Builder class for creating ServerProfile instances in tests.
 * 
 * This builder provides a fluent interface for constructing ServerProfile objects
 * with specific configurations for testing scenarios.
 */
class ServerProfileBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = "Test Server"
    private var hostname: String = "test.example.com"
    private var port: Int = 22
    private var username: String = "testuser"
    private var sshIdentityId: String = UUID.randomUUID().toString()
    private var wrapperPort: Int = 8080
    private var lastConnectedAt: Long? = null
    private var status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED
    private var createdAt: Long = System.currentTimeMillis()

    fun id(id: String) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun hostname(hostname: String) = apply { this.hostname = hostname }
    fun port(port: Int) = apply { this.port = port }
    fun username(username: String) = apply { this.username = username }
    fun sshIdentityId(sshIdentityId: String) = apply { this.sshIdentityId = sshIdentityId }
    fun wrapperPort(wrapperPort: Int) = apply { this.wrapperPort = wrapperPort }
    fun lastConnectedAt(timestamp: Long?) = apply { this.lastConnectedAt = timestamp }
    fun status(status: ConnectionStatus) = apply { this.status = status }
    fun createdAt(timestamp: Long) = apply { this.createdAt = timestamp }
    
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
        createdAt = createdAt
    )
}

/**
 * Extension functions for ServerProfile operations.
 */

/**
 * Update the connection status.
 */
fun ServerProfile.withStatus(status: ConnectionStatus): ServerProfile = 
    copy(status = status)

/**
 * Mark as connected with current timestamp.
 */
fun ServerProfile.markAsConnected(): ServerProfile = 
    copy(
        status = ConnectionStatus.CONNECTED,
        lastConnectedAt = System.currentTimeMillis()
    )

/**
 * Mark as disconnected.
 */
fun ServerProfile.markAsDisconnected(): ServerProfile = 
    copy(status = ConnectionStatus.DISCONNECTED)

/**
 * Mark as connecting.
 */
fun ServerProfile.markAsConnecting(): ServerProfile = 
    copy(status = ConnectionStatus.CONNECTING)

/**
 * Mark as error state.
 */
fun ServerProfile.markAsError(): ServerProfile = 
    copy(status = ConnectionStatus.ERROR)

/**
 * Check if the server profile matches the search query.
 */
fun ServerProfile.matchesSearch(query: String): Boolean = 
    name.contains(query, ignoreCase = true) || 
    hostname.contains(query, ignoreCase = true) ||
    username.contains(query, ignoreCase = true)

/**
 * Get the age of the server profile in days.
 */
fun ServerProfile.getAgeInDays(): Long {
    val now = System.currentTimeMillis()
    val dayInMillis = 24 * 60 * 60 * 1000
    return (now - createdAt) / dayInMillis
}

/**
 * Get the days since last connection.
 */
fun ServerProfile.getDaysSinceLastConnection(): Long? {
    if (lastConnectedAt == null) return null
    val now = System.currentTimeMillis()
    val dayInMillis = 24 * 60 * 60 * 1000
    return (now - lastConnectedAt) / dayInMillis
}

/**
 * Create a copy for export (with minimal data).
 */
fun ServerProfile.toExportModel(): ServerProfileExport = ServerProfileExport(
    id = id,
    name = name,
    hostname = hostname,
    port = port,
    username = username,
    sshIdentityId = sshIdentityId,
    wrapperPort = wrapperPort,
    createdAt = createdAt
)

/**
 * Export model for Server Profile (without status information).
 */
@Serializable
data class ServerProfileExport(
    val id: String,
    val name: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val sshIdentityId: String,
    val wrapperPort: Int,
    val createdAt: Long
)

/**
 * Validation utilities for Server Profile.
 */
object ServerProfileValidator {
    private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
    private val VALID_HOSTNAME_REGEX = Regex("^[a-zA-Z0-9.-]+$")
    private val VALID_USERNAME_REGEX = Regex("^[a-zA-Z0-9_-]+$")
    
    /**
     * Validate server profile name.
     */
    fun validateName(name: String): Result<Unit> {
        return when {
            name.isBlank() -> Result.failure(IllegalArgumentException("Name cannot be blank"))
            name.length > 100 -> Result.failure(IllegalArgumentException("Name too long (max 100 chars)"))
            !VALID_NAME_REGEX.matches(name) -> Result.failure(IllegalArgumentException("Name contains invalid characters"))
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate hostname format.
     */
    fun validateHostname(hostname: String): Result<Unit> {
        return when {
            hostname.isBlank() -> Result.failure(IllegalArgumentException("Hostname cannot be blank"))
            !VALID_HOSTNAME_REGEX.matches(hostname) -> Result.failure(IllegalArgumentException("Invalid hostname format"))
            hostname.length > 255 -> Result.failure(IllegalArgumentException("Hostname too long (max 255 chars)"))
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate username format.
     */
    fun validateUsername(username: String): Result<Unit> {
        return when {
            username.isBlank() -> Result.failure(IllegalArgumentException("Username cannot be blank"))
            !VALID_USERNAME_REGEX.matches(username) -> Result.failure(IllegalArgumentException("Invalid username format"))
            username.length > 32 -> Result.failure(IllegalArgumentException("Username too long (max 32 chars)"))
            else -> Result.success(Unit)
        }
    }
    
    /**
     * Validate port number.
     */
    fun validatePort(port: Int): Result<Unit> {
        return when {
            port < 1 || port > 65535 -> Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
            else -> Result.success(Unit)
        }
    }
}

/**
 * Common server profile factory methods.
 */
object ServerProfileFactory {
    /**
     * Create a sample server profile for testing.
     */
    fun createSample(
        name: String = "Sample Server",
        sshIdentityId: String = UUID.randomUUID().toString()
    ): ServerProfile = ServerProfileBuilder()
        .name(name)
        .hostname("sample.example.com")
        .username("sampleuser")
        .sshIdentityId(sshIdentityId)
        .build()
    
    /**
     * Create multiple sample server profiles.
     */
    fun createSamples(count: Int, sshIdentityId: String): List<ServerProfile> = 
        (1..count).map { index ->
            createSample("Sample Server $index", sshIdentityId)
        }
    
    /**
     * Create a localhost server profile for development.
     */
    fun createLocalhost(
        name: String = "Localhost",
        sshIdentityId: String = UUID.randomUUID().toString()
    ): ServerProfile = ServerProfileBuilder()
        .name(name)
        .hostname("localhost")
        .username("developer")
        .sshIdentityId(sshIdentityId)
        .build()
}