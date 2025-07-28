package logger

import (
	"bytes"
	"context"
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func TestLogLevels(t *testing.T) {
	tests := []struct {
		level    string
		logDebug bool
		logInfo  bool
		logWarn  bool
		logError bool
	}{
		{"debug", true, true, true, true},
		{"info", false, true, true, true},
		{"warn", false, false, true, true},
		{"error", false, false, false, true},
		{"invalid", false, true, true, true}, // defaults to info
	}

	for _, tt := range tests {
		t.Run(tt.level, func(t *testing.T) {
			var buf bytes.Buffer
			logger := NewWithConfig(Config{
				Level:  tt.level,
				Format: "json",
				Output: &buf,
			})

			logger.Debug("debug message")
			hasDebug := strings.Contains(buf.String(), "debug message")
			if hasDebug != tt.logDebug {
				t.Errorf("debug log: expected %v, got %v", tt.logDebug, hasDebug)
			}

			buf.Reset()
			logger.Info("info message")
			hasInfo := strings.Contains(buf.String(), "info message")
			if hasInfo != tt.logInfo {
				t.Errorf("info log: expected %v, got %v", tt.logInfo, hasInfo)
			}

			buf.Reset()
			logger.Warn("warn message")
			hasWarn := strings.Contains(buf.String(), "warn message")
			if hasWarn != tt.logWarn {
				t.Errorf("warn log: expected %v, got %v", tt.logWarn, hasWarn)
			}

			buf.Reset()
			logger.Error("error message")
			hasError := strings.Contains(buf.String(), "error message")
			if hasError != tt.logError {
				t.Errorf("error log: expected %v, got %v", tt.logError, hasError)
			}
		})
	}
}

func TestJSONFormat(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	logger.Info("test message", "key", "value", "number", 42)

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	if logEntry["msg"] != "test message" {
		t.Errorf("expected msg 'test message', got %v", logEntry["msg"])
	}
	if logEntry["key"] != "value" {
		t.Errorf("expected key 'value', got %v", logEntry["key"])
	}
	if logEntry["number"] != float64(42) {
		t.Errorf("expected number 42, got %v", logEntry["number"])
	}
	if _, ok := logEntry["time"]; !ok {
		t.Error("expected time field in log entry")
	}
	if _, ok := logEntry["level"]; !ok {
		t.Error("expected level field in log entry")
	}
}

func TestTextFormat(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "text",
		Output: &buf,
	})

	logger.Info("test message", "key", "value")

	output := buf.String()
	if !strings.Contains(output, "test message") {
		t.Error("expected log to contain 'test message'")
	}
	if !strings.Contains(output, "key=value") {
		t.Error("expected log to contain 'key=value'")
	}
}

func TestContextFields(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	ctx := context.Background()
	ctx = context.WithValue(ctx, CorrelationIDKey, "corr-123")
	ctx = context.WithValue(ctx, RequestIDKey, "req-456")
	ctx = context.WithValue(ctx, ProjectIDKey, "proj-789")
	ctx = context.WithValue(ctx, SessionIDKey, "sess-101")

	ctxLogger := logger.WithContext(ctx)
	ctxLogger.Info("test with context")

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	if logEntry["correlation_id"] != "corr-123" {
		t.Errorf("expected correlation_id 'corr-123', got %v", logEntry["correlation_id"])
	}
	if logEntry["request_id"] != "req-456" {
		t.Errorf("expected request_id 'req-456', got %v", logEntry["request_id"])
	}
	if logEntry["project_id"] != "proj-789" {
		t.Errorf("expected project_id 'proj-789', got %v", logEntry["project_id"])
	}
	if logEntry["session_id"] != "sess-101" {
		t.Errorf("expected session_id 'sess-101', got %v", logEntry["session_id"])
	}
}

func TestWithFields(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	fields := map[string]interface{}{
		"user_id": "user-123",
		"action":  "create_project",
		"count":   5,
	}

	fieldLogger := logger.WithFields(fields)
	fieldLogger.Info("action performed")

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	if logEntry["user_id"] != "user-123" {
		t.Errorf("expected user_id 'user-123', got %v", logEntry["user_id"])
	}
	if logEntry["action"] != "create_project" {
		t.Errorf("expected action 'create_project', got %v", logEntry["action"])
	}
	if logEntry["count"] != float64(5) {
		t.Errorf("expected count 5, got %v", logEntry["count"])
	}
}

func TestWithError(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	testErr := &customError{msg: "something went wrong"}
	errLogger := logger.WithError(testErr)
	errLogger.Error("operation failed")

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	errGroup, ok := logEntry["error"].(map[string]interface{})
	if !ok {
		t.Fatal("expected error field to be a map")
	}

	if errGroup["message"] != "something went wrong" {
		t.Errorf("expected error message 'something went wrong', got %v", errGroup["message"])
	}
	if !strings.Contains(errGroup["type"].(string), "customError") {
		t.Errorf("expected error type to contain 'customError', got %v", errGroup["type"])
	}
}

func TestLogRequest(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	logger.LogRequest("GET", "/api/projects", 200, 150*time.Millisecond, 1024)

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	if logEntry["msg"] != "http_request" {
		t.Errorf("expected msg 'http_request', got %v", logEntry["msg"])
	}
	if logEntry["method"] != "GET" {
		t.Errorf("expected method 'GET', got %v", logEntry["method"])
	}
	if logEntry["path"] != "/api/projects" {
		t.Errorf("expected path '/api/projects', got %v", logEntry["path"])
	}
	if logEntry["status"] != float64(200) {
		t.Errorf("expected status 200, got %v", logEntry["status"])
	}
	if logEntry["size"] != float64(1024) {
		t.Errorf("expected size 1024, got %v", logEntry["size"])
	}
}

func TestNopLogger(t *testing.T) {
	logger := NewNop()
	// Should not panic
	logger.Debug("debug")
	logger.Info("info")
	logger.Warn("warn")
	logger.Error("error")
}

func TestSourceLocation(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	logger.Info("test message")

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	// Should have source location
	if source, ok := logEntry["source"].(string); ok {
		if !strings.Contains(source, "logger_test.go:") {
			t.Errorf("expected source to contain 'logger_test.go:', got %v", source)
		}
		// Should contain line number
		parts := strings.Split(source, ":")
		if len(parts) != 2 {
			t.Errorf("expected source format 'file:line', got %v", source)
		}
	} else {
		t.Error("expected source field in log entry")
	}
}

func TestTimeFormat(t *testing.T) {
	var buf bytes.Buffer
	logger := NewWithConfig(Config{
		Level:  "info",
		Format: "json",
		Output: &buf,
	})

	logger.Info("test message")

	var logEntry map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &logEntry); err != nil {
		t.Fatalf("failed to parse JSON log: %v", err)
	}

	timeStr, ok := logEntry["time"].(string)
	if !ok {
		t.Fatal("expected time field to be a string")
	}

	// Should be RFC3339Nano format
	if _, err := time.Parse(time.RFC3339Nano, timeStr); err != nil {
		t.Errorf("time format is not RFC3339Nano: %v", err)
	}
}

// Test helper types
type customError struct {
	msg string
}

func (e *customError) Error() string {
	return e.msg
}
