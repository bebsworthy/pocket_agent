/**
 * WebSocket state atoms using Jotai for atomic state management.
 * Manages WebSocket connections, messages, and connection state per server.
 */

import { atom } from 'jotai';
import type {
  ServerMessage,
  ClientMessage,
  ConnectionStatus,
  ProjectState,
} from '../../types/models';
import type { WebSocketService } from '../../services/websocket/WebSocketService';

// Consolidated server state interface
export interface WebSocketServerState {
  service: WebSocketService | null;
  connectionStatus: ConnectionStatus;
  reconnectionAttempts: number;
  lastActivity: string;
  config: {
    url: string;
    autoReconnect: boolean;
    maxReconnectAttempts: number;
    reconnectDelay: number;
    pingInterval: number;
  } | null;
  joinedProjects: Set<string>;
  pendingMessages: ClientMessage[];
  error: {
    error: string;
    timestamp: string;
    critical: boolean;
  } | null;
  stats: {
    connections: number;
    projects: number;
    uptime: number;
    version: string;
    status: 'healthy' | 'degraded' | 'unhealthy';
    lastUpdate: string;
  } | null;
}

// Create default server state
const createDefaultServerState = (): WebSocketServerState => ({
  service: null,
  connectionStatus: 'disconnected',
  reconnectionAttempts: 0,
  lastActivity: '',
  config: null,
  joinedProjects: new Set(),
  pendingMessages: [],
  error: null,
  stats: null,
});

// Consolidated server states atom - replaces multiple individual atoms
export const websocketServerStatesAtom = atom<Map<string, WebSocketServerState>>(new Map());

// Separate atoms for data that doesn't belong to specific servers
export const websocketMessageQueuesAtom = atom<Map<string, ServerMessage[]>>(new Map());

export const projectStatesAtom = atom<
  Map<
    string,
    {
      state: ProjectState;
      sessionId?: string | null;
      lastUpdate: string;
    }
  >
>(new Map());

// Derived atoms for backward compatibility - these select from the consolidated state
export const websocketServicesAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const services = new Map<string, WebSocketService | null>();
  serverStates.forEach((state, serverId) => {
    services.set(serverId, state.service);
  });
  return services;
});

export const websocketConnectionStatesAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const connectionStates = new Map<string, ConnectionStatus>();
  serverStates.forEach((state, serverId) => {
    connectionStates.set(serverId, state.connectionStatus);
  });
  return connectionStates;
});

export const websocketReconnectionAttemptsAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const attempts = new Map<string, number>();
  serverStates.forEach((state, serverId) => {
    attempts.set(serverId, state.reconnectionAttempts);
  });
  return attempts;
});

export const websocketLastActivityAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const activity = new Map<string, string>();
  serverStates.forEach((state, serverId) => {
    activity.set(serverId, state.lastActivity);
  });
  return activity;
});

export const websocketConfigAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const configs = new Map<
    string,
    {
      url: string;
      autoReconnect: boolean;
      maxReconnectAttempts: number;
      reconnectDelay: number;
      pingInterval: number;
    }
  >();
  serverStates.forEach((state, serverId) => {
    if (state.config) {
      configs.set(serverId, state.config);
    }
  });
  return configs;
});

export const joinedProjectsAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const joined = new Map<string, Set<string>>();
  serverStates.forEach((state, serverId) => {
    joined.set(serverId, state.joinedProjects);
  });
  return joined;
});

export const pendingMessagesAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const pending = new Map<string, ClientMessage[]>();
  serverStates.forEach((state, serverId) => {
    pending.set(serverId, state.pendingMessages);
  });
  return pending;
});

export const websocketErrorsAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const errors = new Map<
    string,
    {
      error: string;
      timestamp: string;
      critical: boolean;
    }
  >();
  serverStates.forEach((state, serverId) => {
    if (state.error) {
      errors.set(serverId, state.error);
    }
  });
  return errors;
});

export const serverStatsAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const stats = new Map<
    string,
    {
      connections: number;
      projects: number;
      uptime: number;
      version: string;
      status: 'healthy' | 'degraded' | 'unhealthy';
      lastUpdate: string;
    }
  >();
  serverStates.forEach((state, serverId) => {
    if (state.stats) {
      stats.set(serverId, state.stats);
    }
  });
  return stats;
});

// Derived atom to get connection status for a specific server
export const getServerConnectionStatusAtom = atom(
  null,
  (get, _set, serverId: string): ConnectionStatus => {
    const connectionStates = get(websocketConnectionStatesAtom);
    return connectionStates.get(serverId) || 'disconnected';
  }
);

// Optimized derived atom to get all connected servers directly from consolidated state
export const connectedServersAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);
  const connectedServerIds: string[] = [];

  // Use Array.from to iterate Map entries safely
  serverStates.forEach((state, serverId) => {
    if (state.connectionStatus === 'connected') {
      connectedServerIds.push(serverId);
    }
  });

  return connectedServerIds;
});

// Derived atom to get total connection count (reuses connectedServersAtom for consistency)
export const totalConnectionsAtom = atom(get => get(connectedServersAtom).length);

// Optimized derived atom to check if any server is connecting
export const isAnyServerConnectingAtom = atom(get => {
  const serverStates = get(websocketServerStatesAtom);

  // Early exit optimization: return as soon as we find a connecting server
  let isConnecting = false;
  serverStates.forEach(state => {
    if (state.connectionStatus === 'connecting') {
      isConnecting = true;
    }
  });
  return isConnecting;
});

// Helper function to get or create server state
const getOrCreateServerState = (
  serverStates: Map<string, WebSocketServerState>,
  serverId: string
): WebSocketServerState => {
  return serverStates.get(serverId) || createDefaultServerState();
};

