# Data Layer & Entity Management Feature Specification
**For Android Mobile Application**

## Overview

The Data Layer & Entity Management feature provides the foundational data persistence and entity management capabilities for the **Claude Code Android Mobile App**. This feature implements the core data models, database schema, and repository pattern that supports the three primary entities: SSH Identities, Server Profiles, and Projects.

**Target Platform**: Native Android Application (API 26+)
**Development Environment**: Android Studio with Kotlin
**Architecture**: Android Clean Architecture with MVVM pattern

This feature is designed to be implemented independently and serves as the foundation for all other app functionality. All specifications are tailored for Android development best practices and mobile-specific constraints.

## Architecture

### Technology Stack (Android-Specific)

- **Database**: Room Persistence Library v2.6.1+ with SQLite backend (Android's recommended ORM)
- **Dependency Injection**: Hilt for database and repository injection (Android's recommended DI)
- **Coroutines**: Kotlin Coroutines for asynchronous database operations (Android lifecycle-aware)
- **Type Safety**: Kotlin data classes with compile-time null safety (Android's preferred language)
- **Migration**: Room automated and manual migration support (handles Android app updates)
- **Testing**: Room in-memory database for Android unit testing (supports Android Test framework)
- **Memory Management**: Android-optimized LruCache for entity caching
- **Security**: Android Keystore integration for sensitive data protection

### Key Components

- **Entity Classes**: Data models mapped to database tables
- **DAO Interfaces**: Type-safe database access objects
- **Database Class**: Room database configuration and setup
- **Repository Implementation**: Business logic layer over DAOs
- **Migration Strategy**: Database schema evolution support

## Components Architecture

### Database Schema

```kotlin
@Database(
    entities = [
        SshIdentityEntity::class,
        ServerProfileEntity::class,
        ProjectEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class ClaudeCodeDatabase : RoomDatabase() {
    abstract fun sshIdentityDao(): SshIdentityDao
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun projectDao(): ProjectDao
}
```

### Entity Definitions

#### SSH Identity Entity
```kotlin
@Entity(
    tableName = "ssh_identities",
    indices = [Index(value = ["name"], unique = true)]
)
data class SshIdentityEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val keyAlias: String, // Android Keystore alias
    val publicKey: String, // Base64 encoded public key
    val created: Instant,
    val lastUsed: Instant?
)
```

#### Server Profile Entity
```kotlin
@Entity(
    tableName = "server_profiles",
    foreignKeys = [
        ForeignKey(
            entity = SshIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["sshIdentityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["sshIdentityId"])
    ]
)
data class ServerProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val sshIdentityId: String,
    val lastConnected: Instant?,
    val status: ConnectionStatus,
    val created: Instant
)

enum class ConnectionStatus {
    NEVER_CONNECTED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
```

#### Project Entity
```kotlin
@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverProfileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["serverProfileId"])
    ]
)
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String = "scripts",
    val claudeSessionId: String?,
    val status: ProjectStatus,
    val created: Instant,
    val lastActive: Instant?
)

enum class ProjectStatus {
    INACTIVE,
    CONNECTING,
    ACTIVE,
    DISCONNECTED,
    ERROR
}
```

### Data Access Objects (DAOs)

#### SSH Identity DAO
```kotlin
@Dao
interface SshIdentityDao {
    @Query("SELECT * FROM ssh_identities ORDER BY name ASC")
    fun getAllIdentities(): Flow<List<SshIdentityEntity>>
    
    @Query("SELECT * FROM ssh_identities WHERE id = :id")
    suspend fun getIdentityById(id: String): SshIdentityEntity?
    
    @Query("SELECT * FROM ssh_identities WHERE name = :name")
    suspend fun getIdentityByName(name: String): SshIdentityEntity?
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdentity(identity: SshIdentityEntity)
    
    @Update
    suspend fun updateIdentity(identity: SshIdentityEntity)
    
    @Delete
    suspend fun deleteIdentity(identity: SshIdentityEntity)
    
    @Query("UPDATE ssh_identities SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Instant)
}
```

#### Server Profile DAO
```kotlin
@Dao
interface ServerProfileDao {
    @Transaction
    @Query("SELECT * FROM server_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ServerProfileWithIdentity>>
    
    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ServerProfileEntity?
    
    @Query("SELECT * FROM server_profiles WHERE sshIdentityId = :identityId")
    suspend fun getProfilesByIdentity(identityId: String): List<ServerProfileEntity>
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: ServerProfileEntity)
    
    @Update
    suspend fun updateProfile(profile: ServerProfileEntity)
    
    @Delete
    suspend fun deleteProfile(profile: ServerProfileEntity)
    
    @Query("UPDATE server_profiles SET status = :status, lastConnected = :timestamp WHERE id = :id")
    suspend fun updateConnectionStatus(id: String, status: ConnectionStatus, timestamp: Instant?)
}
```

#### Project DAO
```kotlin
@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY lastActive DESC, name ASC")
    fun getAllProjects(): Flow<List<ProjectWithServer>>
    
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?
    
    @Query("SELECT * FROM projects WHERE serverProfileId = :serverProfileId")
    suspend fun getProjectsByServer(serverProfileId: String): List<ProjectEntity>
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity)
    
    @Update
    suspend fun updateProject(project: ProjectEntity)
    
    @Delete
    suspend fun deleteProject(project: ProjectEntity)
    
    @Query("UPDATE projects SET status = :status, lastActive = :timestamp WHERE id = :id")
    suspend fun updateProjectStatus(id: String, status: ProjectStatus, timestamp: Instant)
    
    @Query("UPDATE projects SET claudeSessionId = :sessionId WHERE id = :id")
    suspend fun updateClaudeSession(id: String, sessionId: String?)
}
```

### Relationship Classes

```kotlin
data class ServerProfileWithIdentity(
    @Embedded val serverProfile: ServerProfileEntity,
    @Relation(
        parentColumn = "sshIdentityId",
        entityColumn = "id"
    )
    val sshIdentity: SshIdentityEntity
)

data class ProjectWithServer(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "serverProfileId",
        entityColumn = "id"
    )
    val serverProfile: ServerProfileEntity
)

data class ServerWithProjects(
    @Embedded val serverProfile: ServerProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverProfileId"
    )
    val projects: List<ProjectEntity>
)
```

### Repository Pattern

#### Domain Models
```kotlin
// Domain models (outside of data layer)
data class SshIdentity(
    val id: String,
    val name: String,
    val keyAlias: String,
    val publicKey: String,
    val created: Instant,
    val lastUsed: Instant?
)

data class ServerProfile(
    val id: String,
    val name: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val sshIdentityId: String,
    val lastConnected: Instant?,
    val status: ConnectionStatus,
    val created: Instant
)

data class Project(
    val id: String,
    val name: String,
    val serverProfileId: String,
    val projectPath: String,
    val scriptsFolder: String,
    val claudeSessionId: String?,
    val status: ProjectStatus,
    val created: Instant,
    val lastActive: Instant?
)
```

#### Repository Interfaces
```kotlin
interface SshIdentityRepository {
    fun getAllIdentities(): Flow<List<SshIdentity>>
    suspend fun getIdentityById(id: String): SshIdentity?
    suspend fun getIdentityByName(name: String): SshIdentity?
    suspend fun createIdentity(identity: SshIdentity)
    suspend fun updateIdentity(identity: SshIdentity)
    suspend fun deleteIdentity(identity: SshIdentity)
    suspend fun markIdentityUsed(id: String)
}

interface ServerProfileRepository {
    fun getAllProfiles(): Flow<List<ServerProfileWithIdentity>>
    suspend fun getProfileById(id: String): ServerProfile?
    suspend fun getProfilesByIdentity(identityId: String): List<ServerProfile>
    suspend fun createProfile(profile: ServerProfile)
    suspend fun updateProfile(profile: ServerProfile)
    suspend fun deleteProfile(profile: ServerProfile)
    suspend fun updateConnectionStatus(id: String, status: ConnectionStatus)
}

interface ProjectRepository {
    fun getAllProjects(): Flow<List<ProjectWithServer>>
    suspend fun getProjectById(id: String): Project?
    suspend fun getProjectsByServer(serverProfileId: String): List<Project>
    suspend fun createProject(project: Project)
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(project: Project)
    suspend fun updateProjectStatus(id: String, status: ProjectStatus)
    suspend fun updateClaudeSession(id: String, sessionId: String?)
}
```

#### Repository Implementations
```kotlin
@Singleton
class SshIdentityRepositoryImpl @Inject constructor(
    private val dao: SshIdentityDao
) : SshIdentityRepository {
    
    override fun getAllIdentities(): Flow<List<SshIdentity>> {
        return dao.getAllIdentities().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getIdentityById(id: String): SshIdentity? {
        return dao.getIdentityById(id)?.toDomainModel()
    }
    
    override suspend fun getIdentityByName(name: String): SshIdentity? {
        return dao.getIdentityByName(name)?.toDomainModel()
    }
    
    override suspend fun createIdentity(identity: SshIdentity) {
        validateSshIdentity(identity)
        try {
            dao.insertIdentity(identity.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw DataLayerException.DuplicateNameException("SSH Identity with name '${identity.name}' already exists")
        }
    }
    
    override suspend fun updateIdentity(identity: SshIdentity) {
        validateSshIdentity(identity)
        dao.updateIdentity(identity.toEntity())
    }
    
    override suspend fun deleteIdentity(identity: SshIdentity) {
        dao.deleteIdentity(identity.toEntity())
    }
    
    override suspend fun markIdentityUsed(id: String) {
        dao.updateLastUsed(id, Instant.now())
    }
    
    private fun validateSshIdentity(identity: SshIdentity) {
        require(identity.name.isNotBlank()) { "SSH Identity name cannot be blank" }
        require(identity.name.length <= 100) { "SSH Identity name too long (max 100 characters)" }
        require(identity.keyAlias.isNotBlank()) { "Key alias cannot be blank" }
        require(identity.publicKey.isNotBlank()) { "Public key cannot be blank" }
        require(identity.publicKey.startsWith("ssh-")) { "Invalid SSH public key format" }
    }
}

@Singleton
class ServerProfileRepositoryImpl @Inject constructor(
    private val dao: ServerProfileDao
) : ServerProfileRepository {
    
    override fun getAllProfiles(): Flow<List<ServerProfileWithIdentity>> {
        return dao.getAllProfiles()
    }
    
    override suspend fun getProfileById(id: String): ServerProfile? {
        return dao.getProfileById(id)?.toDomainModel()
    }
    
    override suspend fun getProfilesByIdentity(identityId: String): List<ServerProfile> {
        return dao.getProfilesByIdentity(identityId).map { it.toDomainModel() }
    }
    
    override suspend fun createProfile(profile: ServerProfile) {
        validateServerProfile(profile)
        try {
            dao.insertProfile(profile.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw DataLayerException.DuplicateNameException("Server profile with name '${profile.name}' already exists")
        }
    }
    
    override suspend fun updateProfile(profile: ServerProfile) {
        validateServerProfile(profile)
        dao.updateProfile(profile.toEntity())
    }
    
    override suspend fun deleteProfile(profile: ServerProfile) {
        dao.deleteProfile(profile.toEntity())
    }
    
    override suspend fun updateConnectionStatus(id: String, status: ConnectionStatus) {
        val timestamp = if (status == ConnectionStatus.CONNECTED) Instant.now() else null
        dao.updateConnectionStatus(id, status, timestamp)
    }
    
    private fun validateServerProfile(profile: ServerProfile) {
        require(profile.name.isNotBlank()) { "Server profile name cannot be blank" }
        require(profile.name.length <= 100) { "Server profile name too long (max 100 characters)" }
        require(profile.hostname.isNotBlank()) { "Hostname cannot be blank" }
        require(profile.hostname.matches(Regex("^[a-zA-Z0-9.-]+$"))) { "Invalid hostname format" }
        require(profile.port in 1..65535) { "Port must be between 1 and 65535" }
        require(profile.username.isNotBlank()) { "Username cannot be blank" }
        require(profile.username.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid username format" }
    }
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao
) : ProjectRepository {
    
    override fun getAllProjects(): Flow<List<ProjectWithServer>> {
        return dao.getAllProjects()
    }
    
    override suspend fun getProjectById(id: String): Project? {
        return dao.getProjectById(id)?.toDomainModel()
    }
    
    override suspend fun getProjectsByServer(serverProfileId: String): List<Project> {
        return dao.getProjectsByServer(serverProfileId).map { it.toDomainModel() }
    }
    
    override suspend fun createProject(project: Project) {
        validateProject(project)
        try {
            dao.insertProject(project.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw DataLayerException.DuplicateNameException("Project with name '${project.name}' already exists")
        }
    }
    
    override suspend fun updateProject(project: Project) {
        validateProject(project)
        dao.updateProject(project.toEntity())
    }
    
    override suspend fun deleteProject(project: Project) {
        dao.deleteProject(project.toEntity())
    }
    
    override suspend fun updateProjectStatus(id: String, status: ProjectStatus) {
        dao.updateProjectStatus(id, status, Instant.now())
    }
    
    override suspend fun updateClaudeSession(id: String, sessionId: String?) {
        dao.updateClaudeSession(id, sessionId)
    }
    
    private fun validateProject(project: Project) {
        require(project.name.isNotBlank()) { "Project name cannot be blank" }
        require(project.name.length <= 100) { "Project name too long (max 100 characters)" }
        require(project.projectPath.isNotBlank()) { "Project path cannot be blank" }
        require(project.projectPath.startsWith("/")) { "Project path must be absolute" }
        require(project.scriptsFolder.isNotBlank()) { "Scripts folder cannot be blank" }
        require(!project.scriptsFolder.startsWith("/")) { "Scripts folder must be relative" }
    }
}
```

### Error Handling

```kotlin
sealed class DataLayerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DuplicateNameException(message: String) : DataLayerException(message)
    class EntityNotFoundException(message: String) : DataLayerException(message)
    class ValidationException(message: String) : DataLayerException(message)
    class DatabaseException(message: String, cause: Throwable) : DataLayerException(message, cause)
    class ConstraintViolationException(message: String) : DataLayerException(message)
}

sealed class DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error(val exception: DataLayerException) : DataResult<Nothing>()
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) action(data)
    return this
}

inline fun <T> DataResult<T>.onError(action: (DataLayerException) -> Unit): DataResult<T> {
    if (this is DataResult.Error) action(exception)
    return this
}
```

### Database Converters

```kotlin
class DatabaseConverters {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.epochSecond
    }
    
    @TypeConverter
    fun toInstant(epochSecond: Long?): Instant? {
        return epochSecond?.let { Instant.ofEpochSecond(it) }
    }
    
    @TypeConverter
    fun fromConnectionStatus(status: ConnectionStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toConnectionStatus(status: String): ConnectionStatus {
        return ConnectionStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toProjectStatus(status: String): ProjectStatus {
        return ProjectStatus.valueOf(status)
    }
}
```

### Extension Functions

```kotlin
// Entity to Domain Model conversions
fun SshIdentityEntity.toDomainModel(): SshIdentity {
    return SshIdentity(
        id = id,
        name = name,
        keyAlias = keyAlias,
        publicKey = publicKey,
        created = created,
        lastUsed = lastUsed
    )
}

fun SshIdentity.toEntity(): SshIdentityEntity {
    return SshIdentityEntity(
        id = id,
        name = name,
        keyAlias = keyAlias,
        publicKey = publicKey,
        created = created,
        lastUsed = lastUsed
    )
}

fun ServerProfileEntity.toDomainModel(): ServerProfile {
    return ServerProfile(
        id = id,
        name = name,
        hostname = hostname,
        port = port,
        username = username,
        sshIdentityId = sshIdentityId,
        lastConnected = lastConnected,
        status = status,
        created = created
    )
}

fun ServerProfile.toEntity(): ServerProfileEntity {
    return ServerProfileEntity(
        id = id,
        name = name,
        hostname = hostname,
        port = port,
        username = username,
        sshIdentityId = sshIdentityId,
        lastConnected = lastConnected,
        status = status,
        created = created
    )
}

fun ProjectEntity.toDomainModel(): Project {
    return Project(
        id = id,
        name = name,
        serverProfileId = serverProfileId,
        projectPath = projectPath,
        scriptsFolder = scriptsFolder,
        claudeSessionId = claudeSessionId,
        status = status,
        created = created,
        lastActive = lastActive
    )
}

fun Project.toEntity(): ProjectEntity {
    return ProjectEntity(
        id = id,
        name = name,
        serverProfileId = serverProfileId,
        projectPath = projectPath,
        scriptsFolder = scriptsFolder,
        claudeSessionId = claudeSessionId,
        status = status,
        created = created,
        lastActive = lastActive
    )
}
```

### Dependency Injection Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideClaudeCodeDatabase(@ApplicationContext context: Context): ClaudeCodeDatabase {
        return Room.databaseBuilder(
            context,
            ClaudeCodeDatabase::class.java,
            "claude_code_database"
        )
        .addTypeConverter(DatabaseConverters())
        .addCallback(DatabaseInitializationCallback())
        .fallbackToDestructiveMigration() // TODO: Implement proper migrations
        .build()
    }
    
    @Provides
    @Singleton
    fun provideDatabaseInitializationCallback(): DatabaseInitializationCallback {
        return DatabaseInitializationCallback()
    }
    
    @Provides
    fun provideSshIdentityDao(database: ClaudeCodeDatabase): SshIdentityDao {
        return database.sshIdentityDao()
    }
    
    @Provides
    fun provideServerProfileDao(database: ClaudeCodeDatabase): ServerProfileDao {
        return database.serverProfileDao()
    }
    
    @Provides
    fun provideProjectDao(database: ClaudeCodeDatabase): ProjectDao {
        return database.projectDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindSshIdentityRepository(
        repositoryImpl: SshIdentityRepositoryImpl
    ): SshIdentityRepository
    
    @Binds
    abstract fun bindServerProfileRepository(
        repositoryImpl: ServerProfileRepositoryImpl
    ): ServerProfileRepository
    
    @Binds
    abstract fun bindProjectRepository(
        repositoryImpl: ProjectRepositoryImpl
    ): ProjectRepository
}
```

### Database Initialization

```kotlin
class DatabaseInitializationCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Database is created - any initialization logic can go here
    }
    
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Database is opened - can be used for pragma settings
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

// Optional: Database seeding for development/testing
class DatabaseSeeder @Inject constructor(
    private val sshIdentityRepository: SshIdentityRepository,
    private val serverProfileRepository: ServerProfileRepository,
    private val projectRepository: ProjectRepository
) {
    suspend fun seedDevelopmentData() {
        // Example development data - remove in production
        val devIdentity = SshIdentity(
            id = "dev-identity-1",
            name = "Development Key",
            keyAlias = "dev_key_alias",
            publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...",
            created = Instant.now(),
            lastUsed = null
        )
        
        try {
            sshIdentityRepository.createIdentity(devIdentity)
        } catch (e: DataLayerException.DuplicateNameException) {
            // Identity already exists, skip seeding
        }
    }
}
```

## Testing

### Unit Tests for DAOs
```kotlin
@RunWith(AndroidJUnit4::class)
class SshIdentityDaoTest {
    
    private lateinit var database: ClaudeCodeDatabase
    private lateinit var dao: SshIdentityDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ClaudeCodeDatabase::class.java
        ).allowMainThreadQueries().build()
        
        dao = database.sshIdentityDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertAndRetrieveIdentity() = runTest {
        val identity = SshIdentityEntity(
            name = "Test Identity",
            keyAlias = "test_key",
            publicKey = "ssh-rsa AAAAB3...",
            created = Instant.now()
        )
        
        dao.insertIdentity(identity)
        val retrieved = dao.getIdentityById(identity.id)
        
        assertThat(retrieved).isEqualTo(identity)
    }
}
```

### Repository Tests
```kotlin
@RunWith(AndroidJUnit4::class)
class SshIdentityRepositoryTest {
    
