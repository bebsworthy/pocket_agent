# SSH Identity Service Layer

This directory contains the comprehensive SSH Identity CRUD service implementation for the Pocket Agent mobile application.

## Overview

The SSH Identity Service provides a complete solution for managing SSH identities in the mobile app, including:

- **Complete CRUD Operations**: Create, read, update, delete SSH identities
- **SSH Key Management**: Import, export, generate, and validate SSH keys
- **Format Support**: OpenSSH, PEM, PKCS#8, and basic PuTTY format detection
- **Secure Storage**: AES-256-GCM encryption with Android Keystore integration
- **Search & Filtering**: Advanced search and filtering capabilities
- **Usage Tracking**: Monitor SSH identity usage across server profiles and projects
- **Comprehensive Validation**: Field-level and business rule validation

## Architecture

### Core Components

#### SshIdentityService
The main service class that provides high-level SSH identity management operations.

**Key Features:**
- CRUD operations with validation
- SSH key import/export
- Key generation and validation
- Search and filtering
- Usage statistics and tracking
- Observable flows for reactive UI updates

**Usage Example:**
```kotlin
@Inject
lateinit var sshIdentityService: SshIdentityService

// Create a new SSH identity
val result = sshIdentityService.createSshIdentity(
    name = "Production Server Key",
    privateKeyData = pemKeyData,
    format = SshKeyFormat.PEM,
    description = "Key for production server access"
)

// Search SSH identities
val searchResults = sshIdentityService.searchSshIdentities("production")

// Get usage statistics
val usageStats = sshIdentityService.getUsageStatistics()
```

#### SshKeyParser
Handles SSH key parsing, format detection, and conversion.

**Supported Formats:**
- OpenSSH private key format
- PEM format (RSA, DSA, EC)
- PKCS#8 format
- PuTTY format (detection only)

**Key Features:**
- Automatic format detection
- Key pair generation (RSA, DSA, ECDSA)
- Public key extraction and formatting
- SSH wire format encoding

**Usage Example:**
```kotlin
val parser = SshKeyParser()

// Parse a private key
val keyPair = parser.parsePrivateKey(keyData, SshKeyFormat.AUTO_DETECT)

// Generate a new key pair
val newKeyPair = parser.generateKeyPair(SshKeyType.RSA, 2048)

// Format public key
val opensshPublicKey = parser.formatPublicKey(keyPair.public, SshKeyFormat.OPENSSH)
```

#### SshKeyEncryption
Provides secure encryption and decryption of SSH private keys.

**Security Features:**
- AES-256-GCM encryption
- Android Keystore integration
- Secure key derivation
- Master key rotation support
- Biometric authentication ready

**Usage Example:**
```kotlin
val encryption = SshKeyEncryption()

// Encrypt a private key
val encryptedKey = encryption.encryptPrivateKey(privateKey)

// Decrypt a private key
val decryptedKey = encryption.decryptPrivateKey(encryptedKey)

// Check master key availability
val keyAvailable = encryption.isMasterKeyAvailable()
```

## Service Operations

### CRUD Operations

#### Create SSH Identity
```kotlin
suspend fun createSshIdentity(
    name: String,
    privateKeyData: String,
    keyFormat: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
    passphrase: String? = null,
    description: String? = null
): ServiceResult<SshIdentity>
```

**Features:**
- Automatic key format detection
- Passphrase support for encrypted keys
- Duplicate fingerprint detection
- Comprehensive validation
- Secure key encryption

#### Read SSH Identity
```kotlin
suspend fun getSshIdentity(id: String): ServiceResult<SshIdentity>
```

#### Update SSH Identity
```kotlin
suspend fun updateSshIdentity(
    id: String,
    name: String? = null,
    description: String? = null
): ServiceResult<SshIdentity>
```

**Features:**
- Partial updates supported
- Name uniqueness validation
- Business rule validation

#### Delete SSH Identity
```kotlin
suspend fun deleteSshIdentity(id: String): ServiceResult<Unit>
```

**Features:**
- Dependency checking (server profiles)
- Safe deletion with user feedback

