package com.pocketagent.testing

import com.pocketagent.service.BackgroundServiceTest
import com.pocketagent.ui.navigation.NavigationTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for all instrumentation tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Example tests
    ExampleInstrumentationTest::class,
    
    // Navigation tests
    NavigationTest::class,
    
    // Service tests
    BackgroundServiceTest::class,
    
    // Add other instrumentation test classes here
)
class AllInstrumentationTestSuite

/**
 * Test suite for UI instrumentation tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    NavigationTest::class,
    // Add other UI test classes here
)
class UIInstrumentationTestSuite

/**
 * Test suite for service instrumentation tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    BackgroundServiceTest::class,
    // Add other service test classes here
)
class ServiceInstrumentationTestSuite

/**
 * Test suite for integration tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Integration tests would go here
    // Add integration test classes here
)
class IntegrationTestSuite

/**
 * Test suite for performance tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Performance tests would go here
    // Add performance test classes here
)
class PerformanceTestSuite