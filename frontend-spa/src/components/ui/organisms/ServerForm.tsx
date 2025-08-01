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
      newErrors.websocketUrl =
        'Enter a valid WebSocket URL (ws://host:port, wss://host:port, or [IPv6]:port)';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  // Handle input changes
  const handleInputChange = (field: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setFormData(prev => ({ ...prev, [field]: value }));

    // Clear field error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }

    // Clear connection test result when URL changes
    if (field === 'websocketUrl' && connectionTestResult) {
      setConnectionTestResult(null);
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
        // Default to ws:// for plain host:port format
        wsUrl = `ws://${wsUrl}`;
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
        websocketUrl = `ws://${websocketUrl}`;
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className="max-h-[90vh] w-full max-w-md overflow-hidden">
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

        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4 overflow-y-auto p-4">
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
                placeholder="localhost:8080 or ws://server.com:8080"
                value={formData.websocketUrl}
                onChange={handleInputChange('websocketUrl')}
                error={errors.websocketUrl}
                required
                type="url"
                inputMode="url"
                autoComplete="url"
              />
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Enter host:port or full ws://host:port URL
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
