# Coroutines and Flow Configuration

This directory contains comprehensive coroutine and Flow configuration for the Pocket Agent mobile application. The configuration provides optimized, scalable, and testable coroutine usage throughout the application.

## Architecture Overview

The coroutine configuration follows a layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │    ViewModels   │  │   Use Cases     │  │   Repositories  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                  Coroutine Framework                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Dispatchers   │  │     Scopes      │  │   Flow Utils    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ Error Handling  │  │  Cancellation   │  │ Optimizations   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                    Core Coroutines                          │
│              kotlinx.coroutines library                     │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. CoroutineDispatchers
**File**: `CoroutineDispatchers.kt`

Centralized dispatcher provider for consistent coroutine usage across the application.

```kotlin
// Inject and use
@Inject lateinit var dispatchers: CoroutineDispatchers

// Usage in repository
suspend fun loadData() = withContext(dispatchers.io) {
    // Perform I/O operation
}
```

**Key Features**:
- Type-safe dispatcher selection
- Consistent dispatcher usage
- Easy testing with test dispatchers

### 2. FlowConfiguration
**File**: `FlowConfiguration.kt`

Configuration and utilities for Flow-based reactive programming.

```kotlin
// Create configured StateFlow
val state = FlowConfiguration.createStateFlow(initialValue)

// Create configured SharedFlow
val events = FlowConfiguration.createSharedFlow()

// Apply optimizations
val optimizedFlow = flow
    .withErrorHandling()
    .withRetry()
    .withPerformanceOptimizations(dispatchers.io)
```

**Key Features**:
- Standardized Flow configurations
- Performance optimizations
- Error handling integration
- Hot flow utilities

### 3. CoroutineScopes
**File**: `CoroutineScopes.kt`

Centralized scope management for different lifecycle scenarios.

```kotlin
// Application-wide operations
coroutineScopes.launchInApplicationScope {
    // Long-running operation
}

// WebSocket operations
coroutineScopes.launchInWebSocketScope {
    // WebSocket message handling
}

// ViewModel operations with error handling
viewModel.launchWithErrorHandling { throwable ->
    // Handle error
} {
    // Operation
}
```

**Key Features**:
- Lifecycle-aware scope management
- Hierarchical cancellation
- Automatic cleanup
- Error handling integration

### 4. CoroutineErrorHandler
**File**: `CoroutineErrorHandler.kt`

Centralized error handling for all coroutine operations.

```kotlin
// General exception handler
val handler = errorHandler.createGeneralExceptionHandler()

// WebSocket-specific handler
val wsHandler = errorHandler.createWebSocketExceptionHandler(
    onConnectionError = { /* reconnect */ },
    onTimeoutError = { /* retry */ }
)

// Safe execution with error handling
val result = ErrorHandlingUtils.safeCall(
    onError = { defaultValue },
    block = { riskyOperation() }
)
```

**Key Features**:
- Context-specific error handling
- Automatic retry logic
- Error categorization
- Recovery mechanisms

### 5. CancellationUtils
**File**: `CancellationUtils.kt`

Comprehensive cancellation support with proper cleanup.

```kotlin
// Graceful cancellation
cancellationUtils.cancelGracefully(job, timeoutMs = 5000)

// Hierarchical cancellation
val nodeId = hierarchicalManager.registerJob(job, parentId)
hierarchicalManager.cancelHierarchy(nodeId)

// Cancellation-aware execution
cancellationUtils.withCancellationHandling(
    onCancellation = { cleanup() }
) {
    // Operation
}
```

**Key Features**:
- Graceful cancellation with timeout
- Hierarchical cancellation management
- Cooperative cancellation for CPU-intensive operations
- Proper cleanup execution

### 6. CoroutineOptimizations
**File**: `CoroutineOptimizations.kt`

Performance optimizations for different use cases.

```kotlin
// Optimize flow for high-frequency updates
val optimizedFlow = flow.optimizeForHighFrequency()

// Optimize for UI updates
val uiFlow = flow.optimizeForUI()

// Performance monitoring
val result = performanceMonitor.measureExecutionTime("operation") {
    // Operation to measure
}
```

**Key Features**:
- Use case-specific optimizations
- Performance monitoring
- Memory management
- Resource pooling

### 7. CoroutineTestUtils
**File**: `CoroutineTestUtils.kt`

Comprehensive testing utilities for coroutines and flows.

```kotlin
// Test with dispatcher control
CoroutineTestUtils.runTestWithDispatcher {
    // Test coroutine operations
}

// Test flow emissions
FlowTestUtils.testFlowEmissions(flow) { collector ->
    collector.assertValueCount(3)
    collector.assertValueAt(0, expectedValue)
}
```

**Key Features**:
- Test dispatcher management
- Flow testing utilities
- Time control for tests
- Mock implementations

## Dependency Injection Setup

### Module Configuration

```kotlin
// Add to your application module
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // ... other bindings
}

// Include CoroutineModule
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    // Provided dispatchers and scopes
}
```

### Qualifiers

Use the provided qualifiers for dependency injection:

```kotlin
class MyRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
)
```

## Usage Patterns

### Repository Pattern

