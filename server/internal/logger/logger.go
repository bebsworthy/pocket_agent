// Package logger provides structured logging for the Pocket Agent server.
package logger

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

// contextKey is a type for context keys.
type contextKey string

const (
	// CorrelationIDKey is the context key for correlation IDs.
	CorrelationIDKey contextKey = "correlation_id"

	// RequestIDKey is the context key for request IDs.
	RequestIDKey contextKey = "request_id"

	// ProjectIDKey is the context key for project IDs.
	ProjectIDKey contextKey = "project_id"

	// SessionIDKey is the context key for session IDs.
	SessionIDKey contextKey = "session_id"
)

// Logger wraps slog.Logger with convenience methods.
type Logger struct {
	*slog.Logger
	mu       sync.RWMutex
	handlers []slog.Handler
}

// Config represents logger configuration.
type Config struct {
	Level    string
	Format   string // "json" or "text"
	Output   io.Writer
	FilePath string
}

// New creates a new logger with the specified configuration.
func New(level string) *Logger {
	return NewWithConfig(Config{
		Level:  level,
		Format: "json",
		Output: os.Stdout,
	})
}

// NewWithConfig creates a new logger with full configuration.
func NewWithConfig(cfg Config) *Logger {
	var logLevel slog.Level
	switch strings.ToLower(cfg.Level) {
	case "debug":
		logLevel = slog.LevelDebug
	case "info":
		logLevel = slog.LevelInfo
	case "warn", "warning":
		logLevel = slog.LevelWarn
	case "error":
		logLevel = slog.LevelError
	default:
		logLevel = slog.LevelInfo
	}

	opts := &slog.HandlerOptions{
		Level: logLevel,
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			// Customize time format
			if a.Key == slog.TimeKey {
				return slog.Attr{
					Key:   a.Key,
					Value: slog.StringValue(a.Value.Time().Format(time.RFC3339Nano)),
				}
			}
			// Shorten source file paths
			if a.Key == slog.SourceKey {
				if src, ok := a.Value.Any().(*slog.Source); ok {
					// Get just the filename and line
					a.Value = slog.StringValue(fmt.Sprintf("%s:%d", filepath.Base(src.File), src.Line))
				}
			}
			return a
		},
		AddSource: true,
	}

	var handlers []slog.Handler

	// Console handler
	var consoleHandler slog.Handler
	if cfg.Format == "text" {
		consoleHandler = slog.NewTextHandler(cfg.Output, opts)
	} else {
		consoleHandler = slog.NewJSONHandler(cfg.Output, opts)
	}
	handlers = append(handlers, &contextHandler{Handler: consoleHandler})

	// File handler if specified
	if cfg.FilePath != "" {
		file, err := os.OpenFile(cfg.FilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
		if err == nil {
			fileHandler := slog.NewJSONHandler(file, opts)
			handlers = append(handlers, &contextHandler{Handler: fileHandler})
		}
	}

	// Create multi-handler
	multiHandler := &multiHandler{handlers: handlers}
	logger := slog.New(multiHandler)

	return &Logger{
		Logger:   logger,
		handlers: handlers,
	}
}

// WithContext returns a logger with context values added as attributes.
func (l *Logger) WithContext(ctx context.Context, attrs ...interface{}) *Logger {
	// Extract context values
	var contextAttrs []interface{}

	if correlationID, ok := ctx.Value(CorrelationIDKey).(string); ok && correlationID != "" {
		contextAttrs = append(contextAttrs, slog.String("correlation_id", correlationID))
	}

	if requestID, ok := ctx.Value(RequestIDKey).(string); ok && requestID != "" {
		contextAttrs = append(contextAttrs, slog.String("request_id", requestID))
	}

	if projectID, ok := ctx.Value(ProjectIDKey).(string); ok && projectID != "" {
		contextAttrs = append(contextAttrs, slog.String("project_id", projectID))
	}

	if sessionID, ok := ctx.Value(SessionIDKey).(string); ok && sessionID != "" {
		contextAttrs = append(contextAttrs, slog.String("session_id", sessionID))
	}

	// Combine context attributes with provided attributes
	allAttrs := append(contextAttrs, attrs...)

	return &Logger{Logger: l.With(allAttrs...)}
}

