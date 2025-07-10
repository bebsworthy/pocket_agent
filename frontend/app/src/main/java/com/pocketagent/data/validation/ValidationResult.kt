package com.pocketagent.data.validation

/**
 * Represents the result of a validation operation.
 * 
 * This sealed class provides a comprehensive result type for validation operations,
 * supporting both success and various types of failures with detailed error information.
 */
sealed class ValidationResult {
    /**
     * Validation succeeded.
     */
    object Success : ValidationResult()
    
    /**
     * Validation failed with one or more errors.
     * 
     * @property errors List of validation errors
     */
    data class Failure(val errors: List<ValidationError>) : ValidationResult() {
        constructor(error: ValidationError) : this(listOf(error))
        constructor(message: String, field: String? = null) : this(
            ValidationError(message, field, ValidationError.Type.BUSINESS_RULE)
        )
    }
    
    /**
     * Check if the validation was successful.
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Check if the validation failed.
     */
    fun isFailure(): Boolean = this is Failure
    
    /**
     * Get the error messages, or empty list if successful.
     */
    fun getErrorMessages(): List<String> = when (this) {
        is Success -> emptyList()
        is Failure -> errors.map { it.message }
    }
    
    /**
     * Get the first error message, or null if successful.
     */
    fun getFirstErrorMessage(): String? = when (this) {
        is Success -> null
        is Failure -> errors.firstOrNull()?.message
    }
    
    /**
     * Get errors for a specific field.
     */
    fun getFieldErrors(field: String): List<ValidationError> = when (this) {
        is Success -> emptyList()
        is Failure -> errors.filter { it.field == field }
    }
    
    /**
     * Combine this result with another validation result.
     */
    fun and(other: ValidationResult): ValidationResult = when {
        this is Success && other is Success -> Success
        this is Success && other is Failure -> other
        this is Failure && other is Success -> this
        this is Failure && other is Failure -> Failure(this.errors + other.errors)
        else -> this
    }
    
    /**
     * Map the validation result to a different type.
     */
    inline fun <T> map(transform: () -> T): ValidationResult = when (this) {
        is Success -> try {
            transform()
            Success
        } catch (e: Exception) {
            Failure(ValidationError(e.message ?: "Transformation failed", null, ValidationError.Type.INTERNAL))
        }
        is Failure -> this
    }
    
    /**
     * Execute an action only if validation succeeded.
     */
    inline fun onSuccess(action: () -> Unit): ValidationResult {
        if (this is Success) {
            action()
        }
        return this
    }
    
    /**
     * Execute an action only if validation failed.
     */
    inline fun onFailure(action: (List<ValidationError>) -> Unit): ValidationResult {
        if (this is Failure) {
            action(errors)
        }
        return this
    }
}

/**
 * Represents a single validation error.
 * 
 * @property message Human-readable error message
 * @property field Field name that caused the error (null for entity-level errors)
 * @property type Type of validation error
 * @property code Optional error code for programmatic handling
 */
data class ValidationError(
    val message: String,
    val field: String? = null,
    val type: Type = Type.FIELD_VALIDATION,
    val code: String? = null
) {
    /**
     * Types of validation errors.
     */
    enum class Type {
        /** Field-level validation error (e.g., invalid format, required field) */
        FIELD_VALIDATION,
        
        /** Business rule validation error (e.g., duplicate name, constraint violation) */
        BUSINESS_RULE,
        
        /** Relationship constraint error (e.g., foreign key violation) */
        RELATIONSHIP_CONSTRAINT,
        
        /** Database constraint error (e.g., unique constraint, check constraint) */
        DATABASE_CONSTRAINT,
        
        /** Custom validation rule error */
        CUSTOM_RULE,
        
        /** Internal validation system error */
        INTERNAL
    }
    
    /**
     * Create a field-specific error.
     */
    companion object {
        fun fieldError(field: String, message: String, code: String? = null): ValidationError =
            ValidationError(message, field, Type.FIELD_VALIDATION, code)
            
        fun businessRuleError(message: String, field: String? = null, code: String? = null): ValidationError =
            ValidationError(message, field, Type.BUSINESS_RULE, code)
            
        fun relationshipError(message: String, field: String? = null, code: String? = null): ValidationError =
            ValidationError(message, field, Type.RELATIONSHIP_CONSTRAINT, code)
            
        fun databaseError(message: String, field: String? = null, code: String? = null): ValidationError =
            ValidationError(message, field, Type.DATABASE_CONSTRAINT, code)
            
        fun customError(message: String, field: String? = null, code: String? = null): ValidationError =
            ValidationError(message, field, Type.CUSTOM_RULE, code)
    }
}

