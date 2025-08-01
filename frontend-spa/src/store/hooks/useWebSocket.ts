import { useSetAtom, useAtomValue } from 'jotai';
import { useEffect, useCallback, useRef } from 'react';
import type { ClientMessage, ServerMessage, ConnectionStatus } from '../../types/models';
import { useWebSocketContext } from '../../services/websocket/WebSocketContext';
import type { WebSocketServiceConfig, WebSocketServiceEvents } from '../../services/websocket/WebSocketService';

// Import existing WebSocket atoms
import {
  // websocketServicesAtom,
  websocketConnectionStatesAtom,
  websocketMessageQueuesAtom,
  joinedProjectsAtom,
  pendingMessagesAtom,
  projectStatesAtom,
  // setWebSocketServiceAtom,
  // updateConnectionStatusAtom,
  // addMessageToQueueAtom,
  clearMessageQueueAtom,
  joinProjectAtom,
  leaveProjectAtom,
  addPendingMessageAtom,
  clearPendingMessagesAtom,
  setWebSocketErrorAtom,
  clearWebSocketErrorAtom,
  connectedServersAtom,
  totalConnectionsAtom
} from '../atoms/websocket';

// Re-export WebSocketService from services
export { WebSocketService } from '../../services/websocket';

// Custom hook for WebSocket management per server
export function useWebSocket(serverId: string, url: string) {
  const context = useWebSocketContext();
  const connectionStates = useAtomValue(websocketConnectionStatesAtom);
  const messageQueues = useAtomValue(websocketMessageQueuesAtom);
  const joinedProjects = useAtomValue(joinedProjectsAtom);
  const pendingMessages = useAtomValue(pendingMessagesAtom);

  // Action setters
  const clearMessageQueue = useSetAtom(clearMessageQueueAtom);
  const joinProjectAction = useSetAtom(joinProjectAtom);
  const leaveProjectAction = useSetAtom(leaveProjectAtom);
  const addPendingMessage = useSetAtom(addPendingMessageAtom);
  const clearPendingMessages = useSetAtom(clearPendingMessagesAtom);
  const setWebSocketError = useSetAtom(setWebSocketErrorAtom);
  const clearWebSocketError = useSetAtom(clearWebSocketErrorAtom);

  // Get current state for this server
  const connectionState = connectionStates.get(serverId) || 'disconnected';
  const messages = messageQueues.get(serverId) || [];
  const serverJoinedProjects = joinedProjects.get(serverId) || new Set();
  const serverPendingMessages = pendingMessages.get(serverId) || [];
  const isConnected = connectionState === 'connected';

  // Use a ref to track the service but rely on context for service management
  const serviceRef = useRef<string | null>(null);

  useEffect(() => {
    // Ensure service exists for this server
    const config: WebSocketServiceConfig = {
      url,
      autoReconnect: true,
      maxReconnectAttempts: 5,
      initialReconnectDelay: 1000,
      maxReconnectDelay: 30000,
      pingInterval: 30000,
      connectionTimeout: 10000
    };

    let service = context.getService(serverId);
    if (!service) {
      service = context.createService(serverId, config);
    }
    
    // Track that we're using this service
    serviceRef.current = serverId;
    
    return () => {
      // Only cleanup if this hook instance created/managed the service
      // In practice, services should be managed at a higher level
      serviceRef.current = null;
    };
  }, [serverId, url, context]);

  const connect = useCallback(async () => {
    try {
      clearWebSocketError(serverId);
      
      const service = context.getService(serverId);
      if (!service) {
        throw new Error('WebSocket service not initialized');
      }
      
      await service.connect();
      
      // Send any pending messages
      serverPendingMessages.forEach(message => {
        service.send(message);
      });
      clearPendingMessages(serverId);
      
      // Rejoin previously joined projects
      serverJoinedProjects.forEach(projectId => {
        service.joinProject(projectId);
      });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown connection error';
      setWebSocketError(serverId, errorMessage, true);
      throw error;
    }
  }, [serverId, context, serverPendingMessages, serverJoinedProjects, clearWebSocketError, clearPendingMessages, setWebSocketError]);

  const disconnect = useCallback(() => {
    const service = context.getService(serverId);
    if (service) {
      service.disconnect();
    }
  }, [serverId, context]);

  const send = useCallback((message: ClientMessage): boolean => {
    const service = context.getService(serverId);
    
    if (service && isConnected) {
      try {
        return service.send(message);
      } catch (error) {
        console.error('Failed to send WebSocket message:', error);
        // Add to pending messages if send fails
        addPendingMessage(serverId, message);
        return false;
      }
    } else {
      // Add to pending messages if not connected
      addPendingMessage(serverId, message);
      return false;
    }
  }, [serverId, context, isConnected, addPendingMessage]);

  const joinProject = useCallback((projectId: string) => {
    const service = context.getService(serverId);
    
    if (service) {
      service.joinProject(projectId);
      return true;
    } else {
      // Store for later when service is available
      joinProjectAction(serverId, projectId);
      return false;
    }
  }, [serverId, context, joinProjectAction]);

  const leaveProject = useCallback((projectId: string) => {
    const service = context.getService(serverId);
    
    if (service) {
      service.leaveProject(projectId);
      return true;
    } else {
      // Remove from joined projects
      leaveProjectAction(serverId, projectId);
      return false;
    }
  }, [serverId, context, leaveProjectAction]);

  const clearMessages = useCallback(() => {
    clearMessageQueue(serverId);
  }, [serverId, clearMessageQueue]);

  return {
    // State
    connectionState,
    messages,
    isConnected,
    isConnecting: connectionState === 'connecting',
    hasError: connectionState === 'error',
    joinedProjects: Array.from(serverJoinedProjects),
    pendingMessages: serverPendingMessages,
    
    // Actions
    connect,
    disconnect,
    send,
    joinProject,
    leaveProject,
    clearMessages
  };
}

