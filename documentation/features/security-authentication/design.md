# Security Authentication - Design

## Architecture Overview

The Security Authentication feature implements a comprehensive security architecture that leverages Android's hardware security capabilities while providing a seamless authentication experience. The design follows defense-in-depth principles with multiple security layers protecting user credentials and sessions.

### Core Design Principles

1. **Hardware-First Security**: Utilize hardware security modules whenever available
2. **Zero-Knowledge Architecture**: Keys never exist in plaintext in application memory
3. **Fail-Secure Defaults**: Deny by default, require explicit authentication
4. **Audit Everything**: Comprehensive logging without exposing sensitive data
5. **User Control**: Clear visibility and control over all security operations

### System Architecture

```mermaid
graph TB
    subgraph "User Interface Layer"
        BP[BiometricPrompt]
        SAF[SecurityAuthFragment]
        KMF[KeyManagementFragment]
        ALF[AuditLogFragment]
    end
    
    subgraph "Authentication Layer"
        BAM[BiometricAuthManager]
        SAM[SessionAuthManager]
        PV[PermissionVerifier]
        PM[PolicyManager]
    end
    
    subgraph "Cryptographic Layer"
        KS[Android Keystore]
        CM[CryptoManager]
        KM[KeyManager]
        TV[TokenVault]
    end
    
    subgraph "Storage Layer"
        EDB[(Encrypted Database)]
        ESP[EncryptedSharedPrefs]
        ADB[(Audit Database)]
    end
    
    subgraph "Network Layer"
        WSA[WebSocketAuthenticator]
        CH[CertificateHandler]
        ST[SecureTransport]
    end
    
    BP --> BAM
    SAF --> SAM
    KMF --> KM
    ALF --> ADB
    
    BAM --> KS
    SAM --> TV
    PV --> CM
    PM --> ESP
    
    KM --> KS
    TV --> EDB
    CM --> KS
    
    WSA --> ST
    CH --> ST
```

## Component Specifications

### 1. BiometricAuthManager

**Purpose**: Manages all biometric authentication operations with hardware security integration

**Purpose**: Singleton class that handles biometric authentication with hardware-backed security, providing cryptographic objects for secure operations

```kotlin
package com.pocketagent.security.biometric

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    UNAVAILABLE,
    NOT_ENROLLED,
    UNKNOWN
}

sealed class SecurityOperation(
    val title: String,
    val subtitle: String,
    val description: String,
    val requiresConfirmation: Boolean
) {
    data class Encryption(val operationId: String) : SecurityOperation(
        "Encrypt Data",
        "Biometric authentication required",
        "Authenticate to encrypt sensitive data",
        false
    )
    
    data class Decryption(val operationId: String, val iv: ByteArray) : SecurityOperation(
        "Decrypt Data",
        "Biometric authentication required",
        "Authenticate to decrypt sensitive data",
        false
    )
    
    data class Signing(val operationId: String) : SecurityOperation(
        "Sign Operation",
        "Biometric authentication required",
        "Authenticate to sign this operation",
        true
    )
}

sealed class AuthenticationResult {
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : AuthenticationResult()
    data class Error(val errorCode: Int, val errorMessage: String) : AuthenticationResult()
}

@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val keyManager: KeyManager
) {
    companion object {
        private const val BIOMETRIC_KEY_ALIAS = "pocket_agent_biometric_key"
        private const val AUTH_VALIDITY_DURATION = -1 // Require auth for every use
    }
    
    private val biometricManager = BiometricManager.from(context)
    
    fun canAuthenticate(): BiometricStatus {
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNKNOWN
        }
    }
    
    suspend fun authenticateForOperation(
        activity: FragmentActivity,
        operation: SecurityOperation
    ): AuthenticationResult {
        val cipher = when (operation) {
            is SecurityOperation.Encryption -> prepareEncryptionCipher()
            is SecurityOperation.Decryption -> prepareDecryptionCipher(operation.iv)
            is SecurityOperation.Signing -> prepareSigningCipher()
        }
        
        return suspendCancellableCoroutine { continuation ->
            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        val cryptoObject = result.cryptoObject
                        continuation.resume(
                            AuthenticationResult.Success(cryptoObject)
                        )
                    }
                    
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        continuation.resume(
                            AuthenticationResult.Error(errorCode, errString.toString())
                        )
                    }
                    
                    override fun onAuthenticationFailed() {
                        // Individual attempt failed, biometric prompt handles retry
                    }
                }
            )
            
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(operation.title)
                .setSubtitle(operation.subtitle)
                .setDescription(operation.description)
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(operation.requiresConfirmation)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
            
            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
    }
    
    private fun prepareEncryptionCipher(): Cipher {
        val key = keyManager.getOrCreateBiometricKey(BIOMETRIC_KEY_ALIAS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }
}
```

### 2. KeyManager

**Purpose**: Manages cryptographic keys with hardware security module integration

**Purpose**: Manages the creation and retrieval of cryptographic keys stored in Android Keystore with hardware security module support

