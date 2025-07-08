# Communication Layer Feature Specification - Authentication & Session Management
**For Android Mobile Application**

> **Navigation**: [Overview](./communication-layer-overview.feat.md) | [WebSocket](./communication-layer-websocket.feat.md) | **Authentication** | [Messages](./communication-layer-messages.feat.md) | [Testing](./communication-layer-testing.feat.md) | [Index](./communication-layer-index.md)

## SSH Key Authenticator

**Purpose**: Handles SSH key authentication for WebSocket connections. Manages challenge-response authentication flow, SSH key signing operations, and authentication state management.

```kotlin
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.signers.RSADigestSigner
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyAuthenticator @Inject constructor(
    private val sshKeyImportManager: SshKeyImportManager,
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    @Serializable
    data class AuthChallenge(
        val type: String = "auth_challenge",
        val nonce: String,
        val timestamp: Long,
        val serverVersion: String
    )
    
    @Serializable
    data class AuthResponse(
        val type: String = "auth_response",
        val publicKey: String,
        val signature: String,
        val clientVersion: String,
        val sessionId: String? = null // For session resumption
    )
    
    @Serializable
    data class AuthSuccess(
        val type: String = "auth_success",
        val sessionId: String,
        val expiresAt: Long
    )
    
    @Serializable
    data class AuthError(
        val type: String = "auth_error",
        val code: String,
        val message: String
    )
    
    /**
     * Sign authentication challenge with SSH private key
     */
    suspend fun signChallenge(
        challenge: AuthChallenge,
        sshIdentity: SshIdentityEntity
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            // Get decrypted SSH private key (already unlocked at app launch)
            val privateKeyBytes = sshKeyImportManager.decryptSshPrivateKey(
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            // Parse private key
            val privateKey = parsePrivateKey(privateKeyBytes)
            
            // Create data to sign: nonce + timestamp
            val dataToSign = "${challenge.nonce}${challenge.timestamp}"
            
            // Sign the data
            val signature = signData(privateKey, dataToSign.toByteArray())
            
            // Create auth response
            val authResponse = AuthResponse(
                publicKey = sshIdentity.publicKey,
                signature = Base64.getEncoder().encodeToString(signature),
                clientVersion = BuildConfig.VERSION_NAME,
                sessionId = null // New connection, no session to resume
            )
            
            // Clear sensitive data
            privateKeyBytes.fill(0)
            
            Result.success(authResponse)
        } catch (e: Exception) {
            securityAuditLogger.logAuthenticationAttempt(
                success = false,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Sign data for session resumption
     */
    suspend fun signSessionResumption(
        sessionId: String,
        nonce: String,
        sshIdentity: SshIdentityEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val privateKeyBytes = sshKeyImportManager.decryptSshPrivateKey(
                encryptedPrivateKey = sshIdentity.encryptedPrivateKey,
                keyAlias = sshIdentity.keyAlias
            )
            
            val privateKey = parsePrivateKey(privateKeyBytes)
            val dataToSign = "$sessionId$nonce"
            val signature = signData(privateKey, dataToSign.toByteArray())
            
            privateKeyBytes.fill(0)
            
            Result.success(Base64.getEncoder().encodeToString(signature))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse SSH private key from bytes
     */
    private fun parsePrivateKey(keyBytes: ByteArray): PrivateKey {
        return try {
            // Try PKCS8 format first
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            // Try PEM format
            val keyString = String(keyBytes)
            val pemParser = PEMParser(keyString.reader())
            val pemObject = pemParser.readObject()
            
            when (pemObject) {
                is PEMKeyPair -> {
                    val converter = JcaPEMKeyConverter()
                    converter.getPrivateKey(pemObject.privateKeyInfo)
                }
                is PrivateKeyInfo -> {
                    val converter = JcaPEMKeyConverter()
                    converter.getPrivateKey(pemObject)
                }
                else -> throw IllegalArgumentException("Unsupported key format")
            }
        }
    }
    
    /**
     * Sign data with RSA private key (SSH-compatible)
     */
    private fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
        return when (privateKey.algorithm) {
            "RSA" -> {
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            "EC" -> {
                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            "Ed25519" -> {
                val signature = Signature.getInstance("Ed25519")
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
            else -> throw IllegalArgumentException("Unsupported key algorithm: ${privateKey.algorithm}")
        }
    }
    
    /**
     * Verify server response signature (optional, for mutual auth)
     */
    fun verifyServerSignature(
        publicKey: String,
        signature: String,
        data: String
    ): Boolean {
        return try {
            // Parse server's public key
            val keyBytes = Base64.getDecoder().decode(
                publicKey.replace("ssh-rsa ", "").split(" ")[0]
            )
            
            // Verify signature
            // Implementation depends on server's signing method
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

## Session Manager

**Purpose**: Manages Claude Code session lifecycle including persistence, resumption, and synchronization. Tracks conversation IDs, handles session state recovery after disconnections, and ensures message continuity across app restarts.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val messageProtocol: MessageProtocol,
    private val webSocketClient: WebSocketClient,
    private val messageQueueManager: MessageQueueManager
) {
    
    companion object {
        private const val SESSION_CACHE_SIZE = 10
        private const val SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    private val _activeSessions = MutableStateFlow<Map<String, SessionState>>(emptyMap())
    val activeSessions: StateFlow<Map<String, SessionState>> = _activeSessions.asStateFlow()
    
    data class SessionState(
        val sessionId: String,
        val projectId: String,
        val conversationId: String?,
        val lastMessageId: String?,
        val lastMessageTimestamp: Long,
        val turnNumber: Int = 0,
        val pendingOperations: List<String> = emptyList(),
        val isActive: Boolean = true
    )
    
    /**
     * Resume an existing session after reconnection
     */
    suspend fun resumeSession(
        sessionId: String,
        projectId: String
    ): Result<SessionState> {
        return try {
            // Load session from repository
            val persistedSession = sessionRepository.getSession(sessionId)
                ?: return Result.failure(IllegalStateException("Session not found"))
            
            // Create session state
            val sessionState = SessionState(
                sessionId = sessionId,
                projectId = projectId,
                conversationId = persistedSession.conversationId,
                lastMessageId = persistedSession.lastMessageId,
                lastMessageTimestamp = persistedSession.lastMessageTimestamp,
                turnNumber = persistedSession.turnNumber
            )
            
            // Send resume message
            val resumeMessage = MessageProtocol.SessionResumeMessage(
                sessionId = sessionId,
                lastMessageId = persistedSession.lastMessageId ?: "",
                lastMessageTimestamp = persistedSession.lastMessageTimestamp
            )
            
            webSocketClient.sendMessage(resumeMessage)
            
            // Update active sessions
            _activeSessions.update { current ->
                current + (projectId to sessionState)
            }
            
            Result.success(sessionState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Track Claude conversation ID for a session
     */
    suspend fun trackClaudeConversationId(
        sessionId: String,
        conversationId: String
    ) {
        // Update in-memory state
        _activeSessions.update { sessions ->
            sessions.mapValues { (_, state) ->
                if (state.sessionId == sessionId) {
                    state.copy(conversationId = conversationId)
                } else {
                    state
                }
            }
        }
        
        // Persist to repository
        sessionRepository.updateConversationId(sessionId, conversationId)
    }
    
    /**
     * Sync session state with wrapper
     */
    suspend fun syncSessionState(sessionId: String): SessionSyncState {
        val sessionState = _activeSessions.value.values.find { it.sessionId == sessionId }
            ?: throw IllegalStateException("Session not found")
        
        // Wait for sync response
        val syncFlow = webSocketClient.incomingMessages
            .filterIsInstance<MessageProtocol.SessionSyncMessage>()
            .filter { it.sessionId == sessionId }
            .first()
        
        return SessionSyncState(
            synchronized = true,
            missedMessages = syncFlow.messageHistory,
            currentTurnNumber = syncFlow.currentTurnNumber,
            pendingOperations = syncFlow.pendingOperations
        )
    }
    
    /**
     * Handle session timeout
     */
    suspend fun cleanupTimedOutSessions() {
        val now = System.currentTimeMillis()
        _activeSessions.update { sessions ->
            sessions.filter { (_, state) ->
                now - state.lastMessageTimestamp < SESSION_TIMEOUT_MS
            }
        }
    }
    
    /**
     * Persist session state
     */
    suspend fun persistSession(sessionState: SessionState) {
        sessionRepository.saveSession(
            SessionEntity(
                sessionId = sessionState.sessionId,
                projectId = sessionState.projectId,
                conversationId = sessionState.conversationId,
                lastMessageId = sessionState.lastMessageId,
                lastMessageTimestamp = sessionState.lastMessageTimestamp,
                turnNumber = sessionState.turnNumber
            )
        )
    }
    
    /**
     * Initialize a new session from handshake
     */
    suspend fun initializeSession(
        sessionId: String,
        protocolVersion: String
    ) {
        // Implementation would create or update session
        val sessionState = SessionState(
            sessionId = sessionId,
            projectId = sessionId, // Map as needed
            conversationId = null,
            lastMessageId = null,
            lastMessageTimestamp = System.currentTimeMillis(),
            turnNumber = 0,
            pendingOperations = emptyList(),
            isActive = true
        )
        
        _activeSessions.update { current ->
            current + (sessionId to sessionState)
        }
    }
    
    data class SessionSyncState(
        val synchronized: Boolean,
        val missedMessages: List<MessageProtocol.SessionSyncMessage.MessageSummary>,
        val currentTurnNumber: Int,
        val pendingOperations: List<String>
    )
}
```

