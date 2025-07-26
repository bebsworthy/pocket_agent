package com.pocketagent

import android.app.Application
import android.util.Log
import com.pocketagent.data.service.ApplicationException
import dagger.hilt.android.HiltAndroidApp
import java.security.NoSuchProviderException
import java.security.Security

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
            Security.insertProviderAt(
                org.bouncycastle.jce.provider
                    .BouncyCastleProvider(),
                1,
            )
            Log.i("PocketAgentApplication", "Successfully initialized BouncyCastle security provider")
        } catch (e: NoSuchProviderException) {
            Log.e("PocketAgentApplication", "BouncyCastle provider not available", e)
            throw ApplicationException.SecurityProviderException("BouncyCastle provider not available", e)
        } catch (e: SecurityException) {
            Log.e("PocketAgentApplication", "Security exception initializing BouncyCastle provider", e)
            throw ApplicationException.SecurityProviderException("Security error initializing BouncyCastle provider", e)
        } catch (e: IllegalArgumentException) {
            Log.e("PocketAgentApplication", "Invalid argument initializing BouncyCastle provider", e)
            throw ApplicationException.ConfigurationException("Invalid configuration for BouncyCastle provider", e)
        } catch (e: RuntimeException) {
            Log.e("PocketAgentApplication", "Runtime error initializing BouncyCastle provider", e)
            throw ApplicationException.SecurityProviderException("Runtime error initializing BouncyCastle provider", e)
        }
    }
}
