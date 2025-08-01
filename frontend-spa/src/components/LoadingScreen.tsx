/**
 * LoadingScreen component for lazy-loaded routes and async operations
 * Provides mobile-optimized loading states with accessibility features
 */

import { useEffect, useState } from 'react';
import { cn } from '../utils/cn';
import { Loader2, Wifi, WifiOff } from 'lucide-react';

interface LoadingScreenProps {
  /** Loading message to display */
  message?: string;
  /** Show detailed loading status */
  showDetails?: boolean;
  /** Timeout in milliseconds before showing fallback */
  timeout?: number;
  /** Custom loading animation */
  animation?: 'spinner' | 'pulse' | 'skeleton' | 'dots';
  /** Size variant */
  size?: 'sm' | 'md' | 'lg' | 'fullscreen';
  /** Show network status */
  showNetworkStatus?: boolean;
  /** Background overlay */
  overlay?: boolean;
  /** Custom className */
  className?: string;
  /** Callback when timeout is reached */
  onTimeout?: () => void;
}

export function LoadingScreen({
  message = 'Loading...',
  showDetails = false,
  timeout = 10000, // 10 seconds
  animation = 'spinner',
  size = 'fullscreen',
  showNetworkStatus = false,
  overlay = false,
  className,
  onTimeout,
}: LoadingScreenProps) {
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [hasTimedOut, setHasTimedOut] = useState(false);
  const [loadingDots, setLoadingDots] = useState('');

  // Monitor network status
  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Timeout handling
  useEffect(() => {
    if (timeout <= 0) return;

    const timer = setTimeout(() => {
      setHasTimedOut(true);
      onTimeout?.();
    }, timeout);

    return () => clearTimeout(timer);
  }, [timeout, onTimeout]);

  // Animated dots for loading text
  useEffect(() => {
    if (animation !== 'dots') return;

    const interval = setInterval(() => {
      setLoadingDots(dots => {
        if (dots.length >= 3) return '';
        return dots + '.';
      });
    }, 500);

    return () => clearInterval(interval);
  }, [animation]);

  const containerClasses = cn(
    'flex flex-col items-center justify-center',
    {
      // Size variants
      'p-4': size === 'sm',
      'p-6': size === 'md',
      'p-8': size === 'lg',
      'min-h-screen p-4': size === 'fullscreen',

      // Overlay
      'fixed inset-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm': overlay,

      // Background for fullscreen
      'bg-gray-50 dark:bg-gray-900': size === 'fullscreen' && !overlay,
    },
    className
  );

  const SpinnerAnimation = () => (
    <Loader2
      className={cn('animate-spin text-blue-600 dark:text-blue-400', {
        'h-6 w-6': size === 'sm',
        'h-8 w-8': size === 'md',
        'h-12 w-12': size === 'lg' || size === 'fullscreen',
      })}
      aria-hidden="true"
    />
  );

  const PulseAnimation = () => (
    <div className="flex space-x-2">
      {[0, 1, 2].map(i => (
        <div
          key={i}
          className={cn('animate-pulse rounded-full bg-blue-600 dark:bg-blue-400', {
            'h-2 w-2': size === 'sm',
            'h-3 w-3': size === 'md',
            'h-4 w-4': size === 'lg' || size === 'fullscreen',
          })}
          style={{
            animationDelay: `${i * 0.2}s`,
            animationDuration: '1s',
          }}
          aria-hidden="true"
        />
      ))}
    </div>
  );

  const SkeletonAnimation = () => (
    <div className="w-full max-w-sm space-y-3">
      {[1, 2, 3].map(i => (
        <div key={i} className="flex animate-pulse space-x-3" aria-hidden="true">
          <div className="h-4 w-4 rounded-full bg-gray-300 dark:bg-gray-600" />
          <div className="flex-1 space-y-2">
            <div className="h-3 w-3/4 rounded bg-gray-300 dark:bg-gray-600" />
            <div className="h-3 w-1/2 rounded bg-gray-300 dark:bg-gray-600" />
          </div>
        </div>
      ))}
    </div>
  );

  const DotsAnimation = () => (
    <div className="flex items-center space-x-1">
      <span className="text-lg font-medium text-gray-700 dark:text-gray-300">{message}</span>
      <span
        className="w-6 text-left text-lg font-medium text-blue-600 dark:text-blue-400"
        aria-live="polite"
      >
        {loadingDots}
      </span>
    </div>
  );

  const renderAnimation = () => {
    switch (animation) {
      case 'pulse':
        return <PulseAnimation />;
      case 'skeleton':
        return <SkeletonAnimation />;
      case 'dots':
        return <DotsAnimation />;
      default:
        return <SpinnerAnimation />;
    }
  };

  return (
    <div className={containerClasses} role="status" aria-live="polite">
      {/* Loading Animation */}
      <div className="mb-4">{renderAnimation()}</div>

      {/* Loading Message */}
      {animation !== 'dots' && (
        <div className="space-y-2 text-center">
          <h2
            className={cn('font-medium text-gray-900 dark:text-white', {
              'text-sm': size === 'sm',
              'text-base': size === 'md',
              'text-lg': size === 'lg' || size === 'fullscreen',
            })}
          >
            {message}
          </h2>

          {showDetails && (
            <p
              className={cn('text-gray-600 dark:text-gray-400', {
                'text-xs': size === 'sm',
                'text-sm': size === 'md' || size === 'lg' || size === 'fullscreen',
              })}
            >
              Please wait while we load your content
            </p>
          )}
        </div>
      )}

      {/* Network Status */}
      {showNetworkStatus && (
        <div className="mt-4 flex items-center space-x-2 text-sm">
          {isOnline ? (
            <>
              <Wifi className="h-4 w-4 text-green-600 dark:text-green-400" />
              <span className="text-green-600 dark:text-green-400">Connected</span>
            </>
          ) : (
            <>
              <WifiOff className="h-4 w-4 text-red-600 dark:text-red-400" />
              <span className="text-red-600 dark:text-red-400">Offline</span>
            </>
          )}
        </div>
      )}

      {/* Timeout Message */}
      {hasTimedOut && (
        <div className="mt-4 rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-center dark:border-yellow-800 dark:bg-yellow-900/20">
          <p className="text-sm text-yellow-800 dark:text-yellow-200">
            This is taking longer than expected. Please check your connection.
          </p>
        </div>
      )}

      {/* Screen Reader Text */}
      <div className="sr-only" aria-live="assertive">
        {hasTimedOut ? 'Loading is taking longer than expected' : `Loading content: ${message}`}
      </div>
    </div>
  );
}

// Specialized loading screens for different contexts

export function RouteLoadingScreen() {
  return (
    <LoadingScreen
      message="Loading page..."
      showDetails
      timeout={8000}
      animation="spinner"
      size="fullscreen"
      showNetworkStatus
    />
  );
}

export function ComponentLoadingScreen({ message = 'Loading...' }: { message?: string }) {
  return <LoadingScreen message={message} animation="pulse" size="md" className="py-8" />;
}

export function InlineLoadingScreen({ message = 'Loading...' }: { message?: string }) {
  return <LoadingScreen message={message} animation="dots" size="sm" className="py-2" />;
}

export function OverlayLoadingScreen({ message = 'Loading...' }: { message?: string }) {
  return <LoadingScreen message={message} animation="spinner" size="lg" overlay showDetails />;
}

export default LoadingScreen;
