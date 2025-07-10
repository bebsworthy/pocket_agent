package com.pocketagent.data.validation

/**
 * Represents a validation rule that can be applied to a value.
 * 
 * @param T The type of value this rule validates
 */
fun interface ValidationRule<T> {
    /**
     * Validate the given value.
     * 
     * @param value The value to validate
     * @return ValidationResult indicating success or failure
     */
    fun validate(value: T): ValidationResult
}

/**
 * Represents an async validation rule that can be applied to a value.
 * 
 * @param T The type of value this rule validates
 */
fun interface AsyncValidationRule<T> {
    /**
     * Validate the given value asynchronously.
     * 
     * @param value The value to validate
     * @return ValidationResult indicating success or failure
     */
    suspend fun validate(value: T): ValidationResult
}

/**
 * Represents a conditional validation rule that only applies when a condition is met.
 * 
 * @param T The type of value this rule validates
 */
class ConditionalValidationRule<T>(
    private val condition: (T) -> Boolean,
    private val rule: ValidationRule<T>
) : ValidationRule<T> {
    override fun validate(value: T): ValidationResult {
        return if (condition(value)) {
            rule.validate(value)
        } else {
            ValidationResult.Success
        }
    }
}

/**
 * Builder for creating composite validation rules.
 * 
 * @param T The type of value to validate
 */
class ValidationRuleBuilder<T> {
    private val rules = mutableListOf<ValidationRule<T>>()
    private val asyncRules = mutableListOf<AsyncValidationRule<T>>()
    
    /**
     * Add a validation rule.
     */
    fun addRule(rule: ValidationRule<T>): ValidationRuleBuilder<T> {
        rules.add(rule)
        return this
    }
    
    /**
     * Add an async validation rule.
     */
    fun addAsyncRule(rule: AsyncValidationRule<T>): ValidationRuleBuilder<T> {
        asyncRules.add(rule)
        return this
    }
    
    /**
     * Add a conditional validation rule.
     */
    fun addConditionalRule(condition: (T) -> Boolean, rule: ValidationRule<T>): ValidationRuleBuilder<T> {
        rules.add(ConditionalValidationRule(condition, rule))
        return this
    }
    
    /**
     * Add a simple validation rule with a predicate and error message.
     */
    fun addRule(predicate: (T) -> Boolean, errorMessage: String, field: String? = null): ValidationRuleBuilder<T> {
        rules.add { value ->
            if (predicate(value)) {
                ValidationResult.Success
            } else {
                ValidationResult.Failure(ValidationError(errorMessage, field))
            }
        }
        return this
    }
    
    /**
     * Build a composite validation rule that applies all rules.
     */
    fun build(): ValidationRule<T> {
        return ValidationRule { value ->
            rules.fold(ValidationResult.Success as ValidationResult) { acc, rule ->
                acc.and(rule.validate(value))
            }
        }
    }
    
    /**
     * Build a composite async validation rule that applies all rules.
     */
    fun buildAsync(): AsyncValidationRule<T> {
        return AsyncValidationRule { value ->
            // First apply synchronous rules
            var result = rules.fold(ValidationResult.Success as ValidationResult) { acc, rule ->
                acc.and(rule.validate(value))
            }
            
            // Then apply async rules
            for (asyncRule in asyncRules) {
                result = result.and(asyncRule.validate(value))
            }
            
            result
        }
    }
}

/**
 * Common validation rules for basic types.
 */
object CommonValidationRules {
    
    /**
     * Rule that validates a string is not blank.
     */
    fun notBlank(field: String, errorMessage: String = "$field cannot be blank"): ValidationRule<String> {
        return ValidationRule { value ->
            ValidationResultUtils.fromCondition(
                value.isNotBlank(),
                errorMessage,
                field
            )
        }
    }
    
