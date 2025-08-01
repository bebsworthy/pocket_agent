/**
 * WebSocket-related React hooks
 * 
 * These hooks provide convenient interfaces for components to interact
 * with WebSocket services and listen to specific message types.
 */

import { useEffect, useCallback, useState, useRef, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { useWebSocketContext } from './WebSocketContext';
import { 
  websocketConnectionStatesAtom,
  websocketErrorsAtom,
  websocketReconnectionAttemptsAtom,
  joinedProjectsAtom,
  projectStatesAtom,
  serverStatsAtom
} from '../../store/atoms/websocket';
import type { 
  ServerMessage, 
  ClientMessage, 
  ProjectStateMessage,
  AgentMessage,
  ErrorMessage
} from '../../types/messages';
import type { ConnectionStatus, ProjectState } from '../../types/models';

/**
 * Hook to listen to specific WebSocket message types
 */
export function useWebSocketMessage<T extends ServerMessage>(
  serverId: string,
  messageTypes: string | string[],
  handler: (message: T) => void,
  deps: React.DependencyList = []
): void {
  const context = useWebSocketContext();
  
  // Use useCallback to stabilize the handler
  const stableHandler = useCallback(handler, deps);
  const handlerRef = useRef(stableHandler);
  
  // Update handler ref when stable handler changes
  useEffect(() => {
    handlerRef.current = stableHandler;
  }, [stableHandler]);

  // Memoize the message types array to avoid JSON.stringify
  const typesArray = useMemo(() => {
    return Array.isArray(messageTypes) ? messageTypes : [messageTypes];
  }, [messageTypes]);

  useEffect(() => {
    const service = context.getService(serverId);
    if (!service) return;

    const listeners: Array<() => void> = [];

    typesArray.forEach(messageType => {
      const listener = (message: ServerMessage) => {
        handlerRef.current(message as T);
      };
      
      service.on(messageType as string, listener);
      listeners.push(() => service.off(messageType as string, listener));
    });

    return () => {
      listeners.forEach(cleanup => cleanup());
    };
  }, [serverId, context, typesArray]);
}

/**
 * Hook to get the current connection status for a server
 */
export function useWebSocketConnectionStatus(serverId: string): ConnectionStatus {
  const connectionStates = useAtomValue(websocketConnectionStatesAtom);
  return connectionStates.get(serverId) || 'disconnected';
}

/**
 * Hook to get WebSocket errors for a server
 */
export function useWebSocketError(serverId: string): { error: string; timestamp: string; critical: boolean } | null {
  const errors = useAtomValue(websocketErrorsAtom);
  return errors.get(serverId) || null;
}

/**
 * Hook to get reconnection attempts for a server
 */
export function useWebSocketReconnectionAttempts(serverId: string): number {
  const attempts = useAtomValue(websocketReconnectionAttemptsAtom);
  return attempts.get(serverId) || 0;
}

/**
 * Hook to send WebSocket messages
 */
export function useWebSocketSend(serverId: string) {
  const context = useWebSocketContext();

  return useCallback((message: ClientMessage): boolean => {
    const service = context.getService(serverId);
    if (!service) {
      console.warn(`Cannot send message: No WebSocket service found for server ${serverId}`);
      return false;
    }
    return service.send(message);
  }, [serverId, context]);
}

/**
 * Hook to manage project join/leave operations
 */
export function useWebSocketProject(serverId: string, projectId: string) {
  const context = useWebSocketContext();
  const joinedProjects = useAtomValue(joinedProjectsAtom);
  
  const isJoined = joinedProjects.get(serverId)?.has(projectId) || false;

  const join = useCallback(() => {
    const service = context.getService(serverId);
    if (service) {
      service.joinProject(projectId);
    }
  }, [serverId, projectId, context]);

  const leave = useCallback(() => {
    const service = context.getService(serverId);
    if (service) {
      service.leaveProject(projectId);
    }
  }, [serverId, projectId, context]);

  return {
    isJoined,
    join,
    leave
  };
}

/**
 * Hook to get project state from WebSocket messages
 */
export function useProjectState(projectId: string): {
  state: ProjectState;
  sessionId?: string | null;
  lastUpdate: string;
} | null {
  const projectStates = useAtomValue(projectStatesAtom);
  return projectStates.get(projectId) || null;
}

/**
 * Hook to get server statistics
 */
export function useServerStats(serverId: string): {
  connections: number;
  projects: number;
  uptime: number;
  version: string;
  status: 'healthy' | 'degraded' | 'unhealthy';
  lastUpdate: string;
} | null {
  const serverStats = useAtomValue(serverStatsAtom);
  return serverStats.get(serverId) || null;
}

/**
 * Hook to listen to project-specific agent messages
 */
export function useAgentMessages(
  serverId: string,
  projectId: string,
  handler: (message: AgentMessage) => void
): void {
  useWebSocketMessage<AgentMessage>(
    serverId,
    'agent_message',
    (message) => {
      if (message.project_id === projectId) {
        handler(message);
      }
    },
    [projectId]
  );
}

/**
 * Hook to listen to project state changes
 */
export function useProjectStateChanges(
  serverId: string,
  projectId: string,
  handler: (message: ProjectStateMessage) => void
): void {
  useWebSocketMessage<ProjectStateMessage>(
    serverId,
    'project_state',
    (message) => {
      if (message.project_id === projectId) {
        handler(message);
      }
    },
    [projectId]
  );
}

/**
 * Hook to listen to WebSocket errors
 */
export function useWebSocketErrorMessages(
  serverId: string,
  handler: (message: ErrorMessage) => void
): void {
  useWebSocketMessage<ErrorMessage>(serverId, 'error', handler);
}

/**
 * Hook for connection health monitoring
 */
export function useConnectionHealth(serverId: string): {
  isConnected: boolean;
  isConnecting: boolean;
  hasError: boolean;
  errorMessage?: string;
  reconnectAttempts: number;
  lastActivity?: string;
} {
  const connectionStatus = useWebSocketConnectionStatus(serverId);
  const error = useWebSocketError(serverId);
  const reconnectAttempts = useWebSocketReconnectionAttempts(serverId);

  return {
    isConnected: connectionStatus === 'connected',
    isConnecting: connectionStatus === 'connecting',
    hasError: connectionStatus === 'error' || !!error,
    errorMessage: error?.error,
    reconnectAttempts,
    lastActivity: error?.timestamp
  };
}

/**
 * Hook to automatically retry failed connections
 */
export function useConnectionRetry(serverId: string, maxRetries = 3): {
  retry: () => Promise<void>;
  isRetrying: boolean;
  canRetry: boolean;
} {
  const context = useWebSocketContext();
  const [isRetrying, setIsRetrying] = useState(false);
  const reconnectAttempts = useWebSocketReconnectionAttempts(serverId);
  const connectionStatus = useWebSocketConnectionStatus(serverId);

  const canRetry = connectionStatus === 'error' && reconnectAttempts < maxRetries;

  const retry = useCallback(async (): Promise<void> => {
    if (!canRetry || isRetrying) return;

    const service = context.getService(serverId);
    if (!service) {
      console.warn(`Cannot retry: No WebSocket service found for server ${serverId}`);
      return;
    }

    setIsRetrying(true);
    try {
      await service.connect();
    } catch (error) {
      console.error(`Manual retry failed for server ${serverId}:`, error);
    } finally {
      setIsRetrying(false);
    }
  }, [serverId, context, canRetry, isRetrying]);

  return {
    retry,
    isRetrying,
    canRetry
  };
}

/**
 * Hook to track WebSocket message history for debugging
 */
export function useMessageHistory(serverId: string, maxMessages = 50): ServerMessage[] {
  const [messages, setMessages] = useState<ServerMessage[]>([]);

  useWebSocketMessage(
    serverId,
    '*', // Listen to all message types
    (message: ServerMessage) => {
      setMessages(prev => {
        const newMessages = [...prev, message].slice(-maxMessages);
        return newMessages;
      });
    }
  );

  return messages;
}

/**
 * Hook to detect stale connections and trigger reconnection
 */
export function useConnectionWatchdog(
  serverId: string,
  timeoutMs = 60000 // 1 minute
): void {
  const context = useWebSocketContext();
  const connectionStatus = useWebSocketConnectionStatus(serverId);
  const timeoutRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    if (connectionStatus === 'connected') {
      // Reset watchdog when connected
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      
      timeoutRef.current = setTimeout(() => {
        const service = context.getService(serverId);
        if (service && service.isConnected()) {
          console.warn(`Connection watchdog triggered for server ${serverId} - forcing reconnect`);
          service.disconnect();
          // Service will auto-reconnect if configured
        }
      }, timeoutMs);
    } else {
      // Clear watchdog when not connected
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = undefined;
      }
    }

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [connectionStatus, serverId, context, timeoutMs]);
}