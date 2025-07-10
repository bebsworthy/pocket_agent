package com.pocketagent.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for PocketAgentApplication.
 * 
 * This test verifies that the application can be properly initialized with Hilt.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PocketAgentApplicationTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assert(appContext.packageName == "com.pocketagent.mobile") {
            "Package name should be com.pocketagent.mobile"
        }
    }
    
    @Test
    fun verifyApplicationClass() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val app = appContext.applicationContext as PocketAgentApplication
        assert(app is PocketAgentApplication) {
            "Application should be instance of PocketAgentApplication"
        }
    }
}