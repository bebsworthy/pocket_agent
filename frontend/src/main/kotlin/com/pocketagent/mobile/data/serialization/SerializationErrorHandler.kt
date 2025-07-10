package com.pocketagent.mobile.data.serialization

import android.util.Log
import kotlinx.serialization.SerializationException as KotlinSerializationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling for serialization operations
 */
@Singleton
class SerializationErrorHandler @Inject constructor() {
    
    companion object {
        private const val TAG = "SerializationErrorHandler"
    }
    
    /**
     * Handles serialization errors with appropriate logging and recovery
     */
    fun handleSerializationError(
        operation: String,
        error: Throwable,
        context: Map<String, Any> = emptyMap()
    ): SerializationError {
        val errorDetails = buildErrorDetails(operation, error, context)
        
        // Log error with appropriate level
        when (error) {
            is KotlinSerializationException -> {
                Log.e(TAG, "Serialization error in $operation: ${error.message}", error)
            }
            is SerializationException -> {
                Log.e(TAG, "Custom serialization error in $operation: ${error.message}", error)
            }
            is IllegalArgumentException -> {
                Log.w(TAG, "Validation error in $operation: ${error.message}", error)
            }
            else -> {
                Log.e(TAG, "Unexpected error in $operation: ${error.message}", error)
            }
        }
        
        return SerializationError(
            operation = operation,
            errorType = determineErrorType(error),
            message = error.message ?: "Unknown error",
            cause = error,
            context = context,
            timestamp = System.currentTimeMillis(),
            recoverable = isRecoverable(error)
        )
    }
    
