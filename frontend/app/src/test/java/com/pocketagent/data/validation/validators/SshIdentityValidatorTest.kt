package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.validation.ValidationResult
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for SshIdentityValidator.
 */
class SshIdentityValidatorTest {
    
    private lateinit var validator: SshIdentityValidator
    
    @Before
    fun setup() {
        validator = SshIdentityValidator()
    }
    
    @Test
    fun `valid SSH identity should pass validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            description = "Test SSH identity",
            createdAt = System.currentTimeMillis(),
            lastUsedAt = null
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should succeed", result.isSuccess())
    }
    
    @Test
    fun `SSH identity with blank name should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain name error", 
            result.getErrorMessages().any { it.contains("name") && it.contains("blank") }
        )
    }
    
    @Test
    fun `SSH identity with too long name should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "a".repeat(101), // 101 characters
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain length error", 
            result.getErrorMessages().any { it.contains("100") }
        )
    }
    
    @Test
    fun `SSH identity with invalid name characters should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test@Key#Invalid",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain character error", 
            result.getErrorMessages().any { it.contains("invalid characters") }
        )
    }
    
    @Test
    fun `SSH identity with blank encrypted key should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain key error", 
            result.getErrorMessages().any { it.contains("private key") && it.contains("blank") }
        )
    }
    
    @Test
    fun `SSH identity with invalid fingerprint should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "invalid-fingerprint"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain fingerprint error", 
            result.getErrorMessages().any { it.contains("fingerprint") && it.contains("format") }
        )
    }
    
    @Test
    fun `SSH identity with valid hex fingerprint should pass validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "ab:cd:ef:12:34:56:78:90:ab:cd:ef:12:34:56:78:90"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should succeed", result.isSuccess())
    }
    
    @Test
    fun `SSH identity with too long description should fail validation`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            description = "a".repeat(501) // 501 characters
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain description error", 
            result.getErrorMessages().any { it.contains("description") && it.contains("500") }
        )
    }
    
    @Test
    fun `SSH identity with future timestamp should fail validation`() {
        val futureTime = System.currentTimeMillis() + 3600000 // 1 hour in future
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            createdAt = futureTime
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain future timestamp error", 
            result.getErrorMessages().any { it.contains("future") }
        )
    }
    
    @Test
    fun `validateForCreation should catch invalid lastUsedAt`() {
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            lastUsedAt = System.currentTimeMillis() // New identity shouldn't have lastUsedAt
        )
        
        val result = validator.validateForCreation(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain last used error", 
            result.getErrorMessages().any { it.contains("last used") }
        )
    }
    
    @Test
    fun `validateForUpdate should prevent ID changes`() {
        val original = SshIdentity(
            id = "original-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val updated = original.copy(id = "different-id")
        
        val result = validator.validateForUpdate(original, updated)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain ID change error", 
            result.getErrorMessages().any { it.contains("ID") && it.contains("changed") }
        )
    }
    
    @Test
    fun `validateForUpdate should prevent backwards lastUsedAt`() {
        val original = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            lastUsedAt = System.currentTimeMillis()
        )
        
        val updated = original.copy(lastUsedAt = original.lastUsedAt!! - 3600000) // 1 hour earlier
        
        val result = validator.validateForUpdate(original, updated)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain backwards timestamp error", 
            result.getErrorMessages().any { it.contains("backwards") }
        )
    }
    
    @Test
    fun `validateNameUniqueness should detect duplicates`() {
        val existingNames = listOf("Existing Key 1", "Existing Key 2", "Test SSH Key")
        
        val result = validator.validateNameUniqueness("Test SSH Key", existingNames)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate error", 
            result.getErrorMessages().any { it.contains("already exists") }
        )
    }
    
    @Test
    fun `validateNameUniqueness should allow unique names`() {
        val existingNames = listOf("Existing Key 1", "Existing Key 2")
        
        val result = validator.validateNameUniqueness("New SSH Key", existingNames)
        assertTrue("Validation should succeed", result.isSuccess())
    }
    
    @Test
    fun `validateFingerprintUniqueness should detect duplicates`() {
        val existingFingerprints = listOf(
            "SHA256:existing1", 
            "SHA256:existing2", 
            "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validateFingerprintUniqueness("SHA256:abcd1234efgh5678", existingFingerprints)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate error", 
            result.getErrorMessages().any { it.contains("already exists") }
        )
    }
    
    @Test
    fun `validateField should validate individual fields`() {
        // Valid name
        val nameResult = validator.validateField("name", "Valid Name")
        assertTrue("Name validation should succeed", nameResult.isSuccess())
        
        // Invalid name
        val invalidNameResult = validator.validateField("name", "")
        assertTrue("Invalid name validation should fail", invalidNameResult.isFailure())
        
        // Valid fingerprint
        val fingerprintResult = validator.validateField("publicKeyFingerprint", "SHA256:abcd1234")
        assertTrue("Fingerprint validation should succeed", fingerprintResult.isSuccess())
        
        // Unknown field should succeed
        val unknownResult = validator.validateField("unknownField", "value")
        assertTrue("Unknown field validation should succeed", unknownResult.isSuccess())
    }
    
    @Test
    fun `business rules should catch suspicious patterns`() {
        // Name looks like fingerprint
        val identity = SshIdentity(
            id = "test-id",
            name = "ab:cd:ef:12:34:56",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678"
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain fingerprint warning", 
            result.getErrorMessages().any { it.contains("fingerprint") }
        )
    }
    
    @Test
    fun `business rules should validate timestamp relationships`() {
        val createdAt = System.currentTimeMillis()
        val identity = SshIdentity(
            id = "test-id",
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data_here",
            publicKeyFingerprint = "SHA256:abcd1234efgh5678",
            createdAt = createdAt,
            lastUsedAt = createdAt - 3600000 // 1 hour before creation
        )
        
        val result = validator.validate(identity)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain timestamp order error", 
            result.getErrorMessages().any { it.contains("before creation") }
        )
    }
}
