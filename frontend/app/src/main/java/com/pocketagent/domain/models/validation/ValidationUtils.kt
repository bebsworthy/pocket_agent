package com.pocketagent.domain.models.validation

import com.pocketagent.domain.models.error.ValidationException
import com.pocketagent.domain.models.entities.*
import com.pocketagent.domain.models.AppData
import java.util.regex.Pattern

/**
 * Validation utilities for domain models.
 */
object ValidationUtils {
    
    // Common validation patterns
    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )
    
    private val HOSTNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$"
    )
    
    private val URL_PATTERN = Pattern.compile(
        "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    )
    
    private val SSH_FINGERPRINT_PATTERN = Pattern.compile(
        "^(SHA256:|MD5:)?[A-Fa-f0-9:]+$"
    )
    
    private val USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_][a-zA-Z0-9_-]{0,31}$"
    )
    
    private val PATH_PATTERN = Pattern.compile(
        "^/[a-zA-Z0-9._/-]*$"
    )
    
    /**
     * Validates a string field.
     * 
     * @param value The value to validate
     * @param fieldName The name of the field
     * @param minLength Minimum length (optional)
     * @param maxLength Maximum length (optional)
     * @param pattern Regex pattern to match (optional)
     * @param allowEmpty Whether empty values are allowed
     * @throws ValidationException if validation fails
     */
    fun validateString(
        value: String,
        fieldName: String,
        minLength: Int? = null,
        maxLength: Int? = null,
        pattern: Pattern? = null,
        allowEmpty: Boolean = false
    ) {
        if (!allowEmpty && value.isEmpty()) {
            throw ValidationException(fieldName, value, "$fieldName cannot be empty")
        }
        
        if (value.isBlank() && !allowEmpty) {
            throw ValidationException(fieldName, value, "$fieldName cannot be blank")
        }
        
        minLength?.let { min ->
            if (value.length < min) {
                throw ValidationException(fieldName, value, "$fieldName must be at least $min characters")
            }
        }
        
        maxLength?.let { max ->
            if (value.length > max) {
                throw ValidationException(fieldName, value, "$fieldName must be at most $max characters")
            }
        }
        
        pattern?.let { pat ->
            if (!pat.matcher(value).matches()) {
                throw ValidationException(fieldName, value, "$fieldName has invalid format")
            }
        }
    }
    
    /**
     * Validates an integer field.
     * 
     * @param value The value to validate
     * @param fieldName The name of the field
     * @param min Minimum value (optional)
     * @param max Maximum value (optional)
     * @throws ValidationException if validation fails
     */
    fun validateInt(
        value: Int,
        fieldName: String,
        min: Int? = null,
        max: Int? = null
    ) {
        min?.let { minimum ->
            if (value < minimum) {
                throw ValidationException(fieldName, value, "$fieldName must be at least $minimum")
            }
        }
        
        max?.let { maximum ->
            if (value > maximum) {
                throw ValidationException(fieldName, value, "$fieldName must be at most $maximum")
            }
        }
    }
    
    /**
     * Validates a long field.
     * 
     * @param value The value to validate
     * @param fieldName The name of the field
     * @param min Minimum value (optional)
     * @param max Maximum value (optional)
     * @throws ValidationException if validation fails
     */
    fun validateLong(
        value: Long,
        fieldName: String,
        min: Long? = null,
        max: Long? = null
    ) {
        min?.let { minimum ->
            if (value < minimum) {
                throw ValidationException(fieldName, value, "$fieldName must be at least $minimum")
            }
        }
        
        max?.let { maximum ->
            if (value > maximum) {
                throw ValidationException(fieldName, value, "$fieldName must be at most $maximum")
            }
        }
    }
    
    /**
     * Validates a list field.
     * 
     * @param value The value to validate
     * @param fieldName The name of the field
     * @param minSize Minimum size (optional)
     * @param maxSize Maximum size (optional)
     * @param allowEmpty Whether empty lists are allowed
     * @throws ValidationException if validation fails
     */
    fun <T> validateList(
        value: List<T>,
        fieldName: String,
        minSize: Int? = null,
        maxSize: Int? = null,
        allowEmpty: Boolean = true
    ) {
        if (!allowEmpty && value.isEmpty()) {
            throw ValidationException(fieldName, value, "$fieldName cannot be empty")
        }
        
        minSize?.let { min ->
            if (value.size < min) {
                throw ValidationException(fieldName, value, "$fieldName must have at least $min items")
            }
        }
        
        maxSize?.let { max ->
            if (value.size > max) {
                throw ValidationException(fieldName, value, "$fieldName must have at most $max items")
            }
        }
    }
    
    /**
     * Validates an email address.
     * 
     * @param value The email to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateEmail(value: String, fieldName: String) {
        validateString(value, fieldName, pattern = EMAIL_PATTERN)
    }
    
    /**
     * Validates a hostname.
     * 
     * @param value The hostname to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateHostname(value: String, fieldName: String) {
        validateString(value, fieldName, maxLength = 255, pattern = HOSTNAME_PATTERN)
    }
    
    /**
     * Validates a URL.
     * 
     * @param value The URL to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateUrl(value: String, fieldName: String) {
        validateString(value, fieldName, pattern = URL_PATTERN)
    }
    
    /**
     * Validates an SSH fingerprint.
     * 
     * @param value The fingerprint to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateSshFingerprint(value: String, fieldName: String) {
        validateString(value, fieldName, pattern = SSH_FINGERPRINT_PATTERN)
    }
    
    /**
     * Validates a username.
     * 
     * @param value The username to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateUsername(value: String, fieldName: String) {
        validateString(value, fieldName, minLength = 1, maxLength = 32, pattern = USERNAME_PATTERN)
    }
    
    /**
     * Validates a file path.
     * 
     * @param value The path to validate
     * @param fieldName The name of the field
     * @param mustBeAbsolute Whether the path must be absolute
     * @throws ValidationException if validation fails
     */
    fun validatePath(value: String, fieldName: String, mustBeAbsolute: Boolean = true) {
        validateString(value, fieldName, maxLength = 4096)
        
        if (mustBeAbsolute && !value.startsWith("/")) {
            throw ValidationException(fieldName, value, "$fieldName must be an absolute path")
        }
        
        if (value.contains("..")) {
            throw ValidationException(fieldName, value, "$fieldName cannot contain '..'")
        }
        
        if (value.contains("//")) {
            throw ValidationException(fieldName, value, "$fieldName cannot contain '//'")
        }
    }
    
    /**
     * Validates a port number.
     * 
     * @param value The port to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validatePort(value: Int, fieldName: String) {
        validateInt(value, fieldName, min = 1, max = 65535)
    }
    
    /**
     * Validates a timestamp.
     * 
     * @param value The timestamp to validate
     * @param fieldName The name of the field
     * @param allowFuture Whether future timestamps are allowed
     * @throws ValidationException if validation fails
     */
    fun validateTimestamp(value: Long, fieldName: String, allowFuture: Boolean = true) {
        val now = System.currentTimeMillis()
        val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000)
        val oneYearFromNow = now + (365L * 24 * 60 * 60 * 1000)
        
        if (value < oneYearAgo) {
            throw ValidationException(fieldName, value, "$fieldName is too far in the past")
        }
        
        if (!allowFuture && value > now) {
            throw ValidationException(fieldName, value, "$fieldName cannot be in the future")
        }
        
        if (value > oneYearFromNow) {
            throw ValidationException(fieldName, value, "$fieldName is too far in the future")
        }
    }
    
    /**
     * Validates a UUID string.
     * 
     * @param value The UUID to validate
     * @param fieldName The name of the field
     * @throws ValidationException if validation fails
     */
    fun validateUuid(value: String, fieldName: String) {
        val uuidPattern = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        validateString(value, fieldName, pattern = uuidPattern)
    }
    
    /**
     * Validates that a collection has unique values.
     * 
     * @param values The collection to validate
     * @param fieldName The name of the field
     * @param keySelector Function to extract the key for uniqueness check
     * @throws ValidationException if validation fails
     */
    fun <T, K> validateUnique(
        values: Collection<T>,
        fieldName: String,
        keySelector: (T) -> K
    ) {
        val keys = values.map(keySelector)
        val uniqueKeys = keys.toSet()
        
        if (keys.size != uniqueKeys.size) {
            throw ValidationException(fieldName, values, "$fieldName contains duplicate values")
        }
    }
    
    /**
     * Validates that a reference exists in a collection.
     * 
     * @param referenceId The reference ID to validate
     * @param collection The collection to check
     * @param fieldName The name of the field
     * @param keySelector Function to extract the key from collection items
     * @throws ValidationException if validation fails
     */
    fun <T, K> validateReference(
        referenceId: K,
        collection: Collection<T>,
        fieldName: String,
        keySelector: (T) -> K
    ) {
        val exists = collection.any { keySelector(it) == referenceId }
        if (!exists) {
            throw ValidationException(fieldName, referenceId, "$fieldName references non-existent item")
        }
    }
    
    /**
     * Validates that a reference is not in use.
     * 
     * @param referenceId The reference ID to validate
     * @param collection The collection to check
     * @param fieldName The name of the field
     * @param keySelector Function to extract the key from collection items
     * @throws ValidationException if validation fails
     */
    fun <T, K> validateReferenceNotInUse(
        referenceId: K,
        collection: Collection<T>,
        fieldName: String,
        keySelector: (T) -> K
    ) {
        val inUse = collection.any { keySelector(it) == referenceId }
        if (inUse) {
            throw ValidationException(fieldName, referenceId, "$fieldName is in use and cannot be deleted")
        }
    }
}

