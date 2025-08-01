/**
 * Input Sanitization Utilities
 * 
 * Provides security-focused utilities for:
 * - XSS prevention
 * - Input validation and sanitization
 * - URL sanitization
 * - HTML content sanitization
 * - File path validation
 * - SQL injection prevention
 */

// HTML Entity Encoding Map
const HTML_ENTITIES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#x27;',
  '/': '&#x2F;',
  '`': '&#x60;',
  '=': '&#x3D;',
} as const;

// Dangerous HTML Tags (should be stripped)
const DANGEROUS_TAGS = [
  'script',
  'object',
  'embed',
  'link',
  'style',
  'iframe',
  'frame',
  'frameset',
  'applet',
  'meta',
  'form',
  'input',
  'button',
  'textarea',
  'select',
  'option',
] as const;

// Dangerous Attributes (should be stripped)
const DANGEROUS_ATTRIBUTES = [
  'onload',
  'onerror',
  'onclick',
  'onmouseover',
  'onmouseout',
  'onkeydown',
  'onkeyup',
  'onkeypress',
  'onfocus',
  'onblur',
  'onsubmit',
  'onchange',
  'onselect',
  'onreset',
  'onabort',
  'ondragdrop',
  'onresize',
  'onactivate',
  'onafterprint',
  'onmoveend',
  'javascript:',
  'vbscript:',
  'data:',
] as const;

/**
 * Escape HTML entities to prevent XSS attacks
 */
