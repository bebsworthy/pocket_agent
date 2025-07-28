package websocket

import (
	"testing"
	"time"
)

func TestRateLimiter(t *testing.T) {
	rl := NewRateLimiter(3, 100*time.Millisecond)
	defer rl.Stop()

	key := "test-key"

	// Should allow first 3 requests
	for i := 0; i < 3; i++ {
		if !rl.Allow(key) {
			t.Errorf("Request %d should be allowed", i+1)
		}
	}

	// 4th request should be denied
	if rl.Allow(key) {
		t.Error("4th request should be denied")
	}

	// Wait for window to reset
	time.Sleep(110 * time.Millisecond)

	// Should allow requests again
	if !rl.Allow(key) {
		t.Error("Request should be allowed after window reset")
	}
}

func TestRateLimiterMultipleKeys(t *testing.T) {
	rl := NewRateLimiter(2, 100*time.Millisecond)
	defer rl.Stop()

	key1 := "key1"
	key2 := "key2"

	// Each key should have its own limit
	for i := 0; i < 2; i++ {
		if !rl.Allow(key1) {
			t.Errorf("Key1 request %d should be allowed", i+1)
		}
		if !rl.Allow(key2) {
			t.Errorf("Key2 request %d should be allowed", i+1)
		}
	}

	// Both should be rate limited now
	if rl.Allow(key1) {
		t.Error("Key1 should be rate limited")
	}
	if rl.Allow(key2) {
		t.Error("Key2 should be rate limited")
	}
}

func TestRateLimiterCleanup(t *testing.T) {
	rl := NewRateLimiter(1, 50*time.Millisecond)
	defer rl.Stop()

	// Create some buckets
	for i := 0; i < 10; i++ {
		key := string(rune('a' + i))
		rl.Allow(key)
	}

	// Wait for cleanup (2 windows)
	time.Sleep(150 * time.Millisecond)

	// Count remaining buckets
	count := 0
	rl.buckets.Range(func(key, value interface{}) bool {
		count++
		return true
	})

	// Should have cleaned up old buckets
	if count > 0 {
		t.Errorf("Expected 0 buckets after cleanup, got %d", count)
	}
}

func TestRateLimiterConcurrency(t *testing.T) {
	rl := NewRateLimiter(100, 100*time.Millisecond)
	defer rl.Stop()

	// Test concurrent access
	done := make(chan bool)
	for i := 0; i < 10; i++ {
		go func(id int) {
			key := string(rune('a' + id))
			for j := 0; j < 10; j++ {
				rl.Allow(key)
				time.Sleep(time.Millisecond)
			}
			done <- true
		}(i)
	}

	// Wait for all goroutines
	for i := 0; i < 10; i++ {
		<-done
	}

	// No panic means success
}