// Hook for listening to specific message types from a server
export function useWebSocketMessage(
  serverId: string,
  messageType: string,
  handler: (message: ServerMessage) => void,
  deps: React.DependencyList = []
) {
  const context = useWebSocketContext();
  const stableHandler = useCallback(handler, deps);
  const handlerRef = useRef(stableHandler);

  // Update handler ref when stable handler changes
  useEffect(() => {
    handlerRef.current = stableHandler;
  }, [stableHandler]);

  useEffect(() => {
    const service = context.getService(serverId);
    if (!service) return;

    const wrappedHandler = (message: ServerMessage) => {
      handlerRef.current(message);
    };

    service.on(messageType as keyof WebSocketServiceEvents, wrappedHandler);

    return () => {
      service.off(messageType as keyof WebSocketServiceEvents, wrappedHandler);
    };
  }, [serverId, messageType, context, stableHandler]);
}

// Hook for WebSocket connection management across multiple servers
export function useWebSocketManager() {
  const connectionStates = useAtomValue(websocketConnectionStatesAtom);
  const connectedServers = useAtomValue(connectedServersAtom);
  const totalConnections = useAtomValue(totalConnectionsAtom);

  const getConnectionState = useCallback((serverId: string): ConnectionStatus => {
    return connectionStates.get(serverId) || 'disconnected';
  }, [connectionStates]);

  const isAnyConnected = useCallback(() => {
    return totalConnections > 0;
  }, [totalConnections]);

  const getConnectedServers = useCallback(() => {
    return connectedServers;
  }, [connectedServers]);

  const disconnectAll = useCallback(() => {
    // This function should disconnect all servers
    // Since we don't have access to context here, this is a limitation
    // In practice, this should be handled by the context or higher-level component
    console.warn('disconnectAll: This function requires architectural refactoring to work with per-server services');
  }, []);

  return {
    connectionStates,
    connectedServers,
    totalConnections,
    getConnectionState,
    isAnyConnected,
    getConnectedServers,
    disconnectAll
  };
}

// Hook for monitoring project states received via WebSocket
export function useProjectStates() {
  const projectStates = useAtomValue(projectStatesAtom);

  const getProjectState = useCallback((projectId: string) => {
    return projectStates.get(projectId) || null;
  }, [projectStates]);

  const getAllProjectStates = useCallback(() => {
    return Array.from(projectStates.entries()).map(([projectId, state]) => ({
      projectId,
      ...state
    }));
  }, [projectStates]);

  return {
    projectStates,
    getProjectState,
    getAllProjectStates
  };
}