    /**
     * Determines the type of serialization error
     */
    private fun determineErrorType(error: Throwable): SerializationErrorType {
        return when (error) {
            is KotlinSerializationException -> SerializationErrorType.SERIALIZATION_ERROR
            is SerializationException -> SerializationErrorType.CUSTOM_SERIALIZATION_ERROR
            is IllegalArgumentException -> SerializationErrorType.VALIDATION_ERROR
            is OutOfMemoryError -> SerializationErrorType.MEMORY_ERROR
            is SecurityException -> SerializationErrorType.SECURITY_ERROR
            else -> SerializationErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * Determines if the error is recoverable
     */
    private fun isRecoverable(error: Throwable): Boolean {
        return when (error) {
            is OutOfMemoryError -> false
            is SecurityException -> false
            is KotlinSerializationException -> {
                // Some serialization errors are recoverable (e.g., unknown keys)
                error.message?.contains("Unknown key") == true
            }
            is IllegalArgumentException -> true // Validation errors are usually recoverable
            else -> true
        }
    }
    
    /**
     * Builds detailed error information
     */
    private fun buildErrorDetails(
        operation: String,
        error: Throwable,
        context: Map<String, Any>
    ): Map<String, Any> {
        return mapOf(
            "operation" to operation,
            "error_class" to error::class.java.simpleName,
            "error_message" to (error.message ?: "Unknown error"),
            "stack_trace" to error.stackTrace.take(5).joinToString("\n") { it.toString() },
            "context" to context,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    /**
     * Attempts to recover from serialization errors
     */
    fun attemptRecovery(
        error: SerializationError,
        fallbackData: String? = null
    ): RecoveryResult {
        return when (error.errorType) {
            SerializationErrorType.SERIALIZATION_ERROR -> {
                // Try with more lenient JSON settings
                fallbackData?.let { data ->
                    tryLenientParsing(data)
                } ?: RecoveryResult.Failed("No fallback data available")
            }
            
            SerializationErrorType.VALIDATION_ERROR -> {
                // Try to clean and re-validate data
                fallbackData?.let { data ->
                    tryDataCleaning(data)
                } ?: RecoveryResult.Failed("No fallback data available")
            }
            
            SerializationErrorType.MEMORY_ERROR -> {
                // Cannot recover from memory errors
                RecoveryResult.Failed("Memory error - cannot recover")
            }
            
            SerializationErrorType.SECURITY_ERROR -> {
                // Cannot recover from security errors
                RecoveryResult.Failed("Security error - cannot recover")
            }
            
            else -> {
                // For unknown errors, try basic recovery
                fallbackData?.let { data ->
                    tryBasicRecovery(data)
                } ?: RecoveryResult.Failed("Unknown error - no recovery method")
            }
        }
    }
    
    /**
     * Tries parsing with lenient JSON settings
     */
    private fun tryLenientParsing(data: String): RecoveryResult {
        return try {
            val lenientJson = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }
            
            // Try to parse as JsonElement first
            val element = lenientJson.parseToJsonElement(data)
            RecoveryResult.Success(element.toString())
        } catch (e: Exception) {
            RecoveryResult.Failed("Lenient parsing failed: ${e.message}")
        }
    }
    
    /**
     * Tries to clean data and re-validate
     */
    private fun tryDataCleaning(data: String): RecoveryResult {
        return try {
            // Remove common problematic characters
            val cleanedData = data
                .replace("\\u0000", "") // Remove null characters
                .replace("\\u0008", "") // Remove backspace
                .replace("\\u000C", "") // Remove form feed
                .trim()
            
            // Try to parse cleaned data
            kotlinx.serialization.json.Json.parseToJsonElement(cleanedData)
            RecoveryResult.Success(cleanedData)
        } catch (e: Exception) {
            RecoveryResult.Failed("Data cleaning failed: ${e.message}")
        }
    }
    
    /**
     * Tries basic recovery methods
     */
    private fun tryBasicRecovery(data: String): RecoveryResult {
        return try {
            // Try to extract basic JSON structure
            if (data.startsWith("{") && data.endsWith("}")) {
                // Looks like JSON object
                val basicJson = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
                
                val element = basicJson.parseToJsonElement(data)
                RecoveryResult.Success(element.toString())
            } else {
                RecoveryResult.Failed("Data doesn't appear to be JSON")
            }
        } catch (e: Exception) {
            RecoveryResult.Failed("Basic recovery failed: ${e.message}")
        }
    }
    
    /**
     * Creates a sanitized error report for logging
     */
    fun createErrorReport(error: SerializationError): String {
        return buildString {
            appendLine("=== Serialization Error Report ===")
            appendLine("Operation: ${error.operation}")
            appendLine("Error Type: ${error.errorType}")
            appendLine("Message: ${error.message}")
            appendLine("Timestamp: ${error.timestamp}")
            appendLine("Recoverable: ${error.recoverable}")
            
            if (error.context.isNotEmpty()) {
                appendLine("Context:")
                error.context.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            
            appendLine("Stack Trace:")
            error.cause?.stackTrace?.take(10)?.forEach { element ->
                appendLine("  $element")
            }
            appendLine("=== End Error Report ===")
        }
    }
}

/**
 * Data class representing a serialization error
 */
data class SerializationError(
    val operation: String,
    val errorType: SerializationErrorType,
    val message: String,
    val cause: Throwable,
    val context: Map<String, Any>,
    val timestamp: Long,
    val recoverable: Boolean
)

/**
 * Types of serialization errors
 */
enum class SerializationErrorType {
    SERIALIZATION_ERROR,
    CUSTOM_SERIALIZATION_ERROR,
    VALIDATION_ERROR,
    MEMORY_ERROR,
    SECURITY_ERROR,
    UNKNOWN_ERROR
}

/**
 * Result of recovery attempts
 */
sealed class RecoveryResult {
    data class Success(val recoveredData: String) : RecoveryResult()
    data class Failed(val reason: String) : RecoveryResult()
}

/**
 * Extension function for easy error handling
 */
fun <T> Result<T>.handleSerializationError(
    handler: SerializationErrorHandler,
    operation: String,
    context: Map<String, Any> = emptyMap()
): Result<T> {
    return this.onFailure { error ->
        handler.handleSerializationError(operation, error, context)
    }
}