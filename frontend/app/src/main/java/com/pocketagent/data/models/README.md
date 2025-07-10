# Pocket Agent Data Models

This directory contains the core data models for the Pocket Agent mobile application, implementing the data layer specification for secure, encrypted JSON storage.

## 📁 File Structure

```
data/models/
├── AppData.kt          # Root data container with metadata
├── SshIdentity.kt      # SSH identity model with validation
├── ServerProfile.kt    # Server connection profile model
├── Project.kt          # Claude Code project model
├── Message.kt          # Chat message model
├── Extensions.kt       # Extension functions for common operations
├── TestBuilders.kt     # Comprehensive test builders and factories
├── ModelUsageExample.kt # Usage examples and demonstrations
└── README.md           # This documentation file
```

## 🏗️ Core Models

### AppData & AppMetadata
- **AppData**: Root container for all application data
- **AppMetadata**: Device and backup information
- Includes comprehensive validation of entity relationships
- Supports builder pattern for easy testing
- Extensive extension functions for data operations

### SshIdentity
- Represents SSH keys with encrypted private key storage
- Includes validation for names, fingerprints, and descriptions
- Supports various fingerprint formats (hex:colon and SHA256:base64)
- Builder pattern and factory methods for testing
- Extension functions for common operations

### ServerProfile
- Represents server connection configurations
- Links to SSH identities for authentication
- Includes ConnectionStatus enum for state tracking
- Validation for hostnames, usernames, and ports
- Extension functions for connection management

### Project
- Represents Claude Code projects
- Links to server profiles for hosting
- Includes ProjectStatus enum for session state
- Validation for project paths and repository URLs
- Extension functions for project lifecycle management

### Message
- Represents chat messages in conversations
- Supports different message types (user, claude, system, error, status)
- Includes metadata for tool requests and permissions
- Validation for content length and metadata
- Extension functions for message operations

## 🔧 Key Features

### Entity Relationships
The models implement the specified entity hierarchy:
```
SSH Identity (1) → (N) Server Profile → (N) Project
```

### Validation
- Comprehensive validation in `init` blocks
- Separate validator classes for complex validation logic
- Extension functions for runtime validation
- Support for relationship validation across entities

### Serialization
- Full kotlinx.serialization support with `@Serializable` annotations
- Uses `Long` timestamps for better JSON compatibility
- Export models for data without sensitive information
- JSON serialization extension functions

### Testing Support
- Builder patterns for all models
- Factory methods for creating realistic test data
- Comprehensive test scenarios (development, enterprise, personal)
- Example usage demonstrating all features

### Extension Functions
- Filtering and searching capabilities
- Sorting by various criteria
- Data transformation and export
- Health status monitoring
- Relationship validation

## 🚀 Usage Examples

### Basic Model Creation
```kotlin
val identity = SshIdentityBuilder()
    .name("Development Key")
    .encryptedPrivateKey("encrypted_key_data")
    .publicKeyFingerprint("SHA256:fingerprint")
    .build()

val server = ServerProfileBuilder()
    .name("Development Server")
    .hostname("dev.example.com")
    .sshIdentityId(identity.id)
    .build()

val project = ProjectBuilder()
    .name("My Project")
    .serverProfileId(server.id)
    .projectPath("/home/user/project")
    .build()
```

### Creating Complete Data Scenarios
```kotlin
val appData = DataScenarioBuilder()
    .addDevelopmentScenario()
    .addPersonalScenario()
    .buildAppData()
```

### Data Operations
```kotlin
// Search and filter
val activeProjects = appData.getActiveProjects()
val searchResults = appData.searchProjects("react")

// Validation
val validationResult = appData.validateRelationships()

// Data cleanup
val cleanedData = appData.cleanupOrphanedData()

// Health monitoring
val healthStatus = appData.getHealthStatus()
```

### Message Operations
```kotlin
val userMessage = MessageFactory.createUserInput("Help me with React")
val claudeMessage = MessageFactory.createClaudeResponse("I can help!")
    .withToolRequests(listOf("create_file"))
    .withPermissionRequired(true)
```

## 🔍 Model Specifications

### SshIdentity
- **Name**: Max 100 characters, alphanumeric with basic symbols
- **Fingerprint**: Hex:colon format or SHA256:base64 format
- **Description**: Optional, max 500 characters
- **Validation**: Name format, fingerprint format, description length