```kotlin
package com.pocketagent.security.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class KeyManager @Inject constructor(
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) },
    private val keyGenerator: KeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
) {
    
    fun getOrCreateBiometricKey(alias: String): SecretKey {
        return if (keyStore.containsAlias(alias)) {
            keyStore.getKey(alias, null) as SecretKey
        } else {
            generateBiometricKey(alias)
        }
    }
    
    private fun generateBiometricKey(alias: String): SecretKey {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setIsStrongBoxBacked(isStrongBoxAvailable()) // Use StrongBox if available
            .build()
            
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    private fun isStrongBoxAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Note: context would need to be injected for this to work
            // context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            false // Simplified for compilation
        } else {
            false
        }
    }
}
```

### 3. SshKeyManager

**Purpose**: Handles SSH key import, storage, and operations

**Purpose**: Manages SSH key lifecycle including import, secure storage, signing operations, and key usage tracking

```kotlin
package com.pocketagent.security.ssh

import android.util.Base64
import androidx.biometric.BiometricPrompt
import com.pocketagent.data.dao.SshKeyDao
import com.pocketagent.data.entity.SshKeyEntity
import com.pocketagent.security.audit.AuditLogger
import com.pocketagent.security.crypto.CryptoManager
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPrivateKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedKey(
    val privateKeyBytes: ByteArray,
    val publicKey: String,
    val algorithm: String
)

data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray
)

sealed class ImportResult {
    data class Success(val sshKey: SshKeyEntity) : ImportResult()
    data class Failure(val message: String) : ImportResult()
}

class UnsupportedKeyFormatException(message: String) : Exception(message)
class KeyNotFoundException(keyId: String) : Exception("Key not found: $keyId")
class UnsupportedAlgorithmException(algorithm: String) : Exception("Unsupported algorithm: $algorithm")

@Singleton
class SshKeyManager @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val sshKeyDao: SshKeyDao,
    private val auditLogger: AuditLogger
) {
    
    suspend fun importSshKey(
        keyData: String,
        passphrase: String? = null,
        keyName: String
    ): ImportResult {
        return try {
            // Parse key
            val parsedKey = when {
                keyData.startsWith("-----BEGIN RSA PRIVATE KEY-----") -> 
                    parseRsaPrivateKey(keyData, passphrase)
                keyData.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----") -> 
                    parseOpenSshPrivateKey(keyData, passphrase)
                keyData.startsWith("ssh-rsa") || keyData.startsWith("ssh-ed25519") ->
                    parsePublicKey(keyData)
                else -> throw UnsupportedKeyFormatException("Unknown key format")
            }
            
            // Encrypt private key
            val encryptedKey = cryptoManager.encryptSshKey(parsedKey.privateKeyBytes)
            
            // Store in database
            val sshKey = SshKeyEntity(
                id = UUID.randomUUID().toString(),
                name = keyName,
                publicKey = parsedKey.publicKey,
                encryptedPrivateKey = encryptedKey.ciphertext,
                privateKeyIv = encryptedKey.iv,
                algorithm = parsedKey.algorithm,
                fingerprint = calculateFingerprint(parsedKey.publicKey),
                createdAt = System.currentTimeMillis(),
                lastUsed = null
            )
            
            sshKeyDao.insert(sshKey)
            
            // Audit log
            auditLogger.logKeyImport(
                keyId = sshKey.id,
                keyName = keyName,
                algorithm = parsedKey.algorithm,
                fingerprint = sshKey.fingerprint
            )
            
            ImportResult.Success(sshKey)
            
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Import failed")
        }
    }
    
    suspend fun signWithKey(
        keyId: String,
        data: ByteArray,
        authResult: BiometricPrompt.CryptoObject
    ): ByteArray {
        val sshKey = sshKeyDao.getById(keyId) ?: throw KeyNotFoundException(keyId)
        
        // Decrypt private key using authenticated cipher
        val cipher = authResult.cipher ?: throw IllegalStateException("No cipher in auth result")
        val privateKeyBytes = cipher.doFinal(sshKey.encryptedPrivateKey)
        
        // Perform signing
        val signature = when (sshKey.algorithm) {
            "RSA" -> signWithRsa(privateKeyBytes, data)
            "Ed25519" -> signWithEd25519(privateKeyBytes, data)
            else -> throw UnsupportedAlgorithmException(sshKey.algorithm)
        }
        
        // Update last used
        sshKeyDao.updateLastUsed(keyId, System.currentTimeMillis())
        
        // Audit log
        auditLogger.logKeyUsage(
            keyId = keyId,
            operation = "SIGN",
            dataSize = data.size
        )
        
        // Clear sensitive data
        privateKeyBytes.fill(0)
        
        return signature
    }
    
    private fun parseRsaPrivateKey(keyData: String, passphrase: String?): ParsedKey {
        // Implementation would use Bouncy Castle or similar library
        // This is a placeholder for the actual implementation
        TODO("Implement RSA private key parsing")
    }
    
    private fun parseOpenSshPrivateKey(keyData: String, passphrase: String?): ParsedKey {
        // Implementation would use sshj or similar library
        // This is a placeholder for the actual implementation
        TODO("Implement OpenSSH private key parsing")
    }
    
    private fun parsePublicKey(keyData: String): ParsedKey {
        // Implementation would parse public key format
        // This is a placeholder for the actual implementation
        TODO("Implement public key parsing")
    }
    
    private fun calculateFingerprint(publicKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private fun signWithRsa(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        // Implementation would use RSA signing
        // This is a placeholder for the actual implementation
        TODO("Implement RSA signing")
    }
    
    private fun signWithEd25519(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        // Implementation would use Ed25519 signing
        // This is a placeholder for the actual implementation
        TODO("Implement Ed25519 signing")
    }
}
```

