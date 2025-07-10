# Data Migration System

This document provides comprehensive documentation for the Pocket Agent data migration system, which provides robust, versioned data migrations with backup and rollback capabilities.

## Overview

The data migration system provides:

- **Versioned Migrations**: Support for structured data migrations between versions
- **Automatic Migration Detection**: Automatically detects when migration is needed
- **Backup Creation**: Creates backups before migration for rollback support
- **Progress Tracking**: Real-time progress reporting during migration operations
- **Data Integrity Validation**: Validates data before and after migration
- **Rollback Support**: Ability to rollback migrations using backups
- **Error Handling**: Comprehensive error handling and recovery mechanisms
- **Integration**: Seamless integration with the encrypted storage system

## Architecture

### Core Components

1. **MigrationVersion**: Represents migration version information
2. **DataMigration**: Interface for implementing individual migrations
3. **MigrationRegistry**: Manages available migrations and migration paths
4. **DataMigrationManager**: Coordinates migration execution and management
5. **MigrationHelper**: High-level interface for common migration operations

### Migration Flow

```
1. Load current data
2. Detect version and check if migration is needed
3. Find migration path from current to target version
4. Create backup (if enabled)
5. Execute migrations in sequence
6. Validate migrated data
7. Save migrated data
8. Log migration result
```

## Usage

### Basic Repository Initialization with Migration

```kotlin
@Inject
lateinit var migrationHelper: MigrationHelper

@Inject
lateinit var repository: SecureDataRepository

// Initialize repository with automatic migration
val summary = migrationHelper.initializeRepositoryWithMigration(repository) { progress ->
    // Update UI with migration progress
    updateProgressUI(progress)
}

if (summary.success) {
    if (summary.migrationPerformed) {
        Log.i("App", "Migration completed: ${summary.message}")
    } else {
        Log.i("App", "No migration needed")
    }
} else {
    Log.e("App", "Migration failed: ${summary.error}")
}
```

### Manual Migration Management

```kotlin
@Inject
lateinit var migrationManager: DataMigrationManager

// Check if migration is needed
val currentData = repository.loadData()
val migrationNeeded = migrationManager.isMigrationNeeded(currentData)

if (migrationNeeded) {
    // Perform migration
    val result = migrationManager.migrateToLatest(
        data = currentData,
        createBackup = true
    )
    
    if (result.success) {
        Log.i("App", "Migration successful")
    } else {
        Log.e("App", "Migration failed: ${result.message}")
    }
}
```

### Data Health Check and Repair

```kotlin
// Perform data health check
val healthResult = migrationHelper.performDataHealthCheck(repository)

if (!healthResult.isHealthy) {
    Log.w("App", "Data issues found: ${healthResult.issuesFound}")
    
    if (healthResult.repairPerformed) {
        Log.i("App", "Repaired ${healthResult.issuesRepaired} issues")
    }
    
    if (healthResult.unrepairedIssues.isNotEmpty()) {
        Log.e("App", "Unrepaired issues: ${healthResult.unrepairedIssues}")
    }
}
```

### Creating and Restoring Backups

```kotlin
// Create manual backup
val backupResult = migrationHelper.createDataBackup(
    repository = repository,
    description = "Before major update"
)

if (backupResult.success) {
    Log.i("App", "Backup created: ${backupResult.backupFilename}")
}

// List available backups
val backups = migrationHelper.listAvailableBackups()
Log.i("App", "Available backups: $backups")

// Restore from backup
val restoreResult = repository.restoreFromMigrationBackup(
    migrationManager = migrationManager,
    backupFilename = backups.first()
)
```

### Observing Migration Progress

```kotlin
// Observe migration progress
migrationHelper.observeMigrationProgress().collect { progress ->
    progress?.let {
        updateProgressBar(it.progressPercent)
        updateStatusText(it.stepDescription)
    }
}

// Check if migration is in progress
migrationHelper.isMigrationInProgress().collect { inProgress ->
    setUIEnabled(!inProgress)
}
```

## Implementing Custom Migrations

### Creating a Migration

```kotlin
@Singleton
class MyCustomMigration @Inject constructor() : BaseDataMigration() {
    
    override val fromVersion: Int = 1
    override val toVersion: Int = 2
    override val name: String = "Add User Preferences"
    override val description: String = "Adds user preferences to the data structure"
    override val isReversible: Boolean = true
    
    override suspend fun migrate(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        validateDataVersion(data)
        
        reportProgress(1, 3, "Adding preference structure", progressCallback)
        
        // Perform migration logic here
        val migratedData = data.copy(
            // Add new fields or modify existing ones
        )
        
        reportProgress(2, 3, "Validating migrated data", progressCallback)
        
        // Additional validation
        
        reportProgress(3, 3, "Migration completed", progressCallback)
        
        return updateDataVersion(migratedData)
    }
    
    override suspend fun canMigrate(data: AppData): Boolean {
        return data.version == fromVersion
    }
    
    override suspend fun rollback(
        data: AppData,
        progressCallback: ((MigrationProgress) -> Unit)?
    ): AppData {
        // Implement rollback logic if isReversible = true
        return data.copy(
            version = fromVersion,
            lastModified = System.currentTimeMillis()
        )
    }
    
    override suspend fun getEstimatedSteps(data: AppData): Int = 3
}
```

### Registering Migrations

