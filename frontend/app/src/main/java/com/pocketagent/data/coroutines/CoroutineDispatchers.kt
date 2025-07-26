package com.pocketagent.data.coroutines

import com.pocketagent.di.qualifiers.DefaultDispatcher
import com.pocketagent.di.qualifiers.IoDispatcher
import com.pocketagent.di.qualifiers.MainDispatcher
import com.pocketagent.di.qualifiers.UnconfinedDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized dispatcher provider for consistent coroutine usage across the application.
 *
 * This class provides a single point of access for all coroutine dispatchers,
 * making it easier to manage and test coroutine-based operations.
 *
 * Usage:
 * ```
 * class MyRepository @Inject constructor(
 *     private val dispatchers: CoroutineDispatchers
 * ) {
 *     suspend fun loadData() = withContext(dispatchers.io) {
 *         // Perform I/O operation
 *     }
 * }
 * ```
 */
@Singleton
class CoroutineDispatchers
    @Inject
    constructor(
        @MainDispatcher val main: CoroutineDispatcher,
        @IoDispatcher val io: CoroutineDispatcher,
        @DefaultDispatcher val default: CoroutineDispatcher,
        @UnconfinedDispatcher val unconfined: CoroutineDispatcher,
    ) {
        /**
         * Returns the appropriate dispatcher for the given operation type.
         *
         * @param operationType The type of operation to be performed
         * @return The appropriate dispatcher for the operation
         */
        fun getDispatcher(operationType: OperationType): CoroutineDispatcher =
            when (operationType) {
                OperationType.UI -> main
                OperationType.IO -> io
                OperationType.CPU -> default
                OperationType.IMMEDIATE -> unconfined
            }

        /**
         * Enum defining different types of operations and their appropriate dispatchers.
         */
        enum class OperationType {
            /** UI updates and main thread operations */
            UI,

            /** I/O operations (network, file system, database) */
            IO,

            /** CPU-intensive operations (computation, encryption) */
            CPU,

            /** Immediate execution (testing, simple operations) */
            IMMEDIATE,
        }
    }

/**
 * Extension function to easily get dispatcher for specific operation types.
 */
inline fun <T> CoroutineDispatchers.withDispatcher(
    operationType: CoroutineDispatchers.OperationType,
    crossinline block: suspend () -> T,
): suspend () -> T =
    {
        kotlinx.coroutines.withContext(getDispatcher(operationType)) {
            block()
        }
    }
