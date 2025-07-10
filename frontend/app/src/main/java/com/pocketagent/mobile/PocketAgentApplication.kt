package com.pocketagent.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Pocket Agent that enables Hilt dependency injection.
 * 
 * This class serves as the entry point for the Hilt dependency injection framework
 * and initializes the application-level dependencies.
 */
@HiltAndroidApp
class PocketAgentApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize application-level components
        initializeLogging()
        initializeSecurityProvider()
    }
    
    /**
     * Initialize logging configuration for the application
     */
    private fun initializeLogging() {
        // Configure logging for debug/release builds
        // This will be expanded when we add proper logging framework
    }
    
    /**
     * Initialize security provider for cryptographic operations
     */
    private fun initializeSecurityProvider() {
        // Initialize Bouncy Castle security provider for SSH operations
        // This will be expanded when we implement SSH functionality
    }
}