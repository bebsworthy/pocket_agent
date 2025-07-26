# Background Services - Research

## Executive Summary

The Background Services feature implements a sophisticated Android foreground service architecture that provides persistent monitoring of Claude sessions while respecting platform constraints and battery life. After extensive research into Android's evolving background execution model, we've chosen a foreground service approach combined with WorkManager for scheduled tasks. This solution offers the best balance of reliability, user transparency, and battery efficiency.

Key findings indicate that foreground services remain the only guaranteed method for continuous background execution on modern Android, while adaptive polling based on battery state can reduce power consumption by up to 80% during low battery conditions. The multi-channel notification system allows users fine-grained control over alert types, addressing notification fatigue concerns while ensuring critical permission requests are never missed.

## Architecture Context

This research builds upon the application's layered architecture described in the global `architecture.md`. The Background Services feature integrates with:

- **Communication Layer**: Monitors WebSocket connections for health and message flow
- **Data Layer**: Persists session state and manages message caching
- **Presentation Layer**: Provides notification UI and service controls
- **Domain Layer**: Implements business logic for monitoring and battery optimization

## Android Background Execution Evolution

### Historical Context

Android's approach to background execution has evolved significantly:

1. **Pre-Android 6.0**: Unrestricted background execution
2. **Android 6.0 (Doze)**: Introduction of Doze mode
3. **Android 7.0**: Doze on the Go
4. **Android 8.0**: Background execution limits
5. **Android 9.0+**: Adaptive Battery and App Standby Buckets
6. **Android 12.0+**: Foreground service launch restrictions

This evolution requires careful consideration of background service implementation strategies.

## Technology Analysis

### Background Execution Technology Comparison

| Technology | Reliability | Battery Impact | User Visibility | Real-time Capability | Platform Support |
|------------|-------------|----------------|-----------------|---------------------|------------------|
| **Foreground Service** | ✅ Guaranteed | Medium | ✅ Always visible | ✅ Excellent | All versions |
| **WorkManager** | ❌ Best effort | ✅ Low | Hidden | ❌ Poor (15min+) | API 14+ |
| **JobScheduler** | ❌ Best effort | ✅ Low | Hidden | ❌ Poor | API 21+ |
| **AlarmManager** | ⚠️ Restricted | Medium | Hidden | ⚠️ Limited | All versions |
| **Firebase Cloud Messaging** | ✅ High | ✅ Very Low | Per message | ✅ Good | Requires Play Services |
| **Bound Service** | ❌ UI dependent | Low | Hidden | ✅ Excellent | All versions |

### Foreground Service vs Alternatives

#### Why Foreground Service?

1. **Guaranteed Execution**: Not subject to background limits
2. **User Awareness**: Visible notification maintains trust
3. **System Priority**: Less likely to be killed
4. **Network Access**: Unrestricted during Doze
5. **Platform Standard**: Google's recommended approach

#### Alternatives Considered

- **JobScheduler**: Rejected - Too restrictive for real-time monitoring
- **Firebase Cloud Messaging**: Rejected - Requires server infrastructure
- **WorkManager Only**: Rejected - Cannot guarantee timely execution
- **Bound Service**: Rejected - Dies when UI closes
- **Alarm Manager**: Rejected - Exact alarms restricted in Android 12+

### Notification System Design

#### Android Lifecycle Considerations

The notification system must handle various Android lifecycle scenarios:

1. **Process Death**: Notifications persist even if app process is killed
2. **Device Reboot**: Re-establish monitoring after device restart
3. **App Updates**: Preserve notification state through app updates
4. **Permission Revocation**: Handle runtime permission changes gracefully
5. **Doze Mode**: Ensure critical notifications break through Doze restrictions

#### Android Notification Channels

The codebase implements a multi-channel approach:

```kotlin
const val CHANNEL_MONITORING = "monitoring"       // Low importance
const val CHANNEL_PERMISSIONS = "permissions"     // High importance  
const val CHANNEL_TASKS = "tasks"                // Default importance
const val CHANNEL_ERRORS = "errors"              // High importance
const val CHANNEL_PROGRESS = "progress"          // Low importance
const val CHANNEL_ALERTS = "alerts"              // High importance
```

This design allows users to customize notification preferences per type.

#### Rich Notification Features

1. **Action Buttons**: Direct approve/deny for permissions
2. **Progress Indicators**: Show ongoing operations
3. **Grouped Notifications**: Prevent notification spam
4. **Auto-timeout**: Permissions expire if not addressed
5. **Deep Links**: Tap to open specific project

### WorkManager Integration Strategy

#### Scheduled Tasks Architecture

```kotlin
// Daily cleanup
PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder()
        .setRequiresCharging(true)
        .build())
    .build()

// Cache management every 3 days
PeriodicWorkRequestBuilder<CacheCleanupWorker>(3, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .build())
    .build()
```

