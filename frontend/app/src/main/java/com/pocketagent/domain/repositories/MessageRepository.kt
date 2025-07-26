package com.pocketagent.domain.repositories

import com.pocketagent.domain.models.Result
import com.pocketagent.domain.models.entities.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for message management.
 *
 * This interface defines the contract for managing chat messages
 * in Claude Code conversations, including real-time updates, pagination,
 * and message lifecycle management.
 *
 * All operations use suspend functions for async processing and return Flow<T> for
 * observable data with proper error handling via Result<T>.
 */
interface MessageRepository {
    /**
     * Retrieves messages for a specific project with pagination.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @param offset Number of messages to skip
     * @return Flow emitting a list of messages
     */
    fun getMessagesForProject(
        projectId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): Flow<Result<List<Message>>>

    /**
     * Observes messages for a project with real-time updates.
     *
     * @param projectId The project ID
     * @return Flow emitting message list updates
     */
    fun observeMessages(projectId: String): Flow<List<Message>>

    /**
     * Retrieves a message by ID.
     *
     * @param id The message ID
     * @return The message if found
     */
    suspend fun getMessageById(id: String): Result<Message?>

    /**
     * Observes a specific message for real-time updates.
     *
     * @param id The message ID
     * @return Flow emitting message updates
     */
    fun observeMessage(id: String): Flow<Message?>

    /**
     * Saves a new message.
     *
     * @param projectId The project ID
     * @param message The message to save
     * @return The saved message
     */
    suspend fun saveMessage(
        projectId: String,
        message: Message,
    ): Result<Message>

    /**
     * Batch saves multiple messages.
     *
     * @param projectId The project ID
     * @param messages The messages to save
     * @return List of saved messages
     */
    suspend fun saveMessages(
        projectId: String,
        messages: List<Message>,
    ): Result<List<Message>>

    /**
     * Updates an existing message.
     *
     * @param message The message to update
     * @return The updated message
     */
    suspend fun updateMessage(message: Message): Result<Message>

    /**
     * Updates message with partial content (for streaming).
     *
     * @param id The message ID
     * @param content The new content
     * @param isPartial Whether this is a partial update
     * @return The updated message
     */
    suspend fun updateMessageContent(
        id: String,
        content: String,
        isPartial: Boolean = false,
    ): Result<Message>

    /**
     * Marks a message as completed (no longer partial).
     *
     * @param id The message ID
     * @return The updated message
     */
    suspend fun completeMessage(id: String): Result<Message>

    /**
     * Deletes a message.
     *
     * @param id The message ID to delete
     * @return Success or error result
     */
    suspend fun deleteMessage(id: String): Result<Unit>

    /**
     * Batch deletes multiple messages.
     *
     * @param ids The message IDs to delete
     * @return Success or error result with failed IDs
     */
    suspend fun deleteMessages(ids: List<String>): Result<List<String>>

    /**
     * Deletes all messages for a project.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun deleteMessagesForProject(projectId: String): Result<Unit>

    /**
     * Gets recent messages for a project.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @return List of recent messages
     */
    suspend fun getRecentMessages(
        projectId: String,
        limit: Int = 20,
    ): Result<List<Message>>

    /**
     * Gets messages by type for a project.
     *
     * @param projectId The project ID
     * @param type The message type to filter by
     * @param limit Maximum number of messages to return
     * @return List of messages with the specified type
     */
    suspend fun getMessagesByType(
        projectId: String,
        type: String,
        limit: Int = 50,
    ): Result<List<Message>>

    /**
     * Gets user input messages for a project.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @return List of user input messages
     */
    suspend fun getUserMessages(
        projectId: String,
        limit: Int = 50,
    ): Result<List<Message>>

    /**
     * Gets Claude response messages for a project.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @return List of Claude response messages
     */
    suspend fun getClaudeMessages(
        projectId: String,
        limit: Int = 50,
    ): Result<List<Message>>

    /**
     * Gets system messages for a project.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @return List of system messages
     */
    suspend fun getSystemMessages(
        projectId: String,
        limit: Int = 50,
    ): Result<List<Message>>

