package com.pocketagent.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

/**
 * Example unit test demonstrating the testing framework usage.
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ExampleUnitTest : BaseUnitTest() {
    
    @Mock
    private lateinit var mockRepository: ExampleRepository
    
    @Test
    fun `test basic functionality`() {
        // Given
        val testData = "test data"
        
        // When
        val result = processData(testData)
        
        // Then
        assertThat(result).isEqualTo("processed: $testData")
    }
    
    @Test
    fun `test coroutine function`() = runTest {
        // Given
        val expectedResult = "async result"
        whenever(mockRepository.getData()).thenReturn(flowOf(expectedResult))
        
        // When
        val result = mockRepository.getData()
        
        // Then
        result.collect { data ->
            assertThat(data).isEqualTo(expectedResult)
        }
    }
    
    @Test
    fun `test with test data builder`() {
        // Given
        val testProject = TestDataFactory.createProject(
            name = "Test Project",
            status = "CONNECTED"
        )
        
        // When
        val isConnected = testProject.status == "CONNECTED"
        
        // Then
        assertThat(isConnected).isTrue()
        assertThat(testProject.name).isEqualTo("Test Project")
    }
    
    @Test
    fun `test with flow testing utilities`() = runTest {
        // Given
        val testFlow = flowOf("item1", "item2", "item3")
        
        // When & Then
        FlowTestUtils.run {
            testFlow.assertEmitsSequence(testScope, "item1", "item2", "item3")
        }
    }
    
    private fun processData(data: String): String {
        return "processed: $data"
    }
}

/**
 * Example repository interface for testing.
 */
interface ExampleRepository {
    suspend fun getData(): kotlinx.coroutines.flow.Flow<String>
    suspend fun saveData(data: String): Result<Unit>
}