### ServerProfile
- **Name**: Max 100 characters, alphanumeric with basic symbols
- **Hostname**: Valid hostname format (alphanumeric, dots, hyphens)
- **Username**: Max 32 characters, alphanumeric with underscores/hyphens
- **Ports**: Valid range 1-65535, SSH and wrapper ports must differ
- **Validation**: Hostname format, username format, port ranges

### Project
- **Name**: Max 100 characters, alphanumeric with basic symbols
- **ProjectPath**: Must be absolute path starting with /
- **ScriptsFolder**: Relative path, no leading slash
- **RepositoryUrl**: Optional, must start with https:// or git@
- **Validation**: Path format, URL format, name format

### Message
- **Content**: Max 50,000 characters, cannot be blank
- **Metadata**: Max 20 entries, keys max 100 chars, values max 1000 chars
- **Types**: USER_INPUT, CLAUDE_RESPONSE, SYSTEM_MESSAGE, ERROR_MESSAGE, STATUS_UPDATE
- **Validation**: Content length, metadata limits

### AppData
- **Limits**: Max 50 SSH identities, 100 server profiles, 200 projects
- **Validation**: Entity relationship integrity, no orphaned references
- **Messages**: Max 1000 messages per project (enforced by repository)

## 🧪 Testing

### Test Builders
- `SshIdentityBuilder`: Fluent API for SSH identity creation
- `ServerProfileBuilder`: Fluent API for server profile creation
- `ProjectBuilder`: Fluent API for project creation
- `MessageBuilder`: Fluent API for message creation
- `AppDataBuilder`: Fluent API for complete data structure creation

### Test Factories
- `SshIdentityFactory`: Create sample SSH identities
- `ServerProfileFactory`: Create sample server profiles
- `ProjectFactory`: Create sample projects
- `MessageFactory`: Create sample messages and conversations
- `TestDataFactory`: Create complete test datasets

### Realistic Test Data
- `RealisticSshIdentityBuilder`: Create realistic SSH identities
- `RealisticServerProfileBuilder`: Create realistic server profiles
- `RealisticProjectBuilder`: Create realistic projects
- `RealisticMessageBuilder`: Create realistic conversations
- `DataScenarioBuilder`: Create complete scenarios with relationships

## 📊 Data Management

### Health Monitoring
- `DataHealthStatus`: HEALTHY, NEEDS_ATTENTION, EMPTY
- Orphaned data detection
- Unused entity detection
- Error state monitoring

### Data Operations
- Search and filtering across all entities
- Sorting by various criteria (usage, activity, status)
- Data cleanup and maintenance
- Export/import capabilities

### Validation
- Real-time validation during data operations
- Relationship integrity checking
- Format validation for all fields
- Comprehensive error reporting

## 🔐 Security Considerations

### Data Protection
- Private keys are already encrypted by SshKeyImportManager
- No plaintext sensitive data in models
- Export models exclude sensitive information
- Validation prevents injection attacks

### Validation
- Input sanitization through validation
- Format checking for all user inputs
- Relationship integrity enforcement
- Length limits prevent DoS attacks

## 📈 Performance Features

### Optimizations
- In-memory caching support
- Lazy loading compatible
- Efficient serialization
- Minimal object allocations

### Scalability
- Entity count limits prevent performance issues
- Message limiting (1000 per project)
- Efficient search and filtering
- Memory-conscious data structures

## 🛠️ Integration

### Repository Integration
Ready for use with SecureDataRepository:
- Compatible with encrypted JSON storage
- Supports atomic operations
- Thread-safe design
- Observable data flows

### UI Integration
- Display-friendly extension functions
- Search and filtering capabilities
- Status enums for UI state
- Preview content generation

### Testing Integration
- Comprehensive test builders
- Realistic test data generation
- Validation testing support
- Mock data scenarios

## 📝 Notes

- All models use `Long` timestamps for JSON compatibility
- Builder patterns provide fluent APIs for testing
- Extension functions provide common operations
- Validation is comprehensive and user-friendly
- Export models protect sensitive data
- Health monitoring helps maintain data integrity

This implementation follows the data layer specification and provides a robust foundation for the Pocket Agent mobile application's data layer.