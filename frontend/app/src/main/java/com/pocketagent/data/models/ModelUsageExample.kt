package com.pocketagent.data.models

/**
 * Example usage of the data models to demonstrate functionality.
 * 
 * This file shows how to use the core data models, builders, and extensions
 * in typical application scenarios.
 */

/**
 * Example demonstrating basic model creation and usage.
 */
fun demonstrateBasicUsage() {
    // Create SSH Identity
    val sshIdentity = SshIdentityBuilder()
        .name("Development Key")
        .encryptedPrivateKey("encrypted_dev_key_data")
        .publicKeyFingerprint("SHA256:example+fingerprint")
        .description("SSH key for development server")
        .build()
    
    // Create Server Profile
    val serverProfile = ServerProfileBuilder()
        .name("Development Server")
        .hostname("dev.example.com")
        .username("developer")
        .sshIdentityId(sshIdentity.id)
        .build()
    
    // Create Project
    val project = ProjectBuilder()
        .name("My React App")
        .serverProfileId(serverProfile.id)
        .projectPath("/home/developer/my-react-app")
        .repositoryUrl("https://github.com/user/my-react-app.git")
        .build()
    
    // Create Messages
    val messages = listOf(
        MessageFactory.createUserInput("Hello, can you help me with React?"),
        MessageFactory.createClaudeResponse("I'd be happy to help you with React! What do you need?"),
        MessageFactory.createSystemMessage("Session started successfully")
    )
    
    // Create AppData
    val appData = AppDataBuilder()
        .addSshIdentity(sshIdentity)
        .addServerProfile(serverProfile)
        .addProject(project)
        .addMessages(project.id, messages)
        .build()
    
    // Demonstrate extension functions
    println("SSH Identities: ${appData.getSshIdentitiesSorted().size}")
    println("Server Profiles: ${appData.getServerProfilesSorted().size}")
    println("Projects: ${appData.getProjectsSorted().size}")
    println("Active Projects: ${appData.getActiveProjects().size}")
    println("Total Messages: ${appData.getTotalMessageCount()}")
    
    // Demonstrate validation
    val validationResult = appData.validateRelationships()
    println("Data validation: ${if (validationResult.isSuccess) "PASSED" else "FAILED"}")
    
    // Demonstrate search
    val searchResults = appData.searchProjects("react")
    println("Search results for 'react': ${searchResults.size}")
    
    // Demonstrate data summary
    val summary = appData.createSummary()
    println("Data summary - Projects: ${summary.totalProjects}, Messages: ${summary.totalMessages}")
}

/**
 * Example demonstrating realistic data scenario creation.
 */
fun demonstrateRealisticScenario() {
    // Create a comprehensive development scenario
    val appData = DataScenarioBuilder()
        .addDevelopmentScenario()
        .addPersonalScenario()
        .buildAppData()
    
    // Show statistics
    println("=== Development Scenario ===")
    println("SSH Identities: ${appData.sshIdentities.size}")
    println("Server Profiles: ${appData.serverProfiles.size}")
    println("Projects: ${appData.projects.size}")
    println("Connected Servers: ${appData.getConnectedServers().size}")
    println("Active Projects: ${appData.getActiveProjects().size}")
    println("Recently Used Identities: ${appData.getRecentlyUsedIdentities().size}")
    
    // Show health status
    val healthStatus = appData.getHealthStatus()
    println("Health Status: $healthStatus")
    
    // Show data summary
    val summary = appData.getDataSummary()
    println("Summary: $summary")
}

/**
 * Example demonstrating model operations and transformations.
 */
fun demonstrateModelOperations() {
    // Create a project and demonstrate state changes
    val project = ProjectBuilder()
        .name("Test Project")
        .serverProfileId("server-123")
        .projectPath("/home/user/test-project")
        .build()
    
    println("Initial project status: ${project.status}")
    
    // Update project status
    val connectingProject = project.markAsConnecting()
    println("After connecting: ${connectingProject.status}")
    
    val activeProject = connectingProject.markAsActive("session-456")
    println("After activation: ${activeProject.status}, Session: ${activeProject.claudeSessionId}")
    
    val errorProject = activeProject.markAsError("Connection failed")
    println("After error: ${errorProject.status}, Error: ${errorProject.lastError}")
    
    // Demonstrate server profile operations
    val serverProfile = ServerProfileBuilder()
        .name("Test Server")
        .hostname("test.example.com")
        .username("testuser")
        .sshIdentityId("identity-123")
        .build()
    
    println("\nServer operations:")
    println("Initial status: ${serverProfile.status}")
    println("Connection string: ${serverProfile.getConnectionString()}")
    println("Is connected: ${serverProfile.isConnected()}")
    
    val connectedServer = serverProfile.markAsConnected()
    println("After connection: ${connectedServer.status}, Last connected: ${connectedServer.lastConnectedAt}")
}

/**
 * Example demonstrating message operations.
 */