    @Mock
    private lateinit var dao: SshIdentityDao
    
    private lateinit var repository: SshIdentityRepository
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = SshIdentityRepositoryImpl(dao)
    }
    
    @Test
    fun getAllIdentities_returnsFlowOfDomainModels() = runTest {
        val entities = listOf(
            SshIdentityEntity(
                name = "Test",
                keyAlias = "test",
                publicKey = "key",
                created = Instant.now()
            )
        )
        
        whenever(dao.getAllIdentities()).thenReturn(flowOf(entities))
        
        val result = repository.getAllIdentities().first()
        
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Test")
    }
}
```

## Implementation Notes (Android Mobile)

### Database Migration Strategy (Android App Updates)
- Start with version 1 schema
- Use Room's automatic migration detection for Android app updates
- Plan manual migrations for complex schema changes during app releases
- Export schema to version control for migration planning across app versions
- **Mobile Consideration**: Handle migrations gracefully to avoid app crashes during updates

### Performance Considerations (Android-Specific)
- Use Flow for reactive data observation (integrates with Compose UI)
- Implement proper indexing on frequently queried columns
- Use @Transaction for complex multi-table operations
- **Mobile Optimization**: Consider pagination for large datasets to reduce memory usage
- **Battery Optimization**: Minimize database queries during background operations
- **Storage Optimization**: Use Room's built-in SQLite optimizations for mobile storage

### Error Handling
- Use Result pattern for repository methods that can fail
- Implement proper constraint violation handling
- Log database errors for debugging
- Provide meaningful error messages to upper layers

### Package Structure

```
data/
├── database/
│   ├── ClaudeCodeDatabase.kt
│   ├── DatabaseConverters.kt
│   ├── DatabaseInitializationCallback.kt
│   └── migrations/
│       └── DatabaseMigrations.kt
├── entities/
│   ├── SshIdentityEntity.kt
│   ├── ServerProfileEntity.kt
│   ├── ProjectEntity.kt
│   └── RelationshipEntities.kt
├── daos/
│   ├── SshIdentityDao.kt
│   ├── ServerProfileDao.kt
│   └── ProjectDao.kt
├── repositories/
│   ├── SshIdentityRepositoryImpl.kt
│   ├── ServerProfileRepositoryImpl.kt
│   └── ProjectRepositoryImpl.kt
├── mappers/
│   └── EntityMappers.kt
├── exceptions/
│   └── DataLayerException.kt
└── di/
    ├── DatabaseModule.kt
    └── RepositoryModule.kt
