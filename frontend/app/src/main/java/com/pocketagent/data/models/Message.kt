package com.pocketagent.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a message in the Claude Code conversation.
 *
 * Messages are exchanged between the mobile app and Claude Code instances,
 * including user prompts, Claude responses, system messages, and error messages.
 * Messages are stored per project to maintain conversation context.
 *
 * @property id Unique identifier for the message
 * @property content Message content text
 * @property type Type of message (user, claude, system, error, status)
 * @property timestamp Timestamp when the message was created
 * @property isPartial Whether this is a partial message (streaming response)
 * @property metadata Additional message metadata (tool requests, permissions, etc.)
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MAX_CONTENT_LENGTH = 50_000
        const val MAX_METADATA_ENTRIES = 20
        const val MAX_METADATA_KEY_LENGTH = 100
        const val MAX_METADATA_VALUE_LENGTH = 1_000
        const val DEFAULT_PREVIEW_LENGTH = 100
        const val MINUTE_IN_MILLIS = 60 * 1000L
        const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

        // Display priorities for MessageType
        const val PRIORITY_ERROR = 1
        const val PRIORITY_STATUS = 2
        const val PRIORITY_SYSTEM = 3
        const val PRIORITY_USER = 4
        const val PRIORITY_CLAUDE = 5
    }

    init {
        require(content.isNotBlank()) { "Message content cannot be blank" }
        require(content.length <= MAX_CONTENT_LENGTH) { "Message content too long (max $MAX_CONTENT_LENGTH chars)" }
        require(timestamp > 0) { "Timestamp must be positive" }
        require(metadata.size <= MAX_METADATA_ENTRIES) { "Too many metadata entries (max $MAX_METADATA_ENTRIES)" }
        metadata.forEach { (key, value) ->
            require(key.isNotBlank()) { "Metadata key cannot be blank" }
            require(key.length <= MAX_METADATA_KEY_LENGTH) { "Metadata key too long (max $MAX_METADATA_KEY_LENGTH chars)" }
            require(value.length <= MAX_METADATA_VALUE_LENGTH) { "Metadata value too long (max $MAX_METADATA_VALUE_LENGTH chars)" }
        }
    }

    /**
     * Check if this message is from the user.
     */
    fun isFromUser(): Boolean = type == MessageType.USER_INPUT

    /**
     * Check if this message is from Claude.
     */
    fun isFromClaude(): Boolean = type == MessageType.CLAUDE_RESPONSE

    /**
     * Check if this message is a system message.
     */
    fun isSystemMessage(): Boolean = type == MessageType.SYSTEM_MESSAGE

    /**
     * Check if this message is an error message.
     */
    fun isErrorMessage(): Boolean = type == MessageType.ERROR_MESSAGE

    /**
     * Check if this message is a status update.
     */
    fun isStatusUpdate(): Boolean = type == MessageType.STATUS_UPDATE

    /**
     * Check if this message has tool requests.
     */
    fun hasToolRequests(): Boolean = metadata.containsKey("tool_requests")

    /**
     * Check if this message requires permission.
     */
    fun requiresPermission(): Boolean = metadata["permission_required"] == "true"

    /**
     * Get the execution time if available.
     */
    fun getExecutionTime(): Long? = metadata["execution_time"]?.toLongOrNull()

    /**
     * Get tool requests if available.
     */
    fun getToolRequests(): List<String> = metadata["tool_requests"]?.split(",")?.map { it.trim() } ?: emptyList()

    /**
     * Get a truncated version of the content for display.
     */
    fun getPreviewContent(maxLength: Int = DEFAULT_PREVIEW_LENGTH): String =
        if (content.length <=
            maxLength
        ) {
            content
        } else {
            content.take(maxLength) + "..."
        }

    /**
     * Get the age of the message in minutes.
     */
    fun getAgeInMinutes(): Long {
        val now = System.currentTimeMillis()
        return (now - timestamp) / MINUTE_IN_MILLIS
    }
}

/**
 * Represents the type of message in the conversation.
 *
 * Message types help categorize and display messages appropriately in the UI,
 * with different styling and behavior for each type.
 */
@Serializable
enum class MessageType {
    /** User input message */
    USER_INPUT,

    /** Claude response message */
    CLAUDE_RESPONSE,

    /** System message (connection, session info, etc.) */
    SYSTEM_MESSAGE,

    /** Error message */
    ERROR_MESSAGE,

