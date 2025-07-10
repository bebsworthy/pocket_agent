package com.pocketagent.data.validation.di

import com.pocketagent.data.validation.AsyncValidator
import com.pocketagent.data.validation.RepositoryValidationService
import com.pocketagent.data.validation.validators.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
     * Provides Repository Validation Service.
     */
    @Provides
    @Singleton
    fun provideRepositoryValidationService(
        sshIdentityValidator: SshIdentityValidator,
        serverProfileValidator: ServerProfileValidator,
        projectValidator: ProjectValidator,
        messageValidator: MessageValidator,
        businessRuleValidator: BusinessRuleValidator,
        asyncValidator: AsyncValidator
    ): RepositoryValidationService {
        return RepositoryValidationService(
            sshIdentityValidator = sshIdentityValidator,
            serverProfileValidator = serverProfileValidator,
            projectValidator = projectValidator,
            messageValidator = messageValidator,
            businessRuleValidator = businessRuleValidator,
            asyncValidator = asyncValidator
        )
    }
}