```

### Test Fixtures

```kotlin
object TestDataFactory {
    fun createSshIdentity(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Identity",
        keyAlias: String = "test_key_${Random.nextInt()}",
        publicKey: String = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...",
        created: Instant = Instant.now(),
        lastUsed: Instant? = null
    ): SshIdentity {
        return SshIdentity(id, name, keyAlias, publicKey, created, lastUsed)
    }
    
    fun createServerProfile(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Server",
        hostname: String = "test.example.com",
        port: Int = 22,
        username: String = "testuser",
        sshIdentityId: String,
        lastConnected: Instant? = null,
        status: ConnectionStatus = ConnectionStatus.NEVER_CONNECTED,
        created: Instant = Instant.now()
    ): ServerProfile {
        return ServerProfile(id, name, hostname, port, username, sshIdentityId, lastConnected, status, created)
    }
    
    fun createProject(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Project",
        serverProfileId: String,
        projectPath: String = "/home/user/projects/test",
        scriptsFolder: String = "scripts",
        claudeSessionId: String? = null,
        status: ProjectStatus = ProjectStatus.INACTIVE,
        created: Instant = Instant.now(),
        lastActive: Instant? = null
    ): Project {
        return Project(id, name, serverProfileId, projectPath, scriptsFolder, claudeSessionId, status, created, lastActive)
    }
}
```

### Android Mobile Performance Optimization

```kotlin
import android.util.LruCache

