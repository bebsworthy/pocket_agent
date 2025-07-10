package com.pocketagent.data.service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*

/**
 * Unit tests for SshKeyEncryption.
 * 
 * Tests encryption/decryption functionality, master key management,
 * and error handling scenarios.
 */
@DisplayName("SSH Key Encryption Tests")
class SshKeyEncryptionTest {

    @MockK
    private lateinit var mockKeyStore: KeyStore
    
    @MockK
    private lateinit var mockSecretKey: SecretKey
    
    @MockK
    private lateinit var mockCipher: Cipher
    
    @MockK
    private lateinit var mockKeyGenerator: KeyGenerator

    private lateinit var encryption: SshKeyEncryption
    private val testKeyPair = generateTestKeyPair()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock static methods
        mockkStatic(KeyStore::class)
        mockkStatic(Cipher::class)
        mockkStatic(KeyGenerator::class)
        mockkStatic(Base64::class)
        
        setupDefaultMocks()
        
        encryption = SshKeyEncryption()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // Encryption Tests

    @Test
    @DisplayName("Encrypt Private Key - Success")
    fun testEncryptPrivateKey_Success() {
        // Arrange
        val expectedEncryptedData = "encrypted_test_data".toByteArray()
        val expectedBase64 = "ZW5jcnlwdGVkX3Rlc3RfZGF0YQ=="
        
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { mockCipher.doFinal(any()) } returns expectedEncryptedData
        every { Base64.getEncoder().encodeToString(any()) } returns expectedBase64

        // Act
        val result = encryption.encryptPrivateKey(testKeyPair.private)

        // Assert
        assertEquals(expectedBase64, result)
        verify { mockCipher.init(Cipher.ENCRYPT_MODE, mockSecretKey, any<GCMParameterSpec>()) }
        verify { mockCipher.doFinal(testKeyPair.private.encoded) }
    }

    @Test
    @DisplayName("Encrypt Private Key - Create New Master Key")
    fun testEncryptPrivateKey_CreateNewMasterKey() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns false
        every { KeyGenerator.getInstance(any(), any()) } returns mockKeyGenerator
        every { mockKeyGenerator.generateKey() } returns mockSecretKey
        every { mockCipher.doFinal(any()) } returns "encrypted_data".toByteArray()
        every { Base64.getEncoder().encodeToString(any()) } returns "base64_result"

        // Act
        val result = encryption.encryptPrivateKey(testKeyPair.private)

