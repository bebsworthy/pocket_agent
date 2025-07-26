# Communication Layer - Requirements

## User Stories

### Story 1: Establish Secure Connection
**As a** developer using Pocket Agent  
**I want to** securely connect to my Claude Code wrapper from my mobile device  
**So that** I can interact with Claude while away from my development machine  

**Acceptance Criteria**:
- 1.1: WHEN I select a server profile and tap connect THEN the system SHALL establish a WebSocket connection within 5 seconds
- 1.2: WHEN the connection is established THEN the system SHALL authenticate using my selected SSH key
- 1.3: WHEN authentication succeeds THEN the system SHALL display a "Connected" status with session information
- 1.4: WHEN authentication fails THEN the system SHALL display a clear error message explaining the failure reason
- 1.5: WHEN I am connected THEN the system SHALL maintain the connection across app backgrounding

### Story 2: Send Commands to Claude
**As a** developer interacting with Claude  
**I want to** send text commands and see responses in real-time  
**So that** I can have a continuous conversation with Claude from my mobile device  

**Acceptance Criteria**:
- 2.1: WHEN I type a message and tap send THEN the system SHALL transmit the message immediately to Claude
- 2.2: WHEN Claude responds THEN the system SHALL display the response in the chat within 500ms of receipt
- 2.3: WHEN Claude sends a partial response THEN the system SHALL update the UI incrementally as content arrives
- 2.4: WHEN I send a message while disconnected THEN the system SHALL queue it and send upon reconnection
- 2.5: WHEN message delivery fails THEN the system SHALL show a retry option with the failed message

### Story 3: Handle Permission Requests
**As a** developer managing Claude's actions  
**I want to** receive and respond to permission requests on my mobile device  
**So that** I can control what actions Claude performs on my development machine  

**Acceptance Criteria**:
- 3.1: WHEN Claude requests permission THEN the system SHALL deliver a notification within 2 seconds
- 3.2: WHEN I tap the notification THEN the system SHALL open directly to the permission request
- 3.3: WHEN viewing a permission request THEN the system SHALL display the tool, action, affected resources, and risk level
- 3.4: WHEN I approve or deny a permission THEN the system SHALL send the response immediately to Claude
- 3.5: WHEN a permission times out THEN the system SHALL apply my configured default policy

### Story 4: Maintain Connection Reliability
**As a** mobile user with varying network conditions  
**I want** the app to handle network changes gracefully  
**So that** my Claude session remains active despite connectivity issues  

**Acceptance Criteria**:
- 4.1: WHEN network connectivity is lost THEN the system SHALL attempt automatic reconnection
- 4.2: WHEN switching between WiFi and cellular THEN the system SHALL resume the connection within 10 seconds
- 4.3: WHEN reconnecting THEN the system SHALL restore my session without losing conversation context
- 4.4: WHEN offline for extended periods THEN the system SHALL preserve queued messages for up to 24 hours
- 4.5: WHEN reconnection fails repeatedly THEN the system SHALL notify me and stop after 10 attempts

### Story 5: Monitor Session Activity
**As a** developer tracking Claude's progress  
**I want to** see real-time status updates and progress indicators  
**So that** I know what Claude is doing without constant interaction  

**Acceptance Criteria**:
- 5.1: WHEN Claude is processing THEN the system SHALL display a typing indicator or progress status
- 5.2: WHEN Claude completes a task THEN the system SHALL show a summary of actions taken
- 5.3: WHEN Claude encounters an error THEN the system SHALL send an immediate notification
- 5.4: WHEN viewing session status THEN the system SHALL display connection quality, latency, and message count
- 5.5: WHEN Claude is idle THEN the system SHALL indicate how long since last activity

### Story 6: Configure Connection Policies
**As a** power user wanting automation  
**I want to** set policies for automatic permission handling  
**So that** Claude can continue working when I'm not actively monitoring  

