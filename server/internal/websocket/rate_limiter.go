package websocket

import (
	"sync"
	"time"
)

// RateLimiter implements a simple token bucket rate limiter
type RateLimiter struct {
	rate     int           // requests per window
	window   time.Duration // time window
	buckets  sync.Map      // map[string]*bucket
	cleaner  *time.Ticker
	stopChan chan struct{}
}

// bucket represents a token bucket for a specific key
type bucket struct {
	tokens    int
	lastReset time.Time
	mu        sync.Mutex
}

// NewRateLimiter creates a new rate limiter
func NewRateLimiter(rate int, window time.Duration) *RateLimiter {
	rl := &RateLimiter{
		rate:     rate,
		window:   window,
		cleaner:  time.NewTicker(window), // Clean up more frequently to prevent memory buildup
		stopChan: make(chan struct{}),
	}

	// Start cleanup goroutine
	go rl.cleanup()

	return rl
}

// Allow checks if a request is allowed for the given key
func (rl *RateLimiter) Allow(key string) bool {
	now := time.Now()

	// Get or create bucket
	val, _ := rl.buckets.LoadOrStore(key, &bucket{
		tokens:    rl.rate,
		lastReset: now,
	})

	b := val.(*bucket)
	b.mu.Lock()
	defer b.mu.Unlock()

	// Reset tokens if window has passed
	if now.Sub(b.lastReset) >= rl.window {
		b.tokens = rl.rate
		b.lastReset = now
	}

	// Check if tokens available
	if b.tokens > 0 {
		b.tokens--
		return true
	}

	return false
}

// cleanup removes old buckets to prevent memory leak
func (rl *RateLimiter) cleanup() {
	for {
		select {
		case <-rl.cleaner.C:
			now := time.Now()
			expiredKeys := make([]interface{}, 0)

			// First pass: identify expired buckets
			rl.buckets.Range(func(key, value interface{}) bool {
				b := value.(*bucket)
				b.mu.Lock()
				// Remove buckets that haven't been used in 2 windows
				if now.Sub(b.lastReset) > rl.window*2 {
					expiredKeys = append(expiredKeys, key)
				}
				b.mu.Unlock()
				return true
			})

			// Second pass: delete expired buckets without holding locks
			for _, key := range expiredKeys {
				rl.buckets.Delete(key)
			}

		case <-rl.stopChan:
			rl.cleaner.Stop()
			return
		}
	}
}

// Stop stops the rate limiter cleanup
func (rl *RateLimiter) Stop() {
	close(rl.stopChan)
}
