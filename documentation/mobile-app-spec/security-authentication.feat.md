# Security & Authentication Feature Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [Android Keystore Integration](#android-keystore-integration)
   - [Biometric Authentication](#biometric-authentication)
   - [SSH Key Import Manager](#ssh-key-import-manager)
   - [Token Vault](#token-vault)
   - [Encrypted Storage](#encrypted-storage)
   - [Security Manager](#security-manager)
   - [Auto Lock Manager](#auto-lock-manager)
   - [Security Audit Logger](#security-audit-logger)
   - [Certificate Validator](#certificate-validator)
   - [WebSocket Authentication](#websocket-authentication)
   - [UI Components](#ui-components)
   - [Error Handling](#error-handling)
   - [Dependency Injection](#dependency-injection)
4. [Testing](#testing)
   - [Security Testing Checklist](#security-testing-checklist)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Security Best Practices](#security-best-practices)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Migration Strategy](#migration-strategy)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The Security & Authentication feature provides comprehensive security infrastructure for **Pocket Agent - a remote coding agent mobile interface**. This feature implements biometric authentication, secure credential storage, and encrypted data persistence to protect sensitive user information including SSH private keys, API tokens, and session data.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: Security-first design with hardware-backed cryptography
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#security-implementation)

This feature implements the security requirements defined in the [Frontend Technical Specification](./frontend.spec.md), serving as the foundation for all secure operations including token storage and user authentication. The implementation leverages Android's hardware security features when available.

## Architecture

### Technology Stack (Android-Specific)

- **Android Keystore**: Hardware-backed key storage (API 23+, enhanced in API 28+)
- **BiometricPrompt**: Unified biometric authentication (API 28+, compat to API 23)
- **EncryptedSharedPreferences**: Secure key-value storage (Security-Crypto 1.1.0-alpha06+)
- **Tink**: Google's crypto library for additional cryptographic operations
- **Bouncy Castle**: For SSH key generation and manipulation
- **Kotlin Coroutines**: For async cryptographic operations
- **Hardware Security Module**: StrongBox support for Pixel 3+ and compatible devices

### Key Components

- **KeystoreManager**: Manages cryptographic keys in Android Keystore
- **BiometricAuthManager**: Handles biometric authentication flows
- **AppLaunchAuthManager**: Single authentication on app launch
- **SshKeyImportManager**: Imports and securely stores SSH private keys
- **TokenVault**: Secure storage for API tokens and credentials
- **EncryptionService**: Provides encryption/decryption operations
- **SecurityValidator**: Validates security state and requirements
- **SecurityAuditLogger**: Logs security events for compliance
- **CertificateValidator**: Validates server certificates with pinning

### Single Authentication Flow

Pocket Agent uses a single biometric authentication when the app launches:

1. **App Launch**: User opens the app
2. **Biometric Prompt**: Single authentication prompt appears
3. **Data Unlock**: Successful auth unlocks the encrypted data file
4. **Session Active**: All SSH keys and tokens accessible for the session
5. **Background Lock**: App locks when going to background
6. **Resume**: Re-authentication required when returning to foreground

This approach provides better UX while maintaining security - users authenticate once to unlock all their data, rather than being prompted repeatedly for each operation.

## Components Architecture

### Android Keystore Integration

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor() {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MASTER_KEY_ALIAS = "pocket_agent_master_key"
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }
    
    /**
     * Generate or retrieve the master encryption key
     */
    fun getOrCreateMasterKey(): SecretKey {
        return if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        } else {
            generateMasterKey()
        }
    }
    
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setUserAuthenticationRequired(false) // Master key always accessible
            
            // Use StrongBox if available (hardware security module)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }.build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Create a key that requires biometric authentication
     */
    fun createBiometricKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setUserAuthenticationRequired(true)
            setUserAuthenticationValidityDurationSeconds(-1) // Require auth every time
            setInvalidatedByBiometricEnrollment(true)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    0, // Require authentication every time
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            }
        }.build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
    
    fun keyExists(alias: String): Boolean = keyStore.containsAlias(alias)
}
```


### Biometric Authentication

```kotlin
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BiometricAuthManager @Inject constructor(
    private val context: Context
) {
    
    enum class BiometricStatus {
        AVAILABLE,
        NOT_ENROLLED,
        NO_HARDWARE,
        UNAVAILABLE
    }
    
    fun getBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }
    
    /**
     * Authenticate with biometrics
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Use your biometric credential",
        description: String? = null,
        cipher: Cipher? = null
    ): BiometricPrompt.AuthenticationResult = suspendCancellableCoroutine { cont ->
        
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cont.resumeWithException(
                        BiometricAuthException.AuthenticationError(errorCode, errString.toString())
                    )
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    cont.resume(result)
                }
                
                override fun onAuthenticationFailed() {
                    cont.resumeWithException(
                        BiometricAuthException.AuthenticationFailed()
                    )
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                description?.let { setDescription(it) }
            }
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        if (cipher != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
        
        cont.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }
    
    /**
     * Authenticate with device credentials (PIN/Pattern/Password) as fallback
     */
    suspend fun authenticateWithDeviceCredentials(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Enter your device credentials"
    ): BiometricPrompt.AuthenticationResult = suspendCancellableCoroutine { cont ->
        
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cont.resumeWithException(
                        BiometricAuthException.AuthenticationError(errorCode, errString.toString())
                    )
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    cont.resume(result)
                }
                
                override fun onAuthenticationFailed() {
                    cont.resumeWithException(
                        BiometricAuthException.AuthenticationFailed()
                    )
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        
        biometricPrompt.authenticate(promptInfo)
        
        cont.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }
}
```

### SSH Key Import Manager

```kotlin
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyImportManager @Inject constructor(
    private val encryptionService: EncryptionService,
    private val biometricAuthManager: BiometricAuthManager
) {
    
    companion object {
        private const val SSH_KEY_ALIAS_PREFIX = "ssh_imported_"
        private const val SUPPORTED_KEY_TYPE = KeyPair.RSA
    }
    
    data class ImportedSshKey(
        val encryptedPrivateKey: String, // Base64 encoded encrypted key
        val publicKeyFingerprint: String,
        val keyType: String,
        val keySize: Int
    )
    
    /**
     * Import an existing SSH private key
     * @param privateKeyContent The content of the private key file (e.g., id_rsa)
     * @param passphrase Optional passphrase for encrypted keys
     * @return ImportedSshKey with encrypted private key and metadata
     */
    suspend fun importSshPrivateKey(
        privateKeyContent: String,
        passphrase: String? = null,
        keyAlias: String
    ): ImportedSshKey = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            
            // Parse the private key
            val keyPair = KeyPair.load(jsch, privateKeyContent.toByteArray(), null)
            
            // Decrypt with passphrase if provided
            if (passphrase != null && keyPair.isEncrypted) {
                if (!keyPair.decrypt(passphrase)) {
                    throw SecurityException("Invalid passphrase for SSH key")
                }
            }
            
            // Validate key type
            if (keyPair.getKeyType() != SUPPORTED_KEY_TYPE) {
                throw SecurityException("Unsupported key type. Only RSA keys are supported.")
            }
            
            // Get key metadata
            val keySize = keyPair.getKeySize()
            val fingerprint = keyPair.getFingerPrint()
            
            // Get decrypted private key bytes
            val privateKeyBytes = ByteArrayOutputStream().use { baos ->
                keyPair.writePrivateKey(baos)
                baos.toByteArray()
            }
            
            // Encrypt the private key using biometric protection
            val encryptedKey = encryptionService.encrypt(
                data = privateKeyBytes,
                keyAlias = "$SSH_KEY_ALIAS_PREFIX$keyAlias",
                requireBiometric = true
            )
            
            // Clean up
            keyPair.dispose()
            
            // Clear sensitive data from memory
            privateKeyBytes.fill(0)
            
            return@withContext ImportedSshKey(
                encryptedPrivateKey = encryptedKey.toBase64(),
                publicKeyFingerprint = fingerprint,
                keyType = "RSA",
                keySize = keySize
            )
        } catch (e: Exception) {
            when (e) {
                is SecurityException -> throw e
                else -> throw SecurityException("Failed to import SSH key: ${e.message}", e)
            }
        }
    }
    
    /**
     * Decrypt SSH private key for use
     * Uses already authenticated session from app launch
     */
    suspend fun decryptSshPrivateKey(
        encryptedPrivateKey: String,
        keyAlias: String
    ): ByteArray {
        // App should already be authenticated at launch
        // This just decrypts using the unlocked key
        return encryptionService.decrypt(
            encryptedData = encryptedPrivateKey.fromBase64(),
            keyAlias = "$SSH_KEY_ALIAS_PREFIX$keyAlias"
        )
    }
    
    /**
     * Delete imported SSH key
     */
    suspend fun deleteSshKey(keyAlias: String) = withContext(Dispatchers.IO) {
        encryptionService.deleteKey("$SSH_KEY_ALIAS_PREFIX$keyAlias")
    }
    
    /**
     * Validate SSH private key format
     */
    fun isValidSshPrivateKey(content: String): Boolean {
        return content.contains("-----BEGIN") && 
               (content.contains("PRIVATE KEY-----") || content.contains("RSA PRIVATE KEY-----"))
    }
    
    /**
     * Extract public key from imported private key
     */
    suspend fun getPublicKey(
        privateKeyContent: String,
        passphrase: String? = null
    ): String = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val keyPair = KeyPair.load(jsch, privateKeyContent.toByteArray(), null)
        
        if (passphrase != null && keyPair.isEncrypted) {
            keyPair.decrypt(passphrase)
        }
        
        val publicKeyBytes = ByteArrayOutputStream().use { baos ->
            keyPair.writePublicKey(baos, "")
            baos.toByteArray()
        }
        
        keyPair.dispose()
        
        return@withContext String(publicKeyBytes).trim()
    }
    
    /**
     * Sign data with SSH private key for WebSocket authentication
     * Uses already authenticated session from app launch
     */
    suspend fun signDataForAuth(
        dataToSign: ByteArray,
        encryptedPrivateKey: String,
        keyAlias: String
    ): ByteArray = withContext(Dispatchers.IO) {
        // Decrypt the private key using authenticated session
        val privateKeyBytes = decryptSshPrivateKey(
            encryptedPrivateKey = encryptedPrivateKey,
            keyAlias = keyAlias
        )
        
        try {
            // Parse the private key
            val jsch = JSch()
            val keyPair = KeyPair.load(jsch, privateKeyBytes, null)
            
            // Create signature
            val signature = when (keyPair.getKeyType()) {
                KeyPair.RSA -> createRsaSignature(keyPair, dataToSign)
                else -> throw SecurityException("Unsupported key type for signing")
            }
            
            // Clean up
            keyPair.dispose()
            
            return@withContext signature
        } finally {
            // Clear sensitive data
            privateKeyBytes.fill(0)
        }
    }
    
    /**
     * Create RSA signature for authentication
     */
    private fun createRsaSignature(keyPair: KeyPair, data: ByteArray): ByteArray {
        // Use JSch's internal signing capability
        val signatureBytes = ByteArrayOutputStream().use { baos ->
            // This is a simplified version - actual implementation would use
            // proper RSA signing with SHA256
            keyPair.getSignature(data)
        }
        return signatureBytes
    }
    
    /**
     * Verify SSH key ownership by signing a challenge
     */
    suspend fun verifySshKeyOwnership(
        challenge: String,
        encryptedPrivateKey: String,
        keyAlias: String
    ): String = withContext(Dispatchers.IO) {
        val signature = signDataForAuth(
            dataToSign = challenge.toByteArray(),
            encryptedPrivateKey = encryptedPrivateKey,
            keyAlias = keyAlias
        )
        return@withContext signature.toBase64()
    }
}

// Extension functions for Base64 encoding/decoding
private fun ByteArray.toBase64(): String = 
    android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

private fun String.fromBase64(): ByteArray = 
    android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
```

### Token Vault

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "token_vault"
)

@Singleton
class TokenVault @Inject constructor(
    private val context: Context,
    private val encryptionService: EncryptionService,
    private val biometricAuthManager: BiometricAuthManager
) {
    
    companion object {
        private const val TOKEN_KEY_PREFIX = "token_"
        private const val TOKEN_VAULT_KEY_ALIAS = "token_vault_key"
    }
    
    data class Token(
        val service: String,
        val tokenValue: String,
        val createdAt: Long = System.currentTimeMillis(),
        val expiresAt: Long? = null
    )
    
    enum class TokenService(val displayName: String) {
        GITHUB("GitHub"),
        GITLAB("GitLab"),
        CUSTOM("Custom Git Server")
    }
    
    /**
     * Store a token securely
     * Requires biometric authentication
     */
    suspend fun storeToken(
        service: TokenService,
        tokenValue: String,
        customServiceName: String? = null
    ) {
        val serviceName = customServiceName ?: service.name
        val encryptedToken = encryptionService.encrypt(
            data = tokenValue.toByteArray(),
            keyAlias = "$TOKEN_KEY_PREFIX$serviceName",
            requireBiometric = true
        )
        
        val key = stringPreferencesKey("$TOKEN_KEY_PREFIX$serviceName")
        context.tokenDataStore.edit { preferences ->
            preferences[key] = encryptedToken.toBase64()
        }
    }
    
    /**
     * Retrieve a token
     * Uses already authenticated session from app launch
     */
    suspend fun getToken(
        service: TokenService,
        customServiceName: String? = null
    ): Token? {
        val serviceName = customServiceName ?: service.name
        val key = stringPreferencesKey("$TOKEN_KEY_PREFIX$serviceName")
        
        val encryptedTokenBase64 = context.tokenDataStore.data
            .map { preferences -> preferences[key] }
            .first()
            
        return encryptedTokenBase64?.let { base64 ->
            try {
                // Use authenticated session from app launch
                val decryptedBytes = encryptionService.decrypt(
                    encryptedData = base64.fromBase64(),
                    keyAlias = "$TOKEN_KEY_PREFIX$serviceName"
                )
                
                Token(
                    service = serviceName,
                    tokenValue = String(decryptedBytes)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Delete a token
     */
    suspend fun deleteToken(
        service: TokenService,
        customServiceName: String? = null
    ) {
        val serviceName = customServiceName ?: service.name
        val key = stringPreferencesKey("$TOKEN_KEY_PREFIX$serviceName")
        
        context.tokenDataStore.edit { preferences ->
            preferences.remove(key)
        }
        
        // Clean up encryption key
        encryptionService.deleteKey("$TOKEN_KEY_PREFIX$serviceName")
    }
    
    /**
     * List all stored token services
     */
    fun getAllTokenServices(): Flow<List<String>> {
        return context.tokenDataStore.data.map { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(TOKEN_KEY_PREFIX) }
                .map { it.name.removePrefix(TOKEN_KEY_PREFIX) }
        }
    }
    
    /**
     * Clear all tokens (for logout)
     */
    suspend fun clearAllTokens() {
        val services = getAllTokenServices().first()
        services.forEach { service ->
            encryptionService.deleteKey("$TOKEN_KEY_PREFIX$service")
        }
        
        context.tokenDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

// Extension functions for Base64 encoding/decoding
private fun ByteArray.toBase64(): String = 
    android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

private fun String.fromBase64(): ByteArray = 
    android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
```

### Encrypted Storage

```kotlin
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedStorageManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val ENCRYPTED_PREFS_NAME = "pocket_agent_encrypted_prefs"
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Store encrypted string
     */
    fun putString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }
    
    /**
     * Retrieve encrypted string
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return encryptedPrefs.getString(key, defaultValue)
    }
    
    /**
     * Store encrypted boolean
     */
    fun putBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }
    
    /**
     * Retrieve encrypted boolean
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return encryptedPrefs.getBoolean(key, defaultValue)
    }
    
    /**
     * Store encrypted long
     */
    fun putLong(key: String, value: Long) {
        encryptedPrefs.edit().putLong(key, value).apply()
    }
    
    /**
     * Retrieve encrypted long
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return encryptedPrefs.getLong(key, defaultValue)
    }
    
    /**
     * Remove a key
     */
    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }
    
    /**
     * Clear all encrypted preferences
     */
    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }
    
    /**
     * Check if key exists
     */
    fun contains(key: String): Boolean {
        return encryptedPrefs.contains(key)
    }
}

/**
 * Extension class for secure session storage
 */
class SecureSessionStorage @Inject constructor(
    private val encryptedStorage: EncryptedStorageManager
) {
    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_EXPIRY = "session_expiry"
        private const val KEY_LAST_AUTH_TIME = "last_auth_time"
    }
    
    fun saveSession(token: String, expiryTimeMillis: Long) {
        encryptedStorage.putString(KEY_SESSION_TOKEN, token)
        encryptedStorage.putLong(KEY_SESSION_EXPIRY, expiryTimeMillis)
        encryptedStorage.putLong(KEY_LAST_AUTH_TIME, System.currentTimeMillis())
    }
    
    fun getSession(): Pair<String, Long>? {
        val token = encryptedStorage.getString(KEY_SESSION_TOKEN) ?: return null
        val expiry = encryptedStorage.getLong(KEY_SESSION_EXPIRY, 0L)
        
        return if (expiry > System.currentTimeMillis()) {
            token to expiry
        } else {
            clearSession()
            null
        }
    }
    
    fun clearSession() {
        encryptedStorage.remove(KEY_SESSION_TOKEN)
        encryptedStorage.remove(KEY_SESSION_EXPIRY)
        encryptedStorage.remove(KEY_LAST_AUTH_TIME)
    }
    
    fun getLastAuthTime(): Long {
        return encryptedStorage.getLong(KEY_LAST_AUTH_TIME, 0L)
    }
}
```

### Security Manager

```kotlin
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val biometricAuthManager: BiometricAuthManager
) {
    
    data class SecurityStatus(
        val isDeviceSecure: Boolean,
        val hasBiometricHardware: Boolean,
        val hasBiometricEnrolled: Boolean,
        val hasStrongBoxSupport: Boolean,
        val isRooted: Boolean
    )
    
    /**
     * Get comprehensive security status
     */
    fun getSecurityStatus(): SecurityStatus {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val biometricStatus = biometricAuthManager.getBiometricStatus()
        
        return SecurityStatus(
            isDeviceSecure = keyguardManager.isDeviceSecure,
            hasBiometricHardware = biometricStatus != BiometricAuthManager.BiometricStatus.NO_HARDWARE,
            hasBiometricEnrolled = biometricStatus == BiometricAuthManager.BiometricStatus.AVAILABLE,
            hasStrongBoxSupport = hasStrongBoxSupport(),
            isRooted = isDeviceRooted()
        )
    }
    
    /**
     * Check if device has hardware security module
     */
    private fun hasStrongBoxSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }
    
    /**
     * Basic root detection
     */
    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        return paths.any { java.io.File(it).exists() }
    }
    
    /**
     * Validate security requirements
     */
    fun validateSecurityRequirements(): SecurityValidationResult {
        val status = getSecurityStatus()
        
        return when {
            !status.isDeviceSecure -> SecurityValidationResult.Error(
                "Device must be secured with PIN, pattern, or password"
            )
            status.isRooted -> SecurityValidationResult.Warning(
                "Device appears to be rooted. Some security features may be compromised"
            )
            else -> SecurityValidationResult.Success
        }
    }
    
    sealed class SecurityValidationResult {
        object Success : SecurityValidationResult()
        data class Warning(val message: String) : SecurityValidationResult()
        data class Error(val message: String) : SecurityValidationResult()
    }
}

/**
 * Encryption service for general purpose encryption
 */
@Singleton
class EncryptionService @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val biometricAuthManager: BiometricAuthManager
) {
    
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
    }
    
    /**
     * Encrypt data
     */
    fun encrypt(
        data: ByteArray,
        keyAlias: String,
        requireBiometric: Boolean = false
    ): ByteArray {
        val secretKey = if (requireBiometric) {
            keystoreManager.createBiometricKey(keyAlias)
        } else {
            keystoreManager.getOrCreateMasterKey()
        }
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        
        // Combine IV and ciphertext
        return iv + ciphertext
    }
    
    /**
     * Decrypt data
     */
    fun decrypt(
        encryptedData: ByteArray,
        keyAlias: String
    ): ByteArray {
        val secretKey = if (keystoreManager.keyExists(keyAlias)) {
            keystoreManager.keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            keystoreManager.getOrCreateMasterKey()
        }
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // Extract IV from the beginning
        val iv = encryptedData.sliceArray(0 until IV_SIZE)
        val ciphertext = encryptedData.sliceArray(IV_SIZE until encryptedData.size)
        
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Unlock the app data encryption key with biometric
     * Called once at app launch
     */
    suspend fun unlockDataKey(
        activity: FragmentActivity
    ): Boolean {
        return try {
            // Create or get the app data key
            val appDataKey = keystoreManager.getOrCreateMasterKey()
            
            // Verify biometric to unlock the keystore
            biometricAuthManager.authenticate(
                activity = activity,
                title = "Unlock Pocket Agent",
                subtitle = "Authenticate to access your data"
            )
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun deleteKey(alias: String) {
        keystoreManager.deleteKey(alias)
    }
}
```

### App Launch Authentication Manager

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchAuthManager @Inject constructor(
    private val encryptedStorageManager: EncryptedStorageManager,
    private val securityAuditLogger: SecurityAuditLogger,
    private val biometricAuthManager: BiometricAuthManager,
    private val secureDataRepository: SecureDataRepository
) {
    
    companion object {
        private const val KEY_AUTHENTICATED_SESSION = "authenticated_session_time"
        private const val KEY_APP_LOCKED = "app_is_locked"
        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000 // 30 minutes
        private const val APP_DATA_KEY_ALIAS = "app_data_key"
    }
    
    private val _authState = MutableStateFlow(AuthState.LOCKED)
    val authState: Flow<AuthState> = _authState
    
    enum class AuthState {
        LOCKED,                // App is locked, needs biometric
        AUTHENTICATED,         // User authenticated, data unlocked
        AUTHENTICATION_FAILED  // Auth failed, retry needed
    }
    
    /**
     * Authenticate user on app launch with biometric
     * This unlocks the encrypted data file for the session
     */
    suspend fun authenticateOnLaunch(
        activity: FragmentActivity
    ): Result<Unit> {
        return try {
            // Check if we have a valid session
            if (hasValidSession()) {
                _authState.value = AuthState.AUTHENTICATED
                return Result.success(Unit)
            }
            
            // Show biometric prompt
            val result = biometricAuthManager.authenticate(
                activity = activity,
                title = "Unlock Pocket Agent",
                subtitle = "Authenticate to access your projects"
            )
            
            // Create app data encryption key if needed
            if (!keystoreManager.keyExists(APP_DATA_KEY_ALIAS)) {
                keystoreManager.createBiometricKey(APP_DATA_KEY_ALIAS)
            }
            
            // Initialize secure data repository
            secureDataRepository.initialize()
            
            // Mark session as authenticated
            encryptedStorageManager.putLong(KEY_AUTHENTICATED_SESSION, System.currentTimeMillis())
            encryptedStorageManager.putBoolean(KEY_APP_LOCKED, false)
            
            _authState.value = AuthState.AUTHENTICATED
            securityAuditLogger.logBiometricAuth(success = true)
            
            Result.success(Unit)
        } catch (e: BiometricAuthException) {
            _authState.value = AuthState.AUTHENTICATION_FAILED
            securityAuditLogger.logBiometricAuth(success = false, error = e.message)
            Result.failure(e)
        }
    }
    
    /**
     * Check if current session is still valid
     */
    private fun hasValidSession(): Boolean {
        val lastAuth = encryptedStorageManager.getLong(KEY_AUTHENTICATED_SESSION, 0L)
        val isLocked = encryptedStorageManager.getBoolean(KEY_APP_LOCKED, true)
        
        return !isLocked && (System.currentTimeMillis() - lastAuth) < SESSION_TIMEOUT_MS
    }
    
    /**
     * Lock the app when going to background
     */
    fun lockApp() {
        _authState.value = AuthState.LOCKED
        encryptedStorageManager.putBoolean(KEY_APP_LOCKED, true)
        
        // Clear cached data
        secureDataRepository.clearCache()
        
        securityAuditLogger.logAppLocked()
    }
    
    /**
     * Check if app needs authentication
     */
    fun needsAuthentication(): Boolean {
        return _authState.value != AuthState.AUTHENTICATED || !hasValidSession()
    }
    
    /**
     * Clear authentication session
     */
    fun clearSession() {
        encryptedStorageManager.remove(KEY_AUTHENTICATED_SESSION)
        encryptedStorageManager.putBoolean(KEY_APP_LOCKED, true)
        _authState.value = AuthState.LOCKED
        secureDataRepository.clearCache()
    }
}

/**
 * Singleton caches for sensitive data that should be cleared on lock
 */
object TokenCache {
    private val cache = mutableMapOf<String, String>()
    
    fun put(key: String, token: String) {
        cache[key] = token
    }
    
    fun get(key: String): String? = cache[key]
    
    fun clear() {
        cache.clear()
    }
}

object DecryptedKeyCache {
    private val cache = mutableMapOf<String, ByteArray>()
    
    fun put(alias: String, key: ByteArray) {
        cache[alias] = key
    }
    
    fun get(alias: String): ByteArray? = cache[alias]
    
    fun clear() {
        cache.values.forEach { it.fill(0) } // Clear byte arrays
        cache.clear()
    }
}
```

### Security Audit Logger

```kotlin
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Audit log model for encrypted JSON storage
@Serializable
data class SecurityAuditLog(
    val id: String = UUID.randomUUID().toString(),
    val eventType: SecurityEventType,
    val eventDetails: String, // JSON string with event-specific data
    val success: Boolean,
    val timestamp: Instant = Instant.now(),
    val errorCode: String? = null,
    val userId: String? = null
)

enum class SecurityEventType {
    TOKEN_ACCESS,
    TOKEN_ADDED,
    TOKEN_DELETED,
    SSH_KEY_IMPORT,
    SSH_KEY_ACCESS,
    SSH_KEY_DELETED,
    BIOMETRIC_AUTH,
    APP_LOCKED,
    APP_UNLOCKED,
    PERMISSION_REQUEST,
    SECURITY_VALIDATION,
    CERTIFICATE_VALIDATION
}

// DAO for audit logs
@Dao
interface SecurityAuditDao {
    @Insert
    suspend fun insertLog(log: SecurityAuditLog)
    
    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SecurityAuditLog>>
    
    @Query("SELECT * FROM security_audit_logs WHERE eventType = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: SecurityEventType): Flow<List<SecurityAuditLog>>
    
    @Query("SELECT COUNT(*) FROM security_audit_logs")
    suspend fun getLogCount(): Int
    
    @Query("DELETE FROM security_audit_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLogs(cutoffTime: Instant)
    
    @Query("DELETE FROM security_audit_logs WHERE id IN (SELECT id FROM security_audit_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestLogs(count: Int)
}

@Singleton
class SecurityAuditLogger @Inject constructor(
    private val auditDao: SecurityAuditDao,
    private val encryptionService: EncryptionService
) {
    
    companion object {
        const val MAX_LOGS = 10000
        const val RETENTION_DAYS = 30
    }
    
    suspend fun logTokenAccess(service: String, success: Boolean, errorCode: String? = null) {
        val details = """{"service":"$service","action":"access"}"""
        insertLog(SecurityEventType.TOKEN_ACCESS, details, success, errorCode)
    }
    
    suspend fun logTokenAdded(service: String) {
        val details = """{"service":"$service","action":"add"}"""
        insertLog(SecurityEventType.TOKEN_ADDED, details, true)
    }
    
    suspend fun logSshKeyImport(keyAlias: String, success: Boolean, errorCode: String? = null) {
        val details = """{"keyAlias":"$keyAlias","action":"import"}"""
        insertLog(SecurityEventType.SSH_KEY_IMPORT, details, success, errorCode)
    }
    
    suspend fun logSshKeyAccess(keyAlias: String, success: Boolean) {
        val details = """{"keyAlias":"$keyAlias","action":"access"}"""
        insertLog(SecurityEventType.SSH_KEY_ACCESS, details, success)
    }
    
    suspend fun logBiometricAuth(success: Boolean, errorCode: Int? = null) {
        val details = """{"errorCode":${errorCode ?: "null"}}"""
        insertLog(SecurityEventType.BIOMETRIC_AUTH, details, success, errorCode?.toString())
    }
    
    suspend fun logAppLocked(reason: String) {
        val details = """{"reason":"$reason"}"""
        insertLog(SecurityEventType.APP_LOCKED, details, true)
    }
    
    suspend fun logAppUnlocked() {
        insertLog(SecurityEventType.APP_UNLOCKED, "{}", true)
    }
    
    suspend fun logPermissionRequest(tool: String, approved: Boolean) {
        val details = """{"tool":"$tool","approved":$approved}"""
        insertLog(SecurityEventType.PERMISSION_REQUEST, details, true)
    }
    
    suspend fun logCertificateValidation(hostname: String, success: Boolean, reason: String? = null) {
        val details = """{"hostname":"$hostname","reason":"${reason ?: ""}"}"""
        insertLog(SecurityEventType.CERTIFICATE_VALIDATION, details, success)
    }
    
    private suspend fun insertLog(
        eventType: SecurityEventType,
        details: String,
        success: Boolean,
        errorCode: String? = null
    ) {
        val log = SecurityAuditLog(
            eventType = eventType,
            eventDetails = details,
            success = success,
            errorCode = errorCode
        )
        
        auditDao.insertLog(log)
        
        // Enforce retention policy
        enforceRetentionPolicy()
    }
    
    private suspend fun enforceRetentionPolicy() {
        // Delete logs older than retention period
        val cutoffTime = Instant.now().minusSeconds(RETENTION_DAYS * 24 * 60 * 60L)
        auditDao.deleteOldLogs(cutoffTime)
        
        // Enforce maximum log count
        val count = auditDao.getLogCount()
        if (count > MAX_LOGS) {
            auditDao.deleteOldestLogs(count - MAX_LOGS)
        }
    }
    
    /**
     * Export audit logs as encrypted JSON
     */
    suspend fun exportLogs(): ByteArray {
        val logs = auditDao.getRecentLogs(limit = Int.MAX_VALUE)
        val json = logs.toString() // Use proper JSON serialization in production
        
        return encryptionService.encrypt(
            data = json.toByteArray(),
            keyAlias = "audit_export_key"
        )
    }
}
```

### Certificate Validator

```kotlin
import okhttp3.CertificatePinner
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLPeerUnverifiedException

@Singleton
class CertificateValidator @Inject constructor(
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    companion object {
        // Certificate pins for known services
        private val PINNED_CERTIFICATES = mapOf(
            "github.com" to listOf(
                "sha256/Jbj1CTvID8T5u3qfAW3HKhCU9+qJbo3Or9LJ7T9jTcE=", // Current GitHub cert
                "sha256/MEepV0sOViH4MR5YuM7zcFyTHbF0F7fcdr9NxLR1qqg="  // Backup pin
            ),
            "gitlab.com" to listOf(
                "sha256/mHvNYHqkJk6b3t7EW5Z7jKk7XvRY5M7L9VoKA2qHVQA=",
                "sha256/7HIpactkIAq2Y49orFOOQKurWxH6XAUfHpYCUi4dQYw="
            ),
            "*.github.com" to listOf(
                "sha256/Jbj1CTvID8T5u3qfAW3HKhCU9+qJbo3Or9LJ7T9jTcE=",
                "sha256/MEepV0sOViH4MR5YuM7zcFyTHbF0F7fccdr9NxLR1qqg="
            ),
            "*.gitlab.com" to listOf(
                "sha256/mHvNYHqkJk6b3t7EW5Z7jKk7XvRY5M7L9VoKA2qHVQA=",
                "sha256/7HIpactkIAq2Y49orFOOQKurWxH6XAUfHpYCUi4dQYw="
            )
        )
        
        // User-approved self-signed certificates
        private val allowedSelfSignedHosts = mutableSetOf<String>()
    }
    
    /**
     * Build OkHttp CertificatePinner with our pins
     */
    fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        
        PINNED_CERTIFICATES.forEach { (hostname, pins) ->
            pins.forEach { pin ->
                builder.add(hostname, pin)
            }
        }
        
        return builder.build()
    }
    
    /**
     * Validate a certificate for a given hostname
     */
    suspend fun validateCertificate(
        hostname: String,
        peerCertificates: List<Certificate>
    ): CertificateValidationResult {
        try {
            // Check if this is a self-signed certificate that user approved
            if (allowedSelfSignedHosts.contains(hostname)) {
                securityAuditLogger.logCertificateValidation(hostname, true, "User-approved self-signed")
                return CertificateValidationResult.Valid
            }
            
            // For pinned hosts, certificate pinner will validate
            if (isPinnedHost(hostname)) {
                // OkHttp will handle the validation
                securityAuditLogger.logCertificateValidation(hostname, true, "Pinned certificate")
                return CertificateValidationResult.Valid
            }
            
            // For unknown hosts, check basic certificate validity
            val cert = peerCertificates.firstOrNull() as? X509Certificate
                ?: return CertificateValidationResult.Invalid("No X509 certificate found")
            
            // Check certificate validity period
            try {
                cert.checkValidity()
            } catch (e: Exception) {
                securityAuditLogger.logCertificateValidation(hostname, false, "Certificate expired or not yet valid")
                return CertificateValidationResult.Invalid("Certificate expired or not yet valid")
            }
            
            // Check hostname matches certificate
            if (!isHostnameValid(hostname, cert)) {
                securityAuditLogger.logCertificateValidation(hostname, false, "Hostname mismatch")
                return CertificateValidationResult.Invalid("Hostname does not match certificate")
            }
            
            securityAuditLogger.logCertificateValidation(hostname, true, "Standard validation passed")
            return CertificateValidationResult.Valid
            
        } catch (e: SSLPeerUnverifiedException) {
            securityAuditLogger.logCertificateValidation(hostname, false, e.message ?: "Peer verification failed")
            return CertificateValidationResult.Invalid(e.message ?: "Certificate validation failed")
        }
    }
    
    /**
     * Allow a self-signed certificate for a specific host
     */
    fun allowSelfSignedCertificate(hostname: String) {
        allowedSelfSignedHosts.add(hostname)
    }
    
    /**
     * Remove approval for a self-signed certificate
     */
    fun revokeSelfSignedApproval(hostname: String) {
        allowedSelfSignedHosts.remove(hostname)
    }
    
    private fun isPinnedHost(hostname: String): Boolean {
        return PINNED_CERTIFICATES.keys.any { pattern ->
            if (pattern.startsWith("*.")) {
                hostname.endsWith(pattern.substring(2))
            } else {
                hostname == pattern
            }
        }
    }
    
    private fun isHostnameValid(hostname: String, cert: X509Certificate): Boolean {
        // Simplified hostname validation - in production use proper hostname verifier
        val cn = cert.subjectDN.name
            .split(",")
            .find { it.trim().startsWith("CN=") }
            ?.substringAfter("CN=")
            ?.trim()
        
        return cn == hostname || (cn?.startsWith("*.") == true && hostname.endsWith(cn.substring(2)))
    }
    
    sealed class CertificateValidationResult {
        object Valid : CertificateValidationResult()
        data class Invalid(val reason: String) : CertificateValidationResult()
        data class RequiresUserApproval(val certificate: X509Certificate) : CertificateValidationResult()
    }
}
```

### WebSocket Authentication

**Purpose**: Provides secure authentication for direct WebSocket connections using SSH key signatures. Implements challenge-response authentication flow that verifies SSH key ownership without transmitting private keys.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketAuthenticationManager @Inject constructor(
    private val sshKeyImportManager: SshKeyImportManager,
    private val securityAuditLogger: SecurityAuditLogger,
    private val encryptedStorageManager: EncryptedStorageManager
) {
    
    companion object {
        private const val CHALLENGE_SIZE = 32 // bytes
        private const val SESSION_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_AUTH_ATTEMPTS = 3
        private const val AUTH_ATTEMPT_WINDOW_MS = 60 * 1000L // 1 minute
    }
    
    private val secureRandom = SecureRandom()
    private val authAttempts = mutableMapOf<String, MutableList<Long>>()
    
    @Serializable
    data class AuthSession(
        val sessionId: String,
        val publicKeyFingerprint: String,
        val createdAt: Long,
        val expiresAt: Long,
        val serverUrl: String
    )
    
    /**
     * Generate authentication challenge for WebSocket connection
     */
    fun generateChallenge(): Pair<String, Long> {
        val nonce = ByteArray(CHALLENGE_SIZE)
        secureRandom.nextBytes(nonce)
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)
        val timestamp = System.currentTimeMillis()
        return Pair(nonceBase64, timestamp)
    }
    
    /**
     * Sign authentication challenge for WebSocket
     */
    suspend fun signAuthChallenge(
        activity: FragmentActivity,
        nonce: String,
        timestamp: Long,
        sshIdentity: SshIdentityEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check rate limiting
            if (!checkRateLimit(sshIdentity.publicKeyFingerprint)) {
                return@withContext Result.failure(
                    SecurityException("Too many authentication attempts")
                )
            }
            
            // Create data to sign: nonce + timestamp
            val dataToSign = "$nonce$timestamp".toByteArray()
            
            // Sign with SSH key (requires biometric auth)
            val signature = sshKeyImportManager.signDataForAuth(
                activity = activity,
                dataToSign = dataToSign,
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            // Record attempt
            recordAuthAttempt(sshIdentity.publicKeyFingerprint)
            
            Result.success(Base64.getEncoder().encodeToString(signature))
        } catch (e: Exception) {
            securityAuditLogger.logAuthenticationAttempt(
                success = false,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Create and store authenticated session
     */
    suspend fun createAuthSession(
        sessionId: String,
        publicKeyFingerprint: String,
        serverUrl: String
    ): AuthSession {
        val now = System.currentTimeMillis()
        val session = AuthSession(
            sessionId = sessionId,
            publicKeyFingerprint = publicKeyFingerprint,
            createdAt = now,
            expiresAt = now + SESSION_DURATION_MS,
            serverUrl = serverUrl
        )
        
        // Store session securely
        val sessionJson = Json.encodeToString(AuthSession.serializer(), session)
        encryptedStorageManager.putString("ws_session_$sessionId", sessionJson)
        
        // Log successful authentication
        securityAuditLogger.logWebSocketAuth(
            serverUrl = serverUrl,
            success = true
        )
        
        return session
    }
    
    /**
     * Retrieve existing session
     */
    suspend fun getSession(sessionId: String): AuthSession? {
        val sessionJson = encryptedStorageManager.getString("ws_session_$sessionId") ?: return null
        val session = Json.decodeFromString(AuthSession.serializer(), sessionJson)
        
        // Check if session is still valid
        if (System.currentTimeMillis() > session.expiresAt) {
            deleteSession(sessionId)
            return null
        }
        
        return session
    }
    
    /**
     * Sign data for session resumption
     */
    suspend fun signSessionResumption(
        activity: FragmentActivity,
        sessionId: String,
        nonce: String,
        sshIdentity: SshIdentityEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Verify session exists and is valid
            val session = getSession(sessionId)
                ?: return@withContext Result.failure(SecurityException("Invalid session"))
            
            // Verify the SSH key matches the session
            if (session.publicKeyFingerprint != sshIdentity.publicKeyFingerprint) {
                return@withContext Result.failure(SecurityException("Key mismatch for session"))
            }
            
            // Sign session ID + nonce
            val dataToSign = "$sessionId$nonce".toByteArray()
            val signature = sshKeyImportManager.signDataForAuth(
                activity = activity,
                dataToSign = dataToSign,
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            Result.success(Base64.getEncoder().encodeToString(signature))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete session
     */
    suspend fun deleteSession(sessionId: String) {
        encryptedStorageManager.remove("ws_session_$sessionId")
    }
    
    /**
     * Delete all sessions for a server
     */
    suspend fun deleteServerSessions(serverUrl: String) {
        // In production, implement proper session management with server URL indexing
        encryptedStorageManager.getAllKeys()
            .filter { it.startsWith("ws_session_") }
            .forEach { key ->
                val sessionJson = encryptedStorageManager.getString(key)
                if (sessionJson != null) {
                    try {
                        val session = Json.decodeFromString(AuthSession.serializer(), sessionJson)
                        if (session.serverUrl == serverUrl) {
                            encryptedStorageManager.remove(key)
                        }
                    } catch (e: Exception) {
                        // Invalid session data, remove it
                        encryptedStorageManager.remove(key)
                    }
                }
            }
    }
    
    /**
     * Check rate limiting for authentication attempts
     */
    private fun checkRateLimit(publicKeyFingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        val attempts = authAttempts.getOrPut(publicKeyFingerprint) { mutableListOf() }
        
        // Remove old attempts
        attempts.removeAll { it < now - AUTH_ATTEMPT_WINDOW_MS }
        
        return attempts.size < MAX_AUTH_ATTEMPTS
    }
    
    /**
     * Record authentication attempt
     */
    private fun recordAuthAttempt(publicKeyFingerprint: String) {
        val attempts = authAttempts.getOrPut(publicKeyFingerprint) { mutableListOf() }
        attempts.add(System.currentTimeMillis())
    }
    
    /**
     * Clear authentication rate limit for a key
     */
    fun clearRateLimit(publicKeyFingerprint: String) {
        authAttempts.remove(publicKeyFingerprint)
    }
}
```

### UI Components

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * App Launch Authentication Screen
 * Shows on app startup to unlock data with biometric
 */
@Composable
fun AppLaunchAuthScreen(
    authManager: AppLaunchAuthManager,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authState by authManager.authState.collectAsState(AuthState.LOCKED)
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    LaunchedEffect(Unit) {
        // Automatically trigger biometric on launch
        activity?.let {
            authManager.authenticateOnLaunch(it).onSuccess {
                onAuthenticated()
            }
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometric",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colors.primary
                )
                
                Text(
                    text = "Welcome to Pocket Agent",
                    style = MaterialTheme.typography.h5
                )
                
                Text(
                    text = when (authState) {
                        AuthState.LOCKED -> "Authenticate to access your projects"
                        AuthState.AUTHENTICATION_FAILED -> "Authentication failed. Please try again."
                        AuthState.AUTHENTICATED -> "Successfully authenticated!"
                    },
                    style = MaterialTheme.typography.body1,
                    color = when (authState) {
                        AuthState.AUTHENTICATION_FAILED -> MaterialTheme.colors.error
                        else -> MaterialTheme.colors.onSurface
                    }
                )
                
                if (authState != AuthState.AUTHENTICATED) {
                    Button(
                        onClick = {
                            activity?.let {
                                lifecycleScope.launch {
                                    authManager.authenticateOnLaunch(it).onSuccess {
                                        onAuthenticated()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticate with Biometric")
                    }
                    
                    // Fallback to device credentials
                    TextButton(
                        onClick = {
                            // Use device credentials as fallback
                            activity?.let {
                                lifecycleScope.launch {
                                    authManager.authenticateWithDeviceCredentials(it).onSuccess {
                                        onAuthenticated()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Use PIN/Pattern Instead")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding new tokens
 */
@Composable
fun TokenEntryDialog(
    onDismiss: () -> Unit,
    onTokenAdded: (TokenVault.TokenService, String, Long?) -> Unit
) {
    var selectedService by remember { mutableStateOf(TokenVault.TokenService.GITHUB) }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var hasExpiration by remember { mutableStateOf(false) }
    var expirationDate by remember { mutableStateOf<LocalDate?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Access Token") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Service selection
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = { }
                ) {
                    OutlinedTextField(
                        value = selectedService.displayName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Service") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }
                    )
                }
                
                // Token input
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token") },
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle token visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Expiration option
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasExpiration,
                        onCheckedChange = { hasExpiration = it }
                    )
                    Text("Token has expiration date")
                }
                
                if (hasExpiration) {
                    // Date picker placeholder
                    OutlinedTextField(
                        value = expirationDate?.toString() ?: "",
                        onValueChange = { },
                        label = { Text("Expiration Date") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.DateRange, "Select date")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val expirationMillis = expirationDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
                    onTokenAdded(selectedService, token, expirationMillis)
                },
                enabled = token.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Lock screen overlay
 */
@Composable
fun LockScreen(
    onUnlockRequested: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colors.primary
            )
            
            Text(
                text = "Pocket Agent Locked",
                style = MaterialTheme.typography.h5
            )
            
            Text(
                text = "Your session has been locked for security",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onUnlockRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock with Biometric")
            }
            
            TextButton(
                onClick = onUnlockRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use Device PIN")
            }
        }
    }
}

/**
 * SSH Key Import Dialog
 */
@Composable
fun SshKeyImportDialog(
    onDismiss: () -> Unit,
    onKeyImported: (String, String, String?) -> Unit // content, alias, passphrase
) {
    var keyContent by remember { mutableStateOf("") }
    var keyAlias by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var hasPassphrase by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import SSH Key") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key alias
                OutlinedTextField(
                    value = keyAlias,
                    onValueChange = { keyAlias = it },
                    label = { Text("Key Name") },
                    placeholder = { Text("e.g., Work SSH Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Key content
                OutlinedTextField(
                    value = keyContent,
                    onValueChange = { keyContent = it },
                    label = { Text("Private Key") },
                    placeholder = { Text("Paste your private key here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
                
                // Passphrase option
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasPassphrase,
                        onCheckedChange = { hasPassphrase = it }
                    )
                    Text("Key has passphrase")
                }
                
                if (hasPassphrase) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = if (showPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle passphrase visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onKeyImported(
                        keyContent,
                        keyAlias,
                        if (hasPassphrase) passphrase else null
                    )
                },
                enabled = keyContent.isNotBlank() && keyAlias.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### Error Handling

```kotlin
sealed class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class KeyGenerationException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
    class EncryptionException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
    class DecryptionException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
    class KeyNotFoundException(alias: String) : SecurityException("Key not found: $alias")
    class BiometricNotAvailableException(message: String) : SecurityException(message)
    class DeviceNotSecureException : SecurityException("Device must be secured with PIN/Pattern/Password")
}

sealed class BiometricAuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationError(val errorCode: Int, message: String) : BiometricAuthException("Error $errorCode: $message")
    class AuthenticationFailed : BiometricAuthException("Authentication failed")
    class AuthenticationCancelled : BiometricAuthException("Authentication cancelled by user")
}

/**
 * Result wrapper for security operations
 */
sealed class SecurityResult<out T> {
    data class Success<T>(val data: T) : SecurityResult<T>()
    data class Error(val exception: SecurityException) : SecurityResult<Nothing>()
    
    inline fun onSuccess(action: (T) -> Unit): SecurityResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (SecurityException) -> Unit): SecurityResult<T> {
        if (this is Error) action(exception)
        return this
    }
}
```

### Dependency Injection

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager {
        return KeystoreManager()
    }
    
    @Provides
    @Singleton
    fun provideBiometricAuthManager(
        @ApplicationContext context: Context
    ): BiometricAuthManager {
        return BiometricAuthManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSshKeyImportManager(
        encryptionService: EncryptionService,
        biometricAuthManager: BiometricAuthManager
    ): SshKeyImportManager {
        return SshKeyImportManager(encryptionService, biometricAuthManager)
    }
    
    @Provides
    @Singleton
    fun provideTokenVault(
        @ApplicationContext context: Context,
        encryptionService: EncryptionService,
        biometricAuthManager: BiometricAuthManager
    ): TokenVault {
        return TokenVault(context, encryptionService, biometricAuthManager)
    }
    
    @Provides
    @Singleton
    fun provideEncryptedStorageManager(
        @ApplicationContext context: Context
    ): EncryptedStorageManager {
        return EncryptedStorageManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSecureSessionStorage(
        encryptedStorageManager: EncryptedStorageManager
    ): SecureSessionStorage {
        return SecureSessionStorage(encryptedStorageManager)
    }
    
    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager,
        biometricAuthManager: BiometricAuthManager
    ): SecurityManager {
        return SecurityManager(context, keystoreManager, biometricAuthManager)
    }
    
    @Provides
    @Singleton
    fun provideEncryptionService(
        keystoreManager: KeystoreManager,
        biometricAuthManager: BiometricAuthManager
    ): EncryptionService {
        return EncryptionService(keystoreManager, biometricAuthManager)
    }
}
```

## Testing

### Security Testing Checklist

```kotlin
/**
 * Security Testing Checklist:
 * 1. [ ] Test key generation with and without StrongBox
 * 2. [ ] Test biometric authentication flow
 * 3. [ ] Test fallback to device credentials
 * 4. [ ] Test encryption/decryption round trip
 * 5. [ ] Test SSH key import with and without passphrase
 * 6. [ ] Test SSH key encryption and secure storage
 * 7. [ ] Test SSH key decryption with biometric auth
 * 8. [ ] Test token storage and retrieval with biometric
 * 9. [ ] Test security validation on rooted devices
 * 10. [ ] Test key deletion and cleanup
 * 11. [ ] Test session timeout and invalidation
 * 12. [ ] Test migration from unencrypted to encrypted storage
 * 13. [ ] Test biometric enrollment changes
 * 14. [ ] Test device lock/unlock scenarios
 * 15. [ ] Test app uninstall/reinstall data persistence
 * 16. [ ] Test concurrent access to encrypted resources
 * 17. [ ] Test memory cleanup for sensitive data
 */
```

### Unit Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SshKeyImportManagerTest {
    
    @Mock
    private lateinit var encryptionService: EncryptionService
    
    @Mock
    private lateinit var biometricAuthManager: BiometricAuthManager
    
    private lateinit var sshKeyImportManager: SshKeyImportManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sshKeyImportManager = SshKeyImportManager(encryptionService, biometricAuthManager)
    }
    
    @Test
    fun `importSshPrivateKey encrypts and returns key data`() = runTest {
        // Given
        val privateKeyContent = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA...
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        
        val keyAlias = "test_key"
        val mockEncryptedData = "encrypted".toByteArray()
        
        whenever(encryptionService.encrypt(any(), anyString(), eq(true)))
            .thenReturn(mockEncryptedData)
        
        // When
        val result = sshKeyImportManager.importSshPrivateKey(
            privateKeyContent = privateKeyContent,
            passphrase = null,
            keyAlias = keyAlias
        )
        
        // Then
        assertThat(result.encryptedPrivateKey).isNotEmpty()
        assertThat(result.publicKeyFingerprint).isNotEmpty()
        assertThat(result.keyType).isEqualTo("RSA")
        verify(encryptionService).encrypt(any(), eq("ssh_imported_$keyAlias"), eq(true))
    }
    
    @Test
    fun `isValidSshPrivateKey validates key format`() {
        // Valid keys
        assertThat(sshKeyImportManager.isValidSshPrivateKey("-----BEGIN RSA PRIVATE KEY-----")).isTrue()
        assertThat(sshKeyImportManager.isValidSshPrivateKey("-----BEGIN PRIVATE KEY-----")).isTrue()
        
        // Invalid keys
        assertThat(sshKeyImportManager.isValidSshPrivateKey("invalid key")).isFalse()
        assertThat(sshKeyImportManager.isValidSshPrivateKey("")).isFalse()
    }
}

@RunWith(AndroidJUnit4::class)
class TokenVaultTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var context: Context
    private lateinit var tokenVault: TokenVault
    
    @Mock
    private lateinit var encryptionService: EncryptionService
    
    @Mock
    private lateinit var biometricAuthManager: BiometricAuthManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        MockitoAnnotations.openMocks(this)
        tokenVault = TokenVault(context, encryptionService, biometricAuthManager)
    }
    
    @Test
    fun `storeToken encrypts and stores token`() = runTest {
        // Given
        val service = TokenVault.TokenService.GITHUB
        val token = "ghp_testtoken123"
        val encryptedToken = "encrypted".toByteArray()
        
        whenever(encryptionService.encrypt(any(), anyString(), eq(true)))
            .thenReturn(encryptedToken)
        
        // When
        tokenVault.storeToken(service, token)
        
        // Then
        verify(encryptionService).encrypt(
            data = token.toByteArray(),
            keyAlias = "token_GITHUB",
            requireBiometric = true
        )
    }
}
```

### Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SecurityIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var securityManager: SecurityManager
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var encryptionService: EncryptionService
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        keystoreManager = KeystoreManager()
        val biometricAuthManager = BiometricAuthManager(context)
        encryptionService = EncryptionService(keystoreManager, biometricAuthManager)
        securityManager = SecurityManager(context, keystoreManager, biometricAuthManager)
    }
    
    @After
    fun cleanup() {
        // Clean up test keys
        keystoreManager.deleteKey("test_key")
    }
    
    @Test
    fun `full encryption decryption cycle works`() {
        // Given
        val testData = "sensitive data".toByteArray()
        val keyAlias = "test_key"
        
        // When - Encrypt
        val encrypted = encryptionService.encrypt(
            data = testData,
            keyAlias = keyAlias,
            requireBiometric = false
        )
        
        // Then - Encrypted data is different
        assertThat(encrypted).isNotEqualTo(testData)
        
        // When - Decrypt
        val decrypted = encryptionService.decrypt(
            encryptedData = encrypted,
            keyAlias = keyAlias
        )
        
        // Then - Decrypted data matches original
        assertThat(decrypted).isEqualTo(testData)
    }
    
    @Test
    fun `security status reports correct device state`() {
        // When
        val status = securityManager.getSecurityStatus()
        
        // Then
        assertThat(status.isDeviceSecure).isNotNull()
        assertThat(status.hasBiometricHardware).isNotNull()
        assertThat(status.hasStrongBoxSupport).isNotNull()
        assertThat(status.isRooted).isFalse() // Assuming test device is not rooted
    }
}
```

## Implementation Notes (Android Mobile)

### Security Best Practices

#### Key Management
- Always use hardware-backed keys when available (StrongBox on Pixel 3+)
- Set appropriate key validity durations for biometric-protected keys
- Invalidate keys when biometric enrollment changes
- Use separate keys for different security contexts

#### Biometric Authentication
- Always provide fallback to device credentials
- Handle biometric hardware variations across devices
- Clear error messages for authentication failures
- Implement rate limiting for failed attempts

#### Data Protection
- Never log sensitive data
- Clear sensitive data from memory after use
- Use secure random for all cryptographic operations
- Implement proper session management with timeouts

### Performance Considerations (Android-Specific)

- **Cryptographic Operations**: Perform on background threads to avoid UI blocking
- **Key Generation**: Cache keys appropriately to avoid regeneration
- **Biometric Prompt**: Show loading state while preparing authentication
- **Memory Management**: Clear byte arrays containing sensitive data
- **Battery Impact**: Minimize cryptographic operations during low battery

```kotlin
// Example: Performance-optimized key caching
class OptimizedKeystoreManager : KeystoreManager() {
    private val keyCache = LruCache<String, SecretKey>(10)
    
    override fun getOrCreateMasterKey(): SecretKey {
        return keyCache.get(MASTER_KEY_ALIAS) 
            ?: super.getOrCreateMasterKey().also { key ->
                keyCache.put(MASTER_KEY_ALIAS, key)
            }
    }
}
```

### Migration Strategy

```kotlin
/**
 * Migration from unencrypted to encrypted storage
 */
class SecurityMigrationManager @Inject constructor(
    private val context: Context,
    private val encryptedStorage: EncryptedStorageManager
) {
    
    fun migrateToEncryptedStorage() {
        val oldPrefs = context.getSharedPreferences("old_prefs", Context.MODE_PRIVATE)
        
        // Migrate each value
        oldPrefs.all.forEach { (key, value) ->
            when (value) {
                is String -> encryptedStorage.putString(key, value)
                is Boolean -> encryptedStorage.putBoolean(key, value)
                is Long -> encryptedStorage.putLong(key, value)
                is Int -> encryptedStorage.putLong(key, value.toLong())
            }
        }
        
        // Clear old preferences
        oldPrefs.edit().clear().apply()
        
        // Delete old preference file
        val oldPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/old_prefs.xml")
        oldPrefsFile.delete()
    }
}
```

### Package Structure

```
security/
├── keystore/
│   ├── KeystoreManager.kt
│   └── KeystoreConfig.kt
├── ssh/
│   ├── SshKeyImportManager.kt
│   ├── ImportedSshKey.kt
│   └── SshKeyValidator.kt
├── biometric/
│   ├── BiometricAuthManager.kt
│   ├── BiometricPromptUtils.kt
│   └── BiometricStatus.kt
├── vault/
│   ├── TokenVault.kt
│   ├── TokenService.kt
│   └── TokenEncryption.kt
├── storage/
│   ├── EncryptedStorageManager.kt
│   ├── SecureSessionStorage.kt
│   └── StorageMigration.kt
├── encryption/
│   ├── EncryptionService.kt
│   ├── CipherProvider.kt
│   └── Base64Extensions.kt
├── validation/
│   ├── SecurityManager.kt
│   ├── SecurityValidator.kt
│   └── RootDetection.kt
├── exception/
│   ├── SecurityException.kt
│   └── BiometricException.kt
└── di/
    └── SecurityModule.kt
```

### Future Extensions (Android Mobile Focus)

- **Certificate Pinning**: Add certificate pinning for server connections
- **Secure Backup**: Implement Android Auto Backup with encryption
- **Key Rotation**: Automatic key rotation policies
- **Hardware Attestation**: Verify key generation in secure hardware
- **Multi-User Support**: Separate key storage for work profiles
- **FIDO2 Support**: Add WebAuthn/FIDO2 for web-based auth
- **Secure Communication**: Implement end-to-end encryption for messages
- **Audit Logging**: Secure audit trail for all security operations
- **Tamper Detection**: Advanced root and tamper detection
- **Compliance**: Add FIPS 140-2 compliance mode for enterprise