### Search and Filtering

#### Search by Multiple Criteria
```kotlin
suspend fun searchSshIdentities(
    query: String,
    limit: Int = DEFAULT_SEARCH_LIMIT
): ServiceResult<List<SshIdentity>>
```

**Search Fields:**
- SSH identity name
- Description
- Public key fingerprint (full and short)

#### Advanced Filtering
```kotlin
suspend fun filterSshIdentities(
    criteria: SshIdentityFilterCriteria
): ServiceResult<List<SshIdentity>>
```

**Filter Options:**
- Date range (created after/before)
- Recently used only
- Unused identities only
- Name pattern matching (regex)

### Key Management

#### Import SSH Key
```kotlin
suspend fun importSshKey(
    name: String,
    keyData: String,
    format: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
    passphrase: String? = null,
    description: String? = null
): ServiceResult<SshIdentity>
```

#### Export SSH Key
```kotlin
suspend fun exportSshKey(
    id: String,
    format: SshKeyFormat = SshKeyFormat.OPENSSH,
    includePrivateKey: Boolean = false
): ServiceResult<String>
```

#### Generate SSH Key
```kotlin
suspend fun generateSshKey(
    name: String,
    keyType: SshKeyType = SshKeyType.RSA,
    keySize: Int = 2048,
    description: String? = null
): ServiceResult<SshIdentity>
```

#### Validate SSH Key
```kotlin
suspend fun validateSshKey(
    keyData: String,
    format: SshKeyFormat = SshKeyFormat.AUTO_DETECT,
    passphrase: String? = null
): ServiceResult<SshKeyInfo>
```

### Usage Tracking

#### Mark as Used
```kotlin
suspend fun markAsUsed(id: String): ServiceResult<Unit>
```

#### Get Usage Statistics
```kotlin
suspend fun getUsageStatistics(
    identityIds: List<String>? = null
): Map<String, SshIdentityUsageStats>
```

**Statistics Include:**
- Server profile count
- Project count
- Last connection timestamp
- Recent connections (last 7 days)

### Observable Flows

#### Observe SSH Identities
```kotlin
fun observeSshIdentities(): Flow<List<SshIdentity>>
```

#### Observe with Usage Statistics
```kotlin
fun observeSshIdentitiesWithUsage(): Flow<List<SshIdentityWithUsage>>
```

## Data Types

### Enums

#### SshKeyFormat
```kotlin
enum class SshKeyFormat {
    OPENSSH,        // OpenSSH format
    PEM,            // PEM format
    PKCS8,          // PKCS#8 format
    PUTTY,          // PuTTY format
    AUTO_DETECT     // Automatic detection
}
```

#### SshKeyType
```kotlin
enum class SshKeyType {
    RSA,
    DSA,
    ECDSA,
    ED25519,
    UNKNOWN
}
```

#### SshIdentitySortBy
```kotlin
enum class SshIdentitySortBy {
    NAME,
    CREATED_DATE,
    LAST_USED,
    USAGE_COUNT
}
```

### Data Classes

#### SshIdentityFilterCriteria
```kotlin
data class SshIdentityFilterCriteria(
    val createdAfter: Long? = null,
    val createdBefore: Long? = null,
    val recentlyUsedOnly: Boolean = false,
    val unusedOnly: Boolean = false,
    val namePattern: String? = null
)
```

#### SshKeyInfo
```kotlin
data class SshKeyInfo(
    val keyType: SshKeyType,
    val keySize: Int,
    val fingerprint: String,
    val isEncrypted: Boolean,
    val format: SshKeyFormat
)
```

#### SshIdentityUsageStats
```kotlin
data class SshIdentityUsageStats(
    val serverProfileCount: Int,
    val projectCount: Int,
    val lastConnectionAt: Long?,
    val recentConnections: Int
)
```

### Result Types

#### ServiceResult
```kotlin
sealed class ServiceResult<out T> {
    data class Success<T>(val data: T) : ServiceResult<T>()
    data class Failure(val error: String, val exception: Throwable? = null) : ServiceResult<Nothing>()
    
    val isSuccess: Boolean
    val isFailure: Boolean
    
    fun getOrNull(): T?
    fun getErrorOrNull(): String?
}
```

