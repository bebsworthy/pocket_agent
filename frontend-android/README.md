# Pocket Agent - Android Frontend

This is the Android frontend for the Pocket Agent mobile application, built with Kotlin and Jetpack Compose.

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/pocketagent/mobile/
│   │   │   ├── presentation/          # UI layer (Compose, ViewModels, Navigation)
│   │   │   ├── domain/               # Business logic (Use Cases, Repository Interfaces, Models)
│   │   │   ├── data/                 # Data layer (Repositories, Storage, Remote APIs)
│   │   │   ├── di/                   # Dependency Injection (Hilt modules)
│   │   │   └── background/           # Background services
│   │   ├── res/                      # Android resources
│   │   └── AndroidManifest.xml
│   ├── test/                         # Unit tests
│   └── androidTest/                  # Instrumentation tests
├── build.gradle.kts                  # Module build configuration
└── proguard-rules.pro               # ProGuard configuration
```

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: Clean Architecture (Presentation, Domain, Data)
- **Dependency Injection**: Hilt
- **Networking**: OkHttp3 for WebSocket communication
- **Security**: Android Keystore + BiometricPrompt + EncryptedSharedPreferences
- **Data Storage**: Encrypted JSON with Kotlinx.serialization
- **Background Processing**: WorkManager + Foreground Services
- **Testing**: JUnit, Mockito, Hilt Testing

## Current Status

This project is currently in the **foundation setup phase**. The following has been completed:

✅ **Task 1.3: Setup Dependency Injection with Hilt**
- Configured Hilt in the Android project
- Created Application class with @HiltAndroidApp
- Set up Hilt modules for each layer:
  - ApplicationModule: App-level dependencies and coroutine dispatchers
  - DataModule: Data layer (repositories, storage)
  - DomainModule: Domain layer (use cases)
  - SecurityModule: Security layer (encryption, authentication)
  - NetworkModule: Network layer (WebSocket, HTTP)
- Added proper annotations and scopes
- Created TestingModule for testability
- Set up test infrastructure with HiltTestRunner

## Build Requirements

- Android Studio Electric Eel or later
- Kotlin 1.9.20+
- Android SDK 35 (target)
- Min SDK 26 (Android 8.0)

## Building the Project

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

## Next Steps

The next tasks in the development pipeline are:

1. **Task 1.4**: Configure Kotlin Serialization
2. **Task 1.5**: Setup Testing Framework
3. **Task 1.6**: Configure Code Quality Tools
4. **Task 2.1**: Setup Clean Architecture Package Structure
5. **Task 2.2**: Create Base Domain Models

## Security Note

This application handles sensitive data including SSH keys and authentication tokens. All sensitive data is encrypted using Android Keystore and biometric authentication when available.

## License

This project is part of the Pocket Agent suite and follows the same licensing terms.