package com.pocketagent.data.service

import com.pocketagent.data.models.*
import com.pocketagent.data.repository.DataException
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ProjectValidator
import com.pocketagent.testing.BaseUnitTest
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

/**
 * Comprehensive unit tests for ProjectService.
 * 
 * Tests all CRUD operations, validation logic, business rules,
 * and error handling scenarios.
 */
class ProjectServiceTest : BaseUnitTest() {
    
    private lateinit var projectService: ProjectService
    private lateinit var mockRepository: SecureDataRepository
    private lateinit var mockValidator: ProjectValidator
    private lateinit var mockServerProfileService: ServerProfileService
    private lateinit var mockRepositoryValidationService: RepositoryValidationService
    
    private val testServerId = "server-123"
    private val testProjectId = "project-456"
    private val testProject = Project(
        id = testProjectId,
        name = "Test Project",
        serverProfileId = testServerId,
        projectPath = "/home/user/test-project",
        scriptsFolder = "scripts",
        repositoryUrl = "https://github.com/user/test-project.git",
        status = ProjectStatus.INACTIVE
    )
    
    private val testServerProfile = ServerProfile(
        id = testServerId,
        name = "Test Server",
        hostname = "test.example.com",
        port = 22,
        username = "testuser",
        sshIdentityId = "ssh-789"
    )
    
