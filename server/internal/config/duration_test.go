package config

import (
	"encoding/json"
	"testing"
	"time"
)

func TestDurationUnmarshalJSON(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected time.Duration
		wantErr  bool
	}{
		{
			name:     "string format - seconds",
			input:    `"10s"`,
			expected: 10 * time.Second,
		},
		{
			name:     "string format - minutes",
			input:    `"5m"`,
			expected: 5 * time.Minute,
		},
		{
			name:     "string format - hours",
			input:    `"2h"`,
			expected: 2 * time.Hour,
		},
		{
			name:     "string format - combined",
			input:    `"1h30m"`,
			expected: 90 * time.Minute,
		},
		{
			name:     "integer format - nanoseconds",
			input:    `1000000000`,
			expected: time.Second,
		},
		{
			name:     "invalid string format",
			input:    `"invalid"`,
			wantErr:  true,
		},
		{
			name:     "invalid type",
			input:    `true`,
			wantErr:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var d Duration
			err := json.Unmarshal([]byte(tt.input), &d)
			
			if tt.wantErr {
				if err == nil {
					t.Errorf("expected error but got none")
				}
				return
			}
			
			if err != nil {
				t.Errorf("unexpected error: %v", err)
				return
			}
			
			if d.Get() != tt.expected {
				t.Errorf("expected %v, got %v", tt.expected, d.Get())
			}
		})
	}
}

func TestDurationMarshalJSON(t *testing.T) {
	tests := []struct {
		name     string
		duration Duration
		expected string
	}{
		{
			name:     "seconds",
			duration: Duration{10 * time.Second},
			expected: `"10s"`,
		},
		{
			name:     "minutes",
			duration: Duration{5 * time.Minute},
			expected: `"5m0s"`,
		},
		{
			name:     "hours",
			duration: Duration{2 * time.Hour},
			expected: `"2h0m0s"`,
		},
		{
			name:     "zero",
			duration: Duration{0},
			expected: `"0s"`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			data, err := json.Marshal(tt.duration)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			
			if string(data) != tt.expected {
				t.Errorf("expected %s, got %s", tt.expected, string(data))
			}
		})
	}
}

func TestConfigWithDurationFields(t *testing.T) {
	jsonData := `{
		"port": 8443,
		"host": "0.0.0.0",
		"websocket": {
			"read_timeout": "10m",
			"write_timeout": "10s",
			"ping_interval": "5m",
			"pong_timeout": "30s",
			"max_message_size": 1048576
		},
		"execution": {
			"command_timeout": "5m",
			"max_projects": 100
		}
	}`

	var cfg Config
	err := json.Unmarshal([]byte(jsonData), &cfg)
	if err != nil {
		t.Fatalf("failed to unmarshal config: %v", err)
	}

	// Verify WebSocket durations
	if cfg.WebSocket.ReadTimeout.Get() != 10*time.Minute {
		t.Errorf("expected read timeout 10m, got %v", cfg.WebSocket.ReadTimeout.Get())
	}
	if cfg.WebSocket.WriteTimeout.Get() != 10*time.Second {
		t.Errorf("expected write timeout 10s, got %v", cfg.WebSocket.WriteTimeout.Get())
	}
	if cfg.WebSocket.PingInterval.Get() != 5*time.Minute {
		t.Errorf("expected ping interval 5m, got %v", cfg.WebSocket.PingInterval.Get())
	}
	if cfg.WebSocket.PongTimeout.Get() != 30*time.Second {
		t.Errorf("expected pong timeout 30s, got %v", cfg.WebSocket.PongTimeout.Get())
	}

	// Verify Execution duration
	if cfg.Execution.CommandTimeout.Get() != 5*time.Minute {
		t.Errorf("expected command timeout 5m, got %v", cfg.Execution.CommandTimeout.Get())
	}
}