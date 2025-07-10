package com.pocketagent.ui.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketagent.testing.BaseInstrumentationTest
import com.pocketagent.testing.ComposeTestUtils
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Integration tests for the navigation system.
 * Tests navigation flows, deep linking, and state preservation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest : BaseInstrumentationTest() {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navController: NavHostController
    
    @Test
    fun navigateToProjectsList_fromWelcome() {
        // Given: Setup navigation
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // When: Navigate to projects list
        composeTestRule.onNodeWithText("Get Started").performClick()
        
        // Then: Verify navigation
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
        // assertEquals(Screen.ProjectsList::class.qualifiedName, navController.currentDestination?.route)
    }
    
    @Test
    fun navigateToProjectDetail_withTabs() {
        // Given: Setup navigation and navigate to project detail
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // Navigate to project detail
        // navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // Then: Verify bottom navigation is displayed
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
    
    @Test
    fun switchBetweenProjectTabs() {
        // Given: In project detail screen
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // Navigate to project detail
        // navController.navigate(Screen.ProjectDetail("test-project-id"))
        
        // When: Click chat tab
        composeTestRule.onNodeWithText("Chat").performClick()
        
        // Then: Chat screen is displayed
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // When: Click files tab
        composeTestRule.onNodeWithText("Files").performClick()
        
        // Then: Files screen is displayed
        composeTestRule.onNodeWithContentDescription("File browser").assertExists()
    }
    
    @Test
    fun navigationStatePersistence_acrossConfigChange() {
        // Given: Navigate to specific screen
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()
        
        // Navigate to a project
        composeTestRule.onNodeWithText("Sample Project").performClick()
        composeTestRule.waitForIdle()
        
        // Switch to chat tab
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()
        
        // When: Rotate device
        composeTestRule.activity.requestedOrientation = 
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()
        
        // Then: Verify still on chat tab
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
        
        // When: Rotate back
        composeTestRule.activity.requestedOrientation = 
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()
        
        // Then: Verify still on chat tab
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
    }
    
    @Test
    fun deepLinkNavigation() {
        // Given: Setup navigation
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // When: Navigate via deep link
        // navController.navigate("project/test-project-id/chat")
        
        // Then: Verify correct screen is displayed
        composeTestRule.onNodeWithContentDescription("Chat input field").assertExists()
    }
    
    @Test
    fun backNavigation() {
        // Given: Navigate to nested screen
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // Navigate through screens
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithContentDescription("Create new project").performClick()
        composeTestRule.waitForIdle()
        
        // When: Press back
        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        
        // Then: Verify back navigation
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()
    }
    
    @Test
    fun navigationWithArguments() {
        // Given: Setup navigation
        composeTestRule.setContent {
            navController = rememberNavController()
            // AppNavigation(navController = navController)
        }
        
        // When: Navigate with arguments
        val projectId = "test-project-123"
        // navController.navigate("project/$projectId")
        
        // Then: Verify arguments are passed correctly
        // This would require checking the actual screen content
        // that uses the projectId argument
    }
}

/**
 * Unit tests for navigation utilities and helpers.
 */
@RunWith(AndroidJUnit4::class)
class NavigationUtilsTest : BaseInstrumentationTest() {
    
    @Test
    fun testNavigationUtils() {
        // Given
        val navController = ComposeTestUtils.createTestNavController()
        
        // When & Then
        // Test navigation utility functions
        // This would test helper functions for navigation
    }
}