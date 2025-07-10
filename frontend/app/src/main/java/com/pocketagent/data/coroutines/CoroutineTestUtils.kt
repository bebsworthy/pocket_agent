package com.pocketagent.data.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test utilities for coroutine testing in the Pocket Agent application.
 * 
 * This class provides utilities for testing coroutines, flows, and suspending functions
 * with proper dispatcher management and time control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineTestUtils {
    
    companion object {
        /**
         * Runs a test with a test dispatcher and automatic cleanup.
         */
        fun runTestWithDispatcher(
            testDispatcher: TestDispatcher = StandardTestDispatcher(),
            block: suspend CoroutineScope.() -> Unit
        ) {
            runTest(testDispatcher) {
                block()
            }
        }
        
        /**
         * Runs a test with an unconfined dispatcher for immediate execution.
         */
        fun runTestUnconfined(
            block: suspend CoroutineScope.() -> Unit
        ) {
            runTest(UnconfinedTestDispatcher()) {
                block()
            }
        }
        
        /**
         * Collects all emissions from a flow for testing.
         */
        suspend fun <T> Flow<T>.collectToList(): List<T> {
            return this.toList()
        }
        
        /**
         * Creates a test scope with a supervisor job.
         */
        fun createTestScope(
            testDispatcher: TestDispatcher = StandardTestDispatcher()
        ): CoroutineScope {
            return CoroutineScope(SupervisorJob() + testDispatcher)
        }
        
        /**
         * Creates test dispatchers for all operation types.
         */
        fun createTestDispatchers(): TestDispatchers {
            val scheduler = TestCoroutineScheduler()
            return TestDispatchers(
                main = StandardTestDispatcher(scheduler),
                io = StandardTestDispatcher(scheduler),
                default = StandardTestDispatcher(scheduler),
                unconfined = UnconfinedTestDispatcher(scheduler)
            )
        }
    }
    
    /**
     * Data class holding test dispatchers.
     */
    data class TestDispatchers(
        val main: TestDispatcher,
        val io: TestDispatcher,
        val default: TestDispatcher,
        val unconfined: TestDispatcher
    )
}

/**
 * Test implementation of CoroutineDispatchers for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineDispatchers(
    private val testDispatchers: CoroutineTestUtils.TestDispatchers
) : CoroutineDispatchers(
    main = testDispatchers.main,
    io = testDispatchers.io,
    default = testDispatchers.default,
    unconfined = testDispatchers.unconfined
) {
    
    /**
     * Advances time for all test dispatchers.
     */
    fun advanceTimeBy(delayTimeMillis: Long) {
        testDispatchers.main.scheduler.advanceTimeBy(delayTimeMillis)
    }
    
    /**
     * Advances time until idle for all test dispatchers.
     */
    fun advanceUntilIdle() {
        testDispatchers.main.scheduler.advanceUntilIdle()
    }
}

/**
 * JUnit rule for setting up test dispatchers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineTestRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    
    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: Description) {
        super.finished(description)
        Dispatchers.resetMain()
    }
    
    /**
     * Runs a test block with the test dispatcher.
     */
    fun runTest(block: suspend CoroutineScope.() -> Unit) {
        kotlinx.coroutines.test.runTest(testDispatcher) {
            block()
        }
    }
    
    /**
     * Advances time by the specified amount.
     */
    fun advanceTimeBy(delayTimeMillis: Long) {
        testDispatcher.scheduler.advanceTimeBy(delayTimeMillis)
    }
    
    /**
     * Advances time until all coroutines are idle.
     */
    fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}

/**
 * Test implementation of CoroutineScopes for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineScopes(
    private val testDispatchers: CoroutineTestUtils.TestDispatchers
) {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + testDispatchers.default)
    private val webSocketScope = CoroutineScope(SupervisorJob() + testDispatchers.io)
    private val backgroundScope = CoroutineScope(SupervisorJob() + testDispatchers.default)
    
    fun getApplicationScope(): CoroutineScope = applicationScope
    fun getWebSocketScope(): CoroutineScope = webSocketScope
    fun getBackgroundScope(): CoroutineScope = backgroundScope
    
    /**
     * Cancels all test scopes.
     */
    fun cancelAll() {
        applicationScope.cancel()
        webSocketScope.cancel()
        backgroundScope.cancel()
    }
}

/**
 * Utility for testing flows with time control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowTestUtils {
    
    companion object {
        /**
         * Tests a flow with time control and assertion.
         */
        suspend fun <T> testFlow(
            flow: Flow<T>,
            testDispatcher: TestDispatcher = StandardTestDispatcher(),
            timeAdvancement: Long = 0,
            expectedValues: List<T>
        ) {
            runTest(testDispatcher) {
                val values = mutableListOf<T>()
                
                val job = launch {
                    flow.collect { value ->
                        values.add(value)
                    }
                }
                
                if (timeAdvancement > 0) {
                    advanceTimeBy(timeAdvancement)
                }
                
                advanceUntilIdle()
                job.cancel()
                
                assert(values == expectedValues) {
                    "Expected $expectedValues but got $values"
                }
            }
        }
        
        /**
         * Tests a flow that should emit specific values in order.
         */
        suspend fun <T> testFlowEmissions(
            flow: Flow<T>,
            block: suspend FlowTestCollector<T>.() -> Unit
        ) {
            val collector = FlowTestCollector<T>()
            
            runTest {
                val job = launch {
                    flow.collect { value ->
                        collector.addValue(value)
                    }
                }
                
                block(collector)
                
                job.cancel()
            }
        }
    }
    
    /**
     * Helper class for collecting and asserting flow values.
     */
    class FlowTestCollector<T> {
        private val values = mutableListOf<T>()
        
        fun addValue(value: T) {
            values.add(value)
        }
        
        fun assertValueCount(count: Int) {
            assert(values.size == count) {
                "Expected $count values but got ${values.size}"
            }
        }
        
        fun assertValueAt(index: Int, expected: T) {
            assert(values.size > index) {
                "No value at index $index"
            }
            assert(values[index] == expected) {
                "Expected $expected at index $index but got ${values[index]}"
            }
        }
        
        fun assertValues(vararg expected: T) {
            assert(values == expected.toList()) {
                "Expected ${expected.toList()} but got $values"
            }
        }
        
        fun getValues(): List<T> = values.toList()
    }
}

/**
 * Mock implementation of error handler for testing.
 */
class MockCoroutineErrorHandler : CoroutineErrorHandler() {
    
    val errors = mutableListOf<Throwable>()
    
    override fun createGeneralExceptionHandler(): kotlinx.coroutines.CoroutineExceptionHandler {
        return kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            errors.add(throwable)
        }
    }
    
    fun getLastError(): Throwable? = errors.lastOrNull()
    fun getErrorCount(): Int = errors.size
    fun clearErrors() = errors.clear()
}

/**
 * Extension functions for testing coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.launchTest(
    testDispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend CoroutineScope.() -> Unit
) {
    this.launch(testDispatcher) {
        block()
    }
}

/**
 * Utility for testing suspend functions with timeout.
 */
suspend fun <T> testSuspendFunction(
    timeoutMs: Long = 5000,
    block: suspend () -> T
): T {
    return kotlinx.coroutines.withTimeout(timeoutMs) {
        block()
    }
}

/**
 * Utility for testing cancellation behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testCancellation(
    testDispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend CoroutineScope.() -> Unit
) {
    runTest(testDispatcher) {
        val job = launch {
            block()
        }
        
        // Cancel the job and verify it's cancelled
        job.cancel()
        assert(job.isCancelled) {
            "Job should be cancelled"
        }
    }
}