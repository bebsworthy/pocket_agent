package executor

import (
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"syscall"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/platform"
)

// KillExecution terminates an active Claude process for a project
func (ce *ClaudeExecutor) KillExecution(projectID string) error {
	// Find active process by project ID (Requirement 5.1)
	processInfo, err := ce.getProcess(projectID)
	if err != nil {
		// Return appropriate error if no execution is active (Requirement 5.2)
		return errors.New(errors.CodeProcessNotFound,
			"no active execution found for project %s", projectID)
	}

	ce.logger.Info("Killing execution for project",
		"project_id", projectID,
		"process_pid", processInfo.Cmd.Process.Pid)

	// First, try graceful termination by canceling context
	if processInfo.Cancel != nil {
		processInfo.Cancel()
	}

	// Give the process a moment to respond to context cancellation
	gracefulTimeout := 2 * time.Second
	done := make(chan error, 1)

	go func() {
		err := processInfo.Cmd.Wait()
		done <- err
	}()

	select {
	case err := <-done:
		// Process terminated gracefully
		ce.logger.Info("Process terminated gracefully",
			"project_id", projectID,
			"wait_error", err)
		ce.cleanupProcess(projectID, processInfo)
		return nil

	case <-time.After(gracefulTimeout):
		// Process didn't terminate, need to kill it
		ce.logger.Warn("Process did not terminate gracefully, sending kill signal",
			"project_id", projectID)
	}

	// Send kill signal to process (Requirement 5.1)
	if err := ce.killProcess(processInfo.Cmd.Process); err != nil {
		ce.logger.Error("Failed to kill process",
			"project_id", projectID,
			"error", err)
		return errors.Wrap(err, errors.CodeExecutionFailed,
			"failed to kill process")
	}

	// Wait for process to actually terminate
	killTimeout := 5 * time.Second
	select {
	case err := <-done:
		ce.logger.Info("Process killed successfully",
			"project_id", projectID,
			"wait_error", err)

	case <-time.After(killTimeout):
		ce.logger.Error("Process did not terminate after kill signal",
			"project_id", projectID,
			"pid", processInfo.Cmd.Process.Pid)
		// Even if the process didn't terminate, clean it up from our tracking
		// to prevent resource leaks
		ce.cleanupProcess(projectID, processInfo)
		return errors.New(errors.CodeExecutionFailed,
			"process did not terminate after kill signal")
	}

	// Clean up process tracking (Requirement 5.3)
	ce.cleanupProcess(projectID, processInfo)

	return nil
}

// killProcess sends appropriate kill signal based on platform
func (ce *ClaudeExecutor) killProcess(process *os.Process) error {
	if process == nil {
		return fmt.Errorf("process is nil")
	}

	// Try to use platform-specific process group kill
	if cmd, ok := ce.getCommandForProcess(process); ok {
		if err := platform.KillProcessGroup(cmd); err == nil {
			return nil
		}
		// Fall back to regular kill if process group kill fails
	}

	// Platform-specific kill implementation
	switch runtime.GOOS {
	case "linux", "darwin":
		// Force kill with SIGKILL since this is called after graceful termination failed
		if err := process.Signal(syscall.SIGKILL); err != nil {
			return fmt.Errorf("failed to send kill signal: %w", err)
		}
		return nil

	default:
		// For other platforms, use the standard Kill method
		return process.Kill()
	}
}

// getCommandForProcess retrieves the exec.Cmd for a process
func (ce *ClaudeExecutor) getCommandForProcess(process *os.Process) (*exec.Cmd, bool) {
	ce.mu.Lock()
	defer ce.mu.Unlock()

	for _, info := range ce.activeProcesses {
		if info.Cmd.Process == process {
			return info.Cmd, true
		}
	}

	return nil, false
}

// KillAll terminates all active executions
func (ce *ClaudeExecutor) KillAll() error {
	ce.mu.Lock()
	// Get list of all active project IDs
	projectIDs := make([]string, 0, len(ce.activeProcesses))
	for projectID := range ce.activeProcesses {
		projectIDs = append(projectIDs, projectID)
	}
	ce.mu.Unlock()

	ce.logger.Info("Killing all active executions",
		"count", len(projectIDs))

	// Kill each execution
	var lastErr error
	successCount := 0

	for _, projectID := range projectIDs {
		if err := ce.KillExecution(projectID); err != nil {
			ce.logger.Error("Failed to kill execution",
				"project_id", projectID,
				"error", err)
			lastErr = err
		} else {
			successCount++
		}
	}

	ce.logger.Info("Completed killing executions",
		"total", len(projectIDs),
		"successful", successCount,
		"failed", len(projectIDs)-successCount)

	if lastErr != nil {
		return fmt.Errorf("failed to kill some executions, last error: %w", lastErr)
	}

	return nil
}

// ForceKillExecution forcefully terminates an execution without grace period
func (ce *ClaudeExecutor) ForceKillExecution(projectID string) error {
	processInfo, err := ce.getProcess(projectID)
	if err != nil {
		return err
	}

	ce.logger.Warn("Force killing execution",
		"project_id", projectID,
		"process_pid", processInfo.Cmd.Process.Pid)

	// Cancel context immediately
	if processInfo.Cancel != nil {
		processInfo.Cancel()
	}

	// Send SIGKILL directly
	if runtime.GOOS == "linux" || runtime.GOOS == "darwin" {
		if err := processInfo.Cmd.Process.Signal(syscall.SIGKILL); err != nil {
			return errors.Wrap(err, errors.CodeExecutionFailed,
				"failed to force kill process")
		}
	} else {
		if err := processInfo.Cmd.Process.Kill(); err != nil {
			return errors.Wrap(err, errors.CodeExecutionFailed,
				"failed to force kill process")
		}
	}

	// Don't wait, just clean up immediately
	ce.cleanupProcess(projectID, processInfo)

	return nil
}

// IsProcessAlive checks if a process is still running
func (ce *ClaudeExecutor) IsProcessAlive(projectID string) bool {
	processInfo, err := ce.getProcess(projectID)
	if err != nil {
		return false
	}

	// Check if process is still running
	if processInfo.Cmd.Process == nil {
		return false
	}

	// Try to send signal 0 (no-op) to check if process exists
	err = processInfo.Cmd.Process.Signal(syscall.Signal(0))
	return err == nil
}
