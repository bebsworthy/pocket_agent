import React, { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Menu, Sun, Moon, AlertCircle } from 'lucide-react';
import { useProjects } from '../store/hooks/useProjects';
import { useServers } from '../store/hooks/useServers';
import { useTheme } from '../store/hooks/useUI';
import { useSetAtom, useAtomValue } from 'jotai';
import { updateCreateProjectFieldAtom, createProjectIsVisibleAtom } from '../store/atoms/projectCreationAtoms';
import { FAB } from '../components/ui/atoms/FAB';
import { IconButton } from '../components/ui/atoms/IconButton';
import { ProjectCard } from '../components/ui/organisms/ProjectCard';
import { ProjectCreationModal } from '../components/ui/organisms/ProjectCreationModal';
import { ServerForm } from '../components/ui/organisms/ServerForm';
import { EmptyState, EmptyStatePresets } from '../components/ui/organisms/EmptyState';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { DevErrorBoundary } from '../components/DevErrorBoundary';
import { debugRender, debugProps, debugComponentMount, debugComponentUnmount, validateProps } from '../utils/debug';
import type { Project, Server } from '../types/models';

const DashboardInner: React.FC = () => {
  const navigate = useNavigate();
  const { projects, hasProjects, isLoading } = useProjects();
  const { servers, addServer } = useServers();
  const { theme, toggleTheme } = useTheme();
  
  // Atom for updating project creation form
  const updateProjectField = useSetAtom(updateCreateProjectFieldAtom);

  // Modal state management using atoms for consistency
  const showCreateProjectModal = useAtomValue(createProjectIsVisibleAtom);
  const [showServerFormModal, setShowServerFormModal] = useState(false);

  // Debug component lifecycle
  useEffect(() => {
    debugComponentMount('Dashboard');
    debugRender('Dashboard', { 
      projects: projects.length, 
      hasProjects, 
      isLoading, 
      servers: servers.length,
      theme,
      showCreateProjectModal,
      showServerFormModal,
    });
    
    return () => debugComponentUnmount('Dashboard');
  }, []);

  // Debug state changes
  useEffect(() => {
    debugRender('Dashboard - State Update', { 
      projects: projects?.length || 0, 
      hasProjects, 
      isLoading, 
      servers: servers?.length || 0,
      theme,
      showCreateProjectModal,
      showServerFormModal,
    });
  }, [projects, hasProjects, isLoading, servers, theme, showCreateProjectModal, showServerFormModal]);

  // Handle project creation modal using atoms
  const handleOpenCreateProject = useCallback(() => {
    // Using atoms directly - no need for local state setter
    // This will be handled by the atom update
  }, []);

  const handleCloseCreateProject = useCallback(() => {
    // Using atoms directly - no need for local state setter
    // This will be handled by the atom update
  }, []);

  // Handle server form modal  
  const handleOpenServerForm = useCallback(() => {
    setShowServerFormModal(true);
  }, []);

  const handleCloseServerForm = useCallback(() => {
    setShowServerFormModal(false);
  }, []);

  // Handle server creation and auto-selection
  const handleServerSubmit = useCallback(async (serverData: Parameters<typeof addServer>[0]) => {
    try {
      const newServer = addServer(serverData);
      
      // Wait a brief moment to ensure server is properly added
      await new Promise(resolve => setTimeout(resolve, 100));
      
      setShowServerFormModal(false);
      
      // Auto-select the newly created server in the project creation form
      // This ensures seamless workflow continuity
      if (newServer && newServer.id) {
        updateProjectField('serverId', newServer.id);
      }
    } catch (error) {
      console.error('Failed to create server:', error);
      // Error is handled by the ServerForm component
    }
  }, [addServer, updateProjectField]);

  // Handle project card press
  const handleProjectPress = useCallback((projectId: string) => {
    navigate(`/project/${projectId}`);
  }, [navigate]);

  // Handle server addition notification from ProjectCreationModal
  const handleServerAdded = useCallback((server: { id: string; name: string }) => {
    // The auto-selection is already handled in handleServerSubmit
    // This callback could be used for additional UI feedback if needed
    console.log('Server added:', server.name);
  }, []);

  // Get projects with server data for rendering - with safe server data access
  const projectsWithServers = projects.map(project => {
    const server = servers.find(s => s.id === project.serverId);
    
    // Debug project and server data
    if (import.meta.env.DEV) {
      console.group(`🔍 Dashboard - Processing project: ${project?.name || 'Unknown'}`);
      console.log('Project:', project);
      console.log('Server found:', server);
      console.log('Server ID match:', project?.serverId, '->', server?.id);
      console.groupEnd();
    }
    
    // Safe server data handling - return null for missing servers instead of placeholder
    return {
      project,
      server: server || null, // Use null instead of fake server object
    };
  }).filter(({ server }) => server !== null) as Array<{ project: Project; server: Server }>;

  // Separate list of projects with missing servers for error display
  const projectsWithMissingServers = projects.filter(project => 
    !servers.find(s => s.id === project.serverId)
  );

  // Debug final project lists
  if (import.meta.env.DEV) {
    console.group('🔍 Dashboard - Final project lists');
    console.log('Projects with servers:', projectsWithServers);
    console.log('Projects with missing servers:', projectsWithMissingServers);
    console.log('Empty state preset:', hasProjects ? 'Not applicable' : EmptyStatePresets.noProjects(handleOpenCreateProject));
    console.groupEnd();
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50 dark:bg-gray-900">
      {/* App Bar */}
      <header className="sticky top-0 z-40 border-b border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
        <div className="container mx-auto max-w-md px-4">
          <div className="flex h-14 items-center justify-between">
            {/* Left side - Menu icon (placeholder for future navigation) */}
            <IconButton
              icon={Menu}
              onPress={() => {
                // Menu functionality not implemented yet - could show navigation drawer
                console.log('Menu pressed - navigation drawer would open here');
              }}
              size="sm"
              variant="ghost"
              aria-label="Open menu"
            />

            {/* Center - App title */}
            <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
              Pocket Agent
            </h1>

            {/* Right side - Theme toggle */}
            <IconButton
              icon={theme === 'dark' ? Sun : Moon}
              onPress={toggleTheme}
              size="sm"
              variant="ghost"
              aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
            />
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1">
        <div className="container mx-auto max-w-md p-4">
          {/* Projects Section Header */}
          <div className="mb-6">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
              Projects
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {hasProjects ? `${projects.length} project${projects.length === 1 ? '' : 's'}` : 'No projects yet'}
            </p>
          </div>

          {/* Projects List or Empty State */}
          <div className="space-y-4">
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="text-center">
                  <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600 dark:border-gray-600 dark:border-t-blue-400"></div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Loading projects...</p>
                </div>
              </div>
            ) : hasProjects ? (
              <>
                {/* Projects with missing servers warning */}
                {projectsWithMissingServers.length > 0 && (
                  <div className="mb-4 rounded-md border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-800 dark:bg-yellow-900/20">
                    <div className="flex items-start gap-2">
                      <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-yellow-600 dark:text-yellow-400" />
                      <div className="text-sm">
                        <p className="font-medium text-yellow-800 dark:text-yellow-200">
                          Server Connection Issues
                        </p>
                        <p className="mt-1 text-yellow-700 dark:text-yellow-300">
                          {projectsWithMissingServers.length} project{projectsWithMissingServers.length === 1 ? '' : 's'} cannot connect to their server{projectsWithMissingServers.length === 1 ? '' : 's'}. 
                          Please check your server connections.
                        </p>
                      </div>
                    </div>
                  </div>
                )}
                
                {/* Projects List with Error Boundaries */}
                {projectsWithServers.map(({ project, server }) => {
                  // Debug each ProjectCard props before rendering
                  if (import.meta.env.DEV) {
                    console.group(`🔍 Dashboard - Rendering ProjectCard for ${project?.name}`);
                    validateProps('ProjectCard', { project, server, onPress: handleProjectPress, onSettings: () => {} });
                    console.log('Project data:', project);
                    console.log('Server data:', server);
                    console.groupEnd();
                  }

                  return (
                    <DevErrorBoundary
                      key={project.id}
                      componentName={`ProjectCard-${project.name}`}
                    >
                      <ErrorBoundary
                        fallback={(error, resetError) => (
                          <div className="rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
                            <div className="flex items-start gap-2">
                              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-red-600 dark:text-red-400" />
                              <div className="flex-1 text-sm">
                                <p className="font-medium text-red-800 dark:text-red-200">
                                  Project Display Error
                                </p>
                                <p className="mt-1 text-red-700 dark:text-red-300">
                                  Unable to display project "{project.name}". 
                                </p>
                                <button
                                  onClick={resetError}
                                  className="mt-2 text-xs text-red-600 underline hover:text-red-500 dark:text-red-400 dark:hover:text-red-300"
                                >
                                  Try again
                                </button>
                              </div>
                            </div>
                          </div>
                        )}
                      >
                        <ProjectCard
                          project={project}
                          server={server}
                          onPress={() => handleProjectPress(project.id)}
                          onSettings={() => {
                            // Project settings functionality - would open settings modal
                            console.log('Project settings for:', project.id);
                            // Future: Open project settings modal or navigate to settings page
                          }}
                        />
                      </ErrorBoundary>
                    </DevErrorBoundary>
                  );
                })}
              </>
            ) : (
              /* Empty State */
              (() => {
                const emptyStateProps = EmptyStatePresets.noProjects(handleOpenCreateProject);
                if (import.meta.env.DEV) {
                  console.group('🔍 Dashboard - Rendering EmptyState');
                  validateProps('EmptyState', emptyStateProps);
                  console.log('EmptyState props:', emptyStateProps);
                  console.groupEnd();
                }
                return (
                  <DevErrorBoundary componentName="EmptyState">
                    <EmptyState {...emptyStateProps} />
                  </DevErrorBoundary>
                );
              })()
            )}
          </div>
        </div>
      </main>

      {/* Floating Action Button */}
      {hasProjects && (() => {
        const fabProps = {
          onPress: handleOpenCreateProject,
          ariaLabel: "Create new project",
          position: "bottom-right" as const,
        };
        if (import.meta.env.DEV) {
          console.group('🔍 Dashboard - Rendering FAB');
          validateProps('FAB', fabProps);
          console.log('FAB props:', fabProps);
          console.groupEnd();
        }
        return (
          <DevErrorBoundary componentName="FAB">
            <FAB {...fabProps} />
          </DevErrorBoundary>
        );
      })()}

      {/* Project Creation Modal with Error Boundary */}
      <ErrorBoundary
        fallback={(error, resetError) => (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="w-full max-w-md rounded-lg bg-white p-6 dark:bg-gray-800">
              <div className="text-center">
                <AlertCircle className="mx-auto mb-4 h-12 w-12 text-red-500" />
                <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
                  Project Creation Error
                </h3>
                <p className="mb-4 text-sm text-gray-600 dark:text-gray-400">
                  There was an error with the project creation form.
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={resetError}
                    className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
                  >
                    Try Again
                  </button>
                  <button
                    onClick={handleCloseCreateProject}
                    className="flex-1 rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      >
        <ProjectCreationModal
          isVisible={showCreateProjectModal}
          onClose={handleCloseCreateProject}
          onAddServer={handleOpenServerForm}
          onServerAdded={handleServerAdded}
        />
      </ErrorBoundary>

      {/* Server Form Modal with Error Boundary */}
      {showServerFormModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md">
            <ErrorBoundary
              fallback={(error, resetError) => (
                <div className="rounded-lg bg-white p-6 dark:bg-gray-800">
                  <div className="text-center">
                    <AlertCircle className="mx-auto mb-4 h-12 w-12 text-red-500" />
                    <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
                      Server Form Error
                    </h3>
                    <p className="mb-4 text-sm text-gray-600 dark:text-gray-400">
                      There was an error with the server creation form.
                    </p>
                    <div className="flex gap-2">
                      <button
                        onClick={resetError}
                        className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
                      >
                        Try Again
                      </button>
                      <button
                        onClick={handleCloseServerForm}
                        className="flex-1 rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                      >
                        Close
                      </button>
                    </div>
                  </div>
                </div>
              )}
            >
              <ServerForm
                onSubmit={handleServerSubmit}
                onCancel={handleCloseServerForm}
              />
            </ErrorBoundary>
          </div>
        </div>
      )}
    </div>
  );
};

// Wrap the Dashboard with development error boundary
export const Dashboard: React.FC = () => {
  if (import.meta.env.DEV) {
    return (
      <DevErrorBoundary componentName="Dashboard">
        <DashboardInner />
      </DevErrorBoundary>
    );
  }
  
  return <DashboardInner />;
};
