/**
 * Custom hook for UI state management using Jotai atoms.
 * Provides convenient interface for managing theme, modals, loading states, etc.
 */

import { useCallback } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import {
  themeAtom,
  loadingAtom,
  errorAtom,
  activeModalAtom,
  navigationStateAtom,
  mobileUIStateAtom,
  toastAtom,
  appStateAtom,
  formStatesAtom,
  storageStatusAtom,
  isDarkModeAtom,
  hasActiveModalAtom,
  isAppReadyAtom,
  showToastAtom,
  dismissToastAtom,
  showModalAtom,
  hideModalAtom,
  setLoadingAtom,
  setErrorAtom,
  updateFormStateAtom,
  updateMobileUIStateAtom,
  updateAppStateAtom,
  updateStorageStatusAtom,
  refreshStorageStatusAtom
} from '../atoms/ui';

// Primary hook for UI state management
export function useUI() {
  const [theme, setTheme] = useAtom(themeAtom);
  const loading = useAtomValue(loadingAtom);
  const error = useAtomValue(errorAtom);
  const activeModal = useAtomValue(activeModalAtom);
  const [navigationState, setNavigationState] = useAtom(navigationStateAtom);
  const mobileUIState = useAtomValue(mobileUIStateAtom);
  const toast = useAtomValue(toastAtom);
  const appState = useAtomValue(appStateAtom);
  const formStates = useAtomValue(formStatesAtom);
  const storageStatus = useAtomValue(storageStatusAtom);

  // Derived state
  const isDarkMode = useAtomValue(isDarkModeAtom);
  const hasActiveModal = useAtomValue(hasActiveModalAtom);
  const isAppReady = useAtomValue(isAppReadyAtom);

  // Action setters
  const showToast = useSetAtom(showToastAtom);
  const dismissToast = useSetAtom(dismissToastAtom);
  const showModal = useSetAtom(showModalAtom);
  const hideModal = useSetAtom(hideModalAtom);
  const setLoading = useSetAtom(setLoadingAtom);
  const setError = useSetAtom(setErrorAtom);
  const updateFormState = useSetAtom(updateFormStateAtom);
  const updateMobileUIState = useSetAtom(updateMobileUIStateAtom);
  const updateAppState = useSetAtom(updateAppStateAtom);
  // const updateStorageStatus = useSetAtom(updateStorageStatusAtom);
  const refreshStorageStatus = useSetAtom(refreshStorageStatusAtom);

  // Helper functions
  const toggleTheme = useCallback(() => {
    setTheme(current => current === 'light' ? 'dark' : 'light');
  }, [setTheme]);

  const showSuccessToast = useCallback((message: string, description?: string) => {
    showToast({
      type: 'success',
      message,
      description
    });
  }, [showToast]);

  const showErrorToast = useCallback((message: string, description?: string) => {
    showToast({
      type: 'error',
      message,
      description
    });
  }, [showToast]);

  const showWarningToast = useCallback((message: string, description?: string) => {
    showToast({
      type: 'warning',
      message,
      description
    });
  }, [showToast]);

  const showInfoToast = useCallback((message: string, description?: string) => {
    showToast({
      type: 'info',
      message,
      description
    });
  }, [showToast]);

  const clearError = useCallback(() => {
    setError(null);
  }, [setError]);

  const startLoading = useCallback(() => {
    setLoading(true);
  }, [setLoading]);

  const stopLoading = useCallback(() => {
    setLoading(false);
  }, [setLoading]);

  const updateNavigation = useCallback((updates: Partial<typeof navigationState>) => {
    setNavigationState(current => ({ ...current, ...updates }));
  }, [setNavigationState]);

  const startNavigation = useCallback((fromRoute?: string, toRoute?: string) => {
    setNavigationState({
      isNavigating: true,
      fromRoute,
      toRoute
    });
  }, [setNavigationState]);

  const endNavigation = useCallback(() => {
    setNavigationState({
      isNavigating: false
    });
  }, [setNavigationState]);

  const updateKeyboardVisibility = useCallback((visible: boolean) => {
    updateMobileUIState({ keyboardVisible: visible });
  }, [updateMobileUIState]);

  const updateOrientation = useCallback((orientation: 'portrait' | 'landscape') => {
    updateMobileUIState({ orientation });
  }, [updateMobileUIState]);

  const updateSafeAreaInsets = useCallback((insets: typeof mobileUIState.safeAreaInsets) => {
    updateMobileUIState({ safeAreaInsets: insets });
  }, [updateMobileUIState]);

  const markAppInitialized = useCallback(() => {
    updateAppState({ isInitialized: true });
  }, [updateAppState]);

  const markAppHydrated = useCallback(() => {
    updateAppState({ hasHydrated: true });
  }, [updateAppState]);

  const updateOnlineStatus = useCallback((isOnline: boolean) => {
    updateAppState({ isOnline });
  }, [updateAppState]);

  const getFormState = useCallback((formId: string) => {
    return formStates.get(formId) || { isSubmitting: false, hasErrors: false };
  }, [formStates]);

  const startFormSubmission = useCallback((formId: string) => {
    updateFormState(formId, { isSubmitting: true, hasErrors: false });
  }, [updateFormState]);

  const endFormSubmission = useCallback((formId: string, hasErrors: boolean = false) => {
    updateFormState(formId, { 
      isSubmitting: false, 
      hasErrors,
      lastSubmitted: new Date().toISOString()
    });
  }, [updateFormState]);

  const checkStorageStatus = useCallback(async () => {
    await refreshStorageStatus();
  }, [refreshStorageStatus]);

  const isStorageQuotaExceeded = useCallback(() => {
    return storageStatus.quotaExceeded;
  }, [storageStatus.quotaExceeded]);

  const getStorageUsage = useCallback(() => {
    return {
      used: storageStatus.used,
      available: storageStatus.available,
      usagePercentage: storageStatus.available > 0 
        ? (storageStatus.used / (storageStatus.used + storageStatus.available)) * 100
        : 100
    };
  }, [storageStatus]);

  return {
    // State
    theme,
    loading,
    error,
    activeModal,
    navigationState,
    mobileUIState,
    toast,
    appState,
    formStates,
    storageStatus,
    isDarkMode,
    hasActiveModal,
    isAppReady,

    // Theme actions
    setTheme,
    toggleTheme,

    // Loading actions
    startLoading,
    stopLoading,

    // Error actions
    setError,
    clearError,

    // Modal actions
    showModal,
    hideModal,

    // Toast actions
    showToast,
    showSuccessToast,
    showErrorToast,
    showWarningToast,
    showInfoToast,
    dismissToast,

    // Navigation actions
    updateNavigation,
    startNavigation,
    endNavigation,

    // Mobile UI actions
    updateKeyboardVisibility,
    updateOrientation,
    updateSafeAreaInsets,

    // App state actions
    markAppInitialized,
    markAppHydrated,
    updateOnlineStatus,

    // Form state actions
    getFormState,
    startFormSubmission,
    endFormSubmission,

    // Storage monitoring actions
    checkStorageStatus,
    isStorageQuotaExceeded,
    getStorageUsage
  };
}

