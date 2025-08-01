package executor

import (
	"context"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// ExecuteWithProject executes Claude CLI for a specific project
func (ce *ClaudeExecutor) ExecuteWithProject(projectID, projectPath string, options ExecuteOptions) (*ExecuteResult, error) {
	// Create a project model
	project := &models.Project{
		ID:   projectID,
		Path: projectPath,
	}

	// Check if project is already executing
	if ce.IsProjectExecuting(projectID) {
		return nil, errors.New(errors.CodeProcessActive,
			"project %s already has an active execution", projectID)
	}

	// Check concurrent execution limit
	if ce.GetActiveProcessCount() >= ce.config.MaxConcurrentExecutions {
		return nil, errors.New(errors.CodeResourceLimit,
			"maximum concurrent executions (%d) reached", ce.config.MaxConcurrentExecutions)
	}

	// Use the streaming execute method
	return ce.executeInternalWithStreaming(project, options, nil)
}

// ExecuteWithContext executes Claude CLI with a cancellable context
func (ce *ClaudeExecutor) ExecuteWithContext(ctx context.Context, projectID, projectPath string, options ExecuteOptions) (*ExecuteResult, error) {
	// Create a derived context with timeout if specified
	var cancel context.CancelFunc
	if options.Timeout > 0 {
		ctx, cancel = context.WithTimeout(ctx, options.Timeout)
	} else {
		ctx, cancel = context.WithTimeout(ctx, ce.config.DefaultTimeout)
	}
	defer cancel()

	// Monitor context cancellation
	done := make(chan struct{})
	go func() {
		<-ctx.Done()
		close(done)
	}()

	// Execute with standard method
	resultChan := make(chan struct {
		result *ExecuteResult
		err    error
	}, 1)

	go func() {
		result, err := ce.ExecuteWithProject(projectID, projectPath, options)
		select {
		case resultChan <- struct {
			result *ExecuteResult
			err    error
		}{result, err}:
		case <-done:
			// Context was cancelled
		}
	}()

	// Wait for either completion or cancellation
	select {
	case res := <-resultChan:
		return res.result, res.err
	case <-done:
		// Ensure process cleanup on cancellation
		ce.mu.Lock()
		if info, exists := ce.activeProcesses[projectID]; exists {
			if info.Cancel != nil {
				info.Cancel()
			}
		}
		ce.mu.Unlock()
		return nil, ctx.Err()
	}
}
