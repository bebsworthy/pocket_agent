import React from 'react';
import { Plus } from 'lucide-react';
import { cn } from '../../../utils/cn';

export interface FABProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  icon?: React.ComponentType<{ className?: string }> | React.ReactNode;
  position?: 'bottom-right' | 'bottom-left' | 'bottom-center';
  color?: 'primary' | 'secondary';
  size?: 'sm' | 'md' | 'lg';
  onPress: () => void;
  ariaLabel?: string;
}

const FAB = React.forwardRef<HTMLButtonElement, FABProps>(
  (
    {
      className,
      icon = Plus,
      position = 'bottom-right',
      color = 'primary',
      size = 'md',
      disabled = false,
      onPress,
      ariaLabel = 'Create new item',
      ...props
    },
    ref
  ) => {
    const baseClasses =
      'fixed z-50 inline-flex items-center justify-center font-medium transition-all duration-200 rounded-full shadow-lg hover:shadow-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none active:scale-95 no-tap-highlight';

    // Positioning classes with 24px margins
    const positions = {
      'bottom-right': 'bottom-6 right-6', // 24px margins
      'bottom-left': 'bottom-6 left-6',
      'bottom-center': 'bottom-6 left-1/2 -translate-x-1/2',
    };

    // Color variants
    const colors = {
      primary:
        'bg-primary-600 text-white hover:bg-primary-700 focus-visible:ring-primary-600 active:bg-primary-800',
      secondary:
        'bg-gray-600 text-white hover:bg-gray-700 focus-visible:ring-gray-600 active:bg-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600',
    };

    // Size variants - ensuring 56px default size and 44px+ touch targets
    const sizes = {
      sm: 'h-12 w-12 min-h-[48px] min-w-[48px]', // 48px for small, still accessible
      md: 'h-14 w-14 min-h-[56px] min-w-[56px]', // 56px default size
      lg: 'h-16 w-16 min-h-[64px] min-w-[64px]', // 64px for large
    };

    // Icon sizing based on FAB size
    const iconSizes = {
      sm: 'h-5 w-5',
      md: 'h-6 w-6',
      lg: 'h-7 w-7',
    };

    const handleClick = () => {
      if (!disabled) {
        onPress();
      }
    };

    const renderIcon = () => {
      if (React.isValidElement(icon)) {
        return React.cloneElement(icon as React.ReactElement, {
          className: cn(iconSizes[size], (icon as React.ReactElement).props?.className),
        });
      }

      // Handle component-based icons (like Lucide, Heroicons, etc.)
      if (typeof icon === 'function') {
        const IconComponent = icon as React.ComponentType<{ className?: string }>;
        return <IconComponent className={iconSizes[size]} />;
      }

      // Handle any other potential component types or objects
      if (icon && typeof icon === 'object' && !React.isValidElement(icon)) {
        // Check if it's a component-like object (forwardRef, etc.)
        if ('render' in icon && typeof (icon as any).render === 'function') {
          const IconComponent = icon as unknown as React.ComponentType<{ className?: string }>;
          return <IconComponent className={iconSizes[size]} />;
        }
      }

      // Defensive fallback - if icon is not a valid React child, render nothing
      if (icon && typeof icon === 'object') {
        if (import.meta.env.DEV) {
          console.warn('FAB: Invalid icon type provided. Expected React element, component function, or primitive value.', icon);
        }
        return null;
      }

      // For primitive values (string, number, etc.) - though unlikely for icons
      return icon;
    };

    return (
      <button
        className={cn(
          baseClasses,
          positions[position],
          colors[color],
          sizes[size],
          className
        )}
        ref={ref}
        onClick={handleClick}
        disabled={disabled}
        aria-label={ariaLabel}
        role="button"
        tabIndex={disabled ? -1 : 0}
        {...props}
      >
        {renderIcon()}
      </button>
    );
  }
);

FAB.displayName = 'FAB';

export { FAB };