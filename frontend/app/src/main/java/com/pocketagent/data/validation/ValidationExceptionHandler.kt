package com.pocketagent.data.validation

import android.util.Log

/**
 * Centralized exception handling for validation operations.
 * 
 * This utility provides consistent exception handling patterns for validation
 * operations, consolidating multiple exception types into ValidationResult
 * to reduce throw count violations.
 */
object ValidationExceptionHandler {
    
    /**
     * Executes a validation operation with comprehensive exception handling.
     * 
     * @param operation The validation operation to execute
     * @param operationName Descriptive name for logging
     * @param field Optional field name for context
     * @param tag Logging tag
     * @return ValidationResult with success or appropriate failure
     */
    fun handleValidationOperation(
        operation: () -> ValidationResult,
        operationName: String,
        field: String? = null,
        tag: String = "ValidationOperation"
    ): ValidationResult =
        try {
            operation()
        } catch (e: ValidationException) {
            Log.w(tag, "$operationName failed - validation error", e)
            ValidationResult.Failure(
                ValidationError(
                    e.message ?: "Validation failed",
                    field,
                    ValidationError.Type.FIELD,
                    "VALIDATION_ERROR"
                )
            )
        } catch (e: IllegalArgumentException) {
            Log.w(tag, "$operationName failed - invalid arguments", e)
            ValidationResult.Failure(
                ValidationError(
                    "Invalid arguments: ${e.message}",
                    field,
                    ValidationError.Type.FIELD,
                    "INVALID_ARGUMENT"
                )
            )
        } catch (e: IllegalStateException) {
            Log.e(tag, "$operationName failed - invalid state", e)
            ValidationResult.Failure(
                ValidationError(
                    "Invalid state: ${e.message}",
                    field,
                    ValidationError.Type.INTERNAL,
                    "INVALID_STATE"
                )
            )
        } catch (e: SecurityException) {
            Log.e(tag, "$operationName failed - security error", e)
            ValidationResult.Failure(
                ValidationError(
                    "Security validation failed: ${e.message}",
                    field,
                    ValidationError.Type.SECURITY,
                    "SECURITY_VIOLATION"
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "$operationName failed - unexpected error", e)
            ValidationResult.Failure(
                ValidationError(
                    "Unexpected validation error: ${e.message}",
                    field,
                    ValidationError.Type.INTERNAL,
                    "UNEXPECTED_ERROR"
                )
            )
        }
    
    /**
     * Validates a value with exception handling and returns ValidationResult.
     * 
     * @param value The value to validate
     * @param validator The validation function
     * @param fieldName The name of the field being validated
     * @param validationName Descriptive name for the validation
     * @return ValidationResult
     */
    fun <T> validateValue(
        value: T,
        validator: (T) -> Boolean,
        fieldName: String,
        validationName: String,
        errorCode: String = "VALIDATION_FAILED"
    ): ValidationResult =
        handleValidationOperation(
            operation = {
                if (validator(value)) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Failure(
                        ValidationError.fieldError("$validationName failed", fieldName, errorCode)
                    )
                }
            },
            operationName = validationName,
            field = fieldName
        )
    
    /**
     * Validates multiple conditions and aggregates results.
     * 
     * @param validations List of validation operations
     * @param aggregationStrategy How to combine results
     * @return ValidationResult
     */
    fun validateMultiple(
        validations: List<() -> ValidationResult>,
        aggregationStrategy: ValidationAggregationStrategy = ValidationAggregationStrategy.FAIL_FAST
    ): ValidationResult =
        try {
            when (aggregationStrategy) {
                ValidationAggregationStrategy.FAIL_FAST -> {
                    validations.forEach { validation ->
                        val result = validation()
                        if (result.isFailure()) return result
                    }
                    ValidationResult.Success
                }
                ValidationAggregationStrategy.COLLECT_ALL -> {
                    val failures = mutableListOf<ValidationError>()
                    validations.forEach { validation ->
                        val result = validation()
                        if (result.isFailure()) {
                            failures.addAll(result.getErrors())
                        }
                    }
                    if (failures.isEmpty()) {
                        ValidationResult.Success
                    } else {
                        ValidationResult.Failure(failures)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ValidationExceptionHandler", "Failed to execute multiple validations", e)
            ValidationResult.Failure(
                ValidationError(
                    "Multiple validation execution failed: ${e.message}",
                    null,
                    ValidationError.Type.INTERNAL,
                    "MULTIPLE_VALIDATION_ERROR"
                )
            )
        }
    
    /**
     * Creates a safe validation wrapper that never throws exceptions.
     * 
     * @param validation The validation function that might throw
     * @param fallbackError Error to return if validation throws
     * @return Safe validation function
     */
    fun <T> createSafeValidator(
        validation: (T) -> ValidationResult,
        fallbackError: ValidationError
    ): (T) -> ValidationResult = { value ->
        try {
            validation(value)
        } catch (e: Exception) {
            Log.w("ValidationExceptionHandler", "Validation threw exception, using fallback", e)
            ValidationResult.Failure(fallbackError)
        }
    }
    
    /**
     * Converts exception-throwing validators to Result-returning validators.
     * 
     * @param validator Function that validates and throws on failure
     * @param fieldName Name of the field being validated
     * @param errorMessage Error message template
     * @return Result-returning validator
     */
    fun <T> convertThrowingValidator(
        validator: (T) -> Unit,
        fieldName: String,
        errorMessage: String = "Validation failed"
    ): (T) -> ValidationResult = { value ->
        try {
            validator(value)
            ValidationResult.Success
        } catch (e: IllegalArgumentException) {
            ValidationResult.Failure(
                ValidationError.fieldError("$errorMessage: ${e.message}", fieldName)
            )
        } catch (e: Exception) {
            ValidationResult.Failure(
                ValidationError.fieldError("$errorMessage: ${e.message}", fieldName)
            )
        }
    }
}

/**
 * Strategy for aggregating multiple validation results.
 */
enum class ValidationAggregationStrategy {
    /** Stop at first failure */
    FAIL_FAST,
    /** Collect all failures before returning */
    COLLECT_ALL
}

/**
 * Extension function to execute validation with exception handling.
 */
fun executeValidation(
    operationName: String,
    field: String? = null,
    tag: String = "Validation",
    operation: () -> ValidationResult
): ValidationResult = ValidationExceptionHandler.handleValidationOperation(operation, operationName, field, tag)