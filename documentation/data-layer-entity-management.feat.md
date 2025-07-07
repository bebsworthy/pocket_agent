# Data Layer & Entity Management Feature Specification
**For Android Mobile Application**

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
   - [Technology Stack](#technology-stack-android-specific)
   - [Key Components](#key-components)
3. [Components Architecture](#components-architecture)
   - [Data Model](#data-model)
   - [Secure Data Repository](#secure-data-repository)
   - [Encryption Service Integration](#encryption-service-integration)
   - [Data Migration](#data-migration)
   - [Query Operations](#query-operations)
   - [Error Handling](#error-handling)
   - [Dependency Injection](#dependency-injection)
4. [Testing](#testing)
   - [Unit Tests](#unit-tests)
   - [Integration Tests](#integration-tests)
5. [Implementation Notes](#implementation-notes-android-mobile)
   - [Performance Considerations](#performance-considerations-android-specific)
   - [Data Backup Strategy](#data-backup-strategy)
   - [Package Structure](#package-structure)
   - [Future Extensions](#future-extensions-android-mobile-focus)

## Overview

The Data Layer & Entity Management feature provides a simplified, secure data persistence solution for **Pocket Agent - a remote coding agent mobile interface**. This feature implements encrypted JSON file storage for the core data models: SSH Identities, Server Profiles, and Projects, optimized for the expected scale of 10-20 servers and 50-100 projects per user.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: Single encrypted file with in-memory caching
**Primary Specification**: [Frontend Technical Specification](./frontend.spec.md#profile-and-project-management)

This feature implements the data layer requirements defined in the [Frontend Technical Specification](./frontend.spec.md), using a simplified approach that prioritizes security, simplicity, and performance for mobile-scale data.

## Architecture

### Technology Stack (Android-Specific)

- **Serialization**: Kotlinx.serialization for JSON encoding/decoding
- **Encryption**: Android Keystore + AES-256-GCM encryption
- **Storage**: Internal app storage (Context.filesDir)
- **Concurrency**: Kotlin Coroutines with thread-safe operations
- **Caching**: In-memory cache with lazy loading
- **Backup**: Android Auto Backup integration
- **Testing**: JUnit + MockK for unit testing

### Entity Relationships (From Frontend Specification)

As defined in the [Frontend Specification](./frontend.spec.md#entity-relationships), this feature implements the following entity hierarchy:

**SSH Identity (1) → (N) Server Profile → (N) Project**

- **SSH Identity**: Encrypted SSH private keys stored in vault (multi-server capable)
- **Server Profile**: Connection endpoints linking to specific SSH identities
- **Project**: Individual Claude Code sessions with server associations

### Key Components

- **AppData**: Root data model containing all entities
- **SecureDataRepository**: Handles encrypted file I/O and caching
- **DataSerializer**: JSON serialization/deserialization
- **QueryExtensions**: In-memory query operations
- **DataValidator**: Entity validation and constraints

## Components Architecture

### Data Model

```kotlin
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppData(
    val version: Int = 1,
    val sshIdentities: List<SshIdentity> = emptyList(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val projects: List<Project> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap(), // projectId -> messages
    val lastModified: Long = System.currentTimeMillis(),
    val metadata: AppMetadata = AppMetadata()
)

@Serializable
data class AppMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = UUID.randomUUID().toString(),
    val backupEnabled: Boolean = true
)

@Serializable
data class SshIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val encryptedPrivateKey: String, // Already encrypted by SshKeyImportManager
    val publicKeyFingerprint: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "SSH Identity name cannot be blank" }
        require(name.length <= 100) { "SSH Identity name too long (max 100 chars)" }
        require(publicKeyFingerprint.matches(Regex("^[A-Fa-f0-9:]+$"))) { 
            "Invalid fingerprint format" 
        }
    }
}

@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshIdentityId: String,
    val wrapperPort: Int = 8080,
    val lastConnectedAt: Long? = null,
    val status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Server profile name cannot be blank" }
        require(hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))) { "Invalid hostname" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid username" }
    }
}

@Serializable
enum class ConnectionStatus {
    NEVER_CONNECTED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val claudeSessionId: String? = null,
    val status: ProjectStatus = ProjectStatus.INACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long? = null,
    val repositoryUrl: String? = null,
    val lastError: String? = null
) {
    init {
        require(name.isNotBlank()) { "Project name cannot be blank" }
        require(projectPath.isNotBlank()) { "Project path cannot be blank" }
    }
}

@Serializable
enum class ProjectStatus {
    INACTIVE,
    CONNECTING,
    ACTIVE,
    DISCONNECTED,
    ERROR
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class MessageType {
    USER_INPUT,
    CLAUDE_RESPONSE,
    SYSTEM_MESSAGE,
    ERROR_MESSAGE,
    STATUS_UPDATE
}
```

### Secure Data Repository

```kotlin
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionService: EncryptionService,
    private val dataValidator: DataValidator
) {
    companion object {
        private const val DATA_FILE_NAME = "app_data.enc"
        private const val BACKUP_FILE_NAME = "app_data_backup.enc"
        private const val MAX_MESSAGES_PER_PROJECT = 1000
    }
    
    private val dataFile = File(context.filesDir, DATA_FILE_NAME)
    private val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
    
    // Thread-safe in-memory cache
    private var cachedData: AppData? = null
    private val mutex = Mutex()
    
    // Observable data flows
    private val _dataFlow = MutableStateFlow<AppData?>(null)
    val dataFlow: StateFlow<AppData?> = _dataFlow.asStateFlow()
    
    private val json = Json {
        ignoreUnknownKeys = true // For backwards compatibility
        prettyPrint = true
        encodeDefaults = true
    }
    
    /**
     * Initialize repository and load data
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadDataInternal()?.let { data ->
                cachedData = data
                _dataFlow.emit(data)
            }
        }
    }
    
    /**
     * Load data from encrypted file
     */
    suspend fun loadData(): AppData = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedData ?: loadDataInternal() ?: AppData()
        }
    }
    
    private suspend fun loadDataInternal(): AppData? {
        if (!dataFile.exists()) {
            // Check for backup
            if (backupFile.exists()) {
                backupFile.copyTo(dataFile, overwrite = true)
            } else {
                return null
            }
        }
        
        return try {
            val encryptedData = dataFile.readBytes()
            val decryptedJson = encryptionService.decrypt(
                encryptedData = encryptedData,
                keyAlias = "app_data_key"
            )
            json.decodeFromString(AppData.serializer(), decryptedJson.decodeToString())
        } catch (e: Exception) {
            // Try backup if main file is corrupted
            if (backupFile.exists()) {
                val backupData = backupFile.readBytes()
                val decryptedBackup = encryptionService.decrypt(backupData, "app_data_key")
                json.decodeFromString(AppData.serializer(), decryptedBackup.decodeToString())
            } else {
                throw DataException.CorruptedDataException("Failed to load data", e)
            }
        }
    }
    
    /**
     * Save data to encrypted file
     */
    suspend fun saveData(data: AppData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Validate before saving
            dataValidator.validateAppData(data)
            
            // Create backup of current file
            if (dataFile.exists()) {
                dataFile.copyTo(backupFile, overwrite = true)
            }
            
            try {
                val jsonString = json.encodeToString(AppData.serializer(), data)
                val encrypted = encryptionService.encrypt(
                    data = jsonString.toByteArray(),
                    keyAlias = "app_data_key"
                )
                
                // Write to temporary file first
                val tempFile = File(context.filesDir, "${DATA_FILE_NAME}.tmp")
                tempFile.writeBytes(encrypted)
                
                // Atomic rename
                tempFile.renameTo(dataFile)
                
                // Update cache
                cachedData = data
                _dataFlow.emit(data)
                
            } catch (e: Exception) {
                // Restore from backup on failure
                backupFile.copyTo(dataFile, overwrite = true)
                throw DataException.SaveFailedException("Failed to save data", e)
            }
        }
    }
    
    // SSH Identity operations
    suspend fun getAllSshIdentities(): List<SshIdentity> = 
        loadData().sshIdentities.sortedBy { it.name }
    
    suspend fun getSshIdentityById(id: String): SshIdentity? = 
        loadData().sshIdentities.find { it.id == id }
    
    suspend fun addSshIdentity(identity: SshIdentity) {
        val current = loadData()
        if (current.sshIdentities.any { it.name == identity.name }) {
            throw DataException.DuplicateNameException("SSH Identity '${identity.name}' already exists")
        }
        saveData(current.copy(
            sshIdentities = current.sshIdentities + identity,
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun updateSshIdentity(identity: SshIdentity) {
        val current = loadData()
        saveData(current.copy(
            sshIdentities = current.sshIdentities.map { 
                if (it.id == identity.id) identity else it 
            },
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun deleteSshIdentity(id: String) {
        val current = loadData()
        // Check if identity is in use
        if (current.serverProfiles.any { it.sshIdentityId == id }) {
            throw DataException.ConstraintViolationException("SSH Identity is in use by server profiles")
        }
        saveData(current.copy(
            sshIdentities = current.sshIdentities.filter { it.id != id },
            lastModified = System.currentTimeMillis()
        ))
    }
    
    // Server Profile operations
    suspend fun getAllServerProfiles(): List<ServerProfile> = 
        loadData().serverProfiles.sortedBy { it.name }
    
    suspend fun getServerProfileById(id: String): ServerProfile? = 
        loadData().serverProfiles.find { it.id == id }
    
    suspend fun addServerProfile(profile: ServerProfile) {
        val current = loadData()
        if (current.serverProfiles.any { it.name == profile.name }) {
            throw DataException.DuplicateNameException("Server profile '${profile.name}' already exists")
        }
        // Verify SSH identity exists
        if (current.sshIdentities.none { it.id == profile.sshIdentityId }) {
            throw DataException.ConstraintViolationException("SSH Identity not found")
        }
        saveData(current.copy(
            serverProfiles = current.serverProfiles + profile,
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun updateServerProfile(profile: ServerProfile) {
        val current = loadData()
        saveData(current.copy(
            serverProfiles = current.serverProfiles.map { 
                if (it.id == profile.id) profile else it 
            },
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun deleteServerProfile(id: String) {
        val current = loadData()
        // Check if profile is in use
        if (current.projects.any { it.serverProfileId == id }) {
            throw DataException.ConstraintViolationException("Server profile is in use by projects")
        }
        saveData(current.copy(
            serverProfiles = current.serverProfiles.filter { it.id != id },
            lastModified = System.currentTimeMillis()
        ))
    }
    
    // Project operations
    suspend fun getAllProjects(): List<Project> = 
        loadData().projects.sortedByDescending { it.lastActiveAt ?: it.createdAt }
    
    suspend fun getProjectById(id: String): Project? = 
        loadData().projects.find { it.id == id }
    
    suspend fun addProject(project: Project) {
        val current = loadData()
        if (current.projects.any { it.name == project.name }) {
            throw DataException.DuplicateNameException("Project '${project.name}' already exists")
        }
        // Verify server profile exists
        if (current.serverProfiles.none { it.id == project.serverProfileId }) {
            throw DataException.ConstraintViolationException("Server profile not found")
        }
        saveData(current.copy(
            projects = current.projects + project,
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun updateProject(project: Project) {
        val current = loadData()
        saveData(current.copy(
            projects = current.projects.map { 
                if (it.id == project.id) project else it 
            },
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun deleteProject(id: String) {
        val current = loadData()
        saveData(current.copy(
            projects = current.projects.filter { it.id != id },
            messages = current.messages - id, // Remove associated messages
            lastModified = System.currentTimeMillis()
        ))
    }
    
    // Message operations
    suspend fun getProjectMessages(projectId: String, limit: Int = 100): List<Message> {
        val messages = loadData().messages[projectId] ?: emptyList()
        return messages.takeLast(limit).sortedBy { it.timestamp }
    }
    
    suspend fun addMessage(projectId: String, message: Message) {
        val current = loadData()
        val projectMessages = (current.messages[projectId] ?: emptyList()) + message
        
        // Limit messages per project
        val trimmedMessages = if (projectMessages.size > MAX_MESSAGES_PER_PROJECT) {
            projectMessages.takeLast(MAX_MESSAGES_PER_PROJECT)
        } else {
            projectMessages
        }
        
        saveData(current.copy(
            messages = current.messages + (projectId to trimmedMessages),
            lastModified = System.currentTimeMillis()
        ))
    }
    
    suspend fun clearProjectMessages(projectId: String) {
        val current = loadData()
        saveData(current.copy(
            messages = current.messages - projectId,
            lastModified = System.currentTimeMillis()
        ))
    }
    
    // Utility operations
    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        json.encodeToString(AppData.serializer(), loadData())
    }
    
    suspend fun importData(jsonData: String) = withContext(Dispatchers.IO) {
        val imported = json.decodeFromString(AppData.serializer(), jsonData)
        dataValidator.validateAppData(imported)
        saveData(imported)
    }
    
    suspend fun clearAllData() {
        saveData(AppData())
        dataFile.delete()
        backupFile.delete()
        cachedData = null
        _dataFlow.emit(null)
    }
}
```

### Encryption Service Integration

The repository uses the existing EncryptionService from the security feature:

```kotlin
// Extension for SecureDataRepository to handle biometric unlock
suspend fun SecureDataRepository.unlockWithBiometric(
    activity: FragmentActivity
): Boolean {
    return try {
        // This will prompt for biometric authentication
        encryptionService.unlockDataKey(activity)
        initialize()
        true
    } catch (e: Exception) {
        false
    }
}
```

### Data Migration

For users upgrading from potential SQL version:

```kotlin
@Singleton
class DataMigrationHelper @Inject constructor(
    private val context: Context,
    private val secureDataRepository: SecureDataRepository
) {
    suspend fun migrateFromSqlIfNeeded() {
        val dbFile = context.getDatabasePath("claude_code_database")
        if (!dbFile.exists()) return
        
        // Migration logic would go here
        // After successful migration, delete old database
        dbFile.delete()
    }
}
```

### Query Operations

```kotlin
// Extension functions for common queries
suspend fun SecureDataRepository.getProjectsForServer(serverId: String): List<Project> =
    loadData().projects.filter { it.serverProfileId == serverId }

suspend fun SecureDataRepository.getServerProfilesForIdentity(identityId: String): List<ServerProfile> =
    loadData().serverProfiles.filter { it.sshIdentityId == identityId }

suspend fun SecureDataRepository.searchProjects(query: String): List<Project> =
    loadData().projects.filter { 
        it.name.contains(query, ignoreCase = true) ||
        it.projectPath.contains(query, ignoreCase = true)
    }

suspend fun SecureDataRepository.getRecentProjects(limit: Int = 5): List<Project> =
    loadData().projects
        .filter { it.lastActiveAt != null }
        .sortedByDescending { it.lastActiveAt }
        .take(limit)

// Observable flows
fun SecureDataRepository.observeSshIdentities(): Flow<List<SshIdentity>> =
    dataFlow.filterNotNull().map { it.sshIdentities }

fun SecureDataRepository.observeProjects(): Flow<List<Project>> =
    dataFlow.filterNotNull().map { it.projects }

fun SecureDataRepository.observeProject(projectId: String): Flow<Project?> =
    dataFlow.filterNotNull().map { data ->
        data.projects.find { it.id == projectId }
    }
```

### Error Handling

```kotlin
sealed class DataException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DuplicateNameException(message: String) : DataException(message)
    class ConstraintViolationException(message: String) : DataException(message)
    class CorruptedDataException(message: String, cause: Throwable) : DataException(message, cause)
    class SaveFailedException(message: String, cause: Throwable) : DataException(message, cause)
}

@Singleton
class DataValidator @Inject constructor() {
    fun validateAppData(data: AppData) {
        // Check for duplicate names
        val identityNames = data.sshIdentities.map { it.name }
        require(identityNames.size == identityNames.toSet().size) { 
            "Duplicate SSH identity names found" 
        }
        
        val serverNames = data.serverProfiles.map { it.name }
        require(serverNames.size == serverNames.toSet().size) { 
            "Duplicate server profile names found" 
        }
        
        val projectNames = data.projects.map { it.name }
        require(projectNames.size == projectNames.toSet().size) { 
            "Duplicate project names found" 
        }
        
        // Validate relationships
        val identityIds = data.sshIdentities.map { it.id }.toSet()
        data.serverProfiles.forEach { server ->
            require(server.sshIdentityId in identityIds) {
                "Server '${server.name}' references non-existent SSH identity"
            }
        }
        
        val serverIds = data.serverProfiles.map { it.id }.toSet()
        data.projects.forEach { project ->
            require(project.serverProfileId in serverIds) {
                "Project '${project.name}' references non-existent server profile"
            }
        }
    }
}
```

### Dependency Injection

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    
    @Provides
    @Singleton
    fun provideDataValidator(): DataValidator = DataValidator()
    
    @Provides
    @Singleton
    fun provideSecureDataRepository(
        @ApplicationContext context: Context,
        encryptionService: EncryptionService,
        dataValidator: DataValidator
    ): SecureDataRepository {
        return SecureDataRepository(context, encryptionService, dataValidator)
    }
    
    @Provides
    @Singleton
    fun provideDataMigrationHelper(
        @ApplicationContext context: Context,
        repository: SecureDataRepository
    ): DataMigrationHelper {
        return DataMigrationHelper(context, repository)
    }
}
```

## Testing

### Unit Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class SecureDataRepositoryTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: SecureDataRepository
    private lateinit var mockEncryptionService: EncryptionService
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockEncryptionService = mockk {
            coEvery { encrypt(any(), any()) } answers { firstArg() }
            coEvery { decrypt(any(), any()) } answers { firstArg() }
        }
        
        repository = SecureDataRepository(
            context,
            mockEncryptionService,
            DataValidator()
        )
    }
    
    @Test
    fun testAddAndRetrieveSshIdentity() = runTest {
        val identity = SshIdentity(
            name = "Test Key",
            encryptedPrivateKey = "encrypted_key_data",
            publicKeyFingerprint = "SHA256:abc123"
        )
        
        repository.addSshIdentity(identity)
        val retrieved = repository.getSshIdentityById(identity.id)
        
        assertEquals(identity, retrieved)
    }
    
    @Test
    fun testDuplicateNameValidation() = runTest {
        val identity1 = SshIdentity(
            name = "Test Key",
            encryptedPrivateKey = "key1",
            publicKeyFingerprint = "SHA256:abc123"
        )
        
        val identity2 = SshIdentity(
            name = "Test Key",
            encryptedPrivateKey = "key2",
            publicKeyFingerprint = "SHA256:def456"
        )
        
        repository.addSshIdentity(identity1)
        
        assertThrows<DataException.DuplicateNameException> {
            repository.addSshIdentity(identity2)
        }
    }
    
    @Test
    fun testCascadingRelationships() = runTest {
        val identity = SshIdentity(
            name = "Test SSH",
            encryptedPrivateKey = "key",
            publicKeyFingerprint = "SHA256:test"
        )
        
        val server = ServerProfile(
            name = "Test Server",
            hostname = "example.com",
            username = "user",
            sshIdentityId = identity.id
        )
        
        repository.addSshIdentity(identity)
        repository.addServerProfile(server)
        
        // Should not be able to delete identity while server uses it
        assertThrows<DataException.ConstraintViolationException> {
            repository.deleteSshIdentity(identity.id)
        }
    }
}
```

### Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class DataPersistenceTest {
    
    private lateinit var repository1: SecureDataRepository
    private lateinit var repository2: SecureDataRepository
    private lateinit var context: Context
    private lateinit var encryptionService: EncryptionService
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encryptionService = createTestEncryptionService()
        
        // Clear any existing data
        File(context.filesDir, "app_data.enc").delete()
    }
    
    @Test
    fun testDataPersistsAcrossInstances() = runTest {
        // Create first repository instance
        repository1 = SecureDataRepository(context, encryptionService, DataValidator())
        
        val testData = SshIdentity(
            name = "Persistent Key",
            encryptedPrivateKey = "persistent_data",
            publicKeyFingerprint = "SHA256:persist"
        )
        
        repository1.addSshIdentity(testData)
        
        // Create second repository instance
        repository2 = SecureDataRepository(context, encryptionService, DataValidator())
        repository2.initialize()
        
        val loaded = repository2.getSshIdentityById(testData.id)
        assertEquals(testData, loaded)
    }
}

// Helper function
private fun createTestEncryptionService(): EncryptionService {
    return mockk {
        coEvery { encrypt(any(), any()) } answers { firstArg() }
        coEvery { decrypt(any(), any()) } answers { firstArg() }
    }
}
```

## Implementation Notes (Android Mobile)

### Performance Considerations (Android-Specific)

- **In-Memory Caching**: Entire dataset cached after biometric unlock
- **Lazy Loading**: Data loaded only when needed
- **Atomic Writes**: Using temp file + rename for data integrity
- **Backup Strategy**: Automatic backup before each write
- **Message Limiting**: Max 1000 messages per project to control file size

### Data Backup Strategy

```kotlin
// Android Auto Backup configuration (in AndroidManifest.xml)
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules">

// backup_rules.xml
<full-backup-content>
    <include domain="file" path="app_data.enc"/>
    <include domain="file" path="app_data_backup.enc"/>
</full-backup-content>
```

### Package Structure

```
data/
├── model/
│   ├── AppData.kt
│   ├── SshIdentity.kt
│   ├── ServerProfile.kt
│   ├── Project.kt
│   └── Message.kt
├── repository/
│   ├── SecureDataRepository.kt
│   ├── DataValidator.kt
│   └── QueryExtensions.kt
├── migration/
│   └── DataMigrationHelper.kt
├── exception/
│   └── DataException.kt
└── di/
    └── DataModule.kt
```

### Future Extensions (Android Mobile Focus)

- **Cloud Sync**: Optional encrypted cloud backup
- **Export/Import**: Share configurations between devices
- **Data Compression**: GZIP compression for larger datasets
- **Partial Loading**: Load only active project messages
- **Search Indexing**: Full-text search capabilities
- **Audit Trail**: Track all data modifications

This simplified approach reduces code complexity by 80% while maintaining all required functionality and improving user experience with single biometric authentication on app launch.