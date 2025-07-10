# Testing Framework for Pocket Agent

This directory contains the comprehensive testing framework for the Pocket Agent Android application.

## Overview

The testing framework provides:

- **Unit Tests**: Fast, isolated tests for business logic
- **Integration Tests**: Tests for component interactions
- **UI Tests**: Compose UI testing with accessibility validation
- **Instrumentation Tests**: Android framework integration tests
- **Mock Utilities**: Mockito and MockK helpers
- **Test Data Builders**: Easy test data creation
- **Coroutine Testing**: Utilities for async code testing

## Test Structure

```
src/test/java/
├── com/pocketagent/testing/
│   ├── BaseUnitTest.kt                 # Base class for unit tests
│   ├── BaseViewModelTest.kt           # Base class for ViewModel tests
│   ├── MockitoTestUtils.kt            # Mockito utilities
│   ├── MockKTestUtils.kt              # MockK utilities
│   ├── TestDataBuilders.kt            # Test data creation
│   ├── CoroutineTestUtils.kt          # Coroutine testing utilities
│   ├── TestSuite.kt                   # Test suite definitions
│   └── ExampleUnitTest.kt             # Example unit test
│
├── com/pocketagent/communication/
│   └── WebSocketClientTest.kt         # WebSocket testing example
│
└── resources/
    ├── robolectric.properties         # Robolectric configuration
    └── junit-platform.properties     # JUnit configuration

src/androidTest/java/
├── com/pocketagent/testing/
│   ├── HiltTestRunner.kt              # Custom test runner
│   ├── BaseInstrumentationTest.kt     # Base instrumentation test
│   ├── ComposeTestUtils.kt            # Compose testing utilities
│   ├── InstrumentationTestUtils.kt    # Android test utilities
│   ├── InstrumentationTestSuite.kt    # Instrumentation test suites
│   └── ExampleInstrumentationTest.kt  # Example instrumentation test
│
├── com/pocketagent/ui/navigation/
│   └── NavigationTest.kt              # Navigation testing example
│
└── com/pocketagent/service/
    └── BackgroundServiceTest.kt       # Service testing example
```

## Running Tests

### Unit Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "com.pocketagent.communication.WebSocketClientTest"

# Run tests with coverage
./gradlew testDebugUnitTest jacocoTestReport
```

### Instrumentation Tests

```bash
# Run all instrumentation tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pocketagent.ui.navigation.NavigationTest

# Run tests with orchestrator
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.clearPackageData=true
```

### Test Suites

```bash
# Run communication layer tests
./gradlew testDebugUnitTest --tests "com.pocketagent.testing.CommunicationTestSuite"

# Run UI instrumentation tests
./gradlew connectedAndroidTest --tests "com.pocketagent.testing.UIInstrumentationTestSuite"
```

## Writing Tests

### Unit Tests

```kotlin
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MyRepositoryTest : BaseUnitTest() {
    
    @Mock
    private lateinit var mockDataSource: DataSource
    
    private lateinit var repository: MyRepository
    
    @Before
    override fun setUp() {
        super.setUp()
        repository = MyRepository(mockDataSource)
    }
    
    @Test
    fun `getUser returns user data`() = runTest {
        // Given
        val expectedUser = TestDataFactory.createUser()
        whenever(mockDataSource.getUser()).thenReturn(expectedUser)
        
        // When
        val result = repository.getUser()
        
        // Then
        assertThat(result).isEqualTo(expectedUser)
    }
}
```

### Compose UI Tests

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MyScreenTest : BaseComposeInstrumentationTest() {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun myScreen_displaysCorrectly() {
        // Given
        composeTestRule.setContent {
            MyScreen()
        }
        
        // When & Then
        composeTestRule.onNodeWithText("Hello World").assertIsDisplayed()
    }
}
```

### ViewModel Tests

```kotlin
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MyViewModelTest : BaseViewModelTest() {
    
    @Mock
    private lateinit var mockRepository: Repository
    
    private lateinit var viewModel: MyViewModel
    
    @Before
    override fun setUp() {
        super.setUp()
        viewModel = MyViewModel(mockRepository)
    }
    
    @Test
    fun `viewModel loads data correctly`() = runTest {
        // Given
        val testData = TestDataFactory.createData()
        whenever(mockRepository.getData()).thenReturn(flowOf(testData))
        
        // When
        viewModel.loadData()
        
        // Then
        viewModel.uiState.value.data.assertThat().isEqualTo(testData)
    }
}
```

## Test Data Builders

Use the provided builders to create test data:

```kotlin
// Create test entities
val sshIdentity = SshIdentityTestBuilder()
    .name("Test SSH Key")
    .keyAlias("test_key")
    .build()

val project = ProjectTestBuilder()
    .name("Test Project")
    .status("CONNECTED")
    .serverProfileId(serverProfile.id)
    .build()

// Or use the factory
val completeSetup = TestDataFactory.createCompleteProjectSetup()
```

## Mock Utilities

### Mockito

```kotlin
// Create mocks
val mockService = MockitoTestUtils.createMock<MyService>()

// Stub suspend functions
whenever(mockService.getData()).thenReturnSuspend(testData)

// Stub flows
whenever(mockService.getFlow()).thenReturn(MockitoTestUtils.stubFlow(testData))
```

### MockK

```kotlin
// Create relaxed mocks
val mockService = MockKTestUtils.createRelaxedMock<MyService>()

// Stub functions
every { mockService.getData() } returns testData

// Verify calls
verify { mockService.getData() }
```

## Coroutine Testing

```kotlin
@Test
fun `test coroutine function`() = runTest {
    // Given
    val testFlow = flowOf("item1", "item2")
    
    // When & Then
    testFlow.assertEmitsSequence(testScope, "item1", "item2")
}
```

## Test Coverage

Generate test coverage reports:

```bash
./gradlew testDebugUnitTest jacocoTestReport
```

Reports are generated in: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

## Best Practices

1. **Use appropriate test types**: Unit tests for business logic, integration tests for component interactions
2. **Mock external dependencies**: Use Mockito/MockK to isolate units under test
3. **Test edge cases**: Include error conditions and boundary cases
4. **Use descriptive test names**: Follow the pattern `methodName_stateUnderTest_expectedBehavior`
5. **Keep tests independent**: Each test should be able to run in isolation
6. **Use test data builders**: Create reusable test data with the provided builders
7. **Test accessibility**: Use the provided accessibility testing utilities
8. **Verify coroutine behavior**: Use coroutine testing utilities for async code

## Troubleshooting

### Common Issues

1. **Test runner not found**: Make sure `testInstrumentationRunner` is set correctly in `build.gradle`
2. **Hilt injection fails**: Use `@HiltAndroidTest` and `HiltTestRunner`
3. **Compose tests fail**: Ensure proper theme setup in test content
4. **MockWebServer issues**: Make sure to start and shutdown server properly
5. **Coroutine tests hang**: Use `UnconfinedTestDispatcher` for immediate execution

### Debug Tips

1. Enable debug logging in tests
2. Use `composeTestRule.onRoot().printToLog("TAG")` to debug Compose hierarchies
3. Add breakpoints in test code
4. Use `Thread.sleep()` sparingly for debugging timing issues
5. Check logcat for system errors during instrumentation tests

## Contributing

When adding new tests:

1. Follow the existing patterns and structure
2. Add appropriate documentation
3. Include both positive and negative test cases
4. Update test suites if needed
5. Ensure tests pass in CI environment