**Acceptance Criteria**:
- 6.1: WHEN configuring policies THEN the system SHALL enable setting defaults for low, medium, and high-risk actions
- 6.2: WHEN a policy applies THEN the system SHALL log the automatic decision for review
- 6.3: WHEN I'm actively connected THEN the system SHALL override policies with manual responses
- 6.4: WHEN setting timeout policies THEN the system SHALL provide options to approve, deny, or use risk-based defaults
- 6.5: WHEN policies are active THEN the system SHALL display an indicator in the connection status

### Story 7: Resume Interrupted Sessions
**As a** developer with multiple devices  
**I want to** resume my Claude conversation after app restarts or device switches  
**So that** I don't lose context or have to restart conversations  

**Acceptance Criteria**:
- 7.1: WHEN I restart the app THEN the system SHALL automatically attempt to resume my last active session
- 7.2: WHEN resuming a session THEN the system SHALL display the last 10 messages for context
- 7.3: WHEN the session is expired THEN the system SHALL offer to start a new session
- 7.4: WHEN multiple sessions exist THEN the system SHALL enable choosing which to resume
- 7.5: WHEN session resume fails THEN the system SHALL explain why and offer alternatives

## Non-Functional Requirements

### Performance Requirements

1. **Connection Latency**
   - Initial connection establishment: <3 seconds on LTE
   - Authentication completion: <2 seconds
   - Message round-trip time: <200ms on good network
   - Reconnection time: <5 seconds after network recovery

2. **Throughput**
   - Support minimum 100 messages per minute
   - Handle message sizes up to 100KB
   - Queue up to 1000 messages while offline

3. **Resource Usage**
   - Battery drain: <5% per hour of active use
   - Memory usage: <100MB for communication layer
   - Network data: <1MB per hour during idle

### Reliability Requirements

1. **Availability**
   - 99% connection success rate on stable networks
   - Automatic recovery from temporary failures
   - Graceful degradation on poor networks

2. **Data Integrity**
   - Zero message loss during normal operation
   - Message ordering preservation
   - Duplicate message prevention

3. **Error Handling**
   - All errors must be caught and logged
   - User-friendly error messages
   - Automatic retry with backoff

### Security Requirements

1. **Authentication**
   - SSH key-based authentication only
   - No plaintext password transmission
   - Session tokens expire after 24 hours

2. **Encryption**
   - All communications over TLS 1.3+
   - Certificate pinning for known servers
   - Local storage encryption for queued messages

3. **Audit**
   - Log all authentication attempts
   - Track all permission decisions
   - Maintain audit trail for 30 days

### Usability Requirements

1. **Response Times**
   - Visual feedback within 100ms of user action
   - Connection status updates within 1 second
   - Error messages displayed within 2 seconds

2. **Offline Capability**
   - Clear indication of offline status
   - Queued message count visible
   - Estimated reconnection time shown

3. **Notifications**
   - Permission requests show preview text
   - Notifications gruppable by project
   - Quick actions available from notification

### Compatibility Requirements

1. **Platform Support**
   - Android 7.0 (API 24) and higher
   - Support for Android 14 features
   - Adaptive to different screen sizes

2. **Network Compatibility**
   - IPv4 and IPv6 support
   - Proxy server compatibility
   - Corporate firewall friendly

3. **Protocol Versions**
   - WebSocket protocol version 13
   - TLS 1.3 preferred, 1.2 minimum
   - JSON message format version 1.0

## Constraints

1. **Technical Constraints**
   - Must use existing Claude Code WebSocket protocol
   - Cannot modify server-side implementation
   - Must work within Android background limits

2. **Resource Constraints**
   - Maximum 5 concurrent connections
   - Message queue limited to 50MB
   - Background operation limited by Android OS

3. **Business Constraints**
   - No cloud relay servers (direct connection only)
   - SSH key management is user responsibility
   - No message content analysis or modification

## Requirement Mapping Table

