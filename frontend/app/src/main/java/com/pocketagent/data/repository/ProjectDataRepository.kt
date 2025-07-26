package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized repository for Project data operations.
 *
 * This repository handles all Project-specific CRUD operations,
 * extracted from SecureDataRepository to improve maintainability.
 * It maintains the same interface but focuses specifically on Project management.
 */
@Singleton
class ProjectDataRepository
    @Inject
    constructor(
        private val dataStorage: SecureDataRepositoryCore,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "ProjectDataRepository"
        }

        /**
         * Retrieves all projects sorted by last activity.
         *
         * @return List of projects
         */
        suspend fun getAllProjects(): List<Project> {
            Log.d(TAG, "Getting all projects")
            return dataStorage.loadData().projects.sortedByDescending { it.lastActiveAt ?: it.createdAt }
        }

        /**
         * Retrieves a project by ID.
         *
         * @param id The project ID
         * @return The project or null if not found
         */
        suspend fun getProjectById(id: String): Project? {
            Log.d(TAG, "Getting project by ID: $id")
            return dataStorage.loadData().projects.find { it.id == id }
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

                val current = dataStorage.loadData()

                // Check name uniqueness
                if (current.projects.any { it.name == project.name }) {
                    throw DataException.DuplicateNameException("Project '${project.name}' already exists")
                }

                // Verify server profile exists
                if (current.serverProfiles.none { it.id == project.serverProfileId }) {
                    throw DataException.ConstraintViolationException("Server profile '${project.serverProfileId}' not found")
                }

                val updatedData = current.copy(projects = current.projects + project)
                dataStorage.saveData(updatedData)

                Log.d(TAG, "Project added successfully: ${project.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to add project - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to add project - invalid arguments", e)
                throw DataException.ValidationException("Failed to add project - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to add project - invalid state", e)
                throw DataException.ValidationException("Failed to add project - repository in invalid state: ${e.message}", e)
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

                val current = dataStorage.loadData()
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
                dataStorage.saveData(updatedData)

                Log.d(TAG, "Project updated successfully: ${project.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to update project - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to update project - invalid arguments", e)
                throw DataException.ValidationException("Failed to update project - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to update project - invalid state", e)
                throw DataException.ValidationException("Failed to update project - repository in invalid state: ${e.message}", e)
            }
        }

        /**
         * Deletes a project and its associated messages.
         *
         * @param id The project ID to delete
         */
        suspend fun deleteProject(id: String) {
            Log.d(TAG, "Deleting project: $id")

            val current = dataStorage.loadData()
            val updatedData =
                current.copy(
                    projects = current.projects.filter { it.id != id },
                    messages = current.messages - id, // Remove associated messages
                )
            dataStorage.saveData(updatedData)

            Log.d(TAG, "Project deleted successfully: $id")
        }

        /**
         * Observable flow of projects.
         *
         * @return Flow of projects list
         */
        fun observeProjects(): Flow<List<Project>> = dataStorage.observeData().map { it.projects }

        /**
         * Observable flow of a specific project.
         *
         * @param projectId The project ID to observe
         * @return Flow of project or null
         */
        fun observeProject(projectId: String): Flow<Project?> =
            dataStorage.observeData().map { data ->
                data.projects.find { it.id == projectId }
            }

        /**
         * Gets projects for a specific server profile.
         *
         * @param serverProfileId The server profile ID
         * @return List of projects
         */
        suspend fun getProjectsForServer(serverProfileId: String): List<Project> {
            Log.d(TAG, "Getting projects for server: $serverProfileId")
            return dataStorage.loadData().projects.filter { it.serverProfileId == serverProfileId }
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

            return dataStorage.loadData().projects.filter {
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

            return dataStorage
                .loadData()
                .projects
                .filter { it.lastActiveAt != null }
                .sortedByDescending { it.lastActiveAt }
                .take(limit)
        }
    }
