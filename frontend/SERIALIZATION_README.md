# Kotlin Serialization Configuration

This document describes the Kotlin Serialization setup for the Pocket Agent mobile application.

## Overview

The application uses Kotlin Serialization for JSON handling across multiple components:
- Data storage (encrypted JSON files)
- Network communication (WebSocket messages)
- Configuration management
- Message protocol handling

## Configuration

### JsonConfig Class

The `JsonConfig` class provides three preconfigured JSON instances:

1. **Standard JSON** (`json`): For general use with pretty printing
2. **Compact JSON** (`compactJson`): For network communication without formatting
3. **Strict JSON** (`strictJson`): For critical data validation with strict rules

### Key Features

- **Backwards compatibility**: Ignores unknown fields during deserialization
- **Default values**: Includes default values in serialization
- **Error handling**: Comprehensive error handling with recovery mechanisms
- **Custom serializers**: Specialized serializers for UUIDs, timestamps, and validation
- **Type safety**: Sealed classes for WebSocket message types

## Data Models

### Core Data Models

1. **AppData**: Root data model containing all app entities
2. **SshIdentity**: SSH private key management
3. **ServerProfile**: Development server configurations
4. **Project**: Claude Code session information
5. **Message**: Chat history and communication

### Message Protocol

WebSocket communication uses sealed classes with `@Serializable` annotations:

- Authentication messages (AuthChallenge, AuthResponse, AuthSuccess)
- Command messages (CommandMessage, ProjectInitMessage)
- Response messages (ClaudeResponse, CommandOutput, ProgressUpdate)
- Permission messages (PermissionRequest, PermissionResponse)
- Session management (SessionResume, SessionStatus, SessionTerminate)

## Custom Serializers

### Built-in Serializers

- `UuidSerializer`: Handles UUID serialization/deserialization
- `InstantSerializer`: Java 8 Instant objects
- `DurationSerializer`: Java 8 Duration objects
- `FlexibleTimestampSerializer`: Handles both epoch milliseconds and ISO strings

### Validation Serializers

- `SshFingerprintSerializer`: Validates SSH fingerprint format
- `HostnameSerializer`: Validates hostname format
- `UsernameSerializer`: Validates username format
- `PortSerializer`: Validates port number ranges

## Error Handling

### SerializationErrorHandler

Centralized error handling with:
- Error classification and logging
- Recovery mechanisms for different error types
- Detailed error reporting
- Fallback parsing strategies

### Error Types

- `SERIALIZATION_ERROR`: Kotlin serialization failures
- `VALIDATION_ERROR`: Data validation failures
- `MEMORY_ERROR`: Out of memory during serialization
- `SECURITY_ERROR`: Security-related failures
- `UNKNOWN_ERROR`: Unclassified errors

## Utility Functions

### SerializationUtils

- WebSocket message serialization/deserialization
- JSON structure validation
- JSON object merging
- Field extraction
- Size estimation

### Extension Functions

- `toJson()`: Safe serialization with error handling
- `fromJson()`: Safe deserialization with error handling
- `toCompactJson()`: Compact serialization for network
- `isValidJson()`: JSON validation
- `prettifyJson()`: JSON formatting
- `extractJsonField()`: Field extraction

## Usage Examples

### Basic Serialization

```kotlin
// Serialize object to JSON
val result = myObject.toJson()
if (result.isSuccess) {
    val jsonString = result.getOrThrow()
    // Use JSON string
}

// Deserialize from JSON
val deserializedResult = jsonString.fromJson<MyObject>()
if (deserializedResult.isSuccess) {
    val obj = deserializedResult.getOrThrow()
    // Use object
}
```

### WebSocket Messages

```kotlin
// Serialize WebSocket message
val message = CommandMessage(command = "git status", isShellCommand = true)
val serialized = serializationUtils.serializeWebSocketMessage(message)

// Deserialize WebSocket message
val deserialized = serializationUtils.deserializeWebSocketMessage(jsonString)
```

### Error Handling

```kotlin
val result = data.toJson()
    .handleSerializationError(errorHandler, "user_data_save")
    
if (result.isFailure) {
    val error = result.exceptionOrNull()
    // Handle error appropriately
}
```

## Dependency Injection

The serialization components are provided through Hilt:

```kotlin
@Inject
lateinit var jsonConfig: JsonConfig

@Inject
lateinit var serializationUtils: SerializationUtils

@Inject
lateinit var errorHandler: SerializationErrorHandler
```

## Testing

Comprehensive test coverage includes:
- Data model serialization/deserialization
- WebSocket message protocol testing
- Error handling validation
- Custom serializer testing
- Extension function testing

## Security Considerations

- No sensitive data logged in error messages
- Encrypted data handled securely
- Validation of all input data
- Safe defaults for unknown fields
- Memory cleanup after operations

## Performance Optimizations

- Lazy JSON instance creation
- Efficient custom serializers
- Minimal memory allocation
- Compact network serialization
- Batch processing for large datasets

## Migration Support

The serialization system supports:
- Schema version tracking
- Backwards compatibility
- Data migration helpers
- Graceful degradation for unknown fields
- Validation of migrated data

## Future Enhancements

- Binary serialization for large datasets
- Streaming serialization for real-time data
- Compression for storage optimization
- Schema validation
- Performance monitoring