package com.pocketagent.data.models

import java.util.UUID

/**
 * Additional test builder utilities for creating complex data scenarios.
 *
 * This file provides comprehensive builders and factories for creating
 * interconnected data structures useful for testing scenarios.
 */

/**
 * Builder for creating complete data scenarios with related entities.
 *
 * This builder creates a full hierarchy of SSH Identity → Server Profile → Project
 * with proper relationships and realistic data.
 */
class DataScenarioBuilder {
    private val sshIdentities = mutableListOf<SshIdentity>()
    private val serverProfiles = mutableListOf<ServerProfile>()
    private val projects = mutableListOf<Project>()
    private val messages = mutableMapOf<String, List<Message>>()
    private var metadata = AppMetadata()

    /**
     * Add a complete scenario with SSH identity, server profile, and project.
     */
    fun addScenario(
        identityName: String,
        serverName: String,
        projectName: String,
        withMessages: Boolean = true,
    ): DataScenarioBuilder =
        apply {
            // Create SSH identity
            val identity =
                SshIdentityBuilder()
                    .name(identityName)
                    .encryptedPrivateKey("encrypted_key_for_${identityName.lowercase()}")
                    .publicKeyFingerprint("SHA256:${identityName.lowercase()}fingerprint")
                    .description("SSH identity for $identityName")
                    .build()
            sshIdentities.add(identity)

            // Create server profile
            val server =
                ServerProfileBuilder()
                    .name(serverName)
                    .hostname("${serverName.lowercase().replace(" ", "-")}.example.com")
                    .username("developer")
                    .sshIdentityId(identity.id)
                    .build()
            serverProfiles.add(server)

            // Create project
            val project =
                ProjectBuilder()
                    .name(projectName)
                    .serverProfileId(server.id)
                    .projectPath("/home/developer/${projectName.lowercase().replace(" ", "-")}")
                    .repositoryUrl("https://github.com/user/${projectName.lowercase().replace(" ", "-")}.git")
                    .build()
            projects.add(project)

            // Add sample messages if requested
            if (withMessages) {
                val sampleMessages = MessageFactory.createSampleConversation()
                messages[project.id] = sampleMessages
            }
        }

    /**
     * Add development scenario with common development tools.
     */
    fun addDevelopmentScenario(): DataScenarioBuilder =
        apply {
            addScenario("Development Key", "Development Server", "My React App")
            addScenario("Production Key", "Production Server", "Backend API")
            addScenario("Staging Key", "Staging Server", "Mobile App")
        }

    /**
     * Add enterprise scenario with multiple environments.
     */
    fun addEnterpriseScenario(): DataScenarioBuilder =
        apply {
            addScenario("Corporate Key", "Corporate Server", "Enterprise Dashboard")
            addScenario("Client Key", "Client Server", "Client Portal")
            addScenario("Internal Key", "Internal Server", "Internal Tools")
        }

    /**
     * Add personal scenario with hobby projects.
     */
    fun addPersonalScenario(): DataScenarioBuilder =
        apply {
            addScenario("Personal Key", "Home Server", "Personal Website")
            addScenario("Hobby Key", "Raspberry Pi", "IoT Project")
            addScenario("Learning Key", "Learning Server", "Tutorial Project")
        }

    /**
     * Build the complete AppData structure.
     */
    fun buildAppData(): AppData =
        AppData(
            sshIdentities = sshIdentities.toList(),
            serverProfiles = serverProfiles.toList(),
            projects = projects.toList(),
            messages = messages.toMap(),
            metadata = metadata,
        )

    /**
     * Build individual components for testing.
     */
    fun buildComponents(): DataComponents =
        DataComponents(
            sshIdentities = sshIdentities.toList(),
            serverProfiles = serverProfiles.toList(),
            projects = projects.toList(),
            messages = messages.toMap(),
        )
}

/**
 * Data components for testing individual parts.
 */
data class DataComponents(
    val sshIdentities: List<SshIdentity>,
    val serverProfiles: List<ServerProfile>,
    val projects: List<Project>,
    val messages: Map<String, List<Message>>,
)

/**
 * Builder for creating realistic SSH identities with various configurations.
 */
class RealisticSshIdentityBuilder {
    companion object {
        /**
         * Create a development SSH identity.
         */
        fun createDevelopment(name: String = "Development Key"): SshIdentity =
            SshIdentityBuilder()
                .name(name)
                .encryptedPrivateKey("encrypted_dev_key_rsa_4096")
                .publicKeyFingerprint("SHA256:dev+key+fingerprint+abc123")
                .description("Development SSH key for local and staging environments")
                .build()

        /**
         * Create a production SSH identity.
         */
        fun createProduction(name: String = "Production Key"): SshIdentity =
            SshIdentityBuilder()
                .name(name)
                .encryptedPrivateKey("encrypted_prod_key_ed25519")
                .publicKeyFingerprint("SHA256:prod+key+fingerprint+def456")
                .description("Production SSH key with restricted access")
                .lastUsedAt(System.currentTimeMillis() - (24 * 60 * 60 * 1000)) // 1 day ago
                .build()

        /**
         * Create a personal SSH identity.
         */
        fun createPersonal(name: String = "Personal Key"): SshIdentity =
            SshIdentityBuilder()
                .name(name)
                .encryptedPrivateKey("encrypted_personal_key_rsa_2048")
                .publicKeyFingerprint("SHA256:personal+key+fingerprint+ghi789")
                .description("Personal SSH key for hobby projects")
                .lastUsedAt(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)) // 1 week ago
                .build()

