import React, { useMemo } from 'react';
import { cn } from '../../../utils/cn';

export interface SegmentedControlOption<T extends string = string> {
  value: T;
  label: string;
  icon?: React.ReactNode;
  disabled?: boolean;
}

export interface SegmentedControlProps<T extends string = string> {
  options: SegmentedControlOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
  fullWidth?: boolean;
  size?: 'sm' | 'md' | 'lg';
  'aria-label'?: string;
}

const SegmentedControl = <T extends string = string>({
  options,
  value,
  onChange,
  className,
  fullWidth = false,
  size = 'md',
  'aria-label': ariaLabel,
  ...props
}: SegmentedControlProps<T>) => {
  // Memoize the transform calculation for performance
  const transformStyle = useMemo(() => {
    const activeIndex = options.findIndex(opt => opt.value === value);
    const safeIndex = activeIndex === -1 ? 0 : activeIndex;
    return {
      width: `calc(${100 / options.length}% - 4px)`,
      transform: `translateX(${safeIndex * 100}%)`,
    };
  }, [options, value]);

  const handleKeyDown = (event: React.KeyboardEvent) => {
    const currentIndex = options.findIndex(opt => opt.value === value);
    let newIndex = currentIndex;

    switch (event.key) {
      case 'ArrowLeft':
      case 'ArrowUp':
        event.preventDefault();
        newIndex = currentIndex > 0 ? currentIndex - 1 : options.length - 1;
        break;
      case 'ArrowRight':
      case 'ArrowDown':
        event.preventDefault();
        newIndex = currentIndex < options.length - 1 ? currentIndex + 1 : 0;
        break;
      case 'Home':
        event.preventDefault();
        newIndex = 0;
        break;
      case 'End':
        event.preventDefault();
        newIndex = options.length - 1;
        break;
      default:
        return; // Don't prevent default for other keys
    }

    // Find the next non-disabled option
    const newOption = options[newIndex];
    if (newOption && !newOption.disabled) {
      onChange(newOption.value);
    } else {
      // If the target option is disabled, find the next available one
      let searchIndex = newIndex;
      for (let i = 0; i < options.length; i++) {
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
          searchIndex = (searchIndex - 1 + options.length) % options.length;
        } else {
          searchIndex = (searchIndex + 1) % options.length;
        }
        const searchOption = options[searchIndex];
        if (searchOption && !searchOption.disabled) {
          onChange(searchOption.value);
          break;
        }
      }
    }
  };
  const sizes = {
    sm: 'h-9 p-0.5',
    md: 'h-11 p-1', // 44px height for proper touch targets
    lg: 'h-12 p-1', // 48px height for larger touch targets
  };

  const buttonSizes = {
    sm: 'px-2 py-1 text-xs min-h-8',
    md: 'px-3 py-1.5 text-sm min-h-9', // ~36px button inside container
    lg: 'px-4 py-2 text-base min-h-10', // ~40px button inside container
  };

  return (
    <div
      className={cn(
        'scrollbar-hide overflow-scroll-touch relative inline-flex items-center justify-center overflow-x-auto rounded-lg bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
        sizes[size],
        fullWidth && 'w-full',
        className
      )}
      role="tablist"
      aria-label={ariaLabel || 'Tab navigation'}
      onKeyDown={handleKeyDown}
      {...props}
    >
      {/* Background indicator for active tab */}
      <div
        className="absolute inset-1 rounded-md bg-white shadow-sm transition-all duration-200 ease-out dark:bg-gray-700"
        style={transformStyle}
      />

      {options.map((option, _index) => (
        <button
          key={option.value}
          className={cn(
            'ring-offset-background relative z-10 inline-flex items-center justify-center whitespace-nowrap rounded-md font-medium transition-all duration-200 ease-out',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2',
            'disabled:pointer-events-none disabled:opacity-50',
            'active:scale-95', // Mobile touch feedback
            'touch-target', // Ensure minimum 44px touch targets
            buttonSizes[size],
            fullWidth && 'flex-1',
            value === option.value
              ? 'text-gray-900 dark:text-gray-100'
              : 'hover:text-gray-800 dark:hover:text-gray-200',
            option.disabled && 'cursor-not-allowed opacity-50'
          )}
          onClick={() => !option.disabled && onChange(option.value)}
          role="tab"
          aria-selected={value === option.value}
          aria-controls={`panel-${option.value}`}
          tabIndex={value === option.value ? 0 : -1}
          disabled={option.disabled}
          type="button"
        >
          {option.icon && (
            <span
              className={cn(
                'shrink-0 transition-transform duration-200',
                option.label && 'mr-2',
                size === 'sm' ? 'h-3 w-3' : size === 'md' ? 'h-4 w-4' : 'h-5 w-5'
              )}
            >
              {option.icon}
            </span>
          )}
          <span className="truncate">{option.label}</span>
        </button>
      ))}
    </div>
  );
};

SegmentedControl.displayName = 'SegmentedControl';

export { SegmentedControl };
