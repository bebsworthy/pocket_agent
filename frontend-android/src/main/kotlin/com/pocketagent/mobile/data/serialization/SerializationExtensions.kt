package com.pocketagent.mobile.data.serialization

import com.pocketagent.mobile.data.model.AppData
import com.pocketagent.mobile.data.model.WebSocketMessage
import kotlinx.serialization.SerializationException as KotlinxSerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Extension functions for convenient serialization operations
 */

/**
 * Serializes any object to JSON string with error handling
 */
inline fun <reified T> T.toJson(json: Json = Json.Default): Result<String> {
    return try {
        Result.success(json.encodeToString(this))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to serialize ${T::class.simpleName}", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid object for serialization: ${T::class.simpleName}", e))
    }
}

/**
 * Deserializes JSON string to object with error handling
 */
inline fun <reified T> String.fromJson(json: Json = Json.Default): Result<T> {
    return try {
        Result.success(json.decodeFromString<T>(this))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to deserialize ${T::class.simpleName}", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for ${T::class.simpleName}", e))
    }
}

/**
 * Serializes object to compact JSON (no pretty printing)
 */
inline fun <reified T> T.toCompactJson(): Result<String> {
    return try {
        val compactJson = Json { prettyPrint = false }
        Result.success(compactJson.encodeToString(this))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to serialize to compact JSON", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid object for compact JSON serialization", e))
    }
}

/**
 * Extension for AppData to validate before serialization
 */
fun AppData.toValidatedJson(): Result<String> {
    return try {
        // Validate data integrity
        val identityIds = sshIdentities.map { it.id }.toSet()
        val serverIds = serverProfiles.map { it.id }.toSet()
        val projectIds = projects.map { it.id }.toSet()
        
        // Check for duplicate names
        val identityNames = sshIdentities.map { it.name }
        require(identityNames.size == identityNames.toSet().size) { 
            "Duplicate SSH identity names found" 
        }
        
        val serverNames = serverProfiles.map { it.name }
        require(serverNames.size == serverNames.toSet().size) { 
            "Duplicate server profile names found" 
        }
        
        val projectNames = projects.map { it.name }
        require(projectNames.size == projectNames.toSet().size) { 
            "Duplicate project names found" 
        }
        
        // Validate relationships
        serverProfiles.forEach { server ->
            require(server.sshIdentityId in identityIds) {
                "Server '${server.name}' references non-existent SSH identity"
            }
        }
        
        projects.forEach { project ->
            require(project.serverProfileId in serverIds) {
                "Project '${project.name}' references non-existent server profile"
            }
        }
        
        // Validate messages belong to existing projects
        val invalidProjectIds = messages.keys.filter { it !in projectIds }
        require(invalidProjectIds.isEmpty()) {
            "Messages found for non-existent projects: ${invalidProjectIds.joinToString(", ")}"
        }
        
        // Serialize after validation
        toJson()
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("AppData validation failed", e))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("AppData serialization failed", e))
    }
}

/**
 * Extension for WebSocketMessage to add type safety
 */
fun WebSocketMessage.toNetworkJson(): Result<String> {
    return try {
        val networkJson = Json { 
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        Result.success(networkJson.encodeToString(this))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to serialize WebSocket message", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid WebSocket message for serialization", e))
    }
}

/**
 * Extension to safely parse JSON with fallback
 */
inline fun <reified T> String.parseJsonOrDefault(default: T, json: Json = Json.Default): T {
    return try {
        json.decodeFromString<T>(this)
    } catch (e: KotlinxSerializationException) {
        default
    } catch (e: IllegalArgumentException) {
        default
    }
}

/**
 * Extension to check if string is valid JSON
 */
fun String.isValidJson(): Boolean {
    return try {
        Json.parseToJsonElement(this)
        true
    } catch (e: KotlinxSerializationException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}

/**
 * Extension to prettify JSON string
 */
fun String.prettifyJson(): Result<String> {
    return try {
        val element = Json.parseToJsonElement(this)
        val prettyJson = Json { prettyPrint = true }
        Result.success(prettyJson.encodeToString(element))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to prettify JSON", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for prettification", e))
    }
}

/**
 * Extension to minify JSON string
 */
fun String.minifyJson(): Result<String> {
    return try {
        val element = Json.parseToJsonElement(this)
        val minifiedJson = Json { prettyPrint = false }
        Result.success(minifiedJson.encodeToString(element))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to minify JSON", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for minification", e))
    }
}

/**
 * Extension to get JSON size in bytes
 */
fun String.jsonSizeInBytes(): Int {
    return this.toByteArray(Charsets.UTF_8).size
}

/**
 * Extension to format JSON size
 */
fun String.formatJsonSize(): String {
    val bytes = jsonSizeInBytes()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * Extension to safely extract a field from JSON
 */
fun String.extractJsonField(fieldName: String): Result<String> {
    return try {
        val element = Json.parseToJsonElement(this)
        val jsonObject = element.jsonObject
        val field = jsonObject[fieldName]?.jsonPrimitive?.content
            ?: return Result.failure(SerializationException("Field '$fieldName' not found"))
        Result.success(field)
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to extract field '$fieldName'", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for field extraction", e))
    }
}

/**
 * Extension to validate required fields in JSON
 */
fun String.validateRequiredFields(requiredFields: List<String>): Result<Boolean> {
    return try {
        val element = Json.parseToJsonElement(this)
        val jsonObject = element.jsonObject
        
        val missingFields = requiredFields.filter { field ->
            !jsonObject.containsKey(field)
        }
        
        if (missingFields.isNotEmpty()) {
            return Result.failure(SerializationException("Missing required fields: ${missingFields.joinToString(", ")}"))
        }
        
        Result.success(true)
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to validate required fields", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for field validation", e))
    }
}

/**
 * Extension to merge JSON objects
 */
fun String.mergeWith(other: String): Result<String> {
    return try {
        val thisElement = Json.parseToJsonElement(this)
        val otherElement = Json.parseToJsonElement(other)
        
        if (thisElement !is JsonObject || otherElement !is JsonObject) {
            return Result.failure(SerializationException("Both values must be JSON objects"))
        }
        
        val mergedMap = thisElement.toMutableMap()
        otherElement.forEach { (key, value) ->
            mergedMap[key] = value
        }
        
        val mergedObject = JsonObject(mergedMap)
        Result.success(Json.encodeToString(mergedObject))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to merge JSON objects", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for merging", e))
    }
}

/**
 * Extension to remove fields from JSON
 */
fun String.removeJsonFields(fieldsToRemove: List<String>): Result<String> {
    return try {
        val element = Json.parseToJsonElement(this)
        val jsonObject = element.jsonObject
        
        val filteredMap = jsonObject.filterKeys { key ->
            key !in fieldsToRemove
        }
        
        val filteredObject = JsonObject(filteredMap)
        Result.success(Json.encodeToString(filteredObject))
    } catch (e: KotlinxSerializationException) {
        Result.failure(SerializationException("Failed to remove JSON fields", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(SerializationException("Invalid JSON format for field removal", e))
    }
}