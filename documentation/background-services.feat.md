# Background Services Feature Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [Foreground Service Manager](#foreground-service-manager)
   - [Notification System](#notification-system)
   - [Work Manager Integration](#work-manager-integration)
   - [Battery Optimization Manager](#battery-optimization-manager)
   - [Connection Health Monitor](#connection-health-monitor)
   - [Session State Persistence](#session-state-persistence)
   - [Background Task Scheduler](#background-task-scheduler)
   - [Error Handling](#error-handling)
   - [Integration Points](#integration-points)
4. [Testing](#testing)
   - [Testing Checklist](#testing-checklist)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Critical Implementation Details](#critical-implementation-details)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The Background Services feature provides the persistent monitoring and notification infrastructure for **Pocket Agent - a remote coding agent mobile interface**. This feature implements Android's foreground service for continuous connection monitoring, WorkManager for scheduled tasks, a comprehensive notification system, and battery-aware optimization strategies. It ensures that Claude Code sessions remain active and users receive timely updates even when the app is not in the foreground.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: Service-based architecture with WorkManager and Foreground Service
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#background-monitoring-service)

This feature is designed to be implemented in Phase 2 as critical infrastructure that supports all other features. It provides the foundation for real-time notifications, connection persistence, and background task execution while respecting Android's battery optimization requirements.

## Architecture

### Technology Stack (Android-Specific)

- **Foreground Service**: Android Foreground Service - Persistent execution with notification
- **Task Scheduling**: WorkManager 2.9.0+ - Guaranteed background task execution
- **Notifications**: NotificationCompat - Rich notifications with actions
- **Process Lifecycle**: ProcessLifecycleOwner - App lifecycle monitoring
- **Power Management**: PowerManager & BatteryManager - Battery-aware optimizations
- **Dependency Injection**: Hilt with ServiceComponent - Service-scoped dependencies
- **Coroutines**: Kotlin Coroutines with Service scope - Async operations
- **Mobile Optimization**: Doze mode compliance, battery optimization exemptions

### Key Components

- **ClaudeBackgroundService**: Main foreground service managing all background operations
- **PocketAgentNotificationManager**: Centralized notification creation and management
- **ConnectionHealthMonitor**: Periodic health checks for active connections with health criteria
- **BatteryOptimizationManager**: Adjusts polling frequencies based on battery state
- **SessionStatePersistence**: Persists and restores session state across app lifecycle
- **BackgroundTaskScheduler**: Scheduled tasks for cleanup and maintenance via WorkManager
- **DefaultPermissionPolicyManager**: Automated permission handling with predefined policies
- **WakeLockManager**: Manages wake locks for critical operations
- **SubAgentProgressMonitor**: Tracks Claude Code's internal agent orchestration
- **ServerResourceMonitor**: Monitors CPU, memory, and disk usage on remote server
- **ProgressParser**: Extracts progress information from Claude messages
- **BackgroundVoiceManager**: Text-to-speech announcements for background events

## Components Architecture

### Foreground Service Manager

**Purpose**: Implements the main Android foreground service that runs continuously while monitoring active Claude Code sessions. Manages service lifecycle, notification updates, and coordinates all background operations while ensuring compliance with Android's foreground service requirements.

```kotlin
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

// Note: MainActivity should be replaced with your actual main activity class
// Note: Drawable resources (R.drawable.*) need to be created during implementation

@AndroidEntryPoint
class ClaudeBackgroundService : LifecycleService() {
    
    companion object {
        const val ACTION_START_MONITORING = "com.pocketagent.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.pocketagent.action.STOP_MONITORING"
        const val ACTION_UPDATE_PROJECT = "com.pocketagent.action.UPDATE_PROJECT"
        const val EXTRA_PROJECT_ID = "project_id"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "claude_monitoring"
        
        fun startMonitoring(context: Context, projectId: String) {
            val intent = Intent(context, ClaudeBackgroundService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startForegroundService(intent)
        }
        
        fun stopMonitoring(context: Context, projectId: String) {
            val intent = Intent(context, ClaudeBackgroundService::class.java).apply {
                action = ACTION_STOP_MONITORING
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startService(intent)
        }
    }
    
    @Inject lateinit var notificationManager: PocketAgentNotificationManager
    @Inject lateinit var connectionMonitor: ConnectionHealthMonitor
    @Inject lateinit var sessionStateManager: SessionStateManager
    @Inject lateinit var batteryManager: BatteryOptimizationManager
    @Inject lateinit var secureDataRepository: SecureDataRepository
    @Inject lateinit var connectionManager: ConnectionManager
    
    private val activeProjects = ConcurrentHashMap<String, MonitoringSession>()
    private var serviceStarted = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
                startMonitoringProject(projectId)
            }
            ACTION_STOP_MONITORING -> {
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
                stopMonitoringProject(projectId)
            }
            ACTION_UPDATE_PROJECT -> {
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
                updateProjectMonitoring(projectId)
            }
        }
        
        return START_STICKY
    }
    
    private fun startMonitoringProject(projectId: String) {
        if (!serviceStarted) {
            startForegroundService()
            serviceStarted = true
        }
        
        lifecycleScope.launch {
            val project = secureDataRepository.getProject(projectId)
            if (project != null) {
                val session = MonitoringSession(
                    projectId = projectId,
                    project = project,
                    scope = lifecycleScope
                )
                activeProjects[projectId] = session
                
                // Start monitoring flows
                launchMonitoringFlows(session)
                
                // Update notification
                updateNotification()
            }
        }
    }
    
    private fun stopMonitoringProject(projectId: String) {
        activeProjects.remove(projectId)?.let { session ->
            session.scope.cancel()
        }
        
        updateNotification()
        
        // Stop service if no active projects
        if (activeProjects.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            serviceStarted = false
        }
    }
    
    private fun startForegroundService() {
        val notification = notificationManager.createMonitoringNotification(
            activeProjectCount = 0,
            connectionStatus = ConnectionStatus.CONNECTING
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun updateNotification() {
        if (!serviceStarted) return
        
        val activeCount = activeProjects.size
        val overallStatus = if (activeProjects.isEmpty()) {
            ConnectionStatus.DISCONNECTED
        } else {
            activeProjects.values
                .map { connectionManager.getConnectionStatus(it.projectId) }
                .minByOrNull { it.ordinal } ?: ConnectionStatus.DISCONNECTED
        }
        
        val notification = notificationManager.createMonitoringNotification(
            activeProjectCount = activeCount,
            connectionStatus = overallStatus
        )
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun launchMonitoringFlows(session: MonitoringSession) {
        // Monitor connection status
        connectionManager.connectionStates
            .map { states -> states[session.projectId] }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { status ->
                handleConnectionStatusChange(session, status)
            }
            .launchIn(session.scope)
        
        // Monitor battery state
        batteryManager.batteryState
            .distinctUntilChanged()
            .onEach { batteryState ->
                adjustMonitoringFrequency(session, batteryState)
            }
            .launchIn(session.scope)
        
        // Start periodic health checks
        session.scope.launch {
            connectionMonitor.startMonitoring(
                projectId = session.projectId,
                frequency = batteryManager.getPollingFrequency()
            )
        }
    }
    
    private suspend fun handleConnectionStatusChange(
        session: MonitoringSession,
        status: ConnectionStatus
    ) {
        when (status) {
            ConnectionStatus.CONNECTED -> {
                notificationManager.showConnectionNotification(
                    projectName = session.project.name,
                    status = "Connected"
                )
            }
            ConnectionStatus.ERROR -> {
                notificationManager.showErrorNotification(
                    projectName = session.project.name,
                    error = "Connection lost"
                )
            }
            else -> {
                // Update main notification
                updateNotification()
            }
        }
    }
    
    private fun adjustMonitoringFrequency(
        session: MonitoringSession,
        batteryState: BatteryState
    ) {
        connectionMonitor.updateFrequency(
            projectId = session.projectId,
            frequency = batteryManager.getPollingFrequency(batteryState)
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claude Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Claude Code monitoring status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updateProjectMonitoring(projectId: String) {
        activeProjects[projectId]?.let { session ->
            lifecycleScope.launch {
                val updatedProject = secureDataRepository.getProject(projectId)
                if (updatedProject != null) {
                    session.project = updatedProject
                    updateNotification()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up all monitoring sessions
        activeProjects.values.forEach { session ->
            session.scope.cancel()
        }
        activeProjects.clear()
    }
}

data class MonitoringSession(
    val projectId: String,
    var project: Project,
    val scope: CoroutineScope
)
```

### Notification System

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
class PocketAgentNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Notification IDs
        const val MONITORING_NOTIFICATION_ID = 1001
        const val PERMISSION_REQUEST_ID_BASE = 2000
        const val TASK_COMPLETION_ID_BASE = 3000
        const val ERROR_NOTIFICATION_ID_BASE = 4000
        const val PROGRESS_NOTIFICATION_ID_BASE = 5000
        
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
        operationName: String,
        progress: Int,
        maxProgress: Int = 100,
        indeterminate: Boolean = false
    ) {
        val notificationId = PROGRESS_NOTIFICATION_ID_BASE + projectName.hashCode()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setContentTitle(operationName)
            .setContentText("$projectName: ${progress}%")
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(maxProgress, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showSubAgentProgressNotification(
        projectName: String,
        agentName: String,
        agentDescription: String,
        status: SubAgentStatus,
        progress: Int? = null,
        error: String? = null
    ) {
        val notificationId = PROGRESS_NOTIFICATION_ID_BASE + (projectName + agentName).hashCode()
        
        val icon = when (status) {
            SubAgentStatus.STARTING -> R.drawable.ic_agent_starting
            SubAgentStatus.RUNNING -> R.drawable.ic_agent_running
            SubAgentStatus.COMPLETED -> R.drawable.ic_agent_completed
            SubAgentStatus.FAILED -> R.drawable.ic_agent_failed
        }
        
        val title = "$agentName ${status.displayName}"
        val content = buildString {
            append(projectName)
            if (progress != null) {
                append(" - $progress%")
            }
            if (error != null) {
                append("\nError: $error")
            }
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                """
                $agentDescription
                
                $content
                """.trimIndent()
            ))
            .setSmallIcon(icon)
            .setOngoing(status == SubAgentStatus.RUNNING || status == SubAgentStatus.STARTING)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        if (status == SubAgentStatus.RUNNING && progress != null) {
            builder.setProgress(100, progress, false)
        } else if (status == SubAgentStatus.STARTING) {
            builder.setProgress(0, 0, true)
        }
        
        // Auto dismiss completed/failed notifications after 10 seconds
        if (status == SubAgentStatus.COMPLETED || status == SubAgentStatus.FAILED) {
            builder.setTimeoutAfter(10000)
            builder.setAutoCancel(true)
        }
        
        notificationManager.notify(notificationId, builder.build())
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

### Work Manager Integration

**Purpose**: Implements scheduled background tasks using Android's WorkManager for operations that don't require immediate execution. Handles periodic cleanup, log rotation, cache management, and other maintenance tasks while respecting system constraints and battery optimization.

```kotlin
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    
    companion object {
        const val WORK_CLEANUP = "cleanup_work"
        const val WORK_LOG_UPLOAD = "log_upload_work"
        const val WORK_CACHE_CLEANUP = "cache_cleanup_work"
        const val WORK_SESSION_BACKUP = "session_backup_work"
        const val WORK_METRICS_COLLECTION = "metrics_collection_work"
        
        const val TAG_PERIODIC = "periodic"
        const val TAG_MAINTENANCE = "maintenance"
    }
    
    fun schedulePeriodicWork() {
        // Schedule daily cleanup
        scheduleCleanupWork()
        
        // Schedule cache cleanup every 3 days
        scheduleCacheCleanup()
        
        // Schedule session backup every 12 hours
        scheduleSessionBackup()
        
        // Schedule metrics collection daily
        scheduleMetricsCollection()
    }
    
    private fun scheduleCleanupWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 4,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .addTag(TAG_MAINTENANCE)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_CLEANUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            cleanupRequest
        )
    }
    
    private fun scheduleCacheCleanup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .setRequiresStorageNotLow(false) // Run even if storage is low
            .build()
        
        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            repeatInterval = 3,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 12,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .addTag(TAG_MAINTENANCE)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_CACHE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            cacheCleanupRequest
        )
    }
    
    private fun scheduleSessionBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val backupRequest = PeriodicWorkRequestBuilder<SessionBackupWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_SESSION_BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            backupRequest
        )
    }
    
    private fun scheduleMetricsCollection() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val metricsRequest = PeriodicWorkRequestBuilder<MetricsCollectionWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 4,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_METRICS_COLLECTION,
            ExistingPeriodicWorkPolicy.KEEP,
            metricsRequest
        )
    }
    
    fun scheduleOneTimeLogUpload(priority: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(!priority)
            .build()
        
        val uploadRequest = OneTimeWorkRequestBuilder<LogUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniqueWork(
            WORK_LOG_UPLOAD,
            ExistingWorkPolicy.REPLACE,
            uploadRequest
        )
    }
    
    fun cancelWork(workName: String) {
        workManager.cancelUniqueWork(workName)
    }
    
    fun cancelAllWork() {
        workManager.cancelAllWorkByTag(TAG_PERIODIC)
    }
    
    fun getWorkInfo(workName: String): ListenableFuture<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWork(workName)
    }
}

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageHistoryRepository: MessageHistoryRepository,
    private val sessionRepository: SessionRepository,
    private val logManager: LogManager
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Clean old message history (older than 30 days)
            val cutoffDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            messageHistoryRepository.deleteMessagesOlderThan(cutoffDate)
            
            // Clean orphaned sessions
            sessionRepository.cleanupOrphanedSessions()
            
            // Rotate logs
            logManager.rotateLogs()
            
            // Clean temporary files
            cleanTempFiles()
            
            Result.success()
        } catch (e: Exception) {
            logManager.logError("Cleanup worker failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun cleanTempFiles() {
        val tempDir = applicationContext.cacheDir
        tempDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_") && 
                file.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)) {
                file.delete()
            }
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): CleanupWorker
    }
}

@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cacheManager: CacheManager
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Clear image cache older than 7 days
            cacheManager.clearOldImageCache(days = 7)
            
            // Clear WebSocket message cache
            cacheManager.clearMessageCache()
            
            // Trim database cache
            cacheManager.trimDatabaseCache()
            
            // Report cache size
            val cacheSize = cacheManager.calculateCacheSize()
            val outputData = workDataOf(
                "cache_size" to cacheSize,
                "timestamp" to System.currentTimeMillis()
            )
            
            Result.success(outputData)
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): CacheCleanupWorker
    }
}

@HiltWorker
class SessionBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionBackupManager: SessionBackupManager
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val backedUpSessions = sessionBackupManager.backupActiveSessions()
            
            val outputData = workDataOf(
                "backed_up_count" to backedUpSessions,
                "timestamp" to System.currentTimeMillis()
            )
            
            Result.success(outputData)
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): SessionBackupWorker
    }
}

@HiltWorker
class MetricsCollectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val metricsCollector: MetricsCollector
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val metrics = metricsCollector.collectDailyMetrics()
            metricsCollector.uploadMetrics(metrics)
            
            Result.success()
        } catch (e: Exception) {
            // Metrics collection failure shouldn't retry
            Result.failure()
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): MetricsCollectionWorker
    }
}

@HiltWorker
class LogUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val logUploader: LogUploader
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val logsUploaded = logUploader.uploadPendingLogs()
            
            if (logsUploaded) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): LogUploadWorker
    }
}
```

### Battery Optimization Manager

**Purpose**: Manages battery-aware optimizations for background operations, adjusting polling frequencies and task scheduling based on battery level, charging state, and power save mode. Ensures the app respects Android's battery optimization guidelines while maintaining functionality.

```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryOptimizationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Polling frequencies in milliseconds
        const val FREQUENCY_CHARGING = 3_000L      // 3 seconds when charging
        const val FREQUENCY_NORMAL = 5_000L        // 5 seconds on normal battery
        const val FREQUENCY_LOW = 15_000L          // 15 seconds on low battery
        const val FREQUENCY_CRITICAL = 30_000L     // 30 seconds on critical battery
        const val FREQUENCY_POWER_SAVE = 60_000L   // 60 seconds in power save mode
        
        // Battery thresholds
        const val BATTERY_THRESHOLD_LOW = 30
        const val BATTERY_THRESHOLD_CRITICAL = 15
    }
    
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    val batteryState: Flow<BatteryState> = callbackFlow {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED,
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        trySend(getCurrentBatteryState())
                    }
                }
            }
        }
        
        // Register for battery changes
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(batteryReceiver, batteryFilter)
        
        // Emit initial state
        trySend(getCurrentBatteryState())
        
        awaitClose {
            context.unregisterReceiver(batteryReceiver)
        }
    }.distinctUntilChanged()
        .shareIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )
    
    fun getCurrentBatteryState(): BatteryState {
        val batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercentage = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        
        val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
        
        return BatteryState(
            percentage = batteryPercentage,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            level = when {
                isCharging -> BatteryLevel.CHARGING
                isPowerSaveMode -> BatteryLevel.POWER_SAVE
                batteryPercentage <= BATTERY_THRESHOLD_CRITICAL -> BatteryLevel.CRITICAL
                batteryPercentage <= BATTERY_THRESHOLD_LOW -> BatteryLevel.LOW
                else -> BatteryLevel.NORMAL
            }
        )
    }
    
    fun getPollingFrequency(batteryState: BatteryState = getCurrentBatteryState()): Long {
        return when (batteryState.level) {
            BatteryLevel.CHARGING -> FREQUENCY_CHARGING
            BatteryLevel.NORMAL -> FREQUENCY_NORMAL
            BatteryLevel.LOW -> FREQUENCY_LOW
            BatteryLevel.CRITICAL -> FREQUENCY_CRITICAL
            BatteryLevel.POWER_SAVE -> FREQUENCY_POWER_SAVE
        }
    }
    
    fun shouldReduceBackgroundActivity(batteryState: BatteryState = getCurrentBatteryState()): Boolean {
        return batteryState.level == BatteryLevel.CRITICAL || 
               batteryState.level == BatteryLevel.POWER_SAVE
    }
    
    fun canPerformIntensiveOperation(batteryState: BatteryState = getCurrentBatteryState()): Boolean {
        return batteryState.isCharging || 
               (batteryState.percentage > BATTERY_THRESHOLD_LOW && !batteryState.isPowerSaveMode)
    }
    
    fun getBackgroundTaskConstraints(priority: TaskPriority): TaskConstraints {
        val batteryState = getCurrentBatteryState()
        
        return when (priority) {
            TaskPriority.CRITICAL -> TaskConstraints(
                requiresCharging = false,
                requiresBatteryNotLow = false,
                maxExecutionTime = 60_000L // 1 minute max
            )
            TaskPriority.HIGH -> TaskConstraints(
                requiresCharging = false,
                requiresBatteryNotLow = batteryState.percentage < BATTERY_THRESHOLD_CRITICAL,
                maxExecutionTime = 30_000L // 30 seconds max
            )
            TaskPriority.NORMAL -> TaskConstraints(
                requiresCharging = batteryState.isPowerSaveMode,
                requiresBatteryNotLow = true,
                maxExecutionTime = 15_000L // 15 seconds max
            )
            TaskPriority.LOW -> TaskConstraints(
                requiresCharging = true,
                requiresBatteryNotLow = true,
                maxExecutionTime = 10_000L // 10 seconds max
            )
        }
    }
    
    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No battery optimizations before M
        }
    }
    
    fun requestIgnoreBatteryOptimizations(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
            Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
}

data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
    val level: BatteryLevel
)

enum class BatteryLevel {
    CHARGING,
    NORMAL,
    LOW,
    CRITICAL,
    POWER_SAVE
}

enum class TaskPriority {
    CRITICAL,  // Must run regardless of battery
    HIGH,      // Should run unless battery is critical
    NORMAL,    // Can be delayed on low battery
    LOW        // Only run when charging or high battery
}

data class TaskConstraints(
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
    val maxExecutionTime: Long
)
```

### Connection Health Monitor

**Purpose**: Periodically monitors the health of active WebSocket connections and Claude Code processes, detecting connection failures and triggering reconnection attempts. Implements adaptive polling based on battery state and connection stability.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionHealthMonitor @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val connectionManager: ConnectionManager,
    private val batteryOptimizationManager: BatteryOptimizationManager,
    private val notificationManager: PocketAgentNotificationManager
) {
    
    companion object {
        // Health check timeouts
        const val WEBSOCKET_PING_TIMEOUT_MS = 5_000L
        const val PROCESS_HEALTH_TIMEOUT_MS = 10_000L
        const val AUTH_CHECK_TIMEOUT_MS = 3_000L
        
        // Health criteria thresholds
        const val MAX_ACCEPTABLE_LATENCY_MS = 15_000L
        const val MIN_SUCCESS_RATE_PERCENT = 80
        const val HEALTH_HISTORY_SIZE = 10
        
        // Retry configuration
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 2_000L
    }
    
    private val monitoringJobs = ConcurrentHashMap<String, Job>()
    private val healthCheckResults = MutableSharedFlow<HealthCheckResult>()
    private val healthHistory = ConcurrentHashMap<String, MutableList<HealthCheckResult>>()
    
    private val _monitoringState = MutableStateFlow<Map<String, MonitoringState>>(emptyMap())
    val monitoringState: StateFlow<Map<String, MonitoringState>> = _monitoringState.asStateFlow()
    
    fun startMonitoring(
        projectId: String,
        frequency: Long = batteryOptimizationManager.getPollingFrequency()
    ) {
        // Cancel existing monitoring for this project
        stopMonitoring(projectId)
        
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            updateMonitoringState(projectId, MonitoringState.ACTIVE)
            
            while (isActive) {
                try {
                    performHealthCheck(projectId)
                    
                    // Adaptive delay based on connection stability
                    val delay = getAdaptiveDelay(projectId, frequency)
                    delay(delay)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleMonitoringError(projectId, e)
                    delay(frequency * 2) // Back off on errors
                }
            }
        }
        
        monitoringJobs[projectId] = job
    }
    
    fun stopMonitoring(projectId: String) {
        monitoringJobs.remove(projectId)?.cancel()
        updateMonitoringState(projectId, MonitoringState.STOPPED)
    }
    
    fun updateFrequency(projectId: String, frequency: Long) {
        if (monitoringJobs.containsKey(projectId)) {
            stopMonitoring(projectId)
            startMonitoring(projectId, frequency)
        }
    }
    
    private suspend fun performHealthCheck(projectId: String) {
        val startTime = System.currentTimeMillis()
        
        // Check WebSocket connection and authentication status
        val wsHealthy = checkWebSocketHealth(projectId)
        val authValid = checkAuthenticationStatus(projectId)
        
        // Check Claude process (via wrapper)
        val processHealthy = checkClaudeProcessHealth(projectId)
        
        val result = HealthCheckResult(
            projectId = projectId,
            timestamp = startTime,
            webSocketHealthy = wsHealthy,
            authenticationValid = authValid,
            processHealthy = processHealthy,
            latency = System.currentTimeMillis() - startTime
        )
        
        healthCheckResults.emit(result)
        updateHealthHistory(result)
        
        // Update connection status based on health check
        updateConnectionStatus(result)
        
        // Handle unhealthy connections
        if (!result.isHealthy()) {
            handleUnhealthyConnection(result)
        }
    }
    
    private suspend fun checkAuthenticationStatus(projectId: String): Boolean {
        return try {
            val connection = webSocketManager.getConnection(projectId)
            connection?.let {
                // Check if authentication is still valid
                val authStatus = withTimeoutOrNull(AUTH_CHECK_TIMEOUT_MS) {
                    it.checkAuthenticationStatus()
                }
                authStatus == AuthenticationStatus.AUTHENTICATED
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun checkWebSocketHealth(projectId: String): Boolean {
        return try {
            val webSocket = webSocketManager.getConnection(projectId)
            webSocket?.let {
                // Send ping and wait for pong
                val pongReceived = withTimeoutOrNull(5000L) {
                    it.sendPing()
                }
                pongReceived != null
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun checkClaudeProcessHealth(projectId: String): Boolean {
        return try {
            // Send health check message through WebSocket
            val response = withTimeoutOrNull(10000L) {
                webSocketManager.sendHealthCheck(projectId)
            }
            response != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun updateConnectionStatus(result: HealthCheckResult) {
        val newStatus = when {
            !result.webSocketHealthy -> ConnectionStatus.ERROR
            !result.authenticationValid -> ConnectionStatus.AUTHENTICATION_REQUIRED
            !result.processHealthy -> ConnectionStatus.ERROR
            else -> ConnectionStatus.CONNECTED
        }
        
        connectionManager.updateConnectionStatus(result.projectId, newStatus)
    }
    
    private suspend fun handleUnhealthyConnection(result: HealthCheckResult) {
        val projectName = connectionManager.getProjectName(result.projectId)
        
        when {
            !result.webSocketHealthy -> {
                notificationManager.showErrorNotification(
                    projectName = projectName,
                    error = "Server connection lost",
                    canRetry = true
                )
                // Attempt to reconnect WebSocket
                connectionManager.reconnectWebSocket(result.projectId)
            }
            !result.authenticationValid -> {
                notificationManager.showAuthenticationRequiredNotification(
                    projectName = projectName,
                    message = "Authentication expired. Please re-authenticate."
                )
                // Trigger re-authentication flow
                connectionManager.requestAuthentication(result.projectId)
            }
            !result.processHealthy -> {
                notificationManager.showErrorNotification(
                    projectName = projectName,
                    error = "Claude Code process not responding",
                    canRetry = true
                )
            }
        }
    }
    
    private fun getAdaptiveDelay(projectId: String, baseFrequency: Long): Long {
        val recentResults = healthCheckResults.replayCache
            .filter { it.projectId == projectId }
            .takeLast(5)
        
        return if (recentResults.all { it.isHealthy() }) {
            // Stable connection, can reduce frequency
            (baseFrequency * 1.5).toLong().coerceAtMost(60_000L)
        } else if (recentResults.count { !it.isHealthy() } > 2) {
            // Unstable connection, increase frequency
            (baseFrequency * 0.5).toLong().coerceAtLeast(1_000L)
        } else {
            baseFrequency
        }
    }
    
    fun evaluateConnectionHealth(projectId: String): ConnectionHealthStatus {
        val history = healthHistory[projectId] ?: return ConnectionHealthStatus.UNKNOWN
        
        if (history.isEmpty()) {
            return ConnectionHealthStatus.UNKNOWN
        }
        
        val recentResults = history.takeLast(HEALTH_HISTORY_SIZE)
        val successCount = recentResults.count { it.isHealthy() }
        val successRate = (successCount * 100) / recentResults.size
        val avgLatency = recentResults.map { it.latency }.average()
        
        return when {
            successRate >= MIN_SUCCESS_RATE_PERCENT && avgLatency <= MAX_ACCEPTABLE_LATENCY_MS -> {
                ConnectionHealthStatus.HEALTHY
            }
            successRate >= 50 -> {
                ConnectionHealthStatus.DEGRADED
            }
            else -> {
                ConnectionHealthStatus.UNHEALTHY
            }
        }
    }
    
    private fun updateHealthHistory(result: HealthCheckResult) {
        healthHistory.compute(result.projectId) { _, history ->
            val list = history ?: mutableListOf()
            list.add(result)
            if (list.size > HEALTH_HISTORY_SIZE) {
                list.removeAt(0)
            }
            list
        }
    }
    
    private fun updateMonitoringState(projectId: String, state: MonitoringState) {
        _monitoringState.update { current ->
            current + (projectId to state)
        }
    }
    
    private fun handleMonitoringError(projectId: String, error: Exception) {
        updateMonitoringState(projectId, MonitoringState.ERROR)
        // Log error for debugging
    }
    
    fun stopAllMonitoring() {
        monitoringJobs.values.forEach { it.cancel() }
        monitoringJobs.clear()
        _monitoringState.value = emptyMap()
    }
}

data class HealthCheckResult(
    val projectId: String,
    val timestamp: Long,
    val webSocketHealthy: Boolean,
    val authenticationValid: Boolean,
    val processHealthy: Boolean,
    val latency: Long
) {
    fun isHealthy(): Boolean = webSocketHealthy && authenticationValid && processHealthy
}

enum class MonitoringState {
    ACTIVE,
    STOPPED,
    ERROR
}

enum class ConnectionHealthStatus {
    HEALTHY,    // All checks passing, good latency
    DEGRADED,   // Some failures but still functional
    UNHEALTHY,  // Too many failures
    UNKNOWN     // Not enough data
}
```

### WakeLock Manager

**Purpose**: Manages Android wake locks to ensure critical operations complete even when the device screen is off. Implements proper wake lock acquisition and release with timeout protection to prevent battery drain.

```kotlin
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val DEFAULT_TIMEOUT = 60_000L // 1 minute default timeout
        const val MAX_TIMEOUT = 300_000L // 5 minutes maximum
        const val TAG_PREFIX = "PocketAgent:"
    }
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLocks = ConcurrentHashMap<String, PowerManager.WakeLock>()
    
    fun acquireWakeLock(
        tag: String,
        timeout: Long = DEFAULT_TIMEOUT,
        level: Int = PowerManager.PARTIAL_WAKE_LOCK
    ) {
        val safeTimeout = timeout.coerceIn(1000L, MAX_TIMEOUT)
        val fullTag = "$TAG_PREFIX$tag"
        
        // Release existing wake lock if any
        releaseWakeLock(tag)
        
        val wakeLock = powerManager.newWakeLock(level, fullTag).apply {
            setReferenceCounted(false)
        }
        
        wakeLock.acquire(safeTimeout)
        wakeLocks[tag] = wakeLock
    }
    
    fun releaseWakeLock(tag: String) {
        wakeLocks.remove(tag)?.let { wakeLock ->
            if (wakeLock.isHeld) {
                try {
                    wakeLock.release()
                } catch (e: Exception) {
                    // Wake lock may have already been released
                }
            }
        }
    }
    
    fun releaseAllWakeLocks() {
        wakeLocks.keys.toList().forEach { tag ->
            releaseWakeLock(tag)
        }
    }
    
    fun isWakeLockHeld(tag: String): Boolean {
        return wakeLocks[tag]?.isHeld ?: false
    }
    
    // Use for critical operations that must complete
    suspend fun <T> withWakeLock(
        tag: String,
        timeout: Long = DEFAULT_TIMEOUT,
        block: suspend () -> T
    ): T {
        acquireWakeLock(tag, timeout)
        return try {
            block()
        } finally {
            releaseWakeLock(tag)
        }
    }
}
```

### Sub-Agent Progress Monitor

**Purpose**: Tracks Claude's sub-agent orchestration, monitoring when specialized agents are spawned for tasks like testing or deployment. Provides detailed progress tracking and hierarchy visualization for complex multi-agent operations.

```kotlin
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubAgentMonitor @Inject constructor(
    private val notificationManager: PocketAgentNotificationManager,
    private val progressParser: ProgressParser
) {
    
    private val activeSubAgents = ConcurrentHashMap<String, SubAgentProgress>()
    private val _subAgentUpdates = MutableSharedFlow<SubAgentUpdate>()
    val subAgentUpdates: SharedFlow<SubAgentUpdate> = _subAgentUpdates.asSharedFlow()
    
    suspend fun processClaudeMessage(projectId: String, message: ClaudeMessage) {
        // Detect sub-agent creation
        val subAgentInfo = parseSubAgentInfo(message)
        if (subAgentInfo != null) {
            trackSubAgent(projectId, subAgentInfo)
            return
        }
        
        // Update existing sub-agent progress
        val progressUpdate = parseSubAgentProgress(message)
        if (progressUpdate != null) {
            updateSubAgentProgress(projectId, progressUpdate)
        }
    }
    
    private suspend fun trackSubAgent(projectId: String, info: SubAgentInfo) {
        val progress = SubAgentProgress(
            agentId = info.agentId,
            agentType = info.agentType,
            parentAgentId = info.parentAgentId,
            projectId = projectId,
            status = SubAgentStatus.STARTING,
            progress = 0f,
            currentTask = info.initialTask,
            startTime = System.currentTimeMillis(),
            completionTime = null
        )
        
        activeSubAgents[info.agentId] = progress
        
        _subAgentUpdates.emit(
            SubAgentUpdate.Started(progress)
        )
        
        notificationManager.showSubAgentProgressNotification(
            projectName = projectId,
            agentType = info.agentType.displayName,
            progress = 0,
            currentTask = info.initialTask
        )
    }
    
    private suspend fun updateSubAgentProgress(projectId: String, update: SubAgentProgressUpdate) {
        val agent = activeSubAgents[update.agentId] ?: return
        
        val updatedAgent = agent.copy(
            status = update.status ?: agent.status,
            progress = update.progress ?: agent.progress,
            currentTask = update.currentTask ?: agent.currentTask,
            completionTime = if (update.status?.isTerminal() == true) {
                System.currentTimeMillis()
            } else {
                agent.completionTime
            }
        )
        
        activeSubAgents[update.agentId] = updatedAgent
        
        _subAgentUpdates.emit(
            when (update.status) {
                SubAgentStatus.COMPLETED -> SubAgentUpdate.Completed(updatedAgent)
                SubAgentStatus.FAILED -> SubAgentUpdate.Failed(updatedAgent)
                else -> SubAgentUpdate.Progress(updatedAgent)
            }
        )
        
        // Update notification
        if (updatedAgent.status == SubAgentStatus.RUNNING) {
            notificationManager.showSubAgentProgressNotification(
                projectName = projectId,
                agentType = updatedAgent.agentType.displayName,
                progress = (updatedAgent.progress * 100).toInt(),
                currentTask = updatedAgent.currentTask
            )
        }
    }
    
    private fun parseSubAgentInfo(message: ClaudeMessage): SubAgentInfo? {
        // Pattern matching for sub-agent creation
        val patterns = listOf(
            Regex("""Starting (\w+) agent for (.+)$""", RegexOption.MULTILINE),
            Regex("""Delegating to (\w+) agent: (.+)$""", RegexOption.MULTILINE),
            Regex("""\[Agent:(\w+)\] Starting: (.+)$""", RegexOption.MULTILINE)
        )
        
        for (pattern in patterns) {
            pattern.find(message.content)?.let { match ->
                val agentType = match.groupValues[1]
                val task = match.groupValues[2]
                
                return SubAgentInfo(
                    agentId = "${message.id}_${agentType.lowercase()}",
                    agentType = SubAgentType.fromString(agentType),
                    parentAgentId = message.parentAgentId,
                    initialTask = task
                )
            }
        }
        
        return null
    }
    
    private fun parseSubAgentProgress(message: ClaudeMessage): SubAgentProgressUpdate? {
        // Extract progress from agent messages
        val progressInfo = progressParser.parseProgress(message) ?: return null
        
        // Determine which agent this belongs to
        val agentId = message.agentId ?: findAgentByContext(message) ?: return null
        
        return SubAgentProgressUpdate(
            agentId = agentId,
            progress = progressInfo.percentage / 100f,
            currentTask = progressInfo.description,
            status = when {
                progressInfo.percentage >= 100 -> SubAgentStatus.COMPLETED
                progressInfo.percentage > 0 -> SubAgentStatus.RUNNING
                else -> null
            }
        )
    }
    
    private fun findAgentByContext(message: ClaudeMessage): String? {
        // Match message content to active agents
        return activeSubAgents.values.firstOrNull { agent ->
            message.content.contains(agent.agentType.displayName, ignoreCase = true)
        }?.agentId
    }
    
    fun getActiveSubAgents(projectId: String): List<SubAgentProgress> {
        return activeSubAgents.values.filter { it.projectId == projectId }
    }
    
    fun getAgentHierarchy(projectId: String): SubAgentHierarchy {
        val agents = getActiveSubAgents(projectId)
        val rootAgents = agents.filter { it.parentAgentId == null }
        
        return SubAgentHierarchy(
            rootAgents = rootAgents,
            childrenMap = agents.groupBy { it.parentAgentId ?: "" }
        )
    }
    
    fun clearCompletedAgents(projectId: String) {
        val toRemove = activeSubAgents.values
            .filter { it.projectId == projectId && it.status.isTerminal() }
            .map { it.agentId }
        
        toRemove.forEach { activeSubAgents.remove(it) }
    }
}

data class SubAgentProgress(
    val agentId: String,
    val agentType: SubAgentType,
    val parentAgentId: String?,
    val projectId: String,
    val status: SubAgentStatus,
    val progress: Float,
    val currentTask: String,
    val startTime: Long,
    val completionTime: Long?
)

enum class SubAgentType(val displayName: String) {
    TESTING("Testing"),
    DEPLOYMENT("Deployment"),
    ANALYSIS("Analysis"),
    REFACTORING("Refactoring"),
    DOCUMENTATION("Documentation"),
    BUILD("Build"),
    SECURITY("Security"),
    PERFORMANCE("Performance"),
    UNKNOWN("Unknown");
    
    companion object {
        fun fromString(type: String): SubAgentType {
            return values().find { 
                it.name.equals(type, ignoreCase = true) ||
                it.displayName.equals(type, ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}

data class SubAgentInfo(
    val agentId: String,
    val agentType: SubAgentType,
    val parentAgentId: String?,
    val initialTask: String
)

data class SubAgentProgressUpdate(
    val agentId: String,
    val status: SubAgentStatus? = null,
    val progress: Float? = null,
    val currentTask: String? = null
)

sealed class SubAgentUpdate {
    data class Started(val agent: SubAgentProgress) : SubAgentUpdate()
    data class Progress(val agent: SubAgentProgress) : SubAgentUpdate()
    data class Completed(val agent: SubAgentProgress) : SubAgentUpdate()
    data class Failed(val agent: SubAgentProgress) : SubAgentUpdate()
}

data class SubAgentHierarchy(
    val rootAgents: List<SubAgentProgress>,
    val childrenMap: Map<String, List<SubAgentProgress>>
)

data class ClaudeMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val agentId: String? = null,
    val parentAgentId: String? = null
)
```

### Server Resource Monitor

**Purpose**: Monitors system resource usage on the remote development server including CPU, memory, and disk usage. Helps identify performance issues and prevent resource exhaustion during intensive Claude Code operations.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerResourceMonitor @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val notificationManager: PocketAgentNotificationManager
) {
    
    companion object {
        const val CPU_THRESHOLD_WARNING = 80f
        const val CPU_THRESHOLD_CRITICAL = 95f
        const val MEMORY_THRESHOLD_WARNING = 80f
        const val MEMORY_THRESHOLD_CRITICAL = 95f
        const val DISK_THRESHOLD_WARNING = 85f
        const val DISK_THRESHOLD_CRITICAL = 95f
    }
    
    private val _resourceMetrics = MutableStateFlow<Map<String, ServerResourceMetrics>>(emptyMap())
    val resourceMetrics: StateFlow<Map<String, ServerResourceMetrics>> = _resourceMetrics.asStateFlow()
    
    suspend fun checkServerResources(projectId: String): ServerResourceMetrics? {
        return try {
            val response = withTimeoutOrNull(5000L) {
                webSocketManager.sendCommand(
                    projectId = projectId,
                    command = ServerCommand.GET_RESOURCES
                )
            }
            
            response?.let { parseResourceMetrics(it) }?.also { metrics ->
                updateMetrics(projectId, metrics)
                checkResourceThresholds(projectId, metrics)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateMetrics(projectId: String, metrics: ServerResourceMetrics) {
        _resourceMetrics.update { current ->
            current + (projectId to metrics)
        }
    }
    
    private suspend fun checkResourceThresholds(projectId: String, metrics: ServerResourceMetrics) {
        val warnings = mutableListOf<String>()
        val critical = mutableListOf<String>()
        
        // Check CPU
        when {
            metrics.cpuUsage > CPU_THRESHOLD_CRITICAL -> {
                critical.add("CPU: ${metrics.cpuUsage.toInt()}%")
            }
            metrics.cpuUsage > CPU_THRESHOLD_WARNING -> {
                warnings.add("CPU: ${metrics.cpuUsage.toInt()}%")
            }
        }
        
        // Check Memory
        val memoryPercentage = (metrics.memoryUsed.toFloat() / metrics.memoryTotal * 100)
        when {
            memoryPercentage > MEMORY_THRESHOLD_CRITICAL -> {
                critical.add("Memory: ${memoryPercentage.toInt()}%")
            }
            memoryPercentage > MEMORY_THRESHOLD_WARNING -> {
                warnings.add("Memory: ${memoryPercentage.toInt()}%")
            }
        }
        
        // Check Disk
        val diskPercentage = (metrics.diskUsed.toFloat() / metrics.diskTotal * 100)
        when {
            diskPercentage > DISK_THRESHOLD_CRITICAL -> {
                critical.add("Disk: ${diskPercentage.toInt()}%")
            }
            diskPercentage > DISK_THRESHOLD_WARNING -> {
                warnings.add("Disk: ${diskPercentage.toInt()}%")
            }
        }
        
        // Send notifications if needed
        if (critical.isNotEmpty()) {
            notificationManager.showErrorNotification(
                projectName = projectId,
                error = "Critical resource usage: ${critical.joinToString(", ")}",
                canRetry = false
            )
        } else if (warnings.isNotEmpty() && shouldNotifyWarning(projectId)) {
            notificationManager.showConnectionNotification(
                projectName = projectId,
                status = "High resource usage: ${warnings.joinToString(", ")}"
            )
        }
    }
    
    private fun shouldNotifyWarning(projectId: String): Boolean {
        // Throttle warning notifications to once per hour
        val lastNotification = lastWarningNotifications[projectId] ?: 0L
        val now = System.currentTimeMillis()
        
        return if (now - lastNotification > 3600_000L) {
            lastWarningNotifications[projectId] = now
            true
        } else {
            false
        }
    }
    
    private val lastWarningNotifications = mutableMapOf<String, Long>()
    
    private fun parseResourceMetrics(response: WebSocketResponse): ServerResourceMetrics? {
        return try {
            val data = response.data as? Map<*, *> ?: return null
            
            ServerResourceMetrics(
                cpuUsage = (data["cpu"] as? Number)?.toFloat() ?: 0f,
                memoryUsed = (data["memoryUsed"] as? Number)?.toLong() ?: 0L,
                memoryTotal = (data["memoryTotal"] as? Number)?.toLong() ?: 1L,
                diskUsed = (data["diskUsed"] as? Number)?.toLong() ?: 0L,
                diskTotal = (data["diskTotal"] as? Number)?.toLong() ?: 1L,
                networkBandwidth = (data["networkBandwidth"] as? Number)?.toLong() ?: 0L,
                processCount = (data["processCount"] as? Number)?.toInt() ?: 0,
                loadAverage = (data["loadAverage"] as? List<*>)?.mapNotNull { 
                    (it as? Number)?.toDouble() 
                } ?: emptyList(),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getResourceHistory(projectId: String, duration: Long = 3600_000L): List<ServerResourceMetrics> {
        // In a real implementation, this would query stored metrics
        val current = _resourceMetrics.value[projectId]
        return listOfNotNull(current)
    }
    
    fun clearMetrics(projectId: String) {
        _resourceMetrics.update { current ->
            current - projectId
        }
        lastWarningNotifications.remove(projectId)
    }
}

data class ServerResourceMetrics(
    val cpuUsage: Float,          // Percentage (0-100)
    val memoryUsed: Long,         // Bytes
    val memoryTotal: Long,        // Bytes
    val diskUsed: Long,           // Bytes
    val diskTotal: Long,          // Bytes
    val networkBandwidth: Long,   // Bytes per second
    val processCount: Int,        // Number of processes
    val loadAverage: List<Double>, // 1, 5, 15 minute averages
    val timestamp: Long
)

enum class ServerCommand {
    GET_RESOURCES,
    GET_PROCESS_LIST,
    KILL_PROCESS,
    RESTART_SERVICE
}

data class WebSocketResponse(
    val command: String,
    val success: Boolean,
    val data: Any?,
    val error: String?
)
```

### Progress Parser

**Purpose**: Extracts progress information from Claude messages by identifying patterns like step counts, percentages, and task descriptions. Enables accurate progress tracking for long-running operations.

```kotlin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressParser @Inject constructor() {
    
    private val progressPatterns = listOf(
        // Step X of Y patterns
        Regex("""(?:Step|Task|Stage)\s+(\d+)\s+(?:of|/)\s+(\d+)(?:\s*[-:]\s*(.+))?$""", RegexOption.MULTILINE),
        // X/Y pattern
        Regex("""(\d+)/(\d+)\s+(?:completed|done|finished)(?:\s*[-:]\s*(.+))?$""", RegexOption.MULTILINE),
        // Percentage patterns
        Regex("""(\d+)%\s+(?:complete|completed|done|progress)(?:\s*[-:]\s*(.+))?$""", RegexOption.MULTILINE),
        // Progress bar patterns [=====>    ]
        Regex("""\[(=+)>?\s*\]\s*(\d+)?%?(?:\s*[-:]\s*(.+))?$""", RegexOption.MULTILINE),
        // Bullet progress (• Done: X, • In Progress: Y)
        Regex("""(?:Done|Completed):\s*(\d+).*?(?:Total|Remaining):\s*(\d+)""", RegexOption.DOTALL)
    )
    
    private val taskPatterns = listOf(
        Regex("""(?:Currently|Now)\s+(?:working on|processing|executing):\s*(.+)$""", RegexOption.MULTILINE),
        Regex("""(?:Task|Action|Operation):\s*(.+)$""", RegexOption.MULTILINE),
        Regex("""(?:->|→|▶)\s*(.+)$""", RegexOption.MULTILINE)
    )
    
    fun parseProgress(message: ClaudeMessage): TaskProgress? {
        val content = message.content
        
        // Try each pattern
        for ((index, pattern) in progressPatterns.withIndex()) {
            pattern.find(content)?.let { match ->
                return when (index) {
                    0, 1 -> { // Step/Task patterns
                        val current = match.groupValues[1].toIntOrNull() ?: return@let null
                        val total = match.groupValues[2].toIntOrNull() ?: return@let null
                        val description = match.groupValues.getOrNull(3)?.trim() ?: extractCurrentTask(content)
                        
                        TaskProgress(
                            current = current,
                            total = total,
                            percentage = (current * 100f / total).toInt(),
                            description = description,
                            isEstimated = false
                        )
                    }
                    2 -> { // Percentage pattern
                        val percentage = match.groupValues[1].toIntOrNull() ?: return@let null
                        val description = match.groupValues.getOrNull(2)?.trim() ?: extractCurrentTask(content)
                        
                        TaskProgress(
                            percentage = percentage,
                            description = description,
                            isEstimated = false
                        )
                    }
                    3 -> { // Progress bar pattern
                        val filled = match.groupValues[1].length
                        val total = match.value.count { it == '=' || it == ' ' || it == '>' }
                        val percentage = match.groupValues.getOrNull(2)?.toIntOrNull() 
                            ?: (filled * 100 / total)
                        val description = match.groupValues.getOrNull(3)?.trim() ?: extractCurrentTask(content)
                        
                        TaskProgress(
                            percentage = percentage,
                            description = description,
                            isEstimated = false
                        )
                    }
                    4 -> { // Bullet progress
                        val done = match.groupValues[1].toIntOrNull() ?: return@let null
                        val total = match.groupValues[2].toIntOrNull() ?: return@let null
                        
                        TaskProgress(
                            current = done,
                            total = done + total,
                            percentage = (done * 100f / (done + total)).toInt(),
                            description = extractCurrentTask(content),
                            isEstimated = false
                        )
                    }
                    else -> null
                }
            }
        }
        
        // Try to estimate progress from context
        return estimateProgressFromContext(content)
    }
    
    private fun extractCurrentTask(content: String): String {
        // Try to find current task description
        for (pattern in taskPatterns) {
            pattern.find(content)?.let { match ->
                return match.groupValues[1].trim()
                    .take(100) // Limit length
                    .replace("\n", " ")
                    .trim()
            }
        }
        
        // Fallback: extract first meaningful line
        return content.lines()
            .firstOrNull { line ->
                line.trim().length > 10 && 
                !line.contains("```") &&
                !line.startsWith("#")
            }
            ?.trim()
            ?.take(100)
            ?: "Processing..."
    }
    
    private fun estimateProgressFromContext(content: String): TaskProgress? {
        // Keywords that indicate progress
        val startKeywords = listOf("starting", "beginning", "initializing", "preparing")
        val middleKeywords = listOf("processing", "working on", "analyzing", "executing")
        val endKeywords = listOf("finishing", "completing", "finalizing", "done", "complete")
        
        val lowerContent = content.lowercase()
        
        return when {
            endKeywords.any { lowerContent.contains(it) } -> {
                TaskProgress(
                    percentage = 90,
                    description = "Finalizing changes",
                    isEstimated = true
                )
            }
            middleKeywords.any { lowerContent.contains(it) } -> {
                TaskProgress(
                    percentage = 50,
                    description = extractCurrentTask(content),
                    isEstimated = true
                )
            }
            startKeywords.any { lowerContent.contains(it) } -> {
                TaskProgress(
                    percentage = 10,
                    description = "Getting started",
                    isEstimated = true
                )
            }
            else -> null
        }
    }
    
    fun parseCompletionSummary(content: String): CompletionSummary? {
        // Extract summary of completed work
        val filesModifiedPattern = Regex("""(\d+)\s+files?\s+(?:modified|changed|updated)""", RegexOption.IGNORECASE)
        val testsRunPattern = Regex("""(\d+)\s+tests?\s+(?:passed|run|executed)""", RegexOption.IGNORECASE)
        val errorsPattern = Regex("""(\d+)\s+errors?\s+(?:found|detected|fixed)""", RegexOption.IGNORECASE)
        
        val filesModified = filesModifiedPattern.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val testsRun = testsRunPattern.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val errors = errorsPattern.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        return if (filesModified > 0 || testsRun > 0 || errors > 0) {
            CompletionSummary(
                filesModified = filesModified,
                testsRun = testsRun,
                errorCount = errors,
                summary = extractSummaryLine(content)
            )
        } else {
            null
        }
    }
    
    private fun extractSummaryLine(content: String): String {
        // Look for summary patterns
        val summaryPatterns = listOf(
            Regex("""Summary:\s*(.+)$""", RegexOption.MULTILINE),
            Regex("""Completed:\s*(.+)$""", RegexOption.MULTILINE),
            Regex("""Result:\s*(.+)$""", RegexOption.MULTILINE)
        )
        
        for (pattern in summaryPatterns) {
            pattern.find(content)?.let { match ->
                return match.groupValues[1].trim().take(200)
            }
        }
        
        return "Task completed"
    }
}

data class TaskProgress(
    val current: Int? = null,
    val total: Int? = null,
    val percentage: Int,
    val description: String,
    val isEstimated: Boolean = false
)

data class CompletionSummary(
    val filesModified: Int,
    val testsRun: Int,
    val errorCount: Int,
    val summary: String
)
```

### Background Voice Manager

**Purpose**: Provides voice announcements for important notifications when the app is in the background, enhancing accessibility and allowing users to stay informed without looking at their device.

```kotlin
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundVoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized
    
    private val utteranceQueue = mutableListOf<VoiceAnnouncement>()
    
    init {
        if (preferencesManager.isVoiceAnnouncementsEnabled) {
            initializeTts()
        }
    }
    
    private fun initializeTts() {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.getDefault())
                if (result != TextToSpeech.LANG_MISSING_DATA && 
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    _isInitialized.value = true
                    
                    // Set up utterance listener
                    engine.setOnUtteranceProgressListener(utteranceListener)
                    
                    // Process any queued announcements
                    processQueue()
                }
            }
        }
    }
    
    fun announceNotification(
        type: NotificationType,
        message: String,
        priority: AnnouncementPriority = AnnouncementPriority.NORMAL
    ) {
        if (!preferencesManager.isVoiceAnnouncementsEnabled) return
        
        val announcement = VoiceAnnouncement(
            id = "${type.name}_${System.currentTimeMillis()}",
            type = type,
            message = prepareMessage(type, message),
            priority = priority,
            timestamp = System.currentTimeMillis()
        )
        
        if (_isInitialized.value) {
            speak(announcement)
        } else {
            // Queue for when TTS is ready
            utteranceQueue.add(announcement)
            if (tts == null) {
                initializeTts()
            }
        }
    }
    
    private fun prepareMessage(type: NotificationType, message: String): String {
        return when (type) {
            NotificationType.PERMISSION_REQUEST -> {
                "Claude permission request: $message. Swipe down to respond."
            }
            NotificationType.TASK_COMPLETE -> {
                "Task completed: $message"
            }
            NotificationType.ERROR -> {
                "Error: $message"
            }
            NotificationType.CONNECTION_STATUS -> {
                message
            }
            NotificationType.PROGRESS -> {
                "Progress update: $message"
            }
            NotificationType.SUB_AGENT -> {
                "Agent update: $message"
            }
        }
    }
    
    private fun speak(announcement: VoiceAnnouncement) {
        tts?.let { engine ->
            // Configure speech parameters
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, getVolumeForPriority(announcement.priority))
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0f) // Center audio
            }
            
            val queueMode = when (announcement.priority) {
                AnnouncementPriority.HIGH -> TextToSpeech.QUEUE_FLUSH
                else -> TextToSpeech.QUEUE_ADD
            }
            
            engine.speak(
                announcement.message,
                queueMode,
                params,
                announcement.id
            )
        }
    }
    
    private fun getVolumeForPriority(priority: AnnouncementPriority): Float {
        return when (priority) {
            AnnouncementPriority.HIGH -> 1.0f
            AnnouncementPriority.NORMAL -> 0.8f
            AnnouncementPriority.LOW -> 0.6f
        }
    }
    
    private fun processQueue() {
        if (utteranceQueue.isNotEmpty()) {
            val sortedQueue = utteranceQueue.sortedByDescending { it.priority.ordinal }
            utteranceQueue.clear()
            
            sortedQueue.forEach { announcement ->
                speak(announcement)
            }
        }
    }
    
    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // Track speaking state if needed
        }
        
        override fun onDone(utteranceId: String?) {
            // Clean up completed announcements
        }
        
        override fun onError(utteranceId: String?) {
            // Handle TTS errors
        }
    }
    
    fun summarizeForSpeech(content: String, maxWords: Int = 50): String {
        // Smart summarization for long content
        val sentences = content.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        
        if (sentences.isEmpty()) return content.take(200)
        
        val summary = StringBuilder()
        var wordCount = 0
        
        for (sentence in sentences) {
            val words = sentence.split(" ")
            if (wordCount + words.size <= maxWords) {
                summary.append(sentence).append(". ")
                wordCount += words.size
            } else if (wordCount < maxWords / 2) {
                // Include at least some content
                val remainingWords = maxWords - wordCount
                summary.append(words.take(remainingWords).joinToString(" "))
                    .append("...")
                break
            } else {
                break
            }
        }
        
        return summary.toString().trim()
    }
    
    fun stop() {
        tts?.stop()
        utteranceQueue.clear()
    }
    
    fun shutdown() {
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
    
    fun setEnabled(enabled: Boolean) {
        if (enabled && tts == null) {
            initializeTts()
        } else if (!enabled) {
            shutdown()
        }
    }
}

data class VoiceAnnouncement(
    val id: String,
    val type: NotificationType,
    val message: String,
    val priority: AnnouncementPriority,
    val timestamp: Long
)

enum class NotificationType {
    PERMISSION_REQUEST,
    TASK_COMPLETE,
    ERROR,
    CONNECTION_STATUS,
    PROGRESS,
    SUB_AGENT
}

enum class AnnouncementPriority {
    LOW,
    NORMAL,
    HIGH
}

// Extension for preferences
interface PreferencesManager {
    val isVoiceAnnouncementsEnabled: Boolean
    val voiceAnnouncementTypes: Set<NotificationType>
    fun setVoiceAnnouncementsEnabled(enabled: Boolean)
    fun setVoiceAnnouncementTypes(types: Set<NotificationType>)
}
```

### Session State Persistence

**Purpose**: Manages the persistence and restoration of session state across app lifecycle events, process death, and device reboots. Ensures that active Claude Code sessions can be resumed seamlessly when the app restarts.

```kotlin
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_state")

@Singleton
class SessionStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager
) {
    
    private object PreferencesKeys {
        val ACTIVE_SESSIONS = stringPreferencesKey("active_sessions")
        val SESSION_PREFIX = "session_"
        
        fun sessionKey(projectId: String) = stringPreferencesKey("$SESSION_PREFIX$projectId")
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun saveSessionState(session: SessionState) {
        context.sessionDataStore.edit { preferences ->
            // Encrypt sensitive session data
            val encryptedSession = encryptSessionData(session)
            val sessionJson = json.encodeToString(encryptedSession)
            
            // Save individual session
            preferences[PreferencesKeys.sessionKey(session.projectId)] = sessionJson
            
            // Update active sessions list
            val activeSessions = getActiveSessionIds(preferences).toMutableSet()
            activeSessions.add(session.projectId)
            preferences[PreferencesKeys.ACTIVE_SESSIONS] = activeSessions.joinToString(",")
        }
    }
    
    suspend fun restoreSessionState(projectId: String): SessionState? {
        return context.sessionDataStore.data
            .map { preferences ->
                val sessionJson = preferences[PreferencesKeys.sessionKey(projectId)]
                sessionJson?.let {
                    try {
                        val encryptedSession = json.decodeFromString<EncryptedSessionState>(it)
                        decryptSessionData(encryptedSession)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .firstOrNull()
    }
    
    suspend fun getAllActiveSessions(): List<SessionState> {
        return context.sessionDataStore.data
            .map { preferences ->
                val activeIds = getActiveSessionIds(preferences)
                activeIds.mapNotNull { projectId ->
                    val sessionJson = preferences[PreferencesKeys.sessionKey(projectId)]
                    sessionJson?.let {
                        try {
                            val encryptedSession = json.decodeFromString<EncryptedSessionState>(it)
                            decryptSessionData(encryptedSession)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
            .firstOrNull() ?: emptyList()
    }
    
    suspend fun removeSessionState(projectId: String) {
        context.sessionDataStore.edit { preferences ->
            // Remove session data
            preferences.remove(PreferencesKeys.sessionKey(projectId))
            
            // Update active sessions list
            val activeSessions = getActiveSessionIds(preferences).toMutableSet()
            activeSessions.remove(projectId)
            preferences[PreferencesKeys.ACTIVE_SESSIONS] = activeSessions.joinToString(",")
        }
    }
    
    suspend fun clearAllSessions() {
        context.sessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    private fun getActiveSessionIds(preferences: Preferences): Set<String> {
        val sessionsList = preferences[PreferencesKeys.ACTIVE_SESSIONS] ?: ""
        return if (sessionsList.isNotEmpty()) {
            sessionsList.split(",").toSet()
        } else {
            emptySet()
        }
    }
    
    private suspend fun encryptSessionData(session: SessionState): EncryptedSessionState {
        return EncryptedSessionState(
            projectId = session.projectId,
            encryptedData = encryptionManager.encrypt(
                json.encodeToString(session.sensitiveData)
            ),
            metadata = session.metadata,
            timestamp = session.timestamp
        )
    }
    
    private suspend fun decryptSessionData(encrypted: EncryptedSessionState): SessionState {
        val sensitiveData = json.decodeFromString<SensitiveSessionData>(
            encryptionManager.decrypt(encrypted.encryptedData)
        )
        
        return SessionState(
            projectId = encrypted.projectId,
            sensitiveData = sensitiveData,
            metadata = encrypted.metadata,
            timestamp = encrypted.timestamp
        )
    }
    
    // Auto-save active sessions periodically
    fun startAutoSave(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(30_000L) // Save every 30 seconds
                saveCurrentActiveSessions()
            }
        }
    }
    
    private suspend fun saveCurrentActiveSessions() {
        // This would be called by the service to save current state
        // Implementation depends on how sessions are tracked in the app
    }
}

@Serializable
data class SessionState(
    val projectId: String,
    val sensitiveData: SensitiveSessionData,
    val metadata: SessionMetadata,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SensitiveSessionData(
    val claudeSessionId: String,
    val wrapperSessionToken: String?,
    val lastMessageId: String?,
    val pendingPermissions: List<PendingPermission> = emptyList()
)

@Serializable
data class SessionMetadata(
    val projectName: String,
    val serverName: String,
    val connectionStatus: String,
    val lastActivity: Long,
    val messageCount: Int
)

@Serializable
data class EncryptedSessionState(
    val projectId: String,
    val encryptedData: String,
    val metadata: SessionMetadata,
    val timestamp: Long
)

@Serializable
data class PendingPermission(
    val requestId: String,
    val tool: String,
    val action: String,
    val timestamp: Long
)

// Session restoration helper
@Singleton
class SessionRestorationHelper @Inject constructor(
    private val sessionStateManager: SessionStateManager,
    private val connectionManager: ConnectionManager,
    private val notificationManager: PocketAgentNotificationManager
) {
    
    suspend fun restoreActiveSessions() {
        val sessions = sessionStateManager.getAllActiveSessions()
        
        sessions.forEach { session ->
            try {
                // Attempt to restore connection
                val restored = connectionManager.restoreSession(
                    projectId = session.projectId,
                    claudeSessionId = session.sensitiveData.claudeSessionId,
                    lastMessageId = session.sensitiveData.lastMessageId
                )
                
                if (restored) {
                    // Handle any pending permissions
                    session.sensitiveData.pendingPermissions.forEach { permission ->
                        notificationManager.showPermissionRequestNotification(
                            projectName = session.metadata.projectName,
                            tool = permission.tool,
                            action = permission.action,
                            requestId = permission.requestId,
                            timeout = 300 // 5 minutes
                        )
                    }
                }
            } catch (e: Exception) {
                // Log restoration failure
                sessionStateManager.removeSessionState(session.projectId)
            }
        }
    }
    
    suspend fun saveSessionBeforeTermination(projectId: String) {
        val currentState = connectionManager.getSessionState(projectId)
        currentState?.let { state ->
            sessionStateManager.saveSessionState(state)
        }
    }
}
```

### Background Task Scheduler

**Purpose**: Coordinates and schedules various background tasks, ensuring they run at appropriate times based on system constraints, battery state, and user preferences. Provides a unified interface for scheduling one-time and periodic background operations.

```kotlin
import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundTaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val batteryOptimizationManager: BatteryOptimizationManager
) {
    
    companion object {
        const val TAG_BACKGROUND_TASK = "background_task"
        const val TAG_USER_TRIGGERED = "user_triggered"
        const val TAG_SYSTEM_TRIGGERED = "system_triggered"
        
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_PRIORITY = "priority"
    }
    
    fun scheduleImmediateTask(
        taskType: BackgroundTaskType,
        projectId: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        inputData: Data = Data.EMPTY
    ): UUID {
        val constraints = batteryOptimizationManager.getBackgroundTaskConstraints(priority)
        
        val workData = Data.Builder()
            .putString(KEY_TASK_TYPE, taskType.name)
            .putString(KEY_PROJECT_ID, projectId ?: "")
            .putString(KEY_PRIORITY, priority.name)
            .putAll(inputData)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
            .setInputData(workData)
            .setConstraints(buildConstraints(constraints))
            .addTag(TAG_BACKGROUND_TASK)
            .addTag(TAG_USER_TRIGGERED)
            .addTag(taskType.name)
            .apply {
                if (priority == TaskPriority.CRITICAL) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        
        workManager.enqueue(workRequest)
        return workRequest.id
    }
    
    fun scheduleDelayedTask(
        taskType: BackgroundTaskType,
        delay: Long,
        timeUnit: TimeUnit,
        projectId: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL
    ): UUID {
        val constraints = batteryOptimizationManager.getBackgroundTaskConstraints(priority)
        
        val workData = Data.Builder()
            .putString(KEY_TASK_TYPE, taskType.name)
            .putString(KEY_PROJECT_ID, projectId ?: "")
            .putString(KEY_PRIORITY, priority.name)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, timeUnit)
            .setConstraints(buildConstraints(constraints))
            .addTag(TAG_BACKGROUND_TASK)
            .addTag(TAG_SYSTEM_TRIGGERED)
            .addTag(taskType.name)
            .build()
        
        workManager.enqueue(workRequest)
        return workRequest.id
    }
    
    fun schedulePeriodicTask(
        taskType: BackgroundTaskType,
        interval: Long,
        timeUnit: TimeUnit,
        flexInterval: Long = interval / 4,
        projectId: String? = null
    ) {
        val workData = Data.Builder()
            .putString(KEY_TASK_TYPE, taskType.name)
            .putString(KEY_PROJECT_ID, projectId ?: "")
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<BackgroundTaskWorker>(
            interval, timeUnit,
            flexInterval, timeUnit
        )
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag(TAG_BACKGROUND_TASK)
            .addTag(taskType.name)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "${taskType.name}_${projectId ?: "global"}",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    fun cancelTask(taskId: UUID) {
        workManager.cancelWorkById(taskId)
    }
    
    fun cancelTasksForProject(projectId: String) {
        workManager.cancelAllWorkByTag("project_$projectId")
    }
    
    fun cancelTasksByType(taskType: BackgroundTaskType) {
        workManager.cancelAllWorkByTag(taskType.name)
    }
    
    fun getTaskStatus(taskId: UUID): Flow<WorkInfo.State> {
        return workManager.getWorkInfoByIdFlow(taskId)
            .map { workInfo -> workInfo.state }
    }
    
    fun getAllScheduledTasks(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosByTagFlow(TAG_BACKGROUND_TASK)
    }
    
    private fun buildConstraints(taskConstraints: TaskConstraints): Constraints {
        return Constraints.Builder().apply {
            setRequiresCharging(taskConstraints.requiresCharging)
            setRequiresBatteryNotLow(taskConstraints.requiresBatteryNotLow)
            
            // Add network constraint based on task requirements
            if (taskConstraints.requiresCharging) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            
            // Don't run during device idle for critical tasks
            if (taskConstraints.maxExecutionTime < 30_000L) {
                setRequiresDeviceIdle(false)
            }
        }.build()
    }
    
    // Specific task scheduling methods
    
    fun scheduleSyncTask(projectId: String, immediate: Boolean = false) {
        if (immediate) {
            scheduleImmediateTask(
                taskType = BackgroundTaskType.SYNC_MESSAGES,
                projectId = projectId,
                priority = TaskPriority.HIGH
            )
        } else {
            scheduleDelayedTask(
                taskType = BackgroundTaskType.SYNC_MESSAGES,
                delay = 5,
                timeUnit = TimeUnit.MINUTES,
                projectId = projectId,
                priority = TaskPriority.NORMAL
            )
        }
    }
    
    fun scheduleLogUpload(priority: TaskPriority = TaskPriority.LOW) {
        scheduleDelayedTask(
            taskType = BackgroundTaskType.UPLOAD_LOGS,
            delay = 1,
            timeUnit = TimeUnit.HOURS,
            priority = priority
        )
    }
    
    fun scheduleHealthCheck(projectId: String) {
        scheduleImmediateTask(
            taskType = BackgroundTaskType.HEALTH_CHECK,
            projectId = projectId,
            priority = TaskPriority.HIGH
        )
    }
    
    fun scheduleSessionBackup(projectId: String) {
        scheduleDelayedTask(
            taskType = BackgroundTaskType.BACKUP_SESSION,
            delay = 30,
            timeUnit = TimeUnit.SECONDS,
            projectId = projectId,
            priority = TaskPriority.NORMAL
        )
    }
}

enum class BackgroundTaskType {
    SYNC_MESSAGES,
    HEALTH_CHECK,
    BACKUP_SESSION,
    CLEANUP_CACHE,
    UPLOAD_LOGS,
    REFRESH_TOKENS,
    CHECK_UPDATES,
    PRUNE_DATABASE,
    EXPORT_DATA
}

enum class SubAgentStatus(val displayName: String) {
    STARTING("Starting"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    FAILED("Failed");
    
    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED
}

// Background task worker implementation
@HiltWorker
class BackgroundTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskExecutor: BackgroundTaskExecutor
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        val taskType = inputData.getString(BackgroundTaskScheduler.KEY_TASK_TYPE)
            ?: return Result.failure()
        val projectId = inputData.getString(BackgroundTaskScheduler.KEY_PROJECT_ID)
            ?.takeIf { it.isNotEmpty() }
        val priority = inputData.getString(BackgroundTaskScheduler.KEY_PRIORITY)
            ?.let { TaskPriority.valueOf(it) }
            ?: TaskPriority.NORMAL
        
        return try {
            val backgroundTaskType = BackgroundTaskType.valueOf(taskType)
            val result = taskExecutor.executeTask(
                taskType = backgroundTaskType,
                projectId = projectId,
                priority = priority,
                inputData = inputData
            )
            
            when (result) {
                is TaskResult.Success -> Result.success(result.outputData)
                is TaskResult.Retry -> Result.retry()
                is TaskResult.Failure -> Result.failure(result.outputData)
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, workerParams: WorkerParameters): BackgroundTaskWorker
    }
}

// Task executor
@Singleton
class BackgroundTaskExecutor @Inject constructor(
    private val messageSync: MessageSyncManager,
    private val healthMonitor: ConnectionHealthMonitor,
    private val sessionBackup: SessionBackupManager,
    private val cacheManager: CacheManager,
    private val logUploader: LogUploader
) {
    
    suspend fun executeTask(
        taskType: BackgroundTaskType,
        projectId: String?,
        priority: TaskPriority,
        inputData: Data
    ): TaskResult {
        return when (taskType) {
            BackgroundTaskType.SYNC_MESSAGES -> {
                projectId?.let {
                    val synced = messageSync.syncMessages(it)
                    if (synced) {
                        TaskResult.Success(workDataOf("synced_count" to synced))
                    } else {
                        TaskResult.Retry
                    }
                } ?: TaskResult.Failure()
            }
            
            BackgroundTaskType.HEALTH_CHECK -> {
                projectId?.let {
                    healthMonitor.performHealthCheck(it)
                    TaskResult.Success()
                } ?: TaskResult.Failure()
            }
            
            BackgroundTaskType.BACKUP_SESSION -> {
                projectId?.let {
                    val backed = sessionBackup.backupSession(it)
                    if (backed) TaskResult.Success() else TaskResult.Retry
                } ?: TaskResult.Failure()
            }
            
            BackgroundTaskType.CLEANUP_CACHE -> {
                cacheManager.performCleanup()
                TaskResult.Success()
            }
            
            BackgroundTaskType.UPLOAD_LOGS -> {
                val uploaded = logUploader.uploadPendingLogs()
                if (uploaded) TaskResult.Success() else TaskResult.Retry
            }
            
            else -> TaskResult.Failure()
        }
    }
}

sealed class TaskResult {
    data class Success(val outputData: Data = Data.EMPTY) : TaskResult()
    object Retry : TaskResult()
    data class Failure(val outputData: Data = Data.EMPTY) : TaskResult()
}
```

### Error Handling

**Purpose**: Comprehensive error handling for background services, including retry strategies, error reporting, and graceful degradation when services fail. Ensures the app remains stable even when background operations encounter issues.

```kotlin
import android.content.Context
import kotlinx.coroutines.*
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

sealed class BackgroundServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ServiceStartException(message: String, cause: Throwable? = null) : BackgroundServiceException(message, cause)
    class ConnectionException(message: String, cause: Throwable? = null) : BackgroundServiceException(message, cause)
    class NotificationException(message: String, cause: Throwable? = null) : BackgroundServiceException(message, cause)
    class PermissionException(message: String) : BackgroundServiceException(message)
    class BatteryOptimizationException(message: String) : BackgroundServiceException(message)
}

@Singleton
class BackgroundErrorHandler @Inject constructor(
    private val context: Context,
    private val crashReporter: CrashReporter,
    private val analyticsTracker: AnalyticsTracker
) : CoroutineExceptionHandler {
    
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler
    
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        when (exception) {
            is CancellationException -> {
                // Normal cancellation, don't report
            }
            is BackgroundServiceException -> {
                handleServiceException(exception)
            }
            is IOException -> {
                handleNetworkException(exception)
            }
            else -> {
                handleUnexpectedException(exception)
            }
        }
    }
    
    private fun handleServiceException(exception: BackgroundServiceException) {
        when (exception) {
            is BackgroundServiceException.ServiceStartException -> {
                analyticsTracker.trackError("service_start_failed", exception)
                // Try to recover by restarting service
            }
            is BackgroundServiceException.ConnectionException -> {
                analyticsTracker.trackError("connection_failed", exception)
                // Schedule retry
            }
            is BackgroundServiceException.NotificationException -> {
                // Non-critical, log and continue
                logError("Notification failed", exception)
            }
            is BackgroundServiceException.PermissionException -> {
                analyticsTracker.trackError("permission_denied", exception)
                // Notify user about missing permissions
            }
            is BackgroundServiceException.BatteryOptimizationException -> {
                // Adjust behavior for battery optimization
                logError("Battery optimization issue", exception)
            }
        }
    }
    
    private fun handleNetworkException(exception: IOException) {
        when (exception) {
            is UnknownHostException -> {
                // No network connection
                logError("Network unavailable", exception)
            }
            else -> {
                // Other IO errors
                analyticsTracker.trackError("io_error", exception)
            }
        }
    }
    
    private fun handleUnexpectedException(exception: Throwable) {
        crashReporter.reportCrash(exception)
        analyticsTracker.trackError("unexpected_error", exception)
    }
    
    private fun logError(message: String, exception: Throwable) {
        // Log to local storage for debugging
    }
}

// Retry policy for background operations
class BackgroundRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Long = 1000L,
    val maxDelay: Long = 30000L,
    val factor: Double = 2.0,
    val jitter: Boolean = true
) {
    fun calculateDelay(attempt: Int): Long {
        val exponentialDelay = (initialDelay * factor.pow(attempt - 1)).toLong()
        val clampedDelay = exponentialDelay.coerceAtMost(maxDelay)
        
        return if (jitter) {
            // Add random jitter to prevent thundering herd
            val jitterAmount = (clampedDelay * 0.1).toLong()
            clampedDelay + (-jitterAmount..jitterAmount).random()
        } else {
            clampedDelay
        }
    }
    
    fun shouldRetry(attempt: Int, exception: Throwable): Boolean {
        return attempt < maxAttempts && isRetryableException(exception)
    }
    
    private fun isRetryableException(exception: Throwable): Boolean {
        return when (exception) {
            is IOException -> true
            is BackgroundServiceException.ConnectionException -> true
            is BackgroundServiceException.ServiceStartException -> false
            else -> false
        }
    }
}

// Resilient background operation executor
suspend fun <T> withBackgroundRetry(
    policy: BackgroundRetryPolicy = BackgroundRetryPolicy(),
    onError: (Throwable) -> Unit = {},
    operation: suspend () -> T
): Result<T> {
    var lastException: Throwable? = null
    
    repeat(policy.maxAttempts) { attempt ->
        try {
            return Result.success(operation())
        } catch (e: CancellationException) {
            throw e // Don't retry cancellations
        } catch (e: Throwable) {
            lastException = e
            onError(e)
            
            if (!policy.shouldRetry(attempt + 1, e)) {
                return Result.failure(e)
            }
            
            if (attempt < policy.maxAttempts - 1) {
                delay(policy.calculateDelay(attempt + 1))
            }
        }
    }
    
    return Result.failure(lastException ?: Exception("Unknown error"))
}

// Service recovery manager
@Singleton
class ServiceRecoveryManager @Inject constructor(
    private val context: Context,
    private val notificationManager: PocketAgentNotificationManager
) {
    
    fun handleServiceCrash(serviceName: String, exception: Throwable) {
        // Log crash details
        logServiceCrash(serviceName, exception)
        
        // Notify user if critical service
        if (isCriticalService(serviceName)) {
            notificationManager.showErrorNotification(
                projectName = "System",
                error = "$serviceName stopped unexpectedly",
                canRetry = true
            )
        }
        
        // Schedule service restart
        scheduleServiceRestart(serviceName)
    }
    
    private fun logServiceCrash(serviceName: String, exception: Throwable) {
        // Implementation for crash logging
    }
    
    private fun isCriticalService(serviceName: String): Boolean {
        return serviceName in listOf(
            "ClaudeBackgroundService",
            "ConnectionHealthMonitor"
        )
    }
    
    private fun scheduleServiceRestart(serviceName: String) {
        // Implementation for service restart scheduling
    }
}
```

### Default Permission Policies

**Purpose**: Implements default permission policies for Claude Code operations, providing automatic approval/denial based on predefined rules. This ensures consistent behavior when the user is not available to respond to permission requests and improves the user experience.

```kotlin
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DefaultPermissionPolicyManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        // Permission categories
        const val CATEGORY_FILE_READ = "file_read"
        const val CATEGORY_FILE_WRITE = "file_write"
        const val CATEGORY_SHELL_COMMAND = "shell_command"
        const val CATEGORY_GIT_OPERATION = "git_operation"
        const val CATEGORY_BUILD_COMMAND = "build_command"
        const val CATEGORY_TEST_COMMAND = "test_command"
        const val CATEGORY_INSTALL_COMMAND = "install_command"
        const val CATEGORY_NETWORK_REQUEST = "network_request"
        
        // Default timeout (30 seconds)
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
    
    private val _policies = MutableStateFlow<Map<String, PermissionPolicy>>(loadDefaultPolicies())
    val policies: Flow<Map<String, PermissionPolicy>> = _policies.asStateFlow()
    
    init {
        loadUserPolicies()
    }
    
    private fun loadDefaultPolicies(): Map<String, PermissionPolicy> {
        return mapOf(
            CATEGORY_FILE_READ to PermissionPolicy(
                category = CATEGORY_FILE_READ,
                defaultAction = PolicyAction.ALLOW,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = ".*\\.env.*", action = PolicyAction.PROMPT), // Always prompt for env files
                    PolicyRule(pattern = ".*password.*", action = PolicyAction.PROMPT),
                    PolicyRule(pattern = ".*secret.*", action = PolicyAction.PROMPT),
                    PolicyRule(pattern = ".*token.*", action = PolicyAction.PROMPT)
                )
            ),
            CATEGORY_FILE_WRITE to PermissionPolicy(
                category = CATEGORY_FILE_WRITE,
                defaultAction = PolicyAction.PROMPT,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = ".*\\.test\\..*", action = PolicyAction.ALLOW), // Allow test files
                    PolicyRule(pattern = ".*\\.spec\\..*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*README.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*\\.gitignore", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = "/etc/.*", action = PolicyAction.DENY), // Deny system files
                    PolicyRule(pattern = "/usr/.*", action = PolicyAction.DENY),
                    PolicyRule(pattern = "~/\\.*", action = PolicyAction.PROMPT) // Prompt for dotfiles
                )
            ),
            CATEGORY_SHELL_COMMAND to PermissionPolicy(
                category = CATEGORY_SHELL_COMMAND,
                defaultAction = PolicyAction.PROMPT,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = "^ls\\s.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = "^cat\\s.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = "^echo\\s.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = "^pwd$", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = "^cd\\s.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*rm\\s+-rf.*", action = PolicyAction.DENY), // Dangerous
                    PolicyRule(pattern = ".*sudo.*", action = PolicyAction.DENY),
                    PolicyRule(pattern = ".*chmod.*777.*", action = PolicyAction.DENY)
                )
            ),
            CATEGORY_GIT_OPERATION to PermissionPolicy(
                category = CATEGORY_GIT_OPERATION,
                defaultAction = PolicyAction.ALLOW,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = ".*push.*--force.*", action = PolicyAction.PROMPT),
                    PolicyRule(pattern = ".*reset.*--hard.*", action = PolicyAction.PROMPT)
                )
            ),
            CATEGORY_BUILD_COMMAND to PermissionPolicy(
                category = CATEGORY_BUILD_COMMAND,
                defaultAction = PolicyAction.ALLOW,
                timeout = 60_000L, // 1 minute for builds
                rules = emptyList()
            ),
            CATEGORY_TEST_COMMAND to PermissionPolicy(
                category = CATEGORY_TEST_COMMAND,
                defaultAction = PolicyAction.ALLOW,
                timeout = 120_000L, // 2 minutes for tests
                rules = emptyList()
            ),
            CATEGORY_INSTALL_COMMAND to PermissionPolicy(
                category = CATEGORY_INSTALL_COMMAND,
                defaultAction = PolicyAction.PROMPT,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = ".*npm\\s+install.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*pip\\s+install.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*apt.*install.*", action = PolicyAction.PROMPT),
                    PolicyRule(pattern = ".*brew\\s+install.*", action = PolicyAction.PROMPT)
                )
            ),
            CATEGORY_NETWORK_REQUEST to PermissionPolicy(
                category = CATEGORY_NETWORK_REQUEST,
                defaultAction = PolicyAction.PROMPT,
                timeout = DEFAULT_TIMEOUT_MS,
                rules = listOf(
                    PolicyRule(pattern = ".*localhost.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*127\\.0\\.0\\.1.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*github\\.com.*", action = PolicyAction.ALLOW),
                    PolicyRule(pattern = ".*googleapis\\.com.*", action = PolicyAction.ALLOW)
                )
            )
        )
    }
    
    private fun loadUserPolicies() {
        // Load user-customized policies from preferences
        preferencesManager.getPermissionPolicies()?.let { userPolicies ->
            _policies.value = _policies.value + userPolicies
        }
    }
    
    fun evaluatePermission(
        category: String,
        operation: String,
        details: Map<String, Any> = emptyMap()
    ): PermissionDecision {
        val policy = _policies.value[category] ?: return PermissionDecision(
            action = PolicyAction.PROMPT,
            reason = "No policy defined for category: $category",
            timeout = DEFAULT_TIMEOUT_MS
        )
        
        // Check specific rules first
        for (rule in policy.rules) {
            if (operation.matches(Regex(rule.pattern))) {
                return PermissionDecision(
                    action = rule.action,
                    reason = "Matched rule: ${rule.pattern}",
                    timeout = rule.timeout ?: policy.timeout,
                    matchedRule = rule
                )
            }
        }
        
        // Use default action if no rules match
        return PermissionDecision(
            action = policy.defaultAction,
            reason = "Using default policy for category: $category",
            timeout = policy.timeout
        )
    }
    
    fun updatePolicy(category: String, policy: PermissionPolicy) {
        _policies.value = _policies.value + (category to policy)
        preferencesManager.savePermissionPolicy(category, policy)
    }
    
    fun resetToDefaults() {
        _policies.value = loadDefaultPolicies()
        preferencesManager.clearPermissionPolicies()
    }
    
    fun categorizeOperation(tool: String, action: String): String {
        return when {
            tool == "file" && action == "read" -> CATEGORY_FILE_READ
            tool == "file" && action == "write" -> CATEGORY_FILE_WRITE
            tool == "shell" -> categorizeShellCommand(action)
            tool == "git" -> CATEGORY_GIT_OPERATION
            tool == "network" -> CATEGORY_NETWORK_REQUEST
            else -> "unknown"
        }
    }
    
    private fun categorizeShellCommand(command: String): String {
        return when {
            command.startsWith("npm test") || 
            command.startsWith("pytest") ||
            command.startsWith("jest") -> CATEGORY_TEST_COMMAND
            
            command.startsWith("npm run build") || 
            command.startsWith("gradle build") ||
            command.startsWith("make") -> CATEGORY_BUILD_COMMAND
            
            command.contains("install") -> CATEGORY_INSTALL_COMMAND
            
            command.startsWith("git") -> CATEGORY_GIT_OPERATION
            
            else -> CATEGORY_SHELL_COMMAND
        }
    }
}

data class PermissionPolicy(
    val category: String,
    val defaultAction: PolicyAction,
    val timeout: Long,
    val rules: List<PolicyRule>
)

data class PolicyRule(
    val pattern: String,
    val action: PolicyAction,
    val timeout: Long? = null
)

enum class PolicyAction {
    ALLOW,  // Automatically approve
    DENY,   // Automatically deny
    PROMPT  // Ask the user
}

data class PermissionDecision(
    val action: PolicyAction,
    val reason: String,
    val timeout: Long,
    val matchedRule: PolicyRule? = null
)

// Extension to permission handler implementation
@Singleton
class EnhancedPermissionRequestHandler @Inject constructor(
    private val policyManager: DefaultPermissionPolicyManager,
    private val notificationManager: PocketAgentNotificationManager,
    private val webSocketManager: WebSocketManager,
    private val analyticsTracker: AnalyticsTracker,
    @BackgroundScope private val scope: CoroutineScope
) : PermissionRequestHandler {
    
    private val pendingRequests = ConcurrentHashMap<String, PendingPermissionRequest>()
    
    override suspend fun handlePermissionRequest(
        requestId: String,
        projectName: String,
        tool: String,
        action: String,
        details: Map<String, Any>
    ) {
        val category = policyManager.categorizeOperation(tool, action)
        val decision = policyManager.evaluatePermission(category, action, details)
        
        when (decision.action) {
            PolicyAction.ALLOW -> {
                // Auto-approve based on policy
                approvePermission(requestId)
                analyticsTracker.trackEvent("permission_auto_approved", mapOf(
                    "category" to category,
                    "reason" to decision.reason
                ))
            }
            PolicyAction.DENY -> {
                // Auto-deny based on policy
                denyPermission(requestId)
                analyticsTracker.trackEvent("permission_auto_denied", mapOf(
                    "category" to category,
                    "reason" to decision.reason
                ))
            }
            PolicyAction.PROMPT -> {
                // Show notification and wait for user response
                val request = PendingPermissionRequest(
                    id = requestId,
                    projectName = projectName,
                    tool = tool,
                    action = action,
                    details = details,
                    timeout = decision.timeout
                )
                pendingRequests[requestId] = request
                
                notificationManager.showPermissionRequestNotification(
                    projectName = projectName,
                    tool = tool,
                    action = action,
                    requestId = requestId,
                    timeout = (decision.timeout / 1000).toInt()
                )
                
                // Schedule timeout handler
                scope.launch {
                    delay(decision.timeout)
                    if (pendingRequests.containsKey(requestId)) {
                        handleTimeout(requestId)
                    }
                }
            }
        }
    }
    
    override suspend fun approvePermission(requestId: String) {
        pendingRequests.remove(requestId)?.let { request ->
            webSocketManager.sendPermissionResponse(requestId, approved = true)
            notificationManager.cancel(
                PocketAgentNotificationManager.PERMISSION_REQUEST_ID_BASE + requestId.hashCode()
            )
        }
    }
    
    override suspend fun denyPermission(requestId: String) {
        pendingRequests.remove(requestId)?.let { request ->
            webSocketManager.sendPermissionResponse(requestId, approved = false)
            notificationManager.cancel(
                PocketAgentNotificationManager.PERMISSION_REQUEST_ID_BASE + requestId.hashCode()
            )
        }
    }
    
    override suspend fun handleTimeout(requestId: String) {
        pendingRequests.remove(requestId)?.let { request ->
            // Use default timeout action from preferences
            val defaultTimeoutAction = preferencesManager.getDefaultTimeoutAction()
            when (defaultTimeoutAction) {
                TimeoutAction.APPROVE -> approvePermission(requestId)
                TimeoutAction.DENY -> denyPermission(requestId)
                TimeoutAction.PAUSE -> {
                    // Pause the Claude session
                    webSocketManager.pauseSession(request.projectName)
                    notificationManager.showTaskCompletionNotification(
                        projectName = request.projectName,
                        success = false,
                        message = "Session paused: Permission request timed out"
                    )
                }
            }
        }
    }
}

data class PendingPermissionRequest(
    val id: String,
    val projectName: String,
    val tool: String,
    val action: String,
    val details: Map<String, Any>,
    val timeout: Long,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TimeoutAction {
    APPROVE,
    DENY,
    PAUSE
}

// Preferences interface extension
interface PreferencesManager {
    fun getPermissionPolicies(): Map<String, PermissionPolicy>?
    fun savePermissionPolicy(category: String, policy: PermissionPolicy)
    fun clearPermissionPolicies()
    fun getDefaultTimeoutAction(): TimeoutAction
}
```

### Integration Points

**Purpose**: Defines how the Background Services feature integrates with the rest of the application through dependency injection, providing background execution capabilities to all other features.

```kotlin
import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

// Service module
@Module
@InstallIn(SingletonComponent::class)
object BackgroundServiceModule {
    
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    @Provides
    @Singleton
    @BackgroundScope
    fun provideBackgroundCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    
    @Provides
    @Singleton
    fun provideNotificationActionReceiver(): NotificationActionReceiver {
        return NotificationActionReceiver()
    }
}

// Binding module
@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundServiceBindingModule {
    
    @Binds
    abstract fun bindSessionBackupManager(
        implementation: SessionBackupManagerImpl
    ): SessionBackupManager
    
    @Binds
    abstract fun bindMessageSyncManager(
        implementation: MessageSyncManagerImpl
    ): MessageSyncManager
    
    @Binds
    abstract fun bindCacheManager(
        implementation: CacheManagerImpl
    ): CacheManager
    
    @Binds
    abstract fun bindLogUploader(
        implementation: LogUploaderImpl
    ): LogUploader
    
    @Binds
    abstract fun bindMetricsCollector(
        implementation: MetricsCollectorImpl
    ): MetricsCollector
    
    @Binds
    abstract fun bindCrashReporter(
        implementation: CrashReporterImpl
    ): CrashReporter
    
    @Binds
    abstract fun bindAnalyticsTracker(
        implementation: AnalyticsTrackerImpl
    ): AnalyticsTracker
}

// Application class configuration for WorkManager
class PocketAgentApplication : Application(), Configuration.Provider {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize background services
        initializeBackgroundServices()
    }
    
    private fun initializeBackgroundServices() {
        // Schedule periodic work
        val workScheduler = WorkManagerScheduler(WorkManager.getInstance(this))
        workScheduler.schedulePeriodicWork()
    }
}

// Notification action receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    
    @Inject lateinit var permissionHandler: PermissionRequestHandler
    @Inject lateinit var connectionManager: ConnectionManager
    @Inject @BackgroundScope lateinit var scope: CoroutineScope
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PocketAgentNotificationManager.ACTION_APPROVE -> {
                val requestId = intent.getStringExtra("request_id") ?: return
                scope.launch {
                    permissionHandler.approvePermission(requestId)
                }
            }
            PocketAgentNotificationManager.ACTION_DENY -> {
                val requestId = intent.getStringExtra("request_id") ?: return
                scope.launch {
                    permissionHandler.denyPermission(requestId)
                }
            }
            PocketAgentNotificationManager.ACTION_RETRY -> {
                val projectName = intent.getStringExtra("project_name") ?: return
                scope.launch {
                    connectionManager.retryConnection(projectName)
                }
            }
        }
    }
}