Migrations are automatically registered through dependency injection:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyMigrationModule {
    
    @Provides
    @IntoSet
    fun provideMyCustomMigration(
        myCustomMigration: MyCustomMigration
    ): DataMigration = myCustomMigration
}
```

## Configuration

### Migration Configuration

```kotlin
val config = MigrationConfiguration(
    autoMigrationEnabled = true,
    backupBeforeMigration = true,
    maxBackupFiles = 5,
    migrationTimeoutMs = 30_000L,
    enableProgressReporting = true,
    validateAfterMigration = true
)

// Use configuration during initialization
repository.initializeWithMigration(migrationManager, config)
```

### Environment-Specific Configurations

```kotlin
// Production configuration
val prodConfig = MigrationConfiguration.default()

// Development configuration
val devConfig = MigrationConfiguration.development()

// Testing configuration
val testConfig = MigrationConfiguration.testing()
```

## Error Handling

### Migration Exceptions

The system defines several exception types:

- `MigrationException.InvalidVersionException`: Invalid data version
- `MigrationException.ValidationException`: Data validation failed
- `MigrationException.ExecutionException`: Migration execution failed
- `MigrationException.RollbackNotSupportedException`: Rollback not supported
- `MigrationException.RollbackException`: Rollback operation failed
- `MigrationException.MigrationNotFoundException`: Required migration not found
- `MigrationException.CorruptedDataException`: Data corruption detected

### Handling Migration Failures

```kotlin
try {
    val result = migrationManager.migrateToLatest(data)
    if (!result.success) {
        // Handle migration failure
        when (val exception = result.exception) {
            is MigrationException.ValidationException -> {
                // Data validation failed
                showUserError("Data validation failed: ${exception.message}")
            }
            is MigrationException.ExecutionException -> {
                // Migration execution failed
                showUserError("Migration failed: ${exception.message}")
                // Consider rollback
                if (result.backupCreated) {
                    migrationManager.rollbackLastMigration(data, result.backupFilename)
                }
            }
            else -> {
                // General error handling
                showUserError("Migration failed: ${result.message}")
            }
        }
    }
} catch (e: Exception) {
    Log.e("App", "Unexpected migration error", e)
    showUserError("An unexpected error occurred during migration")
}
```

## Testing

### Unit Testing Migrations

```kotlin
@Test
fun testMyCustomMigration() = runTest {
    val migration = MyCustomMigration()
    
    val testData = AppData(version = 1)
    assertTrue(migration.canMigrate(testData))
    
    val migratedData = migration.migrate(testData)
    assertEquals(2, migratedData.version)
    assertTrue(migration.validateMigration(testData, migratedData))
}
```

### Integration Testing

```kotlin
@Test
fun testMigrationIntegration() = runTest {
    // Setup test repository and migration manager
    val repository = createTestRepository()
    val migrationManager = createTestMigrationManager()
    
    // Test full migration flow
    val result = repository.initializeWithMigration(migrationManager)
    assertNotNull(result)
    assertTrue(result!!.success)
}
```

## Performance Considerations

### Memory Usage

- Migrations operate on in-memory data copies
- Large datasets may require streaming or chunked processing
- Consider implementing progress reporting for long-running migrations

### Storage Impact

- Backups increase storage usage
- Configure `maxBackupFiles` to control backup retention
- Old backups are automatically cleaned up

### Migration Time

- Set appropriate `migrationTimeoutMs` values
- Complex migrations may need longer timeouts
- Consider splitting large migrations into smaller steps

## Security Considerations

### Data Protection

- All data remains encrypted during migration
- Backups are encrypted using the same encryption as primary data
- Migration logs do not contain sensitive data

### Access Control

- Migration operations require the same security context as data access
- Biometric authentication is maintained throughout migration
- No plaintext data is written to logs or temporary files

## Troubleshooting

### Common Issues

1. **Migration Timeout**
   - Increase `migrationTimeoutMs` in configuration
   - Split large migrations into smaller steps
   - Check for blocking operations in migration code

2. **Backup Creation Failed**
   - Check available storage space
   - Verify encryption service is working
   - Check file permissions

3. **Data Validation Failed**
   - Review migration logic for data integrity
   - Check entity relationships
   - Verify required fields are populated

4. **Migration Path Not Found**
   - Ensure all required migrations are registered
   - Check migration version sequence
   - Verify migration registry configuration

### Debugging

Enable detailed logging by adding this to your migration:

```kotlin
override suspend fun migrate(
    data: AppData,
    progressCallback: ((MigrationProgress) -> Unit)?
): AppData {
    Log.d("Migration", "Starting migration: $name")
    Log.d("Migration", "Input data: ${data.getTotalEntityCount()} entities")
    
    // Migration logic here
    
    Log.d("Migration", "Migration completed successfully")
    return migratedData
}
```

## Best Practices

1. **Always test migrations thoroughly** before releasing
2. **Keep migrations small and focused** on specific changes
3. **Provide clear progress reporting** for user experience
4. **Document migration purpose and changes** in detail
5. **Handle edge cases and data corruption** gracefully
6. **Use validation to ensure data integrity** before and after migration
7. **Create reversible migrations** when possible for rollback support
8. **Monitor migration performance** and optimize as needed
9. **Plan migration sequences** to avoid conflicts
10. **Test rollback procedures** to ensure they work correctly

## Future Enhancements

The migration system is designed to be extensible. Potential future enhancements include:

- **Partial migrations**: Migrate only specific data subsets
- **Cloud backup integration**: Store backups in cloud storage
- **Migration scheduling**: Schedule migrations for optimal timing
- **Migration analytics**: Track migration performance and success rates
- **Data compression**: Compress large datasets during migration
- **Parallel migrations**: Execute independent migrations in parallel