### 4. SessionAuthManager

**Purpose**: Manages authenticated sessions and token lifecycle

**Purpose**: Handles WebSocket session establishment, authentication, token management, and session heartbeat monitoring

```kotlin
package com.pocketagent.security.session

import android.util.Base64
import androidx.biometric.BiometricPrompt
import com.pocketagent.data.dao.SessionDao
import com.pocketagent.data.entity.SshKeyEntity
import com.pocketagent.network.WebSocketManager
import com.pocketagent.security.crypto.CryptoManager
import com.pocketagent.security.ssh.SshKeyManager
import com.pocketagent.security.token.TokenVault
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job

data class AuthenticatedSession(
    val sessionId: String,
    val projectId: String,
    val tokenId: String,
    val connection: WebSocketConnection,
    val establishedAt: Long,
    var lastActivity: Long,
    var heartbeatJob: Job? = null
) {
    fun toEntity(): SessionEntity {
        return SessionEntity(
            sessionId = sessionId,
            projectId = projectId,
            tokenId = tokenId,
            establishedAt = establishedAt,
            lastActivity = lastActivity,
            isActive = true
        )
    }
}

data class AuthResponse(
    val projectId: String,
    val publicKey: String,
    val signature: String,
    val timestamp: Long,
    val nonce: String
)

data class SessionToken(
    val token: String,
    val expiresAt: Long
)

sealed class SessionResult {
    data class Success(val session: AuthenticatedSession) : SessionResult()
    data class Failure(val message: String) : SessionResult()
}

// Placeholder interfaces for WebSocket components
interface WebSocketConnection {
    suspend fun receiveChallenge(): Challenge
    suspend fun sendAuthResponse(response: AuthResponse)
    suspend fun receiveSessionToken(): SessionToken
    suspend fun sendPing()
}

data class Challenge(
    val data: ByteArray,
    val nonce: String
)

enum class TokenType {
    SESSION,
    API_KEY,
    REFRESH,
    TEMPORARY
}

// Placeholder entity classes
data class SessionEntity(
    val sessionId: String,
    val projectId: String,
    val tokenId: String,
    val establishedAt: Long,
    val lastActivity: Long,
    val isActive: Boolean
)

@Singleton
class SessionAuthManager @Inject constructor(
    private val tokenVault: TokenVault,
    private val webSocketManager: WebSocketManager,
    private val cryptoManager: CryptoManager,
    private val sessionDao: SessionDao,
    private val sshKeyManager: SshKeyManager
) {
    private val activeSessions = ConcurrentHashMap<String, AuthenticatedSession>()
    
    suspend fun establishSession(
        projectId: String,
        serverUrl: String,
        sshKey: SshKeyEntity,
        authResult: BiometricPrompt.CryptoObject
    ): SessionResult {
        return try {
            // Connect WebSocket
            val connection = webSocketManager.connect(serverUrl)
            
            // Receive authentication challenge
            val challenge = connection.receiveChallenge()
            
            // Sign challenge with SSH key
            val signature = sshKeyManager.signWithKey(
                keyId = sshKey.id,
                data = challenge.data,
                authResult = authResult
            )
            
            // Send authentication response
            val authResponse = AuthResponse(
                projectId = projectId,
                publicKey = sshKey.publicKey,
                signature = Base64.encodeToString(signature, Base64.NO_WRAP),
                timestamp = System.currentTimeMillis(),
                nonce = challenge.nonce
            )
            
            connection.sendAuthResponse(authResponse)
            
            // Receive session token
            val sessionToken = connection.receiveSessionToken()
            
            // Store token securely
            val tokenId = tokenVault.storeToken(
                projectId = projectId,
                token = sessionToken.token,
                type = TokenType.SESSION,
                expiresAt = sessionToken.expiresAt
            )
            
            // Create session
            val session = AuthenticatedSession(
                sessionId = UUID.randomUUID().toString(),
                projectId = projectId,
                tokenId = tokenId,
                connection = connection,
                establishedAt = System.currentTimeMillis(),
                lastActivity = System.currentTimeMillis()
            )
            
            // Store session
            activeSessions[session.sessionId] = session
            sessionDao.insert(session.toEntity())
            
            // Start heartbeat
            startHeartbeat(session)
            
            SessionResult.Success(session)
            
        } catch (e: Exception) {
            SessionResult.Failure(e.message ?: "Session establishment failed")
        }
    }
    
    private fun startHeartbeat(session: AuthenticatedSession) {
        session.heartbeatJob = GlobalScope.launch {
            while (isActive) {
                delay(30_000) // 30 seconds
                
                try {
                    session.connection.sendPing()
                    session.lastActivity = System.currentTimeMillis()
                    sessionDao.updateLastActivity(session.sessionId, session.lastActivity)
                } catch (e: Exception) {
                    handleSessionFailure(session, e)
                    break
                }
            }
        }
    }
    
    private suspend fun handleSessionFailure(session: AuthenticatedSession, error: Exception) {
        // Remove from active sessions
        activeSessions.remove(session.sessionId)
        
        // Update database
        sessionDao.markInactive(session.sessionId)
        
        // Log error
        // Implementation would include proper error logging
    }
}
```

