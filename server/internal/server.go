package internal

import (
	"context"
	"fmt"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/boyd/pocket_agent/server/internal/config"
	"github.com/boyd/pocket_agent/server/internal/errors"
	"github.com/boyd/pocket_agent/server/internal/executor"
	"github.com/boyd/pocket_agent/server/internal/logger"
	"github.com/boyd/pocket_agent/server/internal/metrics"
	"github.com/boyd/pocket_agent/server/internal/platform"
	"github.com/boyd/pocket_agent/server/internal/project"
	"github.com/boyd/pocket_agent/server/internal/validation"
	"github.com/boyd/pocket_agent/server/internal/websocket"
	"github.com/boyd/pocket_agent/server/internal/websocket/handlers"
)

// Server represents the main application server that integrates all components
type Server struct {
	// Core components
	config         *config.Config
	logger         *logger.Logger
	wsServer       *websocket.Server
	projectManager *project.Manager
	executor       *executor.ClaudeExecutor
	validator      *validation.Validator

	// Resource management
	maxConnections int32
	maxProjects    int32
	activeConns    int32
	memoryLimit    uint64 // in bytes
	goroutineLimit int

	// Metrics
	startTime        time.Time
	metricsCollector *metrics.Collector

	// Lifecycle
	ctx            context.Context
	cancel         context.CancelFunc
	wg             sync.WaitGroup
	shutdownOnce   sync.Once
	resourceTicker *time.Ticker
}

// ServerConfig contains configuration for the Server
type ServerConfig struct {
	Config                *config.Config
	MaxConnections        int
	MaxProjects           int
	MemoryLimitMB         int
	GoroutineLimit        int
	ResourceCheckInterval time.Duration
}

// DefaultServerConfig returns default server configuration
func DefaultServerConfig() ServerConfig {
	return ServerConfig{
		MaxConnections:        100,  // Requirement 10.2
		MaxProjects:           100,  // Requirement 10.2
		MemoryLimitMB:         2048, // 2GB default
		GoroutineLimit:        1000,
		ResourceCheckInterval: 30 * time.Second,
	}
}

// NewServer creates and initializes a new Server with all components wired together
func NewServer(cfg ServerConfig) (*Server, error) {
	if cfg.Config == nil {
		return nil, fmt.Errorf("config cannot be nil")
	}

	// Create logger
	log := logger.New(cfg.Config.LogLevel)

	// Create validator
	validator := validation.NewValidator()

	// Create project manager
	projectCfg := project.Config{
		DataDir:     cfg.Config.DataDir,
		MaxProjects: cfg.MaxProjects,
		Validator:   validator,
	}
	projectManager, err := project.NewManager(projectCfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create project manager: %w", err)
	}

	// Create Claude executor
	executorCfg := executor.Config{
		ClaudePath:              cfg.Config.Execution.ClaudeBinaryPath,
		DefaultTimeout:          cfg.Config.Execution.CommandTimeout,
		MaxConcurrentExecutions: 10,
	}
	claudeExecutor, err := executor.NewClaudeExecutor(executorCfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create Claude executor: %w", err)
	}

	// Create context for server lifecycle
	ctx, cancel := context.WithCancel(context.Background())

	// Create server instance
	s := &Server{
		config:           cfg.Config,
		logger:           log,
		projectManager:   projectManager,
		executor:         claudeExecutor,
		validator:        validator,
		maxConnections:   int32(cfg.MaxConnections),
		maxProjects:      int32(cfg.MaxProjects),
		memoryLimit:      uint64(cfg.MemoryLimitMB) * 1024 * 1024,
		goroutineLimit:   cfg.GoroutineLimit,
		startTime:        time.Now(),
		ctx:              ctx,
		cancel:           cancel,
		resourceTicker:   time.NewTicker(cfg.ResourceCheckInterval),
		metricsCollector: metrics.NewCollector(),
	}

	// Create WebSocket configuration
	wsConfig := websocket.Config{
		Port:                cfg.Config.Port,
		TLSCert:             cfg.Config.TLSCertFile,
		TLSKey:              cfg.Config.TLSKeyFile,
		MaxConnections:      cfg.MaxConnections,
		MaxConnectionsPerIP: 10,
		ConnectionTimeout:   5 * time.Minute,
		PingInterval:        30 * time.Second,
		PongTimeout:         60 * time.Second,
		AllowedOrigins:      []string{"*"}, // TODO: Configure from config
		RateLimitPerIP:      60,
		MaxMessageSize:      1024 * 1024, // 1MB
		BufferSize:          1024,
	}

	// Create handlers with all dependencies
	handlerCfg := handlers.Config{
		ProjectManager:  projectManager,
		Executor:        claudeExecutor,
		Logger:          log,
		BroadcastConfig: handlers.BroadcasterConfig{},
		ClaudePath:      cfg.Config.Execution.ClaudeBinaryPath,
		DataDir:         cfg.Config.DataDir,
	}
	handler := handlers.NewHandlers(handlerCfg, s)

	// Create WebSocket server
	s.wsServer = websocket.NewServer(wsConfig, handler, log)

	// Wire up metrics collection from WebSocket server
	go s.collectWebSocketMetrics()

	return s, nil
}

