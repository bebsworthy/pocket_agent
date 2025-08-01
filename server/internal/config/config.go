// Package config provides configuration management for the Pocket Agent server.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// Config represents the server configuration.
type Config struct {
	// Server settings
	Port        int    `json:"port"`
	Host        string `json:"host"`
	TLSEnabled  bool   `json:"tls_enabled"`
	TLSCertFile string `json:"tls_cert_file"`
	TLSKeyFile  string `json:"tls_key_file"`

	// Data storage
	DataDir string `json:"data_dir"`

	// WebSocket settings
	WebSocket WebSocketConfig `json:"websocket"`

	// Execution settings
	Execution ExecutionConfig `json:"execution"`

	// Logging
	LogLevel string `json:"log_level"`
	LogFile  string `json:"log_file"`
}

// WebSocketConfig contains WebSocket-specific configuration.
type WebSocketConfig struct {
	ReadTimeout     Duration `json:"read_timeout"`
	WriteTimeout    Duration `json:"write_timeout"`
	PingInterval    Duration `json:"ping_interval"`
	PongTimeout     Duration `json:"pong_timeout"`
	MaxMessageSize  int64    `json:"max_message_size"`
	WriteBufferSize int      `json:"write_buffer_size"`
	ReadBufferSize  int      `json:"read_buffer_size"`
}

// ExecutionConfig contains Claude execution configuration.
type ExecutionConfig struct {
	CommandTimeout    Duration `json:"command_timeout"`
	MaxProjects       int      `json:"max_projects"`
	MaxLogSize        int64    `json:"max_log_size"`
	MaxMessagesPerLog int      `json:"max_messages_per_log"`
	ClaudeBinaryPath  string   `json:"claude_binary_path"`
}

// Options represents configuration options passed via command line.
type Options struct {
	RootDir string
	Port    int
	DataDir string
}

// DefaultConfig returns a configuration with sensible defaults.
func DefaultConfig() *Config {
	homeDir, _ := os.UserHomeDir()
	baseDir := filepath.Join(homeDir, ".pocket_agent")

	return &Config{
		Port:        8443,
		Host:        "0.0.0.0",
		TLSEnabled:  false,
		DataDir:     baseDir,
		LogLevel:    "info",
		LogFile:     filepath.Join(baseDir, "logs", "pocket-agent.log"),
		TLSCertFile: filepath.Join(baseDir, "certs", "server.crt"),
		TLSKeyFile:  filepath.Join(baseDir, "certs", "server.key"),

		WebSocket: WebSocketConfig{
			ReadTimeout:     Duration{10 * time.Minute},
			WriteTimeout:    Duration{10 * time.Second},
			PingInterval:    Duration{5 * time.Minute},
			PongTimeout:     Duration{30 * time.Second},
			MaxMessageSize:  1024 * 1024, // 1MB
			WriteBufferSize: 1024,
			ReadBufferSize:  1024,
		},

		Execution: ExecutionConfig{
			CommandTimeout:    Duration{0}, // No timeout by default
			MaxProjects:       100,
			MaxLogSize:        100 * 1024 * 1024, // 100MB
			MaxMessagesPerLog: 10000,
			ClaudeBinaryPath:  "claude",
		},
	}
}

// DefaultConfigPath returns the default configuration file path
func DefaultConfigPath() string {
	return DefaultConfigPathWithRoot("")
}

// DefaultConfigPathWithRoot returns the configuration file path for a given root directory
func DefaultConfigPathWithRoot(rootDir string) string {
	if rootDir == "" {
		homeDir, _ := os.UserHomeDir()
		rootDir = filepath.Join(homeDir, ".pocket_agent")
	}
	return filepath.Join(rootDir, "config.json")
}

// EnsureDefaultConfig ensures the default config file and directories exist
func EnsureDefaultConfig() error {
	return EnsureDefaultConfigWithRoot("")
}

