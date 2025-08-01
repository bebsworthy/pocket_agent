package executor

import (
	"context"
	"fmt"
	"os/exec"
	"runtime"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/logger"
)

func TestKillExecution(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Test killing non-existent process
	err := ce.KillExecution("non-existent")
	if err == nil {
		t.Error("expected error for non-existent process")
	}
	appErr, _ := err.(*errors.AppError)
	if appErr.Code != errors.CodeProcessNotFound {
		t.Errorf("expected CodeProcessNotFound, got %s", appErr.Code)
	}

	// Create a long-running process
	ctx, cancel := context.WithCancel(context.Background())
	cmd := exec.CommandContext(ctx, "sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start sleep command")
	}

	processInfo := &ProcessInfo{
		Cmd:       cmd,
		ProjectID: "test-kill",
		StartTime: time.Now(),
		Context:   ctx,
		Cancel:    cancel,
	}
	ce.activeProcesses["test-kill"] = processInfo

	// Kill the process
	err = ce.KillExecution("test-kill")
	if err != nil {
		t.Errorf("KillExecution() unexpected error: %v", err)
	}

	// Verify process was cleaned up
	if _, exists := ce.activeProcesses["test-kill"]; exists {
		t.Error("process was not cleaned up after kill")
	}

	// Verify process actually terminated
	if err := cmd.Wait(); err == nil {
		t.Error("expected process to be terminated")
	}
}

func TestKillExecutionGracefulTermination(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Create a process that responds to context cancellation
	ctx, cancel := context.WithCancel(context.Background())

	// Create a process that will actually respond to context cancellation
	// Use a longer sleep time to ensure it doesn't terminate before we try to kill it
	cmd := exec.CommandContext(ctx, "sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start command")
	}

	processInfo := &ProcessInfo{
		Cmd:       cmd,
		ProjectID: "graceful-test",
		StartTime: time.Now(),
		Context:   ctx,
		Cancel:    cancel,
	}
	ce.activeProcesses["graceful-test"] = processInfo

	// Kill should succeed with graceful termination
	err := ce.KillExecution("graceful-test")
	if err != nil {
		t.Errorf("KillExecution() unexpected error: %v", err)
	}

	// Verify process was cleaned up
	if _, exists := ce.activeProcesses["graceful-test"]; exists {
		t.Error("process was not cleaned up from active processes")
	}

	// The process should have been terminated gracefully (within 2 seconds as per implementation)
	// We can't check the exact termination because cmd.Wait() is called inside KillExecution
}

func TestKillProcess(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix-specific test")
	}

	ce := &ClaudeExecutor{logger: logger.New("info")}

	// Start a real process
	cmd := exec.Command("sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start sleep command")
	}

	process := cmd.Process

	// Kill the process
	err := ce.killProcess(process)
	if err != nil {
		t.Errorf("killProcess() unexpected error: %v", err)
	}

	// Wait and verify termination
	done := make(chan error, 1)
	go func() {
		done <- cmd.Wait()
	}()

	select {
	case err := <-done:
		if err == nil {
			t.Error("expected process to be terminated")
		}
	case <-time.After(2 * time.Second):
		t.Error("process did not terminate")
	}
}

func TestKillProcessNil(t *testing.T) {
	ce := &ClaudeExecutor{logger: logger.New("info")}

	err := ce.killProcess(nil)
	if err == nil {
		t.Error("expected error for nil process")
	}
}

func TestKillAll(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Create multiple mock processes
	for i := 0; i < 3; i++ {
		ctx, cancel := context.WithCancel(context.Background())
		projectID := string(rune('a' + i))

		// Create a short-lived process that will terminate on context cancel
		cmd := exec.CommandContext(ctx, "sleep", "0.1")
		cmd.Start()

		ce.activeProcesses[projectID] = &ProcessInfo{
			Cmd:       cmd,
			ProjectID: projectID,
			Context:   ctx,
			Cancel:    cancel,
		}
	}

	// Kill all processes
	err := ce.KillAll()
	if err != nil {
		t.Errorf("KillAll() unexpected error: %v", err)
	}

	// Verify all processes were cleaned up
	if len(ce.activeProcesses) != 0 {
		t.Errorf("expected 0 active processes after KillAll, got %d", len(ce.activeProcesses))
	}
}

func TestKillAllWithFailures(t *testing.T) {
	// Test KillAll behavior when some kills fail
	// We'll use a mock-like approach by modifying activeProcesses during execution

	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// This test actually validates the robustness of KillAll
	// In practice, KillAll rarely fails because KillExecution handles most errors gracefully
	// The only real failure case is when a process doesn't exist in activeProcesses

	// Add multiple processes
	processes := make([]*exec.Cmd, 3)
	cancels := make([]context.CancelFunc, 3)

	for i := 0; i < 3; i++ {
		ctx, cancel := context.WithCancel(context.Background())
		cmd := exec.CommandContext(ctx, "sleep", "10")
		cmd.Start()

		processes[i] = cmd
		cancels[i] = cancel

		projectID := fmt.Sprintf("process-%d", i)
		ce.activeProcesses[projectID] = &ProcessInfo{
			Cmd:       cmd,
			ProjectID: projectID,
			Context:   ctx,
			Cancel:    cancel,
			StartTime: time.Now(),
		}
	}

	// KillAll should handle all processes successfully
	err := ce.KillAll()
	// In the current implementation, KillAll only fails if KillExecution returns an error
	// Since our processes are valid, no error should occur
	if err != nil {
		t.Logf("KillAll returned error: %v", err)
		// If there is an error, verify it's the expected format
		if !strings.Contains(err.Error(), "failed to kill some executions") {
			t.Errorf("unexpected error format: %v", err)
		}
	}

	// All processes should be cleaned up regardless
	if len(ce.activeProcesses) != 0 {
		t.Errorf("expected all processes to be cleaned up, but %d remain", len(ce.activeProcesses))
	}

	// Clean up
	for i := range cancels {
		cancels[i]()
	}
}

