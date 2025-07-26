package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized repository for Server Profile data operations.
 *
 * This repository handles all Server Profile-specific CRUD operations,
 * extracted from SecureDataRepository to improve maintainability.
 * It maintains the same interface but focuses specifically on Server Profile management.
 */
@Singleton
class ServerProfileDataRepository
    @Inject
    constructor(
        private val dataStorage: SecureDataRepositoryCore,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "ServerProfileDataRepository"
        }

        /**
         * Retrieves all server profiles sorted by name.
         *
         * @return List of server profiles
         */
        suspend fun getAllServerProfiles(): List<ServerProfile> {
            Log.d(TAG, "Getting all server profiles")
            return dataStorage.loadData().serverProfiles.sortedBy { it.name }
        }

        /**
         * Retrieves a server profile by ID.
         *
         * @param id The server profile ID
         * @return The server profile or null if not found
         */
        suspend fun getServerProfileById(id: String): ServerProfile? {
            Log.d(TAG, "Getting server profile by ID: $id")
            return dataStorage.loadData().serverProfiles.find { it.id == id }
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

                val current = dataStorage.loadData()

                // Check name uniqueness
                if (current.serverProfiles.any { it.name == profile.name }) {
                    throw DataException.DuplicateNameException("Server profile '${profile.name}' already exists")
                }

                // Verify SSH identity exists
                if (current.sshIdentities.none { it.id == profile.sshIdentityId }) {
                    throw DataException.ConstraintViolationException("SSH Identity '${profile.sshIdentityId}' not found")
                }

                val updatedData = current.copy(serverProfiles = current.serverProfiles + profile)
                dataStorage.saveData(updatedData)

                Log.d(TAG, "Server profile added successfully: ${profile.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to add server profile - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to add server profile - invalid arguments", e)
                throw DataException.ValidationException("Failed to add server profile - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to add server profile - invalid state", e)
                throw DataException.ValidationException("Failed to add server profile - repository in invalid state: ${e.message}", e)
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

                val current = dataStorage.loadData()
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
                dataStorage.saveData(updatedData)

                Log.d(TAG, "Server profile updated successfully: ${profile.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to update server profile - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to update server profile - invalid arguments", e)
                throw DataException.ValidationException("Failed to update server profile - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to update server profile - invalid state", e)
                throw DataException.ValidationException("Failed to update server profile - repository in invalid state: ${e.message}", e)
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

            val current = dataStorage.loadData()

            // Check if profile is in use by projects
            val dependentProjects = current.projects.filter { it.serverProfileId == id }
            if (dependentProjects.isNotEmpty()) {
                val projectNames = dependentProjects.map { it.name }
                throw DataException.ConstraintViolationException(
                    "Server profile is in use by projects: ${projectNames.joinToString(", ")}",
                )
            }

            val updatedData = current.copy(serverProfiles = current.serverProfiles.filter { it.id != id })
            dataStorage.saveData(updatedData)

            Log.d(TAG, "Server profile deleted successfully: $id")
        }

        /**
         * Observable flow of server profiles.
         *
         * @return Flow of server profiles list
         */
        fun observeServerProfiles(): Flow<List<ServerProfile>> = dataStorage.observeData().map { it.serverProfiles }

        /**
         * Gets server profiles for a specific SSH identity.
         *
         * @param sshIdentityId The SSH identity ID
         * @return List of server profiles
         */
        suspend fun getServerProfilesForIdentity(sshIdentityId: String): List<ServerProfile> {
            Log.d(TAG, "Getting server profiles for identity: $sshIdentityId")
            return dataStorage.loadData().serverProfiles.filter { it.sshIdentityId == sshIdentityId }
        }
    }
