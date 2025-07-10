package com.pocketagent.mobile.di

import com.pocketagent.mobile.data.serialization.JsonConfig
import com.pocketagent.mobile.data.serialization.SerializationErrorHandler
import com.pocketagent.mobile.data.serialization.SerializationUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Dependency injection module for serialization components
 */
@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {
    
    /**
     * Provides the main JSON configuration instance
     */
    @Provides
    @Singleton
    fun provideJsonConfig(): JsonConfig = JsonConfig()
    
    /**
     * Provides the standard JSON instance for general use
     */
    @Provides
    @Singleton
    @StandardJson
    fun provideStandardJson(jsonConfig: JsonConfig): Json = jsonConfig.json
    
    /**
     * Provides the compact JSON instance for network communication
     */
    @Provides
    @Singleton
    @CompactJson
    fun provideCompactJson(jsonConfig: JsonConfig): Json = jsonConfig.compactJson
    
    /**
     * Provides the strict JSON instance for data validation
     */
    @Provides
    @Singleton
    @StrictJson
    fun provideStrictJson(jsonConfig: JsonConfig): Json = jsonConfig.strictJson
    
    /**
     * Provides serialization utilities
     */
    @Provides
    @Singleton
    fun provideSerializationUtils(jsonConfig: JsonConfig): SerializationUtils {
        return SerializationUtils(jsonConfig)
    }
    
    /**
     * Provides serialization error handler
     */
    @Provides
    @Singleton
    fun provideSerializationErrorHandler(): SerializationErrorHandler {
        return SerializationErrorHandler()
    }
}

/**
 * Qualifier annotations for different JSON configurations
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardJson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CompactJson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StrictJson