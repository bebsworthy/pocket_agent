package com.pocketagent.testing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Utility functions for MockK testing.
 */
object MockKTestUtils {
    /**
     * Creates a relaxed mock that returns sensible defaults.
     */
    inline fun <reified T : Any> createRelaxedMock(): T = mockk<T>(relaxed = true)

    /**
     * Creates a spy with relaxed behavior.
     */
    inline fun <reified T : Any> createRelaxedSpy(obj: T): T = spyk(obj, recordPrivateCalls = true)

    /**
     * Stub a suspend function with a result.
     */
    suspend fun <T> MockKStubScope<T, T>.returnsResult(value: T): MockKStubScope<T, T> = this.returns(value)

    /**
     * Stub a suspend function with an exception.
     */
    suspend fun <T> MockKStubScope<T, T>.throwsException(exception: Throwable): MockKStubScope<T, T> = this.throws(exception)

    /**
     * Stub a Flow return value.
     */
    fun <T> stubFlow(value: T): Flow<T> = flowOf(value)

    /**
     * Stub a Flow with multiple values.
     */
    fun <T> stubFlow(vararg values: T): Flow<T> = flowOf(*values)

    /**
     * Verify a suspend function was called.
     */
    suspend fun <T> T.verifyBlocking(block: suspend T.() -> Unit): T {
        verify { runBlocking { block() } }
        return this
    }
}

// Extension functions for easier MockK usage

/**
 * Stub a suspend function call with MockK.
 */
suspend fun <T> T.stub(block: suspend T.() -> Unit): T {
    every { runBlocking { block() } } returns Unit
    return this
}

/**
 * Mock extension for easier Flow stubbing with MockK.
 */
fun <T> Flow<T>.mockReturn(value: T): Flow<T> {
    every { this@mockReturn } returns flowOf(value)
    return this
}

/**
 * Mock extension for easier Result success stubbing with MockK.
 */
fun <T> mockSuccess(value: T): Result<T> = Result.success(value)

/**
 * Mock extension for easier Result failure stubbing with MockK.
 */
fun <T> mockFailure(exception: Throwable): Result<T> = Result.failure(exception)

/**
 * Verify that a suspend function was called with specific parameters.
 */
suspend fun <T> T.verifyCall(block: suspend T.() -> Unit) {
    verify { runBlocking { block() } }
}

/**
 * Verify that a suspend function was called exactly n times.
 */
suspend fun <T> T.verifyCall(
    exactly: Int,
    block: suspend T.() -> Unit,
) {
    verify(exactly = exactly) { runBlocking { block() } }
}

/**
 * Verify that a suspend function was never called.
 */
suspend fun <T> T.verifyNeverCalled(block: suspend T.() -> Unit) {
    verify(exactly = 0) { runBlocking { block() } }
}