// EnsureDefaultConfigWithRoot ensures the config file and directories exist for a given root
func EnsureDefaultConfigWithRoot(rootDir string) error {
	if rootDir == "" {
		homeDir, _ := os.UserHomeDir()
		rootDir = filepath.Join(homeDir, ".pocket_agent")
	}

	// Create directory structure
	dirs := []string{
		rootDir,
		filepath.Join(rootDir, "certs"),
		filepath.Join(rootDir, "projects"),
	}

	for _, dir := range dirs {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return fmt.Errorf("failed to create directory %s: %w", dir, err)
		}
	}

	// Check if config file exists
	configPath := filepath.Join(rootDir, "config.json")
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		// Create default config file with paths relative to root
		cfg := DefaultConfig()
		// Override paths to use the specified root
		cfg.DataDir = rootDir
		cfg.LogFile = filepath.Join(rootDir, "logs", "pocket-agent.log")
		cfg.TLSCertFile = filepath.Join(rootDir, "certs", "server.crt")
		cfg.TLSKeyFile = filepath.Join(rootDir, "certs", "server.key")

		data, err := json.MarshalIndent(cfg, "", "  ")
		if err != nil {
			return fmt.Errorf("failed to marshal default config: %w", err)
		}

		if err := os.WriteFile(configPath, data, 0o644); err != nil {
			return fmt.Errorf("failed to write default config file: %w", err)
		}
	}

	return nil
}

// Load loads configuration from file and applies command line options.
func Load(configPath string, opts Options) (*Config, error) {
	cfg := DefaultConfig()

	// If root directory is specified, update default paths
	if opts.RootDir != "" {
		cfg.DataDir = opts.RootDir
		cfg.LogFile = filepath.Join(opts.RootDir, "logs", "pocket-agent.log")
		cfg.TLSCertFile = filepath.Join(opts.RootDir, "certs", "server.crt")
		cfg.TLSKeyFile = filepath.Join(opts.RootDir, "certs", "server.key")
	}

	// Load from file if provided
	if configPath != "" {
		data, err := os.ReadFile(configPath)
		if err != nil {
			return nil, fmt.Errorf("failed to read config file: %w", err)
		}

		if err := json.Unmarshal(data, cfg); err != nil {
			return nil, fmt.Errorf("failed to parse config file: %w", err)
		}
	}

	// Apply environment variables (priority: ENV > config file > defaults)
	if err := cfg.loadFromEnv(); err != nil {
		return nil, fmt.Errorf("failed to load environment variables: %w", err)
	}

	// Apply command line options (highest priority)
	if opts.Port != 0 {
		cfg.Port = opts.Port
	}
	if opts.DataDir != "" {
		cfg.DataDir = opts.DataDir
	}

	// Validate configuration
	if err := cfg.Validate(); err != nil {
		return nil, fmt.Errorf("invalid configuration: %w", err)
	}

	// Ensure data directory exists
	if err := os.MkdirAll(cfg.DataDir, 0o755); err != nil {
		return nil, fmt.Errorf("failed to create data directory: %w", err)
	}

	// Create projects directory
	projectsDir := filepath.Join(cfg.DataDir, "projects")
	if err := os.MkdirAll(projectsDir, 0o755); err != nil {
		return nil, fmt.Errorf("failed to create projects directory: %w", err)
	}

	// Ensure log directory exists
	if cfg.LogFile != "" {
		logDir := filepath.Dir(cfg.LogFile)
		if err := os.MkdirAll(logDir, 0o755); err != nil {
			return nil, fmt.Errorf("failed to create log directory: %w", err)
		}
	}

	return cfg, nil
}

