package executor

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/platform"
)

// ExecuteOptions contains options for Claude execution
type ExecuteOptions struct {
	Prompt                     string
	Timeout                    time.Duration
	DangerouslySkipPermissions bool
	AllowedTools               []string
	DisallowedTools            []string
	MCPConfig                  string
	AppendSystemPrompt         string
	PermissionMode             string
	Model                      string
	FallbackModel              string
	AddDirs                    []string
	StrictMCPConfig            bool
}

// ExecuteResult contains the result of a Claude execution
type ExecuteResult struct {
	Messages      []models.ClaudeMessage
	SessionID     string
	ExitCode      int
	Stdout        string
	Stderr        string
	ExecutionTime time.Duration
}

// executeInternalWithStreaming runs Claude with streaming output support
func (ce *ClaudeExecutor) executeInternalWithStreaming(
	project *models.Project,
	options ExecuteOptions,
	callback func(models.ClaudeMessage),
) (*ExecuteResult, error) {
	if project == nil {
		return nil, errors.NewValidationError("project cannot be nil")
	}

	if options.Prompt == "" {
		return nil, errors.NewValidationError("prompt cannot be empty")
	}

	// Use the project's existing message log
	messageLog := project.MessageLog

	// Create timeout context (Requirement 3.5)
	ctx, cancel := ce.createTimeoutContext(options.Timeout)
	defer cancel()

	// Build Claude command arguments
	args := ce.buildCommandArgs(project, options)

	// Create command
	cmd := exec.CommandContext(ctx, ce.config.ClaudePath, args...)

	// Set working directory to project path
	cmd.Dir = project.Path

	// Set environment
	cmd.Env = append(os.Environ(),
		"NO_COLOR=1", // Disable color output for cleaner parsing
	)

	// Apply platform-specific process setup
	if err := platform.SetupProcessGroup(cmd); err != nil {
		ce.logger.Warn("Failed to setup process group", "error", err)
	}

	// Platform-specific setup
	switch runtime.GOOS {
	case "darwin":
		platform.SetupMacOSProcess(cmd)
	case "linux":
		platform.SetupLinuxProcess(cmd)
	}

	// Get stdout pipe for streaming
	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to get stdout pipe: %w", err)
	}

	// Create buffer for stderr capture
	var stderrBuf bytes.Buffer
	cmd.Stderr = &stderrBuf

	// Get stdin pipe for sending prompt
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to get stdin pipe: %w", err)
	}

	// Create process info
	processInfo := &ProcessInfo{
		Cmd:       cmd,
		ProjectID: project.ID,
		StartTime: time.Now(),
		Context:   ctx,
		Cancel:    cancel,
	}

	// Register process (Requirement 3.1)
	if err := ce.registerProcess(project.ID, processInfo); err != nil {
		return nil, err
	}

	// Ensure cleanup happens
	defer ce.cleanupProcess(project.ID, processInfo)

	ce.logger.Info("Starting Claude execution",
		"project_id", project.ID,
		"session_id", project.SessionID,
		"prompt_length", len(options.Prompt))

	// Start the process
	startTime := time.Now()
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("failed to start Claude: %w", err)
	}

	// Log the user prompt first
	if messageLog != nil {
		userMsg := models.TimestampedMessage{
			Timestamp: time.Now(),
			Message: models.ClaudeMessage{
				Type:    "user",
				Content: json.RawMessage(fmt.Sprintf(`{"text":%q}`, options.Prompt)),
			},
			Direction: "client",
		}
		if err := messageLog.Append(userMsg); err != nil {
			ce.logger.Error("Failed to log user prompt", "error", err)
		}
	}

	// Send prompt via stdin
	go func() {
		defer stdin.Close()
		if _, err := stdin.Write([]byte(options.Prompt)); err != nil {
			ce.logger.Error("Failed to write prompt to stdin", "error", err)
		}
	}()

	// Channels for streaming results
	messagesChan := make(chan models.ClaudeMessage, 100)
	errorChan := make(chan error, 1)
	var sessionID string
	var sessionIDMutex sync.Mutex

	// Start goroutine to read and parse streaming output
	go func() {
		defer close(messagesChan)
		scanner := bufio.NewScanner(stdoutPipe)
		for scanner.Scan() {
			line := scanner.Text()
			if line == "" {
				continue
			}

			// Parse each line as a JSON object
			var obj map[string]interface{}
			if err := json.Unmarshal([]byte(line), &obj); err != nil {
				ce.logger.Debug("Failed to parse line as JSON",
					"line", line,
					"error", err)
				continue
			}

			// Extract message type
			msgType, ok := obj["type"].(string)
			if !ok {
				continue
			}

			// Handle different message types
			switch msgType {
			case "system":
				// Extract session ID from system messages
				if sid, ok := obj["session_id"].(string); ok && sid != "" {
					sessionIDMutex.Lock()
					sessionID = sid
					sessionIDMutex.Unlock()
				}
				// Store and stream system messages
				content, _ := json.Marshal(obj)
				msg := models.ClaudeMessage{
					Type:    msgType,
					Content: json.RawMessage(content),
				}
				messagesChan <- msg
				if callback != nil {
					callback(msg)
				}
				// Log the message
				if messageLog != nil {
					timestampedMsg := models.TimestampedMessage{
						Timestamp: time.Now(),
						Message:   msg,
						Direction: "claude",
					}
					if err := messageLog.Append(timestampedMsg); err != nil {
						ce.logger.Error("Failed to log Claude message", "error", err, "type", msgType)
					}
				}

			case "assistant", "user", "result":
				// Store and stream these message types
				content, _ := json.Marshal(obj)
				msg := models.ClaudeMessage{
					Type:    msgType,
					Content: json.RawMessage(content),
				}
				messagesChan <- msg
				if callback != nil {
					callback(msg)
				}
				// Log the message
				if messageLog != nil {
					timestampedMsg := models.TimestampedMessage{
						Timestamp: time.Now(),
						Message:   msg,
						Direction: "claude",
					}
					if err := messageLog.Append(timestampedMsg); err != nil {
						ce.logger.Error("Failed to log Claude message", "error", err, "type", msgType)
					}
				}
				// Also check for session_id in any message
				if sid, ok := obj["session_id"].(string); ok && sid != "" {
					sessionIDMutex.Lock()
					sessionID = sid
					sessionIDMutex.Unlock()
				}

			case "message_start", "content_block_start", "content_block_delta",
				"content_block_stop", "message_delta", "message_stop":
				// These are streaming message events - store and stream the raw JSON
				content, _ := json.Marshal(obj)
				msg := models.ClaudeMessage{
					Type:    msgType,
					Content: json.RawMessage(content),
				}
				messagesChan <- msg
				if callback != nil {
					callback(msg)
				}
				// Log the message
				if messageLog != nil {
					timestampedMsg := models.TimestampedMessage{
						Timestamp: time.Now(),
						Message:   msg,
						Direction: "claude",
					}
					if err := messageLog.Append(timestampedMsg); err != nil {
						ce.logger.Error("Failed to log Claude message", "error", err, "type", msgType)
					}
				}

			case "error":
				// Store error message
				content, _ := json.Marshal(obj)
				msg := models.ClaudeMessage{
					Type:    msgType,
					Content: json.RawMessage(content),
				}
				messagesChan <- msg
				if callback != nil {
					callback(msg)
				}
				// Log the error message
				if messageLog != nil {
					timestampedMsg := models.TimestampedMessage{
						Timestamp: time.Now(),
						Message:   msg,
						Direction: "claude",
					}
					if err := messageLog.Append(timestampedMsg); err != nil {
						ce.logger.Error("Failed to log Claude error", "error", err)
					}
				}
				// Also send to error channel - check both 'message' and 'error' fields
				if errMsg, ok := obj["message"].(string); ok {
					errorChan <- fmt.Errorf("Claude error: %s", errMsg)
				} else if errMsg, ok := obj["error"].(string); ok {
					errorChan <- fmt.Errorf("Claude error: %s", errMsg)
				} else {
					errorChan <- fmt.Errorf("Claude error: %v", obj)
				}
				return

			default:
				// Log unknown message types for debugging
				ce.logger.Debug("Unknown message type", "type", msgType, "object", obj)
			}
		}

		if err := scanner.Err(); err != nil {
			errorChan <- fmt.Errorf("error reading stdout: %w", err)
		}
	}()

	// Wait for completion
	err = cmd.Wait()
	executionTime := time.Since(startTime)

	// Capture stderr
	stderr := stderrBuf.String()

	// Build result
	result := &ExecuteResult{
		Stderr:        stderr,
		ExecutionTime: executionTime,
	}

	// Collect all messages
	var messages []models.ClaudeMessage
	for msg := range messagesChan {
		messages = append(messages, msg)
	}

	// Check for streaming errors
	select {
	case streamErr := <-errorChan:
		if streamErr != nil {
			return result, errors.Wrap(streamErr, errors.CodeJSONParsing,
				"error during streaming")
		}
	default:
	}

	// Handle execution errors
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			return nil, errors.NewExecutionTimeoutError(project.ID, options.Timeout.String())
		}

		// Get exit code if available
		if exitErr, ok := err.(*exec.ExitError); ok {
			result.ExitCode = exitErr.ExitCode()
		} else {
			result.ExitCode = -1
		}

		ce.logger.Error("Claude execution failed",
			"project_id", project.ID,
			"error", err,
			"stderr", stderr,
			"exit_code", result.ExitCode)

		return result, errors.New(errors.CodeExecutionFailed,
			"Claude execution failed: %v", err)
	}

	// Get final session ID
	sessionIDMutex.Lock()
	result.SessionID = sessionID
	sessionIDMutex.Unlock()

	result.Messages = messages
	result.ExitCode = 0

	ce.logger.Info("Claude execution completed successfully",
		"project_id", project.ID,
		"session_id", result.SessionID,
		"message_count", len(messages),
		"execution_time", executionTime)

	return result, nil
}

