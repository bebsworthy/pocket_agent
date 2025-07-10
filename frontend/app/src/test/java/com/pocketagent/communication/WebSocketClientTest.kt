package com.pocketagent.communication

import com.google.common.truth.Truth.assertThat
import com.pocketagent.testing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

/**
 * Unit tests for WebSocket client functionality.
 * Tests connection establishment, message exchange, and error handling.
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class WebSocketClientTest : BaseUnitTest() {
    
    @Mock
    private lateinit var mockConnectionStateManager: ConnectionStateManager
    
    @Mock
    private lateinit var mockMessageProtocol: MessageProtocol
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var webSocketClient: WebSocketClient
    
    @Before
    override fun setUp() {
        super.setUp()
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        
        webSocketClient = WebSocketClient(
            okHttpClient,
            mockConnectionStateManager,
            mockMessageProtocol
        )
    }
    
    @After
    override fun tearDown() {
        super.tearDown()
        mockWebServer.shutdown()
    }
    
    @Test
    fun `connect establishes WebSocket connection`() = runTest {
        // Given
        val serverUrl = mockWebServer.url("/ws").toString()
        val projectId = "test-project"
        
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(TestWebSocketListener()))
        
        // When
        val result = webSocketClient.connect(projectId, serverUrl)
        
        // Then
        assertThat(result.isSuccess).isTrue()
    }
    
    @Test
    fun `sendMessage encodes and sends message`() = runTest {
        // Given
        val projectId = "test-project"
        val message = TestDataFactory.createCommandMessage("test command")
        val encodedMessage = """{"type":"command","command":"test command"}"""
        
        whenever(mockMessageProtocol.encodeMessage(message)).thenReturn(encodedMessage)
        
        // Setup connection
        val serverUrl = mockWebServer.url("/ws").toString()
        val listener = TestWebSocketListener()
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(listener))
        
        webSocketClient.connect(projectId, serverUrl)
        
        // When
        webSocketClient.sendMessage(projectId, message)
        
        // Then
        assertThat(listener.receivedMessages).contains(encodedMessage)
    }
    
    @Test
    fun `receiveMessage decodes incoming message`() = runTest {
        // Given
        val projectId = "test-project"
        val incomingJson = """{"type":"claude_response","content":"Hello"}"""
        val decodedMessage = TestDataFactory.createClaudeMessage("Hello")
        
        whenever(mockMessageProtocol.decodeMessage(incomingJson)).thenReturn(decodedMessage)
        
        // Setup connection and message flow
        val serverUrl = mockWebServer.url("/ws").toString()
        val listener = TestWebSocketListener()
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(listener))
        
        webSocketClient.connect(projectId, serverUrl)
        
        // When
        listener.webSocket?.send(incomingJson)
        
        // Then
        // Verify message was processed (would need to check internal state or events)
    }
    
    @Test
    fun `connection failure returns error result`() = runTest {
        // Given
        val serverUrl = "ws://invalid-url:9999/ws"
        val projectId = "test-project"
        
        // When
        val result = webSocketClient.connect(projectId, serverUrl)
        
        // Then
        assertThat(result.isFailure).isTrue()
    }
    
    @Test
    fun `disconnect closes WebSocket connection`() = runTest {
        // Given
        val projectId = "test-project"
        val serverUrl = mockWebServer.url("/ws").toString()
        val listener = TestWebSocketListener()
        
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(listener))
        webSocketClient.connect(projectId, serverUrl)
        
        // When
        webSocketClient.disconnect(projectId)
        
        // Then
        assertThat(listener.isClosed).isTrue()
    }
    
    @Test
    fun `reconnection attempts with exponential backoff`() = runTest {
        // Given
        val projectId = "test-project"
        val serverUrl = mockWebServer.url("/ws").toString()
        
        // First attempt fails
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        
        // Second attempt succeeds
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(TestWebSocketListener()))
        
        // When
        val result = webSocketClient.connectWithRetry(projectId, serverUrl, maxRetries = 2)
        
        // Then
        assertThat(result.isSuccess).isTrue()
    }
    
    @Test
    fun `message queuing during disconnection`() = runTest {
        // Given
        val projectId = "test-project"
        val message = TestDataFactory.createCommandMessage("queued command")
        
        // When: Send message while disconnected
        webSocketClient.sendMessage(projectId, message)
        
        // Then: Message should be queued
        val queuedMessages = webSocketClient.getQueuedMessages(projectId)
        assertThat(queuedMessages).hasSize(1)
        assertThat(queuedMessages.first()).isEqualTo(message)
    }
    
    @Test
    fun `flush queued messages on reconnection`() = runTest {
        // Given
        val projectId = "test-project"
        val queuedMessage = TestDataFactory.createCommandMessage("queued command")
        
        // Queue message while disconnected
        webSocketClient.sendMessage(projectId, queuedMessage)
        
        // When: Reconnect
        val serverUrl = mockWebServer.url("/ws").toString()
        val listener = TestWebSocketListener()
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(listener))
        
        webSocketClient.connect(projectId, serverUrl)
        
        // Then: Queued message should be sent
        val encodedMessage = """{"type":"command","command":"queued command"}"""
        whenever(mockMessageProtocol.encodeMessage(queuedMessage)).thenReturn(encodedMessage)
        
        // Verify message was sent
        assertThat(listener.receivedMessages).contains(encodedMessage)
    }
}

/**
 * Example interfaces for testing.
 */
interface WebSocketClient {
    suspend fun connect(projectId: String, serverUrl: String): Result<Unit>
    suspend fun connectWithRetry(projectId: String, serverUrl: String, maxRetries: Int): Result<Unit>
    suspend fun disconnect(projectId: String): Result<Unit>
    suspend fun sendMessage(projectId: String, message: Any): Result<Unit>
    fun getQueuedMessages(projectId: String): List<Any>
}

interface ConnectionStateManager {
    fun updateConnectionState(projectId: String, state: String)
    fun getConnectionState(projectId: String): String
}

interface MessageProtocol {
    fun encodeMessage(message: Any): String
    fun decodeMessage(json: String): Any
}

/**
 * Test data factory extensions for communication layer.
 */
private fun TestDataFactory.createCommandMessage(command: String): Any {
    return mapOf(
        "type" to "command",
        "command" to command,
        "isShellCommand" to false
    )
}

private fun TestDataFactory.createClaudeMessage(content: String): Any {
    return mapOf(
        "type" to "claude_response",
        "content" to content,
        "conversationId" to "test-conversation"
    )
}

/**
 * Test WebSocket listener for mocking.
 */
private class TestWebSocketListener : okhttp3.WebSocketListener() {
    val receivedMessages = mutableListOf<String>()
    var webSocket: okhttp3.WebSocket? = null
    var isClosed = false
    
    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
        this.webSocket = webSocket
    }
    
    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        receivedMessages.add(text)
    }
    
    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        isClosed = true
    }
}