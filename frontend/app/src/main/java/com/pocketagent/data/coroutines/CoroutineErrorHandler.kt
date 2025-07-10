package com.pocketagent.data.coroutines

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling for coroutines in the Pocket Agent application.
 * 
 * This class provides standardized error handling, logging, and recovery mechanisms
 * for different types of exceptions that can occur in coroutines.
 */
@Singleton
class CoroutineErrorHandler @Inject constructor() {
    
    companion object {
        private const val TAG = "CoroutineErrorHandler"
    }
    
    /**
     * Creates a general-purpose exception handler for application coroutines.
     */
    fun createGeneralExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleException(throwable, "General")
        }
    }
    
    /**
     * Creates a WebSocket-specific exception handler.
     */
    fun createWebSocketExceptionHandler(
        onConnectionError: () -> Unit = {},
        onTimeoutError: () -> Unit = {},
        onUnknownError: (Throwable) -> Unit = {}
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleWebSocketException(throwable, onConnectionError, onTimeoutError, onUnknownError)
        }
    }
    
    /**
     * Creates a background service exception handler.
     */
    fun createBackgroundServiceExceptionHandler(
        onServiceRestart: () -> Unit = {}
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleBackgroundServiceException(throwable, onServiceRestart)
        }
    }
    
    /**
     * Creates a repository exception handler.
     */
    fun createRepositoryExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleRepositoryException(throwable)
        }
    }
    
    /**
     * Handles general exceptions with appropriate logging and recovery.
     */
    private fun handleException(throwable: Throwable, context: String) {
        // Don't log cancellation exceptions as they're expected
        if (throwable is CancellationException) {
            Log.d(TAG, "Coroutine cancelled in $context context")
            return
        }
        
        Log.e(TAG, "Exception in $context coroutine", throwable)
        
        when (throwable) {
            is OutOfMemoryError -> {
                Log.e(TAG, "Out of memory error - attempting cleanup")
                // Trigger garbage collection
                System.gc()
                // Potentially restart critical services
            }
            is SecurityException -> {
                Log.e(TAG, "Security exception - may need to re-authenticate")
                // Potentially trigger re-authentication
            }
            is IllegalStateException -> {
                Log.e(TAG, "Illegal state - checking application state")
                // Potentially reset application state
            }
            else -> {
                Log.e(TAG, "Unexpected exception type: ${throwable.javaClass.simpleName}")
            }
        }
    }
    
    /**
     * Handles WebSocket-specific exceptions.
     */
    private fun handleWebSocketException(
        throwable: Throwable,
        onConnectionError: () -> Unit,
        onTimeoutError: () -> Unit,
        onUnknownError: (Throwable) -> Unit
    ) {
        if (throwable is CancellationException) {
            Log.d(TAG, "WebSocket coroutine cancelled")
            return
        }
        
        Log.e(TAG, "WebSocket exception", throwable)
        
        when (throwable) {
            is ConnectException -> {
                Log.w(TAG, "WebSocket connection failed - server may be down")
                onConnectionError()
            }
            is SocketTimeoutException -> {
                Log.w(TAG, "WebSocket timeout - network may be slow")
                onTimeoutError()
            }
            is UnknownHostException -> {
                Log.w(TAG, "WebSocket unknown host - DNS resolution failed")
                onConnectionError()
            }
            is javax.net.ssl.SSLException -> {
                Log.e(TAG, "SSL/TLS error in WebSocket connection")
                onConnectionError()
            }
            else -> {
                Log.e(TAG, "Unexpected WebSocket error: ${throwable.javaClass.simpleName}")
                onUnknownError(throwable)
            }
        }
    }
    
    /**
     * Handles background service exceptions.
     */
    private fun handleBackgroundServiceException(
        throwable: Throwable,
        onServiceRestart: () -> Unit
    ) {
        if (throwable is CancellationException) {
            Log.d(TAG, "Background service coroutine cancelled")
            return
        }
        
        Log.e(TAG, "Background service exception", throwable)
        
        when (throwable) {
            is OutOfMemoryError -> {
                Log.e(TAG, "Background service out of memory - restarting")
                onServiceRestart()
            }
            is SecurityException -> {
                Log.e(TAG, "Background service security exception")
                // May need to request permissions again
            }
            else -> {
                Log.e(TAG, "Background service error: ${throwable.javaClass.simpleName}")
                // Most background errors shouldn't crash the service
            }
        }
    }
    
    /**
     * Handles repository-specific exceptions.
     */
    private fun handleRepositoryException(throwable: Throwable) {
        if (throwable is CancellationException) {
            Log.d(TAG, "Repository coroutine cancelled")
            return
        }
        
        Log.e(TAG, "Repository exception", throwable)
        
        when (throwable) {
            is java.io.IOException -> {
                Log.w(TAG, "Repository I/O error - may retry operation")
            }
            is kotlinx.serialization.SerializationException -> {
                Log.e(TAG, "Serialization error in repository")
            }
            is java.sql.SQLException -> {
                Log.e(TAG, "Database error in repository")
            }
            else -> {
                Log.e(TAG, "Repository error: ${throwable.javaClass.simpleName}")
            }
        }
    }
}

