/**
 * Application Constants
 *
 * Contains all application-wide constants including:
 * - Theme values
 * - Viewport and layout constants
 * - API and WebSocket configuration
 * - Touch target sizes
 * - Performance thresholds
 */

// Theme Constants
export const THEMES = {
  LIGHT: 'light',
  DARK: 'dark',
} as const;

export type Theme = (typeof THEMES)[keyof typeof THEMES];

// Mobile Viewport Constants
export const VIEWPORT = {
  MIN_WIDTH: 320,
  MAX_WIDTH: 428,
  TOUCH_TARGET_MIN: 44, // Minimum touch target size in pixels
  TOUCH_TARGET_RECOMMENDED: 48,
} as const;

// Layout Constants
export const LAYOUT = {
  HEADER_HEIGHT: 64,
  BOTTOM_TAB_HEIGHT: 60,
  CARD_PADDING: {
    SM: 12,
    MD: 16,
    LG: 24,
  },
  BORDER_RADIUS: {
    SM: 4,
    MD: 8,
    LG: 12,
    XL: 16,
  },
} as const;

// WebSocket Constants
export const WEBSOCKET = {
  RECONNECT_DELAY_BASE: 1000, // Base delay in ms
  RECONNECT_MAX_ATTEMPTS: 5,
  RECONNECT_MULTIPLIER: 2, // Exponential backoff multiplier
  PING_INTERVAL: 30000, // 30 seconds
  CONNECTION_TIMEOUT: 10000, // 10 seconds
} as const;

// LocalStorage Keys
export const STORAGE_KEYS = {
  PROJECTS: 'pocket_agent_projects',
  SERVERS: 'pocket_agent_servers',
  THEME: 'pocket_agent_theme',
  LAST_ACTIVE_PROJECT: 'pocket_agent_last_active_project',
  USER_PREFERENCES: 'pocket_agent_user_preferences',
} as const;

// Performance Constants
export const PERFORMANCE = {
  BUNDLE_SIZE_TARGET: 500 * 1024, // 500KB in bytes
  LOAD_TIME_TARGET: 3000, // 3 seconds in ms
  TTI_TARGET: 2000, // Time to Interactive in ms
  FRAME_BUDGET: 16, // 60fps = 16ms per frame
} as const;

// Animation Constants
export const ANIMATION = {
  DURATION: {
    FAST: 150,
    NORMAL: 200,
    SLOW: 300,
  },
  EASING: {
    EASE_OUT: 'cubic-bezier(0.16, 1, 0.3, 1)',
    EASE_IN: 'cubic-bezier(0.4, 0, 1, 1)',
    EASE_IN_OUT: 'cubic-bezier(0.4, 0, 0.2, 1)',
  },
} as const;

// Input Validation Constants
export const VALIDATION = {
  PROJECT_NAME: {
    MIN_LENGTH: 1,
    MAX_LENGTH: 100,
  },
  SERVER_NAME: {
    MIN_LENGTH: 1,
    MAX_LENGTH: 50,
  },
  PROJECT_PATH: {
    MIN_LENGTH: 1,
    MAX_LENGTH: 500,
  },
  WEBSOCKET_URL: {
    MIN_LENGTH: 5,
    MAX_LENGTH: 200,
  },
} as const;

// Error Messages
export const ERROR_MESSAGES = {
  WEBSOCKET: {
    CONNECTION_FAILED: 'Failed to connect to server',
    CONNECTION_LOST: 'Connection to server lost',
    RECONNECT_FAILED: 'Unable to reconnect to server',
    INVALID_MESSAGE: 'Received invalid message from server',
    SEND_FAILED: 'Failed to send message to server',
  },
  STORAGE: {
    QUOTA_EXCEEDED: 'Storage quota exceeded',
    CORRUPTED_DATA: 'Stored data is corrupted',
    STORAGE_UNAVAILABLE: 'Storage is not available',
  },
  VALIDATION: {
    REQUIRED_FIELD: 'This field is required',
    INVALID_URL: 'Please enter a valid URL',
    INVALID_FORMAT: 'Invalid format',
    TOO_SHORT: 'Too short',
    TOO_LONG: 'Too long',
  },
  NETWORK: {
    OFFLINE: 'You are currently offline',
    TIMEOUT: 'Request timed out',
    SERVER_ERROR: 'Server error occurred',
  },
} as const;

// Success Messages
export const SUCCESS_MESSAGES = {
  PROJECT_CREATED: 'Project created successfully',
  SERVER_ADDED: 'Server added successfully',
  CONNECTION_ESTABLISHED: 'Connected to server',
  SETTINGS_SAVED: 'Settings saved successfully',
} as const;

// Component Sizes
export const COMPONENT_SIZES = {
  BUTTON: {
    SM: 'sm',
    MD: 'md',
    LG: 'lg',
  },
  ICON: {
    XS: 12,
    SM: 16,
    MD: 20,
    LG: 24,
    XL: 32,
  },
} as const;

// Z-Index Scale
export const Z_INDEX = {
  MODAL: 1000,
  DROPDOWN: 900,
  STICKY_HEADER: 800,
  TOOLTIP: 700,
  OVERLAY: 600,
} as const;

// Breakpoints (for media queries in JavaScript)
export const BREAKPOINTS = {
  SM: 640,
  MD: 768,
  LG: 1024,
  XL: 1280,
} as const;

// Device Detection (DEPRECATED - use getDeviceInfo() from helpers.ts for reactive detection)
// These constants are computed once at module load and don't update with viewport changes
export const DEVICE = {
  // These are static device capability detection - prefer getDeviceInfo() for reactive updates
  IS_TOUCH: typeof window !== 'undefined' && 'ontouchstart' in window,
  IS_IOS: typeof window !== 'undefined' && /iPad|iPhone|iPod/.test(navigator.userAgent),
  IS_ANDROID: typeof window !== 'undefined' && /Android/.test(navigator.userAgent),
} as const;

// Note: For reactive device detection that responds to viewport changes, use:
// - getDeviceInfo() for current device state
// - createDeviceListener() for continuous monitoring
// - createOrientationListener() for orientation changes
// - createResponsiveListener() for viewport-based changes
