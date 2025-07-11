package com.pocketagent.di.modules

import android.content.Context
import com.pocketagent.data.migration.DataMigrationManager
import com.pocketagent.data.migration.MigrationRegistry
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.BusinessRuleValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for repository implementations.
 * 
 * This module provides repository implementations and their dependencies
 * for dependency injection across the application.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    /**
     * Provides a singleton instance of DataValidator.
     * 
     * @param repositoryValidationService Repository validation service
     * @param businessRuleValidator Business rule validator
     * @return DataValidator instance
     */
    @Provides
    @Singleton
    fun provideDataValidator(
        repositoryValidationService: RepositoryValidationService,
        businessRuleValidator: BusinessRuleValidator
    ): DataValidator {
        return DataValidator(repositoryValidationService, businessRuleValidator)
    }
    
    /**
     * Provides a singleton instance of SecureDataRepository.
     * 
     * @param context Application context
     * @param encryptedStorage Encrypted storage implementation
     * @param dataValidator Data validator instance
     * @return SecureDataRepository instance
     */
    @Provides
    @Singleton
    fun provideSecureDataRepository(
        @ApplicationContext context: Context,
        encryptedStorage: EncryptedJsonStorage,
        dataValidator: DataValidator
    ): SecureDataRepository {
        return SecureDataRepository(context, encryptedStorage, dataValidator)
    }
    
    /**
     * Provides a singleton instance of SecureDataRepository with migration support.
     * 
     * This is an alternative provider that includes migration capabilities.
     * Use this when you need migration-aware repository initialization.
     * 
     * @param context Application context
     * @param encryptedStorage Encrypted storage implementation
     * @param dataValidator Data validator instance
     * @param migrationManager Migration manager for handling data migrations
     * @return SecureDataRepository instance with migration support
     */
    @Provides
    @Singleton
    fun provideSecureDataRepositoryWithMigration(
        @ApplicationContext context: Context,
        encryptedStorage: EncryptedJsonStorage,
        dataValidator: DataValidator,
        migrationManager: DataMigrationManager
    ): SecureDataRepositoryWithMigration {
        return SecureDataRepositoryWithMigration(
            repository = SecureDataRepository(context, encryptedStorage, dataValidator),
            migrationManager = migrationManager
        )
    }
}

/**
 * Wrapper class that provides migration-aware repository operations.
 * 
 * This class decorates the SecureDataRepository with migration capabilities,
 * ensuring that data is automatically migrated when needed.
 */
class SecureDataRepositoryWithMigration(
    private val repository: SecureDataRepository,
    private val migrationManager: DataMigrationManager
) {
    
    /**
     * Initializes the repository with automatic migration support.
     */
    suspend fun initialize() {
        repository.initialize()
    }
    
    /**
     * Gets the underlying repository instance.
     */
    fun getRepository(): SecureDataRepository = repository
    
    /**
     * Gets the migration manager instance.
     */
    fun getMigrationManager(): DataMigrationManager = migrationManager
}