// Lightweight hook for theme management only
export function useTheme() {
  const [theme, setTheme] = useAtom(themeAtom);
  const isDarkMode = useAtomValue(isDarkModeAtom);

  const toggleTheme = useCallback(() => {
    setTheme(current => current === 'light' ? 'dark' : 'light');
  }, [setTheme]);

  return {
    theme,
    isDarkMode,
    setTheme,
    toggleTheme
  };
}

// Hook for toast notifications only
export function useToast() {
  const toast = useAtomValue(toastAtom);
  const showToast = useSetAtom(showToastAtom);
  const dismissToast = useSetAtom(dismissToastAtom);

  const showSuccessToast = useCallback((message: string, description?: string) => {
    showToast({ type: 'success', message, description });
  }, [showToast]);

  const showErrorToast = useCallback((message: string, description?: string) => {
    showToast({ type: 'error', message, description });
  }, [showToast]);

  const showWarningToast = useCallback((message: string, description?: string) => {
    showToast({ type: 'warning', message, description });
  }, [showToast]);

  const showInfoToast = useCallback((message: string, description?: string) => {
    showToast({ type: 'info', message, description });
  }, [showToast]);

  return {
    toast,
    showToast,
    showSuccessToast,
    showErrorToast,
    showWarningToast,
    showInfoToast,
    dismissToast
  };
}

// Hook for loading state management only
export function useLoading() {
  const loading = useAtomValue(loadingAtom);
  const setLoading = useSetAtom(setLoadingAtom);

  const startLoading = useCallback(() => setLoading(true), [setLoading]);
  const stopLoading = useCallback(() => setLoading(false), [setLoading]);

  return {
    loading,
    startLoading,
    stopLoading
  };
}

// Hook for modal management only
export function useModal() {
  const activeModal = useAtomValue(activeModalAtom);
  const hasActiveModal = useAtomValue(hasActiveModalAtom);
  const showModal = useSetAtom(showModalAtom);
  const hideModal = useSetAtom(hideModalAtom);

  const isModalActive = useCallback((modalId: string) => {
    return activeModal === modalId;
  }, [activeModal]);

  return {
    activeModal,
    hasActiveModal,
    showModal,
    hideModal,
    isModalActive
  };
}