// Validate checks if the configuration is valid.
func (c *Config) Validate() error {
	if c.Port < 1 || c.Port > 65535 {
		return fmt.Errorf("invalid port: %d (must be between 1-65535)", c.Port)
	}

	if c.Host == "" {
		return fmt.Errorf("host cannot be empty")
	}

	if c.TLSEnabled {
		if c.TLSCertFile == "" || c.TLSKeyFile == "" {
			return fmt.Errorf("TLS is enabled but cert/key files not specified")
		}
		// Check if TLS files exist only if paths are provided
		if c.TLSCertFile != "" {
			if _, err := os.Stat(c.TLSCertFile); err != nil {
				return fmt.Errorf("TLS cert file not found: %s", c.TLSCertFile)
			}
		}
		if c.TLSKeyFile != "" {
			if _, err := os.Stat(c.TLSKeyFile); err != nil {
				return fmt.Errorf("TLS key file not found: %s", c.TLSKeyFile)
			}
		}
	}

	if c.DataDir == "" {
		return fmt.Errorf("data_dir cannot be empty")
	}

	// Validate WebSocket settings
	if c.WebSocket.MaxMessageSize < 1024 {
		return fmt.Errorf("max_message_size must be at least 1KB")
	}
	if c.WebSocket.MaxMessageSize > 10*1024*1024 {
		return fmt.Errorf("max_message_size cannot exceed 10MB")
	}
	if c.WebSocket.ReadTimeout.Get() < time.Second {
		return fmt.Errorf("read_timeout must be at least 1 second")
	}
	if c.WebSocket.PingInterval.Get() < time.Second {
		return fmt.Errorf("ping_interval must be at least 1 second")
	}
	if c.WebSocket.PongTimeout.Get() < time.Second {
		return fmt.Errorf("pong_timeout must be at least 1 second")
	}

	// Validate Execution settings
	if c.Execution.MaxProjects < 1 {
		return fmt.Errorf("max_projects must be at least 1")
	}
	if c.Execution.MaxProjects > 1000 {
		return fmt.Errorf("max_projects cannot exceed 1000")
	}
	if c.Execution.CommandTimeout.Get() < 0 {
		return fmt.Errorf("command_timeout cannot be negative")
	}
	if c.Execution.MaxLogSize < 1024*1024 {
		return fmt.Errorf("max_log_size must be at least 1MB")
	}
	if c.Execution.MaxMessagesPerLog < 100 {
		return fmt.Errorf("max_messages_per_log must be at least 100")
	}
	if c.Execution.ClaudeBinaryPath == "" {
		return fmt.Errorf("claude_binary_path cannot be empty")
	}

	// Validate log level
	validLogLevels := map[string]bool{
		"debug": true,
		"info":  true,
		"warn":  true,
		"error": true,
	}
	if !validLogLevels[strings.ToLower(c.LogLevel)] {
		return fmt.Errorf("invalid log_level: %s (must be debug, info, warn, or error)", c.LogLevel)
	}

	return nil
}