        /**
         * Create multiple realistic SSH identities.
         */
        fun createMultiple(count: Int): List<SshIdentity> =
            (1..count).map { index ->
                when (index % 3) {
                    0 -> createDevelopment("Development Key $index")
                    1 -> createProduction("Production Key $index")
                    else -> createPersonal("Personal Key $index")
                }
            }
    }
}

/**
 * Builder for creating realistic server profiles with various configurations.
 */
class RealisticServerProfileBuilder {
    companion object {
        /**
         * Create a localhost server profile.
         */
        fun createLocalhost(
            sshIdentityId: String,
            name: String = "Localhost",
        ): ServerProfile =
            ServerProfileBuilder()
                .name(name)
                .hostname("localhost")
                .port(22)
                .username("developer")
                .sshIdentityId(sshIdentityId)
                .wrapperPort(8080)
                .status(ConnectionStatus.CONNECTED)
                .lastConnectedAt(System.currentTimeMillis() - (60 * 1000)) // 1 minute ago
                .build()

        /**
         * Create a cloud server profile.
         */
        fun createCloud(
            sshIdentityId: String,
            name: String = "Cloud Server",
        ): ServerProfile =
            ServerProfileBuilder()
                .name(name)
                .hostname("cloud.example.com")
                .port(22)
                .username("ubuntu")
                .sshIdentityId(sshIdentityId)
                .wrapperPort(8080)
                .status(ConnectionStatus.DISCONNECTED)
                .lastConnectedAt(System.currentTimeMillis() - (2 * 60 * 60 * 1000)) // 2 hours ago
                .build()

        /**
         * Create a VPS server profile.
         */
        fun createVPS(
            sshIdentityId: String,
            name: String = "VPS Server",
        ): ServerProfile =
            ServerProfileBuilder()
                .name(name)
                .hostname("vps.example.com")
                .port(2222)
                .username("root")
                .sshIdentityId(sshIdentityId)
                .wrapperPort(8080)
                .status(ConnectionStatus.ERROR)
                .build()

        /**
         * Create multiple realistic server profiles.
         */
        fun createMultiple(
            count: Int,
            sshIdentityId: String,
        ): List<ServerProfile> =
            (1..count).map { index ->
                when (index % 3) {
                    0 -> createLocalhost(sshIdentityId, "Localhost $index")
                    1 -> createCloud(sshIdentityId, "Cloud Server $index")
                    else -> createVPS(sshIdentityId, "VPS Server $index")
                }
            }
    }
}

/**
 * Builder for creating realistic projects with various configurations.
 */
class RealisticProjectBuilder {
    companion object {
        /**
         * Create a web application project.
         */
        fun createWebApp(
            serverProfileId: String,
            name: String = "Web App",
        ): Project =
            ProjectBuilder()
                .name(name)
                .serverProfileId(serverProfileId)
                .projectPath("/var/www/${name.lowercase().replace(" ", "-")}")
                .scriptsFolder("scripts")
                .status(ProjectStatus.ACTIVE)
                .repositoryUrl("https://github.com/user/${name.lowercase().replace(" ", "-")}.git")
                .claudeSessionId("session_${UUID.randomUUID().toString().take(8)}")
                .lastActiveAt(System.currentTimeMillis() - (30 * 60 * 1000)) // 30 minutes ago
                .build()

        /**
         * Create a mobile application project.
         */
        fun createMobileApp(
            serverProfileId: String,
            name: String = "Mobile App",
        ): Project =
            ProjectBuilder()
                .name(name)
                .serverProfileId(serverProfileId)
                .projectPath("/home/developer/${name.lowercase().replace(" ", "-")}")
                .scriptsFolder("build-scripts")
                .status(ProjectStatus.INACTIVE)
                .repositoryUrl("https://github.com/user/${name.lowercase().replace(" ", "-")}.git")
                .lastActiveAt(System.currentTimeMillis() - (24 * 60 * 60 * 1000)) // 1 day ago
                .build()

        /**
         * Create a backend API project.
         */
        fun createBackendAPI(
            serverProfileId: String,
            name: String = "Backend API",
        ): Project =
            ProjectBuilder()
                .name(name)
                .serverProfileId(serverProfileId)
                .projectPath("/opt/api/${name.lowercase().replace(" ", "-")}")
                .scriptsFolder("deployment")
                .status(ProjectStatus.CONNECTING)
                .repositoryUrl("https://github.com/company/${name.lowercase().replace(" ", "-")}.git")
                .build()

        /**
         * Create multiple realistic projects.
         */
        fun createMultiple(
            count: Int,
            serverProfileId: String,
        ): List<Project> =
            (1..count).map { index ->
                when (index % 3) {
                    0 -> createWebApp(serverProfileId, "Web App $index")
                    1 -> createMobileApp(serverProfileId, "Mobile App $index")
                    else -> createBackendAPI(serverProfileId, "Backend API $index")
                }
            }
    }
}