/**
 * Extension function to validate an SSH identity.
 */
fun SshIdentity.validate() {
    ValidationUtils.validateString(name, "name", minLength = 1, maxLength = 100)
    ValidationUtils.validateString(encryptedPrivateKey, "encryptedPrivateKey", minLength = 1)
    ValidationUtils.validateSshFingerprint(publicKeyFingerprint, "publicKeyFingerprint")
    ValidationUtils.validateTimestamp(createdAt, "createdAt", allowFuture = false)
    lastUsedAt?.let { ValidationUtils.validateTimestamp(it, "lastUsedAt", allowFuture = false) }
    description?.let { ValidationUtils.validateString(it, "description", maxLength = 500, allowEmpty = true) }
}

/**
 * Extension function to validate a server profile.
 */
fun ServerProfile.validate() {
    ValidationUtils.validateString(name, "name", minLength = 1, maxLength = 100)
    ValidationUtils.validateHostname(hostname, "hostname")
    ValidationUtils.validatePort(port, "port")
    ValidationUtils.validateUsername(username, "username")
    ValidationUtils.validatePort(wrapperPort, "wrapperPort")
    ValidationUtils.validateTimestamp(createdAt, "createdAt", allowFuture = false)
    lastConnectedAt?.let { ValidationUtils.validateTimestamp(it, "lastConnectedAt", allowFuture = false) }
    description?.let { ValidationUtils.validateString(it, "description", maxLength = 500, allowEmpty = true) }
}