Key insights:
- Use constraints to respect device state
- Leverage charging time for heavy operations
- Batch operations to minimize wake-ups

### Battery Optimization Strategies

#### Adaptive Polling Implementation

The codebase shows sophisticated battery awareness:

```kotlin
fun getPollingFrequency(batteryState: BatteryState): Long {
    return when {
        batteryState.isCharging -> 3_000L              // 3 seconds
        batteryState.level == BatteryLevel.CRITICAL -> 30_000L  // 30 seconds
        batteryState.level == BatteryLevel.LOW -> 15_000L       // 15 seconds
        else -> 5_000L                                           // 5 seconds
    }
}
```

This adaptive approach balances functionality with battery life.

#### Power Management Best Practices

1. **Wake Lock Minimization**: Only for critical operations
2. **Batch Network Requests**: Reduce radio wake-ups
3. **Respect Doze Windows**: Use maintenance windows
4. **Honor Power Save Mode**: Reduce activity when enabled

### Connection Health Monitoring

#### Health Check Protocol

The monitoring system uses multiple signals:

1. **WebSocket State**: Connection open/closed
2. **Ping/Pong**: Liveness check with timeout
3. **Message Flow**: Actual data transmission
4. **Authentication**: Session validity
5. **Server Health**: Wrapper service status

#### Failure Detection and Recovery

```kotlin
private suspend fun performHealthCheck(projectId: String): HealthStatus {
    return withTimeoutOrNull(HEALTH_CHECK_TIMEOUT) {
        val isConnected = webSocketManager.isConnected(projectId)
        val pingSuccess = webSocketManager.sendPing(projectId)
        val lastMessage = connectionManager.getLastMessageTime(projectId)
        
        when {
            !isConnected -> HealthStatus.DISCONNECTED
            !pingSuccess -> HealthStatus.UNHEALTHY
            lastMessage > MESSAGE_TIMEOUT -> HealthStatus.STALE
            else -> HealthStatus.HEALTHY
        }
    } ?: HealthStatus.TIMEOUT
}
```

### Session State Persistence

#### State Management Architecture

The system persists multiple levels of state:

1. **Active Projects**: Currently monitored projects
2. **Connection States**: Last known status per project
3. **Message Cache**: Recent messages for context
4. **User Preferences**: Quick actions and settings
5. **UI State**: Last viewed screen/position

#### Persistence Triggers

- App lifecycle events (onStop, onDestroy)
- Low memory warnings
- Configuration changes
- Periodic auto-save
- Before system-initiated process death

### Android Platform Integration

#### Lifecycle-Aware Components

```kotlin
class BackgroundOperationsService : LifecycleService() {
    init {
        lifecycleScope.launchWhenStarted {
            // Coroutines automatically cancelled on service stop
        }
    }
}
```

Using LifecycleService provides:
- Automatic coroutine scope management  
- Lifecycle-aware observers
- Proper cleanup on service destruction

#### Memory Pressure Handling

```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_UI_HIDDEN -> {
            // Reduce memory when UI hidden
            messageCache.trimToSize(100)
        }
        TRIM_MEMORY_RUNNING_LOW -> {
            // More aggressive trimming
            messageCache.trimToSize(50)
        }
        TRIM_MEMORY_RUNNING_CRITICAL -> {
            // Emergency measures
            messageCache.clear()
            // Save critical state
            sessionStatePersistence.saveEmergencyState()
        }
    }
}
```

## Implementation Patterns

### Service Binding Pattern

For UI-service communication:

```kotlin
interface ServiceConnection {
    fun onServiceConnected(binder: BackgroundServiceBinder)
    fun onServiceDisconnected()
}

class BackgroundServiceBinder(private val service: BackgroundOperationsService) : Binder() {
    fun getActiveProjects(): List<Project> = service.activeProjects.values.toList()
    fun getConnectionState(projectId: String): ConnectionState = service.getConnectionState(projectId)
}
```

### Notification Action Pattern

Handling notification actions efficiently:

```kotlin
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Use goAsync() for longer operations
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_APPROVE -> handleApprove(intent)
                    ACTION_DENY -> handleDeny(intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

### Progress Tracking Pattern

For operation progress:

```kotlin
sealed class OperationProgress {
    object Starting : OperationProgress()
    data class InProgress(val message: String) : OperationProgress()
    data class Completed(val success: Boolean, val result: String) : OperationProgress()
}

