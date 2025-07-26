package com.pocketagent.data.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides asynchronous validation capabilities for database constraints and remote validations.
 *
 * This class handles validation that requires database access or network calls,
 * such as uniqueness constraints, foreign key validation, and remote service validation.
 */
@Singleton
class AsyncValidator
    @Inject
    constructor() {
        /**
         * Validate multiple async rules in parallel.
         *
         * @param rules List of async validation rules to execute
         * @return Combined validation result
         */
        suspend fun validateParallel(vararg rules: suspend () -> ValidationResult): ValidationResult =
            withContext(Dispatchers.IO) {
                val results =
                    rules.map { rule ->
                        try {
                            rule()
                        } catch (e: Exception) {
                            ValidationResult.Failure(
                                ValidationError(
                                    "Async validation failed: ${e.message}",
                                    null,
                                    ValidationError.Type.INTERNAL,
                                ),
                            )
                        }
                    }

                ValidationResultUtils.combine(results)
            }

        /**
         * Validate multiple async rules sequentially (stops on first failure).
         *
         * @param rules List of async validation rules to execute
         * @return Validation result (stops on first failure)
         */
        suspend fun validateSequential(vararg rules: suspend () -> ValidationResult): ValidationResult {
            return withContext(Dispatchers.IO) {
                for (rule in rules) {
                    try {
                        val result = rule()
                        if (result.isFailure()) {
                            return@withContext result
                        }
                    } catch (e: Exception) {
                        return@withContext ValidationResult.Failure(
                            ValidationError(
                                "Async validation failed: ${e.message}",
                                null,
                                ValidationError.Type.INTERNAL,
                            ),
                        )
                    }
                }
                ValidationResult.Success
            }
        }

        /**
         * Create an async validation rule with timeout.
         *
         * @param timeoutMs Timeout in milliseconds
         * @param rule The async rule to execute
         * @return Async validation rule with timeout
         */
        fun withTimeout(
            timeoutMs: Long,
            rule: suspend () -> ValidationResult,
        ): suspend () -> ValidationResult =
            {
                try {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        rule()
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    ValidationResult.Failure(
                        ValidationError(
                            "Validation timed out after ${timeoutMs}ms",
                            null,
                            ValidationError.Type.INTERNAL,
                            "VALIDATION_TIMEOUT",
                        ),
                    )
                } catch (e: Exception) {
                    ValidationResult.Failure(
                        ValidationError(
                            "Async validation failed: ${e.message}",
                            null,
                            ValidationError.Type.INTERNAL,
                        ),
                    )
                }
            }

        /**
         * Create an async validation rule with retry logic.
         *
         * @param maxRetries Maximum number of retries
         * @param delayMs Delay between retries in milliseconds
         * @param rule The async rule to execute
         * @return Async validation rule with retry logic
         */
        fun withRetry(
            maxRetries: Int,
            delayMs: Long,
            rule: suspend () -> ValidationResult,
        ): suspend () -> ValidationResult {
            return retryRule@{
                var lastError: Exception? = null

                for (attempt in 0..maxRetries) {
                    try {
                        val result = rule()
                        if (result.isSuccess()) {
                            return@retryRule result
                        } else if (attempt == maxRetries) {
                            return@retryRule result
                        }
                    } catch (e: Exception) {
                        lastError = e
                        if (attempt < maxRetries) {
                            kotlinx.coroutines.delay(delayMs)
                        }
                    }
                }

                ValidationResult.Failure(
                    ValidationError(
                        "Async validation failed after $maxRetries retries: ${lastError?.message}",
                        null,
                        ValidationError.Type.INTERNAL,
                        "VALIDATION_RETRY_EXHAUSTED",
                    ),
                )
            }
        }

        /**
         * Create a cached async validation rule that caches results for a specified duration.
         *
         * @param cacheKeyProvider Function to generate cache key from input
         * @param cacheDurationMs Cache duration in milliseconds
         * @param rule The async rule to execute
         * @return Cached async validation rule
         */
        fun <T> withCache(
            cacheKeyProvider: (T) -> String,
            cacheDurationMs: Long,
            rule: suspend (T) -> ValidationResult,
        ): suspend (T) -> ValidationResult {
            val cache = mutableMapOf<String, Pair<ValidationResult, Long>>()

            return { input ->
                val cacheKey = cacheKeyProvider(input)
                val now = System.currentTimeMillis()

                val cachedResult = cache[cacheKey]
                if (cachedResult != null && (now - cachedResult.second) < cacheDurationMs) {
                    cachedResult.first
                } else {
                    try {
                        val result = rule(input)
                        cache[cacheKey] = Pair(result, now)
                        result
                    } catch (e: Exception) {
                        ValidationResult.Failure(
                            ValidationError(
                                "Cached async validation failed: ${e.message}",
                                null,
                                ValidationError.Type.INTERNAL,
                            ),
                        )
                    }
                }
            }
        }
    }

