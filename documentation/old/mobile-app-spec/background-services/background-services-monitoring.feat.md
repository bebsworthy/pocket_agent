# Background Services Feature Specification - Monitoring & Optimization
**For Android Mobile Application**

> **Navigation**: [Index](./background-services-index.md) | [Overview](./background-services-overview.feat.md) | [Foreground Service](./background-services-foreground.feat.md) | [Notifications](./background-services-notifications.feat.md) | **Monitoring** | [Testing](./background-services-testing.feat.md)

## Work Manager Integration

**Purpose**: Implements scheduled background tasks using Android's WorkManager for operations that don't require immediate execution. Handles periodic cleanup, log rotation, cache management, and other maintenance tasks while respecting system constraints and battery optimization.

### Key Components

- **WorkManagerScheduler**: Schedules and manages periodic work
- **CleanupWorker**: Removes old messages and orphaned sessions
- **CacheCleanupWorker**: Manages app cache size
- **SessionBackupWorker**: Backs up active session state
- **MetricsCollectionWorker**: Collects usage analytics

### Scheduled Tasks

1. **Daily Cleanup**
   - Remove messages older than 30 days
   - Clean orphaned sessions
   - Rotate log files

2. **Cache Management** (Every 3 days)
   - Clear image cache
   - Remove temporary files
   - Optimize database

3. **Session Backup** (Every 12 hours)
   - Backup active project sessions
   - Save conversation history
   - Preserve quick actions

4. **Metrics Collection** (Daily)
   - Usage statistics
   - Performance metrics
   - Error tracking

## Battery Optimization Manager

**Purpose**: Manages app behavior based on battery state to optimize power consumption. Implements adaptive polling frequencies, reduces background work during low battery, and respects Android's battery optimization features while maintaining essential functionality.

### Battery States

```kotlin
enum class BatteryState {
    CHARGING,        // Device is charging
    FULL,           // Battery is full (>90%)
    NORMAL,         // Battery is normal (30-90%)
    LOW,            // Battery is low (15-30%)
    CRITICAL        // Battery is critical (<15%)
}
```

### Polling Frequencies

| Battery State | Health Check Interval | UI Update Rate | Background Work |
|--------------|---------------------|----------------|-----------------|
| CHARGING     | 3 seconds          | Real-time      | Full           |
| FULL/NORMAL  | 5 seconds          | Real-time      | Full           |
| LOW          | 15 seconds         | 1 second       | Reduced        |
| CRITICAL     | 30 seconds         | 2 seconds      | Minimal        |

### Key Features

- **Adaptive Polling**: Automatically adjusts based on battery level
- **User Preferences**: Allow users to override optimization
- **Charging Detection**: Resume normal operation when charging
- **Doze Mode Compliance**: Respect system power saving modes

## Connection Health Monitor

**Purpose**: Monitors the health of active WebSocket connections and performs periodic health checks. Detects connection failures, measures latency, tracks message flow, and triggers reconnection when necessary while providing detailed connection diagnostics.

### Health Check Criteria

1. **WebSocket State**: Connection open and authenticated
2. **Ping/Pong**: Response within timeout (5 seconds)
3. **Message Flow**: Messages being sent/received
4. **Authentication**: Session still valid
5. **Server Health**: Wrapper service responding

### Monitoring Features

- **Periodic Health Checks**: Based on battery state
- **Latency Measurement**: Track round-trip times
- **Failure Detection**: Immediate notification of issues
- **Auto-Recovery**: Trigger reconnection on failure
- **Diagnostics**: Detailed connection logs

## Session State Persistence

**Purpose**: Persists and restores session state across app lifecycle events, process death, and device reboots. Ensures users can resume their work seamlessly even after the app is killed by the system.

### Persisted Data

- **Active Projects**: List of monitored projects
- **Connection States**: Last known connection status
- **Message History**: Recent conversation cache
- **Quick Actions**: User preferences and history
- **UI State**: Last viewed screen and position

### Persistence Triggers

- App backgrounded
- Low memory warning
- Configuration change
- Service shutdown
- Periodic backup

## Wake Lock Manager

**Purpose**: Manages wake locks to ensure critical operations complete even when the device screen is off. Uses partial wake locks judiciously to balance functionality with battery life.

### Wake Lock Usage

1. **Permission Handling**: Keep device awake for permission timeout
2. **Critical Operations**: File operations, deployments
3. **Message Processing**: Ensure messages are processed
4. **Reconnection**: Complete reconnection attempts

### Best Practices

- Acquire wake locks only when necessary
- Use timeout to prevent indefinite locks
- Release immediately when done
- Track wake lock usage for debugging

## Server Resource Monitor

**Purpose**: Monitors CPU, memory, and disk usage on the remote development server to prevent resource exhaustion and provide early warning of potential issues.

### Monitored Resources

1. **CPU Usage**: Load average and percentage
2. **Memory**: Used, available, and swap
3. **Disk Space**: Free space on project partition
4. **Process Count**: Number of active processes
5. **Network**: Connection count and bandwidth

### Alert Thresholds

| Resource | Warning | Critical |
|----------|---------|----------|
| CPU      | 80%     | 95%      |
| Memory   | 85%     | 95%      |
| Disk     | 90%     | 95%      |
| Swap     | 50%     | 80%      |

## Progress Parser

**Purpose**: Detects operation completion status from Claude result messages to provide basic operation tracking.

### v1 Functionality

- Detect operation start (first Claude message)
- Show "Working..." status during operation
- Detect result messages (success/error)
- Update UI with completion status

### Result Detection

- Monitor for `result` message type
- Check `subtype`: "success" or "error_*"
- Display completion notification
- Clear "Working..." status

## Integration Points

### With Foreground Service

```kotlin
// Update monitoring based on battery state
batteryOptimizationManager.batteryState
    .onEach { state ->
        connectionHealthMonitor.updateFrequency(
            batteryOptimizationManager.getPollingFrequency(state)
        )
    }
```

### With Notification System

```kotlin
// Show resource alerts
serverResourceMonitor.resourceAlerts
    .onEach { alert ->
        notificationManager.showResourceAlert(
            projectName = alert.projectName,
            resource = alert.resourceType,
            level = alert.level
        )
    }
```

### With Data Layer

```kotlin
// Persist session state
sessionStatePersistence.saveState(
    activeProjects = activeProjects,
    connectionStates = connectionStates,
    messageCache = recentMessages
)
```

## Performance Considerations

1. **Battery Impact**
   - Minimize wake lock usage
   - Adaptive polling frequencies
   - Batch operations when possible
   - Respect Doze mode

2. **Memory Usage**
   - Limit message history cache
   - Clean up old data regularly
   - Use efficient data structures
   - Monitor memory pressure

3. **Network Efficiency**
   - Batch status updates
   - Use compression
   - Minimize polling when idle
   - Cache server responses

4. **CPU Usage**
   - Offload parsing to background threads
   - Use efficient regex patterns
   - Throttle UI updates
   - Batch notification updates