/**
 * Utility functions for common error handling patterns.
 */
object ErrorHandlingUtils {
    
    /**
     * Executes a suspending function with error handling.
     */
    suspend fun <T> safeCall(
        onError: (Throwable) -> T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            when (throwable) {
                is CancellationException -> throw throwable // Don't handle cancellation
                else -> onError(throwable)
            }
        }
    }
    
    /**
     * Executes a suspending function with retry logic.
     */
    suspend fun <T> safeCallWithRetry(
        retries: Int = 3,
        onError: (Throwable) -> T,
        block: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        
        repeat(retries) { attempt ->
            try {
                return block()
            } catch (throwable: Throwable) {
                when (throwable) {
                    is CancellationException -> throw throwable
                    else -> {
                        lastException = throwable
                        Log.w("ErrorHandlingUtils", "Attempt ${attempt + 1} failed", throwable)
                        if (attempt < retries - 1) {
                            // Add exponential backoff
                            kotlinx.coroutines.delay(1000L * (attempt + 1))
                        }
                    }
                }
            }
        }
        
        return onError(lastException!!)
    }
    
    /**
     * Checks if an exception is recoverable (should be retried).
     */
    fun isRecoverableException(throwable: Throwable): Boolean {
        return when (throwable) {
            is CancellationException -> false
            is OutOfMemoryError -> false
            is SocketTimeoutException -> true
            is ConnectException -> true
            is UnknownHostException -> true
            is java.io.IOException -> true
            else -> false
        }
    }
}

/**
 * Enumeration of different error types for categorization.
 */
enum class ErrorType {
    NETWORK,
    SECURITY,
    STORAGE,
    SERIALIZATION,
    UNKNOWN
}

/**
 * Data class for structured error information.
 */
data class ErrorInfo(
    val type: ErrorType,
    val message: String,
    val throwable: Throwable? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val recoverable: Boolean = false
)

/**
 * Error categorization utility.
 */
object ErrorCategorizer {
    
    /**
     * Categorizes a throwable into an ErrorInfo object.
     */
    fun categorizeError(throwable: Throwable): ErrorInfo {
        val type = when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> ErrorType.NETWORK
            is SecurityException, is javax.net.ssl.SSLException -> ErrorType.SECURITY
            is java.io.IOException, is java.sql.SQLException -> ErrorType.STORAGE
            is kotlinx.serialization.SerializationException -> ErrorType.SERIALIZATION
            else -> ErrorType.UNKNOWN
        }
        
        val recoverable = ErrorHandlingUtils.isRecoverableException(throwable)
        
        return ErrorInfo(
            type = type,
            message = throwable.message ?: "Unknown error",
            throwable = throwable,
            recoverable = recoverable
        )
    }
}