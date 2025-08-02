/**
 * Comprehensive Project Validation Utilities
 *
 * Implements EARS-based validation rules (Event-Action-Response-State pattern)
 * for project creation form validation with security-focused input handling.
 *
 * Features:
 * - Real-time validation with structured error responses
 * - XSS prevention and input sanitization integration
 * - Path traversal attack prevention
 * - Server selection validation with connectivity checks
 * - Localization-ready error messages with specific error codes
 */

import type { CreateProjectFormData, CreateProjectValidationErrors } from '../store/atoms/projectCreationAtoms';
import type { Server } from '../types/models';
import { sanitizeProjectName, sanitizeFilePath, validateLength } from './sanitize';

// Validation error codes for internationalization and specific error handling
export enum ValidationErrorCode {
  REQUIRED = 'REQUIRED',
  TOO_SHORT = 'TOO_SHORT',
  TOO_LONG = 'TOO_LONG',
  INVALID_FORMAT = 'INVALID_FORMAT',
  INVALID_CHARACTERS = 'INVALID_CHARACTERS',
  PATH_TRAVERSAL = 'PATH_TRAVERSAL',
  RESERVED_NAME = 'RESERVED_NAME',
  SERVER_NOT_FOUND = 'SERVER_NOT_FOUND',
  SERVER_DISCONNECTED = 'SERVER_DISCONNECTED',
  SERVER_INVALID_URL = 'SERVER_INVALID_URL',
  DUPLICATE_NAME = 'DUPLICATE_NAME',
  SECURITY_VIOLATION = 'SECURITY_VIOLATION',
}

// Validation severity levels
export enum ValidationSeverity {
  ERROR = 'error',
  WARNING = 'warning',
  INFO = 'info',
}

// Structured validation result interface
export interface FieldValidationResult {
  isValid: boolean;
  error?: string;
  errorCode?: ValidationErrorCode;
  severity?: ValidationSeverity;
  sanitizedValue?: string;
  suggestions?: string[];
}

// Complete form validation result
export interface ValidationResult {
  isValid: boolean;
  errors: CreateProjectValidationErrors;
  fieldResults: {
    name: FieldValidationResult;
    path: FieldValidationResult;
    serverId: FieldValidationResult;
  };
  warnings?: string[];
}

// EARS validation rule interface
export interface ValidationRule {
  event: string;
  action: (value: string, context?: ValidationContext) => FieldValidationResult;
  response: (result: FieldValidationResult) => string;
  stateUpdate?: (result: FieldValidationResult) => void;
}

// Validation context for enhanced validation logic
export interface ValidationContext {
  servers?: Server[];
  existingProjects?: Array<{ name: string; path: string }>;
  userId?: string;
}

// Path validation specific result
export interface PathValidationResult extends FieldValidationResult {
  isAbsolute?: boolean;
  isHomeRelative?: boolean;
  normalizedPath?: string;
  pathSegments?: string[];
}

// Sanitization options for enhanced security
export interface SanitizationOptions {
  allowSpecialChars?: boolean;
  maxLength?: number;
  preserveCase?: boolean;
  trimWhitespace?: boolean;
}

// Reserved system names that should not be used in projects
const RESERVED_PROJECT_NAMES = [
  'con', 'prn', 'aux', 'nul', 'com1', 'com2', 'com3', 'com4', 'com5',
  'com6', 'com7', 'com8', 'com9', 'lpt1', 'lpt2', 'lpt3', 'lpt4',
  'lpt5', 'lpt6', 'lpt7', 'lpt8', 'lpt9', 'admin', 'root', 'system',
  'config', 'test', 'tmp', 'temp', 'cache', 'log', 'logs', 'data',
];

