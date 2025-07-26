package com.pocketagent.di.qualifiers

import javax.inject.Qualifier

/**
 * Qualifier for main dispatcher (UI thread).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Qualifier for IO dispatcher (file I/O, network operations).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for Default dispatcher (CPU-intensive operations).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Qualifier for Unconfined dispatcher (testing and immediate execution).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UnconfinedDispatcher

/**
 * Qualifier for Application scope (application-wide coroutines).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Qualifier for WebSocket scope (WebSocket connection lifecycle).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketScope

/**
 * Qualifier for Background scope (background monitoring service).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackgroundScope

/**
 * Qualifier for Test scope (testing utilities).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TestScope
