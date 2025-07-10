package com.pocketagent.data.migration.di

import android.content.Context
import com.pocketagent.data.migration.DataMigration
import com.pocketagent.data.migration.DataMigrationManager
import com.pocketagent.data.migration.MigrationRegistry
import com.pocketagent.data.migration.migrations.DataRepairMigration
import com.pocketagent.data.migration.migrations.InitialMigration
import com.pocketagent.data.migration.migrations.LegacyDataCleanupMigration
import com.pocketagent.data.migration.migrations.LegacyDataMigration
import com.pocketagent.data.migration.migrations.Version1To2Migration
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing migration-related dependencies.
 * 
 * This module configures the dependency injection for the data migration system,
 * including migration registry, migration manager, and individual migration implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {
    
    /**
     * Provides the migration registry with all available migrations.
     */
    @Provides
    @Singleton
    fun provideMigrationRegistry(
        migrations: Set<@JvmSuppressWildcards DataMigration>
    ): MigrationRegistry {
        val registry = MigrationRegistry()
        
        // Register all available migrations
        runBlocking {
            migrations.forEach { migration ->
                registry.registerMigration(migration)
            }
        }
        
        return registry
    }
    
    /**
     * Provides the data migration manager.
     */
    @Provides
    @Singleton
    fun provideDataMigrationManager(
        @ApplicationContext context: Context,
        encryptedStorage: EncryptedJsonStorage,
        migrationRegistry: MigrationRegistry,
        dataValidator: DataValidator
    ): DataMigrationManager {
        return DataMigrationManager(
            context = context,
            encryptedStorage = encryptedStorage,
            migrationRegistry = migrationRegistry,
            dataValidator = dataValidator
        )
    }
    
    // Individual Migration Providers
    
    /**
     * Provides the initial migration.
     */
    @Provides
    @IntoSet
    fun provideInitialMigration(
        initialMigration: InitialMigration
    ): DataMigration = initialMigration
    
    /**
     * Provides the version 1 to 2 migration (future migration example).
     */
    @Provides
    @IntoSet
    fun provideVersion1To2Migration(
        version1To2Migration: Version1To2Migration
    ): DataMigration = version1To2Migration
    
    /**
     * Provides the data repair migration.
     */
    @Provides
    @IntoSet
    fun provideDataRepairMigration(
        dataRepairMigration: DataRepairMigration
    ): DataMigration = dataRepairMigration
    
    /**
     * Provides the legacy data migration.
     */
    @Provides
    @IntoSet
    fun provideLegacyDataMigration(
        legacyDataMigration: LegacyDataMigration
    ): DataMigration = legacyDataMigration
    
    /**
     * Provides the legacy data cleanup migration.
     */
    @Provides
    @IntoSet
    fun provideLegacyDataCleanupMigration(
        legacyDataCleanupMigration: LegacyDataCleanupMigration
    ): DataMigration = legacyDataCleanupMigration
}

/**
 * Additional module for migration-specific configurations and utilities.
 */
@Module
@InstallIn(SingletonComponent::class)
object MigrationConfigurationModule {
    
    /**
     * Provides migration configuration settings.
     */
    @Provides
    @Singleton
    fun provideMigrationConfiguration(
        @ApplicationContext context: Context
    ): MigrationConfiguration {
        return MigrationConfiguration(
            autoMigrationEnabled = true,
            backupBeforeMigration = true,
            maxBackupFiles = 5,
            migrationTimeoutMs = 30_000L, // 30 seconds
            enableProgressReporting = true,
            validateAfterMigration = true
        )
    }
}

/**
 * Configuration class for migration behavior.
 */
data class MigrationConfiguration(
    val autoMigrationEnabled: Boolean = true,
    val backupBeforeMigration: Boolean = true,
    val maxBackupFiles: Int = 5,
    val migrationTimeoutMs: Long = 30_000L,
    val enableProgressReporting: Boolean = true,
    val validateAfterMigration: Boolean = true
) {
    
    init {
        require(maxBackupFiles >= 0) { "Max backup files must be non-negative" }
        require(migrationTimeoutMs > 0) { "Migration timeout must be positive" }
    }
    
    companion object {
        /**
         * Creates a default configuration for production use.
         */
        fun default(): MigrationConfiguration {
            return MigrationConfiguration()
        }
        
        /**
         * Creates a configuration optimized for testing.
         */
        fun testing(): MigrationConfiguration {
            return MigrationConfiguration(
                backupBeforeMigration = false,
                maxBackupFiles = 1,
                migrationTimeoutMs = 5_000L,
                enableProgressReporting = false,
                validateAfterMigration = true
            )
        }
        
        /**
         * Creates a configuration for development with verbose logging.
         */
        fun development(): MigrationConfiguration {
            return MigrationConfiguration(
                autoMigrationEnabled = true,
                backupBeforeMigration = true,
                maxBackupFiles = 10,
                migrationTimeoutMs = 60_000L,
                enableProgressReporting = true,
                validateAfterMigration = true
            )
        }
    }
}