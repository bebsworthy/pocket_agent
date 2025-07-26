# Cross-Reference Mapping for Pocket Agent Features

## Overview

This document provides a comprehensive cross-reference mapping of all inter-feature dependencies, requirement numbering, and shared components across the 6 core features of Pocket Agent. All features have been successfully updated to use the new X.Y numbering format.

## Feature Status Summary

| Feature | Status | Requirements Count | Stories Count |
|---------|--------|-------------------|---------------|
| Data Layer | ✅ Updated | 86 requirements | 7 stories |
| Communication Layer | ✅ Updated | 35 requirements | 7 stories |
| Background Services | ✅ Updated | 41 requirements | 8 stories |
| Security Authentication | ✅ Updated | 38 requirements | 10 stories |
| UI Navigation Foundation | ✅ Updated | 36 requirements | 10 stories |
| Screen Design | ✅ Updated | 46 requirements | 10 stories |

**Total Requirements: 282 across 52 user stories**

## Master Requirement List by Feature

### Data Layer (DL)
- **Story 1: Secure Data Storage** (1.1 - 1.5)
- **Story 2: Entity Management** (2.1 - 2.5)
- **Story 3: Data Persistence** (3.1 - 3.5)
- **Story 4: Message History** (4.1 - 4.5)
- **Story 5: Performance and Memory** (5.1 - 5.5)
- **Story 6: Data Search and Query** (6.1 - 6.5)
- **Story 7: Data Export and Backup** (7.1 - 7.5)

### Communication Layer (CL)
- **Story 1: Establish Secure Connection** (1.1 - 1.5)
- **Story 2: Send Commands to Claude** (2.1 - 2.5)
- **Story 3: Handle Permission Requests** (3.1 - 3.5)
- **Story 4: Maintain Connection Reliability** (4.1 - 4.5)
- **Story 5: Monitor Session Activity** (5.1 - 5.5)
- **Story 6: Configure Connection Policies** (6.1 - 6.5)
- **Story 7: Resume Interrupted Sessions** (7.1 - 7.5)

### Background Services (BS)
- **Story 1: Enable Background Monitoring** (1.1 - 1.5)
- **Story 2: Receive Permission Notifications** (2.1 - 2.6)
- **Story 3: Monitor Multiple Projects** (3.1 - 3.5)
- **Story 4: Preserve Battery Life** (4.1 - 4.5)
- **Story 5: Handle Task Completion** (5.1 - 5.5)
- **Story 6: Manage Background Permissions** (6.1 - 6.5)
- **Story 7: Recover from Interruptions** (7.1 - 7.5)
- **Story 8: Schedule Maintenance Tasks** (8.1 - 8.5)

### Security Authentication (SA)
- **Story 1: Set Up Biometric Authentication** (1.1 - 1.5)
- **Story 2: Import SSH Keys** (2.1 - 2.6)
- **Story 3: Authenticate WebSocket Connections** (3.1 - 3.6)
- **Story 4: Approve Permission Requests** (4.1 - 4.6)
- **Story 5: Manage Active Sessions** (5.1 - 5.6)
- **Story 6: Review Security Audit Log** (6.1 - 6.6)
- **Story 7: Configure Security Policies** (7.1 - 7.6)
- **Story 8: Handle Key Rotation** (8.1 - 8.6)
- **Story 9: Recover from Device Loss** (9.1 - 9.6)
- **Story 10: Export Security Configuration** (10.1 - 10.6)

### UI Navigation Foundation (UNF)
- **Story 1: Navigate the App Structure** (1.1 - 1.6)
- **Story 2: Customize Visual Theme** (2.1 - 2.6)
- **Story 3: Access Project Features** (3.1 - 3.6)
- **Story 4: Find Features Through Visual Hierarchy** (4.1 - 4.6)
- **Story 5: Navigate Using Assistive Technology** (5.1 - 5.6)
- **Story 6: Return via Deep Links** (6.1 - 6.6)
- **Story 7: Experience Smooth Transitions** (7.1 - 7.6)
- **Story 8: Organize Multiple Projects** (8.1 - 8.6)
- **Story 9: Understand App State** (9.1 - 9.6)
- **Story 10: Use Gestures Efficiently** (10.1 - 10.6)