export function escapeHtml(unsafe: string): string {
  return unsafe.replace(/[&<>"'`=/]/g, (match) => HTML_ENTITIES[match] || match);
}

/**
 * Unescape HTML entities
 */
export function unescapeHtml(safe: string): string {
  const entityMap: Record<string, string> = {
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#x27;': "'",
    '&#x2F;': '/',
    '&#x60;': '`',
    '&#x3D;': '=',
  };
  
  return safe.replace(/&[#\w]+;/g, (entity) => entityMap[entity] || entity);
}

/**
 * Strip all HTML tags from a string
 */
export function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '');
}

/**
 * Sanitize HTML content by removing dangerous tags and attributes
 */
export function sanitizeHtml(html: string): string {
  let sanitized = html;
  
  // Remove dangerous tags
  DANGEROUS_TAGS.forEach(tag => {
    const regex = new RegExp(`<\\/?${tag}[^>]*>`, 'gi');
    sanitized = sanitized.replace(regex, '');
  });
  
  // Remove dangerous attributes
  DANGEROUS_ATTRIBUTES.forEach(attr => {
    const regex = new RegExp(`\\s${attr}\\s*=\\s*["'][^"']*["']`, 'gi');
    sanitized = sanitized.replace(regex, '');
  });
  
  // Remove javascript: and data: protocols
  sanitized = sanitized.replace(/(?:javascript|data|vbscript):/gi, '');
  
  return sanitized;
}

/**
 * Sanitize user input for display
 */
export function sanitizeUserInput(input: string): string {
  if (typeof input !== 'string') return '';
  
  return escapeHtml(input.trim());
}

/**
 * Sanitize and validate project name
 */
export function sanitizeProjectName(name: string): string {
  if (typeof name !== 'string') return '';
  
  // Remove HTML and trim
  let sanitized = stripHtml(name).trim();
  
  // Remove dangerous characters but keep alphanumeric, spaces, hyphens, underscores
  sanitized = sanitized.replace(/[^a-zA-Z0-9\s\-_()[\]{}]/g, '');
  
  // Collapse multiple spaces into one
  sanitized = sanitized.replace(/\s+/g, ' ');
  
  // Limit length
  if (sanitized.length > 100) {
    sanitized = sanitized.substring(0, 100).trim();
  }
  
  return sanitized;
}

/**
 * Sanitize and validate server name
 */
export function sanitizeServerName(name: string): string {
  if (typeof name !== 'string') return '';
  
  // Remove HTML and trim
  let sanitized = stripHtml(name).trim();
  
  // Remove dangerous characters but keep alphanumeric, spaces, hyphens, underscores, dots
  sanitized = sanitized.replace(/[^a-zA-Z0-9\s\-_.]/g, '');
  
  // Collapse multiple spaces into one
  sanitized = sanitized.replace(/\s+/g, ' ');
  
  // Limit length
  if (sanitized.length > 50) {
    sanitized = sanitized.substring(0, 50).trim();
  }
  
  return sanitized;
}

/**
 * Sanitize file path to prevent directory traversal attacks
 */
export function sanitizeFilePath(path: string): string {
  if (typeof path !== 'string') return '';
  
  let sanitized = path.trim();
  
  // Remove null bytes
  sanitized = sanitized.replace(/\0/g, '');
  
  // Remove or replace directory traversal attempts
  sanitized = sanitized.replace(/\.\./g, '');
  
  // Remove leading slashes (to prevent absolute path access)
  sanitized = sanitized.replace(/^\/+/, '');
  
  // Remove dangerous characters
  sanitized = sanitized.replace(/[<>:"|?*]/g, '');
  
  // Normalize path separators
  sanitized = sanitized.replace(/\\+/g, '/');
  sanitized = sanitized.replace(/\/+/g, '/');
  
  // Remove trailing slashes
  sanitized = sanitized.replace(/\/+$/, '');
  
  // Limit length
  if (sanitized.length > 500) {
    sanitized = sanitized.substring(0, 500);
  }
  
  return sanitized;
}

/**
 * Sanitize URL input
 */
export function sanitizeUrl(url: string): string {
  if (typeof url !== 'string') return '';
  
  let sanitized = url.trim();
  
  // Remove null bytes and control characters
  // eslint-disable-next-line no-control-regex
  sanitized = sanitized.replace(/[\0-\x1f\x7f]/g, '');
  
  // Remove HTML entities
  sanitized = stripHtml(sanitized);
  
  // Check for valid URL format
  try {
    // If it doesn't contain ://, assume it's a relative URL
    if (!sanitized.includes('://')) {
      // Validate relative URL format
      if (/^[a-zA-Z0-9\-._~:/?#[\]@!$&'()*+,;=%]+$/.test(sanitized)) {
        return sanitized;
      }
      return '';
    }
    
    const parsedUrl = new URL(sanitized);
    
    // Only allow http, https, ws, wss protocols
    if (!['http:', 'https:', 'ws:', 'wss:'].includes(parsedUrl.protocol)) {
      return '';
    }
    
    return parsedUrl.toString();
  } catch {
    return '';
  }
}

/**
 * Sanitize WebSocket URL specifically
 */
export function sanitizeWebSocketUrl(url: string): string {
  if (typeof url !== 'string') return '';
  
  const sanitized = sanitizeUrl(url);
  
  if (!sanitized) return '';
  
  // If it's a relative URL, it's valid for WebSocket
  if (!sanitized.includes('://')) {
    return sanitized;
  }
  
  try {
    const parsedUrl = new URL(sanitized);
    
    // Only allow ws and wss protocols for WebSocket
    if (!['ws:', 'wss:'].includes(parsedUrl.protocol)) {
      return '';
    }
    
    return parsedUrl.toString();
  } catch {
    return '';
  }
}

/**
 * Validate and sanitize JSON input
 */
export function sanitizeJsonInput(input: string): string | null {
  if (typeof input !== 'string') return null;
  
  try {
    // Parse and re-stringify to ensure valid JSON
    const parsed = JSON.parse(input);
    return JSON.stringify(parsed);
  } catch {
    return null;
  }
}

/**
 * Remove SQL injection patterns (basic protection)
 */
export function sanitizeSqlInput(input: string): string {
  if (typeof input !== 'string') return '';
  
  // Remove common SQL injection patterns
  const sqlPatterns = [
    /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|SCRIPT)\b)/gi,
    /(--|\/\*|\*\/|;|'|"|`)/g,
    /(\bOR\s+\d+\s*=\s*\d+)/gi,
    /(\bAND\s+\d+\s*=\s*\d+)/gi,
  ];
  
  let sanitized = input;
  sqlPatterns.forEach(pattern => {
    sanitized = sanitized.replace(pattern, '');
  });
  
  return sanitized.trim();
}

/**
 * Sanitize search query input
 */
export function sanitizeSearchQuery(query: string): string {
  if (typeof query !== 'string') return '';
  
  let sanitized = query.trim();
  
  // Remove HTML
  sanitized = stripHtml(sanitized);
  
  // Remove special regex characters that could cause errors
  sanitized = sanitized.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  
  // Limit length
  if (sanitized.length > 200) {
    sanitized = sanitized.substring(0, 200);
  }
  
  return sanitized;
}

/**
 * Validate input length
 */
export function validateLength(
  input: string,
  minLength: number,
  maxLength: number
): { valid: boolean; error?: string } {
  if (typeof input !== 'string') {
    return { valid: false, error: 'Input must be a string' };
  }
  
  const length = input.trim().length;
  
  if (length < minLength) {
    return { valid: false, error: `Must be at least ${minLength} characters` };
  }
  
  if (length > maxLength) {
    return { valid: false, error: `Must be no more than ${maxLength} characters` };
  }
  
  return { valid: true };
}

/**
 * Validate email format (basic)
 */
export function validateEmail(email: string): { valid: boolean; error?: string } {
  if (typeof email !== 'string') {
    return { valid: false, error: 'Email must be a string' };
  }
  
  const sanitized = sanitizeUserInput(email);
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  
  if (!emailRegex.test(sanitized)) {
    return { valid: false, error: 'Invalid email format' };
  }
  
  return { valid: true };
}

/**
 * Create a Content Security Policy nonce
 */
export function generateCSPNonce(): string {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * Sanitize CSS class names
 */
export function sanitizeClassName(className: string): string {
  if (typeof className !== 'string') return '';
  
  // Remove dangerous characters and keep only valid CSS class characters
  return className.replace(/[^a-zA-Z0-9\-_\s]/g, '').trim();
}

/**
 * Rate limiting helper (basic implementation)
 */
export function createRateLimit(maxRequests: number, windowMs: number) {
  const requests = new Map<string, number[]>();
  
  return (identifier: string): boolean => {
    const now = Date.now();
    const windowStart = now - windowMs;
    
    if (!requests.has(identifier)) {
      requests.set(identifier, []);
    }
    
    const userRequests = requests.get(identifier)!;
    
    // Remove old requests outside the window
    const validRequests = userRequests.filter(time => time > windowStart);
    
    if (validRequests.length >= maxRequests) {
      return false; // Rate limit exceeded
    }
    
    validRequests.push(now);
    requests.set(identifier, validRequests);
    
    return true; // Request allowed
  };
}