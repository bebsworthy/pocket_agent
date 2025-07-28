package validation

import (
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/boyd/pocket_agent/server/internal/errors"
)

const (
	// MaxMessageSize is the default maximum message size (1MB)
	MaxMessageSize = 1024 * 1024
	// MaxPathLength is the maximum allowed path length
	MaxPathLength = 4096
	// MaxPromptLength is the maximum allowed prompt length
	MaxPromptLength = 100000
)

var (
	// pathTraversalPattern matches potential path traversal attempts
	pathTraversalPattern = regexp.MustCompile(`\.\.(/|\\|$)`)

	// invalidPathChars matches characters that are invalid in paths
	invalidPathChars = regexp.MustCompile(`[\x00-\x1f<>:"|?*]`)
)

// Validator provides validation functionality for the WebSocket API
type Validator struct {
	MaxMessageSize  int
	MaxPathLength   int
	MaxPromptLength int
	AllowedPaths    []string // Whitelist of allowed path prefixes
	DeniedPaths     []string // Blacklist of denied path prefixes
}

// NewValidator creates a new validator with default settings
func NewValidator() *Validator {
	return &Validator{
		MaxMessageSize:  MaxMessageSize,
		MaxPathLength:   MaxPathLength,
		MaxPromptLength: MaxPromptLength,
		DeniedPaths: []string{
			"/etc",
			"/sys",
			"/proc",
			"/dev",
			"/root",
			"/var/log",
			"/private/etc",
			"/private/var",
			"/System",
			"/Library",
			"C:\\Windows",
			"C:\\Program Files",
		},
	}
}

// ValidatePath validates a filesystem path for security and correctness
func (v *Validator) ValidatePath(path string) error {
	if path == "" {
		return errors.NewValidationError("path cannot be empty")
	}

	// Check path length
	if len(path) > v.MaxPathLength {
		return errors.NewValidationError("path exceeds maximum length of %d characters", v.MaxPathLength)
	}

	// Prevent tilde expansion paths for security
	if strings.HasPrefix(path, "~") {
		return errors.NewInvalidPathError(path, "tilde expansion paths are not allowed for security reasons")
	}

	// Check for path traversal attempts BEFORE cleaning
	if pathTraversalPattern.MatchString(path) {
		return errors.NewInvalidPathError(path, "path traversal detected")
	}

	// Check for invalid characters
	if invalidPathChars.MatchString(path) {
		return errors.NewInvalidPathError(path, "path contains invalid characters")
	}

	// Ensure path is absolute
	if !v.IsAbsolutePath(path) {
		return errors.NewInvalidPathError(path, "path must be absolute")
	}

	// Clean the path to resolve any symbolic links or redundant elements
	cleanPath := filepath.Clean(path)
	
	// Double-check after cleaning - if cleaned path differs significantly, it might be suspicious
	if strings.Contains(path, "..") && !strings.Contains(cleanPath, "..") {
		// Path was attempting traversal that got cleaned
		return errors.NewInvalidPathError(path, "path traversal detected")
	}

	// Check against denied paths
	for _, denied := range v.DeniedPaths {
		if strings.HasPrefix(cleanPath, denied) {
			return errors.NewInvalidPathError(path, "path is in restricted directory")
		}
	}

	// Check against allowed paths if configured
	if len(v.AllowedPaths) > 0 {
		allowed := false
		for _, prefix := range v.AllowedPaths {
			if strings.HasPrefix(cleanPath, prefix) {
				allowed = true
				break
			}
		}
		if !allowed {
			return errors.NewInvalidPathError(path, "path is not in allowed directories")
		}
	}

	// Check if path exists
	info, err := os.Stat(cleanPath)
	if err != nil {
		if os.IsNotExist(err) {
			// If the original path contained .. but the cleaned path doesn't,
			// it was a traversal attempt that resulted in a non-existent path
			if strings.Contains(path, "..") && !strings.Contains(cleanPath, "..") {
				return errors.NewInvalidPathError(path, "path traversal detected")
			}
			return errors.NewInvalidPathError(path, "path does not exist")
		}
		return errors.NewFileOperationError("stat", err)
	}

	// Ensure it's a directory
	if !info.IsDir() {
		return errors.NewInvalidPathError(path, "path is not a directory")
	}

	return nil
}

