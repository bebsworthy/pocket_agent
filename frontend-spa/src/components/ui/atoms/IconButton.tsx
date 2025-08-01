import React from 'react';
import { cn } from '../../../utils/cn';

export interface IconButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  icon: React.ComponentType<{ className?: string }> | React.ReactNode;
  'aria-label': string; // Required for accessibility
  size?: 'sm' | 'md' | 'lg';
  variant?: 'ghost' | 'solid' | 'outline';
  onPress: () => void; // Touch-optimized naming - required for consistency
}

const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ className, icon, size = 'md', variant = 'ghost', onPress, disabled, ...props }, ref) => {
    const baseClasses = 'inline-flex items-center justify-center rounded-md font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none active:scale-95';
    
    // Ensure minimum 44px touch targets for mobile
    const sizes = {
      sm: 'h-10 w-10 min-h-[40px] min-w-[40px]', // 40px for small, still accessible
      md: 'h-11 w-11 min-h-[44px] min-w-[44px]', // 44px minimum touch target
      lg: 'h-12 w-12 min-h-[48px] min-w-[48px]'  // 48px for large
    };

    const variants = {
      ghost: 'hover:bg-gray-100 dark:hover:bg-gray-800 focus-visible:ring-gray-500 active:bg-gray-200 dark:active:bg-gray-700',
      solid: 'bg-blue-600 text-white hover:bg-blue-700 focus-visible:ring-blue-500 active:bg-blue-800',
      outline: 'border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-800 focus-visible:ring-gray-500'
    };

    // Icon sizing based on button size
    const iconSizes = {
      sm: 'h-4 w-4',
      md: 'h-5 w-5',
      lg: 'h-6 w-6'
    };

    const handleClick = () => {
      if (!disabled) {
        onPress();
      }
    };

    const renderIcon = () => {
      if (React.isValidElement(icon)) {
        return React.cloneElement(icon as React.ReactElement, {
          className: cn(iconSizes[size], (icon as React.ReactElement).props?.className)
        });
      }
      
      // Handle component-based icons (like Lucide, Heroicons, etc.)
      if (typeof icon === 'function') {
        const IconComponent = icon as React.ComponentType<{ className?: string }>;
        return <IconComponent className={iconSizes[size]} />;
      }
      
      return icon;
    };

    return (
      <button
        className={cn(
          baseClasses,
          sizes[size],
          variants[variant],
          className
        )}
        ref={ref}
        onClick={handleClick}
        disabled={disabled}
        // Enhanced accessibility
        role="button"
        tabIndex={disabled ? -1 : 0}
        {...props}
      >
        {renderIcon()}
      </button>
    );
  }
);

IconButton.displayName = 'IconButton';

export { IconButton };