    /** Status update message */
    STATUS_UPDATE,

    ;

    /**
     * Check if this type represents a user message.
     */
    fun isUserMessage(): Boolean = this == USER_INPUT

    /**
     * Check if this type represents a Claude message.
     */
    fun isClaudeMessage(): Boolean = this == CLAUDE_RESPONSE

    /**
     * Check if this type represents a system message.
     */
    fun isSystemMessage(): Boolean = this == SYSTEM_MESSAGE

    /**
     * Check if this type represents an error message.
     */
    fun isErrorMessage(): Boolean = this == ERROR_MESSAGE

    /**
     * Check if this type represents a status update.
     */
    fun isStatusUpdate(): Boolean = this == STATUS_UPDATE

    /**
     * Get a user-friendly description of the message type.
     */
    fun getDescription(): String =
        when (this) {
            USER_INPUT -> "User"
            CLAUDE_RESPONSE -> "Claude"
            SYSTEM_MESSAGE -> "System"
            ERROR_MESSAGE -> "Error"
            STATUS_UPDATE -> "Status"
        }

    /**
     * Get the display priority (lower number = higher priority).
     */
    fun getDisplayPriority(): Int =
        when (this) {
            ERROR_MESSAGE -> Message.PRIORITY_ERROR
            STATUS_UPDATE -> Message.PRIORITY_STATUS
            SYSTEM_MESSAGE -> Message.PRIORITY_SYSTEM
            USER_INPUT -> Message.PRIORITY_USER
            CLAUDE_RESPONSE -> Message.PRIORITY_CLAUDE
        }
}

/**
 * Builder class for creating Message instances in tests.
 *
 * This builder provides a fluent interface for constructing Message objects
 * with specific configurations for testing scenarios.
 */
class MessageBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var content: String = "Test message content"
    private var type: MessageType = MessageType.USER_INPUT
    private var timestamp: Long = System.currentTimeMillis()
    private var isPartial: Boolean = false
    private var metadata: MutableMap<String, String> = mutableMapOf()

    fun id(id: String) = apply { this.id = id }

    fun content(content: String) = apply { this.content = content }

    fun type(type: MessageType) = apply { this.type = type }

    fun timestamp(timestamp: Long) = apply { this.timestamp = timestamp }

    fun isPartial(isPartial: Boolean) = apply { this.isPartial = isPartial }

    fun metadata(metadata: Map<String, String>) = apply { this.metadata = metadata.toMutableMap() }

    fun addMetadata(
        key: String,
        value: String,
    ) = apply { this.metadata[key] = value }

    fun build(): Message =
        Message(
            id = id,
            content = content,
            type = type,
            timestamp = timestamp,
            isPartial = isPartial,
            metadata = metadata.toMap(),
        )
}

// ================================
// Extension functions for Message operations
// ================================

/**
 * Create a copy with updated content.
 */
fun Message.withContent(content: String): Message = copy(content = content)

/**
 * Create a copy with updated partial status.
 */
fun Message.withPartialStatus(isPartial: Boolean): Message = copy(isPartial = isPartial)

/**
 * Create a copy with additional metadata.
 */
fun Message.withMetadata(
    key: String,
    value: String,
): Message = copy(metadata = metadata + (key to value))

/**
 * Create a copy with updated metadata.
 */
fun Message.withMetadata(newMetadata: Map<String, String>): Message = copy(metadata = newMetadata)

/**
 * Create a copy with tool requests metadata.
 */
fun Message.withToolRequests(toolRequests: List<String>): Message =
    copy(metadata = metadata + ("tool_requests" to toolRequests.joinToString(",")))

/**
 * Create a copy with permission requirement.
 */
fun Message.withPermissionRequired(required: Boolean): Message = copy(metadata = metadata + ("permission_required" to required.toString()))

/**
 * Create a copy with execution time.
 */
fun Message.withExecutionTime(executionTime: Long): Message = copy(metadata = metadata + ("execution_time" to executionTime.toString()))

/**
 * Check if the message matches the search query.
 */
fun Message.matchesSearch(query: String): Boolean = content.contains(query, ignoreCase = true)

/**
 * Get the message formatted for display.
 */
fun Message.getFormattedContent(): String =
    when (type) {
        MessageType.USER_INPUT -> content
        MessageType.CLAUDE_RESPONSE -> content
        MessageType.SYSTEM_MESSAGE -> "[System] $content"
        MessageType.ERROR_MESSAGE -> "[Error] $content"
        MessageType.STATUS_UPDATE -> "[Status] $content"
    }

