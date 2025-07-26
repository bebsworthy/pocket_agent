package com.pocketagent.common.constants

/**
 * Cryptographic constants used throughout the application.
 *
 * Centralizes all cryptography-related magic numbers including
 * key sizes, encryption parameters, and security thresholds.
 */
object CryptoConstants {
    // AES encryption
    const val AES_KEY_SIZE_BITS = 256
    const val AES_BLOCK_SIZE_BYTES = 16

    // RSA encryption
    const val RSA_MIN_KEY_SIZE_BITS = 2048
    const val RSA_RECOMMENDED_KEY_SIZE_BITS = 4096
    const val RSA_MAX_KEY_SIZE_BITS = 8192

    // Elliptic curve encryption
    const val EC_MIN_KEY_SIZE_BITS = 256
    const val EC_RECOMMENDED_KEY_SIZE_BITS = 384
    const val EC_MAX_KEY_SIZE_BITS = 521

    // Ed25519 encryption
    const val ED25519_KEY_SIZE_BITS = 256

    // DSA encryption
    const val DSA_MIN_KEY_SIZE_BITS = 1024
    const val DSA_RECOMMENDED_KEY_SIZE_BITS = 2048
    const val DSA_MAX_KEY_SIZE_BITS = 3072

    // GCM encryption parameters
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BYTES = 16
    const val GCM_TAG_LENGTH_BITS = 128

    // Salt and key derivation
    const val DEFAULT_SALT_LENGTH_BYTES = 32
    const val PBKDF2_MIN_ITERATIONS = 10000
    const val PBKDF2_RECOMMENDED_ITERATIONS = 100000

    // Biometric authentication
    const val DEFAULT_BIOMETRIC_VALIDITY_SECONDS = 300 // 5 minutes

    // Data format versioning
    const val ENCRYPTED_DATA_VERSION = 1
    const val BACKUP_VERSION = 1
    const val STORAGE_VERSION = 1

    // Header and metadata sizes
    const val ENCRYPTED_DATA_HEADER_SIZE_BYTES = 8
    const val CHECKSUM_SIZE_BYTES = 32 // SHA-256 checksum

    // Compression thresholds
    const val COMPRESSION_THRESHOLD_BYTES = 1024 // 1KB

    // Binary data manipulation
    const val INT_BYTE_SIZE = 4
    const val BYTE_SHIFT_24 = 24
    const val BYTE_SHIFT_16 = 16
    const val BYTE_SHIFT_8 = 8
    const val UNSIGNED_BYTE_MASK = 0xFF

    // Android API levels for security features
    const val API_LEVEL_STRONGBOX = 28
    const val API_LEVEL_BIOMETRIC = 23
    const val API_LEVEL_KEYSTORE = 18

    // Key rotation and expiry
    const val DEFAULT_KEY_ROTATION_INTERVAL_DAYS = 30
    const val DEFAULT_KEY_EXPIRY_WARNING_DAYS = 30

    // Security validation thresholds
    const val MIN_PASSWORD_LENGTH = 8
    const val RECOMMENDED_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 128

    // Certificate validation
    const val CERTIFICATE_VALIDITY_BUFFER_DAYS = 30
    const val MAX_CERTIFICATE_CHAIN_LENGTH = 10

    // Random data generation
    const val SECURE_RANDOM_SEED_SIZE = 32
    const val NONCE_SIZE_BYTES = 16

    // Hash algorithm sizes (in bits)
    const val SHA1_HASH_SIZE_BITS = 160
    const val SHA256_HASH_SIZE_BITS = 256
    const val SHA512_HASH_SIZE_BITS = 512

    // Hash algorithm sizes (in bytes)
    const val SHA1_HASH_SIZE_BYTES = 20
    const val SHA256_HASH_SIZE_BYTES = 32
    const val SHA512_HASH_SIZE_BYTES = 64
}