// Start starts the server and all its components
func (s *Server) Start() error {
	// Platform-specific checks
	s.performPlatformChecks()

	// Set resource limits
	if err := platform.SetResourceLimits(10000); err != nil {
		s.logger.Warn("Failed to set resource limits", "error", err)
	}

	s.logger.Info("Starting Pocket Agent Server",
		"version", "1.0.0",
		"platform", runtime.GOOS,
		"max_connections", s.maxConnections,
		"max_projects", s.maxProjects,
		"memory_limit_mb", s.memoryLimit/1024/1024,
		"goroutine_limit", s.goroutineLimit,
	)

	// Start resource monitoring
	s.wg.Add(1)
	go s.monitorResources()

	// Start metrics collection
	s.wg.Add(1)
	go s.collectMetrics()

	// Start WebSocket server
	errChan := make(chan error, 1)
	go func() {
		if err := s.wsServer.Start(); err != nil {
			errChan <- err
		}
	}()

	// Setup platform-specific signal handling
	sigChan := platform.SetupSignalHandling()

	for {
		select {
		case err := <-errChan:
			return fmt.Errorf("server startup failed: %w", err)
		case sig := <-sigChan:
			reload, shutdown := platform.HandlePlatformSignal(sig)
			if reload {
				s.logger.Info("Received reload signal", "signal", sig)
				s.reloadConfiguration()
			} else if shutdown {
				s.logger.Info("Received shutdown signal", "signal", sig)
				return s.Shutdown()
			} else {
				s.logger.Debug("Received signal", "signal", sig)
			}
		case <-s.ctx.Done():
			return s.Shutdown()
		}
	}
}

// Shutdown performs graceful shutdown of the server
func (s *Server) Shutdown() error {
	var shutdownErr error

	s.shutdownOnce.Do(func() {
		s.logger.Info("Starting graceful shutdown")

		// Stop accepting new connections
		s.cancel()

		// Stop resource ticker
		s.resourceTicker.Stop()

		// Create shutdown context with timeout
		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer shutdownCancel()

		// Shutdown WebSocket server
		if err := s.wsServer.Stop(30 * time.Second); err != nil {
			s.logger.Error("Failed to stop WebSocket server", "error", err)
			shutdownErr = err
		}

		// Shutdown executor
		if err := s.executor.Shutdown(shutdownCtx); err != nil {
			s.logger.Error("Failed to shutdown executor", "error", err)
			if shutdownErr == nil {
				shutdownErr = err
			}
		}

		// Save all project metadata
		projects := s.projectManager.GetAllProjects()
		for _, proj := range projects {
			// UpdateProject saves the project metadata to disk
			if err := s.projectManager.UpdateProject(proj); err != nil {
				s.logger.Error("Failed to save project", "project_id", proj.ID, "error", err)
			}
		}

		// Wait for all goroutines
		done := make(chan struct{})
		go func() {
			s.wg.Wait()
			close(done)
		}()

		select {
		case <-done:
			s.logger.Info("All goroutines stopped")
		case <-shutdownCtx.Done():
			s.logger.Error("Shutdown timeout exceeded")
			if shutdownErr == nil {
				shutdownErr = errors.New(errors.CodeExecutionTimeout, "shutdown timeout")
			}
		}

		// Log final metrics
		s.logFinalMetrics()

		s.logger.Info("Server shutdown complete")
	})

	return shutdownErr
}

// monitorResources monitors system resource usage
func (s *Server) monitorResources() {
	defer s.wg.Done()

	for {
		select {
		case <-s.ctx.Done():
			return
		case <-s.resourceTicker.C:
			s.checkResources()
		}
	}
}

