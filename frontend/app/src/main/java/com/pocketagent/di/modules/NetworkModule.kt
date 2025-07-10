package com.pocketagent.di.modules

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module for network-related dependencies.
 * 
 * This module provides network clients, WebSocket connections,
 * and other network-related services for the entire application scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // Network bindings will be added in later tasks
    // when we implement the communication layer
}