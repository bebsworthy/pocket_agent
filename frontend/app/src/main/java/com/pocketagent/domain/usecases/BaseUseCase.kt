package com.pocketagent.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Base use case for handling business logic operations.
 *
 * This abstract class provides a foundation for all use cases in the application,
 * following the Clean Architecture principles. It handles execution context and
 * provides a consistent structure for business logic operations.
 *
 * @param P Parameter type for the use case
 * @param R Return type for the use case
 */
abstract class BaseUseCase<in P, R> {
    /**
     * Executes the use case with the provided parameters.
     *
     * @param parameters The input parameters for the use case
     * @return The result of the use case execution
     */
    abstract suspend fun execute(parameters: P): R

    /**
     * Executes the use case and returns a Flow for reactive programming.
     *
     * @param parameters The input parameters for the use case
     * @param context The coroutine context for execution
     * @return A Flow emitting the result of the use case
     */
    fun executeAsFlow(
        parameters: P,
        context: CoroutineContext,
    ): Flow<R> =
        flow {
            emit(execute(parameters))
        }.flowOn(context)
}

/**
 * Base use case for operations that don't require parameters.
 *
 * @param R Return type for the use case
 */
abstract class BaseUseCaseNoParams<R> {
    /**
     * Executes the use case without parameters.
     *
     * @return The result of the use case execution
     */
    abstract suspend fun execute(): R

    /**
     * Executes the use case and returns a Flow for reactive programming.
     *
     * @param context The coroutine context for execution
     * @return A Flow emitting the result of the use case
     */
    fun executeAsFlow(context: CoroutineContext): Flow<R> =
        flow {
            emit(execute())
        }.flowOn(context)
}
