package config

import (
	"encoding/json"
	"fmt"
	"time"
)

// Duration is a wrapper around time.Duration that supports JSON marshaling/unmarshaling
// from/to string format (e.g., "10s", "5m", "1h")
type Duration struct {
	time.Duration
}

// UnmarshalJSON implements json.Unmarshaler interface
func (d *Duration) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		// Try to unmarshal as integer (nanoseconds)
		var i int64
		if err := json.Unmarshal(b, &i); err != nil {
			return fmt.Errorf("duration should be a string or integer: %w", err)
		}
		d.Duration = time.Duration(i)
		return nil
	}

	duration, err := time.ParseDuration(s)
	if err != nil {
		return fmt.Errorf("invalid duration format %q: %w", s, err)
	}

	d.Duration = duration
	return nil
}

// MarshalJSON implements json.Marshaler interface
func (d Duration) MarshalJSON() ([]byte, error) {
	return json.Marshal(d.Duration.String())
}

// Get returns the underlying time.Duration value
func (d Duration) Get() time.Duration {
	return d.Duration
}
