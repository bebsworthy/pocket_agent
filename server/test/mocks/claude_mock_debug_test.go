package mocks

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestClaudeMockDebug(t *testing.T) {
	// Test that the mock script is created correctly
	tempDir := t.TempDir()
	scriptPath := filepath.Join(tempDir, "claude")
	
	config := ClaudeMockConfig{
		Scenario: ScenarioSuccess,
		Delay:    0,
	}
	
	t.Logf("Creating mock script at: %s", scriptPath)
	
	if err := CreateMockClaudeScript(scriptPath, config); err != nil {
		t.Fatalf("Failed to create mock script: %v", err)
	}
	
	// Check if file exists
	info, err := os.Stat(scriptPath)
	if err != nil {
		t.Fatalf("Script does not exist: %v", err)
	}
	
	t.Logf("Script created successfully, size: %d, mode: %v", info.Size(), info.Mode())
	
	// Read the script content
	content, err := os.ReadFile(scriptPath)
	if err != nil {
		t.Fatalf("Failed to read script: %v", err)
	}
	
	t.Logf("Script content:\n%s", string(content))
	
	// Try to execute it
	cmd := exec.Command(scriptPath, "--help")
	output, err := cmd.CombinedOutput()
	t.Logf("Execution output: %s", string(output))
	if err != nil {
		t.Logf("Execution error: %v", err)
	}
	
	// Also test with the ClaudeMockExecutable helper
	t.Run("With ClaudeMockExecutable", func(t *testing.T) {
		mock := NewClaudeMockExecutable(t)
		defer mock.Cleanup()
		
		t.Logf("Mock path: %s", mock.Path())
		t.Logf("Mock tempDir: %s", mock.tempDir)
		
		if err := mock.Create(); err != nil {
			t.Fatalf("Failed to create mock: %v", err)
		}
		
		// Check if file exists
		info, err := os.Stat(mock.Path())
		if err != nil {
			t.Fatalf("Mock script does not exist: %v", err)
		}
		
		t.Logf("Mock script created successfully, size: %d", info.Size())
	})
}