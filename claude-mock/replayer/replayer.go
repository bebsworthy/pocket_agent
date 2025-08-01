package replayer

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"time"
)

// LogEntry represents a single line from the log file
type LogEntry struct {
	Timestamp string          `json:"timestamp"`
	Message   json.RawMessage `json:"message"`
	Direction string          `json:"direction"`
}

// Replayer handles replaying messages from a log file
type Replayer struct {
	logFile   string
	sessionID string
	delay     time.Duration
	speed     float64
}

// New creates a new Replayer
func New(logFile string) (*Replayer, error) {
	// Get delay from environment
	delayMs := 100 // default 100ms
	if delayStr := os.Getenv("CLAUDE_MOCK_DELAY_MS"); delayStr != "" {
		if d, err := strconv.Atoi(delayStr); err == nil {
			delayMs = d
		}
	}

	// Get speed multiplier from environment
	speed := 1.0
	if speedStr := os.Getenv("CLAUDE_MOCK_SPEED"); speedStr != "" {
		if s, err := strconv.ParseFloat(speedStr, 64); err == nil && s > 0 {
			speed = s
		}
	}

	return &Replayer{
		logFile: logFile,
		delay:   time.Duration(delayMs) * time.Millisecond,
		speed:   speed,
	}, nil
}

// SetSessionID sets the session ID for the replayer
func (r *Replayer) SetSessionID(sessionID string) {
	r.sessionID = sessionID
}

// Replay reads the log file and outputs messages
func (r *Replayer) Replay() error {
	file, err := os.Open(r.logFile)
	if err != nil {
		return fmt.Errorf("failed to open log file: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	firstMessage := true

	for scanner.Scan() {
		line := scanner.Text()
		if line == "" {
			continue
		}

		// Parse log entry
		var entry LogEntry
		if err := json.Unmarshal([]byte(line), &entry); err != nil {
			// Skip invalid lines
			continue
		}

		// Only process Claude messages
		if entry.Direction != "claude" {
			continue
		}

		// Extract the inner message
		var msg map[string]interface{}
		if err := json.Unmarshal(entry.Message, &msg); err != nil {
			continue
		}

		// If we have a session ID from the command line, update system messages
		if r.sessionID != "" && msg["type"] == "system" {
			if content, ok := msg["content"].(map[string]interface{}); ok {
				content["session_id"] = r.sessionID
				msg["session_id"] = r.sessionID
			}
		}

		// Output the message as JSON
		output, err := json.Marshal(msg)
		if err != nil {
			continue
		}

		// Add delay between messages (except before first message)
		if !firstMessage && r.delay > 0 {
			adjustedDelay := time.Duration(float64(r.delay) / r.speed)
			time.Sleep(adjustedDelay)
		}
		firstMessage = false

		// Output to stdout
		fmt.Println(string(output))
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("error reading log file: %w", err)
	}

	return nil
}