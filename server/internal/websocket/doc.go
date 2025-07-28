// Package websocket provides WebSocket server implementation for real-time
// bidirectional communication between clients and the Pocket Agent Server.
//
// The package includes:
//   - WebSocket server with TLS support
//   - Connection management and lifecycle handling
//   - Message routing and dispatch
//   - Rate limiting and security features
//   - Session management with ping/pong heartbeat
//   - Middleware support for cross-cutting concerns
//
// Basic usage:
//
//	config := websocket.DefaultConfig()
//	handler := &MyMessageHandler{}
//	server := websocket.NewServer(config, handler, logger)
//
//	// Start server
//	if err := server.Start(); err != nil {
//	    log.Fatal(err)
//	}
//
//	// Graceful shutdown
//	server.Stop(30 * time.Second)
//
// The server supports the following security features:
//   - Origin header validation
//   - Connection rate limiting per IP
//   - Maximum connections per IP limit
//   - Total connection limit
//   - Message size limits
//   - Connection timeout handling
//
// Message routing is handled through the MessageRouter which dispatches
// incoming messages to registered handlers based on message type.
package websocket