/**
 * Extension function to validate a project.
 */
fun Project.validate() {
    ValidationUtils.validateString(name, "name", minLength = 1, maxLength = 100)
    ValidationUtils.validatePath(projectPath, "projectPath", mustBeAbsolute = true)
    ValidationUtils.validateString(scriptsFolder, "scriptsFolder", minLength = 1, maxLength = 100)
    ValidationUtils.validateTimestamp(createdAt, "createdAt", allowFuture = false)
    lastActiveAt?.let { ValidationUtils.validateTimestamp(it, "lastActiveAt", allowFuture = false) }
    description?.let { ValidationUtils.validateString(it, "description", maxLength = 500, allowEmpty = true) }
    repositoryUrl?.let { ValidationUtils.validateUrl(it, "repositoryUrl") }
    claudeSessionId?.let { ValidationUtils.validateUuid(it, "claudeSessionId") }
    lastError?.let { ValidationUtils.validateString(it, "lastError", maxLength = 1000, allowEmpty = true) }
}

/**
 * Extension function to validate a message.
 */
fun Message.validate() {
    ValidationUtils.validateString(content, "content", minLength = 1, maxLength = 100_000)
    ValidationUtils.validateTimestamp(timestamp, "timestamp")
    attachments.forEach { it.validate() }
}

