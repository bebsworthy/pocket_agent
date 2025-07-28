//go:build linux
// +build linux

package platform

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"
)

// CheckPlatformSpecificPermissions checks platform-specific permissions
func CheckPlatformSpecificPermissions() []string {
	return CheckLinuxPermissions()
}

// CheckLinuxPermissions checks for Linux-specific permissions
func CheckLinuxPermissions() []string {
	var issues []string

	// Check if running in container
	if isContainer() {
		issues = append(issues, "Running in container - some features may be limited")
	}

	// Check SELinux status
	if selinuxEnabled() {
		issues = append(issues, "SELinux is enabled - may need additional permissions")
	}

	// Check AppArmor status
	if apparmorEnabled() {
		issues = append(issues, "AppArmor is enabled - may need additional permissions")
	}

	// Check cgroup limits
	if limits := checkCgroupLimits(); len(limits) > 0 {
		issues = append(issues, limits...)
	}

	return issues
}

// isContainer attempts to detect if running in a container
func isContainer() bool {
	// Check for .dockerenv file
	if _, err := os.Stat("/.dockerenv"); err == nil {
		return true
	}

	// Check cgroup for docker/kubernetes
	if data, err := os.ReadFile("/proc/self/cgroup"); err == nil {
		content := string(data)
		if strings.Contains(content, "docker") ||
			strings.Contains(content, "kubepods") ||
			strings.Contains(content, "containerd") {
			return true
		}
	}

	return false
}

// selinuxEnabled checks if SELinux is enabled
func selinuxEnabled() bool {
	if data, err := os.ReadFile("/sys/fs/selinux/enforce"); err == nil {
		return strings.TrimSpace(string(data)) == "1"
	}
	return false
}

// apparmorEnabled checks if AppArmor is enabled
func apparmorEnabled() bool {
	if _, err := os.Stat("/sys/kernel/security/apparmor"); err == nil {
		return true
	}
	return false
}

// checkCgroupLimits checks for cgroup resource limits
func checkCgroupLimits() []string {
	var limits []string

	// Check memory limits
	if limit, exists := getCgroupMemoryLimit(); exists && limit > 0 {
		limitMB := limit / (1024 * 1024)
		if limitMB < 512 {
			limits = append(limits, fmt.Sprintf("Memory limited to %d MB by cgroup", limitMB))
		}
	}

	// Check CPU limits
	if quota, period := getCgroupCPULimit(); quota > 0 && period > 0 {
		cpus := float64(quota) / float64(period)
		if cpus < 1.0 {
			limits = append(limits, fmt.Sprintf("CPU limited to %.2f cores by cgroup", cpus))
		}
	}

	return limits
}

// getCgroupMemoryLimit returns the cgroup memory limit
func getCgroupMemoryLimit() (int64, bool) {
	// Try cgroup v2 first
	if data, err := os.ReadFile("/sys/fs/cgroup/memory.max"); err == nil {
		var limit int64
		if _, err := fmt.Sscanf(string(data), "%d", &limit); err == nil {
			return limit, true
		}
	}

	// Try cgroup v1
	if data, err := os.ReadFile("/sys/fs/cgroup/memory/memory.limit_in_bytes"); err == nil {
		var limit int64
		if _, err := fmt.Sscanf(string(data), "%d", &limit); err == nil {
			return limit, true
		}
	}

	return 0, false
}

// getCgroupCPULimit returns the cgroup CPU quota and period
func getCgroupCPULimit() (quota, period int64) {
	// Try cgroup v2 first
	if data, err := os.ReadFile("/sys/fs/cgroup/cpu.max"); err == nil {
		fmt.Sscanf(string(data), "%d %d", &quota, &period)
		if quota > 0 && period > 0 {
			return quota, period
		}
	}

	// Try cgroup v1
	if quotaData, err := os.ReadFile("/sys/fs/cgroup/cpu/cpu.cfs_quota_us"); err == nil {
		fmt.Sscanf(string(quotaData), "%d", &quota)
	}
	if periodData, err := os.ReadFile("/sys/fs/cgroup/cpu/cpu.cfs_period_us"); err == nil {
		fmt.Sscanf(string(periodData), "%d", &period)
	}

	return quota, period
}

// SetupLinuxProcess configures Linux-specific process attributes
func SetupLinuxProcess(cmd *exec.Cmd) {
	if cmd.SysProcAttr == nil {
		cmd.SysProcAttr = &syscall.SysProcAttr{}
	}

	// Set process priority
	cmd.SysProcAttr.Credential = &syscall.Credential{
		Uid: uint32(os.Getuid()),
		Gid: uint32(os.Getgid()),
	}

	// Ensure child processes are killed when parent dies
	cmd.SysProcAttr.Pdeathsig = syscall.SIGKILL
}

// GetSystemInfo returns Linux-specific system information
func GetSystemInfo() map[string]string {
	info := make(map[string]string)

	// Get distribution info
	if data, err := os.ReadFile("/etc/os-release"); err == nil {
		scanner := bufio.NewScanner(strings.NewReader(string(data)))
		for scanner.Scan() {
			line := scanner.Text()
			if strings.HasPrefix(line, "NAME=") {
				info["distribution"] = strings.Trim(strings.TrimPrefix(line, "NAME="), "\"")
			} else if strings.HasPrefix(line, "VERSION=") {
				info["version"] = strings.Trim(strings.TrimPrefix(line, "VERSION="), "\"")
			}
		}
	}

	// Get kernel version
	if output, err := exec.Command("uname", "-r").Output(); err == nil {
		info["kernel"] = strings.TrimSpace(string(output))
	}

	// Check if systemd is available
	if _, err := exec.LookPath("systemctl"); err == nil {
		info["init_system"] = "systemd"
	} else {
		info["init_system"] = "other"
	}

	// Container detection
	if isContainer() {
		info["container"] = "true"

		// Try to detect container type
		if _, err := os.Stat("/.dockerenv"); err == nil {
			info["container_type"] = "docker"
		} else if data, err := os.ReadFile("/proc/self/cgroup"); err == nil {
			if strings.Contains(string(data), "kubepods") {
				info["container_type"] = "kubernetes"
			}
		}
	} else {
		info["container"] = "false"
	}

	return info
}

// GetProcessNamespaces returns the process namespace information
func GetProcessNamespaces(pid int) (map[string]string, error) {
	namespaces := make(map[string]string)
	nsTypes := []string{"ipc", "mnt", "net", "pid", "user", "uts"}

	for _, nsType := range nsTypes {
		path := fmt.Sprintf("/proc/%d/ns/%s", pid, nsType)
		if target, err := os.Readlink(path); err == nil {
			namespaces[nsType] = target
		}
	}

	return namespaces, nil
}
