# Communication Layer Feature Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [SSH Key Authenticator](#ssh-key-authenticator)
   - [SSH Auth WebSocket Client](#ssh-auth-websocket-client)
   - [Message Protocol](#message-protocol)
   - [Connection State Manager](#connection-state-manager)
   - [Message Queue Manager](#message-queue-manager)
   - [Reconnection Manager](#reconnection-manager)
   - [Connection Health Monitor](#connection-health-monitor)
   - [Message Handlers](#message-handlers)
   - [Error Handling](#error-handling)
   - [Dependency Injection](#dependency-injection)
4. [Testing](#testing)
   - [Connection Testing Checklist](#connection-testing-checklist)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Connection Lifecycle](#connection-lifecycle)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Network Optimization](#network-optimization)
   - [Battery Optimization](#battery-optimization)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The Communication Layer feature provides the core networking infrastructure for **Pocket Agent - a remote coding agent mobile interface**. This feature implements direct WebSocket communication with SSH key authentication, message protocol handling, and robust connection management to enable real-time interaction with remote Claude Code instances.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: Reactive communication with coroutines and flows
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#communication-protocol)

This feature implements the communication requirements defined in the [Frontend Technical Specification](./frontend.spec.md), specifically the Message Flow Architecture and Connection Recovery sections. The implementation leverages Android's networking capabilities while managing battery and network efficiency.

## Architecture

### Technology Stack (Android-Specific)

- **SSH Key Operations**: Bouncy Castle for SSH key signing and verification
- **WebSocket**: OkHttp3 WebSocket implementation with custom authentication
- **Serialization**: Kotlinx.serialization for JSON message handling
- **Coroutines**: Kotlin Coroutines + Flow for async operations
- **Network Detection**: Android ConnectivityManager for network state
- **Background Execution**: Android Foreground Service for persistent connections
- **Dependency Injection**: Hilt for component management
- **Testing**: MockWebServer for WebSocket testing

### Key Components

- **SshAuthWebSocketClient**: Manages WebSocket connections with SSH key authentication
- **SshKeyAuthenticator**: Handles SSH key challenge-response authentication
- **MessageProtocol**: Defines and handles message types and serialization
- **ConnectionStateManager**: Tracks and manages connection lifecycle
- **MessageQueueManager**: Queues messages during disconnections
- **ReconnectionManager**: Implements exponential backoff reconnection
- **ConnectionHealthMonitor**: Monitors connection health with ping/pong
- **NetworkStateObserver**: Observes Android network connectivity changes

## Components Architecture

### SSH Key Authenticator

**Purpose**: Handles SSH key authentication for WebSocket connections. Manages challenge-response authentication flow, SSH key signing operations, and authentication state management.

```kotlin
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.signers.RSADigestSigner
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyAuthenticator @Inject constructor(
    private val sshKeyImportManager: SshKeyImportManager,
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    @Serializable
    data class AuthChallenge(
        val type: String = "auth_challenge",
        val nonce: String,
        val timestamp: Long,
        val serverVersion: String
    )
    
    @Serializable
    data class AuthResponse(
        val type: String = "auth_response",
        val publicKey: String,
        val signature: String,
        val clientVersion: String,
        val sessionId: String? = null // For session resumption
    )
    
    @Serializable
    data class AuthSuccess(
        val type: String = "auth_success",
        val sessionId: String,
        val expiresAt: Long
    )
    
    @Serializable
    data class AuthError(
        val type: String = "auth_error",
        val code: String,
        val message: String
    )
    
    /**
     * Sign authentication challenge with SSH private key
     */
    suspend fun signChallenge(
        challenge: AuthChallenge,
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            // Get decrypted SSH private key
            val privateKeyBytes = sshKeyImportManager.decryptSshPrivateKey(
                activity = activity,
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            // Parse private key
            val privateKey = parsePrivateKey(privateKeyBytes)
            
            // Create data to sign: nonce + timestamp
            val dataToSign = "${challenge.nonce}${challenge.timestamp}"
            
            // Sign the data
            val signature = signData(privateKey, dataToSign.toByteArray())
            
            // Create auth response
            val authResponse = AuthResponse(
                publicKey = sshIdentity.publicKey,
                signature = Base64.getEncoder().encodeToString(signature),
                clientVersion = BuildConfig.VERSION_NAME,
                sessionId = null // New connection, no session to resume
            )
            
            // Clear sensitive data
            privateKeyBytes.fill(0)
            
            Result.success(authResponse)
        } catch (e: Exception) {
            securityAuditLogger.logAuthenticationAttempt(
                success = false,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Sign data for session resumption
     */
    suspend fun signSessionResumption(
        sessionId: String,
        nonce: String,
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val privateKeyBytes = sshKeyImportManager.decryptSshPrivateKey(
                activity = activity,
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            val privateKey = parsePrivateKey(privateKeyBytes)
            val dataToSign = "$sessionId$nonce"
            val signature = signData(privateKey, dataToSign.toByteArray())
            
            privateKeyBytes.fill(0)
            
            Result.success(Base64.getEncoder().encodeToString(signature))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse SSH private key from bytes
     */
    private fun parsePrivateKey(keyBytes: ByteArray): PrivateKey {
        return try {
            // Try PKCS8 format first
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            // Try PEM format
            val keyString = String(keyBytes)
            val pemParser = PEMParser(keyString.reader())
            val pemObject = pemParser.readObject()
            
            when (pemObject) {
                is PEMKeyPair -> {
                    val converter = JcaPEMKeyConverter()
                    converter.getPrivateKey(pemObject.privateKeyInfo)
                }
                is PrivateKeyInfo -> {
                    val converter = JcaPEMKeyConverter()
                    converter.getPrivateKey(pemObject)
                }
                else -> throw IllegalArgumentException("Unsupported key format")
            }
        }
    }
    
    /**
     * Sign data with RSA private key (SSH-compatible)
     */
    private fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
        return when (privateKey.algorithm) {
            "RSA" -> {
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            "EC" -> {
                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            "Ed25519" -> {
                val signature = Signature.getInstance("Ed25519")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            else -> throw IllegalArgumentException("Unsupported key algorithm: ${privateKey.algorithm}")
        }
    }
    
    /**
     * Verify server response signature (optional, for mutual auth)
     */
    fun verifyServerSignature(
        publicKey: String,
        signature: String,
        data: String
    ): Boolean {
        return try {
            // Parse server's public key
            val keyBytes = Base64.getDecoder().decode(
                publicKey.replace("ssh-rsa ", "").split(" ")[0]
            )
            
            // Verify signature
            // Implementation depends on server's signing method
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### SSH Auth WebSocket Client

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
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
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
            
            webSocket = client.newWebSocket(request, createAuthWebSocketListener(projectId, sshIdentity, activity))
            
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
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
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
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
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
                        "auth_challenge" -> handleAuthChallenge(json, sshIdentity, activity)
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
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
    ) {
        val challenge = Json.decodeFromJsonElement<SshKeyAuthenticator.AuthChallenge>(json)
        
        val authResult = sshKeyAuthenticator.signChallenge(challenge, sshIdentity, activity)
        
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
        connectionStateManager.updateState(projectId, ConnectionState.CONNECTING)
        
        try {
            val request = Request.Builder()
                .url("ws://localhost:$localPort$path")
                .addHeader("X-Project-Id", projectId)
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, createWebSocketListener(projectId))
            
            // Start ping/pong monitoring
            startPingMonitoring()
            
            // Start message sending coroutine
            launchMessageSender()
            
        } catch (e: Exception) {
            connectionStateManager.updateState(
                projectId, 
                ConnectionState.ERROR("WebSocket connection failed: ${e.message}")
            )
            throw e
        }
    }
    
    /**
     * Send message through WebSocket
     */
    suspend fun sendMessage(message: MessageProtocol.Message) {
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
        projectId?.let {
            connectionStateManager.updateState(it, ConnectionState.DISCONNECTED)
        }
    }
    
    /**
     * Get current WebSocket instance
     */
    fun getWebSocket(): WebSocket? {
        return webSocket
    }
    
    private fun createWebSocketListener(projectId: String) = object : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connectionStateManager.updateState(projectId, ConnectionState.CONNECTED)
            
            // Send queued messages
            GlobalScope.launch {
                messageQueueManager.drainQueue(projectId).collect { message ->
                    sendMessage(message)
                }
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = messageProtocol.decodeMessage(text)
                GlobalScope.launch {
                    _incomingMessages.emit(message)
                }
            } catch (e: Exception) {
                // Log malformed message
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
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connectionStateManager.updateState(
                projectId,
                ConnectionState.ERROR("WebSocket failure: ${t.message}")
            )
            pingJob?.cancel()
        }
    }
    
    private fun startPingMonitoring() {
        pingJob = GlobalScope.launch {
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
        GlobalScope.launch {
            for (message in messageChannel) {
                webSocket?.send(message)
            }
        }
    }
}
```

### WebSocket Manager

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
        sshIdentity: SshIdentityEntity,
        activity: FragmentActivity
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
            client.connect(projectId, serverUrl, sshIdentity, activity)
            
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
            connection.webSocket.send(okio.ByteString.EMPTY)
            true
        } catch (e: Exception) {
            false
        }
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

### Message Protocol

**Purpose**: Defines the complete message protocol between mobile app and wrapper service. Includes all message types (commands, responses, permissions, status updates), serialization/deserialization logic, and type-safe message handling using Kotlin sealed classes.

```kotlin
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageProtocol @Inject constructor() {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Base message class
    @Serializable
    sealed class Message {
        abstract val id: String
        abstract val timestamp: Long
        abstract val type: MessageType
    }
    
    enum class MessageType {
        COMMAND,
        CLAUDE_RESPONSE,
        PERMISSION_REQUEST,
        PERMISSION_RESPONSE,
        SESSION_STATUS,
        ERROR,
        HEARTBEAT,
        WRAPPER_STATUS,
        SESSION_RESUME,
        SESSION_SYNC,
        PENDING_PERMISSIONS,
        WRAPPER_HANDSHAKE,
        PROGRESS_UPDATE
    }
    
    // Outgoing messages (Mobile → Wrapper)
    
    @Serializable
    @SerialName("command")
    data class CommandMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.COMMAND,
        val command: String,
        val isShellCommand: Boolean = false
    ) : Message()
    
    @Serializable
    @SerialName("permission_response")
    data class PermissionResponse(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.PERMISSION_RESPONSE,
        val requestId: String,
        val approved: Boolean,
        val remember: Boolean = false
    ) : Message()
    
    @Serializable
    @SerialName("heartbeat")
    data class HeartbeatMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.HEARTBEAT
    ) : Message()
    
    @Serializable
    @SerialName("session_resume")
    data class SessionResumeMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.SESSION_RESUME,
        val sessionId: String,
        val lastMessageId: String,
        val lastMessageTimestamp: Long
    ) : Message()
    
    // Incoming messages (Wrapper → Mobile)
    
    @Serializable
    @SerialName("claude_response")
    data class ClaudeResponse(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.CLAUDE_RESPONSE,
        val content: String,
        val isPartial: Boolean = false,
        val conversationId: String?,
        val metadata: Map<String, String> = emptyMap()
    ) : Message()
    
    @Serializable
    @SerialName("permission_request")
    data class PermissionRequest(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.PERMISSION_REQUEST,
        val tool: String,
        val action: String,
        val details: Map<String, Any>,
        val risk: RiskLevel = RiskLevel.MEDIUM
    ) : Message()
    
    enum class RiskLevel {
        LOW,    // Read-only operations
        MEDIUM, // Modifying files
        HIGH    // System changes, deletions
    }
    
    @Serializable
    @SerialName("session_status")
    data class SessionStatus(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.SESSION_STATUS,
        val status: Status,
        val progress: Progress? = null,
        val subAgents: List<SubAgentInfo> = emptyList()
    ) : Message() {
        
        @Serializable
        data class Status(
            val state: SessionState,
            val message: String? = null
        )
        
        @Serializable
        data class Progress(
            val currentStep: String,
            val completedSteps: Int,
            val totalSteps: Int?,
            val percentage: Float?
        )
        
        @Serializable
        data class SubAgentInfo(
            val id: String,
            val name: String,
            val status: String,
            val startedAt: Long,
            val completedAt: Long? = null
        )
    }
    
    enum class SessionState {
        IDLE,
        PROCESSING,
        WAITING_FOR_PERMISSION,
        COMPLETED,
        ERROR
    }
    
    @Serializable
    @SerialName("error")
    data class ErrorMessage(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.ERROR,
        val code: String,
        val message: String,
        val details: Map<String, Any> = emptyMap(),
        val recoverable: Boolean = true
    ) : Message()
    
    @Serializable
    @SerialName("wrapper_status")
    data class WrapperStatus(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.WRAPPER_STATUS,
        val version: String,
        val claudeCodeStatus: ClaudeCodeStatus,
        val resourceUsage: ResourceUsage
    ) : Message() {
        
        @Serializable
        data class ClaudeCodeStatus(
            val running: Boolean,
            val pid: Int?,
            val uptime: Long? // seconds
        )
        
        @Serializable
        data class ResourceUsage(
            val cpuPercent: Float,
            val memoryMB: Int,
            val diskUsageMB: Int
        )
    }
    
    @Serializable
    @SerialName("session_sync")
    data class SessionSyncMessage(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.SESSION_SYNC,
        val sessionId: String,
        val conversationId: String,
        val messageHistory: List<MessageSummary>,
        val currentTurnNumber: Int,
        val pendingOperations: List<String>
    ) : Message() {
        
        @Serializable
        data class MessageSummary(
            val id: String,
            val timestamp: Long,
            val type: String,
            val summary: String
        )
    }
    
    @Serializable
    @SerialName("pending_permissions")
    data class PendingPermissionsMessage(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.PENDING_PERMISSIONS,
        val permissions: List<PermissionRequest>
    ) : Message()
    
    @Serializable
    @SerialName("health_check")
    data class HealthCheckMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.HEARTBEAT
    ) : Message()
    
    @Serializable
    @SerialName("health_check_response")
    data class HealthCheckResponse(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.HEARTBEAT,
        val requestId: String,
        val healthy: Boolean,
        val wrapperVersion: String,
        val claudeStatus: String
    ) : Message()
    
    @Serializable
    @SerialName("session_control")
    data class SessionControlMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: MessageType = MessageType.SESSION_STATUS,
        val action: SessionAction
    ) : Message()
    
    enum class SessionAction {
        PAUSE,
        RESUME,
        STOP,
        RESTART
    }
    
    @Serializable
    @SerialName("wrapper_handshake")
    data class WrapperHandshakeMessage(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.WRAPPER_HANDSHAKE,
        val wrapperVersion: String,
        val protocolVersion: String,
        val supportedFeatures: List<String>,
        val websocketPort: Int,
        val sessionId: String
    ) : Message()
    
    @Serializable
    @SerialName("progress_update")
    data class ProgressUpdateMessage(
        override val id: String,
        override val timestamp: Long,
        override val type: MessageType = MessageType.PROGRESS_UPDATE,
        val operationId: String,
        val currentStep: String,
        val completedSteps: Int,
        val totalSteps: Int?,
        val percentage: Float?,
        val estimatedCompletionTime: Long?,
        val subAgentProgress: List<SubAgentProgress>?
    ) : Message() {
        
        @Serializable
        data class SubAgentProgress(
            val agentId: String,
            val agentName: String,
            val status: String,
            val progress: Float?
        )
    }
    
    /**
     * Encode message to JSON
     */
    fun encodeMessage(message: Message): String {
        return json.encodeToString(Message.serializer(), message)
    }
    
    /**
     * Decode JSON to message
     */
    fun decodeMessage(jsonString: String): Message {
        return json.decodeFromString(Message.serializer(), jsonString)
    }
    
    /**
     * Create polymorphic serializer module
     */
    val module = SerializersModule {
        polymorphic(Message::class) {
            subclass(CommandMessage::class, CommandMessage.serializer())
            subclass(PermissionResponse::class, PermissionResponse.serializer())
            subclass(HeartbeatMessage::class, HeartbeatMessage.serializer())
            subclass(SessionResumeMessage::class, SessionResumeMessage.serializer())
            subclass(ClaudeResponse::class, ClaudeResponse.serializer())
            subclass(PermissionRequest::class, PermissionRequest.serializer())
            subclass(SessionStatus::class, SessionStatus.serializer())
            subclass(ErrorMessage::class, ErrorMessage.serializer())
            subclass(WrapperStatus::class, WrapperStatus.serializer())
            subclass(SessionSyncMessage::class, SessionSyncMessage.serializer())
            subclass(PendingPermissionsMessage::class, PendingPermissionsMessage.serializer())
            subclass(WrapperHandshakeMessage::class, WrapperHandshakeMessage.serializer())
            subclass(ProgressUpdateMessage::class, ProgressUpdateMessage.serializer())
        }
    }
}
```

### Shared Models

**Purpose**: Common data models and enums used across the communication layer and other features. These models ensure consistent data representation throughout the application.

```kotlin
// Connection status enum used by background services and UI
enum class ConnectionStatus {
    DISCONNECTED,      // No active connection
    CONNECTING,        // Establishing connection
    CONNECTED,         // Active connection
    ERROR,            // Connection error
    DISCONNECTING,    // Closing connection
    SHUTDOWN          // Claude Code shutdown
}

// Claude message representation with detailed chat message types
data class ClaudeMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val type: MessageType,
    val conversationId: String? = null,
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val uiData: MessageUIData? = null
) {
    enum class MessageType {
        USER_INPUT,             // User text input
        CLAUDE_RESPONSE,        // Claude's response
        SYSTEM_MESSAGE,         // System notifications
        ERROR_MESSAGE,          // Error notifications
        STATUS_UPDATE,          // Connection/session status
        PERMISSION_REQUEST,     // Interactive permission card
        PROGRESS_UPDATE,        // Task progress with percentage
        CODE_BLOCK,            // Code snippet with syntax highlighting
        SUB_AGENT_STATUS,      // Sub-agent activity update
        TASK_COMPLETION,       // Task summary with results
        TYPING_INDICATOR,      // Claude is thinking
        FILE_REFERENCE,        // File path with preview
        COMMAND_EXECUTION,     // Shell command result
        VOICE_INPUT,           // Voice transcription
        CONNECTION_STATUS,     // Connection metrics card
        QUICK_ACTION_RESULT    // Quick action execution result
    }
    
    // Additional UI data for rich message rendering
    data class MessageUIData(
        val alignment: MessageAlignment = MessageAlignment.LEFT,
        val backgroundColor: String? = null,
        val icon: String? = null,
        val actions: List<MessageAction> = emptyList(),
        val progress: ProgressData? = null,
        val codeData: CodeBlockData? = null,
        val commandData: CommandExecutionData? = null,
        val permissionData: PermissionRequestData? = null,
        val subAgentData: SubAgentData? = null,
        val fileReferenceData: FileReferenceData? = null,
        val voiceData: VoiceInputData? = null,
        val quickActionData: QuickActionData? = null,
        val connectionData: ConnectionStatusData? = null
    )
    
    enum class MessageAlignment {
        LEFT,    // Claude, system messages
        RIGHT,   // User messages
        CENTER   // System notifications
    }
    
    data class MessageAction(
        val id: String,
        val label: String,
        val style: ActionStyle,
        val enabled: Boolean = true
    )
    
    enum class ActionStyle {
        PRIMARY,    // Allow, Retry
        SECONDARY,  // Deny, Cancel
        DANGER,     // Delete, Stop
        TEXT        // Copy, View More
    }
    
    data class ProgressData(
        val percentage: Float,
        val currentStep: String,
        val totalSteps: Int?,
        val estimatedTimeRemaining: Long?
    )
    
    data class CodeBlockData(
        val language: String,
        val code: String,
        val fileName: String? = null,
        val startLine: Int? = null,
        val highlighted: List<Int> = emptyList()
    )
    
    data class CommandExecutionData(
        val command: String,
        val output: String,
        val exitCode: Int,
        val executionTime: Long,
        val isError: Boolean
    )
    
    data class PermissionRequestData(
        val tool: String,
        val action: String,
        val affectedResources: List<String>,
        val riskLevel: RiskLevel,
        val timeoutSeconds: Int,
        val defaultAction: PermissionAction
    )
    
    enum class PermissionAction {
        ALLOW,
        DENY,
        PENDING
    }
    
    data class SubAgentData(
        val agentId: String,
        val agentName: String,
        val status: SubAgentStatus,
        val currentTask: String,
        val startTime: Long,
        val completionTime: Long? = null
    )
    
    enum class SubAgentStatus {
        PLANNING,
        RUNNING,
        PAUSED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    
    data class FileReferenceData(
        val filePath: String,
        val lineStart: Int? = null,
        val lineEnd: Int? = null,
        val preview: String? = null,
        val gitStatus: GitFileStatus? = null
    )
    
    enum class GitFileStatus {
        MODIFIED,
        ADDED,
        DELETED,
        UNTRACKED,
        UNCHANGED
    }
    
    data class VoiceInputData(
        val transcription: String,
        val confidence: Float,
        val duration: Long,
        val language: String
    )
    
    data class QuickActionData(
        val actionName: String,
        val actionType: QuickActionType,
        val executionTime: Long,
        val status: QuickActionStatus,
        val result: String? = null
    )
    
    enum class QuickActionType {
        CLAUDE_PROMPT,
        SHELL_COMMAND,
        PROJECT_SCRIPT
    }
    
    enum class QuickActionStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }
    
    data class ConnectionStatusData(
        val sshStatus: ConnectionState,
        val websocketStatus: ConnectionState,
        val latencyMs: Long,
        val dataTransferred: DataTransferStats,
        val uptime: Long
    )
    
    data class ConnectionState(
        val connected: Boolean,
        val message: String? = null
    )
    
    data class DataTransferStats(
        val bytesSent: Long,
        val bytesReceived: Long,
        val messagesSent: Int,
        val messagesReceived: Int
    )
}

// Battery state for optimization
data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
    val level: BatteryLevel
)

enum class BatteryLevel {
    CHARGING,
    NORMAL,      // > 30%
    LOW,         // 15-30%
    CRITICAL,    // < 15%
    POWER_SAVE   // Power save mode active
}

// Session state for persistence
data class SessionState(
    val sessionId: String,
    val projectId: String,
    val conversationId: String?,
    val lastMessageId: String?,
    val lastActivity: Long,
    val pendingPermissions: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

// Chat manager interface for voice integration
interface ChatManager {
    suspend fun sendMessage(projectId: String, message: String): Result<String>
    suspend fun getLastMessage(projectId: String): ClaudeMessage?
    suspend fun getConversationHistory(projectId: String, limit: Int = 10): List<ClaudeMessage>
    fun observeMessages(projectId: String): Flow<ClaudeMessage>
    suspend fun clearChat(projectId: String): Result<Unit>
}
```

### Connection State Manager

**Purpose**: Centralized state management for all project connections. Tracks connection states (disconnected, connecting, connected, error, reconnecting), provides reactive state flows for UI updates, and maintains connection inventory across multiple projects.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionStateManager @Inject constructor() {
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    
    sealed class ConnectionState {
        object DISCONNECTED : ConnectionState()
        object CONNECTING : ConnectionState()
        object CONNECTED : ConnectionState()
        data class DISCONNECTING(val reason: String) : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
        object RECONNECTING : ConnectionState()
    }
    
    /**
     * Update connection state for a project
     */
    fun updateState(projectId: String, state: ConnectionState) {
        _connectionStates.update { current ->
            current + (projectId to state)
        }
    }
    
    /**
     * Get current state for a project
     */
    fun getState(projectId: String): ConnectionState {
        return _connectionStates.value[projectId] ?: ConnectionState.DISCONNECTED
    }
    
    /**
     * Get state flow for a specific project
     */
    fun getStateFlow(projectId: String): Flow<ConnectionState> {
        return _connectionStates.map { states ->
            states[projectId] ?: ConnectionState.DISCONNECTED
        }.distinctUntilChanged()
    }
    
    /**
     * Check if project is connected
     */
    fun isConnected(projectId: String): Boolean {
        return getState(projectId) == ConnectionState.CONNECTED
    }
    
    /**
     * Clear state for a project
     */
    fun clearState(projectId: String) {
        _connectionStates.update { current ->
            current - projectId
        }
    }
    
    /**
     * Get all connected projects
     */
    fun getConnectedProjects(): List<String> {
        return _connectionStates.value
            .filter { it.value == ConnectionState.CONNECTED }
            .keys
            .toList()
    }
}
```

### Message Queue Manager

**Purpose**: Manages message queuing during network disconnections to prevent data loss. Implements per-project queues with size limits, message expiration, and automatic queue draining when connection is restored. Essential for handling intermittent connectivity.

```kotlin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class MessageQueueManager @Inject constructor() {
    
    companion object {
        private const val MAX_QUEUE_SIZE = 1000
        private const val MESSAGE_RETENTION_MS = 5 * 60 * 1000 // 5 minutes
    }
    
    private val messageQueues = ConcurrentHashMap<String, MessageQueue>()
    
    data class QueuedMessage(
        val message: MessageProtocol.Message,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    class MessageQueue {
        private val queue = Channel<QueuedMessage>(MAX_QUEUE_SIZE)
        private val _queueSize = MutableStateFlow(0)
        val queueSize: StateFlow<Int> = _queueSize.asStateFlow()
        
        suspend fun enqueue(message: MessageProtocol.Message) {
            val queued = QueuedMessage(message)
            if (queue.trySend(queued).isSuccess) {
                _queueSize.value++
            }
        }
        
        suspend fun dequeue(): QueuedMessage? {
            return queue.tryReceive().getOrNull()?.also {
                _queueSize.value--
            }
        }
        
        fun close() {
            queue.close()
        }
    }
    
    /**
     * Enqueue message for a project
     */
    suspend fun enqueueMessage(projectId: String, message: MessageProtocol.Message) {
        val queue = messageQueues.getOrPut(projectId) { MessageQueue() }
        queue.enqueue(message)
    }
    
    /**
     * Drain all queued messages for a project
     */
    fun drainQueue(projectId: String): Flow<MessageProtocol.Message> = flow {
        val queue = messageQueues[projectId] ?: return@flow
        val now = System.currentTimeMillis()
        
        while (true) {
            val queued = queue.dequeue() ?: break
            
            // Skip expired messages
            if (now - queued.timestamp > MESSAGE_RETENTION_MS) {
                continue
            }
            
            emit(queued.message)
        }
    }
    
    /**
     * Get queue size for a project
     */
    fun getQueueSize(projectId: String): Int {
        return messageQueues[projectId]?.queueSize?.value ?: 0
    }
    
    /**
     * Clear queue for a project
     */
    fun clearQueue(projectId: String) {
        messageQueues[projectId]?.close()
        messageQueues.remove(projectId)
    }
    
    /**
     * Get total queued messages across all projects
     */
    fun getTotalQueuedMessages(): Int {
        return messageQueues.values.sumOf { it.queueSize.value }
    }
}
```

### Reconnection Manager

**Purpose**: Implements intelligent reconnection strategy with exponential backoff. Monitors connection states, respects network availability, tracks retry attempts, and prevents reconnection storms. Configurable per-project with automatic retry limit enforcement.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class ReconnectionManager @Inject constructor(
    private val connectionStateManager: ConnectionStateManager,
    private val networkStateObserver: NetworkStateObserver
) {
    
    companion object {
        private const val INITIAL_DELAY_MS = 1000L // 1 second
        private const val MAX_DELAY_MS = 60000L // 60 seconds
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val MAX_RETRY_ATTEMPTS = 10
    }
    
    private val reconnectionJobs = mutableMapOf<String, Job>()
    private val retryAttempts = mutableMapOf<String, Int>()
    
    data class ReconnectionConfig(
        val projectId: String,
        val onReconnect: suspend () -> Result<Unit>
    )
    
    /**
     * Start monitoring connection for automatic reconnection
     */
    fun startMonitoring(config: ReconnectionConfig) {
        val job = GlobalScope.launch {
            // Monitor connection state
            connectionStateManager.getStateFlow(config.projectId).collect { state ->
                when (state) {
                    is ConnectionStateManager.ConnectionState.ERROR,
                    is ConnectionStateManager.ConnectionState.DISCONNECTED -> {
                        if (shouldReconnect(config.projectId)) {
                            startReconnection(config)
                        }
                    }
                    is ConnectionStateManager.ConnectionState.CONNECTED -> {
                        // Reset retry attempts on successful connection
                        retryAttempts[config.projectId] = 0
                    }
                    else -> { /* No action needed */ }
                }
            }
        }
        
        reconnectionJobs[config.projectId] = job
    }
    
    /**
     * Stop monitoring for a project
     */
    fun stopMonitoring(projectId: String) {
        reconnectionJobs[projectId]?.cancel()
        reconnectionJobs.remove(projectId)
        retryAttempts.remove(projectId)
    }
    
    private suspend fun startReconnection(config: ReconnectionConfig) {
        val attempts = retryAttempts.getOrDefault(config.projectId, 0)
        
        if (attempts >= MAX_RETRY_ATTEMPTS) {
            connectionStateManager.updateState(
                config.projectId,
                ConnectionStateManager.ConnectionState.ERROR("Max reconnection attempts reached")
            )
            return
        }
        
        connectionStateManager.updateState(
            config.projectId,
            ConnectionStateManager.ConnectionState.RECONNECTING
        )
        
        val delay = calculateBackoffDelay(attempts)
        delay(delay)
        
        // Check network availability before attempting
        if (!networkStateObserver.isNetworkAvailable()) {
            // Wait for network
            networkStateObserver.networkState.first { it.isConnected }
        }
        
        val result = config.onReconnect()
        
        if (result.isFailure) {
            retryAttempts[config.projectId] = attempts + 1
        }
    }
    
    private fun shouldReconnect(projectId: String): Boolean {
        // Check if reconnection is already in progress
        val currentState = connectionStateManager.getState(projectId)
        return currentState !is ConnectionStateManager.ConnectionState.RECONNECTING &&
               currentState !is ConnectionStateManager.ConnectionState.CONNECTING
    }
    
    private fun calculateBackoffDelay(attempts: Int): Long {
        val exponentialDelay = INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempts.toDouble()).toLong()
        return min(exponentialDelay, MAX_DELAY_MS)
    }
    
    /**
     * Force immediate reconnection attempt
     */
    suspend fun forceReconnect(config: ReconnectionConfig) {
        retryAttempts[config.projectId] = 0
        startReconnection(config)
    }
    
    /**
     * Get current retry attempt for a project
     */
    fun getRetryAttempt(projectId: String): Int {
        return retryAttempts[projectId] ?: 0
    }
}
```

### Connection Health Monitor

**Purpose**: Continuously monitors health of SSH tunnels and WebSocket connections. Performs periodic health checks, measures latency, detects connection degradation, and triggers reconnection when unhealthy. Provides real-time health metrics for UI display.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionHealthMonitor @Inject constructor(
    private val sshTunnelManager: SshTunnelManager,
    private val webSocketClient: WebSocketClient,
    private val connectionStateManager: ConnectionStateManager,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L // 5 seconds
        private const val SSH_CHECK_TIMEOUT_MS = 3000L
        private const val WS_CHECK_TIMEOUT_MS = 2000L
    }
    
    private val monitoringJobs = mutableMapOf<String, Job>()
    private val _healthStatus = MutableStateFlow<Map<String, HealthStatus>>(emptyMap())
    val healthStatus: StateFlow<Map<String, HealthStatus>> = _healthStatus.asStateFlow()
    
    data class HealthStatus(
        val sshTunnelHealthy: Boolean,
        val webSocketHealthy: Boolean,
        val lastCheckTime: Long,
        val latencyMs: Long? = null
    ) {
        val isHealthy: Boolean get() = sshTunnelHealthy && webSocketHealthy
    }
    
    /**
     * Start health monitoring for a project
     */
    fun startMonitoring(projectId: String) {
        val job = scope.launch {
            while (isActive) {
                val health = checkHealth(projectId)
                updateHealthStatus(projectId, health)
                
                if (!health.isHealthy) {
                    connectionStateManager.updateState(
                        projectId,
                        ConnectionStateManager.ConnectionState.ERROR("Connection unhealthy")
                    )
                }
                
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
        
        monitoringJobs[projectId] = job
    }
    
    /**
     * Stop health monitoring for a project
     */
    fun stopMonitoring(projectId: String) {
        monitoringJobs[projectId]?.cancel()
        monitoringJobs.remove(projectId)
        _healthStatus.update { it - projectId }
    }
    
    private suspend fun checkHealth(projectId: String): HealthStatus {
        val startTime = System.currentTimeMillis()
        
        // Check SSH tunnel
        val sshHealthy = withTimeoutOrNull(SSH_CHECK_TIMEOUT_MS) {
            sshTunnelManager.isTunnelActive(projectId)
        } ?: false
        
        // Check WebSocket
        val wsHealthy = withTimeoutOrNull(WS_CHECK_TIMEOUT_MS) {
            connectionStateManager.isConnected(projectId)
        } ?: false
        
        val latency = System.currentTimeMillis() - startTime
        
        return HealthStatus(
            sshTunnelHealthy = sshHealthy,
            webSocketHealthy = wsHealthy,
            lastCheckTime = System.currentTimeMillis(),
            latencyMs = latency
        )
    }
    
    private fun updateHealthStatus(projectId: String, status: HealthStatus) {
        _healthStatus.update { current ->
            current + (projectId to status)
        }
    }
    
    /**
     * Force immediate health check
     */
    suspend fun forceHealthCheck(projectId: String): HealthStatus {
        val health = checkHealth(projectId)
        updateHealthStatus(projectId, health)
        return health
    }
    
    /**
     * Get average latency for a project
     */
    fun getAverageLatency(projectId: String): Long? {
        return _healthStatus.value[projectId]?.latencyMs
    }
}
```

### Message Handlers

**Purpose**: Registry pattern for handling different message types. Each handler processes specific message types (Claude responses, permission requests, status updates) with appropriate actions like database storage, notification display, or UI updates. Extensible for new message types.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHandlerRegistry @Inject constructor() {
    
    private val handlers = mutableMapOf<MessageProtocol.MessageType, MessageHandler>()
    
    interface MessageHandler {
        suspend fun handle(message: MessageProtocol.Message)
    }
    
    /**
     * Register a handler for a message type
     */
    fun register(type: MessageProtocol.MessageType, handler: MessageHandler) {
        handlers[type] = handler
    }
    
    /**
     * Process incoming message
     */
    suspend fun processMessage(message: MessageProtocol.Message) {
        handlers[message.type]?.handle(message)
    }
}

// Example handlers

@Singleton
class ClaudeResponseHandler @Inject constructor(
    private val messageRepository: MessageRepository
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.ClaudeResponse) {
            // Store in local database
            messageRepository.insertMessage(
                MessageEntity(
                    id = message.id,
                    projectId = message.conversationId ?: "",
                    content = message.content,
                    isFromClaude = true,
                    timestamp = message.timestamp
                )
            )
        }
    }
}

@Singleton
class PermissionRequestHandler @Inject constructor(
    private val notificationManager: NotificationManager
) : MessageHandlerRegistry.MessageHandler {
    
    private val _pendingRequests = MutableStateFlow<List<MessageProtocol.PermissionRequest>>(emptyList())
    val pendingRequests: StateFlow<List<MessageProtocol.PermissionRequest>> = _pendingRequests.asStateFlow()
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.PermissionRequest) {
            _pendingRequests.update { current -> current + message }
            
            // Show notification
            notificationManager.showPermissionNotification(
                requestId = message.id,
                tool = message.tool,
                action = message.action,
                risk = message.risk
            )
        }
    }
    
    fun removeRequest(requestId: String) {
        _pendingRequests.update { current ->
            current.filter { it.id != requestId }
        }
    }
}

@Singleton
class SessionStatusHandler @Inject constructor(
    private val progressTracker: ProgressTracker
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.SessionStatus) {
            message.progress?.let { progress ->
                progressTracker.updateProgress(
                    projectId = "", // Extract from context
                    currentStep = progress.currentStep,
                    completedSteps = progress.completedSteps,
                    totalSteps = progress.totalSteps,
                    percentage = progress.percentage
                )
            }
            
            // Update sub-agents
            message.subAgents.forEach { subAgent ->
                progressTracker.updateSubAgent(subAgent)
            }
        }
    }
}

@Singleton
class WrapperHandshakeHandler @Inject constructor(
    private val sessionManager: SessionManager,
    private val connectionStateManager: ConnectionStateManager
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.WrapperHandshakeMessage) {
            // Store session information
            sessionManager.initializeSession(
                sessionId = message.sessionId,
                protocolVersion = message.protocolVersion
            )
            
            // Update connection state
            connectionStateManager.updateState(
                projectId = message.sessionId,
                state = ConnectionStateManager.ConnectionState.CONNECTED
            )
        }
    }
}

@Singleton
class PendingPermissionsHandler @Inject constructor(
    private val permissionRequestHandler: PermissionRequestHandler,
    private val permissionPolicyManager: PermissionPolicyManager,
    private val webSocketClient: WebSocketClient
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.PendingPermissionsMessage) {
            message.permissions.forEach { request ->
                // Evaluate each permission
                val decision = permissionPolicyManager.evaluatePermission(
                    request = request,
                    isConnected = true
                )
                
                if (decision.policyApplied != PermissionPolicyManager.PolicyType.USER_DECISION) {
                    // Auto-respond based on policy
                    val response = MessageProtocol.PermissionResponse(
                        requestId = request.id,
                        approved = decision.approved,
                        remember = false
                    )
                    webSocketClient.sendMessage(response)
                } else {
                    // Queue for user decision
                    permissionRequestHandler.handle(request)
                }
            }
        }
    }
}

@Singleton
class ProgressUpdateHandler @Inject constructor(
    private val progressTracker: ProgressTracker
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.ProgressUpdateMessage) {
            progressTracker.updateDetailedProgress(
                operationId = message.operationId,
                currentStep = message.currentStep,
                completedSteps = message.completedSteps,
                totalSteps = message.totalSteps,
                percentage = message.percentage,
                estimatedCompletionTime = message.estimatedCompletionTime,
                subAgentProgress = message.subAgentProgress
            )
        }
    }
}
```

### Session Manager

**Purpose**: Manages Claude Code session lifecycle including persistence, resumption, and synchronization. Tracks conversation IDs, handles session state recovery after disconnections, and ensures message continuity across app restarts.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val messageProtocol: MessageProtocol,
    private val webSocketClient: WebSocketClient,
    private val messageQueueManager: MessageQueueManager
) {
    
    companion object {
        private const val SESSION_CACHE_SIZE = 10
        private const val SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    private val _activeSessions = MutableStateFlow<Map<String, SessionState>>(emptyMap())
    val activeSessions: StateFlow<Map<String, SessionState>> = _activeSessions.asStateFlow()
    
    data class SessionState(
        val sessionId: String,
        val projectId: String,
        val conversationId: String?,
        val lastMessageId: String?,
        val lastMessageTimestamp: Long,
        val turnNumber: Int = 0,
        val pendingOperations: List<String> = emptyList(),
        val isActive: Boolean = true
    )
    
    /**
     * Resume an existing session after reconnection
     */
    suspend fun resumeSession(
        sessionId: String,
        projectId: String
    ): Result<SessionState> {
        return try {
            // Load session from repository
            val persistedSession = sessionRepository.getSession(sessionId)
                ?: return Result.failure(IllegalStateException("Session not found"))
            
            // Create session state
            val sessionState = SessionState(
                sessionId = sessionId,
                projectId = projectId,
                conversationId = persistedSession.conversationId,
                lastMessageId = persistedSession.lastMessageId,
                lastMessageTimestamp = persistedSession.lastMessageTimestamp,
                turnNumber = persistedSession.turnNumber
            )
            
            // Send resume message
            val resumeMessage = MessageProtocol.SessionResumeMessage(
                sessionId = sessionId,
                lastMessageId = persistedSession.lastMessageId ?: "",
                lastMessageTimestamp = persistedSession.lastMessageTimestamp
            )
            
            webSocketClient.sendMessage(resumeMessage)
            
            // Update active sessions
            _activeSessions.update { current ->
                current + (projectId to sessionState)
            }
            
            Result.success(sessionState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Track Claude conversation ID for a session
     */
    suspend fun trackClaudeConversationId(
        sessionId: String,
        conversationId: String
    ) {
        // Update in-memory state
        _activeSessions.update { sessions ->
            sessions.mapValues { (_, state) ->
                if (state.sessionId == sessionId) {
                    state.copy(conversationId = conversationId)
                } else {
                    state
                }
            }
        }
        
        // Persist to repository
        sessionRepository.updateConversationId(sessionId, conversationId)
    }
    
    /**
     * Sync session state with wrapper
     */
    suspend fun syncSessionState(sessionId: String): SessionSyncState {
        val sessionState = _activeSessions.value.values.find { it.sessionId == sessionId }
            ?: throw IllegalStateException("Session not found")
        
        // Wait for sync response
        val syncFlow = webSocketClient.incomingMessages
            .filterIsInstance<MessageProtocol.SessionSyncMessage>()
            .filter { it.sessionId == sessionId }
            .first()
        
        return SessionSyncState(
            synchronized = true,
            missedMessages = syncFlow.messageHistory,
            currentTurnNumber = syncFlow.currentTurnNumber,
            pendingOperations = syncFlow.pendingOperations
        )
    }
    
    /**
     * Handle session timeout
     */
    suspend fun cleanupTimedOutSessions() {
        val now = System.currentTimeMillis()
        _activeSessions.update { sessions ->
            sessions.filter { (_, state) ->
                now - state.lastMessageTimestamp < SESSION_TIMEOUT_MS
            }
        }
    }
    
    /**
     * Persist session state
     */
    suspend fun persistSession(sessionState: SessionState) {
        sessionRepository.saveSession(
            SessionEntity(
                sessionId = sessionState.sessionId,
                projectId = sessionState.projectId,
                conversationId = sessionState.conversationId,
                lastMessageId = sessionState.lastMessageId,
                lastMessageTimestamp = sessionState.lastMessageTimestamp,
                turnNumber = sessionState.turnNumber
            )
        )
    }
    
    /**
     * Initialize a new session from handshake
     */
    suspend fun initializeSession(
        sessionId: String,
        protocolVersion: String
    ) {
        // Implementation would create or update session
        val sessionState = SessionState(
            sessionId = sessionId,
            projectId = sessionId, // Map as needed
            conversationId = null,
            lastMessageId = null,
            lastMessageTimestamp = System.currentTimeMillis(),
            turnNumber = 0,
            pendingOperations = emptyList(),
            isActive = true
        )
        
        _activeSessions.update { current ->
            current + (sessionId to sessionState)
        }
    }
    
    data class SessionSyncState(
        val synchronized: Boolean,
        val missedMessages: List<MessageProtocol.SessionSyncMessage.MessageSummary>,
        val currentTurnNumber: Int,
        val pendingOperations: List<String>
    )
}
```

### Permission Policy Manager

**Purpose**: Manages permission policies for unattended operation, caches permission decisions, and applies default policies based on risk levels. Enables the app to handle permissions automatically when disconnected based on user preferences.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class PermissionPolicyManager @Inject constructor(
    private val encryptedStorageManager: EncryptedStorageManager,
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    companion object {
        private const val POLICY_KEY_PREFIX = "permission_policy_"
        private const val DECISION_CACHE_SIZE = 100
        private const val DECISION_EXPIRY_MS = 60 * 60 * 1000L // 1 hour
    }
    
    private val decisionCache = ConcurrentHashMap<String, CachedDecision>()
    private val _defaultPolicies = MutableStateFlow(loadDefaultPolicies())
    val defaultPolicies: StateFlow<DefaultPolicies> = _defaultPolicies.asStateFlow()
    
    data class CachedDecision(
        val decision: PermissionDecision,
        val timestamp: Long,
        val expiresAt: Long
    )
    
    data class PermissionDecision(
        val approved: Boolean,
        val reason: String,
        val policyApplied: PolicyType
    )
    
    enum class PolicyType {
        USER_DECISION,
        DEFAULT_POLICY,
        CACHED_DECISION,
        RISK_BASED,
        TIMEOUT_DEFAULT
    }
    
    data class DefaultPolicies(
        val allowLowRiskByDefault: Boolean = true,
        val allowMediumRiskByDefault: Boolean = false,
        val denyHighRiskByDefault: Boolean = true,
        val timeoutSeconds: Int = 30,
        val timeoutAction: TimeoutAction = TimeoutAction.DENY
    )
    
    enum class TimeoutAction {
        APPROVE,
        DENY,
        USE_RISK_BASED
    }
    
    /**
     * Evaluate permission request based on policies
     */
    fun evaluatePermission(
        request: MessageProtocol.PermissionRequest,
        isConnected: Boolean
    ): PermissionDecision {
        // Check cached decision first
        getCachedDecision(request.id)?.let { cached ->
            if (System.currentTimeMillis() < cached.expiresAt) {
                return cached.decision
            }
        }
        
        // If connected, wait for user decision
        if (isConnected) {
            return PermissionDecision(
                approved = false,
                reason = "Awaiting user decision",
                policyApplied = PolicyType.USER_DECISION
            )
        }
        
        // Apply default policies for unattended operation
        return applyDefaultPolicies(request.tool, request.action, request.risk)
    }
    
    /**
     * Cache a permission decision
     */
    fun cacheDecision(
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean = false
    ) {
        val expiryTime = if (remember) {
            System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24 hours
        } else {
            System.currentTimeMillis() + DECISION_EXPIRY_MS
        }
        
        decisionCache[requestId] = CachedDecision(
            decision = decision,
            timestamp = System.currentTimeMillis(),
            expiresAt = expiryTime
        )
        
        // Limit cache size
        if (decisionCache.size > DECISION_CACHE_SIZE) {
            cleanupOldestDecisions()
        }
        
        // Log decision
        securityAuditLogger.logPermissionDecision(
            requestId = requestId,
            approved = decision.approved,
            policyType = decision.policyApplied
        )
    }
    
    /**
     * Apply default policies based on risk level
     */
    fun applyDefaultPolicies(
        tool: String,
        action: String,
        risk: MessageProtocol.RiskLevel
    ): PermissionDecision {
        val policies = _defaultPolicies.value
        
        val approved = when (risk) {
            MessageProtocol.RiskLevel.LOW -> policies.allowLowRiskByDefault
            MessageProtocol.RiskLevel.MEDIUM -> policies.allowMediumRiskByDefault
            MessageProtocol.RiskLevel.HIGH -> !policies.denyHighRiskByDefault
        }
        
        return PermissionDecision(
            approved = approved,
            reason = "Default policy for $risk risk",
            policyApplied = PolicyType.RISK_BASED
        )
    }
    
    /**
     * Handle permission timeout
     */
    fun handlePermissionTimeout(
        request: MessageProtocol.PermissionRequest
    ): PermissionDecision {
        val policies = _defaultPolicies.value
        
        return when (policies.timeoutAction) {
            TimeoutAction.APPROVE -> PermissionDecision(
                approved = true,
                reason = "Approved by timeout policy",
                policyApplied = PolicyType.TIMEOUT_DEFAULT
            )
            TimeoutAction.DENY -> PermissionDecision(
                approved = false,
                reason = "Denied by timeout policy",
                policyApplied = PolicyType.TIMEOUT_DEFAULT
            )
            TimeoutAction.USE_RISK_BASED -> applyDefaultPolicies(
                request.tool,
                request.action,
                request.risk
            )
        }
    }
    
    /**
     * Update default policies
     */
    fun updateDefaultPolicies(policies: DefaultPolicies) {
        _defaultPolicies.value = policies
        saveDefaultPolicies(policies)
    }
    
    private fun getCachedDecision(requestId: String): CachedDecision? {
        return decisionCache[requestId]
    }
    
    private fun cleanupOldestDecisions() {
        val sortedEntries = decisionCache.entries.sortedBy { it.value.timestamp }
        val toRemove = sortedEntries.take(20)
        toRemove.forEach { decisionCache.remove(it.key) }
    }
    
    private fun loadDefaultPolicies(): DefaultPolicies {
        // Load from encrypted storage
        val lowRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}low_risk", true)
        val mediumRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}medium_risk", false)
        val highRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}high_risk", true)
        val timeout = encryptedStorageManager.getLong("${POLICY_KEY_PREFIX}timeout", 30).toInt()
        
        return DefaultPolicies(
            allowLowRiskByDefault = lowRisk,
            allowMediumRiskByDefault = mediumRisk,
            denyHighRiskByDefault = highRisk,
            timeoutSeconds = timeout
        )
    }
    
    private fun saveDefaultPolicies(policies: DefaultPolicies) {
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}low_risk", policies.allowLowRiskByDefault)
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}medium_risk", policies.allowMediumRiskByDefault)
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}high_risk", policies.denyHighRiskByDefault)
        encryptedStorageManager.putLong("${POLICY_KEY_PREFIX}timeout", policies.timeoutSeconds.toLong())
    }
}
```

### Wrapper Service Protocol

**Purpose**: Handles wrapper service discovery, handshake protocol, and version negotiation. Ensures proper initialization of communication channel including WebSocket port discovery and protocol compatibility verification.

```kotlin
import kotlinx.coroutines.*
import com.jcraft.jsch.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WrapperServiceProtocol @Inject constructor(
    private val messageProtocol: MessageProtocol
) {
    
    companion object {
        private const val WRAPPER_CHECK_COMMAND = "claude-wrapper status"
        private const val WRAPPER_START_COMMAND = "claude-wrapper start"
        private const val WRAPPER_INSTALL_URL = "https://install.claude-wrapper.dev"
        private const val MIN_WRAPPER_VERSION = "1.0.0"
        private const val PROTOCOL_VERSION = "1.0"
        private const val HANDSHAKE_TIMEOUT_MS = 10000L
    }
    
    data class WrapperInfo(
        val installed: Boolean,
        val version: String?,
        val running: Boolean,
        val pid: Int?,
        val websocketPort: Int?
    )
    
    data class HandshakeResult(
        val success: Boolean,
        val sessionId: String?,
        val websocketPort: Int?,
        val protocolVersion: String?,
        val supportedFeatures: List<String>
    )
    
    /**
     * Check wrapper service status via SSH
     */
    suspend fun checkWrapperStatus(sshSession: Session): WrapperInfo = withContext(Dispatchers.IO) {
        try {
            val channel = sshSession.openChannel("exec") as ChannelExec
            channel.setCommand(WRAPPER_CHECK_COMMAND)
            
            val output = ByteArrayOutputStream()
            channel.outputStream = output
            
            channel.connect()
            
            // Wait for command completion
            while (!channel.isClosed) {
                delay(100)
            }
            
            val result = output.toString()
            channel.disconnect()
            
            parseWrapperStatus(result)
        } catch (e: Exception) {
            WrapperInfo(
                installed = false,
                version = null,
                running = false,
                pid = null,
                websocketPort = null
            )
        }
    }
    
    /**
     * Install or update wrapper service
     */
    suspend fun installWrapper(sshSession: Session): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val channel = sshSession.openChannel("exec") as ChannelExec
            val installCommand = "curl -sSL $WRAPPER_INSTALL_URL | sh"
            channel.setCommand(installCommand)
            
            val output = ByteArrayOutputStream()
            val error = ByteArrayOutputStream()
            channel.outputStream = output
            channel.setErrStream(error)
            
            channel.connect()
            
            // Wait for installation
            while (!channel.isClosed) {
                delay(100)
            }
            
            val exitStatus = channel.exitStatus
            channel.disconnect()
            
            if (exitStatus == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception("Installation failed: ${error.toString()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Start wrapper service
     */
    suspend fun startWrapper(
        sshSession: Session,
        projectPath: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val channel = sshSession.openChannel("exec") as ChannelExec
            val startCommand = "$WRAPPER_START_COMMAND --project-path=$projectPath --websocket-port=0"
            channel.setCommand(startCommand)
            
            val output = ByteArrayOutputStream()
            channel.outputStream = output
            
            channel.connect()
            
            // Wait for startup
            while (!channel.isClosed) {
                delay(100)
            }
            
            val result = output.toString()
            channel.disconnect()
            
            // Parse WebSocket port from output
            val port = parseWebSocketPort(result)
            if (port != null) {
                Result.success(port)
            } else {
                Result.failure(Exception("Failed to parse WebSocket port"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Perform handshake with wrapper service
     */
    suspend fun performHandshake(
        webSocket: WebSocket,
        projectId: String
    ): HandshakeResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                // Wait for handshake message
                val handshakeDeferred = CompletableDeferred<MessageProtocol.WrapperHandshakeMessage>()
                
                // Set up listener
                val listener = object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val message = messageProtocol.decodeMessage(text)
                            if (message is MessageProtocol.WrapperHandshakeMessage) {
                                handshakeDeferred.complete(message)
                            }
                        } catch (e: Exception) {
                            // Ignore malformed messages during handshake
                        }
                    }
                }
                
                // Wait for handshake
                val handshake = handshakeDeferred.await()
                
                // Validate version compatibility
                if (!isVersionCompatible(handshake.wrapperVersion)) {
                    return@withTimeout HandshakeResult(
                        success = false,
                        sessionId = null,
                        websocketPort = null,
                        protocolVersion = null,
                        supportedFeatures = emptyList()
                    )
                }
                
                HandshakeResult(
                    success = true,
                    sessionId = handshake.sessionId,
                    websocketPort = handshake.websocketPort,
                    protocolVersion = handshake.protocolVersion,
                    supportedFeatures = handshake.supportedFeatures
                )
            }
        } catch (e: TimeoutCancellationException) {
            HandshakeResult(
                success = false,
                sessionId = null,
                websocketPort = null,
                protocolVersion = null,
                supportedFeatures = emptyList()
            )
        }
    }
    
    /**
     * Discover WebSocket port from running wrapper
     */
    suspend fun discoverWebSocketPort(sshSession: Session): Int? = withContext(Dispatchers.IO) {
        try {
            val channel = sshSession.openChannel("exec") as ChannelExec
            channel.setCommand("claude-wrapper info --json")
            
            val output = ByteArrayOutputStream()
            channel.outputStream = output
            
            channel.connect()
            
            while (!channel.isClosed) {
                delay(100)
            }
            
            val result = output.toString()
            channel.disconnect()
            
            parseWebSocketPortFromInfo(result)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseWrapperStatus(output: String): WrapperInfo {
        // Parse wrapper status output
        val lines = output.lines()
        var installed = false
        var version: String? = null
        var running = false
        var pid: Int? = null
        var port: Int? = null
        
        lines.forEach { line ->
            when {
                line.contains("Version:") -> {
                    version = line.substringAfter("Version:").trim()
                    installed = true
                }
                line.contains("Status: running") -> {
                    running = true
                }
                line.contains("PID:") -> {
                    pid = line.substringAfter("PID:").trim().toIntOrNull()
                }
                line.contains("WebSocket Port:") -> {
                    port = line.substringAfter("WebSocket Port:").trim().toIntOrNull()
                }
            }
        }
        
        return WrapperInfo(installed, version, running, pid, port)
    }
    
    private fun parseWebSocketPort(output: String): Int? {
        // Look for port in output like "WebSocket server started on port 12345"
        val regex = """port\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    private fun parseWebSocketPortFromInfo(jsonOutput: String): Int? {
        // Parse JSON output to get WebSocket port
        return try {
            val json = Json.parseToJsonElement(jsonOutput).jsonObject
            json["websocket_port"]?.jsonPrimitive?.intOrNull
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isVersionCompatible(wrapperVersion: String): Boolean {
        // Simple version comparison
        return try {
            val current = wrapperVersion.split(".").map { it.toInt() }
            val minimum = MIN_WRAPPER_VERSION.split(".").map { it.toInt() }
            
            current[0] > minimum[0] || 
            (current[0] == minimum[0] && current[1] > minimum[1]) ||
            (current[0] == minimum[0] && current[1] == minimum[1] && current[2] >= minimum[2])
        } catch (e: Exception) {
            false
        }
    }
}
```

### Command Sanitizer

**Purpose**: Validates and sanitizes shell commands before execution to prevent security issues. Ensures commands stay within project boundaries and don't execute dangerous operations.

```kotlin
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandSanitizer @Inject constructor(
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    companion object {
        // Dangerous commands that should never be allowed
        private val BLOCKED_COMMANDS = setOf(
            "rm", "rmdir", "del", "format", "fdisk",
            "mkfs", "dd", "shred", "wipefs"
        )
        
        // Dangerous flags for common commands
        private val DANGEROUS_FLAGS = mapOf(
            "chmod" to setOf("-R", "777"),
            "chown" to setOf("-R"),
            "find" to setOf("-delete", "-exec"),
            "git" to setOf("push --force", "reset --hard HEAD~")
        )
        
        // Path traversal patterns
        private val PATH_TRAVERSAL_PATTERNS = listOf(
            "../",
            "..\\",
            "%2e%2e",
            "..%2f",
            "..%5c"
        )
    }
    
    data class SanitizationResult(
        val safe: Boolean,
        val sanitizedCommand: String?,
        val reason: String?
    )
    
    /**
     * Validate and sanitize a shell command
     */
    fun sanitizeCommand(
        command: String,
        projectPath: String,
        allowedPaths: List<String> = emptyList()
    ): SanitizationResult {
        // Check for empty command
        if (command.isBlank()) {
            return SanitizationResult(false, null, "Empty command")
        }
        
        // Check for blocked commands
        val baseCommand = extractBaseCommand(command)
        if (baseCommand in BLOCKED_COMMANDS) {
            securityAuditLogger.logBlockedCommand(command, "Dangerous command")
            return SanitizationResult(false, null, "Command '$baseCommand' is not allowed")
        }
        
        // Check for dangerous flags
        DANGEROUS_FLAGS.forEach { (cmd, flags) ->
            if (command.contains(cmd)) {
                flags.forEach { flag ->
                    if (command.contains(flag)) {
                        securityAuditLogger.logBlockedCommand(command, "Dangerous flag: $flag")
                        return SanitizationResult(false, null, "Dangerous flag '$flag' not allowed with $cmd")
                    }
                }
            }
        }
        
        // Check for path traversal
        if (containsPathTraversal(command)) {
            securityAuditLogger.logBlockedCommand(command, "Path traversal detected")
            return SanitizationResult(false, null, "Path traversal detected")
        }
        
        // Validate paths stay within project
        if (!validatePaths(command, projectPath, allowedPaths)) {
            securityAuditLogger.logBlockedCommand(command, "Path outside project")
            return SanitizationResult(false, null, "Command accesses paths outside project")
        }
        
        // Check for command injection
        if (containsCommandInjection(command)) {
            securityAuditLogger.logBlockedCommand(command, "Command injection detected")
            return SanitizationResult(false, null, "Command injection detected")
        }
        
        // Sanitize environment variables
        val sanitized = sanitizeEnvironmentVariables(command)
        
        return SanitizationResult(true, sanitized, null)
    }
    
    /**
     * Validate command scope stays within project
     */
    fun validateProjectScope(
        command: String,
        projectPath: String
    ): Boolean {
        // Extract file paths from command
        val paths = extractPaths(command)
        
        // Check each path
        return paths.all { path ->
            isPathWithinProject(path, projectPath)
        }
    }
    
    private fun extractBaseCommand(command: String): String {
        return command.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
    }
    
    private fun containsPathTraversal(command: String): Boolean {
        return PATH_TRAVERSAL_PATTERNS.any { pattern ->
            command.contains(pattern, ignoreCase = true)
        }
    }
    
    private fun containsCommandInjection(command: String): Boolean {
        // Check for command chaining characters
        val dangerousChars = listOf(";", "&&", "||", "|", "`", "$(", "${")
        return dangerousChars.any { char -> command.contains(char) }
    }
    
    private fun validatePaths(
        command: String,
        projectPath: String,
        allowedPaths: List<String>
    ): Boolean {
        val paths = extractPaths(command)
        return paths.all { path ->
            isPathWithinProject(path, projectPath) || 
            allowedPaths.any { allowed -> path.startsWith(allowed) }
        }
    }
    
    private fun extractPaths(command: String): List<String> {
        // Simple path extraction - in production use more sophisticated parsing
        val pathRegex = """(?:^|\s)([./~][^\s]+)""".toRegex()
        return pathRegex.findAll(command).map { it.groupValues[1] }.toList()
    }
    
    private fun isPathWithinProject(path: String, projectPath: String): Boolean {
        return try {
            val normalizedPath = File(path).canonicalPath
            val normalizedProject = File(projectPath).canonicalPath
            normalizedPath.startsWith(normalizedProject)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun sanitizeEnvironmentVariables(command: String): String {
        // Remove potentially dangerous environment variable expansions
        return command
            .replace("\$PATH", "\\$PATH")
            .replace("\$HOME", "\\$HOME")
            .replace("\$USER", "\\$USER")
    }
}

// Extension functions for SecurityAuditLogger (defined in security feature)
suspend fun SecurityAuditLogger.logBlockedCommand(command: String, reason: String) {
    logSecurityEvent(
        eventType = SecurityEventType.COMMAND_BLOCKED,
        details = """{"command":"$command","reason":"$reason"}""",
        success = false
    )
}

suspend fun SecurityAuditLogger.logSshConnection(hostname: String, success: Boolean, error: String? = null) {
    logSecurityEvent(
        eventType = SecurityEventType.SSH_CONNECTION,
        details = """{"hostname":"$hostname","error":"${error ?: ""}"}""",
        success = success
    )
}

suspend fun SecurityAuditLogger.logPermissionDecision(requestId: String, approved: Boolean, policyType: PermissionPolicyManager.PolicyType) {
    logSecurityEvent(
        eventType = SecurityEventType.PERMISSION_DECISION,
        details = """{"requestId":"$requestId","approved":$approved,"policy":"$policyType"}""",
        success = true
    )
}
```

### Connection Manager

**Purpose**: High-level connection orchestrator that manages the complete connection lifecycle for projects. Coordinates SSH tunnel establishment, WebSocket connections, and provides a unified interface for other features to interact with connections.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    private val sshTunnelManager: SshTunnelManager,
    private val webSocketManager: WebSocketManager,
    private val connectionStateManager: ConnectionStateManager,
    private val reconnectionManager: ReconnectionManager,
    private val projectRepository: ProjectRepository,
    private val scope: CoroutineScope
) {
    
    /**
     * Establish complete connection for a project
     */
    suspend fun connect(projectId: String): Result<Unit> {
        return try {
            val project = projectRepository.getProject(projectId).firstOrNull()
                ?: return Result.failure(IllegalArgumentException("Project not found"))
            
            // Update state
            connectionStateManager.updateState(projectId, ConnectionStateManager.ConnectionState.CONNECTING)
            
            // 1. Create SSH tunnel
            val tunnel = sshTunnelManager.createTunnel(
                projectId = projectId,
                serverProfile = project.serverProfile
            ).getOrThrow()
            
            // 2. Create WebSocket connection
            val connection = webSocketManager.createConnection(
                projectId = projectId,
                localPort = tunnel.localPort,
                path = "/ws"
            ).getOrThrow()
            
            // 3. Start reconnection monitoring
            reconnectionManager.startMonitoring(
                ReconnectionManager.ReconnectionConfig(
                    projectId = projectId,
                    onReconnect = { reconnect(projectId) }
                )
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            connectionStateManager.updateState(
                projectId, 
                ConnectionStateManager.ConnectionState.ERROR(e.message ?: "Connection failed")
            )
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect a project
     */
    suspend fun disconnect(projectId: String) {
        connectionStateManager.updateState(
            projectId,
            ConnectionStateManager.ConnectionState.DISCONNECTING("User requested")
        )
        
        reconnectionManager.stopMonitoring(projectId)
        webSocketManager.disconnect(projectId)
        sshTunnelManager.closeTunnel(projectId)
        
        connectionStateManager.updateState(
            projectId,
            ConnectionStateManager.ConnectionState.DISCONNECTED
        )
    }
    
    /**
     * Reconnect SSH tunnel
     */
    suspend fun reconnectSshTunnel(projectId: String): Result<Unit> {
        val project = projectRepository.getProject(projectId).firstOrNull()
            ?: return Result.failure(IllegalArgumentException("Project not found"))
        
        // Close existing tunnel
        sshTunnelManager.closeTunnel(projectId)
        
        // Create new tunnel
        return sshTunnelManager.createTunnel(
            projectId = projectId,
            serverProfile = project.serverProfile
        ).map { Unit }
    }
    
    /**
     * Reconnect WebSocket
     */
    suspend fun reconnectWebSocket(projectId: String): Result<Unit> {
        val tunnel = sshTunnelManager.getTunnel(projectId)
            ?: return Result.failure(IllegalStateException("No SSH tunnel found"))
        
        return webSocketManager.createConnection(
            projectId = projectId,
            localPort = tunnel.localPort,
            path = "/ws"
        ).map { Unit }
    }
    
    /**
     * Get project name
     */
    suspend fun getProjectName(projectId: String): String {
        return projectRepository.getProject(projectId).firstOrNull()?.name ?: "Unknown"
    }
    
    /**
     * Update connection status
     */
    fun updateConnectionStatus(projectId: String, status: ConnectionStatus) {
        val state = when (status) {
            ConnectionStatus.DISCONNECTED -> ConnectionStateManager.ConnectionState.DISCONNECTED
            ConnectionStatus.CONNECTING -> ConnectionStateManager.ConnectionState.CONNECTING
            ConnectionStatus.CONNECTED -> ConnectionStateManager.ConnectionState.CONNECTED
            ConnectionStatus.ERROR -> ConnectionStateManager.ConnectionState.ERROR("Status error")
            ConnectionStatus.DISCONNECTING -> ConnectionStateManager.ConnectionState.DISCONNECTING("Status change")
            ConnectionStatus.SHUTDOWN -> ConnectionStateManager.ConnectionState.DISCONNECTED
        }
        connectionStateManager.updateState(projectId, state)
    }
    
    /**
     * Retry connection
     */
    suspend fun retryConnection(projectName: String) {
        // In real implementation, map project name to ID
        val projectId = projectRepository.getProjectByName(projectName)?.id ?: return
        reconnect(projectId)
    }
    
    private suspend fun reconnect(projectId: String): Result<Unit> {
        return connect(projectId)
    }
}
```

### Background Service Manager

**Purpose**: Manages Android foreground service for persistent connection monitoring, handles notification display, respects Doze mode restrictions, and ensures proper service lifecycle management.

```kotlin
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

class ClaudeBackgroundService : Service() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "claude_background_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.pocket.agent.STOP_SERVICE"
        const val ACTION_HANDLE_PERMISSION = "com.pocket.agent.HANDLE_PERMISSION"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PERMISSION_ID = "permission_id"
        const val EXTRA_PERMISSION_APPROVED = "permission_approved"
    }
    
    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var connectionHealthMonitor: ConnectionHealthMonitor
    @Inject lateinit var permissionPolicyManager: PermissionPolicyManager
    @Inject lateinit var notificationBuilder: NotificationBuilder
    
    private val binder = LocalBinder()
    private var serviceScope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): ClaudeBackgroundService = this@ClaudeBackgroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        HiltAndroidApp.inject(this)
        
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HANDLE_PERMISSION -> {
                handlePermissionAction(intent)
            }
        }
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring
        startConnectionMonitoring()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        serviceScope?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Claude Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection to Claude Code instances"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ClaudeBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pocket Agent Active")
            .setContentText("Monitoring Claude Code connections")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }
    
    private fun startConnectionMonitoring() {
        serviceScope?.launch {
            // Monitor all active connections
            connectionManager.getActiveProjects().collect { projects ->
                projects.forEach { projectId ->
                    monitorProject(projectId)
                }
            }
        }
        
        // Start periodic health checks
        serviceScope?.launch {
            while (isActive) {
                performHealthChecks()
                delay(getHealthCheckInterval())
            }
        }
    }
    
    private suspend fun monitorProject(projectId: String) {
        // Monitor connection state
        connectionHealthMonitor.healthStatus.collect { healthMap ->
            healthMap[projectId]?.let { health ->
                if (!health.isHealthy) {
                    showConnectionIssueNotification(projectId)
                }
            }
        }
    }
    
    private fun handlePermissionAction(intent: Intent) {
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return
        val permissionId = intent.getStringExtra(EXTRA_PERMISSION_ID) ?: return
        val approved = intent.getBooleanExtra(EXTRA_PERMISSION_APPROVED, false)
        
        serviceScope?.launch {
            // Send permission response
            val response = MessageProtocol.PermissionResponse(
                requestId = permissionId,
                approved = approved,
                remember = false
            )
            connectionManager.sendMessage(projectId, response)
        }
    }
    
    private fun showPermissionNotification(
        projectId: String,
        request: MessageProtocol.PermissionRequest
    ) {
        val approveIntent = createPermissionIntent(projectId, request.id, true)
        val denyIntent = createPermissionIntent(projectId, request.id, false)
        
        val notification = notificationBuilder.buildPermissionNotification(
            request = request,
            approveIntent = approveIntent,
            denyIntent = denyIntent
        )
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(request.id.hashCode(), notification)
    }
    
    private fun createPermissionIntent(
        projectId: String,
        permissionId: String,
        approved: Boolean
    ): PendingIntent {
        val intent = Intent(this, ClaudeBackgroundService::class.java).apply {
            action = ACTION_HANDLE_PERMISSION
            putExtra(EXTRA_PROJECT_ID, projectId)
            putExtra(EXTRA_PERMISSION_ID, permissionId)
            putExtra(EXTRA_PERMISSION_APPROVED, approved)
        }
        
        return PendingIntent.getService(
            this,
            permissionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PocketAgent::BackgroundService"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes with timeout
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun getHealthCheckInterval(): Long {
        // Adjust based on Doze mode and battery
        val powerManager = getSystemService(PowerManager::class.java)
        return when {
            powerManager.isDeviceIdleMode -> 60000L // 1 minute in Doze
            powerManager.isPowerSaveMode -> 30000L // 30 seconds in battery saver
            else -> 5000L // 5 seconds normal
        }
    }
    
    private suspend fun performHealthChecks() {
        // Implementation depends on ConnectionHealthMonitor
    }
    
    private fun showConnectionIssueNotification(projectId: String) {
        // Show notification about connection issues
    }
}

@Singleton
class NotificationBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun buildPermissionNotification(
        request: MessageProtocol.PermissionRequest,
        approveIntent: PendingIntent,
        denyIntent: PendingIntent
    ): Notification {
        val color = when (request.risk) {
            MessageProtocol.RiskLevel.HIGH -> Color.RED
            MessageProtocol.RiskLevel.MEDIUM -> Color.YELLOW
            MessageProtocol.RiskLevel.LOW -> Color.GREEN
        }
        
        return NotificationCompat.Builder(context, ClaudeBackgroundService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Permission Request")
            .setContentText("${request.tool} wants to ${request.action}")
            .setSmallIcon(R.drawable.ic_permission)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, "Allow", approveIntent)
            .addAction(R.drawable.ic_close, "Deny", denyIntent)
            .setTimeoutAfter(30000) // 30 second timeout
            .build()
    }
}
```

### Error Handling

**Purpose**: Comprehensive error handling for all communication scenarios. Defines specific exception types for different failure modes (SSH, WebSocket, network). Includes network state observer for Android that monitors connectivity changes and provides reactive network state updates.

```kotlin
sealed class CommunicationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class SshConnectionException(message: String, cause: Throwable? = null) : CommunicationException(message, cause)
    class SshAuthenticationException(message: String) : CommunicationException(message)
    class TunnelCreationException(message: String, cause: Throwable? = null) : CommunicationException(message, cause)
    class WebSocketConnectionException(message: String, cause: Throwable? = null) : CommunicationException(message, cause)
    class MessageEncodingException(message: String, cause: Throwable? = null) : CommunicationException(message, cause)
    class MessageDecodingException(message: String, cause: Throwable? = null) : CommunicationException(message, cause)
    class NetworkUnavailableException : CommunicationException("Network is not available")
    class ConnectionTimeoutException(message: String) : CommunicationException(message)
}

/**
 * Network state observer for Android
 */
@Singleton
class NetworkStateObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    data class NetworkState(
        val isConnected: Boolean,
        val isMetered: Boolean,
        val isWifi: Boolean
    )
    
    private val _networkState = MutableStateFlow(getCurrentNetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkState.value = getCurrentNetworkState()
        }
        
        override fun onLost(network: Network) {
            _networkState.value = NetworkState(false, false, false)
        }
        
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _networkState.value = getCurrentNetworkState()
        }
    }
    
    private fun getCurrentNetworkState(): NetworkState {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        
        return NetworkState(
            isConnected = capabilities != null,
            isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        )
    }
    
    fun isNetworkAvailable(): Boolean = _networkState.value.isConnected
}
```

### Dependency Injection

**Purpose**: Hilt module configuration for dependency injection of all communication components. Provides singleton instances, configures OkHttp client with proper timeouts and certificate pinning, sets up coroutine scope for background operations, and wires all components together.

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommunicationModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificateValidator: CertificateValidator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .certificatePinner(certificateValidator.buildCertificatePinner())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideCommunicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    
    @Provides
    @Singleton
    fun provideSshTunnelManager(
        sshKeyImportManager: SshKeyImportManager,
        securityAuditLogger: SecurityAuditLogger,
        scope: CoroutineScope
    ): SshTunnelManager {
        return SshTunnelManager(sshKeyImportManager, securityAuditLogger, scope)
    }
    
    @Provides
    @Singleton
    fun provideWebSocketClient(
        okHttpClient: OkHttpClient,
        messageProtocol: MessageProtocol,
        connectionStateManager: ConnectionStateManager,
        messageQueueManager: MessageQueueManager,
        certificateValidator: CertificateValidator
    ): WebSocketClient {
        return WebSocketClient(
            okHttpClient,
            messageProtocol,
            connectionStateManager,
            messageQueueManager,
            certificateValidator
        )
    }
    
    @Provides
    @Singleton
    fun provideMessageHandlerRegistry(
        claudeResponseHandler: ClaudeResponseHandler,
        permissionRequestHandler: PermissionRequestHandler,
        sessionStatusHandler: SessionStatusHandler,
        wrapperHandshakeHandler: WrapperHandshakeHandler,
        pendingPermissionsHandler: PendingPermissionsHandler,
        progressUpdateHandler: ProgressUpdateHandler
    ): MessageHandlerRegistry {
        return MessageHandlerRegistry().apply {
            register(MessageProtocol.MessageType.CLAUDE_RESPONSE, claudeResponseHandler)
            register(MessageProtocol.MessageType.PERMISSION_REQUEST, permissionRequestHandler)
            register(MessageProtocol.MessageType.SESSION_STATUS, sessionStatusHandler)
            register(MessageProtocol.MessageType.WRAPPER_HANDSHAKE, wrapperHandshakeHandler)
            register(MessageProtocol.MessageType.PENDING_PERMISSIONS, pendingPermissionsHandler)
            register(MessageProtocol.MessageType.PROGRESS_UPDATE, progressUpdateHandler)
        }
    }
    
    @Provides
    @Singleton
    fun provideConnectionManager(
        sshTunnelManager: SshTunnelManager,
        webSocketClient: WebSocketClient,
        connectionStateManager: ConnectionStateManager,
        reconnectionManager: ReconnectionManager,
        connectionHealthMonitor: ConnectionHealthMonitor,
        wrapperServiceProtocol: WrapperServiceProtocol,
        sessionManager: SessionManager
    ): ConnectionManager {
        return ConnectionManager(
            sshTunnelManager,
            webSocketClient,
            connectionStateManager,
            reconnectionManager,
            connectionHealthMonitor,
            wrapperServiceProtocol,
            sessionManager
        )
    }
    
    @Provides
    @Singleton
    fun provideSessionManager(
        sessionRepository: SessionRepository,
        messageProtocol: MessageProtocol,
        webSocketClient: WebSocketClient,
        messageQueueManager: MessageQueueManager
    ): SessionManager {
        return SessionManager(
            sessionRepository,
            messageProtocol,
            webSocketClient,
            messageQueueManager
        )
    }
    
    @Provides
    @Singleton
    fun providePermissionPolicyManager(
        encryptedStorageManager: EncryptedStorageManager,
        securityAuditLogger: SecurityAuditLogger
    ): PermissionPolicyManager {
        return PermissionPolicyManager(
            encryptedStorageManager,
            securityAuditLogger
        )
    }
    
    @Provides
    @Singleton
    fun provideWrapperServiceProtocol(
        messageProtocol: MessageProtocol
    ): WrapperServiceProtocol {
        return WrapperServiceProtocol(messageProtocol)
    }
    
    @Provides
    @Singleton
    fun provideCommandSanitizer(
        securityAuditLogger: SecurityAuditLogger
    ): CommandSanitizer {
        return CommandSanitizer(securityAuditLogger)
    }
    
    @Provides
    @Singleton
    fun provideNotificationBuilder(
        @ApplicationContext context: Context
    ): NotificationBuilder {
        return NotificationBuilder(context)
    }
}
```

### Shared Service Interfaces

**Purpose**: Defines common service interfaces used across multiple features. These interfaces provide abstraction for cross-cutting concerns like preferences, analytics, and error reporting.

```kotlin
import kotlinx.coroutines.flow.Flow

// Preferences management interface
interface PreferencesManager {
    // Permission preferences
    fun getPermissionPolicies(): Map<String, PermissionPolicy>?
    suspend fun savePermissionPolicy(category: String, policy: PermissionPolicy)
    suspend fun clearPermissionPolicies()
    fun getDefaultTimeoutAction(): TimeoutAction
    suspend fun setDefaultTimeoutAction(action: TimeoutAction)
    
    // Connection preferences
    fun getAutoReconnectEnabled(): Boolean
    suspend fun setAutoReconnectEnabled(enabled: Boolean)
    fun getPollingFrequencyAdjustmentEnabled(): Boolean
    suspend fun setPollingFrequencyAdjustmentEnabled(enabled: Boolean)
    
    // UI preferences
    fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
    fun getHighContrastEnabled(): Boolean
    suspend fun setHighContrastEnabled(enabled: Boolean)
    
    // General preferences
    fun <T> getPreference(key: String, defaultValue: T): T
    suspend fun <T> setPreference(key: String, value: T)
    fun <T> observePreference(key: String, defaultValue: T): Flow<T>
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

// Analytics tracking interface
interface AnalyticsTracker {
    fun trackEvent(event: String, parameters: Map<String, Any> = emptyMap())
    fun trackError(event: String, throwable: Throwable)
    fun trackScreen(screenName: String)
    fun trackUserProperty(name: String, value: String)
    fun setUserId(userId: String?)
    
    // Predefined events
    fun trackConnectionEstablished(projectId: String, duration: Long)
    fun trackConnectionFailed(projectId: String, error: String)
    fun trackPermissionDecision(decision: String, category: String, automated: Boolean)
    fun trackQuickActionExecuted(actionType: String, source: String)
}

// Crash reporting interface
interface CrashReporter {
    fun initialize(context: Context)
    fun reportCrash(throwable: Throwable)
    fun reportNonFatal(throwable: Throwable)
    fun log(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String?)
    
    // Breadcrumbs for debugging
    fun addBreadcrumb(message: String, data: Map<String, Any> = emptyMap())
    fun clearBreadcrumbs()
}

// Security audit logging interface
interface SecurityAuditLogger {
    suspend fun logSecurityEvent(
        eventType: SecurityEventType,
        details: String,
        success: Boolean,
        userId: String? = null
    )
    
    suspend fun logSshConnection(
        hostname: String,
        username: String,
        success: Boolean,
        error: String? = null
    )
    
    suspend fun logPermissionRequest(
        tool: String,
        action: String,
        decision: String,
        automated: Boolean
    )
    
    suspend fun logCommandExecution(
        command: String,
        projectId: String,
        blocked: Boolean,
        reason: String? = null
    )
    
    fun getAuditLogs(limit: Int = 100): Flow<List<SecurityAuditLog>>
    suspend fun clearOldLogs(beforeTimestamp: Long)
}

enum class SecurityEventType {
    SSH_CONNECTION,
    PERMISSION_REQUEST,
    PERMISSION_DECISION,
    COMMAND_EXECUTION,
    COMMAND_BLOCKED,
    KEY_IMPORT,
    KEY_ACCESS,
    TOKEN_ACCESS,
    AUTHENTICATION_FAILURE,
    SESSION_START,
    SESSION_END
}

data class SecurityAuditLog(
    val id: String,
    val timestamp: Long,
    val eventType: SecurityEventType,
    val details: String,
    val success: Boolean,
    val userId: String?
)

// Encryption service interface (referenced by background services)
interface EncryptionService {
    suspend fun encrypt(data: ByteArray): ByteArray
    suspend fun decrypt(encryptedData: ByteArray): ByteArray
    suspend fun encryptString(plainText: String): String
    suspend fun decryptString(encryptedText: String): String
    fun generateSecureKey(): String
}
```

## Testing

### Connection Testing Checklist

**Purpose**: Comprehensive checklist ensuring all critical communication scenarios are tested, from basic connectivity to edge cases like network transitions and battery optimization impacts.

```kotlin
/**
 * Connection Testing Checklist:
 * 1. [ ] Test SSH tunnel establishment with valid credentials
 * 2. [ ] Test SSH authentication failure handling
 * 3. [ ] Test port forwarding setup and teardown
 * 4. [ ] Test WebSocket connection over SSH tunnel
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
 */
```

### Unit Tests

**Purpose**: Unit test examples demonstrating how to test message protocol encoding/decoding and reconnection backoff calculations. Shows proper test setup with mocks and assertions for core logic validation.

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
}

@RunWith(AndroidJUnit4::class)
class ReconnectionManagerTest {
    
    @Mock
    private lateinit var connectionStateManager: ConnectionStateManager
    
    @Mock
    private lateinit var networkStateObserver: NetworkStateObserver
    
    private lateinit var reconnectionManager: ReconnectionManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        reconnectionManager = ReconnectionManager(connectionStateManager, networkStateObserver)
    }
    
    @Test
    fun `calculate exponential backoff correctly`() = runTest {
        // Test backoff calculation
        val delays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)
        
        for (i in delays.indices) {
            val delay = reconnectionManager.calculateBackoffDelay(i)
            assertThat(delay).isEqualTo(delays[i])
        }
    }
}
```

### Integration Tests

**Purpose**: Integration test example showing how to test WebSocket communication using MockWebServer. Demonstrates full connection establishment, message exchange, and async message collection in a controlled test environment.

```kotlin
@RunWith(AndroidJUnit4::class)
class WebSocketIntegrationTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var messageProtocol: MessageProtocol
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        messageProtocol = MessageProtocol()
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
            
        webSocketClient = WebSocketClient(
            okHttpClient,
            messageProtocol,
            ConnectionStateManager(),
            MessageQueueManager(),
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
        val port = mockWebServer.port
        
        // Setup mock WebSocket response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send test message
                val testMessage = MessageProtocol.ClaudeResponse(
                    id = "test-1",
                    timestamp = System.currentTimeMillis(),
                    content = "Hello from Claude",
                    conversationId = projectId
                )
                webSocket.send(messageProtocol.encodeMessage(testMessage))
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                // Echo back
                webSocket.send(text)
            }
        }))
        
        // When
        webSocketClient.connect(projectId, port, "/")
        
        // Collect incoming messages
        val messages = mutableListOf<MessageProtocol.Message>()
        val job = launch {
            webSocketClient.incomingMessages.collect { message ->
                messages.add(message)
            }
        }
        
        // Send a message
        webSocketClient.sendMessage(
            MessageProtocol.CommandMessage(command = "test command")
        )
        
        // Wait for messages
        delay(1000)
        
        // Then
        assertThat(messages).hasSize(2) // Initial message + echo
        assertThat(messages[0]).isInstanceOf(MessageProtocol.ClaudeResponse::class.java)
        
        job.cancel()
    }
}
```

## Implementation Notes (Android Mobile)

### Connection Lifecycle

**Purpose**: High-level connection manager orchestrating the complete connection lifecycle. Shows how all components work together to establish, maintain, and gracefully close connections. Includes reconnection handling and resource cleanup.

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection lifecycle for a project:
 * 
 * 1. User initiates connection
 * 2. Establish SSH tunnel to server
 * 3. Get local port from tunnel
 * 4. Connect WebSocket through tunnel
 * 5. Exchange initial handshake
 * 6. Start health monitoring
 * 7. Process messages bidirectionally
 * 8. Handle disconnections gracefully
 * 9. Cleanup resources on shutdown
 */

@Singleton
class ConnectionManager @Inject constructor(
    private val sshTunnelManager: SshTunnelManager,
    private val webSocketClient: WebSocketClient,
    private val connectionStateManager: ConnectionStateManager,
    private val reconnectionManager: ReconnectionManager,
    private val connectionHealthMonitor: ConnectionHealthMonitor,
    private val wrapperServiceProtocol: WrapperServiceProtocol,
    private val sessionManager: SessionManager
) {
    
    suspend fun connect(
        projectId: String,
        serverProfile: ServerProfileEntity,
        sshIdentity: SshIdentityEntity,
        projectPath: String
    ): Result<Unit> {
        return try {
            // 1. Establish SSH tunnel
            val tunnelResult = sshTunnelManager.establishTunnel(
                serverProfile = serverProfile,
                sshIdentity = sshIdentity,
                remotePort = 0, // Let wrapper choose port
                projectId = projectId
            )
            
            val sshSession = sshTunnelManager.getSession(projectId)
                ?: return Result.failure(Exception("SSH session not found"))
            
            // 2. Check wrapper status
            val wrapperInfo = wrapperServiceProtocol.checkWrapperStatus(sshSession)
            
            // 3. Install/start wrapper if needed
            val websocketPort = if (!wrapperInfo.running) {
                if (!wrapperInfo.installed) {
                    wrapperServiceProtocol.installWrapper(sshSession).getOrThrow()
                }
                wrapperServiceProtocol.startWrapper(sshSession, projectPath).getOrThrow()
            } else {
                wrapperInfo.websocketPort ?: wrapperServiceProtocol.discoverWebSocketPort(sshSession)
                    ?: return Result.failure(Exception("Could not discover WebSocket port"))
            }
            
            // 4. Update tunnel with correct port
            val localPort = sshTunnelManager.updatePortForwarding(
                projectId = projectId,
                remotePort = websocketPort
            ).getOrThrow()
            
            // 5. Connect WebSocket
            webSocketClient.connect(projectId, localPort)
            
            // 6. Wait for handshake
            val handshakeResult = wrapperServiceProtocol.performHandshake(
                webSocket = webSocketClient.getWebSocket(),
                projectId = projectId
            )
            
            if (!handshakeResult.success) {
                return Result.failure(Exception("Handshake failed"))
            }
            
            // 7. Resume session if exists
            sessionManager.resumeSession(
                sessionId = handshakeResult.sessionId!!,
                projectId = projectId
            )
            
            // 8. Start health monitoring
            connectionHealthMonitor.startMonitoring(projectId)
            
            // 9. Setup auto-reconnection
            reconnectionManager.startMonitoring(
                ReconnectionManager.ReconnectionConfig(
                    projectId = projectId,
                    onReconnect = {
                        reconnect(projectId, serverProfile, sshIdentity, projectPath)
                    }
                )
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun disconnect(projectId: String) {
        // Stop monitoring
        reconnectionManager.stopMonitoring(projectId)
        connectionHealthMonitor.stopMonitoring(projectId)
        
        // Disconnect WebSocket
        webSocketClient.disconnect()
        
        // Close SSH tunnel
        sshTunnelManager.closeTunnel(projectId)
        
        // Clear state
        connectionStateManager.clearState(projectId)
    }
    
    private suspend fun reconnect(
        projectId: String,
        serverProfile: ServerProfileEntity,
        sshIdentity: SshIdentityEntity,
        projectPath: String
    ): Result<Unit> {
        // Close existing connections
        webSocketClient.disconnect()
        sshTunnelManager.closeTunnel(projectId)
        
        // Re-establish connection
        return connect(projectId, serverProfile, sshIdentity, projectPath)
    }
    
    /**
     * Get flow of active project IDs
     */
    fun getActiveProjects(): Flow<List<String>> {
        return connectionStateManager.connectionStates.map { states ->
            states.filter { (_, state) -> 
                state == ConnectionStateManager.ConnectionState.CONNECTED ||
                state == ConnectionStateManager.ConnectionState.CONNECTING
            }.keys.toList()
        }
    }
    
    /**
     * Send message to specific project
     */
    suspend fun sendMessage(projectId: String, message: MessageProtocol.Message) {
        webSocketClient.sendMessage(message)
    }
}
```

### Performance Considerations (Android-Specific)

- **Thread Management**: All network operations on IO dispatcher
- **Message Batching**: Combine multiple small messages when possible
- **Compression**: Enable compression for large messages
- **Connection Pooling**: Reuse SSH sessions when reconnecting
- **Memory Management**: Limit message queue sizes

### Network Optimization

**Purpose**: Example of network-aware message handling that prioritizes messages based on network type (metered vs unmetered). Demonstrates how to optimize data usage on cellular connections by queuing non-priority messages.

```kotlin
class OptimizedWebSocketClient : WebSocketClient() {
    
    override suspend fun sendMessage(message: MessageProtocol.Message) {
        // Check network type
        val networkState = networkStateObserver.networkState.value
        
        if (networkState.isMetered && !message.isPriority()) {
            // Queue non-priority messages on metered connections
            messageQueueManager.enqueueMessage(projectId, message)
        } else {
            super.sendMessage(message)
        }
    }
    
    private fun MessageProtocol.Message.isPriority(): Boolean {
        return when (this) {
            is MessageProtocol.PermissionResponse -> true
            is MessageProtocol.HeartbeatMessage -> true
            else -> false
        }
    }
}
```

### Battery Optimization

- **Adaptive Polling**: Reduce health check frequency on low battery
- **Message Coalescing**: Batch messages to reduce radio wake-ups
- **Doze Mode**: Handle Doze mode restrictions properly
- **Background Limits**: Respect Android background execution limits

### Package Structure

```
communication/
├── ssh/
│   ├── SshTunnelManager.kt
│   ├── SshConfig.kt
│   └── PortForwardingManager.kt
├── websocket/
│   ├── WebSocketClient.kt
│   ├── WebSocketConfig.kt
│   └── MessageDispatcher.kt
├── protocol/
│   ├── MessageProtocol.kt
│   ├── MessageSerializer.kt
│   └── MessageTypes.kt
├── state/
│   ├── ConnectionStateManager.kt
│   ├── ConnectionState.kt
│   └── StateTransitions.kt
├── queue/
│   ├── MessageQueueManager.kt
│   ├── PersistentQueue.kt
│   └── QueuePolicy.kt
├── reconnection/
│   ├── ReconnectionManager.kt
│   ├── BackoffStrategy.kt
│   └── NetworkObserver.kt
├── health/
│   ├── ConnectionHealthMonitor.kt
│   ├── HealthMetrics.kt
│   └── DiagnosticsCollector.kt
├── handlers/
│   ├── MessageHandlerRegistry.kt
│   ├── ClaudeResponseHandler.kt
│   ├── PermissionRequestHandler.kt
│   └── SessionStatusHandler.kt
├── exception/
│   └── CommunicationException.kt
└── di/
    └── CommunicationModule.kt
```

### Future Extensions (Android Mobile Focus)

- **Multi-Protocol Support**: Add support for alternative protocols (gRPC, MQTT)
- **Compression Algorithms**: Implement adaptive compression based on content
- **Connection Multiplexing**: Share SSH tunnels between multiple WebSockets
- **Offline Mode**: Enhanced offline queueing with persistence
- **P2P Communication**: Direct device-to-device communication
- **Push Notifications**: FCM integration for wake-up events
- **Connection Metrics**: Detailed analytics and performance monitoring
- **Protocol Versioning**: Support for multiple protocol versions
- **End-to-End Encryption**: Additional encryption layer over WebSocket
- **Traffic Shaping**: Bandwidth management and QoS