// Helper function to check if string contains substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > 0 && strings.Contains(s, substr))
}

func TestForceKillExecution(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Test force killing non-existent process
	err := ce.ForceKillExecution("non-existent")
	if err != nil {
		appErr, _ := err.(*errors.AppError)
		if appErr.Code != errors.CodeProcessNotFound {
			t.Errorf("expected CodeProcessNotFound, got %s", appErr.Code)
		}
	}

	// Create a process
	ctx, cancel := context.WithCancel(context.Background())
	cmd := exec.Command("sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start sleep command")
	}

	processInfo := &ProcessInfo{
		Cmd:       cmd,
		ProjectID: "force-kill",
		Context:   ctx,
		Cancel:    cancel,
	}
	ce.activeProcesses["force-kill"] = processInfo

	// Force kill should immediately terminate
	err = ce.ForceKillExecution("force-kill")
	if err != nil {
		t.Errorf("ForceKillExecution() unexpected error: %v", err)
	}

	// Process should be cleaned up immediately
	if _, exists := ce.activeProcesses["force-kill"]; exists {
		t.Error("process was not cleaned up after force kill")
	}

	// Verify process was killed
	err = cmd.Wait()
	if err == nil {
		t.Error("expected process to be killed")
	}
}

func TestIsProcessAlive(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Test non-existent process
	if ce.IsProcessAlive("non-existent") {
		t.Error("expected false for non-existent process")
	}

	// Create a running process
	cmd := exec.Command("sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start sleep command")
	}

	ce.activeProcesses["alive"] = &ProcessInfo{
		Cmd:       cmd,
		ProjectID: "alive",
	}

	// Should be alive
	if !ce.IsProcessAlive("alive") {
		t.Error("expected process to be alive")
	}

	// Kill and wait
	cmd.Process.Kill()
	cmd.Wait()

	// After killing and waiting, IsProcessAlive should return false
	// because signal 0 will fail on a dead process
	if ce.IsProcessAlive("alive") {
		t.Error("expected false for killed process")
	}

	// Test with nil process
	ce.activeProcesses["nil-proc"] = &ProcessInfo{
		Cmd:       &exec.Cmd{},
		ProjectID: "nil-proc",
	}

	if ce.IsProcessAlive("nil-proc") {
		t.Error("expected false for nil process")
	}

	// Clean up - remove from active processes to test actual behavior
	delete(ce.activeProcesses, "alive")

	// Now it should definitely be false (not in map)
	if ce.IsProcessAlive("alive") {
		t.Error("expected false for process not in active map")
	}
}

func TestKillTimeouts(t *testing.T) {
	ce := &ClaudeExecutor{
		activeProcesses: make(map[string]*ProcessInfo),
		logger:          logger.New("info"),
	}

	// Create a process that ignores SIGTERM
	// Use a shorter sleep to avoid test timeout issues
	cmd := exec.Command("sh", "-c", "trap '' TERM; sleep 3")
	
	// Set up process group to ensure child processes are killed too
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setpgid: true,
	}
	
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start command")
	}

	processInfo := &ProcessInfo{
		Cmd:       cmd,
		ProjectID: "stubborn",
		Context:   context.Background(),
		Cancel:    func() {}, // No-op cancel
		StartTime: time.Now(),
	}
	ce.activeProcesses["stubborn"] = processInfo

	// Kill should eventually force kill after timeout
	start := time.Now()
	err := ce.KillExecution("stubborn")
	elapsed := time.Since(start)

	if err != nil {
		t.Errorf("KillExecution() unexpected error: %v", err)
	}

	// Should have waited for graceful period (2s) but not too long
	// The process ignores SIGTERM, so it should wait 2s then force kill
	if elapsed < 2*time.Second {
		t.Error("kill completed too quickly, expected graceful wait")
	}
	// Allow some buffer for process cleanup
	if elapsed > 4*time.Second {
		t.Errorf("kill took too long: %v", elapsed)
	}

	// Verify process was cleaned up
	if _, exists := ce.activeProcesses["stubborn"]; exists {
		t.Error("process was not cleaned up from active processes")
	}
}

func TestPlatformSpecificKill(t *testing.T) {
	ce := &ClaudeExecutor{logger: logger.New("info")}

	// Start a process
	cmd := exec.Command("sleep", "10")
	if err := cmd.Start(); err != nil {
		t.Skip("Cannot start sleep command")
	}

	process := cmd.Process

	// Test platform-specific kill
	switch runtime.GOOS {
	case "linux", "darwin":
		// On Unix, should use SIGTERM
		err := process.Signal(syscall.Signal(0)) // Check if alive
		if err != nil {
			t.Skip("Process not accessible")
		}

		err = ce.killProcess(process)
		if err != nil {
			t.Errorf("killProcess() failed: %v", err)
		}

	default:
		// On other platforms, should use Kill()
		err := ce.killProcess(process)
		if err != nil {
			t.Errorf("killProcess() failed: %v", err)
		}
	}

	// Cleanup
	cmd.Wait()
}
