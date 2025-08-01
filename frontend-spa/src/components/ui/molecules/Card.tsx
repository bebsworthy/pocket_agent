import React, { forwardRef, useState, useCallback, useRef, useEffect } from 'react';
import { cn } from '../../../utils/cn';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  onPress?: () => void;
  /** 
   * External control of pressed state. Only use this if you need to sync
   * pressed state with external state management. For most use cases,
   * the internal pressed state is sufficient.
   */
  pressed?: boolean;
  padding?: 'sm' | 'md' | 'lg';
  variant?: 'default' | 'outlined' | 'elevated';
}

const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ 
    className, 
    children, 
    onPress, 
    pressed = false, 
    padding = 'md',
    variant = 'default',
    ...props 
  }, ref) => {
    const [isPressed, setIsPressed] = useState(false);
    const touchStartPos = useRef<{ x: number; y: number } | null>(null);

    const baseClasses = 'rounded-lg transition-all duration-150 ease-out min-h-11';
    
    const variants = {
      default: 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700',
      outlined: 'bg-transparent border-2 border-gray-200 dark:border-gray-700',
      elevated: 'bg-white dark:bg-gray-800 shadow-lg border-0'
    };

    const paddings = {
      sm: 'p-3',
      md: 'p-4',
      lg: 'p-6'
    };

    const interactiveClasses = onPress ? [
      'cursor-pointer',
      'select-none',
      'active:scale-[0.98]',
      'hover:shadow-md',
      'focus-visible:outline-none',
      'focus-visible:ring-2',
      'focus-visible:ring-blue-500',
      'focus-visible:ring-offset-2',
      'focus-visible:ring-offset-white',
      'dark:focus-visible:ring-offset-gray-800',
      isPressed || pressed ? 'shadow-sm scale-[0.98] bg-gray-50 dark:bg-gray-700' : ''
    ].filter(Boolean).join(' ') : '';

    const handleTouchStart = useCallback((event: React.TouchEvent) => {
      if (onPress) {
        const touch = event.touches[0];
        touchStartPos.current = { x: touch.clientX, y: touch.clientY };
        setIsPressed(true);
      }
    }, [onPress]);

    const handleTouchMove = useCallback((event: React.TouchEvent) => {
      if (onPress && touchStartPos.current) {
        const touch = event.touches[0];
        const deltaX = Math.abs(touch.clientX - touchStartPos.current.x);
        const deltaY = Math.abs(touch.clientY - touchStartPos.current.y);
        
        // Cancel press if touch moves too far (more than 10px in any direction)
        if (deltaX > 10 || deltaY > 10) {
          setIsPressed(false);
          touchStartPos.current = null;
        }
      }
    }, [onPress]);

    const handleTouchEnd = useCallback(() => {
      if (onPress) {
        setIsPressed(false);
        touchStartPos.current = null;
      }
    }, [onPress]);

    const handleClick = useCallback(() => {
      if (onPress) {
        onPress();
      }
    }, [onPress]);

    const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
      if (onPress && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault();
        onPress();
      }
    }, [onPress]);

    // Cleanup effect to reset press state on unmount or when onPress changes
    useEffect(() => {
      return () => {
        touchStartPos.current = null;
      };
    }, [onPress]);

    return (
      <div
        ref={ref}
        className={cn(
          baseClasses,
          variants[variant],
          paddings[padding],
          interactiveClasses,
          className
        )}
        onClick={handleClick}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchEnd}
        onKeyDown={handleKeyDown}
        tabIndex={onPress ? 0 : undefined}
        role={onPress ? 'button' : undefined}
        aria-pressed={onPress ? (isPressed || pressed) : undefined}
        {...props}
      >
        {children}
      </div>
    );
  }
);

Card.displayName = 'Card';

// Card sub-components for content organization
export interface CardHeaderProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

const CardHeader = forwardRef<HTMLDivElement, CardHeaderProps>(
  ({ className, children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn('flex flex-col space-y-1.5 mb-4', className)}
        {...props}
      >
        {children}
      </div>
    );
  }
);

CardHeader.displayName = 'CardHeader';

export interface CardContentProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

const CardContent = forwardRef<HTMLDivElement, CardContentProps>(
  ({ className, children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn('space-y-2', className)}
        {...props}
      >
        {children}
      </div>
    );
  }
);

CardContent.displayName = 'CardContent';

export interface CardFooterProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

const CardFooter = forwardRef<HTMLDivElement, CardFooterProps>(
  ({ className, children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn('flex items-center justify-between mt-4 pt-4 border-t border-gray-200 dark:border-gray-700', className)}
        {...props}
      >
        {children}
      </div>
    );
  }
);

CardFooter.displayName = 'CardFooter';

export { Card, CardHeader, CardContent, CardFooter };