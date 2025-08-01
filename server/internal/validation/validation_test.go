package validation

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestValidatorValidatePath(t *testing.T) {
	// Create temporary test directories
	tempDir, err := os.MkdirTemp("", "validation_test_*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	// Create test subdirectories
	validDir := filepath.Join(tempDir, "valid")
	os.Mkdir(validDir, 0o755)

	// Create a file for testing
	testFile := filepath.Join(tempDir, "test.txt")
	os.WriteFile(testFile, []byte("test"), 0o644)

	v := NewValidator()

	tests := []struct {
		name    string
		path    string
		wantErr bool
		errMsg  string
	}{
		// Valid paths
		{
			name:    "valid existing directory",
			path:    validDir,
			wantErr: false,
		},
		{
			name:    "temp directory",
			path:    tempDir,
			wantErr: false,
		},
		// Invalid paths
		{
			name:    "empty path",
			path:    "",
			wantErr: true,
			errMsg:  "path cannot be empty",
		},
		{
			name:    "relative path",
			path:    "./relative",
			wantErr: true,
			errMsg:  "path must be absolute",
		},
		{
			name:    "path with ..",
			path:    tempDir + "/../escape", // Don't use filepath.Join as it resolves ..
			wantErr: true,
			errMsg:  "path traversal detected",
		},
		{
			name:    "non-existent path",
			path:    filepath.Join(tempDir, "nonexistent"),
			wantErr: true,
			errMsg:  "path does not exist",
		},
		{
			name:    "file instead of directory",
			path:    testFile,
			wantErr: true,
			errMsg:  "path is not a directory",
		},
		{
			name:    "null byte in path",
			path:    "/test\x00null",
			wantErr: true,
			errMsg:  "path contains invalid characters",
		},
		{
			name:    "tilde expansion",
			path:    "~/projects",
			wantErr: true,
			errMsg:  "tilde expansion paths are not allowed",
		},
	}

	// Platform-specific tests
	if runtime.GOOS != "windows" {
		tests = append(tests, []struct {
			name    string
			path    string
			wantErr bool
			errMsg  string
		}{
			{
				name:    "restricted /etc",
				path:    "/etc",
				wantErr: true,
				errMsg:  "path is in restricted directory",
			},
			{
				name:    "restricted /sys",
				path:    "/sys",
				wantErr: true,
				errMsg:  "path is in restricted directory",
			},
			{
				name:    "path with pipe",
				path:    "/test|pipe",
				wantErr: true,
				errMsg:  "path contains invalid characters",
			},
		}...)
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidatePath(tt.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidatePath() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("ValidatePath() error = %v, want error containing %q", err, tt.errMsg)
			}
		})
	}
}