fun demonstrateMessageOperations() {
    // Create different types of messages
    val userMessage = MessageFactory.createUserInput("Create a new React component")
    val claudeMessage = MessageFactory.createClaudeResponse("I'll help you create a React component")
    val systemMessage = MessageFactory.createSystemMessage("Development server connected")
    val errorMessage = MessageFactory.createErrorMessage("Failed to compile code")
    
    val messages = listOf(userMessage, claudeMessage, systemMessage, errorMessage)
    
    println("=== Message Operations ===")
    messages.forEach { message ->
        println("${message.type.getDescription()}: ${message.getPreviewContent(50)}")
        println("  Age: ${message.getAgeInMinutes()} minutes")
        println("  Is from user: ${message.isFromUser()}")
        println("  Is from Claude: ${message.isFromClaude()}")
    }
    
    // Demonstrate message filtering
    val userMessages = messages.userMessages()
    val errorMessages = messages.errors()
    
    println("\nUser messages: ${userMessages.size}")
    println("Error messages: ${errorMessages.size}")
    
    // Demonstrate message with metadata
    val messageWithMetadata = MessageFactory.createClaudeResponse("Here's your component")
        .withToolRequests(listOf("create_file", "write_code"))
        .withPermissionRequired(true)
        .withExecutionTime(1500)
    
    println("\nMessage with metadata:")
    println("Has tool requests: ${messageWithMetadata.hasToolRequests()}")
    println("Requires permission: ${messageWithMetadata.requiresPermission()}")
    println("Execution time: ${messageWithMetadata.getExecutionTime()}ms")
    println("Tool requests: ${messageWithMetadata.getToolRequests()}")
}

/**
 * Example demonstrating validation and error handling.
 */
fun demonstrateValidationAndErrors() {
    println("=== Validation Examples ===")
    
    // Test SSH identity validation
    val validIdentity = SshIdentityBuilder()
        .name("Valid Identity")
        .encryptedPrivateKey("valid_key")
        .publicKeyFingerprint("SHA256:validfingerprint")
        .build()
    
    val identityValidation = validIdentity.validate()
    println("Valid identity validation: ${identityValidation.isSuccess}")
    
    // Test server profile validation
    val validServer = ServerProfileBuilder()
        .name("Valid Server")
        .hostname("valid.example.com")
        .username("validuser")
        .sshIdentityId("identity-123")
        .build()
    
    val serverValidation = validServer.validate()
    println("Valid server validation: ${serverValidation.isSuccess}")
    
    // Test project validation
    val validProject = ProjectBuilder()
        .name("Valid Project")
        .serverProfileId("server-123")
        .projectPath("/valid/path")
        .build()
    
    val projectValidation = validProject.validate()
    println("Valid project validation: ${projectValidation.isSuccess}")
    
    // Test message validation
    val validMessage = MessageFactory.createUserInput("Valid message content")
    val messageValidation = validMessage.validate()
    println("Valid message validation: ${messageValidation.isSuccess}")
    
    // Test AppData validation
    val validAppData = AppDataBuilder()
        .addSshIdentity(validIdentity)
        .addServerProfile(validServer)
        .addProject(validProject)
        .build()
    
    val appDataValidation = validAppData.validateRelationships()
    println("Valid AppData validation: ${appDataValidation.isSuccess}")
}

/**
 * Example demonstrating serialization capabilities.
 */
fun demonstrateSerializationExample() {
    println("=== Serialization Examples ===")
    
    // Create sample data
    val appData = TestDataFactory.createMinimalDataset()
    
    // Serialize to JSON
    val jsonString = appData.toJson()
    println("Serialized data length: ${jsonString.length} characters")
    
    // Deserialize from JSON
    val deserializedData = jsonString.toAppData()
    println("Deserialized SSH identities: ${deserializedData.sshIdentities.size}")
    println("Deserialized server profiles: ${deserializedData.serverProfiles.size}")
    println("Deserialized projects: ${deserializedData.projects.size}")
    
    // Test export format
    val exportData = appData.toExportFormat()
    println("Export format created at: ${exportData.exportedAt}")
    
    // Individual model serialization
    val identity = appData.sshIdentities.first()
    val identityJson = identity.toJson()
    println("SSH Identity JSON length: ${identityJson.length} characters")
}

/**
 * Example demonstrating data cleanup and maintenance.
 */
fun demonstrateDataCleanup() {
    println("=== Data Cleanup Examples ===")
    
    // Create data with potential issues
    val appData = TestDataFactory.createComprehensiveDataset()
    
    // Check for orphaned data
    val orphanedServers = appData.getOrphanedServerProfiles()
    val orphanedProjects = appData.getOrphanedProjects()
    val unusedIdentities = appData.getUnusedSshIdentities()
    val unusedServers = appData.getUnusedServerProfiles()
    
    println("Orphaned servers: ${orphanedServers.size}")
    println("Orphaned projects: ${orphanedProjects.size}")
    println("Unused SSH identities: ${unusedIdentities.size}")
    println("Unused server profiles: ${unusedServers.size}")
    
    // Clean up orphaned data
    val cleanedData = appData.cleanupOrphanedData()
    println("Data cleaned successfully")
    
    // Check if data needs attention
    val needsAttention = cleanedData.needsAttention()
    println("Data needs attention: $needsAttention")
    
    // Get health status
    val healthStatus = cleanedData.getHealthStatus()
    println("Data health status: $healthStatus")
}

/**
 * Main function to run all examples.
 */
fun runAllExamples() {
    try {
        demonstrateBasicUsage()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateRealisticScenario()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateModelOperations()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateMessageOperations()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateValidationAndErrors()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateSerializationExample()
        println("\n" + "=".repeat(50) + "\n")
        
        demonstrateDataCleanup()
        
        println("\n✅ All examples completed successfully!")
        
    } catch (e: Exception) {
        println("❌ Error running examples: ${e.message}")
        e.printStackTrace()
    }
}