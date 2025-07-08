# Communication Layer Feature Specification - WebSocket Components
**For Android Mobile Application**

> **Navigation**: [Overview](./communication-layer-overview.feat.md) | **WebSocket** | [Authentication](./communication-layer-authentication.feat.md) | [Messages](./communication-layer-messages.feat.md) | [Testing](./communication-layer-testing.feat.md) | [Index](./communication-layer-index.md)

## SSH Auth WebSocket Client

**Purpose**: Handles WebSocket connections with SSH key authentication for real-time bidirectional communication. Manages authentication flow, connection lifecycle, message sending/receiving, and automatic reconnection with authentication.

```kotlin
import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Singleton
class SshAuthWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sshKeyAuthenticator: SshKeyAuthenticator,
    private val messageProtocol: MessageProtocol,
    private val connectionStateManager: ConnectionStateManager,
    private val messageQueueManager: MessageQueueManager,
    private val certificateValidator: CertificateValidator,
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val PING_INTERVAL_MS = 30000L // 30 seconds
        private const val PONG_TIMEOUT_MS = 10000L // 10 seconds
        private const val AUTH_TIMEOUT_MS = 60000L // 60 seconds for auth
    }
    
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var projectId: String? = null
    private var sessionId: String? = null
    private var isAuthenticated = false
    
    private val _incomingMessages = MutableSharedFlow<MessageProtocol.Message>()
    val incomingMessages: SharedFlow<MessageProtocol.Message> = _incomingMessages.asSharedFlow()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotAuthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private val authChannel = Channel<String>(1)
    
    sealed class AuthState {
        object NotAuthenticated : AuthState()
        object Authenticating : AuthState()
        data class Authenticated(val sessionId: String) : AuthState()
        data class AuthenticationFailed(val error: String) : AuthState()
    }
    
    /**
     * Connect to WebSocket server with SSH key authentication
     */
    suspend fun connect(
        projectId: String,
        serverUrl: String,
        sshIdentity: SshIdentityEntity
    ) = withContext(Dispatchers.IO) {
        this@SshAuthWebSocketClient.projectId = projectId
        _authState.value = AuthState.NotAuthenticated
        
        connectionStateManager.updateState(projectId, ConnectionState.CONNECTING)
        
        try {
            // Build WebSocket request
            val request = Request.Builder()
                .url(serverUrl)
                .build()
            
            // Create custom OkHttpClient with certificate validation
            val client = if (certificateValidator.shouldPinCertificate(serverUrl)) {
                okHttpClient.newBuilder()
                    .certificatePinner(certificateValidator.getCertificatePinner(serverUrl))
                    .build()
            } else {
                okHttpClient
            }
            
            webSocket = client.newWebSocket(request, createAuthWebSocketListener(projectId, sshIdentity))
            
            // Wait for authentication to complete
            val authResult = withTimeout(AUTH_TIMEOUT_MS) {
                authState.first { it !is AuthState.Authenticating }
            }
            
            when (authResult) {
                is AuthState.Authenticated -> {
                    sessionId = authResult.sessionId
                    isAuthenticated = true
                    connectionStateManager.updateState(projectId, ConnectionState.CONNECTED)
                    startPingMonitoring()
                    launchMessageSender()
                    
                    // Send queued messages
                    scope.launch {
                        messageQueueManager.drainQueue(projectId).collect { message ->
                            sendMessage(message)
                        }
                    }
                    
                    securityAuditLogger.logWebSocketConnection(
                        serverUrl = serverUrl,
                        authenticated = true
                    )
                }
                is AuthState.AuthenticationFailed -> {
                    disconnect()
                    throw SecurityException("Authentication failed: ${authResult.error}")
                }
                else -> {
                    disconnect()
                    throw IllegalStateException("Unexpected auth state: $authResult")
                }
            }
        } catch (e: Exception) {
            connectionStateManager.updateState(
                projectId, 
                ConnectionState.ERROR("WebSocket connection failed: ${e.message}")
            )
            securityAuditLogger.logWebSocketConnection(
                serverUrl = serverUrl,
                authenticated = false,
                error = e.message
            )
            throw e
        }
    }
    
    /**
     * Resume existing session with authentication
     */
    suspend fun resumeSession(
        projectId: String,
        serverUrl: String,
        sessionId: String,
        sshIdentity: SshIdentityEntity
    ) = withContext(Dispatchers.IO) {
        this@SshAuthWebSocketClient.projectId = projectId
        this@SshAuthWebSocketClient.sessionId = sessionId
        // Similar to connect but with session resumption logic
    }
    
    /**
     * Send message through authenticated WebSocket
     */
    suspend fun sendMessage(message: MessageProtocol.Message) {
        if (!isAuthenticated) {
            throw SecurityException("Cannot send message: not authenticated")
        }
        
        when (val state = connectionStateManager.getState(projectId ?: "")) {
            is ConnectionState.CONNECTED -> {
                val json = messageProtocol.encodeMessage(message)
                messageChannel.send(json)
            }
            is ConnectionState.CONNECTING -> {
                // Queue message
                messageQueueManager.enqueueMessage(projectId ?: "", message)
            }
            else -> {
                throw IllegalStateException("Cannot send message in state: $state")
            }
        }
    }
    
    /**
     * Disconnect WebSocket
     */
    fun disconnect() {
        pingJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Client disconnect")
        webSocket = null
        isAuthenticated = false
        sessionId = null
        _authState.value = AuthState.NotAuthenticated
        projectId?.let {
            connectionStateManager.updateState(it, ConnectionState.DISCONNECTED)
        }
    }
    
    private fun createAuthWebSocketListener(
        projectId: String,
        sshIdentity: SshIdentityEntity
    ) = object : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Connection opened, wait for auth challenge
            _authState.value = AuthState.Authenticating
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                try {
                    val json = Json.parseToJsonElement(text)
                    val messageType = json.jsonObject["type"]?.jsonPrimitive?.content
                    
                    when (messageType) {
                        "auth_challenge" -> handleAuthChallenge(json, sshIdentity)
                        "auth_success" -> handleAuthSuccess(json)
                        "auth_error" -> handleAuthError(json)
                        else -> {
                            if (isAuthenticated) {
                                val message = messageProtocol.decodeMessage(text)
                                _incomingMessages.emit(message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Log malformed message
                }
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            connectionStateManager.updateState(
                projectId, 
                ConnectionState.DISCONNECTING("Server closing: $reason")
            )
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connectionStateManager.updateState(projectId, ConnectionState.DISCONNECTED)
            pingJob?.cancel()
            isAuthenticated = false
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connectionStateManager.updateState(
                projectId,
                ConnectionState.ERROR("WebSocket failure: ${t.message}")
            )
            pingJob?.cancel()
            _authState.value = AuthState.AuthenticationFailed(t.message ?: "Unknown error")
        }
    }
    
    private suspend fun handleAuthChallenge(
        json: JsonElement,
        sshIdentity: SshIdentityEntity
    ) {
        val challenge = Json.decodeFromJsonElement<SshKeyAuthenticator.AuthChallenge>(json)
        
        val authResult = sshKeyAuthenticator.signChallenge(challenge, sshIdentity)
        
        authResult.fold(
            onSuccess = { authResponse ->
                val responseJson = Json.encodeToString(authResponse)
                authChannel.send(responseJson)
                webSocket?.send(responseJson)
            },
            onFailure = { error ->
                _authState.value = AuthState.AuthenticationFailed(error.message ?: "Signing failed")
                disconnect()
            }
        )
    }
    
    private fun handleAuthSuccess(json: JsonElement) {
        val authSuccess = Json.decodeFromJsonElement<SshKeyAuthenticator.AuthSuccess>(json)
        sessionId = authSuccess.sessionId
        _authState.value = AuthState.Authenticated(authSuccess.sessionId)
    }
    
    private fun handleAuthError(json: JsonElement) {
        val authError = Json.decodeFromJsonElement<SshKeyAuthenticator.AuthError>(json)
        _authState.value = AuthState.AuthenticationFailed("${authError.code}: ${authError.message}")
    }
    
    private fun startPingMonitoring() {
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                webSocket?.send(ByteString.EMPTY) // Send ping frame
                
                // Wait for pong with timeout
                withTimeoutOrNull(PONG_TIMEOUT_MS) {
                    // In real implementation, track pong responses
                    delay(1000) // Placeholder
                } ?: run {
                    // Pong timeout - connection lost
                    projectId?.let {
                        connectionStateManager.updateState(
                            it,
                            ConnectionState.ERROR("Ping timeout")
                        )
                    }
                    disconnect()
                }
            }
        }
    }
    
    private fun launchMessageSender() {
        scope.launch {
            for (message in messageChannel) {
                webSocket?.send(message)
            }
        }
    }
}
```