func TestValidatorWithAllowedPaths(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "allowed_test_*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	allowedDir := filepath.Join(tempDir, "allowed")
	deniedDir := filepath.Join(tempDir, "denied")
	os.Mkdir(allowedDir, 0o755)
	os.Mkdir(deniedDir, 0o755)

	v := NewValidator()
	v.AllowedPaths = []string{allowedDir}

	tests := []struct {
		name    string
		path    string
		wantErr bool
	}{
		{
			name:    "allowed directory",
			path:    allowedDir,
			wantErr: false,
		},
		{
			name:    "not in allowed list",
			path:    deniedDir,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidatePath(tt.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidatePath() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestValidatorValidateProjectNesting(t *testing.T) {
	v := NewValidator()

	tests := []struct {
		name          string
		newPath       string
		existingPaths []string
		wantErr       bool
		errMsg        string
	}{
		{
			name:          "no existing projects",
			newPath:       "/home/user/project1",
			existingPaths: []string{},
			wantErr:       false,
		},
		{
			name:          "non-overlapping projects",
			newPath:       "/home/user/project2",
			existingPaths: []string{"/home/user/project1", "/var/data/project3"},
			wantErr:       false,
		},
		{
			name:          "same path",
			newPath:       "/home/user/project1",
			existingPaths: []string{"/home/user/project1"},
			wantErr:       true,
			errMsg:        "already exists",
		},
		{
			name:          "child of existing",
			newPath:       "/home/user/project1/sub",
			existingPaths: []string{"/home/user/project1"},
			wantErr:       true,
			errMsg:        "would be child",
		},
		{
			name:          "parent of existing",
			newPath:       "/home/user",
			existingPaths: []string{"/home/user/project1"},
			wantErr:       true,
			errMsg:        "would be parent",
		},
		{
			name:          "sibling paths",
			newPath:       "/home/user/project2",
			existingPaths: []string{"/home/user/project1", "/home/user/project3"},
			wantErr:       false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidateProjectNesting(tt.newPath, tt.existingPaths)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateProjectNesting() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("ValidateProjectNesting() error = %v, want error containing %q", err, tt.errMsg)
			}
		})
	}
}

func TestValidatorValidateMessageSize(t *testing.T) {
	v := NewValidator()
	v.MaxMessageSize = 1024 // 1KB for testing

	tests := []struct {
		name    string
		data    []byte
		wantErr bool
	}{
		{
			name:    "small message",
			data:    []byte("hello"),
			wantErr: false,
		},
		{
			name:    "exact limit",
			data:    make([]byte, 1024),
			wantErr: false,
		},
		{
			name:    "over limit",
			data:    make([]byte, 1025),
			wantErr: true,
		},
		{
			name:    "nil data",
			data:    nil,
			wantErr: false,
		},
		{
			name:    "empty data",
			data:    []byte{},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidateMessageSize(tt.data)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateMessageSize() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestValidatorValidateMessageBatch(t *testing.T) {
	v := NewValidator()
	v.MaxMessageSize = 100

	tests := []struct {
		name         string
		messages     []json.RawMessage
		maxTotalSize int
		wantErr      bool
		errMsg       string
	}{
		{
			name: "valid batch",
			messages: []json.RawMessage{
				json.RawMessage(`{"msg": "test1"}`),
				json.RawMessage(`{"msg": "test2"}`),
			},
			maxTotalSize: 1000,
			wantErr:      false,
		},
		{
			name: "individual message too large",
			messages: []json.RawMessage{
				json.RawMessage(make([]byte, 101)),
			},
			maxTotalSize: 1000,
			wantErr:      true,
			errMsg:       "exceeds maximum",
		},
		{
			name: "total size exceeded",
			messages: []json.RawMessage{
				json.RawMessage(make([]byte, 50)),
				json.RawMessage(make([]byte, 50)),
				json.RawMessage(make([]byte, 50)),
			},
			maxTotalSize: 100,
			wantErr:      true,
			errMsg:       "total message batch size",
		},
		{
			name:         "empty batch",
			messages:     []json.RawMessage{},
			maxTotalSize: 100,
			wantErr:      false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidateMessageBatch(tt.messages, tt.maxTotalSize)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateMessageBatch() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("ValidateMessageBatch() error = %v, want error containing %q", err, tt.errMsg)
			}
		})
	}
}

func TestValidatorValidateJSON(t *testing.T) {
	v := NewValidator()

	tests := []struct {
		name    string
		data    []byte
		wantErr bool
	}{
		{
			name:    "valid JSON object",
			data:    []byte(`{"key": "value"}`),
			wantErr: false,
		},
		{
			name:    "valid JSON array",
			data:    []byte(`[1, 2, 3]`),
			wantErr: false,
		},
		{
			name:    "invalid JSON",
			data:    []byte(`{key: value}`),
			wantErr: true,
		},
		{
			name:    "empty string",
			data:    []byte(""),
			wantErr: true,
		},
		{
			name:    "null",
			data:    []byte("null"),
			wantErr: false,
		},
		{
			name:    "truncated JSON",
			data:    []byte(`{"key": "val`),
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidateJSON(tt.data)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateJSON() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestValidatorValidatePrompt(t *testing.T) {
	v := NewValidator()
	v.MaxPromptLength = 100

	tests := []struct {
		name    string
		prompt  string
		wantErr bool
		errMsg  string
	}{
		{
			name:    "valid prompt",
			prompt:  "Please help me write a function",
			wantErr: false,
		},
		{
			name:    "empty prompt",
			prompt:  "",
			wantErr: true,
			errMsg:  "prompt cannot be empty",
		},
		{
			name:    "prompt with newlines",
			prompt:  "Line 1\nLine 2\nLine 3",
			wantErr: false,
		},
		{
			name:    "prompt with tabs",
			prompt:  "Column1\tColumn2\tColumn3",
			wantErr: false,
		},
		{
			name:    "prompt too long",
			prompt:  strings.Repeat("a", 101),
			wantErr: true,
			errMsg:  "exceeds maximum length",
		},
		{
			name:    "prompt with null byte",
			prompt:  "test\x00null",
			wantErr: true,
			errMsg:  "invalid control character",
		},
		{
			name:    "prompt with other control chars",
			prompt:  "test\x01\x02\x03",
			wantErr: true,
			errMsg:  "invalid control character",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidatePrompt(tt.prompt)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidatePrompt() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("ValidatePrompt() error = %v, want error containing %q", err, tt.errMsg)
			}
		})
	}
}

func TestValidatorValidateClaudeOptions(t *testing.T) {
	v := NewValidator()

	// Create temp dir for path validation
	tempDir, err := os.MkdirTemp("", "claude_opts_*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	validDir := filepath.Join(tempDir, "valid")
	os.Mkdir(validDir, 0o755)

	tests := []struct {
		name    string
		opts    map[string]interface{}
		wantErr bool
		errMsg  string
	}{
		{
			name:    "nil options",
			opts:    nil,
			wantErr: false,
		},
		{
			name:    "empty options",
			opts:    map[string]interface{}{},
			wantErr: false,
		},
		{
			name: "valid permission mode",
			opts: map[string]interface{}{
				"permission_mode": "auto",
			},
			wantErr: false,
		},
		{
			name: "invalid permission mode",
			opts: map[string]interface{}{
				"permission_mode": "invalid",
			},
			wantErr: true,
			errMsg:  "invalid permission_mode",
		},
		{
			name: "valid tool lists",
			opts: map[string]interface{}{
				"allowed_tools":    []interface{}{"tool1", "tool2"},
				"disallowed_tools": []interface{}{"tool3"},
			},
			wantErr: false,
		},
		{
			name: "invalid tool list type",
			opts: map[string]interface{}{
				"allowed_tools": "not an array",
			},
			wantErr: true,
			errMsg:  "must be an array",
		},
		{
			name: "invalid tool list element",
			opts: map[string]interface{}{
				"allowed_tools": []interface{}{"tool1", 123},
			},
			wantErr: true,
			errMsg:  "must be a string",
		},
		{
			name: "valid add_dirs",
			opts: map[string]interface{}{
				"add_dirs": []interface{}{validDir},
			},
			wantErr: false,
		},
		{
			name: "invalid add_dirs path",
			opts: map[string]interface{}{
				"add_dirs": []interface{}{"../relative/path"},
			},
			wantErr: true,
			errMsg:  "add_dirs",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := v.ValidateClaudeOptions(tt.opts)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateClaudeOptions() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err != nil && tt.errMsg != "" && !strings.Contains(err.Error(), tt.errMsg) {
				t.Errorf("ValidateClaudeOptions() error = %v, want error containing %q", err, tt.errMsg)
			}
		})
	}
}

func TestValidatorSanitizePath(t *testing.T) {
	v := NewValidator()

	tests := []struct {
		name string
		path string
		want string
	}{
		{
			name: "clean path",
			path: "/home/user/project",
			want: "/home/user/project",
		},
		{
			name: "path with ..",
			path: "/home/user/../project",
			want: "/home/user/_/project", // .. gets replaced with _/
		},
		{
			name: "path with invalid chars",
			path: "/home/user<>project",
			want: "/home/user__project",
		},
		{
			name: "path with null bytes",
			path: "/home/user\x00project",
			want: "/home/user_project",
		},
		{
			name: "multiple issues",
			path: "/home/../user<>/../project",
			want: "/home/_/user__/_/project", // .. -> _/, <> -> __
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := v.SanitizePath(tt.path)
			if got != tt.want {
				t.Errorf("SanitizePath() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestValidatorSanitizeString(t *testing.T) {
	v := NewValidator()

	tests := []struct {
		name      string
		input     string
		maxLength int
		want      string
	}{
		{
			name:      "clean string",
			input:     "Hello, World!",
			maxLength: 100,
			want:      "Hello, World!",
		},
		{
			name:      "string with null bytes",
			input:     "Hello\x00World",
			maxLength: 100,
			want:      "HelloWorld",
		},
		{
			name:      "string with control chars",
			input:     "Hello\x01\x02World",
			maxLength: 100,
			want:      "HelloWorld",
		},
		{
			name:      "string with allowed whitespace",
			input:     "Hello\n\r\tWorld",
			maxLength: 100,
			want:      "Hello\n\r\tWorld",
		},
		{
			name:      "truncate long string",
			input:     "This is a very long string",
			maxLength: 10,
			want:      "This is a ",
		},
		{
			name:      "empty string",
			input:     "",
			maxLength: 100,
			want:      "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := v.SanitizeString(tt.input, tt.maxLength)
			if got != tt.want {
				t.Errorf("SanitizeString() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestValidatorValidateIDs(t *testing.T) {
	v := NewValidator()

	t.Run("ValidateProjectID", func(t *testing.T) {
		tests := []struct {
			name    string
			id      string
			wantErr bool
		}{
			{
				name:    "valid UUID",
				id:      "123e4567-e89b-12d3-a456-426614174000",
				wantErr: false,
			},
			{
				name:    "empty ID",
				id:      "",
				wantErr: true,
			},
			{
				name:    "invalid format",
				id:      "not-a-uuid",
				wantErr: true,
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				err := v.ValidateProjectID(tt.id)
				if (err != nil) != tt.wantErr {
					t.Errorf("ValidateProjectID() error = %v, wantErr %v", err, tt.wantErr)
				}
			})
		}
	})

	t.Run("ValidateSessionID", func(t *testing.T) {
		tests := []struct {
			name    string
			id      string
			wantErr bool
		}{
			{
				name:    "valid session ID",
				id:      "session-1234567890abcdef",
				wantErr: false,
			},
			{
				name:    "empty ID",
				id:      "",
				wantErr: true,
			},
			{
				name:    "too short",
				id:      "short",
				wantErr: true,
			},
			{
				name:    "too long",
				id:      strings.Repeat("a", 129),
				wantErr: true,
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				err := v.ValidateSessionID(tt.id)
				if (err != nil) != tt.wantErr {
					t.Errorf("ValidateSessionID() error = %v, wantErr %v", err, tt.wantErr)
				}
			})
		}
	})
}

// Benchmarks
func BenchmarkValidatePath(b *testing.B) {
	v := NewValidator()
	tempDir, _ := os.MkdirTemp("", "bench_*")
	defer os.RemoveAll(tempDir)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		v.ValidatePath(tempDir)
	}
}

func BenchmarkValidateProjectNesting(b *testing.B) {
	v := NewValidator()
	existing := make([]string, 100)
	for i := 0; i < 100; i++ {
		existing[i] = filepath.Join("/home/user/projects", string(rune(i)))
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		v.ValidateProjectNesting("/home/user/newproject", existing)
	}
}

func BenchmarkSanitizeString(b *testing.B) {
	v := NewValidator()
	input := "This is a test string with\x00null\x01bytes\x02and\nnewlines"

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		v.SanitizeString(input, 1000)
	}
}

func BenchmarkValidateJSON(b *testing.B) {
	v := NewValidator()
	data := []byte(`{"key": "value", "nested": {"array": [1, 2, 3]}}`)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		v.ValidateJSON(data)
	}
}
