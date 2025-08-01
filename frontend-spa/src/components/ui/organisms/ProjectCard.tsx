import React, { useMemo } from 'react';
import { Card, CardContent } from '../molecules/Card';
import { StatusIndicator } from '../molecules/StatusIndicator';
import { IconButton } from '../atoms/IconButton';
import { Project, Server, ConnectionStatus } from '../../../types/models';
import { ArrowRight, MoreVertical, Folder } from 'lucide-react';

export interface ProjectCardProps {
  project: Project;
  server: Server;
  onPress: () => void;
  onDisconnect?: () => void;
  onSettings?: () => void;
}

/**
 * ProjectCard - An organism component for displaying project information
 * with server connection status and action buttons. Optimized for mobile touch.
 */
export function ProjectCard({
  project,
  server,
  onPress,
  onDisconnect,
  onSettings,
}: ProjectCardProps) {
  // Determine connection status
  const connectionStatus: ConnectionStatus = server.isConnected ? 'connected' : 'disconnected';

  // Format last active time for display (memoized for performance)
  const formatLastActive = useMemo(() => {
    return (dateString: string) => {
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
  }, []); // Empty dependency array since function logic is static

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
            <StatusIndicator status={connectionStatus} size="sm" label={server.name} />
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
            {server.isConnected && onDisconnect && (
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

        {/* Connection status banner for disconnected state */}
        {connectionStatus === 'disconnected' && (
          <div className="mt-3 rounded-md border border-red-200 bg-red-50 p-2 dark:border-red-800 dark:bg-red-900/20">
            <p className="text-sm text-red-700 dark:text-red-300">
              Connection failed. Tap to retry.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
