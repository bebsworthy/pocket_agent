package com.pocketagent.data.service

import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.ValidationResult
import com.pocketagent.data.validation.validators.ProjectValidator
import com.pocketagent.testing.BaseUnitTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for ProjectService demonstrating full CRUD workflow.
 *
 * These tests demonstrate realistic usage scenarios and integration
 * between ProjectService and its dependencies.
 */
class ProjectServiceIntegrationTest : BaseUnitTest() {
    private lateinit var projectService: ProjectService
    private lateinit var mockRepository: SecureDataRepository
    private lateinit var mockValidator: ProjectValidator
    private lateinit var mockServerProfileService: ServerProfileService
    private lateinit var mockRepositoryValidationService: RepositoryValidationService

    private val testServerId = "server-123"
    private val testServerProfile =
        ServerProfile(
            id = testServerId,
            name = "Test Server",
            hostname = "test.example.com",
            port = 22,
            username = "testuser",
            sshIdentityId = "ssh-789",
        )

    @BeforeEach
    fun setup() {
        mockRepository = mockk()
        mockValidator = mockk()
        mockServerProfileService = mockk()
        mockRepositoryValidationService = mockk()

        projectService =
            ProjectService(
                repository = mockRepository,
                validator = mockValidator,
                serverProfileService = mockServerProfileService,
                repositoryValidationService = mockRepositoryValidationService,
            )

        // Setup default mock behaviors
        every { mockValidator.validateForCreation(any()) } returns ValidationResult.Success
        every { mockValidator.validateForUpdate(any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateNameUniqueness(any(), any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateProjectPathUniqueness(any(), any(), any(), any()) } returns ValidationResult.Success
        every { mockValidator.validateStatusTransition(any(), any()) } returns ValidationResult.Success

        coEvery { mockServerProfileService.getServerProfile(testServerId) } returns Result.success(testServerProfile)
        coEvery { mockRepositoryValidationService.validateRepositoryUrl(any()) } returns ValidationResult.Success

        coEvery { mockRepository.getAllProjects() } returns emptyList()
        coEvery { mockRepository.addProject(any()) } just Runs
        coEvery { mockRepository.updateProject(any()) } just Runs
        coEvery { mockRepository.deleteProject(any()) } just Runs
        coEvery { mockRepository.clearProjectMessages(any()) } just Runs
        coEvery { mockRepository.getProjectsForServer(any()) } returns emptyList()
        coEvery { mockRepository.getMessageCount(any()) } returns 0
    }

    @Test
    fun `complete project lifecycle - create, update, activate, deactivate, delete`() =
        runTest {
            // Test project data
            val projectName = "Integration Test Project"
            val projectPath = "/home/user/integration-test"
            val repositoryUrl = "https://github.com/user/integration-test.git"

            // 1. Create Project
            val createResult =
                projectService.createProject(
                    name = projectName,
                    serverProfileId = testServerId,
                    projectPath = projectPath,
                    repositoryUrl = repositoryUrl,
                )

            assertTrue(createResult.isSuccess, "Project creation should succeed")
            val createdProject = createResult.getOrNull()!!
            assertEquals(projectName, createdProject.name)
            assertEquals(testServerId, createdProject.serverProfileId)
            assertEquals(projectPath, createdProject.projectPath)
            assertEquals(repositoryUrl, createdProject.repositoryUrl)
            assertEquals(ProjectStatus.INACTIVE, createdProject.status)

            // Mock the created project for subsequent calls
            coEvery { mockRepository.getProjectById(createdProject.id) } returns createdProject

            // 2. Update Project
            val updatedName = "Updated Integration Test Project"
            val updateResult =
                projectService.updateProject(
                    id = createdProject.id,
                    name = updatedName,
                    scriptsFolder = "custom-scripts",
                )

            assertTrue(updateResult.isSuccess, "Project update should succeed")
            val updatedProject = updateResult.getOrNull()!!
            assertEquals(updatedName, updatedProject.name)
            assertEquals("custom-scripts", updatedProject.scriptsFolder)

            // Mock the updated project
            coEvery { mockRepository.getProjectById(createdProject.id) } returns updatedProject

            // 3. Activate Project
            val claudeSessionId = "session-12345"
            val activateResult = projectService.activateProject(createdProject.id, claudeSessionId)

            assertTrue(activateResult.isSuccess, "Project activation should succeed")
            val activeProject = activateResult.getOrNull()!!
            assertEquals(ProjectStatus.ACTIVE, activeProject.status)
            assertEquals(claudeSessionId, activeProject.claudeSessionId)
            assertNotNull(activeProject.lastActiveAt)

            // Mock the active project
            coEvery { mockRepository.getProjectById(createdProject.id) } returns activeProject

            // 4. Update Activity
            coEvery { mockRepository.updateProject(any()) } just Runs

            // 5. Deactivate Project
            val deactivateResult = projectService.deactivateProject(createdProject.id)

            assertTrue(deactivateResult.isSuccess, "Project deactivation should succeed")
            val inactiveProject = deactivateResult.getOrNull()!!
            assertEquals(ProjectStatus.INACTIVE, inactiveProject.status)
            assertNull(inactiveProject.claudeSessionId)

            // Mock the inactive project
            coEvery { mockRepository.getProjectById(createdProject.id) } returns inactiveProject

            // 6. Delete Project
            val deleteResult = projectService.deleteProject(createdProject.id)

            assertTrue(deleteResult.isSuccess, "Project deletion should succeed")

            // Verify all operations
            coVerify { mockRepository.addProject(any()) }
            coVerify(atLeast = 3) { mockRepository.updateProject(any()) } // update, activate, deactivate
            coVerify { mockRepository.deleteProject(createdProject.id) }
            coVerify { mockRepository.clearProjectMessages(createdProject.id) }
        }

    @Test
    fun `project error handling and recovery workflow`() =
        runTest {
            // Create a project
            val createResult =
                projectService.createProject(
                    name = "Error Test Project",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/error-test",
                )

            assertTrue(createResult.isSuccess)
            val project = createResult.getOrNull()!!

            // Mock the project
            coEvery { mockRepository.getProjectById(project.id) } returns project

            // 1. Mark project as error
            val errorMessage = "Connection failed due to network timeout"
            val errorResult = projectService.markProjectAsError(project.id, errorMessage)

            assertTrue(errorResult.isSuccess)
            val errorProject = errorResult.getOrNull()!!
            assertEquals(ProjectStatus.ERROR, errorProject.status)
            assertEquals(errorMessage, errorProject.lastError)

            // Mock the error project
            coEvery { mockRepository.getProjectById(project.id) } returns errorProject

            // 2. Clear error state
            val clearResult = projectService.clearProjectError(project.id)

            assertTrue(clearResult.isSuccess)
            // clearProjectError calls updateProject internally

            coVerify { mockRepository.updateProject(any()) }
        }

    @Test
    fun `project search and filtering integration`() =
        runTest {
            // Create multiple test projects
            val project1 =
                Project(
                    id = "project-1",
                    name = "Web Application",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/web-app",
                    repositoryUrl = "https://github.com/user/web-app.git",
                    status = ProjectStatus.ACTIVE,
                )

            val project2 =
                Project(
                    id = "project-2",
                    name = "Mobile App",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/mobile-app",
                    repositoryUrl = "https://github.com/user/mobile-app.git",
                    status = ProjectStatus.INACTIVE,
                )

            val project3 =
                Project(
                    id = "project-3",
                    name = "Data Pipeline",
                    serverProfileId = "other-server",
                    projectPath = "/home/user/data-pipeline",
                    repositoryUrl = null,
                    status = ProjectStatus.ERROR,
                )

            val allProjects = listOf(project1, project2, project3)
            coEvery { mockRepository.getAllProjects() } returns allProjects

            // 1. Test search functionality
            val searchResult = projectService.searchProjects("app")
            assertTrue(searchResult.isSuccess)
            val searchResults = searchResult.getOrNull()!!
            assertEquals(2, searchResults.size) // "Web Application" and "Mobile App"
            assertTrue(searchResults.any { it.name == "Web Application" })
            assertTrue(searchResults.any { it.name == "Mobile App" })

            // 2. Test filtering by server
            val filterResult =
                projectService.filterProjects(
                    ProjectFilterCriteria(serverProfileId = testServerId),
                )
            assertTrue(filterResult.isSuccess)
            val filteredResults = filterResult.getOrNull()!!
            assertEquals(2, filteredResults.size) // project1 and project2
            assertTrue(filteredResults.all { it.serverProfileId == testServerId })

            // 3. Test filtering by status
            val statusFilterResult =
                projectService.filterProjects(
                    ProjectFilterCriteria(status = ProjectStatus.ACTIVE),
                )
            assertTrue(statusFilterResult.isSuccess)
            val statusResults = statusFilterResult.getOrNull()!!
            assertEquals(1, statusResults.size)
            assertEquals(ProjectStatus.ACTIVE, statusResults.first().status)

            // 4. Test filtering with repository
            val repoFilterResult =
                projectService.filterProjects(
                    ProjectFilterCriteria(hasRepositoryOnly = true),
                )
            assertTrue(repoFilterResult.isSuccess)
            val repoResults = repoFilterResult.getOrNull()!!
            assertEquals(2, repoResults.size) // project1 and project2 have repositories
            assertTrue(repoResults.all { it.repositoryUrl != null })

            // 5. Test complex filtering
            val complexFilterResult =
                projectService.filterProjects(
                    ProjectFilterCriteria(
                        serverProfileId = testServerId,
                        hasRepositoryOnly = true,
                        status = ProjectStatus.INACTIVE,
                    ),
                )
            assertTrue(complexFilterResult.isSuccess)
            val complexResults = complexFilterResult.getOrNull()!!
            assertEquals(1, complexResults.size) // Only project2 matches all criteria
            assertEquals("Mobile App", complexResults.first().name)
        }

    @Test
    fun `project import and export workflow`() =
        runTest {
            // Create a project to export
            val originalProject =
                Project(
                    id = "export-project",
                    name = "Export Test Project",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/export-test",
                    scriptsFolder = "automation",
                    repositoryUrl = "https://github.com/user/export-test.git",
                )

            coEvery { mockRepository.getProjectById("export-project") } returns originalProject

            // 1. Export project
            val exportResult = projectService.exportProject("export-project")

            assertTrue(exportResult.isSuccess)
            val exportedJson = exportResult.getOrNull()!!
            assertFalse(exportedJson.isEmpty())
            assertTrue(exportedJson.contains("Export Test Project"))

            // 2. Import project with mapping
            val serverMapping = mapOf(testServerId to "new-server-id")
            val newServerProfile = testServerProfile.copy(id = "new-server-id", name = "New Server")

            coEvery { mockServerProfileService.getServerProfile("new-server-id") } returns Result.success(newServerProfile)

            val importResult = projectService.importProject(exportedJson, serverMapping)

            assertTrue(importResult.isSuccess)
            val importedProject = importResult.getOrNull()!!
            assertEquals("Export Test Project", importedProject.name)
            assertEquals("new-server-id", importedProject.serverProfileId)
            assertEquals("/home/user/export-test", importedProject.projectPath)
            assertEquals("automation", importedProject.scriptsFolder)
            assertEquals("https://github.com/user/export-test.git", importedProject.repositoryUrl)
            assertNotEquals("export-project", importedProject.id) // Should have new ID

            coVerify { mockRepository.addProject(any()) }
        }

    @Test
    fun `project usage statistics tracking`() =
        runTest {
            val project1 = Project(id = "stats-1", name = "Stats Project 1", serverProfileId = testServerId, projectPath = "/path1")
            val project2 = Project(id = "stats-2", name = "Stats Project 2", serverProfileId = testServerId, projectPath = "/path2")

            coEvery { mockRepository.getAllProjects() } returns listOf(project1, project2)
            coEvery { mockRepository.getProjectById("stats-1") } returns project1
            coEvery { mockRepository.getProjectById("stats-2") } returns project2
            coEvery { mockRepository.getMessageCount("stats-1") } returns 25
            coEvery { mockRepository.getMessageCount("stats-2") } returns 42

            // Test usage statistics
            val stats = projectService.getUsageStatistics()

            assertEquals(2, stats.size)
            assertTrue(stats.containsKey("stats-1"))
            assertTrue(stats.containsKey("stats-2"))
            assertEquals(25, stats["stats-1"]!!.messageCount)
            assertEquals(42, stats["stats-2"]!!.messageCount)

            // Test specific project statistics
            val specificStats = projectService.getUsageStatistics(listOf("stats-1"))

            assertEquals(1, specificStats.size)
            assertTrue(specificStats.containsKey("stats-1"))
            assertEquals(25, specificStats["stats-1"]!!.messageCount)
        }

    @Test
    fun `project validation and constraint enforcement`() =
        runTest {
            // Setup existing projects to test constraints
            val existingProject =
                Project(
                    id = "existing-1",
                    name = "Existing Project",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/existing",
                    repositoryUrl = "https://github.com/user/existing.git",
                )

            coEvery { mockRepository.getAllProjects() } returns listOf(existingProject)

            // Test name uniqueness constraint
            every { mockValidator.validateNameUniqueness("Existing Project", listOf("Existing Project")) } returns
                ValidationResult.Failure(
                    com.pocketagent.data.validation.ValidationError.businessRuleError(
                        "Project name already exists",
                        "name",
                        "DUPLICATE_NAME",
                    ),
                )

            val duplicateNameResult =
                projectService.createProject(
                    name = "Existing Project",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/duplicate",
                )

            assertTrue(duplicateNameResult.isFailure)
            assertTrue(duplicateNameResult.getErrorMessage()!!.contains("already exists"))

            // Test path uniqueness constraint
            every {
                mockValidator.validateProjectPathUniqueness(
                    "/home/user/existing",
                    testServerId,
                    listOf("/home/user/existing" to testServerId),
                )
            } returns
                ValidationResult.Failure(
                    com.pocketagent.data.validation.ValidationError.businessRuleError(
                        "Project path already exists",
                        "projectPath",
                        "DUPLICATE_PATH",
                    ),
                )

            val duplicatePathResult =
                projectService.createProject(
                    name = "Different Name",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/existing",
                )

            assertTrue(duplicatePathResult.isFailure)
            assertTrue(duplicatePathResult.getErrorMessage()!!.contains("same path already exists"))

            // Test server profile limit
            val manyProjects =
                (1..100).map {
                    existingProject.copy(id = "project-$it", name = "Project $it", projectPath = "/path$it")
                }
            coEvery { mockRepository.getAllProjects() } returns manyProjects

            val limitResult =
                projectService.createProject(
                    name = "Over Limit Project",
                    serverProfileId = testServerId,
                    projectPath = "/home/user/over-limit",
                )

            assertTrue(limitResult.isFailure)
            assertTrue(limitResult.getErrorMessage()!!.contains("Maximum number of projects"))

            coVerify(exactly = 0) { mockRepository.addProject(any()) }
        }
}
