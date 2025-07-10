package com.pocketagent.di

import android.content.Context
import com.pocketagent.di.modules.CoroutineModule
import com.pocketagent.data.service.di.ServiceModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Main dependency injection module for the application.
 * Provides application-wide dependencies.
 */
@Module(includes = [CoroutineModule::class, ServiceModule::class])
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
}