## WebSocket Manager

**Purpose**: High-level manager that coordinates multiple authenticated WebSocket connections for different projects. Provides a unified interface for background services to interact with WebSocket connections, handle authentication, and manage permission responses across all active connections.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.WebSocket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val sshAuthWebSocketClient: SshAuthWebSocketClient,
    private val messageProtocol: MessageProtocol,
    private val connectionStateManager: ConnectionStateManager,
    private val scope: CoroutineScope
) {
    
    private val activeConnections = ConcurrentHashMap<String, WebSocketConnection>()
    
    data class WebSocketConnection(
        val projectId: String,
        val serverUrl: String,
        val sessionId: String?,
        val client: SshAuthWebSocketClient
    )
    
    /**
     * Get WebSocket connection for a specific project
     */
    fun getConnection(projectId: String): WebSocketConnection? {
        return activeConnections[projectId]
    }
    
    /**
     * Create and manage a new authenticated WebSocket connection
     */
    suspend fun createConnection(
        projectId: String,
        serverProfile: ServerProfileEntity,
        sshIdentity: SshIdentityEntity
    ): Result<WebSocketConnection> {
        return try {
            // Build WebSocket URL from server profile
            val serverUrl = "wss://${serverProfile.hostname}:${serverProfile.websocketPort}/ws"
            
            // Create new client instance for this connection
            val client = SshAuthWebSocketClient(
                okHttpClient = sshAuthWebSocketClient.okHttpClient,
                sshKeyAuthenticator = sshAuthWebSocketClient.sshKeyAuthenticator,
                messageProtocol = messageProtocol,
                connectionStateManager = connectionStateManager,
                messageQueueManager = sshAuthWebSocketClient.messageQueueManager,
                certificateValidator = sshAuthWebSocketClient.certificateValidator,
                securityAuditLogger = sshAuthWebSocketClient.securityAuditLogger
            )
            
            // Connect with authentication
            client.connect(projectId, serverUrl, sshIdentity)
            
            // Wait for authentication
            val authState = client.authState.first { it is SshAuthWebSocketClient.AuthState.Authenticated }
            val sessionId = (authState as SshAuthWebSocketClient.AuthState.Authenticated).sessionId
            
            val connection = WebSocketConnection(projectId, serverUrl, sessionId, client)
            activeConnections[projectId] = connection
            Result.success(connection)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send health check to specific project
     */
    suspend fun sendHealthCheck(projectId: String): HealthCheckResponse? {
        val connection = activeConnections[projectId] ?: return null
        
        return try {
            val healthMessage = messageProtocol.HealthCheckMessage()
            connection.client.sendMessage(healthMessage)
            
            // Wait for health response with timeout
            withTimeoutOrNull(10_000L) {
                connection.client.incomingMessages
                    .filterIsInstance<messageProtocol.HealthCheckResponse>()
                    .first { it.requestId == healthMessage.id }
            }?.let { response ->
                HealthCheckResponse(
                    healthy = response.healthy,
                    wrapperVersion = response.wrapperVersion,
                    claudeStatus = response.claudeStatus,
                    timestamp = response.timestamp
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Send permission response
     */
    suspend fun sendPermissionResponse(
        requestId: String,
        approved: Boolean,
        projectId: String? = null
    ) {
        val connection = if (projectId != null) {
            activeConnections[projectId]
        } else {
            // Find connection that has this permission request
            activeConnections.values.firstOrNull { conn ->
                // In a real implementation, track pending permissions per connection
                true
            }
        } ?: return
        
        val response = messageProtocol.PermissionResponse(
            requestId = requestId,
            approved = approved
        )
        
        connection.client.sendMessage(response)
    }
    
    /**
     * Pause Claude session
     */
    suspend fun pauseSession(projectName: String) {
        // Find connection by project name
        val connection = activeConnections.values.firstOrNull { conn ->
            // In real implementation, map project names to IDs
            true
        } ?: return
        
        val pauseMessage = messageProtocol.SessionControlMessage(
            action = messageProtocol.SessionAction.PAUSE
        )
        
        connection.client.sendMessage(pauseMessage)
    }
    
    /**
     * Send ping to check connection liveness
     */
    suspend fun sendPing(projectId: String): Boolean {
        val connection = activeConnections[projectId] ?: return false
        
        return try {
            connection.client.getWebSocket()?.send(okio.ByteString.EMPTY) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if connected
     */
    fun isConnected(projectId: String): Boolean {
        val state = connectionStateManager.getState(projectId)
        return state is ConnectionState.CONNECTED
    }
    
    /**
     * Disconnect specific project
     */
    fun disconnect(projectId: String) {
        activeConnections.remove(projectId)?.let { connection ->
            connection.client.disconnect()
        }
    }
    
    /**
     * Disconnect all projects
     */
    fun disconnectAll() {
        activeConnections.forEach { (_, connection) ->
            connection.client.disconnect()
        }
        activeConnections.clear()
    }
    
    /**
     * Get all active project IDs
     */
    fun getActiveProjects(): Set<String> {
        return activeConnections.keys.toSet()
    }
    
    /**
     * Monitor connection states
     */
    val connectionStates: Flow<Map<String, ConnectionState>> = 
        connectionStateManager.connectionStates
}

// Response data classes
data class HealthCheckResponse(
    val healthy: Boolean,
    val wrapperVersion: String,
    val claudeStatus: String,
    val timestamp: Long
)
```

## Connection State Manager

**Purpose**: Manages and tracks the connection state for all WebSocket connections. Provides centralized state management with reactive state updates, connection lifecycle tracking, and state persistence across app restarts.

```kotlin
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionStateManager @Inject constructor() {
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    
    fun updateState(projectId: String, state: ConnectionState) {
        _connectionStates.update { current ->
            current + (projectId to state)
        }
    }
    
    fun getState(projectId: String): ConnectionState {
        return _connectionStates.value[projectId] ?: ConnectionState.DISCONNECTED
    }
    
    fun removeState(projectId: String) {
        _connectionStates.update { current ->
            current - projectId
        }
    }
    
    fun clearAll() {
        _connectionStates.value = emptyMap()
    }
    
    /**
     * Get flow for specific project's connection state
     */
    fun getProjectConnectionState(projectId: String): Flow<ConnectionState> {
        return connectionStates
            .map { it[projectId] ?: ConnectionState.DISCONNECTED }
            .distinctUntilChanged()
    }
    
    /**
     * Get all connected projects
     */
    fun getConnectedProjects(): List<String> {
        return _connectionStates.value
            .filter { it.value is ConnectionState.CONNECTED }
            .keys
            .toList()
    }
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object CONNECTING : ConnectionState()
    object CONNECTED : ConnectionState()
    data class DISCONNECTING(val reason: String) : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
    data class RECONNECTING(val attempt: Int) : ConnectionState()
}
```

## Message Queue Manager

**Purpose**: Manages message queuing for offline/disconnected scenarios. Provides persistent message storage, automatic retry on reconnection, message expiration handling, and priority-based queue management.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageQueueManager @Inject constructor(
    private val messageStorage: MessageStorage,
    private val scope: CoroutineScope
) {
    
    private val messageQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedMessage>>()
    
    data class QueuedMessage(
        val message: MessageProtocol.Message,
        val timestamp: Long = System.currentTimeMillis(),
        val priority: MessagePriority = MessagePriority.NORMAL,
        val retryCount: Int = 0
    )
    
    enum class MessagePriority {
        HIGH,    // Permission responses, critical operations
        NORMAL,  // Regular messages
        LOW      // Status updates, non-critical
    }
    
    /**
     * Enqueue message for later sending
     */
    suspend fun enqueueMessage(
        projectId: String,
        message: MessageProtocol.Message,
        priority: MessagePriority = MessagePriority.NORMAL
    ) {
        val queue = messageQueues.getOrPut(projectId) { ConcurrentLinkedQueue() }
        val queuedMessage = QueuedMessage(message, priority = priority)
        
        queue.offer(queuedMessage)
        
        // Persist high priority messages
        if (priority == MessagePriority.HIGH) {
            messageStorage.persistMessage(projectId, queuedMessage)
        }
    }
    
    /**
     * Drain queue for sending
     */
    fun drainQueue(projectId: String): Flow<MessageProtocol.Message> = flow {
        val queue = messageQueues[projectId] ?: return@flow
        
        // Sort by priority and timestamp
        val messages = queue.toList().sortedWith(
            compareByDescending<QueuedMessage> { it.priority.ordinal }
                .thenBy { it.timestamp }
        )
        
        queue.clear()
        
        messages.forEach { queuedMessage ->
            emit(queuedMessage.message)
        }
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(projectId: String): Int {
        return messageQueues[projectId]?.size ?: 0
    }
    
    /**
     * Clear expired messages
     */
    suspend fun clearExpiredMessages() {
        val expirationTime = System.currentTimeMillis() - MESSAGE_EXPIRATION_MS
        
        messageQueues.forEach { (projectId, queue) ->
            queue.removeIf { it.timestamp < expirationTime }
        }
    }
    
    companion object {
        private const val MESSAGE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}
```

## Reconnection Manager

**Purpose**: Implements exponential backoff reconnection strategy for WebSocket connections. Handles network state changes, automatic reconnection attempts, session resumption, and reconnection policy configuration.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

@Singleton
class ReconnectionManager @Inject constructor(
    private val context: Context,
    private val connectionStateManager: ConnectionStateManager,
    private val scope: CoroutineScope
) {
    
    private val reconnectionJobs = mutableMapOf<String, Job>()
    private val reconnectionAttempts = mutableMapOf<String, Int>()
    
    companion object {
        private const val INITIAL_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 60000L // 1 minute
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val JITTER_FACTOR = 0.1
        private const val MAX_ATTEMPTS = 10
    }
    
    /**
     * Start reconnection attempts for a project
     */
    fun startReconnection(
        projectId: String,
        reconnectAction: suspend () -> Result<Unit>
    ) {
        // Cancel any existing reconnection job
        reconnectionJobs[projectId]?.cancel()
        
        reconnectionJobs[projectId] = scope.launch {
            var attempt = 0
            reconnectionAttempts[projectId] = attempt
            
            while (isActive && attempt < MAX_ATTEMPTS) {
                connectionStateManager.updateState(
                    projectId,
                    ConnectionState.RECONNECTING(attempt + 1)
                )
                
                val result = reconnectAction()
                
                if (result.isSuccess) {
                    reconnectionAttempts.remove(projectId)
                    reconnectionJobs.remove(projectId)
                    return@launch
                }
                
                attempt++
                reconnectionAttempts[projectId] = attempt
                
                val delay = calculateBackoffDelay(attempt)
                delay(delay)
            }
            
            // Max attempts reached
            connectionStateManager.updateState(
                projectId,
                ConnectionState.ERROR("Max reconnection attempts reached")
            )
            reconnectionAttempts.remove(projectId)
            reconnectionJobs.remove(projectId)
        }
    }
    
    /**
     * Stop reconnection attempts
     */
    fun stopReconnection(projectId: String) {
        reconnectionJobs[projectId]?.cancel()
        reconnectionJobs.remove(projectId)
        reconnectionAttempts.remove(projectId)
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, (attempt - 1).toDouble()).toLong()
        val boundedDelay = min(exponentialDelay, MAX_DELAY_MS)
        
        // Add jitter to prevent thundering herd
        val jitter = (boundedDelay * JITTER_FACTOR * Random.nextDouble()).toLong()
        
        return boundedDelay + jitter
    }
    
    /**
     * Monitor network connectivity
     */
    fun observeNetworkConnectivity(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        
        connectivityManager.registerDefaultNetworkCallback(callback)
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Get reconnection status
     */
    fun isReconnecting(projectId: String): Boolean {
        return reconnectionJobs[projectId]?.isActive == true
    }
    
    /**
     * Get reconnection attempt count
     */
    fun getReconnectionAttempts(projectId: String): Int {
        return reconnectionAttempts[projectId] ?: 0
    }
}
```