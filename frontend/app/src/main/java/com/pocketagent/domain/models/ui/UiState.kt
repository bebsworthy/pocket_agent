package com.pocketagent.domain.models.ui

import com.pocketagent.domain.models.entities.*
import com.pocketagent.domain.models.responses.PermissionRequest
import com.pocketagent.domain.models.responses.ServerEvent
import com.pocketagent.domain.models.UserPreferences
import com.pocketagent.domain.models.DeviceInfo

/**
 * Base class for all UI state models.
 */
sealed class UiState {
    abstract val isLoading: Boolean
    abstract val error: String?
}

/**
 * UI state for the projects list screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property projects List of projects
 * @property selectedProject Currently selected project
 * @property searchQuery Current search query
 * @property filterType Filter type for projects
 * @property sortOrder Sort order for projects
 * @property isRefreshing Whether the list is being refreshed
 */
data class ProjectsUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val projects: List<Project> = emptyList(),
    val selectedProject: Project? = null,
    val searchQuery: String = "",
    val filterType: ProjectFilterType = ProjectFilterType.ALL,
    val sortOrder: ProjectSortOrder = ProjectSortOrder.LAST_ACTIVE,
    val isRefreshing: Boolean = false
) : UiState()

/**
 * UI state for the project details screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property project Current project
 * @property connectionStatus Connection status
 * @property serverProfile Associated server profile
 * @property sshIdentity Associated SSH identity
 * @property recentMessages Recent messages
 * @property quickActions Available quick actions
 * @property isConnecting Whether currently connecting
 * @property isDisconnecting Whether currently disconnecting
 */
data class ProjectDetailsUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val project: Project? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED,
    val serverProfile: ServerProfile? = null,
    val sshIdentity: SshIdentity? = null,
    val recentMessages: List<Message> = emptyList(),
    val quickActions: List<QuickAction> = emptyList(),
    val isConnecting: Boolean = false,
    val isDisconnecting: Boolean = false
) : UiState()

/**
 * UI state for the chat screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property messages List of chat messages
 * @property inputText Current input text
 * @property isTyping Whether the user is typing
 * @property isClaudeTyping Whether Claude is typing
 * @property pendingMessage Pending message being sent
 * @property attachments Current attachments
 * @property permissionRequest Current permission request
 * @property canSendMessage Whether a message can be sent
 * @property isConnected Whether connected to project
 */
data class ChatUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val isClaudeTyping: Boolean = false,
    val pendingMessage: Message? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val permissionRequest: PermissionRequest? = null,
    val canSendMessage: Boolean = false,
    val isConnected: Boolean = false
) : UiState()

/**
 * UI state for the files screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property currentPath Current directory path
 * @property files List of files in current directory
 * @property selectedFiles List of selected files
 * @property gitStatus Git status information
 * @property isGitRepository Whether current directory is a git repository
 * @property breadcrumbs Breadcrumb navigation
 * @property sortOrder Sort order for files
 * @property showHidden Whether to show hidden files
 */
data class FilesUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val selectedFiles: List<FileItem> = emptyList(),
    val gitStatus: GitStatus? = null,
    val isGitRepository: Boolean = false,
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val sortOrder: FileSortOrder = FileSortOrder.NAME,
    val showHidden: Boolean = false
) : UiState()

/**
 * UI state for the server profiles screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property serverProfiles List of server profiles
 * @property selectedProfile Currently selected profile
 * @property testingConnection Profile being tested
 * @property connectionResults Connection test results
 * @property isCreatingProfile Whether creating a new profile
 * @property isEditingProfile Whether editing an existing profile
 */
data class ServerProfilesUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val serverProfiles: List<ServerProfile> = emptyList(),
    val selectedProfile: ServerProfile? = null,
    val testingConnection: ServerProfile? = null,
    val connectionResults: Map<String, ConnectionTestResult> = emptyMap(),
    val isCreatingProfile: Boolean = false,
    val isEditingProfile: Boolean = false
) : UiState()

/**
 * UI state for the SSH identities screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property sshIdentities List of SSH identities
 * @property selectedIdentity Currently selected identity
 * @property isImporting Whether importing a new identity
 * @property importProgress Import progress
 * @property usageMap Usage map for identities
 */
data class SshIdentitiesUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val sshIdentities: List<SshIdentity> = emptyList(),
    val selectedIdentity: SshIdentity? = null,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val usageMap: Map<String, Int> = emptyMap()
) : UiState()

/**
 * UI state for the settings screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property preferences User preferences
 * @property deviceInfo Device information
 * @property storageInfo Storage information
 * @property isSaving Whether saving preferences
 * @property isExporting Whether exporting data
 * @property isImporting Whether importing data
 */
data class SettingsUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val preferences: UserPreferences = UserPreferences(),
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val storageInfo: StorageInfo = StorageInfo(),
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false
) : UiState()

/**
 * UI state for the dashboard screen.
 * 
 * @property isLoading Whether the screen is loading
 * @property error Error message if any
 * @property activeProjects List of active projects
 * @property recentActivity Recent activity items
 * @property notifications List of notifications
 * @property systemStatus System status information
 * @property quickActions Available quick actions
 */
data class DashboardUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    val activeProjects: List<Project> = emptyList(),
    val recentActivity: List<ActivityItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val systemStatus: SystemStatus = SystemStatus(),
    val quickActions: List<QuickAction> = emptyList()
) : UiState()

/**
 * Project filter types.
 */
