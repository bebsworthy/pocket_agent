package com.pocketagent.testing

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.MockitoAnnotations

/**
 * Base class for unit tests that provides common setup and teardown.
 * 
 * Features:
 * - Coroutine testing support with TestScope
 * - Mockito initialization
 * - Architecture components instant execution
 * - Test dispatcher configuration
 */
@ExperimentalCoroutinesApi
abstract class BaseUnitTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    protected val testDispatcher = UnconfinedTestDispatcher()
    protected val testScope = TestScope(testDispatcher)
    
    @Before
    open fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }
    
    @After
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}

/**
 * Base class for ViewModelTest that includes LiveData testing support.
 */
@ExperimentalCoroutinesApi
abstract class BaseViewModelTest : BaseUnitTest() {
    
    // Additional ViewModel-specific setup can be added here
    
    @Before
    override fun setUp() {
        super.setUp()
        // ViewModel-specific setup
    }
    
    @After
    override fun tearDown() {
        super.tearDown()
        // ViewModel-specific cleanup
    }
}