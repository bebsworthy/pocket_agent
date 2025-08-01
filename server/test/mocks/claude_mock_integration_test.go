package mocks

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestClaudeMockIntegration(t *testing.T) {
	// Test that the claude-mock binary wrapper works correctly
	t.Run("Basic Wrapper Test", func(t *testing.T) {
		// Create a simple mock
		tempDir := t.TempDir()
		scriptPath := filepath.Join(tempDir, "claude-mock-wrapper")
		
		config := ClaudeMockConfig{
			Scenario: ScenarioSuccess,
			Delay:    0,
		}
		
		if err := CreateMockClaudeScript(scriptPath, config); err != nil {
			t.Fatalf("Failed to create mock script: %v", err)
		}
		
		// Run the wrapper
		cmd := exec.Command(scriptPath)
		cmd.Stdin = strings.NewReader("Hello, Claude!")
		
		output, err := cmd.CombinedOutput()
		if err != nil {
			t.Fatalf("Failed to run mock: %v\nOutput: %s", err, output)
		}
		
		// Check output contains expected content
		outputStr := string(output)
		if !strings.Contains(outputStr, "test-session-123") || !strings.Contains(outputStr, "successfully processed") {
			t.Errorf("Unexpected output: %s", outputStr)
		}
	})
	
	t.Run("Error Scenario Test", func(t *testing.T) {
		tempDir := t.TempDir()
		scriptPath := filepath.Join(tempDir, "claude-mock-wrapper")
		
		config := ClaudeMockConfig{
			Scenario: ScenarioError,
			Delay:    0,
		}
		
		if err := CreateMockClaudeScript(scriptPath, config); err != nil {
			t.Fatalf("Failed to create mock script: %v", err)
		}
		
		cmd := exec.Command(scriptPath)
		cmd.Stdin = strings.NewReader("Hello, Claude!")
		
		output, err := cmd.CombinedOutput()
		// For error scenario, the command itself should succeed but output an error message
		if err == nil {
			// Check if output contains error
			outputStr := string(output)
			if !strings.Contains(outputStr, "error") {
				t.Errorf("Expected error in output, got: %s", outputStr)
			}
		}
	})
	
	t.Run("Timeout Test", func(t *testing.T) {
		tempDir := t.TempDir()
		scriptPath := filepath.Join(tempDir, "claude-mock-wrapper")
		
		config := ClaudeMockConfig{
			Scenario: ScenarioTimeout,
			Delay:    0, // The timeout scenario has its own delays
		}
		
		if err := CreateMockClaudeScript(scriptPath, config); err != nil {
			t.Fatalf("Failed to create mock script: %v", err)
		}
		
		cmd := exec.Command(scriptPath)
		cmd.Stdin = strings.NewReader("Hello, Claude!")
		
		// Set a timeout for the command
		done := make(chan error)
		go func() {
			_, err := cmd.CombinedOutput()
			done <- err
		}()
		
		select {
		case err := <-done:
			// Should complete even with delay
			if err != nil {
				t.Logf("Command completed with error (expected): %v", err)
			}
		case <-time.After(15 * time.Second):
			cmd.Process.Kill()
			t.Error("Command timed out")
		}
	})
	
	t.Run("Environment Variable Test", func(t *testing.T) {
		tempDir := t.TempDir()
		scriptPath := filepath.Join(tempDir, "claude-mock-wrapper")
		
		config := ClaudeMockConfig{
			Scenario: ScenarioSuccess,
			Delay:    100 * time.Millisecond,
		}
		
		if err := CreateMockClaudeScript(scriptPath, config); err != nil {
			t.Fatalf("Failed to create mock script: %v", err)
		}
		
		// Read the script to verify environment variables
		content, err := os.ReadFile(scriptPath)
		if err != nil {
			t.Fatalf("Failed to read script: %v", err)
		}
		
		scriptContent := string(content)
		if !strings.Contains(scriptContent, "CLAUDE_MOCK_LOG_FILE=") {
			t.Error("Script missing CLAUDE_MOCK_LOG_FILE export")
		}
		if !strings.Contains(scriptContent, "CLAUDE_MOCK_DELAY_MS=") {
			t.Error("Script missing CLAUDE_MOCK_DELAY_MS export")
		}
		if !strings.Contains(scriptContent, `exec "`) {
			t.Error("Script missing exec command")
		}
	})
}