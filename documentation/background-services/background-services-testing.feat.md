# Background Services Feature Specification - Testing
**For Android Mobile Application**

> **Navigation**: [Index](./background-services-index.md) | [Overview](./background-services-overview.feat.md) | [Foreground Service](./background-services-foreground.feat.md) | [Notifications](./background-services-notifications.feat.md) | [Monitoring](./background-services-monitoring.feat.md) | **Testing**

## Testing Overview

Comprehensive testing is crucial for background services due to their complex lifecycle, system integration, and impact on battery life. This specification covers unit tests, integration tests, and performance testing strategies.

## Testing Checklist

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

## Unit Tests

### Battery Optimization Manager Tests

```kotlin
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
```

### Notification Manager Tests

```kotlin
import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class NotificationManagerTest {
    
    @Mock
    private lateinit var mockNotificationManager: NotificationManagerCompat
    
    private lateinit var context: Context
    private lateinit var appNotificationManager: AppNotificationManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        appNotificationManager = AppNotificationManager(context)
    }
    
    @Test
    fun testMonitoringNotificationCreation() {
        val notification = appNotificationManager.createMonitoringNotification(
            activeProjectCount = 2,
            connectionStatus = ConnectionStatus.CONNECTED
        )
        
        assertNotNull(notification)
        assertEquals(Notification.FLAG_ONGOING_EVENT, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }
    
    @Test
    fun testPermissionRequestNotification() {
        // Create spy to verify notification
        val spy = spy(appNotificationManager)
        
        spy.showPermissionRequestNotification(
            projectName = "Test Project",
            tool = "Editor",
            action = "create file",
            requestId = "req_123",
            timeout = 60
        )
        
        verify(spy).notify(any(), any())
    }
    
    @Test
    fun testNotificationChannelCreation() {
        val channelIds = listOf(
            AppNotificationManager.CHANNEL_MONITORING,
            AppNotificationManager.CHANNEL_PERMISSIONS,
            AppNotificationManager.CHANNEL_TASKS,
            AppNotificationManager.CHANNEL_ERRORS,
            AppNotificationManager.CHANNEL_PROGRESS,
            AppNotificationManager.CHANNEL_ALERTS
        )
        
        // Verify all channels are created
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager = context.getSystemService(NotificationManager::class.java)
            channelIds.forEach { channelId ->
                assertNotNull(systemNotificationManager.getNotificationChannel(channelId))
            }
        }
    }
}
```

### WorkManager Scheduler Tests

```kotlin
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    
    @Test
    fun testWorkCancellation() {
        scheduler.schedulePeriodicWork()
        scheduler.cancelWork(WorkManagerScheduler.WORK_CLEANUP)
        
        val workInfos = workManager.getWorkInfosForUniqueWork(
            WorkManagerScheduler.WORK_CLEANUP
        ).get()
        
        assertTrue(workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
```

### Connection Health Monitor Tests

```kotlin
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ConnectionHealthMonitorTest {
    
    @Mock private lateinit var mockWebSocketManager: WebSocketManager
    @Mock private lateinit var mockConnectionManager: ConnectionManager
    @Mock private lateinit var mockBatteryOptimizationManager: BatteryOptimizationManager
    @Mock private lateinit var mockNotificationManager: AppNotificationManager
    
    private lateinit var healthMonitor: ConnectionHealthMonitor
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        healthMonitor = ConnectionHealthMonitor(
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
        
        // Mock healthy connection
        whenever(mockWebSocketManager.isConnected(projectId)).thenReturn(true)
        whenever(mockWebSocketManager.sendPing(projectId)).thenReturn(true)
        
        healthMonitor.startMonitoring(projectId, 5000L)
        advanceTimeBy(5100L)
        
        verify(mockWebSocketManager, atLeastOnce()).sendPing(projectId)
    }
    
    @Test
    fun testFailureDetection() = testScope.runTest {
        val projectId = "test_project"
        
        // Mock connection failure
        whenever(mockWebSocketManager.isConnected(projectId)).thenReturn(false)
        
        healthMonitor.startMonitoring(projectId, 1000L)
        advanceTimeBy(1100L)
        
        verify(mockConnectionManager).triggerReconnection(projectId)
    }
}
```

## Integration Tests

### Foreground Service Integration Test

