# Background Services Feature Specification - Notification System
**For Android Mobile Application**

> **Navigation**: [Index](./background-services-index.md) | [Overview](./background-services-overview.feat.md) | [Foreground Service](./background-services-foreground.feat.md) | **Notifications** | [Monitoring](./background-services-monitoring.feat.md) | [Testing](./background-services-testing.feat.md)

## Notification System

**Purpose**: Comprehensive notification management system that creates and manages all app notifications including monitoring status, permission requests, task completion, and errors. Implements notification channels, actions, and rich notification features while ensuring compatibility across Android versions.

```kotlin
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Notification IDs
        const val MONITORING_NOTIFICATION_ID = 1001
        const val PERMISSION_REQUEST_ID_BASE = 2000
        const val TASK_COMPLETION_ID_BASE = 3000
        const val ERROR_NOTIFICATION_ID_BASE = 4000
        const val PROGRESS_NOTIFICATION_ID_BASE = 5000
        const val AUTH_NOTIFICATION_ID_BASE = 6000
        
        // Channel IDs
        const val CHANNEL_MONITORING = "monitoring"
        const val CHANNEL_PERMISSIONS = "permissions"
        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_ERRORS = "errors"
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_ALERTS = "alerts"
        
        // Action keys
        const val ACTION_APPROVE = "com.pocketagent.action.APPROVE"
        const val ACTION_DENY = "com.pocketagent.action.DENY"
        const val ACTION_OPEN_PROJECT = "com.pocketagent.action.OPEN_PROJECT"
        const val ACTION_DISCONNECT = "com.pocketagent.action.DISCONNECT"
        const val ACTION_RETRY = "com.pocketagent.action.RETRY"
        const val ACTION_AUTHENTICATE = "com.pocketagent.action.AUTHENTICATE"
        
        // Remote input keys
        const val KEY_PERMISSION_RESPONSE = "permission_response"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_MONITORING,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows active Claude Code monitoring status"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_PERMISSIONS,
                    "Permission Requests",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Claude Code permission requests requiring approval"
                    setShowBadge(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_TASKS,
                    "Task Completion",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when Claude Code completes tasks"
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_ERRORS,
                    "Errors & Warnings",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Error notifications requiring attention"
                    setShowBadge(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    "Progress Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of ongoing operations"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Security and authentication alerts"
                    setShowBadge(true)
                    enableVibration(true)
                }
            )
            
            notificationManager.createNotificationChannels(channels)
        }
    }
    
    fun createMonitoringNotification(
        activeProjectCount: Int,
        connectionStatus: ConnectionStatus
    ): Notification {
        val title = when (activeProjectCount) {
            0 -> "Pocket Agent Running"
            1 -> "Monitoring 1 project"
            else -> "Monitoring $activeProjectCount projects"
        }
        
        val content = when (connectionStatus) {
            ConnectionStatus.CONNECTED -> "All connections active"
            ConnectionStatus.CONNECTING -> "Establishing connections..."
            ConnectionStatus.ERROR -> "Connection issues detected"
            else -> "Ready to connect"
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    fun showPermissionRequestNotification(
        projectName: String,
        tool: String,
        action: String,
        requestId: String,
        timeout: Int
    ) {
        val notificationId = PERMISSION_REQUEST_ID_BASE + requestId.hashCode()
        
        val approveIntent = createPermissionResponseIntent(requestId, true)
        val denyIntent = createPermissionResponseIntent(requestId, false)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setContentTitle("Permission Request: $projectName")
            .setContentText("Claude wants to use $tool to $action")
            .setSmallIcon(R.drawable.ic_permission)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(timeout * 1000L)
            .addAction(
                R.drawable.ic_check,
                "Allow",
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    approveIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_close,
                "Deny",
                PendingIntent.getBroadcast(
                    context,
                    notificationId + 1,
                    denyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showTaskCompletionNotification(
        projectName: String,
        taskDescription: String,
        success: Boolean,
        filesModified: Int = 0
    ) {
        val notificationId = TASK_COMPLETION_ID_BASE + System.currentTimeMillis().toInt()
        
        val title = if (success) {
            "Task Completed: $projectName"
        } else {
            "Task Failed: $projectName"
        }
        
        val content = buildString {
            append(taskDescription)
            if (success && filesModified > 0) {
                append(" ($filesModified files modified)")
            }
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("project_name", projectName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(if (success) R.drawable.ic_check_circle else R.drawable.ic_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showErrorNotification(
        projectName: String,
        error: String,
        canRetry: Boolean = false
    ) {
        val notificationId = ERROR_NOTIFICATION_ID_BASE + projectName.hashCode()
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setContentTitle("Error: $projectName")
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_error)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        if (canRetry) {
            val retryIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_RETRY
                putExtra("project_name", projectName)
            }
            
            builder.addAction(
                R.drawable.ic_refresh,
                "Retry",
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        
        notificationManager.notify(notificationId, builder.build())
    }
    
    fun showProgressNotification(
        projectName: String,
        isWorking: Boolean
    ) {
        val notificationId = PROGRESS_NOTIFICATION_ID_BASE + projectName.hashCode()
        
        if (isWorking) {
            val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setContentTitle("Claude is working...")
                .setContentText(projectName)
                .setSmallIcon(R.drawable.ic_sync)
                .setProgress(0, 0, true) // Indeterminate progress
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            notificationManager.notify(notificationId, notification)
        } else {
            // Operation complete, dismiss the progress notification
            notificationManager.cancel(notificationId)
        }
    }
    
    fun dismissProgressNotification(projectName: String) {
        val notificationId = PROGRESS_NOTIFICATION_ID_BASE + projectName.hashCode()
        notificationManager.cancel(notificationId)
    }
    
    fun showAuthenticationRequiredNotification(
        projectName: String,
        message: String
    ) {
        val notificationId = AUTH_NOTIFICATION_ID_BASE + projectName.hashCode()
        
        val authIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_AUTHENTICATE
            putExtra("project_name", projectName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            authIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Authentication Required")
            .setContentText("$projectName: $message")
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .addAction(
                R.drawable.ic_fingerprint,
                "Authenticate",
                pendingIntent
            )
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showConnectionNotification(
        projectName: String,
        status: String
    ) {
        // Use a consistent ID for connection notifications per project
        val notificationId = projectName.hashCode()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setContentTitle(projectName)
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_connection)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Auto-dismiss after 5 seconds
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    private fun createPermissionResponseIntent(requestId: String, approved: Boolean): Intent {
        return Intent(context, NotificationActionReceiver::class.java).apply {
            action = if (approved) ACTION_APPROVE else ACTION_DENY
            putExtra("request_id", requestId)
            putExtra("approved", approved)
        }
    }
    
    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }
    
    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }
    
    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
```