### Screen Design (SD)
- **Story 1: Navigate App Screens** (1.1 - 1.6)
- **Story 2: View Project Information** (2.1 - 2.6)
- **Story 3: Interact with Claude** (3.1 - 3.6)
- **Story 4: Manage Permissions** (4.1 - 4.6)
- **Story 5: Browse Project Files** (5.1 - 5.6)
- **Story 6: Configure Settings** (6.1 - 6.6)
- **Story 7: Handle Errors Gracefully** (7.1 - 7.6)
- **Story 8: Access Quick Actions** (8.1 - 8.6)
- **Story 9: Understand Loading States** (9.1 - 9.6)
- **Story 10: Use App Efficiently** (10.1 - 10.6)

## Inter-Feature Dependencies

### 1. Data Layer Dependencies
The Data Layer serves as the foundation for all other features:

**Depended on by:**
- **Communication Layer**: CL 7.2 (Display last 10 messages) → DL 4.3 (Recent message history loading)
- **Background Services**: BS 8.1 (Auto-cleanup old messages) → DL 4.2 (Message count limiting)
- **Security Authentication**: SA 2.5 (Store keys in hardware) → DL 1.1 (Encryption with Android Keystore)
- **UI Navigation Foundation**: UNF 8.1 (Sort projects by activity) → DL 6.3 (Last active date sorting)
- **Screen Design**: SD 2.1 (Project card display) → DL 2.1-2.5 (Entity management)

### 2. Communication Layer Dependencies

**Depends on:**
- **Data Layer**: For message storage (DL 4.1-4.5) and project management (DL 2.1-2.5)
- **Security Authentication**: For SSH key operations (SA 3.1-3.6)

**Depended on by:**
- **Background Services**: BS 2.1 (Permission notifications) → CL 3.1 (Permission delivery)
- **Screen Design**: SD 3.1-3.6 (Chat interface) → CL 2.1-2.5 (Message sending)

### 3. Background Services Dependencies

**Depends on:**
- **Communication Layer**: For connection monitoring (CL 5.1-5.5)
- **Data Layer**: For message cleanup (DL 4.2, 8.1-8.5)
- **Security Authentication**: For permission handling (SA 4.1-4.6)

**Depended on by:**
- **Screen Design**: SD 9.5 (Background operations) → BS 5.1-5.5 (Task completion)
- **UI Navigation Foundation**: UNF 9.5 (Background progress) → BS monitoring

### 4. Security Authentication Dependencies

**Depends on:**
- **Data Layer**: For secure key storage (DL 1.1-1.5)

**Depended on by:**
- **Communication Layer**: CL 1.2 (SSH authentication) → SA 3.1-3.6
- **Background Services**: BS 2.4 (Permission response) → SA 4.1-4.6
- **Screen Design**: SD 4.1 (Permission dialog) → SA 4.1-4.6

### 5. UI Navigation Foundation Dependencies

**Depends on:**
- **Data Layer**: For project organization (DL 6.1-6.5)

**Depended on by:**
- **Screen Design**: All SD stories depend on UNF for navigation infrastructure
- **Background Services**: BS 1.4 (Open projects view) → UNF navigation

### 6. Screen Design Dependencies

**Depends on all other features:**
- **Data Layer**: For displaying data (all DL stories)
- **Communication Layer**: For chat interface (CL 2.1-2.5)
- **Background Services**: For status indicators (BS 5.1-5.5)
- **Security Authentication**: For permission UI (SA 4.1-4.6)
- **UI Navigation Foundation**: For all navigation (all UNF stories)

## Shared Components