/**
 * Interface for async validation rules that require database access.
 */
interface DatabaseValidationRule<T> {
    /**
     * Validate the given value against database constraints.
     *
     * @param value The value to validate
     * @return ValidationResult indicating success or failure
     */
    suspend fun validateAgainstDatabase(value: T): ValidationResult
}

/**
 * Interface for async validation rules that require network access.
 */
interface NetworkValidationRule<T> {
    /**
     * Validate the given value against network services.
     *
     * @param value The value to validate
     * @return ValidationResult indicating success or failure
     */
    suspend fun validateAgainstNetwork(value: T): ValidationResult
}

/**
 * Builder for creating complex async validation workflows.
 */
class AsyncValidationWorkflowBuilder {
    private val steps = mutableListOf<suspend () -> ValidationResult>()
    private var failFast = true

    /**
     * Set whether to fail fast (stop on first error) or continue and collect all errors.
     */
    fun failFast(enabled: Boolean): AsyncValidationWorkflowBuilder {
        this.failFast = enabled
        return this
    }

    /**
     * Add a validation step.
     */
    fun addStep(step: suspend () -> ValidationResult): AsyncValidationWorkflowBuilder {
        steps.add(step)
        return this
    }

    /**
     * Add a conditional validation step.
     */
    fun addConditionalStep(
        condition: suspend () -> Boolean,
        step: suspend () -> ValidationResult,
    ): AsyncValidationWorkflowBuilder {
        steps.add {
            if (condition()) {
                step()
            } else {
                ValidationResult.Success
            }
        }
        return this
    }

    /**
     * Add a parallel validation group.
     */
    fun addParallelGroup(vararg parallelSteps: suspend () -> ValidationResult): AsyncValidationWorkflowBuilder {
        steps.add {
            val results =
                parallelSteps.map { step ->
                    try {
                        step()
                    } catch (e: Exception) {
                        ValidationResult.Failure(
                            ValidationError(
                                "Parallel validation step failed: ${e.message}",
                                null,
                                ValidationError.Type.INTERNAL,
                            ),
                        )
                    }
                }
            ValidationResultUtils.combine(results)
        }
        return this
    }

    /**
     * Build the async validation workflow.
     */
    fun build(): suspend () -> ValidationResult =
        {
            if (failFast) {
                var result = ValidationResult.Success as ValidationResult
                for (step in steps) {
                    result = step()
                    if (result.isFailure()) {
                        break
                    }
                }
                result
            } else {
                val results =
                    steps.map { step ->
                        try {
                            step()
                        } catch (e: Exception) {
                            ValidationResult.Failure(
                                ValidationError(
                                    "Workflow step failed: ${e.message}",
                                    null,
                                    ValidationError.Type.INTERNAL,
                                ),
                            )
                        }
                    }
                ValidationResultUtils.combine(results)
            }
        }
}

/*
 * Extension functions for async validation.
 */

/**
 * Convert a sync validation rule to an async one.
 */
fun <T> ValidationRule<T>.toAsync(): AsyncValidationRule<T> =
    AsyncValidationRule { value ->
        withContext(Dispatchers.Default) {
            this@toAsync.validate(value)
        }
    }

/**
 * Combine async validation rules.
 */
suspend fun combineAsync(vararg rules: suspend () -> ValidationResult): ValidationResult {
    val results =
        rules.map { rule ->
            try {
                rule()
            } catch (e: Exception) {
                ValidationResult.Failure(
                    ValidationError(
                        "Async validation failed: ${e.message}",
                        null,
                        ValidationError.Type.INTERNAL,
                    ),
                )
            }
        }
    return ValidationResultUtils.combine(results)
}

/**
 * Execute async validation with fallback.
 */
suspend fun validateWithFallback(
    primary: suspend () -> ValidationResult,
    fallback: suspend () -> ValidationResult,
): ValidationResult =
    try {
        val result = primary()
        if (result.isSuccess()) {
            result
        } else {
            fallback()
        }
    } catch (e: Exception) {
        try {
            fallback()
        } catch (fallbackException: Exception) {
            ValidationResult.Failure(
                ValidationError(
                    "Both primary and fallback validations failed: ${e.message}, ${fallbackException.message}",
                    null,
                    ValidationError.Type.INTERNAL,
                ),
            )
        }
    }
