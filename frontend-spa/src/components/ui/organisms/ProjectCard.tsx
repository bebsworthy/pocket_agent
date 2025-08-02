import React from 'react';
import { Card, CardContent } from '../molecules/Card';
import { IconButton } from '../atoms/IconButton';
import { Project, Server, ConnectionStatus } from '../../../types/models';
import { ArrowRight, MoreVertical, Folder, Circle } from 'lucide-react';
import { useServerConnectionStatus } from '../../../store/hooks/useServers';

export interface ProjectCardProps {
  project: Project;
  server: Server;
  onPress: () => void;
  onDisconnect?: () => void;
  onSettings?: () => void;
}

/**
 * Utility function to get connection status icon with appropriate colors
 * Based on design.md specifications:
 * - Green: Connected server
 * - Yellow: Connecting to server  
 * - Gray: Disconnected server
 */
const getConnectionStatusIcon = (status: ConnectionStatus): React.ReactNode => {
  switch (status) {
    case 'connected':
      return <Circle className="w-3 h-3 fill-green-500 text-green-500" />;
    case 'connecting':
      return <Circle className="w-3 h-3 fill-yellow-500 text-yellow-500" />;
    case 'error':
      return <Circle className="w-3 h-3 fill-red-500 text-red-500" />;
    case 'disconnected':
    default:
      return <Circle className="w-3 h-3 fill-gray-400 text-gray-400" />;
  }
};

/**
 * Format last active time for display
 * Moved outside component to prevent recreation on every render
 */
const formatLastActive = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffHours / 24);

  if (diffHours < 1) return 'Active now';
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
};

/**
 * ProjectCard - An organism component for displaying project information
 * with server connection status and action buttons. Optimized for mobile touch.
 * Memoized to prevent unnecessary re-renders when parent state changes.
 */
export const ProjectCard = React.memo<ProjectCardProps>(function ProjectCard({
  project,
  server,
  onPress,
  onDisconnect,
  onSettings,
}) {
  // Get real-time connection status from hook, with fallback
  const { status: realTimeStatus } = useServerConnectionStatus(server.id);
  const connectionStatus: ConnectionStatus = realTimeStatus || 'disconnected';
  
  // Track previous status for announcements
  const [previousStatus, setPreviousStatus] = React.useState<ConnectionStatus>(connectionStatus);
  const [statusAnnouncement, setStatusAnnouncement] = React.useState<string>('');
  
  // Announce connection status changes to screen readers
  React.useEffect(() => {
    if (previousStatus !== connectionStatus) {
      const statusMessages = {
        connected: `${project.name} is now connected to ${server.name}`,
        connecting: `${project.name} is connecting to ${server.name}`,
        disconnected: `${project.name} has disconnected from ${server.name}`,
        error: `${project.name} connection to ${server.name} failed`
      };
      
      setStatusAnnouncement(statusMessages[connectionStatus]);
      setPreviousStatus(connectionStatus);
      
      // Clear announcement after screen reader has time to read it
      const timer = setTimeout(() => setStatusAnnouncement(''), 3000);
      return () => clearTimeout(timer);
    }
  }, [connectionStatus, previousStatus, project.name, server.name]);

  const handleCardPress = () => {
    onPress();
  };

  const handleDisconnect = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDisconnect?.();
  };

  const handleSettings = () => {
    onSettings?.();
  };

  return (
    <>
      {/* Live region for screen reader announcements */}
      {statusAnnouncement && (
        <div
          className="sr-only"
          aria-live="polite"
          aria-atomic="true"
          role="status"
        >
          {statusAnnouncement}
        </div>
      )}
      
      <Card
        onPress={handleCardPress}
        className="relative"
        onClick={(e: React.MouseEvent) => {
          // Prevent card press when clicking action buttons
          if ((e.target as HTMLElement).closest('[data-action-button]')) {
            e.stopPropagation();
            return;
          }
        }}
      >
      <CardContent className="p-4">
        {/* Header with project name and status */}
        <div className="mb-3 flex items-center justify-between">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <Folder className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="min-w-0 flex-1">
              <h3 className="truncate font-semibold text-gray-900 dark:text-gray-100">
                {project.name}
              </h3>
              <p className="truncate text-sm text-gray-500 dark:text-gray-400">{project.path}</p>
            </div>
          </div>

          <div className="flex flex-shrink-0 items-center gap-2">
            <div className="flex items-center gap-2">
              {getConnectionStatusIcon(connectionStatus)}
              <span className="text-xs text-gray-600 dark:text-gray-400">
                {server.name}
              </span>
            </div>
            {onSettings && (
              <IconButton
                icon={MoreVertical}
                onPress={handleSettings}
                size="sm"
                variant="ghost"
                aria-label="Project settings"
                data-action-button
                className="opacity-60 hover:opacity-100"
              />
            )}
          </div>
        </div>

        {/* Server and connection info */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
            <span className="truncate">{server.name}</span>
            <span className="text-gray-400 dark:text-gray-500">•</span>
            <span className="flex-shrink-0">{formatLastActive(project.lastActive)}</span>
          </div>

          <div className="flex items-center gap-1">
            {connectionStatus === 'connected' && onDisconnect && (
              <button
                onClick={handleDisconnect}
                data-action-button
                className="rounded px-2 py-1 text-xs text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
                aria-label="Disconnect from server"
              >
                Disconnect
              </button>
            )}
            <ArrowRight className="h-4 w-4 flex-shrink-0 text-gray-400 dark:text-gray-500" />
          </div>
        </div>

        {/* Connection status banner for non-connected states */}
        {connectionStatus === 'disconnected' && (
          <div className="mt-3 rounded-md border border-red-200 bg-red-50 p-2 dark:border-red-800 dark:bg-red-900/20">
            <p className="text-sm text-red-700 dark:text-red-300">
              Connection failed. Tap to retry.
            </p>
          </div>
        )}
        {connectionStatus === 'connecting' && (
          <div className="mt-3 rounded-md border border-yellow-200 bg-yellow-50 p-2 dark:border-yellow-800 dark:bg-yellow-900/20">
            <p className="text-sm text-yellow-700 dark:text-yellow-300">
              Connecting to server...
            </p>
          </div>
        )}
        {connectionStatus === 'error' && (
          <div className="mt-3 rounded-md border border-red-200 bg-red-50 p-2 dark:border-red-800 dark:bg-red-900/20">
            <p className="text-sm text-red-700 dark:text-red-300">
              Connection error. Check server status.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
    </>
  );
});
