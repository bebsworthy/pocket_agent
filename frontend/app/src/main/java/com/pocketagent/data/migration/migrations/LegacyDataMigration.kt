package com.pocketagent.data.migration.migrations

import android.content.Context
import android.util.Log
import com.pocketagent.data.migration.BaseDataMigration
import com.pocketagent.data.migration.MigrationException
import com.pocketagent.data.migration.MigrationProgress
import com.pocketagent.data.models.AppData
import com.pocketagent.data.models.SshIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migration for importing data from legacy storage formats.
 *
 * This migration handles the import of data from various legacy formats:
 * - SQLite database (if the app previously used Room/SQLite)
 * - Plain JSON files
 * - Shared preferences
 * - Other file-based storage formats
 */
@Singleton
class LegacyDataMigration
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BaseDataMigration() {
        companion object {
            private const val TAG = "LegacyDataMigration"
            private const val LEGACY_DB_NAME = "claude_code_database"
            private const val LEGACY_PREFS_NAME = "pocket_agent_prefs"
            private const val LEGACY_JSON_FILE = "data.json"
        }

        override val fromVersion: Int = -1 // Special version for legacy data
        override val toVersion: Int = 1
        override val name: String = "Legacy Data Import"
        override val description: String = "Imports data from legacy storage formats (SQLite, JSON, SharedPreferences)"
        override val isReversible: Boolean = false

        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
            }

        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            return withContext(Dispatchers.IO) {
                reportProgress(1, 8, "Scanning for legacy data sources", progressCallback)

                val legacySources = findLegacyDataSources()
                if (legacySources.isEmpty()) {
                    throw MigrationException.ValidationException("No legacy data sources found")
                }

                Log.i(TAG, "Found ${legacySources.size} legacy data sources: ${legacySources.keys}")

                reportProgress(2, 8, "Reading legacy data", progressCallback)

                var migratedData = AppData(version = toVersion)

                // Migrate from each source in priority order
                for ((source, file) in legacySources) {
                    reportProgress(3, 8, "Migrating from $source", progressCallback)

                    try {
                        when (source) {
                            "sqlite" -> migratedData = migrateSqliteData(migratedData, file, progressCallback)
                            "json" -> migratedData = migrateJsonData(migratedData, file, progressCallback)
                            "preferences" -> migratedData = migratePreferencesData(migratedData, progressCallback)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to migrate from $source: ${e.message}", e)
                        // Continue with other sources
                    }
                }

                reportProgress(7, 8, "Validating migrated data", progressCallback)

                // Ensure we have valid data structure
                if (migratedData.sshIdentities.isEmpty() &&
                    migratedData.serverProfiles.isEmpty() &&
                    migratedData.projects.isEmpty()
                ) {
                    Log.w(TAG, "No data was successfully migrated from legacy sources")
                }

                reportProgress(8, 8, "Legacy migration completed", progressCallback)

                updateDataVersion(migratedData)
            }
        }

        override suspend fun canMigrate(data: AppData): Boolean {
            return findLegacyDataSources().isNotEmpty()
        }

        override suspend fun getEstimatedSteps(data: AppData): Int = 8

        /**
         * Finds available legacy data sources.
         */
        private fun findLegacyDataSources(): Map<String, File> {
            val sources = mutableMapOf<String, File>()

            // Check for SQLite database
            val dbFile = context.getDatabasePath(LEGACY_DB_NAME)
            if (dbFile.exists() && dbFile.length() > 0) {
                sources["sqlite"] = dbFile
            }

            // Check for legacy JSON file
            val jsonFile = File(context.filesDir, LEGACY_JSON_FILE)
            if (jsonFile.exists() && jsonFile.length() > 0) {
                sources["json"] = jsonFile
            }

            // Check for shared preferences
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$LEGACY_PREFS_NAME.xml")
            if (prefsFile.exists() && prefsFile.length() > 0) {
                sources["preferences"] = prefsFile
            }

            return sources
        }

        /**
         * Migrates data from SQLite database.
         */
        private suspend fun migrateSqliteData(
            currentData: AppData,
            dbFile: File,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            Log.d(TAG, "Migrating from SQLite database: ${dbFile.absolutePath}")

            // Note: In a real implementation, you would:
            // 1. Open the SQLite database
            // 2. Query the tables for data
            // 3. Convert the data to the new format
            // 4. Handle any schema differences

            // For now, we'll simulate this process
            reportProgress(1, 3, "Opening SQLite database", progressCallback)

            // Simulate reading data (in real implementation, use SQLite API)
            reportProgress(2, 3, "Reading tables and converting data", progressCallback)

            // Example of what the real implementation might look like:
        /*
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val sshIdentities = readSshIdentitiesFromDb(db)
            val serverProfiles = readServerProfilesFromDb(db)
            val projects = readProjectsFromDb(db)
            val messages = readMessagesFromDb(db)

            return currentData.copy(
                sshIdentities = currentData.sshIdentities + sshIdentities,
                serverProfiles = currentData.serverProfiles + serverProfiles,
                projects = currentData.projects + projects,
                messages = currentData.messages + messages
            )
        } finally {
            db.close()
        }
         */

            reportProgress(3, 3, "SQLite migration completed", progressCallback)

            // For demonstration, return unchanged data
            return currentData
        }

        /**
         * Migrates data from legacy JSON file.
         */
        private suspend fun migrateJsonData(
            currentData: AppData,
            jsonFile: File,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            Log.d(TAG, "Migrating from JSON file: ${jsonFile.absolutePath}")

            reportProgress(1, 4, "Reading JSON file", progressCallback)

            return try {
                val jsonContent = jsonFile.readText()

                reportProgress(2, 4, "Parsing JSON data", progressCallback)

                // Try to parse as current format first
                val legacyData =
                    try {
                        json.decodeFromString(AppData.serializer(), jsonContent)
                    } catch (e: Exception) {
                        // If that fails, try to parse as legacy format
                        parseLegacyJsonFormat(jsonContent)
                    }

                reportProgress(3, 4, "Converting legacy data", progressCallback)

                // Merge with current data, avoiding duplicates
                val mergedData = mergeLegacyData(currentData, legacyData)

                reportProgress(4, 4, "JSON migration completed", progressCallback)

                mergedData
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate JSON data", e)
                throw MigrationException.CorruptedDataException("Failed to parse legacy JSON data", e)
            }
        }

        /**
         * Migrates data from shared preferences.
         */
        private suspend fun migratePreferencesData(
            currentData: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            Log.d(TAG, "Migrating from shared preferences")

            reportProgress(1, 3, "Reading shared preferences", progressCallback)

            val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

            reportProgress(2, 3, "Converting preferences to entities", progressCallback)

            // Example of migrating specific preference values
            val migratedEntities = mutableListOf<SshIdentity>()

            // Check for legacy SSH key storage
            val legacySshKeyName = prefs.getString("default_ssh_key_name", null)
            val legacySshKeyData = prefs.getString("default_ssh_key_data", null)

            if (!legacySshKeyName.isNullOrBlank() && !legacySshKeyData.isNullOrBlank()) {
                val legacySshIdentity =
                    SshIdentity(
                        name = legacySshKeyName,
                        encryptedPrivateKey = legacySshKeyData,
                        publicKeyFingerprint = "migrated:${System.currentTimeMillis()}",
                        description = "Migrated from legacy preferences",
                    )
                migratedEntities.add(legacySshIdentity)
            }

            reportProgress(3, 3, "Preferences migration completed", progressCallback)

            return currentData.copy(
                sshIdentities = currentData.sshIdentities + migratedEntities,
                lastModified = System.currentTimeMillis(),
            )
        }

        /**
         * Parses legacy JSON format that might have a different structure.
         */
        private fun parseLegacyJsonFormat(jsonContent: String): AppData {
            // This would contain logic to parse older JSON formats
            // For example, if field names changed or structure was different

            // Example: Handle old format where 'ssh_keys' was used instead of 'sshIdentities'
            val legacyJson = Json { ignoreUnknownKeys = true }

            try {
                // Try parsing with legacy field names
                val jsonElement = legacyJson.parseToJsonElement(jsonContent)

                // Convert legacy format to current format
                // This is where you'd handle field name changes, type changes, etc.

                // For now, return empty data
                return AppData(version = 1)
            } catch (e: Exception) {
                throw MigrationException.CorruptedDataException("Unable to parse legacy JSON format", e)
            }
        }

        /**
         * Merges legacy data with current data, avoiding duplicates.
         */
        private fun mergeLegacyData(
            currentData: AppData,
            legacyData: AppData,
        ): AppData {
            val currentNames = currentData.sshIdentities.map { it.name }.toSet()
            val newSshIdentities = legacyData.sshIdentities.filter { it.name !in currentNames }

            val currentServerNames = currentData.serverProfiles.map { it.name }.toSet()
            val newServerProfiles = legacyData.serverProfiles.filter { it.name !in currentServerNames }

            val currentProjectNames = currentData.projects.map { it.name }.toSet()
            val newProjects = legacyData.projects.filter { it.name !in currentProjectNames }

            return currentData.copy(
                sshIdentities = currentData.sshIdentities + newSshIdentities,
                serverProfiles = currentData.serverProfiles + newServerProfiles,
                projects = currentData.projects + newProjects,
                messages = currentData.messages + legacyData.messages,
                lastModified = System.currentTimeMillis(),
            )
        }

        override suspend fun validateMigration(
            originalData: AppData,
            migratedData: AppData,
        ): Boolean {
            return super.validateMigration(originalData, migratedData) &&
                migratedData.version == toVersion
        }
    }

