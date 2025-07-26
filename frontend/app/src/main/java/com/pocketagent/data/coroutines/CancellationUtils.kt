package com.pocketagent.data.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive cancellation utilities for the Pocket Agent application.
 *
 * This class provides sophisticated cancellation support, including:
 * - Graceful cancellation with cleanup
 * - Hierarchical cancellation management
 * - Cancellation-aware operations
 * - Proper exception handling during cancellation
 */
@Singleton
class CancellationUtils
    @Inject
    constructor() {
        companion object {
            private const val TAG = "CancellationUtils"
            private const val CLEANUP_TIMEOUT_MS = 5000L
        }

        /**
         * Executes a block with proper cancellation handling.
         */
        suspend fun <T> withCancellationHandling(
            onCancellation: suspend () -> Unit = {},
            block: suspend () -> T,
        ): T =
            try {
                block()
            } catch (e: CancellationException) {
                android.util.Log.d(TAG, "Operation cancelled")
                onCancellation()
                throw e
            }

        /**
         * Executes a block that should complete even if the parent is cancelled.
         */
        suspend fun <T> withNonCancellableCleanup(block: suspend () -> T): T =
            withContext(NonCancellable) {
                block()
            }

        /**
         * Checks if the current coroutine is active and throws CancellationException if not.
         */
        suspend fun checkCancellation() {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        }

        /**
         * Yields execution and checks for cancellation.
         */
        suspend fun yieldAndCheckCancellation() {
            yield()
            checkCancellation()
        }

        /**
         * Executes a long-running operation with periodic cancellation checks.
         */
        suspend fun <T> withPeriodicCancellationCheck(
            checkInterval: Int = 100,
            operation: suspend (checkCancellation: suspend () -> Unit) -> T,
        ): T {
            var operationCount = 0
            return operation {
                if (++operationCount % checkInterval == 0) {
                    yieldAndCheckCancellation()
                }
            }
        }

        /**
         * Cancels a job gracefully with timeout.
         */
        suspend fun cancelGracefully(
            job: Job,
            timeoutMs: Long = CLEANUP_TIMEOUT_MS,
        ) {
            try {
                withContext(NonCancellable) {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        job.cancelAndJoin()
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.w(TAG, "Job cancellation timed out, forcing cancellation")
                job.cancel()
            }
        }

        /**
         * Creates a cancellation-aware flow.
         */
        fun <T> Flow<T>.withCancellationAwareness(): Flow<T> =
            this
                .onStart {
                    android.util.Log.d(TAG, "Flow started")
                }.catch { throwable ->
                    when (throwable) {
                        is CancellationException -> {
                            android.util.Log.d(TAG, "Flow cancelled")
                            throw throwable
                        }
                        else -> {
                            android.util.Log.e(TAG, "Flow error", throwable)
                            throw throwable
                        }
                    }
                }.onCompletion { throwable ->
                    when (throwable) {
                        null -> android.util.Log.d(TAG, "Flow completed normally")
                        is CancellationException -> android.util.Log.d(TAG, "Flow cancelled")
                        else -> android.util.Log.e(TAG, "Flow completed with error", throwable)
                    }
                }
    }

/**
 * Hierarchical cancellation manager for complex cancellation scenarios.
 */
@Singleton
class HierarchicalCancellationManager
    @Inject
    constructor() {
        private val cancellationHierarchy = ConcurrentHashMap<String, CancellationNode>()
        private val nodeCounter = AtomicInteger(0)

        /**
         * Represents a node in the cancellation hierarchy.
         */
        data class CancellationNode(
            val id: String,
            val parentId: String?,
            val job: Job,
            val children: MutableSet<String> = mutableSetOf(),
            val onCancellation: suspend () -> Unit = {},
        )

        /**
         * Registers a job in the cancellation hierarchy.
         */
        fun registerJob(
            job: Job,
            parentId: String? = null,
            onCancellation: suspend () -> Unit = {},
        ): String {
            val nodeId = "node_${nodeCounter.incrementAndGet()}"
            val node = CancellationNode(nodeId, parentId, job, onCancellation = onCancellation)

            cancellationHierarchy[nodeId] = node

            // Add to parent's children
            parentId?.let { parentNodeId ->
                cancellationHierarchy[parentNodeId]?.children?.add(nodeId)
            }

            return nodeId
        }

        /**
         * Cancels a job and all its children.
         */
        suspend fun cancelHierarchy(nodeId: String) {
            val node = cancellationHierarchy[nodeId] ?: return

            // Cancel all children first
            val childrenToCancel = node.children.toList()
            for (childId in childrenToCancel) {
                cancelHierarchy(childId)
            }

            // Cancel this node
            try {
                node.onCancellation()
            } catch (e: Exception) {
                android.util.Log.e("HierarchicalCancellation", "Error during cancellation cleanup", e)
            }

            node.job.cancel()
            cancellationHierarchy.remove(nodeId)

            // Remove from parent's children
            node.parentId?.let { parentId ->
                cancellationHierarchy[parentId]?.children?.remove(nodeId)
            }
        }

        /**
         * Checks if a node is still active.
         */
        fun isNodeActive(nodeId: String): Boolean = cancellationHierarchy[nodeId]?.job?.isActive == true

        /**
         * Gets all active nodes.
         */
        fun getActiveNodes(): List<String> =
            cancellationHierarchy.entries
                .filter { it.value.job.isActive }
                .map { it.key }

        /**
         * Cancels all nodes.
         */
        suspend fun cancelAll() {
            val rootNodes = cancellationHierarchy.values.filter { it.parentId == null }
            for (rootNode in rootNodes) {
                cancelHierarchy(rootNode.id)
            }
        }
    }

/**
 * Cancellation-aware operation executor.
 */
class CancellationAwareExecutor {
    /**
     * Executes operations with proper cancellation support.
     */
    suspend fun <T> executeWithCancellationSupport(
        operations: List<suspend () -> T>,
        failFast: Boolean = true,
        onCancellation: suspend () -> Unit = {},
    ): List<T> {
        val results = mutableListOf<T>()

        try {
            for (operation in operations) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()

                try {
                    val result = operation()
                    results.add(result)
                } catch (e: CancellationException) {
                    onCancellation()
                    throw e
                } catch (e: Exception) {
                    if (failFast) {
                        throw e
                    } else {
                        android.util.Log.w("CancellationAwareExecutor", "Operation failed", e)
                    }
                }
            }
        } catch (e: CancellationException) {
            android.util.Log.d("CancellationAwareExecutor", "Execution cancelled")
            onCancellation()
            throw e
        }

        return results
    }

    /**
     * Executes operations in parallel with cancellation support.
     */
    suspend fun <T> executeParallelWithCancellationSupport(
        operations: List<suspend () -> T>,
        scope: CoroutineScope,
        onCancellation: suspend () -> Unit = {},
    ): List<T> =
        try {
            val deferreds =
                operations.map { operation ->
                    scope.async {
                        operation()
                    }
                }

            deferreds.map { it.await() }
        } catch (e: CancellationException) {
            onCancellation()
            throw e
        }
}

