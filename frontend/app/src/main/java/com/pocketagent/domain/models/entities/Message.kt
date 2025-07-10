package com.pocketagent.domain.models.entities

import com.pocketagent.domain.models.error.ValidationException
import java.util.UUID

/**
 * Represents a message in the Claude Code conversation.
 * 
 * Messages are exchanged between the mobile app and Claude Code instances,
 * including user prompts, Claude responses, and system messages.
 * 
 * @property id Unique identifier for the message
 * @property content Message content
 * @property type Type of message (user, claude, system)
 * @property timestamp When the message was created
 * @property isPartial Whether this message is partial (streaming)
 * @property metadata Additional message metadata
 * @property attachments List of attachments in the message
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<MessageAttachment> = emptyList()
) {
    init {
        validateContent(content)
    }
    
    /**
     * Marks the message as complete (not partial).
     */
    fun markAsComplete(): Message = copy(isPartial = false)
    
    /**
     * Adds an attachment to the message.
     */
    fun addAttachment(attachment: MessageAttachment): Message = copy(
        attachments = attachments + attachment
    )
    
    /**
     * Removes an attachment from the message.
     */
    fun removeAttachment(attachmentId: String): Message = copy(
        attachments = attachments.filter { it.id != attachmentId }
    )
    
    /**
     * Updates the message content.
     */
    fun updateContent(newContent: String): Message = copy(content = newContent)
    
    /**
     * Adds metadata to the message.
     */
    fun addMetadata(key: String, value: String): Message = copy(
        metadata = metadata + (key to value)
    )
    
    /**
     * Removes metadata from the message.
     */
    fun removeMetadata(key: String): Message = copy(
        metadata = metadata - key
    )
    
    /**
     * Gets the message preview (first 100 characters).
     */
    fun getPreview(): String = if (content.length <= 100) content else "${content.take(100)}..."
    
    /**
     * Checks if the message is from the user.
     */
    fun isFromUser(): Boolean = type == MessageType.USER_INPUT
    
    /**
     * Checks if the message is from Claude.
     */
    fun isFromClaude(): Boolean = type == MessageType.CLAUDE_RESPONSE
    
    /**
     * Checks if the message is a system message.
     */
    fun isSystemMessage(): Boolean = type == MessageType.SYSTEM_MESSAGE
    
    /**
     * Checks if the message is an error message.
     */
    fun isErrorMessage(): Boolean = type == MessageType.ERROR_MESSAGE
    
    /**
     * Checks if the message requires permission.
     */
    fun requiresPermission(): Boolean = type == MessageType.PERMISSION_REQUEST
    
    /**
     * Gets the message size in bytes.
     */
    fun getSize(): Int = content.toByteArray().size + attachments.sumOf { it.size.toInt() }
    
    companion object {
        const val MAX_CONTENT_LENGTH = 100_000 // 100KB
        
        private fun validateContent(content: String) {
            if (content.length > MAX_CONTENT_LENGTH) {
                throw ValidationException("content", content, "Message content too long (max $MAX_CONTENT_LENGTH chars)")
            }
        }
    }
}

/**
 * Represents the type of message.
 */
enum class MessageType {
    USER_INPUT,
    CLAUDE_RESPONSE,
    SYSTEM_MESSAGE,
    ERROR_MESSAGE,
    STATUS_UPDATE,
    PERMISSION_REQUEST,
    PERMISSION_RESPONSE
}

/**
 * Message attachment model.
 * 
 * @property id Unique identifier for the attachment
 * @property type Type of attachment
 * @property name Name of the attachment
 * @property content Content of the attachment
 * @property size Size of the attachment in bytes
 * @property mimeType MIME type of the attachment
 * @property metadata Additional metadata for the attachment
 */
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val content: String,
    val size: Long = 0,
    val mimeType: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        validateName(name)
        validateContent(content)
    }
    
    /**
     * Gets the display name for the attachment.
     */
    fun getDisplayName(): String = name.ifBlank { "Attachment $id" }
    
    /**
     * Gets the file extension if available.
     */
    fun getFileExtension(): String? = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
    
    /**
     * Checks if the attachment is a code file.
     */
    fun isCodeFile(): Boolean = type == AttachmentType.CODE_SNIPPET || 
        getFileExtension() in listOf("kt", "java", "js", "ts", "py", "rb", "go", "rs", "cpp", "c", "h")
    
    /**
     * Checks if the attachment is an image.
     */
    fun isImage(): Boolean = mimeType?.startsWith("image/") == true
    
    /**
     * Checks if the attachment is a text file.
     */
    fun isTextFile(): Boolean = mimeType?.startsWith("text/") == true || 
        type in listOf(AttachmentType.CODE_SNIPPET, AttachmentType.COMMAND_OUTPUT)
    
    companion object {
        const val MAX_NAME_LENGTH = 255
        const val MAX_CONTENT_LENGTH = 1_000_000 // 1MB
        
        private fun validateName(name: String) {
            if (name.length > MAX_NAME_LENGTH) {
                throw ValidationException("name", name, "Attachment name too long (max $MAX_NAME_LENGTH chars)")
            }
        }
        
        private fun validateContent(content: String) {
            if (content.length > MAX_CONTENT_LENGTH) {
                throw ValidationException("content", content, "Attachment content too long (max $MAX_CONTENT_LENGTH chars)")
            }
        }
    }
}