        // Assert
        assertNotNull(result)
        verify { mockKeyGenerator.init(any<KeyGenParameterSpec>()) }
        verify { mockKeyGenerator.generateKey() }
    }

    @Test
    @DisplayName("Encrypt Private Key - Encryption Failure")
    fun testEncryptPrivateKey_EncryptionFailure() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { mockCipher.doFinal(any()) } throws RuntimeException("Encryption failed")

        // Act & Assert
        assertFailsWith<SecurityException> {
            encryption.encryptPrivateKey(testKeyPair.private)
        }
    }

    // Decryption Tests

    @Test
    @DisplayName("Decrypt Private Key - Success")
    fun testDecryptPrivateKey_Success() {
        // Arrange
        val encryptedData = createTestEncryptedPackage()
        val base64EncryptedData = Base64.getEncoder().encodeToString(encryptedData)
        val decryptedKeyData = testKeyPair.private.encoded
        
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { Base64.getDecoder().decode(base64EncryptedData) } returns encryptedData
        every { mockCipher.doFinal(any()) } returns decryptedKeyData
        
        // Mock KeyFactory for private key reconstruction
        mockkStatic(KeyFactory::class)
        val mockKeyFactory = mockk<KeyFactory>()
        every { KeyFactory.getInstance("RSA") } returns mockKeyFactory
        every { mockKeyFactory.generatePrivate(any<PKCS8EncodedKeySpec>()) } returns testKeyPair.private

        // Act
        val result = encryption.decryptPrivateKey(base64EncryptedData)

        // Assert
        assertEquals(testKeyPair.private, result)
        verify { mockCipher.init(Cipher.DECRYPT_MODE, mockSecretKey, any<GCMParameterSpec>()) }
    }

    @Test
    @DisplayName("Decrypt Private Key - Invalid Base64")
    fun testDecryptPrivateKey_InvalidBase64() {
        // Arrange
        val invalidBase64 = "invalid_base64_data"
        every { Base64.getDecoder().decode(invalidBase64) } throws IllegalArgumentException("Invalid base64")

        // Act & Assert
        assertFailsWith<SecurityException> {
            encryption.decryptPrivateKey(invalidBase64)
        }
    }

    @Test
    @DisplayName("Decrypt Private Key - Invalid Version")
    fun testDecryptPrivateKey_InvalidVersion() {
        // Arrange
        val invalidVersionData = createTestEncryptedPackage(version = 99)
        val base64Data = Base64.getEncoder().encodeToString(invalidVersionData)
        
        every { Base64.getDecoder().decode(base64Data) } returns invalidVersionData

        // Act & Assert
        assertFailsWith<SecurityException> {
            encryption.decryptPrivateKey(base64Data)
        }
    }

    @Test
    @DisplayName("Decrypt Private Key - Decryption Failure")
    fun testDecryptPrivateKey_DecryptionFailure() {
        // Arrange
        val encryptedData = createTestEncryptedPackage()
        val base64EncryptedData = Base64.getEncoder().encodeToString(encryptedData)
        
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { Base64.getDecoder().decode(base64EncryptedData) } returns encryptedData
        every { mockCipher.doFinal(any()) } throws RuntimeException("Decryption failed")

        // Act & Assert
        assertFailsWith<SecurityException> {
            encryption.decryptPrivateKey(base64EncryptedData)
        }
    }

    // Master Key Management Tests

    @Test
    @DisplayName("Master Key Available - True")
    fun testIsMasterKeyAvailable_True() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey

        // Act
        val result = encryption.isMasterKeyAvailable()

        // Assert
        assertTrue(result)
    }

    @Test
    @DisplayName("Master Key Available - False")
    fun testIsMasterKeyAvailable_False() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns false

        // Act
        val result = encryption.isMasterKeyAvailable()

        // Assert
        assertFalse(result)
    }

    @Test
    @DisplayName("Master Key Available - Key Null")
    fun testIsMasterKeyAvailable_KeyNull() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns null

        // Act
        val result = encryption.isMasterKeyAvailable()

        // Assert
        assertFalse(result)
    }

    @Test
    @DisplayName("Master Key Available - Exception")
    fun testIsMasterKeyAvailable_Exception() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } throws RuntimeException("Keystore error")

        // Act
        val result = encryption.isMasterKeyAvailable()

        // Assert
        assertFalse(result)
    }

    @Test
    @DisplayName("Recreate Master Key - Success")
    fun testRecreateMasterKey_Success() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.deleteEntry(any()) } just Runs
        every { KeyGenerator.getInstance(any(), any()) } returns mockKeyGenerator
        every { mockKeyGenerator.generateKey() } returns mockSecretKey

        // Act
        val result = encryption.recreateMasterKey()

        // Assert
        assertTrue(result)
        verify { mockKeyStore.deleteEntry(any()) }
        verify { mockKeyGenerator.generateKey() }
    }

    @Test
    @DisplayName("Recreate Master Key - Failure")
    fun testRecreateMasterKey_Failure() {
        // Arrange
        every { mockKeyStore.containsAlias(any()) } throws RuntimeException("Keystore error")

        // Act
        val result = encryption.recreateMasterKey()

        // Assert
        assertFalse(result)
    }

    // Re-encryption Tests

    @Test
    @DisplayName("Re-encrypt Private Key - Success")
    fun testReencryptPrivateKey_Success() {
        // Arrange
        val oldEncryptedData = createTestEncryptedPackage()
        val oldBase64Data = Base64.getEncoder().encodeToString(oldEncryptedData)
        val decryptedKeyData = testKeyPair.private.encoded
        val newEncryptedData = "new_encrypted_data".toByteArray()
        val newBase64Data = "new_base64_data"
        
        val oldMasterKey = mockk<SecretKey>()
        
        // Mock decryption with old key
        every { Base64.getDecoder().decode(oldBase64Data) } returns oldEncryptedData
        every { Cipher.getInstance(any()) } returns mockCipher
        every { mockCipher.doFinal(any()) } returnsMany listOf(decryptedKeyData, newEncryptedData)
        
        // Mock key reconstruction
        mockkStatic(KeyFactory::class)
        val mockKeyFactory = mockk<KeyFactory>()
        every { KeyFactory.getInstance("RSA") } returns mockKeyFactory
        every { mockKeyFactory.generatePrivate(any<PKCS8EncodedKeySpec>()) } returns testKeyPair.private
        
        // Mock encryption with new key
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { Base64.getEncoder().encodeToString(any()) } returns newBase64Data

        // Act
        val result = encryption.reencryptPrivateKey(oldBase64Data, oldMasterKey)

        // Assert
        assertEquals(newBase64Data, result)
    }

    @Test
    @DisplayName("Re-encrypt Private Key - Failure")
    fun testReencryptPrivateKey_Failure() {
        // Arrange
        val oldBase64Data = "old_encrypted_data"
        val oldMasterKey = mockk<SecretKey>()
        
        every { Base64.getDecoder().decode(oldBase64Data) } throws IllegalArgumentException("Invalid data")

        // Act & Assert
        assertFailsWith<SecurityException> {
            encryption.reencryptPrivateKey(oldBase64Data, oldMasterKey)
        }
    }

    // Validation Tests

    @Test
    @DisplayName("Validate Encrypted Data - Valid")
    fun testValidateEncryptedData_Valid() {
        // Arrange
        val validEncryptedData = createTestEncryptedPackage()
        val base64Data = Base64.getEncoder().encodeToString(validEncryptedData)
        val decryptedKeyData = testKeyPair.private.encoded
        
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns mockSecretKey
        every { Base64.getDecoder().decode(base64Data) } returns validEncryptedData
        every { mockCipher.doFinal(any()) } returns decryptedKeyData
        
        // Mock key reconstruction
        mockkStatic(KeyFactory::class)
        val mockKeyFactory = mockk<KeyFactory>()
        every { KeyFactory.getInstance("RSA") } returns mockKeyFactory
        every { mockKeyFactory.generatePrivate(any<PKCS8EncodedKeySpec>()) } returns testKeyPair.private

        // Act
        val result = encryption.validateEncryptedData(base64Data)

        // Assert
        assertTrue(result)
    }

    @Test
    @DisplayName("Validate Encrypted Data - Invalid")
    fun testValidateEncryptedData_Invalid() {
        // Arrange
        val invalidData = "invalid_encrypted_data"
        every { Base64.getDecoder().decode(invalidData) } throws IllegalArgumentException("Invalid data")

        // Act
        val result = encryption.validateEncryptedData(invalidData)

        // Assert
        assertFalse(result)
    }

    // Utility Tests

    @Test
    @DisplayName("SSH Key Encryption Utils - Generate Salt")
    fun testGenerateSalt() {
        // Act
        val salt = SshKeyEncryptionUtils.generateSalt(32)

        // Assert
        assertEquals(32, salt.size)
        
        // Generate another salt and ensure they're different
        val salt2 = SshKeyEncryptionUtils.generateSalt(32)
        assertFalse(salt.contentEquals(salt2))
    }

    @Test
    @DisplayName("SSH Key Encryption Utils - Secure Wipe ByteArray")
    fun testSecureWipeByteArray() {
        // Arrange
        val data = "sensitive_data".toByteArray()
        val originalData = data.copyOf()

        // Act
        SshKeyEncryptionUtils.secureWipe(data)

        // Assert
        assertFalse(data.contentEquals(originalData))
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    @DisplayName("SSH Key Encryption Utils - Secure Wipe CharArray")
    fun testSecureWipeCharArray() {
        // Arrange
        val data = "sensitive_data".toCharArray()
        val originalData = data.copyOf()

        // Act
        SshKeyEncryptionUtils.secureWipe(data)

        // Assert
        assertFalse(data.contentEquals(originalData))
        assertTrue(data.all { it == 0.toChar() })
    }

    @Test
    @DisplayName("SSH Key Encryption Utils - Is Strong Encryption")
    fun testIsStrongEncryption() {
        // Act & Assert
        assertTrue(SshKeyEncryptionUtils.isStrongEncryption("AES", 256))
        assertFalse(SshKeyEncryptionUtils.isStrongEncryption("AES", 128))
        assertTrue(SshKeyEncryptionUtils.isStrongEncryption("RSA", 2048))
        assertFalse(SshKeyEncryptionUtils.isStrongEncryption("RSA", 1024))
        assertTrue(SshKeyEncryptionUtils.isStrongEncryption("EC", 256))
        assertFalse(SshKeyEncryptionUtils.isStrongEncryption("UNKNOWN", 256))
    }

    @Test
    @DisplayName("SSH Key Encryption Utils - Get Recommended Key Size")
    fun testGetRecommendedKeySize() {
        // Act & Assert
        assertEquals(256, SshKeyEncryptionUtils.getRecommendedKeySize("AES"))
        assertEquals(4096, SshKeyEncryptionUtils.getRecommendedKeySize("RSA"))
        assertEquals(384, SshKeyEncryptionUtils.getRecommendedKeySize("EC"))
        assertEquals(256, SshKeyEncryptionUtils.getRecommendedKeySize("ED25519"))
        assertEquals(256, SshKeyEncryptionUtils.getRecommendedKeySize("UNKNOWN"))
    }

    // Helper Methods

    private fun setupDefaultMocks() {
        // Mock KeyStore
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } just Runs
        
        // Mock Cipher
        every { Cipher.getInstance(any()) } returns mockCipher
        every { mockCipher.init(any<Int>(), any<SecretKey>(), any<GCMParameterSpec>()) } just Runs
        every { mockCipher.init(any<Int>(), any<SecretKey>()) } just Runs
        
        // Mock Base64
        val mockEncoder = mockk<Base64.Encoder>()
        val mockDecoder = mockk<Base64.Decoder>()
        every { Base64.getEncoder() } returns mockEncoder
        every { Base64.getDecoder() } returns mockDecoder
        every { mockEncoder.encodeToString(any()) } returns "encoded_string"
        every { mockDecoder.decode(any<String>()) } returns "decoded_bytes".toByteArray()
    }

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun createTestEncryptedPackage(version: Int = 1): ByteArray {
        val iv = ByteArray(12) { it.toByte() }
        val encryptedData = "test_encrypted_data".toByteArray()
        
        return byteArrayOf(
            // Version (4 bytes)
            (version shr 24).toByte(),
            (version shr 16).toByte(),
            (version shr 8).toByte(),
            version.toByte(),
            // IV length (4 bytes)
            0, 0, 0, 12,
            // IV (12 bytes)
            *iv,
            // Encrypted data
            *encryptedData
        )
    }
}

