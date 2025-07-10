package com.pocketagent.di.qualifiers

import javax.inject.Qualifier

/**
 * Qualifier for encrypted shared preferences.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EncryptedPreferences

/**
 * Qualifier for regular shared preferences.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RegularPreferences

/**
 * Qualifier for WebSocket client.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketClient

/**
 * Qualifier for HTTP client.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HttpClient

/**
 * Qualifier for application context.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationContext

/**
 * Qualifier for biometric crypto object.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BiometricCrypto