/**
 * Create a reply message.
 */
fun Message.createReply(
    content: String,
    type: MessageType = MessageType.CLAUDE_RESPONSE,
): Message =
    MessageBuilder()
        .content(content)
        .type(type)
        .build()

/**
 * Create a copy for export (with minimal metadata).
 */
fun Message.toExportModel(): MessageExport =
    MessageExport(
        id = id,
        content = content,
        type = type,
        timestamp = timestamp,
        isPartial = isPartial,
    )

/**
 * Export model for Message (without sensitive metadata).
 */
@Serializable
data class MessageExport(
    val id: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long,
    val isPartial: Boolean,
)

/**
 * Validation utilities for Message.
 */
object MessageValidator {
    /**
     * Validate message content.
     */
    fun validateContent(content: String): Result<Unit> =
        when {
            content.isBlank() -> Result.failure(IllegalArgumentException("Content cannot be blank"))
            content.length > Message.MAX_CONTENT_LENGTH ->
                Result.failure(
                    IllegalArgumentException("Content too long (max ${Message.MAX_CONTENT_LENGTH} chars)"),
                )
            else -> Result.success(Unit)
        }

    /**
     * Validate metadata.
     */
    fun validateMetadata(metadata: Map<String, String>): Result<Unit> {
        return when {
            metadata.size > Message.MAX_METADATA_ENTRIES ->
                Result.failure(
                    IllegalArgumentException("Too many metadata entries (max ${Message.MAX_METADATA_ENTRIES})"),
                )
            else -> {
                metadata.forEach { (key, value) ->
                    if (key.isBlank()) {
                        return Result.failure(IllegalArgumentException("Metadata key cannot be blank"))
                    }
                    if (key.length > Message.MAX_METADATA_KEY_LENGTH) {
                        return Result.failure(
                            IllegalArgumentException("Metadata key too long (max ${Message.MAX_METADATA_KEY_LENGTH} chars)"),
                        )
                    }
                    if (value.length > Message.MAX_METADATA_VALUE_LENGTH) {
                        return Result.failure(
                            IllegalArgumentException("Metadata value too long (max ${Message.MAX_METADATA_VALUE_LENGTH} chars)"),
                        )
                    }
                }
                Result.success(Unit)
            }
        }
    }
}

/**
 * Common message factory methods.
 */
object MessageFactory {
    /**
     * Create a user input message.
     */
    fun createUserInput(content: String): Message =
        MessageBuilder()
            .content(content)
            .type(MessageType.USER_INPUT)
            .build()

    /**
     * Create a Claude response message.
     */
    fun createClaudeResponse(
        content: String,
        isPartial: Boolean = false,
    ): Message =
        MessageBuilder()
            .content(content)
            .type(MessageType.CLAUDE_RESPONSE)
            .isPartial(isPartial)
            .build()

    /**
     * Create a system message.
     */
    fun createSystemMessage(content: String): Message =
        MessageBuilder()
            .content(content)
            .type(MessageType.SYSTEM_MESSAGE)
            .build()

    /**
     * Create an error message.
     */
    fun createErrorMessage(content: String): Message =
        MessageBuilder()
            .content(content)
            .type(MessageType.ERROR_MESSAGE)
            .build()

    /**
     * Create a status update message.
     */
    fun createStatusUpdate(content: String): Message =
        MessageBuilder()
            .content(content)
            .type(MessageType.STATUS_UPDATE)
            .build()

    /**
     * Create a sample conversation for testing.
     */
    fun createSampleConversation(): List<Message> =
        listOf(
            createUserInput("Hello, can you help me with my project?"),
            createClaudeResponse("Hello! I'd be happy to help you with your project. What would you like to work on?"),
            createUserInput("I need to create a new React component"),
            createClaudeResponse("I can help you create a React component. What kind of component do you need?"),
            createSystemMessage("Session started successfully"),
            createStatusUpdate("Connected to development server"),
        )

    /**
     * Create multiple sample messages.
     */
    fun createSamples(
        count: Int,
        type: MessageType = MessageType.USER_INPUT,
    ): List<Message> =
        (1..count).map { index ->
            MessageBuilder()
                .content("Sample message $index")
                .type(type)
                .build()
        }
}
