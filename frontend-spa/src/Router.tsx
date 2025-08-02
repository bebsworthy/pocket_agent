import React, { Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';
import { DevErrorBoundary } from './components/DevErrorBoundary';
import { LoadingScreen } from './components/LoadingScreen';
import { debugComponentMount, debugComponentUnmount } from './utils/debug';

// Lazy load components for better performance
const Dashboard = React.lazy(() =>
  import('./pages/Dashboard').then(module => ({ default: module.Dashboard }))
);
const ProjectDetail = React.lazy(() =>
  import('./pages/ProjectDetail').then(module => ({ default: module.ProjectDetail }))
);
const Settings = React.lazy(() =>
  import('./pages/Settings').then(module => ({ default: module.Settings }))
);

export const Router: React.FC = () => {
  const isDev = import.meta.env.DEV;

  useEffect(() => {
    debugComponentMount('Router');
    return () => debugComponentUnmount('Router');
  }, []);

  const RouterContent = () => (
    <Suspense fallback={<LoadingScreen />}>
      <Routes>
        {/* Dashboard - Main route */}
        <Route path="/" element={<Dashboard />} />

        {/* Project Detail - Dynamic route with projectId param */}
        <Route path="/project/:projectId" element={<ProjectDetail />} />

        {/* Settings - Application settings */}
        <Route path="/settings" element={<Settings />} />

        {/* Catch-all route - Redirect to dashboard */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );

  return (
    <BrowserRouter>
      {isDev ? (
        <DevErrorBoundary componentName="Router (Development)">
          <ErrorBoundary>
            <RouterContent />
          </ErrorBoundary>
        </DevErrorBoundary>
      ) : (
        <ErrorBoundary>
          <RouterContent />
        </ErrorBoundary>
      )}
    </BrowserRouter>
  );
};

export default Router;
