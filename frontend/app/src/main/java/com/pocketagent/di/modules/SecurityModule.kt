package com.pocketagent.di.modules

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module for security-related dependencies.
 * 
 * This module provides security services including biometric authentication,
 * encryption services, and secure storage for the entire application scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    // Security bindings will be added in later tasks
    // when we implement the security layer
}