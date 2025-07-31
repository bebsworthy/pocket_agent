package storage

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
)

const (
	// MaxLogFileSize is 100MB
	MaxLogFileSize = 100 * 1024 * 1024
	// MaxLogMessages is 10,000 messages per file
	MaxLogMessages = 10000
	// LogFileFormat for rotation
	LogFileFormat = "messages_2006-01-02_15-04-05.jsonl"
	// CurrentLogSymlink points to the current log file
	CurrentLogSymlink = "current.jsonl"
)

// MessageLog handles persistent storage of messages with rotation
type MessageLog struct {
	projectID    string
	logDir       string
	currentFile  *os.File
	currentPath  string
	messageCount int
	fileSize     int64
	mu           sync.Mutex
	rotationTime time.Time
	initialized  bool
}

// NewMessageLog creates a new message log for a project
func NewMessageLog(projectID, projectDir string) (*MessageLog, error) {
	ml := &MessageLog{
		projectID:    projectID,
		logDir:       filepath.Join(projectDir, "logs"),
		rotationTime: time.Now().Truncate(24 * time.Hour).Add(24 * time.Hour), // Next midnight
		initialized:  false,
	}

	// Don't create directories or files here - wait for first append
	return ml, nil
}

// ensureInitialized creates the log directory and initial file if not already done
func (ml *MessageLog) ensureInitialized() error {
	if err := os.MkdirAll(ml.logDir, 0o755); err != nil {
		return fmt.Errorf("failed to create log directory: %w", err)
	}

	if err := ml.initializeCurrentFile(); err != nil {
		return fmt.Errorf("failed to initialize log file: %w", err)
	}

	ml.initialized = true
	return nil
}

// Append adds a timestamped message to the log
func (ml *MessageLog) Append(msg models.TimestampedMessage) error {
	ml.mu.Lock()
	defer ml.mu.Unlock()

	// Initialize on first use
	if !ml.initialized {
		if err := ml.ensureInitialized(); err != nil {
			return err
		}
	}

	// Check if rotation is needed
	if err := ml.rotateIfNeeded(); err != nil {
		return fmt.Errorf("failed to rotate log: %w", err)
	}

	// Marshal message to JSON
	data, err := json.Marshal(msg)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %w", err)
	}

	// Write to file with newline
	n, err := ml.currentFile.Write(append(data, '\n'))
	if err != nil {
		return fmt.Errorf("failed to write message: %w", err)
	}

	// Update counters
	ml.messageCount++
	ml.fileSize += int64(n)

	// Sync to ensure durability
	if err := ml.currentFile.Sync(); err != nil {
		return fmt.Errorf("failed to sync file: %w", err)
	}

	return nil
}

// GetMessagesSince retrieves messages after the specified timestamp
func (ml *MessageLog) GetMessagesSince(since time.Time) ([]models.TimestampedMessage, error) {
	ml.mu.Lock()
	defer ml.mu.Unlock()

	// Check if log directory exists even if not initialized
	if !ml.initialized {
		if _, err := os.Stat(ml.logDir); os.IsNotExist(err) {
			// Directory doesn't exist, return empty
			return []models.TimestampedMessage{}, nil
		}
		// Directory exists, we can read from it
	}

	// Get all log files
	files, err := ml.getLogFiles()
	if err != nil {
		return nil, fmt.Errorf("failed to list log files: %w", err)
	}

	var messages []models.TimestampedMessage

	// Read from each file
	for _, file := range files {
		msgs, err := ml.readMessagesFromFile(file, since)
		if err != nil {
			return nil, fmt.Errorf("failed to read from %s: %w", file, err)
		}
		messages = append(messages, msgs...)
	}

	// Sort by timestamp
	sort.Slice(messages, func(i, j int) bool {
		return messages[i].Timestamp.Before(messages[j].Timestamp)
	})

	return messages, nil
}

// Close closes the message log
func (ml *MessageLog) Close() error {
	ml.mu.Lock()
	defer ml.mu.Unlock()

	if ml.currentFile != nil {
		// Check if empty before closing
		stat, _ := ml.currentFile.Stat()
		err := ml.currentFile.Close()
		
		// Delete if empty
		if stat != nil && stat.Size() == 0 {
			os.Remove(ml.currentPath)
		}
		
		return err
	}
	return nil
}

