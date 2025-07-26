package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.data.repository.SecureDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for SSH Identity business rules and operations.
 *
 * This service extracts complex business logic from SshIdentityService to improve
 * maintainability and separation of concerns. It handles:
 * - SSH key parsing and fingerprint generation
 * - Key format detection and conversion
 * - Duplicate key detection
 * - Key encryption and decryption operations
 * - Business rule validation
 * - Usage analytics and statistics
 */
@Singleton
class SshIdentityDomainService
    @Inject
    constructor(
        private val repository: SecureDataRepository,
        private val sshKeyParser: SshKeyParser,
        private val sshKeyEncryption: SshKeyEncryption,
    ) {
        companion object {
            private const val TAG = "SshIdentityDomainService"
            private const val MAX_IDENTITIES_PER_USER = 50
            private const val MIN_KEY_SIZE_BITS = 2048
        }

        /**
         * Processes and validates an SSH key for creation.
         *
         * @param privateKeyData Raw private key data
         * @param keyFormat Format of the private key data
         * @param passphrase Optional passphrase for encrypted keys
         * @return ServiceResult with processed key data or error
         */
        suspend fun processPrivateKeyForCreation(
            privateKeyData: String,
            keyFormat: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
            passphrase: String? = null,
        ): ServiceResult<ProcessedKeyData> {
            Log.d(TAG, "Processing private key for creation")

            return try {
                withContext(Dispatchers.Default) {
                    // Parse and validate the private key
                    val keyPair =
                        sshKeyParser.parsePrivateKey(privateKeyData, keyFormat, passphrase)
                            ?: return@withContext serviceFailure("Failed to parse private key")

                    // Validate key strength
                    val keyStrengthValidation = validateKeyStrength(keyPair.public)
                    if (keyStrengthValidation.isFailure()) {
                        return@withContext keyStrengthValidation
                    }

                    // Generate public key fingerprint
                    val fingerprint = generateFingerprint(keyPair.public)

                    // Check for duplicate fingerprint
                    val duplicateValidation = validateFingerprintUniqueness(fingerprint)
                    if (duplicateValidation.isFailure()) {
                        return@withContext duplicateValidation
                    }

                    // Encrypt the private key for storage
                    val encryptedPrivateKey = sshKeyEncryption.encryptPrivateKey(keyPair.private)

                    val processedData =
                        ProcessedKeyData(
                            encryptedPrivateKey = encryptedPrivateKey,
                            publicKeyFingerprint = fingerprint,
                            keyType = detectKeyType(keyPair.public),
                            keySize = getKeySize(keyPair.public),
                        )

                    serviceSuccess(processedData)
                }
            } catch (e: SecurityManagerException) {
                Log.e(TAG, "Security error processing private key", e)
                serviceFailure("Security error processing private key: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid key format", e)
                serviceFailure("Invalid key format: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process private key", e)
                serviceFailure("Failed to process private key: ${e.message}")
            }
        }

        /**
         * Validates key strength and security requirements.
         *
         * @param publicKey The public key to validate
         * @return ServiceResult indicating success or validation errors
         */
        private fun validateKeyStrength(publicKey: PublicKey): ServiceResult<Unit> =
            try {
                val keySize = getKeySize(publicKey)
                if (keySize < MIN_KEY_SIZE_BITS) {
                    serviceFailure("Key size ($keySize bits) is below minimum requirement ($MIN_KEY_SIZE_BITS bits)")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: ServiceException.CryptographyException) {
                Log.e(TAG, "Cryptography error validating key strength", e)
                serviceFailure("Cryptography error validating key strength: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate key strength", e)
                serviceFailure("Failed to validate key strength: ${e.message}")
            }

        /**
         * Validates fingerprint uniqueness.
         *
         * @param fingerprint The fingerprint to validate
         * @return ServiceResult indicating success or validation errors
         */
        private suspend fun validateFingerprintUniqueness(fingerprint: String): ServiceResult<Unit> =
            try {
                val existingIdentities = repository.getAllSshIdentities()
                if (existingIdentities.any { it.publicKeyFingerprint == fingerprint }) {
                    serviceFailure("SSH key with this fingerprint already exists")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate fingerprint uniqueness", e)
                serviceFailure("Failed to validate fingerprint uniqueness: ${e.message}")
            }

        /**
         * Validates identity limits for a user.
         *
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateIdentityLimits(): ServiceResult<Unit> =
            try {
                val existingIdentities = repository.getAllSshIdentities()
                if (existingIdentities.size >= MAX_IDENTITIES_PER_USER) {
                    val message = "Maximum number of SSH identities ($MAX_IDENTITIES_PER_USER) reached"
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate identity limits", e)
                serviceFailure("Failed to validate identity limits: ${e.message}")
            }

        /**
         * Validates if an SSH identity can be deleted.
         *
         * @param identity The SSH identity to validate for deletion
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForDeletion(identity: SshIdentity): ServiceResult<Unit> =
            try {
                // Check if identity is in use by server profiles
                val serverProfiles = repository.getServerProfilesForIdentity(identity.id)
                if (serverProfiles.isNotEmpty()) {
                    val serverNames = serverProfiles.map { it.name }
                    val message = "SSH Identity is in use by server profiles: ${serverNames.joinToString(", ")}"
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate identity for deletion", e)
                serviceFailure("Failed to validate identity for deletion: ${e.message}")
            }

        /**
         * Generates a fingerprint for a public key.
         *
         * @param publicKey The public key
         * @return The generated fingerprint
         */
        private fun generateFingerprint(publicKey: PublicKey): String =
            try {
                val keyBytes = publicKey.encoded
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(keyBytes)

                // Convert to hex format with colons
                hash.joinToString(":") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate fingerprint", e)
                throw e
            }

        /**
         * Detects the type of an SSH key.
         *
         * @param publicKey The public key
         * @return The key type string
         */
        private fun detectKeyType(publicKey: PublicKey): String =
            when (publicKey) {
                is RSAPublicKey -> "ssh-rsa"
                else -> {
                    val algorithm = publicKey.algorithm.lowercase()
                    when {
                        algorithm.contains("rsa") -> "ssh-rsa"
                        algorithm.contains("dsa") -> "ssh-dss"
                        algorithm.contains("ecdsa") -> "ecdsa-sha2"
                        algorithm.contains("ed25519") -> "ssh-ed25519"
                        else -> "unknown"
                    }
                }
            }

        /**
         * Gets the size of an SSH key in bits.
         *
         * @param publicKey The public key
         * @return The key size in bits
         */
        private fun getKeySize(publicKey: PublicKey): Int =
            when (publicKey) {
                is RSAPublicKey -> publicKey.modulus.bitLength()
                else -> {
                    // For other key types, we'd need specific implementations
                    // This is a simplified version
                    try {
                        val encoded = publicKey.encoded
                        when {
                            encoded.size > 512 -> 4096
                            encoded.size > 256 -> 2048
                            else -> 1024
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to determine key size", e)
                        0
                    }
                }
            }

        /**
         * Exports an SSH identity to various formats.
         *
         * @param identity The SSH identity to export
         * @param format The export format
         * @param includePrivateKey Whether to include private key in export
         * @return ServiceResult with exported data or error
         */
        suspend fun exportIdentity(
            identity: SshIdentity,
            format: ExportFormat,
            includePrivateKey: Boolean = false,
        ): ServiceResult<String> {
            Log.d(TAG, "Exporting SSH identity: ${identity.name}")

            return try {
                withContext(Dispatchers.Default) {
                    when (format) {
                        ExportFormat.OPENSSH -> {
                            if (includePrivateKey) {
                                // Decrypt and format private key
                                val privateKey = sshKeyEncryption.decryptPrivateKey(identity.encryptedPrivateKey)
                                val formattedKey = sshKeyParser.formatPrivateKey(privateKey, SshKeyFormat.OPENSSH)
                                serviceSuccess(formattedKey)
                            } else {
                                // Generate public key from fingerprint
                                serviceFailure("Public key export not yet implemented")
                            }
                        }
                        ExportFormat.PEM -> {
                            if (includePrivateKey) {
                                val privateKey = sshKeyEncryption.decryptPrivateKey(identity.encryptedPrivateKey)
                                val formattedKey = sshKeyParser.formatPrivateKey(privateKey, SshKeyFormat.PEM)
                                serviceSuccess(formattedKey)
                            } else {
                                serviceFailure("Public key export not yet implemented")
                            }
                        }
                        ExportFormat.JSON -> {
                            // Export as JSON metadata (without private key data)
                            val jsonData =
                                buildString {
                                    append("{\n")
                                    append("  \"name\": \"${identity.name}\",\n")
                                    append("  \"fingerprint\": \"${identity.publicKeyFingerprint}\",\n")
                                    append("  \"description\": \"${identity.description ?: ""}\",\n")
                                    append("  \"created\": ${identity.createdAt}\n")
                                    append("}")
                                }
                            serviceSuccess(jsonData)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export SSH identity", e)
                serviceFailure("Failed to export SSH identity: ${e.message}")
            }
        }

        /**
         * Gets usage statistics for SSH identities.
         *
         * @return ServiceResult with usage statistics
         */
        suspend fun getUsageStatistics(): ServiceResult<SshIdentityUsageStats> =
            try {
                val identities = repository.getAllSshIdentities()
                val serverProfiles = repository.getAllServerProfiles()

                val stats =
                    SshIdentityUsageStats(
                        totalIdentities = identities.size,
                        usedIdentities =
                            identities.count { identity ->
                                serverProfiles.any { it.sshIdentityId == identity.id }
                            },
                        unusedIdentities =
                            identities.count { identity ->
                                serverProfiles.none { it.sshIdentityId == identity.id }
                            },
                        keyTypeDistribution =
                            identities
                                .groupBy { identity ->
                                    // This would need actual key type detection from stored data
                                    "unknown"
                                }.mapValues { it.value.size },
                        avgKeyAge = calculateAverageKeyAge(identities),
                    )

                serviceSuccess(stats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get usage statistics", e)
                serviceFailure("Failed to get usage statistics: ${e.message}")
            }

        /**
         * Calculates the average age of SSH keys in days.
         */
        private fun calculateAverageKeyAge(identities: List<SshIdentity>): Long {
            if (identities.isEmpty()) return 0

            val currentTime = System.currentTimeMillis()
            val totalAge = identities.sumOf { currentTime - it.createdAt }
            return (totalAge / identities.size) / (24 * 60 * 60 * 1000) // Convert to days
        }

        /**
         * Data class for processed SSH key information.
         */
        data class ProcessedKeyData(
            val encryptedPrivateKey: String,
            val publicKeyFingerprint: String,
            val keyType: String,
            val keySize: Int,
        )

        /**
         * Data class for SSH identity usage statistics.
         */
        data class SshIdentityUsageStats(
            val totalIdentities: Int,
            val usedIdentities: Int,
            val unusedIdentities: Int,
            val keyTypeDistribution: Map<String, Int>,
            val avgKeyAge: Long,
        )

        /**
         * Enum for export formats.
         */
        enum class ExportFormat {
            OPENSSH,
            PEM,
            JSON,
        }
    }

/**
 * Enum for SSH key formats.
 */
enum class SshKeyFormat {
    AUTO_DETECT,
    OPENSSH,
    PEM,
    PKCS8,
}
