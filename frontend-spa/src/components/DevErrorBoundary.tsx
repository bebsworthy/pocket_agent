/**
 * DevErrorBoundary - Development-focused error boundary with enhanced debugging
 * Provides detailed error information, component stack traces, and debugging helpers
 */
import React from 'react';

interface Props {
  children: React.ReactNode;
  componentName?: string; // Optional component name for better debugging
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: React.ErrorInfo;
  errorId: string;
  timestamp: string;
}

export class DevErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { 
      hasError: false,
      errorId: '',
      timestamp: '',
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    const errorId = `DEV_ERR_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const timestamp = new Date().toISOString();
    
    return { 
      hasError: true, 
      error,
      errorId,
      timestamp,
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    const context = this.props.componentName || 'Unknown Component';
    
    console.group(`🚨 DevErrorBoundary: Error in ${context}`);
    console.error('Error:', error);
    console.error('Error message:', error.message);
    console.error('Error stack:', error.stack);
    console.error('Component stack:', errorInfo.componentStack);
    console.error('Error ID:', this.state.errorId);
    console.error('Timestamp:', this.state.timestamp);
    console.error('Props passed to boundary:', this.props);
    console.error('Browser info:', {
      userAgent: navigator.userAgent,
      url: window.location.href,
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
    });
    console.groupEnd();
    
    // Additional debugging for React rendering errors
    if (error.message.includes('Objects are not valid as a React child')) {
      console.group('🔍 React Child Error Analysis');
      console.warn('This error typically occurs when:');
      console.warn('1. An object is passed as a child instead of a React element');
      console.warn('2. A component returns an object instead of JSX');
      console.warn('3. Props contain objects that should be rendered differently');
      console.warn('Check the component stack above to identify the problematic component');
      console.groupEnd();
    }
    
    this.setState({
      error,
      errorInfo,
    });
  }

  private handleRetry = () => {
    this.setState({ 
      hasError: false, 
      error: undefined, 
      errorInfo: undefined,
      errorId: '',
      timestamp: '',
    });
  };

  private copyErrorToClipboard = async () => {
    const errorReport = {
      errorId: this.state.errorId,
      timestamp: this.state.timestamp,
      componentName: this.props.componentName || 'Unknown',
      error: {
        message: this.state.error?.message,
        stack: this.state.error?.stack,
      },
      componentStack: this.state.errorInfo?.componentStack,
      location: window.location.href,
      userAgent: navigator.userAgent,
    };

    try {
      await navigator.clipboard.writeText(JSON.stringify(errorReport, null, 2));
      alert('Error report copied to clipboard!');
    } catch (err) {
      console.error('Failed to copy to clipboard:', err);
      // Fallback: log the report
      console.log('Error Report (copy manually):', errorReport);
    }
  };

  render() {
    if (this.state.hasError && this.state.error) {
      return (
        <div 
          style={{ 
            padding: '20px', 
            margin: '20px',
            background: '#fee2e2', 
            border: '2px solid #fca5a5',
            borderRadius: '8px',
            fontFamily: 'monospace',
            fontSize: '14px',
            maxHeight: '80vh',
            overflow: 'auto',
          }}
        >
          <div style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '24px' }}>🚨</span>
            <h2 style={{ margin: 0, color: '#dc2626', fontSize: '18px' }}>
              Development Error Boundary
            </h2>
          </div>

          <div style={{ marginBottom: '16px' }}>
            <strong>Component:</strong> {this.props.componentName || 'Unknown Component'}
            <br />
            <strong>Error ID:</strong> <code>{this.state.errorId}</code>
            <br />
            <strong>Timestamp:</strong> <code>{this.state.timestamp}</code>
          </div>

          <details 
            open
            style={{ 
              marginBottom: '16px',
              background: '#fef2f2',
              padding: '12px',
              borderRadius: '4px',
              border: '1px solid #fecaca',
            }}
          >
            <summary style={{ 
              cursor: 'pointer', 
              fontWeight: 'bold',
              color: '#991b1b',
              marginBottom: '12px',
            }}>
              🐛 Error Details
            </summary>
            
            <div style={{ marginBottom: '12px' }}>
              <strong>Message:</strong>
              <div style={{ 
                background: '#ffffff', 
                padding: '8px', 
                borderRadius: '4px',
                color: '#dc2626',
                fontWeight: 'bold',
              }}>
                {this.state.error.message}
              </div>
            </div>

            {this.state.error.stack && (
              <div style={{ marginBottom: '12px' }}>
                <strong>Stack Trace:</strong>
                <pre style={{ 
                  background: '#ffffff', 
                  padding: '8px', 
                  borderRadius: '4px',
                  overflow: 'auto',
                  fontSize: '12px',
                  color: '#374151',
                  border: '1px solid #d1d5db',
                }}>
                  {this.state.error.stack}
                </pre>
              </div>
            )}
          </details>

          {this.state.errorInfo && (
            <details 
              style={{ 
                marginBottom: '16px',
                background: '#eff6ff',
                padding: '12px',
                borderRadius: '4px',
                border: '1px solid #bfdbfe',
              }}
            >
              <summary style={{ 
                cursor: 'pointer', 
                fontWeight: 'bold',
                color: '#1d4ed8',
                marginBottom: '12px',
              }}>
                🧩 Component Stack
              </summary>
              <pre style={{ 
                background: '#ffffff', 
                padding: '8px', 
                borderRadius: '4px',
                overflow: 'auto',
                fontSize: '12px',
                color: '#374151',
                border: '1px solid #d1d5db',
              }}>
                {this.state.errorInfo.componentStack}
              </pre>
            </details>
          )}

          <details 
            style={{ 
              marginBottom: '16px',
              background: '#f0fdf4',
              padding: '12px',
              borderRadius: '4px',
              border: '1px solid #bbf7d0',
            }}
          >
            <summary style={{ 
              cursor: 'pointer', 
              fontWeight: 'bold',
              color: '#166534',
              marginBottom: '12px',
            }}>
              💡 Debugging Tips
            </summary>
            <ul style={{ margin: 0, paddingLeft: '20px', color: '#166534' }}>
              <li>Check the Component Stack above to identify the exact component causing the error</li>
              <li>Look at the Stack Trace to see the specific line numbers in your source code</li>
              <li>Open browser DevTools → Sources to set breakpoints and debug</li>
              <li>Check console for additional warnings or logs</li>
              {this.state.error.message.includes('Objects are not valid as a React child') && (
                <>
                  <li><strong>React Child Error:</strong> You're likely passing an object where JSX is expected</li>
                  <li>Check if you're accidentally rendering an object instead of its properties</li>
                  <li>Ensure all components return valid JSX or React elements</li>
                </>
              )}
            </ul>
          </details>

          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <button 
              onClick={this.handleRetry}
              style={{
                padding: '8px 16px',
                background: '#dc2626',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
              }}
            >
              🔄 Try Again
            </button>
            
            <button 
              onClick={this.copyErrorToClipboard}
              style={{
                padding: '8px 16px',
                background: '#2563eb',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
              }}
            >
              📋 Copy Error Report
            </button>
            
            <button 
              onClick={() => window.location.reload()}
              style={{
                padding: '8px 16px',
                background: '#6b7280',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
              }}
            >
              🔄 Reload Page
            </button>
          </div>

          <div style={{ 
            marginTop: '16px', 
            padding: '12px',
            background: '#f9fafb',
            borderRadius: '4px',
            border: '1px solid #e5e7eb',
            fontSize: '12px',
            color: '#6b7280',
          }}>
            <strong>Note:</strong> This detailed error boundary only appears in development mode.
            In production, users will see a simplified error message.
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}