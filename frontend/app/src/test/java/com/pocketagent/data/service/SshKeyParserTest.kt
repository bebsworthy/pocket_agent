package com.pocketagent.data.service

import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SshKeyParser.
 *
 * Tests SSH key parsing, format detection, key generation,
 * and format conversion functionality.
 */
@DisplayName("SSH Key Parser Tests")
class SshKeyParserTest {
    private lateinit var parser: SshKeyParser
    private val testKeyPair = generateTestKeyPair()

    @BeforeEach
    fun setup() {
        parser = SshKeyParser()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // Format Detection Tests

    @Test
    @DisplayName("Detect OpenSSH Private Key Format")
    fun testDetectFormat_OpenSshPrivateKey() {
        // Arrange
        val openSshKey =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAA
            -----END OPENSSH PRIVATE KEY-----
            """.trimIndent()

        // Act
        val format = parser.detectFormat(openSshKey)

        // Assert
        assertEquals(SshKeyFormat.OPENSSH, format)
    }

    @Test
    @DisplayName("Detect PEM RSA Private Key Format")
    fun testDetectFormat_PemRsaPrivateKey() {
        // Arrange
        val pemRsaKey =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef
            -----END RSA PRIVATE KEY-----
            """.trimIndent()

        // Act
        val format = parser.detectFormat(pemRsaKey)

        // Assert
        assertEquals(SshKeyFormat.PEM, format)
    }

    @Test
    @DisplayName("Detect PKCS8 Private Key Format")
    fun testDetectFormat_Pkcs8PrivateKey() {
        // Arrange
        val pkcs8Key =
            """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASC
            -----END PRIVATE KEY-----
            """.trimIndent()

        // Act
        val format = parser.detectFormat(pkcs8Key)

        // Assert
        assertEquals(SshKeyFormat.PKCS8, format)
    }

    @Test
    @DisplayName("Detect Encrypted PKCS8 Private Key Format")
    fun testDetectFormat_EncryptedPkcs8PrivateKey() {
        // Arrange
        val encryptedPkcs8Key =
            """
            -----BEGIN ENCRYPTED PRIVATE KEY-----
            MIIFLTBXBgkqhkiG9w0BBQ0wSjApBgkq
            -----END ENCRYPTED PRIVATE KEY-----
            """.trimIndent()

        // Act
        val format = parser.detectFormat(encryptedPkcs8Key)

        // Assert
        assertEquals(SshKeyFormat.PKCS8, format)
    }

    @Test
    @DisplayName("Detect PuTTY Private Key Format")
    fun testDetectFormat_PuttyPrivateKey() {
        // Arrange
        val puttyKey =
            """
            PuTTY-User-Key-File-2: ssh-rsa
            Encryption: aes256-cbc
            Comment: test@example.com
            Public-Lines: 6
            AAAAB3NzaC1yc2EAAAADAQABAAABAQDc
            """.trimIndent()

        // Act
        val format = parser.detectFormat(puttyKey)

        // Assert
        assertEquals(SshKeyFormat.PUTTY, format)
    }

    @Test
    @DisplayName("Detect OpenSSH Public Key Format")
    fun testDetectFormat_OpenSshPublicKey() {
        // Arrange
        val publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ test@example.com"

        // Act
        val format = parser.detectFormat(publicKey)

        // Assert
        assertEquals(SshKeyFormat.OPENSSH, format)
    }

    @Test
    @DisplayName("Detect Unknown Format")
    fun testDetectFormat_Unknown() {
        // Arrange
        val unknownKey = "This is not a valid SSH key format"

        // Act
        val format = parser.detectFormat(unknownKey)

        // Assert
        assertEquals(SshKeyFormat.AUTO_DETECT, format)
    }

    // Encryption Detection Tests

    @Test
    @DisplayName("Detect Encrypted PEM Key")
    fun testIsPrivateKeyEncrypted_EncryptedPem() {
        // Arrange
        val encryptedPemKey =
            """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: AES-128-CBC,1234567890ABCDEF

            encrypted_key_data_here
            -----END RSA PRIVATE KEY-----
            """.trimIndent()

        // Act
        val isEncrypted = parser.isPrivateKeyEncrypted(encryptedPemKey)

        // Assert
        assertTrue(isEncrypted)
    }

    @Test
    @DisplayName("Detect Encrypted PKCS8 Key")
    fun testIsPrivateKeyEncrypted_EncryptedPkcs8() {
        // Arrange
        val encryptedPkcs8Key =
            """
            -----BEGIN ENCRYPTED PRIVATE KEY-----
            MIIFLTBXBgkqhkiG9w0BBQ0wSjApBgkq
            -----END ENCRYPTED PRIVATE KEY-----
            """.trimIndent()

        // Act
        val isEncrypted = parser.isPrivateKeyEncrypted(encryptedPkcs8Key)

        // Assert
        assertTrue(isEncrypted)
    }

    @Test
    @DisplayName("Detect Unencrypted Key")
    fun testIsPrivateKeyEncrypted_Unencrypted() {
        // Arrange
        val unencryptedKey =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef
            -----END RSA PRIVATE KEY-----
            """.trimIndent()

        // Act
        val isEncrypted = parser.isPrivateKeyEncrypted(unencryptedKey)

        // Assert
        assertFalse(isEncrypted)
    }

    // Key Generation Tests

    @Test
    @DisplayName("Generate RSA Key Pair")
    fun testGenerateKeyPair_Rsa() {
        // Act
        val keyPair = parser.generateKeyPair(SshKeyType.RSA, 2048)

        // Assert
        assertNotNull(keyPair)
        assertEquals("RSA", keyPair.public.algorithm)
        assertEquals("RSA", keyPair.private.algorithm)

        // Check key size
        val rsaPublicKey = keyPair.public as RSAPublicKey
        assertEquals(2048, rsaPublicKey.modulus.bitLength())
    }

    @Test
    @DisplayName("Generate DSA Key Pair")
    fun testGenerateKeyPair_Dsa() {
        // Act
        val keyPair = parser.generateKeyPair(SshKeyType.DSA, 1024)

        // Assert
        assertNotNull(keyPair)
        assertEquals("DSA", keyPair.public.algorithm)
        assertEquals("DSA", keyPair.private.algorithm)
    }

    @Test
    @DisplayName("Generate ECDSA Key Pair")
    fun testGenerateKeyPair_Ecdsa() {
        // Act
        val keyPair = parser.generateKeyPair(SshKeyType.ECDSA, 256)

        // Assert
        assertNotNull(keyPair)
        assertEquals("EC", keyPair.public.algorithm)
        assertEquals("EC", keyPair.private.algorithm)
    }

    @Test
    @DisplayName("Generate Ed25519 Key Pair - Not Supported")
    fun testGenerateKeyPair_Ed25519_NotSupported() {
        // Act & Assert
        assertFailsWith<UnsupportedOperationException> {
            parser.generateKeyPair(SshKeyType.ED25519, 256)
        }
    }

    @Test
    @DisplayName("Generate Key Pair - Unknown Type")
    fun testGenerateKeyPair_UnknownType() {
        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            parser.generateKeyPair(SshKeyType.UNKNOWN, 2048)
        }
    }

    // Key Formatting Tests

    @Test
    @DisplayName("Format Private Key to PEM")
    fun testFormatPrivateKey_ToPem() {
        // Act
        val formatted = parser.formatPrivateKey(testKeyPair.private, SshKeyFormat.PEM)

        // Assert
        assertTrue(formatted.contains("-----BEGIN RSA PRIVATE KEY-----"))
        assertTrue(formatted.contains("-----END RSA PRIVATE KEY-----"))
    }

    @Test
    @DisplayName("Format Private Key to PKCS8")
    fun testFormatPrivateKey_ToPkcs8() {
        // Act
        val formatted = parser.formatPrivateKey(testKeyPair.private, SshKeyFormat.PKCS8)

        // Assert
        assertTrue(formatted.contains("-----BEGIN PRIVATE KEY-----"))
        assertTrue(formatted.contains("-----END PRIVATE KEY-----"))
    }

    @Test
    @DisplayName("Format Private Key - Unsupported Format")
    fun testFormatPrivateKey_UnsupportedFormat() {
        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            parser.formatPrivateKey(testKeyPair.private, SshKeyFormat.PUTTY)
        }
    }

    @Test
    @DisplayName("Format Public Key to OpenSSH")
    fun testFormatPublicKey_ToOpenSsh() {
        // Act
        val formatted = parser.formatPublicKey(testKeyPair.public, SshKeyFormat.OPENSSH)

        // Assert
        assertTrue(formatted.startsWith("ssh-rsa"))
        assertTrue(formatted.contains(" "))
    }

    @Test
    @DisplayName("Format Public Key to PEM")
    fun testFormatPublicKey_ToPem() {
        // Act
        val formatted = parser.formatPublicKey(testKeyPair.public, SshKeyFormat.PEM)

        // Assert
        assertTrue(formatted.contains("-----BEGIN PUBLIC KEY-----"))
        assertTrue(formatted.contains("-----END PUBLIC KEY-----"))
    }

    // Private Key Parsing Tests (Limited due to complexity)

    @Test
    @DisplayName("Parse Private Key - Auto Detect Format")
    fun testParsePrivateKey_AutoDetect() {
        // Arrange
        val mockKeyData =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef
            -----END RSA PRIVATE KEY-----
            """.trimIndent()

        // Act
        val result = parser.parsePrivateKey(mockKeyData, SshKeyFormat.AUTO_DETECT)

        // Assert
        // Since full parsing is not implemented, result should be null
        assertNull(result)
    }

    @Test
    @DisplayName("Parse Private Key - Invalid Data")
    fun testParsePrivateKey_InvalidData() {
        // Arrange
        val invalidKeyData = "This is not a valid key"

        // Act
        val result = parser.parsePrivateKey(invalidKeyData)

        // Assert
        assertNull(result)
    }

    @Test
    @DisplayName("Parse Private Key - Empty Data")
    fun testParsePrivateKey_EmptyData() {
        // Act
        val result = parser.parsePrivateKey("")

        // Assert
        assertNull(result)
    }

    // SSH Wire Format Encoding Tests

    @Test
    @DisplayName("Format RSA Public Key - Wire Format Structure")
    fun testFormatRsaPublicKey_WireFormat() {
        // Arrange
        val rsaPublicKey = testKeyPair.public as RSAPublicKey

        // Act
        val formatted = parser.formatPublicKey(rsaPublicKey, SshKeyFormat.OPENSSH)

        // Assert
        assertTrue(formatted.startsWith("ssh-rsa"))

        // Should have three parts: type, base64-data, optional comment
        val parts = formatted.split(" ")
        assertTrue(parts.size >= 2)
        assertEquals("ssh-rsa", parts[0])

        // Base64 part should be valid
        try {
            java.util.Base64
                .getDecoder()
                .decode(parts[1])
        } catch (e: Exception) {
            fail("Base64 decoding should not fail: ${e.message}")
        }
    }

    // Error Handling Tests

    @Test
    @DisplayName("Handle Null Key Data")
    fun testHandleNullKeyData() {
        // Act & Assert
        assertFailsWith<NullPointerException> {
            parser.parsePrivateKey(null as String?)
        }
    }

    @Test
    @DisplayName("Handle Malformed Key Headers")
    fun testHandleMalformedKeyHeaders() {
        // Arrange
        val malformedKey =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef
            -----END WRONG FOOTER-----
            """.trimIndent()

        // Act
        val result = parser.parsePrivateKey(malformedKey)

        // Assert
        assertNull(result)
    }

    @Test
    @DisplayName("Handle Key Data With Extra Whitespace")
    fun testHandleKeyDataWithWhitespace() {
        // Arrange
        val keyWithWhitespace =
            """

            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdef

            -----END RSA PRIVATE KEY-----

            """.trimIndent()

        // Act
        val format = parser.detectFormat(keyWithWhitespace)

        // Assert
        assertEquals(SshKeyFormat.PEM, format)
    }

    // Helper Methods

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }
}

/**
 * Test utilities for SSH key parser testing.
 */
object SshKeyParserTestUtils {
    /**
     * Creates test RSA private key data in PEM format.
     */
    fun createTestRsaPemKey(encrypted: Boolean = false): String =
        if (encrypted) {
            """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: AES-128-CBC,1234567890ABCDEF1234567890ABCDEF

            MIIEpAIBAAKCAQEA1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHI
            JKLMNOPQRSTUVWXYZ1234567890abcdefghijklmnopqrstuvwxyzABCDEF
            -----END RSA PRIVATE KEY-----
            """.trimIndent()
        } else {
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHI
            JKLMNOPQRSTUVWXYZ1234567890abcdefghijklmnopqrstuvwxyzABCDEF
            -----END RSA PRIVATE KEY-----
            """.trimIndent()
        }

    /**
     * Creates test OpenSSH private key data.
     */
    fun createTestOpenSshKey(): String =
        """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAFwAAAAdzc2gtcn
        NhAAAAAwEAAQAAAQEA1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP
        -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

    /**
     * Creates test PKCS8 private key data.
     */
    fun createTestPkcs8Key(): String =
        """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDc1234567890abcd
        efghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefgh
        -----END PRIVATE KEY-----
        """.trimIndent()

    /**
     * Creates test public key data in OpenSSH format.
     */
    fun createTestOpenSshPublicKey(): String =
        "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDC1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ test@example.com"

    /**
     * Creates test PuTTY private key data.
     */
    fun createTestPuttyKey(): String =
        """
        PuTTY-User-Key-File-2: ssh-rsa
        Encryption: none
        Comment: test@example.com
        Public-Lines: 6
        AAAAB3NzaC1yc2EAAAADAQABAAABAQDC1234567890abcdefghijklmnopqrstuvwx
        yzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefghijklmnopqrstuvwxyzAB
        Private-Lines: 14
        AAABAQCc1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTU
        VWXYZabcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUV
        Private-MAC: 1234567890abcdef1234567890abcdef1234567890abcdef
        """.trimIndent()
}