    @BeforeEach
    fun setup() {
        mockRepository = mockk()
        mockValidator = mockk()
        mockServerProfileService = mockk()
        mockRepositoryValidationService = mockk()
        
        projectService = ProjectService(
            repository = mockRepository,
            validator = mockValidator,
            serverProfileService = mockServerProfileService,
            repositoryValidationService = mockRepositoryValidationService
        )
        
        // Default mock behaviors
        every { mockValidator.validateForCreation(any()) } returns ValidationResult.Success
        every { mockValidator.validateForUpdate(any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateNameUniqueness(any(), any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateProjectPathUniqueness(any(), any(), any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateStatusTransition(any(), any()) } returns ValidationResult.Success
        
        coEvery { mockServerProfileService.getServerProfile(testServerId) } returns Result.success(testServerProfile)
        coEvery { mockRepositoryValidationService.validateRepositoryUrl(any()) } returns ValidationResult.Success
        
        coEvery { mockRepository.getAllProjects() } returns emptyList()
        coEvery { mockRepository.getProjectById(testProjectId) } returns testProject
        coEvery { mockRepository.addProject(any()) } just Runs
        coEvery { mockRepository.updateProject(any()) } just Runs
        coEvery { mockRepository.deleteProject(any()) } just Runs
        coEvery { mockRepository.clearProjectMessages(any()) } just Runs
        coEvery { mockRepository.getProjectsForServer(any()) } returns emptyList()
        coEvery { mockRepository.getMessageCount(any()) } returns 0
    }
    
    // Create Project Tests
    
    @Test
    fun `createProject with valid data should succeed`() = runTest {
        // Given
        coEvery { mockRepository.getAllProjects() } returns emptyList()
        
        // When
        val result = projectService.createProject(
            name = "New Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/new-project"
        )
        
        // Then
        assertTrue(result.isSuccess)
        val project = result.getOrNull()!!
        assertEquals("New Project", project.name)
        assertEquals(testServerId, project.serverProfileId)
        assertEquals("/home/user/new-project", project.projectPath)
        assertEquals(ProjectStatus.INACTIVE, project.status)
        
        coVerify { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with invalid server profile should fail`() = runTest {
        // Given
        coEvery { mockServerProfileService.getServerProfile(testServerId) } returns Result.failure(Exception("Not found"))
        
        // When
        val result = projectService.createProject(
            name = "New Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/new-project"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Server profile not found"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with validation failure should fail`() = runTest {
        // Given
        every { mockValidator.validateForCreation(any()) } returns ValidationResult.Failure(
            com.pocketagent.data.validation.ValidationError.fieldError("Invalid name", "name", "INVALID_NAME")
        )
        
        // When
        val result = projectService.createProject(
            name = "",
            serverProfileId = testServerId,
            projectPath = "/home/user/new-project"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Validation failed"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with duplicate name should fail`() = runTest {
        // Given
        val existingProject = testProject.copy(name = "Duplicate Name")
        coEvery { mockRepository.getAllProjects() } returns listOf(existingProject)
        every { mockValidator.validateNameUniqueness("Duplicate Name", listOf("Duplicate Name")) } returns 
            ValidationResult.Failure(
                com.pocketagent.data.validation.ValidationError.businessRuleError("Duplicate name", "name", "DUPLICATE_NAME")
            )
        
        // When
        val result = projectService.createProject(
            name = "Duplicate Name",
            serverProfileId = testServerId,
            projectPath = "/home/user/duplicate-project"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("already exists"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with duplicate path on same server should fail`() = runTest {
        // Given
        val existingProject = testProject.copy(projectPath = "/home/user/duplicate-path")
        coEvery { mockRepository.getAllProjects() } returns listOf(existingProject)
        every { mockValidator.validateProjectPathUniqueness(
            "/home/user/duplicate-path", 
            testServerId, 
            listOf("/home/user/duplicate-path" to testServerId)
        ) } returns ValidationResult.Failure(
            com.pocketagent.data.validation.ValidationError.businessRuleError("Duplicate path", "projectPath", "DUPLICATE_PATH")
        )
        
        // When
        val result = projectService.createProject(
            name = "New Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/duplicate-path"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("same path already exists"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with maximum projects per server should fail`() = runTest {
        // Given
        val existingProjects = (1..100).map { 
            testProject.copy(id = "project-$it", name = "Project $it") 
        }
        coEvery { mockRepository.getAllProjects() } returns existingProjects
        
        // When
        val result = projectService.createProject(
            name = "New Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/new-project"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Maximum number of projects"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `createProject with invalid repository URL should fail`() = runTest {
        // Given
        coEvery { mockRepositoryValidationService.validateRepositoryUrl("invalid-url") } returns 
            ValidationResult.Failure(
                com.pocketagent.data.validation.ValidationError.fieldError("Invalid URL", "repositoryUrl", "INVALID_URL")
            )
        
        // When
        val result = projectService.createProject(
            name = "New Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/new-project",
            repositoryUrl = "invalid-url"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Repository URL validation failed"))
        
        coVerify(exactly = 0) { mockRepository.addProject(any()) }
    }
    
    // Get Project Tests
    
    @Test
    fun `getProject with valid ID should return project`() = runTest {
        // When
        val result = projectService.getProject(testProjectId)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testProject, result.getOrNull())
        
        coVerify { mockRepository.getProjectById(testProjectId) }
    }
    
    @Test
    fun `getProject with invalid ID should fail`() = runTest {
        // Given
        coEvery { mockRepository.getProjectById("invalid-id") } returns null
        
        // When
        val result = projectService.getProject("invalid-id")
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Project not found", result.getErrorMessage())
    }
    
    // Update Project Tests
    
    @Test
    fun `updateProject with valid changes should succeed`() = runTest {
        // When
        val result = projectService.updateProject(
            id = testProjectId,
            name = "Updated Name",
            repositoryUrl = "https://github.com/user/updated-project.git"
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals("Updated Name", updatedProject.name)
        assertEquals("https://github.com/user/updated-project.git", updatedProject.repositoryUrl)
        
        coVerify { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProject with non-existent project should fail`() = runTest {
        // Given
        coEvery { mockRepository.getProjectById("non-existent") } returns null
        
        // When
        val result = projectService.updateProject(
            id = "non-existent",
            name = "Updated Name"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Project not found", result.getErrorMessage())
        
        coVerify(exactly = 0) { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProject with validation failure should fail`() = runTest {
        // Given
        every { mockValidator.validateForUpdate(any(), any()) } returns ValidationResult.Failure(
            com.pocketagent.data.validation.ValidationError.fieldError("Invalid update", "name", "INVALID_UPDATE")
        )
        
        // When
        val result = projectService.updateProject(
            id = testProjectId,
            name = "Invalid Name"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Validation failed"))
        
        coVerify(exactly = 0) { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProject with duplicate name should fail`() = runTest {
        // Given
        val existingProject = testProject.copy(id = "other-project", name = "Existing Name")
        coEvery { mockRepository.getAllProjects() } returns listOf(testProject, existingProject)
        every { mockValidator.validateNameUniqueness("Existing Name", listOf("Existing Name"), testProjectId) } returns 
            ValidationResult.Failure(
                com.pocketagent.data.validation.ValidationError.businessRuleError("Duplicate name", "name", "DUPLICATE_NAME")
            )
        
        // When
        val result = projectService.updateProject(
            id = testProjectId,
            name = "Existing Name"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("already exists"))
        
        coVerify(exactly = 0) { mockRepository.updateProject(any()) }
    }
    
    // Delete Project Tests
    
    @Test
    fun `deleteProject with valid ID should succeed`() = runTest {
        // When
        val result = projectService.deleteProject(testProjectId)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { mockRepository.deleteProject(testProjectId) }
        coVerify { mockRepository.clearProjectMessages(testProjectId) }
    }
    
    @Test
    fun `deleteProject with non-existent project should fail`() = runTest {
        // Given
        coEvery { mockRepository.getProjectById("non-existent") } returns null
        
        // When
        val result = projectService.deleteProject("non-existent")
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Project not found", result.getErrorMessage())
        
        coVerify(exactly = 0) { mockRepository.deleteProject(any()) }
    }
    
    @Test
    fun `deleteProject with active project should fail`() = runTest {
        // Given
        val activeProject = testProject.copy(status = ProjectStatus.ACTIVE)
        coEvery { mockRepository.getProjectById(testProjectId) } returns activeProject
        
        // When
        val result = projectService.deleteProject(testProjectId)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Cannot delete active project"))
        
        coVerify(exactly = 0) { mockRepository.deleteProject(any()) }
    }
    
    @Test
    fun `deleteProject without removing messages should not clear messages`() = runTest {
        // When
        val result = projectService.deleteProject(testProjectId, removeMessages = false)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { mockRepository.deleteProject(testProjectId) }
        coVerify(exactly = 0) { mockRepository.clearProjectMessages(any()) }
    }
    
    // List Projects Tests
    
    @Test
    fun `listProjects with default parameters should return all projects`() = runTest {
        // Given
        val projects = listOf(
            testProject,
            testProject.copy(id = "project-2", name = "Project 2"),
            testProject.copy(id = "project-3", name = "Project 3")
        )
        coEvery { mockRepository.getAllProjects() } returns projects
        
        // When
        val result = projectService.listProjects()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()!!.size)
    }
    
    @Test
    fun `listProjects with server filter should return filtered projects`() = runTest {
        // Given
        val projects = listOf(
            testProject.copy(serverProfileId = testServerId),
            testProject.copy(id = "project-2", serverProfileId = "other-server"),
            testProject.copy(id = "project-3", serverProfileId = testServerId)
        )
        coEvery { mockRepository.getAllProjects() } returns projects
        
        // When
        val result = projectService.listProjects(serverProfileId = testServerId)
        
        // Then
        assertTrue(result.isSuccess)
        val filteredProjects = result.getOrNull()!!
        assertEquals(2, filteredProjects.size)
        assertTrue(filteredProjects.all { it.serverProfileId == testServerId })
    }
    
    @Test
    fun `listProjects with status filter should return filtered projects`() = runTest {
        // Given
        val projects = listOf(
            testProject.copy(status = ProjectStatus.ACTIVE),
            testProject.copy(id = "project-2", status = ProjectStatus.INACTIVE),
            testProject.copy(id = "project-3", status = ProjectStatus.ACTIVE)
        )
        coEvery { mockRepository.getAllProjects() } returns projects
        
        // When
        val result = projectService.listProjects(status = ProjectStatus.ACTIVE)
        
        // Then
        assertTrue(result.isSuccess)
        val filteredProjects = result.getOrNull()!!
        assertEquals(2, filteredProjects.size)
        assertTrue(filteredProjects.all { it.status == ProjectStatus.ACTIVE })
    }
    
    // Project Status Management Tests
    
    @Test
    fun `updateProjectStatus to ACTIVE should succeed`() = runTest {
        // When
        val result = projectService.updateProjectStatus(
            testProjectId, 
            ProjectStatus.ACTIVE, 
            claudeSessionId = "session-123"
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.ACTIVE, updatedProject.status)
        assertEquals("session-123", updatedProject.claudeSessionId)
        assertNotNull(updatedProject.lastActiveAt)
        assertNull(updatedProject.lastError)
        
        coVerify { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProjectStatus to ERROR should set error message`() = runTest {
        // When
        val result = projectService.updateProjectStatus(
            testProjectId, 
            ProjectStatus.ERROR, 
            errorMessage = "Connection failed"
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.ERROR, updatedProject.status)
        assertEquals("Connection failed", updatedProject.lastError)
        
        coVerify { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProjectStatus to INACTIVE should clear session`() = runTest {
        // Given
        val activeProject = testProject.copy(
            status = ProjectStatus.ACTIVE, 
            claudeSessionId = "session-123"
        )
        coEvery { mockRepository.getProjectById(testProjectId) } returns activeProject
        
        // When
        val result = projectService.updateProjectStatus(testProjectId, ProjectStatus.INACTIVE)
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.INACTIVE, updatedProject.status)
        assertNull(updatedProject.claudeSessionId)
        assertNull(updatedProject.lastError)
        
        coVerify { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `updateProjectStatus with invalid transition should fail`() = runTest {
        // Given
        every { mockValidator.validateStatusTransition(ProjectStatus.INACTIVE, ProjectStatus.ACTIVE) } returns 
            ValidationResult.Failure(
                com.pocketagent.data.validation.ValidationError.businessRuleError("Invalid transition", "status", "INVALID_TRANSITION")
            )
        
        // When
        val result = projectService.updateProjectStatus(testProjectId, ProjectStatus.ACTIVE)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Invalid status transition"))
        
        coVerify(exactly = 0) { mockRepository.updateProject(any()) }
    }
    
    @Test
    fun `activateProject should set project as active with session`() = runTest {
        // When
        val result = projectService.activateProject(testProjectId, "session-456")
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.ACTIVE, updatedProject.status)
        assertEquals("session-456", updatedProject.claudeSessionId)
    }
    
    @Test
    fun `deactivateProject should set project as inactive`() = runTest {
        // When
        val result = projectService.deactivateProject(testProjectId)
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.INACTIVE, updatedProject.status)
        assertNull(updatedProject.claudeSessionId)
    }
    
    @Test
    fun `markProjectAsError should set error status and message`() = runTest {
        // When
        val result = projectService.markProjectAsError(testProjectId, "Database connection failed")
        
        // Then
        assertTrue(result.isSuccess)
        val updatedProject = result.getOrNull()!!
        assertEquals(ProjectStatus.ERROR, updatedProject.status)
        assertEquals("Database connection failed", updatedProject.lastError)
    }
    
    @Test
    fun `clearProjectError should reset error state`() = runTest {
        // Given
        val errorProject = testProject.copy(
            status = ProjectStatus.ERROR, 
            lastError = "Some error"
        )
        coEvery { mockRepository.getProjectById(testProjectId) } returns errorProject
        
        // When
        val result = projectService.clearProjectError(testProjectId)
        
        // Then
        assertTrue(result.isSuccess)
        // Should call updateProject to clear error
        coVerify { 
            projectService.updateProject(
                id = testProjectId, 
                status = ProjectStatus.INACTIVE, 
                lastError = null
            ) 
        }
    }
    
    // Search and Filter Tests
    
    @Test
    fun `searchProjects with matching query should return results`() = runTest {
        // Given
        val projects = listOf(
            testProject.copy(name = "Web Application"),
            testProject.copy(id = "project-2", name = "Mobile App", projectPath = "/home/user/mobile"),
            testProject.copy(id = "project-3", name = "Desktop Tool", repositoryUrl = "https://github.com/user/web-app.git")
        )
        coEvery { mockRepository.getAllProjects() } returns projects
        
        // When
        val result = projectService.searchProjects("web")
        
        // Then
        assertTrue(result.isSuccess)
        val searchResults = result.getOrNull()!!
        assertEquals(2, searchResults.size) // "Web Application" and project with "web-app.git" URL
        assertTrue(searchResults.any { it.name == "Web Application" })
        assertTrue(searchResults.any { it.repositoryUrl?.contains("web-app") == true })
    }
    
    @Test
    fun `searchProjects with empty query should return empty list`() = runTest {
        // When
        val result = projectService.searchProjects("")
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
        
        coVerify(exactly = 0) { mockRepository.getAllProjects() }
    }
    
    @Test
    fun `filterProjects with criteria should apply all filters`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val projects = listOf(
            testProject.copy(
                serverProfileId = testServerId,
                status = ProjectStatus.ACTIVE,
                createdAt = now - 1000,
                lastActiveAt = now - 100,
                repositoryUrl = "https://github.com/user/repo1.git"
            ),
            testProject.copy(
                id = "project-2",
                serverProfileId = "other-server",
                status = ProjectStatus.INACTIVE,
                createdAt = now - 2000,
                repositoryUrl = null
            ),
            testProject.copy(
                id = "project-3",
                serverProfileId = testServerId,
                status = ProjectStatus.ERROR,
                createdAt = now - 500,
                repositoryUrl = "https://github.com/user/repo2.git"
            )
        )
        coEvery { mockRepository.getAllProjects() } returns projects
        
        val criteria = ProjectFilterCriteria(
            serverProfileId = testServerId,
            hasRepositoryOnly = true,
            createdAfter = now - 1500
        )
        
        // When
        val result = projectService.filterProjects(criteria)
        
        // Then
        assertTrue(result.isSuccess)
        val filteredProjects = result.getOrNull()!!
        assertEquals(2, filteredProjects.size) // project-1 and project-3 match all criteria
        assertTrue(filteredProjects.all { it.serverProfileId == testServerId })
        assertTrue(filteredProjects.all { it.repositoryUrl != null })
        assertTrue(filteredProjects.all { it.createdAt >= now - 1500 })
    }
    
    // Usage Statistics Tests
    
    @Test
    fun `getUsageStatistics should return stats for all projects`() = runTest {
        // Given
        val projects = listOf(testProject)
        coEvery { mockRepository.getAllProjects() } returns projects
        coEvery { mockRepository.getMessageCount(testProjectId) } returns 42
        
        // When
        val stats = projectService.getUsageStatistics()
        
        // Then
        assertTrue(stats.containsKey(testProjectId))
        val projectStats = stats[testProjectId]!!
        assertEquals(42, projectStats.messageCount)
        assertNull(projectStats.lastActivity) // testProject has null lastActiveAt
    }
    
    @Test
    fun `getUsageStatistics with specific project IDs should return stats for those projects only`() = runTest {
        // Given
        coEvery { mockRepository.getMessageCount(testProjectId) } returns 10
        
        // When
        val stats = projectService.getUsageStatistics(listOf(testProjectId))
        
        // Then
        assertEquals(1, stats.size)
        assertTrue(stats.containsKey(testProjectId))
        assertEquals(10, stats[testProjectId]!!.messageCount)
    }
    
    // Import/Export Tests
    
    @Test
    fun `exportProject should return JSON data`() = runTest {
        // When
        val result = projectService.exportProject(testProjectId)
        
        // Then
        assertTrue(result.isSuccess)
        val jsonData = result.getOrNull()!!
        assertFalse(jsonData.isEmpty())
        assertTrue(jsonData.contains("Test Project"))
    }
    
    @Test
    fun `exportProject with non-existent project should fail`() = runTest {
        // Given
        coEvery { mockRepository.getProjectById("non-existent") } returns null
        
        // When
        val result = projectService.exportProject("non-existent")
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("Project not found", result.getErrorMessage())
    }
    
    @Test
    fun `importProject with valid JSON should create project`() = runTest {
        // Given
        val exportData = ProjectExport(
            id = "original-id",
            name = "Imported Project",
            serverProfileId = "original-server",
            projectPath = "/home/user/imported",
            scriptsFolder = "scripts",
            repositoryUrl = "https://github.com/user/imported.git",
            createdAt = System.currentTimeMillis()
        )
        val jsonData = kotlinx.serialization.json.Json.encodeToString(
            ProjectExport.serializer(), 
            exportData
        )
        
        // Mock the creation chain
        coEvery { mockRepository.getAllProjects() } returns emptyList()
        coEvery { mockRepository.addProject(any()) } just Runs
        
        // When
        val result = projectService.importProject(jsonData)
        
        // Then
        assertTrue(result.isSuccess)
        val importedProject = result.getOrNull()!!
        assertEquals("Imported Project", importedProject.name)
        assertEquals(testServerId, importedProject.serverProfileId) // Should use original mapping
        
        coVerify { mockRepository.addProject(any()) }
    }
    
    @Test
    fun `importProject with server profile mapping should use mapped server`() = runTest {
        // Given
        val exportData = ProjectExport(
            id = "original-id",
            name = "Imported Project",
            serverProfileId = "original-server",
            projectPath = "/home/user/imported",
            scriptsFolder = "scripts",
            repositoryUrl = null,
            createdAt = System.currentTimeMillis()
        )
        val jsonData = kotlinx.serialization.json.Json.encodeToString(
            ProjectExport.serializer(), 
            exportData
        )
        val mapping = mapOf("original-server" to "new-server")
        
        // Mock mapped server profile
        coEvery { mockServerProfileService.getServerProfile("new-server") } returns Result.success(
            testServerProfile.copy(id = "new-server", name = "New Server")
        )
        coEvery { mockRepository.getAllProjects() } returns emptyList()
        coEvery { mockRepository.addProject(any()) } just Runs
        
        // When
        val result = projectService.importProject(jsonData, mapping)
        
        // Then
        assertTrue(result.isSuccess)
        val importedProject = result.getOrNull()!!
        assertEquals("new-server", importedProject.serverProfileId)
    }
    
    // Error Handling Tests
    
    @Test
    fun `repository exceptions should be handled gracefully`() = runTest {
        // Given
        coEvery { mockRepository.getProjectById(testProjectId) } throws DataException.CorruptedDataException("Data corrupted", RuntimeException())
        
        // When
        val result = projectService.getProject(testProjectId)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Failed to retrieve project"))
    }
    
    @Test
    fun `validation exceptions should be handled gracefully`() = runTest {
        // Given
        every { mockValidator.validateForCreation(any()) } throws RuntimeException("Validation error")
        
        // When
        val result = projectService.createProject(
            name = "Test Project",
            serverProfileId = testServerId,
            projectPath = "/home/user/test"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.getErrorMessage()!!.contains("Failed to create project"))
    }
}