### 5. PermissionVerifier

**Purpose**: Cryptographically verifies permission requests

**Purpose**: Validates permission request authenticity using cryptographic signatures and enforces security policies

```kotlin
package com.pocketagent.security.permission

import android.util.Base64
import com.pocketagent.security.audit.AuditLogger
import com.pocketagent.security.policy.PolicyManager
import com.pocketagent.security.session.SessionAuthManager
import com.pocketagent.security.token.TokenVault
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val tool: String,
    val action: String,
    val params: Map<String, Any>,
    val timestamp: Long,
    val signature: String
)

data class PermissionDecision(
    val decision: Decision,
    val reason: String? = null,
    val confirmed: Boolean = false
) {
    enum class Decision {
        ALLOW,
        DENY
    }
}

data class PermissionResponse(
    val requestId: String,
    val decision: PermissionDecision.Decision,
    val timestamp: Long,
    val reason: String?
)

sealed class PermissionResult {
    object Success : PermissionResult()
    object InvalidSession : PermissionResult()
    object InvalidSignature : PermissionResult()
    object Expired : PermissionResult()
    object PolicyDenied : PermissionResult()
    data class RequiresConfirmation(val riskAssessment: RiskAssessment) : PermissionResult()
}

data class RiskAssessment(
    val level: RiskLevel,
    val factors: List<String>
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class PolicyDecision {
    ALLOW,
    DENY,
    REQUIRE_CONFIRMATION,
    CONTINUE
}

enum class SecurityAlertType {
    INVALID_SIGNATURE,
    BRUTE_FORCE_DETECTED,
    TAMPERING
}

const val MAX_REQUEST_AGE = 60_000L // 60 seconds

@Singleton
class PermissionVerifier @Inject constructor(
    private val sessionManager: SessionAuthManager,
    private val policyManager: PolicyManager,
    private val auditLogger: AuditLogger
) {
    
    suspend fun verifyAndAuthorize(
        request: PermissionRequest,
        userDecision: PermissionDecision
    ): PermissionResult {
        // Verify request authenticity
        val session = sessionManager.getSession(request.sessionId)
            ?: return PermissionResult.InvalidSession
            
        if (!verifyRequestSignature(request, session)) {
            auditLogger.logSecurityAlert(
                type = SecurityAlertType.INVALID_SIGNATURE,
                sessionId = request.sessionId,
                details = "Permission request signature verification failed"
            )
            return PermissionResult.InvalidSignature
        }
        
        // Check request freshness
        val age = System.currentTimeMillis() - request.timestamp
        if (age > MAX_REQUEST_AGE) {
            return PermissionResult.Expired
        }
        
        // Apply security policy
        val policyDecision = policyManager.evaluateRequest(request)
        if (policyDecision == PolicyDecision.DENY) {
            return PermissionResult.PolicyDenied
        }
        
        // Check risk level
        val riskAssessment = assessRisk(request)
        if (riskAssessment.level == RiskLevel.CRITICAL && !userDecision.confirmed) {
            return PermissionResult.RequiresConfirmation(riskAssessment)
        }
        
        // Log decision
        auditLogger.logPermissionDecision(
            requestId = request.id,
            sessionId = request.sessionId,
            tool = request.tool,
            action = request.action,
            decision = userDecision.decision,
            riskLevel = riskAssessment.level
        )
        
        // Send response
        val response = PermissionResponse(
            requestId = request.id,
            decision = userDecision.decision,
            timestamp = System.currentTimeMillis(),
            reason = userDecision.reason
        )
        
        session.connection.sendPermissionResponse(response)
        
        return PermissionResult.Success
    }
    
    private fun verifyRequestSignature(
        request: PermissionRequest,
        session: AuthenticatedSession
    ): Boolean {
        val sessionKey = tokenVault.getSessionKey(session.tokenId)
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(sessionKey)
        
        val message = buildString {
            append(request.id)
            append(":")
            append(request.tool)
            append(":")
            append(request.action)
            append(":")
            append(request.timestamp)
            append(":")
            append(request.params.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" })
        }
        
        val expectedSignature = mac.doFinal(message.toByteArray())
        val providedSignature = Base64.decode(request.signature, Base64.NO_WRAP)
        
        return MessageDigest.isEqual(expectedSignature, providedSignature)
    }
    
    private fun assessRisk(request: PermissionRequest): RiskAssessment {
        val factors = mutableListOf<String>()
        var riskLevel = RiskLevel.LOW
        
        // Check operation type
        if (request.tool in listOf("file", "database", "system")) {
            factors.add("Sensitive operation")
            riskLevel = RiskLevel.MEDIUM
        }
        
        // Check action type
        if (request.action in listOf("delete", "modify", "execute")) {
            factors.add("Destructive action")
            riskLevel = RiskLevel.HIGH
        }
        
        // Check parameter patterns
        if (request.params.values.any { it.toString().contains("sudo") || it.toString().contains("rm -rf") }) {
            factors.add("Dangerous parameters")
            riskLevel = RiskLevel.CRITICAL
        }
        
        return RiskAssessment(riskLevel, factors)
    }
    
    private fun calculateRiskLevel(request: PermissionRequest): RiskLevel {
        return assessRisk(request).level
    }
}
```

