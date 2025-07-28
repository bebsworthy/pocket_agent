//go:build linux || darwin
// +build linux darwin

package platform

import (
	"os"
	"os/exec"
	"os/signal"
	"runtime"
	"syscall"
)

// SetupProcessGroup sets up process group for proper signal handling
func SetupProcessGroup(cmd *exec.Cmd) error {
	if cmd.SysProcAttr == nil {
		cmd.SysProcAttr = &syscall.SysProcAttr{}
	}

	// Create new process group
	cmd.SysProcAttr.Setpgid = true

	// Pdeathsig is Linux-specific
	if runtime.GOOS == "linux" {
		// This is handled in the Linux-specific file
		SetupLinuxProcess(cmd)
	}

	return nil
}

// KillProcessGroup kills the entire process group
func KillProcessGroup(cmd *exec.Cmd) error {
	if cmd.Process == nil {
		return nil
	}

	// Kill the entire process group
	pgid, err := syscall.Getpgid(cmd.Process.Pid)
	if err == nil && pgid > 0 {
		// Negative PID kills the entire process group
		return syscall.Kill(-pgid, syscall.SIGTERM)
	}

	// Fallback to killing just the process
	return cmd.Process.Kill()
}

// SetupSignalHandling sets up Unix-specific signal handling
func SetupSignalHandling() chan os.Signal {
	sigChan := make(chan os.Signal, 1)

	// Standard signals
	signal.Notify(sigChan,
		os.Interrupt,
		syscall.SIGTERM,
		syscall.SIGQUIT,
	)

	// Unix-specific signals
	signal.Notify(sigChan,
		syscall.SIGHUP,  // Hangup - often used to reload config
		syscall.SIGUSR1, // User-defined signal 1
		syscall.SIGUSR2, // User-defined signal 2
	)

	return sigChan
}

// HandlePlatformSignal handles platform-specific signals
func HandlePlatformSignal(sig os.Signal) (reload bool, shutdown bool) {
	switch sig {
	case syscall.SIGHUP:
		// Traditional Unix signal for reload
		return true, false
	case syscall.SIGUSR1:
		// Could be used for custom actions like log rotation
		return false, false
	case syscall.SIGUSR2:
		// Could be used for custom actions like dumping metrics
		return false, false
	case os.Interrupt, syscall.SIGTERM, syscall.SIGQUIT:
		return false, true
	default:
		return false, false
	}
}

// SetResourceLimits sets Unix resource limits
func SetResourceLimits(maxOpenFiles uint64) error {
	var rLimit syscall.Rlimit

	// Get current limits
	err := syscall.Getrlimit(syscall.RLIMIT_NOFILE, &rLimit)
	if err != nil {
		return err
	}

	// Set new limit if needed
	if maxOpenFiles > 0 && maxOpenFiles != rLimit.Cur {
		rLimit.Cur = maxOpenFiles
		if maxOpenFiles > rLimit.Max {
			rLimit.Max = maxOpenFiles
		}
		return syscall.Setrlimit(syscall.RLIMIT_NOFILE, &rLimit)
	}

	return nil
}

// GetResourceLimits returns current resource limits
func GetResourceLimits() (ResourceLimits, error) {
	limits := ResourceLimits{}

	var rLimit syscall.Rlimit

	// File descriptors
	if err := syscall.Getrlimit(syscall.RLIMIT_NOFILE, &rLimit); err == nil {
		limits.MaxOpenFiles = rLimit.Cur
	}

	// Memory
	if err := syscall.Getrlimit(syscall.RLIMIT_AS, &rLimit); err == nil {
		limits.MaxMemory = rLimit.Cur
	}

	// CPU time
	if err := syscall.Getrlimit(syscall.RLIMIT_CPU, &rLimit); err == nil {
		limits.MaxCPUTime = rLimit.Cur
	}

	return limits, nil
}

// ResourceLimits contains Unix resource limit information
type ResourceLimits struct {
	MaxOpenFiles uint64
	MaxMemory    uint64
	MaxCPUTime   uint64
}

// CheckPermissions checks for required permissions
func CheckPermissions() []string {
	var issues []string

	// Check if we can execute processes
	if _, err := exec.LookPath("sh"); err != nil {
		issues = append(issues, "Cannot find shell for process execution")
	}

	// Check if we can create files in temp directory
	tmpFile := "/tmp/.pocket_agent_test"
	if f, err := os.Create(tmpFile); err != nil {
		issues = append(issues, "Cannot write to temp directory")
	} else {
		f.Close()
		os.Remove(tmpFile)
	}

	return issues
}

// IsRoot returns true if running as root
func IsRoot() bool {
	return os.Geteuid() == 0
}
