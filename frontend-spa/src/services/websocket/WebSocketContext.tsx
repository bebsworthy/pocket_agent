/**
 * WebSocket Context for React integration
 *
 * Provides React context and provider for WebSocket services,
 * integrating with Jotai state management for seamless state updates.
 *
 * This context manages WebSocket service instances per server and
 * automatically updates the global state atoms when events occur.
 */

import React, { createContext, useContext, useEffect, useRef, useMemo, useCallback } from 'react';
import { useWebSocketErrorHandler } from './WebSocketErrorBoundary';
import { useSetAtom, useAtomValue } from 'jotai';
import { WebSocketService, type WebSocketServiceConfig } from './WebSocketService';
import {
  updateConnectionStatusAtom,
  addMessageToQueueAtom,
  updateReconnectionAttemptsAtom,
  setWebSocketErrorAtom,
  clearWebSocketErrorAtom,
  updateProjectStateAtom,
  updateServerStatsAtom,
  setWebSocketServiceAtom,
  websocketConnectionStatesAtom,
} from '../../store/atoms/websocket';
import type {
  ServerMessage,
  ProjectStateMessage,
  HealthCheckMessage,
  ServerStatsMessage,
  ErrorMessage,
  ProjectUpdateMessage,
  ProcessKilledMessage,
  ConnectionHealthMessage,
} from '../../types/messages';