### 6. TokenVault

**Purpose**: Secure storage and management of authentication tokens

**Purpose**: Provides secure encrypted storage for authentication tokens with automatic expiration and usage tracking

```kotlin
package com.pocketagent.security.token

import androidx.biometric.BiometricPrompt
import com.pocketagent.data.dao.TokenDao
import com.pocketagent.data.entity.TokenEntity
import com.pocketagent.security.audit.AuditLogger
import com.pocketagent.security.crypto.CryptoManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.SecretKey

data class TokenEntity(
    val id: String,
    val projectId: String,
    val encryptedToken: ByteArray,
    val tokenIv: ByteArray,
    val type: TokenType,
    val createdAt: Long,
    val expiresAt: Long?,
    val lastUsed: Long,
    val usageCount: Int
)

enum class RevocationReason {
    EXPIRED,
    MANUAL,
    SECURITY_INCIDENT,
    ROTATION
}

@Singleton
class TokenVault @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val tokenDao: TokenDao,
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TOKEN_KEY_ALIAS = "token_vault_master_key"
    }
    
    suspend fun storeToken(
        projectId: String,
        token: String,
        type: TokenType,
        expiresAt: Long? = null
    ): String {
        val tokenId = UUID.randomUUID().toString()
        
        // Encrypt token
        val encryptedToken = cryptoManager.encryptWithMasterKey(
            data = token.toByteArray(),
            keyAlias = TOKEN_KEY_ALIAS
        )
        
        // Store in database
        val tokenEntity = TokenEntity(
            id = tokenId,
            projectId = projectId,
            encryptedToken = encryptedToken.ciphertext,
            tokenIv = encryptedToken.iv,
            type = type,
            createdAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            lastUsed = System.currentTimeMillis(),
            usageCount = 0
        )
        
        tokenDao.insert(tokenEntity)
        
        // Schedule cleanup if token expires
        if (expiresAt != null) {
            scheduleTokenCleanup(tokenId, expiresAt)
        }
        
        // Audit log
        auditLogger.logTokenCreated(
            tokenId = tokenId,
            type = type,
            projectId = projectId
        )
        
        return tokenId
    }
    
    suspend fun retrieveToken(
        tokenId: String,
        authResult: BiometricPrompt.CryptoObject
    ): String? {
        val tokenEntity = tokenDao.getById(tokenId) ?: return null
        
        // Check expiration
        if (tokenEntity.expiresAt != null && 
            System.currentTimeMillis() > tokenEntity.expiresAt) {
            revokeToken(tokenId, RevocationReason.EXPIRED)
            return null
        }
        
        // Decrypt token
        val cipher = authResult.cipher ?: throw IllegalStateException("No cipher in auth result")
        val tokenBytes = cipher.doFinal(tokenEntity.encryptedToken)
        val token = String(tokenBytes)
        
        // Update usage
        tokenDao.updateUsage(
            tokenId = tokenId,
            lastUsed = System.currentTimeMillis(),
            usageCount = tokenEntity.usageCount + 1
        )
        
        // Clear sensitive data
        tokenBytes.fill(0)
        
        return token
    }
    
    suspend fun revokeToken(
        tokenId: String,
        reason: RevocationReason
    ) {
        tokenDao.delete(tokenId)
        
        auditLogger.logTokenRevoked(
            tokenId = tokenId,
            reason = reason
        )
    }
    
    private fun scheduleTokenCleanup(tokenId: String, expiresAt: Long) {
        // Implementation would schedule a job to clean up expired tokens
        // This could use WorkManager or a similar scheduling mechanism
    }
    
    suspend fun getSessionKey(tokenId: String): SecretKey {
        // Implementation would retrieve and decrypt the session key
        // This is a placeholder for the actual implementation
        TODO("Implement session key retrieval")
    }
}
```

### 7. AuditLogger

**Purpose**: Comprehensive security event logging without exposing sensitive data

**Purpose**: Records security events and operations for audit compliance while protecting sensitive information

