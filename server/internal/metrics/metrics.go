package metrics

import (
	"sync"
	"sync/atomic"
	"time"
)

// Collector collects and aggregates metrics
type Collector struct {
	// Counters
	totalExecutions  uint64
	totalMessages    uint64
	totalConnections uint64
	totalErrors      uint64

	// Gauges
	activeConnections int64
	activeExecutions  int64
	activeProjects    int64

	// Histograms
	executionDurations *DurationHistogram
	messageThroughput  *ThroughputCounter

	// Resource metrics
	memoryUsage    uint64
	goroutineCount int32
	cpuPercent     float64

	mu sync.RWMutex
}

// DurationHistogram tracks duration distributions
type DurationHistogram struct {
	mu        sync.Mutex
	durations []time.Duration
	maxSize   int
}

// ThroughputCounter tracks throughput over time windows
type ThroughputCounter struct {
	mu      sync.Mutex
	windows map[time.Time]uint64
	window  time.Duration
}

// NewCollector creates a new metrics collector
func NewCollector() *Collector {
	return &Collector{
		executionDurations: NewDurationHistogram(1000),
		messageThroughput:  NewThroughputCounter(time.Minute),
	}
}

// NewDurationHistogram creates a new duration histogram
func NewDurationHistogram(maxSize int) *DurationHistogram {
	return &DurationHistogram{
		durations: make([]time.Duration, 0, maxSize),
		maxSize:   maxSize,
	}
}

// NewThroughputCounter creates a new throughput counter
func NewThroughputCounter(window time.Duration) *ThroughputCounter {
	return &ThroughputCounter{
		windows: make(map[time.Time]uint64),
		window:  window,
	}
}

// IncrementExecutions increments the execution counter
func (c *Collector) IncrementExecutions() {
	atomic.AddUint64(&c.totalExecutions, 1)
	atomic.AddInt64(&c.activeExecutions, 1)
}

// DecrementExecutions decrements the active execution counter
func (c *Collector) DecrementExecutions() {
	atomic.AddInt64(&c.activeExecutions, -1)
}

// IncrementMessages increments the message counter
func (c *Collector) IncrementMessages() {
	atomic.AddUint64(&c.totalMessages, 1)
	c.messageThroughput.Increment()
}

// IncrementConnections increments the connection counters
func (c *Collector) IncrementConnections() {
	atomic.AddUint64(&c.totalConnections, 1)
	atomic.AddInt64(&c.activeConnections, 1)
}

// DecrementConnections decrements the active connection counter
func (c *Collector) DecrementConnections() {
	atomic.AddInt64(&c.activeConnections, -1)
}

// IncrementErrors increments the error counter
func (c *Collector) IncrementErrors() {
	atomic.AddUint64(&c.totalErrors, 1)
}

// SetActiveProjects sets the active project count
func (c *Collector) SetActiveProjects(count int64) {
	atomic.StoreInt64(&c.activeProjects, count)
}

// RecordExecutionDuration records an execution duration
func (c *Collector) RecordExecutionDuration(duration time.Duration) {
	c.executionDurations.Record(duration)
}

// UpdateResourceMetrics updates resource usage metrics
func (c *Collector) UpdateResourceMetrics(memoryMB uint64, goroutines int, cpuPercent float64) {
	atomic.StoreUint64(&c.memoryUsage, memoryMB)
	atomic.StoreInt32(&c.goroutineCount, int32(goroutines))

	c.mu.Lock()
	c.cpuPercent = cpuPercent
	c.mu.Unlock()
}

