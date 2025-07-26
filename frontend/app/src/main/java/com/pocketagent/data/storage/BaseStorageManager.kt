package com.pocketagent.data.storage

import com.pocketagent.domain.models.Result
import kotlinx.coroutines.flow.Flow

/**
 * Base class for storage managers.
 *
 * This abstract class provides common functionality for storage operations
 * including error handling and consistent result types.
 *
 * @param T The type of data being stored
 */
abstract class BaseStorageManager<T> {
    /**
     * Saves data to storage.
     *
     * @param data The data to save
     * @return Success or error result
     */
    abstract suspend fun save(data: T): Result<Unit>

    /**
     * Loads data from storage.
     *
     * @return The loaded data or error result
     */
    abstract suspend fun load(): Result<T?>

    /**
     * Clears all data from storage.
     *
     * @return Success or error result
     */
    abstract suspend fun clear(): Result<Unit>

    /**
     * Checks if data exists in storage.
     *
     * @return True if data exists
     */
    abstract suspend fun exists(): Result<Boolean>

    /**
     * Observes data changes in storage.
     *
     * @return Flow emitting data changes
     */
    abstract fun observe(): Flow<Result<T?>>

    /**
     * Handles storage exceptions and converts them to Result.Error.
     *
     * @param operation The storage operation to execute
     * @return Result wrapped operation result
     */
    protected suspend fun <R> handleStorageOperation(operation: suspend () -> R): Result<R> =
        try {
            Result.Success(operation())
        } catch (e: Exception) {
            Result.Error(e, "Storage operation failed: ${e.message}")
        }
}
