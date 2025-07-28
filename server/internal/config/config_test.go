package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()

	if cfg.Port != 8443 {
		t.Errorf("expected port 8443, got %d", cfg.Port)
	}
	if cfg.Host != "0.0.0.0" {
		t.Errorf("expected host 0.0.0.0, got %s", cfg.Host)
	}
	if !cfg.TLSEnabled {
		t.Error("expected TLS to be enabled by default")
	}
	if cfg.DataDir != "./data" {
		t.Errorf("expected data dir ./data, got %s", cfg.DataDir)
	}
	if cfg.LogLevel != "info" {
		t.Errorf("expected log level info, got %s", cfg.LogLevel)
	}

	// WebSocket defaults
	if cfg.WebSocket.ReadTimeout != 10*time.Minute {
		t.Errorf("expected read timeout 10m, got %v", cfg.WebSocket.ReadTimeout)
	}
	if cfg.WebSocket.PingInterval != 5*time.Minute {
		t.Errorf("expected ping interval 5m, got %v", cfg.WebSocket.PingInterval)
	}
	if cfg.WebSocket.MaxMessageSize != 1024*1024 {
		t.Errorf("expected max message size 1MB, got %d", cfg.WebSocket.MaxMessageSize)
	}

	// Execution defaults
	if cfg.Execution.CommandTimeout != 5*time.Minute {
		t.Errorf("expected command timeout 5m, got %v", cfg.Execution.CommandTimeout)
	}
	if cfg.Execution.MaxProjects != 100 {
		t.Errorf("expected max projects 100, got %d", cfg.Execution.MaxProjects)
	}
	if cfg.Execution.ClaudeBinaryPath != "claude" {
		t.Errorf("expected claude binary path 'claude', got %s", cfg.Execution.ClaudeBinaryPath)
	}
}

func TestLoadFromFile(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "config.json")

	testConfig := map[string]interface{}{
		"port":        9999,
		"host":        "localhost",
		"log_level":   "debug",
		"data_dir":    "/custom/data",
		"tls_enabled": false, // Disable TLS for test
		"websocket": map[string]interface{}{
			"max_message_size": 2097152, // 2MB
		},
		"execution": map[string]interface{}{
			"max_projects": 50,
		},
	}

	data, err := json.Marshal(testConfig)
	if err != nil {
		t.Fatal(err)
	}

	if err := os.WriteFile(configPath, data, 0o644); err != nil {
		t.Fatal(err)
	}

	// Create a temp data dir
	dataDir := filepath.Join(tmpDir, "data")

	cfg, err := Load(configPath, Options{DataDir: dataDir})
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Port != 9999 {
		t.Errorf("expected port 9999, got %d", cfg.Port)
	}
	if cfg.Host != "localhost" {
		t.Errorf("expected host localhost, got %s", cfg.Host)
	}
	if cfg.LogLevel != "debug" {
		t.Errorf("expected log level debug, got %s", cfg.LogLevel)
	}
	// Command line option should override file
	if cfg.DataDir != dataDir {
		t.Errorf("expected data dir %s, got %s", dataDir, cfg.DataDir)
	}
	if cfg.WebSocket.MaxMessageSize != 2097152 {
		t.Errorf("expected max message size 2MB, got %d", cfg.WebSocket.MaxMessageSize)
	}
	if cfg.Execution.MaxProjects != 50 {
		t.Errorf("expected max projects 50, got %d", cfg.Execution.MaxProjects)
	}
}

func TestLoadFromEnv(t *testing.T) {
	// Save and restore environment
	environ := os.Environ()
	defer func() {
		os.Clearenv()
		for _, env := range environ {
			if kv := splitEnv(env); len(kv) == 2 {
				os.Setenv(kv[0], kv[1])
			}
		}
	}()

	// Set test environment variables
	os.Setenv("POCKET_AGENT_PORT", "7777")
	os.Setenv("POCKET_AGENT_HOST", "127.0.0.1")
	os.Setenv("POCKET_AGENT_TLS_ENABLED", "false")
	os.Setenv("POCKET_AGENT_LOG_LEVEL", "error")
	os.Setenv("POCKET_AGENT_WEBSOCKET_MAX_MESSAGE_SIZE", "5MB")
	os.Setenv("POCKET_AGENT_WEBSOCKET_PING_INTERVAL", "1m")
	os.Setenv("POCKET_AGENT_EXECUTION_MAX_PROJECTS", "200")
	os.Setenv("POCKET_AGENT_EXECUTION_COMMAND_TIMEOUT", "10m")

	tmpDir := t.TempDir()
	cfg, err := Load("", Options{DataDir: tmpDir})
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Port != 7777 {
		t.Errorf("expected port 7777, got %d", cfg.Port)
	}
	if cfg.Host != "127.0.0.1" {
		t.Errorf("expected host 127.0.0.1, got %s", cfg.Host)
	}
	if cfg.TLSEnabled {
		t.Error("expected TLS to be disabled")
	}
	if cfg.LogLevel != "error" {
		t.Errorf("expected log level error, got %s", cfg.LogLevel)
	}
	if cfg.WebSocket.MaxMessageSize != 5*1024*1024 {
		t.Errorf("expected max message size 5MB, got %d", cfg.WebSocket.MaxMessageSize)
	}
	if cfg.WebSocket.PingInterval != time.Minute {
		t.Errorf("expected ping interval 1m, got %v", cfg.WebSocket.PingInterval)
	}
	if cfg.Execution.MaxProjects != 200 {
		t.Errorf("expected max projects 200, got %d", cfg.Execution.MaxProjects)
	}
	if cfg.Execution.CommandTimeout != 10*time.Minute {
		t.Errorf("expected command timeout 10m, got %v", cfg.Execution.CommandTimeout)
	}
}

