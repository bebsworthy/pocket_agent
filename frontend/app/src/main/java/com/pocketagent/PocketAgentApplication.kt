package com.pocketagent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main application class for Pocket Agent.
 * Configured with Hilt for dependency injection.
 */
@HiltAndroidApp
class PocketAgentApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize security providers
        initializeSecurityProviders()
    }
    
    private fun initializeSecurityProviders() {
        // Initialize Bouncy Castle security provider for SSH operations
        try {
            java.security.Security.insertProviderAt(
                org.bouncycastle.jce.provider.BouncyCastleProvider(),
                1
            )
        } catch (e: Exception) {
            // Log error but don't crash the app
            e.printStackTrace()
        }
    }
}