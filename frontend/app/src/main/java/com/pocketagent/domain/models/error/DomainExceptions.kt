package com.pocketagent.domain.models.error

/**
 * Base exception class for all domain-related errors.
 *
 * @param message The error message
 * @param cause The underlying cause of the error
 */
sealed class DomainException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception thrown when data validation fails.
 *
 * @param field The field that failed validation
 * @param value The invalid value
 * @param constraint The validation constraint that was violated
 */
class ValidationException(
    val field: String,
    val value: Any?,
    val constraint: String,
) : DomainException("Validation failed for field '$field' with value '$value': $constraint")

/**
 * Exception thrown when a required entity is not found.
 *
 * @param entityType The type of entity that was not found
 * @param identifier The identifier used to search for the entity
 */
class EntityNotFoundException(
    val entityType: String,
    val identifier: String,
) : DomainException("$entityType with identifier '$identifier' not found")

/**
 * Exception thrown when attempting to create a duplicate entity.
 *
 * @param entityType The type of entity being duplicated
 * @param identifier The identifier that already exists
 */
class DuplicateEntityException(
    val entityType: String,
    val identifier: String,
) : DomainException("$entityType with identifier '$identifier' already exists")

/**
 * Exception thrown when a business rule constraint is violated.
 *
 * @param rule The business rule that was violated
 * @param context Additional context about the violation
 */
class BusinessRuleException(
    val rule: String,
    val context: String? = null,
) : DomainException("Business rule violation: $rule${context?.let { " - $it" } ?: ""}")

/**
 * Exception thrown when an operation is not supported in the current state.
 *
 * @param operation The operation that was attempted
 * @param currentState The current state that prevents the operation
 */
class InvalidOperationException(
    val operation: String,
    val currentState: String,
) : DomainException("Operation '$operation' is not valid in current state: $currentState")

/**
 * Exception thrown when data serialization/deserialization fails.
 *
 * @param dataType The type of data being processed
 * @param operation The operation that failed (serialize/deserialize)
 */
class SerializationException(
    val dataType: String,
    val operation: String,
    cause: Throwable,
) : DomainException("Failed to $operation $dataType", cause)

/**
 * Exception thrown when security-related operations fail.
 *
 * @param operation The security operation that failed
 * @param reason The reason for the failure
 */
class SecurityException(
    val operation: String,
    val reason: String,
) : DomainException("Security operation '$operation' failed: $reason")

/**
 * Exception thrown when network operations fail.
 *
 * @param operation The network operation that failed
 * @param endpoint The endpoint that was being accessed
 */
class NetworkException(
    val operation: String,
    val endpoint: String,
    cause: Throwable? = null,
) : DomainException("Network operation '$operation' failed for endpoint '$endpoint'", cause)

/**
 * Exception thrown when storage operations fail.
 *
 * @param operation The storage operation that failed
 * @param resource The resource being accessed
 */
class StorageException(
    val operation: String,
    val resource: String,
    cause: Throwable? = null,
) : DomainException("Storage operation '$operation' failed for resource '$resource'", cause)
