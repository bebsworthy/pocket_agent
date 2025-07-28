// +build darwin

package platform

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
)

// CheckMacOSPermissions checks for macOS-specific permissions
func CheckMacOSPermissions() []string {
	var issues []string
	
	// Check for Terminal/iTerm permissions
	if !hasTerminalAccess() {
		issues = append(issues, "Terminal access may be restricted - check System Preferences > Security & Privacy > Privacy > Developer Tools")
	}
	
	// Check for Full Disk Access if needed
	if needsFullDiskAccess() && !hasFullDiskAccess() {
		issues = append(issues, "Full Disk Access may be needed for some operations - check System Preferences > Security & Privacy > Privacy > Full Disk Access")
	}
	
	// Check for code signing issues
	if err := checkCodeSigning(); err != nil {
		issues = append(issues, fmt.Sprintf("Code signing issue: %v", err))
	}
	
	return issues
}

// hasTerminalAccess checks if the app has terminal access permissions
func hasTerminalAccess() bool {
	// Try to execute a simple command
	cmd := exec.Command("echo", "test")
	err := cmd.Run()
	return err == nil
}

// needsFullDiskAccess checks if the app needs full disk access
func needsFullDiskAccess() bool {
	// Check if we're trying to access protected directories
	home := os.Getenv("HOME")
	if home == "" {
		return false
	}
	
	// These directories typically require full disk access
	protectedDirs := []string{
		home + "/Desktop",
		home + "/Documents",
		home + "/Downloads",
	}
	
	for _, dir := range protectedDirs {
		if _, err := os.Stat(dir); err == nil {
			// Try to list the directory
			if _, err := os.ReadDir(dir); err != nil {
				return true
			}
		}
	}
	
	return false
}

// hasFullDiskAccess attempts to determine if we have full disk access
func hasFullDiskAccess() bool {
	// This is a heuristic - try to access a protected location
	home := os.Getenv("HOME")
	if home == "" {
		return true // Assume we have access if we can't determine
	}
	
	testFile := home + "/Library/Safari/.pocket_agent_test"
	if f, err := os.Create(testFile); err == nil {
		f.Close()
		os.Remove(testFile)
		return true
	}
	
	return false
}

// checkCodeSigning verifies code signing status
func checkCodeSigning() error {
	// Check if the binary is code signed
	executable, err := os.Executable()
	if err != nil {
		return err
	}
	
	cmd := exec.Command("codesign", "-v", executable)
	output, err := cmd.CombinedOutput()
	if err != nil {
		// Check if it's just not signed (which is OK for development)
		if strings.Contains(string(output), "not signed") {
			return nil
		}
		return fmt.Errorf("codesign verification failed: %s", output)
	}
	
	return nil
}

// SetupMacOSProcess configures macOS-specific process attributes
func SetupMacOSProcess(cmd *exec.Cmd) {
	if cmd.Env == nil {
		cmd.Env = os.Environ()
	}
	
	// Ensure PATH includes common locations
	pathSet := false
	for i, env := range cmd.Env {
		if strings.HasPrefix(env, "PATH=") {
			// Add common macOS paths if not present
			path := strings.TrimPrefix(env, "PATH=")
			if !strings.Contains(path, "/usr/local/bin") {
				path = "/usr/local/bin:" + path
			}
			if !strings.Contains(path, "/opt/homebrew/bin") {
				path = "/opt/homebrew/bin:" + path
			}
			cmd.Env[i] = "PATH=" + path
			pathSet = true
			break
		}
	}
	
	if !pathSet {
		cmd.Env = append(cmd.Env, "PATH=/opt/homebrew/bin:/usr/local/bin:"+os.Getenv("PATH"))
	}
}

// GetSystemInfo returns macOS-specific system information
func GetSystemInfo() map[string]string {
	info := make(map[string]string)
	
	// Get macOS version
	if output, err := exec.Command("sw_vers", "-productVersion").Output(); err == nil {
		info["macos_version"] = strings.TrimSpace(string(output))
	}
	
	// Get hardware info
	if output, err := exec.Command("sysctl", "-n", "hw.model").Output(); err == nil {
		info["hardware_model"] = strings.TrimSpace(string(output))
	}
	
	// Check if running on Apple Silicon
	if output, err := exec.Command("sysctl", "-n", "hw.optional.arm64").Output(); err == nil {
		if strings.TrimSpace(string(output)) == "1" {
			info["architecture"] = "arm64 (Apple Silicon)"
		} else {
			info["architecture"] = "x86_64 (Intel)"
		}
	}
	
	return info
}