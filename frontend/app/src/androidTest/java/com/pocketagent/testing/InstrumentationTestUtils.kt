package com.pocketagent.testing

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import java.io.IOException

/**
 * Utility functions for instrumentation testing.
 */
object InstrumentationTestUtils {
    /**
     * Gets the test application context.
     */
    fun getContext(): Context {
        return ApplicationProvider.getApplicationContext()
    }

    /**
     * Gets the target context (app under test).
     */
    fun getTargetContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * Gets the test context (test app).
     */
    fun getTestContext(): Context {
        return InstrumentationRegistry.getInstrumentation().context
    }

    /**
     * Gets the UiDevice instance for UI automation.
     */
    fun getUiDevice(): UiDevice {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * Initializes WorkManager for testing.
     */
    fun initializeWorkManager(context: Context = getContext()) {
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    /**
     * Gets the WorkManager instance for testing.
     */
    fun getWorkManager(context: Context = getContext()): WorkManager {
        return WorkManager.getInstance(context)
    }

    /**
     * Waits for idle state.
     */
    fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * Runs code on the UI thread.
     */
    fun runOnUiThread(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    /**
     * Clears app data and cache.
     */
    fun clearAppData() {
        val context = getContext()
        try {
            // Clear databases
            context.databaseList().forEach { dbName ->
                context.deleteDatabase(dbName)
            }

            // Clear shared preferences
            context.getSharedPreferences("default", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            // Clear cache
            context.cacheDir.deleteRecursively()
        } catch (e: Exception) {
            // Handle clearing errors
        }
    }

    /**
     * Grants runtime permissions.
     */
    fun grantPermissions(vararg permissions: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation

        permissions.forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(
                    getContext().packageName,
                    permission,
                )
            } catch (e: Exception) {
                // Permission may already be granted
            }
        }
    }

    /**
     * Simulates device rotation.
     */
    fun rotateDevice() {
        val uiDevice = getUiDevice()
        try {
            uiDevice.setOrientationLeft()
            Thread.sleep(1000)
            uiDevice.setOrientationNatural()
        } catch (e: Exception) {
            // Handle rotation errors
        }
    }

    /**
     * Simulates low memory condition.
     */
    fun simulateLowMemory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HiltTestApplication
        app.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }

    /**
     * Simulates app going to background.
     */
    fun simulateAppBackground() {
        val uiDevice = getUiDevice()
        uiDevice.pressHome()
        Thread.sleep(1000)
    }

    /**
     * Simulates app coming to foreground.
     */
    fun simulateAppForeground() {
        val context = getContext()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Thread.sleep(1000)
    }
}

/**
 * Base class for MockWebServer testing.
 */
abstract class MockWebServerTestBase {
    protected lateinit var mockWebServer: MockWebServer
    protected lateinit var baseUrl: String

    @Before
    open fun setUpMockWebServer() {
        mockWebServer = MockWebServer()
        try {
            mockWebServer.start()
            baseUrl = mockWebServer.url("/").toString()
        } catch (e: IOException) {
            throw RuntimeException("Failed to start MockWebServer", e)
        }
    }

    @After
    open fun tearDownMockWebServer() {
        try {
            mockWebServer.shutdown()
        } catch (e: IOException) {
            // Ignore shutdown errors
        }
    }

    /**
     * Gets the WebSocket URL for testing.
     */
    protected fun getWebSocketUrl(): String {
        return baseUrl.replace("http://", "ws://") + "ws"
    }
}

/**
 * Test utilities for working with permissions.
 */
object PermissionTestUtils {
    /**
     * Grants all required app permissions.
     */
    fun grantAllAppPermissions() {
        InstrumentationTestUtils.grantPermissions(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.USE_BIOMETRIC,
            android.Manifest.permission.USE_FINGERPRINT,
        )
    }

    /**
     * Checks if a permission is granted.
     */
    fun isPermissionGranted(permission: String): Boolean {
        val context = InstrumentationTestUtils.getContext()
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Waits for permission dialog and grants it.
     */
    fun grantPermissionInDialog(timeoutMillis: Long = 5000) {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        val allowButton =
            uiDevice.findObject(
                androidx.test.uiautomator.UiSelector()
                    .textContains("Allow")
                    .className("android.widget.Button"),
            )

        if (allowButton.waitForExists(timeoutMillis)) {
            allowButton.click()
        }
    }

    /**
     * Denies permission in dialog.
     */
    fun denyPermissionInDialog(timeoutMillis: Long = 5000) {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        val denyButton =
            uiDevice.findObject(
                androidx.test.uiautomator.UiSelector()
                    .textContains("Deny")
                    .className("android.widget.Button"),
            )

        if (denyButton.waitForExists(timeoutMillis)) {
            denyButton.click()
        }
    }
}

/**
 * Test utilities for working with notifications.
 */
object NotificationTestUtils {
    /**
     * Opens the notification panel.
     */
    fun openNotificationPanel() {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        uiDevice.openNotification()
    }

    /**
     * Closes the notification panel.
     */
    fun closeNotificationPanel() {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        uiDevice.pressBack()
    }

    /**
     * Finds a notification by title.
     */
    fun findNotificationByTitle(title: String): androidx.test.uiautomator.UiObject? {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        return uiDevice.findObject(
            androidx.test.uiautomator.UiSelector()
                .textContains(title)
                .className("android.widget.TextView"),
        )
    }

    /**
     * Clicks on a notification.
     */
    fun clickNotification(title: String): Boolean {
        openNotificationPanel()
        Thread.sleep(1000)

        val notification = findNotificationByTitle(title)
        return if (notification != null && notification.exists()) {
            notification.click()
            true
        } else {
            closeNotificationPanel()
            false
        }
    }

    /**
     * Swipes away a notification.
     */
    fun dismissNotification(title: String): Boolean {
        openNotificationPanel()
        Thread.sleep(1000)

        val notification = findNotificationByTitle(title)
        return if (notification != null && notification.exists()) {
            notification.swipeRight()
            true
        } else {
            closeNotificationPanel()
            false
        }
    }
}

/**
 * Test utilities for working with system UI.
 */
object SystemUiTestUtils {
    /**
     * Waits for a dialog to appear.
     */
    fun waitForDialog(timeoutMillis: Long = 5000): Boolean {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        val dialog =
            uiDevice.findObject(
                androidx.test.uiautomator.UiSelector()
                    .className("android.app.AlertDialog"),
            )
        return dialog.waitForExists(timeoutMillis)
    }

    /**
     * Clicks OK in a dialog.
     */
    fun clickDialogOk(): Boolean {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        val okButton =
            uiDevice.findObject(
                androidx.test.uiautomator.UiSelector()
                    .textMatches("OK|ok")
                    .className("android.widget.Button"),
            )
        return if (okButton.exists()) {
            okButton.click()
            true
        } else {
            false
        }
    }

    /**
     * Clicks Cancel in a dialog.
     */
    fun clickDialogCancel(): Boolean {
        val uiDevice = InstrumentationTestUtils.getUiDevice()
        val cancelButton =
            uiDevice.findObject(
                androidx.test.uiautomator.UiSelector()
                    .textMatches("Cancel|cancel")
                    .className("android.widget.Button"),
            )
        return if (cancelButton.exists()) {
            cancelButton.click()
            true
        } else {
            false
        }
    }

    /**
     * Handles battery optimization dialog.
     */
    fun handleBatteryOptimizationDialog(allow: Boolean = true) {
        if (waitForDialog()) {
            if (allow) {
                clickDialogOk()
            } else {
                clickDialogCancel()
            }
        }
    }
}