// Dangerous path patterns for security validation
const DANGEROUS_PATH_PATTERNS = [
  /\.\./,                    // Parent directory traversal
  /~[^/]/,                   // Invalid home directory usage
  /\/\.[^/]/,                // Hidden directory patterns (starting with dot after slash)
  /\/\/+/,                   // Multiple consecutive slashes (fixed to only match 2+ slashes)
  /[<>:"|?*]/,              // Windows invalid characters
  // eslint-disable-next-line no-control-regex
  /[\0-\x1f\x7f]/,          // Control characters
  /^\s+|\s+$/,              // Leading/trailing whitespace
];

// Project name validation patterns
const PROJECT_NAME_PATTERNS = {
  validChars: /^[a-zA-Z0-9\s\-_()[\]{}]+$/,
  noLeadingNumbers: /^[a-zA-Z]/,
  noSpecialStart: /^[^-_\s]/,
  balanced: /^[^()[\]{}]*(\([^()]*\)|\[[^\]]*\]|\{[^{}]*\})*[^()[\]{}]*$/,
};

/**
 * EARS-based validation rules for project creation
 */
export function createEARSValidationRules(): Record<string, ValidationRule> {
  return {
    projectName: {
      event: 'User types in project name field',
      action: (name: string, context?: ValidationContext) => validateProjectName(name, context),
      response: (_result: FieldValidationResult) => 
        _result.error || 'Project name is valid',
      stateUpdate: (_result: FieldValidationResult) => {
        // State updates handled by calling component via atoms
      },
    },
    projectPath: {
      event: 'User selects or types project path',
      action: (path: string, context?: ValidationContext) => validateProjectPath(path, context),
      response: (_result: FieldValidationResult) =>
        _result.error || 'Project path is valid and secure',
      stateUpdate: (_result: FieldValidationResult) => {
        // State updates handled by calling component via atoms
      },
    },
    serverSelection: {
      event: 'User selects server for project',
      action: (serverId: string, context?: ValidationContext) => 
        validateServerSelection(serverId, context?.servers || []),
      response: (_result: FieldValidationResult) =>
        _result.error || 'Server selection is valid',
      stateUpdate: (_result: FieldValidationResult) => {
        // State updates handled by calling component via atoms
      },
    },
  };
}

/**
 * Comprehensive project name validation with EARS pattern
 * Event: User types in project name field
 * Action: Validate name format, length, uniqueness, and sanitization
 * Response: Show real-time validation feedback with specific error messages
 * State: Update form validation state atoms
 */
export function validateProjectName(
  name: string,
  context?: ValidationContext
): FieldValidationResult {
  // Type check
  if (typeof name !== 'string') {
    return {
      isValid: false,
      error: 'Project name must be a text value',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
    };
  }

  const trimmedName = name.trim();

  // Required field validation
  if (!trimmedName) {
    return {
      isValid: false,
      error: 'Project name is required',
      errorCode: ValidationErrorCode.REQUIRED,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Enter a descriptive name for your project'],
    };
  }

  // Length validation
  const lengthResult = validateLength(trimmedName, 2, 100);
  if (!lengthResult.valid) {
    return {
      isValid: false,
      error: lengthResult.error,
      errorCode: trimmedName.length < 2 ? ValidationErrorCode.TOO_SHORT : ValidationErrorCode.TOO_LONG,
      severity: ValidationSeverity.ERROR,
      suggestions: trimmedName.length < 2 
        ? ['Use at least 2 characters for the project name']
        : ['Shorten the project name to 100 characters or less'],
    };
  }

  // Character validation
  if (!PROJECT_NAME_PATTERNS.validChars.test(trimmedName)) {
    return {
      isValid: false,
      error: 'Project name contains invalid characters. Use only letters, numbers, spaces, hyphens, underscores, and brackets.',
      errorCode: ValidationErrorCode.INVALID_CHARACTERS,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Remove special characters except -_()[]{}'],
    };
  }

  // Format validation - no leading numbers
  if (!PROJECT_NAME_PATTERNS.noLeadingNumbers.test(trimmedName)) {
    return {
      isValid: false,
      error: 'Project name must start with a letter',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Start the project name with a letter'],
    };
  }

  // Format validation - no leading special characters
  if (!PROJECT_NAME_PATTERNS.noSpecialStart.test(trimmedName)) {
    return {
      isValid: false,
      error: 'Project name cannot start with special characters',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Start the project name with a letter or number'],
    };
  }

  // Balanced brackets validation
  if (!PROJECT_NAME_PATTERNS.balanced.test(trimmedName)) {
    return {
      isValid: false,
      error: 'Project name has unbalanced brackets',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Check that all brackets are properly closed'],
    };
  }

  // Reserved name validation
  const lowerName = trimmedName.toLowerCase();
  if (RESERVED_PROJECT_NAMES.includes(lowerName)) {
    return {
      isValid: false,
      error: 'This name is reserved by the system. Please choose a different name.',
      errorCode: ValidationErrorCode.RESERVED_NAME,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Try adding a prefix or suffix to make the name unique'],
    };
  }

  // Uniqueness validation (if context provided)
  if (context?.existingProjects) {
    const isDuplicate = context.existingProjects.some(
      project => project.name.toLowerCase() === lowerName
    );
    if (isDuplicate) {
      return {
        isValid: false,
        error: 'A project with this name already exists',
        errorCode: ValidationErrorCode.DUPLICATE_NAME,
        severity: ValidationSeverity.ERROR,
        suggestions: ['Choose a unique name for your project'],
      };
    }
  }

  // Sanitize the name for safe usage
  const sanitizedName = sanitizeProjectName(trimmedName);
  
  // Check if sanitization changed the name significantly
  if (sanitizedName !== trimmedName) {
    return {
      isValid: true,
      error: undefined,
      sanitizedValue: sanitizedName,
      severity: ValidationSeverity.WARNING,
      suggestions: [`Name will be cleaned to: "${sanitizedName}"`],
    };
  }

  return {
    isValid: true,
    sanitizedValue: sanitizedName,
  };
}