/**
 * Extension function to validate a message attachment.
 */
fun MessageAttachment.validate() {
    ValidationUtils.validateString(name, "name", minLength = 1, maxLength = 255)
    ValidationUtils.validateString(content, "content", maxLength = 1_000_000)
    ValidationUtils.validateLong(size, "size", min = 0)
    mimeType?.let { ValidationUtils.validateString(it, "mimeType", maxLength = 100) }
}

/**
 * Extension function to validate app data.
 */
fun AppData.validate() {
    ValidationUtils.validateInt(version, "version", min = 1)
    ValidationUtils.validateTimestamp(lastModified, "lastModified", allowFuture = false)
    
    // Validate individual entities
    sshIdentities.forEach { it.validate() }
    serverProfiles.forEach { it.validate() }
    projects.forEach { it.validate() }
    messages.values.flatten().forEach { it.validate() }
    
    // Validate uniqueness
    ValidationUtils.validateUnique(sshIdentities, "sshIdentities") { it.name }
    ValidationUtils.validateUnique(serverProfiles, "serverProfiles") { it.name }
    ValidationUtils.validateUnique(projects, "projects") { it.name }
    
    // Validate references
    val identityIds = sshIdentities.map { it.id }.toSet()
    val serverIds = serverProfiles.map { it.id }.toSet()
    val projectIds = projects.map { it.id }.toSet()
    
    serverProfiles.forEach { server ->
        ValidationUtils.validateReference(server.sshIdentityId, sshIdentities, "sshIdentityId") { it.id }
    }
    
    projects.forEach { project ->
        ValidationUtils.validateReference(project.serverProfileId, serverProfiles, "serverProfileId") { it.id }
    }
    
    messages.keys.forEach { projectId ->
        if (projectId !in projectIds) {
            throw ValidationException("messages", projectId, "Messages exist for non-existent project")
        }
    }
}

/**
 * Extension function to validate project settings.
 */
fun ProjectSettings.validate() {
    ValidationUtils.validateInt(maxTurns, "maxTurns", min = 1, max = 1000)
    ValidationUtils.validateInt(maxMessageHistory, "maxMessageHistory", min = 100, max = 10000)
    ValidationUtils.validateList(allowedTools, "allowedTools", maxSize = 100)
    ValidationUtils.validateList(autoApprovePatterns, "autoApprovePatterns", maxSize = 50)
    customPrompts.forEach { (key, value) ->
        ValidationUtils.validateString(key, "customPrompts.key", maxLength = 100)
        ValidationUtils.validateString(value, "customPrompts.value", maxLength = 10000)
    }
}

/**
 * Extension function to validate server connection settings.
 */
fun ServerConnectionSettings.validate() {
    ValidationUtils.validateLong(connectionTimeout, "connectionTimeout", min = 1000, max = 300_000)
    ValidationUtils.validateLong(keepAliveInterval, "keepAliveInterval", min = 1000, max = 600_000)
    ValidationUtils.validateInt(maxRetries, "maxRetries", min = 0, max = 10)
}

/**
 * Extension function to validate user preferences.
 */
fun UserPreferences.validate() {
    ValidationUtils.validateString(language, "language", minLength = 2, maxLength = 10)
    batteryOptimization.validate()
    networkPreferences.validate()
}

/**
 * Extension function to validate battery optimization settings.
 */
fun BatteryOptimization.validate() {
    ValidationUtils.validateInt(lowBatteryThreshold, "lowBatteryThreshold", min = 1, max = 100)
}

/**
 * Extension function to validate network preferences.
 */
fun NetworkPreferences.validate() {
    timeoutSettings.validate()
}

/**
 * Extension function to validate network timeout settings.
 */
fun NetworkTimeoutSettings.validate() {
    ValidationUtils.validateLong(connectionTimeout, "connectionTimeout", min = 1000, max = 300_000)
    ValidationUtils.validateLong(readTimeout, "readTimeout", min = 1000, max = 600_000)
    ValidationUtils.validateLong(writeTimeout, "writeTimeout", min = 1000, max = 600_000)
}