package errors

import (
	"fmt"
	"log"
	"runtime/debug"
)

// RecoveryHandler provides panic recovery functionality
type RecoveryHandler struct {
	Logger       func(format string, args ...interface{})
	OnPanic      func(recovered interface{}, stack []byte)
	IncludeStack bool
}

// NewRecoveryHandler creates a new recovery handler with default settings
func NewRecoveryHandler() *RecoveryHandler {
	return &RecoveryHandler{
		Logger:       log.Printf,
		IncludeStack: true,
	}
}

// Recover recovers from panics and converts them to errors
func (r *RecoveryHandler) Recover(context string) error {
	if recovered := recover(); recovered != nil {
		stack := debug.Stack()

		// Always attempt to log panics, fall back to standard log if no logger
		if r.Logger != nil {
			r.Logger("PANIC in %s: %v\n%s", context, recovered, stack)
		} else {
			// Fallback to standard log to ensure panics are never silently ignored
			log.Printf("PANIC in %s: %v\n%s", context, recovered, stack)
		}

		// Call custom panic handler if provided
		if r.OnPanic != nil {
			r.OnPanic(recovered, stack)
		}

		// Convert panic to error
		err := New(CodeInternalError, "unexpected panic occurred")
		err.WithDetail("context", context)

		// Only include panic details in development mode
		if r.IncludeStack {
			err.WithDetail("panic", fmt.Sprintf("%v", recovered))
		}

		return err
	}
	return nil
}

// RecoverMiddleware returns a function that can be used to wrap handlers
func (r *RecoveryHandler) RecoverMiddleware(handler func() error) func() error {
	return func() error {
		defer func() {
			if recovered := recover(); recovered != nil {
				stack := debug.Stack()

				if r.Logger != nil {
					r.Logger("PANIC in handler: %v\n%s", recovered, stack)
				} else {
					log.Printf("PANIC in handler: %v\n%s", recovered, stack)
				}

				if r.OnPanic != nil {
					r.OnPanic(recovered, stack)
				}
			}
		}()

		return handler()
	}
}

// RecoverWebSocket wraps WebSocket handlers with panic recovery
func (r *RecoveryHandler) RecoverWebSocket(sessionID string, handler func()) {
	defer func() {
		if recovered := recover(); recovered != nil {
			stack := debug.Stack()

			if r.Logger != nil {
				r.Logger("PANIC in WebSocket session %s: %v\n%s", sessionID, recovered, stack)
			} else {
				log.Printf("PANIC in WebSocket session %s: %v\n%s", sessionID, recovered, stack)
			}

			if r.OnPanic != nil {
				r.OnPanic(recovered, stack)
			}
		}
	}()

	handler()
}

// RecoverGoroutine wraps goroutine execution with panic recovery
func (r *RecoveryHandler) RecoverGoroutine(name string, fn func()) {
	go func() {
		defer func() {
			if recovered := recover(); recovered != nil {
				stack := debug.Stack()

				if r.Logger != nil {
					r.Logger("PANIC in goroutine %s: %v\n%s", name, recovered, stack)
				} else {
					log.Printf("PANIC in goroutine %s: %v\n%s", name, recovered, stack)
				}

				if r.OnPanic != nil {
					r.OnPanic(recovered, stack)
				}
			}
		}()

		fn()
	}()
}
