/**
 * Theme constants for light and dark modes
 * Optimized for mobile-first design with high contrast ratios
 * Colors match the mobile mockup specifications
 */

export type ThemeMode = 'light' | 'dark' | 'system';

export interface ThemeColors {
  // Background colors
  background: {
    primary: string;
    secondary: string;
    tertiary: string;
    overlay: string;
  };

  // Text colors
  text: {
    primary: string;
    secondary: string;
    tertiary: string;
    inverse: string;
  };

  // Border colors
  border: {
    primary: string;
    secondary: string;
    focus: string;
  };

  // Interactive colors
  interactive: {
    primary: string;
    primaryHover: string;
    primaryActive: string;
    secondary: string;
    secondaryHover: string;
    ghost: string;
    ghostHover: string;
  };

  // Status colors
  status: {
    success: string;
    warning: string;
    error: string;
    info: string;
  };

  // Connection status
  connection: {
    connected: string;
    disconnected: string;
    connecting: string;
  };

  // Shadow colors
  shadow: {
    card: string;
    cardHover: string;
    modal: string;
  };
}

export const lightTheme: ThemeColors = {
  background: {
    primary: '#ffffff',
    secondary: '#f9fafb',
    tertiary: '#f3f4f6',
    overlay: 'rgba(0, 0, 0, 0.5)',
  },

  text: {
    primary: '#111827',
    secondary: '#374151',
    tertiary: '#6b7280',
    inverse: '#ffffff',
  },

  border: {
    primary: '#e5e7eb',
    secondary: '#d1d5db',
    focus: '#3b82f6',
  },

  interactive: {
    primary: '#3b82f6',
    primaryHover: '#2563eb',
    primaryActive: '#1d4ed8',
    secondary: '#f3f4f6',
    secondaryHover: '#e5e7eb',
    ghost: 'transparent',
    ghostHover: '#f3f4f6',
  },

  status: {
    success: '#22c55e',
    warning: '#f59e0b',
    error: '#ef4444',
    info: '#3b82f6',
  },

  connection: {
    connected: '#22c55e',
    disconnected: '#ef4444',
    connecting: '#f59e0b',
  },

  shadow: {
    card: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
    cardHover: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    modal: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  },
};

export const darkTheme: ThemeColors = {
  background: {
    primary: '#111827',
    secondary: '#1f2937',
    tertiary: '#374151',
    overlay: 'rgba(0, 0, 0, 0.75)',
  },

  text: {
    primary: '#f9fafb',
    secondary: '#e5e7eb',
    tertiary: '#9ca3af',
    inverse: '#111827',
  },

  border: {
    primary: '#374151',
    secondary: '#4b5563',
    focus: '#60a5fa',
  },

  interactive: {
    primary: '#3b82f6',
    primaryHover: '#2563eb',
    primaryActive: '#1d4ed8',
    secondary: '#374151',
    secondaryHover: '#4b5563',
    ghost: 'transparent',
    ghostHover: '#374151',
  },

  status: {
    success: '#22c55e',
    warning: '#f59e0b',
    error: '#ef4444',
    info: '#60a5fa',
  },

  connection: {
    connected: '#22c55e',
    disconnected: '#ef4444',
    connecting: '#f59e0b',
  },

  shadow: {
    card: '0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px 0 rgba(0, 0, 0, 0.2)',
    cardHover: '0 4px 6px -1px rgba(0, 0, 0, 0.3), 0 2px 4px -1px rgba(0, 0, 0, 0.2)',
    modal: '0 20px 25px -5px rgba(0, 0, 0, 0.4), 0 10px 10px -5px rgba(0, 0, 0, 0.3)',
  },
};

/**
 * Theme utilities for managing theme state and detection
 */
export class ThemeManager {
  private static readonly STORAGE_KEY = 'theme-preference';