| Story | ID | Description |
|-------|-----|-------------|
| Story 1 | 1.1 | Establish WebSocket connection within 5 seconds |
| Story 1 | 1.2 | Authenticate using SSH key |
| Story 1 | 1.3 | Display "Connected" status with session info |
| Story 1 | 1.4 | Display clear error message on auth failure |
| Story 1 | 1.5 | Maintain connection across app backgrounding |
| Story 2 | 2.1 | Transmit message immediately to Claude |
| Story 2 | 2.2 | Display response within 500ms of receipt |
| Story 2 | 2.3 | Update UI incrementally for partial responses |
| Story 2 | 2.4 | Queue messages when disconnected |
| Story 2 | 2.5 | Show retry option for failed messages |
| Story 3 | 3.1 | Deliver permission notification within 2 seconds |
| Story 3 | 3.2 | Open directly to permission request from notification |
| Story 3 | 3.3 | Display tool, action, resources, and risk level |
| Story 3 | 3.4 | Send permission response immediately |
| Story 3 | 3.5 | Apply default policy on timeout |
| Story 4 | 4.1 | Attempt automatic reconnection on network loss |
| Story 4 | 4.2 | Resume connection within 10 seconds on network switch |
| Story 4 | 4.3 | Restore session without losing context |
| Story 4 | 4.4 | Preserve queued messages for 24 hours |
| Story 4 | 4.5 | Notify and stop after 10 reconnection attempts |
| Story 5 | 5.1 | Display typing indicator or progress status |
| Story 5 | 5.2 | Show summary of actions taken |
| Story 5 | 5.3 | Send immediate notification on error |
| Story 5 | 5.4 | Display connection quality, latency, and message count |
| Story 5 | 5.5 | Indicate time since last activity |
| Story 6 | 6.1 | Enable setting defaults for risk levels |
| Story 6 | 6.2 | Log automatic decisions for review |
| Story 6 | 6.3 | Override policies with manual responses |
| Story 6 | 6.4 | Provide timeout policy options |
| Story 6 | 6.5 | Display indicator when policies active |
| Story 7 | 7.1 | Automatically attempt to resume last session |
| Story 7 | 7.2 | Display last 10 messages for context |
| Story 7 | 7.3 | Offer to start new session when expired |
| Story 7 | 7.4 | Enable choosing which session to resume |
| Story 7 | 7.5 | Explain failure and offer alternatives |

## Acceptance Testing Scenarios

### Scenario 1: First Connection
1. User imports SSH key successfully
2. User creates server profile with correct details
3. User taps connect on server profile
4. App shows "Connecting..." status
5. WebSocket connection established
6. SSH authentication challenge received
7. App signs challenge with private key
8. Authentication success received
9. App shows "Connected" with session ID
10. User can send first message to Claude

### Scenario 2: Network Disruption
1. User has active Claude conversation
2. User enters elevator (loses connection)
3. App shows "Reconnecting..." status
4. User sends message while disconnected
5. App shows message as queued
6. User exits elevator (connection restored)
7. App automatically reconnects
8. Queued message sent successfully
9. Conversation continues seamlessly
10. User sees no message loss

### Scenario 3: Permission Handling
1. Claude requests file deletion permission
2. User receives notification instantly
3. Notification shows "High Risk: Delete file.txt"
4. User taps notification
5. App opens to permission detail view
6. User sees full path and context
7. User taps "Deny" button
8. Response sent to Claude immediately
9. Claude acknowledges permission denied
10. Audit log shows decision timestamp

### Scenario 4: Battery Optimization
1. User's device enters low battery mode (15%)
2. App detects battery state change
3. Connection switches to power-saving mode
4. Ping interval increases to 60 seconds
5. Message batching enabled automatically
6. User still receives critical notifications
7. Non-critical updates are delayed
8. User plugs in device
9. App returns to normal operation mode
10. All delayed updates delivered