// Write-only atom for setting WebSocket service for a server
export const setWebSocketServiceAtom = atom(
  null,
  (get, set, serverId: string, service: WebSocketService | null) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      service,
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for updating connection status
export const updateConnectionStatusAtom = atom(
  null,
  (get, set, serverId: string, status: ConnectionStatus) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      connectionStatus: status,
      // Update last activity on connection events
      lastActivity:
        status === 'connected' || status === 'disconnected'
          ? new Date().toISOString()
          : currentState.lastActivity,
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for updating reconnection attempts
export const updateReconnectionAttemptsAtom = atom(
  null,
  (get, set, serverId: string, attempts: number) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      reconnectionAttempts: attempts,
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for adding message to queue
export const addMessageToQueueAtom = atom(
  null,
  (get, set, serverId: string, message: ServerMessage) => {
    const messageQueues = get(websocketMessageQueuesAtom);
    const currentQueue = messageQueues.get(serverId) || [];
    const newMessageQueues = new Map(messageQueues);

    // Keep only last 100 messages per server to prevent memory issues
    const updatedQueue = [...currentQueue, message].slice(-100);
    newMessageQueues.set(serverId, updatedQueue);
    set(websocketMessageQueuesAtom, newMessageQueues);
  }
);

// Write-only atom for clearing message queue
export const clearMessageQueueAtom = atom(null, (get, set, serverId: string) => {
  const messageQueues = get(websocketMessageQueuesAtom);
  const newMessageQueues = new Map(messageQueues);
  newMessageQueues.set(serverId, []);
  set(websocketMessageQueuesAtom, newMessageQueues);
});

// Write-only atom for updating project state
export const updateProjectStateAtom = atom(
  null,
  (get, set, projectId: string, state: ProjectState, sessionId?: string | null) => {
    const projectStates = get(projectStatesAtom);
    const newProjectStates = new Map(projectStates);
    newProjectStates.set(projectId, {
      state,
      sessionId,
      lastUpdate: new Date().toISOString(),
    });
    set(projectStatesAtom, newProjectStates);
  }
);

// Write-only atom for joining a project
export const joinProjectAtom = atom(null, (get, set, serverId: string, projectId: string) => {
  const serverStates = get(websocketServerStatesAtom);
  const newServerStates = new Map(serverStates);
  const currentState = getOrCreateServerState(serverStates, serverId);

  const updatedProjects = new Set(currentState.joinedProjects);
  updatedProjects.add(projectId);

  newServerStates.set(serverId, {
    ...currentState,
    joinedProjects: updatedProjects,
  });
  set(websocketServerStatesAtom, newServerStates);
});

// Write-only atom for leaving a project
export const leaveProjectAtom = atom(null, (get, set, serverId: string, projectId: string) => {
  const serverStates = get(websocketServerStatesAtom);
  const newServerStates = new Map(serverStates);
  const currentState = getOrCreateServerState(serverStates, serverId);

  const updatedProjects = new Set(currentState.joinedProjects);
  updatedProjects.delete(projectId);

  newServerStates.set(serverId, {
    ...currentState,
    joinedProjects: updatedProjects,
  });
  set(websocketServerStatesAtom, newServerStates);
});

// Write-only atom for adding pending message
export const addPendingMessageAtom = atom(
  null,
  (get, set, serverId: string, message: ClientMessage) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      pendingMessages: [...currentState.pendingMessages, message],
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for clearing pending messages
export const clearPendingMessagesAtom = atom(null, (get, set, serverId: string) => {
  const serverStates = get(websocketServerStatesAtom);
  const newServerStates = new Map(serverStates);
  const currentState = getOrCreateServerState(serverStates, serverId);

  newServerStates.set(serverId, {
    ...currentState,
    pendingMessages: [],
  });
  set(websocketServerStatesAtom, newServerStates);
});

// Write-only atom for setting WebSocket error
export const setWebSocketErrorAtom = atom(
  null,
  (get, set, serverId: string, error: string, critical: boolean = false) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      error: {
        error,
        timestamp: new Date().toISOString(),
        critical,
      },
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for clearing WebSocket error
export const clearWebSocketErrorAtom = atom(null, (get, set, serverId: string) => {
  const serverStates = get(websocketServerStatesAtom);
  const newServerStates = new Map(serverStates);
  const currentState = getOrCreateServerState(serverStates, serverId);

  newServerStates.set(serverId, {
    ...currentState,
    error: null,
  });
  set(websocketServerStatesAtom, newServerStates);
});

// Write-only atom for updating server stats
export const updateServerStatsAtom = atom(
  null,
  (
    get,
    set,
    serverId: string,
    stats: Omit<
      {
        connections: number;
        projects: number;
        uptime: number;
        version: string;
        status: 'healthy' | 'degraded' | 'unhealthy';
        lastUpdate: string;
      },
      'lastUpdate'
    >
  ) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      stats: {
        ...stats,
        lastUpdate: new Date().toISOString(),
      },
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);

// Write-only atom for setting WebSocket configuration
export const setWebSocketConfigAtom = atom(
  null,
  (
    get,
    set,
    serverId: string,
    config: {
      url: string;
      autoReconnect: boolean;
      maxReconnectAttempts: number;
      reconnectDelay: number;
      pingInterval: number;
    }
  ) => {
    const serverStates = get(websocketServerStatesAtom);
    const newServerStates = new Map(serverStates);
    const currentState = getOrCreateServerState(serverStates, serverId);

    newServerStates.set(serverId, {
      ...currentState,
      config,
    });
    set(websocketServerStatesAtom, newServerStates);
  }
);
