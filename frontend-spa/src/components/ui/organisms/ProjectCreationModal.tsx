import React, { useState, useCallback, useEffect } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { Card, CardHeader, CardContent, CardFooter } from '../molecules/Card';
import { Button } from '../atoms/Button';
import { Input } from '../atoms/Input';
import { IconButton } from '../atoms/IconButton';
import { useProjects } from '../../../store/hooks/useProjects';
import { useServers } from '../../../store/hooks/useServers';
import { serverOperationAtom } from '../../../store/atoms/servers';
import { useWebSocket, useWebSocketMessage } from '../../../store/hooks/useWebSocket';
import { X, Plus, Server, AlertCircle } from 'lucide-react';
import {
  createProjectFormDataAtom,
  createProjectErrorsAtom,
  createProjectIsSubmittingAtom,
  createProjectIsValidAtom,
  updateCreateProjectFieldAtom,
  setCreateProjectErrorsAtom,
  completeCreateProjectSubmissionAtom,
  failCreateProjectSubmissionAtom,
  startOptimisticProjectCreationAtom,
  confirmOptimisticProjectCreationAtom,
  rollbackOptimisticProjectCreationAtom,
  isOptimisticCreationActiveAtom,
  optimisticProjectDataAtom,
  queueProjectCreationRequestAtom,
  startConnectionRetryAtom,
  connectionRetryStateAtom,
  queuedRequestsCountAtom,
  isConnectionRetryingAtom,
} from '../../../store/atoms/projectCreationAtoms';
import { cn } from '../../../utils/cn';
import type { 
  CreateProjectMessage, 
  ProjectCreatedMessage, 
  ProjectCreationErrorMessage, 
  ServerMessage 
} from '../../../types/messages';

export interface ProjectCreationModalProps {
  isVisible: boolean;
  onClose: () => void;
  onAddServer: () => void;
  onServerAdded?: (server: { id: string; name: string }) => void;
  className?: string;
}

interface FormErrors {
  name?: string;
  path?: string;
  serverId?: string;
  general?: string;
}

class ProjectCreationError extends Error {
  code?: string;
  details?: Record<string, unknown>;
  
  constructor(message: string, code?: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ProjectCreationError';
    this.code = code;
    this.details = details;
  }
}

/**
 * Comprehensive path validation and sanitization
 * Protects against path traversal attacks and validates filesystem compatibility
 */
