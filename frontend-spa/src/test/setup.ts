/**
 * Test setup configuration for Vitest + React Testing Library
 * 
 * This file sets up the testing environment with proper mocks and utilities
 * for comprehensive component and integration testing.
 */

import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeAll, vi } from 'vitest';

// Cleanup after each test case (e.g. clearing jsdom)
afterEach(() => {
  cleanup();
});

// Mock global objects for testing environment
beforeAll(() => {
  // Mock matchMedia for responsive design testing
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(), // deprecated
      removeListener: vi.fn(), // deprecated
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });

  // Mock ResizeObserver for components that use it
  global.ResizeObserver = vi.fn().mockImplementation(() => ({
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  }));

  // Mock IntersectionObserver for lazy loading and visibility detection
  global.IntersectionObserver = vi.fn().mockImplementation(() => ({
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  }));

  // Mock scrollTo for smooth scroll behavior
  window.scrollTo = vi.fn();

  // Mock localStorage for state persistence testing
  const localStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
    length: 0,
    key: vi.fn(),
  };
  Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
  });

  // Mock sessionStorage
  const sessionStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
    length: 0,
    key: vi.fn(),
  };
  Object.defineProperty(window, 'sessionStorage', {
    value: sessionStorageMock,
  });

  // Mock console methods to reduce noise in tests (optional)
  // Uncomment if you want to suppress console logs in tests
  // vi.spyOn(console, 'log').mockImplementation(() => {});
  // vi.spyOn(console, 'warn').mockImplementation(() => {});
  // vi.spyOn(console, 'error').mockImplementation(() => {});
});

// Global test utilities that can be used across all test files
export const mockLocalStorage = () => {
  const store: Record<string, string> = {};
  
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      Object.keys(store).forEach(key => delete store[key]);
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((index: number) => Object.keys(store)[index] || null),
    _store: store, // For test access to internal state
  };
};

// Mock WebSocket for testing WebSocket integration
export const mockWebSocket = () => {
  const mockWS = {
    send: vi.fn(),
    close: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    readyState: WebSocket.OPEN,
    CONNECTING: WebSocket.CONNECTING,
    OPEN: WebSocket.OPEN,
    CLOSING: WebSocket.CLOSING,
    CLOSED: WebSocket.CLOSED,
  };

  // Mock the WebSocket constructor
  global.WebSocket = vi.fn().mockImplementation(() => mockWS) as any;
  
  return mockWS;
};

// Utility to create mock router props for React Router testing
export const mockRouterProps = () => ({
  navigate: vi.fn(),
  location: {
    pathname: '/',
    search: '',
    hash: '',
    state: null,
    key: 'default',
  },
  params: {},
});

// Utility for testing async operations with proper cleanup
export const waitForAsync = () => new Promise(resolve => setTimeout(resolve, 0));

// Mock import.meta.env for testing environment variables
vi.stubGlobal('import.meta', {
  env: {
    DEV: true,
    PROD: false,
    MODE: 'test',
    VITE_API_URL: 'http://localhost:3000',
  },
});