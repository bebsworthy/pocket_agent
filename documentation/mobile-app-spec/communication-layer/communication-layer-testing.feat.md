# Communication Layer Feature Specification - Testing
**For Android Mobile Application**

> **Navigation**: [Overview](./communication-layer-overview.feat.md) | [WebSocket](./communication-layer-websocket.feat.md) | [Authentication](./communication-layer-authentication.feat.md) | [Messages](./communication-layer-messages.feat.md) | **Testing** | [Index](./communication-layer-index.md)

## Testing Overview

This section provides comprehensive testing guidelines and examples for the Communication Layer, ensuring robust WebSocket connections, proper SSH key authentication, and reliable message handling.

## Connection Testing Checklist

**Purpose**: Comprehensive checklist ensuring all critical communication scenarios are tested, from basic connectivity to edge cases like network transitions and battery optimization impacts.

```kotlin
/**
 * Connection Testing Checklist:
 * 1. [ ] Test WebSocket connection with SSH key authentication
 * 2. [ ] Test SSH key authentication failure handling
 * 3. [ ] Test authentication session management
 * 4. [ ] Test WebSocket reconnection with re-authentication
 * 5. [ ] Test message encoding/decoding for all types
 * 6. [ ] Test connection state transitions
 * 7. [ ] Test automatic reconnection with exponential backoff
 * 8. [ ] Test message queuing during disconnection
 * 9. [ ] Test network change handling (WiFi to cellular)
 * 10. [ ] Test connection health monitoring
 * 11. [ ] Test concurrent connections to multiple projects
 * 12. [ ] Test graceful shutdown and cleanup
 * 13. [ ] Test error propagation and recovery
 * 14. [ ] Test battery optimization impact
 * 15. [ ] Test background service lifecycle
 * 16. [ ] Test certificate pinning and validation
 * 17. [ ] Test session resumption after app restart
 * 18. [ ] Test permission policy evaluation
 * 19. [ ] Test progress tracking and updates
 * 20. [ ] Test sub-agent monitoring
 */
```

## Unit Tests

**Purpose**: Unit test examples demonstrating how to test message protocol encoding/decoding and reconnection backoff calculations. Shows proper test setup with mocks and assertions for core logic validation.

### Message Protocol Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class MessageProtocolTest {
    
    private lateinit var messageProtocol: MessageProtocol
    
    @Before
    fun setup() {
        messageProtocol = MessageProtocol()
    }
    
    @Test
    fun `encode and decode command message`() {
        // Given
        val message = MessageProtocol.CommandMessage(
            command = "run tests",
            isShellCommand = true
        )
        
        // When
        val json = messageProtocol.encodeMessage(message)
        val decoded = messageProtocol.decodeMessage(json)
        
        // Then
        assertThat(decoded).isInstanceOf(MessageProtocol.CommandMessage::class.java)
        assertThat((decoded as MessageProtocol.CommandMessage).command).isEqualTo("run tests")
        assertThat(decoded.isShellCommand).isTrue()
    }
    
    @Test
    fun `decode permission request`() {
        // Given
        val json = """
            {
                "type": "permission_request",
                "id": "test-123",
                "timestamp": 1234567890,
                "tool": "bash",
                "action": "execute",
                "details": {"command": "rm -rf /"},
                "risk": "HIGH"
            }
        """.trimIndent()
        
        // When
        val message = messageProtocol.decodeMessage(json)
        
        // Then
        assertThat(message).isInstanceOf(MessageProtocol.PermissionRequest::class.java)
        val request = message as MessageProtocol.PermissionRequest
        assertThat(request.tool).isEqualTo("bash")
        assertThat(request.risk).isEqualTo(MessageProtocol.RiskLevel.HIGH)
    }
    
    @Test
    fun `encode all message types`() {
        // Test encoding for each message type
        val messages = listOf(
            MessageProtocol.HeartbeatMessage(),
            MessageProtocol.PermissionResponse(requestId = "test", approved = true),
            MessageProtocol.SessionResumeMessage(
                sessionId = "session-123",
                lastMessageId = "msg-456",
                lastMessageTimestamp = System.currentTimeMillis()
            )
        )
        
        messages.forEach { message ->
            val json = messageProtocol.encodeMessage(message)
            val decoded = messageProtocol.decodeMessage(json)
            assertThat(decoded.javaClass).isEqualTo(message.javaClass)
        }
    }
}
```

### Reconnection Manager Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class ReconnectionManagerTest {
    
    @Mock
    private lateinit var connectionStateManager: ConnectionStateManager
    
    @Mock
    private lateinit var context: Context
    
    private lateinit var reconnectionManager: ReconnectionManager
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        reconnectionManager = ReconnectionManager(
            context, 
            connectionStateManager,
            testScope
        )
    }
    
    @Test
    fun `calculate exponential backoff correctly`() {
        // Test backoff calculation with expected delays
        val expectedDelays = listOf(
            1000L,   // Initial delay
            2000L,   // 1s * 2
            4000L,   // 2s * 2
            8000L,   // 4s * 2
            16000L,  // 8s * 2
            32000L,  // 16s * 2
            60000L,  // Max delay
            60000L   // Capped at max
        )
        
        for (i in expectedDelays.indices) {
            val delay = reconnectionManager.calculateBackoffDelay(i + 1)
            // Allow for jitter (±10%)
            assertThat(delay).isGreaterThan(expectedDelays[i] * 0.9f)
            assertThat(delay).isLessThan(expectedDelays[i] * 1.1f)
        }
    }
    
    @Test
    fun `stop reconnection cancels job`() = testScope.runTest {
        // Given
        val projectId = "test-project"
        var reconnectAttempts = 0
        
        reconnectionManager.startReconnection(projectId) {
            reconnectAttempts++
            Result.failure(Exception("Test failure"))
        }
        
        // When
        advanceTimeBy(500)
        reconnectionManager.stopReconnection(projectId)
        advanceTimeBy(5000)
        
        // Then
        assertThat(reconnectAttempts).isEqualTo(1)
        assertThat(reconnectionManager.isReconnecting(projectId)).isFalse()
    }
}
```

