package com.pocketagent.di.modules

import com.pocketagent.di.qualifiers.ApplicationScope
import com.pocketagent.di.qualifiers.BackgroundScope
import com.pocketagent.di.qualifiers.DefaultDispatcher
import com.pocketagent.di.qualifiers.IoDispatcher
import com.pocketagent.di.qualifiers.MainDispatcher
import com.pocketagent.di.qualifiers.TestScope
import com.pocketagent.di.qualifiers.UnconfinedDispatcher
import com.pocketagent.di.qualifiers.WebSocketScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides coroutine-related dependencies including dispatchers, scopes, and exception handlers.
 *
 * Key Features:
 * - Custom dispatcher configuration for different operation types
 * - Scoped coroutine contexts for lifecycle management
 * - Exception handling with proper error propagation
 * - Performance-optimized configurations
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    /**
     * Provides Main dispatcher for UI operations.
     * Used for UI updates and immediate execution on the main thread.
     */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher {
        return Dispatchers.Main.immediate
    }

    /**
     * Provides IO dispatcher for I/O operations.
     * Optimized for file operations, network requests, and database operations.
     */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    /**
     * Provides Default dispatcher for CPU-intensive operations.
     * Used for data processing, encryption, and computational tasks.
     */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default
    }

    /**
     * Provides Unconfined dispatcher for testing and immediate execution.
     * Should be used carefully in production code.
     */
    @Provides
    @UnconfinedDispatcher
    fun provideUnconfinedDispatcher(): CoroutineDispatcher {
        return Dispatchers.Unconfined
    }

    /**
     * Provides application-wide coroutine scope.
     * Lives for the entire application lifecycle.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        applicationExceptionHandler: CoroutineExceptionHandler,
    ): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + defaultDispatcher + applicationExceptionHandler,
        )

    /**
     * Provides WebSocket-specific coroutine scope.
     * Manages WebSocket connection lifecycle and message handling.
     */
    @Provides
    @Singleton
    @WebSocketScope
    fun provideWebSocketScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        webSocketExceptionHandler: CoroutineExceptionHandler,
    ): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + ioDispatcher + webSocketExceptionHandler,
        )

    /**
     * Provides background service coroutine scope.
     * Used for background monitoring and periodic tasks.
     */
    @Provides
    @Singleton
    @BackgroundScope
    fun provideBackgroundScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        backgroundExceptionHandler: CoroutineExceptionHandler,
    ): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + defaultDispatcher + backgroundExceptionHandler,
        )

    /**
     * Provides test coroutine scope.
     * Used for testing utilities and test-specific operations.
     */
    @Provides
    @TestScope
    fun provideTestScope(
        @UnconfinedDispatcher unconfinedDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + unconfinedDispatcher)

    /**
     * Provides global application exception handler.
     * Handles uncaught exceptions in application-wide coroutines.
     */
    @Provides
    @Singleton
    fun provideApplicationExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            // Log the exception
            android.util.Log.e("PocketAgent", "Uncaught exception in application coroutine", throwable)

            // In a real app, you might want to:
            // - Send crash report to analytics
            // - Show user-friendly error message
            // - Restart critical services

            // For now, we'll just log it
            throwable.printStackTrace()
        }

    /**
     * Provides WebSocket-specific exception handler.
     * Handles WebSocket connection errors and message processing failures.
     */
    @Provides
    @Singleton
    fun provideWebSocketExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("PocketAgent", "WebSocket coroutine exception", throwable)

            // WebSocket-specific error handling:
            // - Trigger reconnection logic
            // - Update connection state
            // - Notify UI of connection issues

            when (throwable) {
                is java.net.SocketTimeoutException -> {
                    // Handle timeout - likely network issue
                    android.util.Log.w("PocketAgent", "WebSocket timeout, will attempt reconnection")
                }
                is java.net.ConnectException -> {
                    // Handle connection failure
                    android.util.Log.w("PocketAgent", "WebSocket connection failed")
                }
                else -> {
                    // Handle other exceptions
                    android.util.Log.e("PocketAgent", "Unexpected WebSocket error: ${throwable.message}")
                }
            }
        }

    /**
     * Provides background service exception handler.
     * Handles exceptions in background monitoring and periodic tasks.
     */
    @Provides
    @Singleton
    fun provideBackgroundExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("PocketAgent", "Background service coroutine exception", throwable)

            // Background service error handling:
            // - Continue background operations if possible
            // - Log important failures
            // - Potentially restart service on critical errors

            // Most background errors shouldn't crash the app
            throwable.printStackTrace()
        }
}