## Permission Policy Manager

**Purpose**: Manages permission policies for unattended operation, caches permission decisions, and applies default policies based on risk levels. Enables the app to handle permissions automatically when disconnected based on user preferences.

```kotlin
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class PermissionPolicyManager @Inject constructor(
    private val encryptedStorageManager: EncryptedStorageManager,
    private val securityAuditLogger: SecurityAuditLogger
) {
    
    companion object {
        private const val POLICY_KEY_PREFIX = "permission_policy_"
        private const val DECISION_CACHE_SIZE = 100
        private const val DECISION_EXPIRY_MS = 60 * 60 * 1000L // 1 hour
    }
    
    private val decisionCache = ConcurrentHashMap<String, CachedDecision>()
    private val _defaultPolicies = MutableStateFlow(loadDefaultPolicies())
    val defaultPolicies: StateFlow<DefaultPolicies> = _defaultPolicies.asStateFlow()
    
    data class CachedDecision(
        val decision: PermissionDecision,
        val timestamp: Long,
        val expiresAt: Long
    )
    
    data class PermissionDecision(
        val approved: Boolean,
        val reason: String,
        val policyApplied: PolicyType
    )
    
    enum class PolicyType {
        USER_DECISION,
        DEFAULT_POLICY,
        CACHED_DECISION,
        RISK_BASED,
        TIMEOUT_DEFAULT
    }
    
    data class DefaultPolicies(
        val allowLowRiskByDefault: Boolean = true,
        val allowMediumRiskByDefault: Boolean = false,
        val denyHighRiskByDefault: Boolean = true,
        val timeoutSeconds: Int = 30,
        val timeoutAction: TimeoutAction = TimeoutAction.DENY
    )
    
    enum class TimeoutAction {
        APPROVE,
        DENY,
        USE_RISK_BASED
    }
    
    /**
     * Evaluate permission request based on policies
     */
    fun evaluatePermission(
        request: MessageProtocol.PermissionRequest,
        isConnected: Boolean
    ): PermissionDecision {
        // Check cached decision first
        getCachedDecision(request.id)?.let { cached ->
            if (System.currentTimeMillis() < cached.expiresAt) {
                return cached.decision
            }
        }
        
        // If connected, wait for user decision
        if (isConnected) {
            return PermissionDecision(
                approved = false,
                reason = "Awaiting user decision",
                policyApplied = PolicyType.USER_DECISION
            )
        }
        
        // Apply default policies for unattended operation
        return applyDefaultPolicies(request.tool, request.action, request.risk)
    }
    
    /**
     * Cache a permission decision
     */
    fun cacheDecision(
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean = false
    ) {
        val expiryTime = if (remember) {
            System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24 hours
        } else {
            System.currentTimeMillis() + DECISION_EXPIRY_MS
        }
        
        decisionCache[requestId] = CachedDecision(
            decision = decision,
            timestamp = System.currentTimeMillis(),
            expiresAt = expiryTime
        )
        
        // Limit cache size
        if (decisionCache.size > DECISION_CACHE_SIZE) {
            cleanupOldestDecisions()
        }
        
        // Log decision
        securityAuditLogger.logPermissionDecision(
            requestId = requestId,
            approved = decision.approved,
            policyType = decision.policyApplied
        )
    }
    
    /**
     * Apply default policies based on risk level
     */
    fun applyDefaultPolicies(
        tool: String,
        action: String,
        risk: MessageProtocol.RiskLevel
    ): PermissionDecision {
        val policies = _defaultPolicies.value
        
        val approved = when (risk) {
            MessageProtocol.RiskLevel.LOW -> policies.allowLowRiskByDefault
            MessageProtocol.RiskLevel.MEDIUM -> policies.allowMediumRiskByDefault
            MessageProtocol.RiskLevel.HIGH -> !policies.denyHighRiskByDefault
        }
        
        return PermissionDecision(
            approved = approved,
            reason = "Default policy for $risk risk",
            policyApplied = PolicyType.RISK_BASED
        )
    }
    
    /**
     * Handle permission timeout
     */
    fun handlePermissionTimeout(
        request: MessageProtocol.PermissionRequest
    ): PermissionDecision {
        val policies = _defaultPolicies.value
        
        return when (policies.timeoutAction) {
            TimeoutAction.APPROVE -> PermissionDecision(
                approved = true,
                reason = "Approved by timeout policy",
                policyApplied = PolicyType.TIMEOUT_DEFAULT
            )
            TimeoutAction.DENY -> PermissionDecision(
                approved = false,
                reason = "Denied by timeout policy",
                policyApplied = PolicyType.TIMEOUT_DEFAULT
            )
            TimeoutAction.USE_RISK_BASED -> applyDefaultPolicies(
                request.tool,
                request.action,
                request.risk
            )
        }
    }
    
    /**
     * Update default policies
     */
    fun updateDefaultPolicies(policies: DefaultPolicies) {
        _defaultPolicies.value = policies
        saveDefaultPolicies(policies)
    }
    
    private fun getCachedDecision(requestId: String): CachedDecision? {
        return decisionCache[requestId]
    }
    
    private fun cleanupOldestDecisions() {
        val sortedEntries = decisionCache.entries.sortedBy { it.value.timestamp }
        val toRemove = sortedEntries.take(20)
        toRemove.forEach { decisionCache.remove(it.key) }
    }
    
    private fun loadDefaultPolicies(): DefaultPolicies {
        // Load from encrypted storage
        val lowRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}low_risk", true)
        val mediumRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}medium_risk", false)
        val highRisk = encryptedStorageManager.getBoolean("${POLICY_KEY_PREFIX}high_risk", true)
        val timeout = encryptedStorageManager.getLong("${POLICY_KEY_PREFIX}timeout", 30).toInt()
        
        return DefaultPolicies(
            allowLowRiskByDefault = lowRisk,
            allowMediumRiskByDefault = mediumRisk,
            denyHighRiskByDefault = highRisk,
            timeoutSeconds = timeout
        )
    }
    
    private fun saveDefaultPolicies(policies: DefaultPolicies) {
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}low_risk", policies.allowLowRiskByDefault)
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}medium_risk", policies.allowMediumRiskByDefault)
        encryptedStorageManager.putBoolean("${POLICY_KEY_PREFIX}high_risk", policies.denyHighRiskByDefault)
        encryptedStorageManager.putLong("${POLICY_KEY_PREFIX}timeout", policies.timeoutSeconds.toLong())
    }
}
```

