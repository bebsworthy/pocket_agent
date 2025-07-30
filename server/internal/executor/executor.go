package executor

import (
	"context"
	"fmt"
	"os/exec"
	"sync"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
)

// ClaudeExecutor manages Claude CLI process execution
type ClaudeExecutor struct {
	// activeProcesses tracks running processes by project ID
	activeProcesses map[string]*ProcessInfo
	// mu protects concurrent access to activeProcesses
	mu sync.Mutex
	// logger for operational logging
	logger *logger.Logger
	// config contains executor configuration
	config Config
}

// ProcessInfo tracks information about a running process
type ProcessInfo struct {
	Cmd       *exec.Cmd
	ProjectID string
	StartTime time.Time
	Context   context.Context
	Cancel    context.CancelFunc
}

// Config contains configuration for the ClaudeExecutor
type Config struct {
	// ClaudePath is the path to the Claude CLI executable
	ClaudePath string
	// DefaultTimeout is the default execution timeout
	DefaultTimeout time.Duration
	// MaxConcurrentExecutions limits concurrent executions
	MaxConcurrentExecutions int
}

// DefaultConfig returns default executor configuration
func DefaultConfig() Config {
	return Config{
		ClaudePath:              "claude", // Assumes claude is in PATH
		DefaultTimeout:          5 * time.Minute,
		MaxConcurrentExecutions: 10,
	}
}

// NewClaudeExecutor creates a new Claude executor instance
func NewClaudeExecutor(config Config) (*ClaudeExecutor, error) {
	// Validate configuration
	if config.ClaudePath == "" {
		config.ClaudePath = "claude"
	}

	if config.DefaultTimeout <= 0 {
		config.DefaultTimeout = 5 * time.Minute
	}

	if config.MaxConcurrentExecutions <= 0 {
		config.MaxConcurrentExecutions = 10
	}

	// Verify Claude CLI is available
	if _, err := exec.LookPath(config.ClaudePath); err != nil {
		return nil, errors.New(errors.CodeClaudeNotFound,
			"Claude CLI not found at path: %s", config.ClaudePath)
	}

	return &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
		config:          config,
	}, nil
}

// GetActiveProcessCount returns the number of active processes
func (ce *ClaudeExecutor) GetActiveProcessCount() int {
	ce.mu.Lock()
	defer ce.mu.Unlock()
	return len(ce.activeProcesses)
}

// DefaultTimeout returns the default timeout for executions
func (ce *ClaudeExecutor) DefaultTimeout() time.Duration {
	return ce.config.DefaultTimeout
}

// IsProjectExecuting checks if a project has an active execution
func (ce *ClaudeExecutor) IsProjectExecuting(projectID string) bool {
	ce.mu.Lock()
	defer ce.mu.Unlock()
	_, exists := ce.activeProcesses[projectID]
	return exists
}

// registerProcess registers a new process for tracking
func (ce *ClaudeExecutor) registerProcess(projectID string, info *ProcessInfo) error {
	ce.mu.Lock()
	defer ce.mu.Unlock()

	// Check if project already has an active process
	if _, exists := ce.activeProcesses[projectID]; exists {
		return errors.New(errors.CodeProcessActive,
			"project already has an active execution")
	}

	// Check concurrent execution limit
	if len(ce.activeProcesses) >= ce.config.MaxConcurrentExecutions {
		return errors.NewResourceLimitError("concurrent executions",
			ce.config.MaxConcurrentExecutions, len(ce.activeProcesses))
	}

	ce.activeProcesses[projectID] = info
	return nil
}

// unregisterProcess removes a process from tracking
func (ce *ClaudeExecutor) unregisterProcess(projectID string) {
	ce.mu.Lock()
	defer ce.mu.Unlock()
	delete(ce.activeProcesses, projectID)
}

// getProcess retrieves process info for a project
func (ce *ClaudeExecutor) getProcess(projectID string) (*ProcessInfo, error) {
	ce.mu.Lock()
	defer ce.mu.Unlock()

	info, exists := ce.activeProcesses[projectID]
	if !exists {
		return nil, errors.New(errors.CodeProcessNotFound,
			"no active process found for project")
	}

	return info, nil
}

// createTimeoutContext creates a context with timeout for process execution
func (ce *ClaudeExecutor) createTimeoutContext(timeout time.Duration) (context.Context, context.CancelFunc) {
	if timeout <= 0 {
		timeout = ce.config.DefaultTimeout
	}

	return context.WithTimeout(context.Background(), timeout)
}

// cleanupProcess performs cleanup after process termination
func (ce *ClaudeExecutor) cleanupProcess(projectID string, info *ProcessInfo) {
	// Cancel context if not already cancelled
	if info.Cancel != nil {
		info.Cancel()
	}

	// Unregister the process
	ce.unregisterProcess(projectID)

	ce.logger.Debug("Process cleanup completed",
		"project_id", projectID,
		"duration", time.Since(info.StartTime))
}

// GetStats returns executor statistics
func (ce *ClaudeExecutor) GetStats() map[string]interface{} {
	ce.mu.Lock()
	defer ce.mu.Unlock()

	activeProjects := make([]string, 0, len(ce.activeProcesses))
	for projectID := range ce.activeProcesses {
		activeProjects = append(activeProjects, projectID)
	}

	return map[string]interface{}{
		"active_processes":          len(ce.activeProcesses),
		"max_concurrent_executions": ce.config.MaxConcurrentExecutions,
		"default_timeout":           ce.config.DefaultTimeout.String(),
		"active_projects":           activeProjects,
	}
}

// Shutdown gracefully shuts down the executor
func (ce *ClaudeExecutor) Shutdown(ctx context.Context) error {
	ce.mu.Lock()
	processes := make([]*ProcessInfo, 0, len(ce.activeProcesses))
	for _, info := range ce.activeProcesses {
		processes = append(processes, info)
	}
	ce.mu.Unlock()

	// Cancel all active processes
	for _, info := range processes {
		if info.Cancel != nil {
			info.Cancel()
		}
	}

	// Wait for processes to terminate or context to expire
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return fmt.Errorf("shutdown timeout: %d processes still active", ce.GetActiveProcessCount())
		case <-ticker.C:
			if ce.GetActiveProcessCount() == 0 {
				return nil
			}
		}
	}
}
