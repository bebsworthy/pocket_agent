package com.pocketagent.data.repository

import android.util.Log
import com.pocketagent.data.models.SshIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized repository for SSH Identity data operations.
 *
 * This repository handles all SSH Identity-specific CRUD operations,
 * extracted from SecureDataRepository to improve maintainability.
 * It maintains the same interface but focuses specifically on SSH Identity management.
 */
@Singleton
class SshIdentityDataRepository
    @Inject
    constructor(
        private val dataStorage: SecureDataRepositoryCore,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "SshIdentityDataRepository"
        }

        /**
         * Retrieves all SSH identities sorted by name.
         *
         * @return List of SSH identities
         */
        suspend fun getAllSshIdentities(): List<SshIdentity> {
            Log.d(TAG, "Getting all SSH identities")
            return dataStorage.loadData().sshIdentities.sortedBy { it.name }
        }

        /**
         * Retrieves an SSH identity by ID.
         *
         * @param id The SSH identity ID
         * @return The SSH identity or null if not found
         */
        suspend fun getSshIdentityById(id: String): SshIdentity? {
            Log.d(TAG, "Getting SSH identity by ID: $id")
            return dataStorage.loadData().sshIdentities.find { it.id == id }
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

                val current = dataStorage.loadData()
                if (current.sshIdentities.any { it.name == identity.name }) {
                    throw DataException.DuplicateNameException("SSH Identity '${identity.name}' already exists")
                }

                val updatedData = current.copy(sshIdentities = current.sshIdentities + identity)
                dataStorage.saveData(updatedData)

                Log.d(TAG, "SSH identity added successfully: ${identity.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to add SSH identity - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to add SSH identity - invalid arguments", e)
                throw DataException.ValidationException("Failed to add SSH identity - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to add SSH identity - invalid state", e)
                throw DataException.ValidationException("Failed to add SSH identity - repository in invalid state: ${e.message}", e)
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

                val current = dataStorage.loadData()
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
                dataStorage.saveData(updatedData)

                Log.d(TAG, "SSH identity updated successfully: ${identity.name}")
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to update SSH identity - validation error", e)
                throw e
            } catch (e: DataException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to update SSH identity - invalid arguments", e)
                throw DataException.ValidationException("Failed to update SSH identity - invalid data: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to update SSH identity - invalid state", e)
                throw DataException.ValidationException("Failed to update SSH identity - repository in invalid state: ${e.message}", e)
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

            val current = dataStorage.loadData()

            // Check if identity is in use by server profiles
            val dependentServers = current.serverProfiles.filter { it.sshIdentityId == id }
            if (dependentServers.isNotEmpty()) {
                val serverNames = dependentServers.map { it.name }
                throw DataException.ConstraintViolationException(
                    "SSH Identity is in use by server profiles: ${serverNames.joinToString(", ")}",
                )
            }

            val updatedData = current.copy(sshIdentities = current.sshIdentities.filter { it.id != id })
            dataStorage.saveData(updatedData)

            Log.d(TAG, "SSH identity deleted successfully: $id")
        }

        /**
         * Observable flow of SSH identities.
         *
         * @return Flow of SSH identities list
         */
        fun observeSshIdentities(): Flow<List<SshIdentity>> = dataStorage.observeData().map { it.sshIdentities }

        /**
         * Gets server profiles for a specific SSH identity.
         *
         * @param sshIdentityId The SSH identity ID
         * @return List of server profiles
         */
        suspend fun getServerProfilesForIdentity(sshIdentityId: String) =
            dataStorage.loadData().serverProfiles.filter { it.sshIdentityId == sshIdentityId }
    }