// WithFields returns a logger with additional fields.
func (l *Logger) WithFields(fields map[string]interface{}) *Logger {
	attrs := make([]interface{}, 0, len(fields)*2)
	for k, v := range fields {
		attrs = append(attrs, slog.Any(k, v))
	}
	return &Logger{Logger: l.With(attrs...)}
}

// WithError returns a logger with an error field.
func (l *Logger) WithError(err error) *Logger {
	if err == nil {
		return l
	}
	return &Logger{Logger: l.With(FormatError(err))}
}

// Fatal logs a fatal error and exits the program.
func (l *Logger) Fatal(msg string, args ...interface{}) {
	l.Error(msg, args...)
	os.Exit(1)
}

// LogRequest logs an HTTP request with standard fields.
func (l *Logger) LogRequest(method, path string, statusCode int, duration time.Duration, size int64) {
	l.Info("http_request",
		slog.String("method", method),
		slog.String("path", path),
		slog.Int("status", statusCode),
		slog.Duration("duration", duration),
		slog.Int64("size", size),
	)
}

// NewNop creates a no-op logger for testing.
func NewNop() *Logger {
	return &Logger{Logger: slog.New(slog.NewTextHandler(io.Discard, nil))}
}

// FormatError formats an error for logging with additional context.
func FormatError(err error) slog.Attr {
	if err == nil {
		return slog.String("error", "<nil>")
	}

	attrs := []interface{}{
		slog.String("message", err.Error()),
		slog.String("type", fmt.Sprintf("%T", err)),
	}

	// Add stack trace for panic recovery
	if _, ok := err.(runtime.Error); ok {
		buf := make([]byte, 4096)
		n := runtime.Stack(buf, false)
		attrs = append(attrs, slog.String("stack", string(buf[:n])))
	}

	return slog.Group("error", attrs...)
}

// contextHandler extracts values from context and adds them as attributes.
type contextHandler struct {
	slog.Handler
}

func (h *contextHandler) Handle(ctx context.Context, r slog.Record) error {
	// Context values are already added via WithContext
	return h.Handler.Handle(ctx, r)
}

// multiHandler sends log records to multiple handlers.
type multiHandler struct {
	handlers []slog.Handler
	mu       sync.RWMutex
}

func (h *multiHandler) Enabled(ctx context.Context, level slog.Level) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, handler := range h.handlers {
		if handler.Enabled(ctx, level) {
			return true
		}
	}
	return false
}

func (h *multiHandler) Handle(ctx context.Context, r slog.Record) error {
	h.mu.RLock()
	defer h.mu.RUnlock()

	var errs []error
	for _, handler := range h.handlers {
		if err := handler.Handle(ctx, r); err != nil {
			errs = append(errs, err)
		}
	}

	if len(errs) > 0 {
		return fmt.Errorf("multi-handler errors: %v", errs)
	}
	return nil
}

func (h *multiHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	h.mu.RLock()
	defer h.mu.RUnlock()

	newHandlers := make([]slog.Handler, len(h.handlers))
	for i, handler := range h.handlers {
		newHandlers[i] = handler.WithAttrs(attrs)
	}

	return &multiHandler{handlers: newHandlers}
}

func (h *multiHandler) WithGroup(name string) slog.Handler {
	h.mu.RLock()
	defer h.mu.RUnlock()

	newHandlers := make([]slog.Handler, len(h.handlers))
	for i, handler := range h.handlers {
		newHandlers[i] = handler.WithGroup(name)
	}

	return &multiHandler{handlers: newHandlers}
}

// SetLevel dynamically changes the log level.
func (l *Logger) SetLevel(level string) {
	// This would require storing and updating the handler options
	// For now, log the attempt
	l.Info("log level change requested", slog.String("new_level", level))
}

// Close closes any file handlers.
func (l *Logger) Close() error {
	// Implementation would close file handlers if we tracked them
	return nil
}
