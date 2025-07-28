package errors

import (
	"errors"
	"testing"
)

func TestNew(t *testing.T) {
	err := New(CodeInvalidPath, "test error: %s", "details")

	if err.Code != CodeInvalidPath {
		t.Errorf("expected code %s, got %s", CodeInvalidPath, err.Code)
	}

	expectedMsg := "test error: details"
	if err.Message != expectedMsg {
		t.Errorf("expected message %s, got %s", expectedMsg, err.Message)
	}

	if err.Details == nil {
		t.Error("expected details map to be initialized")
	}

	if len(err.Stack) == 0 {
		t.Error("expected stack trace to be captured")
	}
}

func TestWrap(t *testing.T) {
	originalErr := errors.New("original error")
	wrapped := Wrap(originalErr, CodeInternalError, "wrapped: %s", "context")

	if wrapped.Code != CodeInternalError {
		t.Errorf("expected code %s, got %s", CodeInternalError, wrapped.Code)
	}

	expectedMsg := "wrapped: context"
	if wrapped.Message != expectedMsg {
		t.Errorf("expected message %s, got %s", expectedMsg, wrapped.Message)
	}

	if wrapped.Cause != originalErr {
		t.Error("expected cause to be set to original error")
	}

	// Test wrapping an AppError
	appErr := New(CodeValidationFailed, "validation failed")
	appErr.WithDetail("field", "value")

	wrapped2 := Wrap(appErr, CodeInternalError, "wrapped app error")

	if wrapped2.Cause != appErr {
		t.Error("expected cause to be set to app error")
	}

	if _, exists := wrapped2.Details["field"]; !exists {
		t.Error("expected details to be preserved when wrapping AppError")
	}
}

func TestWithDetail(t *testing.T) {
	err := New(CodeInvalidPath, "test error")
	err.WithDetail("path", "/test/path").
		WithDetail("reason", "invalid characters")

	if len(err.Details) != 2 {
		t.Errorf("expected 2 details, got %d", len(err.Details))
	}

	if err.Details["path"] != "/test/path" {
		t.Errorf("expected path detail to be /test/path, got %v", err.Details["path"])
	}

	if err.Details["reason"] != "invalid characters" {
		t.Errorf("expected reason detail to be 'invalid characters', got %v", err.Details["reason"])
	}
}

func TestWithCause(t *testing.T) {
	cause := errors.New("underlying error")
	err := New(CodeFileOperation, "file operation failed")
	err.WithCause(cause)

	if err.Cause != cause {
		t.Error("expected cause to be set")
	}

	if err.Unwrap() != cause {
		t.Error("expected Unwrap to return cause")
	}
}

func TestIsCode(t *testing.T) {
	err := New(CodeProjectNotFound, "project not found")

	if !IsCode(err, CodeProjectNotFound) {
		t.Error("expected IsCode to return true for matching code")
	}

	if IsCode(err, CodeInternalError) {
		t.Error("expected IsCode to return false for non-matching code")
	}

	if IsCode(nil, CodeProjectNotFound) {
		t.Error("expected IsCode to return false for nil error")
	}

	regularErr := errors.New("regular error")
	if IsCode(regularErr, CodeProjectNotFound) {
		t.Error("expected IsCode to return false for non-AppError")
	}
}

func TestGetCode(t *testing.T) {
	err := New(CodeExecutionTimeout, "timeout")

	if GetCode(err) != CodeExecutionTimeout {
		t.Errorf("expected code %s, got %s", CodeExecutionTimeout, GetCode(err))
	}

	if GetCode(nil) != "" {
		t.Error("expected empty string for nil error")
	}

	regularErr := errors.New("regular error")
	if GetCode(regularErr) != CodeInternalError {
		t.Errorf("expected %s for non-AppError, got %s", CodeInternalError, GetCode(regularErr))
	}
}

func TestToJSON(t *testing.T) {
	err := New(CodeResourceLimit, "limit exceeded")
	err.WithDetail("resource", "connections").
		WithDetail("limit", 100).
		WithDetail("current", 105)

	json := err.ToJSON()

	if json["code"] != CodeResourceLimit {
		t.Errorf("expected code %s in JSON, got %v", CodeResourceLimit, json["code"])
	}

	if json["message"] != "limit exceeded" {
		t.Errorf("expected message 'limit exceeded' in JSON, got %v", json["message"])
	}

	details, ok := json["details"].(map[string]interface{})
	if !ok {
		t.Fatal("expected details to be a map")
	}

	if details["resource"] != "connections" {
		t.Errorf("expected resource detail 'connections', got %v", details["resource"])
	}
}

