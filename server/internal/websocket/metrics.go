package websocket

// MetricsProvider is an interface for external metrics collection
type MetricsProvider interface {
	IncrementConnections()
	DecrementConnections()
	IncrementMessages()
	IncrementErrors()
}