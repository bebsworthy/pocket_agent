package com.pocketagent.data.coroutines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketagent.di.qualifiers.ApplicationScope
import com.pocketagent.di.qualifiers.BackgroundScope
import com.pocketagent.di.qualifiers.WebSocketScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized scope management for the Pocket Agent application.
 *
 * This class provides a unified interface for managing different coroutine scopes
 * throughout the application lifecycle, ensuring proper cleanup and cancellation.
 */
@Singleton
class CoroutineScopes
    @Inject
    constructor(
        @ApplicationScope private val applicationScope: CoroutineScope,
        @WebSocketScope private val webSocketScope: CoroutineScope,
        @BackgroundScope private val backgroundScope: CoroutineScope,
    ) {
        /**
         * Gets the application-wide coroutine scope.
         * Use this for operations that should live for the entire app lifecycle.
         */
        fun getApplicationScope(): CoroutineScope = applicationScope

        /**
         * Gets the WebSocket coroutine scope.
         * Use this for WebSocket connection management and message handling.
         */
        fun getWebSocketScope(): CoroutineScope = webSocketScope

        /**
         * Gets the background service coroutine scope.
         * Use this for background monitoring and periodic tasks.
         */
        fun getBackgroundScope(): CoroutineScope = backgroundScope

        /**
         * Launches a coroutine in the application scope.
         */
        fun launchInApplicationScope(block: suspend CoroutineScope.() -> Unit): Job {
            return applicationScope.launch(block = block)
        }

        /**
         * Launches a coroutine in the WebSocket scope.
         */
        fun launchInWebSocketScope(block: suspend CoroutineScope.() -> Unit): Job {
            return webSocketScope.launch(block = block)
        }

        /**
         * Launches a coroutine in the background scope.
         */
        fun launchInBackgroundScope(block: suspend CoroutineScope.() -> Unit): Job {
            return backgroundScope.launch(block = block)
        }

        /**
         * Cancels all scopes (typically called during app shutdown).
         */
        fun cancelAllScopes() {
            applicationScope.cancel()
            webSocketScope.cancel()
            backgroundScope.cancel()
        }
    }

/**
 * Extension functions for ViewModel coroutine scope management.
 */
object ViewModelScopeExtensions {
    /**
     * Launches a coroutine in the ViewModel scope with error handling.
     */
    fun ViewModel.launchWithErrorHandling(
        onError: (Throwable) -> Unit = { it.printStackTrace() },
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch {
            try {
                block()
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }
    }

    /**
     * Launches a coroutine in the ViewModel scope with supervisor behavior.
     * This ensures that if one child coroutine fails, others continue running.
     */
    fun ViewModel.launchWithSupervisor(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch {
            supervisorScope {
                block()
            }
        }
    }
}

/**
 * Lifecycle-aware coroutine scope that can be cancelled when the lifecycle is destroyed.
 */
class LifecycleCoroutineScope(
    private val baseScope: CoroutineScope,
) {
    private var scopeJob: Job? = null

    /**
     * Creates a new scope tied to the lifecycle.
     */
    fun createScope(): CoroutineScope {
        val job = Job()
        scopeJob = job
        return baseScope + job
    }

    /**
     * Cancels the current scope.
     */
    fun cancelScope() {
        scopeJob?.cancel()
        scopeJob = null
    }

    /**
     * Checks if the scope is active.
     */
    fun isActive(): Boolean {
        return scopeJob?.isActive == true
    }
}

/**
 * Utility class for managing cancellation of coroutines.
 */
class CancellationManager {
    private val jobs = mutableMapOf<String, Job>()

    /**
     * Registers a job with a key for later cancellation.
     */
    fun registerJob(
        key: String,
        job: Job,
    ) {
        // Cancel any existing job with the same key
        jobs[key]?.cancel()
        jobs[key] = job
    }

    /**
     * Cancels a job by key.
     */
    fun cancelJob(key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
    }

    /**
     * Cancels all registered jobs.
     */
    fun cancelAllJobs() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Checks if a job is still active.
     */
    fun isJobActive(key: String): Boolean {
        return jobs[key]?.isActive == true
    }

    /**
     * Gets the number of active jobs.
     */
    fun getActiveJobCount(): Int {
        return jobs.values.count { it.isActive }
    }
}

/**
 * Extension functions for coroutine scope management.
 */
fun CoroutineScope.launchCancellable(
    cancellationManager: CancellationManager,
    key: String,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val job = this.launch(block = block)
    cancellationManager.registerJob(key, job)
    return job
}

/**
 * Creates a scope that automatically cancels when the parent scope is cancelled.
 */
fun CoroutineScope.createChildScope(): CoroutineScope {
    return this + Job()
}

/**
 * Utility for creating scopes with specific error handling.
 */
fun CoroutineScope.withErrorHandling(onError: (Throwable) -> Unit): CoroutineScope {
    return this +
        kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            onError(throwable)
        }
}