  /**
   * Get the current theme preference from localStorage
   */
  static getStoredTheme(): ThemeMode {
    if (typeof window === 'undefined') return 'system';

    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored && ['light', 'dark', 'system'].includes(stored)) {
        return stored as ThemeMode;
      }
    } catch (error) {
      console.warn('Failed to read theme preference from localStorage:', error);
    }

    return 'system';
  }

  /**
   * Store theme preference in localStorage
   */
  static setStoredTheme(theme: ThemeMode): void {
    if (typeof window === 'undefined') return;

    try {
      localStorage.setItem(this.STORAGE_KEY, theme);
    } catch (error) {
      console.warn('Failed to save theme preference to localStorage:', error);
    }
  }

  /**
   * Get system theme preference
   */
  static getSystemTheme(): 'light' | 'dark' {
    if (typeof window === 'undefined') return 'light';

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  /**
   * Resolve the effective theme based on preference and system
   */
  static resolveTheme(preference: ThemeMode): 'light' | 'dark' {
    if (preference === 'system') {
      return this.getSystemTheme();
    }
    return preference;
  }

  /**
   * Apply theme to the document root
   */
  static applyTheme(theme: 'light' | 'dark'): void {
    if (typeof document === 'undefined') return;

    const root = document.documentElement;
    const isDark = theme === 'dark';

    // Toggle the 'dark' class on the root element
    root.classList.toggle('dark', isDark);

    // Update the color-scheme CSS property for better native form controls
    root.style.colorScheme = theme;

    // Update meta theme-color for mobile browsers
    const metaThemeColor = document.querySelector('meta[name="theme-color"]');
    if (metaThemeColor) {
      metaThemeColor.setAttribute(
        'content',
        isDark ? darkTheme.background.primary : lightTheme.background.primary
      );
    }
  }

  /**
   * Listen for system theme changes
   */
  static onSystemThemeChange(callback: (theme: 'light' | 'dark') => void): () => void {
    if (typeof window === 'undefined') return () => {};

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      callback(e.matches ? 'dark' : 'light');
    };

    mediaQuery.addEventListener('change', handler);

    // Return cleanup function
    return () => {
      mediaQuery.removeEventListener('change', handler);
    };
  }
}

/**
 * Get theme colors for the current theme
 */
export function getThemeColors(theme: 'light' | 'dark'): ThemeColors {
  return theme === 'dark' ? darkTheme : lightTheme;
}

/**
 * CSS custom properties for dynamic theming
 * These can be used in CSS-in-JS or for runtime theme switching
 */
export function getCSSCustomProperties(theme: 'light' | 'dark'): Record<string, string> {
  const colors = getThemeColors(theme);

  return {
    '--color-bg-primary': colors.background.primary,
    '--color-bg-secondary': colors.background.secondary,
    '--color-bg-tertiary': colors.background.tertiary,
    '--color-bg-overlay': colors.background.overlay,

    '--color-text-primary': colors.text.primary,
    '--color-text-secondary': colors.text.secondary,
    '--color-text-tertiary': colors.text.tertiary,
    '--color-text-inverse': colors.text.inverse,

    '--color-border-primary': colors.border.primary,
    '--color-border-secondary': colors.border.secondary,
    '--color-border-focus': colors.border.focus,

    '--color-interactive-primary': colors.interactive.primary,
    '--color-interactive-primary-hover': colors.interactive.primaryHover,
    '--color-interactive-primary-active': colors.interactive.primaryActive,
    '--color-interactive-secondary': colors.interactive.secondary,
    '--color-interactive-secondary-hover': colors.interactive.secondaryHover,
    '--color-interactive-ghost': colors.interactive.ghost,
    '--color-interactive-ghost-hover': colors.interactive.ghostHover,

    '--color-status-success': colors.status.success,
    '--color-status-warning': colors.status.warning,
    '--color-status-error': colors.status.error,
    '--color-status-info': colors.status.info,

    '--color-connection-connected': colors.connection.connected,
    '--color-connection-disconnected': colors.connection.disconnected,
    '--color-connection-connecting': colors.connection.connecting,

    '--shadow-card': colors.shadow.card,
    '--shadow-card-hover': colors.shadow.cardHover,
    '--shadow-modal': colors.shadow.modal,
  };
}

/**
 * Default export with all theme utilities
 */
export default {
  light: lightTheme,
  dark: darkTheme,
  ThemeManager,
  getThemeColors,
  getCSSCustomProperties,
};