```kotlin
package com.pocketagent.security.audit

import com.pocketagent.data.dao.AuditDao
import com.pocketagent.data.entity.AuditEvent
import com.pocketagent.device.DeviceInfo
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AuditEvent(
    val id: String,
    val timestamp: Long,
    val userId: String,
    val eventType: EventType,
    val eventSubtype: String,
    val success: Boolean,
    val ipAddress: String? = null,
    val deviceId: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val resourceId: String? = null,
    val errorMessage: String? = null
)

enum class EventType {
    AUTHENTICATION,
    KEY_OPERATION,
    PERMISSION_GRANT,
    PERMISSION_DENY,
    SESSION_START,
    SESSION_END,
    SECURITY_ALERT
}

enum class AuthEventType {
    BIOMETRIC,
    PASSWORD,
    PIN,
    PATTERN
}

enum class AuthMethod {
    BIOMETRIC,
    DEVICE_CREDENTIAL,
    PASSWORD
}

enum class KeyOperation {
    IMPORT,
    EXPORT,
    DELETE,
    ROTATE,
    SIGN,
    DECRYPT
}

@Singleton
class AuditLogger @Inject constructor(
    private val auditDao: AuditDao,
    private val deviceInfo: DeviceInfo
) {
    
    suspend fun logAuthenticationEvent(
        userId: String,
        type: AuthEventType,
        success: Boolean,
        method: AuthMethod,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = userId,
            eventType = EventType.AUTHENTICATION,
            eventSubtype = type.name,
            success = success,
            ipAddress = deviceInfo.getIpAddress(),
            deviceId = deviceInfo.getDeviceId(),
            metadata = metadata + mapOf(
                "method" to method.name,
                "device_model" to deviceInfo.getModel(),
                "os_version" to deviceInfo.getOsVersion()
            )
        )
        
        auditDao.insert(event)
        
        // Alert on suspicious activity
        if (!success && type == AuthEventType.BIOMETRIC) {
            checkForBruteForce(userId)
        }
    }
    
    suspend fun logKeyOperation(
        keyId: String,
        operation: KeyOperation,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            eventType = EventType.KEY_OPERATION,
            eventSubtype = operation.name,
            success = success,
            resourceId = keyId,
            errorMessage = errorMessage,
            metadata = mapOf(
                "operation" to operation.name
            )
        )
        
        auditDao.insert(event)
    }
    
    private suspend fun checkForBruteForce(userId: String) {
        val recentFailures = auditDao.getRecentAuthFailures(
            userId = userId,
            since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        )
        
        if (recentFailures.size >= 5) {
            logSecurityAlert(
                type = SecurityAlertType.BRUTE_FORCE_DETECTED,
                userId = userId,
                details = "${recentFailures.size} failed auth attempts in 5 minutes"
            )
        }
    }
    
    private suspend fun getCurrentUserId(): String {
        // Implementation would get the current user ID from session/auth manager
        return "current-user-id"
    }
    
    suspend fun logKeyImport(keyId: String, keyName: String, algorithm: String, fingerprint: String) {
        logKeyOperation(
            keyId = keyId,
            operation = KeyOperation.IMPORT,
            success = true,
            errorMessage = null
        )
    }
    
    suspend fun logKeyUsage(keyId: String, operation: String, dataSize: Int) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            eventType = EventType.KEY_OPERATION,
            eventSubtype = operation,
            success = true,
            resourceId = keyId,
            metadata = mapOf("data_size" to dataSize)
        )
        
        auditDao.insert(event)
    }
    
    suspend fun logSecurityAlert(type: SecurityAlertType, sessionId: String? = null, userId: String? = null, details: String) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = userId ?: getCurrentUserId(),
            eventType = EventType.SECURITY_ALERT,
            eventSubtype = type.name,
            success = false,
            metadata = mapOf(
                "details" to details,
                "session_id" to (sessionId ?: "")
            )
        )
        
        auditDao.insert(event)
    }
    
    suspend fun logPermissionDecision(
        requestId: String,
        sessionId: String,
        tool: String,
        action: String,
        decision: PermissionDecision.Decision,
        riskLevel: RiskLevel
    ) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            eventType = if (decision == PermissionDecision.Decision.ALLOW) EventType.PERMISSION_GRANT else EventType.PERMISSION_DENY,
            eventSubtype = "$tool.$action",
            success = true,
            metadata = mapOf(
                "request_id" to requestId,
                "session_id" to sessionId,
                "risk_level" to riskLevel.name
            )
        )
        
        auditDao.insert(event)
    }
    
    suspend fun logTokenCreated(tokenId: String, type: TokenType, projectId: String) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            eventType = EventType.KEY_OPERATION,
            eventSubtype = "TOKEN_CREATED",
            success = true,
            resourceId = tokenId,
            metadata = mapOf(
                "token_type" to type.name,
                "project_id" to projectId
            )
        )
        
        auditDao.insert(event)
    }
    
    suspend fun logTokenRevoked(tokenId: String, reason: RevocationReason) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            eventType = EventType.KEY_OPERATION,
            eventSubtype = "TOKEN_REVOKED",
            success = true,
            resourceId = tokenId,
            metadata = mapOf("reason" to reason.name)
        )
        
        auditDao.insert(event)
    }
}
```

### 8. PolicyManager

**Purpose**: Enforces configurable security policies

**Purpose**: Evaluates and enforces security policies for permission requests based on configurable rules

