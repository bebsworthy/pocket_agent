/**
 * Project creation state atoms using Jotai for atomic state management.
 * Manages form data, validation errors, and loading states for project creation workflow.
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Project creation form data interface
export interface CreateProjectFormData {
  name: string;
  path: string;
  serverId: string;
}

// Validation error interface
export interface CreateProjectValidationErrors {
  name?: string;
  path?: string;
  serverId?: string;
  general?: string;
}

// Queue entry for deferred project creation requests
export interface QueuedProjectCreationRequest {
  id: string;
  formData: CreateProjectFormData;
  timestamp: number;
  retryCount: number;
  maxRetries: number;
}

// Connection status and retry handling
export interface ConnectionRetryState {
  isRetrying: boolean;
  retryAttempt: number;
  maxRetries: number;
  nextRetryTime?: number;
  lastError?: string;
}

// Optimistic update state for WebSocket project creation
export interface OptimisticProjectState {
  isOptimistic: boolean;
  optimisticProject?: {
    id: string;
    name: string;
    path: string;
    serverId: string;
    createdAt: string;
    lastActive: string;
  };
  rollbackData?: CreateProjectFormData;
  requestId?: string;
  startTime?: number;
  queuedRequests: QueuedProjectCreationRequest[];
  retryState: ConnectionRetryState;
}

// Complete project creation state interface
export interface CreateProjectState {
  isVisible: boolean;
  formData: CreateProjectFormData;
  errors: CreateProjectValidationErrors;
  isSubmitting: boolean;
  hasUnsavedChanges: boolean;
  optimisticState: OptimisticProjectState;
}

// Default connection retry state
const defaultRetryState: ConnectionRetryState = {
  isRetrying: false,
  retryAttempt: 0,
  maxRetries: 3,
  nextRetryTime: undefined,
  lastError: undefined,
};

// Default optimistic state
const defaultOptimisticState: OptimisticProjectState = {
  isOptimistic: false,
  optimisticProject: undefined,
  rollbackData: undefined,
  requestId: undefined,
  startTime: undefined,
  queuedRequests: [],
  retryState: defaultRetryState,
};

// Default state for project creation
const defaultCreateProjectState: CreateProjectState = {
  isVisible: false,
  formData: {
    name: '',
    path: '',
    serverId: '',
  },
  errors: {},
  isSubmitting: false,
  hasUnsavedChanges: false,
  optimisticState: defaultOptimisticState,
};

// Custom storage implementation with error handling for project creation state
const createProjectStorage = {
  getItem: (key: string, initialValue: CreateProjectState): CreateProjectState => {
    try {
      const item = localStorage.getItem(key);
      if (item === null) {
        return initialValue;
      }
      const parsed = JSON.parse(item);
      
      // Validate that parsed data has the expected structure
      if (
        typeof parsed !== 'object' ||
        parsed === null ||
        typeof parsed.formData !== 'object' ||
        parsed.formData === null
      ) {
        console.warn('Project creation state in localStorage has invalid structure, resetting to default');
        return initialValue;
      }
      
      // Merge with default state to ensure all properties exist
      return {
        ...initialValue,
        ...parsed,
        formData: {
          ...initialValue.formData,
          ...parsed.formData,
        },
        errors: parsed.errors || {},
        // Don't persist optimistic state - always start with default
        optimisticState: initialValue.optimisticState,
      };
    } catch (error) {
      console.error('Failed to deserialize project creation state from localStorage:', error);
      return initialValue;
    }
  },
  setItem: (key: string, value: CreateProjectState): void => {
    try {
      // Only persist form data and hasUnsavedChanges, not modal visibility, errors, or optimistic state
      const persistedState = {
        formData: value.formData,
        hasUnsavedChanges: value.hasUnsavedChanges,
      };
      localStorage.setItem(key, JSON.stringify(persistedState));
    } catch (error) {
      console.error('Failed to serialize project creation state to localStorage:', error);
    }
  },
  removeItem: (key: string): void => {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error('Failed to remove project creation state from localStorage:', error);
    }
  },
  subscribe: (key: string, callback: (value: CreateProjectState) => void, initialValue: CreateProjectState) => {
    if (typeof window === 'undefined' || typeof window.addEventListener === 'undefined') {
      return () => {};
    }
    const handler = (e: StorageEvent) => {
      if (e.storageArea === localStorage && e.key === key) {
        try {
          const newValue = e.newValue ? JSON.parse(e.newValue) : {};
          const mergedValue = {
            ...initialValue,
            formData: {
              ...initialValue.formData,
              ...newValue.formData,
            },
            hasUnsavedChanges: newValue.hasUnsavedChanges || false,
          };
          callback(mergedValue);
        } catch {
          callback(initialValue);
        }
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  },
};

// Main project creation state atom with localStorage persistence
export const createProjectStateAtom = atomWithStorage<CreateProjectState>(
  'createProjectState',
  defaultCreateProjectState,
  createProjectStorage
);

// Derived atoms for specific state parts (performance optimization)
export const createProjectFormDataAtom = atom(
  (get) => get(createProjectStateAtom).formData,
  (get, set, newFormData: Partial<CreateProjectFormData>) => {
    const currentState = get(createProjectStateAtom);
    const updatedFormData = { ...currentState.formData, ...newFormData };
    
    // Check if form data actually changed
    const hasChanges = Object.keys(updatedFormData).some(
      key => updatedFormData[key as keyof CreateProjectFormData] !== defaultCreateProjectState.formData[key as keyof CreateProjectFormData]
    );
    
    set(createProjectStateAtom, {
      ...currentState,
      formData: updatedFormData,
      hasUnsavedChanges: hasChanges,
    });
  }
);

export const createProjectErrorsAtom = atom(
  (get) => get(createProjectStateAtom).errors,
  (get, set, newErrors: CreateProjectValidationErrors) => {
    const currentState = get(createProjectStateAtom);
    set(createProjectStateAtom, {
      ...currentState,
      errors: newErrors,
    });
  }
);

export const createProjectIsVisibleAtom = atom(
  (get) => get(createProjectStateAtom).isVisible,
  (get, set, isVisible: boolean) => {
    const currentState = get(createProjectStateAtom);
    set(createProjectStateAtom, {
      ...currentState,
      isVisible,
    });
  }
);

export const createProjectIsSubmittingAtom = atom(
  (get) => get(createProjectStateAtom).isSubmitting,
  (get, set, isSubmitting: boolean) => {
    const currentState = get(createProjectStateAtom);
    set(createProjectStateAtom, {
      ...currentState,
      isSubmitting,
    });
  }
);

// Derived atoms for form validation status
export const createProjectHasErrorsAtom = atom((get) => {
  const errors = get(createProjectErrorsAtom);
  return Object.keys(errors).length > 0;
});

export const createProjectIsValidAtom = atom((get) => {
  const formData = get(createProjectFormDataAtom);
  const hasErrors = get(createProjectHasErrorsAtom);
  
  // Check required fields
  const hasRequiredFields = Boolean(
    formData.name.trim() &&
    formData.path.trim() &&
    formData.serverId
  );
  
  return hasRequiredFields && !hasErrors;
});

export const createProjectHasUnsavedChangesAtom = atom(
  (get) => get(createProjectStateAtom).hasUnsavedChanges
);

// Write-only atom for showing project creation modal
export const showCreateProjectModalAtom = atom(null, (get, set) => {
  set(createProjectIsVisibleAtom, true);
});

// Write-only atom for hiding project creation modal
export const hideCreateProjectModalAtom = atom(null, (get, set) => {
  const currentState = get(createProjectStateAtom);
  
  // If there are unsaved changes, keep them for next time
  if (!currentState.hasUnsavedChanges) {
    // Clear errors when hiding modal without unsaved changes
    set(createProjectErrorsAtom, {});
  }
  
  set(createProjectIsVisibleAtom, false);
  set(createProjectIsSubmittingAtom, false);
});

// Write-only atom for updating a single form field
export const updateCreateProjectFieldAtom = atom(
  null,
  (get, set, field: keyof CreateProjectFormData, value: string) => {
    set(createProjectFormDataAtom, { [field]: value });
    
    // Clear field-specific error when user starts typing
    const currentErrors = get(createProjectErrorsAtom);
    if (currentErrors[field]) {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [field]: _, ...remainingErrors } = currentErrors;
      set(createProjectErrorsAtom, remainingErrors);
    }
  }
);

// Write-only atom for setting a field error
export const setCreateProjectFieldErrorAtom = atom(
  null,
  (get, set, field: keyof CreateProjectValidationErrors, error: string | undefined) => {
    const currentErrors = get(createProjectErrorsAtom);
    if (error) {
      set(createProjectErrorsAtom, { ...currentErrors, [field]: error });
    } else {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [field]: _, ...remainingErrors } = currentErrors;
      set(createProjectErrorsAtom, remainingErrors);
    }
  }
);

// Write-only atom for batch setting multiple field errors
export const setCreateProjectErrorsAtom = atom(
  null,
  (get, set, errors: CreateProjectValidationErrors) => {
    set(createProjectErrorsAtom, errors);
  }
);

// Write-only atom for clearing all errors
export const clearCreateProjectErrorsAtom = atom(null, (get, set) => {
  set(createProjectErrorsAtom, {});
});

// Write-only atom for resetting the entire form
export const resetCreateProjectFormAtom = atom(null, (get, set) => {
  set(createProjectStateAtom, defaultCreateProjectState);
});

// Write-only atom for resetting form data only (keeping modal state)
export const resetCreateProjectFormDataAtom = atom(null, (get, set) => {
  const currentState = get(createProjectStateAtom);
  set(createProjectStateAtom, {
    ...currentState,
    formData: defaultCreateProjectState.formData,
    errors: {},
    hasUnsavedChanges: false,
    optimisticState: defaultOptimisticState,
  });
});

// Write-only atom for starting form submission
export const startCreateProjectSubmissionAtom = atom(null, (get, set) => {
  set(createProjectIsSubmittingAtom, true);
  set(createProjectErrorsAtom, {}); // Clear errors when starting submission
});

// Write-only atom for completing form submission (success)
export const completeCreateProjectSubmissionAtom = atom(null, (get, set) => {
  // Reset entire state on successful submission
  set(createProjectStateAtom, defaultCreateProjectState);
});

// Write-only atom for failing form submission
export const failCreateProjectSubmissionAtom = atom(
  null,
  (get, set, errors: CreateProjectValidationErrors) => {
    set(createProjectIsSubmittingAtom, false);
    set(createProjectErrorsAtom, errors);
  }
);

// Utility atom for form cleanup (e.g., when component unmounts)
export const cleanupCreateProjectFormAtom = atom(null, (get, set) => {
  const currentState = get(createProjectStateAtom);
  
  // Only reset if no unsaved changes, otherwise preserve for next time
  if (!currentState.hasUnsavedChanges) {
    set(createProjectStateAtom, defaultCreateProjectState);
  } else {
    // Just hide modal and clear errors/submission state
    set(createProjectStateAtom, {
      ...currentState,
      isVisible: false,
      errors: {},
      isSubmitting: false,
      optimisticState: defaultOptimisticState, // Reset optimistic state
    });
  }
});

// Optimistic update atoms for WebSocket project creation

// Read-only atom for optimistic state
export const createProjectOptimisticStateAtom = atom(
  (get) => get(createProjectStateAtom).optimisticState
);

// Write-only atom for starting optimistic project creation
export const startOptimisticProjectCreationAtom = atom(
  null,
  (get, set, params: { formData: CreateProjectFormData; requestId: string }) => {
    const { formData, requestId } = params;
    const currentState = get(createProjectStateAtom);
    
    // Generate optimistic project data
    const optimisticProject = {
      id: `temp_${requestId}`,
      name: formData.name.trim(),
      path: formData.path.trim(),
      serverId: formData.serverId,
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString(),
    };

    set(createProjectStateAtom, {
      ...currentState,
      isSubmitting: true,
      errors: {},
      optimisticState: {
        ...currentState.optimisticState,
        isOptimistic: true,
        optimisticProject,
        rollbackData: { ...formData },
        requestId,
        startTime: Date.now(),
      },
    });
  }
);

// Write-only atom for confirming optimistic project creation (WebSocket success)
export const confirmOptimisticProjectCreationAtom = atom(
  null,
  (get, set, serverProject: { id: string; name: string; path: string; created_at: string }) => {
    const currentState = get(createProjectStateAtom);
    
    // Update optimistic project with server data
    set(createProjectStateAtom, {
      ...currentState,
      isSubmitting: false,
      optimisticState: {
        ...currentState.optimisticState,
        isOptimistic: false,
        optimisticProject: {
          ...currentState.optimisticState.optimisticProject!,
          id: serverProject.id,
          createdAt: serverProject.created_at,
        },
      },
    });
  }
);

// Write-only atom for rolling back optimistic project creation (WebSocket error)
export const rollbackOptimisticProjectCreationAtom = atom(
  null,
  (get, set, errors: CreateProjectValidationErrors) => {
    const currentState = get(createProjectStateAtom);
    
    // Rollback to previous state with errors
    set(createProjectStateAtom, {
      ...currentState,
      isSubmitting: false,
      errors,
      optimisticState: defaultOptimisticState,
    });
  }
);

// Write-only atom for clearing optimistic state
export const clearOptimisticStateAtom = atom(null, (get, set) => {
  const currentState = get(createProjectStateAtom);
  set(createProjectStateAtom, {
    ...currentState,
    optimisticState: defaultOptimisticState,
  });
});

// Read-only atom to check if currently in optimistic state
export const isOptimisticCreationActiveAtom = atom(
  (get) => get(createProjectOptimisticStateAtom).isOptimistic
);

// Read-only atom to get optimistic project data
export const optimisticProjectDataAtom = atom(
  (get) => get(createProjectOptimisticStateAtom).optimisticProject
);

// Connection status and queuing atoms for WebSocket project creation

// Write-only atom for queuing project creation when connection is lost
export const queueProjectCreationRequestAtom = atom(
  null,
  (get, set, params: { formData: CreateProjectFormData; requestId: string }) => {
    const { formData, requestId } = params;
    const currentState = get(createProjectStateAtom);
    
    const queuedRequest: QueuedProjectCreationRequest = {
      id: requestId,
      formData: { ...formData },
      timestamp: Date.now(),
      retryCount: 0,
      maxRetries: 3,
    };

    set(createProjectStateAtom, {
      ...currentState,
      optimisticState: {
        ...currentState.optimisticState,
        queuedRequests: [...currentState.optimisticState.queuedRequests, queuedRequest],
      },
    });
  }
);

// Write-only atom for processing queued requests when connection is restored
export const processQueuedRequestsAtom = atom(
  null,
  (get, set) => {
    const currentState = get(createProjectStateAtom);
    const queuedRequests = currentState.optimisticState.queuedRequests;
    
    if (queuedRequests.length === 0) {
      return;
    }

    // Process the oldest request first
    const nextRequest = queuedRequests[0];
    const remainingRequests = queuedRequests.slice(1);
    
    // Update state to remove processed request from queue
    set(createProjectStateAtom, {
      ...currentState,
      optimisticState: {
        ...currentState.optimisticState,
        queuedRequests: remainingRequests,
      },
    });

    // Return the request to be processed by the component
    return nextRequest;
  }
);

// Write-only atom for starting connection retry
export const startConnectionRetryAtom = atom(
  null,
  (get, set, error?: string) => {
    const currentState = get(createProjectStateAtom);
    const currentRetryState = currentState.optimisticState.retryState;
    
    if (currentRetryState.retryAttempt >= currentRetryState.maxRetries) {
      // Max retries reached, give up
      set(createProjectStateAtom, {
        ...currentState,
        optimisticState: {
          ...currentState.optimisticState,
          retryState: {
            ...currentRetryState,
            isRetrying: false,
            lastError: error || 'Maximum retry attempts exceeded',
          },
        },
      });
      return;
    }

    // Calculate exponential backoff delay
    const baseDelay = 1000; // 1 second
    const maxDelay = 30000; // 30 seconds
    const exponentialDelay = baseDelay * Math.pow(2, currentRetryState.retryAttempt);
    const delay = Math.min(exponentialDelay, maxDelay);
    const nextRetryTime = Date.now() + delay;

    set(createProjectStateAtom, {
      ...currentState,
      optimisticState: {
        ...currentState.optimisticState,
        retryState: {
          isRetrying: true,
          retryAttempt: currentRetryState.retryAttempt + 1,
          maxRetries: currentRetryState.maxRetries,
          nextRetryTime,
          lastError: error,
        },
      },
    });
  }
);

// Write-only atom for completing connection retry
export const completeConnectionRetryAtom = atom(
  null,
  (get, set, success: boolean) => {
    const currentState = get(createProjectStateAtom);
    
    if (success) {
      // Reset retry state on successful connection
      set(createProjectStateAtom, {
        ...currentState,
        optimisticState: {
          ...currentState.optimisticState,
          retryState: defaultRetryState,
        },
      });
    } else {
      // Continue retrying or give up if max retries reached
      const currentRetryState = currentState.optimisticState.retryState;
      if (currentRetryState.retryAttempt >= currentRetryState.maxRetries) {
        set(createProjectStateAtom, {
          ...currentState,
          optimisticState: {
            ...currentState.optimisticState,
            retryState: {
              ...currentRetryState,
              isRetrying: false,
              lastError: 'Connection failed after maximum retry attempts',
            },
          },
        });
      } else {
        // Prepare for next retry
        set(createProjectStateAtom, {
          ...currentState,
          optimisticState: {
            ...currentState.optimisticState,
            retryState: {
              ...currentRetryState,
              isRetrying: false,
            },
          },
        });
      }
    }
  }
);

// Read-only atoms for connection status
export const connectionRetryStateAtom = atom(
  (get) => get(createProjectOptimisticStateAtom).retryState
);

export const queuedRequestsCountAtom = atom(
  (get) => get(createProjectOptimisticStateAtom).queuedRequests.length
);

export const isConnectionRetryingAtom = atom(
  (get) => get(connectionRetryStateAtom).isRetrying
);

// Write-only atom for clearing all queued requests (e.g., on user cancel)
export const clearQueuedRequestsAtom = atom(null, (get, set) => {
  const currentState = get(createProjectStateAtom);
  set(createProjectStateAtom, {
    ...currentState,
    optimisticState: {
      ...currentState.optimisticState,
      queuedRequests: [],
      retryState: defaultRetryState,
    },
  });
});