package com.pocketagent.mobile.di

import com.pocketagent.mobile.data.local.EncryptedJsonStorage
import com.pocketagent.data.storage.StorageValidator
import kotlinx.coroutines.flow.Flow
import com.pocketagent.mobile.data.security.SecurityManager
import com.pocketagent.mobile.data.remote.WebSocketClient
import com.pocketagent.mobile.domain.repository.DataRepository
import com.pocketagent.mobile.domain.repository.MessageRepository
import com.pocketagent.mobile.domain.repository.ProjectRepository
import com.pocketagent.mobile.domain.repository.ServerProfileRepository
import com.pocketagent.mobile.domain.repository.SshIdentityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import javax.inject.Singleton

/**
 * Testing module for dependency injection.
 * 
 * This module provides test implementations and mocks for testing purposes.
 * It replaces the production modules when running tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestingModule {
    
    /**
     * Provides a mock encrypted storage for testing.
     */
    @Provides
    @Singleton
    fun provideMockEncryptedStorage(): EncryptedJsonStorage {
        return object : EncryptedJsonStorage {
            private val storage = mutableMapOf<String, String>()
            
            override suspend fun storeJsonData(key: String, jsonData: String) {
                storage[key] = jsonData
            }
            
            override suspend fun getJsonData(key: String): String? {
                return storage[key]
            }
            
            override suspend fun deleteJsonData(key: String) {
                storage.remove(key)
            }
            
            override suspend fun clearAllData() {
                storage.clear()
            }
            
            override suspend fun hasData(key: String): Boolean {
                return storage.containsKey(key)
            }
            
            override fun observeJsonData(key: String): Flow<String?> {
                return kotlinx.coroutines.flow.flowOf(storage[key])
            }
            
            override suspend fun createBackup(): String? {
                return "mock_backup.json"
            }
            
            override suspend fun restoreBackup(backupFilename: String): Boolean {
                return true
            }
            
            override suspend fun validateStorage(): StorageValidator.ValidationReport {
                return StorageValidator.ValidationReport(
                    isValid = true,
                    results = emptyList(),
                    checkedFiles = storage.size,
                    totalSize = storage.values.sumOf { it.length }.toLong()
                )
            }
            
            override suspend fun getStorageStats(): EncryptedJsonStorage.StorageStats {
                return EncryptedJsonStorage.StorageStats(
                    totalFiles = storage.size,
                    totalSize = storage.values.sumOf { it.length }.toLong(),
                    lastModified = System.currentTimeMillis(),
                    backupCount = 0,
                    backupSize = 0,
                    isHealthy = true
                )
            }
        }
    }
    
    /**
     * Provides a mock data repository for testing.
     */
    @Provides
    @Singleton
    fun provideMockDataRepository(): DataRepository {
        return object : DataRepository {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock SSH identity repository for testing.
     */
    @Provides
    @Singleton
    fun provideMockSshIdentityRepository(): SshIdentityRepository {
        return object : SshIdentityRepository {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock server profile repository for testing.
     */
    @Provides
    @Singleton
    fun provideMockServerProfileRepository(): ServerProfileRepository {
        return object : ServerProfileRepository {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock project repository for testing.
     */
    @Provides
    @Singleton
    fun provideMockProjectRepository(): ProjectRepository {
        return object : ProjectRepository {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock message repository for testing.
     */
    @Provides
    @Singleton
    fun provideMockMessageRepository(): MessageRepository {
        return object : MessageRepository {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock WebSocket client for testing.
     */
    @Provides
    @Singleton
    fun provideMockWebSocketClient(): WebSocketClient {
        return object : WebSocketClient {
            // Mock implementation for testing
        }
    }
    
    /**
     * Provides a mock security manager for testing.
     */
    @Provides
    @Singleton
    fun provideMockSecurityManager(): SecurityManager {
        return object : SecurityManager {
            // Mock implementation for testing
        }
    }
}