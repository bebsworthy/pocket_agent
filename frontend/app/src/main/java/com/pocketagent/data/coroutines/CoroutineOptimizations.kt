package com.pocketagent.data.coroutines

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance optimizations for coroutines in the Pocket Agent application.
 *
 * This class provides various optimization strategies for coroutine usage,
 * including custom dispatchers, flow optimizations, and performance monitoring.
 */
@Singleton
class CoroutineOptimizations
    @Inject
    constructor() {
        companion object {
            private const val WEBSOCKET_THREAD_POOL_SIZE = 4
            private const val BACKGROUND_THREAD_POOL_SIZE = 2
            private const val BUFFER_SIZE_SMALL = 16
            private const val BUFFER_SIZE_MEDIUM = 64
            private const val BUFFER_SIZE_LARGE = 256
            private const val SAMPLE_RATE_MS = 100L
        }

        /**
         * Creates an optimized dispatcher for WebSocket operations.
         */
        fun createWebSocketDispatcher(): CoroutineDispatcher =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use ForkJoinPool for better performance on newer Android versions
                ForkJoinPool(WEBSOCKET_THREAD_POOL_SIZE).asCoroutineDispatcher()
            } else {
                // Fallback to ThreadPoolExecutor for older versions
                Executors.newFixedThreadPool(WEBSOCKET_THREAD_POOL_SIZE).asCoroutineDispatcher()
            }

        /**
         * Creates an optimized dispatcher for background operations.
         */
        fun createBackgroundDispatcher(): CoroutineDispatcher =
            Executors.newFixedThreadPool(BACKGROUND_THREAD_POOL_SIZE).asCoroutineDispatcher()

        /**
         * Optimizes a flow for high-frequency updates.
         */
        fun <T> Flow<T>.optimizeForHighFrequency(): Flow<T> =
            this
                .distinctUntilChanged()
                .conflate()
                .buffer(BUFFER_SIZE_LARGE)

        /**
         * Optimizes a flow for low-frequency updates.
         */
        fun <T> Flow<T>.optimizeForLowFrequency(): Flow<T> =
            this
                .distinctUntilChanged()
                .buffer(BUFFER_SIZE_SMALL)

        /**
         * Optimizes a flow for UI updates.
         */
        fun <T> Flow<T>.optimizeForUI(): Flow<T> =
            this
                .distinctUntilChanged()
                .sample(SAMPLE_RATE_MS)
                .buffer(BUFFER_SIZE_MEDIUM)

        /**
         * Optimizes a flow for WebSocket messages.
         */
        fun <T> Flow<T>.optimizeForWebSocket(): Flow<T> =
            this
                .buffer(BUFFER_SIZE_LARGE)
                .catch { throwable ->
                    // Log WebSocket errors but don't stop the flow
                    android.util.Log.w("CoroutineOptimizations", "WebSocket flow error", throwable)
                    // Re-throw to allow upstream handling
                    throw throwable
                }

        /**
         * Optimizes a flow for background processing.
         */
        fun <T> Flow<T>.optimizeForBackground(): Flow<T> =
            this
                .distinctUntilChanged()
                .buffer(BUFFER_SIZE_MEDIUM)
                .onEach {
                    // Yield to allow other coroutines to run
                    yield()
                }

        /**
         * Applies dispatcher-specific optimizations.
         */
        fun <T> Flow<T>.optimizeForDispatcher(dispatcher: CoroutineDispatcher): Flow<T> =
            this
                .flowOn(dispatcher)
                .buffer(BUFFER_SIZE_MEDIUM)

        /**
         * Applies memory-conscious optimizations.
         */
        fun <T> Flow<T>.optimizeForMemory(): Flow<T> =
            this
                .distinctUntilChanged()
                .buffer(BUFFER_SIZE_SMALL)
                .onEach {
                    // Yield to allow garbage collection
                    yield()
                }

        /**
         * Applies CPU-intensive operation optimizations.
         */
        fun <T> Flow<T>.optimizeForCPU(): Flow<T> =
            this
                .buffer(BUFFER_SIZE_SMALL)
                .onEach {
                    // Yield frequently to prevent blocking
                    yield()
                }
    }

/**
 * Performance monitoring utilities for coroutines.
 */