### SSH Key Authenticator Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SshKeyAuthenticatorTest {
    
    @Mock
    private lateinit var sshKeyImportManager: SshKeyImportManager
    
    @Mock
    private lateinit var securityAuditLogger: SecurityAuditLogger
    
    private lateinit var sshKeyAuthenticator: SshKeyAuthenticator
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sshKeyAuthenticator = SshKeyAuthenticator(
            sshKeyImportManager,
            securityAuditLogger
        )
    }
    
    @Test
    fun `sign challenge with valid SSH key`() = runTest {
        // Given
        val challenge = SshKeyAuthenticator.AuthChallenge(
            nonce = "test-nonce",
            timestamp = System.currentTimeMillis(),
            serverVersion = "1.0.0"
        )
        
        val sshIdentity = SshIdentityEntity(
            id = "test-id",
            name = "Test Key",
            publicKey = "ssh-rsa AAAAB3...",
            encryptedPrivateKey = byteArrayOf(),
            keyAlias = "test-alias",
            createdAt = Instant.now()
        )
        
        // Mock private key decryption
        val privateKeyBytes = loadTestPrivateKey()
        whenever(sshKeyImportManager.decryptSshPrivateKey(any(), any()))
            .thenReturn(privateKeyBytes)
        
        // When
        val result = sshKeyAuthenticator.signChallenge(challenge, sshIdentity)
        
        // Then
        assertThat(result.isSuccess).isTrue()
        result.getOrNull()?.let { response ->
            assertThat(response.publicKey).isEqualTo(sshIdentity.publicKey)
            assertThat(response.signature).isNotEmpty()
            assertThat(response.clientVersion).isNotEmpty()
        }
        
        verify(securityAuditLogger, never()).logAuthenticationAttempt(
            success = false,
            error = any()
        )
    }
    
    @Test
    fun `handle authentication failure`() = runTest {
        // Given
        val challenge = SshKeyAuthenticator.AuthChallenge(
            nonce = "test-nonce",
            timestamp = System.currentTimeMillis(),
            serverVersion = "1.0.0"
        )
        
        val sshIdentity = mock<SshIdentityEntity>()
        
        whenever(sshKeyImportManager.decryptSshPrivateKey(any(), any()))
            .thenThrow(SecurityException("Key decryption failed"))
        
        // When
        val result = sshKeyAuthenticator.signChallenge(challenge, sshIdentity)
        
        // Then
        assertThat(result.isFailure).isTrue()
        verify(securityAuditLogger).logAuthenticationAttempt(
            success = false,
            error = "Key decryption failed"
        )
    }
    
    private fun loadTestPrivateKey(): ByteArray {
        // Load test RSA private key for testing
        return """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7W8bA8L5tJfMR
            ...test key content...
            -----END PRIVATE KEY-----
        """.trimIndent().toByteArray()
    }
}
```

## Integration Tests

**Purpose**: Integration test example showing how to test WebSocket communication using MockWebServer. Demonstrates full connection establishment, message exchange, and async message collection in a controlled test environment.

```kotlin
@RunWith(AndroidJUnit4::class)
class WebSocketIntegrationTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var sshAuthWebSocketClient: SshAuthWebSocketClient
    private lateinit var messageProtocol: MessageProtocol
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        messageProtocol = MessageProtocol()
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
            
        sshAuthWebSocketClient = SshAuthWebSocketClient(
            okHttpClient,
            mock(),
            messageProtocol,
            ConnectionStateManager(),
            MessageQueueManager(),
            mock(),
            mock()
        )
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    @Test
    fun `establish websocket connection and exchange messages`() = runTest {
        // Given
        val projectId = "test-project"
        val serverUrl = "ws://localhost:${mockWebServer.port}/ws"
        
        // Setup mock WebSocket response with auth flow
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send auth challenge
                val challenge = SshKeyAuthenticator.AuthChallenge(
                    nonce = "test-nonce",
                    timestamp = System.currentTimeMillis(),
                    serverVersion = "1.0.0"
                )
                webSocket.send(messageProtocol.encodeMessage(challenge))
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = messageProtocol.decodeMessage(text)
                when (message) {
                    is SshKeyAuthenticator.AuthResponse -> {
                        // Send auth success
                        val authSuccess = SshKeyAuthenticator.AuthSuccess(
                            sessionId = "session-123",
                            expiresAt = System.currentTimeMillis() + 86400000
                        )
                        webSocket.send(messageProtocol.encodeMessage(authSuccess))
                        
                        // Send test message after auth
                        val testMessage = MessageProtocol.ClaudeResponse(
                            id = "test-1",
                            timestamp = System.currentTimeMillis(),
                            content = "Hello from Claude",
                            conversationId = projectId
                        )
                        webSocket.send(messageProtocol.encodeMessage(testMessage))
                    }
                    else -> {
                        // Echo back other messages
                        webSocket.send(text)
                    }
                }
            }
        }))
        
        // When
        val sshIdentity = createTestSshIdentity()
        sshAuthWebSocketClient.connect(projectId, serverUrl, sshIdentity)
        
        // Wait for auth to complete
        val authState = sshAuthWebSocketClient.authState.first { 
            it is SshAuthWebSocketClient.AuthState.Authenticated 
        }
        assertThat(authState).isInstanceOf(SshAuthWebSocketClient.AuthState.Authenticated::class.java)
        
        // Collect incoming messages
        val messages = mutableListOf<MessageProtocol.Message>()
        val job = launch {
            sshAuthWebSocketClient.incomingMessages.collect { message ->
                messages.add(message)
            }
        }
        
        // Send a message
        sshAuthWebSocketClient.sendMessage(
            MessageProtocol.CommandMessage(command = "test command")
        )
        
        // Wait for messages
        delay(1000)
        
        // Then
        assertThat(messages).hasSize(2) // Initial message + echo
        assertThat(messages[0]).isInstanceOf(MessageProtocol.ClaudeResponse::class.java)
        
        job.cancel()
    }
    
    @Test
    fun `handle connection failure and retry`() = runTest {
        // Test connection failure and automatic retry
        val projectId = "test-project"
        val serverUrl = "ws://localhost:${mockWebServer.port}/ws"
        
        // First attempt fails
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        
        // Second attempt succeeds
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            // Implementation
        }))
        
        // Test reconnection behavior
    }
    
    private fun createTestSshIdentity(): SshIdentityEntity {
        return SshIdentityEntity(
            id = "test-id",
            name = "Test SSH Key",
            publicKey = "ssh-rsa AAAAB3...",
            encryptedPrivateKey = byteArrayOf(),
            keyAlias = "test-alias",
            createdAt = Instant.now()
        )
    }
}
```

## End-to-End Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class CommunicationLayerE2ETest {
    
    @Test
    fun `complete message flow from user input to Claude response`() = runTest {
        // Setup full communication stack
        val communicationStack = setupTestCommunicationStack()
        
        // 1. User sends command
        val userCommand = "Explain this code"
        communicationStack.sendUserMessage(userCommand)
        
        // 2. Verify command sent through WebSocket
        val sentMessage = communicationStack.capturedMessages.first()
        assertThat(sentMessage).isInstanceOf(MessageProtocol.CommandMessage::class.java)
        
        // 3. Simulate Claude response
        communicationStack.simulateClaudeResponse(
            "This code implements a WebSocket client..."
        )
        
        // 4. Verify response received and stored
        val storedMessages = communicationStack.getStoredMessages()
        assertThat(storedMessages).hasSize(2)
        assertThat(storedMessages.last().content).contains("WebSocket client")
        
        // 5. Verify UI updated
        val uiMessages = communicationStack.getUIMessages()
        assertThat(uiMessages).hasSize(2)
    }
    
    @Test
    fun `permission request flow`() = runTest {
        // Test complete permission request handling
        val communicationStack = setupTestCommunicationStack()
        
        // 1. Simulate permission request from wrapper
        communicationStack.simulatePermissionRequest(
            tool = "bash",
            action = "rm -rf temp/",
            risk = MessageProtocol.RiskLevel.MEDIUM
        )
        
        // 2. Verify notification shown
        val notifications = communicationStack.getNotifications()
        assertThat(notifications).hasSize(1)
        assertThat(notifications.first().title).contains("Permission Request")
        
        // 3. User approves permission
        communicationStack.approvePermission(notifications.first().id)
        
        // 4. Verify response sent
        val response = communicationStack.capturedMessages
            .filterIsInstance<MessageProtocol.PermissionResponse>()
            .first()
        assertThat(response.approved).isTrue()
    }
}
```