// buildCommandArgs builds the command line arguments for Claude
func (ce *ClaudeExecutor) buildCommandArgs(project *models.Project, options ExecuteOptions) []string {
	args := []string{}

	// Add session ID if exists (Requirement 3.2)
	if project.SessionID != "" {
		args = append(args, "-c", project.SessionID)
	}

	// Add -p flag to print response and exit (non-interactive mode)
	args = append(args, "-p")

	// Add options
	if options.DangerouslySkipPermissions {
		args = append(args, "--dangerously-skip-permissions")
	}

	if len(options.AllowedTools) > 0 {
		args = append(args, "--allowed-tools", strings.Join(options.AllowedTools, ","))
	}

	if len(options.DisallowedTools) > 0 {
		args = append(args, "--disallowed-tools", strings.Join(options.DisallowedTools, ","))
	}

	if options.MCPConfig != "" {
		args = append(args, "--mcp-config", options.MCPConfig)
	}

	if options.AppendSystemPrompt != "" {
		args = append(args, "--append-system-prompt", options.AppendSystemPrompt)
	}

	if options.PermissionMode != "" {
		args = append(args, "--permission-mode", options.PermissionMode)
	}

	if options.Model != "" {
		args = append(args, "--model", options.Model)
	}

	if options.FallbackModel != "" {
		args = append(args, "--fallback-model", options.FallbackModel)
	}

	for _, dir := range options.AddDirs {
		args = append(args, "--add-dir", dir)
	}

	if options.StrictMCPConfig {
		args = append(args, "--strict-mcp-config")
	}

	// Add required flags for JSON output
	args = append(args, "--verbose", "--output-format", "stream-json")

	// Prompt will be sent via stdin, not as argument

	return args
}

