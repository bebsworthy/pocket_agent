package com.pocketagent.data.service.di

import com.pocketagent.data.repository.SecureDataRepository
import com.pocketagent.data.service.*
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.ProjectValidator
import com.pocketagent.data.validation.validators.ServerProfileValidator
import com.pocketagent.data.validation.validators.SshIdentityValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing service layer dependencies.
 * 
 * This module provides instances of service classes that handle business logic
 * and coordinate between the data layer and presentation layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    /**
     * Provides SSH key parser for handling SSH key format conversion and parsing.
     */
    @Provides
    @Singleton
    fun provideSshKeyParser(): SshKeyParser {
        return SshKeyParser()
    }
    
    /**
     * Provides SSH key encryption service for secure key storage.
     */
    @Provides
    @Singleton
    fun provideSshKeyEncryption(): SshKeyEncryption {
        return SshKeyEncryption()
    }
    
    /**
     * Provides comprehensive SSH identity service with CRUD operations.
     * 
     * @param repository Secure data repository for persistence
     * @param validator SSH identity validator for validation
     * @param sshKeyParser SSH key parser for format handling
     * @param sshKeyEncryption SSH key encryption service for security
     */
    @Provides
    @Singleton
    fun provideSshIdentityService(
        repository: SecureDataRepository,
        validator: SshIdentityValidator,
        sshKeyParser: SshKeyParser,
        sshKeyEncryption: SshKeyEncryption
    ): SshIdentityService {
        return SshIdentityService(
            repository = repository,
            validator = validator,
            sshKeyParser = sshKeyParser,
            sshKeyEncryption = sshKeyEncryption
        )
    }
    
    /**
     * Provides server connection tester for testing network connectivity.
     * 
     * @param sshIdentityService SSH identity service for authentication validation
     */
    @Provides
    @Singleton
    fun provideServerConnectionTester(
        sshIdentityService: SshIdentityService
    ): ServerConnectionTester {
        return ServerConnectionTester(sshIdentityService)
    }
    
    /**
     * Provides network configuration validator for validating server configurations.
     */
    @Provides
    @Singleton
    fun provideNetworkConfigurationValidator(): NetworkConfigurationValidator {
        return NetworkConfigurationValidator()
    }
    
    /**
     * Provides comprehensive server profile service with CRUD operations.
     * 
     * @param repository Secure data repository for persistence
     * @param validator Server profile validator for validation
     * @param sshIdentityService SSH identity service for SSH key management
     * @param connectionTester Server connection tester for connectivity testing
     * @param networkValidator Network configuration validator for configuration validation
     */
    @Provides
    @Singleton
    fun provideServerProfileService(
        repository: SecureDataRepository,
        validator: ServerProfileValidator,
        sshIdentityService: SshIdentityService,
        connectionTester: ServerConnectionTester,
        networkValidator: NetworkConfigurationValidator
    ): ServerProfileService {
        return ServerProfileService(
            repository = repository,
            validator = validator,
            sshIdentityService = sshIdentityService,
            connectionTester = connectionTester,
            networkValidator = networkValidator
        )
    }
    
    /**
     * Provides comprehensive project service with CRUD operations.
     * 
     * @param repository Secure data repository for persistence
     * @param validator Project validator for validation
     * @param serverProfileService Server profile service for server integration
     * @param repositoryValidationService Repository validation service for URL validation
     */
    @Provides
    @Singleton
    fun provideProjectService(
        repository: SecureDataRepository,
        validator: ProjectValidator,
        serverProfileService: ServerProfileService,
        repositoryValidationService: RepositoryValidationService
    ): ProjectService {
        return ProjectService(
            repository = repository,
            validator = validator,
            serverProfileService = serverProfileService,
            repositoryValidationService = repositoryValidationService
        )
    }
    
    /**
     * Provides project initialization utilities for project setup operations.
     */
    @Provides
    @Singleton
    fun provideProjectInitializationUtils(): ProjectInitializationUtils {
        return ProjectInitializationUtils()
    }
    
    /**
     * Provides project status manager for status workflow management.
     * 
     * @param projectService Project service for status updates
     */
    @Provides
    @Singleton
    fun provideProjectStatusManager(
        projectService: ProjectService
    ): ProjectStatusManager {
        return ProjectStatusManager(projectService)
    }
}