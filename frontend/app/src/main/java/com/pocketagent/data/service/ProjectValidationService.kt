package com.pocketagent.data.service

import android.util.Log
import com.pocketagent.data.models.Project
import com.pocketagent.data.models.ServerProfile
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.ProjectValidator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized service for Project validation operations.
 *
 * This service extracts complex validation logic from ProjectService to improve
 * maintainability and separation of concerns. It handles:
 * - Project creation validation
 * - Project update validation
 * - Name and path uniqueness validation
 * - Repository URL validation
 * - Server path validation
 * - Business rule validation
 */
@Singleton
class ProjectValidationService
    @Inject
    constructor(
        private val repository: SecureDataRepository,
        private val validator: ProjectValidator,
        private val repositoryValidationService: RepositoryValidationService,
        private val serverProfileService: ServerProfileService,
    ) {
        companion object {
            private const val TAG = "ProjectValidationService"
            private const val MAX_PROJECTS_PER_SERVER = 100
        }

        /**
         * Validates a project for creation.
         *
         * @param project The project to validate
         * @param validatePaths Whether to validate project paths on server
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForCreation(
            project: Project,
            validatePaths: Boolean = false,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating project for creation: ${project.name}")

            return try {
                // Basic project validation
                val validationResult = validator.validateForCreation(project)
                if (validationResult.isFailure) {
                    val errorMessage = validationResult.getFirstErrorMessage()
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Validate server profile exists
                val serverResult = serverProfileService.getServerProfile(project.serverProfileId)
                if (serverResult.isFailure) {
                    val errorMessage = serverResult.getErrorOrNull()
                    return serviceFailure("Server profile not found: $errorMessage")
                }
                val serverProfile = serverResult.getOrNull()!!

                // Check name uniqueness
                val nameValidation = validateNameUniqueness(project.name)
                if (nameValidation.isFailure) {
                    return nameValidation
                }

                // Check project path uniqueness on the same server
                val pathValidation =
                    validateProjectPathUniqueness(
                        project.projectPath,
                        project.serverProfileId,
                    )
                if (pathValidation.isFailure) {
                    return pathValidation
                }

                // Check server project limits
                val serverProjectsValidation = validateServerProjectLimits(project.serverProfileId)
                if (serverProjectsValidation.isFailure) {
                    return serverProjectsValidation
                }

                // Validate repository URL if provided
                if (project.repositoryUrl != null) {
                    val repoValidation = validateRepositoryUrl(project.repositoryUrl!!)
                    if (repoValidation.isFailure) {
                        return repoValidation
                    }
                }

                // Validate paths on server if requested
                if (validatePaths) {
                    val pathValidationResult =
                        validateProjectPaths(
                            serverProfile,
                            project.projectPath,
                            project.scriptsFolder,
                        )
                    if (pathValidationResult.isFailure) {
                        return pathValidationResult
                    }
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate project for creation", e)
                serviceFailure("Validation failed: ${e.message}")
            }
        }

        /**
         * Validates a project update.
         *
         * @param existing The existing project
         * @param updated The updated project
         * @param validatePaths Whether to validate project paths on server
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForUpdate(
            existing: Project,
            updated: Project,
            validatePaths: Boolean = false,
        ): ServiceResult<Unit> {
            Log.d(TAG, "Validating project for update: ${existing.id}")

            return try {
                // Basic project validation
                val validationResult = validator.validateForUpdate(existing, updated)
                if (validationResult.isFailure) {
                    val errorMessage = validationResult.getFirstErrorMessage() ?: "Validation failed"
                    return serviceFailure("Validation failed: $errorMessage")
                }

                // Validate uniqueness constraints for changes
                val uniquenessValidation = validateUpdateUniqueness(existing, updated)
                if (uniquenessValidation.isFailure) {
                    return uniquenessValidation
                }

                // Validate repository URL if changed
                val repoValidation = validateUpdateRepositoryUrl(existing, updated)
                if (repoValidation.isFailure) {
                    return repoValidation
                }

                // Validate paths on server if requested and paths changed
                val pathValidation = validateUpdatePaths(existing, updated, validatePaths)
                if (pathValidation.isFailure) {
                    return pathValidation
                }

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate project for update", e)
                serviceFailure("Validation failed: ${e.message}")
            }
        }

        /**
         * Validates uniqueness constraints for project update.
         */
        private suspend fun validateUpdateUniqueness(
            existing: Project,
            updated: Project,
        ): ServiceResult<Unit> {
            // Check name uniqueness if name changed
            if (updated.name != existing.name) {
                val nameValidation = validateNameUniqueness(updated.name, excludeId = existing.id)
                if (nameValidation.isFailure) {
                    return nameValidation
                }
            }

            // Check project path uniqueness if path changed
            if (updated.projectPath != existing.projectPath) {
                val pathValidation =
                    validateProjectPathUniqueness(
                        updated.projectPath,
                        existing.serverProfileId,
                        excludeId = existing.id,
                    )
                if (pathValidation.isFailure) {
                    return pathValidation
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates repository URL for project update.
         */
        private suspend fun validateUpdateRepositoryUrl(
            existing: Project,
            updated: Project,
        ): ServiceResult<Unit> {
            if (updated.repositoryUrl != existing.repositoryUrl && updated.repositoryUrl != null) {
                val repoValidation = validateRepositoryUrl(updated.repositoryUrl!!)
                if (repoValidation.isFailure) {
                    return repoValidation
                }
            }
            return serviceSuccess(Unit)
        }

        /**
         * Validates paths for project update.
         */
        private suspend fun validateUpdatePaths(
            existing: Project,
            updated: Project,
            validatePaths: Boolean,
        ): ServiceResult<Unit> {
            if (!validatePaths) {
                return serviceSuccess(Unit)
            }

            val projectPathChanged = updated.projectPath != existing.projectPath
            val scriptsFolderChanged = updated.scriptsFolder != existing.scriptsFolder
            val pathsChanged = projectPathChanged || scriptsFolderChanged

            if (pathsChanged) {
                val serverResult = serverProfileService.getServerProfile(existing.serverProfileId)
                if (serverResult.isSuccess) {
                    val pathValidationResult =
                        validateProjectPaths(
                            serverResult.getOrNull()!!,
                            updated.projectPath,
                            updated.scriptsFolder,
                        )
                    if (pathValidationResult.isFailure) {
                        return pathValidationResult
                    }
                }
            }

            return serviceSuccess(Unit)
        }

        /**
         * Validates project name uniqueness.
         *
         * @param name The project name to validate
         * @param excludeId Optional project ID to exclude from uniqueness check
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateNameUniqueness(
            name: String,
            excludeId: String? = null,
        ): ServiceResult<Unit> =
            try {
                val existingProjects = repository.getAllProjects()
                val nameValidation =
                    validator.validateNameUniqueness(
                        name,
                        existingProjects.map { it.name },
                        excludeId = excludeId,
                    )

                if (nameValidation.isFailure) {
                    serviceFailure("Project name already exists")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate name uniqueness", e)
                serviceFailure("Failed to validate name uniqueness: ${e.message}")
            }

        /**
         * Validates project path uniqueness on a server.
         *
         * @param projectPath The project path to validate
         * @param serverProfileId The server profile ID
         * @param excludeId Optional project ID to exclude from uniqueness check
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateProjectPathUniqueness(
            projectPath: String,
            serverProfileId: String,
            excludeId: String? = null,
        ): ServiceResult<Unit> =
            try {
                val existingProjects = repository.getAllProjects()
                val pathPairs =
                    existingProjects
                        .filter { excludeId == null || it.id != excludeId }
                        .map { it.projectPath to it.serverProfileId }

                val pathValidation =
                    validator.validateProjectPathUniqueness(
                        projectPath,
                        serverProfileId,
                        pathPairs,
                        excludeId = excludeId,
                    )

                if (pathValidation.isFailure) {
                    serviceFailure("Project with same path already exists on this server")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate path uniqueness", e)
                serviceFailure("Failed to validate path uniqueness: ${e.message}")
            }

        /**
         * Validates server project limits.
         *
         * @param serverProfileId The server profile ID
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateServerProjectLimits(serverProfileId: String): ServiceResult<Unit> =
            try {
                val existingProjects = repository.getAllProjects()
                val serverProjects = existingProjects.filter { it.serverProfileId == serverProfileId }

                if (serverProjects.size >= MAX_PROJECTS_PER_SERVER) {
                    val message = "Maximum number of projects ($MAX_PROJECTS_PER_SERVER) reached for this server"
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate server project limits", e)
                serviceFailure("Failed to validate server project limits: ${e.message}")
            }

        /**
         * Validates repository URL.
         *
         * @param repositoryUrl The repository URL to validate
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateRepositoryUrl(repositoryUrl: String): ServiceResult<Unit> =
            try {
                val repoValidation = repositoryValidationService.validateRepositoryUrl(repositoryUrl)
                if (repoValidation.isFailure) {
                    val errorMessage = repoValidation.getFirstErrorMessage() ?: "Repository validation failed"
                    serviceFailure("Repository URL validation failed: $errorMessage")
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate repository URL", e)
                serviceFailure("Failed to validate repository URL: ${e.message}")
            }

        /**
         * Validates project paths on the server.
         *
         * @param serverProfile The server profile
         * @param projectPath The project path
         * @param scriptsFolder The scripts folder
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateProjectPaths(
            serverProfile: ServerProfile,
            projectPath: String,
            scriptsFolder: String,
        ): ServiceResult<Unit> {
            return try {
                // This would typically involve SSH connection to validate paths
                // For now, we'll do basic path validation
                if (projectPath.isBlank()) {
                    return serviceFailure("Project path cannot be blank")
                }

                if (scriptsFolder.isBlank()) {
                    return serviceFailure("Scripts folder cannot be blank")
                }

                // Additional path validation logic would go here
                // - Check if paths exist on server
                // - Check permissions
                // - Validate path format

                serviceSuccess(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate project paths", e)
                serviceFailure("Failed to validate project paths: ${e.message}")
            }
        }

        /**
         * Validates if a project can be deleted.
         *
         * @param project The project to validate for deletion
         * @return ServiceResult indicating success or validation errors
         */
        suspend fun validateForDeletion(project: Project): ServiceResult<Unit> =
            try {
                // Check if project is active
                if (project.status == com.pocketagent.data.models.ProjectStatus.ACTIVE) {
                    val message = "Cannot delete active project. Please disconnect first."
                    serviceFailure(message)
                } else {
                    serviceSuccess(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate project for deletion", e)
                serviceFailure("Failed to validate project for deletion: ${e.message}")
            }
    }
