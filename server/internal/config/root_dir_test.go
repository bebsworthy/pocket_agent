package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestRootDirectory(t *testing.T) {
	tests := []struct {
		name           string
		rootDir        string
		expectedConfig string
		expectedData   string
		expectedLog    string
		expectedCert   string
	}{
		{
			name:    "default root",
			rootDir: "",
			expectedConfig: func() string {
				home, _ := os.UserHomeDir()
				return filepath.Join(home, ".pocket_agent", "config.json")
			}(),
			expectedData: func() string {
				home, _ := os.UserHomeDir()
				return filepath.Join(home, ".pocket_agent")
			}(),
			expectedLog: func() string {
				home, _ := os.UserHomeDir()
				return filepath.Join(home, ".pocket_agent", "logs", "pocket-agent.log")
			}(),
			expectedCert: func() string {
				home, _ := os.UserHomeDir()
				return filepath.Join(home, ".pocket_agent", "certs", "server.crt")
			}(),
		},
		{
			name:           "custom root",
			rootDir:        "/tmp/test-root",
			expectedConfig: "/tmp/test-root/config.json",
			expectedData:   "/tmp/test-root",
			expectedLog:    "/tmp/test-root/logs/pocket-agent.log",
			expectedCert:   "/tmp/test-root/certs/server.crt",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Test DefaultConfigPathWithRoot
			configPath := DefaultConfigPathWithRoot(tt.rootDir)
			if configPath != tt.expectedConfig {
				t.Errorf("DefaultConfigPathWithRoot() = %v, want %v", configPath, tt.expectedConfig)
			}

			// Test Load with root directory
			tmpDir := t.TempDir()
			opts := Options{
				RootDir: tt.rootDir,
			}
			if tt.rootDir == "" {
				// For default case, don't override
				opts.RootDir = ""
			} else {
				// For custom case, use temp dir
				opts.RootDir = tmpDir
				tt.expectedData = tmpDir
				tt.expectedLog = filepath.Join(tmpDir, "logs", "pocket-agent.log")
				tt.expectedCert = filepath.Join(tmpDir, "certs", "server.crt")
			}

			cfg, err := Load("", opts)
			if err != nil {
				t.Fatalf("Load() error = %v", err)
			}

			if cfg.DataDir != tt.expectedData {
				t.Errorf("cfg.DataDir = %v, want %v", cfg.DataDir, tt.expectedData)
			}
			if cfg.LogFile != tt.expectedLog {
				t.Errorf("cfg.LogFile = %v, want %v", cfg.LogFile, tt.expectedLog)
			}
			if cfg.TLSCertFile != tt.expectedCert {
				t.Errorf("cfg.TLSCertFile = %v, want %v", cfg.TLSCertFile, tt.expectedCert)
			}
		})
	}
}

func TestRootDirectoryOverrides(t *testing.T) {
	tmpDir := t.TempDir()
	customDataDir := filepath.Join(tmpDir, "custom-data")
	
	// Test that command line data-dir overrides root directory default
	opts := Options{
		RootDir: tmpDir,
		DataDir: customDataDir,
	}

	cfg, err := Load("", opts)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	// DataDir should be overridden
	if cfg.DataDir != customDataDir {
		t.Errorf("cfg.DataDir = %v, want %v", cfg.DataDir, customDataDir)
	}

	// But other paths should still use root dir
	expectedLog := filepath.Join(tmpDir, "logs", "pocket-agent.log")
	if cfg.LogFile != expectedLog {
		t.Errorf("cfg.LogFile = %v, want %v", cfg.LogFile, expectedLog)
	}
}