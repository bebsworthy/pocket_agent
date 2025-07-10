package com.pocketagent.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule

/**
 * Utility functions for Compose UI testing.
 */
object ComposeTestUtils {
    /**
     * Creates a test NavController for navigation testing.
     */
    fun createTestNavController(): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestNavHostController(context)
    }

    /**
     * Sets up a Compose test with proper context and providers.
     */
    fun ComposeContentTestRule.setTestContent(
        navController: NavController? = null,
        density: Density = Density(1f),
        content: @Composable () -> Unit,
    ) {
        this.setContent {
            CompositionLocalProvider(
                LocalContext provides ApplicationProvider.getApplicationContext(),
                LocalDensity provides density,
                LocalInspectionMode provides false,
            ) {
                content()
            }
        }
    }

    /**
     * Waits for the compose UI to be idle.
     */
    fun ComposeContentTestRule.waitForCompose() {
        this.waitForIdle()
    }

    /**
     * Finds a node with test tag and optional text.
     */
    fun ComposeContentTestRule.findByTestTag(
        tag: String,
        useUnmergedTree: Boolean = false,
    ): SemanticsNodeInteraction {
        return this.onNode(hasTestTag(tag), useUnmergedTree = useUnmergedTree)
    }

    /**
     * Finds all nodes with test tag.
     */
    fun ComposeContentTestRule.findAllByTestTag(
        tag: String,
        useUnmergedTree: Boolean = false,
    ): SemanticsNodeInteractionCollection {
        return this.onAllNodes(hasTestTag(tag), useUnmergedTree = useUnmergedTree)
    }

    /**
     * Finds a node with content description.
     */
    fun ComposeContentTestRule.findByContentDescription(
        description: String,
        useUnmergedTree: Boolean = false,
    ): SemanticsNodeInteraction {
        return this.onNode(hasContentDescription(description), useUnmergedTree = useUnmergedTree)
    }

    /**
     * Finds a node with text.
     */
    fun ComposeContentTestRule.findByText(
        text: String,
        ignoreCase: Boolean = false,
        useUnmergedTree: Boolean = false,
    ): SemanticsNodeInteraction {
        return this.onNode(hasText(text, ignoreCase = ignoreCase), useUnmergedTree = useUnmergedTree)
    }

    /**
     * Performs a click and waits for compose to be idle.
     */
    fun SemanticsNodeInteraction.clickAndWait(composeRule: ComposeContentTestRule): SemanticsNodeInteraction {
        this.performClick()
        composeRule.waitForIdle()
        return this
    }

    /**
     * Performs text input and waits for compose to be idle.
     */
    fun SemanticsNodeInteraction.typeTextAndWait(
        text: String,
        composeRule: ComposeContentTestRule,
    ): SemanticsNodeInteraction {
        this.performTextInput(text)
        composeRule.waitForIdle()
        return this
    }

    /**
     * Performs scroll to and waits for compose to be idle.
     */
    fun SemanticsNodeInteraction.scrollToAndWait(composeRule: ComposeContentTestRule): SemanticsNodeInteraction {
        this.performScrollTo()
        composeRule.waitForIdle()
        return this
    }

    /**
     * Asserts that the node is displayed and enabled.
     */
    fun SemanticsNodeInteraction.assertDisplayedAndEnabled(): SemanticsNodeInteraction {
        this.assertIsDisplayed()
        this.assertIsEnabled()
        return this
    }

    /**
     * Asserts that the node is displayed but disabled.
     */
    fun SemanticsNodeInteraction.assertDisplayedAndDisabled(): SemanticsNodeInteraction {
        this.assertIsDisplayed()
        this.assertIsNotEnabled()
        return this
    }

    /**
     * Asserts that the node has specific text and is displayed.
     */
    fun SemanticsNodeInteraction.assertTextAndDisplayed(
        text: String,
        ignoreCase: Boolean = false,
    ): SemanticsNodeInteraction {
        this.assertTextEquals(text, ignoreCase = ignoreCase)
        this.assertIsDisplayed()
        return this
    }

    /**
     * Asserts that the node has specific content description and is displayed.
     */
    fun SemanticsNodeInteraction.assertContentDescriptionAndDisplayed(description: String): SemanticsNodeInteraction {
        this.assertContentDescriptionEquals(description)
        this.assertIsDisplayed()
        return this
    }
}

/**
 * Base class for Compose UI tests with common setup.
 */
abstract class BaseComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    protected fun setTestContent(content: @Composable () -> Unit) {
        composeTestRule.setTestContent(content = content)
    }

    protected fun waitForCompose() {
        composeTestRule.waitForCompose()
    }

    protected fun findByTestTag(tag: String) = composeTestRule.findByTestTag(tag)

    protected fun findByText(text: String) = composeTestRule.findByText(text)

    protected fun findByContentDescription(description: String) = composeTestRule.findByContentDescription(description)
}

/**
 * Semantic matchers for common UI patterns.
 */
object ComposeMatchers {
    /**
     * Matcher for loading states.
     */
    fun hasLoadingState() = hasTestTag("loading")

    /**
     * Matcher for error states.
     */
    fun hasErrorState() = hasTestTag("error")

    /**
     * Matcher for success states.
     */
    fun hasSuccessState() = hasTestTag("success")

    /**
     * Matcher for empty states.
     */
    fun hasEmptyState() = hasTestTag("empty")

    /**
     * Matcher for connection status indicators.
     */
    fun hasConnectionStatus(status: String) = hasTestTag("connection_status_$status")

    /**
     * Matcher for progress indicators.
     */
    fun hasProgressIndicator() = hasTestTag("progress_indicator")

    /**
     * Matcher for buttons by their role.
     */
    fun hasButtonRole(role: String) = hasTestTag("button_$role")

    /**
     * Matcher for input fields by their type.
     */
    fun hasInputField(type: String) = hasTestTag("input_$type")

    /**
     * Matcher for navigation items.
     */
    fun hasNavigationItem(item: String) = hasTestTag("nav_$item")

    /**
     * Matcher for list items.
     */
    fun hasListItem(itemId: String) = hasTestTag("list_item_$itemId")
}

/**
 * Accessibility testing utilities.
 */
object AccessibilityTestUtils {
    /**
     * Asserts that all nodes have proper accessibility labels.
     */
    fun ComposeContentTestRule.assertAllNodesHaveAccessibilityLabels() {
        val allNodes = this.onAllNodes(hasClickAction())
        allNodes.fetchSemanticsNodes().forEach { node ->
            assert(
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) != null ||
                    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text) != null,
            ) {
                "Node should have either content description or text for accessibility"
            }
        }
    }

    /**
     * Asserts that all interactive elements have minimum touch target size.
     */
    fun ComposeContentTestRule.assertMinimumTouchTargetSize(minimumSize: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.dp(48)) {
        val interactiveNodes = this.onAllNodes(hasClickAction())
        interactiveNodes.fetchSemanticsNodes().forEach { node ->
            val bounds = node.boundsInRoot
            assert(bounds.width >= minimumSize.value && bounds.height >= minimumSize.value) {
                "Interactive element should have minimum touch target size of $minimumSize"
            }
        }
    }

    /**
     * Simulates TalkBack navigation.
     */
    fun ComposeContentTestRule.simulateTalkBackNavigation() {
        // This would implement TalkBack gesture simulation
        // For now, it's a placeholder for future implementation
    }
}
