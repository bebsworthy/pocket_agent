package com.pocketagent.data.repository

import android.content.Context
import android.util.Log
import com.pocketagent.data.models.AppData
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core data storage operations for the application.
 *
 * This class provides the fundamental data storage and retrieval operations
 * that are used by the specialized repository classes. It handles:
 * - Data loading and saving
 * - Encryption/decryption
 * - Caching and state management
 * - Observable data flows
 * - Backup and restore operations
 */
@Singleton
class SecureDataRepositoryCore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val encryptedStorage: EncryptedJsonStorage,
        private val dataValidator: DataValidator,
    ) {
        companion object {
            private const val TAG = "SecureDataRepositoryCore"
            private const val APP_DATA_KEY = "app_data"
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
            Log.d(TAG, "Initializing SecureDataRepositoryCore")

            try {
                withContext(Dispatchers.IO) {
                    accessMutex.withLock {
                        val data = loadDataInternal()
                        cachedData = data
                        _dataFlow.emit(data)
                        Log.d(TAG, "Repository initialized successfully with ${data.getTotalEntityCount()} entities")
                    }
                }
            } catch (e: DataException) {
                Log.e(TAG, "Failed to initialize repository - data error", e)
                throw DataException.InitializationException("Failed to initialize repository", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initialize repository - I/O error", e)
                throw DataException.InitializationException("Failed to initialize repository due to I/O error", e)
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to initialize repository - security error", e)
                throw DataException.InitializationException("Failed to initialize repository due to security error", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to initialize repository - invalid state", e)
                throw DataException.InitializationException("Failed to initialize repository due to invalid state", e)
            }
        }

        /**
         * Loads the current application data.
         *
         * @return The current AppData instance
         * @throws DataException.CorruptedDataException if data cannot be loaded
         */
        suspend fun loadData(): AppData =
            withContext(Dispatchers.IO) {
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

        /**
         * Internal method to load data from encrypted storage.
         */
        private suspend fun loadDataInternal(): AppData =
            try {
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
            } catch (e: SerializationException) {
                Log.e(TAG, "Failed to load data from storage - corrupted data", e)
                throw DataException.CorruptedDataException("Failed to deserialize data from storage - data may be corrupted", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load data from storage - I/O error", e)
                throw DataException.CorruptedDataException("Failed to load data from storage due to I/O error", e)
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to load data from storage - decryption error", e)
                throw DataException.CorruptedDataException("Failed to decrypt data from storage", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to load data from storage - invalid data format", e)
                throw DataException.CorruptedDataException("Invalid data format in storage", e)
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
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to save data - validation error", e)
                throw DataException.SaveFailedException("Failed to save data due to validation error", e)
            } catch (e: SerializationException) {
                Log.e(TAG, "Failed to save data - serialization error", e)
                throw DataException.SaveFailedException("Failed to serialize data for saving", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save data - I/O error", e)
                throw DataException.SaveFailedException("Failed to save data due to I/O error", e)
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to save data - encryption error", e)
                throw DataException.SaveFailedException("Failed to encrypt data for saving", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to save data - invalid repository state", e)
                throw DataException.SaveFailedException("Cannot save data - repository in invalid state", e)
            }
        }

        /**
         * Observable flow of application data.
         *
         * @return Flow of AppData
         */
        fun observeData(): Flow<AppData> = dataFlow.filterNotNull()

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
            } catch (e: DataException.ValidationException) {
                Log.e(TAG, "Failed to import data - validation error", e)
                throw e
            } catch (e: SerializationException) {
                Log.e(TAG, "Failed to import data - invalid JSON format", e)
                throw DataException.ValidationException("Failed to import data - invalid JSON format: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to import data - invalid data structure", e)
                throw DataException.ValidationException("Failed to import data - invalid data structure: ${e.message}", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to import data - I/O error", e)
                throw DataException.ValidationException("Failed to import data due to I/O error: ${e.message}", e)
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
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create backup - I/O error", e)
                null
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to create backup - encryption error", e)
                null
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to create backup - invalid state", e)
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
            } catch (e: IOException) {
                Log.e(TAG, "Failed to restore backup - I/O error", e)
                false
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to restore backup - decryption error", e)
                false
            } catch (e: DataException.InitializationException) {
                Log.e(TAG, "Failed to restore backup - initialization error", e)
                false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to restore backup - invalid backup file", e)
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
            } catch (e: IOException) {
                Log.e(TAG, "Failed to clear data - I/O error", e)
                throw DataException.SaveFailedException("Failed to clear data due to I/O error", e)
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Failed to clear data - security error", e)
                throw DataException.SaveFailedException("Failed to clear data due to security error", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to clear data - invalid state", e)
                throw DataException.SaveFailedException("Failed to clear data - repository in invalid state", e)
            }
        }

        /**
         * Gets storage statistics.
         *
         * @return Storage statistics
         */
        suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats = encryptedStorage.getStorageStats()

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
            } catch (e: IOException) {
                Log.e(TAG, "Storage validation failed - I/O error", e)
                false
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Storage validation failed - security error", e)
                false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Storage validation failed - invalid state", e)
                false
            }
        }
    }
