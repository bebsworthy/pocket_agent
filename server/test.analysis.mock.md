# Test Analysis: Mock-Heavy Tests Improvement Plan

This document contains a comprehensive analysis of tests identified as overly reliant on mocks, along with detailed improvement plans for each test.

## Overview

The test-quality-auditor identified 10 tests that are overly reliant on mocks and are testing mock interactions rather than real behavior. Each test has been analyzed by the go-test-engineer to provide:

1. Analysis of current test issues
2. Plan to rewrite with minimal mocking
3. Recommendations on real vs mocked components
4. Example code structure
5. Specific behaviors to verify

## Test 1: TestHandleProjectCreate

### Current Issues
- Tests only verify that mocks were called with expected arguments
- No verification of actual data transformation or business logic
- Brittle test structure dependent on mock setup
- Missing edge cases and error scenarios

### Improvement Plan
1. Use real ProjectManager with in-memory storage
2. Create minimal test session that captures actual responses
3. Verify complete message flow and data transformation
4. Test error scenarios with real validation

### Components
- **Real**: ProjectManager, Validator, Models, Logger
- **Mocked**: WebSocket connection (minimal test double)

### Key Behaviors to Verify
- Project creation with proper validation
- Unique ID generation
- Path validation and sanitization
- Broadcast to subscribers
- Error handling for invalid inputs

## Test 2: TestHandleProjectDelete

### Current Issues
- Only verifies mock method calls
- Doesn't test actual deletion behavior
- No verification of subscriber notification
- Missing cascade effects testing

### Improvement Plan
1. Use real ProjectManager with actual project creation
2. Verify project is actually deleted from storage
3. Test subscriber notification behavior
4. Verify cleanup of associated resources

### Components
- **Real**: ProjectManager, Storage, Broadcaster
- **Mocked**: WebSocket connections for subscribers

### Key Behaviors to Verify
- Project exists before deletion
- Project is removed from storage
- All subscribers are notified
- Message logs are cleaned up
- Cannot delete non-existent project

## Test 3: TestHandleProjectJoin

### Current Issues
- Mock-based verification only
- No testing of actual subscription mechanics
- Missing concurrent join scenarios
- No verification of state synchronization

### Improvement Plan
1. Use real ProjectManager and subscription system
2. Test actual message delivery to joined sessions
3. Verify concurrent join handling
4. Test leave/rejoin scenarios

### Components
- **Real**: ProjectManager, Session management, Message routing
- **Mocked**: WebSocket connection endpoints

### Key Behaviors to Verify
- Session is added to project subscribers
- Current project state is sent on join
- Multiple sessions can join same project
- Session project association is updated
- Broadcasts reach joined sessions

## Test 4: TestHandleProjectList

### Current Issues
- Tests only verify that `GetAllProjects()` was called
- No verification of data transformation logic
- Missing edge cases (empty list, various states)
- Doesn't test the actual response structure

### Improvement Plan
1. Use real ProjectManager with in-memory storage
2. Create test projects with various states
3. Verify exact response structure and content
4. Test data transformation and formatting

### Components
- **Real**: ProjectManager, Models, Logger, Storage
- **Mocked**: WebSocket connection (test double)

### Key Behaviors to Verify
- All projects are included in response
- Date formatting is correct (ISO 8601)
- Subscriber counts are accurate
- Response structure matches protocol
- Empty list handling

### Example Test Structure
```go
func TestHandleProjectList_RealBehavior(t *testing.T) {
    // Use real project manager
    manager, _ := createTestProjectManager(t)
    
    // Create test projects
    proj1, _ := manager.CreateProject("/test/path1")
    proj2, _ := manager.CreateProject("/test/path2")
    
    // Add subscribers to test metadata
    session1 := &models.Session{ID: "sub1"}
    manager.AddSubscriber(proj1.ID, session1)
    
    // Execute handler
    handler := NewProjectHandlers(manager, broadcaster, logger)
    err := handler.HandleProjectList(ctx, session, nil)
    
    // Verify response structure and content
    response := session.GetWrittenResponse()
    projects := response["projects"].([]interface{})
    assert.Len(t, projects, 2)
    
    // Verify project details
    assert.Equal(t, 1, projects[0]["metadata"]["subscriber_count"])
}
```