## Security Considerations

### Encryption
- **Algorithm**: AES-256-GCM for symmetric encryption
- **Key Management**: Android Keystore for master key protection
- **IV Generation**: Cryptographically secure random IVs
- **Authentication**: GCM provides built-in authentication

### Key Storage
- Private keys are never stored in plaintext
- Master encryption key is protected by Android Keystore
- Biometric authentication can be enabled for key access
- Secure memory wiping for sensitive data

### Validation
- Comprehensive input validation
- Business rule enforcement
- Fingerprint uniqueness verification
- Safe deletion with dependency checking

## Error Handling

The service uses a comprehensive error handling strategy:

### ServiceResult Pattern
All service operations return `ServiceResult<T>` which encapsulates:
- Success with data
- Failure with error message and optional exception

### Exception Types
- `SecurityException`: Encryption/decryption failures
- `IllegalArgumentException`: Invalid input parameters
- `DataException`: Repository-level errors
- `ValidationException`: Validation failures

### Error Recovery
- Automatic retry for transient failures
- Graceful degradation for non-critical operations
- User-friendly error messages
- Detailed logging for debugging

## Testing

### Unit Tests
Comprehensive unit test coverage including:

#### SshIdentityServiceTest
- CRUD operations (success and failure cases)
- Search and filtering functionality
- Key management operations
- Usage tracking
- Error handling scenarios
- Observable flow testing

#### SshKeyParserTest
- Format detection for all supported formats
- Key parsing and validation
- Key generation for different types
- Format conversion and encoding
- Error handling for malformed data

#### SshKeyEncryptionTest
- Encryption/decryption operations
- Master key management
- Key rotation scenarios
- Security utilities
- Android Keystore integration

### Test Utilities
Helper classes and methods for creating test data:
- `SshIdentityServiceTestUtils`
- `SshKeyParserTestUtils`
- `SshKeyEncryptionTestUtils`

## Integration

### Dependency Injection
The service layer is fully integrated with Dagger Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideSshIdentityService(
        repository: SecureDataRepository,
        validator: SshIdentityValidator,
        sshKeyParser: SshKeyParser,
        sshKeyEncryption: SshKeyEncryption
    ): SshIdentityService
}
```

### Repository Integration
The service layer integrates with the existing repository pattern:
- `SecureDataRepository` for data persistence
- `SshIdentityValidator` for validation
- Encrypted JSON storage backend

### Validation Framework
Leverages the existing validation framework:
- Field-level validation
- Business rule validation
- Cross-entity validation
- Async validation support

## Performance Considerations

### Caching
- Repository-level caching for data access
- In-memory validation cache
- Lazy loading of usage statistics

### Concurrency
- Thread-safe operations with proper synchronization
- Coroutine-based async operations
- Background processing for heavy operations

### Memory Management
- Secure memory wiping for sensitive data
- Efficient data structures
- Proper resource cleanup

## Future Enhancements

### Planned Features
- Complete OpenSSH private key format parsing
- Full PuTTY format support
- Ed25519 key generation (with BouncyCastle)
- Hardware security module integration
- Key attestation support

### Extensibility
The service architecture supports easy extension:
- New key formats can be added to `SshKeyParser`
- Additional encryption algorithms in `SshKeyEncryption`
- Extended search capabilities in `SshIdentityService`
- Custom validation rules in the validation framework

## Best Practices

### Usage Guidelines
1. Always handle `ServiceResult` return values
2. Use appropriate error handling for UI feedback
3. Leverage observable flows for reactive UI updates
4. Validate user input before service calls
5. Use dependency injection for service access

### Security Guidelines
1. Never log sensitive key material
2. Use secure memory wiping for temporary data
3. Validate all user inputs
4. Follow principle of least privilege
5. Regular security audits and updates

This SSH Identity Service implementation provides a robust, secure, and comprehensive solution for SSH key management in the Pocket Agent mobile application.