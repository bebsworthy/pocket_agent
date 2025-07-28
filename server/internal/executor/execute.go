package executor

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strings"
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

// executeInternal runs Claude with the specified options for a project
func (ce *ClaudeExecutor) executeInternal(project *models.Project, options ExecuteOptions) (*ExecuteResult, error) {
	if project == nil {
		return nil, errors.NewValidationError("project cannot be nil")
	}

	if options.Prompt == "" {
		return nil, errors.NewValidationError("prompt cannot be empty")
	}

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
		"CLAUDE_OUTPUT_FORMAT=json",
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

	// Create buffers for output capture
	var stdoutBuf, stderrBuf bytes.Buffer
	cmd.Stdout = &stdoutBuf
	cmd.Stderr = &stderrBuf

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
	err := cmd.Run()
	executionTime := time.Since(startTime)

	// Capture outputs (Requirement 3.4)
	stdout := stdoutBuf.String()
	stderr := stderrBuf.String()

	// Build result
	result := &ExecuteResult{
		Stdout:        stdout,
		Stderr:        stderr,
		ExecutionTime: executionTime,
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

	// Parse Claude JSON output (Requirement 3.4)
	messages, sessionID, err := ce.parseClaudeOutput(stdout)
	if err != nil {
		ce.logger.Error("Failed to parse Claude output",
			"project_id", project.ID,
			"error", err,
			"stdout", stdout)

		return result, errors.Wrap(err, errors.CodeJSONParsing,
			"failed to parse Claude output")
	}

	result.Messages = messages
	result.SessionID = sessionID
	result.ExitCode = 0

	ce.logger.Info("Claude execution completed successfully",
		"project_id", project.ID,
		"session_id", sessionID,
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

	// Add project path
	args = append(args, "-p", project.Path)

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

	// Add the prompt as the last argument
	args = append(args, options.Prompt)

	return args
}

// ClaudeOutput represents the JSON output from Claude CLI
type ClaudeOutput struct {
	SessionID string                 `json:"session_id"`
	Messages  []models.ClaudeMessage `json:"messages"`
	Error     string                 `json:"error,omitempty"`
}

// parseClaudeOutput parses the JSON output from Claude CLI
func (ce *ClaudeExecutor) parseClaudeOutput(output string) ([]models.ClaudeMessage, string, error) {
	// Trim any whitespace
	output = strings.TrimSpace(output)

	if output == "" {
		return nil, "", fmt.Errorf("empty Claude output")
	}

	// Try to parse as structured JSON output
	var claudeOutput ClaudeOutput
	if err := json.Unmarshal([]byte(output), &claudeOutput); err != nil {
		// Try to extract JSON from mixed output
		jsonStart := strings.Index(output, "{")
		jsonEnd := strings.LastIndex(output, "}")

		if jsonStart >= 0 && jsonEnd > jsonStart {
			jsonStr := output[jsonStart : jsonEnd+1]
			if err := json.Unmarshal([]byte(jsonStr), &claudeOutput); err != nil {
				return nil, "", fmt.Errorf("failed to parse JSON: %w", err)
			}
		} else {
			return nil, "", fmt.Errorf("no valid JSON found in output")
		}
	}

	// Check for error in output
	if claudeOutput.Error != "" {
		return nil, "", fmt.Errorf("Claude reported error: %s", claudeOutput.Error)
	}

	return claudeOutput.Messages, claudeOutput.SessionID, nil
}

// ExecuteWithCallback executes Claude with a callback for streaming updates
func (ce *ClaudeExecutor) ExecuteWithCallback(
	project *models.Project,
	options ExecuteOptions,
	callback func(msg models.ClaudeMessage),
) (*ExecuteResult, error) {
	// For now, this executes normally and calls callback with all messages
	// In future, this could stream output in real-time
	result, err := ce.executeInternal(project, options)
	if err != nil {
		return result, err
	}

	// Call callback for each message
	if callback != nil {
		for _, msg := range result.Messages {
			callback(msg)
		}
	}

	return result, nil
}
