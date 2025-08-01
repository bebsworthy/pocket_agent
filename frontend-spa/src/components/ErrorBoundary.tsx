/**
 * ErrorBoundary component for production error handling
 * Provides mobile-optimized error UI with recovery options
 */

import { Component } from 'react';
import type { ReactNode, ErrorInfo } from 'react';
import { Button } from './ui/atoms/Button';
import { Card } from './ui/molecules/Card';
import { RefreshCw, AlertTriangle, Home } from 'lucide-react';

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: (error: Error, resetError: () => void) => ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  isolate?: boolean; // If true, only catches errors in immediate children
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorId: string | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private retryCount = 0;
  private readonly maxRetries = 3;

  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    // Generate unique error ID for logging/debugging
    const errorId = `ERR_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

    return {
      hasError: true,
      error,
      errorId,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log error details for debugging (not exposed to user)
    console.error('ErrorBoundary caught an error:', {
      error: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      errorId: this.state.errorId,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
    });

    this.setState({ errorInfo });

    // Call optional error callback
    this.props.onError?.(error, errorInfo);

    // Report to error tracking service in production
    if (process.env.NODE_ENV === 'production') {
      this.reportError(error, errorInfo);
    }
  }

  private reportError = (error: Error, errorInfo: ErrorInfo) => {
    // In a real app, you would send this to an error tracking service
    // like Sentry, LogRocket, or Bugsnag
    try {
      // Example implementation - replace with your error tracking service
      console.warn('Error reporting would be implemented here:', {
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        errorId: this.state.errorId,
        buildVersion: process.env.VITE_APP_VERSION || 'unknown',
      });
    } catch (reportingError) {
      console.error('Failed to report error:', reportingError);
    }
  };

  private handleRetry = () => {
    if (this.retryCount < this.maxRetries) {
      this.retryCount++;
      this.resetError();
    }
  };

  private resetError = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: null,
    });
  };

  private handleGoHome = () => {
    this.resetError();
    window.location.href = '/';
  };

  private handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      // Use custom fallback if provided
      if (this.props.fallback && this.state.error) {
        return this.props.fallback(this.state.error, this.resetError);
      }

      // Default mobile-optimized error UI
      const canRetry = this.retryCount < this.maxRetries;
      const isDevelopment = process.env.NODE_ENV === 'development';

      return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
          <Card className="w-full max-w-sm">
            <div className="space-y-4 p-6 text-center">
              {/* Error Icon */}
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/20">
                <AlertTriangle className="h-8 w-8 text-red-600 dark:text-red-400" />
              </div>

              {/* Error Title */}
              <div className="space-y-2">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                  Something went wrong
                </h2>
                <p className="text-sm text-gray-600 dark:text-gray-400">
                  We're sorry, but something unexpected happened. Please try again.
                </p>
              </div>

              {/* Development Error Details */}
              {isDevelopment && this.state.error && (
                <details className="rounded bg-gray-100 p-3 text-left text-xs dark:bg-gray-800">
                  <summary className="mb-2 cursor-pointer font-medium text-gray-700 dark:text-gray-300">
                    Error Details (Development)
                  </summary>
                  <div className="space-y-2 text-gray-600 dark:text-gray-400">
                    <div>
                      <strong>Error:</strong> {this.state.error.message}
                    </div>
                    <div>
                      <strong>Error ID:</strong> {this.state.errorId}
                    </div>
                    {this.state.error.stack && (
                      <div>
                        <strong>Stack:</strong>
                        <pre className="mt-1 overflow-x-auto whitespace-pre-wrap text-xs">
                          {this.state.error.stack}
                        </pre>
                      </div>
                    )}
                  </div>
                </details>
              )}

              {/* Error ID for User */}
              {this.state.errorId && (
                <div className="rounded bg-gray-100 p-2 text-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400">
                  Error ID: {this.state.errorId}
                </div>
              )}

              {/* Action Buttons */}
              <div className="space-y-3">
                {canRetry && (
                  <Button
                    variant="primary"
                    fullWidth
                    onPress={this.handleRetry}
                    ariaLabel="Try again"
                  >
                    <RefreshCw className="mr-2 h-4 w-4" />
                    Try Again ({this.maxRetries - this.retryCount} attempts left)
                  </Button>
                )}

                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    fullWidth
                    onPress={this.handleGoHome}
                    ariaLabel="Go to homepage"
                  >
                    <Home className="mr-2 h-4 w-4" />
                    Go Home
                  </Button>
                  <Button
                    variant="ghost"
                    fullWidth
                    onPress={this.handleReload}
                    ariaLabel="Refresh page"
                  >
                    <RefreshCw className="mr-2 h-4 w-4" />
                    Refresh
                  </Button>
                </div>
              </div>

              {/* Support Information */}
              <div className="border-t border-gray-200 pt-2 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
                <p>If this problem persists, please contact support.</p>
                {this.state.errorId && (
                  <p className="mt-1">Include Error ID: {this.state.errorId}</p>
                )}
              </div>
            </div>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}

// HOC for wrapping components with error boundary
// eslint-disable-next-line react-refresh/only-export-components
export function withErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  errorBoundaryProps?: Omit<ErrorBoundaryProps, 'children'>
) {
  const WrappedComponent = (props: P) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;
  return WrappedComponent;
}

// Hook for triggering error boundary from child components
// eslint-disable-next-line react-refresh/only-export-components
export function useErrorBoundary() {
  return (error: Error) => {
    throw error;
  };
}

// Async error boundary for handling promise rejections
export class AsyncErrorBoundary extends ErrorBoundary {
  componentDidMount() {
    // Handle unhandled promise rejections
    window.addEventListener('unhandledrejection', this.handleUnhandledRejection);
  }

  componentWillUnmount() {
    window.removeEventListener('unhandledrejection', this.handleUnhandledRejection);
  }

  private handleUnhandledRejection = (event: PromiseRejectionEvent) => {
    // Only handle if this boundary hasn't already caught an error
    if (!this.state.hasError) {
      const error = new Error(event.reason?.message || 'Unhandled promise rejection');
      error.stack = event.reason?.stack || error.stack;

      this.componentDidCatch(error, {
        componentStack: 'AsyncErrorBoundary - Unhandled Promise Rejection',
      });

      event.preventDefault(); // Prevent default browser error handling
    }
  };
}

export default ErrorBoundary;
