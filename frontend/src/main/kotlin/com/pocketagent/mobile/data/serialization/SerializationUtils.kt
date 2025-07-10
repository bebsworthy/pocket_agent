package com.pocketagent.mobile.data.serialization

import com.pocketagent.mobile.data.model.WebSocketMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for common serialization operations
 */
@Singleton
class SerializationUtils @Inject constructor(
    private val jsonConfig: JsonConfig
) {
    
    /**
     * Safely deserializes WebSocket messages with type detection
     */
    fun deserializeWebSocketMessage(jsonString: String): Result<WebSocketMessage> {
        return try {
            // First parse as JsonElement to extract type
            val jsonElement = jsonConfig.compactJson.parseToJsonElement(jsonString)
            val jsonObject = jsonElement.jsonObject
            
            val messageType = jsonObject["type"]?.jsonPrimitive?.content
                ?: return Result.failure(SerializationException("Missing message type"))
            
            // Deserialize based on type
            val message = when (messageType) {
                "auth_challenge" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.AuthChallenge.serializer(),
                    jsonElement
                )
                "auth_response" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.AuthResponse.serializer(),
                    jsonElement
                )
                "auth_success" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.AuthSuccess.serializer(),
                    jsonElement
                )
                "auth_failure" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.AuthFailure.serializer(),
                    jsonElement
                )
                "command" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.CommandMessage.serializer(),
                    jsonElement
                )
                "project_init" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.ProjectInitMessage.serializer(),
                    jsonElement
                )
                "claude_response" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.ClaudeResponse.serializer(),
                    jsonElement
                )
                "command_output" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.CommandOutput.serializer(),
                    jsonElement
                )
                "progress_update" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.ProgressUpdate.serializer(),
                    jsonElement
                )
                "permission_request" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.PermissionRequest.serializer(),
                    jsonElement
                )
                "permission_response" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.PermissionResponse.serializer(),
                    jsonElement
                )
                "session_resume" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.SessionResume.serializer(),
                    jsonElement
                )
                "session_status" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.SessionStatus.serializer(),
                    jsonElement
                )
                "session_terminate" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.SessionTerminate.serializer(),
                    jsonElement
                )
                "clone_progress" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.CloneProgress.serializer(),
                    jsonElement
                )
                "project_init_complete" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.ProjectInitComplete.serializer(),
                    jsonElement
                )
                "error" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.ErrorMessage.serializer(),
                    jsonElement
                )
                "heartbeat" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.Heartbeat.serializer(),
                    jsonElement
                )
                "pong" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.Pong.serializer(),
                    jsonElement
                )
                "file_list_request" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.FileListRequest.serializer(),
                    jsonElement
                )
                "file_list_response" -> jsonConfig.compactJson.decodeFromJsonElement(
                    com.pocketagent.mobile.data.model.FileListResponse.serializer(),
                    jsonElement
                )
                else -> return Result.failure(SerializationException("Unknown message type: $messageType"))
            }
            
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to deserialize WebSocket message", e))
        }
    }
    
    /**
     * Serializes WebSocket messages to compact JSON
     */
    fun serializeWebSocketMessage(message: WebSocketMessage): Result<String> {
        return try {
            val jsonString = when (message) {
                is com.pocketagent.mobile.data.model.AuthChallenge -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.AuthChallenge.serializer(), message)
                is com.pocketagent.mobile.data.model.AuthResponse -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.AuthResponse.serializer(), message)
                is com.pocketagent.mobile.data.model.AuthSuccess -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.AuthSuccess.serializer(), message)
                is com.pocketagent.mobile.data.model.AuthFailure -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.AuthFailure.serializer(), message)
                is com.pocketagent.mobile.data.model.CommandMessage -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.CommandMessage.serializer(), message)
                is com.pocketagent.mobile.data.model.ProjectInitMessage -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.ProjectInitMessage.serializer(), message)
                is com.pocketagent.mobile.data.model.ClaudeResponse -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.ClaudeResponse.serializer(), message)
                is com.pocketagent.mobile.data.model.CommandOutput -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.CommandOutput.serializer(), message)
                is com.pocketagent.mobile.data.model.ProgressUpdate -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.ProgressUpdate.serializer(), message)
                is com.pocketagent.mobile.data.model.PermissionRequest -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.PermissionRequest.serializer(), message)
                is com.pocketagent.mobile.data.model.PermissionResponse -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.PermissionResponse.serializer(), message)
                is com.pocketagent.mobile.data.model.SessionResume -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.SessionResume.serializer(), message)
                is com.pocketagent.mobile.data.model.SessionStatus -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.SessionStatus.serializer(), message)
                is com.pocketagent.mobile.data.model.SessionTerminate -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.SessionTerminate.serializer(), message)
                is com.pocketagent.mobile.data.model.CloneProgress -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.CloneProgress.serializer(), message)
                is com.pocketagent.mobile.data.model.ProjectInitComplete -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.ProjectInitComplete.serializer(), message)
                is com.pocketagent.mobile.data.model.ErrorMessage -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.ErrorMessage.serializer(), message)
                is com.pocketagent.mobile.data.model.Heartbeat -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.Heartbeat.serializer(), message)
                is com.pocketagent.mobile.data.model.Pong -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.Pong.serializer(), message)
                is com.pocketagent.mobile.data.model.FileListRequest -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.FileListRequest.serializer(), message)
                is com.pocketagent.mobile.data.model.FileListResponse -> 
                    jsonConfig.compactJson.encodeToString(com.pocketagent.mobile.data.model.FileListResponse.serializer(), message)
                else -> return Result.failure(SerializationException("Unknown message type: ${message::class.simpleName}"))
            }
            
            Result.success(jsonString)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to serialize WebSocket message", e))
        }
    }
    
    /**
     * Validates JSON structure without full deserialization
     */
    fun validateJsonStructure(jsonString: String, expectedFields: List<String>): Result<Boolean> {
        return try {
            val jsonElement = jsonConfig.json.parseToJsonElement(jsonString)
            if (jsonElement !is JsonObject) {
                return Result.failure(SerializationException("Expected JSON object"))
            }
            
            val missingFields = expectedFields.filter { field ->
                !jsonElement.containsKey(field)
            }
            
            if (missingFields.isNotEmpty()) {
                return Result.failure(SerializationException("Missing required fields: ${missingFields.joinToString(", ")}"))
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(SerializationException("Invalid JSON structure", e))
        }
    }
    
    /**
     * Merges two JSON objects, with the second overriding the first
     */
    fun mergeJsonObjects(base: String, override: String): Result<String> {
        return try {
            val baseElement = jsonConfig.json.parseToJsonElement(base)
            val overrideElement = jsonConfig.json.parseToJsonElement(override)
            
            if (baseElement !is JsonObject || overrideElement !is JsonObject) {
                return Result.failure(SerializationException("Both inputs must be JSON objects"))
            }
            
            val mergedMap = baseElement.toMutableMap()
            overrideElement.forEach { (key, value) ->
                mergedMap[key] = value
            }
            
            val mergedObject = JsonObject(mergedMap)
            val result = jsonConfig.json.encodeToString(JsonElement.serializer(), mergedObject)
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to merge JSON objects", e))
        }
    }
    
    /**
     * Extracts specific fields from JSON without full deserialization
     */
    fun extractJsonFields(jsonString: String, fields: List<String>): Result<Map<String, String>> {
        return try {
            val jsonElement = jsonConfig.json.parseToJsonElement(jsonString)
            if (jsonElement !is JsonObject) {
                return Result.failure(SerializationException("Expected JSON object"))
            }
            
            val extractedFields = fields.mapNotNull { field ->
                jsonElement[field]?.jsonPrimitive?.content?.let { value ->
                    field to value
                }
            }.toMap()
            
            Result.success(extractedFields)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to extract JSON fields", e))
        }
    }
    
    /**
     * Converts data size to human-readable format
     */
    fun formatDataSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", size, units[unitIndex])
    }
    
    /**
     * Estimates serialized size of an object
     */
    inline fun <reified T> estimateSerializedSize(obj: T): Result<Long> {
        return try {
            val jsonString = jsonConfig.json.encodeToString(obj)
            Result.success(jsonString.toByteArray().size.toLong())
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to estimate serialized size", e))
        }
    }
}