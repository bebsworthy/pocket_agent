package errors

import (
	"encoding/json"
	"fmt"
	"runtime"
	"strings"
)

// ErrorCode represents standardized error codes for the WebSocket API
type ErrorCode string

const (
	// Path and validation errors
	CodeInvalidPath      ErrorCode = "INVALID_PATH"
	CodeProjectNesting   ErrorCode = "PROJECT_NESTING"
	CodeValidationFailed ErrorCode = "VALIDATION_FAILED"

	// Project errors
	CodeProjectNotFound ErrorCode = "PROJECT_NOT_FOUND"
	CodeProjectExists   ErrorCode = "PROJECT_EXISTS"
	CodeProcessActive   ErrorCode = "PROCESS_ACTIVE"

	// Execution errors
	CodeExecutionTimeout ErrorCode = "EXECUTION_TIMEOUT"
	CodeClaudeNotFound   ErrorCode = "CLAUDE_NOT_FOUND"
	CodeExecutionFailed  ErrorCode = "EXECUTION_FAILED"
	CodeProcessNotFound  ErrorCode = "PROCESS_NOT_FOUND"

	// Resource errors
	CodeResourceLimit    ErrorCode = "RESOURCE_LIMIT"
	CodeDiskSpaceLow     ErrorCode = "DISK_SPACE_LOW"
	CodeConnectionLimit  ErrorCode = "CONNECTION_LIMIT"
	CodeMessageSizeLimit ErrorCode = "MESSAGE_SIZE_LIMIT"

	// System errors
	CodeInternalError  ErrorCode = "INTERNAL_ERROR"
	CodeFileOperation  ErrorCode = "FILE_OPERATION"
	CodeJSONParsing    ErrorCode = "JSON_PARSING"
	CodeWebSocketError ErrorCode = "WEBSOCKET_ERROR"

	// Permission errors
	CodePermissionDenied ErrorCode = "PERMISSION_DENIED"
	CodeUnauthorized     ErrorCode = "UNAUTHORIZED"
)

// AppError represents a structured application error
type AppError struct {
	Code    ErrorCode              `json:"code"`
	Message string                 `json:"message"`
	Details map[string]interface{} `json:"details,omitempty"`
	Cause   error                  `json:"-"`
	Stack   []string               `json:"-"`
}

// Error implements the error interface
func (e *AppError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("%s: %s (caused by: %v)", e.Code, e.Message, e.Cause)
	}
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

// Unwrap returns the underlying error
func (e *AppError) Unwrap() error {
	return e.Cause
}

// WithDetail adds a detail to the error
func (e *AppError) WithDetail(key string, value interface{}) *AppError {
	if e.Details == nil {
		e.Details = make(map[string]interface{})
	}
	e.Details[key] = value
	return e
}

// WithCause adds an underlying cause to the error
func (e *AppError) WithCause(cause error) *AppError {
	e.Cause = cause
	return e
}

// ToJSON converts the error to a JSON-safe format
func (e *AppError) ToJSON() map[string]interface{} {
	result := map[string]interface{}{
		"code":    e.Code,
		"message": e.Message,
	}

	// Only include details if present
	if len(e.Details) > 0 {
		// Sanitize details to ensure no sensitive information
		sanitized := make(map[string]interface{})
		for k, v := range e.Details {
			// Filter out system paths and sensitive info
			if !isSensitiveKey(k) {
				sanitized[k] = sanitizeValue(v)
			}
		}
		if len(sanitized) > 0 {
			result["details"] = sanitized
		}
	}

	return result
}

// New creates a new AppError with stack trace
func New(code ErrorCode, message string, args ...interface{}) *AppError {
	if len(args) > 0 {
		message = fmt.Sprintf(message, args...)
	}

	err := &AppError{
		Code:    code,
		Message: message,
		Details: make(map[string]interface{}),
		Stack:   captureStack(),
	}

	return err
}

// Wrap wraps an existing error with additional context
func Wrap(err error, code ErrorCode, message string, args ...interface{}) *AppError {
	if len(args) > 0 {
		message = fmt.Sprintf(message, args...)
	}

	// If already an AppError, preserve the original code and details
	if appErr, ok := err.(*AppError); ok {
		return &AppError{
			Code:    code,
			Message: message,
			Details: appErr.Details,
			Cause:   appErr,
			Stack:   captureStack(),
		}
	}

	return &AppError{
		Code:    code,
		Message: message,
		Details: make(map[string]interface{}),
		Cause:   err,
		Stack:   captureStack(),
	}
}