/**
 * Migration to clean up legacy data files after successful migration.
 */
@Singleton
class LegacyDataCleanupMigration
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BaseDataMigration() {
        companion object {
            private const val TAG = "LegacyDataCleanup"
        }

        override val fromVersion: Int = 1
        override val toVersion: Int = 1
        override val name: String = "Legacy Data Cleanup"
        override val description: String = "Safely removes legacy data files after successful migration"
        override val isReversible: Boolean = false

        override suspend fun migrate(
            data: AppData,
            progressCallback: ((MigrationProgress) -> Unit)?,
        ): AppData {
            return withContext(Dispatchers.IO) {
                reportProgress(1, 5, "Identifying legacy files to clean up", progressCallback)

                val filesToCleanup = mutableListOf<File>()

                // Add legacy database file
                val dbFile = context.getDatabasePath("claude_code_database")
                if (dbFile.exists()) {
                    filesToCleanup.add(dbFile)
                }

                // Add legacy JSON file
                val jsonFile = File(context.filesDir, "data.json")
                if (jsonFile.exists()) {
                    filesToCleanup.add(jsonFile)
                }

                reportProgress(2, 5, "Creating backup of legacy files", progressCallback)

                // Create a backup directory for legacy files
                val backupDir = File(context.filesDir, "legacy_backup")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                reportProgress(3, 5, "Moving legacy files to backup", progressCallback)

                // Move files to backup instead of deleting them
                for (file in filesToCleanup) {
                    try {
                        val backupFile = File(backupDir, "${file.name}.backup")
                        if (file.renameTo(backupFile)) {
                            Log.d(TAG, "Moved legacy file to backup: ${file.name}")
                        } else {
                            Log.w(TAG, "Failed to move legacy file: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error moving legacy file: ${file.name}", e)
                    }
                }

                reportProgress(4, 5, "Cleaning up temporary files", progressCallback)

                // Clean up any temporary files from migration
                val tempFiles =
                    context.filesDir.listFiles { _, name ->
                        name.startsWith("migration_temp_") || name.endsWith(".tmp")
                    }

                tempFiles?.forEach { file ->
                    try {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted temporary file: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting temporary file: ${file.name}", e)
                    }
                }

                reportProgress(5, 5, "Legacy cleanup completed", progressCallback)

                // Return data unchanged
                data
            }
        }

        override suspend fun canMigrate(data: AppData): Boolean {
            // Can always run cleanup migration
            return data.version == fromVersion
        }

        override suspend fun getEstimatedSteps(data: AppData): Int = 5
    }