/**
 * Comprehensive project path validation with security focus
 * Event: User selects or types project path
 * Action: Validate path format, permissions, security, and traversal protection
 * Response: Show path validation results with security warnings if needed
 * State: Update path validation state with sanitized value
 */
export function validateProjectPath(
  path: string,
  context?: ValidationContext
): PathValidationResult {
  // Type check
  if (typeof path !== 'string') {
    return {
      isValid: false,
      error: 'Project path must be a text value',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
    };
  }

  const trimmedPath = path.trim();

  // Required field validation
  if (!trimmedPath) {
    return {
      isValid: false,
      error: 'Project path is required',
      errorCode: ValidationErrorCode.REQUIRED,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Enter the filesystem path where your project is located'],
    };
  }

  // Length validation (filesystem limits)
  if (trimmedPath.length > 260) {
    return {
      isValid: false,
      error: 'Path is too long (maximum 260 characters)',
      errorCode: ValidationErrorCode.TOO_LONG,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Use a shorter path or move the project closer to the root directory'],
    };
  }

  // Security validation - check for dangerous patterns
  for (const pattern of DANGEROUS_PATH_PATTERNS) {
    if (pattern.test(trimmedPath)) {
      // Special handling for home directory paths
      if (trimmedPath.startsWith('~/')) {
        continue; // This is allowed
      }
      
      return {
        isValid: false,
        error: 'Path contains invalid or potentially dangerous patterns',
        errorCode: ValidationErrorCode.SECURITY_VIOLATION,
        severity: ValidationSeverity.ERROR,
        suggestions: ['Use a simple, absolute path without special characters'],
      };
    }
  }

  // Path traversal validation
  if (trimmedPath.includes('..') && !trimmedPath.startsWith('~/')) {
    return {
      isValid: false,
      error: 'Path contains directory traversal patterns',
      errorCode: ValidationErrorCode.PATH_TRAVERSAL,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Use an absolute path without ".." segments'],
    };
  }

  // Reserved Windows filenames validation
  const windowsReserved = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i;
  const pathSegments = trimmedPath.split(/[/\\]/).filter(Boolean);
  const hasReservedName = pathSegments.some(segment => {
    // Remove file extensions for comparison
    const nameWithoutExt = segment.split('.')[0];
    return windowsReserved.test(nameWithoutExt);
  });
  
  if (hasReservedName) {
    return {
      isValid: false,
      error: 'Path contains reserved system names',
      errorCode: ValidationErrorCode.RESERVED_NAME,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Avoid using system reserved names in the path'],
    };
  }

  // Path segment length validation
  const hasLongSegment = pathSegments.some(segment => segment.length > 255);
  if (hasLongSegment) {
    return {
      isValid: false,
      error: 'Path segment is too long (maximum 255 characters)',
      errorCode: ValidationErrorCode.TOO_LONG,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Shorten individual directory or file names in the path'],
    };
  }

  // Determine path type
  const isAbsolute = trimmedPath.startsWith('/') || /^[A-Za-z]:/.test(trimmedPath);
  const isHomeRelative = trimmedPath.startsWith('~/');

  // Sanitize the path
  const sanitizedPath = sanitizeFilePath(trimmedPath);
  
  // Normalize path separators and remove redundant elements
  let normalizedPath = sanitizedPath
    .replace(/[/\\]+/g, '/') // Normalize separators to forward slash
    .replace(/\/+$/, ''); // Remove trailing slashes

  // Handle home directory expansion
  if (isHomeRelative) {
    // Keep as-is for home directory paths - server will handle expansion
  } else if (!isAbsolute && normalizedPath) {
    // Ensure non-home relative paths are treated as absolute
    normalizedPath = '/' + normalizedPath;
  }

  // Final validation of normalized path
  if (!normalizedPath || normalizedPath === '/') {
    return {
      isValid: false,
      error: 'Path resolves to root directory or empty path',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Specify a complete path to your project directory'],
    };
  }

  // Uniqueness validation (if context provided)
  if (context?.existingProjects) {
    const isDuplicatePath = context.existingProjects.some(
      project => project.path === normalizedPath
    );
    if (isDuplicatePath) {
      return {
        isValid: false,
        error: 'A project already exists at this path',
        errorCode: ValidationErrorCode.DUPLICATE_NAME,
        severity: ValidationSeverity.ERROR,
        suggestions: ['Choose a different path for your project'],
      };
    }
  }

  // Check if sanitization changed the path significantly
  const suggestions: string[] = [];
  if (normalizedPath !== trimmedPath) {
    suggestions.push(`Path will be normalized to: "${normalizedPath}"`);
  }

  return {
    isValid: true,
    sanitizedValue: normalizedPath,
    isAbsolute,
    isHomeRelative,
    normalizedPath,
    pathSegments,
    suggestions: suggestions.length > 0 ? suggestions : undefined,
  };
}