// Permission request handler
interface PermissionRequestHandler {
    suspend fun approvePermission(requestId: String)
    suspend fun denyPermission(requestId: String)
    suspend fun handleTimeout(requestId: String)
}

// Supporting interfaces
interface SessionBackupManager {
    suspend fun backupSession(projectId: String): Boolean
    suspend fun backupActiveSessions(): Int
    suspend fun restoreSession(projectId: String): SessionState?
}

interface MessageSyncManager {
    suspend fun syncMessages(projectId: String): Boolean
    suspend fun getPendingMessages(projectId: String): List<PendingMessage>
}

interface CacheManager {
    suspend fun performCleanup()
    suspend fun clearOldImageCache(days: Int)
    suspend fun clearMessageCache()
    suspend fun trimDatabaseCache()
    suspend fun calculateCacheSize(): Long
}

interface LogUploader {
    suspend fun uploadPendingLogs(): Boolean
    suspend fun getPendingLogSize(): Long
}

interface MetricsCollector {
    suspend fun collectDailyMetrics(): Metrics
    suspend fun uploadMetrics(metrics: Metrics)
}

interface CrashReporter {
    fun reportCrash(throwable: Throwable)
    fun reportNonFatal(throwable: Throwable)
}

interface AnalyticsTracker {
    fun trackError(event: String, throwable: Throwable)
    fun trackEvent(event: String, parameters: Map<String, Any> = emptyMap())
}

