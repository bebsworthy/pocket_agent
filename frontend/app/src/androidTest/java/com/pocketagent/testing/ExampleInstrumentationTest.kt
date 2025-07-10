package com.pocketagent.testing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example instrumentation test demonstrating the testing framework usage.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentationTest : BaseInstrumentationTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBasicCompose() {
        // Given
        composeTestRule.setContent {
            MaterialTheme {
                testScreen()
            }
        }

        // When & Then
        composeTestRule.onNodeWithText("Hello World").assertExists()
    }

    @Test
    fun testComposeInteraction() {
        // Given
        var clicked = false

        composeTestRule.setContent {
            MaterialTheme {
                testButtonScreen { clicked = true }
            }
        }

        // When
        composeTestRule.onNodeWithText("Click Me").performClick()

        // Then
        assertThat(clicked).isTrue()
    }

    @Test
    fun testWithComposeUtils() {
        // Given
        composeTestRule.setTestContent {
            MaterialTheme {
                testScreen()
            }
        }

        // When & Then
        composeTestRule.findByText("Hello World").assertExists()
    }

    @Test
    fun testWithTestDataFactory() {
        // Given
        val testProject = TestDataFactory.createProject(name = "Integration Test Project")

        // When
        val hasValidName = testProject.name.isNotBlank()

        // Then
        assertThat(hasValidName).isTrue()
        assertThat(testProject.name).isEqualTo("Integration Test Project")
    }

    @Test
    fun testAccessibility() {
        // Given
        composeTestRule.setContent {
            MaterialTheme {
                testAccessibilityScreen()
            }
        }

        // When & Then
        AccessibilityTestUtils.run {
            composeTestRule.assertAllNodesHaveAccessibilityLabels()
        }
    }
}

@Composable
private fun testScreen() {
    Text(text = "Hello World")
}

@Composable
private fun testButtonScreen(onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick) {
        Text(text = "Click Me")
    }
}

@Composable
private fun testAccessibilityScreen() {
    androidx.compose.foundation.layout.Column {
        androidx.compose.material3.Button(
            onClick = { },
            modifier =
                androidx.compose.ui.Modifier.semantics {
                    contentDescription = "Primary action button"
                },
        ) {
            Text("Action")
        }

        Text(
            text = "Information text",
            modifier =
                androidx.compose.ui.Modifier.semantics {
                    contentDescription = "Information display"
                },
        )
    }
}
