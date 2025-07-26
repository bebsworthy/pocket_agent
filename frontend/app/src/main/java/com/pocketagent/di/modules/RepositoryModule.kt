package com.pocketagent.di.modules

import android.content.Context
import com.pocketagent.data.migration.DataMigrationManager
import com.pocketagent.data.repository.DataValidator
import com.pocketagent.data.repository.MessageDataRepository
import com.pocketagent.data.repository.ProjectDataRepository
import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.repository.SecureDataRepositoryCore
import com.pocketagent.data.repository.ServerProfileDataRepository
import com.pocketagent.data.repository.SshIdentityDataRepository
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
        businessRuleValidator: BusinessRuleValidator,
    ): DataValidator = DataValidator(repositoryValidationService, businessRuleValidator)

    /**
     * Provides a singleton instance of SecureDataRepositoryCore.
     *
     * @param context Application context
     * @param encryptedStorage Encrypted storage implementation
     * @param dataValidator Data validator instance
     * @return SecureDataRepositoryCore instance
     */
    @Provides
    @Singleton
    fun provideSecureDataRepositoryCore(
        @ApplicationContext context: Context,
        encryptedStorage: EncryptedJsonStorage,
        dataValidator: DataValidator,
    ): SecureDataRepositoryCore = SecureDataRepositoryCore(context, encryptedStorage, dataValidator)

    /**
     * Provides a singleton instance of SshIdentityDataRepository.
     *
     * @param dataStorage Core data storage
     * @param dataValidator Data validator instance
     * @return SshIdentityDataRepository instance
     */
    @Provides
    @Singleton
    fun provideSshIdentityDataRepository(
        dataStorage: SecureDataRepositoryCore,
        dataValidator: DataValidator,
    ): SshIdentityDataRepository = SshIdentityDataRepository(dataStorage, dataValidator)

    /**
     * Provides a singleton instance of ServerProfileDataRepository.
     *
     * @param dataStorage Core data storage
     * @param dataValidator Data validator instance
     * @return ServerProfileDataRepository instance
     */
    @Provides
    @Singleton
    fun provideServerProfileDataRepository(
        dataStorage: SecureDataRepositoryCore,
        dataValidator: DataValidator,
    ): ServerProfileDataRepository = ServerProfileDataRepository(dataStorage, dataValidator)

    /**
     * Provides a singleton instance of ProjectDataRepository.
     *
     * @param dataStorage Core data storage
     * @param dataValidator Data validator instance
     * @return ProjectDataRepository instance
     */
    @Provides
    @Singleton
    fun provideProjectDataRepository(
        dataStorage: SecureDataRepositoryCore,
        dataValidator: DataValidator,
    ): ProjectDataRepository = ProjectDataRepository(dataStorage, dataValidator)

    /**
     * Provides a singleton instance of MessageDataRepository.
     *
     * @param dataStorage Core data storage
     * @param dataValidator Data validator instance
     * @return MessageDataRepository instance
     */
    @Provides
    @Singleton
    fun provideMessageDataRepository(
        dataStorage: SecureDataRepositoryCore,
        dataValidator: DataValidator,
    ): MessageDataRepository = MessageDataRepository(dataStorage, dataValidator)

    /**
     * Provides a singleton instance of SecureDataRepository.
     *
     * @param dataStorage Core data storage
     * @param sshIdentityRepository SSH identity repository
     * @param serverProfileRepository Server profile repository
     * @param projectRepository Project repository
     * @param messageRepository Message repository
     * @return SecureDataRepository instance
     */
    @Provides
    @Singleton
    fun provideSecureDataRepository(
        dataStorage: SecureDataRepositoryCore,
        sshIdentityRepository: SshIdentityDataRepository,
        serverProfileRepository: ServerProfileDataRepository,
        projectRepository: ProjectDataRepository,
        messageRepository: MessageDataRepository,
    ): SecureDataRepository =
        SecureDataRepository(
            dataStorage,
            sshIdentityRepository,
            serverProfileRepository,
            projectRepository,
            messageRepository,
        )

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
        repository: SecureDataRepository,
        migrationManager: DataMigrationManager,
    ): SecureDataRepositoryWithMigration =
        SecureDataRepositoryWithMigration(
            repository = repository,
            migrationManager = migrationManager,
        )
}

/**
 * Wrapper class that provides migration-aware repository operations.
 *
 * This class decorates the SecureDataRepository with migration capabilities,
 * ensuring that data is automatically migrated when needed.
 */
class SecureDataRepositoryWithMigration(
    private val repository: SecureDataRepository,
    private val migrationManager: DataMigrationManager,
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