func TestSanitization(t *testing.T) {
	err := New(CodeInternalError, "internal error")
	err.WithDetail("password", "secret123").
		WithDetail("token", "auth-token").
		WithDetail("safe_field", "safe_value").
		WithDetail("system_path", "/home/user/data")

	json := err.ToJSON()

	details, ok := json["details"].(map[string]interface{})
	if !ok {
		t.Fatal("expected details to be a map")
	}

	// Sensitive fields should be filtered out
	if _, exists := details["password"]; exists {
		t.Error("expected password to be filtered out")
	}

	if _, exists := details["token"]; exists {
		t.Error("expected token to be filtered out")
	}

	if _, exists := details["system_path"]; exists {
		t.Error("expected system_path to be filtered out")
	}

	// Safe fields should remain
	if details["safe_field"] != "safe_value" {
		t.Error("expected safe_field to be preserved")
	}
}

func TestPathSanitization(t *testing.T) {
	err := New(CodeFileOperation, "file error")
	err.WithDetail("path", "/Users/john/project/file.txt")

	json := err.ToJSON()
	details := json["details"].(map[string]interface{})

	path, ok := details["path"].(string)
	if !ok {
		t.Fatal("expected path to be a string")
	}

	if path != "~/project/file.txt" {
		t.Errorf("expected sanitized path ~/project/file.txt, got %s", path)
	}
}

func TestCommonConstructors(t *testing.T) {
	// Test NewValidationError
	valErr := NewValidationError("field %s is invalid", "email")
	if valErr.Code != CodeValidationFailed {
		t.Errorf("expected code %s, got %s", CodeValidationFailed, valErr.Code)
	}
	if valErr.Message != "field email is invalid" {
		t.Errorf("unexpected message: %s", valErr.Message)
	}

	// Test NewInvalidPathError
	pathErr := NewInvalidPathError("/test/path", "contains invalid characters")
	if pathErr.Code != CodeInvalidPath {
		t.Errorf("expected code %s, got %s", CodeInvalidPath, pathErr.Code)
	}
	if pathErr.Details["path"] != "/test/path" {
		t.Errorf("expected path detail, got %v", pathErr.Details["path"])
	}

	// Test NewProjectNotFoundError
	projErr := NewProjectNotFoundError("proj-123")
	if projErr.Code != CodeProjectNotFound {
		t.Errorf("expected code %s, got %s", CodeProjectNotFound, projErr.Code)
	}
	if projErr.Details["project_id"] != "proj-123" {
		t.Errorf("expected project_id detail, got %v", projErr.Details["project_id"])
	}

	// Test NewResourceLimitError
	resErr := NewResourceLimitError("connections", 100, 105)
	if resErr.Code != CodeResourceLimit {
		t.Errorf("expected code %s, got %s", CodeResourceLimit, resErr.Code)
	}
	if resErr.Details["limit"] != 100 {
		t.Errorf("expected limit 100, got %v", resErr.Details["limit"])
	}

	// Test NewExecutionTimeoutError
	timeErr := NewExecutionTimeoutError("proj-123", "5m")
	if timeErr.Code != CodeExecutionTimeout {
		t.Errorf("expected code %s, got %s", CodeExecutionTimeout, timeErr.Code)
	}

	// Test NewInternalError
	cause := errors.New("underlying issue")
	intErr := NewInternalError(cause)
	if intErr.Code != CodeInternalError {
		t.Errorf("expected code %s, got %s", CodeInternalError, intErr.Code)
	}
	if intErr.Cause != cause {
		t.Error("expected cause to be set")
	}

	// Test NewFileOperationError
	fileErr := NewFileOperationError("read", cause)
	if fileErr.Code != CodeFileOperation {
		t.Errorf("expected code %s, got %s", CodeFileOperation, fileErr.Code)
	}

	// Test NewJSONParsingError
	jsonErr := NewJSONParsingError(cause)
	if jsonErr.Code != CodeJSONParsing {
		t.Errorf("expected code %s, got %s", CodeJSONParsing, jsonErr.Code)
	}
}
