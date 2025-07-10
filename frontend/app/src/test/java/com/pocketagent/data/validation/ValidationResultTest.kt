package com.pocketagent.data.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ValidationResult and related classes.
 */
class ValidationResultTest {
    @Test
    fun `success result should indicate success`() {
        val result = ValidationResult.Success

        assertTrue(result.isSuccess())
        assertFalse(result.isFailure())
        assertTrue(result.getErrorMessages().isEmpty())
        assertNull(result.getFirstErrorMessage())
    }

    @Test
    fun `failure result should indicate failure`() {
        val error = ValidationError("Test error", "field1")
        val result = ValidationResult.Failure(error)

        assertFalse(result.isSuccess())
        assertTrue(result.isFailure())
        assertEquals(listOf("Test error"), result.getErrorMessages())
        assertEquals("Test error", result.getFirstErrorMessage())
    }

    @Test
    fun `failure result with multiple errors`() {
        val errors =
            listOf(
                ValidationError("Error 1", "field1"),
                ValidationError("Error 2", "field2"),
            )
        val result = ValidationResult.Failure(errors)

        assertEquals(listOf("Error 1", "Error 2"), result.getErrorMessages())
        assertEquals("Error 1", result.getFirstErrorMessage())
    }

    @Test
    fun `get field errors should filter by field`() {
        val errors =
            listOf(
                ValidationError("Error 1", "field1"),
                ValidationError("Error 2", "field2"),
                ValidationError("Error 3", "field1"),
            )
        val result = ValidationResult.Failure(errors)

        val field1Errors = result.getFieldErrors("field1")
        assertEquals(2, field1Errors.size)
        assertEquals("Error 1", field1Errors[0].message)
        assertEquals("Error 3", field1Errors[1].message)
    }

    @Test
    fun `and operation should combine results correctly`() {
        val success = ValidationResult.Success
        val failure1 = ValidationResult.Failure(ValidationError("Error 1", "field1"))
        val failure2 = ValidationResult.Failure(ValidationError("Error 2", "field2"))

        // Success + Success = Success
        assertTrue((success and success).isSuccess())

        // Success + Failure = Failure
        val result1 = success and failure1
        assertTrue(result1.isFailure())
        assertEquals(1, (result1 as ValidationResult.Failure).errors.size)

        // Failure + Success = Failure
        val result2 = failure1 and success
        assertTrue(result2.isFailure())
        assertEquals(1, (result2 as ValidationResult.Failure).errors.size)

        // Failure + Failure = Combined Failure
        val result3 = failure1 and failure2
        assertTrue(result3.isFailure())
        assertEquals(2, (result3 as ValidationResult.Failure).errors.size)
    }

    @Test
    fun `map operation should work correctly`() {
        val success = ValidationResult.Success
        val failure = ValidationResult.Failure(ValidationError("Test error", "field1"))

        // Map on success should execute transform
        var transformExecuted = false
        val mappedSuccess =
            success.map {
                transformExecuted = true
            }
        assertTrue(transformExecuted)
        assertTrue(mappedSuccess.isSuccess())

        // Map on failure should not execute transform
        transformExecuted = false
        val mappedFailure =
            failure.map {
                transformExecuted = true
            }
        assertFalse(transformExecuted)
        assertTrue(mappedFailure.isFailure())
    }

    @Test
    fun `onSuccess should execute only on success`() {
        val success = ValidationResult.Success
        val failure = ValidationResult.Failure(ValidationError("Test error", "field1"))

        var actionExecuted = false

        success.onSuccess { actionExecuted = true }
        assertTrue(actionExecuted)

        actionExecuted = false
        failure.onSuccess { actionExecuted = true }
        assertFalse(actionExecuted)
    }

    @Test
    fun `onFailure should execute only on failure`() {
        val success = ValidationResult.Success
        val failure = ValidationResult.Failure(ValidationError("Test error", "field1"))

        var actionExecuted = false
        var receivedErrors: List<ValidationError>? = null

        success.onFailure { errors ->
            actionExecuted = true
            receivedErrors = errors
        }
        assertFalse(actionExecuted)

        failure.onFailure { errors ->
            actionExecuted = true
            receivedErrors = errors
        }
        assertTrue(actionExecuted)
        assertEquals(1, receivedErrors?.size)
    }
}

/**
 * Unit tests for ValidationError.
 */
class ValidationErrorTest {
    @Test
    fun `validation error should be created correctly`() {
        val error =
            ValidationError(
                message = "Test message",
                field = "testField",
                type = ValidationError.Type.FIELD_VALIDATION,
                code = "TEST_CODE",
            )

        assertEquals("Test message", error.message)
        assertEquals("testField", error.field)
        assertEquals(ValidationError.Type.FIELD_VALIDATION, error.type)
        assertEquals("TEST_CODE", error.code)
    }

    @Test
    fun `companion factory methods should work correctly`() {
        val fieldError = ValidationError.fieldError("field1", "Field error", "FIELD_CODE")
        assertEquals(ValidationError.Type.FIELD_VALIDATION, fieldError.type)
        assertEquals("field1", fieldError.field)

        val businessError = ValidationError.businessRuleError("Business error", null, "BUSINESS_CODE")
        assertEquals(ValidationError.Type.BUSINESS_RULE, businessError.type)
        assertNull(businessError.field)

        val relationshipError = ValidationError.relationshipError("Relationship error")
        assertEquals(ValidationError.Type.RELATIONSHIP_CONSTRAINT, relationshipError.type)

        val databaseError = ValidationError.databaseError("Database error")
        assertEquals(ValidationError.Type.DATABASE_CONSTRAINT, databaseError.type)

        val customError = ValidationError.customError("Custom error")
        assertEquals(ValidationError.Type.CUSTOM_RULE, customError.type)
    }
}

