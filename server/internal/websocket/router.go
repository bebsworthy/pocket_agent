package websocket

import (
	"context"
	"encoding/json"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
)

// MessageRouter routes messages to appropriate handlers
type MessageRouter struct {
	handlers map[models.MessageType]HandlerFunc
	log      *logger.Logger
}

// HandlerFunc is a function that handles a specific message type
type HandlerFunc func(ctx context.Context, session *models.Session, data json.RawMessage) error

// NewMessageRouter creates a new message router
func NewMessageRouter(log *logger.Logger) *MessageRouter {
	return &MessageRouter{
		handlers: make(map[models.MessageType]HandlerFunc),
		log:      log,
	}
}

// Register registers a handler for a message type
func (r *MessageRouter) Register(msgType models.MessageType, handler HandlerFunc) {
	r.handlers[msgType] = handler
	r.log.Debug("Registered handler", "type", msgType)
}

// HandleMessage implements MessageHandler interface
func (r *MessageRouter) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	if msg == nil {
		return errors.New(errors.CodeValidationFailed, "message is nil")
	}

	r.log.Debug("Routing message",
		"session_id", session.ID,
		"type", msg.Type,
		"project_id", msg.ProjectID,
	)

	// Get handler for message type
	handler, ok := r.handlers[msg.Type]
	if !ok {
		return errors.New(errors.CodeValidationFailed, "unknown message type: %s", msg.Type)
	}

	// Route to handler
	if err := handler(ctx, session, msg.Data); err != nil {
		// Log error with context
		r.log.Error("Handler error",
			"session_id", session.ID,
			"type", msg.Type,
			"error", err,
		)

		// Wrap error with message context
		if appErr, ok := err.(*errors.AppError); ok {
			return appErr.WithDetail("message_type", msg.Type)
		}

		return errors.Wrap(err, errors.CodeInternalError, "handler failed for message type: %s", msg.Type)
	}

	return nil
}

// OnSessionCleanup implements MessageHandler interface
func (r *MessageRouter) OnSessionCleanup(session *models.Session) {
	// No-op for router - cleanup is handled by the parent handler
}

// RouteMessage is a convenience method for dispatching messages
func (r *MessageRouter) RouteMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	return r.HandleMessage(ctx, session, msg)
}

// MessageDispatcher provides high-level message dispatching with middleware
type MessageDispatcher struct {
	router      *MessageRouter
	middlewares []Middleware
	log         *logger.Logger
}

// Middleware is a function that wraps message handling
type Middleware func(next MessageHandler) MessageHandler

// NewMessageDispatcher creates a new message dispatcher
func NewMessageDispatcher(router *MessageRouter, log *logger.Logger) *MessageDispatcher {
	return &MessageDispatcher{
		router:      router,
		middlewares: make([]Middleware, 0),
		log:         log,
	}
}

// Use adds middleware to the dispatcher
func (d *MessageDispatcher) Use(middleware Middleware) {
	d.middlewares = append(d.middlewares, middleware)
}

// HandleMessage implements MessageHandler with middleware chain
func (d *MessageDispatcher) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	// Build middleware chain
	handler := MessageHandler(d.router)
	for i := len(d.middlewares) - 1; i >= 0; i-- {
		handler = d.middlewares[i](handler)
	}

	return handler.HandleMessage(ctx, session, msg)
}

// OnSessionCleanup implements MessageHandler interface
func (d *MessageDispatcher) OnSessionCleanup(session *models.Session) {
	// Delegate to router
	d.router.OnSessionCleanup(session)
}

// Common middleware functions

// LoggingMiddleware logs all messages
func LoggingMiddleware(log *logger.Logger) Middleware {
	return func(next MessageHandler) MessageHandler {
		return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
			start := time.Now()

			log.Info("Message received",
				"session_id", session.ID,
				"type", msg.Type,
				"project_id", msg.ProjectID,
			)

			err := next.HandleMessage(ctx, session, msg)

			log.Info("Message handled",
				"session_id", session.ID,
				"type", msg.Type,
				"duration", time.Since(start),
				"error", err != nil,
			)

			return err
		})
	}
}

// RecoveryMiddleware recovers from panics
func RecoveryMiddleware(log *logger.Logger) Middleware {
	return func(next MessageHandler) MessageHandler {
		return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) (err error) {
			defer func() {
				if r := recover(); r != nil {
					log.Error("Panic recovered in message handler",
						"session_id", session.ID,
						"type", msg.Type,
						"panic", r,
					)
					err = errors.New(errors.CodeInternalError, "internal server error")
				}
			}()

			return next.HandleMessage(ctx, session, msg)
		})
	}
}

// ValidationMiddleware validates messages before handling
func ValidationMiddleware() Middleware {
	return func(next MessageHandler) MessageHandler {
		return MessageHandlerFunc(func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
			// Message should already be validated by server, but double-check
			if err := msg.Validate(); err != nil {
				return errors.Wrap(err, errors.CodeValidationFailed, "message validation failed")
			}

			// Additional validation based on message type
			switch msg.Type {
			case models.MessageTypeExecute,
				models.MessageTypeProjectDelete,
				models.MessageTypeAgentNewSession,
				models.MessageTypeAgentKill,
				models.MessageTypeProjectLeave,
				models.MessageTypeGetMessages:
				// These require an active project
				if session.GetProject() == "" && msg.ProjectID == "" {
					return errors.New(errors.CodeValidationFailed, "project_id required")
				}
			}

			return next.HandleMessage(ctx, session, msg)
		})
	}
}

// MessageHandlerFunc is an adapter to allow functions to implement MessageHandler
type MessageHandlerFunc func(ctx context.Context, session *models.Session, msg *models.ClientMessage) error

// HandleMessage implements MessageHandler
func (f MessageHandlerFunc) HandleMessage(ctx context.Context, session *models.Session, msg *models.ClientMessage) error {
	return f(ctx, session, msg)
}

// OnSessionCleanup implements MessageHandler
func (f MessageHandlerFunc) OnSessionCleanup(session *models.Session) {
	// No-op for function handlers
}

// Helper functions for common response patterns

// SendProjectState sends project state update to client
func SendProjectState(session *models.Session, project *models.Project) error {
	msg := models.NewProjectStateMessage(project)
	return session.WriteJSON(msg)
}

// SendError sends error message to client
func SendError(session *models.Session, err error) error {
	appErr, ok := err.(*errors.AppError)
	if !ok {
		appErr = errors.NewInternalError(err)
	}

	msg := models.NewErrorMessage(
		session.GetProject(),
		string(appErr.Code),
		appErr.Message,
		appErr.Details,
	)

	return session.WriteJSON(msg)
}

// SendSuccess sends a generic success response
func SendSuccess(session *models.Session, msgType models.MessageType, data interface{}) error {
	msg := models.ServerMessage{
		Type:      msgType,
		ProjectID: session.GetProject(),
		Data:      data,
	}

	return session.WriteJSON(msg)
}