// checkResources checks current resource usage against limits
func (s *Server) checkResources() {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	// Update metrics collector
	s.metricsCollector.UpdateResourceMetrics(
		m.Alloc/1024/1024,
		runtime.NumGoroutine(),
		s.getCPUUsage(),
	)

	// Check memory usage
	if m.Alloc > s.memoryLimit {
		s.logger.Warn("Memory limit exceeded",
			"current_mb", m.Alloc/1024/1024,
			"limit_mb", s.memoryLimit/1024/1024,
		)
		// Force garbage collection
		runtime.GC()

		// If still over limit after GC, may need to reject new operations
		runtime.ReadMemStats(&m)
		if m.Alloc > s.memoryLimit {
			// Implement backpressure - could reject new connections/executions
			s.logger.Error("Memory limit still exceeded after GC")
		}
	}

	// Check goroutine count
	numGoroutines := runtime.NumGoroutine()
	if numGoroutines > s.goroutineLimit {
		s.logger.Warn("Goroutine limit exceeded",
			"current", numGoroutines,
			"limit", s.goroutineLimit,
		)
	}

	// Check connection limit
	currentConns := atomic.LoadInt32(&s.activeConns)
	if currentConns > s.maxConnections {
		s.logger.Warn("Connection limit exceeded",
			"current", currentConns,
			"limit", s.maxConnections,
		)
	}

	// Check project limit
	projectCount := s.projectManager.GetProjectCount()
	if projectCount > int(s.maxProjects) {
		s.logger.Warn("Project limit exceeded",
			"current", projectCount,
			"limit", s.maxProjects,
		)
	}

	// Update metrics
	s.metricsCollector.SetActiveProjects(int64(projectCount))

	// Log resource metrics
	s.logger.Debug("Resource check",
		"memory_mb", m.Alloc/1024/1024,
		"goroutines", numGoroutines,
		"connections", currentConns,
		"projects", projectCount,
		"gc_runs", m.NumGC,
	)
}

// collectMetrics collects and aggregates server metrics
func (s *Server) collectMetrics() {
	defer s.wg.Done()

	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-s.ctx.Done():
			return
		case <-ticker.C:
			s.publishMetrics()
		}
	}
}

// collectWebSocketMetrics collects metrics from WebSocket server
func (s *Server) collectWebSocketMetrics() {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-s.ctx.Done():
			return
		case <-ticker.C:
			metrics := s.wsServer.GetMetrics()
			if conns, ok := metrics["active_connections"].(int64); ok {
				atomic.StoreInt32(&s.activeConns, int32(conns))
			}
		}
	}
}

// publishMetrics publishes current metrics
func (s *Server) publishMetrics() {
	snapshot := s.metricsCollector.GetSnapshot()

	metrics := map[string]interface{}{
		"uptime_seconds": time.Since(s.startTime).Seconds(),
		"counters": map[string]interface{}{
			"total_executions":  snapshot.Counters.TotalExecutions,
			"total_messages":    snapshot.Counters.TotalMessages,
			"total_connections": snapshot.Counters.TotalConnections,
			"total_errors":      snapshot.Counters.TotalErrors,
		},
		"gauges": map[string]interface{}{
			"active_connections": snapshot.Gauges.ActiveConnections,
			"active_executions":  snapshot.Gauges.ActiveExecutions,
			"active_projects":    snapshot.Gauges.ActiveProjects,
		},
		"resources": map[string]interface{}{
			"memory_mb":   snapshot.Resources.MemoryMB,
			"goroutines":  snapshot.Resources.GoroutineCount,
			"cpu_percent": snapshot.Resources.CPUPercent,
		},
		"performance": map[string]interface{}{
			"execution_p50_ms":      snapshot.Performance.ExecutionDurations.P50.Milliseconds(),
			"execution_p90_ms":      snapshot.Performance.ExecutionDurations.P90.Milliseconds(),
			"execution_p99_ms":      snapshot.Performance.ExecutionDurations.P99.Milliseconds(),
			"message_throughput_ps": snapshot.Performance.MessageThroughput,
		},
	}

	s.logger.Info("Server metrics", metrics)
}

// logFinalMetrics logs final metrics at shutdown
func (s *Server) logFinalMetrics() {
	uptime := time.Since(s.startTime)
	snapshot := s.metricsCollector.GetSnapshot()

	s.logger.Info("Final server metrics",
		"uptime", uptime.String(),
		"total_executions", snapshot.Counters.TotalExecutions,
		"total_messages", snapshot.Counters.TotalMessages,
		"total_connections", snapshot.Counters.TotalConnections,
		"total_errors", snapshot.Counters.TotalErrors,
		"final_projects", s.projectManager.GetProjectCount(),
	)
}

// IncrementExecutions increments the execution counter
func (s *Server) IncrementExecutions() {
	s.metricsCollector.IncrementExecutions()
}

// DecrementExecutions decrements the active execution counter
func (s *Server) DecrementExecutions() {
	s.metricsCollector.DecrementExecutions()
}

// IncrementMessages increments the message counter
func (s *Server) IncrementMessages() {
	s.metricsCollector.IncrementMessages()
}

// IncrementConnections increments connection counters
func (s *Server) IncrementConnections() {
	s.metricsCollector.IncrementConnections()
	atomic.AddInt32(&s.activeConns, 1)
}

// DecrementConnections decrements connection counters
func (s *Server) DecrementConnections() {
	s.metricsCollector.DecrementConnections()
	atomic.AddInt32(&s.activeConns, -1)
}

// IncrementErrors increments the error counter
func (s *Server) IncrementErrors() {
	s.metricsCollector.IncrementErrors()
}

