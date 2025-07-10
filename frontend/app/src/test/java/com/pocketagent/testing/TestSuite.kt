package com.pocketagent.testing

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for running all unit tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Example tests - these would be replaced with actual test classes
    ExampleUnitTest::class,
    // Add other unit test classes here
)
class UnitTestSuite

/**
 * Test suite for communication layer tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // WebSocket tests
    com.pocketagent.communication.WebSocketClientTest::class,
    // Add other communication tests here
)
class CommunicationTestSuite

/**
 * Test suite for UI layer tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Navigation tests would go here
    // Add UI test classes here
)
class UITestSuite

/**
 * Test suite for data layer tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Repository tests would go here
    // Add data layer test classes here
)
class DataTestSuite

/**
 * Test suite for security tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // SSH key tests would go here
    // Add security test classes here
)
class SecurityTestSuite

/**
 * Complete test suite for all unit tests.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    UnitTestSuite::class,
    CommunicationTestSuite::class,
    UITestSuite::class,
    DataTestSuite::class,
    SecurityTestSuite::class
)
class AllUnitTestSuite