/**
 * Timeout-aware cancellation utilities.
 */
object TimeoutCancellationUtils {
    /**
     * Executes a block with timeout and proper cancellation handling.
     */
    suspend fun <T> withTimeoutAndCancellation(
        timeoutMs: Long,
        onTimeout: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T =
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                block()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("TimeoutCancellation", "Operation timed out after ${timeoutMs}ms")
            onTimeout()
            throw e
        }

    /**
     * Executes a block with timeout or until cancelled.
     */
    suspend fun <T> withTimeoutOrCancellation(
        timeoutMs: Long,
        onTimeout: suspend () -> Unit = {},
        onCancellation: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T =
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                try {
                    block()
                } catch (e: CancellationException) {
                    onCancellation()
                    throw e
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            onTimeout()
            throw e
        }
}

/**
 * Extension functions for cancellation support.
 */
fun CoroutineScope.launchCancellable(
    onCancellation: suspend () -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
): Job =
    this.launch {
        try {
            block()
        } catch (e: CancellationException) {
            android.util.Log.d("CancellationExtensions", "Coroutine cancelled")
            onCancellation()
            throw e
        }
    }

/**
 * Utility for creating cancellation tokens.
 */
class CancellationToken {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled

    fun throwIfCancelled() {
        if (cancelled) {
            throw CancellationException("Operation was cancelled")
        }
    }
}

/**
 * Cooperative cancellation checker for CPU-intensive operations.
 */
class CooperativeCancellationChecker(
    private val scope: CoroutineScope,
    private val checkInterval: Int = 1000,
) {
    private var operationCount = 0

    suspend fun checkCancellation() {
        if (++operationCount % checkInterval == 0) {
            scope.ensureActive()
            yield()
        }
    }

    fun reset() {
        operationCount = 0
    }
}
