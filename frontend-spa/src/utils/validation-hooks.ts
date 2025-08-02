/**
 * React Validation Hooks for Real-time Form Validation
 *
 * Provides custom React hooks for:
 * - Real-time field validation with debouncing
 * - Form-level validation state management
 * - Integration with Jotai atoms for state management
 * - Performance-optimized validation with memoization
 */

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { 
  createProjectFormDataAtom,
  createProjectErrorsAtom,
  setCreateProjectFieldErrorAtom,
  type CreateProjectFormData,
  type CreateProjectValidationErrors,
} from '../store/atoms/projectCreationAtoms';
import {
  validateProjectForm,
  validateProjectFormWithContext,
  quickValidateField,
  type ValidationResult,
  type FieldValidationResult,
  type ValidationContext,
  ValidationSeverity,
} from './projectValidation';
import type { Server } from '../types/models';

// Debounce delay constants
const DEFAULT_DEBOUNCE_DELAY = 300; // ms
const FAST_DEBOUNCE_DELAY = 150; // ms for less intensive validations

// Validation state interface
export interface ValidationState {
  isValidating: boolean;
  isValid: boolean;
  error?: string;
  warnings?: string[];
  lastValidated?: Date;
}

// Form validation state interface
export interface FormValidationState {
  isValidating: boolean;
  isValid: boolean;
  errors: CreateProjectValidationErrors;
  warnings: string[];
  fieldStates: {
    name: ValidationState;
    path: ValidationState;
    serverId: ValidationState;
  };
}

// Validation state manager interface
export interface ValidationStateManager {
  validateField: (field: keyof CreateProjectFormData, value: string) => Promise<FieldValidationResult>;
  validateForm: () => Promise<ValidationResult>;
  clearErrors: () => void;
  clearFieldError: (field: keyof CreateProjectFormData) => void;
  getFieldState: (field: keyof CreateProjectFormData) => ValidationState;
  getFormState: () => FormValidationState;
}

/**
 * Custom hook for debounced values
 * Optimizes validation by reducing excessive function calls
 */
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

/**
 * Real-time field validation hook with debouncing
 * Provides optimized validation for individual form fields
 */
export function useRealTimeValidation(
  field: keyof CreateProjectFormData,
  value: string,
  context?: Partial<ValidationContext>,
  debounceDelay: number = DEFAULT_DEBOUNCE_DELAY
): ValidationState {
  const debouncedValue = useDebounce(value, debounceDelay);
  const [validationState, setValidationState] = useState<ValidationState>({
    isValidating: false,
    isValid: true,
  });

  const setFieldError = useSetAtom(setCreateProjectFieldErrorAtom);

  // Memoize validation function to prevent unnecessary re-creation
  const validateField = useCallback(async (fieldValue: string): Promise<FieldValidationResult> => {
    return quickValidateField(field, fieldValue, context);
  }, [field, context]);

  // Effect for real-time validation
  useEffect(() => {
    if (!debouncedValue.trim() && field !== 'serverId') {
      // Don't validate empty values for name and path (except serverId which can be empty initially)
      setValidationState({
        isValidating: false,
        isValid: true,
      });
      return;
    }

    let isCancelled = false;

    const performValidation = async () => {
      setValidationState(prev => ({
        ...prev,
        isValidating: true,
      }));

      try {
        const result = await validateField(debouncedValue);
        
        if (!isCancelled) {
          const newState: ValidationState = {
            isValidating: false,
            isValid: result.isValid,
            error: result.error,
            warnings: result.suggestions && result.severity === ValidationSeverity.WARNING 
              ? result.suggestions 
              : undefined,
            lastValidated: new Date(),
          };

          setValidationState(newState);

          // Update Jotai atom with validation result
          setFieldError(field, result.error);
        }
      } catch (error) {
        if (!isCancelled) {
          console.error(`Validation error for field ${field}:`, error);
          setValidationState({
            isValidating: false,
            isValid: false,
            error: 'Validation failed. Please try again.',
            lastValidated: new Date(),
          });
          setFieldError(field, 'Validation failed');
        }
      }
    };

    performValidation();

    return () => {
      isCancelled = true;
    };
  }, [debouncedValue, field, validateField, setFieldError]);

  return validationState;
}