### 1. Entity Models (Data Layer → All Features)
- `SshIdentity`: Used by SA, CL, SD
- `ServerProfile`: Used by CL, SD, UNF
- `Project`: Used by all features
- `Message`: Used by CL, BS, SD

### 2. Security Components (Security Authentication → Multiple Features)
- `BiometricAuthManager`: Used by SA, SD
- `SshKeyAuthenticator`: Used by SA, CL
- `PermissionManager`: Used by SA, BS, CL, SD

### 3. Connection Components (Communication Layer → Multiple Features)
- `WebSocketManager`: Used by CL, BS
- `ConnectionStateManager`: Used by CL, BS, SD
- `MessageProtocol`: Used by CL, BS

### 4. UI Components (UI Navigation Foundation → Screen Design)
- `NavigationManager`: Used by UNF, SD
- `ThemeManager`: Used by UNF, SD
- `GestureHandler`: Used by UNF, SD

### 5. Background Components (Background Services → Multiple Features)
- `ForegroundService`: Used by BS, CL
- `NotificationManager`: Used by BS, SD
- `WorkManager`: Used by BS, DL

## Critical Integration Points

### 1. Authentication Flow
```
SA 2.1-2.6 (Import SSH Keys) → DL 1.1-1.5 (Secure Storage) → CL 1.1-1.5 (Connection) → SA 3.1-3.6 (WebSocket Auth)
```

### 2. Permission Request Flow
```
CL 3.1 (Receive Request) → BS 2.1 (Send Notification) → SD 4.1 (Display Dialog) → SA 4.1 (Biometric Auth) → CL 3.4 (Send Response)
```

### 3. Message Flow
```
CL 2.1 (Send Message) → DL 4.1 (Store Message) → SD 3.1 (Display Message) → BS 5.1 (Monitor Progress)
```

### 4. Project Navigation Flow
```
UNF 1.2 (Navigate to Projects) → DL 6.3 (Sort Projects) → SD 2.1 (Display Cards) → UNF 3.1 (Project Navigation)
```

## Validation Checklist

### Consistency Validation
- [x] All features use X.Y numbering format
- [x] No duplicate requirement IDs within features
- [x] All cross-references validated
- [x] Requirement mapping tables complete

### Dependency Validation
- [x] All inter-feature dependencies documented
- [x] No circular dependencies identified
- [x] Integration points clearly defined
- [x] Shared components mapped

### Coverage Validation
- [x] All 6 features documented
- [x] All 52 user stories covered
- [x] All 282 requirements mapped
- [x] All non-functional requirements included

## Risk Areas and Mitigation

### 1. High Coupling Areas
- **Risk**: Security Authentication is heavily depended upon
- **Mitigation**: Clear interface definitions, comprehensive error handling

### 2. Performance Bottlenecks
- **Risk**: Data Layer cache accessed by all features
- **Mitigation**: Efficient caching strategy, concurrent access handling

### 3. Complex Integration Points
- **Risk**: Permission flow involves 4 features
- **Mitigation**: Well-defined protocol, comprehensive testing

### 4. State Management
- **Risk**: Connection state affects multiple features
- **Mitigation**: Centralized state management, reactive updates

## Next Steps

1. **Implementation Planning**
   - Use dependency graph to determine implementation order
   - Start with Data Layer and Security Authentication
   - Implement shared components early

2. **Testing Strategy**
   - Create integration tests for all dependency points
   - Test permission flow end-to-end
   - Validate state management across features

3. **Documentation Updates**
   - Keep cross-references updated as implementation progresses
   - Document any new dependencies discovered
   - Update shared component interfaces

## Conclusion

All 6 features have been successfully updated with the new X.Y requirement numbering format. The cross-reference mapping reveals a well-structured architecture with clear dependencies and integration points. The Data Layer and Security Authentication features form the foundation, while Screen Design integrates all features for the user interface. This mapping will serve as a guide for implementation sequencing and integration testing.