// ClaudeOutput represents the JSON output from Claude CLI
type ClaudeOutput struct {
	SessionID string                 `json:"session_id"`
	Messages  []models.ClaudeMessage `json:"messages"`
	Error     string                 `json:"error,omitempty"`
}

// parseClaudeOutput parses the JSONL (streaming JSON) output from Claude CLI
func (ce *ClaudeExecutor) parseClaudeOutput(output string) ([]models.ClaudeMessage, string, error) {
	// Trim any whitespace
	output = strings.TrimSpace(output)

	if output == "" {
		return nil, "", fmt.Errorf("empty Claude output")
	}

	var messages []models.ClaudeMessage
	var sessionID string

	// Parse JSONL format - each line is a separate JSON object
	lines := strings.Split(output, "\n")
	for i, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		// Parse each line as a JSON object
		var obj map[string]interface{}
		if err := json.Unmarshal([]byte(line), &obj); err != nil {
			ce.logger.Debug("Failed to parse line as JSON",
				"line_number", i+1,
				"line", line,
				"error", err)
			continue
		}

		// Extract message type
		msgType, ok := obj["type"].(string)
		if !ok {
			continue
		}

		// Handle different message types
		switch msgType {
		case "system":
			// Extract session ID from system messages
			if sid, ok := obj["session_id"].(string); ok && sid != "" {
				sessionID = sid
			}
			// Also store system messages
			content, _ := json.Marshal(obj)
			messages = append(messages, models.ClaudeMessage{
				Type:    msgType,
				Content: json.RawMessage(content),
			})
		case "assistant", "user", "result":
			// Store these message types
			content, _ := json.Marshal(obj)
			messages = append(messages, models.ClaudeMessage{
				Type:    msgType,
				Content: json.RawMessage(content),
			})
			// Also check for session_id in any message
			if sid, ok := obj["session_id"].(string); ok && sid != "" {
				sessionID = sid
			}
		case "message_start", "content_block_start", "content_block_delta",
			"content_block_stop", "message_delta", "message_stop":
			// These are streaming message events - store the raw JSON
			content, _ := json.Marshal(obj)
			messages = append(messages, models.ClaudeMessage{
				Type:    msgType,
				Content: json.RawMessage(content),
			})
		case "error":
			// Handle error messages - check both 'message' and 'error' fields
			if errMsg, ok := obj["message"].(string); ok {
				return nil, "", fmt.Errorf("Claude error: %s", errMsg)
			}
			if errMsg, ok := obj["error"].(string); ok {
				return nil, "", fmt.Errorf("Claude error: %s", errMsg)
			}
			return nil, "", fmt.Errorf("Claude error: %v", obj)
		default:
			// Log unknown message types for debugging
			ce.logger.Debug("Unknown message type", "type", msgType, "object", obj)
		}
	}

	if len(messages) == 0 && sessionID == "" {
		return nil, "", fmt.Errorf("no valid messages or session ID found in output")
	}

	return messages, sessionID, nil
}

// ExecuteWithCallback executes Claude with a callback for streaming updates
func (ce *ClaudeExecutor) ExecuteWithCallback(
	project *models.Project,
	options ExecuteOptions,
	callback func(msg models.ClaudeMessage),
) (*ExecuteResult, error) {
	// Use the streaming implementation
	return ce.executeInternalWithStreaming(project, options, callback)
}
