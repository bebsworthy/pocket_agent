/**
 * Test mocks and utilities for comprehensive testing
 * 
 * This file contains reusable mocks for Jotai atoms, WebSocket services,
 * React Router, and other application-specific dependencies.
 */

import { vi } from 'vitest';
import type { Project, Server } from '../types/models';
import type { WebSocketService } from '../services/websocket/WebSocketService';

// Mock data factories
export const createMockProject = (overrides: Partial<Project> = {}): Project => ({
  id: 'test-project-1',
  name: 'Test Project',
  path: '/path/to/project',
  serverId: 'test-server-1',
  createdAt: '2024-01-01T00:00:00.000Z',
  lastActive: '2024-01-01T00:00:00.000Z',
  ...overrides,
});

export const createMockServer = (overrides: Partial<Server> = {}): Server => ({
  id: 'test-server-1',
  name: 'Test Server',
  websocketUrl: 'ws://localhost:8080',
  isConnected: false,
  ...overrides,
});

// Mock multiple projects for list testing
export const createMockProjects = (count: number = 3): Project[] =>
  Array.from({ length: count }, (_, i) =>
    createMockProject({
      id: `test-project-${i + 1}`,
      name: `Test Project ${i + 1}`,
      path: `/path/to/project-${i + 1}`,
      serverId: `test-server-${i + 1}`,
    })
  );

export const createMockServers = (count: number = 3): Server[] =>
  Array.from({ length: count }, (_, i) =>
    createMockServer({
      id: `test-server-${i + 1}`,
      name: `Test Server ${i + 1}`,
      websocketUrl: `ws://localhost:808${i + 1}`,
    })
  );

// Jotai atoms mocks
export const mockAtoms = {
  // Project creation atoms
  createProjectFormDataAtom: {
    name: '',
    path: '',
    serverId: '',
  },
  createProjectErrorsAtom: {},
  createProjectIsSubmittingAtom: false,
  createProjectIsValidAtom: false,
  createProjectIsVisibleAtom: false,

  // Project atoms
  projectsAtom: [],
  
  // Server atoms
  serversAtom: [],
  serverOperationAtom: { type: null, serverId: null },

  // UI atoms
  themeAtom: 'system' as const,

  // WebSocket atoms
  webSocketConnectionsAtom: new Map(),
  webSocketReconnectAttemptsAtom: new Map(),
};

// Mock Jotai hooks
export const mockJotaiHooks = () => {
  const useAtomValue = vi.fn();
  const useSetAtom = vi.fn();
  const useAtom = vi.fn();
  
  // Setup default return values
  useAtomValue.mockImplementation((atom) => {
    // Return appropriate mock values based on atom type
    if (typeof atom === 'object' && atom !== null) {
      return mockAtoms.createProjectFormDataAtom;
    }
    return null;
  });
  
  useSetAtom.mockImplementation(() => vi.fn());
  useAtom.mockImplementation((atom) => [
    useAtomValue(atom),
    useSetAtom(),
  ]);

  return { useAtomValue, useSetAtom, useAtom };
};

// Mock custom hooks
export const mockCustomHooks = () => {
  const useProjects = vi.fn(() => ({
    projects: createMockProjects(2),
    hasProjects: true,
    isLoading: false,
    addProject: vi.fn(),
    updateProject: vi.fn(),
    removeProject: vi.fn(),
  }));

  const useServers = vi.fn(() => ({
    // State
    servers: createMockServers(2),
    serversWithStatus: createMockServers(2),
    connectionStates: new Map(),
    serverCount: 2,
    hasServers: true,
    connectedCount: 0,
    isLoading: false,

    // Actions
    addServer: vi.fn(),
    updateServer: vi.fn(),
    removeServer: vi.fn(),
    updateConnectionStatus: vi.fn(),
    batchUpdateConnectionStatus: vi.fn(),
    getServer: vi.fn(),
    getServerConnectionStatus: vi.fn(),
    getConnectedServers: vi.fn(() => []),
    getDisconnectedServers: vi.fn(() => createMockServers(2)),
    isServerConnected: vi.fn(() => false),
    setLoading: vi.fn(),
  }));

  const useTheme = vi.fn(() => ({
    theme: 'system' as const,
    toggleTheme: vi.fn(),
    setTheme: vi.fn(),
  }));

  const useWebSocket = vi.fn((serverId?: string, url?: string) => ({
    isConnected: serverId ? true : false,
    send: vi.fn(),
    close: vi.fn(),
    reconnect: vi.fn(),
    connectionState: 'connected' as const,
  }));

  const useWebSocketMessage = vi.fn();

  return {
    useProjects,
    useServers,
    useTheme,
    useWebSocket,
    useWebSocketMessage,
  };
};

// Mock React Router hooks
export const mockReactRouter = () => {
  const navigate = vi.fn();
  const useNavigate = vi.fn(() => navigate);
  const useLocation = vi.fn(() => ({
    pathname: '/dashboard',
    search: '',
    hash: '',
    state: null,
    key: 'default',
  }));
  const useParams = vi.fn(() => ({}));

  return {
    useNavigate,
    useLocation,
    useParams,
    navigate,
  };
};

// Mock WebSocket Service
export const createMockWebSocketService = (): Partial<WebSocketService> => ({
  isConnected: () => true,
  getConnectionStatus: () => 'connected' as const,
  send: vi.fn().mockReturnValue(true),
  disconnect: vi.fn(),
  connect: vi.fn().mockResolvedValue(undefined),
  on: vi.fn(),
  off: vi.fn(),
});

// Mock localStorage with spy capabilities
export const createMockLocalStorage = () => {
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
    _getStore: () => ({ ...store }), // For test assertions
  };
};

// Mock error scenarios
export const mockErrorScenarios = {
  webSocketError: new Error('WebSocket connection failed'),
  validationError: new Error('Validation failed'),
  networkError: new Error('Network request failed'),
  serverError: new Error('Server error'),
};

// Utility to reset all mocks
export const resetAllMocks = () => {
  vi.clearAllMocks();
};

// Utility to create a complete mock environment
export const createMockEnvironment = () => {
  const jotai = mockJotaiHooks();
  const customHooks = mockCustomHooks();
  const router = mockReactRouter();
  const localStorage = createMockLocalStorage();
  const webSocketService = createMockWebSocketService();

  return {
    jotai,
    customHooks,
    router,
    localStorage,
    webSocketService,
    mockData: {
      projects: createMockProjects(),
      servers: createMockServers(),
      project: createMockProject(),
      server: createMockServer(),
    },
  };
};