package com.pocketagent.data.storage.di

import android.content.Context
import com.pocketagent.data.storage.BackupManager
import com.pocketagent.data.storage.FileStorageManager
import com.pocketagent.data.storage.StorageConfiguration
import com.pocketagent.data.storage.StorageEncryption
import com.pocketagent.data.storage.StorageValidator
import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import com.pocketagent.mobile.data.local.EncryptedJsonStorageImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing storage-related dependencies.
 *
 * This module configures the dependency injection for the encrypted storage system,
 * including encryption services, file management, backup functionality, and validation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    /**
     * Binds the EncryptedJsonStorage implementation.
     */
    @Binds
    @Singleton
    abstract fun bindEncryptedJsonStorage(encryptedJsonStorageImpl: EncryptedJsonStorageImpl): EncryptedJsonStorage

    companion object {
        /**
         * Provides the StorageEncryption service.
         */
        @Provides
        @Singleton
        fun provideStorageEncryption(
            @ApplicationContext context: Context,
        ): StorageEncryption = StorageEncryption(context)

        /**
         * Provides the FileStorageManager service.
         */
        @Provides
        @Singleton
        fun provideFileStorageManager(
            @ApplicationContext context: Context,
        ): FileStorageManager = FileStorageManager(context)

        /**
         * Provides the StorageConfiguration service.
         */
        @Provides
        @Singleton
        fun provideStorageConfiguration(
            @ApplicationContext context: Context,
        ): StorageConfiguration = StorageConfiguration(context)

        /**
         * Provides the StorageValidator service.
         */
        @Provides
        @Singleton
        fun provideStorageValidator(
            @ApplicationContext context: Context,
            fileStorageManager: FileStorageManager,
            storageEncryption: StorageEncryption,
        ): StorageValidator = StorageValidator(context, fileStorageManager, storageEncryption)

        /**
         * Provides the BackupManager service.
         */
        @Provides
        @Singleton
        fun provideBackupManager(
            @ApplicationContext context: Context,
            fileStorageManager: FileStorageManager,
            storageConfiguration: StorageConfiguration,
            storageEncryption: StorageEncryption,
        ): BackupManager = BackupManager(context, fileStorageManager, storageConfiguration, storageEncryption)
    }
}