// Scope qualifier
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackgroundScope

// Data classes
data class PendingMessage(
    val id: String,
    val content: String,
    val timestamp: Long
)

data class Metrics(
    val activeProjects: Int,
    val totalMessages: Int,
    val connectionUptime: Long,
    val batteryUsage: Float
)
```

## Testing

### Testing Checklist

**Purpose**: Comprehensive testing checklist to ensure all aspects of the Background Services feature are properly tested, including service lifecycle, notifications, battery optimization, and background task execution.

```kotlin
/**
 * Background Services Testing Checklist:
 * 
 * Service Tests:
 * 1. [ ] Test foreground service starts correctly
 * 2. [ ] Test service persists across configuration changes
 * 3. [ ] Test service stops when all projects disconnected
 * 4. [ ] Test service restart after crash
 * 5. [ ] Test service notification updates
 * 6. [ ] Test service binding and unbinding
 * 7. [ ] Test service lifecycle with multiple projects
 * 
 * Notification Tests:
 * 8. [ ] Test permission request notifications
 * 9. [ ] Test task completion notifications
 * 10. [ ] Test error notifications
 * 11. [ ] Test progress notifications
 * 12. [ ] Test notification actions (approve/deny)
 * 13. [ ] Test notification channels creation
 * 14. [ ] Test notification dismissal
 * 
 * WorkManager Tests:
 * 15. [ ] Test periodic work scheduling
 * 16. [ ] Test one-time work execution
 * 17. [ ] Test work constraints enforcement
 * 18. [ ] Test work cancellation
 * 19. [ ] Test work retry with backoff
 * 20. [ ] Test work chaining
 * 
 * Battery Optimization Tests:
 * 21. [ ] Test polling frequency adjustment
 * 22. [ ] Test battery state monitoring
 * 23. [ ] Test power save mode behavior
 * 24. [ ] Test doze mode compliance
 * 25. [ ] Test battery optimization exemption
 * 
 * Connection Monitoring Tests:
 * 26. [ ] Test health check execution
 * 27. [ ] Test adaptive polling
 * 28. [ ] Test reconnection on failure
 * 29. [ ] Test multiple project monitoring
 * 30. [ ] Test monitoring state persistence
 * 
 * Session Persistence Tests:
 * 31. [ ] Test session state save/restore
 * 32. [ ] Test encrypted data persistence
 * 33. [ ] Test session restoration after reboot
 * 34. [ ] Test pending permissions persistence
 * 35. [ ] Test session cleanup
 * 
 * Integration Tests:
 * 36. [ ] Test service with real SSH connections
 * 37. [ ] Test notification interactions
 * 38. [ ] Test background task execution
 * 39. [ ] Test app lifecycle handling
 * 40. [ ] Test memory pressure handling
 * 
 * Performance Tests:
 * 41. [ ] Test service memory usage
 * 42. [ ] Test battery consumption
 * 43. [ ] Test CPU usage during monitoring
 * 44. [ ] Test wake lock usage
 * 45. [ ] Test concurrent project handling
 */