enum class ProjectFilterType {
    ALL,
    ACTIVE,
    INACTIVE,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

/**
 * Project sort orders.
 */
enum class ProjectSortOrder {
    NAME,
    LAST_ACTIVE,
    CREATED,
    STATUS
}

/**
 * File sort orders.
 */
enum class FileSortOrder {
    NAME,
    SIZE,
    MODIFIED,
    TYPE
}

/**
 * Quick action model.
 * 
 * @property id Action ID
 * @property name Action name
 * @property description Action description
 * @property icon Action icon
 * @property type Action type
 * @property enabled Whether the action is enabled
 * @property data Additional action data
 */
data class QuickAction(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val type: QuickActionType,
    val enabled: Boolean = true,
    val data: Map<String, String> = emptyMap()
)

/**
 * Quick action types.
 */
enum class QuickActionType {
    CLAUDE_PROMPT,
    SHELL_COMMAND,
    SCRIPT,
    FILE_OPERATION,
    GIT_OPERATION
}

/**
 * File item model for UI.
 * 
 * @property name File name
 * @property path Full file path
 * @property size File size in bytes
 * @property isDirectory Whether this is a directory
 * @property lastModified Last modified timestamp
 * @property permissions File permissions
 * @property gitStatus Git status for this file
 * @property icon File icon
 * @property mimeType MIME type
 */
data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String? = null,
    val gitStatus: FileGitStatus = FileGitStatus.UNTRACKED,
    val icon: String = "",
    val mimeType: String? = null
)

/**
 * Git status for individual files.
 */
enum class FileGitStatus {
    UNTRACKED,
    MODIFIED,
    STAGED,
    DELETED,
    ADDED,
    RENAMED,
    COPIED,
    CONFLICTED,
    IGNORED
}

/**
 * Breadcrumb navigation item.
 * 
 * @property name Display name
 * @property path Full path
 * @property isLast Whether this is the last item
 */
data class Breadcrumb(
    val name: String,
    val path: String,
    val isLast: Boolean = false
)

/**
 * Connection test result.
 * 
 * @property success Whether the test was successful
 * @property latency Connection latency in milliseconds
 * @property errorMessage Error message if failed
 * @property timestamp Test timestamp
 */
data class ConnectionTestResult(
    val success: Boolean,
    val latency: Long? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Import progress information.
 * 
 * @property step Current step
 * @property totalSteps Total number of steps
 * @property progress Progress percentage
 * @property message Current progress message
 */
data class ImportProgress(
    val step: Int,
    val totalSteps: Int,
    val progress: Float,
    val message: String
)

/**
 * Storage information.
 * 
 * @property totalSpace Total storage space in bytes
 * @property usedSpace Used storage space in bytes
 * @property freeSpace Free storage space in bytes
 * @property appSize App size in bytes
 * @property dataSize Data size in bytes
 * @property cacheSize Cache size in bytes
 */
data class StorageInfo(
    val totalSpace: Long = 0,
    val usedSpace: Long = 0,
    val freeSpace: Long = 0,
    val appSize: Long = 0,
    val dataSize: Long = 0,
    val cacheSize: Long = 0
)

/**
 * Activity item for dashboard.
 * 
 * @property id Activity ID
 * @property type Activity type
 * @property title Activity title
 * @property description Activity description
 * @property timestamp Activity timestamp
 * @property projectId Associated project ID
 * @property icon Activity icon
 * @property data Additional activity data
 */
data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val title: String,
    val description: String,
    val timestamp: Long,
    val projectId: String? = null,
    val icon: String = "",
    val data: Map<String, String> = emptyMap()
)

/**
 * Activity types.
 */
enum class ActivityType {
    PROJECT_CREATED,
    PROJECT_CONNECTED,
    PROJECT_DISCONNECTED,
    MESSAGE_SENT,
    MESSAGE_RECEIVED,
    COMMAND_EXECUTED,
    FILE_UPLOADED,
    FILE_DOWNLOADED,
    ERROR_OCCURRED
}

/**
 * Notification item.
 * 
 * @property id Notification ID
 * @property type Notification type
 * @property title Notification title
 * @property message Notification message
 * @property timestamp Notification timestamp
 * @property isRead Whether the notification is read
 * @property priority Notification priority
 * @property data Additional notification data
 */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val data: Map<String, String> = emptyMap()
)

/**
 * Notification types.
 */
enum class NotificationType {
    PERMISSION_REQUEST,
    TASK_COMPLETED,
    ERROR,
    WARNING,
    INFO,
    SUCCESS
}

/**
 * Notification priorities.
 */
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * System status information.
 * 
 * @property totalProjects Total number of projects
 * @property activeProjects Number of active projects
 * @property connectedProjects Number of connected projects
 * @property totalMessages Total number of messages
 * @property memoryUsage Memory usage in MB
 * @property networkStatus Network status
 * @property batteryLevel Battery level percentage
 * @property isCharging Whether device is charging
 */
data class SystemStatus(
    val totalProjects: Int = 0,
    val activeProjects: Int = 0,
    val connectedProjects: Int = 0,
    val totalMessages: Int = 0,
    val memoryUsage: Long = 0,
    val networkStatus: NetworkStatus = NetworkStatus.UNKNOWN,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false
)

/**
 * Network status.
 */
enum class NetworkStatus {
    UNKNOWN,
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET
}

/**
 * Git status information for UI.
 * 
 * @property branch Current branch
 * @property ahead Number of commits ahead
 * @property behind Number of commits behind
 * @property hasChanges Whether there are any changes
 * @property modifiedFiles Number of modified files
 * @property stagedFiles Number of staged files
 * @property untrackedFiles Number of untracked files
 * @property deletedFiles Number of deleted files
 */
data class GitStatus(
    val branch: String,
    val ahead: Int = 0,
    val behind: Int = 0,
    val hasChanges: Boolean = false,
    val modifiedFiles: Int = 0,
    val stagedFiles: Int = 0,
    val untrackedFiles: Int = 0,
    val deletedFiles: Int = 0
)