package com.pocketagent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketagent.services.BackgroundMonitoringService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Broadcast receiver that starts the background monitoring service when the device boots.
 * This ensures that ongoing Claude Code sessions are monitored even after device restart.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Check for active sessions and start monitoring service
            // Future enhancement: add session state check before starting service
            BackgroundMonitoringService.startService(context)
        }
    }
}
