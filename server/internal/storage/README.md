# Storage Package

The storage package provides persistent storage functionality for the WebSocket API server, implementing Track C tasks (5 & 6) from the websocket-api feature.

## Overview

This package implements two main components:

1. **Message Log with Rotation** - Persistent message storage with automatic file rotation
2. **Project Persistence** - Project metadata storage with atomic operations and corruption recovery

## Features

### Message Log (`message_log.go`)
- Stores timestamped messages in JSONL format
- Automatic rotation based on:
  - File size (100MB limit)
  - Message count (10,000 messages)
  - Time (daily rotation at midnight)
- Atomic write operations to prevent corruption
- Query messages by timestamp with efficient filtering
- Thread-safe concurrent access
- Maintains chronological order across multiple files

### Project Persistence (`project_persistence.go`)
- Saves and loads project metadata atomically
- Uses temp file + rename pattern for atomic writes
- Corruption recovery mechanisms:
  - Automatic backup creation
  - Recovery from backup files
  - Recovery from temporary files
- Graceful handling of corrupted metadata
- Thread-safe operations

### Storage Factory (`factory.go`)
- Centralized creation of storage components
- Directory structure management
- Ensures all required directories exist

## Storage Layout

```
data/
├── projects/                    # Project metadata storage
│   ├── {project-id}/
│   │   └── metadata.json       # Project configuration
│   └── ...
└── logs/                       # Message logs
    ├── {project-id}/
    │   ├── messages_YYYY-MM-DD_HH-MM-SS.jsonl
    │   ├── current.jsonl -> messages_...jsonl (symlink)
    │   └── ...
    └── ...
```

## Usage

```go
// Create storage factory
factory := storage.NewFactory("/var/lib/pocketagent")
if err := factory.EnsureDirectories(); err != nil {
    log.Fatal(err)
}

// Project persistence
persistence, err := factory.CreateProjectPersistence()
if err != nil {
    log.Fatal(err)
}

// Save project
project := models.NewProject("proj-123", "/path/to/project")
err = persistence.SaveProjectMetadata(project)

// Load all projects
projects, err := persistence.LoadProjects()

// Message logging
msgLog, err := factory.CreateMessageLog(project.ID)
if err != nil {
    log.Fatal(err)
}
defer msgLog.Close()

// Append message
msg := models.TimestampedMessage{
    Timestamp: time.Now(),
    Message:   claudeMsg,
    Direction: "client",
}
err = msgLog.Append(msg)

// Query messages
messages, err := msgLog.GetMessagesSince(time.Now().Add(-1 * time.Hour))
```

## Thread Safety

All operations are thread-safe and can be called concurrently. The package uses mutexes to ensure data consistency.

## Error Handling

The package provides comprehensive error handling with wrapped errors that include context about the operation that failed. Corruption recovery is automatic where possible.

## Testing

The package includes comprehensive tests with 76.1% coverage:
- Unit tests for individual components
- Integration tests for complete workflows
- Concurrent access tests
- Corruption recovery tests

Run tests with:
```bash
go test ./internal/storage/... -v -cover
```

## Requirements Satisfied

This implementation satisfies the following requirements from the WebSocket API specification:

- **7.1, 7.2**: Message history retrieval with timestamp filtering
- **10.1**: Log file rotation by size and message count
- **8.1, 8.2, 8.3, 8.4**: Project persistence with atomic operations and corruption recovery