// IsAbsolutePath checks if a path is absolute
func (v *Validator) IsAbsolutePath(path string) bool {
	return filepath.IsAbs(path)
}

// ValidateProjectNesting checks if a new project path would nest with existing projects
func (v *Validator) ValidateProjectNesting(newPath string, existingPaths []string) error {
	newClean := filepath.Clean(newPath)

	for _, existing := range existingPaths {
		existingClean := filepath.Clean(existing)

		// Check if paths are the same first
		if newClean == existingClean {
			return errors.New(errors.CodeProjectExists,
				"project already exists at this path")
		}

		// Check if new path is parent of existing
		if strings.HasPrefix(existingClean+"/", newClean+"/") {
			return errors.New(errors.CodeProjectNesting,
				"project would be parent of existing project at %s", existingClean)
		}

		// Check if new path is child of existing
		if strings.HasPrefix(newClean+"/", existingClean+"/") {
			return errors.New(errors.CodeProjectNesting,
				"project would be child of existing project at %s", existingClean)
		}
	}

	return nil
}

// ValidateMessageSize validates that a message doesn't exceed size limits
func (v *Validator) ValidateMessageSize(data []byte) error {
	if len(data) > v.MaxMessageSize {
		return errors.New(errors.CodeMessageSizeLimit,
			"message size %d exceeds maximum of %d bytes", len(data), v.MaxMessageSize)
	}
	return nil
}

// ValidateMessageBatch validates a batch of messages for individual and total size limits
func (v *Validator) ValidateMessageBatch(messages []json.RawMessage, maxTotalSize int) error {
	totalSize := 0

	for i, msg := range messages {
		// Validate individual message size
		msgSize := len(msg)
		if msgSize > v.MaxMessageSize {
			return errors.New(errors.CodeMessageSizeLimit,
				"message[%d] size %d exceeds maximum of %d bytes", i, msgSize, v.MaxMessageSize)
		}

		totalSize += msgSize

		// Check running total to fail fast
		if maxTotalSize > 0 && totalSize > maxTotalSize {
			return errors.New(errors.CodeMessageSizeLimit,
				"total message batch size %d exceeds maximum of %d bytes", totalSize, maxTotalSize)
		}
	}

	return nil
}

// ValidateJSON validates that data is valid JSON
func (v *Validator) ValidateJSON(data []byte) error {
	var js json.RawMessage
	if err := json.Unmarshal(data, &js); err != nil {
		return errors.NewJSONParsingError(err)
	}
	return nil
}

// ValidatePrompt validates a Claude prompt
func (v *Validator) ValidatePrompt(prompt string) error {
	if prompt == "" {
		return errors.NewValidationError("prompt cannot be empty")
	}

	if len(prompt) > v.MaxPromptLength {
		return errors.NewValidationError("prompt exceeds maximum length of %d characters", v.MaxPromptLength)
	}

	// Check for control characters that might cause issues
	for i, r := range prompt {
		if r < 0x20 && r != '\n' && r != '\r' && r != '\t' {
			return errors.NewValidationError("prompt contains invalid control character at position %d", i)
		}
	}

	return nil
}

// ValidateProjectID validates a project ID
func (v *Validator) ValidateProjectID(id string) error {
	if id == "" {
		return errors.NewValidationError("project ID cannot be empty")
	}

	// Assuming UUIDs, but could be adjusted for other ID formats
	if len(id) != 36 {
		return errors.NewValidationError("invalid project ID format")
	}

	return nil
}