interface WebSocketContextValue {
  getService: (serverId: string) => WebSocketService | null;
  createService: (serverId: string, config: WebSocketServiceConfig) => WebSocketService;
  removeService: (serverId: string) => void;
  getServices: () => Map<string, WebSocketService>;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

export interface WebSocketProviderProps {
  children: React.ReactNode;
}

export function WebSocketProvider({ children }: WebSocketProviderProps) {
  const servicesRef = useRef<Map<string, WebSocketService>>(new Map());
  const cleanupFunctionsRef = useRef<Map<string, () => void>>(new Map());
  const { handleError } = useWebSocketErrorHandler();

  // Jotai setters for state updates
  const updateConnectionStatus = useSetAtom(updateConnectionStatusAtom);
  const addMessageToQueue = useSetAtom(addMessageToQueueAtom);
  const updateReconnectionAttempts = useSetAtom(updateReconnectionAttemptsAtom);
  const setWebSocketError = useSetAtom(setWebSocketErrorAtom);
  const clearWebSocketError = useSetAtom(clearWebSocketErrorAtom);
  const updateProjectState = useSetAtom(updateProjectStateAtom);
  const updateServerStats = useSetAtom(updateServerStatsAtom);
  const setWebSocketService = useSetAtom(setWebSocketServiceAtom);

  const getService = useCallback((serverId: string): WebSocketService | null => {
    return servicesRef.current.get(serverId) || null;
  }, []);

  const setupServiceEventListeners = useCallback(
    (service: WebSocketService, serverId: string): (() => void) => {
      // Define all listeners for easier cleanup tracking
      const connectedListener = () => {
        updateConnectionStatus(serverId, 'connected');
        clearWebSocketError(serverId);
        updateReconnectionAttempts(serverId, 0);
      };

      const disconnectedListener = () => {
        updateConnectionStatus(serverId, 'disconnected');
      };

      const reconnectingListener = (attempt: number) => {
        updateConnectionStatus(serverId, 'connecting');
        updateReconnectionAttempts(serverId, attempt);
        clearWebSocketError(serverId);
      };

      const reconnectFailedListener = () => {
        updateConnectionStatus(serverId, 'error');
        setWebSocketError(serverId, 'Failed to reconnect after maximum attempts', true);
      };

      const errorListener = (error: Error) => {
        updateConnectionStatus(serverId, 'error');
        setWebSocketError(serverId, error.message, false);

        // Integrate with error boundary for critical errors
        if (
          'isWebSocketError' in error &&
          (error as Error & { isWebSocketError: boolean }).isWebSocketError
        ) {
          try {
            handleError(error, `WebSocket service for server ${serverId}`);
          } catch {
            // Error boundary will handle this
            console.warn('Error boundary handling triggered for WebSocket error');
          }
        }
      };

      const messageListener = (message: ServerMessage) => {
        addMessageToQueue(serverId, message);
      };

      const projectStateListener = (message: ServerMessage) => {
        const projectStateMessage = message as ProjectStateMessage;
        if (projectStateMessage.project_id && projectStateMessage.data?.state) {
          updateProjectState(
            projectStateMessage.project_id,
            projectStateMessage.data.state,
            projectStateMessage.data.session_id
          );
        }
      };

      const healthCheckListener = (message: ServerMessage) => {
        const healthMessage = message as HealthCheckMessage;
        if (healthMessage.data) {
          updateServerStats(serverId, {
            connections: healthMessage.data.connections?.active || 0,
            projects: healthMessage.data.projects?.count || 0,
            uptime: healthMessage.data.uptime || 0,
            version: healthMessage.data.version || 'unknown',
            status: healthMessage.data.status || 'unhealthy',
          });
        }
      };

      const serverStatsListener = (message: ServerMessage) => {
        const statsMessage = message as ServerStatsMessage;
        if (statsMessage.data) {
          updateServerStats(serverId, {
            connections: statsMessage.data.connections || 0,
            projects: statsMessage.data.projects || 0,
            uptime: statsMessage.data.uptime || 0,
            version: 'unknown', // server_stats doesn't include version
            status: 'healthy', // Assume healthy if we're receiving stats
          });
        }
      };

      const errorMessageListener = (message: ServerMessage) => {
        const errorMessage = message as ErrorMessage;
        if (errorMessage.data?.message) {
          setWebSocketError(serverId, errorMessage.data.message, false);
        }
      };

      const projectJoinedListener = (message: ServerMessage) => {
        console.debug(`Project joined: ${message.project_id} on server ${serverId}`);
      };

      const projectLeftListener = (message: ServerMessage) => {
        console.debug(`Project left: ${message.project_id} on server ${serverId}`);
      };

      const projectDeletedListener = (message: ServerMessage) => {
        console.debug(`Project deleted: ${message.project_id} on server ${serverId}`);
      };

      const sessionResetListener = (message: ServerMessage) => {
        console.debug(`Session reset for project: ${message.project_id} on server ${serverId}`);
      };

      const projectUpdateListener = (message: ServerMessage) => {
        const updateMessage = message as ProjectUpdateMessage;
        console.debug(`Project update: ${updateMessage.project_id} on server ${serverId}`);
        // Add any specific project update handling logic here
      };

      const processKilledListener = (message: ServerMessage) => {
        const processMessage = message as ProcessKilledMessage;
        console.debug(`Process killed: ${processMessage.project_id} on server ${serverId}`);
        // Add any specific process killed handling logic here
      };

      const connectionHealthListener = (message: ServerMessage) => {
        const healthMessage = message as ConnectionHealthMessage;
        console.debug(`Connection health update on server ${serverId}:`, healthMessage.data.status);
        // Add any specific connection health handling logic here
      };

      // Attach all listeners
      service.on('connected', connectedListener);
      service.on('disconnected', disconnectedListener);
      service.on('reconnecting', reconnectingListener);
      service.on('reconnectFailed', reconnectFailedListener);
      service.on('error', errorListener);
      service.on('message', messageListener);
      service.on('project_state', projectStateListener);
      service.on('health_check', healthCheckListener);
      service.on('server_stats', serverStatsListener);
      service.on('error_message', errorMessageListener);
      service.on('project_joined', projectJoinedListener);
      service.on('project_left', projectLeftListener);
      service.on('project_deleted', projectDeletedListener);
      service.on('session_reset', sessionResetListener);
      service.on('project_update', projectUpdateListener);
      service.on('process_killed', processKilledListener);
      service.on('connection_health', connectionHealthListener);

      // Return cleanup function
      return () => {
        service.off('connected', connectedListener);
        service.off('disconnected', disconnectedListener);
        service.off('reconnecting', reconnectingListener);
        service.off('reconnectFailed', reconnectFailedListener);
        service.off('error', errorListener);
        service.off('message', messageListener);
        service.off('project_state', projectStateListener);
        service.off('health_check', healthCheckListener);
        service.off('server_stats', serverStatsListener);
        service.off('error_message', errorMessageListener);
        service.off('project_joined', projectJoinedListener);
        service.off('project_left', projectLeftListener);
        service.off('project_deleted', projectDeletedListener);
        service.off('session_reset', sessionResetListener);
        service.off('project_update', projectUpdateListener);
        service.off('process_killed', processKilledListener);
        service.off('connection_health', connectionHealthListener);
      };
    },
    [
      updateConnectionStatus,
      clearWebSocketError,
      updateReconnectionAttempts,
      setWebSocketError,
      handleError,
      addMessageToQueue,
      updateProjectState,
      updateServerStats,
    ]
  );

  const createService = useCallback(
    (serverId: string, config: WebSocketServiceConfig): WebSocketService => {
      // If service already exists, return it
      const existingService = servicesRef.current.get(serverId);
      if (existingService) {
        return existingService;
      }

      // Create new service
      const service = new WebSocketService(serverId, config);
      servicesRef.current.set(serverId, service);

      // Update Jotai state
      setWebSocketService(serverId, service);

      // Set up event listeners to update Jotai state
      const cleanup = setupServiceEventListeners(service, serverId);
      cleanupFunctionsRef.current.set(serverId, cleanup);

      return service;
    },
    [setWebSocketService, setupServiceEventListeners]
  );

  const removeService = useCallback(
    (serverId: string): void => {
      const service = servicesRef.current.get(serverId);
      const cleanup = cleanupFunctionsRef.current.get(serverId);

      if (service) {
        // Cleanup event listeners first
        if (cleanup) {
          cleanup();
          cleanupFunctionsRef.current.delete(serverId);
        }

        // Cleanup service
        service.destroy();
        servicesRef.current.delete(serverId);

        // Update Jotai state
        setWebSocketService(serverId, null);
        updateConnectionStatus(serverId, 'disconnected');
        clearWebSocketError(serverId);
      }
    },
    [setWebSocketService, updateConnectionStatus, clearWebSocketError]
  );

  const getServices = useCallback((): Map<string, WebSocketService> => {
    return new Map(servicesRef.current);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    // Copy ref values to variables inside the effect
    const cleanupFunctions = cleanupFunctionsRef.current;
    const services = servicesRef.current;

    return () => {
      // Cleanup all event listeners first
      cleanupFunctions.forEach(cleanup => {
        cleanup();
      });
      cleanupFunctions.clear();

      // Cleanup all services
      services.forEach(service => {
        service.destroy();
      });
      services.clear();
    };
  }, []);

  const value: WebSocketContextValue = useMemo(
    () => ({
      getService,
      createService,
      removeService,
      getServices,
    }),
    [getService, createService, removeService, getServices]
  );

  return <WebSocketContext.Provider value={value}>{children}</WebSocketContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWebSocketContext(): WebSocketContextValue {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocketContext must be used within a WebSocketProvider');
  }
  return context;
}

/**
 * Hook to get or create a WebSocket service for a specific server
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useWebSocketService(
  serverId: string,
  config: WebSocketServiceConfig
): WebSocketService {
  const context = useWebSocketContext();

  useEffect(() => {
    // Create service if it doesn't exist
    if (!context.getService(serverId)) {
      context.createService(serverId, config);
    }

    // Ensure service has the latest config
    // Note: This is a simplified approach. In a real implementation,
    // you might want to handle config updates more sophisticatedly

    return () => {
      // Don't automatically remove service on unmount as it might be used elsewhere
      // Services should be explicitly removed when no longer needed
    };
  }, [serverId, config, context]);

  return context.getService(serverId) || context.createService(serverId, config);
}

/**
 * Hook to automatically connect/disconnect a WebSocket service
 * Returns null if serverId is null/undefined or config is invalid, preventing creation of placeholder services
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useWebSocketConnection(
  serverId: string | null | undefined,
  config: WebSocketServiceConfig | null | undefined,
  autoConnect = true
): {
  service: WebSocketService;
  connect: () => Promise<void>;
  disconnect: () => void;
  isConnected: boolean;
} | null {
  const context = useWebSocketContext();
  
  // ALWAYS call all hooks first (Rules of Hooks requirement)
  const connectionStates = useAtomValue(websocketConnectionStatesAtom);
  
  // Stabilize config for useMemo
  const configKey = config ? `${config.url}_${config.autoReconnect}_${config.maxReconnectAttempts}` : null;
  
  // Get or create the service
  const service = useMemo(() => {
    if (!serverId || !config || !config.url) {
      return null;
    }
    
    let svc = context.getService(serverId);
    if (!svc) {
      svc = context.createService(serverId, config);
    }
    return svc;
  }, [serverId, configKey, context]);
  
  // Use ref to avoid service dependencies in callbacks
  const serviceRef = useRef(service);
  serviceRef.current = service;
  
  // Get reactive connection status from atoms (always call this hook)
  const isConnected = service ? (connectionStates.get(serverId!) === 'connected') : false;

  // Always define callbacks (called unconditionally for Rules of Hooks)
  const connect = useCallback(async (): Promise<void> => {
    const currentService = serviceRef.current;
    if (currentService && !currentService.isConnected()) {
      await currentService.connect();
    }
  }, []); // No dependencies needed - using ref

  const disconnect = useCallback((): void => {
    const currentService = serviceRef.current;
    if (currentService) {
      currentService.disconnect();
    }
  }, []); // No dependencies needed - using ref

  // Auto-connect effect - always call useEffect (Rules of Hooks)
  // Use stable keys to avoid dependency array issues when serverId is null/undefined
  const autoConnectKey = `${serverId || 'null'}_${autoConnect}`;
  
  useEffect(() => {
    if (service && autoConnect && !service.isConnected()) {
      service.connect().catch(error => {
        console.error(`Failed to auto-connect to server ${serverId || 'unknown'}:`, error);
      });
    }
  }, [service, autoConnectKey]); // Use stable key instead of nullable serverId

  // Early return for invalid configurations AFTER all hooks
  if (!service) {
    return null;
  }

  return {
    service,
    connect,
    disconnect,
    isConnected,
  };
}
