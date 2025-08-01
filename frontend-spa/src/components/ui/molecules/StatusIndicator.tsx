import React from 'react';
import { cn } from '../../../utils/cn';

export interface StatusIndicatorProps {
  status: 'connected' | 'connecting' | 'disconnected' | 'error';
  label?: string;
  size?: 'sm' | 'md' | 'lg';
  showIcon?: boolean;
  showLabel?: boolean;
  variant?: 'dot' | 'badge' | 'full';
  className?: string;
}

// Simple SVG icons as React components
const WifiIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m0 0a.5.5 0 11-.01 0h.01zm-6.364-8.364a13 13 0 0118.728 0M2.343 5.343a21 21 0 0119.314 0"
    />
  </svg>
);

const WifiOffIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636m12.728 12.728L18 21l-2-2m0 0L14 17m0 0l-2-2m0 0L10 13m0 0l-2-2m0 0L6 9"
    />
  </svg>
);

const LoaderIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24">
    <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" opacity="0.25" />
    <path
      fill="currentColor"
      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
    />
  </svg>
);

const AlertIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.664-.833-2.464 0L4.268 18.5c-.77.833.192 2.5 1.732 2.5z"
    />
  </svg>
);

const StatusIndicator: React.FC<StatusIndicatorProps> = ({
  status,
  label,
  size = 'md',
  showIcon = true,
  showLabel = true,
  variant = 'full',
  className,
}) => {
  const statusConfig = {
    connected: {
      color: 'text-green-600 dark:text-green-400',
      bgColor: 'bg-green-100 dark:bg-green-900/30',
      borderColor: 'border-green-200 dark:border-green-800',
      dotColor: 'bg-green-500',
      icon: WifiIcon,
      defaultLabel: 'Connected',
    },
    connecting: {
      color: 'text-yellow-600 dark:text-yellow-400',
      bgColor: 'bg-yellow-100 dark:bg-yellow-900/30',
      borderColor: 'border-yellow-200 dark:border-yellow-800',
      dotColor: 'bg-yellow-500 animate-pulse',
      icon: LoaderIcon,
      defaultLabel: 'Connecting...',
    },
    disconnected: {
      color: 'text-gray-600 dark:text-gray-400',
      bgColor: 'bg-gray-100 dark:bg-gray-900/30',
      borderColor: 'border-gray-200 dark:border-gray-800',
      dotColor: 'bg-gray-500',
      icon: WifiOffIcon,
      defaultLabel: 'Disconnected',
    },
    error: {
      color: 'text-red-600 dark:text-red-400',
      bgColor: 'bg-red-100 dark:bg-red-900/30',
      borderColor: 'border-red-200 dark:border-red-800',
      dotColor: 'bg-red-500',
      icon: AlertIcon,
      defaultLabel: 'Connection Error',
    },
  };

  const sizeConfig = {
    sm: {
      dotSize: 'h-2 w-2',
      iconSize: 'h-3 w-3',
      textSize: 'text-xs',
      padding: 'px-2 py-1',
      gap: 'gap-1',
    },
    md: {
      dotSize: 'h-3 w-3',
      iconSize: 'h-4 w-4',
      textSize: 'text-sm',
      padding: 'px-3 py-1.5',
      gap: 'gap-2',
    },
    lg: {
      dotSize: 'h-4 w-4',
      iconSize: 'h-5 w-5',
      textSize: 'text-base',
      padding: 'px-4 py-2',
      gap: 'gap-2',
    },
  };

  const config = statusConfig[status];
  const sizes = sizeConfig[size];
  const displayLabel = label || config.defaultLabel;
  const IconComponent = config.icon;

  // Dot variant - just a colored dot
  if (variant === 'dot') {
    return (
      <div
        className={cn('inline-flex items-center', sizes.gap, className)}
        role="status"
        aria-label={`Status: ${displayLabel}`}
      >
        <div className={cn('rounded-full', config.dotColor, sizes.dotSize)} />
        {showLabel && <span className={cn(sizes.textSize, config.color)}>{displayLabel}</span>}
      </div>
    );
  }

  // Badge variant - styled background with border
  if (variant === 'badge') {
    return (
      <div
        className={cn(
          'inline-flex items-center rounded-full border',
          config.bgColor,
          config.borderColor,
          sizes.padding,
          sizes.gap,
          className
        )}
        role="status"
        aria-label={`Status: ${displayLabel}`}
      >
        {showIcon && (
          <IconComponent
            className={cn(sizes.iconSize, config.color, status === 'connecting' && 'animate-spin')}
          />
        )}
        {showLabel && (
          <span className={cn(sizes.textSize, config.color, 'font-medium')}>{displayLabel}</span>
        )}
      </div>
    );
  }

  // Full variant - icon + text without background (default)
  return (
    <div
      className={cn('inline-flex items-center', sizes.gap, className)}
      role="status"
      aria-label={`Status: ${displayLabel}`}
    >
      {showIcon && (
        <IconComponent
          className={cn(sizes.iconSize, config.color, status === 'connecting' && 'animate-spin')}
        />
      )}
      {showLabel && <span className={cn(sizes.textSize, config.color)}>{displayLabel}</span>}
    </div>
  );
};

StatusIndicator.displayName = 'StatusIndicator';

export { StatusIndicator };
