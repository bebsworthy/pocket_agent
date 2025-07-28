// Package storage provides persistent storage functionality for the WebSocket API server.
//
// The storage package implements two main components required by the WebSocket API:
//
// 1. Message Log with Rotation (MessageLog)
//   - Stores timestamped messages for each project
//   - Automatic file rotation based on size (100MB), message count (10,000), or time (daily)
//   - Atomic write operations to prevent corruption
//   - Query methods for retrieving message history by timestamp
//   - Thread-safe concurrent access
//
// 2. Project Persistence (ProjectPersistence)
//   - Saves and loads project metadata to/from disk
//   - Atomic file operations using temp file + rename pattern
//   - Corruption recovery with backup support
//   - Handles server restart scenarios gracefully
//
// Storage Layout:
//
//	data/
//	├── projects/                    # Project metadata storage
//	│   ├── {project-id}/
//	│   │   └── metadata.json       # Project configuration
//	│   └── ...
//	└── logs/                       # Message logs
//	    ├── {project-id}/
//	    │   ├── messages_YYYY-MM-DD_HH-MM-SS.jsonl
//	    │   ├── current.jsonl -> messages_...jsonl
//	    │   └── ...
//	    └── ...
//
// Thread Safety:
//
// All storage operations are thread-safe and can be called concurrently.
// The package uses mutexes to ensure data consistency during concurrent access.
//
// Error Handling:
//
// The package provides comprehensive error handling with wrapped errors that
// include context about the operation that failed. Corruption recovery is
// automatic where possible.
//
// Example Usage:
//
//	// Create storage factory
//	factory := storage.NewFactory("/var/lib/pocketagent")
//	factory.EnsureDirectories()
//
//	// Create project persistence
//	persistence, err := factory.CreateProjectPersistence()
//	if err != nil {
//	    log.Fatal(err)
//	}
//
//	// Save project
//	project := models.NewProject("proj-123", "/path/to/project")
//	err = persistence.SaveProjectMetadata(project)
//
//	// Create message log
//	msgLog, err := factory.CreateMessageLog(project.ID)
//	if err != nil {
//	    log.Fatal(err)
//	}
//	defer msgLog.Close()
//
//	// Append message
//	msg := models.TimestampedMessage{
//	    Timestamp: time.Now(),
//	    Message:   claudeMsg,
//	    Direction: "client",
//	}
//	err = msgLog.Append(msg)
package storage
