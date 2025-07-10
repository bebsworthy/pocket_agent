package com.pocketagent.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing

/**
 * Utility functions for Mockito testing.
 */
object MockitoTestUtils {
    /**
     * Creates a mock for inline/final classes.
     */
    inline fun <reified T> createMock(): T = Mockito.mock(T::class.java)

    /**
     * Creates a spy for inline/final classes.
     */
    inline fun <reified T> createSpy(obj: T): T = Mockito.spy(obj)

    /**
     * Stub a suspend function with a result.
     */
    suspend fun <T> OngoingStubbing<T>.thenReturnSuspend(value: T): OngoingStubbing<T> {
        return this.thenReturn(value)
    }

    /**
     * Stub a suspend function with an exception.
     */
    suspend fun <T> OngoingStubbing<T>.thenThrowSuspend(throwable: Throwable): OngoingStubbing<T> {
        return this.thenThrow(throwable)
    }

    /**
     * Stub a Flow return value.
     */
    fun <T> stubFlow(value: T): Flow<T> = flowOf(value)

    /**
     * Stub a Flow with multiple values.
     */
    fun <T> stubFlow(vararg values: T): Flow<T> = flowOf(*values)

    /**
     * Argument matcher for any non-null value.
     */
    fun <T> anyNonNull(): T = ArgumentMatchers.any()

    /**
     * Argument matcher for any nullable value.
     */
    fun <T> anyNullable(): T? = ArgumentMatchers.any()
}

/**
 * Extension functions for easier Mockito usage.
 */

/**
 * Stub a suspend function call.
 */
suspend fun <T> T.stub(block: suspend T.() -> Unit): T {
    block()
    return this
}

/**
 * Verify a suspend function call.
 */
suspend fun <T> T.verifyBlocking(block: suspend T.() -> Unit): T {
    block()
    return this
}

/**
 * Mock extension for easier Flow stubbing.
 */
fun <T> Flow<T>.mockReturn(value: T): Flow<T> {
    whenever(this).thenReturn(flowOf(value))
    return this
}

/**
 * Mock extension for easier Result stubbing.
 */
fun <T> Result<T>.mockSuccess(value: T): Result<T> {
    return Result.success(value)
}

/**
 * Mock extension for easier Result failure stubbing.
 */
fun <T> Result<T>.mockFailure(exception: Throwable): Result<T> {
    return Result.failure(exception)
}