// GetSnapshot returns a snapshot of all metrics
func (c *Collector) GetSnapshot() Snapshot {
	c.mu.RLock()
	cpu := c.cpuPercent
	c.mu.RUnlock()

	return Snapshot{
		Counters: CounterSnapshot{
			TotalExecutions:  atomic.LoadUint64(&c.totalExecutions),
			TotalMessages:    atomic.LoadUint64(&c.totalMessages),
			TotalConnections: atomic.LoadUint64(&c.totalConnections),
			TotalErrors:      atomic.LoadUint64(&c.totalErrors),
		},
		Gauges: GaugeSnapshot{
			ActiveConnections: atomic.LoadInt64(&c.activeConnections),
			ActiveExecutions:  atomic.LoadInt64(&c.activeExecutions),
			ActiveProjects:    atomic.LoadInt64(&c.activeProjects),
		},
		Resources: ResourceSnapshot{
			MemoryMB:       atomic.LoadUint64(&c.memoryUsage),
			GoroutineCount: atomic.LoadInt32(&c.goroutineCount),
			CPUPercent:     cpu,
		},
		Performance: PerformanceSnapshot{
			ExecutionDurations: c.executionDurations.GetPercentiles(),
			MessageThroughput:  c.messageThroughput.GetRate(),
		},
	}
}

// Record adds a duration to the histogram
func (h *DurationHistogram) Record(duration time.Duration) {
	h.mu.Lock()
	defer h.mu.Unlock()

	h.durations = append(h.durations, duration)
	if len(h.durations) > h.maxSize {
		h.durations = h.durations[1:]
	}
}

// GetPercentiles returns duration percentiles
func (h *DurationHistogram) GetPercentiles() DurationPercentiles {
	h.mu.Lock()
	defer h.mu.Unlock()

	if len(h.durations) == 0 {
		return DurationPercentiles{}
	}

	// Create a copy for sorting
	sorted := make([]time.Duration, len(h.durations))
	copy(sorted, h.durations)

	// Simple bubble sort for percentile calculation
	for i := 0; i < len(sorted); i++ {
		for j := i + 1; j < len(sorted); j++ {
			if sorted[i] > sorted[j] {
				sorted[i], sorted[j] = sorted[j], sorted[i]
			}
		}
	}

	return DurationPercentiles{
		P50: sorted[len(sorted)/2],
		P90: sorted[len(sorted)*9/10],
		P99: sorted[len(sorted)*99/100],
		Min: sorted[0],
		Max: sorted[len(sorted)-1],
	}
}

// Increment increments the current window counter
func (t *ThroughputCounter) Increment() {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now().Truncate(time.Second)
	t.windows[now]++

	// Clean old windows
	cutoff := now.Add(-t.window)
	for ts := range t.windows {
		if ts.Before(cutoff) {
			delete(t.windows, ts)
		}
	}
}

// GetRate returns messages per second rate
func (t *ThroughputCounter) GetRate() float64 {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	cutoff := now.Add(-t.window)

	var total uint64
	var windows int

	for ts, count := range t.windows {
		if ts.After(cutoff) {
			total += count
			windows++
		}
	}

	if windows == 0 {
		return 0
	}

	return float64(total) / t.window.Seconds()
}

// Snapshot represents a point-in-time metrics snapshot
type Snapshot struct {
	Counters    CounterSnapshot
	Gauges      GaugeSnapshot
	Resources   ResourceSnapshot
	Performance PerformanceSnapshot
	Timestamp   time.Time
}

// CounterSnapshot holds counter metrics
type CounterSnapshot struct {
	TotalExecutions  uint64
	TotalMessages    uint64
	TotalConnections uint64
	TotalErrors      uint64
}

// GaugeSnapshot holds gauge metrics
type GaugeSnapshot struct {
	ActiveConnections int64
	ActiveExecutions  int64
	ActiveProjects    int64
}

// ResourceSnapshot holds resource metrics
type ResourceSnapshot struct {
	MemoryMB       uint64
	GoroutineCount int32
	CPUPercent     float64
}

// PerformanceSnapshot holds performance metrics
type PerformanceSnapshot struct {
	ExecutionDurations DurationPercentiles
	MessageThroughput  float64 // messages per second
}

// DurationPercentiles holds duration percentile data
type DurationPercentiles struct {
	P50 time.Duration
	P90 time.Duration
	P99 time.Duration
	Min time.Duration
	Max time.Duration
}
