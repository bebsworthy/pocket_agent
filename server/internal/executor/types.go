package executor

import (
	"time"

	"github.com/boyd/pocket_agent/server/internal/models"
)

// ExecuteCommand represents a command to execute Claude
// This matches the models.ExecuteCommand structure for WebSocket messages
type ExecuteCommand struct {
	Prompt  string                `json:"prompt"`
	Options *models.ClaudeOptions `json:"options,omitempty"`
}

// Execute runs Claude with the specified command for a project
// This is a wrapper that converts ExecuteCommand to ExecuteOptions
func (ce *ClaudeExecutor) Execute(project *models.Project, cmd ExecuteCommand) (*ExecuteResult, error) {
	// Convert ExecuteCommand to ExecuteOptions
	options := ExecuteOptions{
		Prompt:  cmd.Prompt,
		Timeout: ce.config.DefaultTimeout,
	}

	// Apply optional settings if provided
	if cmd.Options != nil {
		options.DangerouslySkipPermissions = cmd.Options.DangerouslySkipPermissions
		options.AllowedTools = cmd.Options.AllowedTools
		options.DisallowedTools = cmd.Options.DisallowedTools
		options.MCPConfig = cmd.Options.MCPConfig
		options.AppendSystemPrompt = cmd.Options.AppendSystemPrompt
		options.PermissionMode = cmd.Options.PermissionMode
		options.Model = cmd.Options.Model
		options.FallbackModel = cmd.Options.FallbackModel
		options.AddDirs = cmd.Options.AddDirs
		options.StrictMCPConfig = cmd.Options.StrictMCPConfig
	}

	// Call the main Execute method
	return ce.ExecuteWithOptions(project, options)
}

// ExecuteWithOptions is the renamed original Execute method
func (ce *ClaudeExecutor) ExecuteWithOptions(project *models.Project, options ExecuteOptions) (*ExecuteResult, error) {
	// This is the original Execute implementation
	// We'll rename the existing Execute method to this
	return ce.executeInternal(project, options)
}