```kotlin
package com.pocketagent.security.policy

import androidx.security.crypto.EncryptedSharedPreferences
import com.pocketagent.data.dao.PolicyDao
import com.pocketagent.data.entity.SecurityPolicy
import javax.inject.Inject
import javax.inject.Singleton

data class SecurityPolicy(
    val id: String,
    val name: String,
    val type: PolicyType,
    val priority: Int,
    val config: Map<String, Any>,
    val isActive: Boolean
)

enum class PolicyType {
    TOOL_BASED,
    TIME_BASED,
    RISK_BASED,
    FREQUENCY_BASED
}

@Singleton
class PolicyManager @Inject constructor(
    private val policyDao: PolicyDao,
    private val encryptedPrefs: EncryptedSharedPreferences
) {
    
    fun evaluateRequest(request: PermissionRequest): PolicyDecision {
        val policies = policyDao.getActivePolicies()
        
        for (policy in policies.sortedByDescending { it.priority }) {
            val decision = evaluatePolicy(policy, request)
            if (decision != PolicyDecision.CONTINUE) {
                return decision
            }
        }
        
        return getDefaultPolicy()
    }
    
    private fun evaluatePolicy(
        policy: SecurityPolicy,
        request: PermissionRequest
    ): PolicyDecision {
        // Check if policy applies
        if (!policyMatches(policy, request)) {
            return PolicyDecision.CONTINUE
        }
        
        // Evaluate conditions
        return when (policy.type) {
            PolicyType.TOOL_BASED -> evaluateToolPolicy(policy, request)
            PolicyType.TIME_BASED -> evaluateTimePolicy(policy, request)
            PolicyType.RISK_BASED -> evaluateRiskPolicy(policy, request)
            PolicyType.FREQUENCY_BASED -> evaluateFrequencyPolicy(policy, request)
        }
    }
    
    private fun evaluateRiskPolicy(
        policy: SecurityPolicy,
        request: PermissionRequest
    ): PolicyDecision {
        val riskLevel = calculateRiskLevel(request)
        val maxAllowedRisk = policy.config["max_risk_level"] as? String ?: "HIGH"
        
        return when {
            riskLevel.ordinal > RiskLevel.valueOf(maxAllowedRisk).ordinal -> 
                PolicyDecision.DENY
            riskLevel == RiskLevel.HIGH && policy.config["require_confirmation"] == true ->
                PolicyDecision.REQUIRE_CONFIRMATION
            else -> PolicyDecision.ALLOW
        }
    }
    
    private fun policyMatches(policy: SecurityPolicy, request: PermissionRequest): Boolean {
        // Implementation would check if the policy applies to this request
        return true // Simplified for example
    }
    
    private fun evaluateToolPolicy(policy: SecurityPolicy, request: PermissionRequest): PolicyDecision {
        val allowedTools = policy.config["allowed_tools"] as? List<String> ?: emptyList()
        val deniedTools = policy.config["denied_tools"] as? List<String> ?: emptyList()
        
        return when {
            request.tool in deniedTools -> PolicyDecision.DENY
            request.tool in allowedTools -> PolicyDecision.ALLOW
            else -> PolicyDecision.CONTINUE
        }
    }
    
    private fun evaluateTimePolicy(policy: SecurityPolicy, request: PermissionRequest): PolicyDecision {
        // Implementation would check time-based restrictions
        return PolicyDecision.CONTINUE
    }
    
    private fun evaluateFrequencyPolicy(policy: SecurityPolicy, request: PermissionRequest): PolicyDecision {
        // Implementation would check frequency limits
        return PolicyDecision.CONTINUE
    }
    
    private fun getDefaultPolicy(): PolicyDecision {
        val defaultSetting = encryptedPrefs.getString("default_policy", "ask")
        return when (defaultSetting) {
            "allow" -> PolicyDecision.ALLOW
            "deny" -> PolicyDecision.DENY
            else -> PolicyDecision.REQUIRE_CONFIRMATION
        }
    }
}
```

## Security Flows

### Authentication Flow

```mermaid
sequenceDiagram
    participant User
    participant App
    participant BiometricHW as Biometric Hardware
    participant Keystore as Android Keystore
    participant Server
    
    User->>App: Request protected operation
    App->>BiometricHW: Request authentication
    BiometricHW->>User: Prompt for biometric
    User->>BiometricHW: Provide biometric
    BiometricHW->>Keystore: Unlock key
    Keystore->>App: Provide cipher
    App->>App: Decrypt credentials
    App->>Server: Authenticate with credentials
    Server->>App: Return session token
    App->>App: Store encrypted token
```

### Permission Verification Flow

```mermaid
sequenceDiagram
    participant Claude
    participant Server
    participant App
    participant User
    participant Audit
    
    Claude->>Server: Request permission
    Server->>Server: Sign request with session key
    Server->>App: Send signed request
    App->>App: Verify signature
    App->>App: Check policies
    App->>User: Show permission dialog
    User->>App: Approve/Deny
    App->>Audit: Log decision
    App->>Server: Send signed response
    Server->>Claude: Forward decision
```

## Error Handling

### Security-Specific Errors

**Purpose**: Defines security-specific error types and their handling strategies

```kotlin
package com.pocketagent.security.error
sealed class SecurityError : Exception() {
    data class BiometricNotAvailable(val reason: String) : SecurityError()
    data class BiometricNotEnrolled(val type: BiometricType) : SecurityError()
    data class KeystoreError(val operation: String, val cause: Throwable) : SecurityError()
    data class AuthenticationTimeout(val duration: Long) : SecurityError()
    data class InvalidCredentials(val attempt: Int) : SecurityError()
    data class SessionExpired(val sessionId: String) : SecurityError()
    data class PolicyViolation(val policy: String) : SecurityError()
    data class TamperedRequest(val requestId: String) : SecurityError()
}

fun handleSecurityError(error: SecurityError) {
    when (error) {
        is SecurityError.BiometricNotEnrolled -> {
            // Guide user to enroll biometrics
            showBiometricEnrollmentDialog()
        }
        is SecurityError.KeystoreError -> {
            // Log and attempt recovery
            logKeystoreError(error)
            attemptKeystoreRecovery()
        }
        is SecurityError.TamperedRequest -> {
            // Security alert
            triggerSecurityAlert(SecurityAlertType.TAMPERING, error.requestId)
            terminateSession()
        }
        // ... handle other errors
    }
}

fun showBiometricEnrollmentDialog() {
    // Implementation would show dialog to guide user to system settings
}

fun logKeystoreError(error: SecurityError.KeystoreError) {
    // Implementation would log the error for debugging
}

fun attemptKeystoreRecovery() {
    // Implementation would try to recover from keystore errors
}

fun triggerSecurityAlert(type: SecurityAlertType, requestId: String) {
    // Implementation would trigger security alert notifications
}

fun terminateSession() {
    // Implementation would terminate the current session
}
```

