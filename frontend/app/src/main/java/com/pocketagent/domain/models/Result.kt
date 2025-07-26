package com.pocketagent.domain.models

import com.pocketagent.domain.models.error.DomainException

/**
 * A generic wrapper for handling operation results with success and error states.
 *
 * This sealed class provides a consistent way to handle success and error states
 * across the application, following functional programming principles.
 *
 * @param T The type of data contained in successful results
 */
sealed class Result<out T> {
    /**
     * Represents a successful operation result.
     *
     * @param data The successful result data
     */
    data class Success<out T>(
        val data: T,
    ) : Result<T>()

    /**
     * Represents an error operation result.
     *
     * @param exception The exception that caused the error
     * @param message Optional error message
     */
    data class Error(
        val exception: Throwable,
        val message: String? = null,
    ) : Result<Nothing>()

    /**
     * Represents a loading state.
     */
    object Loading : Result<Nothing>()

    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Returns true if this is an Error result.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns true if this is a Loading result.
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * Returns the data if this is a Success result, otherwise null.
     */
    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            else -> null
        }

    /**
     * Returns the data if this is a Success result, otherwise throws the exception.
     */
    fun getOrThrow(): T =
        when (this) {
            is Success -> data
            is Error -> throw exception
            is Loading -> error("Cannot get data from loading state")
        }

    /**
     * Returns the data if this is a Success result, otherwise returns the default value.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T =
        when (this) {
            is Success -> data
            else -> defaultValue
        }

    /**
     * Returns the data if this is a Success result, otherwise returns the result of the function.
     */
    inline fun getOrElse(onError: (Throwable) -> @UnsafeVariance T): T =
        when (this) {
            is Success -> data
            is Error -> onError(exception)
            is Loading -> error("Cannot get data from loading state")
        }

    /**
     * Returns true if this is a domain-specific error.
     */
    val isDomainError: Boolean
        get() = this is Error && exception is DomainException

    /**
     * Returns the domain exception if this is a domain error, otherwise null.
     */
    fun getDomainException(): DomainException? =
        when (this) {
            is Error -> exception as? DomainException
            else -> null
        }
}

/**
 * Extension function to map the data of a Success result.
 *
 * @param transform The transformation function to apply to the success data
 * @return A new Result with the transformed data
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }

/**
 * Extension function to handle both success and error cases.
 *
 * @param onSuccess Called when the result is successful
 * @param onError Called when the result is an error
 * @param onLoading Called when the result is loading
 */
inline fun <T> Result<T>.fold(
    onSuccess: (T) -> Unit = {},
    onError: (Throwable, String?) -> Unit = { _, _ -> },
    onLoading: () -> Unit = {},
) {
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(exception, message)
        is Result.Loading -> onLoading()
    }
}

/**
 * Extension function to transform the data of a Success result.
 *
 * @param transform The transformation function to apply to the success data
 * @return A new Result with the transformed data
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> this
        is Result.Loading -> this
    }

/**
 * Extension function to recover from errors.
 *
 * @param recovery The recovery function to apply to the error
 * @return A new Result with the recovered data or the original success
 */
inline fun <T> Result<T>.recover(recovery: (Throwable) -> T): Result<T> =
    when (this) {
        is Result.Success -> this
        is Result.Error -> Result.Success(recovery(exception))
        is Result.Loading -> this
    }

/**
 * Extension function to recover from errors with another Result.
 *
 * @param recovery The recovery function to apply to the error
 * @return A new Result with the recovered data or the original success
 */
inline fun <T> Result<T>.recoverWith(recovery: (Throwable) -> Result<T>): Result<T> =
    when (this) {
        is Result.Success -> this
        is Result.Error -> recovery(exception)
        is Result.Loading -> this
    }

/**
 * Extension function to combine two results.
 *
 * @param other The other result to combine with
 * @param combine The function to combine the two success values
 * @return A new Result with the combined data
 */
inline fun <T, U, V> Result<T>.zip(
    other: Result<U>,
    combine: (T, U) -> V,
): Result<V> =
    when {
        this is Result.Success && other is Result.Success -> Result.Success(combine(data, other.data))
        this is Result.Error -> this
        other is Result.Error -> other
        this is Result.Loading -> this
        other is Result.Loading -> other
        else -> Result.Error(IllegalStateException("Unexpected result state"))
    }

/**
 * Creates a successful result.
 */
fun <T> resultOf(data: T): Result<T> = Result.Success(data)

/**
 * Creates an error result.
 */
fun <T> resultError(
    exception: Throwable,
    message: String? = null,
): Result<T> = Result.Error(exception, message)

/**
 * Creates a loading result.
 */
fun <T> resultLoading(): Result<T> = Result.Loading

/**
 * Executes a block and wraps the result in a Result.
 */
inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
