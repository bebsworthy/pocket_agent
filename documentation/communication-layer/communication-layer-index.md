# Communication Layer Feature Documentation Index

This feature has been split into multiple focused documents for better readability and navigation. Each document covers a specific aspect of the communication layer implementation.

## Documentation Structure

### 1. [Overview](./communication-layer-overview.feat.md)
- Feature introduction and purpose
- Architecture overview
- Technology stack
- Key components listing
- Implementation notes
- Package structure
- Future extensions

### 2. [WebSocket Components](./communication-layer-websocket.feat.md)
- SshAuthWebSocketClient implementation
- WebSocket Manager for multiple connections
- Connection State Manager
- Message Queue Manager
- Reconnection Manager with exponential backoff
- Connection lifecycle management
- Network optimization strategies

### 3. [Authentication & Session Management](./communication-layer-authentication.feat.md)
- SSH Key Authenticator implementation
- Challenge-response authentication flow
- Session Manager for persistence
- Permission Policy Manager
- Certificate validation
- Security audit logging
- Session resumption after disconnection

### 4. [Messages & Protocol](./communication-layer-messages.feat.md)
- Complete message protocol definition
- Message types and serialization
- Shared models across features
- Message handlers registry pattern
- Type-safe message handling
- Protocol versioning
- Extension points for new message types

### 5. [Testing](./communication-layer-testing.feat.md)
- Comprehensive testing checklist
- Unit test examples
- Integration test strategies
- End-to-end test scenarios
- Performance testing
- Test utilities and helpers
- MockWebServer usage

## Quick Navigation

- **Starting Point**: Begin with the [Overview](./communication-layer-overview.feat.md) to understand the feature's purpose and architecture
- **Core Implementation**: The [WebSocket Components](./communication-layer-websocket.feat.md) contains the main connection management
- **Security**: The [Authentication](./communication-layer-authentication.feat.md) covers SSH key auth and session management
- **Protocol**: The [Messages & Protocol](./communication-layer-messages.feat.md) defines all message types
- **Quality Assurance**: The [Testing](./communication-layer-testing.feat.md) provides comprehensive testing strategies

## Key Components Summary

### Connection Management
- **SshAuthWebSocketClient**: WebSocket client with SSH authentication
- **WebSocketManager**: Manages multiple project connections
- **ConnectionStateManager**: Tracks connection lifecycle
- **ReconnectionManager**: Handles automatic reconnection

### Authentication
- **SshKeyAuthenticator**: SSH key challenge-response authentication
- **SessionManager**: Session persistence and resumption
- **PermissionPolicyManager**: Automated permission policies
- **CertificateValidator**: Server certificate validation

### Messaging
- **MessageProtocol**: Type-safe message encoding/decoding
- **MessageQueueManager**: Offline message queuing
- **MessageHandlerRegistry**: Message type routing
- **ConnectionHealthMonitor**: Ping/pong monitoring

### Supporting Components
- **NetworkStateObserver**: Android network connectivity monitoring
- **BatteryOptimizationManager**: Adaptive behavior based on battery
- **SecurityAuditLogger**: Security event logging
- **ProgressTracker**: Operation progress tracking

## Implementation Priority

1. **Phase 1**: Core WebSocket and authentication
   - SshAuthWebSocketClient
   - SshKeyAuthenticator
   - Basic message protocol

2. **Phase 2**: Connection reliability
   - ReconnectionManager
   - MessageQueueManager
   - Connection health monitoring

3. **Phase 3**: Session management
   - SessionManager
   - Permission policies
   - Message persistence

4. **Phase 4**: Advanced features
   - Multiple concurrent connections
   - Progress tracking
   - Sub-agent monitoring

## Message Flow

```
User Input → CommandMessage → WebSocket → Wrapper Service
                                 ↓
UI Update ← MessageHandler ← WebSocket ← ClaudeResponse
```

## Authentication Flow

```
Connect → Receive Challenge → Sign with SSH Key → Send Response
             ↓                                         ↓
    Connection Failed ← Auth Error        Auth Success → Session Established
```

## Related Documentation

- [Frontend Technical Specification](../frontend.spec.md#communication-protocol)
- [Background Services](../background-services/background-services-index.md)
- [Security & Authentication](../security-authentication.feat.md)
- [Data Layer](../data-layer-entity-management.feat.md)