/**
 * Builder for creating realistic message conversations.
 */
class RealisticMessageBuilder {
    companion object {
        /**
         * Create a debugging conversation.
         */
        fun createDebuggingConversation(): List<Message> =
            listOf(
                MessageFactory.createUserInput("I'm getting a null pointer exception in my Java code"),
                MessageFactory.createClaudeResponse("I can help you debug that. Can you share the stack trace?"),
                MessageFactory.createUserInput("Here's the stack trace: [full stack trace...]"),
                MessageFactory.createClaudeResponse("I see the issue. The problem is on line 42 where you're accessing..."),
                MessageFactory.createUserInput("That fixed it! Thanks for the help"),
                MessageFactory.createClaudeResponse("You're welcome! Remember to always check for null values before accessing objects."),
            )

        /**
         * Create a code review conversation.
         */
        fun createCodeReviewConversation(): List<Message> =
            listOf(
                MessageFactory.createUserInput("Can you review my React component?"),
                MessageFactory.createClaudeResponse("I'd be happy to review your React component. Please share the code."),
                MessageFactory.createUserInput("[React component code...]"),
                MessageFactory.createClaudeResponse("Overall the component looks good! Here are some suggestions..."),
                MessageFactory.createUserInput("Great feedback! I'll implement those changes"),
                MessageFactory.createSystemMessage("Code review session completed"),
            )

        /**
         * Create a setup conversation.
         */
        fun createSetupConversation(): List<Message> =
            listOf(
                MessageFactory.createSystemMessage("Connected to development server"),
                MessageFactory.createUserInput("Help me set up a new Node.js project"),
                MessageFactory.createClaudeResponse("I'll help you set up a Node.js project. Let me create the basic structure..."),
                MessageFactory.createStatusUpdate("Creating package.json"),
                MessageFactory.createStatusUpdate("Installing dependencies"),
                MessageFactory.createClaudeResponse("Your Node.js project is set up! Here's what I created..."),
                MessageFactory.createUserInput("Perfect! Now let's add some API endpoints"),
            )

        /**
         * Create an error handling conversation.
         */
        fun createErrorConversation(): List<Message> =
            listOf(
                MessageFactory.createUserInput("Start the development server"),
                MessageFactory.createStatusUpdate("Starting server..."),
                MessageFactory.createErrorMessage("Failed to start server: Port 3000 already in use"),
                MessageFactory.createUserInput("Can you help me fix this?"),
                MessageFactory.createClaudeResponse("The port 3000 is already in use. Let me help you find and stop the process..."),
                MessageFactory.createSystemMessage("Server started successfully on port 3001"),
            )
    }
}

/**
 * Utility class for creating complete test datasets.
 */
object TestDataFactory {
    /**
     * Create a minimal test dataset.
     */
    fun createMinimalDataset(): AppData =
        DataScenarioBuilder()
            .addScenario("Test Key", "Test Server", "Test Project")
            .buildAppData()

    /**
     * Create a comprehensive test dataset.
     */
    fun createComprehensiveDataset(): AppData =
        DataScenarioBuilder()
            .addDevelopmentScenario()
            .addEnterpriseScenario()
            .addPersonalScenario()
            .buildAppData()

    /**
     * Create a dataset with specific counts.
     */
    fun createDatasetWithCounts(
        identityCount: Int,
        serverCount: Int,
        projectCount: Int,
    ): AppData {
        val identities = RealisticSshIdentityBuilder.createMultiple(identityCount)
        val servers =
            identities.flatMap { identity ->
                RealisticServerProfileBuilder.createMultiple(serverCount / identityCount, identity.id)
            }
        val projects =
            servers.flatMap { server ->
                RealisticProjectBuilder.createMultiple(projectCount / servers.size, server.id)
            }

        return AppData(
            sshIdentities = identities,
            serverProfiles = servers,
            projects = projects,
            messages =
                projects.associate { project ->
                    project.id to RealisticMessageBuilder.createDebuggingConversation()
                },
        )
    }

    /**
     * Create an empty dataset.
     */
    fun createEmptyDataset(): AppData = AppData()

    /**
     * Create a dataset with validation errors (for testing error handling).
     */
    fun createInvalidDataset(): AppData {
        // This would normally fail validation, but we're bypassing it for testing
        return AppData(
            sshIdentities = emptyList(),
            serverProfiles =
                listOf(
                    ServerProfile(
                        name = "Invalid Server",
                        hostname = "test.com",
                        username = "user",
                        sshIdentityId = "nonexistent-id", // This will fail validation
                    ),
                ),
            projects = emptyList(),
        )
    }
}