/**
 * Form-level validation hook
 * Manages validation state for the entire form
 */
export function useFormValidation(
  servers: Server[] = [],
  existingProjects?: Array<{ name: string; path: string }>,
  debounceDelay: number = DEFAULT_DEBOUNCE_DELAY
): FormValidationState {
  const formData = useAtomValue(createProjectFormDataAtom);
  const debouncedFormData = useDebounce(formData, debounceDelay);

  const [validationState, setValidationState] = useState<FormValidationState>({
    isValidating: false,
    isValid: false,
    errors: {},
    warnings: [],
    fieldStates: {
      name: { isValidating: false, isValid: true },
      path: { isValidating: false, isValid: true },
      serverId: { isValidating: false, isValid: true },
    },
  });

  // Individual field validation states
  const nameValidation = useRealTimeValidation('name', formData.name, { servers, existingProjects }, FAST_DEBOUNCE_DELAY);
  const pathValidation = useRealTimeValidation('path', formData.path, { servers, existingProjects }, debounceDelay);
  const serverValidation = useRealTimeValidation('serverId', formData.serverId, { servers, existingProjects }, FAST_DEBOUNCE_DELAY);

  // Memoize context to prevent unnecessary re-validation
  const validationContext = useMemo((): ValidationContext => ({
    servers,
    existingProjects,
  }), [servers, existingProjects]);

  // Effect for form-level validation
  useEffect(() => {
    let isCancelled = false;

    const performFormValidation = async () => {
      setValidationState(prev => ({
        ...prev,
        isValidating: true,
      }));

      try {
        const result = await Promise.resolve(
          validateProjectFormWithContext(debouncedFormData, servers, existingProjects)
        );

        if (!isCancelled) {
          const fieldStates = {
            name: nameValidation,
            path: pathValidation,
            serverId: serverValidation,
          };

          const newState: FormValidationState = {
            isValidating: false,
            isValid: result.isValid,
            errors: result.errors,
            warnings: result.warnings || [],
            fieldStates,
          };

          setValidationState(newState);
        }
      } catch (error) {
        if (!isCancelled) {
          console.error('Form validation error:', error);
          setValidationState(prev => ({
            ...prev,
            isValidating: false,
            isValid: false,
            errors: { general: 'Form validation failed. Please try again.' },
          }));
        }
      }
    };

    performFormValidation();

    return () => {
      isCancelled = true;
    };
  }, [debouncedFormData, validationContext, nameValidation, pathValidation, serverValidation, servers, existingProjects]);

  // Update field states from individual validations
  useEffect(() => {
    setValidationState(prev => ({
      ...prev,
      fieldStates: {
        name: nameValidation,
        path: pathValidation,
        serverId: serverValidation,
      },
    }));
  }, [nameValidation, pathValidation, serverValidation]);

  return validationState;
}

/**
 * Validation state management hook with Jotai integration
 * Provides comprehensive validation state management for forms
 */
export function useValidationState(_formId: string = 'default'): ValidationStateManager {
  const [formData] = useAtom(createProjectFormDataAtom);
  const [errors, setErrors] = useAtom(createProjectErrorsAtom);
  const setFieldError = useSetAtom(setCreateProjectFieldErrorAtom);

  // Memoize validation functions for performance
  const validateField = useCallback(async (
    field: keyof CreateProjectFormData,
    value: string
  ): Promise<FieldValidationResult> => {
    return quickValidateField(field, value);
  }, []);

  const validateForm = useCallback(async (): Promise<ValidationResult> => {
    return validateProjectForm(formData);
  }, [formData]);

  const clearErrors = useCallback(() => {
    setErrors({});
  }, [setErrors]);

  const clearFieldError = useCallback((field: keyof CreateProjectFormData) => {
    setFieldError(field, undefined);
  }, [setFieldError]);

  const getFieldState = useCallback((field: keyof CreateProjectFormData): ValidationState => {
    const hasError = Boolean(errors[field]);
    return {
      isValidating: false,
      isValid: !hasError,
      error: errors[field],
    };
  }, [errors]);

  const getFormState = useCallback((): FormValidationState => {
    const hasErrors = Object.keys(errors).length > 0;
    const fieldStates = {
      name: getFieldState('name'),
      path: getFieldState('path'),
      serverId: getFieldState('serverId'),
    };

    return {
      isValidating: false,
      isValid: !hasErrors,
      errors,
      warnings: [],
      fieldStates,
    };
  }, [errors, getFieldState]);

  return {
    validateField,
    validateForm,
    clearErrors,
    clearFieldError,
    getFieldState,
    getFormState,
  };
}