```kotlin
@RunWith(AndroidJUnit4::class)
class ForegroundServiceIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var serviceIntent: Intent
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        serviceIntent = Intent(context, BackgroundOperationsService::class.java)
    }
    
    @Test
    fun testServiceLifecycle() {
        // Start service
        serviceIntent.action = BackgroundOperationsService.ACTION_START_MONITORING
        serviceIntent.putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, "test_project")
        context.startForegroundService(serviceIntent)
        
        // Verify service is running
        val serviceController = Robolectric.buildService(BackgroundOperationsService::class.java, serviceIntent)
        val service = serviceController.create().get()
        assertNotNull(service)
        
        // Stop service
        serviceIntent.action = BackgroundOperationsService.ACTION_STOP_MONITORING
        context.startService(serviceIntent)
        
        // Verify service stops
        serviceController.destroy()
    }
}
```

### Notification Action Integration Test

```kotlin
@RunWith(AndroidJUnit4::class)
class NotificationActionIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var receiver: NotificationActionReceiver
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        receiver = NotificationActionReceiver()
    }
    
    @Test
    fun testPermissionApprovalFlow() {
        val intent = Intent().apply {
            action = AppNotificationManager.ACTION_APPROVE
            putExtra("request_id", "test_request")
            putExtra("approved", true)
        }
        
        receiver.onReceive(context, intent)
        
        // Verify permission was handled
        // In real test, would verify through injected dependencies
    }
}
```

## Performance Tests

### Battery Consumption Test

```kotlin
@LargeTest
class BatteryConsumptionTest {
    
    @Test
    fun measureBatteryImpact() {
        // Run service for extended period
        val startBatteryLevel = getBatteryLevel()
        
        // Start monitoring 3 projects
        repeat(3) { i ->
            BackgroundOperationsService.startMonitoring(context, "project_$i")
        }
        
        // Run for 1 hour
        Thread.sleep(TimeUnit.HOURS.toMillis(1))
        
        val endBatteryLevel = getBatteryLevel()
        val consumption = startBatteryLevel - endBatteryLevel
        
        // Assert reasonable battery consumption (< 5% per hour)
        assertTrue(consumption < 5)
    }
}
```

### Memory Usage Test

```kotlin
@Test
fun testMemoryUsage() {
    val runtime = Runtime.getRuntime()
    val startMemory = runtime.totalMemory() - runtime.freeMemory()
    
    // Start service with multiple projects
    repeat(10) { i ->
        BackgroundOperationsService.startMonitoring(context, "project_$i")
    }
    
    // Force garbage collection
    System.gc()
    Thread.sleep(1000)
    
    val endMemory = runtime.totalMemory() - runtime.freeMemory()
    val memoryIncrease = endMemory - startMemory
    
    // Assert reasonable memory usage (< 50MB for 10 projects)
    assertTrue(memoryIncrease < 50 * 1024 * 1024)
}
```

## Test Utilities

### Mock Factories

```kotlin
object TestFactories {
    
    fun createMockProject(id: String = "test_project"): Project {
        return Project(
            id = id,
            name = "Test Project",
            serverProfileId = "server_1",
            projectPath = "/home/test/project",
            scriptsFolder = "scripts",
            claudeSessionId = "session_123",
            lastActive = Instant.now()
        )
    }
    
    fun createMockBatteryState(
        percentage: Int = 50,
        isCharging: Boolean = false
    ): BatteryState {
        return BatteryState(
            percentage = percentage,
            isCharging = isCharging,
            isPowerSaveMode = false,
            level = when {
                isCharging -> BatteryLevel.CHARGING
                percentage > 30 -> BatteryLevel.NORMAL
                percentage > 15 -> BatteryLevel.LOW
                else -> BatteryLevel.CRITICAL
            }
        )
    }
    
    fun createMockNotification(): Notification {
        return NotificationCompat.Builder(context, "test_channel")
            .setContentTitle("Test Notification")
            .setContentText("Test Content")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
```

### Test Rules

```kotlin
@ExperimentalCoroutinesApi
class CoroutineTestRule : TestWatcher() {
    
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }
    
    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}
```

## Testing Best Practices

1. **Service Testing**
   - Use Robolectric for service lifecycle tests
   - Mock system services (NotificationManager, PowerManager)
   - Test service restart scenarios
   - Verify foreground notification behavior

2. **Notification Testing**
   - Verify notification content and actions
   - Test notification channel creation
   - Check notification dismissal behavior
   - Test action handling

3. **Background Task Testing**
   - Use WorkManager test utilities
   - Test constraint satisfaction
   - Verify periodic work scheduling
   - Test work cancellation

4. **Performance Testing**
   - Measure actual battery consumption
   - Monitor memory usage over time
   - Track wake lock duration
   - Profile CPU usage

5. **Integration Testing**
   - Test full user flows
   - Verify cross-component interactions
   - Test error recovery scenarios
   - Validate state persistence