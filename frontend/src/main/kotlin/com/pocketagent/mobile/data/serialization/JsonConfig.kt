package com.pocketagent.mobile.data.serialization

import kotlinx.serialization.SerializationException as KotlinxSerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central configuration for JSON serialization across the application.
 * Provides consistent serialization settings and module configuration.
 */
@Singleton
class JsonConfig @Inject constructor() {
    
    /**
     * Main JSON instance with production-ready configuration
     */
    val json: Json = Json {
        // Backwards compatibility - ignore unknown fields during deserialization
        ignoreUnknownKeys = true
        
        // Include default values in serialization
        encodeDefaults = true
        
        // Pretty print for debugging and human readability
        prettyPrint = true
        
        // Allow special floating point values
        allowSpecialFloatingPointValues = true
        
        // Allow structured map keys
        allowStructuredMapKeys = true
        
        // Use array polymorphism for sealed classes
        useArrayPolymorphism = false
        
        // Use alternative names for properties
        useAlternativeNames = true
        
        // Coerce input values to expected types when possible
        coerceInputValues = true
        
        // Decode enum values case-insensitively
        decodeEnumsCaseInsensitive = true
        
        // Custom serializers module
        serializersModule = createSerializersModule()
    }
    
    /**
     * Compact JSON instance for network communication
     */
    val compactJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false // Compact format for network efficiency
        allowSpecialFloatingPointValues = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        serializersModule = createSerializersModule()
    }
    
    /**
     * Strict JSON instance for critical data validation
     */
    val strictJson: Json = Json {
        ignoreUnknownKeys = false // Fail on unknown keys
        encodeDefaults = true
        prettyPrint = false
        allowSpecialFloatingPointValues = false
        coerceInputValues = false // Strict type validation
        decodeEnumsCaseInsensitive = false
        serializersModule = createSerializersModule()
    }
    
    /**
     * Creates the serializers module with custom serializers
     */
    private fun createSerializersModule(): SerializersModule = SerializersModule {
        // Add contextual serializers for common types
        contextual(UuidSerializer)
        contextual(InstantSerializer)
        contextual(DurationSerializer)
        
        // Add polymorphic serializers for sealed classes
        polymorphic(Any::class) {
            // Add subclasses for polymorphic serialization as needed
        }
    }
    
    /**
     * Validates JSON string format without full deserialization
     */
    fun isValidJson(jsonString: String): Boolean {
        return try {
            json.parseToJsonElement(jsonString)
            true
        } catch (e: KotlinxSerializationException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    
    /**
     * Safely parses JSON with error handling
     */
    inline fun <reified T> safeParse(jsonString: String): Result<T> {
        return try {
            val result = json.decodeFromString<T>(jsonString)
            Result.success(result)
        } catch (e: KotlinxSerializationException) {
            Result.failure(SerializationException("Failed to parse JSON", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SerializationException("Invalid JSON format", e))
        }
    
    /**
     * Safely serializes object to JSON with error handling
     */
    inline fun <reified T> safeSerialize(obj: T): Result<String> {
        return try {
            val result = json.encodeToString(obj)
            Result.success(result)
        } catch (e: KotlinxSerializationException) {
            Result.failure(SerializationException("Failed to serialize object", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SerializationException("Invalid object for serialization", e))
        }
    
    /**
     * Serializes object to compact JSON for network transmission
     */
    inline fun <reified T> toCompactJson(obj: T): Result<String> {
        return try {
            val result = compactJson.encodeToString(obj)
            Result.success(result)
        } catch (e: KotlinxSerializationException) {
            Result.failure(SerializationException("Failed to serialize to compact JSON", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SerializationException("Invalid object for compact JSON serialization", e))
        }
    
    /**
     * Deserializes from compact JSON
     */
    inline fun <reified T> fromCompactJson(jsonString: String): Result<T> {
        return try {
            val result = compactJson.decodeFromString<T>(jsonString)
            Result.success(result)
        } catch (e: KotlinxSerializationException) {
            Result.failure(SerializationException("Failed to deserialize from compact JSON", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SerializationException("Invalid compact JSON format", e))
        }
    
    /**
     * Validates object against strict JSON serialization rules
     */
    inline fun <reified T> validateStrict(obj: T): Result<T> {
        return try {
            val jsonString = strictJson.encodeToString(obj)
            val result = strictJson.decodeFromString<T>(jsonString)
            Result.success(result)
        } catch (e: KotlinxSerializationException) {
            Result.failure(SerializationException("Strict validation failed", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SerializationException("Invalid object for strict validation", e))
        }
    }
}

/**
 * Custom exception for serialization errors
 */
class SerializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)