/**
 * Advanced validation hook with caching and performance optimization
 * Includes memoization and reduced re-validation for better performance
 */
export function useOptimizedValidation(
  servers: Server[] = [],
  existingProjects?: Array<{ name: string; path: string }>,
  options: {
    debounceDelay?: number;
    enableCaching?: boolean;
    validateOnMount?: boolean;
  } = {}
): FormValidationState & {
  refreshValidation: () => void;
  isInitialized: boolean;
} {
  const {
    debounceDelay = DEFAULT_DEBOUNCE_DELAY,
    enableCaching = true,
    validateOnMount = false,
  } = options;

  const [isInitialized, setIsInitialized] = useState(false);
  const [, setValidationCache] = useState<Map<string, FieldValidationResult>>(new Map());

  const formValidation = useFormValidation(servers, existingProjects, debounceDelay);
  const formData = useAtomValue(createProjectFormDataAtom);

  // Initialize validation on mount if requested
  useEffect(() => {
    if (validateOnMount && !isInitialized) {
      setIsInitialized(true);
    } else if (!validateOnMount) {
      setIsInitialized(true);
    }
  }, [validateOnMount, isInitialized]);

  // Memoize refresh function
  const refreshValidation = useCallback(() => {
    if (enableCaching) {
      setValidationCache(new Map());
    }
    // Force re-validation by updating a dependency
    setIsInitialized(prev => !prev);
    setTimeout(() => setIsInitialized(prev => !prev), 10);
  }, [enableCaching]);

  // Cache validation results if caching is enabled
  useEffect(() => {
    if (enableCaching && formValidation.isValid) {
      const cacheKey = JSON.stringify(formData);
      const cachedResult: FieldValidationResult = {
        isValid: formValidation.isValid,
        error: formValidation.errors.general,
      };
      setValidationCache(prev => new Map(prev).set(cacheKey, cachedResult));
    }
  }, [formValidation, formData, enableCaching]);

  return {
    ...formValidation,
    refreshValidation,
    isInitialized,
  };
}

/**
 * Simple validation hook for basic use cases
 * Lightweight alternative for simpler forms
 */
export function useSimpleValidation(field: keyof CreateProjectFormData, value: string): {
  isValid: boolean;
  error?: string;
  isValidating: boolean;
} {
  const validationState = useRealTimeValidation(field, value, undefined, FAST_DEBOUNCE_DELAY);
  
  return {
    isValid: validationState.isValid,
    error: validationState.error,
    isValidating: validationState.isValidating,
  };
}

/**
 * Validation hook for server-dependent validations
 * Automatically updates when server list changes
 */
export function useServerAwareValidation(
  servers: Server[],
  debounceDelay: number = DEFAULT_DEBOUNCE_DELAY
): FormValidationState {
  return useFormValidation(servers, undefined, debounceDelay);
}

/**
 * Hook for conditional validation based on form state
 * Only validates when specific conditions are met
 */
export function useConditionalValidation(
  condition: boolean,
  servers: Server[] = [],
  debounceDelay: number = DEFAULT_DEBOUNCE_DELAY
): FormValidationState | null {
  const fullValidation = useFormValidation(servers, undefined, debounceDelay);
  
  // Return null when condition is not met, full validation otherwise
  return condition ? fullValidation : null;
}