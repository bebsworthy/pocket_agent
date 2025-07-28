package main

import (
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/boyd/pocket_agent/server/internal"
	"github.com/boyd/pocket_agent/server/internal/config"
	"github.com/boyd/pocket_agent/server/internal/logger"
)

var (
	// Version information set during build
	Version   = "dev"
	BuildTime = "unknown"
	GitCommit = "unknown"
)

func main() {
	// Command line flags
	var (
		configPath  = flag.String("config", "", "Path to configuration file")
		showVersion = flag.Bool("version", false, "Show version information")
		logLevel    = flag.String("log-level", "info", "Log level (debug, info, warn, error)")
		port        = flag.Int("port", 8443, "Server port")
		dataDir     = flag.String("data-dir", "./data", "Data directory path")
	)
	flag.Parse()

	// Show version and exit if requested
	if *showVersion {
		fmt.Printf("Pocket Agent Server\n")
		fmt.Printf("Version:    %s\n", Version)
		fmt.Printf("Build Time: %s\n", BuildTime)
		fmt.Printf("Git Commit: %s\n", GitCommit)
		os.Exit(0)
	}

	// Initialize logger
	log := logger.New(*logLevel)
	log.Info("Starting Pocket Agent Server",
		"version", Version,
		"build_time", BuildTime,
		"git_commit", GitCommit,
	)

	// Load configuration
	cfg, err := config.Load(*configPath, config.Options{
		Port:    *port,
		DataDir: *dataDir,
	})
	if err != nil {
		log.Error("Failed to load configuration", "error", err)
		os.Exit(1)
	}

	// Create server configuration
	serverConfig := internal.ServerConfig{
		Config:                cfg,
		MaxConnections:        100,  // Can be made configurable
		MaxProjects:           100,  // Can be made configurable
		MemoryLimitMB:         2048, // Can be made configurable
		GoroutineLimit:        1000, // Can be made configurable
		ResourceCheckInterval: 30 * time.Second,
	}

	// Create and start server
	server, err := internal.NewServer(serverConfig)
	if err != nil {
		log.Error("Failed to create server", "error", err)
		os.Exit(1)
	}

	// Start server
	if err := server.Start(); err != nil {
		log.Error("Server error", "error", err)
		os.Exit(1)
	}
}