## Notification Types

### 1. Monitoring Notification (Persistent)
- **Channel**: `monitoring` (Low importance)
- **Purpose**: Required foreground service notification
- **Features**:
  - Shows active project count
  - Overall connection status
  - Tap to open app
  - Cannot be dismissed while service is active

### 2. Permission Request Notifications
- **Channel**: `permissions` (High importance)
- **Purpose**: Claude Code permission requests
- **Features**:
  - Action buttons (Allow/Deny)
  - Auto-timeout based on wrapper setting
  - High priority with vibration
  - Shows tool name and action

### 3. Task Completion Notifications
- **Channel**: `tasks` (Default importance)
- **Purpose**: Notify when Claude completes tasks
- **Features**:
  - Success/failure indication
  - File modification count
  - Tap to open project
  - Auto-dismissible

### 4. Error Notifications
- **Channel**: `errors` (High importance)
- **Purpose**: Connection errors and failures
- **Features**:
  - Optional retry action
  - High priority with vibration
  - Persistent until addressed

### 5. Progress Notifications
- **Channel**: `progress` (Low importance)
- **Purpose**: Show when Claude is working on a request
- **Features**:
  - Simple "Working..." indicator
  - Indeterminate progress animation
  - Auto-dismiss when operation completes
  - Only alerts once

### 6. Security Alert Notifications
- **Channel**: `alerts` (High importance)
- **Purpose**: Authentication and security issues
- **Features**:
  - Authentication action button
  - High priority with vibration
  - Deep link to authentication screen

## Notification Action Receiver

```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var notificationManager: AppNotificationManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppNotificationManager.ACTION_APPROVE,
            AppNotificationManager.ACTION_DENY -> {
                handlePermissionResponse(intent)
            }
            AppNotificationManager.ACTION_RETRY -> {
                handleRetry(intent)
            }
            AppNotificationManager.ACTION_DISCONNECT -> {
                handleDisconnect(intent)
            }
        }
    }
    
    private fun handlePermissionResponse(intent: Intent) {
        val requestId = intent.getStringExtra("request_id") ?: return
        val approved = intent.getBooleanExtra("approved", false)
        
        scope.launch {
            permissionManager.respondToPermission(requestId, approved)
            
            // Cancel the notification
            val notificationId = AppNotificationManager.PERMISSION_REQUEST_ID_BASE + 
                              requestId.hashCode()
            notificationManager.cancel(notificationId)
        }
    }
    
    private fun handleRetry(intent: Intent) {
        val projectName = intent.getStringExtra("project_name") ?: return
        
        scope.launch {
            connectionManager.retryConnection(projectName)
        }
    }
    
    private fun handleDisconnect(intent: Intent) {
        val projectName = intent.getStringExtra("project_name") ?: return
        
        scope.launch {
            connectionManager.disconnect(projectName)
        }
    }
}
```

## Deep Linking Configuration

```kotlin
// In MainActivity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Handle notification deep links
    when (intent.action) {
        AppNotificationManager.ACTION_OPEN_PROJECT -> {
            val projectName = intent.getStringExtra("project_name")
            navigateToProject(projectName)
        }
        AppNotificationManager.ACTION_AUTHENTICATE -> {
            val projectName = intent.getStringExtra("project_name")
            showAuthenticationScreen(projectName)
        }
    }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Handle the same actions as onCreate
}
```

## Best Practices

1. **Channel Management**
   - Use appropriate importance levels
   - Group related notifications logically
   - Provide clear channel descriptions

2. **Notification IDs**
   - Use consistent ID schemes
   - Hash-based IDs for per-project notifications
   - Time-based IDs for unique notifications

3. **User Experience**
   - Auto-dismiss informational notifications
   - Keep ongoing notifications updated
   - Use actions for quick responses
   - Respect Do Not Disturb settings

4. **Performance**
   - Cancel obsolete notifications
   - Batch notification updates
   - Use `setOnlyAlertOnce()` for progress
   - Minimize notification churn