/**
 * Unit tests for ValidationResultBuilder.
 */
class ValidationResultBuilderTest {
    @Test
    fun `empty builder should produce success`() {
        val builder = ValidationResultBuilder()
        val result = builder.build()

        assertTrue(result.isSuccess())
        assertFalse(builder.hasErrors())
        assertEquals(0, builder.errorCount())
    }

    @Test
    fun `builder with errors should produce failure`() {
        val builder = ValidationResultBuilder()
        builder.addFieldError("field1", "Error 1")
        builder.addBusinessRuleError("Business error")

        val result = builder.build()

        assertTrue(result.isFailure())
        assertTrue(builder.hasErrors())
        assertEquals(2, builder.errorCount())

        val failure = result as ValidationResult.Failure
        assertEquals(2, failure.errors.size)
    }

    @Test
    fun `builder should add different error types correctly`() {
        val builder = ValidationResultBuilder()
        builder.addFieldError("field1", "Field error", "FIELD_CODE")
        builder.addBusinessRuleError("Business error", "field2", "BUSINESS_CODE")
        builder.addRelationshipError("Relationship error", "field3", "REL_CODE")
        builder.addDatabaseError("Database error", "field4", "DB_CODE")
        builder.addCustomError("Custom error", "field5", "CUSTOM_CODE")

        val result = builder.build() as ValidationResult.Failure
        assertEquals(5, result.errors.size)

        assertEquals(ValidationError.Type.FIELD_VALIDATION, result.errors[0].type)
        assertEquals(ValidationError.Type.BUSINESS_RULE, result.errors[1].type)
        assertEquals(ValidationError.Type.RELATIONSHIP_CONSTRAINT, result.errors[2].type)
        assertEquals(ValidationError.Type.DATABASE_CONSTRAINT, result.errors[3].type)
        assertEquals(ValidationError.Type.CUSTOM_RULE, result.errors[4].type)
    }

    @Test
    fun `builder should add results correctly`() {
        val builder = ValidationResultBuilder()
        val success = ValidationResult.Success
        val failure = ValidationResult.Failure(ValidationError("Test error", "field1"))

        builder.addResult(success)
        builder.addResult(failure)

        val result = builder.build() as ValidationResult.Failure
        assertEquals(1, result.errors.size)
        assertEquals("Test error", result.errors[0].message)
    }
}

/**
 * Unit tests for ValidationResultUtils.
 */
class ValidationResultUtilsTest {
    @Test
    fun `combine should work with multiple results`() {
        val success1 = ValidationResult.Success
        val success2 = ValidationResult.Success
        val failure1 = ValidationResult.Failure(ValidationError("Error 1", "field1"))
        val failure2 = ValidationResult.Failure(ValidationError("Error 2", "field2"))

        // All success
        val result1 = ValidationResultUtils.combine(success1, success2)
        assertTrue(result1.isSuccess())

        // Mixed success and failure
        val result2 = ValidationResultUtils.combine(success1, failure1, success2)
        assertTrue(result2.isFailure())
        assertEquals(1, (result2 as ValidationResult.Failure).errors.size)

        // Multiple failures
        val result3 = ValidationResultUtils.combine(failure1, failure2)
        assertTrue(result3.isFailure())
        assertEquals(2, (result3 as ValidationResult.Failure).errors.size)
    }

    @Test
    fun `combine with list should work correctly`() {
        val results =
            listOf(
                ValidationResult.Success,
                ValidationResult.Failure(ValidationError("Error 1", "field1")),
                ValidationResult.Failure(ValidationError("Error 2", "field2")),
            )

        val combined = ValidationResultUtils.combine(results)
        assertTrue(combined.isFailure())
        assertEquals(2, (combined as ValidationResult.Failure).errors.size)
    }

    @Test
    fun `factory methods should work correctly`() {
        val success = ValidationResultUtils.success()
        assertTrue(success.isSuccess())

        val failure1 = ValidationResultUtils.failure("Test error", "field1")
        assertTrue(failure1.isFailure())
        assertEquals("Test error", failure1.getFirstErrorMessage())

        val errors =
            listOf(
                ValidationError("Error 1", "field1"),
                ValidationError("Error 2", "field2"),
            )
        val failure2 = ValidationResultUtils.failure(errors)
        assertTrue(failure2.isFailure())
        assertEquals(2, (failure2 as ValidationResult.Failure).errors.size)
    }

    @Test
    fun `fromNullable should work correctly`() {
        val successResult = ValidationResultUtils.fromNullable("not null", "Error", "field1")
        assertTrue(successResult.isSuccess())

        val failureResult = ValidationResultUtils.fromNullable(null, "Error", "field1")
        assertTrue(failureResult.isFailure())
        assertEquals("Error", failureResult.getFirstErrorMessage())
    }

    @Test
    fun `fromCondition should work correctly`() {
        val successResult = ValidationResultUtils.fromCondition(true, "Error", "field1")
        assertTrue(successResult.isSuccess())

        val failureResult = ValidationResultUtils.fromCondition(false, "Error", "field1")
        assertTrue(failureResult.isFailure())
        assertEquals("Error", failureResult.getFirstErrorMessage())
    }

    @Test
    fun `fromTryCatch should work correctly`() {
        val successResult =
            ValidationResultUtils.fromTryCatch("field1") {
                // Do nothing - should succeed
            }
        assertTrue(successResult.isSuccess())

        val failureResult =
            ValidationResultUtils.fromTryCatch("field1") {
                throw IllegalArgumentException("Test error")
            }
        assertTrue(failureResult.isFailure())
        assertEquals("Test error", failureResult.getFirstErrorMessage())
    }
}
