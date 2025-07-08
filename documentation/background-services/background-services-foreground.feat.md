# Background Services Feature Specification - Foreground Service
**For Android Mobile Application**

> **Navigation**: [Index](./background-services-index.md) | [Overview](./background-services-overview.feat.md) | **Foreground Service** | [Notifications](./background-services-notifications.feat.md) | [Monitoring](./background-services-monitoring.feat.md) | [Testing](./background-services-testing.feat.md)

## Foreground Service Manager

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
class BackgroundOperationsService : LifecycleService() {
    
    companion object {
        const val ACTION_START_MONITORING = "com.pocketagent.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.pocketagent.action.STOP_MONITORING"
        const val ACTION_UPDATE_PROJECT = "com.pocketagent.action.UPDATE_PROJECT"
        const val EXTRA_PROJECT_ID = "project_id"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "connection_monitoring"
        
        fun startMonitoring(context: Context, projectId: String) {
            val intent = Intent(context, BackgroundOperationsService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startForegroundService(intent)
        }
        
        fun stopMonitoring(context: Context, projectId: String) {
            val intent = Intent(context, BackgroundOperationsService::class.java).apply {
                action = ACTION_STOP_MONITORING
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startService(intent)
        }
    }
    
    @Inject lateinit var notificationManager: AppNotificationManager
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

## Service Lifecycle Management

The foreground service follows Android's service lifecycle with special considerations for foreground service requirements:

### Starting the Service

1. **From Activity/Fragment**:
```kotlin
// Start monitoring a project
BackgroundOperationsService.startMonitoring(context, projectId)
```

2. **From Notification Actions**:
```kotlin
val intent = Intent(context, BackgroundOperationsService::class.java).apply {
    action = BackgroundOperationsService.ACTION_START_MONITORING
    putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, projectId)
}
val pendingIntent = PendingIntent.getService(
    context, 
    requestCode, 
    intent, 
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

### Service States

1. **Not Running**: No active monitoring sessions
2. **Starting**: Service created, foreground notification being prepared
3. **Running**: Active monitoring with persistent notification
4. **Stopping**: Last project disconnected, cleaning up

### Intent Actions

- **ACTION_START_MONITORING**: Begin monitoring a specific project
- **ACTION_STOP_MONITORING**: Stop monitoring a specific project
- **ACTION_UPDATE_PROJECT**: Refresh project configuration

## Manifest Configuration

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <application>
        <!-- Foreground Service -->
        <service
            android:name=".background.service.BackgroundOperationsService"
            android:exported="false"
            android:foregroundServiceType="dataSync"
            android:stopWithTask="false">
            <intent-filter>
                <action android:name="com.pocketagent.action.START_MONITORING" />
                <action android:name="com.pocketagent.action.STOP_MONITORING" />
                <action android:name="com.pocketagent.action.UPDATE_PROJECT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

## Integration Points

### With Connection Manager

```kotlin
// Monitor connection state changes
connectionManager.connectionStates
    .map { states -> states[projectId] }
    .onEach { status -> 
        // Update notification and monitoring behavior
    }
```

### With Battery Manager

```kotlin
// Adjust monitoring based on battery
batteryManager.batteryState
    .onEach { state ->
        val frequency = when (state.level) {
            in 0..15 -> 30_000L  // 30 seconds
            in 16..30 -> 15_000L // 15 seconds
            else -> 5_000L       // 5 seconds
        }
        connectionMonitor.updateFrequency(projectId, frequency)
    }
```

### With Session State Persistence

```kotlin
// Save session state on configuration changes
override fun onTaskRemoved(rootIntent: Intent?) {
    sessionStateManager.saveActiveProjects(activeProjects.keys)
    super.onTaskRemoved(rootIntent)
}
```

## Best Practices

1. **Foreground Service Compliance**
   - Always show notification within 5 seconds
   - Use appropriate foreground service type
   - Handle API level differences properly

2. **Resource Management**
   - Cancel coroutines when stopping monitoring
   - Clean up resources in onDestroy
   - Use lifecycle-aware components

3. **Error Handling**
   - Gracefully handle missing projects
   - Recover from service restarts
   - Log service lifecycle events

4. **Performance**
   - Use concurrent collections for thread safety
   - Minimize work in onStartCommand
   - Batch notification updates