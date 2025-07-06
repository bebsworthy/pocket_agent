# Pocket Agent

**A remote coding agent mobile interface for Android**

Pocket Agent enables developers to remotely control Claude Code instances running on development servers through their Android devices. The app provides a native mobile interface for AI-powered development workflows while maintaining full functionality including interactive permission handling.

## Features

- **Multi-Server Management**: Connect to multiple development servers with different SSH identities
- **Real-time Chat Interface**: Interact with Claude Code through a mobile-optimized chat UI
- **Secure Credential Storage**: Biometric-protected storage for SSH keys and API tokens
- **Background Monitoring**: Stay connected and receive notifications even when the app is closed
- **Voice Integration**: Use speech-to-text for natural language prompts
- **Quick Actions**: Execute common tasks with one tap using project scripts
- **File Browser**: Navigate project files with Git status indicators
- **Progress Tracking**: Monitor multi-step operations and sub-agent activities

## Architecture

The project follows Clean Architecture principles with clear separation between:

- **Presentation Layer**: Jetpack Compose UI with Material Design 3
- **Domain Layer**: Use cases and business logic
- **Data Layer**: Local database (Room), remote APIs, and security management

## Documentation

Comprehensive documentation is available in the `/documentation` directory:

- [`project.spec.md`](./documentation/project.spec.md) - Overall system architecture and design
- [`frontend.spec.md`](./documentation/frontend.spec.md) - Detailed Android app specification
- [`backend.spec.md`](./documentation/backend.spec.md) - Wrapper service specification
- [`component-map.specs.md`](./documentation/component-map.specs.md) - UI component hierarchy

### Feature Specifications

- [`data-layer-entity-management.feat.md`](./documentation/data-layer-entity-management.feat.md) - Database and entity management
- [`security-authentication.feat.md`](./documentation/security-authentication.feat.md) - Security and authentication features
- [`communication-layer.feat.md`](./documentation/communication-layer.feat.md) - SSH tunnels and WebSocket communication
- [`ui-navigation-foundation.feat.md`](./documentation/ui-navigation-foundation.feat.md) - Navigation and UI foundation

## Technology Stack

### Android App
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture
- **Networking**: OkHttp3 (WebSocket), JSch (SSH)
- **Database**: Room
- **DI**: Hilt
- **Security**: Android Keystore + BiometricPrompt

### Backend Service
- **Runtime**: Node.js with TypeScript
- **Framework**: Express with WebSocket support
- **Claude Integration**: Official Claude Code SDK
- **Session Management**: File-based persistence

## Requirements

- Android 8.0+ (API level 26+)
- Development server with SSH access
- Claude Code installed on the development server
- Wrapper service installed on the development server

## Development Setup

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Configure your development environment
5. Build and run on your device or emulator

## Security

- All SSH keys are stored encrypted using Android Keystore
- Biometric authentication required for sensitive operations
- Communication secured through SSH tunnels
- No passwords or tokens stored in plain text

## Contributing

Please read the documentation in the `/documentation` directory to understand the architecture and coding standards before contributing.

## License

[License information to be added]

## Contact

[Contact information to be added]