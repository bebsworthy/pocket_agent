package com.pocketagent.mobile.di

import android.content.Context
import androidx.biometric.BiometricManager
import com.pocketagent.mobile.data.security.BiometricAuthenticationManager
import com.pocketagent.mobile.data.security.BiometricAuthenticationManagerImpl
import com.pocketagent.mobile.data.security.EncryptionService
import com.pocketagent.mobile.data.security.EncryptionServiceImpl
import com.pocketagent.mobile.data.security.KeystoreManager
import com.pocketagent.mobile.data.security.KeystoreManagerImpl
import com.pocketagent.mobile.data.security.SecurityManager
import com.pocketagent.mobile.data.security.SecurityManagerImpl
import com.pocketagent.mobile.data.security.SshKeyManager
import com.pocketagent.mobile.data.security.SshKeyManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Security layer dependency injection module.
 *
 * This module provides dependencies for the security layer including encryption,
 * biometric authentication, SSH key management, and keystore operations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    /**
     * Binds the keystore manager implementation to the interface.
     *
     * @param impl The keystore manager implementation
     * @return The keystore manager interface
     */
    @Binds
    @Singleton
    abstract fun bindKeystoreManager(impl: KeystoreManagerImpl): KeystoreManager

    /**
     * Binds the encryption service implementation to the interface.
     *
     * @param impl The encryption service implementation
     * @return The encryption service interface
     */
    @Binds
    @Singleton
    abstract fun bindEncryptionService(impl: EncryptionServiceImpl): EncryptionService

    /**
     * Binds the biometric authentication manager implementation to the interface.
     *
     * @param impl The biometric authentication manager implementation
     * @return The biometric authentication manager interface
     */
    @Binds
    @Singleton
    abstract fun bindBiometricAuthenticationManager(impl: BiometricAuthenticationManagerImpl): BiometricAuthenticationManager

    /**
     * Binds the SSH key manager implementation to the interface.
     *
     * @param impl The SSH key manager implementation
     * @return The SSH key manager interface
     */
    @Binds
    @Singleton
    abstract fun bindSshKeyManager(impl: SshKeyManagerImpl): SshKeyManager

    /**
     * Binds the security manager implementation to the interface.
     *
     * @param impl The security manager implementation
     * @return The security manager interface
     */
    @Binds
    @Singleton
    abstract fun bindSecurityManager(impl: SecurityManagerImpl): SecurityManager

    companion object {
        /**
         * Provides the biometric manager instance.
         *
         * @param context The application context
         * @return The biometric manager instance
         */
        @Provides
        @Singleton
        fun provideBiometricManager(
            @ApplicationContext context: Context,
        ): BiometricManager = BiometricManager.from(context)

        /**
         * Provides the keystore alias for encryption keys.
         *
         * @return The keystore alias
         */
        @Provides
        @Singleton
        @KeystoreAlias
        fun provideKeystoreAlias(): String {
            val alias = "pocket_agent_master_key"
            return alias
        }

        /**
         * Provides the SSH keystore alias prefix.
         *
         * @return The SSH keystore alias prefix
         */
        @Provides
        @Singleton
        @SshKeystoreAlias
        fun provideSshKeystoreAlias(): String {
            val prefix = "pocket_agent_ssh_key_"
            return prefix
        }

        /**
         * Provides the token vault alias for token encryption.
         *
         * @return The token vault alias
         */
        @Provides
        @Singleton
        @TokenVaultAlias
        fun provideTokenVaultAlias(): String {
            val vaultAlias = "pocket_agent_token_vault"
            return vaultAlias
        }
    }
}

// Qualifiers for security configuration
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KeystoreAlias

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SshKeystoreAlias

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TokenVaultAlias
