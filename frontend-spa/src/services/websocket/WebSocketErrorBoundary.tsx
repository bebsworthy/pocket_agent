/**
 * Error Boundary for WebSocket-related errors
 * 
 * Catches and handles unhandled errors from WebSocket operations,
 * preventing them from crashing the entire component tree.
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class WebSocketErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    // Update state so the next render will show the fallback UI
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log the error for debugging
    console.error('WebSocket Error Boundary caught an error:', error, errorInfo);
    
    // Call custom error handler if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
  }

  render() {
    if (this.state.hasError) {
      // Render fallback UI
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="websocket-error-boundary">
          <h2>WebSocket Connection Error</h2>
          <p>
            There was an error with the WebSocket connection. Please try refreshing the page.
          </p>
          <details style={{ whiteSpace: 'pre-wrap' }}>
            <summary>Error Details</summary>
            {this.state.error?.message}
          </details>
          <button 
            onClick={() => this.setState({ hasError: false, error: undefined })}
            style={{ 
              marginTop: '1rem',
              padding: '0.5rem 1rem',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Try Again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * Hook to provide error boundary context for WebSocket errors
 */
export function useWebSocketErrorHandler() {
  const handleError = (error: Error, context?: string) => {
    console.error(`WebSocket Error${context ? ` (${context})` : ''}:`, error);
    
    // You could integrate with error reporting services here
    // Example: Sentry.captureException(error, { tags: { context: 'websocket' } });
    
    // For development, throw the error to trigger error boundary
    if (process.env.NODE_ENV === 'development') {
      throw error;
    }
  };

  return { handleError };
}

/**
 * Higher-order component to wrap components with WebSocket error boundary
 */
export function withWebSocketErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  fallback?: ReactNode
) {
  const WrappedComponent = (props: P) => (
    <WebSocketErrorBoundary fallback={fallback}>
      <Component {...props} />
    </WebSocketErrorBoundary>
  );

  WrappedComponent.displayName = `withWebSocketErrorBoundary(${Component.displayName || Component.name})`;
  
  return WrappedComponent;
}