## Test 5: TestWebSocketUpgrade

### Current Issues
- Tests WebSocket protocol mechanics rather than business logic
- Complex HTTP server setup for simple verification
- No testing of security features or rate limiting
- Missing concurrent connection scenarios

### Improvement Plan
1. Test actual HTTP upgrade behavior
2. Verify security checks (origin, rate limits)
3. Test session creation and management
4. Benchmark concurrent upgrades

### Components
- **Real**: WebSocket server, Rate limiter, Session manager
- **Mocked**: None (use real HTTP test client)

### Key Behaviors to Verify
- Proper HTTP 101 response
- Origin validation
- Rate limiting per IP
- Session creation
- Concurrent connection handling

## Test 6: TestMessageHandling

### Current Issues
- Only tests that router was called
- No verification of message processing
- Missing error handling scenarios
- No concurrent message testing

### Improvement Plan
1. Test complete message flow end-to-end
2. Use real router and handlers
3. Verify response generation
4. Test concurrent message processing

### Components
- **Real**: Message router, All handlers, Validation
- **Mocked**: External dependencies (Claude CLI)

### Key Behaviors to Verify
- Message routing to correct handler
- Response generation and sending
- Error handling and recovery
- Session state management
- Concurrent message processing

## Test 7: TestMessageRouter

### Current Issues
- Tests only routing mechanics
- No verification of handler execution
- Missing middleware integration
- No error propagation testing

### Improvement Plan
1. Use real router with test handlers
2. Verify complete routing behavior
3. Test middleware chain execution
4. Verify error handling

### Components
- **Real**: Router, Middleware, Logger
- **Mocked**: WebSocket connection

### Key Behaviors to Verify
- Correct handler selection
- Middleware execution order
- Context propagation
- Error handling
- Unknown message type handling

## Test 8: TestValidationMiddleware

### Current Issues
- Only verifies next handler was/wasn't called
- No testing of actual validation rules
- Missing comprehensive message type coverage
- No concurrent validation testing

### Improvement Plan
1. Test actual validation logic directly
2. Cover all message types and rules
3. Verify error messages
4. Test thread safety

### Components
- **Real**: Validation middleware, All validation logic
- **Mocked**: Next handler in chain (minimal)

### Key Behaviors to Verify
- Empty message type rejection
- Project ID requirements
- Execute message validation
- Error message content
- Thread safety

## Test 9: TestSendHelpers

### Current Issues
- Complex WebSocket server setup
- Tests protocol mechanics not business logic
- No verification of message content
- Missing error transformation testing

### Improvement Plan
1. Test message construction directly
2. Use minimal connection test double
3. Verify exact message structure
4. Test concurrent sends

### Components
- **Real**: Session, Message construction, Error handling
- **Mocked**: WebSocket write methods only

### Key Behaviors to Verify
- Message structure correctness
- Error transformation logic
- Thread-safe concurrent sends
- Project state serialization
- Connection error handling

## Test 10: TestProcessTracking

### Current Issues
- Directly manipulates internal state
- No real process execution
- Missing lifecycle testing
- No concurrent execution verification

### Improvement Plan
1. Test through public API
2. Use mock CLI for predictable behavior
3. Verify real process lifecycle
4. Test concurrent execution limits

### Components
- **Real**: ClaudeExecutor, Process management, Concurrency control
- **Mocked**: Claude CLI executable (test helper)

### Key Behaviors to Verify
- Process registration during execution
- Concurrent execution limits
- Process cleanup on completion
- Cancellation handling
- Duplicate execution prevention

## Summary

The common themes across all improvements are:

1. **Use Real Components**: Replace mocks with real implementations using in-memory storage or test doubles
2. **Test Observable Behavior**: Focus on what the system does, not how it does it
3. **Verify Data Flow**: Test actual data transformation and message content
4. **Test Integration**: Verify components work together correctly
5. **Cover Edge Cases**: Test error scenarios, concurrency, and limits

These improvements will result in:
- More reliable tests that catch real bugs
- Better documentation of system behavior
- Easier refactoring without breaking tests
- Higher confidence in production behavior