@Singleton
class CoroutinePerformanceMonitor
    @Inject
    constructor() {
        private val executionCount = AtomicLong(0)
        private val totalExecutionTime = AtomicLong(0)
        private val errorCount = AtomicLong(0)

        /**
         * Measures execution time of a suspend function.
         */
        suspend fun <T> measureExecutionTime(
            operation: String,
            block: suspend () -> T,
        ): T {
            val startTime = System.currentTimeMillis()
            return try {
                val result = block()
                val executionTime = System.currentTimeMillis() - startTime

                executionCount.incrementAndGet()
                totalExecutionTime.addAndGet(executionTime)

                android.util.Log.d("PerformanceMonitor", "$operation completed in ${executionTime}ms")
                result
            } catch (throwable: Throwable) {
                val executionTime = System.currentTimeMillis() - startTime
                errorCount.incrementAndGet()

                android.util.Log.e("PerformanceMonitor", "$operation failed after ${executionTime}ms", throwable)
                throw throwable
            }
        }

        /**
         * Measures execution time of a regular function.
         */
        fun <T> measureExecutionTimeBlocking(
            operation: String,
            block: () -> T,
        ): T =
            runBlocking {
                measureExecutionTime(operation) {
                    withContext(kotlinx.coroutines.Dispatchers.Default) {
                        block()
                    }
                }
            }

        /**
         * Gets performance statistics.
         */
        fun getPerformanceStats(): PerformanceStats {
            val count = executionCount.get()
            val totalTime = totalExecutionTime.get()
            val errors = errorCount.get()

            return PerformanceStats(
                totalExecutions = count,
                totalExecutionTime = totalTime,
                averageExecutionTime = if (count > 0) totalTime / count else 0,
                errorCount = errors,
                successRate = if (count > 0) ((count - errors).toDouble() / count * 100) else 0.0,
            )
        }

        /**
         * Resets performance statistics.
         */
        fun resetStats() {
            executionCount.set(0)
            totalExecutionTime.set(0)
            errorCount.set(0)
        }

        data class PerformanceStats(
            val totalExecutions: Long,
            val totalExecutionTime: Long,
            val averageExecutionTime: Long,
            val errorCount: Long,
            val successRate: Double,
        )
    }

/**
 * Coroutine pooling utilities for resource management.
 */
@Singleton
class CoroutinePoolManager
    @Inject
    constructor() {
        private val jobPool = mutableMapOf<String, Job>()
        private val scopePool = mutableMapOf<String, CoroutineScope>()

        /**
         * Gets or creates a named coroutine scope.
         */
        fun getOrCreateScope(
            name: String,
            dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
        ): CoroutineScope =
            scopePool.getOrPut(name) {
                CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
            }

        /**
         * Launches a coroutine in a named scope.
         */
        fun launchInScope(
            scopeName: String,
            jobName: String,
            dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
            block: suspend CoroutineScope.() -> Unit,
        ): Job {
            val scope = getOrCreateScope(scopeName, dispatcher)

            // Cancel any existing job with the same name
            jobPool[jobName]?.cancel()

            val job = scope.launch(block = block)
            jobPool[jobName] = job

            return job
        }

        /**
         * Cancels a named job.
         */
        fun cancelJob(jobName: String) {
            jobPool[jobName]?.cancel()
            jobPool.remove(jobName)
        }

        /**
         * Cancels all jobs in a scope.
         */
        fun cancelScope(scopeName: String) {
            scopePool[scopeName]?.let { scope ->
                scope.coroutineContext.job.cancel()
                scopePool.remove(scopeName)
            }

            // Remove associated jobs
            jobPool.entries.removeAll { (_, job) ->
                job.isCancelled
            }
        }

        /**
         * Gets active job count.
         */
        fun getActiveJobCount(): Int = jobPool.values.count { it.isActive }

        /**
         * Gets active scope count.
         */
        fun getActiveScopeCount(): Int = scopePool.size

        /**
         * Cleans up completed jobs.
         */
        fun cleanupCompletedJobs() {
            jobPool.entries.removeAll { (_, job) ->
                job.isCompleted
            }
        }
    }

/**
 * Utility for batching coroutine operations.
 */
class CoroutineBatcher<T> {
    private val batch = mutableListOf<T>()
    private var batchJob: Job? = null

    /**
     * Adds an item to the batch.
     */
    fun addToBatch(
        item: T,
        batchSize: Int = 10,
        delayMs: Long = 100,
        scope: CoroutineScope,
        processor: suspend (List<T>) -> Unit,
    ) {
        batch.add(item)

        if (batch.size >= batchSize) {
            processBatch(scope, processor)
        } else {
            // Schedule delayed processing
            batchJob?.cancel()
            batchJob =
                scope.launch {
                    kotlinx.coroutines.delay(delayMs)
                    if (batch.isNotEmpty()) {
                        processBatch(scope, processor)
                    }
                }
        }
    }

    /**
     * Processes the current batch.
     */
    private fun processBatch(
        scope: CoroutineScope,
        processor: suspend (List<T>) -> Unit,
    ) {
        if (batch.isEmpty()) return

        val currentBatch = batch.toList()
        batch.clear()

        scope.launch {
            processor(currentBatch)
        }
    }

    /**
     * Forces processing of the current batch.
     */
    fun flush(
        scope: CoroutineScope,
        processor: suspend (List<T>) -> Unit,
    ) {
        batchJob?.cancel()
        processBatch(scope, processor)
    }
}

/**
 * Extension functions for coroutine optimizations.
 */
fun CoroutineScope.launchOptimized(
    dispatcher: CoroutineDispatcher,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    this.launch(dispatcher) {
        try {
            block()
        } catch (throwable: Throwable) {
            android.util.Log.e("CoroutineOptimizations", "Optimized coroutine failed", throwable)
            throw throwable
        }
    }

/**
 * Utility for memory-conscious coroutine execution.
 */
suspend fun <T> executeWithMemoryOptimization(block: suspend () -> T): T =
    withContext(kotlinx.coroutines.Dispatchers.Default) {
        try {
            block()
        } finally {
            // Suggest garbage collection after memory-intensive operations
            if (Runtime.getRuntime().freeMemory() < Runtime.getRuntime().totalMemory() * 0.1) {
                System.gc()
            }
        }
    }
