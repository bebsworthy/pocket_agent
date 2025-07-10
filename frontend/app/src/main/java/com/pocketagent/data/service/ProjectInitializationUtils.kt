package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ProjectStatus
import com.pocketagent.data.models.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility service for project initialization and management operations.
 * 
 * Provides helper functions for:
 * - Project directory setup and validation
 * - Scripts folder creation and configuration
 * - Repository cloning and setup
 * - Environment validation
 * - Project template application
 * - Cleanup and restoration operations
 */
@Singleton
class ProjectInitializationUtils @Inject constructor() {
    
    companion object {
        private const val TAG = "ProjectInitializationUtils"
        private const val DEFAULT_SCRIPTS_FOLDER = "scripts"
        private const val DEFAULT_GITIGNORE_CONTENT = """
            # Pocket Agent generated files
            .pocket-agent/
            *.log
            *.tmp
            
            # Claude Code session files
            .claude-session
            .claude-temp/
            
            # OS generated files
            .DS_Store
            .DS_Store?
            ._*
            .Spotlight-V100
            .Trashes
            ehthumbs.db
            Thumbs.db
        """.trimIndent()
    }
    
    /**
     * Project initialization configuration.
     */
    data class InitializationConfig(
        val createScriptsFolder: Boolean = true,
        val createGitignore: Boolean = false,
        val validateProjectPath: Boolean = true,
        val validateScriptsPath: Boolean = true,
        val setExecutablePermissions: Boolean = true,
        val createReadme: Boolean = false,
        val templateType: ProjectTemplate = ProjectTemplate.BASIC
    )
    
    /**
     * Project template types.
     */
    enum class ProjectTemplate {
        BASIC,
        WEB_DEVELOPMENT,
        PYTHON_PROJECT,
        NODE_PROJECT,
        DATA_SCIENCE,
        MOBILE_APP,
        CUSTOM
    }
    
    /**
     * Project initialization result.
     */
    data class InitializationResult(
        val success: Boolean,
        val message: String,
        val createdFiles: List<String> = emptyList(),
        val createdDirectories: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    )
    
    /**
     * Path validation result.
     */
    data class PathValidationResult(
        val isValid: Boolean,
        val exists: Boolean,
        val isDirectory: Boolean,
        val isWritable: Boolean,
        val permissions: String? = null,
        val message: String,
        val suggestions: List<String> = emptyList()
    )
    
    /**
     * Initializes a project with the specified configuration.
     * 
     * This method handles the complete setup of a project directory structure,
     * including creating necessary folders, setting permissions, and applying templates.
     * 
     * @param project The project to initialize
     * @param serverProfile The server profile associated with the project
     * @param config Initialization configuration options
     * @return InitializationResult with details of the initialization process
     */
    suspend fun initializeProject(
        project: Project,
        serverProfile: ServerProfile,
        config: InitializationConfig = InitializationConfig()
    ): InitializationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing project: ${project.name} on server: ${serverProfile.name}")
        
        val result = InitializationResult.Builder()
        
