# Communication Layer Feature Specification - Messages & Protocol
**For Android Mobile Application**

> **Navigation**: [Overview](./communication-layer-overview.feat.md) | [WebSocket](./communication-layer-websocket.feat.md) | [Authentication](./communication-layer-authentication.feat.md) | **Messages** | [Testing](./communication-layer-testing.feat.md) | [Index](./communication-layer-index.md)

## Message Protocol

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
        abstract val type: ProtocolMessageType
    }
    
    enum class ProtocolMessageType {
        COMMAND,
        AGENT_RESPONSE,
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
        override val type: ProtocolMessageType = ProtocolMessageType.COMMAND,
        val command: String,
        val isShellCommand: Boolean = false
    ) : Message()
    
    @Serializable
    @SerialName("permission_response")
    data class PermissionResponse(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: ProtocolMessageType = ProtocolMessageType.PERMISSION_RESPONSE,
        val requestId: String,
        val approved: Boolean,
        val remember: Boolean = false
    ) : Message()
    
    @Serializable
    @SerialName("heartbeat")
    data class HeartbeatMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: ProtocolMessageType = ProtocolMessageType.HEARTBEAT
    ) : Message()
    
    @Serializable
    @SerialName("session_resume")
    data class SessionResumeMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: ProtocolMessageType = ProtocolMessageType.SESSION_RESUME,
        val sessionId: String,
        val lastMessageId: String,
        val lastMessageTimestamp: Long
    ) : Message()
    
    // Incoming messages (Wrapper → Mobile)
    
    @Serializable
    @SerialName("agent_response")
    data class AgentResponse(
        override val id: String,
        override val timestamp: Long,
        override val type: ProtocolMessageType = ProtocolMessageType.AGENT_RESPONSE,
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
        override val type: ProtocolMessageType = ProtocolMessageType.PERMISSION_REQUEST,
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
        override val type: ProtocolMessageType = ProtocolMessageType.SESSION_STATUS,
        val status: Status,
        val progress: Progress? = null
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
        override val type: ProtocolMessageType = ProtocolMessageType.ERROR,
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
        override val type: ProtocolMessageType = ProtocolMessageType.WRAPPER_STATUS,
        val version: String,
        val agentStatus: AgentStatus,
        val resourceUsage: ResourceUsage
    ) : Message() {
        
        @Serializable
        data class AgentStatus(
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
        override val type: ProtocolMessageType = ProtocolMessageType.SESSION_SYNC,
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
        override val type: ProtocolMessageType = ProtocolMessageType.PENDING_PERMISSIONS,
        val permissions: List<PermissionRequest>
    ) : Message()
    
    @Serializable
    @SerialName("health_check")
    data class HealthCheckMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: ProtocolMessageType = ProtocolMessageType.HEARTBEAT
    ) : Message()
    
    @Serializable
    @SerialName("health_check_response")
    data class HealthCheckResponse(
        override val id: String,
        override val timestamp: Long,
        override val type: ProtocolMessageType = ProtocolMessageType.HEARTBEAT,
        val requestId: String,
        val healthy: Boolean,
        val wrapperVersion: String,
        val agentStatus: String
    ) : Message()
    
    @Serializable
    @SerialName("session_control")
    data class SessionControlMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: ProtocolMessageType = ProtocolMessageType.SESSION_STATUS,
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
        override val type: ProtocolMessageType = ProtocolMessageType.WRAPPER_HANDSHAKE,
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
        override val type: ProtocolMessageType = ProtocolMessageType.PROGRESS_UPDATE,
        val operationId: String,
        val isWorking: Boolean,
        val statusMessage: String? = null
    ) : Message()
    
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
            subclass(AgentResponse::class, AgentResponse.serializer())
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

## Shared Models

**Purpose**: Common data models and enums used across the communication layer and other features. These models ensure consistent data representation throughout the application.

```kotlin
// Connection status enum used by background services and UI
enum class ConnectionStatus {
    DISCONNECTED,      // No active connection
    CONNECTING,        // Establishing connection
    CONNECTED,         // Active connection
    ERROR,            // Connection error
    DISCONNECTING,    // Closing connection
    SHUTDOWN          // Agent shutdown
}

// Chat message representation with detailed message types
data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val type: ChatMessageType,
    val conversationId: String? = null,
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val uiData: MessageUIData? = null
) {
    enum class ChatMessageType {
        USER_INPUT,             // User text input
        AGENT_MESSAGE,          // Agent's response
        SYSTEM_MESSAGE,         // System notifications
        ERROR_MESSAGE,          // Error notifications
        STATUS_UPDATE,          // General status updates
        PERMISSION_REQUEST,     // Interactive permission card
        CODE_BLOCK,            // Code snippet with syntax highlighting
        TASK_COMPLETION,       // Task summary with results
        TYPING_INDICATOR,      // Agent is thinking
        FILE_REFERENCE,        // File path with preview
        COMMAND_EXECUTION      // Shell command or quick action result
    }
    
    // Additional UI data for rich message rendering
    data class MessageUIData(
        val backgroundColor: String? = null,
        val icon: String? = null,
        val actions: List<MessageAction> = emptyList(),
        val codeData: CodeBlockData? = null,
        val commandData: CommandExecutionData? = null,
        val permissionData: PermissionRequestData? = null,
        val fileReferenceData: FileReferenceData? = null
    )
    
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

// Chat manager interface
interface ChatManager {
    suspend fun sendMessage(projectId: String, message: String): Result<String>
    suspend fun getLastMessage(projectId: String): ChatMessage?
    suspend fun getConversationHistory(projectId: String, limit: Int = 10): List<ChatMessage>
    fun observeMessages(projectId: String): Flow<ChatMessage>
    suspend fun clearChat(projectId: String): Result<Unit>
}
```

## Message Handlers

**Purpose**: Registry pattern for handling different message types. Each handler processes specific message types (agent responses, permission requests, status updates) with appropriate actions like database storage, notification display, or UI updates. Extensible for new message types.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHandlerRegistry @Inject constructor() {
    
    private val handlers = mutableMapOf<MessageProtocol.ProtocolMessageType, MessageHandler>()
    
    interface MessageHandler {
        suspend fun handle(message: MessageProtocol.Message)
    }
    
    /**
     * Register a handler for a message type
     */
    fun register(type: MessageProtocol.ProtocolMessageType, handler: MessageHandler) {
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
class AgentResponseHandler @Inject constructor(
    private val messageRepository: MessageRepository
) : MessageHandlerRegistry.MessageHandler {
    
    override suspend fun handle(message: MessageProtocol.Message) {
        if (message is MessageProtocol.AgentResponse) {
            // Store in local database
            messageRepository.insertMessage(
                MessageEntity(
                    id = message.id,
                    projectId = message.conversationId ?: "",
                    content = message.content,
                    isFromAgent = true,
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
            progressTracker.updateOperationStatus(
                operationId = message.operationId,
                isWorking = message.isWorking,
                statusMessage = message.statusMessage
            )
        }
    }
}
```