/**
 * Attachment type enumeration.
 */
enum class AttachmentType {
    FILE_REFERENCE,
    CODE_SNIPPET,
    COMMAND_OUTPUT,
    ERROR_LOG,
    IMAGE,
    DOCUMENT,
    ARCHIVE
}

/**
 * Builder class for creating messages with validation.
 */
class MessageBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var content: String = ""
    private var type: MessageType = MessageType.USER_INPUT
    private var timestamp: Long = System.currentTimeMillis()
    private var isPartial: Boolean = false
    private var metadata: Map<String, String> = emptyMap()
    private var attachments: List<MessageAttachment> = emptyList()
    
    fun id(id: String) = apply { this.id = id }
    fun content(content: String) = apply { this.content = content }
    fun type(type: MessageType) = apply { this.type = type }
    fun timestamp(timestamp: Long) = apply { this.timestamp = timestamp }
    fun isPartial(isPartial: Boolean) = apply { this.isPartial = isPartial }
    fun metadata(metadata: Map<String, String>) = apply { this.metadata = metadata }
    fun attachments(attachments: List<MessageAttachment>) = apply { this.attachments = attachments }
    
    fun addMetadata(key: String, value: String) = apply { 
        this.metadata = this.metadata + (key to value) 
    }
    
    fun addAttachment(attachment: MessageAttachment) = apply { 
        this.attachments = this.attachments + attachment 
    }
    
    fun build(): Message = Message(
        id = id,
        content = content,
        type = type,
        timestamp = timestamp,
        isPartial = isPartial,
        metadata = metadata,
        attachments = attachments
    )
}

/**
 * Builder class for creating message attachments with validation.
 */
class MessageAttachmentBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var type: AttachmentType = AttachmentType.FILE_REFERENCE
    private var name: String = ""
    private var content: String = ""
    private var size: Long = 0
    private var mimeType: String? = null
    private var metadata: Map<String, String> = emptyMap()
    
    fun id(id: String) = apply { this.id = id }
    fun type(type: AttachmentType) = apply { this.type = type }
    fun name(name: String) = apply { this.name = name }
    fun content(content: String) = apply { this.content = content }
    fun size(size: Long) = apply { this.size = size }
    fun mimeType(mimeType: String?) = apply { this.mimeType = mimeType }
    fun metadata(metadata: Map<String, String>) = apply { this.metadata = metadata }
    
    fun addMetadata(key: String, value: String) = apply { 
        this.metadata = this.metadata + (key to value) 
    }
    
    fun build(): MessageAttachment = MessageAttachment(
        id = id,
        type = type,
        name = name,
        content = content,
        size = size,
        mimeType = mimeType,
        metadata = metadata
    )
}

/**
 * Extension functions for Message.
 */
fun Message.toBuilder(): MessageBuilder = MessageBuilder()
    .id(id)
    .content(content)
    .type(type)
    .timestamp(timestamp)
    .isPartial(isPartial)
    .metadata(metadata)
    .attachments(attachments)

/**
 * Extension functions for MessageAttachment.
 */
fun MessageAttachment.toBuilder(): MessageAttachmentBuilder = MessageAttachmentBuilder()
    .id(id)
    .type(type)
    .name(name)
    .content(content)
    .size(size)
    .mimeType(mimeType)
    .metadata(metadata)

/**
 * Creates a new message builder.
 */
fun message(block: MessageBuilder.() -> Unit): Message = 
    MessageBuilder().apply(block).build()

/**
 * Creates a new message attachment builder.
 */
fun messageAttachment(block: MessageAttachmentBuilder.() -> Unit): MessageAttachment = 
    MessageAttachmentBuilder().apply(block).build()