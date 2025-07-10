package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.Message
import com.pocketagent.data.models.MessageType
import com.pocketagent.data.validation.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive validator for Message entities.
 * 
 * Provides field-level, entity-level, and business rule validation for messages.
 * Supports both synchronous and asynchronous validation scenarios.
 */
@Singleton
class MessageValidator @Inject constructor() {
    
    companion object {
        private const val MAX_CONTENT_LENGTH = 50000
        private const val MAX_METADATA_ENTRIES = 20
        private const val MAX_METADATA_KEY_LENGTH = 100
        private const val MAX_METADATA_VALUE_LENGTH = 1000
        private const val MIN_CONTENT_LENGTH = 1
        
        // Suspicious patterns that might indicate injection attempts
        private val SUSPICIOUS_PATTERNS = listOf(
            "<script",
            "javascript:",
            "data:text/html",
            "vbscript:",
            "onload=",
            "onerror=",
            "onclick="
        )
    }
    
    /**
     * Validate a complete message entity.
     */
    fun validate(message: Message): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Field-level validations
        builder.addResult(validateId(message.id))
        builder.addResult(validateContent(message.content))
        builder.addResult(validateType(message.type))
        builder.addResult(validateTimestamp(message.timestamp))
        builder.addResult(validateIsPartial(message.isPartial))
        builder.addResult(validateMetadata(message.metadata))
        
        // Entity-level validations
        builder.addResult(validateBusinessRules(message))
        
