/**
 * UI state atoms using Jotai for atomic state management.
 * Manages theme, loading states, and other UI-related state.
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Theme state with localStorage persistence
export const themeAtom = atomWithStorage<'light' | 'dark' | 'system'>('theme', 'system');

// Global loading state
export const loadingAtom = atom<boolean>(false);

// Global error state - enhanced for accessibility
export const errorAtom = atom<{
  message: string;
  level: 'error' | 'warning' | 'info';
  announceToScreenReader: boolean;
  context?: string;
  timestamp: string;
} | null>(null);

// Modal/dialog states
export const activeModalAtom = atom<string | null>(null);

// Navigation state
export const navigationStateAtom = atom<{
  isNavigating: boolean;
  fromRoute?: string;
  toRoute?: string;
}>({
  isNavigating: false
});

// Mobile-specific UI state - split into focused atoms for better performance
export const keyboardVisibleAtom = atom<boolean>(false);

export const orientationAtom = atom<'portrait' | 'landscape'>('portrait');

export const statusBarHeightAtom = atom<number>(20);

export type SafeAreaInsets = {
  top: number;
  bottom: number;
  left: number;
  right: number;
};

export const safeAreaInsetsAtom = atom<SafeAreaInsets>({
  top: 0,
  bottom: 0,
  left: 0,
  right: 0
});

// Legacy combined atom for backward compatibility
export const mobileUIStateAtom = atom(
  (get) => ({
    keyboardVisible: get(keyboardVisibleAtom),
    orientation: get(orientationAtom),
    statusBarHeight: get(statusBarHeightAtom),
    safeAreaInsets: get(safeAreaInsetsAtom)
  }),
  (get, set, updates: Partial<{
    keyboardVisible: boolean;
    orientation: 'portrait' | 'landscape';
    statusBarHeight: number;
    safeAreaInsets: SafeAreaInsets;
  }>) => {
    if ('keyboardVisible' in updates) {
      set(keyboardVisibleAtom, updates.keyboardVisible!);
    }
    if ('orientation' in updates) {
      set(orientationAtom, updates.orientation!);
    }
    if ('statusBarHeight' in updates) {
      set(statusBarHeightAtom, updates.statusBarHeight!);
    }
    if ('safeAreaInsets' in updates) {
      set(safeAreaInsetsAtom, updates.safeAreaInsets!);
    }
  }
);

// Toast/notification state
export const toastAtom = atom<{
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  description?: string;
  duration?: number;
} | null>(null);

// App state indicators
export const appStateAtom = atom<{
  isInitialized: boolean;
  hasHydrated: boolean;
  isOnline: boolean;
}>({
  isInitialized: false,
  hasHydrated: false,
  isOnline: true
});

// Form states (for tracking form submission states globally)
export const formStatesAtom = atom<Map<string, {
  isSubmitting: boolean;
  hasErrors: boolean;
  lastSubmitted?: string;
}>>(new Map());

// Derived atom for dark mode based on theme and system preference
export const isDarkModeAtom = atom(
  (get) => {
    const theme = get(themeAtom);
    if (theme === 'dark') return true;
    if (theme === 'light') return false;
    
    // Auto-detect system preference if theme is 'system'
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
);

// Derived atom to check if any modal is open
export const hasActiveModalAtom = atom(
  (get) => get(activeModalAtom) !== null
);

// Derived atom to check if app is ready for user interaction
export const isAppReadyAtom = atom(
  (get) => {
    const appState = get(appStateAtom);
    return appState.isInitialized && appState.hasHydrated;
  }
);

// Write-only atom for showing toast notifications
export const showToastAtom = atom(
  null,
  (get, set, toast: Omit<NonNullable<ReturnType<typeof toastAtom.read>>, 'id'>) => {
    const toastWithId = {
      id: crypto.randomUUID(),
      ...toast
    };
    set(toastAtom, toastWithId);
    
    // Auto-dismiss after duration (default 5 seconds)
    const duration = toast.duration || 5000;
    setTimeout(() => {
      const currentToast = get(toastAtom);
      if (currentToast?.id === toastWithId.id) {
        set(toastAtom, null);
      }
    }, duration);
  }
);

// Write-only atom for dismissing toast
export const dismissToastAtom = atom(
  null,
  (_get, set) => {
    set(toastAtom, null);
  }
);

// Write-only atom for showing modal
export const showModalAtom = atom(
  null,
  (_get, set, modalId: string) => {
    set(activeModalAtom, modalId);
  }
);

// Write-only atom for hiding modal
export const hideModalAtom = atom(
  null,
  (_get, set) => {
    set(activeModalAtom, null);
  }
);

// Write-only atom for setting loading state
export const setLoadingAtom = atom(
  null,
  (_get, set, loading: boolean) => {
    set(loadingAtom, loading);
  }
);

// Write-only atom for setting error state
export const setErrorAtom = atom(
  null,
  (_get, set, error: string | null | {
    message: string;
    level?: 'error' | 'warning' | 'info';
    announceToScreenReader?: boolean;
    context?: string;
  }) => {
    if (error === null) {
      set(errorAtom, null);
    } else if (typeof error === 'string') {
      // Backward compatibility for simple string errors
      set(errorAtom, {
        message: error,
        level: 'error',
        announceToScreenReader: true,
        timestamp: new Date().toISOString()
      });
    } else {
      // Enhanced error object
      set(errorAtom, {
        message: error.message,
        level: error.level || 'error',
        announceToScreenReader: error.announceToScreenReader !== false,
        context: error.context,
        timestamp: new Date().toISOString()
      });
    }
  }
);

// Write-only atom for updating form state
export const updateFormStateAtom = atom(
  null,
  (get, set, formId: string, state: Partial<{
    isSubmitting: boolean;
    hasErrors: boolean;
    lastSubmitted?: string;
  }>) => {
    const formStates = get(formStatesAtom);
    const currentState = formStates.get(formId) || { isSubmitting: false, hasErrors: false };
    const newFormStates = new Map(formStates);
    newFormStates.set(formId, { ...currentState, ...state });
    set(formStatesAtom, newFormStates);
  }
);

// Write-only atom for updating mobile UI state
export const updateMobileUIStateAtom = atom(
  null,
  (get, set, updates: Partial<NonNullable<ReturnType<typeof mobileUIStateAtom.read>>>) => {
    const currentState = get(mobileUIStateAtom);
    set(mobileUIStateAtom, { ...currentState, ...updates });
  }
);

// Write-only atom for updating app state
export const updateAppStateAtom = atom(
  null,
  (get, set, updates: Partial<NonNullable<ReturnType<typeof appStateAtom.read>>>) => {
    const currentState = get(appStateAtom);
    set(appStateAtom, { ...currentState, ...updates });
  }
);

// Storage quota monitoring atom
export const storageStatusAtom = atom<{
  available: number;
  used: number;
  quotaExceeded: boolean;
  lastUpdated: string;
}>({
  available: 0,
  used: 0,
  quotaExceeded: false,
  lastUpdated: new Date().toISOString()
});

// Write-only atom for updating storage status
export const updateStorageStatusAtom = atom(
  null,
  (get, set, updates: Partial<NonNullable<ReturnType<typeof storageStatusAtom.read>>>) => {
    const currentStatus = get(storageStatusAtom);
    set(storageStatusAtom, { 
      ...currentStatus, 
      ...updates, 
      lastUpdated: new Date().toISOString() 
    });
  }
);

// Write-only atom for refreshing storage status from LocalStorageService
export const refreshStorageStatusAtom = atom(
  null,
  async (_get, set) => {
    try {
      // Import LocalStorageService here to avoid circular dependency
      const { localStorageService } = await import('../../services/storage/LocalStorageService');
      const storageUsage = localStorageService.getStorageUsage();
      
      set(storageStatusAtom, {
        available: storageUsage.available,
        used: storageUsage.used,
        quotaExceeded: storageUsage.available < 0,
        lastUpdated: new Date().toISOString()
      });
    } catch (error) {
      console.error('Failed to refresh storage status:', error);
      // Set error state in storage monitoring
      set(storageStatusAtom, {
        available: 0,
        used: 0,
        quotaExceeded: true,
        lastUpdated: new Date().toISOString()
      });
    }
  }
);