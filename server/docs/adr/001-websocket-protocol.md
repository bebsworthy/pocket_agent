# ADR-001: WebSocket Protocol for Client-Server Communication

## Status
Accepted

## Context
The Pocket Agent Server needs to provide real-time bidirectional communication between clients and the server for Claude CLI execution management. We need to choose a communication protocol that supports:
- Real-time message delivery
- Bidirectional communication
- Multiple concurrent clients
- Message broadcasting to subscribers
- Persistent connections
- Cross-platform compatibility

## Decision
We will use WebSocket protocol with JSON message format for all client-server communication.

## Rationale

### Why WebSocket?
1. **Real-time Communication**: WebSocket provides full-duplex communication channels over a single TCP connection
2. **Low Latency**: No HTTP overhead for each message after the initial handshake
3. **Bidirectional**: Server can push updates to clients without polling
4. **Wide Support**: Excellent client library support across all platforms (Android, Web, iOS)
5. **Firewall Friendly**: Uses standard HTTP/HTTPS ports for initial handshake

### Why JSON?
1. **Human Readable**: Easy to debug and inspect messages
2. **Schema Flexible**: Can evolve the protocol without breaking compatibility
3. **Universal Support**: Native support in all target platforms
4. **Sufficient Performance**: Message size is not a bottleneck for our use case

### Alternatives Considered
- **gRPC**: More complex, requires code generation, less suitable for web clients
- **Server-Sent Events**: Unidirectional only, would require separate channel for client commands
- **HTTP Long Polling**: Higher latency, more complex state management
- **Raw TCP**: Would require custom protocol implementation, no web browser support

## Consequences

### Positive
- Simple implementation with standard libraries
- Easy debugging with readable messages
- Flexible message schema evolution
- Excellent cross-platform support
- Can leverage existing WebSocket infrastructure

### Negative
- Text-based protocol has slightly larger message size than binary
- No built-in message schema validation (must implement ourselves)
- Connection management complexity (reconnection, heartbeat)
- Potential for message ordering issues with multiple publishers

### Mitigation Strategies
1. Implement message size limits to prevent abuse
2. Add JSON schema validation for critical messages
3. Use connection pooling and automatic reconnection
4. Add message sequence numbers for ordering guarantees

## Implementation Notes
- Use gorilla/websocket for Go server implementation
- Implement ping/pong for connection health monitoring
- Add connection rate limiting for security
- Log all messages for debugging (with sensitive data redaction)