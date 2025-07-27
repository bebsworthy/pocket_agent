package com.pocketagent.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main application class for Pocket Agent
 */
@HiltAndroidApp
class PocketAgentApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize any necessary components
        // This will be expanded as we add more features
    }
}