// IsCode checks if an error has a specific error code
func IsCode(err error, code ErrorCode) bool {
	if err == nil {
		return false
	}

	appErr, ok := err.(*AppError)
	if !ok {
		return false
	}

	return appErr.Code == code
}

// GetCode returns the error code from an error
func GetCode(err error) ErrorCode {
	if err == nil {
		return ""
	}

	appErr, ok := err.(*AppError)
	if !ok {
		return CodeInternalError
	}

	return appErr.Code
}

// captureStack captures the current stack trace
func captureStack() []string {
	const maxFrames = 10
	stack := make([]string, 0, maxFrames)

	// Skip runtime frames and this function
	for i := 2; i < maxFrames+2; i++ {
		pc, file, line, ok := runtime.Caller(i)
		if !ok {
			break
		}

		// Get function name
		fn := runtime.FuncForPC(pc)
		if fn == nil {
			continue
		}

		// Skip runtime and testing frames
		fnName := fn.Name()
		if strings.Contains(fnName, "runtime.") || strings.Contains(fnName, "testing.") {
			continue
		}

		// Format: function - file:line
		stack = append(stack, fmt.Sprintf("%s - %s:%d", fnName, file, line))
	}

	return stack
}

// isSensitiveKey checks if a detail key might contain sensitive information
func isSensitiveKey(key string) bool {
	sensitive := []string{
		"password", "token", "secret", "key", "auth",
		"system_path", "home_dir", "pwd", "env",
	}

	lowerKey := strings.ToLower(key)
	for _, s := range sensitive {
		if strings.Contains(lowerKey, s) {
			return true
		}
	}

	return false
}

// sanitizeValue sanitizes a value to ensure it doesn't contain sensitive information
func sanitizeValue(v interface{}) interface{} {
	switch val := v.(type) {
	case string:
		// Remove absolute paths that might leak system information
		if strings.HasPrefix(val, "/home/") || strings.HasPrefix(val, "/Users/") {
			parts := strings.Split(val, "/")
			if len(parts) > 3 {
				// Keep only the relative part after the user directory
				return "~/" + strings.Join(parts[3:], "/")
			}
		}
		return val

	case error:
		// Convert errors to strings to avoid leaking internal types
		return val.Error()

	default:
		// For other types, ensure they're JSON-serializable
		if _, err := json.Marshal(val); err != nil {
			return fmt.Sprintf("%v", val)
		}
		return val
	}
}

// Common error constructors

// NewValidationError creates a validation error
func NewValidationError(message string, args ...interface{}) *AppError {
	return New(CodeValidationFailed, message, args...)
}

// NewInvalidPathError creates an invalid path error
func NewInvalidPathError(path string, reason string) *AppError {
	return New(CodeInvalidPath, "invalid path: %s", reason).
		WithDetail("path", path)
}

// NewProjectNotFoundError creates a project not found error
func NewProjectNotFoundError(projectID string) *AppError {
	return New(CodeProjectNotFound, "project not found").
		WithDetail("project_id", projectID)
}

// NewResourceLimitError creates a resource limit error
func NewResourceLimitError(resource string, limit int, current int) *AppError {
	return New(CodeResourceLimit, "resource limit exceeded for %s", resource).
		WithDetail("resource", resource).
		WithDetail("limit", limit).
		WithDetail("current", current)
}

// NewExecutionTimeoutError creates an execution timeout error
func NewExecutionTimeoutError(projectID string, duration string) *AppError {
	return New(CodeExecutionTimeout, "execution timed out after %s", duration).
		WithDetail("project_id", projectID).
		WithDetail("timeout", duration)
}

// NewInternalError creates an internal error with sanitized message
func NewInternalError(cause error) *AppError {
	// Never expose internal error details to clients
	return New(CodeInternalError, "an internal error occurred").
		WithCause(cause)
}

// NewFileOperationError creates a file operation error
func NewFileOperationError(operation string, cause error) *AppError {
	return Wrap(cause, CodeFileOperation, "file operation failed: %s", operation)
}

// NewJSONParsingError creates a JSON parsing error
func NewJSONParsingError(cause error) *AppError {
	return Wrap(cause, CodeJSONParsing, "failed to parse JSON")
}
