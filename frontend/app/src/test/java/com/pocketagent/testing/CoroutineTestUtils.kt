package com.pocketagent.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule for coroutine testing that sets up test dispatchers.
 */
@ExperimentalCoroutinesApi
class CoroutineTestRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}

/**
 * Utility functions for coroutine testing.
 */
@ExperimentalCoroutinesApi
object CoroutineTestUtils {
    /**
     * Creates a test scope with UnconfinedTestDispatcher.
     */
    fun createTestScope(): TestScope = TestScope(UnconfinedTestDispatcher())

    /**
     * Creates a test scope with StandardTestDispatcher.
     */
    fun createStandardTestScope(): TestScope = TestScope(StandardTestDispatcher())

    /**
     * Runs a test with a given TestScope.
     */
    fun runTest(
        testScope: TestScope = createTestScope(),
        testBody: suspend TestScope.() -> Unit,
    ) {
        testScope.runTest { testBody() }
    }

    /**
     * Advances time in the test scope.
     */
    fun TestScope.advanceTimeBy(delayTimeMillis: Long) {
        this.advanceTimeBy(delayTimeMillis)
    }

    /**
     * Advances time until all scheduled tasks are complete.
     */
    fun TestScope.advanceUntilIdle() {
        this.advanceUntilIdle()
    }

    /**
     * Runs all pending tasks immediately.
     */
    fun TestScope.runCurrent() {
        this.runCurrent()
    }
}

/**
 * Test utilities for Flow testing.
 */
@ExperimentalCoroutinesApi
object FlowTestUtils {
    /**
     * Collects all emissions from a Flow within a test scope.
     */
    suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collectInTest(testScope: TestScope): List<T> {
        val emissions = mutableListOf<T>()
        val job =
            testScope.launch {
                this@collectInTest.collect { emission ->
                    emissions.add(emission)
                }
            }
        testScope.advanceUntilIdle()
        job.cancel()
        return emissions
    }

    /**
     * Collects the first emission from a Flow within a test scope.
     */
    suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstInTest(testScope: TestScope): T {
        var result: T? = null
        val job =
            testScope.launch {
                result = this@firstInTest.first()
            }
        testScope.advanceUntilIdle()
        job.cancel()
        return result ?: throw IllegalStateException("No emission received")
    }

    /**
     * Tests that a Flow emits a specific sequence of values.
     */
    suspend fun <T> kotlinx.coroutines.flow.Flow<T>.assertEmitsSequence(
        testScope: TestScope,
        vararg expectedValues: T,
    ) {
        val actualValues = collectInTest(testScope)
        assert(actualValues == expectedValues.toList()) {
            "Expected sequence: ${expectedValues.toList()}, but got: $actualValues"
        }
    }

    /**
     * Tests that a Flow emits at least the specified values.
     */
    suspend fun <T> kotlinx.coroutines.flow.Flow<T>.assertEmitsAtLeast(
        testScope: TestScope,
        vararg expectedValues: T,
    ) {
        val actualValues = collectInTest(testScope)
        expectedValues.forEach { expected ->
            assert(actualValues.contains(expected)) {
                "Expected value $expected was not emitted. Actual values: $actualValues"
            }
        }
    }

    /**
     * Tests that a Flow doesn't emit any values.
     */
    suspend fun <T> kotlinx.coroutines.flow.Flow<T>.assertNoEmissions(
        testScope: TestScope,
        timeoutMillis: Long = 1000,
    ) {
        val emissions = mutableListOf<T>()
        val job =
            testScope.launch {
                this@assertNoEmissions.collect { emission ->
                    emissions.add(emission)
                }
            }
        testScope.advanceTimeBy(timeoutMillis)
        job.cancel()
        assert(emissions.isEmpty()) {
            "Expected no emissions, but got: $emissions"
        }
    }
}

/**
 * Test utilities for StateFlow testing.
 */
@ExperimentalCoroutinesApi
object StateFlowTestUtils {
    /**
     * Waits for a StateFlow to emit a specific value.
     */
    suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.waitForValue(
        testScope: TestScope,
        expectedValue: T,
        timeoutMillis: Long = 5000,
    ) {
        val startTime = System.currentTimeMillis()
        while (this.value != expectedValue) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw AssertionError(
                    "StateFlow did not emit expected value $expectedValue within ${timeoutMillis}ms. Current value: ${this.value}",
                )
            }
            testScope.advanceTimeBy(50)
        }
    }

    /**
     * Asserts that a StateFlow has a specific value.
     */
    fun <T> kotlinx.coroutines.flow.StateFlow<T>.assertValue(expectedValue: T) {
        assert(this.value == expectedValue) {
            "Expected StateFlow value: $expectedValue, but got: ${this.value}"
        }
    }

    /**
     * Asserts that a StateFlow value satisfies a predicate.
     */
    fun <T> kotlinx.coroutines.flow.StateFlow<T>.assertValueSatisfies(
        predicate: (T) -> Boolean,
        message: String = "StateFlow value does not satisfy predicate",
    ) {
        assert(predicate(this.value)) {
            "$message. Current value: ${this.value}"
        }
    }
}

/**
 * Test utilities for SharedFlow testing.
 */
@ExperimentalCoroutinesApi
object SharedFlowTestUtils {
    /**
     * Collects a specific number of emissions from a SharedFlow.
     */
    suspend fun <T> kotlinx.coroutines.flow.SharedFlow<T>.collectCount(
        testScope: TestScope,
        count: Int,
        timeoutMillis: Long = 5000,
    ): List<T> {
        val emissions = mutableListOf<T>()
        val job =
            testScope.launch {
                this@collectCount.collect { emission ->
                    emissions.add(emission)
                    if (emissions.size >= count) {
                        throw kotlinx.coroutines.CancellationException("Collected $count emissions")
                    }
                }
            }

        val startTime = System.currentTimeMillis()
        while (emissions.size < count) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                job.cancel()
                throw AssertionError(
                    "SharedFlow did not emit $count values within ${timeoutMillis}ms. Got ${emissions.size} values: $emissions",
                )
            }
            testScope.advanceTimeBy(50)
        }

        job.cancel()
        return emissions.take(count)
    }

    /**
     * Waits for a SharedFlow to emit any value.
     */
    suspend fun <T> kotlinx.coroutines.flow.SharedFlow<T>.waitForEmission(
        testScope: TestScope,
        timeoutMillis: Long = 5000,
    ): T {
        val emissions = collectCount(testScope, 1, timeoutMillis)
        return emissions.first()
    }
}

/**
 * Extension functions for easier testing.
 */
@ExperimentalCoroutinesApi
fun TestScope.runTest(testBody: suspend TestScope.() -> Unit) {
    this.runTest { testBody() }
}

@ExperimentalCoroutinesApi
fun TestScope.launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): kotlinx.coroutines.Job = this.launch { block() }

@ExperimentalCoroutinesApi
suspend fun TestScope.delay(timeMillis: Long) {
    this.advanceTimeBy(timeMillis)
}
