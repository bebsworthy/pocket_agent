/**
 * Hook for managing loading states
 * Provides utilities for controlling loading state and messages
 */

import { useState } from 'react';

export function useLoadingState(initialLoading = false) {
  const [isLoading, setIsLoading] = useState(initialLoading);
  const [loadingMessage, setLoadingMessage] = useState<string>('Loading...');

  const startLoading = (message?: string) => {
    setIsLoading(true);
    if (message) setLoadingMessage(message);
  };

  const stopLoading = () => {
    setIsLoading(false);
  };

  const updateMessage = (message: string) => {
    setLoadingMessage(message);
  };

  return {
    isLoading,
    loadingMessage,
    startLoading,
    stopLoading,
    updateMessage,
  };
}
