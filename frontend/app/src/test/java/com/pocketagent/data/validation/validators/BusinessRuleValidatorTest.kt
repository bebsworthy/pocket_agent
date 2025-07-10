package com.pocketagent.data.validation.validators

import com.pocketagent.data.models.*
import com.pocketagent.data.validation.ValidationResult
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for BusinessRuleValidator.
 */
class BusinessRuleValidatorTest {
    
    private lateinit var validator: BusinessRuleValidator
    
    @Before
    fun setup() {
        validator = BusinessRuleValidator()
    }
    
    @Test
    fun `valid app data should pass validation`() {
        val appData = createValidAppData()
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should succeed", result.isSuccess())
    }
    
    @Test
    fun `app data with duplicate SSH identity names should fail validation`() {
        val identity1 = createSshIdentity("ssh-1", "Test Key")
        val identity2 = createSshIdentity("ssh-2", "Test Key") // Duplicate name
        
        val appData = AppData(
            sshIdentities = listOf(identity1, identity2),
            serverProfiles = emptyList(),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate name error", 
            result.getErrorMessages().any { it.contains("Duplicate") && it.contains("Test Key") }
        )
    }
    
    @Test
    fun `app data with duplicate SSH fingerprints should fail validation`() {
        val identity1 = createSshIdentity("ssh-1", "Key 1", "SHA256:samefingerprint")
        val identity2 = createSshIdentity("ssh-2", "Key 2", "SHA256:samefingerprint") // Duplicate fingerprint
        
        val appData = AppData(
            sshIdentities = listOf(identity1, identity2),
            serverProfiles = emptyList(),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate fingerprint error", 
            result.getErrorMessages().any { it.contains("Duplicate") && it.contains("fingerprint") }
        )
    }
    
    @Test
    fun `app data with missing SSH identity reference should fail validation`() {
        val server = createServerProfile("server-1", "Test Server", "nonexistent-ssh-id")
        
        val appData = AppData(
            sshIdentities = emptyList(),
            serverProfiles = listOf(server),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain missing SSH identity error", 
            result.getErrorMessages().any { it.contains("non-existent SSH identity") }
        )
    }
    
    @Test
    fun `app data with missing server profile reference should fail validation`() {
        val project = createProject("project-1", "Test Project", "nonexistent-server-id")
        
        val appData = AppData(
            sshIdentities = emptyList(),
            serverProfiles = emptyList(),
            projects = listOf(project),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain missing server profile error", 
            result.getErrorMessages().any { it.contains("non-existent server profile") }
        )
    }
    
