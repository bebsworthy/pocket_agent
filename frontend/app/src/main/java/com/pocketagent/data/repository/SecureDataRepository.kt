package com.pocketagent.data.repository

import android.content.Context
import android.util.Log
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure data repository providing type-safe access to encrypted application data.
 *
 * This repository serves as the primary data access layer for the Pocket Agent application,
 * providing secure storage and retrieval of SSH identities, server profiles, projects,
 * and messages using encrypted JSON storage.
 *
 * Features:
 * - Type-safe data access with proper error handling
 * - Encrypted storage using Android Keystore
 * - Reactive data updates with Flow
 * - Atomic operations with proper concurrency control
 * - Comprehensive validation and constraint enforcement
 * - Backup and restore capabilities
 * - Comprehensive logging for debugging
 */
@Singleton
class SecureDataRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val encryptedStorage: EncryptedJsonStorage,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "SecureDataRepository"
            private const val APP_DATA_KEY = "app_data"
            private const val MAX_MESSAGES_PER_PROJECT = 1000
            private const val MAX_BACKUP_FILES = 5
        }

        // Thread-safe in-memory cache
        private var cachedData: AppData? = null
        private val accessMutex = Mutex()

        // Observable data flows
        private val _dataFlow = MutableStateFlow<AppData?>(null)
        val dataFlow: StateFlow<AppData?> = _dataFlow.asStateFlow()

        // JSON serialization configuration
        private val json =
            Json {
                ignoreUnknownKeys = true // For backwards compatibility
                prettyPrint = true
                encodeDefaults = true
            }

        /**
         * Initializes the repository and loads existing data.
         * Must be called before using the repository.
         *
         * @throws DataException.InitializationException if initialization fails
         */
        suspend fun initialize() {
            Log.d(TAG, "Initializing SecureDataRepository")

            try {
                withContext(Dispatchers.IO) {
                    accessMutex.withLock {
                        val data = loadDataInternal()
                        cachedData = data
                        _dataFlow.emit(data)
                        Log.d(TAG, "Repository initialized successfully with ${data.getTotalEntityCount()} entities")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize repository", e)
                throw DataException.InitializationException("Failed to initialize repository", e)
            }
        }

        /**
         * Loads the current application data.
         *
         * @return The current AppData instance
         * @throws DataException.CorruptedDataException if data cannot be loaded
         */
        suspend fun loadData(): AppData {
            return withContext(Dispatchers.IO) {
                accessMutex.withLock {
                    cachedData ?: run {
                        Log.d(TAG, "Loading data from storage")
                        val data = loadDataInternal()
                        cachedData = data
                        _dataFlow.emit(data)
                        data
                    }
                }
            }
        }

        /**
         * Internal method to load data from encrypted storage.
         */
        private suspend fun loadDataInternal(): AppData {
            return try {
                val jsonData = encryptedStorage.getJsonData(APP_DATA_KEY)
                if (jsonData != null) {
                    val data = json.decodeFromString<AppData>(jsonData)
                    dataValidator.validateAppData(data)
                    Log.d(TAG, "Successfully loaded data from storage")
                    data
                } else {
                    Log.d(TAG, "No existing data found, creating new AppData")
                    AppData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load data from storage", e)
                throw DataException.CorruptedDataException("Failed to load data from storage", e)
            }
        }

        /**
         * Saves the application data to encrypted storage.
         *
         * @param data The AppData to save
         * @throws DataException.SaveFailedException if save operation fails
         */
        suspend fun saveData(data: AppData) {
            Log.d(TAG, "Saving data to storage")

            try {
                withContext(Dispatchers.IO) {
                    accessMutex.withLock {
                        // Validate data before saving
                        dataValidator.validateAppData(data)

                        // Update last modified timestamp
                        val updatedData = data.copy(lastModified = System.currentTimeMillis())

                        // Serialize and save
                        val jsonData = json.encodeToString(updatedData)
                        encryptedStorage.storeJsonData(APP_DATA_KEY, jsonData)

                        // Update cache and notify observers
                        cachedData = updatedData
                        _dataFlow.emit(updatedData)

                        Log.d(TAG, "Data saved successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save data", e)
                throw DataException.SaveFailedException("Failed to save data", e)
            }
        }

        // SSH Identity Operations

        /**
         * Retrieves all SSH identities sorted by name.
         *
         * @return List of SSH identities
         */
        suspend fun getAllSshIdentities(): List<SshIdentity> {
            Log.d(TAG, "Getting all SSH identities")
            return loadData().sshIdentities.sortedBy { it.name }
        }

        /**
         * Retrieves an SSH identity by ID.
         *
         * @param id The SSH identity ID
         * @return The SSH identity or null if not found
         */
        suspend fun getSshIdentityById(id: String): SshIdentity? {
            Log.d(TAG, "Getting SSH identity by ID: $id")
            return loadData().sshIdentities.find { it.id == id }
        }

        /**
         * Adds a new SSH identity.
         *
         * @param identity The SSH identity to add
         * @throws DataException.DuplicateNameException if name already exists
         * @throws DataException.ValidationException if identity is invalid
         */
        suspend fun addSshIdentity(identity: SshIdentity) {
            Log.d(TAG, "Adding SSH identity: ${identity.name}")

            try {
                dataValidator.validateSshIdentity(identity)

                val current = loadData()
                if (current.sshIdentities.any { it.name == identity.name }) {
                    throw DataException.DuplicateNameException("SSH Identity '${identity.name}' already exists")
                }

                val updatedData = current.copy(sshIdentities = current.sshIdentities + identity)
                saveData(updatedData)

                Log.d(TAG, "SSH identity added successfully: ${identity.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add SSH identity", e)
                throw DataException.ValidationException("Failed to add SSH identity: ${e.message}")
            }
        }

        /**
         * Updates an existing SSH identity.
         *
         * @param identity The SSH identity to update
         * @throws DataException.EntityNotFoundException if identity not found
         * @throws DataException.ValidationException if identity is invalid
         */
        suspend fun updateSshIdentity(identity: SshIdentity) {
            Log.d(TAG, "Updating SSH identity: ${identity.name}")

            try {
                dataValidator.validateSshIdentity(identity)

                val current = loadData()
                val existingIndex = current.sshIdentities.indexOfFirst { it.id == identity.id }
                if (existingIndex == -1) {
                    throw DataException.EntityNotFoundException("SSH Identity '${identity.id}' not found")
                }

                // Check for duplicate names (excluding current identity)
                if (current.sshIdentities.any { it.name == identity.name && it.id != identity.id }) {
                    throw DataException.DuplicateNameException("SSH Identity name '${identity.name}' already exists")
                }

                val updatedIdentities = current.sshIdentities.toMutableList()
                updatedIdentities[existingIndex] = identity

                val updatedData = current.copy(sshIdentities = updatedIdentities)
                saveData(updatedData)

                Log.d(TAG, "SSH identity updated successfully: ${identity.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update SSH identity", e)
                throw DataException.ValidationException("Failed to update SSH identity: ${e.message}")
            }
        }

        /**
         * Deletes an SSH identity.
         *
         * @param id The SSH identity ID to delete
         * @throws DataException.ConstraintViolationException if identity is in use
         */
        suspend fun deleteSshIdentity(id: String) {
            Log.d(TAG, "Deleting SSH identity: $id")

            val current = loadData()

            // Check if identity is in use by server profiles
            val dependentServers = current.serverProfiles.filter { it.sshIdentityId == id }
            if (dependentServers.isNotEmpty()) {
                val serverNames = dependentServers.map { it.name }
                throw DataException.ConstraintViolationException(
                    "SSH Identity is in use by server profiles: ${serverNames.joinToString(", ")}",
                )
            }

            val updatedData = current.copy(sshIdentities = current.sshIdentities.filter { it.id != id })
            saveData(updatedData)

            Log.d(TAG, "SSH identity deleted successfully: $id")
        }

        // Server Profile Operations

        /**
         * Retrieves all server profiles sorted by name.
         *
         * @return List of server profiles
         */
        suspend fun getAllServerProfiles(): List<ServerProfile> {
            Log.d(TAG, "Getting all server profiles")
            return loadData().serverProfiles.sortedBy { it.name }
        }

        /**
         * Retrieves a server profile by ID.
         *
         * @param id The server profile ID
         * @return The server profile or null if not found
         */
        suspend fun getServerProfileById(id: String): ServerProfile? {
            Log.d(TAG, "Getting server profile by ID: $id")
            return loadData().serverProfiles.find { it.id == id }
        }

        /**
         * Adds a new server profile.
         *
         * @param profile The server profile to add
         * @throws DataException.DuplicateNameException if name already exists
         * @throws DataException.ConstraintViolationException if SSH identity not found
         * @throws DataException.ValidationException if profile is invalid
         */
        suspend fun addServerProfile(profile: ServerProfile) {
            Log.d(TAG, "Adding server profile: ${profile.name}")

            try {
                dataValidator.validateServerProfile(profile)

                val current = loadData()

                // Check name uniqueness
                if (current.serverProfiles.any { it.name == profile.name }) {
                    throw DataException.DuplicateNameException("Server profile '${profile.name}' already exists")
                }

                // Verify SSH identity exists
                if (current.sshIdentities.none { it.id == profile.sshIdentityId }) {
                    throw DataException.ConstraintViolationException("SSH Identity '${profile.sshIdentityId}' not found")
                }

                val updatedData = current.copy(serverProfiles = current.serverProfiles + profile)
                saveData(updatedData)

                Log.d(TAG, "Server profile added successfully: ${profile.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add server profile", e)
                throw DataException.ValidationException("Failed to add server profile: ${e.message}")
            }
        }

        /**
         * Updates an existing server profile.
         *
         * @param profile The server profile to update
         * @throws DataException.EntityNotFoundException if profile not found
         * @throws DataException.ValidationException if profile is invalid
         */
        suspend fun updateServerProfile(profile: ServerProfile) {
            Log.d(TAG, "Updating server profile: ${profile.name}")

            try {
                dataValidator.validateServerProfile(profile)

                val current = loadData()
                val existingIndex = current.serverProfiles.indexOfFirst { it.id == profile.id }
                if (existingIndex == -1) {
                    throw DataException.EntityNotFoundException("Server profile '${profile.id}' not found")
                }

                // Check for duplicate names (excluding current profile)
                if (current.serverProfiles.any { it.name == profile.name && it.id != profile.id }) {
                    throw DataException.DuplicateNameException("Server profile name '${profile.name}' already exists")
                }

                // Verify SSH identity exists
                if (current.sshIdentities.none { it.id == profile.sshIdentityId }) {
                    throw DataException.ConstraintViolationException("SSH Identity '${profile.sshIdentityId}' not found")
                }

                val updatedProfiles = current.serverProfiles.toMutableList()
                updatedProfiles[existingIndex] = profile

                val updatedData = current.copy(serverProfiles = updatedProfiles)
                saveData(updatedData)

                Log.d(TAG, "Server profile updated successfully: ${profile.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update server profile", e)
                throw DataException.ValidationException("Failed to update server profile: ${e.message}")
            }
        }

        /**
         * Deletes a server profile.
         *
         * @param id The server profile ID to delete
         * @throws DataException.ConstraintViolationException if profile is in use
         */
        suspend fun deleteServerProfile(id: String) {
            Log.d(TAG, "Deleting server profile: $id")

            val current = loadData()

            // Check if profile is in use by projects
            val dependentProjects = current.projects.filter { it.serverProfileId == id }
            if (dependentProjects.isNotEmpty()) {
                val projectNames = dependentProjects.map { it.name }
                throw DataException.ConstraintViolationException(
                    "Server profile is in use by projects: ${projectNames.joinToString(", ")}",
                )
            }

            val updatedData = current.copy(serverProfiles = current.serverProfiles.filter { it.id != id })
            saveData(updatedData)

            Log.d(TAG, "Server profile deleted successfully: $id")
        }

        // Project Operations

        /**
         * Retrieves all projects sorted by last activity.
         *
         * @return List of projects
         */
        suspend fun getAllProjects(): List<Project> {
            Log.d(TAG, "Getting all projects")
            return loadData().projects.sortedByDescending { it.lastActiveAt ?: it.createdAt }
        }

        /**
         * Retrieves a project by ID.
         *
         * @param id The project ID
         * @return The project or null if not found
         */
        suspend fun getProjectById(id: String): Project? {
            Log.d(TAG, "Getting project by ID: $id")
            return loadData().projects.find { it.id == id }
        }

        /**
         * Adds a new project.
         *
         * @param project The project to add
         * @throws DataException.DuplicateNameException if name already exists
         * @throws DataException.ConstraintViolationException if server profile not found
         * @throws DataException.ValidationException if project is invalid
         */
        suspend fun addProject(project: Project) {
            Log.d(TAG, "Adding project: ${project.name}")

            try {
                dataValidator.validateProject(project)

                val current = loadData()

                // Check name uniqueness
                if (current.projects.any { it.name == project.name }) {
                    throw DataException.DuplicateNameException("Project '${project.name}' already exists")
                }

                // Verify server profile exists
                if (current.serverProfiles.none { it.id == project.serverProfileId }) {
                    throw DataException.ConstraintViolationException("Server profile '${project.serverProfileId}' not found")
                }

                val updatedData = current.copy(projects = current.projects + project)
                saveData(updatedData)

                Log.d(TAG, "Project added successfully: ${project.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add project", e)
                throw DataException.ValidationException("Failed to add project: ${e.message}")
            }
        }

        /**
         * Updates an existing project.
         *
         * @param project The project to update
         * @throws DataException.EntityNotFoundException if project not found
         * @throws DataException.ValidationException if project is invalid
         */
        suspend fun updateProject(project: Project) {
            Log.d(TAG, "Updating project: ${project.name}")

            try {
                dataValidator.validateProject(project)

                val current = loadData()
                val existingIndex = current.projects.indexOfFirst { it.id == project.id }
                if (existingIndex == -1) {
                    throw DataException.EntityNotFoundException("Project '${project.id}' not found")
                }

                // Check for duplicate names (excluding current project)
                if (current.projects.any { it.name == project.name && it.id != project.id }) {
                    throw DataException.DuplicateNameException("Project name '${project.name}' already exists")
                }

                // Verify server profile exists
                if (current.serverProfiles.none { it.id == project.serverProfileId }) {
                    throw DataException.ConstraintViolationException("Server profile '${project.serverProfileId}' not found")
                }

                val updatedProjects = current.projects.toMutableList()
                updatedProjects[existingIndex] = project

                val updatedData = current.copy(projects = updatedProjects)
                saveData(updatedData)

                Log.d(TAG, "Project updated successfully: ${project.name}")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update project", e)
                throw DataException.ValidationException("Failed to update project: ${e.message}")
            }
        }

        /**
         * Deletes a project and its associated messages.
         *
         * @param id The project ID to delete
         */
        suspend fun deleteProject(id: String) {
            Log.d(TAG, "Deleting project: $id")

            val current = loadData()
            val updatedData =
                current.copy(
                    projects = current.projects.filter { it.id != id },
                    messages = current.messages - id, // Remove associated messages
                )
            saveData(updatedData)

            Log.d(TAG, "Project deleted successfully: $id")
        }

        // Message Operations

        /**
         * Retrieves messages for a project.
         *
         * @param projectId The project ID
         * @param limit Maximum number of messages to return
         * @return List of messages sorted by timestamp
         */
        suspend fun getProjectMessages(
            projectId: String,
            limit: Int = 100,
        ): List<Message> {
            Log.d(TAG, "Getting messages for project: $projectId (limit: $limit)")

            val messages = loadData().messages[projectId] ?: emptyList()
            return messages.takeLast(limit).sortedBy { it.timestamp }
        }

        /**
         * Adds a message to a project.
         *
         * @param projectId The project ID
         * @param message The message to add
         * @throws DataException.ValidationException if message is invalid
         */
        suspend fun addMessage(
            projectId: String,
            message: Message,
        ) {
            Log.d(TAG, "Adding message to project: $projectId")

            try {
                dataValidator.validateMessage(message)

                val current = loadData()
                val currentMessages = current.messages[projectId] ?: emptyList()
                val updatedMessages =
                    (currentMessages + message).let { messages ->
                        // Limit messages per project
                        if (messages.size > MAX_MESSAGES_PER_PROJECT) {
                            messages.takeLast(MAX_MESSAGES_PER_PROJECT)
                        } else {
                            messages
                        }
                    }

                val updatedData =
                    current.copy(
                        messages = current.messages + (projectId to updatedMessages),
                    )
                saveData(updatedData)

                Log.d(TAG, "Message added successfully to project: $projectId")
            } catch (e: DataException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add message", e)
                throw DataException.ValidationException("Failed to add message: ${e.message}")
            }
        }

        /**
         * Clears all messages for a project.
         *
         * @param projectId The project ID
         */
        suspend fun clearProjectMessages(projectId: String) {
            Log.d(TAG, "Clearing messages for project: $projectId")

            val current = loadData()
            val updatedData = current.copy(messages = current.messages - projectId)
            saveData(updatedData)

            Log.d(TAG, "Messages cleared for project: $projectId")
        }

        // Query Operations

        /**
         * Gets projects for a specific server profile.
         *
         * @param serverProfileId The server profile ID
         * @return List of projects
         */
        suspend fun getProjectsForServer(serverProfileId: String): List<Project> {
            Log.d(TAG, "Getting projects for server: $serverProfileId")
            return loadData().projects.filter { it.serverProfileId == serverProfileId }
        }

        /**
         * Gets server profiles for a specific SSH identity.
         *
         * @param sshIdentityId The SSH identity ID
         * @return List of server profiles
         */
        suspend fun getServerProfilesForIdentity(sshIdentityId: String): List<ServerProfile> {
            Log.d(TAG, "Getting server profiles for identity: $sshIdentityId")
            return loadData().serverProfiles.filter { it.sshIdentityId == sshIdentityId }
        }

        /**
         * Searches projects by name or path.
         *
         * @param query The search query
         * @return List of matching projects
         */
        suspend fun searchProjects(query: String): List<Project> {
            Log.d(TAG, "Searching projects with query: $query")

            if (query.isBlank()) return emptyList()

            return loadData().projects.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.projectPath.contains(query, ignoreCase = true)
            }
        }

        /**
         * Gets recent projects with activity.
         *
         * @param limit Maximum number of projects to return
         * @return List of recent projects
         */
        suspend fun getRecentProjects(limit: Int = 10): List<Project> {
            Log.d(TAG, "Getting recent projects (limit: $limit)")

            return loadData().projects
                .filter { it.lastActiveAt != null }
                .sortedByDescending { it.lastActiveAt }
                .take(limit)
        }

        // Observable Flows

        /**
         * Observable flow of SSH identities.
         *
         * @return Flow of SSH identities list
         */
        fun observeSshIdentities(): Flow<List<SshIdentity>> = dataFlow.filterNotNull().map { it.sshIdentities }

        /**
         * Observable flow of server profiles.
         *
         * @return Flow of server profiles list
         */
        fun observeServerProfiles(): Flow<List<ServerProfile>> = dataFlow.filterNotNull().map { it.serverProfiles }

        /**
         * Observable flow of projects.
         *
         * @return Flow of projects list
         */
        fun observeProjects(): Flow<List<Project>> = dataFlow.filterNotNull().map { it.projects }

        /**
         * Observable flow of a specific project.
         *
         * @param projectId The project ID to observe
         * @return Flow of project or null
         */
        fun observeProject(projectId: String): Flow<Project?> =
            dataFlow.filterNotNull().map { data ->
                data.projects.find { it.id == projectId }
            }

        /**
         * Observable flow of messages for a project.
         *
         * @param projectId The project ID
         * @return Flow of messages list
         */
        fun observeProjectMessages(projectId: String): Flow<List<Message>> =
            dataFlow.filterNotNull().map { data ->
                data.messages[projectId] ?: emptyList()
            }

        // Utility Operations

        /**
         * Exports all application data as JSON.
         *
         * @return JSON string representation of all data
         */
        suspend fun exportData(): String {
            Log.d(TAG, "Exporting data")

            return withContext(Dispatchers.IO) {
                json.encodeToString(loadData())
            }
        }

        /**
         * Imports application data from JSON.
         *
         * @param jsonData The JSON data to import
         * @throws DataException.ValidationException if data is invalid
         */
        suspend fun importData(jsonData: String) {
            Log.d(TAG, "Importing data")

            try {
                withContext(Dispatchers.IO) {
                    val importedData = json.decodeFromString<AppData>(jsonData)
                    dataValidator.validateAppData(importedData)
                    saveData(importedData)
                    Log.d(TAG, "Data imported successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import data", e)
                throw DataException.ValidationException("Failed to import data: ${e.message}")
            }
        }

        /**
         * Creates a backup of current data.
         *
         * @return Backup filename or null if failed
         */
        suspend fun createBackup(): String? {
            Log.d(TAG, "Creating backup")

            return try {
                encryptedStorage.createBackup()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create backup", e)
                null
            }
        }

        /**
         * Restores data from a backup.
         *
         * @param backupFilename The backup filename
         * @return true if restoration was successful
         */
        suspend fun restoreBackup(backupFilename: String): Boolean {
            Log.d(TAG, "Restoring backup: $backupFilename")

            return try {
                val success = encryptedStorage.restoreBackup(backupFilename)
                if (success) {
                    // Reload data from storage
                    initialize()
                    Log.d(TAG, "Backup restored successfully")
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore backup", e)
                false
            }
        }

        /**
         * Clears all application data.
         *
         * @throws DataException.SaveFailedException if clear operation fails
         */
        suspend fun clearAllData() {
            Log.d(TAG, "Clearing all data")

            try {
                encryptedStorage.clearAllData()
                cachedData = null
                _dataFlow.emit(null)
                Log.d(TAG, "All data cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear data", e)
                throw DataException.SaveFailedException("Failed to clear data", e)
            }
        }

        /**
         * Gets storage statistics.
         *
         * @return Storage statistics
         */
        suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats {
            return encryptedStorage.getStorageStats()
        }

        /**
         * Validates storage integrity.
         *
         * @return Validation report
         */
        suspend fun validateStorage(): Boolean {
            Log.d(TAG, "Validating storage")

            return try {
                val report = encryptedStorage.validateStorage()
                Log.d(TAG, "Storage validation completed: ${report.isValid}")
                report.isValid
            } catch (e: Exception) {
                Log.e(TAG, "Storage validation failed", e)
                false
            }
        }

        /**
         * Gets data summary statistics.
         *
         * @return Data summary
         */
        suspend fun getDataSummary(): DataSummary {
            val data = loadData()
            return DataSummary(
                sshIdentityCount = data.sshIdentities.size,
                serverProfileCount = data.serverProfiles.size,
                projectCount = data.projects.size,
                totalMessageCount = data.messages.values.sumOf { it.size },
                lastModified = data.lastModified,
            )
        }

        /**
         * Data summary statistics.
         */
        data class DataSummary(
            val sshIdentityCount: Int,
            val serverProfileCount: Int,
            val projectCount: Int,
            val totalMessageCount: Int,
            val lastModified: Long,
        )
    }