## Certificate Validator

**Purpose**: Validates server certificates and manages certificate pinning for enhanced security.

```kotlin
import okhttp3.CertificatePinner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificateValidator @Inject constructor() {
    
    private val trustedCertificates = mutableMapOf<String, List<String>>()
    
    /**
     * Check if certificate pinning should be used for this URL
     */
    fun shouldPinCertificate(url: String): Boolean {
        val host = extractHost(url)
        return trustedCertificates.containsKey(host)
    }
    
    /**
     * Get certificate pinner for URL
     */
    fun getCertificatePinner(url: String): CertificatePinner {
        val host = extractHost(url)
        val pins = trustedCertificates[host] ?: emptyList()
        
        val builder = CertificatePinner.Builder()
        pins.forEach { pin ->
            builder.add(host, pin)
        }
        
        return builder.build()
    }
    
    /**
     * Add trusted certificate pin
     */
    fun addTrustedCertificate(host: String, pin: String) {
        val pins = trustedCertificates[host]?.toMutableList() ?: mutableListOf()
        pins.add(pin)
        trustedCertificates[host] = pins
    }
    
    private fun extractHost(url: String): String {
        return url.substringAfter("://")
            .substringBefore(":")
            .substringBefore("/")
    }
}
```

## Security Audit Logger

**Purpose**: Logs security-relevant events for audit trails and compliance.

```kotlin
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityAuditLogger @Inject constructor() {
    
    companion object {
        private const val TAG = "SecurityAudit"
    }
    
    fun logAuthenticationAttempt(
        success: Boolean,
        error: String? = null
    ) {
        val message = if (success) {
            "Authentication successful"
        } else {
            "Authentication failed: $error"
        }
        Log.i(TAG, message)
    }
    
    fun logWebSocketConnection(
        serverUrl: String,
        authenticated: Boolean,
        error: String? = null
    ) {
        val message = buildString {
            append("WebSocket connection to $serverUrl: ")
            append(if (authenticated) "authenticated" else "failed")
            error?.let { append(" - $it") }
        }
        Log.i(TAG, message)
    }
    
    fun logPermissionDecision(
        requestId: String,
        approved: Boolean,
        policyType: PermissionPolicyManager.PolicyType
    ) {
        Log.i(TAG, "Permission $requestId: ${if (approved) "approved" else "denied"} by $policyType")
    }
}
```