## Performance Optimizations

### Biometric Pre-warming

**Purpose**: Optimizes biometric authentication performance by pre-creating ciphers

```kotlin
package com.pocketagent.security.optimization

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.crypto.Cipher
class BiometricPrewarmer {
    private val prewarmedCiphers = LruCache<String, Cipher>(5)
    
    fun prewarmForOperation(operation: SecurityOperation) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val cipher = createCipherForOperation(operation)
                prewarmedCiphers.put(operation.id, cipher)
            } catch (e: Exception) {
                // Ignore pre-warming failures
            }
        }
    }
    
    private fun createCipherForOperation(operation: SecurityOperation): Cipher {
        // Implementation would create appropriate cipher for the operation
        return Cipher.getInstance("AES/GCM/NoPadding")
    }
}
```

### Session Caching

**Purpose**: Caches authenticated sessions with automatic expiration and secure cleanup

```kotlin
package com.pocketagent.security.cache

import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit
data class CachedSession(
    val sessionId: String,
    val data: ByteArray
) {
    fun clearSensitiveData() {
        data.fill(0)
    }
}

class SessionCache {
    private val cache = ExpiringMap.Builder<String, CachedSession>()
        .maxSize(100)
        .expiration(5, TimeUnit.MINUTES)
        .expirationPolicy(ExpirationPolicy.ACCESSED)
        .expirationListener<String, CachedSession> { key, value ->
            // Secure cleanup
            value.clearSensitiveData()
        }
        .build()
}
```

## Security Hardening

### Anti-Tampering Measures

**Purpose**: Implements security hardening measures to detect and prevent tampering attempts

```kotlin
package com.pocketagent.security.hardening

import android.app.Activity
import android.os.Debug
import android.view.WindowManager
import com.pocketagent.BuildConfig
object SecurityHardening {
    fun enforceSecurityMeasures(activity: Activity) {
        // Prevent screenshots
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // Detect debugging
        if (BuildConfig.DEBUG) return // Skip in debug builds
        
        if (Debug.isDebuggerConnected()) {
            clearAllSensitiveData()
            activity.finishAndRemoveTask()
            throw SecurityException("Debugger detected")
        }
        
        // Verify app signature
        verifyAppSignature(activity)
        
        // Check for root
        if (RootDetector.isDeviceRooted()) {
            showRootWarning()
        }
    }
    
    private fun clearAllSensitiveData() {
        // Implementation would clear all sensitive data from memory
    }
    
    private fun verifyAppSignature(activity: Activity) {
        // Implementation would verify the app's signature
    }
    
    private fun showRootWarning() {
        // Implementation would show a warning dialog
    }
}

object RootDetector {
    fun isDeviceRooted(): Boolean {
        // Implementation would check for root access
        return false
    }
}
```

### Certificate Pinning

**Purpose**: Implements certificate pinning to prevent man-in-the-middle attacks

```kotlin
package com.pocketagent.security.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
class CertificatePinner {
    fun createPinnedClient(): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add("api.pocketagent.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .add("api.pocketagent.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup
            .build()
            
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }
}
```

## Testing Strategies

### Security Testing

**Purpose**: Test suite for verifying security authentication functionality

```kotlin
package com.pocketagent.security.test

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
@RunWith(AndroidJUnit4::class)
class SecurityAuthenticationTest {
    
    @Test
    fun testBiometricAuthenticationFlow() {
        // Mock biometric hardware
        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(BIOMETRIC_STRONG) } 
            returns BiometricManager.BIOMETRIC_SUCCESS
        
        // Test authentication
        val result = biometricAuthManager.authenticateForOperation(
            activity,
            SecurityOperation.Decryption("test_op")
        )
        
        assertTrue(result is AuthenticationResult.Success)
    }
    
    @Test
    fun testPermissionSignatureVerification() {
        val request = createMockPermissionRequest()
        val tamperedRequest = request.copy(tool = "different_tool")
        
        assertTrue(permissionVerifier.verifySignature(request))
        assertFalse(permissionVerifier.verifySignature(tamperedRequest))
    }
    
    private fun createMockPermissionRequest(): PermissionRequest {
        return PermissionRequest(
            id = "test-request",
            sessionId = "test-session",
            tool = "file",
            action = "read",
            params = mapOf("path" to "/test/file"),
            timestamp = System.currentTimeMillis(),
            signature = "mock-signature"
        )
    }
}
```

## Future Enhancements

1. **Passkey Integration**: FIDO2/WebAuthn support for passwordless authentication
2. **Behavioral Biometrics**: Continuous authentication based on usage patterns
3. **Zero-Knowledge Proofs**: Enhanced privacy for authentication
4. **Hardware Security Key**: YubiKey and similar device support
5. **Quantum-Resistant Algorithms**: Future-proof cryptography