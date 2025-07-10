package com.pocketagent.mobile.data.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Custom serializer for UUID objects
 */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

/**
 * Custom serializer for Instant objects (Java 8 time)
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * Custom serializer for Duration objects
 */
object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Duration {
        return Duration.parse(decoder.decodeString())
    }
}

/**
 * Custom serializer for Long timestamps that can handle both epoch milliseconds and ISO strings
 */
object FlexibleTimestampSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleTimestamp", PrimitiveKind.LONG)
    
    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
    
    override fun deserialize(decoder: Decoder): Long {
        return try {
            // Try to decode as long first
            decoder.decodeLong()
        } catch (e: Exception) {
            try {
                // If that fails, try to parse as ISO string
                val isoString = decoder.decodeString()
                Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                // If both fail, return current time
                System.currentTimeMillis()
            }
        }
    }
}

/**
 * Custom serializer for encrypted data that ensures proper handling
 */
object EncryptedDataSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EncryptedData", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: String) {
        // Ensure encrypted data is properly escaped
        encoder.encodeString(value)
    }
    
    override fun deserialize(decoder: Decoder): String {
        val decoded = decoder.decodeString()
        // Validate that it looks like encrypted data (basic check)
        require(decoded.isNotEmpty()) { "Encrypted data cannot be empty" }
        return decoded
    }
}

/**
 * Custom serializer for SSH key fingerprints with validation
 */
object SshFingerprintSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SshFingerprint", PrimitiveKind.STRING)
    
    private val fingerprintRegex = Regex("^[A-Fa-f0-9:]+$")
    
    override fun serialize(encoder: Encoder, value: String) {
        require(value.matches(fingerprintRegex)) { "Invalid SSH fingerprint format: $value" }
        encoder.encodeString(value)
    }
    
    override fun deserialize(decoder: Decoder): String {
        val decoded = decoder.decodeString()
        require(decoded.matches(fingerprintRegex)) { "Invalid SSH fingerprint format: $decoded" }
        return decoded
    }
}

/**
 * Custom serializer for hostname validation
 */
object HostnameSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Hostname", PrimitiveKind.STRING)
    
    private val hostnameRegex = Regex("^[a-zA-Z0-9.-]+$")
    
    override fun serialize(encoder: Encoder, value: String) {
        require(value.matches(hostnameRegex)) { "Invalid hostname format: $value" }
        encoder.encodeString(value)
    }
    
    override fun deserialize(decoder: Decoder): String {
        val decoded = decoder.decodeString()
        require(decoded.matches(hostnameRegex)) { "Invalid hostname format: $decoded" }
        return decoded
    }
}

/**
 * Custom serializer for username validation
 */
object UsernameSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Username", PrimitiveKind.STRING)
    
    private val usernameRegex = Regex("^[a-zA-Z0-9_-]+$")
    
    override fun serialize(encoder: Encoder, value: String) {
        require(value.matches(usernameRegex)) { "Invalid username format: $value" }
        encoder.encodeString(value)
    }
    
    override fun deserialize(decoder: Decoder): String {
        val decoded = decoder.decodeString()
        require(decoded.matches(usernameRegex)) { "Invalid username format: $decoded" }
        return decoded
    }
}

/**
 * Custom serializer for port numbers with validation
 */
object PortSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Port", PrimitiveKind.INT)
    
    override fun serialize(encoder: Encoder, value: Int) {
        require(value in 1..65535) { "Port must be between 1 and 65535, got: $value" }
        encoder.encodeInt(value)
    }
    
    override fun deserialize(decoder: Decoder): Int {
        val decoded = decoder.decodeInt()
        require(decoded in 1..65535) { "Port must be between 1 and 65535, got: $decoded" }
        return decoded
    }
}