// ValidateSessionID validates a session ID
func (v *Validator) ValidateSessionID(id string) error {
	if id == "" {
		return errors.NewValidationError("session ID cannot be empty")
	}

	// Basic validation - adjust based on actual session ID format
	if len(id) < 16 || len(id) > 128 {
		return errors.NewValidationError("invalid session ID format")
	}

	return nil
}

// SanitizePath removes potentially dangerous elements from a path
func (v *Validator) SanitizePath(path string) string {
	// First, replace invalid characters before cleaning
	// This preserves the structure for the test expectations
	cleaned := invalidPathChars.ReplaceAllString(path, "_")
	
	// Remove path traversal attempts by replacing .. segments
	cleaned = pathTraversalPattern.ReplaceAllString(cleaned, "_/")
	
	// Now clean the path
	cleaned = filepath.Clean(cleaned)

	return cleaned
}

// SanitizeString removes potentially dangerous content from a string
func (v *Validator) SanitizeString(s string, maxLength int) string {
	// Truncate if too long
	if len(s) > maxLength {
		s = s[:maxLength]
	}

	// Remove null bytes and other control characters
	var builder strings.Builder
	for _, r := range s {
		if r >= 0x20 || r == '\n' || r == '\r' || r == '\t' {
			builder.WriteRune(r)
		}
	}

	return builder.String()
}

// ValidateClaudeOptions validates Claude execution options
func (v *Validator) ValidateClaudeOptions(opts map[string]interface{}) error {
	// Validate specific options
	if val, ok := opts["permission_mode"]; ok {
		mode, ok := val.(string)
		if !ok {
			return errors.NewValidationError("permission_mode must be a string")
		}
		validModes := []string{"", "auto", "always", "never"}
		valid := false
		for _, vm := range validModes {
			if mode == vm {
				valid = true
				break
			}
		}
		if !valid {
			return errors.NewValidationError("invalid permission_mode: %s", mode)
		}
	}

	// Validate tool lists
	for _, key := range []string{"allowed_tools", "disallowed_tools"} {
		if val, ok := opts[key]; ok {
			tools, ok := val.([]interface{})
			if !ok {
				return errors.NewValidationError("%s must be an array", key)
			}
			for i, tool := range tools {
				if _, ok := tool.(string); !ok {
					return errors.NewValidationError("%s[%d] must be a string", key, i)
				}
			}
		}
	}

	// Validate add_dirs
	if val, ok := opts["add_dirs"]; ok {
		dirs, ok := val.([]interface{})
		if !ok {
			return errors.NewValidationError("add_dirs must be an array")
		}
		for i, dir := range dirs {
			dirStr, ok := dir.(string)
			if !ok {
				return errors.NewValidationError("add_dirs[%d] must be a string", i)
			}
			// Validate each directory path
			if err := v.ValidatePath(dirStr); err != nil {
				return errors.NewValidationError("add_dirs[%d]: %v", i, err)
			}
		}
	}

	return nil
}

// ValidateEnvironment performs startup validation of the environment
func (v *Validator) ValidateEnvironment() error {
	// Check if claude CLI is available
	if _, err := os.Stat("/usr/local/bin/claude"); os.IsNotExist(err) {
		// Try other common locations
		paths := []string{
			"/usr/bin/claude",
			"/opt/homebrew/bin/claude",
			"/home/linuxbrew/.linuxbrew/bin/claude",
		}

		found := false
		for _, path := range paths {
			if _, err := os.Stat(path); err == nil {
				found = true
				break
			}
		}

		if !found {
			// Check PATH
			pathEnv := os.Getenv("PATH")
			for _, dir := range filepath.SplitList(pathEnv) {
				if _, err := os.Stat(filepath.Join(dir, "claude")); err == nil {
					found = true
					break
				}
			}
		}

		if !found {
			return errors.New(errors.CodeClaudeNotFound,
				"Claude CLI not found. Please install it first.")
		}
	}

	return nil
}