    @Test
    fun `app data with port conflicts should fail validation`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1", port = 22, wrapperPort = 22) // Same ports
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain port conflict error", 
            result.getErrorMessages().any { it.contains("same as wrapper port") }
        )
    }
    
    @Test
    fun `app data with duplicate hostname port combinations should fail validation`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server1 = createServerProfile("server-1", "Server 1", "ssh-1", hostname = "example.com", port = 22)
        val server2 = createServerProfile("server-2", "Server 2", "ssh-1", hostname = "example.com", port = 22) // Duplicate
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server1, server2),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate hostname:port error", 
            result.getErrorMessages().any { it.contains("Duplicate hostname:port") }
        )
    }
    
    @Test
    fun `app data with duplicate project paths on same server should fail validation`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val project1 = createProject("project-1", "Project 1", "server-1", "/path/to/project")
        val project2 = createProject("project-2", "Project 2", "server-1", "/path/to/project") // Duplicate path
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project1, project2),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain duplicate project path error", 
            result.getErrorMessages().any { it.contains("Duplicate project path") }
        )
    }
    
    @Test
    fun `app data with active project without recent activity should fail validation`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val oldTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000) // 2 hours ago
        val project = createProject(
            "project-1", 
            "Active Project", 
            "server-1",
            status = ProjectStatus.ACTIVE,
            lastActiveAt = oldTime
        )
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain active project without activity error", 
            result.getErrorMessages().any { it.contains("ACTIVE") && it.contains("no recent activity") }
        )
    }
    
    @Test
    fun `app data with error project without error message should fail validation`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val project = createProject(
            "project-1", 
            "Error Project", 
            "server-1",
            status = ProjectStatus.ERROR,
            lastError = null // No error message
        )
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain error project without message error", 
            result.getErrorMessages().any { it.contains("ERROR") && it.contains("no error message") }
        )
    }
    
    @Test
    fun `app data exceeding entity limits should fail validation`() {
        val identities = (1..51).map { createSshIdentity("ssh-$it", "Key $it", "SHA256:fingerprint$it") }
        
        val appData = AppData(
            sshIdentities = identities,
            serverProfiles = emptyList(),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateAppData(appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain SSH identity limit error", 
            result.getErrorMessages().any { it.contains("Too many SSH identities") }
        )
    }
    
    @Test
    fun `validateEntityDeletion should prevent deleting SSH identity in use`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = emptyList(),
            messages = emptyMap()
        )
        
        val result = validator.validateEntityDeletion("sshidentity", "ssh-1", appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain in use error", 
            result.getErrorMessages().any { it.contains("used by server profiles") }
        )
    }
    
    @Test
    fun `validateEntityDeletion should prevent deleting server profile in use`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val project = createProject("project-1", "Test Project", "server-1")
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project),
            messages = emptyMap()
        )
        
        val result = validator.validateEntityDeletion("serverprofile", "server-1", appData)
        assertTrue("Validation should fail", result.isFailure())
        assertTrue("Should contain in use error", 
            result.getErrorMessages().any { it.contains("used by projects") }
        )
    }
    
    @Test
    fun `validateEntityDeletion should warn about project with messages`() {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val project = createProject("project-1", "Test Project", "server-1")
        val message = createMessage("msg-1", "Test message")
        
        val appData = AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project),
            messages = mapOf("project-1" to listOf(message))
        )
        
        val result = validator.validateEntityDeletion("project", "project-1", appData)
        assertTrue("Validation should succeed with warning", result.isFailure()) // It's a warning, not a blocker
        assertTrue("Should contain messages warning", 
            result.getErrorMessages().any { it.contains("messages that will be deleted") }
        )
    }
    
    // Helper methods
    
    private fun createValidAppData(): AppData {
        val identity = createSshIdentity("ssh-1", "Test Key")
        val server = createServerProfile("server-1", "Test Server", "ssh-1")
        val project = createProject("project-1", "Test Project", "server-1")
        
        return AppData(
            sshIdentities = listOf(identity),
            serverProfiles = listOf(server),
            projects = listOf(project),
            messages = emptyMap()
        )
    }
    
    private fun createSshIdentity(
        id: String, 
        name: String, 
        fingerprint: String = "SHA256:defaultfingerprint"
    ): SshIdentity {
        return SshIdentity(
            id = id,
            name = name,
            encryptedPrivateKey = "encrypted_key_data",
            publicKeyFingerprint = fingerprint,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun createServerProfile(
        id: String,
        name: String,
        sshIdentityId: String,
        hostname: String = "example.com",
        port: Int = 22,
        wrapperPort: Int = 8080
    ): ServerProfile {
        return ServerProfile(
            id = id,
            name = name,
            hostname = hostname,
            port = port,
            username = "testuser",
            sshIdentityId = sshIdentityId,
            wrapperPort = wrapperPort,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun createProject(
        id: String,
        name: String,
        serverProfileId: String,
        projectPath: String = "/path/to/project",
        status: ProjectStatus = ProjectStatus.INACTIVE,
        lastActiveAt: Long? = null,
        lastError: String? = null
    ): Project {
        return Project(
            id = id,
            name = name,
            serverProfileId = serverProfileId,
            projectPath = projectPath,
            status = status,
            createdAt = System.currentTimeMillis(),
            lastActiveAt = lastActiveAt,
            lastError = lastError
        )
    }
    
    private fun createMessage(
        id: String,
        content: String,
        type: MessageType = MessageType.USER_INPUT
    ): Message {
        return Message(
            id = id,
            content = content,
            type = type,
            timestamp = System.currentTimeMillis()
        )
    }
}
