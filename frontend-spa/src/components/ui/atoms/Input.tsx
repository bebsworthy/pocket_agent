import React from 'react';
import { cn } from '../../../utils/cn';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  inputMode?: 'text' | 'url' | 'email' | 'tel' | 'numeric' | 'decimal' | 'search' | 'none';
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = 'text', label, error, id, inputMode, required, ...props }, ref) => {
    const inputId = id || `input-${Math.random().toString(36).substr(2, 9)}`;

    // Determine appropriate inputMode based on type if not explicitly provided
    const getInputMode = (): 'text' | 'url' | 'email' | 'tel' | 'numeric' | 'decimal' | 'search' | 'none' | undefined => {
      if (inputMode) return inputMode;
      
      switch (type) {
        case 'url':
          return 'url';
        case 'email':
          return 'email';
        case 'tel':
          return 'tel';
        case 'number':
          return 'numeric';
        case 'search':
          return 'search';
        default:
          return 'text';
      }
    };

    return (
      <div className="w-full">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-sm font-medium text-gray-900 dark:text-gray-100 mb-2"
          >
            {label}
            {required && (
              <span className="text-red-500 ml-1" aria-label="required">
                *
              </span>
            )}
          </label>
        )}
        <input
          type={type}
          id={inputId}
          inputMode={getInputMode()}
          className={cn(
            // Base styles with mobile-first approach
            'flex w-full rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-base text-gray-900 dark:text-gray-100 ring-offset-white dark:ring-offset-gray-900',
            // Mobile optimizations - larger touch targets and text
            'h-11 min-h-[44px] text-base', // 44px minimum touch target
            // Focus and interaction states
            'placeholder:text-gray-500 dark:placeholder:text-gray-400',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2',
            'focus:border-blue-500 dark:focus:border-blue-400',
            // Disabled and error states
            'disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-gray-50 dark:disabled:bg-gray-700',
            // Mobile keyboard optimizations
            'transition-colors duration-200',
            // Error styling
            error && 'border-red-500 focus-visible:ring-red-500 focus:border-red-500',
            className
          )}
          ref={ref}
          required={required}
          aria-invalid={error ? 'true' : 'false'}
          aria-describedby={error ? `${inputId}-error` : undefined}
          aria-required={required}
          {...props}
        />
        {error && (
          <p
            id={`${inputId}-error`}
            className="mt-1 text-sm text-red-600 dark:text-red-400"
            role="alert"
            aria-live="polite"
          >
            {error}
          </p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

export { Input };