# UI Navigation & Foundation Feature Specification - Testing
**For Android Mobile Application**

> **Navigation**: [Overview](./ui-navigation-overview.feat.md) | [Navigation](./ui-navigation-navigation.feat.md) | [Theme](./ui-navigation-theme.feat.md) | [Components](./ui-navigation-components.feat.md) | [Scaffolding](./ui-navigation-scaffolding.feat.md) | [State](./ui-navigation-state.feat.md) | **Testing** | [Implementation](./ui-navigation-implementation.feat.md) | [Index](./ui-navigation-index.md)

## Testing Checklist

**Purpose**: Comprehensive testing checklist to ensure all aspects of the UI Navigation & Foundation feature are properly tested, including navigation flows, theme switching, component behavior, and accessibility.

```kotlin
/**
 * UI Navigation & Foundation Testing Checklist:
 * 
 * Navigation Tests:
 * 1. [ ] Test navigation between all top-level screens
 * 2. [ ] Test project-level tab navigation
 * 3. [ ] Test deep linking to specific screens
 * 4. [ ] Test back navigation and up navigation
 * 5. [ ] Test navigation state restoration after process death
 * 6. [ ] Test navigation with arguments passing
 * 7. [ ] Test navigation animations and transitions
 * 
 * Theme Tests:
 * 8. [ ] Test light/dark theme switching
 * 9. [ ] Test dynamic color support on Android 12+
 * 10. [ ] Test theme persistence across app restarts
 * 11. [ ] Test custom color application
 * 
 * Component Tests:
 * 12. [ ] Test all base UI components in isolation
 * 13. [ ] Test component states (enabled, disabled, loading)
 * 14. [ ] Test component interactions and callbacks
 * 15. [ ] Test component accessibility labels
 * 
 * Screen Tests:
 * 16. [ ] Test screen scaffolding structure
 * 17. [ ] Test screen state management (loading, error, success)
 * 18. [ ] Test screen orientation changes
 * 19. [ ] Test screen keyboard handling
 * 
 * Integration Tests:
 * 20. [ ] Test navigation with real data
 * 21. [ ] Test theme changes with all screens
 * 22. [ ] Test error handling across navigation
 * 23. [ ] Test memory leaks in navigation
 * 
 * Accessibility Tests:
 * 24. [ ] Test with TalkBack enabled
 * 25. [ ] Test with large text sizes
 * 26. [ ] Test with reduced animations
 * 27. [ ] Test keyboard navigation
 * 
 * Performance Tests:
 * 28. [ ] Test navigation performance with many screens
 * 29. [ ] Test theme switching performance
 * 30. [ ] Test component rendering performance
 */
```

## Unit Tests

**Purpose**: Example unit tests for navigation logic, theme configuration, and UI state management, demonstrating proper testing patterns for Compose UI components.