/**
 * Builder for creating validation results with multiple errors.
 */
class ValidationResultBuilder {
    private val errors = mutableListOf<ValidationError>()
    
    /**
     * Add a field validation error.
     */
    fun addFieldError(field: String, message: String, code: String? = null): ValidationResultBuilder {
        errors.add(ValidationError.fieldError(field, message, code))
        return this
    }
    
    /**
     * Add a business rule error.
     */
    fun addBusinessRuleError(message: String, field: String? = null, code: String? = null): ValidationResultBuilder {
        errors.add(ValidationError.businessRuleError(message, field, code))
        return this
    }
    
    /**
     * Add a relationship constraint error.
     */
    fun addRelationshipError(message: String, field: String? = null, code: String? = null): ValidationResultBuilder {
        errors.add(ValidationError.relationshipError(message, field, code))
        return this
    }
    
    /**
     * Add a database constraint error.
     */
    fun addDatabaseError(message: String, field: String? = null, code: String? = null): ValidationResultBuilder {
        errors.add(ValidationError.databaseError(message, field, code))
        return this
    }
    
    /**
     * Add a custom validation error.
     */
    fun addCustomError(message: String, field: String? = null, code: String? = null): ValidationResultBuilder {
        errors.add(ValidationError.customError(message, field, code))
        return this
    }
    
    /**
     * Add another validation result's errors.
     */
    fun addResult(result: ValidationResult): ValidationResultBuilder {
        if (result is ValidationResult.Failure) {
            errors.addAll(result.errors)
        }
        return this
    }
    
    /**
     * Add a validation error directly.
     */
    fun addError(error: ValidationError): ValidationResultBuilder {
        errors.add(error)
        return this
    }
    
    /**
     * Build the validation result.
     */
    fun build(): ValidationResult = if (errors.isEmpty()) {
        ValidationResult.Success
    } else {
        ValidationResult.Failure(errors.toList())
    }
    
    /**
     * Check if there are any errors.
     */
    fun hasErrors(): Boolean = errors.isNotEmpty()
    
    /**
     * Get the current error count.
     */
    fun errorCount(): Int = errors.size
}

/**
 * Utility functions for working with validation results.
 */
object ValidationResultUtils {
    /**
     * Combine multiple validation results into a single result.
     */
    fun combine(vararg results: ValidationResult): ValidationResult {
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.and(result)
        }
    }
    
    /**
     * Combine a list of validation results.
     */
    fun combine(results: List<ValidationResult>): ValidationResult {
        return results.fold(ValidationResult.Success as ValidationResult) { acc, result ->
            acc.and(result)
        }
    }
    
    /**
     * Create a successful validation result.
     */
    fun success(): ValidationResult = ValidationResult.Success
    
    /**
     * Create a failed validation result with a single error.
     */
    fun failure(message: String, field: String? = null): ValidationResult =
        ValidationResult.Failure(ValidationError(message, field))
    
    /**
     * Create a failed validation result with multiple errors.
     */
    fun failure(errors: List<ValidationError>): ValidationResult =
        ValidationResult.Failure(errors)
    
    /**
     * Create a validation result from a nullable value.
     */
    fun <T> fromNullable(value: T?, errorMessage: String, field: String? = null): ValidationResult {
        return if (value != null) {
            ValidationResult.Success
        } else {
            failure(errorMessage, field)
        }
    }
    
    /**
     * Create a validation result from a boolean condition.
     */
    fun fromCondition(condition: Boolean, errorMessage: String, field: String? = null): ValidationResult {
        return if (condition) {
            ValidationResult.Success
        } else {
            failure(errorMessage, field)
        }
    }
    
    /**
     * Create a validation result from a try-catch block.
     */
    inline fun fromTryCatch(field: String? = null, action: () -> Unit): ValidationResult {
        return try {
            action()
            ValidationResult.Success
        } catch (e: IllegalArgumentException) {
            failure(e.message ?: "Validation failed", field)
        } catch (e: Exception) {
            failure("Internal validation error: ${e.message}", field)
        }
    }
}