// RecordExecutionDuration records the duration of an execution
func (s *Server) RecordExecutionDuration(projectID string, duration time.Duration) {
	s.metricsCollector.RecordExecutionDuration(duration)
}

// GetMetrics returns comprehensive server metrics
func (s *Server) GetMetrics() map[string]interface{} {
	snapshot := s.metricsCollector.GetSnapshot()
	wsMetrics := s.wsServer.GetMetrics()
	executorStats := s.executor.GetStats()

	// Get platform info
	platformInfo := s.getPlatformInfo()

	return map[string]interface{}{
		"server": map[string]interface{}{
			"uptime_seconds": time.Since(s.startTime).Seconds(),
			"version":        "1.0.0",
			"platform":       runtime.GOOS,
			"arch":           runtime.GOARCH,
		},
		"counters": map[string]interface{}{
			"total_executions":  snapshot.Counters.TotalExecutions,
			"total_messages":    snapshot.Counters.TotalMessages,
			"total_connections": snapshot.Counters.TotalConnections,
			"total_errors":      snapshot.Counters.TotalErrors,
		},
		"gauges": map[string]interface{}{
			"active_connections": snapshot.Gauges.ActiveConnections,
			"active_executions":  snapshot.Gauges.ActiveExecutions,
			"active_projects":    snapshot.Gauges.ActiveProjects,
		},
		"resources": map[string]interface{}{
			"memory_mb":       snapshot.Resources.MemoryMB,
			"memory_limit_mb": s.memoryLimit / 1024 / 1024,
			"goroutines":      snapshot.Resources.GoroutineCount,
			"goroutine_limit": s.goroutineLimit,
			"cpu_percent":     snapshot.Resources.CPUPercent,
			"cpu_count":       runtime.NumCPU(),
		},
		"performance": map[string]interface{}{
			"execution_p50_ms":      snapshot.Performance.ExecutionDurations.P50.Milliseconds(),
			"execution_p90_ms":      snapshot.Performance.ExecutionDurations.P90.Milliseconds(),
			"execution_p99_ms":      snapshot.Performance.ExecutionDurations.P99.Milliseconds(),
			"message_throughput_ps": snapshot.Performance.MessageThroughput,
		},
		"websocket": wsMetrics,
		"projects": map[string]interface{}{
			"total": s.projectManager.GetProjectCount(),
			"limit": s.maxProjects,
		},
		"executor": executorStats,
		"platform": platformInfo,
	}
}

// performPlatformChecks performs platform-specific compatibility checks
func (s *Server) performPlatformChecks() {
	issues := platform.CheckPermissions()

	// Platform-specific checks
	switch runtime.GOOS {
	case "darwin":
		macIssues := platform.CheckMacOSPermissions()
		issues = append(issues, macIssues...)
	case "linux":
		// Platform-specific checks are handled via build tags
		platformIssues := platform.CheckPlatformSpecificPermissions()
		issues = append(issues, platformIssues...)
	}

	// Log any issues found
	for _, issue := range issues {
		s.logger.Warn("Platform compatibility issue", "issue", issue)
	}

	// Check if running as root (generally not recommended)
	if platform.IsRoot() {
		s.logger.Warn("Running as root user - this is not recommended for security reasons")
	}
}

// getPlatformInfo returns platform-specific information
func (s *Server) getPlatformInfo() map[string]interface{} {
	info := map[string]interface{}{
		"os":   runtime.GOOS,
		"arch": runtime.GOARCH,
	}

	// Get platform-specific system info
	switch runtime.GOOS {
	case "darwin":
		sysInfo := platform.GetSystemInfo()
		for k, v := range sysInfo {
			info[k] = v
		}
	case "linux":
		sysInfo := platform.GetSystemInfo()
		for k, v := range sysInfo {
			info[k] = v
		}
	}

	// Get resource limits
	if limits, err := platform.GetResourceLimits(); err == nil {
		info["limits"] = map[string]interface{}{
			"max_open_files": limits.MaxOpenFiles,
			"max_memory":     limits.MaxMemory,
			"max_cpu_time":   limits.MaxCPUTime,
		}
	}

	return info
}

// getCPUUsage returns current CPU usage percentage
func (s *Server) getCPUUsage() float64 {
	// This is a simplified implementation
	// In production, you'd want to track CPU time over intervals
	return 0.0 // Placeholder
}

// reloadConfiguration handles configuration reload on SIGHUP
func (s *Server) reloadConfiguration() {
	s.logger.Info("Configuration reload requested")
	// In a real implementation, this would:
	// 1. Re-read configuration files
	// 2. Update any runtime settings that can be changed
	// 3. Notify components of configuration changes
	// For now, just log that it was requested
	s.logger.Info("Configuration reload completed (no-op)")
}
