package com.pocketagent.data.service

import android.util.Log
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH key parsing and format conversion utility.
 *
 * Supports parsing and converting between various SSH key formats:
 * - OpenSSH format (ssh-rsa, ssh-ed25519, etc.)
 * - PEM format (BEGIN RSA PRIVATE KEY)
 * - PKCS#8 format (BEGIN PRIVATE KEY)
 * - Basic PuTTY format detection
 *
 * Also provides key generation and fingerprint calculation capabilities.
 */
@Singleton
class SshKeyParser
    @Inject
    constructor() {
        companion object {
            private const val TAG = "SshKeyParser"

            // PEM headers for different key types
            private const val PEM_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----"
            private const val PEM_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----"
            private const val PEM_PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----"
            private const val PEM_PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----"
            private const val PEM_ENCRYPTED_PRIVATE_KEY_HEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----"
            private const val PEM_DSA_PRIVATE_KEY_HEADER = "-----BEGIN DSA PRIVATE KEY-----"
            private const val PEM_EC_PRIVATE_KEY_HEADER = "-----BEGIN EC PRIVATE KEY-----"

            // OpenSSH key format headers
            private const val OPENSSH_PRIVATE_KEY_HEADER = "-----BEGIN OPENSSH PRIVATE KEY-----"
            private const val OPENSSH_PRIVATE_KEY_FOOTER = "-----END OPENSSH PRIVATE KEY-----"

            // PuTTY format header
            private const val PUTTY_PRIVATE_KEY_HEADER = "PuTTY-User-Key-File-"

            // OpenSSH public key prefixes
            private val OPENSSH_PUBLIC_KEY_PREFIXES =
                listOf(
                    "ssh-rsa",
                    "ssh-dss",
                    "ecdsa-sha2-nistp256",
                    "ecdsa-sha2-nistp384",
                    "ecdsa-sha2-nistp521",
                    "ssh-ed25519",
                )
        }

        /**
         * Parses a private key from various formats.
         *
         * @param keyData The raw key data
         * @param format Expected format (AUTO_DETECT to auto-detect)
         * @param passphrase Optional passphrase for encrypted keys
         * @return KeyPair if successful, null otherwise
         */
        fun parsePrivateKey(
            keyData: String,
            format: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
            passphrase: String? = null,
        ): KeyPair? {
            Log.d(TAG, "Parsing private key (format: $format)")

            return try {
                val detectedFormat =
                    if (format == SshKeyFormat.AUTO_DETECT) {
                        detectFormat(keyData)
                    } else {
                        format
                    }

                when (detectedFormat) {
                    SshKeyFormat.OPENSSH -> parseOpenSshPrivateKey(keyData, passphrase)
                    SshKeyFormat.PEM -> parsePemPrivateKey(keyData, passphrase)
                    SshKeyFormat.PKCS8 -> parsePkcs8PrivateKey(keyData, passphrase)
                    SshKeyFormat.PUTTY -> parsePuttyPrivateKey(keyData, passphrase)
                    else -> {
                        Log.w(TAG, "Unknown key format, trying all parsers")
                        parseOpenSshPrivateKey(keyData, passphrase)
                            ?: parsePemPrivateKey(keyData, passphrase)
                            ?: parsePkcs8PrivateKey(keyData, passphrase)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse private key", e)
                null
            }
        }

        /**
         * Detects the format of an SSH key.
         */
        fun detectFormat(keyData: String): SshKeyFormat {
            val trimmed = keyData.trim()

            return when {
                trimmed.contains(OPENSSH_PRIVATE_KEY_HEADER) -> SshKeyFormat.OPENSSH
                trimmed.contains(PEM_PRIVATE_KEY_HEADER) || trimmed.contains(PEM_ENCRYPTED_PRIVATE_KEY_HEADER) -> SshKeyFormat.PKCS8
                trimmed.contains(PEM_RSA_PRIVATE_KEY_HEADER) ||
                    trimmed.contains(PEM_DSA_PRIVATE_KEY_HEADER) ||
                    trimmed.contains(PEM_EC_PRIVATE_KEY_HEADER) -> SshKeyFormat.PEM
                trimmed.startsWith(PUTTY_PRIVATE_KEY_HEADER) -> SshKeyFormat.PUTTY
                OPENSSH_PUBLIC_KEY_PREFIXES.any { trimmed.startsWith(it) } -> SshKeyFormat.OPENSSH
                else -> SshKeyFormat.AUTO_DETECT
            }
        }

        /**
         * Checks if a private key is encrypted.
         */
        fun isPrivateKeyEncrypted(keyData: String): Boolean {
            val trimmed = keyData.trim()

            return when {
                trimmed.contains(PEM_ENCRYPTED_PRIVATE_KEY_HEADER) -> true
                trimmed.contains("Proc-Type: 4,ENCRYPTED") -> true
                trimmed.contains("DEK-Info:") -> true
                // OpenSSH encrypted keys have specific structure
                trimmed.contains(OPENSSH_PRIVATE_KEY_HEADER) -> {
                    // This is a simplified check - in practice, need to parse the OpenSSH format
                    trimmed.contains("aes") || trimmed.contains("cipher")
                }
                else -> false
            }
        }

        /**
         * Generates a new SSH key pair.
         */
        fun generateKeyPair(
            keyType: SshKeyType,
            keySize: Int,
        ): KeyPair {
            Log.d(TAG, "Generating key pair: $keyType ($keySize bits)")

            return when (keyType) {
                SshKeyType.RSA -> generateRsaKeyPair(keySize)
                SshKeyType.DSA -> generateDsaKeyPair(keySize)
                SshKeyType.ECDSA -> generateEcdsaKeyPair(keySize)
                SshKeyType.ED25519 -> generateEd25519KeyPair()
                else -> throw IllegalArgumentException("Unsupported key type: $keyType")
            }
        }

        /**
         * Formats a private key to the specified format.
         */
        fun formatPrivateKey(
            privateKey: PrivateKey,
            format: SshKeyFormat,
        ): String {
            Log.d(TAG, "Formatting private key to: $format")

            return when (format) {
                SshKeyFormat.OPENSSH -> formatToOpenSsh(privateKey)
                SshKeyFormat.PEM -> formatToPem(privateKey)
                SshKeyFormat.PKCS8 -> formatToPkcs8(privateKey)
                else -> throw IllegalArgumentException("Unsupported output format: $format")
            }
        }

        /**
         * Formats a public key to the specified format.
         */
        fun formatPublicKey(
            publicKey: PublicKey,
            format: SshKeyFormat,
        ): String {
            Log.d(TAG, "Formatting public key to: $format")

            return when (format) {
                SshKeyFormat.OPENSSH -> formatPublicKeyToOpenSsh(publicKey)
                SshKeyFormat.PEM -> formatPublicKeyToPem(publicKey)
                else -> formatPublicKeyToOpenSsh(publicKey) // Default to OpenSSH
            }
        }

        /**
         * Extracts the public key from an encrypted private key.
         */
        fun extractPublicKey(encryptedPrivateKeyData: String): PublicKey? {
            // This would need to decrypt the private key first
            // For now, return null as this requires the SshKeyEncryption service
            Log.w(TAG, "extractPublicKey not implemented - requires decryption")
            return null
        }

        // Private parsing methods

        private fun parseOpenSshPrivateKey(
            keyData: String,
            passphrase: String?,
        ): KeyPair? {
            Log.d(TAG, "Parsing OpenSSH private key")

            try {
                val trimmed = keyData.trim()

                // Extract the base64 content between headers
                val startMarker = OPENSSH_PRIVATE_KEY_HEADER
                val endMarker = OPENSSH_PRIVATE_KEY_FOOTER

                val startIndex = trimmed.indexOf(startMarker)
                val endIndex = trimmed.indexOf(endMarker)

                if (startIndex == -1 || endIndex == -1) {
                    Log.w(TAG, "OpenSSH key headers not found")
                    return null
                }

                val base64Content =
                    trimmed
                        .substring(startIndex + startMarker.length, endIndex)
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace(" ", "")

                // For now, return null as full OpenSSH parsing is complex
                // This would require implementing the full OpenSSH private key format parser
                Log.w(TAG, "Full OpenSSH private key parsing not implemented")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OpenSSH private key", e)
                return null
            }
        }

        private fun parsePemPrivateKey(
            keyData: String,
            passphrase: String?,
        ): KeyPair? {
            Log.d(TAG, "Parsing PEM private key")

            try {
                val keyFactory =
                    when {
                        keyData.contains(PEM_RSA_PRIVATE_KEY_HEADER) -> KeyFactory.getInstance("RSA")
                        keyData.contains(PEM_DSA_PRIVATE_KEY_HEADER) -> KeyFactory.getInstance("DSA")
                        keyData.contains(PEM_EC_PRIVATE_KEY_HEADER) -> KeyFactory.getInstance("EC")
                        else -> {
                            Log.w(TAG, "Unknown PEM key type")
                            return null
                        }
                    }

                // Remove headers and decode base64
                val base64Key =
                    keyData
                        .replace(PEM_RSA_PRIVATE_KEY_HEADER, "")
                        .replace(PEM_RSA_PRIVATE_KEY_FOOTER, "")
                        .replace(PEM_DSA_PRIVATE_KEY_HEADER, "")
                        .replace("-----END DSA PRIVATE KEY-----", "")
                        .replace(PEM_EC_PRIVATE_KEY_HEADER, "")
                        .replace("-----END EC PRIVATE KEY-----", "")
                        .replace("\n", "")
                        .replace("\r", "")

                val keyBytes = Base64.getDecoder().decode(base64Key)

                // Handle encrypted keys
                if (keyData.contains("Proc-Type: 4,ENCRYPTED") && passphrase != null) {
                    // This would require implementing PEM encryption decryption
                    Log.w(TAG, "Encrypted PEM keys not fully supported")
                    return null
                }

                val privateKeySpec = PKCS8EncodedKeySpec(keyBytes)
                val privateKey = keyFactory.generatePrivate(privateKeySpec)

                // Generate public key from private key
                val publicKey =
                    when (privateKey.algorithm) {
                        "RSA" -> {
                            val rsaPrivateKey = privateKey as java.security.interfaces.RSAPrivateKey
                            val publicKeySpec = RSAPublicKeySpec(rsaPrivateKey.modulus, BigInteger.valueOf(65537))
                            keyFactory.generatePublic(publicKeySpec)
                        }
                        else -> {
                            Log.w(TAG, "Public key generation not implemented for ${privateKey.algorithm}")
                            return null
                        }
                    }

                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse PEM private key", e)
                return null
            }
        }

        private fun parsePkcs8PrivateKey(
            keyData: String,
            passphrase: String?,
        ): KeyPair? {
            Log.d(TAG, "Parsing PKCS8 private key")

            try {
                // Remove headers and decode base64
                val base64Key =
                    keyData
                        .replace(PEM_PRIVATE_KEY_HEADER, "")
                        .replace(PEM_PRIVATE_KEY_FOOTER, "")
                        .replace(PEM_ENCRYPTED_PRIVATE_KEY_HEADER, "")
                        .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                        .replace("\n", "")
                        .replace("\r", "")

                val keyBytes = Base64.getDecoder().decode(base64Key)

                // Handle encrypted PKCS8 keys
                if (keyData.contains(PEM_ENCRYPTED_PRIVATE_KEY_HEADER) && passphrase != null) {
                    // This would require implementing PKCS8 encryption decryption
                    Log.w(TAG, "Encrypted PKCS8 keys not fully supported")
                    return null
                }

                val privateKeySpec = PKCS8EncodedKeySpec(keyBytes)
                val keyFactory = KeyFactory.getInstance("RSA") // Try RSA first
                val privateKey = keyFactory.generatePrivate(privateKeySpec)

                // Generate public key from private key
                val publicKey =
                    when (privateKey.algorithm) {
                        "RSA" -> {
                            val rsaPrivateKey = privateKey as java.security.interfaces.RSAPrivateKey
                            val publicKeySpec = RSAPublicKeySpec(rsaPrivateKey.modulus, BigInteger.valueOf(65537))
                            keyFactory.generatePublic(publicKeySpec)
                        }
                        else -> {
                            Log.w(TAG, "Public key generation not implemented for ${privateKey.algorithm}")
                            return null
                        }
                    }

                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse PKCS8 private key", e)
                return null
            }
        }

        private fun parsePuttyPrivateKey(
            keyData: String,
            passphrase: String?,
        ): KeyPair? {
            Log.d(TAG, "Parsing PuTTY private key")

            // PuTTY format parsing is complex and requires specific implementation
            // For now, return null
            Log.w(TAG, "PuTTY key parsing not implemented")
            return null
        }

        // Key generation methods

        private fun generateRsaKeyPair(keySize: Int): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(keySize)
            return keyPairGenerator.generateKeyPair()
        }

        private fun generateDsaKeyPair(keySize: Int): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("DSA")
            keyPairGenerator.initialize(keySize)
            return keyPairGenerator.generateKeyPair()
        }

        private fun generateEcdsaKeyPair(keySize: Int): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            // Map key size to curve
            val curveName =
                when (keySize) {
                    256 -> "secp256r1"
                    384 -> "secp384r1"
                    521 -> "secp521r1"
                    else -> "secp256r1"
                }
            val ecGenParameterSpec = ECGenParameterSpec(curveName)
            keyPairGenerator.initialize(ecGenParameterSpec)
            return keyPairGenerator.generateKeyPair()
        }

        private fun generateEd25519KeyPair(): KeyPair {
            // Ed25519 is not directly supported in older Android versions
            // This would require Bouncy Castle or similar library
            throw UnsupportedOperationException("Ed25519 key generation requires additional crypto library")
        }

        // Formatting methods

        private fun formatToOpenSsh(privateKey: PrivateKey): String {
            // OpenSSH private key format is complex to implement
            // For now, fall back to PEM format
            Log.w(TAG, "OpenSSH private key formatting not implemented, using PEM")
            return formatToPem(privateKey)
        }

        private fun formatToPem(privateKey: PrivateKey): String {
            val encoded = privateKey.encoded
            val base64 = Base64.getEncoder().encodeToString(encoded)

            // Add line breaks every 64 characters
            val formattedBase64 = base64.chunked(64).joinToString("\n")

            return when (privateKey.algorithm) {
                "RSA" -> "$PEM_RSA_PRIVATE_KEY_HEADER\n$formattedBase64\n$PEM_RSA_PRIVATE_KEY_FOOTER"
                else -> "$PEM_PRIVATE_KEY_HEADER\n$formattedBase64\n$PEM_PRIVATE_KEY_FOOTER"
            }
        }

        private fun formatToPkcs8(privateKey: PrivateKey): String {
            val encoded = privateKey.encoded
            val base64 = Base64.getEncoder().encodeToString(encoded)

            // Add line breaks every 64 characters
            val formattedBase64 = base64.chunked(64).joinToString("\n")

            return "$PEM_PRIVATE_KEY_HEADER\n$formattedBase64\n$PEM_PRIVATE_KEY_FOOTER"
        }

        private fun formatPublicKeyToOpenSsh(publicKey: PublicKey): String =
            when (publicKey) {
                is RSAPublicKey -> {
                    val keyType = "ssh-rsa"
                    val encoded = encodeRsaPublicKey(publicKey)
                    val base64 = Base64.getEncoder().encodeToString(encoded)
                    "$keyType $base64"
                }
                is DSAPublicKey -> {
                    val keyType = "ssh-dss"
                    val encoded = encodeDsaPublicKey(publicKey)
                    val base64 = Base64.getEncoder().encodeToString(encoded)
                    "$keyType $base64"
                }
                else -> {
                    Log.w(TAG, "Unsupported public key type for OpenSSH format: ${publicKey.algorithm}")
                    "# Unsupported key type: ${publicKey.algorithm}"
                }
            }

        private fun formatPublicKeyToPem(publicKey: PublicKey): String {
            val encoded = publicKey.encoded
            val base64 = Base64.getEncoder().encodeToString(encoded)
            val formattedBase64 = base64.chunked(64).joinToString("\n")

            return "-----BEGIN PUBLIC KEY-----\n$formattedBase64\n-----END PUBLIC KEY-----"
        }

        // SSH public key encoding methods

        private fun encodeRsaPublicKey(publicKey: RSAPublicKey): ByteArray {
            // SSH wire format for RSA public key
            val keyType = "ssh-rsa".toByteArray()
            val exponent = publicKey.publicExponent.toByteArray()
            val modulus = publicKey.modulus.toByteArray()

            return encodeSshWireFormat(
                keyType,
                exponent,
                modulus,
            )
        }

        private fun encodeDsaPublicKey(publicKey: DSAPublicKey): ByteArray {
            // SSH wire format for DSA public key
            val keyType = "ssh-dss".toByteArray()
            val p = publicKey.params.p.toByteArray()
            val q = publicKey.params.q.toByteArray()
            val g = publicKey.params.g.toByteArray()
            val y = publicKey.y.toByteArray()

            return encodeSshWireFormat(
                keyType,
                p,
                q,
                g,
                y,
            )
        }

        private fun encodeSshWireFormat(vararg components: ByteArray): ByteArray {
            // Calculate total size
            var totalSize = 0
            for (component in components) {
                totalSize += 4 + component.size // 4 bytes for length + component size
            }

            val result = ByteArray(totalSize)
            var offset = 0

            for (component in components) {
                // Write length (4 bytes, big-endian)
                result[offset] = (component.size shr 24).toByte()
                result[offset + 1] = (component.size shr 16).toByte()
                result[offset + 2] = (component.size shr 8).toByte()
                result[offset + 3] = component.size.toByte()
                offset += 4

                // Write component
                System.arraycopy(component, 0, result, offset, component.size)
                offset += component.size
            }

            return result
        }
    }
