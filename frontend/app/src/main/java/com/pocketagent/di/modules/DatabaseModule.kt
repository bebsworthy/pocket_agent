package com.pocketagent.di.modules

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module for database-related dependencies.
 * 
 * This module provides database instances and related data access objects
 * for the entire application scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    // Database bindings will be added in later tasks
    // when we implement the actual data layer
}