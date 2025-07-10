package com.pocketagent.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Application-level dependency injection module.
 * 
 * This module provides application-wide dependencies that have a singleton scope
 * and are available throughout the entire application lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    
    /**
     * Provides the application context.
     * 
     * @param context The application context
     * @return The application context instance
     */
    @Singleton
    @Provides
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
    
    /**
     * Provides the main dispatcher for coroutines.
     * 
     * @return The main coroutine dispatcher
     */
    @Singleton
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    /**
     * Provides the IO dispatcher for coroutines.
     * 
     * @return The IO coroutine dispatcher
     */
    @Singleton
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    /**
     * Provides the default dispatcher for coroutines.
     * 
     * @return The default coroutine dispatcher
     */
    @Singleton
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

// Qualifiers for different types of coroutine dispatchers
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher