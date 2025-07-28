package handlers

import (
	"context"
	"encoding/json"
	"os/exec"
	"runtime"
	"time"

	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/models"
	"github.com/boyd/pocket_agent/server/internal/websocket"
	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/mem"
)

// HealthHandlers provides health check functionality
// Requirements: Monitoring, Operations
type HealthHandlers struct {
	log        *logger.Logger
	claudePath string
	dataDir    string
}

// NewHealthHandlers creates new health handlers
func NewHealthHandlers(claudePath, dataDir string, log *logger.Logger) *HealthHandlers {
	if claudePath == "" {
		claudePath = "claude"
	}

	return &HealthHandlers{
		log:        log,
		claudePath: claudePath,
		dataDir:    dataDir,
	}
}

// HandleHealthCheck handles WebSocket-based health check requests
func (h *HealthHandlers) HandleHealthCheck(ctx context.Context, session *models.Session, data json.RawMessage) error {
	h.log.Debug("Processing health check request", "session_id", session.ID)

	// Collect health status
	health := h.collectHealthStatus()

	// Send health status
	return websocket.SendSuccess(session, models.MessageTypeHealthCheck, health)
}

// collectHealthStatus gathers system health information
func (h *HealthHandlers) collectHealthStatus() map[string]interface{} {
	health := map[string]interface{}{
		"timestamp": time.Now().Format(time.RFC3339),
		"status":    "healthy",
		"checks":    make(map[string]interface{}),
	}

	checks := health["checks"].(map[string]interface{})
	overallHealthy := true

	// System resource checks
	cpuCheck := h.checkCPU()
	checks["cpu"] = cpuCheck
	if cpuCheck["status"] != "healthy" {
		overallHealthy = false
	}

	memCheck := h.checkMemory()
	checks["memory"] = memCheck
	if memCheck["status"] != "healthy" {
		overallHealthy = false
	}

	diskCheck := h.checkDisk()
	checks["disk"] = diskCheck
	if diskCheck["status"] != "healthy" {
		overallHealthy = false
	}

	// Claude CLI availability check
	claudeCheck := h.checkClaudeCLI()
	checks["claude_cli"] = claudeCheck
	if claudeCheck["status"] != "healthy" {
		overallHealthy = false
	}

	// Update overall status
	if !overallHealthy {
		health["status"] = "unhealthy"
	}

	return health
}

// checkCPU checks CPU usage
func (h *HealthHandlers) checkCPU() map[string]interface{} {
	check := map[string]interface{}{
		"status": "healthy",
	}

	// Get CPU usage percentage
	cpuPercent, err := cpu.Percent(1*time.Second, false)
	if err != nil {
		h.log.Error("Failed to get CPU usage", "error", err)
		check["status"] = "error"
		check["error"] = err.Error()
		return check
	}

	if len(cpuPercent) > 0 {
		usage := cpuPercent[0]
		check["usage_percent"] = usage
		check["cores"] = runtime.NumCPU()

		// Warn if CPU usage is high
		if usage > 90 {
			check["status"] = "warning"
			check["message"] = "High CPU usage"
		}
	}

	return check
}

// checkMemory checks memory usage
func (h *HealthHandlers) checkMemory() map[string]interface{} {
	check := map[string]interface{}{
		"status": "healthy",
	}

	// Get memory stats
	vmStat, err := mem.VirtualMemory()
	if err != nil {
		h.log.Error("Failed to get memory stats", "error", err)
		check["status"] = "error"
		check["error"] = err.Error()
		return check
	}

	check["total_mb"] = vmStat.Total / 1024 / 1024
	check["available_mb"] = vmStat.Available / 1024 / 1024
	check["used_percent"] = vmStat.UsedPercent

	// Warn if memory usage is high
	if vmStat.UsedPercent > 90 {
		check["status"] = "warning"
		check["message"] = "High memory usage"
	} else if vmStat.UsedPercent > 95 {
		check["status"] = "critical"
		check["message"] = "Critical memory usage"
	}

	return check
}

// checkDisk checks disk usage for data directory
func (h *HealthHandlers) checkDisk() map[string]interface{} {
	check := map[string]interface{}{
		"status": "healthy",
		"path":   h.dataDir,
	}

	// Get disk usage stats
	usage, err := disk.Usage(h.dataDir)
	if err != nil {
		h.log.Error("Failed to get disk stats", "error", err)
		check["status"] = "error"
		check["error"] = err.Error()
		return check
	}

	check["total_gb"] = usage.Total / 1024 / 1024 / 1024
	check["free_gb"] = usage.Free / 1024 / 1024 / 1024
	check["used_percent"] = usage.UsedPercent

	// Warn if disk space is low
	if usage.UsedPercent > 90 {
		check["status"] = "warning"
		check["message"] = "Low disk space"
	} else if usage.UsedPercent > 95 {
		check["status"] = "critical"
		check["message"] = "Critical disk space"
	}

	// Also warn if absolute free space is very low
	if usage.Free < 1024*1024*1024 { // Less than 1GB free
		check["status"] = "critical"
		check["message"] = "Less than 1GB free disk space"
	}

	return check
}

// checkClaudeCLI checks if Claude CLI is available
func (h *HealthHandlers) checkClaudeCLI() map[string]interface{} {
	check := map[string]interface{}{
		"status": "healthy",
		"path":   h.claudePath,
	}

	// Check if Claude CLI exists
	path, err := exec.LookPath(h.claudePath)
	if err != nil {
		check["status"] = "critical"
		check["error"] = "Claude CLI not found"
		check["message"] = "Claude CLI must be installed and in PATH"
		return check
	}

	check["resolved_path"] = path

	// Try to get version
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	cmd := exec.CommandContext(ctx, h.claudePath, "--version")
	output, err := cmd.CombinedOutput()
	if err != nil {
		// Some CLIs return non-zero for version, so just log
		h.log.Debug("Claude version check returned error", "error", err)
	}

	if len(output) > 0 {
		check["version"] = string(output)
	}

	return check
}

// GetHealthSummary returns a simple health summary for HTTP endpoints
func (h *HealthHandlers) GetHealthSummary() map[string]interface{} {
	health := h.collectHealthStatus()

	// Simplify for HTTP response
	return map[string]interface{}{
		"status":    health["status"],
		"timestamp": health["timestamp"],
		"checks": map[string]string{
			"cpu":        health["checks"].(map[string]interface{})["cpu"].(map[string]interface{})["status"].(string),
			"memory":     health["checks"].(map[string]interface{})["memory"].(map[string]interface{})["status"].(string),
			"disk":       health["checks"].(map[string]interface{})["disk"].(map[string]interface{})["status"].(string),
			"claude_cli": health["checks"].(map[string]interface{})["claude_cli"].(map[string]interface{})["status"].(string),
		},
	}
}

// RegisterHandlers registers health check handlers
func (h *HealthHandlers) RegisterHandlers(router *websocket.MessageRouter) {
	router.Register(models.MessageTypeHealthCheck, h.HandleHealthCheck)
}