    /**
     * Gets error messages for a project.
     *
     * @param projectId The project ID
     * @param limit Maximum number of messages to return
     * @return List of error messages
     */
    suspend fun getErrorMessages(
        projectId: String,
        limit: Int = 50,
    ): Result<List<Message>>

    /**
     * Searches messages for a project.
     *
     * @param projectId The project ID
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of matching messages
     */
    suspend fun searchMessages(
        projectId: String,
        query: String,
        limit: Int = 20,
    ): Result<List<Message>>

    /**
     * Searches messages across all projects.
     *
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of matching messages with project info
     */
    suspend fun searchAllMessages(
        query: String,
        limit: Int = 20,
    ): Result<List<Message>>

    /**
     * Gets the count of messages for a project.
     *
     * @param projectId The project ID
     * @return Number of messages
     */
    suspend fun getMessageCount(projectId: String): Result<Int>

    /**
     * Gets message counts by type for a project.
     *
     * @param projectId The project ID
     * @return Map of message type to count
     */
    suspend fun getMessageCountByType(projectId: String): Result<Map<String, Int>>

    /**
     * Gets messages that require user permission.
     *
     * @param projectId The project ID
     * @return List of messages requiring permission
     */
    suspend fun getPendingPermissionMessages(projectId: String): Result<List<Message>>

    /**
     * Observes pending permission messages for real-time updates.
     *
     * @param projectId The project ID
     * @return Flow emitting permission request updates
     */
    fun observePendingPermissionMessages(projectId: String): Flow<List<Message>>

    /**
     * Responds to a permission request message.
     *
     * @param messageId The message ID
     * @param approved Whether the permission is approved
     * @return The updated message
     */
    suspend fun respondToPermissionRequest(
        messageId: String,
        approved: Boolean,
    ): Result<Message>

    /**
     * Gets conversation context for a project.
     * Returns last N messages to maintain context.
     *
     * @param projectId The project ID
     * @param contextSize Number of messages to include in context
     * @return List of context messages
     */
    suspend fun getConversationContext(
        projectId: String,
        contextSize: Int = 10,
    ): Result<List<Message>>

    /**
     * Trims old messages for a project to maintain storage limits.
     *
     * @param projectId The project ID
     * @param maxMessages Maximum number of messages to keep
     * @return Number of messages deleted
     */
    suspend fun trimMessages(
        projectId: String,
        maxMessages: Int = 1000,
    ): Result<Int>

    /**
     * Exports messages for a project.
     *
     * @param projectId The project ID
     * @param format Export format (json, text, html, markdown)
     * @param includeMetadata Whether to include message metadata
     * @return Exported messages as string
     */
    suspend fun exportMessages(
        projectId: String,
        format: String = "json",
        includeMetadata: Boolean = true,
    ): Result<String>

    /**
     * Imports messages from exported data.
     *
     * @param projectId The project ID
     * @param data The exported message data
     * @param format The format of the data
     * @return Number of messages imported
     */
    suspend fun importMessages(
        projectId: String,
        data: String,
        format: String = "json",
    ): Result<Int>

    /**
     * Gets message statistics for a project.
     *
     * @param projectId The project ID
     * @return Map of message statistics
     */
    suspend fun getMessageStatistics(projectId: String): Result<Map<String, Any>>

    /**
     * Gets message statistics across all projects.
     *
     * @return Map of global message statistics
     */
    suspend fun getGlobalMessageStatistics(): Result<Map<String, Any>>

    /**
     * Synchronizes messages with encrypted storage.
     * Used for offline/online synchronization.
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun syncMessages(projectId: String): Result<Unit>

    /**
     * Clears all messages for a project (for reset).
     *
     * @param projectId The project ID
     * @return Success or error result
     */
    suspend fun clearMessages(projectId: String): Result<Unit>

    /**
     * Clears all messages across all projects (for logout/reset).
     *
     * @return Success or error result
     */
    suspend fun clearAllMessages(): Result<Unit>
}