/**
 * Test utilities for SSH key encryption testing.
 */
object SshKeyEncryptionTestUtils {
    
    /**
     * Creates a test encrypted package with specified parameters.
     */
    fun createTestEncryptedPackage(
        version: Int = 1,
        ivSize: Int = 12,
        encryptedData: ByteArray = "test_encrypted_data".toByteArray()
    ): ByteArray {
        val iv = ByteArray(ivSize) { it.toByte() }
        
        return byteArrayOf(
            // Version (4 bytes)
            (version shr 24).toByte(),
            (version shr 16).toByte(),
            (version shr 8).toByte(),
            version.toByte(),
            // IV length (4 bytes)
            (ivSize shr 24).toByte(),
            (ivSize shr 16).toByte(),
            (ivSize shr 8).toByte(),
            ivSize.toByte(),
            // IV
            *iv,
            // Encrypted data
            *encryptedData
        )
    }
    
    /**
     * Creates a test secret key for testing.
     */
    fun createTestSecretKey(): SecretKey {
        return object : SecretKey {
            override fun getAlgorithm() = "AES"
            override fun getFormat() = "RAW"
            override fun getEncoded() = ByteArray(32) { it.toByte() }
        }
    }
    
    /**
     * Creates test key generation parameters.
     */
    fun createTestKeyGenParameterSpec(alias: String): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
    }
}