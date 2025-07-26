package com.pocketagent.mobile.di

import com.pocketagent.mobile.data.remote.AuthenticationHandler
import com.pocketagent.mobile.data.remote.AuthenticationHandlerImpl
import com.pocketagent.mobile.data.remote.ConnectionHealthMonitor
import com.pocketagent.mobile.data.remote.ConnectionHealthMonitorImpl
import com.pocketagent.mobile.data.remote.MessageHandler
import com.pocketagent.mobile.data.remote.MessageHandlerImpl
import com.pocketagent.mobile.data.remote.WebSocketClient
import com.pocketagent.mobile.data.remote.WebSocketClientImpl
import com.pocketagent.mobile.data.remote.WebSocketConnectionManager
import com.pocketagent.mobile.data.remote.WebSocketConnectionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Network layer dependency injection module.
 *
 * This module provides dependencies for the network layer including WebSocket clients,
 * HTTP clients, and network-related services.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    /**
     * Binds the WebSocket client implementation to the interface.
     *
     * @param impl The WebSocket client implementation
     * @return The WebSocket client interface
     */
    @Binds
    @Singleton
    abstract fun bindWebSocketClient(impl: WebSocketClientImpl): WebSocketClient

    /**
     * Binds the WebSocket connection manager implementation to the interface.
     *
     * @param impl The WebSocket connection manager implementation
     * @return The WebSocket connection manager interface
     */
    @Binds
    @Singleton
    abstract fun bindWebSocketConnectionManager(impl: WebSocketConnectionManagerImpl): WebSocketConnectionManager

    /**
     * Binds the message handler implementation to the interface.
     *
     * @param impl The message handler implementation
     * @return The message handler interface
     */
    @Binds
    @Singleton
    abstract fun bindMessageHandler(impl: MessageHandlerImpl): MessageHandler

    /**
     * Binds the authentication handler implementation to the interface.
     *
     * @param impl The authentication handler implementation
     * @return The authentication handler interface
     */
    @Binds
    @Singleton
    abstract fun bindAuthenticationHandler(impl: AuthenticationHandlerImpl): AuthenticationHandler

    /**
     * Binds the connection health monitor implementation to the interface.
     *
     * @param impl The connection health monitor implementation
     * @return The connection health monitor interface
     */
    @Binds
    @Singleton
    abstract fun bindConnectionHealthMonitor(impl: ConnectionHealthMonitorImpl): ConnectionHealthMonitor

    companion object {
        /**
         * Provides the OkHttpClient instance for network operations.
         *
         * @return The configured OkHttpClient instance
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val loggingInterceptor =
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

            return OkHttpClient
                .Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Provides the WebSocket connection timeout in milliseconds.
         *
         * @return The connection timeout value
         */
        @Provides
        @Singleton
        @ConnectionTimeout
        fun provideConnectionTimeout(): Long {
            val timeout = 30_000L
            return timeout
        }

        /**
         * Provides the WebSocket ping interval in milliseconds.
         *
         * @return The ping interval value
         */
        @Provides
        @Singleton
        @PingInterval
        fun providePingInterval(): Long {
            val interval = 30_000L
            return interval
        }

        /**
         * Provides the maximum number of reconnection attempts.
         *
         * @return The maximum reconnection attempts
         */
        @Provides
        @Singleton
        @MaxReconnectAttempts
        fun provideMaxReconnectAttempts(): Int {
            val attempts = 5
            return attempts
        }

        /**
         * Provides the base delay for exponential backoff reconnection.
         *
         * @return The base delay in milliseconds
         */
        @Provides
        @Singleton
        @ReconnectBaseDelay
        fun provideReconnectBaseDelay(): Long {
            val delay = 1_000L
            return delay
        }
    }
}

// Qualifiers for network configuration
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConnectionTimeout

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PingInterval

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MaxReconnectAttempts

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReconnectBaseDelay
