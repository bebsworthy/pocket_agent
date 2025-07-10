package com.pocketagent.data.service

/**
 * Service result wrapper for consistent error handling across service layer.
 * 
 * This typealias provides a consistent interface for service operations,
 * wrapping the Kotlin Result type to provide standardized success/failure handling.
 */
typealias ServiceResult<T> = Result<T>

/**
 * Extension property to check if a ServiceResult is successful.
 */
val <T> ServiceResult<T>.isSuccess: Boolean
    get() = isSuccess

/**
 * Extension property to check if a ServiceResult is a failure.
 */
val <T> ServiceResult<T>.isFailure: Boolean
    get() = isFailure

/**
 * Extension function to get the result data or null if failed.
 */
fun <T> ServiceResult<T>.getOrNull(): T? = getOrNull()

/**
 * Extension function to get the error or null if successful.
 */
fun <T> ServiceResult<T>.getErrorOrNull(): Throwable? = exceptionOrNull()

/**
 * Extension function to get the error message or null if successful.
 */
fun <T> ServiceResult<T>.getErrorMessage(): String? = exceptionOrNull()?.message