fun trackOperation(flow: Flow<OperationProgress>) {
    flow.collect { progress ->
        when (progress) {
            is Starting -> showProgressNotification()
            is InProgress -> updateProgressNotification(progress.message)
            is Completed -> showCompletionNotification(progress.success, progress.result)
        }
    }
}
```

## Performance Optimizations

### Memory Efficiency

1. **Message Cache Limits**: Cap at 1000 messages
2. **Lazy Loading**: Load project details on demand
3. **Weak References**: For UI callbacks
4. **Bitmap Recycling**: For notification icons

### CPU Efficiency

1. **Coroutine Dispatchers**: Use IO for network, Default for CPU
2. **Flow Operators**: Share, distinctUntilChanged, debounce
3. **Batch Operations**: Group database updates
4. **Throttling**: Limit UI update frequency

### Battery Efficiency

1. **Coalesce Wake Locks**: Batch operations requiring wake locks
2. **Network Batching**: Send multiple requests together
3. **Adaptive Intervals**: Adjust based on activity
4. **Idle Detection**: Reduce work when device idle

## Security Considerations

### Notification Security

1. **PendingIntent Flags**: Use FLAG_IMMUTABLE for security
2. **Intent Validation**: Verify all broadcast intents
3. **Permission Checks**: Validate sender of broadcasts
4. **Secure Extras**: Don't put sensitive data in notifications

### Service Security

1. **Export Settings**: exported="false" for all services
2. **Permission Requirements**: Custom permissions for service access
3. **Binder Security**: Validate calling UID
4. **State Protection**: Encrypt persisted state

## Testing Strategies

### Service Testing

1. **Robolectric**: For quick service lifecycle tests
2. **AndroidX Test**: For integration tests
3. **Mock Frameworks**: For dependency injection
4. **UI Automator**: For notification tests

### Battery Testing

1. **Battery Historian**: Analyze battery impact
2. **Systrace**: CPU and wake lock analysis
3. **Energy Profiler**: Real-time power monitoring
4. **Monkey Testing**: Long-running stability tests

## Platform-Specific Workarounds

### Manufacturer-Specific Issues

```kotlin
fun requestBatteryOptimizationExemption(context: Context) {
    when (Build.MANUFACTURER.lowercase()) {
        "xiaomi" -> {
            // MIUI specific settings
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)
            context.startActivity(intent)
        }
        "huawei" -> {
            // EMUI specific settings
            val intent = Intent()
            intent.setClassName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            context.startActivity(intent)
        }
        // Add other manufacturers
    }
}
```

### Doze Mode Workarounds

1. **High Priority FCM**: For critical notifications
2. **Foreground Service**: Exempt from Doze
3. **Alarm Manager**: Use setAlarmClock() for user-visible alarms
4. **User Education**: Guide to disable battery optimization

## Future Considerations

### Android 14+ Changes

1. **Foreground Service Types**: More specific type requirements
2. **Notification Runtime Permission**: Handle permission denial
3. **Exact Alarm Permission**: New permission for exact alarms
4. **Background Activity Starts**: Further restrictions

### Potential Enhancements

1. **Wear OS Integration**: Notifications on smartwatches
2. **Assistant Integration**: Google Assistant actions
3. **Widget Support**: Home screen monitoring widget
4. **Bubbles API**: Floating chat-style notifications

## Risk Assessment

### Technical Risks

1. **Service Termination** (Medium Risk)
   - **Threat**: System may still kill foreground services under extreme memory pressure
   - **Mitigation**: Implement state persistence and automatic restart mechanisms
   - **Impact**: Temporary loss of monitoring capability

2. **Battery Drain Complaints** (High Risk)
   - **Threat**: Users may perceive the service as battery-intensive
   - **Mitigation**: Adaptive polling, battery state awareness, clear battery usage display
   - **Impact**: Negative reviews, uninstalls

3. **Notification Fatigue** (Medium Risk)
   - **Threat**: Too many notifications may annoy users
   - **Mitigation**: Smart grouping, importance levels, user customization
   - **Impact**: Users disable notifications entirely

4. **Platform Fragmentation** (Low Risk)
   - **Threat**: Manufacturer customizations may break standard behaviors
   - **Mitigation**: Device-specific workarounds, extensive testing
   - **Impact**: Poor experience on specific devices

### Mitigation Strategies

1. **Graceful Degradation**: Service continues with reduced functionality when restricted
2. **User Education**: Clear explanations of why permissions are needed
3. **Fallback Mechanisms**: Multiple notification delivery paths
4. **Performance Monitoring**: Track battery and resource usage metrics

## Conclusion

The Background Services architecture demonstrates a sophisticated understanding of Android's background execution model. Key strengths include:

1. **Platform Compliance**: Proper use of foreground services
2. **Battery Awareness**: Adaptive behavior based on device state
3. **User Control**: Granular notification preferences
4. **Reliability**: Multiple fallback mechanisms
5. **Performance**: Efficient resource usage

The implementation balances the competing demands of functionality, battery life, and platform restrictions while providing a seamless user experience.