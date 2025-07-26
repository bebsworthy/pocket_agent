package com.pocketagent.mobile.di

import com.pocketagent.mobile.domain.repository.MessageRepository
import com.pocketagent.mobile.domain.repository.ProjectRepository
import com.pocketagent.mobile.domain.repository.ServerProfileRepository
import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import com.pocketagent.mobile.domain.usecase.auth.AuthenticateUserUseCase
import com.pocketagent.mobile.domain.usecase.auth.ValidateBiometricUseCase
import com.pocketagent.mobile.domain.usecase.connection.ConnectToProjectUseCase
import com.pocketagent.mobile.domain.usecase.connection.DisconnectFromProjectUseCase
import com.pocketagent.mobile.domain.usecase.connection.GetConnectionStatusUseCase
import com.pocketagent.mobile.domain.usecase.message.GetMessagesUseCase
import com.pocketagent.mobile.domain.usecase.message.SendMessageUseCase
import com.pocketagent.mobile.domain.usecase.project.CreateProjectUseCase
import com.pocketagent.mobile.domain.usecase.project.DeleteProjectUseCase
import com.pocketagent.mobile.domain.usecase.project.GetProjectsUseCase
import com.pocketagent.mobile.domain.usecase.project.UpdateProjectUseCase
import com.pocketagent.mobile.domain.usecase.server.CreateServerProfileUseCase
import com.pocketagent.mobile.domain.usecase.server.DeleteServerProfileUseCase
import com.pocketagent.mobile.domain.usecase.server.GetServerProfilesUseCase
import com.pocketagent.mobile.domain.usecase.server.UpdateServerProfileUseCase
import com.pocketagent.mobile.domain.usecase.ssh.CreateSshIdentityUseCase
import com.pocketagent.mobile.domain.usecase.ssh.DeleteSshIdentityUseCase
import com.pocketagent.mobile.domain.usecase.ssh.GetSshIdentitiesUseCase
import com.pocketagent.mobile.domain.usecase.ssh.ValidateSshKeyUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Domain layer dependency injection module.
 *
 * This module provides dependencies for the domain layer including use cases
 * and domain services.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    // Authentication Use Cases
    @Provides
    @Singleton
    fun provideAuthenticateUserUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): AuthenticateUserUseCase = AuthenticateUserUseCase(dispatcher)

    @Provides
    @Singleton
    fun provideValidateBiometricUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ValidateBiometricUseCase = ValidateBiometricUseCase(dispatcher)

    // SSH Identity Use Cases
    @Provides
    @Singleton
    fun provideGetSshIdentitiesUseCase(
        repository: SshIdentityRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): GetSshIdentitiesUseCase = GetSshIdentitiesUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideCreateSshIdentityUseCase(
        repository: SshIdentityRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CreateSshIdentityUseCase = CreateSshIdentityUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideDeleteSshIdentityUseCase(
        repository: SshIdentityRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): DeleteSshIdentityUseCase = DeleteSshIdentityUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideValidateSshKeyUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ValidateSshKeyUseCase = ValidateSshKeyUseCase(dispatcher)

    // Server Profile Use Cases
    @Provides
    @Singleton
    fun provideGetServerProfilesUseCase(
        repository: ServerProfileRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): GetServerProfilesUseCase = GetServerProfilesUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideCreateServerProfileUseCase(
        repository: ServerProfileRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CreateServerProfileUseCase = CreateServerProfileUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideUpdateServerProfileUseCase(
        repository: ServerProfileRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): UpdateServerProfileUseCase = UpdateServerProfileUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideDeleteServerProfileUseCase(
        repository: ServerProfileRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): DeleteServerProfileUseCase = DeleteServerProfileUseCase(repository, dispatcher)

    // Project Use Cases
    @Provides
    @Singleton
    fun provideGetProjectsUseCase(
        repository: ProjectRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): GetProjectsUseCase = GetProjectsUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideCreateProjectUseCase(
        repository: ProjectRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CreateProjectUseCase = CreateProjectUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideUpdateProjectUseCase(
        repository: ProjectRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): UpdateProjectUseCase = UpdateProjectUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideDeleteProjectUseCase(
        repository: ProjectRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): DeleteProjectUseCase = DeleteProjectUseCase(repository, dispatcher)

    // Connection Use Cases
    @Provides
    @Singleton
    fun provideConnectToProjectUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ConnectToProjectUseCase = ConnectToProjectUseCase(dispatcher)

    @Provides
    @Singleton
    fun provideDisconnectFromProjectUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): DisconnectFromProjectUseCase = DisconnectFromProjectUseCase(dispatcher)

    @Provides
    @Singleton
    fun provideGetConnectionStatusUseCase(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): GetConnectionStatusUseCase = GetConnectionStatusUseCase(dispatcher)

    // Message Use Cases
    @Provides
    @Singleton
    fun provideGetMessagesUseCase(
        repository: MessageRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): GetMessagesUseCase = GetMessagesUseCase(repository, dispatcher)

    @Provides
    @Singleton
    fun provideSendMessageUseCase(
        repository: MessageRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): SendMessageUseCase = SendMessageUseCase(repository, dispatcher)
}