```

### Unit Tests

**Purpose**: Example unit tests for background service components, demonstrating proper testing patterns for services, notifications, and background tasks.

```kotlin
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.InitHelper
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class BatteryOptimizationManagerTest {
    
    private lateinit var context: Context
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        batteryOptimizationManager = BatteryOptimizationManager(context)
    }
    
    @Test
    fun testPollingFrequencyAdjustment() = testScope.runTest {
        // Test charging state
        val chargingState = BatteryState(
            percentage = 80,
            isCharging = true,
            isPowerSaveMode = false,
            level = BatteryLevel.CHARGING
        )
        assertEquals(
            BatteryOptimizationManager.FREQUENCY_CHARGING,
            batteryOptimizationManager.getPollingFrequency(chargingState)
        )
        
        // Test low battery state
        val lowBatteryState = BatteryState(
            percentage = 25,
            isCharging = false,
            isPowerSaveMode = false,
            level = BatteryLevel.LOW
        )
        assertEquals(
            BatteryOptimizationManager.FREQUENCY_LOW,
            batteryOptimizationManager.getPollingFrequency(lowBatteryState)
        )
        
        // Test power save mode
        val powerSaveState = BatteryState(
            percentage = 50,
            isCharging = false,
            isPowerSaveMode = true,
            level = BatteryLevel.POWER_SAVE
        )
        assertEquals(
            BatteryOptimizationManager.FREQUENCY_POWER_SAVE,
            batteryOptimizationManager.getPollingFrequency(powerSaveState)
        )
    }
    
    @Test
    fun testBackgroundActivityReduction() {
        val criticalState = BatteryState(
            percentage = 10,
            isCharging = false,
            isPowerSaveMode = false,
            level = BatteryLevel.CRITICAL
        )
        assertTrue(batteryOptimizationManager.shouldReduceBackgroundActivity(criticalState))
        
        val normalState = BatteryState(
            percentage = 60,
            isCharging = false,
            isPowerSaveMode = false,
            level = BatteryLevel.NORMAL
        )
        assertTrue(!batteryOptimizationManager.shouldReduceBackgroundActivity(normalState))
    }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class NotificationManagerTest {
    
    @Mock
    private lateinit var mockNotificationManager: NotificationManagerCompat
    
    private lateinit var context: Context
    private lateinit var pocketAgentNotificationManager: PocketAgentNotificationManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        pocketAgentNotificationManager = PocketAgentNotificationManager(context)
    }
    
    @Test
    fun testMonitoringNotificationCreation() {
        val notification = pocketAgentNotificationManager.createMonitoringNotification(
            activeProjectCount = 2,
            connectionStatus = ConnectionStatus.CONNECTED
        )
        
        assertNotNull(notification)
        assertEquals(Notification.FLAG_ONGOING_EVENT, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }
    
    @Test
    fun testPermissionRequestNotification() {
        // Create spy to verify notification
        val spy = spy(pocketAgentNotificationManager)
        
        spy.showPermissionRequestNotification(
            projectName = "Test Project",
            tool = "Editor",
            action = "create file",
            requestId = "req_123",
            timeout = 60
        )
        
        verify(spy).notify(any(), any())
    }
}

