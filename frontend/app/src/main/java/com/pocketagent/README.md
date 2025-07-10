# Pocket Agent - Clean Architecture Package Structure

This document describes the complete package structure for the Pocket Agent mobile application, following Clean Architecture principles.

## Overview

The Pocket Agent mobile app enables developers to remotely control Claude Code instances running on development servers through their Android devices. The application is built using modern Android development practices with Jetpack Compose, Kotlin, and follows Clean Architecture principles.

## Package Structure

```
com.pocketagent/
├── presentation/          # UI Layer (Jetpack Compose + ViewModels)
│   ├── ui/
│   │   ├── screens/       # Screen implementations
│   │   │   ├── projects/  # Projects list and management
│   │   │   ├── dashboard/ # Quick actions and project dashboard
│   │   │   ├── chat/      # Claude Code chat interface
│   │   │   ├── files/     # File browser and git status
│   │   │   └── settings/  # App and project settings
│   │   ├── components/    # Reusable UI components
│   │   │   ├── common/    # Common UI components
│   │   │   ├── dialogs/   # Modal dialogs
│   │   │   └── indicators/ # Status indicators
│   │   └── theme/         # Material Design 3 theme
│   ├── viewmodels/        # MVVM ViewModels
│   └── navigation/        # Navigation configuration
├── domain/               # Business Logic Layer (Framework Independent)
│   ├── usecases/         # Business logic operations
│   │   ├── auth/         # Authentication use cases
│   │   ├── projects/     # Project management use cases
│   │   ├── communication/ # Claude Code communication use cases
│   │   └── monitoring/   # Background monitoring use cases
│   ├── repositories/     # Repository interfaces
│   └── models/           # Domain entities and DTOs
│       ├── entities/     # Core domain entities
│       ├── responses/    # API response models
│       └── requests/     # API request models
├── data/                 # Data Layer (Storage + Network + Security)
│   ├── storage/          # Local data storage
│   │   ├── json/         # Encrypted JSON storage
│   │   ├── cache/        # In-memory caching
│   │   └── preferences/  # SharedPreferences
│   ├── remote/           # Remote data access
│   │   ├── websocket/    # WebSocket client
│   │   ├── api/          # REST API clients
│   │   └── dto/          # Data transfer objects
│   └── security/         # Security implementations
│       ├── keystore/     # Android Keystore
│       ├── biometric/    # Biometric authentication
│       └── encryption/   # AES encryption
└── di/                   # Dependency Injection (Hilt)
    ├── modules/          # Hilt modules
    └── qualifiers/       # Custom qualifiers
```

## Architecture Layers

### 1. Presentation Layer (`presentation/`)
- **UI Components**: Jetpack Compose screens and components
- **ViewModels**: MVVM pattern with StateFlow for reactive UI
- **Navigation**: Navigation Compose for app routing
- **Theme**: Material Design 3 with dark/light mode support

### 2. Domain Layer (`domain/`)
- **Use Cases**: Business logic operations following Single Responsibility Principle
- **Repository Interfaces**: Contracts for data access (Dependency Inversion)
- **Domain Models**: Core entities independent of external frameworks
- **Result Wrapper**: Functional approach to error handling

### 3. Data Layer (`data/`)
- **Storage**: Encrypted JSON storage for sensitive data
- **Remote**: WebSocket and REST API implementations
- **Security**: Android Keystore and biometric authentication
- **Repository Implementations**: Concrete implementations of domain interfaces

### 4. Dependency Injection (`di/`)
- **Hilt Modules**: Centralized dependency configuration
- **Qualifiers**: Type-safe dependency disambiguation
- **Scoping**: Proper lifecycle management

## Key Features

### Clean Architecture Benefits
- **Testability**: Framework-independent business logic
- **Maintainability**: Clear separation of concerns
- **Scalability**: Easy to add new features without affecting existing code
- **Flexibility**: Easy to swap implementations

### Security First
- **Hardware-backed Security**: Android Keystore for key storage
- **Biometric Authentication**: Fingerprint/face/iris recognition
- **Encrypted Storage**: AES encryption for sensitive data
- **SSH Key Management**: Secure import and storage of SSH keys

### Modern Android Development
- **Jetpack Compose**: Modern declarative UI toolkit
- **Material Design 3**: Latest Material Design guidelines
- **Coroutines & Flow**: Reactive programming with structured concurrency
- **Hilt**: Dependency injection for Android

## Entity Relationships

The app manages three main entities with clear relationships:

**SSH Identity (1) → (N) Server Profile → (N) Project**

- **SSH Identity**: Encrypted SSH private keys for authentication
- **Server Profile**: Connection endpoints and configuration
- **Project**: Individual Claude Code sessions and codebases

## Base Classes

### Domain Layer Base Classes
- `BaseUseCase<P, R>`: Template for business logic operations
- `BaseUseCaseNoParams<R>`: Template for parameter-less operations
- `Result<T>`: Functional error handling wrapper

### Presentation Layer Base Classes
- `BaseViewModel<S>`: Template for ViewModels with common functionality
- `UiState`: Interface for UI state classes

### Data Layer Base Classes
- `BaseStorageManager<T>`: Template for storage operations
- `BaseApiService`: Template for API service implementations
- `BaseSecurityManager`: Template for security operations

## Next Steps

This package structure provides the foundation for:

1. **Phase 1**: Core Architecture Setup
   - Implement repository pattern interfaces
   - Create base domain models
   - Configure coroutines and Flow

2. **Phase 2**: Data Layer Implementation
   - Encrypted JSON storage
   - WebSocket communication
   - Security services

3. **Phase 3**: UI Layer Implementation
   - Jetpack Compose screens
   - ViewModels and state management
   - Navigation configuration

4. **Phase 4**: Integration and Testing
   - Unit tests for business logic
   - Integration tests for data layer
   - UI tests for presentation layer

## Dependencies

The structure is designed to work with:
- **Jetpack Compose**: Modern UI toolkit
- **Hilt**: Dependency injection
- **OkHttp3**: WebSocket and HTTP client
- **Kotlinx Serialization**: JSON serialization
- **Android Keystore**: Hardware-backed security
- **Biometric API**: Biometric authentication
- **WorkManager**: Background processing

This clean architecture foundation ensures the Pocket Agent app will be maintainable, testable, and scalable as it grows in complexity.