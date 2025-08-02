import React, { useEffect } from 'react';
import { Provider as JotaiProvider } from 'jotai';
import { useAtom } from 'jotai';
import { Router } from './Router';
import { ErrorBoundary } from './components/ErrorBoundary';
import { DevErrorBoundary } from './components/DevErrorBoundary';
import { WebSocketProvider } from './services/websocket/WebSocketContext';
import { useMobileViewport } from './hooks/useMobileViewport';
import { themeAtom } from './store/atoms/ui';
import { debugComponentMount, debugComponentUnmount } from './utils/debug';
import './styles/globals.css';

// Theme Provider Component
const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setTheme] = useAtom(themeAtom);

  useEffect(() => {
    const root = document.documentElement;
    let mediaQuery: MediaQueryList | null = null;
    let currentAppliedTheme: string | null = null;

    const applyTheme = (themeToApply: string) => {
      // Only update DOM if theme actually changed
      if (currentAppliedTheme !== themeToApply) {
        root.classList.remove('light', 'dark');
        root.classList.add(themeToApply);
        currentAppliedTheme = themeToApply;
      }
    };

    const resolveTheme = () => {
      if (theme === 'system') {
        const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'dark'
          : 'light';
        return systemTheme;
      }
      return theme;
    };

    // Initialize theme
    const initialTheme = resolveTheme();
    applyTheme(initialTheme);

    // Set up system theme listener only if needed
    if (theme === 'system') {
      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

      const handleSystemThemeChange = (_e: MediaQueryListEvent) => {
        // Only respond to system changes if still in system mode
        const currentTheme = resolveTheme();
        applyTheme(currentTheme);
      };

      mediaQuery.addEventListener('change', handleSystemThemeChange);

      return () => {
        if (mediaQuery) {
          mediaQuery.removeEventListener('change', handleSystemThemeChange);
        }
      };
    }

    // No cleanup needed for non-system themes
    return undefined;
  }, [theme, setTheme]);

  return <>{children}</>;
};

// Inner App Component that uses hooks
const AppContent: React.FC = () => {
  // Use mobile viewport hook
  useMobileViewport();

  useEffect(() => {
    debugComponentMount('AppContent');
    return () => debugComponentUnmount('AppContent');
  }, []);

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-900 dark:text-gray-100">
      <Router />
    </div>
  );
};

// Main App Component
const App: React.FC = () => {
  const isDev = import.meta.env.DEV;

  useEffect(() => {
    debugComponentMount('App');
    return () => debugComponentUnmount('App');
  }, []);

  const AppContentWrapper = () => (
    <JotaiProvider>
      <WebSocketProvider>
        <ThemeProvider>
          <AppContent />
        </ThemeProvider>
      </WebSocketProvider>
    </JotaiProvider>
  );

  if (isDev) {
    return (
      <DevErrorBoundary componentName="App (Development)">
        <ErrorBoundary>
          <AppContentWrapper />
        </ErrorBoundary>
      </DevErrorBoundary>
    );
  }

  return (
    <ErrorBoundary>
      <AppContentWrapper />
    </ErrorBoundary>
  );
};

export default App;