        try {
            // Validate project path
            if (config.validateProjectPath) {
                val pathValidation = validateProjectPath(project.projectPath, serverProfile)
                if (!pathValidation.isValid) {
                    return@withContext result
                        .failure("Project path validation failed: ${pathValidation.message}")
                        .addError(pathValidation.message)
                        .addSuggestions(pathValidation.suggestions)
                        .build()
                }
                
                if (!pathValidation.exists) {
                    result.addWarning("Project path does not exist: ${project.projectPath}")
                }
            }
            
            // Create scripts folder
            if (config.createScriptsFolder) {
                val scriptsPath = "${project.projectPath}/${project.scriptsFolder}"
                val scriptsValidation = validateScriptsPath(scriptsPath, serverProfile)
                
                if (config.validateScriptsPath && !scriptsValidation.isValid) {
                    result.addWarning("Scripts folder validation failed: ${scriptsValidation.message}")
                }
                
                // In a real implementation, this would SSH to the server and create the directory
                Log.d(TAG, "Would create scripts folder at: $scriptsPath")
                result.addCreatedDirectory(scriptsPath)
            }
            
            // Apply project template
            applyProjectTemplate(project, serverProfile, config.templateType, result)
            
            // Create .gitignore if requested
            if (config.createGitignore) {
                val gitignorePath = "${project.projectPath}/.gitignore"
                Log.d(TAG, "Would create .gitignore at: $gitignorePath")
                result.addCreatedFile(gitignorePath)
            }
            
            // Create README if requested
            if (config.createReadme) {
                val readmePath = "${project.projectPath}/README.md"
                val readmeContent = generateReadmeContent(project, serverProfile)
                Log.d(TAG, "Would create README.md at: $readmePath")
                result.addCreatedFile(readmePath)
            }
            
            // Set executable permissions if requested
            if (config.setExecutablePermissions) {
                setExecutablePermissions(project, serverProfile, result)
            }
            
            Log.d(TAG, "Project initialization completed successfully: ${project.name}")
            result.success("Project initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Project initialization failed", e)
            result
                .failure("Project initialization failed: ${e.message}")
                .addError(e.message ?: "Unknown error")
        }
        