@RunWith(AndroidJUnit4::class)
class WorkManagerSchedulerTest {
    
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerScheduler
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerScheduler(workManager)
    }
    
    @Test
    fun testPeriodicWorkScheduling() {
        scheduler.schedulePeriodicWork()
        
        val workInfos = workManager.getWorkInfosByTag(WorkManagerScheduler.TAG_PERIODIC).get()
        assertTrue(workInfos.isNotEmpty())
        
        // Verify specific work is scheduled
        val cleanupWork = workManager.getWorkInfosForUniqueWork(
            WorkManagerScheduler.WORK_CLEANUP
        ).get()
        assertEquals(1, cleanupWork.size)
    }
    
    @Test
    fun testOneTimeLogUpload() {
        scheduler.scheduleOneTimeLogUpload(priority = true)
        
        val workInfos = workManager.getWorkInfosForUniqueWork(
            WorkManagerScheduler.WORK_LOG_UPLOAD
        ).get()
        
        assertEquals(1, workInfos.size)
        val workInfo = workInfos.first()
        assertTrue(workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)
    }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ConnectionHealthMonitorTest {
    
    @Mock private lateinit var mockSshAuthWebSocketClient: SshAuthWebSocketClient
    @Mock private lateinit var mockWebSocketManager: WebSocketManager
    @Mock private lateinit var mockConnectionManager: ConnectionManager
    @Mock private lateinit var mockBatteryOptimizationManager: BatteryOptimizationManager
    @Mock private lateinit var mockNotificationManager: PocketAgentNotificationManager
    
    private lateinit var healthMonitor: ConnectionHealthMonitor
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        healthMonitor = ConnectionHealthMonitor(
            mockSshAuthWebSocketClient,
            mockWebSocketManager,
            mockConnectionManager,
            mockBatteryOptimizationManager,
            mockNotificationManager
        )
        
        whenever(mockBatteryOptimizationManager.getPollingFrequency()).thenReturn(5000L)
    }
    
    @Test
    fun testHealthCheckExecution() = testScope.runTest {
        val projectId = "test_project"
        
        // Mock healthy connections
        whenever(mockSshAuthWebSocketClient.isAuthenticated(projectId)).thenReturn(true)
        whenever(mockWebSocketManager.getConnection(projectId)).thenReturn(mock())
        whenever(mockWebSocketManager.sendHealthCheck(projectId)).thenReturn("ok")
        
        healthMonitor.startMonitoring(projectId, 1000L)
        
        // Wait for health check
        testScheduler.advanceTimeBy(1500L)
        
        // Verify connection status was updated
        verify(mockConnectionManager).updateConnectionStatus(projectId, ConnectionStatus.CONNECTED)
        
        healthMonitor.stopMonitoring(projectId)
    }
    
    @Test
    fun testUnhealthyConnectionHandling() = testScope.runTest {
        val projectId = "test_project"
        val projectName = "Test Project"
        
        whenever(mockConnectionManager.getProjectName(projectId)).thenReturn(projectName)
        
        // Mock WebSocket failure
        whenever(mockWebSocketManager.getConnection(projectId)).thenReturn(null)
        
        healthMonitor.startMonitoring(projectId, 1000L)
        
        // Wait for health check
        testScheduler.advanceTimeBy(1500L)
        
        // Verify error notification and reconnection attempt
        verify(mockNotificationManager).showErrorNotification(
            eq(projectName),
            eq("Server connection lost"),
            eq(true)
        )
        verify(mockConnectionManager).reconnectWebSocket(projectId)
        
        healthMonitor.stopMonitoring(projectId)
    }
    
    @Test
    fun testAuthenticationExpiredHandling() = testScope.runTest {
        val projectId = "test_project"
        val projectName = "Test Project"
        
        whenever(mockConnectionManager.getProjectName(projectId)).thenReturn(projectName)
        
        // Mock authentication failure
        val mockConnection = mock<WebSocketConnection> {
            on { checkAuthenticationStatus() } doReturn AuthenticationStatus.EXPIRED
        }
        whenever(mockWebSocketManager.getConnection(projectId)).thenReturn(mockConnection)
        whenever(mockWebSocketManager.sendHealthCheck(projectId)).thenReturn("ok")
        
        healthMonitor.startMonitoring(projectId, 1000L)
        
        testScheduler.advanceTimeBy(1500L)
        
        // Verify authentication notification
        verify(mockNotificationManager).showAuthenticationRequiredNotification(
            eq(projectName),
            eq("Authentication expired. Please re-authenticate.")
        )
        verify(mockConnectionManager).requestAuthentication(projectId)
        
        healthMonitor.stopMonitoring(projectId)
    }
}
```

### Integration Tests

**Purpose**: Integration tests demonstrating full background service functionality with real Android components, testing service lifecycle, notification interactions, and background task execution.

```kotlin
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ClaudeBackgroundServiceIntegrationTest {
    
    @get:Rule
    val serviceRule = ServiceTestRule()
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testServiceStartAndBind() {
        val serviceIntent = Intent(context, ClaudeBackgroundService::class.java).apply {
            action = ClaudeBackgroundService.ACTION_START_MONITORING
            putExtra(ClaudeBackgroundService.EXTRA_PROJECT_ID, "test_project")
        }
        
        val binder = serviceRule.bindService(serviceIntent)
        assertNotNull(binder)
        
        // Verify service is running
        val service = (binder as ClaudeBackgroundService.LocalBinder).getService()
        assertTrue(service.serviceStarted)
    }
    
    @Test
    fun testMultipleProjectMonitoring() {
        // Start monitoring first project
        val intent1 = Intent(context, ClaudeBackgroundService::class.java).apply {
            action = ClaudeBackgroundService.ACTION_START_MONITORING
            putExtra(ClaudeBackgroundService.EXTRA_PROJECT_ID, "project1")
        }
        context.startForegroundService(intent1)
        
        // Start monitoring second project
        val intent2 = Intent(context, ClaudeBackgroundService::class.java).apply {
            action = ClaudeBackgroundService.ACTION_START_MONITORING
            putExtra(ClaudeBackgroundService.EXTRA_PROJECT_ID, "project2")
        }
        context.startForegroundService(intent2)
        
        // Verify both projects are being monitored
        Thread.sleep(1000) // Give service time to process
        
        // Stop monitoring first project
        val stopIntent = Intent(context, ClaudeBackgroundService::class.java).apply {
            action = ClaudeBackgroundService.ACTION_STOP_MONITORING
            putExtra(ClaudeBackgroundService.EXTRA_PROJECT_ID, "project1")
        }
        context.startService(stopIntent)
        
        Thread.sleep(500)
        
        // Service should still be running for project2
        // This would be verified through service state inspection
    }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class BackgroundNotificationIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var notificationManager: PocketAgentNotificationManager
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        notificationManager = PocketAgentNotificationManager(context)
    }
    
    @Test
    fun testNotificationChannelCreation() {
        val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channels = systemNotificationManager.notificationChannels
        assertTrue(channels.any { it.id == PocketAgentNotificationManager.CHANNEL_MONITORING })
        assertTrue(channels.any { it.id == PocketAgentNotificationManager.CHANNEL_PERMISSIONS })
    }
    
    @Test
    fun testPermissionNotificationWithActions() = runTest {
        notificationManager.showPermissionRequestNotification(
            projectName = "Test Project",
            tool = "Editor",
            action = "create auth.py",
            requestId = "test_req_123",
            timeout = 60
        )
        
        // Verify notification is displayed
        val notifications = systemNotificationManager.activeNotifications
        assertTrue(notifications.isNotEmpty())
        
        val permissionNotification = notifications.find { 
            it.notification.extras.getString("android.title")?.contains("Permission Request") == true
        }
        assertNotNull(permissionNotification)
        
        // Verify actions are present
        val actions = permissionNotification.notification.actions
        assertEquals(2, actions.size)
        assertEquals("Allow", actions[0].title)
        assertEquals("Deny", actions[1].title)
    }
}