```kotlin
class UserRepository @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
    private val errorHandler: CoroutineErrorHandler
) {
    suspend fun getUser(id: String): Result<User> = withContext(dispatchers.io) {
        ErrorHandlingUtils.safeCall(
            onError = { Result.failure(it) }
        ) {
            // Network call
            Result.success(userService.getUser(id))
        }
    }
}
```

### ViewModel Pattern

```kotlin
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val coroutineScopes: CoroutineScopes
) : ViewModel() {
    
    private val _userState = FlowConfiguration.createStateFlow(UserState.Loading)
    val userState = _userState.asStateFlow()
    
    fun loadUser(id: String) {
        launchWithErrorHandling { throwable ->
            _userState.value = UserState.Error(throwable.message)
        } {
            _userState.value = UserState.Loading
            val result = userRepository.getUser(id)
            _userState.value = UserState.Success(result)
        }
    }
}
```

### WebSocket Operations

```kotlin
class WebSocketManager @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
    @WebSocketScope private val scope: CoroutineScope,
    private val cancellationUtils: CancellationUtils
) {
    
    private val _messages = FlowConfiguration.createSharedFlow<Message>()
    val messages = _messages.asSharedFlow()
    
    fun startListening() {
        scope.launch(dispatchers.io) {
            cancellationUtils.withCancellationHandling(
                onCancellation = { cleanup() }
            ) {
                websocket.messages
                    .optimizeForWebSocket()
                    .collect { message ->
                        _messages.emit(message)
                    }
            }
        }
    }
}
```

## Testing

### Unit Tests

```kotlin
class UserRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()
    
    @Test
    fun `test user loading`() = coroutineRule.runTest {
        // Test implementation
    }
}
```

### Flow Tests

```kotlin
@Test
fun `test flow emissions`() = runTest {
    val flow = repository.getUserFlow()
    
    FlowTestUtils.testFlowEmissions(flow) { collector ->
        collector.assertValueCount(2)
        collector.assertValueAt(0, UserState.Loading)
        collector.assertValueAt(1, UserState.Success(user))
    }
}
```

## Performance Considerations

### Memory Management

- Use `distinctUntilChanged()` to prevent duplicate emissions
- Apply `conflate()` for high-frequency updates
- Use appropriate buffer sizes for different scenarios

### CPU Optimization

- Use `yield()` in CPU-intensive operations
- Apply periodic cancellation checks
- Use appropriate dispatchers for different operation types

### Network Optimization

- Use `buffer()` for WebSocket messages
- Apply retry logic for transient failures
- Use connection pooling for HTTP requests

## Best Practices

### 1. Dispatcher Selection

- **Main**: UI updates, lightweight operations
- **IO**: Network, file I/O, database operations
- **Default**: CPU-intensive operations, data processing
- **Unconfined**: Testing, immediate execution

### 2. Error Handling

- Always handle `CancellationException` separately
- Use context-specific error handlers
- Implement retry logic for recoverable errors
- Log errors with appropriate levels

### 3. Cancellation

- Use `SupervisorJob` for independent child coroutines
- Implement graceful cancellation with cleanup
- Use cooperative cancellation for CPU-intensive operations
- Handle cancellation in finally blocks

### 4. Flow Usage

- Use `StateFlow` for state management
- Use `SharedFlow` for events
- Apply appropriate optimizations for different use cases
- Handle errors with `catch` operator

### 5. Testing

- Use test dispatchers for deterministic testing
- Test cancellation behavior
- Mock external dependencies
- Use time control for time-dependent operations

## Migration Guide

### From Existing Code

1. **Replace direct dispatcher usage**:
   ```kotlin
   // Before
   withContext(Dispatchers.IO) { /* ... */ }
   
   // After
   withContext(dispatchers.io) { /* ... */ }
   ```

2. **Replace manual scope management**:
   ```kotlin
   // Before
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
   
   // After
   @Inject @ApplicationScope private val scope: CoroutineScope
   ```

3. **Replace manual Flow configuration**:
   ```kotlin
   // Before
   private val _state = MutableStateFlow(initialValue)
   
   // After
   private val _state = FlowConfiguration.createStateFlow(initialValue)
   ```

4. **Add error handling**:
   ```kotlin
   // Before
   scope.launch { riskyOperation() }
   
   // After
   scope.launchWithErrorHandling { throwable ->
       // Handle error
   } {
       riskyOperation()
   }
   ```

## Monitoring and Debugging

### Performance Monitoring

```kotlin
// Enable performance monitoring
val stats = performanceMonitor.getPerformanceStats()
Log.d("Performance", "Average execution time: ${stats.averageExecutionTime}ms")
```

### Debug Logging

All components include debug logging that can be enabled:

```kotlin
// Enable debug logging in logcat
adb shell setprop log.tag.CoroutineErrorHandler DEBUG
```

## Conclusion

This coroutine configuration provides a robust, scalable, and testable foundation for coroutine usage in the Pocket Agent application. It handles common patterns, provides performance optimizations, and ensures proper error handling and cancellation throughout the application.

The configuration is designed to be:
- **Type-safe**: Compile-time safety with proper typing
- **Performant**: Optimized for different use cases
- **Testable**: Comprehensive testing utilities
- **Maintainable**: Clean separation of concerns
- **Extensible**: Easy to add new patterns and optimizations