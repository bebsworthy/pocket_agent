package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.Message
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.models.SshIdentity
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import kotlinx.coroutines.flow.Flow
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
 *
 * This refactored version uses composition with specialized repositories to reduce complexity.
 */
@Singleton
class SecureDataRepository
    @Inject
    constructor(
        private val dataStorage: SecureDataRepositoryCore,
        private val sshIdentityRepository: SshIdentityDataRepository,
        private val serverProfileRepository: ServerProfileDataRepository,
        private val projectRepository: ProjectDataRepository,
        private val messageRepository: MessageDataRepository,
    ) {
        companion object {
            private const val TAG = "SecureDataRepository"
        }

        /**
         * Initializes the repository and loads existing data.
         * Must be called before using the repository.
         *
         * @throws DataException.InitializationException if initialization fails
         */
        suspend fun initialize() {
            Log.d(TAG, "Initializing SecureDataRepository")
            dataStorage.initialize()
        }

        /**
         * Loads the current application data.
         *
         * @return The current AppData instance
         * @throws DataException.CorruptedDataException if data cannot be loaded
         */
        suspend fun loadData(): AppData = dataStorage.loadData()

        /**
         * Saves the application data to encrypted storage.
         *
         * @param data The AppData to save
         * @throws DataException.SaveFailedException if save operation fails
         */
        suspend fun saveData(data: AppData) = dataStorage.saveData(data)

        // SSH Identity Operations (delegated to specialized repository)

        suspend fun getAllSshIdentities(): List<SshIdentity> = sshIdentityRepository.getAllSshIdentities()

        suspend fun getSshIdentityById(id: String): SshIdentity? = sshIdentityRepository.getSshIdentityById(id)

        suspend fun addSshIdentity(identity: SshIdentity) = sshIdentityRepository.addSshIdentity(identity)

        suspend fun updateSshIdentity(identity: SshIdentity) = sshIdentityRepository.updateSshIdentity(identity)

        suspend fun deleteSshIdentity(id: String) = sshIdentityRepository.deleteSshIdentity(id)

        // Server Profile Operations (delegated to specialized repository)

        suspend fun getAllServerProfiles(): List<ServerProfile> = serverProfileRepository.getAllServerProfiles()

        suspend fun getServerProfileById(id: String): ServerProfile? = serverProfileRepository.getServerProfileById(id)

        suspend fun addServerProfile(profile: ServerProfile) = serverProfileRepository.addServerProfile(profile)

        suspend fun updateServerProfile(profile: ServerProfile) = serverProfileRepository.updateServerProfile(profile)

        suspend fun deleteServerProfile(id: String) = serverProfileRepository.deleteServerProfile(id)

        // Project Operations (delegated to specialized repository)

        suspend fun getAllProjects(): List<Project> = projectRepository.getAllProjects()

        suspend fun getProjectById(id: String): Project? = projectRepository.getProjectById(id)

        suspend fun addProject(project: Project) = projectRepository.addProject(project)

        suspend fun updateProject(project: Project) = projectRepository.updateProject(project)

        suspend fun deleteProject(id: String) = projectRepository.deleteProject(id)

        // Message Operations (delegated to specialized repository)

        suspend fun getProjectMessages(
            projectId: String,
            limit: Int = 100,
        ): List<Message> = messageRepository.getProjectMessages(projectId, limit)

        suspend fun addMessage(
            projectId: String,
            message: Message,
        ) = messageRepository.addMessage(projectId, message)

        suspend fun clearProjectMessages(projectId: String) = messageRepository.clearProjectMessages(projectId)

        // Query Operations (delegated to specialized repositories)

        suspend fun getProjectsForServer(serverProfileId: String): List<Project> = projectRepository.getProjectsForServer(serverProfileId)

        suspend fun getServerProfilesForIdentity(sshIdentityId: String): List<ServerProfile> =
            serverProfileRepository.getServerProfilesForIdentity(sshIdentityId)

        suspend fun searchProjects(query: String): List<Project> = projectRepository.searchProjects(query)

        suspend fun getRecentProjects(limit: Int = 10): List<Project> = projectRepository.getRecentProjects(limit)

        // Observable Flows (delegated to specialized repositories)

        fun observeSshIdentities(): Flow<List<SshIdentity>> = sshIdentityRepository.observeSshIdentities()

        fun observeServerProfiles(): Flow<List<ServerProfile>> = serverProfileRepository.observeServerProfiles()

        fun observeProjects(): Flow<List<Project>> = projectRepository.observeProjects()

        fun observeProject(projectId: String): Flow<Project?> = projectRepository.observeProject(projectId)

        fun observeProjectMessages(projectId: String): Flow<List<Message>> = messageRepository.observeProjectMessages(projectId)

        // Utility Operations (delegated to core storage)

        suspend fun exportData(): String = dataStorage.exportData()

        suspend fun importData(jsonData: String) = dataStorage.importData(jsonData)

        suspend fun createBackup(): String? = dataStorage.createBackup()

        suspend fun restoreBackup(backupFilename: String): Boolean = dataStorage.restoreBackup(backupFilename)

        suspend fun clearAllData() = dataStorage.clearAllData()

        suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats = dataStorage.getStorageStats()

        suspend fun validateStorage(): Boolean = dataStorage.validateStorage()

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