    /**
     * Rule that validates a string length is within bounds.
     */
    fun stringLength(field: String, min: Int = 0, max: Int = Int.MAX_VALUE): ValidationRule<String> {
        return ValidationRule { value ->
            when {
                value.length < min -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must be at least $min characters")
                )
                value.length > max -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must be at most $max characters")
                )
                else -> ValidationResult.Success
            }
        }
    }
    
    /**
     * Rule that validates a string matches a regex pattern.
     */
    fun regexPattern(field: String, pattern: Regex, errorMessage: String): ValidationRule<String> {
        return ValidationRule { value ->
            ValidationResultUtils.fromCondition(
                pattern.matches(value),
                errorMessage,
                field
            )
        }
    }
    
    /**
     * Rule that validates an integer is within a range.
     */
    fun intRange(field: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): ValidationRule<Int> {
        return ValidationRule { value ->
            when {
                value < min -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must be at least $min")
                )
                value > max -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must be at most $max")
                )
                else -> ValidationResult.Success
            }
        }
    }
    
    /**
     * Rule that validates a long timestamp is positive.
     */
    fun positiveTimestamp(field: String): ValidationRule<Long> {
        return ValidationRule { value ->
            ValidationResultUtils.fromCondition(
                value > 0,
                "$field timestamp must be positive",
                field
            )
        }
    }
    
    /**
     * Rule that validates a nullable timestamp is positive if provided.
     */
    fun positiveTimestampOrNull(field: String): ValidationRule<Long?> {
        return ValidationRule { value ->
            if (value == null) {
                ValidationResult.Success
            } else {
                ValidationResultUtils.fromCondition(
                    value > 0,
                    "$field timestamp must be positive if provided",
                    field
                )
            }
        }
    }
    
    /**
     * Rule that validates a collection size is within bounds.
     */
    fun <T> collectionSize(field: String, min: Int = 0, max: Int = Int.MAX_VALUE): ValidationRule<Collection<T>> {
        return ValidationRule { value ->
            when {
                value.size < min -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must contain at least $min items")
                )
                value.size > max -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must contain at most $max items")
                )
                else -> ValidationResult.Success
            }
        }
    }
    
    /**
     * Rule that validates a value is not null.
     */
    fun <T> notNull(field: String): ValidationRule<T?> {
        return ValidationRule { value ->
            ValidationResultUtils.fromNullable(
                value,
                "$field cannot be null",
                field
            )
        }
    }
    
    /**
     * Rule that validates an email format.
     */
    fun email(field: String): ValidationRule<String> {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return regexPattern(field, emailRegex, "$field must be a valid email address")
    }
    
    /**
     * Rule that validates a URL format.
     */
    fun url(field: String): ValidationRule<String> {
        val urlRegex = Regex("^https?://.*")
        return regexPattern(field, urlRegex, "$field must be a valid URL starting with http:// or https://")
    }
    
    /**
     * Rule that validates a hostname format.
     */
    fun hostname(field: String): ValidationRule<String> {
        val hostnameRegex = Regex("^[a-zA-Z0-9.-]+$")
        return ValidationRule { value ->
            val result = ValidationRuleBuilder<String>()
                .addRule(notBlank(field))
                .addRule(stringLength(field, max = 253))
                .addRule(regexPattern(field, hostnameRegex, "$field contains invalid characters"))
                .build()
            result.validate(value)
        }
    }
    
    /**
     * Rule that validates a port number.
     */
    fun port(field: String): ValidationRule<Int> {
        return intRange(field, 1, 65535)
    }
    
    /**
     * Rule that validates a Unix username format.
     */
    fun unixUsername(field: String): ValidationRule<String> {
        val usernameRegex = Regex("^[a-zA-Z0-9_.-]+$")
        return ValidationRule { value ->
            val result = ValidationRuleBuilder<String>()
                .addRule(notBlank(field))
                .addRule(stringLength(field, max = 32))
                .addRule(regexPattern(field, usernameRegex, "$field contains invalid characters"))
                .build()
            result.validate(value)
        }
    }
    
    /**
     * Rule that validates an SSH fingerprint format.
     */
    fun sshFingerprint(field: String): ValidationRule<String> {
        val hexFingerprintRegex = Regex("^[A-Fa-f0-9:]+$")
        val sha256FingerprintRegex = Regex("^SHA256:[A-Za-z0-9+/=]+$")
        
        return ValidationRule { value ->
            when {
                value.isBlank() -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field cannot be blank")
                )
                hexFingerprintRegex.matches(value) || sha256FingerprintRegex.matches(value) -> 
                    ValidationResult.Success
                else -> ValidationResult.Failure(
                    ValidationError.fieldError(field, "$field must be in hex:colon or SHA256:base64 format")
                )
            }
        }
    }
    
    /**
     * Rule that validates an entity name format.
     */
    fun entityName(field: String): ValidationRule<String> {
        val entityNameRegex = Regex("^[a-zA-Z0-9\\s\\-_()\\[\\]{}]+$")
        return ValidationRule { value ->
            val result = ValidationRuleBuilder<String>()
                .addRule(notBlank(field))
                .addRule(stringLength(field, max = 100))
                .addRule(regexPattern(field, entityNameRegex, "$field contains invalid characters"))
                .build()
            result.validate(value)
        }
    }
}
