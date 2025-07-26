package com.pocketagent.data.validation.di

import com.pocketagent.data.validation.AsyncValidator
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.BusinessRuleValidator
import com.pocketagent.data.validation.validators.MessageValidator
import com.pocketagent.data.validation.validators.ProjectValidator
import com.pocketagent.data.validation.validators.ServerProfileValidator
import com.pocketagent.data.validation.validators.SshIdentityValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Configuration for validation dependencies.
 */
data class ValidationDependencies(
    val sshIdentityValidator: SshIdentityValidator,
    val serverProfileValidator: ServerProfileValidator,
    val projectValidator: ProjectValidator,
    val messageValidator: MessageValidator,
    val businessRuleValidator: BusinessRuleValidator,
    val asyncValidator: AsyncValidator,
)

/**
 * Dagger Hilt module for providing validation framework dependencies.
 *
 * This module provides all validators, validation services, and related components
 * for the comprehensive data validation framework.
 */
@Module
@InstallIn(SingletonComponent::class)
object ValidationModule {
    /**
     * Provides SSH Identity validator.
     */
    @Provides
    @Singleton
    fun provideSshIdentityValidator(): SshIdentityValidator {
        return SshIdentityValidator()
    }

    /**
     * Provides Server Profile validator.
     */
    @Provides
    @Singleton
    fun provideServerProfileValidator(): ServerProfileValidator {
        return ServerProfileValidator()
    }

    /**
     * Provides Project validator.
     */
    @Provides
    @Singleton
    fun provideProjectValidator(): ProjectValidator {
        return ProjectValidator()
    }

    /**
     * Provides Message validator.
     */
    @Provides
    @Singleton
    fun provideMessageValidator(): MessageValidator {
        return MessageValidator()
    }

    /**
     * Provides Business Rule validator.
     */
    @Provides
    @Singleton
    fun provideBusinessRuleValidator(): BusinessRuleValidator {
        return BusinessRuleValidator()
    }

    /**
     * Provides Async validator.
     */
    @Provides
    @Singleton
    fun provideAsyncValidator(): AsyncValidator {
        return AsyncValidator()
    }

    /**
     * Provides validation dependencies configuration.
     */
    @Provides
    @Singleton
    fun provideValidationDependencies(
        sshIdentityValidator: SshIdentityValidator,
        serverProfileValidator: ServerProfileValidator,
        projectValidator: ProjectValidator,
        messageValidator: MessageValidator,
        businessRuleValidator: BusinessRuleValidator,
        asyncValidator: AsyncValidator,
    ): ValidationDependencies =
        ValidationDependencies(
            sshIdentityValidator = sshIdentityValidator,
            serverProfileValidator = serverProfileValidator,
            projectValidator = projectValidator,
            messageValidator = messageValidator,
            businessRuleValidator = businessRuleValidator,
            asyncValidator = asyncValidator,
        )

    /**
     * Provides Repository Validation Service.
     */
    @Provides
    @Singleton
    fun provideRepositoryValidationService(
        dependencies: ValidationDependencies,
    ): RepositoryValidationService =
        RepositoryValidationService(
            sshIdentityValidator = dependencies.sshIdentityValidator,
            serverProfileValidator = dependencies.serverProfileValidator,
            projectValidator = dependencies.projectValidator,
            messageValidator = dependencies.messageValidator,
            businessRuleValidator = dependencies.businessRuleValidator,
        )
}