// loadFromEnv loads configuration from environment variables.
// Environment variables use the prefix POCKET_AGENT_ and follow the pattern:
// POCKET_AGENT_<SECTION>_<KEY> where section is optional for top-level keys.
func (c *Config) loadFromEnv() error {
	// Server settings
	if val := os.Getenv("POCKET_AGENT_PORT"); val != "" {
		port, err := strconv.Atoi(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_PORT: %w", err)
		}
		c.Port = port
	}

	if val := os.Getenv("POCKET_AGENT_HOST"); val != "" {
		c.Host = val
	}

	if val := os.Getenv("POCKET_AGENT_TLS_ENABLED"); val != "" {
		tlsEnabled, err := strconv.ParseBool(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_TLS_ENABLED: %w", err)
		}
		c.TLSEnabled = tlsEnabled
	}

	if val := os.Getenv("POCKET_AGENT_TLS_CERT_FILE"); val != "" {
		c.TLSCertFile = val
	}

	if val := os.Getenv("POCKET_AGENT_TLS_KEY_FILE"); val != "" {
		c.TLSKeyFile = val
	}

	if val := os.Getenv("POCKET_AGENT_DATA_DIR"); val != "" {
		c.DataDir = val
	}

	if val := os.Getenv("POCKET_AGENT_LOG_LEVEL"); val != "" {
		c.LogLevel = val
	}

	if val := os.Getenv("POCKET_AGENT_LOG_FILE"); val != "" {
		c.LogFile = val
	}

	// WebSocket settings
	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_READ_TIMEOUT"); val != "" {
		dur, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_READ_TIMEOUT: %w", err)
		}
		c.WebSocket.ReadTimeout = Duration{dur}
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_WRITE_TIMEOUT"); val != "" {
		dur, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_WRITE_TIMEOUT: %w", err)
		}
		c.WebSocket.WriteTimeout = Duration{dur}
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_PING_INTERVAL"); val != "" {
		dur, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_PING_INTERVAL: %w", err)
		}
		c.WebSocket.PingInterval = Duration{dur}
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_PONG_TIMEOUT"); val != "" {
		dur, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_PONG_TIMEOUT: %w", err)
		}
		c.WebSocket.PongTimeout = Duration{dur}
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_MAX_MESSAGE_SIZE"); val != "" {
		size, err := parseSize(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_MAX_MESSAGE_SIZE: %w", err)
		}
		c.WebSocket.MaxMessageSize = size
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_WRITE_BUFFER_SIZE"); val != "" {
		size, err := strconv.Atoi(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_WRITE_BUFFER_SIZE: %w", err)
		}
		c.WebSocket.WriteBufferSize = size
	}

	if val := os.Getenv("POCKET_AGENT_WEBSOCKET_READ_BUFFER_SIZE"); val != "" {
		size, err := strconv.Atoi(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_WEBSOCKET_READ_BUFFER_SIZE: %w", err)
		}
		c.WebSocket.ReadBufferSize = size
	}

	// Execution settings
	if val := os.Getenv("POCKET_AGENT_EXECUTION_COMMAND_TIMEOUT"); val != "" {
		dur, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_EXECUTION_COMMAND_TIMEOUT: %w", err)
		}
		c.Execution.CommandTimeout = Duration{dur}
	}

	if val := os.Getenv("POCKET_AGENT_EXECUTION_MAX_PROJECTS"); val != "" {
		max, err := strconv.Atoi(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_EXECUTION_MAX_PROJECTS: %w", err)
		}
		c.Execution.MaxProjects = max
	}

	if val := os.Getenv("POCKET_AGENT_EXECUTION_MAX_LOG_SIZE"); val != "" {
		size, err := parseSize(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_EXECUTION_MAX_LOG_SIZE: %w", err)
		}
		c.Execution.MaxLogSize = size
	}

	if val := os.Getenv("POCKET_AGENT_EXECUTION_MAX_MESSAGES_PER_LOG"); val != "" {
		max, err := strconv.Atoi(val)
		if err != nil {
			return fmt.Errorf("invalid POCKET_AGENT_EXECUTION_MAX_MESSAGES_PER_LOG: %w", err)
		}
		c.Execution.MaxMessagesPerLog = max
	}

	if val := os.Getenv("POCKET_AGENT_EXECUTION_CLAUDE_BINARY_PATH"); val != "" {
		c.Execution.ClaudeBinaryPath = val
	}

	return nil
}

// parseSize parses size strings like "1MB", "100KB", "1024" (bytes).
func parseSize(s string) (int64, error) {
	s = strings.TrimSpace(strings.ToUpper(s))

	// Check for unit suffixes
	var multiplier int64 = 1
	var numStr string

	switch {
	case strings.HasSuffix(s, "GB"):
		multiplier = 1024 * 1024 * 1024
		numStr = strings.TrimSuffix(s, "GB")
	case strings.HasSuffix(s, "MB"):
		multiplier = 1024 * 1024
		numStr = strings.TrimSuffix(s, "MB")
	case strings.HasSuffix(s, "KB"):
		multiplier = 1024
		numStr = strings.TrimSuffix(s, "KB")
	case strings.HasSuffix(s, "B"):
		multiplier = 1
		numStr = strings.TrimSuffix(s, "B")
	default:
		// No suffix, assume bytes
		numStr = s
	}

	num, err := strconv.ParseInt(strings.TrimSpace(numStr), 10, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid size format: %s", s)
	}

	if num < 0 {
		return 0, fmt.Errorf("size cannot be negative: %s", s)
	}

	return num * multiplier, nil
}
