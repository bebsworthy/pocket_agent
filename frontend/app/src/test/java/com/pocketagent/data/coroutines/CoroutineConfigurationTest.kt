package com.pocketagent.data.coroutines

import com.pocketagent.di.qualifiers.ApplicationScope
import com.pocketagent.di.qualifiers.DefaultDispatcher
import com.pocketagent.di.qualifiers.IoDispatcher
import com.pocketagent.di.qualifiers.MainDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test class for coroutine configuration components.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class CoroutineConfigurationTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testDispatchers: CoroutineTestUtils.TestDispatchers
    private lateinit var coroutineDispatchers: CoroutineDispatchers
    private lateinit var testScopes: TestCoroutineScopes

    @Before
    fun setup() {
        testDispatchers = CoroutineTestUtils.createTestDispatchers()
        coroutineDispatchers = CoroutineDispatchers(
            main = testDispatchers.main,
            io = testDispatchers.io,
            default = testDispatchers.default,
            unconfined = testDispatchers.unconfined
        )
        testScopes = TestCoroutineScopes(testDispatchers)
    }

    @Test
    fun `test coroutine dispatchers creation`() {
        assertNotNull(coroutineDispatchers.main)
        assertNotNull(coroutineDispatchers.io)
        assertNotNull(coroutineDispatchers.default)
        assertNotNull(coroutineDispatchers.unconfined)
    }

    @Test
    fun `test dispatcher selection by operation type`() {
        val uiDispatcher = coroutineDispatchers.getDispatcher(CoroutineDispatchers.OperationType.UI)
        val ioDispatcher = coroutineDispatchers.getDispatcher(CoroutineDispatchers.OperationType.IO)
        val cpuDispatcher = coroutineDispatchers.getDispatcher(CoroutineDispatchers.OperationType.CPU)
        
        assertEquals(testDispatchers.main, uiDispatcher)
        assertEquals(testDispatchers.io, ioDispatcher)
        assertEquals(testDispatchers.default, cpuDispatcher)
    }

    @Test
    fun `test flow configuration state flow`() = runTest {
        val initialValue = "initial"
        val stateFlow = FlowConfiguration.createStateFlow(initialValue)
        
        assertEquals(initialValue, stateFlow.value)
        
        stateFlow.value = "updated"
        assertEquals("updated", stateFlow.value)
    }

    @Test
    fun `test flow configuration shared flow`() = runTest {
        val sharedFlow = FlowConfiguration.createSharedFlow<String>()
        
        val values = mutableListOf<String>()
        val job = backgroundScope.launch {
            sharedFlow.collect { values.add(it) }
        }
        
        sharedFlow.emit("test1")
        sharedFlow.emit("test2")
        
        advanceUntilIdle()
        job.cancel()
        
        assertEquals(listOf("test1", "test2"), values)
    }

    @Test
    fun `test flow performance optimizations`() = runTest {
        val originalFlow = flowOf(1, 1, 2, 2, 3, 3)
        
        val optimizedFlow = originalFlow.withPerformanceOptimizations(testDispatchers.default)
        val results = optimizedFlow.toList()
        
        // distinctUntilChanged should remove duplicates
        assertEquals(listOf(1, 2, 3), results)
    }

    @Test
    fun `test state flow manager`() = runTest {
        val manager = StateFlowManager("initial")
        
        assertEquals("initial", manager.getCurrentState())
        
        manager.updateState("updated")
        assertEquals("updated", manager.getCurrentState())
        
        manager.updateState { "$it-modified" }
        assertEquals("updated-modified", manager.getCurrentState())
    }

    @Test
    fun `test shared flow manager`() = runTest {
        val manager = SharedFlowManager<String>()
        
        val values = mutableListOf<String>()
        val job = backgroundScope.launch {
            manager.events.collect { values.add(it) }
        }
        
        manager.emitEvent("event1")
        manager.emitEvent("event2")
        
        advanceUntilIdle()
        job.cancel()
        
        assertEquals(listOf("event1", "event2"), values)
    }

    @Test
    fun `test error handling utils safe call`() = runTest {
        val result = ErrorHandlingUtils.safeCall(
            onError = { "error" }
        ) {
            "success"
        }
        
        assertEquals("success", result)
        
        val errorResult = ErrorHandlingUtils.safeCall(
            onError = { "error" }
        ) {
            throw RuntimeException("test error")
        }
        
        assertEquals("error", errorResult)
    }

    @Test
    fun `test error handling utils retry`() = runTest {
        var attempts = 0
        
        val result = ErrorHandlingUtils.safeCallWithRetry(
            retries = 3,
            onError = { "failed" }
        ) {
            attempts++
            if (attempts < 3) {
                throw RuntimeException("retry error")
            }
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `test error categorization`() {
        val networkError = java.net.ConnectException("connection failed")
        val errorInfo = ErrorCategorizer.categorizeError(networkError)
        
        assertEquals(ErrorType.NETWORK, errorInfo.type)
        assertTrue(errorInfo.recoverable)
        assertEquals("connection failed", errorInfo.message)
    }

    @Test
    fun `test cancellation utils`() = runTest {
        val cancellationUtils = CancellationUtils()
        
        var cancelled = false
        val result = cancellationUtils.withCancellationHandling(
            onCancellation = { cancelled = true }
        ) {
            "completed"
        }
        
        assertEquals("completed", result)
        assertEquals(false, cancelled)
    }

    @Test
    fun `test performance monitor`() = runTest {
        val monitor = CoroutinePerformanceMonitor()
        
        val result = monitor.measureExecutionTime("test-operation") {
            kotlinx.coroutines.delay(10)
            "result"
        }
        
        assertEquals("result", result)
        
        val stats = monitor.getPerformanceStats()
        assertEquals(1, stats.totalExecutions)
        assertTrue(stats.totalExecutionTime > 0)
    }

    @Test
    fun `test flow test utilities`() = runTest {
        val testFlow = flow {
            emit("first")
            emit("second")
            emit("third")
        }
        
        FlowTestUtils.testFlowEmissions(testFlow) { collector ->
            collector.assertValueCount(3)
            collector.assertValueAt(0, "first")
            collector.assertValueAt(1, "second")
            collector.assertValueAt(2, "third")
        }
    }

    @Test
    fun `test coroutine optimizations`() = runTest {
        val optimizations = CoroutineOptimizations()
        
        val testFlow = flowOf(1, 1, 2, 2, 3, 3)
        val optimizedFlow = optimizations.run { testFlow.optimizeForHighFrequency() }
        
        val results = optimizedFlow.toList()
        assertEquals(listOf(1, 2, 3), results)
    }

    @Test
    fun `test cancellation manager`() = runTest {
        val manager = CancellationManager()
        
        val job1 = backgroundScope.launch {
            kotlinx.coroutines.delay(1000)
        }
        
        val job2 = backgroundScope.launch {
            kotlinx.coroutines.delay(1000)
        }
        
        manager.registerJob("job1", job1)
        manager.registerJob("job2", job2)
        
        assertEquals(2, manager.getActiveJobCount())
        
        manager.cancelJob("job1")
        assertEquals(1, manager.getActiveJobCount())
        
        manager.cancelAllJobs()
        assertEquals(0, manager.getActiveJobCount())
    }

    @Test
    fun `test hierarchical cancellation`() = runTest {
        val manager = HierarchicalCancellationManager()
        
        val parentJob = backgroundScope.launch {
            kotlinx.coroutines.delay(1000)
        }
        
        val childJob = backgroundScope.launch {
            kotlinx.coroutines.delay(1000)
        }
        
        val parentId = manager.registerJob(parentJob)
        val childId = manager.registerJob(childJob, parentId)
        
        assertTrue(manager.isNodeActive(parentId))
        assertTrue(manager.isNodeActive(childId))
        
        manager.cancelHierarchy(parentId)
        
        assertEquals(false, manager.isNodeActive(parentId))
        assertEquals(false, manager.isNodeActive(childId))
    }
}