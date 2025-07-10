# Pocket Agent - Build Configuration

## Overview

This Android project is configured with a comprehensive build system that includes all necessary dependencies for the Pocket Agent mobile application. The build system is designed to support:

- **Jetpack Compose** with Material Design 3
- **WebSocket communication** via OkHttp3
- **SSH key authentication** using Bouncy Castle
- **Secure storage** with Android Keystore and biometric authentication
- **Background processing** with WorkManager and foreground services
- **Dependency injection** with Hilt

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/pocketagent/app/
│   │   │   ├── presentation/          # UI layer (Compose)
│   │   │   ├── domain/                # Business logic
│   │   │   ├── data/                  # Data layer
│   │   │   └── di/                    # Dependency injection
│   │   ├── res/                       # Resources
│   │   └── AndroidManifest.xml
│   ├── test/                          # Unit tests
│   └── androidTest/                   # Integration tests
├── build.gradle.kts                   # App module configuration
└── proguard-rules.pro                 # ProGuard/R8 rules
```

## Dependencies

### Core Android
- **androidx.core:core-ktx** - Android KTX extensions
- **androidx.lifecycle:lifecycle-runtime-ktx** - Lifecycle components
- **androidx.activity:activity-compose** - Compose activity integration
- **androidx.work:work-runtime-ktx** - Background work processing

### UI Framework
- **Jetpack Compose BOM** - Bill of materials for consistent versioning
- **Material Design 3** - Modern UI components
- **Navigation Compose** - Navigation for Compose
- **Compose Animation** - Animation support

### Networking
- **OkHttp3** - HTTP client with WebSocket support
- **Retrofit** - Type-safe HTTP client
- **Kotlinx Serialization** - JSON serialization

### Security
- **Bouncy Castle** - SSH key operations
- **Android Keystore** - Hardware-backed key storage
- **Biometric Authentication** - Fingerprint/face recognition
- **EncryptedSharedPreferences** - Secure storage

### Dependency Injection
- **Hilt** - Dependency injection framework
- **Hilt Navigation Compose** - Navigation integration
- **Hilt Work** - WorkManager integration

### Testing
- **JUnit** - Unit testing framework
- **Mockito** - Mocking framework
- **Espresso** - UI testing
- **Compose UI Testing** - Compose-specific testing

## Build Configuration

### Build Types
- **debug**: Development builds with debugging enabled
- **release**: Production builds with code optimization

### Signing Configuration
Release builds require signing configuration in `local.properties`:
```properties
POCKET_AGENT_STORE_FILE=/path/to/keystore.jks
POCKET_AGENT_STORE_PASSWORD=your_store_password
POCKET_AGENT_KEY_ALIAS=your_key_alias
POCKET_AGENT_KEY_PASSWORD=your_key_password
```

### ProGuard/R8 Configuration
- Full mode R8 optimization enabled
- Custom rules for Kotlin serialization, Hilt, OkHttp, and Bouncy Castle
- Keeps SSH key related classes and domain models

## Setup Instructions

1. **Install Android Studio** with Android SDK
2. **Copy configuration**: `cp local.properties.template local.properties`
3. **Set SDK path** in `local.properties`
4. **Generate keystore** for release builds:
   ```bash
   keytool -genkey -v -keystore pocket_agent.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pocket_agent
   ```
5. **Update signing config** in `local.properties`
6. **Build the project**:
   ```bash
   ./gradlew build
   ```

## Build Commands

### Development
```bash
# Build debug APK
./gradlew assembleDebug

# Install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run integration tests
./gradlew connectedAndroidTest
```

### Production
```bash
# Build release APK
./gradlew assembleRelease

# Build release AAB (for Play Store)
./gradlew bundleRelease

# Generate signed APK
./gradlew assembleRelease
```

### Quality Checks
```bash
# Run all tests
./gradlew check

# Generate test coverage report
./gradlew jacocoTestReport

# Run lint checks
./gradlew lint
```

## Performance Optimizations

### Build Performance
- **Gradle configuration cache** enabled
- **Build cache** enabled  
- **Parallel execution** enabled
- **Incremental compilation** enabled

### Runtime Performance
- **R8 full mode** for aggressive optimization
- **Resource shrinking** enabled
- **Vector drawable support** for smaller APK size
- **Non-transitive R classes** for faster builds

## Security Features

### Data Protection
- **Encrypted storage** for sensitive data
- **Biometric authentication** required for access
- **No cloud backup** for sensitive files
- **Certificate pinning** for network security

### Permission Management
- **Runtime permissions** for sensitive operations
- **Foreground service** for background operations
- **Network security config** for HTTPS enforcement

## Feature Flags

Configure features via `gradle.properties`:
```properties
FEATURE_VOICE_INTEGRATION=false
FEATURE_ADVANCED_LOGGING=false
FEATURE_DEVELOPMENT_TOOLS=false
```

## Troubleshooting

### Common Issues
1. **Build fails with "SDK not found"**: Set `sdk.dir` in `local.properties`
2. **Signing errors**: Verify keystore path and passwords in `local.properties`
3. **Out of memory**: Increase heap size in `gradle.properties`
4. **Slow builds**: Enable parallel execution and build cache

### Debug Tools
- **Debug logging**: Enable via `DEBUG_LOGGING=true` in `gradle.properties`
- **Network inspection**: Use OkHttp logging interceptor in debug builds
- **Layout Inspector**: Available in Android Studio for UI debugging

## Next Steps

The build system is now fully configured and ready for development. The next tasks in the implementation timeline are:

1. **Setup Dependency Injection with Hilt** (Task 1.3)
2. **Configure Kotlin Serialization** (Task 1.4)
3. **Setup Testing Framework** (Task 1.5)
4. **Configure Code Quality Tools** (Task 1.6)

All required dependencies are already included in the build configuration, making subsequent development tasks straightforward to implement.