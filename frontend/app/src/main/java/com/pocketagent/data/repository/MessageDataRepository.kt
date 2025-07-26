package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized repository for Message data operations.
 *
 * This repository handles all Message-specific CRUD operations,
 * extracted from SecureDataRepository to improve maintainability.
 * It maintains the same interface but focuses specifically on Message management.
 */
@Singleton
class MessageDataRepository
    @Inject
    constructor(
        private val dataStorage: SecureDataRepositoryCore,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "MessageDataRepository"
            private const val MAX_MESSAGES_PER_PROJECT = 1000
        }

        /**
         * Retrieves messages for a project.
         *
         * @param projectId The project ID
         * @param limit Maximum number of messages to return
         * @return List of messages sorted by timestamp
         */
        suspend fun getProjectMessages(
            projectId: String,
            limit: Int = 100,
        ): List<Message> {
            Log.d(TAG, "Getting messages for project: $projectId (limit: $limit)")

            val messages = dataStorage.loadData().messages[projectId] ?: emptyList()
            return messages.takeLast(limit).sortedBy { it.timestamp }
        }

        /**
         * Adds a message to a project.
         *
         * @param projectId The project ID
         * @param message The message to add
         * @throws DataException.ValidationException if message is invalid
         */
        suspend fun addMessage(
            projectId: String,
            message: Message,
        ) {
            Log.d(TAG, "Adding message to project: $projectId")

            try {
                dataValidator.validateMessage(message)

                val current = dataStorage.loadData()
                val currentMessages = current.messages[projectId] ?: emptyList()
                val updatedMessages =
                    (currentMessages + message).let { messages ->
                        // Limit messages per project
                        if (messages.size > MAX_MESSAGES_PER_PROJECT) {
                            messages.takeLast(MAX_MESSAGES_PER_PROJECT)
                        } else {
                            messages
                        }
                    }

                val updatedData =
                    current.copy(
                        messages = current.messages + (projectId to updatedMessages),
                    )
                dataStorage.saveData(updatedData)

                Log.d(TAG, "Message added successfully to project: $projectId")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to add message - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to add message - invalid arguments", e)
                throw DataException.ValidationException("Failed to add message - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to add message - invalid state", e)
                throw DataException.ValidationException("Failed to add message - repository in invalid state: ${e.message}", e)
            }
        }

        /**
         * Clears all messages for a project.
         *
         * @param projectId The project ID
         */
        suspend fun clearProjectMessages(projectId: String) {
            Log.d(TAG, "Clearing messages for project: $projectId")

            val current = dataStorage.loadData()
            val updatedData = current.copy(messages = current.messages - projectId)
            dataStorage.saveData(updatedData)

            Log.d(TAG, "Messages cleared for project: $projectId")
        }

        /**
         * Observable flow of messages for a project.
         *
         * @param projectId The project ID
         * @return Flow of messages list
         */
        fun observeProjectMessages(projectId: String): Flow<List<Message>> =
            dataStorage.observeData().map { data ->
                data.messages[projectId] ?: emptyList()
            }

        /**
         * Gets the total message count across all projects.
         *
         * @return Total message count
         */
        suspend fun getTotalMessageCount(): Int =
            dataStorage
                .loadData()
                .messages.values
                .sumOf { it.size }

        /**
         * Gets message count for a specific project.
         *
         * @param projectId The project ID
         * @return Message count for the project
         */
        suspend fun getProjectMessageCount(projectId: String): Int = dataStorage.loadData().messages[projectId]?.size ?: 0

        /**
         * Gets recent messages across all projects.
         *
         * @param limit Maximum number of messages to return
         * @return List of recent messages with project IDs
         */
        suspend fun getRecentMessages(limit: Int = 50): List<Pair<String, Message>> {
            Log.d(TAG, "Getting recent messages (limit: $limit)")

            val allMessages = mutableListOf<Pair<String, Message>>()
            val data = dataStorage.loadData()

            data.messages.forEach { (projectId, messages) ->
                messages.forEach { message ->
                    allMessages.add(projectId to message)
                }
            }

            return allMessages
                .sortedByDescending { it.second.timestamp }
                .take(limit)
        }

        /**
         * Searches messages across all projects.
         *
         * @param query The search query
         * @param projectId Optional project ID to limit search scope
         * @return List of matching messages with project IDs
         */
        suspend fun searchMessages(
            query: String,
            projectId: String? = null,
        ): List<Pair<String, Message>> {
            Log.d(TAG, "Searching messages with query: $query")

            if (query.isBlank()) return emptyList()

            val data = dataStorage.loadData()
            val messagesToSearch =
                if (projectId != null) {
                    mapOf(projectId to (data.messages[projectId] ?: emptyList()))
                } else {
                    data.messages
                }

            val results = mutableListOf<Pair<String, Message>>()
            messagesToSearch.forEach { (pId, messages) ->
                messages
                    .filter { message ->
                        message.content.contains(query, ignoreCase = true) ||
                            message.metadata.values.any { it.contains(query, ignoreCase = true) }
                    }.forEach { message ->
                        results.add(pId to message)
                    }
            }

            return results.sortedByDescending { it.second.timestamp }
        }
    }