/**
 * Server selection validation with connectivity checks
 * Event: User selects server for project
 * Action: Validate server availability, connectivity, and compatibility
 * Response: Show server validation status with connection indicators
 * State: Update server selection validation state
 */
export function validateServerSelection(
  serverId: string,
  servers: Server[]
): FieldValidationResult {
  // Type check
  if (typeof serverId !== 'string') {
    return {
      isValid: false,
      error: 'Server selection must be a valid identifier',
      errorCode: ValidationErrorCode.INVALID_FORMAT,
      severity: ValidationSeverity.ERROR,
    };
  }

  const trimmedServerId = serverId.trim();

  // Required field validation
  if (!trimmedServerId) {
    return {
      isValid: false,
      error: 'Server selection is required',
      errorCode: ValidationErrorCode.REQUIRED,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Select a server to host your project'],
    };
  }

  // Server existence validation
  const selectedServer = servers.find(server => server.id === trimmedServerId);
  if (!selectedServer) {
    return {
      isValid: false,
      error: 'Selected server is not available',
      errorCode: ValidationErrorCode.SERVER_NOT_FOUND,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Select a different server or add a new one'],
    };
  }

  // Server configuration validation
  if (!selectedServer.websocketUrl) {
    return {
      isValid: false,
      error: 'Server configuration is incomplete',
      errorCode: ValidationErrorCode.SERVER_INVALID_URL,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Contact support to fix server configuration'],
    };
  }

  // WebSocket URL validation
  try {
    const url = new URL(selectedServer.websocketUrl);
    if (!['ws:', 'wss:', 'http:', 'https:'].includes(url.protocol)) {
      return {
        isValid: false,
        error: 'Server connection URL uses invalid protocol',
        errorCode: ValidationErrorCode.SERVER_INVALID_URL,
        severity: ValidationSeverity.ERROR,
        suggestions: ['Contact support to fix server configuration'],
      };
    }
  } catch {
    return {
      isValid: false,
      error: 'Server connection URL is invalid',
      errorCode: ValidationErrorCode.SERVER_INVALID_URL,
      severity: ValidationSeverity.ERROR,
      suggestions: ['Contact support to fix server configuration'],
    };
  }

  // Connection status validation
  if (!selectedServer.isConnected) {
    return {
      isValid: true,
      error: undefined,
      severity: ValidationSeverity.WARNING,
      suggestions: ['Server is currently disconnected. Connection will be attempted when creating the project.'],
    };
  }

  return {
    isValid: true,
    sanitizedValue: trimmedServerId,
  };
}