@RunWith(AndroidJUnit4::class)
class SessionPersistenceIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var sessionStateManager: SessionStateManager
    private lateinit var encryptionManager: EncryptionManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encryptionManager = EncryptionManager(context)
        sessionStateManager = SessionStateManager(context, encryptionManager)
    }
    
    @Test
    fun testSessionSaveAndRestore() = runTest {
        val testSession = SessionState(
            projectId = "test_project",
            sensitiveData = SensitiveSessionData(
                claudeSessionId = "claude_123",
                wrapperSessionToken = "wrapper_token",
                lastMessageId = "msg_456",
                pendingPermissions = listOf(
                    PendingPermission(
                        requestId = "perm_789",
                        tool = "Editor",
                        action = "create file",
                        timestamp = System.currentTimeMillis()
                    )
                )
            ),
            metadata = SessionMetadata(
                projectName = "Test Project",
                serverName = "Dev Server",
                connectionStatus = "CONNECTED",
                lastActivity = System.currentTimeMillis(),
                messageCount = 42
            )
        )
        
        // Save session
        sessionStateManager.saveSessionState(testSession)
        
        // Restore session
        val restoredSession = sessionStateManager.restoreSessionState("test_project")
        
        assertNotNull(restoredSession)
        assertEquals(testSession.projectId, restoredSession.projectId)
        assertEquals(testSession.sensitiveData.claudeSessionId, restoredSession.sensitiveData.claudeSessionId)
        assertEquals(1, restoredSession.sensitiveData.pendingPermissions.size)
        assertEquals(testSession.metadata.projectName, restoredSession.metadata.projectName)
    }
    
    @Test
    fun testMultipleSessionPersistence() = runTest {
        // Save multiple sessions
        repeat(3) { i ->
            val session = SessionState(
                projectId = "project_$i",
                sensitiveData = SensitiveSessionData(
                    claudeSessionId = "claude_$i",
                    wrapperSessionToken = "token_$i",
                    lastMessageId = null
                ),
                metadata = SessionMetadata(
                    projectName = "Project $i",
                    serverName = "Server $i",
                    connectionStatus = "CONNECTED",
                    lastActivity = System.currentTimeMillis(),
                    messageCount = i * 10
                )
            )
            sessionStateManager.saveSessionState(session)
        }
        
        // Retrieve all sessions
        val allSessions = sessionStateManager.getAllActiveSessions()
        
        assertEquals(3, allSessions.size)
        assertTrue(allSessions.any { it.projectId == "project_0" })
        assertTrue(allSessions.any { it.projectId == "project_1" })
        assertTrue(allSessions.any { it.projectId == "project_2" })
    }
}
```

## Implementation Notes (Android Mobile)

### Critical Implementation Details

#### Service Lifecycle Management
- Use `startForegroundService()` for Android 8.0+ to ensure service starts properly
- Call `startForeground()` within 5 seconds of service start to avoid ANR
- Handle service restarts gracefully with START_STICKY
- Properly clean up resources in `onDestroy()`

#### Notification Handling
- Create notification channels before posting notifications (Android 8.0+)
- Use PendingIntent.FLAG_IMMUTABLE for Android 12+ compatibility
- Handle notification trampolines restrictions in Android 12+
- Implement proper notification grouping for multiple projects

#### Battery Optimization
- Request battery optimization exemption for critical operations
- Implement adaptive polling based on battery state
- Use JobScheduler/WorkManager for deferrable tasks
- Respect Doze mode and App Standby buckets

#### Permission Requirements
- FOREGROUND_SERVICE permission required
- FOREGROUND_SERVICE_DATA_SYNC for Android 14+
- POST_NOTIFICATIONS permission for Android 13+
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for exemption dialog

### Performance Considerations (Android-Specific)

- **Memory Management**: Monitor service memory usage, implement trim callbacks
- **Wake Lock Usage**: Minimize wake lock duration, use partial wake locks
- **Network Efficiency**: Batch network requests, use exponential backoff
- **CPU Usage**: Offload heavy processing to WorkManager tasks
- **Storage**: Implement log rotation and cache cleanup

**Purpose**: Example showing performance optimizations for background services including efficient polling, memory management, and battery-aware scheduling.

```kotlin
// Efficient polling implementation
class EfficientConnectionMonitor {
    private val pollingSessions = ConcurrentHashMap<String, PollingSession>()
    
