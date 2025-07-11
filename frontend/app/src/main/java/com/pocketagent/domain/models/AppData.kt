package com.pocketagent.domain.models

import com.pocketagent.domain.models.entities.*
import com.pocketagent.domain.models.error.ValidationException
import java.util.UUID

/**
 * Root data model containing all app entities.
 * 
 * This is the main data structure that gets serialized and encrypted for storage.
 * It contains all SSH identities, server profiles, projects, and messages.
 * 
 * @property version Data schema version for migration purposes
 * @property sshIdentities List of SSH identities
 * @property serverProfiles List of server profiles
 * @property projects List of projects
 * @property messages Map of project ID to messages
 * @property lastModified Timestamp of last modification
 * @property metadata App metadata
 */
data class AppData(
    val version: Int = 1,
    val sshIdentities: List<SshIdentity> = emptyList(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val projects: List<Project> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap(), // projectId -> messages
    val lastModified: Long = System.currentTimeMillis(),
    val metadata: AppMetadata = AppMetadata()
) {
    init {
        validateData()
    }
    
    /**
     * Gets all SSH identities sorted by name.
     */
    fun getSshIdentitiesSorted(): List<SshIdentity> = sshIdentities.sortedBy { it.name }
    
    /**
     * Gets all server profiles sorted by name.
     */
    fun getServerProfilesSorted(): List<ServerProfile> = serverProfiles.sortedBy { it.name }
    
    /**
     * Gets all projects sorted by last active time.
     */
    fun getProjectsSorted(): List<Project> = projects.sortedByDescending { it.lastActiveAt ?: it.createdAt }
    
    /**
     * Gets active projects.
     */
    fun getActiveProjects(): List<Project> = projects.filter { it.isActive }
    
    /**
     * Gets connected projects.
     */
    fun getConnectedProjects(): List<Project> = projects.filter { it.isConnected() }
    
    /**
     * Gets SSH identity by ID.
     */
    fun getSshIdentityById(id: String): SshIdentity? = sshIdentities.find { it.id == id }
    
    /**
     * Gets server profile by ID.
     */
    fun getServerProfileById(id: String): ServerProfile? = serverProfiles.find { it.id == id }
    
    /**
     * Gets project by ID.
     */
    fun getProjectById(id: String): Project? = projects.find { it.id == id }
    
    /**
     * Gets projects for a specific server profile.
     */
    fun getProjectsForServer(serverProfileId: String): List<Project> = 
        projects.filter { it.serverProfileId == serverProfileId }
    
    /**
     * Gets server profiles for a specific SSH identity.
     */
    fun getServerProfilesForIdentity(identityId: String): List<ServerProfile> = 
        serverProfiles.filter { it.sshIdentityId == identityId }
    
    /**
     * Gets messages for a specific project.
     */
    fun getMessagesForProject(projectId: String): List<Message> = 
        messages[projectId] ?: emptyList()
    
    /**
     * Gets recent messages for a project.
     */
    fun getRecentMessagesForProject(projectId: String, limit: Int = 100): List<Message> = 
        (messages[projectId] ?: emptyList()).takeLast(limit)
    
    /**
     * Gets total message count.
     */
    fun getTotalMessageCount(): Int = messages.values.sumOf { it.size }
    
    /**
     * Gets total count of all entities.
     */
    fun getTotalEntityCount(): Int = 
        sshIdentities.size + serverProfiles.size + projects.size + getTotalMessageCount()
    
    /**
     * Gets total storage size estimate in bytes.
     */
    fun getEstimatedStorageSize(): Long {
        val baseSize = toString().toByteArray().size.toLong()
        val messagesSize = messages.values.flatten().sumOf { it.getSize() }
        return baseSize + messagesSize
    }
    
    /**
     * Checks if an SSH identity is in use.
     */
    fun isSshIdentityInUse(identityId: String): Boolean = 
        serverProfiles.any { it.sshIdentityId == identityId }
    
    /**
     * Checks if a server profile is in use.
     */
    fun isServerProfileInUse(serverProfileId: String): Boolean = 
        projects.any { it.serverProfileId == serverProfileId }
    
    /**
     * Validates the data integrity.
     */
    private fun validateData() {
        // Check for duplicate names
        val identityNames = sshIdentities.map { it.name }
        if (identityNames.size != identityNames.toSet().size) {
            throw ValidationException("sshIdentities", identityNames, "Duplicate SSH identity names found")
        }
        
        val serverNames = serverProfiles.map { it.name }
        if (serverNames.size != serverNames.toSet().size) {
            throw ValidationException("serverProfiles", serverNames, "Duplicate server profile names found")
        }
        
        val projectNames = projects.map { it.name }
        if (projectNames.size != projectNames.toSet().size) {
            throw ValidationException("projects", projectNames, "Duplicate project names found")
        }
        
        // Validate relationships
        val identityIds = sshIdentities.map { it.id }.toSet()
        serverProfiles.forEach { server ->
            if (server.sshIdentityId !in identityIds) {
                throw ValidationException("serverProfiles", server.name, "Server '${server.name}' references non-existent SSH identity")
            }
        }
        
        val serverIds = serverProfiles.map { it.id }.toSet()
        projects.forEach { project ->
            if (project.serverProfileId !in serverIds) {
                throw ValidationException("projects", project.name, "Project '${project.name}' references non-existent server profile")
            }
        }
        
        // Validate message project references
        val projectIds = projects.map { it.id }.toSet()
        messages.keys.forEach { projectId ->
            if (projectId !in projectIds) {
                throw ValidationException("messages", projectId, "Messages exist for non-existent project")
            }
        }
    }
    
    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_MESSAGES_PER_PROJECT = 1000
        
        /**
         * Creates empty app data.
         */
        fun empty(): AppData = AppData()
        
        /**
         * Creates app data with initial setup.
         */
        fun withInitialSetup(deviceId: String? = null): AppData = AppData(
            metadata = AppMetadata(
                deviceId = deviceId ?: UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

/**
 * Application metadata.
 * 
 * @property createdAt Timestamp when the app data was created
 * @property deviceId Unique device identifier
 * @property backupEnabled Whether backup is enabled
 * @property appVersion App version when data was created
 * @property dataSchemaVersion Data schema version
 * @property lastBackupAt Timestamp of last backup
 * @property deviceInfo Device information
 * @property preferences User preferences
 */
data class AppMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = UUID.randomUUID().toString(),
    val backupEnabled: Boolean = true,
    val appVersion: String = "1.0.0",
    val dataSchemaVersion: Int = 1,
    val lastBackupAt: Long? = null,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val preferences: UserPreferences = UserPreferences()
)

/**
 * Device information.
 * 
 * @property manufacturer Device manufacturer
 * @property model Device model
 * @property osVersion Android OS version
 * @property apiLevel Android API level
 * @property screenSize Screen size category
 * @property totalMemory Total device memory in MB
 * @property availableStorage Available storage in MB
 */
data class DeviceInfo(
    val manufacturer: String = "Unknown",
    val model: String = "Unknown",
    val osVersion: String = "Unknown",
    val apiLevel: Int = 0,
    val screenSize: ScreenSize = ScreenSize.NORMAL,
    val totalMemory: Long = 0,
    val availableStorage: Long = 0
)

/**
 * Screen size categories.
 */
enum class ScreenSize {
    SMALL,
    NORMAL,
    LARGE,
    XLARGE
}

/**
 * User preferences.
 * 
 * @property theme App theme preference
 * @property language App language preference
 * @property notificationsEnabled Whether notifications are enabled
 * @property biometricEnabled Whether biometric authentication is enabled
 * @property autoBackup Whether auto backup is enabled
 * @property batteryOptimization Battery optimization preferences
 * @property networkPreferences Network preferences
 * @property debugMode Whether debug mode is enabled
 */
data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val autoBackup: Boolean = true,
    val batteryOptimization: BatteryOptimization = BatteryOptimization(),
    val networkPreferences: NetworkPreferences = NetworkPreferences(),
    val debugMode: Boolean = false
)

/**
 * App theme preferences.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Battery optimization preferences.
 * 
 * @property adaptivePolling Whether to adapt polling based on battery level
 * @property lowBatteryThreshold Battery level threshold for low battery mode
 * @property backgroundSyncEnabled Whether background sync is enabled
 * @property powerSaveMode Whether power save mode is enabled
 */
data class BatteryOptimization(
    val adaptivePolling: Boolean = true,
    val lowBatteryThreshold: Int = 20,
    val backgroundSyncEnabled: Boolean = true,
    val powerSaveMode: Boolean = false
)

/**
 * Network preferences.
 * 
 * @property wifiPreferred Whether to prefer WiFi over cellular
 * @property cellularDataEnabled Whether cellular data is enabled
 * @property roamingEnabled Whether roaming is enabled
 * @property compressionEnabled Whether compression is enabled
 * @property timeoutSettings Network timeout settings
 */
data class NetworkPreferences(
    val wifiPreferred: Boolean = true,
    val cellularDataEnabled: Boolean = true,
    val roamingEnabled: Boolean = false,
    val compressionEnabled: Boolean = true,
    val timeoutSettings: NetworkTimeoutSettings = NetworkTimeoutSettings()
)

/**
 * Network timeout settings.
 * 
 * @property connectionTimeout Connection timeout in milliseconds
 * @property readTimeout Read timeout in milliseconds
 * @property writeTimeout Write timeout in milliseconds
 */
data class NetworkTimeoutSettings(
    val connectionTimeout: Long = 30_000, // 30 seconds
    val readTimeout: Long = 60_000, // 1 minute
    val writeTimeout: Long = 60_000 // 1 minute
)

/**
 * Builder class for creating app data.
 */
class AppDataBuilder {
    private var version: Int = 1
    private var sshIdentities: List<SshIdentity> = emptyList()
    private var serverProfiles: List<ServerProfile> = emptyList()
    private var projects: List<Project> = emptyList()
    private var messages: Map<String, List<Message>> = emptyMap()
    private var lastModified: Long = System.currentTimeMillis()
    private var metadata: AppMetadata = AppMetadata()
    
    fun version(version: Int) = apply { this.version = version }
    fun sshIdentities(identities: List<SshIdentity>) = apply { this.sshIdentities = identities }
    fun serverProfiles(profiles: List<ServerProfile>) = apply { this.serverProfiles = profiles }
    fun projects(projects: List<Project>) = apply { this.projects = projects }
    fun messages(messages: Map<String, List<Message>>) = apply { this.messages = messages }
    fun lastModified(timestamp: Long) = apply { this.lastModified = timestamp }
    fun metadata(metadata: AppMetadata) = apply { this.metadata = metadata }
    
    fun addSshIdentity(identity: SshIdentity) = apply { 
        this.sshIdentities = this.sshIdentities + identity 
    }
    
    fun addServerProfile(profile: ServerProfile) = apply { 
        this.serverProfiles = this.serverProfiles + profile 
    }
    
    fun addProject(project: Project) = apply { 
        this.projects = this.projects + project 
    }
    
    fun addMessage(projectId: String, message: Message) = apply {
        val currentMessages = this.messages[projectId] ?: emptyList()
        this.messages = this.messages + (projectId to (currentMessages + message))
    }
    
    fun build(): AppData = AppData(
        version = version,
        sshIdentities = sshIdentities,
        serverProfiles = serverProfiles,
        projects = projects,
        messages = messages,
        lastModified = lastModified,
        metadata = metadata
    )
}

/**
 * Extension functions for AppData.
 */
fun AppData.toBuilder(): AppDataBuilder = AppDataBuilder()
    .version(version)
    .sshIdentities(sshIdentities)
    .serverProfiles(serverProfiles)
    .projects(projects)
    .messages(messages)
    .lastModified(lastModified)
    .metadata(metadata)

/**
 * Creates a new app data builder.
 */
fun appData(block: AppDataBuilder.() -> Unit): AppData = 
    AppDataBuilder().apply(block).build()