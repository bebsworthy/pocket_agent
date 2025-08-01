import React, { Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';
import { LoadingScreen } from './components/LoadingScreen';

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
  return (
    <BrowserRouter>
      <ErrorBoundary>
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
      </ErrorBoundary>
    </BrowserRouter>
  );
};

export default Router;