// Android-specific caching using LruCache
class AndroidCachedSshIdentityRepository @Inject constructor(
    private val delegate: SshIdentityRepositoryImpl
) : SshIdentityRepository by delegate {
    
    // Android LruCache - automatically handles memory pressure
    private val memoryCache = LruCache<String, SshIdentity>(50) // Cache up to 50 identities
    
    override suspend fun getIdentityById(id: String): SshIdentity? {
        // Check memory cache first
        memoryCache.get(id)?.let { return it }
        
        // Fetch from database and cache
        return delegate.getIdentityById(id)?.also { identity ->
            memoryCache.put(id, identity)
        }
    }
    
    override suspend fun updateIdentity(identity: SshIdentity) {
        delegate.updateIdentity(identity)
        memoryCache.remove(identity.id) // Invalidate cache
    }
}

// Android mobile query optimization
interface AndroidOptimizedQueries {
    // Use LIMIT for RecyclerView pagination
    // Implement search with SQLite FTS for mobile keyboards
    // Use lazy loading for mobile scrolling patterns
    // Consider view models for lifecycle-aware caching
}
```

### Thread Safety & Concurrency

```kotlin
// Room guarantees thread safety for DAOs
// Repositories inherit this safety
// Flow operations are thread-safe by design
// Repository implementations are stateless and thread-safe

// Example of concurrent operation handling
class ThreadSafeOperations {
    // Room handles concurrent database access
    // Flow collectors can be on different threads
    // Suspend functions are safe for concurrent calls
    // No additional synchronization needed
}
```

### Future Extensions (Android Mobile Focus)
- **Security**: Add database encryption using SQLCipher for sensitive SSH data
- **Sync**: Implement cloud backup/restore via Android backup service
- **Performance**: Add LruCache for frequently accessed entities
- **Search**: Implement SQLite FTS for mobile keyboard search
- **Offline**: Add soft delete functionality for offline operation
- **Audit**: Add audit trail tables for security tracking
- **Storage**: Consider Android storage optimization (App Bundle, dynamic delivery)
- **Multi-User**: Support Android work profiles and multi-user scenarios