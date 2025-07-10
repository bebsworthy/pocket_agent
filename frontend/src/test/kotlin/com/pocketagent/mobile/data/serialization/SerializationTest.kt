package com.pocketagent.mobile.data.serialization

import com.pocketagent.mobile.data.model.*
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * Test class for serialization functionality
 */
class SerializationTest {
    
    private lateinit var jsonConfig: JsonConfig
    private lateinit var serializationUtils: SerializationUtils
    
    @Before
    fun setup() {
        jsonConfig = JsonConfig()
        serializationUtils = SerializationUtils(jsonConfig)
    }
    
    @Test
    fun testAppDataSerialization() {
        // Create test data
        val sshIdentity = SshIdentity(
            id = UUID.randomUUID().toString(),
            name = "Test SSH Key",
            encryptedPrivateKey = "encrypted_key_data",
            publicKeyFingerprint = "SHA256:abc123def456"
        )
        
        val serverProfile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = "Test Server",
            hostname = "example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = sshIdentity.id
        )
        
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = "Test Project",
            serverProfileId = serverProfile.id,
            projectPath = "/home/user/project"
        )
        
        val message = Message(
            id = UUID.randomUUID().toString(),
            content = "Test message",
            type = MessageType.USER_INPUT
        )
        
        val appData = AppData(
            version = 1,
            sshIdentities = listOf(sshIdentity),
            serverProfiles = listOf(serverProfile),
            projects = listOf(project),
            messages = mapOf(project.id to listOf(message))
        )
        
        // Test serialization
        val serialized = appData.toJson(jsonConfig.json)
        assertTrue("Serialization should succeed", serialized.isSuccess)
        
        // Test deserialization
        val jsonString = serialized.getOrThrow()
        val deserialized = jsonString.fromJson<AppData>(jsonConfig.json)
        assertTrue("Deserialization should succeed", deserialized.isSuccess)
        
        // Verify data integrity
        val deserializedData = deserialized.getOrThrow()
        assertEquals("SSH identities should match", appData.sshIdentities.size, deserializedData.sshIdentities.size)
        assertEquals("Server profiles should match", appData.serverProfiles.size, deserializedData.serverProfiles.size)
        assertEquals("Projects should match", appData.projects.size, deserializedData.projects.size)
        assertEquals("Messages should match", appData.messages.size, deserializedData.messages.size)
    }
    
    @Test
    fun testWebSocketMessageSerialization() {
        // Test AuthChallenge
        val authChallenge = AuthChallenge(
            nonce = "test_nonce",
            serverVersion = "1.0.0"
        )
        
        val serialized = serializationUtils.serializeWebSocketMessage(authChallenge)
        assertTrue("AuthChallenge serialization should succeed", serialized.isSuccess)
        
        val deserialized = serializationUtils.deserializeWebSocketMessage(serialized.getOrThrow())
        assertTrue("AuthChallenge deserialization should succeed", deserialized.isSuccess)
        
        val deserializedMessage = deserialized.getOrThrow()
        assertTrue("Should be AuthChallenge", deserializedMessage is AuthChallenge)
        assertEquals("Nonce should match", authChallenge.nonce, (deserializedMessage as AuthChallenge).nonce)
        assertEquals("Server version should match", authChallenge.serverVersion, deserializedMessage.serverVersion)
    }
    
    @Test
    fun testCommandMessageSerialization() {
        val commandMessage = CommandMessage(
            command = "git status",
            isShellCommand = true,
            workingDirectory = "/home/user/project"
        )
        
        val serialized = serializationUtils.serializeWebSocketMessage(commandMessage)
        assertTrue("CommandMessage serialization should succeed", serialized.isSuccess)
        
        val deserialized = serializationUtils.deserializeWebSocketMessage(serialized.getOrThrow())
        assertTrue("CommandMessage deserialization should succeed", deserialized.isSuccess)
        
        val deserializedMessage = deserialized.getOrThrow()
        assertTrue("Should be CommandMessage", deserializedMessage is CommandMessage)
        assertEquals("Command should match", commandMessage.command, (deserializedMessage as CommandMessage).command)
        assertEquals("Is shell command should match", commandMessage.isShellCommand, deserializedMessage.isShellCommand)
        assertEquals("Working directory should match", commandMessage.workingDirectory, deserializedMessage.workingDirectory)
    }
    
    @Test
    fun testDataValidation() {
        // Test invalid SSH identity
        assertThrows(IllegalArgumentException::class.java) {
            SshIdentity(
                name = "",
                encryptedPrivateKey = "key",
                publicKeyFingerprint = "invalid_fingerprint"
            )
        }
        
        // Test invalid server profile
        assertThrows(IllegalArgumentException::class.java) {
            ServerProfile(
                name = "Test",
                hostname = "example.com",
                port = 70000, // Invalid port
                username = "user",
                sshIdentityId = "id"
            )
        }
        
        // Test invalid project
        assertThrows(IllegalArgumentException::class.java) {
            Project(
                name = "",
                serverProfileId = "id",
                projectPath = "/path"
            )
        }
    }
    
    @Test
    fun testJsonValidation() {
        val validJson = """{"type": "test", "value": "data"}"""
        val invalidJson = """{"type": "test", "value":}"""
        
        assertTrue("Valid JSON should be recognized", validJson.isValidJson())
        assertFalse("Invalid JSON should be rejected", invalidJson.isValidJson())
    }
    
    @Test
    fun testCompactJsonSerialization() {
        val data = mapOf("key" to "value", "number" to 42)
        
        val compactResult = jsonConfig.toCompactJson(data)
        assertTrue("Compact serialization should succeed", compactResult.isSuccess)
        
        val compactJson = compactResult.getOrThrow()
        assertFalse("Compact JSON should not contain newlines", compactJson.contains("\n"))
        assertFalse("Compact JSON should not contain extra spaces", compactJson.contains("  "))
        
        val deserializedResult = jsonConfig.fromCompactJson<Map<String, Any>>(compactJson)
        assertTrue("Compact deserialization should succeed", deserializedResult.isSuccess)
    }
    
    @Test
    fun testStrictJsonValidation() {
        val validData = mapOf("key" to "value")
        val validationResult = jsonConfig.validateStrict(validData)
        assertTrue("Strict validation should succeed for valid data", validationResult.isSuccess)
    }
    
    @Test
    fun testErrorHandling() {
        val invalidJson = """{"invalid": json}"""
        
        val result = invalidJson.fromJson<AppData>()
        assertTrue("Invalid JSON should fail deserialization", result.isFailure)
        
        val error = result.exceptionOrNull()
        assertNotNull("Error should be present", error)
        assertTrue("Error should be SerializationException", error is SerializationException)
    }
    
    @Test
    fun testSerializationExtensions() {
        val testData = mapOf("key" to "value")
        
        // Test toJson extension
        val jsonResult = testData.toJson()
        assertTrue("toJson should succeed", jsonResult.isSuccess)
        
        // Test fromJson extension
        val jsonString = jsonResult.getOrThrow()
        val deserializedResult = jsonString.fromJson<Map<String, String>>()
        assertTrue("fromJson should succeed", deserializedResult.isSuccess)
        
        // Test compact serialization
        val compactResult = testData.toCompactJson()
        assertTrue("toCompactJson should succeed", compactResult.isSuccess)
        
        // Test JSON size calculation
        val size = jsonString.jsonSizeInBytes()
        assertTrue("JSON size should be positive", size > 0)
        
        // Test formatted size
        val formattedSize = jsonString.formatJsonSize()
        assertTrue("Formatted size should contain 'B'", formattedSize.contains("B"))
    }
    
    @Test
    fun testMessageProtocolTypes() {
        // Test all message types can be serialized/deserialized
        val messages = listOf(
            AuthChallenge("nonce", "1.0.0"),
            AuthResponse("pubkey", "signature", "1.0.0"),
            AuthSuccess("session", System.currentTimeMillis()),
            CommandMessage("test command"),
            ClaudeResponse("response", false, "conv_id"),
            PermissionRequest("tool", "action"),
            PermissionResponse("req_id", true),
            ErrorMessage("error message"),
            Heartbeat()
        )
        
        messages.forEach { message ->
            val serialized = serializationUtils.serializeWebSocketMessage(message)
            assertTrue("${message::class.simpleName} should serialize", serialized.isSuccess)
            
            val deserialized = serializationUtils.deserializeWebSocketMessage(serialized.getOrThrow())
            assertTrue("${message::class.simpleName} should deserialize", deserialized.isSuccess)
            
            assertEquals("Message type should match", message::class, deserialized.getOrThrow()::class)
        }
    }
}