import React, { useRef, useEffect } from 'react';
import { Button } from '../atoms/Button';
import { LucideIcon, FolderOpen, Server, Wifi, Plus } from 'lucide-react';

export interface EmptyStateAction {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary';
}

export interface EmptyStateProps {
  /** Title text displayed prominently */
  title: string;
  /** Description text explaining the empty state */
  description: string;
  /** Optional icon to display above the title */
  icon?: LucideIcon;
  /** Primary action button */
  action?: EmptyStateAction;
  /** Optional secondary action button */
  secondaryAction?: EmptyStateAction;
  /** Size variant for different contexts */
  size?: 'sm' | 'md' | 'lg';
  /** Predefined empty state types for common scenarios */
  type?: 'projects' | 'servers' | 'connection' | 'search' | 'generic';
}

/**
 * EmptyState - An organism component for displaying empty states with
 * appropriate messaging and call-to-action buttons. Optimized for mobile.
 */
export function EmptyState({
  title,
  description,
  icon: CustomIcon,
  action,
  secondaryAction,
  size = 'md',
  type = 'generic'
}: EmptyStateProps) {
  const primaryActionRef = useRef<HTMLButtonElement>(null);

  // Auto-focus primary action for improved accessibility
  useEffect(() => {
    if (action && primaryActionRef.current) {
      primaryActionRef.current.focus();
    }
  }, [action]);
  // Determine icon based on type if not provided
  const getDefaultIcon = (): LucideIcon => {
    switch (type) {
      case 'projects':
        return FolderOpen;
      case 'servers':
        return Server;
      case 'connection':
        return Wifi;
      case 'search':
        return FolderOpen;
      default:
        return Plus;
    }
  };

  const Icon = CustomIcon || getDefaultIcon();

  // Size configurations
  const sizeConfig = {
    sm: {
      container: 'py-8 px-4',
      icon: 'h-12 w-12',
      title: 'text-lg',
      description: 'text-sm',
      spacing: 'space-y-3'
    },
    md: {
      container: 'py-12 px-6',
      icon: 'h-16 w-16',
      title: 'text-xl',
      description: 'text-base',
      spacing: 'space-y-4'
    },
    lg: {
      container: 'py-16 px-8',
      icon: 'h-20 w-20',
      title: 'text-2xl',
      description: 'text-lg',
      spacing: 'space-y-6'
    }
  };

  const config = sizeConfig[size];

  // Get appropriate colors based on type
  const getIconColors = () => {
    switch (type) {
      case 'projects':
        return 'text-blue-400 dark:text-blue-300';
      case 'servers':
        return 'text-green-400 dark:text-green-300';
      case 'connection':
        return 'text-orange-400 dark:text-orange-300';
      case 'search':
        return 'text-purple-400 dark:text-purple-300';
      default:
        return 'text-gray-400 dark:text-gray-300';
    }
  };

  return (
    <div className={`flex flex-col items-center justify-center text-center ${config.container}`}>
      <div className={`${config.spacing}`}>
        {/* Icon */}
        <div className="flex justify-center">
          <div className="rounded-full bg-gray-100 dark:bg-gray-800 p-4">
            <Icon 
              className={`${config.icon} ${getIconColors()}`}
              strokeWidth={1.5}
            />
          </div>
        </div>

        {/* Title and Description */}
        <div className="space-y-2">
          <h3 className={`font-semibold text-gray-900 dark:text-gray-100 ${config.title}`}>
            {title}
          </h3>
          <p className={`text-gray-500 dark:text-gray-400 max-w-sm mx-auto leading-relaxed ${config.description}`}>
            {description}
          </p>
        </div>

        {/* Action Buttons */}
        {(action || secondaryAction) && (
          <div className="space-y-3 w-full max-w-xs mx-auto">
            {action && (
              <Button
                ref={primaryActionRef}
                variant={action.variant || 'primary'}
                size={size === 'sm' ? 'sm' : 'md'}
                fullWidth
                onPress={action.onPress}
              >
                {action.label}
              </Button>
            )}
            {secondaryAction && (
              <Button
                variant={secondaryAction.variant || 'secondary'}
                size={size === 'sm' ? 'sm' : 'md'}
                fullWidth
                onPress={secondaryAction.onPress}
              >
                {secondaryAction.label}
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// Predefined empty state configurations for common use cases
export const EmptyStatePresets = {
  noProjects: (onAddProject: () => void): EmptyStateProps => ({
    type: 'projects',
    title: 'No projects yet',
    description: 'Create your first project to start building and managing your code.',
    action: {
      label: 'Add Project',
      onPress: onAddProject
    }
  }),

  noServers: (onAddServer: () => void): EmptyStateProps => ({
    type: 'servers',
    title: 'No servers configured',
    description: 'Add a server connection to start managing your projects remotely.',
    action: {
      label: 'Add Server',
      onPress: onAddServer
    }
  }),

  connectionLost: (onRetry: () => void): EmptyStateProps => ({
    type: 'connection',
    title: 'Connection lost',
    description: 'Unable to connect to the server. Check your network and try again.',
    action: {
      label: 'Retry Connection',
      onPress: onRetry
    }
  }),

  noSearchResults: (query: string, onClear: () => void): EmptyStateProps => ({
    type: 'search',
    title: 'No results found',
    description: `No projects match "${query}". Try adjusting your search terms.`,
    action: {
      label: 'Clear Search',
      onPress: onClear,
      variant: 'secondary'
    }
  }),

  projectsLoading: (): EmptyStateProps => ({
    type: 'projects',
    title: 'Loading projects...',
    description: 'Please wait while we fetch your projects.',
    size: 'sm'
  }),

  offlineMode: (onRefresh: () => void): EmptyStateProps => ({
    type: 'connection',
    title: 'You\'re offline',
    description: 'Some features may be limited. Connect to the internet to sync your projects.',
    action: {
      label: 'Refresh',
      onPress: onRefresh,
      variant: 'secondary'
    }
  })
};

// Helper function to create empty states with presets
export function createEmptyState(preset: keyof typeof EmptyStatePresets, ...args: unknown[]): EmptyStateProps {
  const presetFn = EmptyStatePresets[preset] as (...args: unknown[]) => EmptyStateProps;
  return presetFn(...args);
}