package com.pocketagent.data.repository

/**
 * Exception hierarchy for data repository operations.
 * 
 * These exceptions provide specific error types for different data operation scenarios
 * to enable better error handling and debugging in the repository layer.
 */
sealed class DataException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Exception thrown when attempting to create an entity with a duplicate name.
     */
    class DuplicateNameException(message: String) : DataException(message)
    
    /**
     * Exception thrown when a constraint violation occurs (e.g., foreign key violation).
     */
    class ConstraintViolationException(message: String) : DataException(message)
    
    /**
     * Exception thrown when stored data is corrupted or cannot be read.
     */
    class CorruptedDataException(message: String, cause: Throwable) : DataException(message, cause)
    
    /**
     * Exception thrown when data save operation fails.
     */
    class SaveFailedException(message: String, cause: Throwable) : DataException(message, cause)
    
    /**
     * Exception thrown when requested entity is not found.
     */
    class EntityNotFoundException(message: String) : DataException(message)
    
    /**
     * Exception thrown when entity validation fails.
     */
    class ValidationException(message: String) : DataException(message)
    
    /**
     * Exception thrown when repository initialization fails.
     */
    class InitializationException(message: String, cause: Throwable) : DataException(message, cause)
    
    /**
     * Exception thrown when backup/restore operations fail.
     */
    class BackupException(message: String, cause: Throwable) : DataException(message, cause)
    
    /**
     * Exception thrown when concurrent access conflicts occur.
     */
    class ConcurrencyException(message: String) : DataException(message)
    
    /**
     * Exception thrown when storage quota is exceeded.
     */
    class QuotaExceededException(message: String) : DataException(message)
}