# Component Map Specification

## Component Hierarchy Tree

```
App Root
│
├── Global Services (Always Active)
│   ├── ThemeManager
│   ├── BackgroundOperationsService
│   │   ├── BatteryAwarePoller
│   │   ├── NotificationManager
│   │   │   ├── PermissionNotification
│   │   │   ├── ProgressNotification
│   │   │   ├── CompletionNotification
│   │   │   └── ErrorNotification
│   │   └── SessionStateManager
│   ├── BiometricPrompt
│   ├── TokenVaultManager
│   └── EncryptedPreferencesManager
│
├── Data Layer (Shared Across Screens)
│   ├── SecureDataRepository
│   ├── EncryptedJsonStorage
│   └── MessageHistoryCache
│
└── Navigation Root
    │
    ├── WelcomeScreen (Initial)
    │   └── → ProjectsListScreen
    │
    ├── ProjectsListScreen (Main Hub)
    │   ├── ProjectCreationScreen
    │   │   ├── ProjectInitializationWizard
    │   │   ├── RepositoryCloneDialog
    │   │   └── → ServerManagementScreen
    │   ├── ServerManagementScreen
    │   │   └── → SSHIdentityManagementScreen
    │   ├── SSHIdentityManagementScreen
    │   │   ├── SshKeyImportManager
    │   │   └── KeyFingerprintViewer
    │   ├── AppSettingsScreen
    │   │   ├── ThemeManager
    │   │   └── HighContrastTheme
    │   └── → Project View (Bottom Tab Navigation)
    │
    └── Project View (Selected Project Context)
        ├── ConnectionStateIndicator (Always Visible)
        ├── ConnectionControlButtons (Always Visible)
        │   ├── AuthenticationStatusView
        │   ├── WebSocketStatusIndicator
        │   └── AuthenticationErrorDialog
        │
        ├── DashboardScreen (Tab 1)
        │   ├── QuickActionsGrid
        │   │   ├── QuickActionCard[]
        │   │   │   ├── ClaudePromptAction
        │   │   │   └── ShellCommandAction
        │   │   │       └── TerminalOutputView
        │   │   └── ScriptDiscoveryService
        │   ├── ProgressTracker
        │   │   ├── TaskTimelineView
        │   │   ├── HierarchicalProgressView
        │   │   └── SubAgentMonitor
        │   ├── ResourceUsageIndicator
        │   └── SessionHistoryDialog
        │
        ├── ChatScreen (Tab 2)
        │   ├── MessageList
        │   │   ├── ClaudeMessageView[]
        │   │   ├── UserMessageView[]
        │   │   └── MessageActionsMenu
        │   ├── MessageInputField
        │   │   └── FileReferenceSelector
        │   ├── PermissionRequestDialog
        │   ├── ExponentialBackoffManager
        │   ├── MessageQueueManager
        │   └── CompressionHandler
        │
        ├── FilesScreen (Tab 3)
        │   ├── FileTreeBrowser
        │   │   ├── GitStatusIndicator[]
        │   │   └── FileSearchBar
        │   ├── FilePreviewPane
        │   └── DiffViewer
        │
        └── ProjectSettingsScreen (Tab 4)
            ├── ScriptsFolderConfigDialog
            ├── TokenEntryDialog
            ├── LargeTextScaler
            ├── ScreenReaderAnnouncer
            └── FocusIndicatorOverlay
```

## Key Relationships

### Hierarchical Relationships
- **App Root** → Contains all global services and navigation
- **Project View** → Contains all project-specific screens as tabs
- **Each Screen** → Contains its specific UI components

### Cross-Screen Dependencies
- **BackgroundOperationsService** → Manages all background operations and monitoring
- **Data Repositories** → Shared across all screens
- **BiometricPrompt** → Used by multiple screens for security
- **NotificationManager** → Triggered by background service, affects all screens

### Navigation Flow
1. **WelcomeScreen** → **ProjectsListScreen** (one-time)
2. **ProjectsListScreen** → **Project View** (main navigation)
3. **Project View** → Bottom tab navigation between 4 screens
4. Various screens can open dialogs and sub-screens

### Component Ownership
- **Global Components**: Owned by App Root, available everywhere
- **Screen Components**: Owned by specific screens, lifecycle-bound
- **Shared Components**: Managed by repositories, accessed via dependency injection

## Component Count Summary

Total Components: **70**

### By Category:
- **Screen Components**: 10
- **Connection & Status Components**: 4
- **Chat Interface Components**: 2
- **Permission & Notification Components**: 6
- **Quick Actions Components**: 6
- **File Management Components**: 5
- **Security Components**: 5
- **Progress & Monitoring Components**: 5
- **Background Service Components**: 4
- **Data Management Components**: 6
- **Utility Components**: 6
- **Dialog & Modal Components**: 5
- **Accessibility Components**: 4
- **Global Services**: 3

### By Screen Location:
- **Global/App Root**: 13
- **ProjectsListScreen & Related**: 8
- **DashboardScreen**: 11
- **ChatScreen**: 10
- **FilesScreen**: 5
- **ProjectSettingsScreen**: 5
- **Shared/Cross-Screen**: 18