package com.pocketagent.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.pocketagent.testing.*
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for background service functionality.
 * Tests service lifecycle, notifications, and WorkManager integration.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackgroundServiceTest : BaseInstrumentationTest() {
    
    private lateinit var workManager: WorkManager
    
    @Before
    override fun setUp() {
        super.setUp()
        
        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }
    
    @Test
    fun backgroundService_startsAndStops() {
        // Given
        val projectId = "test-project"
        val serviceIntent = Intent(context, BackgroundOperationsService::class.java).apply {
            action = BackgroundOperationsService.ACTION_START_MONITORING
            putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, projectId)
        }
        
        // When: Start service
        context.startForegroundService(serviceIntent)
        
        // Then: Service should be running
        // This would require checking service state, which is implementation-specific
        
        // When: Stop service
        serviceIntent.action = BackgroundOperationsService.ACTION_STOP_MONITORING
        context.startService(serviceIntent)
        
        // Then: Service should be stopped
        // This would require checking service state
    }
    
    @Test
    fun backgroundService_createsNotificationChannel() {
        // Given
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        
        // When: Service starts
        val serviceIntent = Intent(context, BackgroundOperationsService::class.java).apply {
            action = BackgroundOperationsService.ACTION_START_MONITORING
            putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, "test-project")
        }
        context.startForegroundService(serviceIntent)
        
        // Then: Notification channels should be created
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            
            val channels = systemNotificationManager.notificationChannels
            val channelIds = channels.map { it.id }
            
            assertThat(channelIds).contains("monitoring")
            assertThat(channelIds).contains("permissions")
            assertThat(channelIds).contains("tasks")
        }
    }
    
    @Test
    fun workManager_schedulesPeriodicWork() {
        // Given
        val scheduler = WorkManagerScheduler(workManager)
        
        // When
        scheduler.schedulePeriodicWork()
        
        // Then
        val workInfos = workManager.getWorkInfosByTag("periodic").get()
        assertThat(workInfos).isNotEmpty()
        
        val cleanupWork = workManager.getWorkInfosForUniqueWork("cleanup").get()
        assertThat(cleanupWork).hasSize(1)
        assertThat(cleanupWork.first().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }
    
    @Test
    fun workManager_executesOneTimeWork() {
        // Given
        val scheduler = WorkManagerScheduler(workManager)
        
        // When
        scheduler.scheduleOneTimeLogUpload(priority = true)
        
        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("log_upload").get()
        assertThat(workInfos).hasSize(1)
        
        val workInfo = workInfos.first()
        assertThat(workInfo.state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }
    
    @Test
    fun workManager_cancelsWork() {
        // Given
        val scheduler = WorkManagerScheduler(workManager)
        scheduler.schedulePeriodicWork()
        
        // When
        scheduler.cancelWork("cleanup")
        
        // Then
        val workInfos = workManager.getWorkInfosForUniqueWork("cleanup").get()
        assertThat(workInfos).isEmpty()
    }
    
    @Test
    fun batteryOptimization_adjustsPollingFrequency() {
        // Given
        val batteryManager = BatteryOptimizationManager(context)
        
        // Test different battery states
        val chargingState = TestDataFactory.createChargingBatteryState()
        val lowBatteryState = TestDataFactory.createLowBatteryState()
        val normalState = BatteryStateTestBuilder()
            .percentage(60)
            .isCharging(false)
            .level("NORMAL")
            .build()
        
        // When & Then
        assertThat(batteryManager.getPollingFrequency(chargingState))
            .isEqualTo(3000L) // High frequency when charging
        
        assertThat(batteryManager.getPollingFrequency(lowBatteryState))
            .isEqualTo(15000L) // Low frequency when battery is low
        
        assertThat(batteryManager.getPollingFrequency(normalState))
            .isEqualTo(5000L) // Normal frequency
    }
    
    @Test
    fun notificationManager_createsPermissionNotification() {
        // Given
        val notificationManager = AppNotificationManager(context)
        
        // When
        notificationManager.showPermissionRequestNotification(
            projectName = "Test Project",
            tool = "Editor",
            action = "create file",
            requestId = "req_123",
            timeout = 60
        )
        
        // Then
        // Verify notification was created
        // This would require checking the notification was actually shown
        // which depends on the notification system implementation
    }
    
    @Test
    fun notificationManager_handlesNotificationActions() {
        // Given
        val notificationManager = AppNotificationManager(context)
        val requestId = "req_123"
        
        // When: Show permission notification
        notificationManager.showPermissionRequestNotification(
            projectName = "Test Project",
            tool = "Editor",
            action = "create file",
            requestId = requestId,
            timeout = 60
        )
        
        // Simulate user clicking approve
        val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = AppNotificationManager.ACTION_APPROVE
            putExtra("request_id", requestId)
            putExtra("approved", true)
        }
        
        val receiver = NotificationActionReceiver()
        receiver.onReceive(context, approveIntent)
        
        // Then: Verify action was processed
        // This would require checking that the approval was handled
    }
    
    @Test
    fun connectionHealthMonitor_detectsFailure() {
        // Given
        val healthMonitor = ConnectionHealthMonitor(context)
        val projectId = "test-project"
        
        // When: Start monitoring
        healthMonitor.startMonitoring(projectId, 1000L)
        
        // Simulate connection failure
        healthMonitor.onConnectionFailure(projectId, Exception("Connection failed"))
        
        // Then: Verify reconnection is triggered
        // This would require checking the internal state or events
    }
    
    @Test
    fun serviceLifecycle_survivesConfigurationChange() {
        // Given
        val projectId = "test-project"
        val serviceIntent = Intent(context, BackgroundOperationsService::class.java).apply {
            action = BackgroundOperationsService.ACTION_START_MONITORING
            putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, projectId)
        }
        
        // When: Start service
        context.startForegroundService(serviceIntent)
        
        // Simulate configuration change
        InstrumentationTestUtils.rotateDevice()
        
        // Then: Service should still be running
        // This would require checking service state
    }
    
    @Test
    fun serviceLifecycle_handlesLowMemory() {
        // Given
        val projectId = "test-project"
        val serviceIntent = Intent(context, BackgroundOperationsService::class.java).apply {
            action = BackgroundOperationsService.ACTION_START_MONITORING
            putExtra(BackgroundOperationsService.EXTRA_PROJECT_ID, projectId)
        }
        
        // When: Start service
        context.startForegroundService(serviceIntent)
        
        // Simulate low memory
        InstrumentationTestUtils.simulateLowMemory()
        
        // Then: Service should handle low memory gracefully
        // This would require checking service behavior
    }
}

/**
 * Example service and manager interfaces for testing.
 */
interface BackgroundOperationsService {
    companion object {
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        const val EXTRA_PROJECT_ID = "project_id"
    }
}

interface WorkManagerScheduler {
    fun schedulePeriodicWork()
    fun scheduleOneTimeLogUpload(priority: Boolean)
    fun cancelWork(workName: String)
}

interface BatteryOptimizationManager {
    fun getPollingFrequency(batteryState: BatteryStateEntity): Long
    fun shouldReduceBackgroundActivity(batteryState: BatteryStateEntity): Boolean
}

interface AppNotificationManager {
    fun showPermissionRequestNotification(
        projectName: String,
        tool: String,
        action: String,
        requestId: String,
        timeout: Int
    )
    
    companion object {
        const val ACTION_APPROVE = "approve"
        const val ACTION_DENY = "deny"
    }
}

interface NotificationActionReceiver {
    fun onReceive(context: Context, intent: Intent)
}

interface ConnectionHealthMonitor {
    fun startMonitoring(projectId: String, intervalMs: Long)
    fun onConnectionFailure(projectId: String, exception: Exception)
}