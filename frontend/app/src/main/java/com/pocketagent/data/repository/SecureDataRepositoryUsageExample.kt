package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.ConnectionStatus
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.MessageType
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Example usage of SecureDataRepository demonstrating common patterns.
 *
 * This class shows how to properly use the SecureDataRepository in various
 * scenarios including initialization, CRUD operations, reactive data access,
 * and error handling.
 */
@Singleton
class SecureDataRepositoryUsageExample
    @Inject
    constructor(
        private val repository: SecureDataRepository,
    ) {
        companion object {
            private const val TAG = "DataRepositoryExample"
        }

        /**
         * Example: Initialize repository and setup basic data.
         */
        suspend fun exampleInitialization() {
            try {
                // Initialize the repository
                repository.initialize()

                Log.d(TAG, "Repository initialized successfully")

                // Get data summary
                val summary = repository.getDataSummary()
                Log.d(TAG, "Data summary: $summary")
            } catch (e: DataException.InitializationException) {
                Log.e(TAG, "Failed to initialize repository", e)
            }
        }

        /**
         * Example: Create a complete project setup.
         */
        suspend fun exampleCreateProjectSetup() {
            try {
                // Create SSH identity
                val sshIdentity =
                    SshIdentity(
                        name = "My Development Key",
                        encryptedPrivateKey = "encrypted-private-key-data",
                        publicKeyFingerprint = "SHA256:abc123def456ghi789",
                        description = "SSH key for development servers",
                    )

                repository.addSshIdentity(sshIdentity)
                Log.d(TAG, "SSH identity created: ${sshIdentity.name}")

                // Create server profile
                val serverProfile =
                    ServerProfile(
                        name = "Development Server",
                        hostname = "dev.mycompany.com",
                        port = 22,
                        username = "developer",
                        sshIdentityId = sshIdentity.id,
                        wrapperPort = 8080,
                        status = ConnectionStatus.NEVER_CONNECTED,
                    )

                repository.addServerProfile(serverProfile)
                Log.d(TAG, "Server profile created: ${serverProfile.name}")

                // Create project
                val project =
                    Project(
                        name = "My Web App",
                        serverProfileId = serverProfile.id,
                        projectPath = "/home/developer/projects/webapp",
                        scriptsFolder = "scripts",
                        status = ProjectStatus.INACTIVE,
                        repositoryUrl = "https://github.com/mycompany/webapp.git",
                    )

                repository.addProject(project)
                Log.d(TAG, "Project created: ${project.name}")

                // Add initial message
                val welcomeMessage =
                    Message(
                        content = "Welcome to the project! Let's start coding.",
                        type = MessageType.SYSTEM_MESSAGE,
                        timestamp = System.currentTimeMillis(),
                        metadata = mapOf("source" to "system", "level" to "info"),
                    )

                repository.addMessage(project.id, welcomeMessage)
                Log.d(TAG, "Welcome message added")
            } catch (e: DataException) {
                Log.e(TAG, "Failed to create project setup", e)
            }
        }

        /**
         * Example: Search and filter data.
         */
        suspend fun exampleSearchAndFilter() {
            try {
                // Search projects by name
                val webProjects = repository.searchProjects("web")
                Log.d(TAG, "Found ${webProjects.size} web projects")

                // Get projects for a specific server
                val servers = repository.getAllServerProfiles()
                if (servers.isNotEmpty()) {
                    val serverProjects = repository.getProjectsForServer(servers[0].id)
                    Log.d(TAG, "Server '${servers[0].name}' has ${serverProjects.size} projects")
                }

                // Get recent projects
                val recentProjects = repository.getRecentProjects(5)
                Log.d(TAG, "Found ${recentProjects.size} recent projects")

                // Get active projects
                val activeProjects = repository.getProjectsByStatus(ProjectStatus.ACTIVE)
                Log.d(TAG, "Found ${activeProjects.size} active projects")
            } catch (e: DataException) {
                Log.e(TAG, "Failed to search data", e)
            }
        }

        /**
         * Example: Reactive data observation.
         */
        suspend fun exampleReactiveObservation() {
            try {
                // Observe all projects
                repository.observeProjects().collect { projects ->
                    Log.d(TAG, "Projects updated: ${projects.size} total")
                }

                // Observe specific project
                val projects = repository.getAllProjects()
                if (projects.isNotEmpty()) {
                    val project = projects[0]
                    repository.observeProject(project.id).collect { updatedProject ->
                        if (updatedProject != null) {
                            Log.d(TAG, "Project '${updatedProject.name}' status: ${updatedProject.status}")
                        }
                    }
                }

                // Observe data summary
                repository.observeDataSummary().collect { summary ->
                    Log.d(TAG, "Data summary updated: ${summary.projectCount} projects, ${summary.totalMessageCount} messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe data", e)
            }
        }

        /**
         * Example: Update project status and metadata.
         */
        suspend fun exampleUpdateProjectStatus(projectId: String) {
            try {
                // Update project to active
                repository.updateProjectStatus(projectId, ProjectStatus.ACTIVE)
                repository.updateProjectLastActive(projectId)

                Log.d(TAG, "Project activated: $projectId")

                // Add status message
                val statusMessage =
                    Message(
                        content = "Project is now active and ready for development.",
                        type = MessageType.STATUS_UPDATE,
                        timestamp = System.currentTimeMillis(),
                        metadata = mapOf("status" to "active", "automated" to "true"),
                    )

                repository.addMessage(projectId, statusMessage)

                // Update server profile last connected time
                val project = repository.getProjectById(projectId)
                if (project != null) {
                    repository.updateServerProfileLastConnected(project.serverProfileId)
                    repository.updateServerProfileStatus(project.serverProfileId, ConnectionStatus.CONNECTED)
                }
            } catch (e: DataException) {
                Log.e(TAG, "Failed to update project status", e)
            }
        }

        /**
         * Example: Handle errors and recovery.
         */
        suspend fun exampleErrorHandling() {
            try {
                // Attempt to add duplicate SSH identity
                val identity =
                    SshIdentity(
                        name = "Duplicate Key",
                        encryptedPrivateKey = "encrypted-key",
                        publicKeyFingerprint = "SHA256:duplicate123",
                    )

                repository.addSshIdentity(identity)
                repository.addSshIdentity(identity) // This will fail
            } catch (e: DataException.DuplicateNameException) {
                Log.w(TAG, "SSH identity name already exists: ${e.message}")

                // Handle by updating existing or choosing new name
                val existingIdentity = repository.getSshIdentityByName("Duplicate Key")
                if (existingIdentity != null) {
                    Log.d(TAG, "Found existing identity: ${existingIdentity.id}")
                }
            }

            try {
                // Attempt to delete SSH identity that's in use
                val identities = repository.getAllSshIdentities()
                if (identities.isNotEmpty()) {
                    repository.deleteSshIdentity(identities[0].id)
                }
            } catch (e: DataException.ConstraintViolationException) {
                Log.w(TAG, "Cannot delete SSH identity: ${e.message}")

                // Show user the constraint violation and ask for confirmation
                // to delete dependent objects first
            }
        }

        /**
         * Example: Backup and restore operations.
         */
        suspend fun exampleBackupAndRestore() {
            try {
                // Create backup
                val backupFile = repository.createBackup()
                if (backupFile != null) {
                    Log.d(TAG, "Backup created: $backupFile")

                    // Simulate data loss or corruption
                    Log.d(TAG, "Simulating data recovery scenario...")

                    // Restore from backup
                    val restored = repository.restoreBackup(backupFile)
                    if (restored) {
                        Log.d(TAG, "Data restored successfully from backup")
                    } else {
                        Log.e(TAG, "Failed to restore from backup")
                    }
                } else {
                    Log.e(TAG, "Failed to create backup")
                }

                // Export data for manual backup
                val exportedData = repository.exportData()
                Log.d(TAG, "Data exported: ${exportedData.length} characters")

                // Could save this to external storage or cloud
            } catch (e: DataException) {
                Log.e(TAG, "Backup/restore operation failed", e)
            }
        }

        /**
         * Example: Bulk operations and batch updates.
         */
        suspend fun exampleBulkOperations() {
            try {
                // Get all data for batch operations
                val data = repository.loadData()

                // Bulk update all projects to set last active time
                val updatedProjects =
                    data.projects.map { project ->
                        if (project.lastActiveAt == null) {
                            project.copy(lastActiveAt = System.currentTimeMillis())
                        } else {
                            project
                        }
                    }

                // Apply bulk updates
                for (project in updatedProjects) {
                    if (project.lastActiveAt != null &&
                        data.projects.find { it.id == project.id }?.lastActiveAt == null
                    ) {
                        repository.updateProject(project)
                    }
                }

                Log.d(TAG, "Bulk update completed for ${updatedProjects.size} projects")
            } catch (e: DataException) {
                Log.e(TAG, "Bulk operations failed", e)
            }
        }

        /**
         * Example: Performance monitoring and optimization.
         */
        suspend fun examplePerformanceMonitoring() {
            try {
                val startTime = System.currentTimeMillis()

                // Perform a series of operations
                val summary = repository.getDataSummary()
                val projects = repository.getAllProjects()
                val identities = repository.getAllSshIdentities()
                val stats = repository.getStorageStats()

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                Log.d(TAG, "Performance metrics:")
                Log.d(TAG, "  - Operation duration: ${duration}ms")
                Log.d(TAG, "  - Projects loaded: ${projects.size}")
                Log.d(TAG, "  - SSH identities: ${identities.size}")
                Log.d(TAG, "  - Storage size: ${stats.totalSize} bytes")
                Log.d(TAG, "  - Storage health: ${stats.isHealthy}")

                // Validate storage integrity
                val isValid = repository.validateStorage()
                Log.d(TAG, "  - Storage validation: ${if (isValid) "PASS" else "FAIL"}")
            } catch (e: Exception) {
                Log.e(TAG, "Performance monitoring failed", e)
            }
        }
    }
