import React, { useState, useCallback, useEffect } from 'react';
import { Card, CardHeader, CardContent, CardFooter } from '../molecules/Card';
import { Button } from '../atoms/Button';
import { Input } from '../atoms/Input';
import { IconButton } from '../atoms/IconButton';
import { Server } from '../../../types/models';
import { X, Globe, AlertCircle } from 'lucide-react';

export interface ServerFormProps {
  onSubmit: (server: Omit<Server, 'id' | 'isConnected'>) => void;
  onCancel: () => void;
  initialValues?: Partial<Server>;
  isEditing?: boolean;
}

interface FormData {
  name: string;
  websocketUrl: string;
}

interface FormErrors {
  name?: string;
  websocketUrl?: string;
}

/**
 * ServerForm - An organism component for adding/editing server configurations.
 * Includes validation, mobile-optimized input handling, and error states.
 */
export function ServerForm({
  onSubmit,
  onCancel,
  initialValues = {},
  isEditing = false,
}: ServerFormProps) {
  const [formData, setFormData] = useState<FormData>({
    name: initialValues.name || '',
    websocketUrl: initialValues.websocketUrl || '',
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTestingConnection, setIsTestingConnection] = useState(false);
  const [connectionTestResult, setConnectionTestResult] = useState<{
    success: boolean;
    message: string;
  } | null>(null);

  // Handle escape key for modal dismissal
  useEffect(() => {
    const handleEscapeKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel();
      }
    };

    document.addEventListener('keydown', handleEscapeKey);
    return () => document.removeEventListener('keydown', handleEscapeKey);
  }, [onCancel]);

  // Validate WebSocket URL format (enhanced to support IPv6)
  const validateWebSocketUrl = (url: string): boolean => {
    if (!url.trim()) return false;

    try {
      // Allow ws://, wss://, or plain host:port format
      if (url.startsWith('ws://') || url.startsWith('wss://')) {
        new URL(url);
        return true;
      }

      // For plain host:port, validate format including IPv6 support
      const trimmedUrl = url.trim();

      // IPv6 address format: [::1]:8080, [2001:db8::1]:8080, etc.
      const ipv6HostPortRegex = /^\[[0-9a-fA-F:]+\]:\d+$/;
      if (ipv6HostPortRegex.test(trimmedUrl)) {
        return true;
      }

      // IPv4/hostname format: localhost:8080, 192.168.1.1:8080, example.com:8080
      const hostPortRegex = /^[a-zA-Z0-9.-]+:\d+$/;
      return hostPortRegex.test(trimmedUrl);
    } catch {
      return false;
    }
  };

  // Validate form fields
  const validateForm = useCallback((): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'Server name is required';
    } else if (formData.name.trim().length < 2) {
      newErrors.name = 'Server name must be at least 2 characters';
    }

    if (!formData.websocketUrl.trim()) {
      newErrors.websocketUrl = 'WebSocket URL is required';
    } else if (!validateWebSocketUrl(formData.websocketUrl)) {
      const url = formData.websocketUrl.trim();
      if (url.includes('://') && !url.startsWith('ws://') && !url.startsWith('wss://')) {
        newErrors.websocketUrl = 'WebSocket URLs must start with ws:// or wss://';
      } else {
        newErrors.websocketUrl = 'Enter a valid format: localhost:8080, ws://server.com:8080, or wss://secure.server.com:8080';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  // Auto-infer server name from URL
  const inferServerNameFromUrl = useCallback((url: string): string => {
    if (!url.trim()) return '';
    
    try {
      let hostname = '';
      
      // Handle full URLs with protocol
      if (url.includes('://')) {
        const parsedUrl = new URL(url);
        hostname = parsedUrl.hostname;
      } else {
        // Handle host:port format
        const hostPart = url.split(':')[0];
        hostname = hostPart;
      }
      
      // Convert hostname to friendly name
      if (hostname === 'localhost' || hostname === '127.0.0.1') {
        return 'Local Development';
      } else if (hostname.includes('.')) {
        // For domain names, capitalize first part
        const firstPart = hostname.split('.')[0];
        return firstPart.charAt(0).toUpperCase() + firstPart.slice(1) + ' Server';
      } else {
        // For simple hostnames, capitalize and add "Server"
        return hostname.charAt(0).toUpperCase() + hostname.slice(1) + ' Server';
      }
    } catch {
      // If URL parsing fails, extract host from host:port format
      const hostPart = url.split(':')[0];
      if (hostPart) {
        return hostPart.charAt(0).toUpperCase() + hostPart.slice(1) + ' Server';
      }
      return '';
    }
  }, []);

  // Handle input changes
  const handleInputChange = (field: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setFormData(prev => ({ ...prev, [field]: value }));

    // Clear field error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }

    // Auto-infer server name from WebSocket URL if name is empty or was auto-generated
    if (field === 'websocketUrl') {
      const currentName = formData.name.trim();
      const inferredFromCurrentUrl = inferServerNameFromUrl(formData.websocketUrl);
      
      // Only auto-update name if it's empty or matches the previously inferred name
      if (!currentName || currentName === inferredFromCurrentUrl) {
        const newInferredName = inferServerNameFromUrl(value);
        if (newInferredName) {
          setFormData(prev => ({ ...prev, name: newInferredName, [field]: value }));
          return; // Early return to avoid duplicate state update
        }
      }
      
      // Clear connection test result when URL changes
      if (connectionTestResult) {
        setConnectionTestResult(null);
      }
    }
  };

  // Test WebSocket connection
  const testConnection = async () => {
    if (!validateWebSocketUrl(formData.websocketUrl)) {
      setConnectionTestResult({
        success: false,
        message: 'Please enter a valid WebSocket URL first',
      });
      return;
    }

    setIsTestingConnection(true);
    setConnectionTestResult(null);

    try {
      // Determine the full WebSocket URL
      let wsUrl = formData.websocketUrl.trim();
      if (!wsUrl.startsWith('ws://') && !wsUrl.startsWith('wss://')) {
        // Default to ws:// for plain host:port format and add /ws path
        wsUrl = `ws://${wsUrl}/ws`;
      }

      // Test connection with a timeout
      const ws = new WebSocket(wsUrl);

      const testPromise = new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
          ws.close();
          reject(new Error('Connection timeout (10s)'));
        }, 10000);

        ws.onopen = () => {
          clearTimeout(timeout);
          ws.close();
          resolve();
        };

        ws.onerror = () => {
          clearTimeout(timeout);
          reject(new Error('Connection failed'));
        };
      });

      await testPromise;

      setConnectionTestResult({
        success: true,
        message: 'Connection successful!',
      });
    } catch (error) {
      setConnectionTestResult({
        success: false,
        message: error instanceof Error ? error.message : 'Connection failed',
      });
    } finally {
      setIsTestingConnection(false);
    }
  };

  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    try {
      // Process the WebSocket URL
      let websocketUrl = formData.websocketUrl.trim();
      if (!websocketUrl.startsWith('ws://') && !websocketUrl.startsWith('wss://')) {
        websocketUrl = `ws://${websocketUrl}/ws`;
      }

      await onSubmit({
        name: formData.name.trim(),
        websocketUrl,
      });
    } catch (error) {
      console.error('Form submission error:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 sm:items-center sm:p-4">
      <Card className="h-full w-full max-h-none overflow-hidden rounded-none sm:h-auto sm:max-w-md sm:max-h-[90vh] sm:rounded-lg">
        <CardHeader className="flex-row items-center justify-between border-b border-gray-200 p-4 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {isEditing ? 'Edit Server' : 'Add Server'}
          </h2>
          <IconButton
            icon={X}
            onPress={onCancel}
            size="sm"
            variant="ghost"
            aria-label="Close form"
          />
        </CardHeader>

        <form onSubmit={handleSubmit} className="flex h-full flex-col sm:h-auto">
          <CardContent className="flex-1 space-y-4 overflow-y-auto p-4">
            {/* Server name input */}
            <div>
              <Input
                label="Server Name"
                placeholder="My Development Server"
                value={formData.name}
                onChange={handleInputChange('name')}
                error={errors.name}
                required
                autoComplete="off"
              />
            </div>

            {/* WebSocket URL input */}
            <div>
              <Input
                label="WebSocket URL"
                placeholder="localhost:8080 (will become ws://localhost:8080/ws)"
                value={formData.websocketUrl}
                onChange={handleInputChange('websocketUrl')}
                error={errors.websocketUrl}
                required
                type="url"
                inputMode="url"
                autoComplete="url"
              />
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Examples: localhost:8080 → ws://localhost:8080/ws, or ws://server.com:8080
              </p>
            </div>

            {/* Connection test */}
            <div className="pt-2">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                fullWidth
                onPress={testConnection}
                loading={isTestingConnection}
                disabled={!formData.websocketUrl.trim() || isSubmitting}
              >
                <Globe className="mr-2 h-4 w-4" />
                Test Connection
              </Button>

              {/* Connection test result */}
              {connectionTestResult && (
                <div
                  className={`mt-2 flex items-start gap-2 rounded-md p-2 ${
                    connectionTestResult.success
                      ? 'border border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-900/20'
                      : 'border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
                  }`}
                >
                  <AlertCircle
                    className={`mt-0.5 h-4 w-4 flex-shrink-0 ${
                      connectionTestResult.success
                        ? 'text-green-600 dark:text-green-400'
                        : 'text-red-600 dark:text-red-400'
                    }`}
                  />
                  <p
                    className={`text-sm ${
                      connectionTestResult.success
                        ? 'text-green-700 dark:text-green-300'
                        : 'text-red-700 dark:text-red-300'
                    }`}
                  >
                    {connectionTestResult.message}
                  </p>
                </div>
              )}
            </div>
          </CardContent>

          <CardFooter className="flex gap-3 border-t border-gray-200 p-4 dark:border-gray-700">
            <Button
              type="button"
              variant="secondary"
              fullWidth
              onPress={onCancel}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              fullWidth
              loading={isSubmitting}
              disabled={isTestingConnection}
              onPress={() => {}}
            >
              {isEditing ? 'Save Changes' : 'Add Server'}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