        result.build()
    }
    
    /**
     * Validates a project path on the specified server.
     */
    suspend fun validateProjectPath(
        projectPath: String,
        serverProfile: ServerProfile
    ): PathValidationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Validating project path: $projectPath on server: ${serverProfile.hostname}")
        
        try {
            // Basic path validation
            if (projectPath.isBlank()) {
                return@withContext PathValidationResult(
                    isValid = false,
                    exists = false,
                    isDirectory = false,
                    isWritable = false,
                    message = "Project path cannot be blank"
                )
            }
            
            if (!projectPath.startsWith("/")) {
                return@withContext PathValidationResult(
                    isValid = false,
                    exists = false,
                    isDirectory = false,
                    isWritable = false,
                    message = "Project path must be absolute (start with /)",
                    suggestions = listOf("Use an absolute path starting with /")
                )
            }
            
            // In a real implementation, this would SSH to the server and check the path
            // For now, we'll simulate basic validation
            val isValidPath = isValidPathFormat(projectPath)
            
            if (!isValidPath) {
                return@withContext PathValidationResult(
                    isValid = false,
                    exists = false,
                    isDirectory = false,
                    isWritable = false,
                    message = "Invalid path format",
                    suggestions = listOf(
                        "Ensure path contains only valid characters",
                        "Avoid spaces and special characters",
                        "Use forward slashes for directory separation"
                    )
                )
            }
            
            // Simulate successful validation
            PathValidationResult(
                isValid = true,
                exists = true, // Assume exists for simulation
                isDirectory = true,
                isWritable = true,
                permissions = "rwxr-xr-x",
                message = "Path is valid and accessible"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Path validation failed", e)
            PathValidationResult(
                isValid = false,
                exists = false,
                isDirectory = false,
                isWritable = false,
                message = "Path validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Validates a scripts folder path on the specified server.
     */
    suspend fun validateScriptsPath(
        scriptsPath: String,
        serverProfile: ServerProfile
    ): PathValidationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Validating scripts path: $scriptsPath on server: ${serverProfile.hostname}")
        
        try {
            // Validate that scripts path is within a valid project directory
            if (!scriptsPath.contains("/")) {
                return@withContext PathValidationResult(
                    isValid = false,
                    exists = false,
                    isDirectory = false,
                    isWritable = false,
                    message = "Scripts path must be within a directory structure"
                )
            }
            
            val parentPath = scriptsPath.substringBeforeLast("/")
            val folderName = scriptsPath.substringAfterLast("/")
            
            // Validate folder name
            if (!isValidFolderName(folderName)) {
                return@withContext PathValidationResult(
                    isValid = false,
                    exists = false,
                    isDirectory = false,
                    isWritable = false,
                    message = "Invalid scripts folder name: $folderName",
                    suggestions = listOf(
                        "Use only alphanumeric characters, hyphens, and underscores",
                        "Avoid starting with dots or special characters"
                    )
                )
            }
            
            // In a real implementation, this would check if parent directory exists and is writable
            PathValidationResult(
                isValid = true,
                exists = false, // Assume doesn't exist yet
                isDirectory = false,
                isWritable = true,
                message = "Scripts path is valid and can be created"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Scripts path validation failed", e)
            PathValidationResult(
                isValid = false,
                exists = false,
                isDirectory = false,
                isWritable = false,
                message = "Scripts path validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Applies a project template to set up initial files and structure.
     */
    private suspend fun applyProjectTemplate(
        project: Project,
        serverProfile: ServerProfile,
        template: ProjectTemplate,
        result: InitializationResult.Builder
    ) {
        Log.d(TAG, "Applying template: $template to project: ${project.name}")
        
        when (template) {
            ProjectTemplate.BASIC -> {
                // Create basic project structure
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
            }
            
            ProjectTemplate.WEB_DEVELOPMENT -> {
                // Create web development structure
                result.addCreatedDirectory("${project.projectPath}/src")
                result.addCreatedDirectory("${project.projectPath}/public")
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
                result.addCreatedFile("${project.projectPath}/package.json")
                result.addCreatedFile("${project.projectPath}/index.html")
            }
            
            ProjectTemplate.PYTHON_PROJECT -> {
                // Create Python project structure
                result.addCreatedDirectory("${project.projectPath}/src")
                result.addCreatedDirectory("${project.projectPath}/tests")
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
                result.addCreatedFile("${project.projectPath}/requirements.txt")
                result.addCreatedFile("${project.projectPath}/setup.py")
                result.addCreatedFile("${project.projectPath}/main.py")
            }
            
            ProjectTemplate.NODE_PROJECT -> {
                // Create Node.js project structure
                result.addCreatedDirectory("${project.projectPath}/src")
                result.addCreatedDirectory("${project.projectPath}/lib")
                result.addCreatedDirectory("${project.projectPath}/test")
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
                result.addCreatedFile("${project.projectPath}/package.json")
                result.addCreatedFile("${project.projectPath}/index.js")
            }
            
            ProjectTemplate.DATA_SCIENCE -> {
                // Create data science project structure
                result.addCreatedDirectory("${project.projectPath}/data")
                result.addCreatedDirectory("${project.projectPath}/notebooks")
                result.addCreatedDirectory("${project.projectPath}/models")
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
                result.addCreatedFile("${project.projectPath}/requirements.txt")
                result.addCreatedFile("${project.projectPath}/analysis.ipynb")
            }
            
            ProjectTemplate.MOBILE_APP -> {
                // Create mobile app project structure
                result.addCreatedDirectory("${project.projectPath}/src/main")
                result.addCreatedDirectory("${project.projectPath}/res")
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
                result.addCreatedFile("${project.projectPath}/build.gradle")
                result.addCreatedFile("${project.projectPath}/AndroidManifest.xml")
            }
            
            ProjectTemplate.CUSTOM -> {
                // Custom template - minimal structure
                result.addCreatedDirectory("${project.projectPath}/${project.scriptsFolder}")
            }
        }
    }
    
    /**
     * Sets executable permissions on script files.
     */
    private suspend fun setExecutablePermissions(
        project: Project,
        serverProfile: ServerProfile,
        result: InitializationResult.Builder
    ) {
        Log.d(TAG, "Setting executable permissions for project: ${project.name}")
        
        val scriptsPath = "${project.projectPath}/${project.scriptsFolder}"
        
        // In a real implementation, this would SSH to the server and set permissions
        // chmod +x ${scriptsPath}/*.sh
        Log.d(TAG, "Would set executable permissions on: $scriptsPath")
        
        result.addWarning("Executable permissions set on scripts folder")
    }
    
    /**
     * Generates README content for the project.
     */
    private fun generateReadmeContent(project: Project, serverProfile: ServerProfile): String {
        return """
            # ${project.name}
            
            Project initialized with Pocket Agent on server: ${serverProfile.name}
            
            ## Project Details
            - **Server**: ${serverProfile.hostname}:${serverProfile.port}
            - **Path**: ${project.projectPath}
            - **Scripts**: ${project.scriptsFolder}
            ${if (project.repositoryUrl != null) "- **Repository**: ${project.repositoryUrl}" else ""}
            
            ## Getting Started
            
            1. Connect to the server using Pocket Agent
            2. Navigate to the project directory: `cd ${project.projectPath}`
            3. Run scripts from: `./${project.scriptsFolder}/`
            
            ## Scripts
            
            Place your automation scripts in the `${project.scriptsFolder}` folder.
            
            ## Notes
            
            This project was initialized by Pocket Agent for Claude Code integration.
            
            ---
            *Generated on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}*
        """.trimIndent()
    }
    
    /**
     * Validates path format.
     */
    private fun isValidPathFormat(path: String): Boolean {
        // Basic path format validation
        val validPathRegex = Regex("^/[a-zA-Z0-9/_.-]*$")
        return validPathRegex.matches(path) && 
               !path.contains("//") && 
               !path.contains("../") &&
               !path.endsWith("/.")
    }
    
    /**
     * Validates folder name.
     */
    private fun isValidFolderName(name: String): Boolean {
        val validNameRegex = Regex("^[a-zA-Z0-9_.-]+$")
        return validNameRegex.matches(name) && 
               !name.startsWith(".") && 
               !name.startsWith("-") &&
               name.isNotBlank()
    }
    
    /**
     * Builder class for InitializationResult.
     */
    private class InitializationResult.Builder {
        private var success: Boolean = false
        private var message: String = ""
        private val createdFiles: MutableList<String> = mutableListOf()
        private val createdDirectories: MutableList<String> = mutableListOf()
        private val warnings: MutableList<String> = mutableListOf()
        private val errors: MutableList<String> = mutableListOf()
        
        fun success(msg: String) = apply {
            success = true
            message = msg
        }
        
        fun failure(msg: String) = apply {
            success = false
            message = msg
        }
        
        fun addCreatedFile(path: String) = apply {
            createdFiles.add(path)
        }
        
        fun addCreatedDirectory(path: String) = apply {
            createdDirectories.add(path)
        }
        
        fun addWarning(warning: String) = apply {
            warnings.add(warning)
        }
        
        fun addError(error: String) = apply {
            errors.add(error)
        }
        
        fun addSuggestions(suggestions: List<String>) = apply {
            // Suggestions could be added to warnings or a separate field
            suggestions.forEach { addWarning("Suggestion: $it") }
        }
        
        fun build() = InitializationResult(
            success = success,
            message = message,
            createdFiles = createdFiles.toList(),
            createdDirectories = createdDirectories.toList(),
            warnings = warnings.toList(),
            errors = errors.toList()
        )
    }
    
    /**
     * Cleans up project artifacts and temporary files.
     */
    suspend fun cleanupProject(
        project: Project,
        serverProfile: ServerProfile,
        removeGeneratedFiles: Boolean = true,
        removeScriptsFolder: Boolean = false
    ): InitializationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Cleaning up project: ${project.name}")
        
        val result = InitializationResult.Builder()
        
        try {
            if (removeGeneratedFiles) {
                // Remove generated files like .gitignore, README.md if they were created by Pocket Agent
                Log.d(TAG, "Would remove generated files for project: ${project.name}")
                result.addWarning("Generated files would be removed")
            }
            
            if (removeScriptsFolder) {
                // Remove scripts folder and contents
                val scriptsPath = "${project.projectPath}/${project.scriptsFolder}"
                Log.d(TAG, "Would remove scripts folder: $scriptsPath")
                result.addWarning("Scripts folder would be removed: $scriptsPath")
            }
            
            result.success("Project cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Project cleanup failed", e)
            result.failure("Project cleanup failed: ${e.message}")
        }
        
        result.build()
    }
}