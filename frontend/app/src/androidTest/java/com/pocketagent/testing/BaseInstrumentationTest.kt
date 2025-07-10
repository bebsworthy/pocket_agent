package com.pocketagent.testing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base class for instrumentation tests that provides common Hilt setup.
 *
 * Features:
 * - Hilt dependency injection
 * - Application context access
 * - Common test setup
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
abstract class BaseInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    protected val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    open fun setUp() {
        hiltRule.inject()
    }
}

/**
 * Base class for Compose instrumentation tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
abstract class BaseComposeInstrumentationTest : BaseInstrumentationTest() {
    // Compose-specific setup will be added here

    @Before
    override fun setUp() {
        super.setUp()
        // Compose-specific setup
    }
}

/**
 * Base class for Activity instrumentation tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
abstract class BaseActivityInstrumentationTest<T : androidx.activity.ComponentActivity> : BaseInstrumentationTest() {
    abstract val activityScenarioRule: ActivityScenarioRule<T>

    @Before
    override fun setUp() {
        super.setUp()
        // Activity-specific setup
    }
}