        return builder.build()
    }
    
    /**
     * Validate message ID.
     */
    fun validateId(id: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("id", "Message ID cannot be blank"))
            .addRule(
                { it.isNotEmpty() },
                "Message ID cannot be empty",
                "id"
            )
            .addRule(
                { it.length <= 100 },
                "Message ID too long (max 100 characters)",
                "id"
            )
            .build()
            .validate(id)
    }
    
    /**
     * Validate message content.
     */
    fun validateContent(content: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("content", "Message content cannot be blank"))
            .addRule(CommonValidationRules.stringLength("content", MIN_CONTENT_LENGTH, MAX_CONTENT_LENGTH))
            .addRule(
                { content ->
                    // Check for suspicious content that might be security risks
                    val lowercaseContent = content.lowercase()
                    !SUSPICIOUS_PATTERNS.any { pattern -> lowercaseContent.contains(pattern) }
                },
                "Message content contains potentially unsafe patterns",
                "content"
            )
            .addRule(
                { !it.trim().isEmpty() },
                "Message content cannot be only whitespace",
                "content"
            )
            .addRule(
                { content ->
                    // Check for reasonable character distribution (not just repeated characters)
                    if (content.length > 10) {
                        val uniqueChars = content.toSet().size
                        val ratio = uniqueChars.toDouble() / content.length
                        ratio > 0.1 // At least 10% unique characters
                    } else {
                        true
                    }
                },
                "Message content appears to be mostly repeated characters",
                "content"
            )
            .build()
            .validate(content)
    }
    
    /**
     * Validate message type.
     */
    fun validateType(type: MessageType): ValidationResult {
        // All enum values are valid, but we can add business logic if needed
        return ValidationResult.Success
    }
    
    /**
     * Validate message timestamp.
     */
    fun validateTimestamp(timestamp: Long): ValidationResult {
        return ValidationRuleBuilder<Long>()
            .addRule(CommonValidationRules.positiveTimestamp("timestamp"))
            .addRule(
                { it <= System.currentTimeMillis() + 60000 }, // Allow 1 minute clock skew
                "Message timestamp cannot be in the future",
                "timestamp"
            )
            .addRule(
                { it >= 1609459200000L }, // January 1, 2021 - reasonable minimum
                "Message timestamp is too far in the past",
                "timestamp"
            )
            .build()
            .validate(timestamp)
    }
    
    /**
     * Validate isPartial flag.
     */
    fun validateIsPartial(isPartial: Boolean): ValidationResult {
        // Boolean values are always valid
        return ValidationResult.Success
    }
    
    /**
     * Validate message metadata.
     */
    fun validateMetadata(metadata: Map<String, String>): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Validate metadata size
        if (metadata.size > MAX_METADATA_ENTRIES) {
            builder.addFieldError(
                "metadata",
                "Too many metadata entries (max $MAX_METADATA_ENTRIES)",
                "METADATA_TOO_MANY_ENTRIES"
            )
        }
        
        // Validate each metadata entry
        metadata.forEach { (key, value) ->
            builder.addResult(validateMetadataKey(key))
            builder.addResult(validateMetadataValue(value))
        }
        
        // Validate reserved metadata keys
        val reservedKeys = listOf("system", "internal", "debug", "trace", "admin")
        reservedKeys.forEach { reservedKey ->
            if (metadata.containsKey(reservedKey)) {
                builder.addBusinessRuleError(
                    "Metadata key '$reservedKey' is reserved for system use",
                    "metadata",
                    "RESERVED_METADATA_KEY"
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate a single metadata key.
     */
    fun validateMetadataKey(key: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.notBlank("metadataKey", "Metadata key cannot be blank"))
            .addRule(CommonValidationRules.stringLength("metadataKey", max = MAX_METADATA_KEY_LENGTH))
            .addRule(
                { it.matches(Regex("^[a-zA-Z0-9._-]+$")) },
                "Metadata key contains invalid characters. Only letters, numbers, dots, underscores, and hyphens are allowed",
                "metadataKey"
            )
            .addRule(
                { !it.startsWith(".") && !it.endsWith(".") },
                "Metadata key cannot start or end with a dot",
                "metadataKey"
            )
            .build()
            .validate(key)
    }
    
    /**
     * Validate a single metadata value.
     */
    fun validateMetadataValue(value: String): ValidationResult {
        return ValidationRuleBuilder<String>()
            .addRule(CommonValidationRules.stringLength("metadataValue", max = MAX_METADATA_VALUE_LENGTH))
            .addRule(
                { value ->
                    // Check for suspicious content in metadata values
                    val lowercaseValue = value.lowercase()
                    !SUSPICIOUS_PATTERNS.any { pattern -> lowercaseValue.contains(pattern) }
                },
                "Metadata value contains potentially unsafe patterns",
                "metadataValue"
            )
            .build()
            .validate(value)
    }
    
    /**
     * Validate business rules for message.
     */
    fun validateBusinessRules(message: Message): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Validate content appropriateness for message type
        when (message.type) {
            MessageType.USER_INPUT -> {
                // User input should not be empty or too short
                if (message.content.trim().length < 2) {
                    builder.addBusinessRuleError(
                        "User input messages should contain meaningful content",
                        "content",
                        "USER_INPUT_TOO_SHORT"
                    )
                }
            }
            MessageType.CLAUDE_RESPONSE -> {
                // Claude responses should be substantial
                if (message.content.trim().length < 5 && !message.isPartial) {
                    builder.addBusinessRuleError(
                        "Claude response messages should contain substantial content",
                        "content",
                        "CLAUDE_RESPONSE_TOO_SHORT"
                    )
                }
            }
            MessageType.SYSTEM_MESSAGE -> {
                // System messages should be concise and informative
                if (message.content.length > 500) {
                    builder.addBusinessRuleError(
                        "System messages should be concise (max 500 characters)",
                        "content",
                        "SYSTEM_MESSAGE_TOO_LONG"
                    )
                }
            }
            MessageType.ERROR_MESSAGE -> {
                // Error messages should indicate what went wrong
                val errorKeywords = listOf("error", "failed", "exception", "invalid", "cannot", "unable")
                val hasErrorKeyword = errorKeywords.any { keyword ->
                    message.content.contains(keyword, ignoreCase = true)
                }
                if (!hasErrorKeyword) {
                    builder.addBusinessRuleError(
                        "Error messages should clearly indicate what went wrong",
                        "content",
                        "ERROR_MESSAGE_UNCLEAR"
                    )
                }
            }
            MessageType.STATUS_UPDATE -> {
                // Status updates should be brief
                if (message.content.length > 200) {
                    builder.addBusinessRuleError(
                        "Status update messages should be brief (max 200 characters)",
                        "content",
                        "STATUS_UPDATE_TOO_LONG"
                    )
                }
            }
        }
        
        // Validate partial message constraints
        if (message.isPartial) {
            when (message.type) {
                MessageType.USER_INPUT -> {
                    builder.addBusinessRuleError(
                        "User input messages cannot be marked as partial",
                        "isPartial",
                        "USER_INPUT_CANNOT_BE_PARTIAL"
                    )
                }
                MessageType.SYSTEM_MESSAGE, MessageType.ERROR_MESSAGE, MessageType.STATUS_UPDATE -> {
                    builder.addBusinessRuleError(
                        "${message.type} messages cannot be marked as partial",
                        "isPartial",
                        "SYSTEM_MESSAGE_CANNOT_BE_PARTIAL"
                    )
                }
                MessageType.CLAUDE_RESPONSE -> {
                    // Claude responses can be partial (streaming)
                }
            }
        }
        
        // Validate timestamp reasonableness
        val now = System.currentTimeMillis()
        val messageAge = now - message.timestamp
        
        // Messages older than 30 days might be suspicious for new creations
        if (messageAge > (30 * 24 * 60 * 60 * 1000L)) {
            builder.addCustomError(
                "Message timestamp is quite old. Please verify this is correct",
                "timestamp",
                "MESSAGE_VERY_OLD"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Validate for creation (additional checks for new entities).
     */
    fun validateForCreation(message: Message): ValidationResult {
        val baseValidation = validate(message)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional creation-specific validations
        val now = System.currentTimeMillis()
        
        // Created timestamp should be recent (within last 10 minutes for new messages)
        if (Math.abs(now - message.timestamp) > 600000) {
            builder.addBusinessRuleError(
                "Message timestamp should be recent for new messages",
                "timestamp",
                "CREATION_TIMESTAMP_NOT_RECENT"
            )
        }
        
        // New messages should not have certain metadata
        val forbiddenMetadataKeys = listOf("processed", "archived", "deleted", "migrated")
        forbiddenMetadataKeys.forEach { forbiddenKey ->
            if (message.metadata.containsKey(forbiddenKey)) {
                builder.addBusinessRuleError(
                    "New messages should not have '$forbiddenKey' metadata",
                    "metadata",
                    "NEW_MESSAGE_FORBIDDEN_METADATA"
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate for update (additional checks for existing entities).
     */
    fun validateForUpdate(original: Message, updated: Message): ValidationResult {
        val baseValidation = validate(updated)
        val builder = ValidationResultBuilder().addResult(baseValidation)
        
        // Additional update-specific validations
        
        // ID should not change
        if (original.id != updated.id) {
            builder.addBusinessRuleError(
                "Message ID cannot be changed during update",
                "id",
                "ID_CHANGE_NOT_ALLOWED"
            )
        }
        
        // Timestamp should not change
        if (original.timestamp != updated.timestamp) {
            builder.addBusinessRuleError(
                "Message timestamp cannot be changed during update",
                "timestamp",
                "TIMESTAMP_CHANGE_NOT_ALLOWED"
            )
        }
        
        // Message type should not change
        if (original.type != updated.type) {
            builder.addBusinessRuleError(
                "Message type cannot be changed during update",
                "type",
                "TYPE_CHANGE_NOT_ALLOWED"
            )
        }
        
        // Content changes should be validated
        if (original.content != updated.content) {
            // Only certain types of messages can have content updates
            when (original.type) {
                MessageType.CLAUDE_RESPONSE -> {
                    // Claude responses can be updated (streaming completion)
                    if (original.isPartial && !updated.isPartial) {
                        // This is completion of a partial message
                        if (!updated.content.startsWith(original.content)) {
                            builder.addBusinessRuleError(
                                "Completed Claude response must contain the original partial content",
                                "content",
                                "PARTIAL_COMPLETION_INVALID"
                            )
                        }
                    }
                }
                MessageType.USER_INPUT -> {
                    builder.addBusinessRuleError(
                        "User input messages cannot be modified after creation",
                        "content",
                        "USER_INPUT_IMMUTABLE"
                    )
                }
                MessageType.SYSTEM_MESSAGE, MessageType.ERROR_MESSAGE, MessageType.STATUS_UPDATE -> {
                    builder.addBusinessRuleError(
                        "${original.type} messages cannot be modified after creation",
                        "content",
                        "SYSTEM_MESSAGE_IMMUTABLE"
                    )
                }
            }
        }
        
        // isPartial flag changes should be validated
        if (original.isPartial != updated.isPartial) {
            if (updated.isPartial && !original.isPartial) {
                builder.addBusinessRuleError(
                    "Message cannot be changed from complete to partial",
                    "isPartial",
                    "CANNOT_MAKE_PARTIAL"
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate message ID uniqueness within a project.
     */
    fun validateIdUniqueness(id: String, existingIds: List<String>, excludeId: String? = null): ValidationResult {
        val conflictingIds = existingIds.filter { existingId ->
            existingId == id && existingId != excludeId
        }
        
        return if (conflictingIds.isNotEmpty()) {
            ValidationResult.Failure(
                ValidationError.businessRuleError(
                    "Message with ID '$id' already exists in this project",
                    "id",
                    "DUPLICATE_MESSAGE_ID"
                )
            )
        } else {
            ValidationResult.Success
        }
    }
    
    /**
     * Validate message ordering within a conversation.
     */
    fun validateMessageOrdering(messages: List<Message>): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Check that timestamps are in order
        val timestamps = messages.map { it.timestamp }
        val sortedTimestamps = timestamps.sorted()
        
        if (timestamps != sortedTimestamps) {
            builder.addBusinessRuleError(
                "Messages are not in chronological order",
                "timestamp",
                "MESSAGES_OUT_OF_ORDER"
            )
        }
        
        // Check for conversation flow logic
        for (i in 1 until messages.size) {
            val previous = messages[i - 1]
            val current = messages[i]
            
            // User input should generally be followed by Claude response or system message
            if (previous.type == MessageType.USER_INPUT && 
                current.type == MessageType.USER_INPUT &&
                current.timestamp - previous.timestamp < 1000) { // Less than 1 second apart
                builder.addBusinessRuleError(
                    "Consecutive user input messages should not be so close together",
                    "type",
                    "CONSECUTIVE_USER_INPUTS"
                )
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate batch of messages for consistency.
     */
    fun validateMessageBatch(messages: List<Message>): ValidationResult {
        val builder = ValidationResultBuilder()
        
        // Validate each message individually
        messages.forEach { message ->
            builder.addResult(validate(message))
        }
        
        // Validate batch-level constraints
        builder.addResult(validateMessageOrdering(messages))
        
        // Check for duplicate IDs within the batch
        val ids = messages.map { it.id }
        val duplicateIds = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        
        duplicateIds.forEach { duplicateId ->
            builder.addBusinessRuleError(
                "Duplicate message ID '$duplicateId' found in batch",
                "id",
                "DUPLICATE_ID_IN_BATCH"
            )
        }
        
        return builder.build()
    }
    
    /**
     * Quick validation for UI field updates (less comprehensive).
     */
    fun validateField(field: String, value: Any?): ValidationResult {
        return when (field) {
            "content" -> validateContent(value as? String ?: "")
            "type" -> validateType(value as? MessageType ?: MessageType.USER_INPUT)
            "timestamp" -> validateTimestamp(value as? Long ?: 0L)
            "isPartial" -> validateIsPartial(value as? Boolean ?: false)
            "metadata" -> validateMetadata(value as? Map<String, String> ?: emptyMap())
            else -> ValidationResult.Success
        }
    }
}