const validateAndSanitizePath = (path: string): { isValid: boolean; error?: string; sanitizedPath?: string } => {
  if (!path || !path.trim()) {
    return { isValid: false, error: 'Path cannot be empty' };
  }

  const trimmedPath = path.trim();

  // Check for path traversal attempts
  const pathTraversalPatterns = [
    /\.\./,           // Parent directory traversal
    /\.\\/,           // Current directory with separator  
    /\/\./,           // Hidden directory patterns
    /~\//,            // User home shortcuts in middle of path
    /\/+/,            // Multiple consecutive slashes
  ];

  const hasPathTraversal = pathTraversalPatterns.some(pattern => pattern.test(trimmedPath));
  if (hasPathTraversal && !trimmedPath.startsWith('~/')) {
    return { isValid: false, error: 'Path contains invalid directory traversal patterns' };
  }

  // Check for dangerous filesystem characters (Windows + Unix)
  // eslint-disable-next-line no-control-regex
  const dangerousChars = /[<>:"|?*\0\x01-\x1f\x7f]/;
  if (dangerousChars.test(trimmedPath)) {
    return { isValid: false, error: 'Path contains invalid filesystem characters' };
  }

  // Check for reserved Windows filenames
  const windowsReserved = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i;
  const pathParts = trimmedPath.split(/[/\\]/).filter(Boolean);
  const hasReservedName = pathParts.some(part => windowsReserved.test(part));
  if (hasReservedName) {
    return { isValid: false, error: 'Path contains reserved system names' };
  }

  // Length validation
  if (trimmedPath.length > 260) { // Windows MAX_PATH limit
    return { isValid: false, error: 'Path is too long (maximum 260 characters)' };
  }

  // Check for excessively long path segments
  const hasLongSegment = pathParts.some(part => part.length > 255); // Most filesystems limit
  if (hasLongSegment) {
    return { isValid: false, error: 'Path segment is too long (maximum 255 characters)' };
  }

  // Sanitize the path by normalizing separators and removing redundant elements
  let sanitizedPath = trimmedPath
    .replace(/[/\\]+/g, '/') // Normalize separators to forward slash
    .replace(/\/+$/, ''); // Remove trailing slashes

  // Handle home directory expansion
  if (sanitizedPath.startsWith('~/')) {
    // Keep as-is for home directory paths - server will handle expansion
  } else if (!sanitizedPath.startsWith('/')) {
    // Ensure absolute paths start with /
    sanitizedPath = '/' + sanitizedPath;
  }

  return { 
    isValid: true, 
    sanitizedPath: sanitizedPath || '/' 
  };
};

/**
 * ProjectCreationModal - A full-screen modal for creating new projects.
 * Features form validation, server selection, and mobile-optimized UI.
 */
export function ProjectCreationModal({
  isVisible,
  onClose,
  onAddServer,
  onServerAdded,
  className,
}: ProjectCreationModalProps) {
  const formData = useAtomValue(createProjectFormDataAtom);
  const errors = useAtomValue(createProjectErrorsAtom);
  const isSubmitting = useAtomValue(createProjectIsSubmittingAtom);
  const isValid = useAtomValue(createProjectIsValidAtom);
  const isOptimisticCreation = useAtomValue(isOptimisticCreationActiveAtom);
  const optimisticProject = useAtomValue(optimisticProjectDataAtom);
  const connectionRetryState = useAtomValue(connectionRetryStateAtom);
  const queuedRequestsCount = useAtomValue(queuedRequestsCountAtom);
  const isConnectionRetrying = useAtomValue(isConnectionRetryingAtom);
  
  const updateField = useSetAtom(updateCreateProjectFieldAtom);
  const setErrors = useSetAtom(setCreateProjectErrorsAtom);
  const completeSubmission = useSetAtom(completeCreateProjectSubmissionAtom);
  const failSubmission = useSetAtom(failCreateProjectSubmissionAtom);
  const startOptimisticCreation = useSetAtom(startOptimisticProjectCreationAtom);
  const confirmOptimisticCreation = useSetAtom(confirmOptimisticProjectCreationAtom);
  const rollbackOptimisticCreation = useSetAtom(rollbackOptimisticProjectCreationAtom);
  const queueProjectCreationRequest = useSetAtom(queueProjectCreationRequestAtom);
  const startConnectionRetry = useSetAtom(startConnectionRetryAtom);

  const { addProject } = useProjects();
  const { servers, hasServers } = useServers();

  const [showServerDropdown, setShowServerDropdown] = useState(false);
  const [activeOptionId, setActiveOptionId] = useState<string | null>(null);

  // WebSocket integration for project creation
  const selectedServer = servers?.find(s => s.id === formData.serverId);
  // Always call hook to avoid conditional hook rule violation
  const webSocket = useWebSocket(
    selectedServer?.id || 'placeholder', 
    selectedServer?.websocketUrl || 'ws://placeholder'
  );
  
  // Handle WebSocket project_created messages for enhanced project creation
  useWebSocketMessage(
    formData.serverId,
    'project_created',
    useCallback((message: ServerMessage) => {
      const projectCreatedMessage = message as ProjectCreatedMessage;
      if (projectCreatedMessage.data?.project && (isSubmitting || isOptimisticCreation)) {
        const serverProject = projectCreatedMessage.data.project;
        
        // Confirm optimistic creation with server data
        confirmOptimisticCreation({
          id: serverProject.id,
          name: serverProject.name,
          path: serverProject.path,
          created_at: serverProject.created_at,
        });
        
        // Add project to global state
        const projectData = {
          id: serverProject.id,
          name: serverProject.name,
          path: serverProject.path,
          serverId: formData.serverId,
          createdAt: serverProject.created_at,
          lastActive: new Date().toISOString(),
        };
        
        addProject(projectData);
        completeSubmission();
        
        // Close modal after successful creation
        setTimeout(() => onClose(), 100);
      }
    }, [formData.serverId, isSubmitting, isOptimisticCreation, confirmOptimisticCreation, addProject, completeSubmission, onClose]),
    [formData.serverId, isSubmitting, isOptimisticCreation]
  );

  // Handle WebSocket project creation errors
  useWebSocketMessage(
    formData.serverId,
    'project_creation_error',
    useCallback((message: ServerMessage) => {
      const errorMessage = message as ProjectCreationErrorMessage;
      if (isSubmitting || isOptimisticCreation) {
        const errorMsg = errorMessage.data?.message || 'Failed to create project';
        const errorCode = errorMessage.data?.error_code || 'UNKNOWN_ERROR';
        
        // Map specific error codes to user-friendly messages
        let userErrorMsg = errorMsg;
        switch (errorCode) {
          case 'INVALID_PATH':
            userErrorMsg = 'The specified path is invalid or inaccessible';
            break;
          case 'PROJECT_NESTING':
            userErrorMsg = 'Cannot create nested projects';
            break;
          case 'PROJECT_LIMIT':
            userErrorMsg = 'Server has reached maximum project limit';
            break;
          case 'RESOURCE_LIMIT':
            userErrorMsg = 'Server resources are at capacity';
            break;
          case 'PROJECT_PATH_EXISTS':
            userErrorMsg = 'A project already exists at this path';
            break;
          case 'PROJECT_NAME_TAKEN':
            userErrorMsg = 'This project name is already in use';
            break;
          case 'PERMISSION_DENIED':
            userErrorMsg = 'Permission denied - check directory access rights';
            break;
          case 'SERVER_UNAVAILABLE':
            userErrorMsg = 'Server is currently unavailable';
            break;
          default:
            userErrorMsg = errorMsg;
        }

        // Rollback optimistic update and show error
        rollbackOptimisticCreation({ general: userErrorMsg });
      }
    }, [isSubmitting, isOptimisticCreation, rollbackOptimisticCreation]),
    [formData.serverId, isSubmitting, isOptimisticCreation]
  );

  // Handle escape key for modal dismissal
  useEffect(() => {
    if (!isVisible) return;

    const handleEscapeKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscapeKey);
    return () => document.removeEventListener('keydown', handleEscapeKey);
  }, [isVisible, onClose]);

  // Focus management - focus first input when modal opens
  useEffect(() => {
    if (isVisible) {
      const timer = setTimeout(() => {
        const firstInput = document.querySelector('#project-name-input') as HTMLInputElement;
        if (firstInput) {
          firstInput.focus();
        }
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [isVisible]);

  // Prevent body scroll when modal is open
  useEffect(() => {
    if (isVisible) {
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = '';
      };
    }
  }, [isVisible]);

  // Use atom-based server operation tracking instead of manual ref counting
  const serverOperation = useAtomValue(serverOperationAtom);
  
  // Auto-select newly added servers using server operation atom with race condition protection
  useEffect(() => {
    if (!isVisible || serverOperation.type !== 'add' || !serverOperation.serverId) return;

    // Use timeout to ensure server has been fully added to the state
    const timeoutId = setTimeout(() => {
      // Find the newly added server with additional validation
      const newServer = servers?.find(s => s.id === serverOperation.serverId);
      if (!newServer) {
        console.warn('Server auto-selection: Server not found after creation', {
          expectedId: serverOperation.serverId,
          availableServers: servers?.map(s => s.id) || [],
        });
        return;
      }

      // Only auto-select if no server is currently selected (avoid overriding user selection)
      if (!formData.serverId) {
        updateField('serverId', newServer.id);
        
        // Close the dropdown if it was open
        setShowServerDropdown(false);
        setActiveOptionId(null);
        
        // Notify parent component about the server addition
        if (onServerAdded) {
          onServerAdded({
            id: newServer.id,
            name: newServer.name,
          });
        }
      }
    }, 50); // Small delay to ensure state consistency

    // Cleanup timeout on unmount or dependency change
    return () => clearTimeout(timeoutId);
  }, [serverOperation, servers, updateField, isVisible, onServerAdded, formData.serverId]);

  // Validate form fields
  const validateForm = useCallback((): boolean => {
    const newErrors: FormErrors = {};

    // Project name validation
    if (!formData.name.trim()) {
      newErrors.name = 'Project name is required';
    } else if (formData.name.trim().length < 2) {
      newErrors.name = 'Project name must be at least 2 characters';
    } else if (formData.name.trim().length > 100) {
      newErrors.name = 'Project name must be less than 100 characters';
    }

    // Project path validation with comprehensive security checks
    if (!formData.path.trim()) {
      newErrors.path = 'Project path is required';
    } else {
      const pathValidation = validateAndSanitizePath(formData.path);
      if (!pathValidation.isValid) {
        newErrors.path = pathValidation.error || 'Invalid path';
      }
    }

    // Server selection validation with comprehensive checks
    if (!formData.serverId) {
      newErrors.serverId = 'Server selection is required';
    } else {
      const selectedServer = servers?.find(s => s.id === formData.serverId);
      if (!selectedServer) {
        newErrors.serverId = 'Selected server is not available';
      } else {
        // Validate server configuration
        if (!selectedServer.websocketUrl) {
          newErrors.serverId = 'Server configuration is incomplete';
        } else {
          try {
            new URL(selectedServer.websocketUrl);
          } catch {
            newErrors.serverId = 'Server connection URL is invalid';
          }
        }
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData, servers, setErrors]);

  // Handle input changes
  const handleInputChange = (field: 'name' | 'path') => (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    updateField(field, value);
  };

  // Handle server selection
  const handleServerSelect = useCallback((serverId: string) => {
    updateField('serverId', serverId);
    setShowServerDropdown(false);
    setActiveOptionId(null);
  }, [updateField]);

  // Handle "Add New Server" selection
  const handleAddNewServer = useCallback(() => {
    setShowServerDropdown(false);
    onAddServer();
    // Form state is preserved in atoms, so when user returns from server creation,
    // their project name and path will still be there
  }, [onAddServer]);

  // Handle keyboard navigation for server dropdown
  const handleServerDropdownKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (!showServerDropdown) return;

    const allOptions = [...(servers || []), { id: 'add-new', name: 'Add New Server' }];
    const currentIndex = activeOptionId ? allOptions.findIndex(opt => opt.id === activeOptionId) : -1;

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        const nextIndex = currentIndex < allOptions.length - 1 ? currentIndex + 1 : 0;
        setActiveOptionId(allOptions[nextIndex].id);
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        const prevIndex = currentIndex > 0 ? currentIndex - 1 : allOptions.length - 1;
        setActiveOptionId(allOptions[prevIndex].id);
        break;
      }
      case 'Enter':
      case ' ':
        e.preventDefault();
        if (activeOptionId === 'add-new') {
          handleAddNewServer();
        } else if (activeOptionId) {
          handleServerSelect(activeOptionId);
        }
        break;
      case 'Escape':
        e.preventDefault();
        setShowServerDropdown(false);
        setActiveOptionId(null);
        break;
    }
  }, [showServerDropdown, servers, activeOptionId, handleServerSelect, handleAddNewServer]);

  // Handle opening server dropdown
  const handleOpenServerDropdown = useCallback(() => {
    setShowServerDropdown(true);
    // Set active option to currently selected server or first option
    if (formData.serverId) {
      setActiveOptionId(formData.serverId);
    } else if (servers && servers.length > 0) {
      setActiveOptionId(servers[0]?.id);
    } else {
      setActiveOptionId('add-new');
    }
  }, [formData.serverId, servers]);


  // Handle form submission with enhanced WebSocket integration and optimistic updates
  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    // Comprehensive server and connection validation
    if (!selectedServer) {
      failSubmission({
        general: 'Please select a server before creating the project.',
        serverId: 'Server selection is required',
      });
      return;
    }

    // Validate server exists in current servers list (prevent stale references)
    const currentServer = servers?.find(s => s.id === selectedServer.id);
    if (!currentServer) {
      failSubmission({
        general: 'Selected server is no longer available. Please select a different server.',
        serverId: 'Selected server is not available',
      });
      return;
    }

    // Validate WebSocket connection
    if (!webSocket) {
      failSubmission({
        general: 'Unable to establish connection to server. Please try again.',
      });
      return;
    }

    if (!webSocket.isConnected) {
      // Queue the request for when connection is restored
      const requestId = `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      
      queueProjectCreationRequest({
        formData: {
          name: formData.name.trim(),
          path: formData.path.trim(),
          serverId: formData.serverId,
        },
        requestId,
      });
      
      // Start connection retry process
      startConnectionRetry('Server connection lost during project creation');
      
      setErrors({
        general: 'Connection lost. Your project creation request has been queued and will be processed when connection is restored.',
      });
      return;
    }

    // Validate WebSocket URL format
    try {
      new URL(currentServer.websocketUrl);
    } catch {
      failSubmission({
        general: 'Server connection URL is invalid. Please contact support.',
        serverId: 'Invalid server configuration',
      });
      return;
    }

    try {
      // Validate and sanitize the path before sending
      const pathValidation = validateAndSanitizePath(formData.path);
      if (!pathValidation.isValid) {
        throw new ProjectCreationError(pathValidation.error || 'Invalid path');
      }

      // Generate unique request ID for optimistic updates
      const requestId = `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      // Start optimistic project creation
      startOptimisticCreation({
        formData: {
          name: formData.name.trim(),
          path: pathValidation.sanitizedPath!,
          serverId: formData.serverId,
        },
        requestId,
      });

      // Create enhanced WebSocket message for project creation
      const createMessage: CreateProjectMessage = {
        type: 'create_project',
        data: {
          name: formData.name.trim(),
          path: pathValidation.sanitizedPath!,
          description: undefined, // Optional description field
          server_id: formData.serverId,
          template: undefined, // Optional template field
        },
      };

      // Send project creation request via WebSocket
      const messageSent = webSocket.send(createMessage);
      if (!messageSent) {
        throw new ProjectCreationError('Failed to send project creation request');
      }

      // Response will be handled by WebSocket message listeners
      // (project_created message for success, project_creation_error for failure)
      
    } catch (error) {
      console.error('Project creation error:', error);
      
      // Handle different error types with appropriate user messages
      let errorMessage = 'Failed to create project. Please try again.';
      
      if (error instanceof ProjectCreationError) {
        errorMessage = error.message;
      } else if (error instanceof Error) {
        errorMessage = error.message;
      }

      // Rollback optimistic update on immediate error
      rollbackOptimisticCreation({ general: errorMessage });
    }
  }, [formData, validateForm, selectedServer, webSocket, servers, startOptimisticCreation, rollbackOptimisticCreation, queueProjectCreationRequest, startConnectionRetry, setErrors, failSubmission]);

  // Handle modal backdrop click
  const handleBackdropClick = useCallback((e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  }, [onClose]);

  if (!isVisible) {
    return null;
  }

  return (
    <div 
      className={cn(
        "fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4",
        // Full screen on mobile with safe area handling
        "sm:p-6 md:p-8",
        className
      )}
      onClick={handleBackdropClick}
      role="dialog"
      aria-modal="true"
      aria-labelledby="project-creation-title"
    >
      <Card className={cn(
        "w-full max-w-md overflow-hidden",
        // Full height on small screens, auto on larger
        "max-h-[90vh] sm:max-h-[80vh]",
        // Handle safe area on mobile devices
        "safe-area-inset"
      )}>
        <CardHeader className="flex-row items-center justify-between border-b border-gray-200 p-4 dark:border-gray-700">
          <h2 
            id="project-creation-title"
            className="text-lg font-semibold text-gray-900 dark:text-gray-100"
          >
            Create New Project
          </h2>
          <IconButton
            icon={X}
            onPress={onClose}
            size="sm"
            variant="ghost"
            aria-label="Close project creation modal"
            disabled={isSubmitting}
          />
        </CardHeader>

        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4 overflow-y-auto p-4">
            {/* General error message */}
            {errors.general && (
              <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
                <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-red-600 dark:text-red-400" />
                <p className="text-sm text-red-700 dark:text-red-300">
                  {errors.general}
                </p>
              </div>
            )}

            {/* Optimistic creation indicator */}
            {isOptimisticCreation && optimisticProject && (
              <div className="flex items-start gap-2 rounded-md border border-blue-200 bg-blue-50 p-3 dark:border-blue-800 dark:bg-blue-900/20">
                <div className="mt-0.5 h-4 w-4 flex-shrink-0">
                  <div 
                    className="h-full w-full animate-spin rounded-full border-2 border-blue-300 border-t-blue-600 dark:border-blue-700 dark:border-t-blue-400"
                    aria-label="Creating project"
                    role="status"
                  ></div>
                </div>
                <div className="text-sm text-blue-700 dark:text-blue-300">
                  <p className="font-medium">Creating project "{optimisticProject.name}"...</p>
                  <p className="text-xs opacity-75">This may take a moment. Please wait.</p>
                </div>
                {/* Live region for screen reader announcements */}
                <div
                  className="sr-only"
                  aria-live="polite"
                  aria-atomic="true"
                  role="status"
                >
                  Creating project {optimisticProject.name}, please wait
                </div>
              </div>
            )}

            {/* Connection retry indicator */}
            {isConnectionRetrying && (
              <div className="flex items-start gap-2 rounded-md border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-800 dark:bg-yellow-900/20">
                <div className="mt-0.5 h-4 w-4 flex-shrink-0">
                  <div 
                    className="h-full w-full animate-spin rounded-full border-2 border-yellow-300 border-t-yellow-600 dark:border-yellow-700 dark:border-t-yellow-400"
                    aria-label="Reconnecting to server"
                    role="status"
                  ></div>
                </div>
                <div className="text-sm text-yellow-700 dark:text-yellow-300">
                  <p className="font-medium">Reconnecting to server...</p>
                  <p className="text-xs opacity-75">
                    Attempt {connectionRetryState.retryAttempt} of {connectionRetryState.maxRetries}
                  </p>
                </div>
                {/* Live region for screen reader announcements */}
                <div
                  className="sr-only"
                  aria-live="polite"
                  aria-atomic="true"
                  role="status"
                >
                  Reconnecting to server, attempt {connectionRetryState.retryAttempt} of {connectionRetryState.maxRetries}
                </div>
              </div>
            )}

            {/* Queued requests indicator */}
            {queuedRequestsCount > 0 && !isConnectionRetrying && (
              <div className="flex items-start gap-2 rounded-md border border-orange-200 bg-orange-50 p-3 dark:border-orange-800 dark:bg-orange-900/20">
                <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-orange-600 dark:text-orange-400" />
                <div className="text-sm text-orange-700 dark:text-orange-300">
                  <p className="font-medium">Connection lost - project creation queued</p>
                  <p className="text-xs opacity-75">
                    {queuedRequestsCount} request{queuedRequestsCount > 1 ? 's' : ''} waiting for connection
                  </p>
                </div>
              </div>
            )}

            {/* Connection error with max retries reached */}
            {connectionRetryState.lastError && 
             connectionRetryState.retryAttempt >= connectionRetryState.maxRetries && 
             !isConnectionRetrying && (
              <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
                <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-red-600 dark:text-red-400" />
                <div className="text-sm text-red-700 dark:text-red-300">
                  <p className="font-medium">Connection failed</p>
                  <p className="text-xs opacity-75">{connectionRetryState.lastError}</p>
                </div>
              </div>
            )}

            {/* Project name input */}
            <div>
              <Input
                id="project-name-input"
                label="Project Name"
                placeholder="My Awesome Project"
                value={formData.name}
                onChange={handleInputChange('name')}
                error={errors.name}
                required
                autoComplete="off"
                disabled={isSubmitting}
                maxLength={100}
              />
            </div>

            {/* Project path input */}
            <div>
              <Input
                label="Project Path"
                placeholder="/path/to/project or ~/projects/my-project"
                value={formData.path}
                onChange={handleInputChange('path')}
                error={errors.path}
                required
                autoComplete="off"
                disabled={isSubmitting}
              />
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                The local filesystem path where your project is located
              </p>
            </div>

            {/* Server selection */}
            <div>
              <label 
                htmlFor="server-selection"
                className="mb-2 block text-sm font-medium text-gray-900 dark:text-gray-100"
              >
                Server <span className="ml-1 text-red-500" aria-label="required">*</span>
              </label>
              
              <div className="relative">
                <button
                  id="server-selection"
                  type="button"
                  onClick={() => showServerDropdown ? setShowServerDropdown(false) : handleOpenServerDropdown()}
                  onKeyDown={handleServerDropdownKeyDown}
                  disabled={isSubmitting}
                  className={cn(
                    "flex w-full items-center justify-between rounded-md border bg-white px-3 py-2 text-left text-base",
                    "h-11 min-h-[44px]", // 44px minimum touch target
                    "border-gray-300 text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2",
                    "disabled:cursor-not-allowed disabled:bg-gray-50 disabled:opacity-50 dark:disabled:bg-gray-700",
                    errors.serverId && "border-red-500 focus-visible:ring-red-500"
                  )}
                  role="combobox"
                  aria-expanded={showServerDropdown}
                  aria-haspopup="listbox"
                  aria-controls="server-listbox"
                  aria-owns="server-listbox"
                  aria-activedescendant={activeOptionId ? `server-option-${activeOptionId}` : undefined}
                  aria-invalid={errors.serverId ? 'true' : 'false'}
                  aria-describedby={errors.serverId ? "server-error server-help" : "server-help"}
                  aria-label={selectedServer ? `Selected server: ${selectedServer.name}` : 'Select a server'}
                >
                  <span className="flex items-center gap-2">
                    <Server className="h-4 w-4 text-gray-500" />
                    {selectedServer ? (
                      <div className="flex flex-col">
                        <span className="font-medium">{selectedServer.name}</span>
                        <span className="text-xs text-gray-500 dark:text-gray-400">
                          {selectedServer.websocketUrl}
                        </span>
                      </div>
                    ) : (
                      <span className="text-gray-500 dark:text-gray-400">
                        Select a server...
                      </span>
                    )}
                  </span>
                  <span className="text-gray-400">▼</span>
                </button>

                {/* Server dropdown */}
                {showServerDropdown && (
                  <div 
                    id="server-listbox"
                    role="listbox"
                    aria-label="Server options"
                    className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md border border-gray-300 bg-white py-1 shadow-lg dark:border-gray-600 dark:bg-gray-800"
                  >
                    {hasServers ? (
                      <>
                        {servers?.map((server) => (
                          <button
                            key={server.id}
                            id={`server-option-${server.id}`}
                            type="button"
                            onClick={() => handleServerSelect(server.id)}
                            className={cn(
                              "flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700",
                              "min-h-[44px]", // Touch target
                              selectedServer?.id === server.id && "bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-300",
                              activeOptionId === server.id && "bg-gray-100 dark:bg-gray-700"
                            )}
                            role="option"
                            aria-selected={selectedServer?.id === server.id}
                          >
                            <Server className="h-4 w-4" />
                            <div>
                              <div className="font-medium">{server.name}</div>
                              <div className="text-xs text-gray-500 dark:text-gray-400">
                                {server.websocketUrl}
                              </div>
                            </div>
                          </button>
                        ))}
                        <div className="border-t border-gray-200 dark:border-gray-700" />
                      </>
                    ) : null}
                    
                    {/* Add New Server option */}
                    <button
                      id="server-option-add-new"
                      type="button"
                      onClick={handleAddNewServer}
                      className={cn(
                        "flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-blue-600 hover:bg-gray-100 dark:text-blue-400 dark:hover:bg-gray-700",
                        "min-h-[44px]", // Touch target
                        activeOptionId === 'add-new' && "bg-gray-100 dark:bg-gray-700"
                      )}
                      role="option"
                      aria-selected={false}
                    >
                      <Plus className="h-4 w-4" />
                      <span className="font-medium">Add New Server</span>
                    </button>
                  </div>
                )}
              </div>

              {/* Help text for server dropdown */}
              <p
                id="server-help"
                className="mt-1 text-xs text-gray-500 dark:text-gray-400"
              >
                Choose the server where your project will be managed. Use arrow keys to navigate options.
              </p>

              {errors.serverId && (
                <p
                  id="server-error"
                  className="mt-1 text-sm text-red-600 dark:text-red-400"
                  role="alert"
                  aria-live="polite"
                >
                  {errors.serverId}
                </p>
              )}
            </div>
          </CardContent>

          <CardFooter className="flex gap-3 border-t border-gray-200 p-4 dark:border-gray-700">
            <Button
              type="button"
              variant="secondary"
              fullWidth
              onPress={onClose}
              disabled={isSubmitting || isOptimisticCreation}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              fullWidth
              loading={isSubmitting || isOptimisticCreation}
              disabled={!isValid || isOptimisticCreation}
              onPress={() => {}} // Form submission handled by form onSubmit
            >
              {isOptimisticCreation ? 'Creating...' : 'Create Project'}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}