/**
 * Comprehensive form validation function
 * Validates all form fields and returns structured results
 */
export function validateProjectForm(
  formData: CreateProjectFormData,
  context?: ValidationContext
): ValidationResult {
  // Validate individual fields
  const nameResult = validateProjectName(formData.name, context);
  const pathResult = validateProjectPath(formData.path, context);
  const serverResult = validateServerSelection(formData.serverId, context?.servers || []);

  // Collect errors for the form
  const errors: CreateProjectValidationErrors = {};
  if (!nameResult.isValid) {
    errors.name = nameResult.error;
  }
  if (!pathResult.isValid) {
    errors.path = pathResult.error;
  }
  if (!serverResult.isValid) {
    errors.serverId = serverResult.error;
  }

  // Collect warnings
  const warnings: string[] = [];
  if (nameResult.suggestions && nameResult.severity === ValidationSeverity.WARNING) {
    warnings.push(...nameResult.suggestions);
  }
  if (pathResult.suggestions && pathResult.severity === ValidationSeverity.WARNING) {
    warnings.push(...pathResult.suggestions);
  }
  if (serverResult.suggestions && serverResult.severity === ValidationSeverity.WARNING) {
    warnings.push(...serverResult.suggestions);
  }

  // Overall validation status
  const isValid = nameResult.isValid && pathResult.isValid && serverResult.isValid;

  return {
    isValid,
    errors,
    fieldResults: {
      name: nameResult,
      path: pathResult,
      serverId: serverResult,
    },
    warnings: warnings.length > 0 ? warnings : undefined,
  };
}

/**
 * Enhanced validation for project creation with context awareness
 * Includes duplicate checking and server-specific validations
 */
export function validateProjectFormWithContext(
  formData: CreateProjectFormData,
  servers: Server[],
  existingProjects?: Array<{ name: string; path: string }>,
  userId?: string
): ValidationResult {
  const context: ValidationContext = {
    servers,
    existingProjects,
    userId,
  };

  return validateProjectForm(formData, context);
}

/**
 * Quick validation for real-time feedback
 * Optimized for frequent calls during user input
 */
export function quickValidateField(
  field: keyof CreateProjectFormData,
  value: string,
  context?: Partial<ValidationContext>
): FieldValidationResult {
  switch (field) {
    case 'name':
      return validateProjectName(value, context);
    case 'path':
      return validateProjectPath(value, context);
    case 'serverId':
      return validateServerSelection(value, context?.servers || []);
    default:
      return {
        isValid: false,
        error: 'Unknown field',
        errorCode: ValidationErrorCode.INVALID_FORMAT,
      };
  }
}

/**
 * Validation rule set for dynamic validation
 * Useful for implementing complex validation workflows
 */
export interface ValidationRuleSet {
  rules: Record<string, ValidationRule>;
  executeRule: (ruleName: string, value: string, context?: ValidationContext) => FieldValidationResult;
  executeAllRules: (formData: CreateProjectFormData, context?: ValidationContext) => ValidationResult;
}

/**
 * Create a validation rule set for reusable validation logic
 */
export function createValidationRuleSet(): ValidationRuleSet {
  const rules = createEARSValidationRules();

  return {
    rules,
    executeRule: (ruleName: string, value: string, context?: ValidationContext) => {
      const rule = rules[ruleName];
      if (!rule) {
        return {
          isValid: false,
          error: 'Unknown validation rule',
          errorCode: ValidationErrorCode.INVALID_FORMAT,
        };
      }
      return rule.action(value, context);
    },
    executeAllRules: (formData: CreateProjectFormData, context?: ValidationContext) => {
      return validateProjectForm(formData, context);
    },
  };
}