---
description: Display available modules and their descriptions
argument-hint: [module-name]
---

# Modules List Command

List all available modules and their current status.

## Usage
```
/spec:modules
```

## Instructions

You are displaying information about the available modules in this multi-module project.

### Process

1. **Load Module Configuration**
   - Read module definitions from `.spec/spec-config.json`
   - Extract module information from the `modules` section

2. **Check Module Status**
   For each module:
   - Check if module directory exists
   - Check if module architecture documentation exists
   - Count existing features for the module
   - Identify module technology stack from config

3. **Display Module Information**
   Present information in this format:

   ```
   Available Modules:

   📁 server
      Description: Backend server and API services
      Technology: Go, WebSocket, REST API
      Directory: server/ [✅ exists / ❌ missing]
      Architecture: documentation/modules/server/architecture.md [✅ exists / ❌ missing]
      Features: X features

   📁 frontend-react
      Description: React web application frontend
      Technology: React, TypeScript, Tailwind CSS
      Directory: frontend-react/ [✅ exists / ❌ missing]
      Architecture: documentation/modules/frontend-react/architecture.md [✅ exists / ❌ missing]
      Features: X features

   📁 frontend-android
      Description: Android mobile application
      Technology: Kotlin, Jetpack Compose, Room
      Directory: frontend-android/ [✅ exists / ❌ missing]
      Architecture: documentation/modules/frontend-android/architecture.md [✅ exists / ❌ missing]
      Features: X features
   ```

4. **Cross-Module Features Summary**
   - Count features in `documentation/features/`
   - List cross-module features if any exist

5. **Usage Guidance**
   Provide helpful next steps:
   ```
   Usage:
   - Create module architecture: /spec:architecture --module <module>
   - Create module feature: /spec:1_create <feature-name> --module <module>
   - Create cross-module feature: /spec:1_create <feature-name> --cross-module
   ```

### Example Output

```
Available Modules:

📁 server
   Description: Backend server and API services
   Technology: Go, WebSocket, REST API
   Directory: server/ ❌ missing
   Architecture: documentation/modules/server/architecture.md ❌ missing
   Features: 0 features

📁 frontend-react
   Description: React web application frontend
   Technology: React, TypeScript, Tailwind CSS
   Directory: frontend-react/ ❌ missing
   Architecture: documentation/modules/frontend-react/architecture.md ❌ missing
   Features: 0 features

📁 frontend-android
   Description: Android mobile application
   Technology: Kotlin, Jetpack Compose, Room
   Directory: frontend-android/ ✅ exists
   Architecture: documentation/modules/frontend-android/architecture.md ❌ missing
   Features: 0 features

Cross-Module Features: 6 features

Usage:
- Create module architecture: /spec:architecture --module <module>
- Create module feature: /spec:1_create <feature-name> --module <module>
- Create cross-module feature: /spec:1_create <feature-name> --cross-module
```

### Key Information to Display

- Module existence status
- Architecture documentation status
- Feature count per module
- Technology stack per module
- Helpful command examples
- Cross-module feature summary