    fun startEfficientPolling(projectId: String) {
        val session = PollingSession(
            projectId = projectId,
            baseInterval = 5000L,
            maxInterval = 60000L,
            successCount = 0,
            failureCount = 0
        )
        
        pollingSessions[projectId] = session
        scheduleNextPoll(session)
    }
    
    private fun scheduleNextPoll(session: PollingSession) {
        val delay = calculateAdaptiveDelay(session)
        
        handler.postDelayed({
            performPoll(session)
        }, delay)
    }
    
    private fun calculateAdaptiveDelay(session: PollingSession): Long {
        return when {
            session.failureCount > 3 -> session.maxInterval
            session.successCount > 10 -> (session.baseInterval * 2).coerceAtMost(session.maxInterval)
            else -> session.baseInterval
        }
    }
}

// Memory-efficient message caching
class MemoryEfficientCache {
    private val messageCache = LruCache<String, Message>(100)
    private val compressionThreshold = 1024 // 1KB
    
    fun cacheMessage(id: String, message: Message) {
        val compressed = if (message.content.length > compressionThreshold) {
            compressMessage(message)
        } else {
            message
        }
        messageCache.put(id, compressed)
    }
    
    private fun compressMessage(message: Message): Message {
        // Implement compression
        return message
    }
}
```

### Package Structure

```
background/
├── service/
│   ├── ClaudeBackgroundService.kt
│   ├── ServiceLifecycleManager.kt
│   └── ServiceRecoveryManager.kt
├── notification/
│   ├── PocketAgentNotificationManager.kt
│   ├── NotificationChannelManager.kt
│   ├── NotificationActionReceiver.kt
│   └── NotificationBuilder.kt
├── work/
│   ├── WorkManagerScheduler.kt
│   ├── workers/
│   │   ├── CleanupWorker.kt
│   │   ├── CacheCleanupWorker.kt
│   │   ├── SessionBackupWorker.kt
│   │   ├── MetricsCollectionWorker.kt
│   │   └── LogUploadWorker.kt
│   └── BackgroundTaskScheduler.kt
├── battery/
│   ├── BatteryOptimizationManager.kt
│   ├── BatteryStateMonitor.kt
│   └── PowerSaveHandler.kt
├── monitoring/
│   ├── ConnectionHealthMonitor.kt
│   ├── HealthCheckExecutor.kt
│   └── MonitoringStateManager.kt
├── persistence/
│   ├── SessionStateManager.kt
│   ├── SessionRestorationHelper.kt
│   └── EncryptedDataStore.kt
├── error/
│   ├── BackgroundErrorHandler.kt
│   ├── ServiceExceptionHandler.kt
│   └── RetryPolicyManager.kt
└── di/
    ├── BackgroundServiceModule.kt
    ├── WorkManagerModule.kt
    └── NotificationModule.kt
```

### Future Extensions (Android Mobile Focus)

- **Android Auto Support**: Extend notifications to Android Auto for driving mode
- **Wear OS Integration**: Send notifications and quick actions to smartwatches
- **Adaptive Icons**: Create adaptive notification icons for different Android versions
- **Notification Channels**: Add user-customizable notification channels per project
- **Background Location**: Add location-based triggers for office/home automation
- **Health Connect**: Integrate with Health Connect for developer wellness tracking
- **Direct Boot**: Support Direct Boot mode for critical notifications
- **Bubble Notifications**: Implement chat bubbles for Android 11+ quick access