func TestValidation(t *testing.T) {
	tests := []struct {
		name    string
		modify  func(*Config)
		wantErr string
	}{
		{
			name: "invalid port low",
			modify: func(c *Config) {
				c.Port = 0
			},
			wantErr: "invalid port",
		},
		{
			name: "invalid port high",
			modify: func(c *Config) {
				c.Port = 70000
			},
			wantErr: "invalid port",
		},
		{
			name: "empty host",
			modify: func(c *Config) {
				c.Host = ""
			},
			wantErr: "host cannot be empty",
		},
		{
			name: "TLS enabled without cert",
			modify: func(c *Config) {
				c.TLSEnabled = true
				c.TLSCertFile = ""
			},
			wantErr: "cert/key files not specified",
		},
		{
			name: "empty data dir",
			modify: func(c *Config) {
				c.DataDir = ""
			},
			wantErr: "data_dir cannot be empty",
		},
		{
			name: "message size too small",
			modify: func(c *Config) {
				c.WebSocket.MaxMessageSize = 512
			},
			wantErr: "max_message_size must be at least 1KB",
		},
		{
			name: "message size too large",
			modify: func(c *Config) {
				c.WebSocket.MaxMessageSize = 11 * 1024 * 1024
			},
			wantErr: "max_message_size cannot exceed 10MB",
		},
		{
			name: "invalid log level",
			modify: func(c *Config) {
				c.LogLevel = "trace"
			},
			wantErr: "invalid log_level",
		},
		{
			name: "max projects too low",
			modify: func(c *Config) {
				c.Execution.MaxProjects = 0
			},
			wantErr: "max_projects must be at least 1",
		},
		{
			name: "max projects too high",
			modify: func(c *Config) {
				c.Execution.MaxProjects = 1001
			},
			wantErr: "max_projects cannot exceed 1000",
		},
		{
			name: "empty claude binary path",
			modify: func(c *Config) {
				c.Execution.ClaudeBinaryPath = ""
			},
			wantErr: "claude_binary_path cannot be empty",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := DefaultConfig()
			cfg.TLSEnabled = false // Disable TLS for most tests
			tt.modify(cfg)

			err := cfg.Validate()
			if err == nil {
				t.Error("expected validation error but got none")
			} else if tt.wantErr != "" && !contains(err.Error(), tt.wantErr) {
				t.Errorf("expected error containing %q, got %q", tt.wantErr, err.Error())
			}
		})
	}
}

func TestParseSize(t *testing.T) {
	tests := []struct {
		input    string
		expected int64
		wantErr  bool
	}{
		{"1024", 1024, false},
		{"1KB", 1024, false},
		{"1kb", 1024, false},
		{"2MB", 2 * 1024 * 1024, false},
		{"5GB", 5 * 1024 * 1024 * 1024, false},
		{"100B", 100, false},
		{"10 MB", 10 * 1024 * 1024, false},
		{"invalid", 0, true},
		{"", 0, true},
		{"-1KB", 0, true},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			result, err := parseSize(tt.input)
			if tt.wantErr {
				if err == nil {
					t.Errorf("expected error for input %q", tt.input)
				}
			} else {
				if err != nil {
					t.Errorf("unexpected error for input %q: %v", tt.input, err)
				}
				if result != tt.expected {
					t.Errorf("for input %q, expected %d, got %d", tt.input, tt.expected, result)
				}
			}
		})
	}
}

func TestCommandLineOverrides(t *testing.T) {
	tmpDir := t.TempDir()

	// Set environment variable
	os.Setenv("POCKET_AGENT_PORT", "8888")
	defer os.Unsetenv("POCKET_AGENT_PORT")

	// Disable TLS for test
	os.Setenv("POCKET_AGENT_TLS_ENABLED", "false")
	defer os.Unsetenv("POCKET_AGENT_TLS_ENABLED")

	// Command line should override environment
	cfg, err := Load("", Options{
		Port:    9999,
		DataDir: tmpDir,
	})
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Port != 9999 {
		t.Errorf("expected command line port 9999 to override env, got %d", cfg.Port)
	}
}

// Helper functions
func splitEnv(env string) []string {
	for i := 0; i < len(env); i++ {
		if env[i] == '=' {
			return []string{env[:i], env[i+1:]}
		}
	}
	return []string{env}
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && s[:len(substr)] == substr || len(s) > len(substr) && contains(s[1:], substr)
}
