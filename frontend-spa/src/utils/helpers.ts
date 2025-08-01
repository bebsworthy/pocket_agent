/**
 * Helper Utilities
 * 
 * Contains common utility functions for:
 * - String manipulation and validation
 * - Date/time formatting
 * - URL validation and parsing
 * - Mobile-specific utilities
 * - Touch detection and viewport helpers
 * - Performance optimization helpers
 */

import { VALIDATION, DEVICE, BREAKPOINTS, VIEWPORT } from './constants';

// Type Guards and Validation
export function isString(value: unknown): value is string {
  return typeof value === 'string';
}

export function isNumber(value: unknown): value is number {
  return typeof value === 'number' && !isNaN(value);
}

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string') return value.trim().length === 0;
  if (Array.isArray(value)) return value.length === 0;
  if (isObject(value)) return Object.keys(value).length === 0;
  return false;
}

// String Utilities
export function truncateString(str: string, maxLength: number, suffix = '...'): string {
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength - suffix.length) + suffix;
}

export function capitalizeFirst(str: string): string {
  if (!str) return str;
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

export function slugify(str: string): string {
  return str
    .toLowerCase()
    .trim()
    .replace(/[^\w\s-]/g, '')
    .replace(/[\s_-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Date and Time Utilities
export function formatRelativeTime(date: Date | string | number): string {
  const now = new Date();
  const targetDate = new Date(date);
  const diffInSeconds = Math.floor((now.getTime() - targetDate.getTime()) / 1000);
  
  if (diffInSeconds < 60) return 'just now';
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  if (diffInSeconds < 2592000) return `${Math.floor(diffInSeconds / 86400)}d ago`;
  
  return targetDate.toLocaleDateString();
}

export function formatDateTime(date: Date | string | number): string {
  const targetDate = new Date(date);
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(targetDate);
}

// URL Validation and Parsing
export function isValidUrl(url: string): boolean {
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

export function isValidWebSocketUrl(url: string): boolean {
  if (!url) return false;
  
  // Allow relative URLs
  if (!url.includes('://')) {
    return url.length >= VALIDATION.WEBSOCKET_URL.MIN_LENGTH && 
           url.length <= VALIDATION.WEBSOCKET_URL.MAX_LENGTH;
  }
  
  try {
    const parsedUrl = new URL(url);
    return parsedUrl.protocol === 'ws:' || parsedUrl.protocol === 'wss:';
  } catch {
    return false;
  }
}

export function normalizeWebSocketUrl(url: string): string {
  if (!url) return url;
  
  // If it's already a full URL, return as-is
  if (url.includes('://')) return url;
  
  // Determine protocol based on current page
  const isSecure = typeof window !== 'undefined' && window.location.protocol === 'https:';
  const protocol = isSecure ? 'wss://' : 'ws://';
  
  // Remove leading slashes
  const cleanUrl = url.replace(/^\/+/, '');
  
  return `${protocol}${cleanUrl}`;
}

// Mobile and Touch Utilities
export function isTouchDevice(): boolean {
  return DEVICE.IS_TOUCH;
}

export function isMobileDevice(): boolean {
  if (typeof window === 'undefined') return false;
  return window.innerWidth <= BREAKPOINTS.MD;
}

export function isIOSDevice(): boolean {
  return DEVICE.IS_IOS;
}

export function isAndroidDevice(): boolean {
  return DEVICE.IS_ANDROID;
}

export function getViewportSize(): { width: number; height: number } {
  if (typeof window === 'undefined') {
    return { width: 0, height: 0 };
  }
  
  return {
    width: window.innerWidth,
    height: window.innerHeight,
  };
}

export function isPortraitOrientation(): boolean {
  const { width, height } = getViewportSize();
  return height > width;
}

// Performance Utilities
/**
 * Creates a debounced version of a function that delays execution until after delay milliseconds
 * 
 * Debouncing ensures that a function is only called once after a series of rapid calls,
 * with the delay starting from the last call. This is useful for expensive operations
 * triggered by rapid user input (like search suggestions or resize handlers).
 * 
 * @param func - The function to debounce
 * @param delay - The number of milliseconds to delay execution
 * @returns Debounced version of the function
 * 
 * @example
 * ```typescript
 * // Debounce search input to avoid excessive API calls
 * const debouncedSearch = debounce((query: string) => {
 *   searchAPI(query);
 * }, 300);
 * 
 * // Only the last call within 300ms will execute
 * searchInput.addEventListener('input', (e) => {
 *   debouncedSearch(e.target.value);
 * });
 * 
 * // Debounce window resize handler for performance
 * const debouncedResize = debounce(() => {
 *   updateLayout();
 * }, 250);
 * 
 * window.addEventListener('resize', debouncedResize);
 * ```
 */
export function debounce<T extends (...args: unknown[]) => void>(
  func: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timeoutId: NodeJS.Timeout;
  
  return (...args: Parameters<T>) => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => func(...args), delay);
  };
}

export function throttle<T extends (...args: unknown[]) => void>(
  func: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle: boolean;
  
  return (...args: Parameters<T>) => {
    if (!inThrottle) {
      func(...args);
      inThrottle = true;
      setTimeout(() => (inThrottle = false), limit);
    }
  };
}

// Array Utilities
export function unique<T>(array: T[]): T[] {
  return [...new Set(array)];
}

export function groupBy<T, K extends string | number | symbol>(
  array: T[],
  keyFn: (item: T) => K
): Record<K, T[]> {
  return array.reduce((groups, item) => {
    const key = keyFn(item);
    if (!groups[key]) {
      groups[key] = [];
    }
    groups[key].push(item);
    return groups;
  }, {} as Record<K, T[]>);
}

export function sortBy<T>(
  array: T[],
  keyFn: (item: T) => string | number | Date,
  direction: 'asc' | 'desc' = 'asc'
): T[] {
  return [...array].sort((a, b) => {
    const aVal = keyFn(a);
    const bVal = keyFn(b);
    
    if (aVal < bVal) return direction === 'asc' ? -1 : 1;
    if (aVal > bVal) return direction === 'asc' ? 1 : -1;
    return 0;
  });
}

// Local Storage Utilities
export function isLocalStorageAvailable(): boolean {
  try {
    const test = '__test__';
    localStorage.setItem(test, test);
    localStorage.removeItem(test);
    return true;
  } catch {
    return false;
  }
}

export function safeJsonParse<T>(value: string, fallback: T): T {
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

// Error Handling Utilities
export function createErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return 'An unexpected error occurred';
}

export function isNetworkError(error: unknown): boolean {
  if (error instanceof Error) {
    return error.message.toLowerCase().includes('network') ||
           error.message.toLowerCase().includes('fetch') ||
           error.message.toLowerCase().includes('connection');
  }
  return false;
}

// ID Generation
export function generateId(): string {
  return Math.random().toString(36).substring(2) + Date.now().toString(36);
}

export function generateShortId(): string {
  return Math.random().toString(36).substring(2, 8);
}

// Color and Theme Utilities
export function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null;
}

export function getContrastColor(hex: string): 'light' | 'dark' {
  const rgb = hexToRgb(hex);
  if (!rgb) return 'dark';
  
  // Calculate luminance
  const luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255;
  return luminance > 0.5 ? 'dark' : 'light';
}

// Focus Management
/**
 * Traps keyboard focus within a specified element, useful for modal dialogs and overlays
 * 
 * This function implements the focus trap pattern required for accessible modal dialogs.
 * When active, Tab and Shift+Tab will cycle focus only within the specified element,
 * preventing users from navigating to elements behind a modal.
 * 
 * @param element - The container element to trap focus within
 * @returns Cleanup function to remove the focus trap and event listeners
 * 
 * @example
 * ```typescript
 * // Trap focus in a modal dialog
 * const modal = document.querySelector('[role="dialog"]');
 * const releaseFocus = trapFocus(modal);
 * 
 * // Show modal and trap focus
 * modal.classList.add('visible');
 * 
 * // Clean up when modal closes
 * function closeModal() {
 *   modal.classList.remove('visible');
 *   releaseFocus(); // Remove focus trap
 * }
 * 
 * // Use in React component
 * useEffect(() => {
 *   if (isModalOpen) {
 *     const cleanup = trapFocus(modalRef.current);
 *     return cleanup;
 *   }
 * }, [isModalOpen]);
 * ```
 */
export function trapFocus(element: HTMLElement): () => void {
  const focusableElements = element.querySelectorAll(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  );
  
  const firstFocusable = focusableElements[0] as HTMLElement;
  const lastFocusable = focusableElements[focusableElements.length - 1] as HTMLElement;
  
  function handleKeyDown(e: KeyboardEvent) {
    if (e.key !== 'Tab') return;
    
    if (e.shiftKey) {
      if (document.activeElement === firstFocusable) {
        lastFocusable.focus();
        e.preventDefault();
      }
    } else {
      if (document.activeElement === lastFocusable) {
        firstFocusable.focus();
        e.preventDefault();
      }
    }
  }
  
  element.addEventListener('keydown', handleKeyDown);
  firstFocusable?.focus();
  
  return () => {
    element.removeEventListener('keydown', handleKeyDown);
  };
}

// Animation Frame Utilities
export function nextFrame(callback: () => void): number {
  return requestAnimationFrame(() => {
    requestAnimationFrame(callback);
  });
}

export function waitForNextFrame(): Promise<void> {
  return new Promise(resolve => {
    nextFrame(() => resolve());
  });
}

// Responsive Design Helpers
export function getBreakpoint(): 'sm' | 'md' | 'lg' | 'xl' {
  const width = getViewportSize().width;
  
  if (width >= BREAKPOINTS.XL) return 'xl';
  if (width >= BREAKPOINTS.LG) return 'lg';
  if (width >= BREAKPOINTS.MD) return 'md';
  return 'sm';
}

export function isMobileBreakpoint(): boolean {
  return getBreakpoint() === 'sm';
}

// Touch Target Validation Utilities (for mobile accessibility)
/**
 * Validates that an element meets minimum touch target size requirements for accessibility
 * 
 * This function ensures compliance with WCAG 2.1 AA guidelines which require touch targets
 * to be at least 44x44 CSS pixels for mobile accessibility.
 * 
 * @param element - The DOM element to validate
 * @param minSize - Minimum required size in pixels (default: 44px per WCAG 2.1 AA)
 * @returns Object containing validation result with dimensions and optional error message
 * 
 * @example
 * ```typescript
 * const button = document.querySelector('button');
 * const validation = validateTouchTargetSize(button);
 * 
 * if (!validation.isValid) {
 *   console.warn(validation.message); // "Touch target too small: 32x32px (minimum: 44x44px)"
 *   ensureTouchTargetSize(button); // Automatically fix the size
 * }
 * ```
 */
export function validateTouchTargetSize(
  element: Element,
  minSize = VIEWPORT.TOUCH_TARGET_MIN // WCAG 2.1 AA requirement
): { isValid: boolean; width: number; height: number; message?: string } {
  const rect = element.getBoundingClientRect();
  const width = rect.width;
  const height = rect.height;
  
  const isValid = width >= minSize && height >= minSize;
  
  return {
    isValid,
    width,
    height,
    message: isValid 
      ? undefined 
      : `Touch target too small: ${width}x${height}px (minimum: ${minSize}x${minSize}px)`
  };
}

/**
 * Automatically adjusts an element's padding to meet minimum touch target size requirements
 * 
 * This function programmatically adds padding to ensure the element meets WCAG accessibility
 * guidelines. It's useful for fixing touch targets that are too small without changing
 * the element's core styling.
 * 
 * @param element - The HTML element to modify
 * @param minSize - Minimum required size in pixels (default: 44px)
 * 
 * @example
 * ```typescript
 * // Fix all small buttons in a form
 * const form = document.querySelector('form');
 * const buttons = form.querySelectorAll('button');
 * 
 * buttons.forEach(button => {
 *   const validation = validateTouchTargetSize(button);
 *   if (!validation.isValid) {
 *     ensureTouchTargetSize(button); // Adds padding to reach 44x44px
 *   }
 * });
 * ```
 */
export function ensureTouchTargetSize(
  element: HTMLElement,
  minSize = VIEWPORT.TOUCH_TARGET_MIN
): void {
  const validation = validateTouchTargetSize(element, minSize);
  
  if (!validation.isValid) {
    // Add padding to meet minimum size requirements
    const style = element.style;
    const computedStyle = window.getComputedStyle(element);
    
    const currentPaddingTop = parseInt(computedStyle.paddingTop) || 0;
    const currentPaddingRight = parseInt(computedStyle.paddingRight) || 0;
    const currentPaddingBottom = parseInt(computedStyle.paddingBottom) || 0;
    const currentPaddingLeft = parseInt(computedStyle.paddingLeft) || 0;
    
    if (validation.width < minSize) {
      const neededWidth = minSize - validation.width;
      const additionalHorizontalPadding = Math.ceil(neededWidth / 2);
      style.paddingLeft = `${currentPaddingLeft + additionalHorizontalPadding}px`;
      style.paddingRight = `${currentPaddingRight + additionalHorizontalPadding}px`;
    }
    
    if (validation.height < minSize) {
      const neededHeight = minSize - validation.height;
      const additionalVerticalPadding = Math.ceil(neededHeight / 2);
      style.paddingTop = `${currentPaddingTop + additionalVerticalPadding}px`;
      style.paddingBottom = `${currentPaddingBottom + additionalVerticalPadding}px`;
    }
  }
}

export function validateTouchTargetsInContainer(
  container: Element,
  minSize = VIEWPORT.TOUCH_TARGET_MIN
): Array<{ element: Element; validation: ReturnType<typeof validateTouchTargetSize> }> {
  // Select interactive elements that should have proper touch targets
  const interactiveSelectors = [
    'button',
    'a[href]',
    'input:not([type="hidden"])',
    'select',
    'textarea',
    '[role="button"]',
    '[role="link"]',
    '[role="checkbox"]',
    '[role="radio"]',
    '[tabindex]:not([tabindex="-1"])',
    '[onclick]'
  ];
  
  const elements = container.querySelectorAll(interactiveSelectors.join(', '));
  
  return Array.from(elements).map(element => ({
    element,
    validation: validateTouchTargetSize(element, minSize)
  }));
}

/**
 * Automatically fix all touch targets in a container that are too small
 * @param container - The container element to scan
 * @param minSize - Minimum touch target size (default: 44px)
 * @returns Array of elements that were modified
 */
export function autoFixTouchTargetsInContainer(
  container: Element,
  minSize = VIEWPORT.TOUCH_TARGET_MIN
): HTMLElement[] {
  const validationResults = validateTouchTargetsInContainer(container, minSize);
  const fixedElements: HTMLElement[] = [];
  
  validationResults.forEach(({ element, validation }) => {
    if (!validation.isValid && element instanceof HTMLElement) {
      ensureTouchTargetSize(element, minSize);
      fixedElements.push(element);
    }
  });
  
  return fixedElements;
}

/**
 * Create a touch target validation report for accessibility auditing
 * @param container - The container element to audit
 * @param minSize - Minimum touch target size (default: 44px)
 * @returns Detailed report with statistics and violations
 */
export function createTouchTargetReport(
  container: Element,
  minSize = VIEWPORT.TOUCH_TARGET_MIN  
): {
  totalElements: number;
  validElements: number;
  invalidElements: number;
  violations: Array<{
    element: Element;
    tagName: string;
    selector: string;
    size: { width: number; height: number };
    message: string;
  }>;
  score: number; // Percentage of valid touch targets
} {
  const validationResults = validateTouchTargetsInContainer(container, minSize);
  
  const violations = validationResults
    .filter(result => !result.validation.isValid)
    .map(({ element, validation }) => ({
      element,
      tagName: element.tagName.toLowerCase(),
      selector: generateElementSelector(element),
      size: { width: validation.width, height: validation.height },
      message: validation.message || 'Touch target too small'
    }));
  
  const totalElements = validationResults.length;
  const invalidElements = violations.length;
  const validElements = totalElements - invalidElements;
  const score = totalElements > 0 ? Math.round((validElements / totalElements) * 100) : 100;
  
  return {
    totalElements,
    validElements,
    invalidElements,
    violations,
    score
  };
}

/**
 * Generate a simple CSS selector for an element (for reporting purposes)
 */
function generateElementSelector(element: Element): string {
  if (element.id) {
    return `#${element.id}`;
  }
  
  if (element.className) {
    const classes = Array.from(element.classList).slice(0, 3).join('.');
    return `${element.tagName.toLowerCase()}.${classes}`;
  }
  
  return element.tagName.toLowerCase();
}

// Mobile-specific device detection utilities
export function getDeviceOrientation(): {
  orientation: 'portrait' | 'landscape';
  angle: number;
} {
  if (typeof window === 'undefined') {
    return { orientation: 'portrait', angle: 0 };
  }
  
  const orientation = window.screen?.orientation;
  if (orientation) {
    return {
      orientation: orientation.angle === 0 || orientation.angle === 180 ? 'portrait' : 'landscape',
      angle: orientation.angle
    };
  }
  
  // Fallback for older browsers
  const { width, height } = getViewportSize();
  return {
    orientation: height > width ? 'portrait' : 'landscape',
    angle: height > width ? 0 : 90
  };
}

export function createResponsiveListener(
  callback: (isMobile: boolean, viewport: { width: number; height: number }) => void
): () => void {
  if (typeof window === 'undefined') {
    return () => {};
  }
  
  const handleResize = throttle(() => {
    const viewport = getViewportSize();
    const isMobile = viewport.width <= BREAKPOINTS.MD;
    callback(isMobile, viewport);
  }, 250);
  
  // Initial call
  handleResize();
  
  window.addEventListener('resize', handleResize);
  window.addEventListener('orientationchange', handleResize);
  
  return () => {
    window.removeEventListener('resize', handleResize);
    window.removeEventListener('orientationchange', handleResize);
  };
}

// Reactive device detection functions (call these instead of static constants)
/**
 * Gets comprehensive device information that updates reactively with viewport changes
 * 
 * Unlike static constants, this function re-evaluates device characteristics each time
 * it's called, making it responsive to window resizing, orientation changes, and
 * other dynamic conditions.
 * 
 * @returns Object containing device characteristics and capabilities
 * 
 * @example
 * ```typescript
 * const deviceInfo = getDeviceInfo();
 * 
 * if (deviceInfo.isMobile && deviceInfo.isTouch) {
 *   // Show mobile-optimized touch interface
 *   enableSwipeGestures();
 * }
 * 
 * if (deviceInfo.isIOS) {
 *   // Apply iOS-specific styling
 *   document.body.classList.add('ios-device');
 * }
 * 
 * // React to orientation changes
 * window.addEventListener('orientationchange', () => {
 *   const newInfo = getDeviceInfo();
 *   updateLayout(newInfo.isMobile);
 * });
 * ```
 */
export function getDeviceInfo(): {
  isTouch: boolean;
  isIOS: boolean;
  isAndroid: boolean;
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  userAgent: string;
} {
  if (typeof window === 'undefined') {
    return {
      isTouch: false,
      isIOS: false,
      isAndroid: false,
      isMobile: false,
      isTablet: false,
      isDesktop: true,
      userAgent: ''
    };
  }
  
  const userAgent = navigator.userAgent;
  const viewport = getViewportSize();
  
  const isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  const isIOS = /iPad|iPhone|iPod/.test(userAgent);
  const isAndroid = /Android/.test(userAgent);
  const isMobile = viewport.width <= BREAKPOINTS.MD;
  const isTablet = isTouch && viewport.width > BREAKPOINTS.MD && viewport.width <= BREAKPOINTS.LG;
  const isDesktop = !isTouch || viewport.width > BREAKPOINTS.LG;
  
  return {
    isTouch,
    isIOS,
    isAndroid,
    isMobile,
    isTablet,
    isDesktop,
    userAgent
  };
}

/**
 * Creates a listener that monitors device information changes and calls a callback
 * 
 * This function sets up event listeners for viewport changes, orientation changes,
 * and other events that might affect device detection. The callback is throttled
 * to prevent excessive calls during rapid changes.
 * 
 * @param callback - Function called when device information changes
 * @returns Cleanup function to remove event listeners
 * 
 * @example
 * ```typescript
 * // Monitor device changes throughout app lifecycle
 * const cleanup = createDeviceListener((deviceInfo) => {
 *   // Update UI based on device changes
 *   if (deviceInfo.isMobile) {
 *     showMobileNav();
 *   } else {
 *     showDesktopNav();
 *   }
 *   
 *   // Update touch-specific features
 *   toggleTouchFeatures(deviceInfo.isTouch);
 * });
 * 
 * // Clean up when component unmounts
 * useEffect(() => cleanup, []);
 * ```
 */
export function createDeviceListener(
  callback: (deviceInfo: ReturnType<typeof getDeviceInfo>) => void
): () => void {
  if (typeof window === 'undefined') {
    return () => {};
  }
  
  const handleChange = throttle(() => {
    callback(getDeviceInfo());
  }, 250);
  
  // Initial call
  handleChange();
  
  // Listen for events that might change device detection
  window.addEventListener('resize', handleChange);
  window.addEventListener('orientationchange', handleChange);
  
  return () => {
    window.removeEventListener('resize', handleChange);
    window.removeEventListener('orientationchange', handleChange);
  };
}

export function createOrientationListener(
  callback: (orientation: ReturnType<typeof getDeviceOrientation>) => void
): () => void {
  if (typeof window === 'undefined') {
    return () => {};
  }
  
  const handleOrientationChange = throttle(() => {
    callback(getDeviceOrientation());
  }, 100);
  
  // Initial call
  handleOrientationChange();
  
  // Listen for orientation changes
  window.addEventListener('resize', handleOrientationChange);
  window.addEventListener('orientationchange', handleOrientationChange);
  
  // Modern orientation API
  if (window.screen?.orientation) {
    window.screen.orientation.addEventListener('change', handleOrientationChange);
  }
  
  return () => {
    window.removeEventListener('resize', handleOrientationChange);
    window.removeEventListener('orientationchange', handleOrientationChange);
    if (window.screen?.orientation) {
      window.screen.orientation.removeEventListener('change', handleOrientationChange);
    }
  };
}