/**
 * Specialized error boundary for storage-related errors
 * Provides storage-specific recovery options and fallbacks
 */

import React, { ReactNode } from 'react';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { Button } from '@/components/ui/atoms/Button';
import { Card } from '@/components/ui/molecules/Card';
import { Database, RefreshCw, Trash2, Download } from 'lucide-react';
import { localStorageService } from './LocalStorageService';

interface StorageErrorBoundaryProps {
  children: ReactNode;
  onStorageReset?: () => void;
}

export function StorageErrorBoundary({ children, onStorageReset }: StorageErrorBoundaryProps) {
  const handleClearStorage = () => {
    try {
      localStorageService.clearAllData();
      onStorageReset?.();
      window.location.reload();
    } catch (error) {
      console.error('Failed to clear storage:', error);
      // Fallback to manual localStorage clear
      try {
        localStorage.clear();
        window.location.reload();
      } catch {
        alert('Please manually clear your browser data and refresh the page.');
      }
    }
  };

  const handleExportData = () => {
    try {
      const data = localStorageService.exportData();
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `pocket-agent-backup-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to export data:', error);
      alert('Unable to export data. Please try clearing storage.');
    }
  };

  const storageErrorFallback = (error: Error, resetError: () => void) => {
    const isStorageError = error.message.includes('Storage Error');

    if (!isStorageError) {
      // Fall back to default error boundary for non-storage errors
      return null;
    }

    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
        <Card className="w-full max-w-md">
          <div className="space-y-4 p-6 text-center">
            {/* Storage Error Icon */}
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-orange-100 dark:bg-orange-900/20">
              <Database className="h-8 w-8 text-orange-600 dark:text-orange-400" />
            </div>

            {/* Error Title */}
            <div className="space-y-2">
              <h2 className="text-xl font-semibold text-gray-900 dark:text-white">Storage Error</h2>
              <p className="text-sm text-gray-600 dark:text-gray-400">
                There's an issue with your browser's storage. This might be due to corrupted data or
                storage access restrictions.
              </p>
            </div>

            {/* Error Message */}
            <div className="rounded bg-gray-100 p-3 text-left text-sm dark:bg-gray-800">
              <div className="text-gray-700 dark:text-gray-300">
                <strong>Error Details:</strong>
              </div>
              <div className="mt-1 text-gray-600 dark:text-gray-400">
                {error.message.replace('Storage Error: ', '')}
              </div>
            </div>

            {/* Recovery Options */}
            <div className="space-y-3">
              <Button variant="primary" fullWidth onPress={resetError} ariaLabel="Try again">
                <RefreshCw className="mr-2 h-4 w-4" />
                Try Again
              </Button>

              <Button
                variant="secondary"
                fullWidth
                onPress={handleExportData}
                ariaLabel="Export your data before clearing storage"
              >
                <Download className="mr-2 h-4 w-4" />
                Export Data First
              </Button>

              <Button
                variant="ghost"
                fullWidth
                onPress={handleClearStorage}
                ariaLabel="Clear storage and restart"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Clear Storage & Restart
              </Button>
            </div>

            {/* Storage Info */}
            <div className="border-t border-gray-200 pt-2 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
              <p>
                <strong>What happens when you clear storage:</strong>
              </p>
              <ul className="mt-1 list-inside list-disc space-y-1 text-left">
                <li>All saved projects and servers will be removed</li>
                <li>Theme and user preferences will reset</li>
                <li>The app will restart with a clean state</li>
              </ul>
            </div>
          </div>
        </Card>
      </div>
    );
  };

  return (
    <ErrorBoundary
      fallback={storageErrorFallback}
      onError={(error, errorInfo) => {
        // Log storage-specific error details
        console.error('StorageErrorBoundary caught error:', {
          error: error.message,
          stack: error.stack,
          componentStack: errorInfo.componentStack,
          timestamp: new Date().toISOString(),
          storageUsage: (() => {
            try {
              return localStorageService.getStorageUsage();
            } catch {
              return { used: 0, available: 0 };
            }
          })(),
        });
      }}
    >
      {children}
    </ErrorBoundary>
  );
}
