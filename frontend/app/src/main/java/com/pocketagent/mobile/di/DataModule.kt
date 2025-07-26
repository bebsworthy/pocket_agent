package com.pocketagent.mobile.di

import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import com.pocketagent.mobile.data.local.EncryptedJsonStorageImpl
import com.pocketagent.mobile.data.repository.DataRepositoryImpl
import com.pocketagent.mobile.data.repository.MessageRepositoryImpl
import com.pocketagent.mobile.data.repository.ProjectRepositoryImpl
import com.pocketagent.mobile.data.repository.ServerProfileRepositoryImpl
import com.pocketagent.mobile.data.repository.SshIdentityRepositoryImpl
import com.pocketagent.mobile.domain.repository.DataRepository
import com.pocketagent.mobile.domain.repository.MessageRepository
import com.pocketagent.mobile.domain.repository.ProjectRepository
import com.pocketagent.mobile.domain.repository.ServerProfileRepository
import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Data layer dependency injection module.
 *
 * This module provides dependencies for the data layer including repositories,
 * storage implementations, and data access objects.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    /**
     * Binds the encrypted storage implementation to the interface.
     *
     * @param impl The encrypted storage implementation
     * @return The encrypted storage interface
     */
    @Binds
    @Singleton
    abstract fun bindEncryptedStorage(impl: EncryptedJsonStorageImpl): EncryptedJsonStorage

    /**
     * Binds the data repository implementation to the interface.
     *
     * @param impl The data repository implementation
     * @return The data repository interface
     */
    @Binds
    @Singleton
    abstract fun bindDataRepository(impl: DataRepositoryImpl): DataRepository

    /**
     * Binds the SSH identity repository implementation to the interface.
     *
     * @param impl The SSH identity repository implementation
     * @return The SSH identity repository interface
     */
    @Binds
    @Singleton
    abstract fun bindSshIdentityRepository(impl: SshIdentityRepositoryImpl): SshIdentityRepository

    /**
     * Binds the server profile repository implementation to the interface.
     *
     * @param impl The server profile repository implementation
     * @return The server profile repository interface
     */
    @Binds
    @Singleton
    abstract fun bindServerProfileRepository(impl: ServerProfileRepositoryImpl): ServerProfileRepository

    /**
     * Binds the project repository implementation to the interface.
     *
     * @param impl The project repository implementation
     * @return The project repository interface
     */
    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    /**
     * Binds the message repository implementation to the interface.
     *
     * @param impl The message repository implementation
     * @return The message repository interface
     */
    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    companion object {
        /**
         * Provides the storage file name for encrypted data.
         *
         * @return The storage file name
         */
        @Provides
        @Singleton
        @StorageFileName
        fun provideStorageFileName(): String {
            val fileName = "pocket_agent_data.json"
            return fileName
        }

        /**
         * Provides the preferences name for encrypted preferences.
         *
         * @return The preferences name
         */
        @Provides
        @Singleton
        @PreferencesName
        fun providePreferencesName(): String {
            val prefsName = "pocket_agent_prefs"
            return prefsName
        }
    }
}

// Qualifiers for storage configuration
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StorageFileName

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PreferencesName