```kotlin
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assert

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navController: NavHostController
    
    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = rememberNavController()
            AppNavigation(navController = navController)
        }
    }
    
    @Test
    fun navigateToProjectsList_fromWelcome() {
        // Given: On welcome screen
        composeTestRule.onNodeWithText("Welcome to Pocket Agent").assertIsDisplayed()
        
        // When: Click get started
        composeTestRule.onNodeWithText("Get Started").performClick()
        
        // Then: Navigate to projects list
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
        assert(navController.currentDestination?.route == Screen.ProjectsList::class.qualifiedName)
    }
    
    @Test
    fun navigateToProjectDetail_withTabs() {
        // Given: Navigate to project detail
        navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // Then: Bottom navigation is displayed
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
    
    @Test
    fun switchBetweenProjectTabs() {
        // Given: In project detail screen
        navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // When: Click chat tab
        composeTestRule.onNodeWithText("Chat").performClick()
        
        // Then: Chat screen is displayed
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // When: Click files tab
        composeTestRule.onNodeWithText("Files").performClick()
        
        // Then: Files screen is displayed
        composeTestRule.onNodeWithContentDescription("File browser").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun themeSwitching_lightToDark() {
        var themeConfig = ThemeConfig(themeMode = ThemeMode.LIGHT)
        
        composeTestRule.setContent {
            PocketAgentTheme(themeConfig = themeConfig) {
                BaseScreen(title = "Test") {
                    // Content
                }
            }
        }
        
        // Verify light theme colors
        // ... color assertions
        
        // Switch to dark theme
        themeConfig = ThemeConfig(themeMode = ThemeMode.DARK)
        
        composeTestRule.setContent {
            PocketAgentTheme(themeConfig = themeConfig) {
                BaseScreen(title = "Test") {
                    // Content
                }
            }
        }
        
        // Verify dark theme colors
        // ... color assertions
    }
}

@RunWith(AndroidJUnit4::class)
class ComponentTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun primaryActionButton_states() {
        var clicked = false
        
        composeTestRule.setContent {
            PrimaryActionButton(
                text = "Action",
                onClick = { clicked = true },
                loading = false
            )
        }
        
        // Test enabled state
        composeTestRule.onNodeWithText("Action").assertIsEnabled()
        composeTestRule.onNodeWithText("Action").performClick()
        assert(clicked)
        
        // Test loading state
        composeTestRule.setContent {
            PrimaryActionButton(
                text = "Action",
                onClick = { },
                loading = true
            )
        }
        
        composeTestRule.onNode(hasProgressBarRangeInfo()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertDoesNotExist()
    }
    
    @Test
    fun statusChip_displaysCorrectly() {
        composeTestRule.setContent {
            Column {
                StatusChip(status = ConnectionStatus.CONNECTED)
                StatusChip(status = ConnectionStatus.CONNECTING)
                StatusChip(status = ConnectionStatus.DISCONNECTED)
                StatusChip(status = ConnectionStatus.ERROR)
            }
        }
        
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connecting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    }
}
```

## Integration Tests

**Purpose**: Integration tests demonstrating full navigation flows, theme persistence, and component interaction with real ViewModels and navigation controllers.

```kotlin
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun fullNavigationFlow_fromWelcomeToProject() {
        // Start at welcome screen
        composeRule.onNodeWithText("Welcome to Pocket Agent").assertIsDisplayed()
        
        // Navigate to projects
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        
        // Create new project
        composeRule.onNodeWithContentDescription("Create new project").performClick()
        composeRule.waitForIdle()
        
        // Fill project details
        composeRule.onNodeWithText("Project Name").performTextInput("Test Project")
        composeRule.onNodeWithText("Next").performClick()
        
        // Select server
        composeRule.onNodeWithText("Select Server").performClick()
        composeRule.onNodeWithText("Development Server").performClick()
        
        // Complete creation
        composeRule.onNodeWithText("Create Project").performClick()
        composeRule.waitForIdle()
        
        // Verify navigation to project detail
        composeRule.onNodeWithText("Test Project").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
    }
    
    @Test
    fun navigationStatePersistence_acrossConfigChange() {
        // Navigate to specific screen
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        
        // Navigate to a project
        composeRule.onNodeWithText("Sample Project").performClick()
        composeRule.waitForIdle()
        
        // Switch to chat tab
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.waitForIdle()
        
        // Rotate device
        composeRule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        
        // Verify still on chat tab
        composeRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // Rotate back
        composeRule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()
        
        // Verify still on chat tab
        composeRule.onNodeWithContentDescription("Chat input field").assertExists()
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ThemeIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun themePersistence_acrossAppRestart() {
        // Navigate to settings
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        
        // Change to dark theme
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.waitForIdle()
        
        // Restart activity
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        
        // Verify dark theme is still applied
        // This would require checking actual theme values
        // which would be done through custom assertions
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccessibilityIntegrationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun navigation_withTalkBackEnabled() {
        // Enable accessibility services in test
        composeRule.onRoot().performTouchInput {
            // Simulate TalkBack gestures
        }
        
        // Verify all content has proper descriptions
        composeRule.onAllNodes(hasContentDescription()).assertCountEquals(expectedAccessibleElements)
        
        // Test navigation with keyboard
        // ... keyboard navigation tests
    }
}
```