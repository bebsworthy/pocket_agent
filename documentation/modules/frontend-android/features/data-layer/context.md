# Data Layer - Context

## Overview

The Data Layer feature provides the foundation for all data persistence in Pocket Agent, enabling developers to securely store and manage their development environment configurations on their mobile devices. This feature addresses the fundamental need for maintaining multiple server connections, project configurations, and conversation histories while ensuring that sensitive information like SSH keys remains protected by hardware-level security.

For mobile developers who work across different environments - personal projects at home, work projects on company servers, and client projects on various cloud platforms - the Data Layer enables seamless switching between contexts without re-entering credentials or losing conversation history. The feature recognizes that developers typically manage 10-20 different servers and 50-100 projects, making efficient local storage with quick access essential.

## Business Context

### User Needs
- **Configuration Persistence**: Developers need their server profiles, project settings, and SSH identities to persist across app sessions
- **Quick Access**: Fast retrieval of frequently used projects without network delays
- **Secure Storage**: Protection of sensitive SSH keys and authentication tokens
- **Offline Capability**: Access to conversation history and project details even without connectivity
- **Multi-Environment Support**: Separate SSH identities for work, personal, and client environments

### Business Value
- **Time Savings**: Eliminates repeated credential entry and project configuration
- **Security Compliance**: Meets enterprise requirements for encrypted credential storage
- **User Retention**: Seamless experience encourages continued app usage
- **Reduced Support**: Fewer issues with lost configurations or credentials

## Technical Context

### System Integration
This feature integrates with:
- **Security Authentication**: Provides encrypted storage for SSH keys managed by the authentication system
- **Background Services**: Supplies project and server data for monitoring operations
- **Communication Layer**: Stores connection profiles and session states
- **UI Navigation**: Provides data models for all screens to display

### Dependencies
- Depends on: Android Keystore for encryption keys
- Required by: All features that need persistent data storage

### Constraints
- Platform: Android's file system and storage limitations
- Performance: Must handle datasets up to 100 projects efficiently
- Security: All sensitive data must be encrypted at rest
- Memory: Entire dataset must fit in memory for performance

## Historical Context

### Previous Approaches
Initially considered using SQLite database for its query capabilities and established patterns in Android development. However, analysis showed that for the expected data volume (10-20 servers, 50-100 projects), the overhead of SQL database management, migrations, and ORM complexity wasn't justified.

### Lessons Learned
- JSON serialization with Kotlinx.serialization provides excellent performance for small datasets
- In-memory caching eliminates repeated file I/O for better battery life
- Single encrypted file simplifies backup and recovery scenarios
- Biometric unlock on app launch provides better UX than per-operation authentication

### Future Considerations
The JSON-based approach allows for easy extension with cloud sync capabilities, data export/import features, and migration to more sophisticated storage if data volumes grow beyond current expectations. The clean repository pattern ensures that switching storage mechanisms in the future would require minimal code changes.