// rotateIfNeeded checks if rotation is required and performs it
func (ml *MessageLog) rotateIfNeeded() error {
	// Don't rotate if no messages written
	if ml.messageCount == 0 {
		return nil
	}

	// Check size and message count
	needRotation := ml.fileSize >= MaxLogFileSize ||
		ml.messageCount >= MaxLogMessages ||
		time.Now().After(ml.rotationTime)

	if !needRotation {
		return nil
	}

	// Perform rotation
	return ml.rotateLog()
}

// rotateLog performs the actual log rotation
func (ml *MessageLog) rotateLog() error {
	// Close current file first to ensure no file descriptor leak
	if ml.currentFile != nil {
		// Check if empty before closing
		stat, _ := ml.currentFile.Stat()
		if err := ml.currentFile.Close(); err != nil {
			return fmt.Errorf("failed to close current file: %w", err)
		}
		
		// Delete if empty
		if stat != nil && stat.Size() == 0 {
			os.Remove(ml.currentPath)
		}
		
		ml.currentFile = nil // Clear reference immediately after closing
	}

	// Create new file
	return ml.initializeCurrentFile()
}

// initializeCurrentFile creates a new log file and updates the symlink
func (ml *MessageLog) initializeCurrentFile() error {
	// Generate new filename
	filename := time.Now().Format(LogFileFormat)
	newPath := filepath.Join(ml.logDir, filename)

	// Create new file
	file, err := os.OpenFile(newPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return fmt.Errorf("failed to create log file: %w", err)
	}

	// Update symlink atomically
	symlinkPath := filepath.Join(ml.logDir, CurrentLogSymlink)
	tempSymlink := symlinkPath + ".tmp"

	// Create temporary symlink
	if err := os.Symlink(filename, tempSymlink); err != nil {
		// If symlinks aren't supported, we'll just skip this
		if !os.IsExist(err) && !os.IsPermission(err) {
			// Clean up and continue without symlink
			os.Remove(tempSymlink)
		}
	} else {
		// Atomically replace the symlink
		if err := os.Rename(tempSymlink, symlinkPath); err != nil {
			os.Remove(tempSymlink)
		}
	}

	// Update state
	ml.currentFile = file
	ml.currentPath = newPath
	ml.messageCount = 0
	ml.fileSize = 0
	ml.rotationTime = time.Now().Truncate(24 * time.Hour).Add(24 * time.Hour)

	return nil
}

// getLogFiles returns all log files sorted by modification time
func (ml *MessageLog) getLogFiles() ([]string, error) {
	entries, err := os.ReadDir(ml.logDir)
	if err != nil {
		return nil, err
	}

	var files []string
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".jsonl") {
			continue
		}
		// Skip symlink
		if entry.Name() == CurrentLogSymlink {
			continue
		}
		files = append(files, filepath.Join(ml.logDir, entry.Name()))
	}

	// Sort by filename (which includes timestamp)
	sort.Strings(files)

	return files, nil
}

// readMessagesFromFile reads messages from a specific file
func (ml *MessageLog) readMessagesFromFile(path string, since time.Time) ([]models.TimestampedMessage, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var messages []models.TimestampedMessage
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024) // 1MB max line size

	for scanner.Scan() {
		var msg models.TimestampedMessage
		if err := json.Unmarshal(scanner.Bytes(), &msg); err != nil {
			// Skip corrupted lines
			continue
		}

		// Filter by timestamp
		if msg.Timestamp.After(since) {
			messages = append(messages, msg)
		}
	}

	if err := scanner.Err(); err != nil && err != io.EOF {
		return nil, fmt.Errorf("error reading file: %w", err)
	}

	return messages, nil
}

// GetStats returns current log statistics
func (ml *MessageLog) GetStats() (messageCount int, fileSize int64, currentFile string) {
	ml.mu.Lock()
	defer ml.mu.Unlock()

	return ml.messageCount, ml.fileSize, ml.currentPath
}
