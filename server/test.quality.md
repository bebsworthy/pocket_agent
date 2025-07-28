Here's the complete list of tests organized by file with their issues:

  Complete Test List by File

  /server/internal/config/config_test.go

  1. ✅ TestDefaultConfig (line 11)
  2. ✅ TestLoadFromFile (line 53)
  3. ✅ TestLoadFromEnv (line 109)
  4. ✅ TestValidation (line 163)
  5. ✅ TestParseSize (line 265)
  6. ✅ TestCommandLineOverrides (line 302)
  7. ⚠️ splitEnv helper (line 328) - Helper function mixed with tests
  8. ⚠️ contains helper (line 337) - Uses recursion instead of strings.Contains

  /server/internal/errors/errors_test.go

  1. ✅ TestNew (line 8)
  2. ✅ TestWrap (line 29)
  3. ✅ TestWithDetail (line 61)
  4. ✅ TestWithCause (line 79)
  5. ✅ TestIsCode (line 93)
  6. ✅ TestGetCode (line 114)
  7. ✅ TestToJSON (line 131)
  8. ✅ TestSanitization (line 157)
  9. ✅ TestPathSanitization (line 190)
  10. ✅ TestCommonConstructors (line 207)

  /server/internal/executor/execute_test.go

  1. ⚠️ createMockClaude helper (line 16) - Should be in test helpers
  2. ❌ TestExecute (line 31) - Over-reliance on mocks
  3. ✅ TestBuildCommandArgs (line 166)
  4. ✅ TestParseClaudeOutput (line 251)
  5. ⚠️ TestExecuteWithCallback (line 337) - Mock doesn't test streaming
  6. ❌ TestExecuteConcurrentLimit (line 396) - Timing-dependent test
  7. ✅ TestExecuteProcessCleanup (line 450)

  /server/internal/executor/executor_test.go

  1. ✅ TestNewClaudeExecutor (line 15)
  2. ✅ TestProcessTracking (line 116)
  3. ✅ TestGetActiveProcessCount (line 181)
  4. ✅ TestIsProjectExecuting (line 198)
  5. ✅ TestCreateTimeoutContext (line 214)
  6. ✅ TestCleanupProcess (line 246)
  7. ✅ TestGetStats (line 278)
  8. ✅ TestShutdown (line 310)
  9. ⚠️ TestShutdownTimeout (line 345) - 10ms timeout too short
  10. ✅ TestConcurrentOperations (line 369)

  /server/internal/executor/kill_test.go

  1. ✅ TestKillExecution (line 15)
  2. ✅ TestKillExecutionGracefulTermination (line 64)
  3. ⚠️ TestKillProcess (line 109) - Platform-specific without Windows handling
  4. ✅ TestKillProcessNil (line 146)
  5. ✅ TestKillAll (line 155)
  6. ⚠️ TestKillAllWithFailures (line 190) - Not portable
  7. ✅ TestForceKillExecution (line 225)
  8. ✅ TestIsProcessAlive (line 273)
  9. ❌ TestKillTimeouts (line 321) - Shell-specific, not portable
  10. ✅ TestPlatformSpecificKill (line 359)

  /server/internal/logger/logger_test.go

  1. ✅ TestLogLevels (line 12)
  2. ✅ TestJSONFormat (line 66)
  3. ✅ TestTextFormat (line 98)
  4. ✅ TestContextFields (line 117)
  5. ✅ TestWithFields (line 152)
  6. ✅ TestWithError (line 186)
  7. ✅ TestLogRequest (line 216)
  8. ✅ TestNopLogger (line 248)
  9. ✅ TestSourceLocation (line 257)
  10. ✅ TestTimeFormat (line 287)

  /server/internal/models/project_test.go

  1. ✅ TestNewProject (line 9)
  2. ✅ TestProjectUpdateState (line 40)
  3. ✅ TestProjectSetError (line 58)
  4. ✅ TestProjectSubscribers (line 73)
  5. ✅ TestProjectMetadataConversion (line 104)
  6. ✅ TestProjectValidate (line 153)
  7. ⚠️ TestProjectConcurrency (line 191) - Race condition in test

  /server/internal/project/crud_test.go

  1. ✅ setupTestManager helper (line 12)
  2. ✅ TestCreateProject (line 36)
  3. ✅ TestDeleteProject (line 142)
  4. ✅ TestGetProjectByID (line 224)
  5. ✅ TestGetProject (line 289)
  6. ✅ TestGetProjectByPath (line 311)
  7. ⚠️ TestUpdateProject (line 359) - Missing persistence verification
  8. ✅ TestProjectNestingValidation (line 434)

  /server/internal/project/manager_test.go

  1. ✅ TestNewManager (line 13)
  2. ⚠️ TestManagerLoadProjects (line 80) - Hardcoded UUIDs
  3. ✅ TestGetAllProjects (line 138)
  4. ✅ TestGetExistingPaths (line 172)
  5. ✅ TestGenerateProjectID (line 208)
  6. ✅ TestManagerConcurrency (line 237)

  /server/internal/project/state_test.go

  1. ✅ TestUpdateProjectState (line 13)
  2. ✅ TestSetProjectError (line 60)
  3. ✅ TestUpdateProjectSession (line 86)
  4. ✅ TestClearProjectSession (line 109)
  5. ✅ TestProjectSubscribers (line 131)
  6. ✅ TestIsProjectExecuting (line 183)
  7. ✅ TestCanProjectExecute (line 213)
  8. ✅ TestTransitionProjectState (line 242)
  9. ✅ TestGetProjectStats (line 292)
  10. ✅ TestStateConcurrency (line 350)

  /server/internal/storage/integration_test.go

  1. ✅ TestStorageIntegration/FactoryIntegration (line 20)
  2. ✅ TestStorageIntegration/ProjectLifecycle (line 96)
  3. ⚠️ TestStorageIntegration/ConcurrentAccess (line 203) - No message order
  verification

  /server/internal/storage/message_log_test.go

  1. ✅ TestMessageLog/NewMessageLog (line 21)
  2. ✅ TestMessageLog/AppendAndGetMessagesSince (line 35)
  3. ✅ TestMessageLog/MessageOrdering (line 119)
  4. ✅ TestMessageLog/Stats (line 156)
  5. ⚠️ TestMessageLogRotation (line 193) - Incomplete implementation

  /server/internal/storage/project_persistence_test.go

  1. ✅ TestProjectPersistence/NewProjectPersistence (line 21)
  2. ✅ TestProjectPersistence/SaveAndLoadProject (line 36)
  3. ✅ TestProjectPersistence/MultipleProjects (line 78)
  4. ✅ TestProjectPersistence/DeleteProject (line 111)
  5. ✅ TestProjectPersistence/AtomicWrite (line 148)
  6. ⚠️ TestCorruptionRecovery/RecoverFromTempFile (line 232) - Hardcoded temp pattern

  /server/internal/validation/validation_test.go

  1. ✅ TestValidatorValidatePath (line 12)
  2. ✅ TestValidatorWithAllowedPaths (line 134)
  3. ✅ TestValidatorValidateProjectNesting (line 176)
  4. ✅ TestValidatorValidateMessageSize (line 240)
  5. ✅ TestValidatorValidateMessageBatch (line 286)
  6. ✅ TestValidatorValidateJSON (line 347)
  7. ✅ TestValidatorValidatePrompt (line 397)
  8. ✅ TestValidatorValidateClaudeOptions (line 461)
  9. ✅ TestValidatorSanitizePath (line 559)
  10. ✅ TestValidatorSanitizeString (line 604)
  11. ✅ TestValidatorValidateIDs (line 661)
  12. ✅ BenchmarkValidatePath (line 737)
  13. ✅ BenchmarkValidateProjectNesting (line 748)
  14. ✅ BenchmarkSanitizeString (line 761)
  15. ✅ BenchmarkValidateJSON (line 771)

  /server/internal/websocket/handlers/project_handlers_test.go

  1. ❌ MockProjectManager (line 19) - Excessive mocking
  2. ❌ MockBroadcaster (line 63) - Empty mock methods
  3. ❌ MockSession (line 76) - Mocks session instead of using test WebSocket
  4. ❌ TestHandleProjectCreate (line 95) - Tests mocks not behavior
  5. ❌ TestHandleProjectList (line 161) - Doesn't verify response format
  6. ❌ TestHandleProjectDelete - Tests mocks not behavior
  7. ❌ TestHandleProjectJoin - Tests mocks not behavior
  8. ❌ TestHandleProjectLeave - Tests mocks not behavior

  /server/internal/websocket/rate_limiter_test.go

  1. ✅ TestRateLimiter (line 8)
  2. ✅ TestRateLimiterMultipleKeys (line 35)
  3. ⚠️ TestRateLimiterCleanup (line 61) - Timing-dependent
  4. ✅ TestRateLimiterConcurrency (line 87)

  /server/internal/websocket/router_test.go

  1. ✅ TestMessageRouter (line 17)
  2. ✅ TestMessageRouterUnknownType (line 48)
  3. ✅ TestMessageRouterHandlerError (line 68)
  4. ✅ TestMessageDispatcher (line 93)
  5. ✅ TestRecoveryMiddleware (line 137)
  6. ✅ TestValidationMiddleware (line 166)
  7. ⚠️ TestSendHelpers (line 232) - Complex test server setup

  Summary Statistics:

  - ✅ GOOD: 94 tests (70%)
  - ⚠️ REFACTOR: 20 tests (15%)
  - ❌ REWRITE: 13 tests (10%)
  - ➕ MISSING: 8 test scenarios (5%)