package main

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/boyd/pocket_agent/claude-mock/replayer"
	flag "github.com/spf13/pflag"
)

func main() {
	// Define command line flags to match Claude CLI
	var (
		sessionID                  string
		printAndExit               bool
		verbose                    bool
		outputFormat               string
		model                      string
		fallbackModel              string
		dangerouslySkipPermissions bool
		allowedTools               string
		disallowedTools            string
		mcpConfig                  string
		appendSystemPrompt         string
		permissionMode             string
		addDirs                    []string
		strictMCPConfig            bool
	)

	// Session management
	flag.StringVarP(&sessionID, "continue", "c", "", "Resume a specific session")
	
	// Output control
	flag.BoolVarP(&printAndExit, "print", "p", false, "Print response and exit (non-interactive mode)")
	flag.BoolVar(&verbose, "verbose", false, "Enable verbose output")
	flag.StringVar(&outputFormat, "output-format", "stream-json", "Output format")
	
	// Model selection (ignored, for compatibility)
	flag.StringVar(&model, "model", "", "Model to use")
	flag.StringVar(&fallbackModel, "fallback-model", "", "Fallback model")
	
	// Permissions (ignored, for compatibility)
	flag.BoolVar(&dangerouslySkipPermissions, "dangerously-skip-permissions", false, "Skip permission checks")
	flag.StringVar(&permissionMode, "permission-mode", "", "Permission mode")
	
	// Tools configuration (ignored, for compatibility)
	flag.StringVar(&allowedTools, "allowed-tools", "", "Allowed tools")
	flag.StringVar(&disallowedTools, "disallowed-tools", "", "Disallowed tools")
	
	// MCP configuration (ignored, for compatibility)
	flag.StringVar(&mcpConfig, "mcp-config", "", "MCP configuration")
	flag.BoolVar(&strictMCPConfig, "strict-mcp-config", false, "Strict MCP config")
	
	// System prompt (ignored, for compatibility)
	flag.StringVar(&appendSystemPrompt, "append-system-prompt", "", "Append to system prompt")
	
	// Directories (ignored, for compatibility)
	flag.StringArrayVar(&addDirs, "add-dir", []string{}, "Add directories")

	// Parse flags
	flag.Parse()

	// Check for log file from environment or use default
	logFile := os.Getenv("CLAUDE_MOCK_LOG_FILE")
	if logFile == "" {
		// Use default hello conversation
		execPath, err := os.Executable()
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error: Could not determine executable path: %v\n", err)
			os.Exit(1)
		}
		dir := filepath.Dir(execPath)
		// Try relative to executable first
		logFile = filepath.Join(dir, "conversations", "hello.jsonl")
		if _, err := os.Stat(logFile); os.IsNotExist(err) {
			// Try relative to module directory
			logFile = filepath.Join(filepath.Dir(dir), "claude-mock", "conversations", "hello.jsonl")
			if _, err := os.Stat(logFile); os.IsNotExist(err) {
				fmt.Fprintf(os.Stderr, "Error: CLAUDE_MOCK_LOG_FILE not set and default conversation file not found\n")
				fmt.Fprintf(os.Stderr, "Please set CLAUDE_MOCK_LOG_FILE or ensure conversations/hello.jsonl exists\n")
				os.Exit(1)
			}
		}
		if verbose {
			fmt.Fprintf(os.Stderr, "Using default conversation: %s\n", logFile)
		}
	}

	// Read prompt from stdin
	promptBytes, err := io.ReadAll(os.Stdin)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error reading prompt: %v\n", err)
		os.Exit(1)
	}
	prompt := strings.TrimSpace(string(promptBytes))

	// Create replayer
	r, err := replayer.New(logFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error creating replayer: %v\n", err)
		os.Exit(1)
	}

	// Set session ID if provided
	if sessionID != "" {
		r.SetSessionID(sessionID)
	}

	// Log prompt if verbose
	if verbose {
		fmt.Fprintf(os.Stderr, "Mock Claude received prompt: %s\n", prompt)
		fmt.Fprintf(os.Stderr, "Session ID: %s\n", sessionID)
		fmt.Fprintf(os.Stderr, "Log file: %s\n", logFile)
	}

	// Replay messages
	if err := r.Replay(); err != nil {
		fmt.Fprintf(os.Stderr, "Error replaying messages: %v\n", err)
		os.Exit(1)
	}
}