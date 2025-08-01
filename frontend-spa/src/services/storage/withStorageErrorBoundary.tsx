/**
 * Higher-order component for wrapping components with storage error boundary
 */

import React from 'react';
import { StorageErrorBoundary } from './StorageErrorBoundary';

/**
 * HOC for wrapping storage-related components with storage error boundary
 */
export function withStorageErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  onStorageReset?: () => void
) {
  const WrappedComponent = (props: P) => (
    <StorageErrorBoundary onStorageReset={onStorageReset}>
      <Component {...props} />
    </StorageErrorBoundary>
  );

  WrappedComponent.displayName = `withStorageErrorBoundary(${Component.displayName || Component.name})`;
  return WrappedComponent;
}