## Performance Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class CommunicationPerformanceTest {
    
    @Test
    fun `measure message throughput`() = runTest {
        val messageCount = 1000
        val messages = List(messageCount) { index ->
            MessageProtocol.CommandMessage(
                command = "Test command $index"
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        messages.forEach { message ->
            webSocketClient.sendMessage(message)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val throughput = messageCount * 1000.0 / duration
        
        assertThat(throughput).isGreaterThan(100.0) // At least 100 messages/second
    }
    
    @Test
    fun `measure reconnection time`() = runTest {
        // Measure time to reconnect after disconnection
        val disconnectTime = System.currentTimeMillis()
        webSocketClient.disconnect()
        
        val reconnectJob = launch {
            webSocketClient.connect(projectId, serverUrl, sshIdentity)
        }
        
        reconnectJob.join()
        val reconnectTime = System.currentTimeMillis()
        val reconnectDuration = reconnectTime - disconnectTime
        
        assertThat(reconnectDuration).isLessThan(5000) // Less than 5 seconds
    }
}
```

## Test Utilities

```kotlin
object CommunicationTestUtils {
    
    fun createMockWebSocketServer(): MockWebServer {
        return MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/ws" -> MockResponse().withWebSocketUpgrade(TestWebSocketListener())
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        }
    }
    
    fun createTestMessage(type: MessageProtocol.MessageType): MessageProtocol.Message {
        return when (type) {
            MessageProtocol.MessageType.COMMAND -> MessageProtocol.CommandMessage(
                command = "test command"
            )
            MessageProtocol.MessageType.CLAUDE_RESPONSE -> MessageProtocol.ClaudeResponse(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                content = "Test response",
                conversationId = "test-conversation"
            )
            // Add other message types
            else -> throw IllegalArgumentException("Unsupported message type: $type")
        }
    }
    
    class TestWebSocketListener : WebSocketListener() {
        val receivedMessages = mutableListOf<String>()
        var webSocket: WebSocket? = null
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this.webSocket = webSocket
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            receivedMessages.add(text)
            // Echo back for testing
            webSocket.send(text)
        }
    }
}
```