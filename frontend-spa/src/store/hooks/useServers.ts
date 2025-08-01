/**
 * Custom hook for server state management using Jotai atoms.
 * Provides convenient interface for managing servers and their connection states.
 */

import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import type { Server, ConnectionStatus } from '../../types/models';
import {
  serversAtom,
  serverConnectionStatesAtom,
  serversWithStatusAtom,
  serverCountAtom,
  hasServersAtom,
  connectedServersCountAtom,
  serversLoadingAtom,
  addServerAtom,
  removeServerAtom,
  updateServerAtom,
  updateServerConnectionStatusAtom,
  batchUpdateServerConnectionStatusAtom,
  getServerByIdAtom
} from '../atoms/servers';

// Primary hook for server management
export function useServers() {
  const servers = useAtomValue(serversAtom);
  const serversWithStatus = useAtomValue(serversWithStatusAtom);
  const connectionStates = useAtomValue(serverConnectionStatesAtom);
  const serverCount = useAtomValue(serverCountAtom);
  const hasServers = useAtomValue(hasServersAtom);
  const connectedCount = useAtomValue(connectedServersCountAtom);
  const [isLoading, setIsLoading] = useAtom(serversLoadingAtom);

  // Action setters
  const addServerAction = useSetAtom(addServerAtom);
  const removeServerAction = useSetAtom(removeServerAtom);
  const updateServerAction = useSetAtom(updateServerAtom);
  const updateConnectionStatusAction = useSetAtom(updateServerConnectionStatusAtom);
  const batchUpdateConnectionStatusAction = useSetAtom(batchUpdateServerConnectionStatusAtom);
  const getServerByIdAction = useSetAtom(getServerByIdAtom);

  const addServer = (serverData: Omit<Server, 'id' | 'isConnected'>) => {
    return addServerAction(serverData);
  };

  const updateServer = (id: string, updates: Partial<Omit<Server, 'id'>>) => {
    const currentServer = servers.find(s => s.id === id);
    if (currentServer) {
      const updatedServer: Server = {
        ...currentServer,
        ...updates
      };
      updateServerAction(updatedServer);
    }
  };

  const removeServer = (id: string) => {
    removeServerAction(id);
  };

  const updateConnectionStatus = (serverId: string, status: ConnectionStatus) => {
    updateConnectionStatusAction(serverId, status);
  };

  const batchUpdateConnectionStatus = (updates: Array<{ serverId: string; status: ConnectionStatus }>) => {
    batchUpdateConnectionStatusAction(updates);
  };

  const getServer = (id: string): Server | null => {
    return getServerByIdAction(id);
  };

  const getServerConnectionStatus = (serverId: string): ConnectionStatus => {
    return connectionStates.get(serverId) || 'disconnected';
  };

  const getConnectedServers = (): Server[] => {
    return servers.filter(server => 
      connectionStates.get(server.id) === 'connected'
    );
  };

  const getDisconnectedServers = (): Server[] => {
    return servers.filter(server => 
      connectionStates.get(server.id) !== 'connected'
    );
  };

  const isServerConnected = (serverId: string): boolean => {
    return connectionStates.get(serverId) === 'connected';
  };

  const setLoading = (loading: boolean) => {
    setIsLoading(loading);
  };

  return {
    // State
    servers,
    serversWithStatus,
    connectionStates,
    serverCount,
    hasServers,
    connectedCount,
    isLoading,

    // Actions
    addServer,
    updateServer,
    removeServer,
    updateConnectionStatus,
    batchUpdateConnectionStatus,
    getServer,
    getServerConnectionStatus,
    getConnectedServers,
    getDisconnectedServers,
    isServerConnected,
    setLoading
  };
}

// Lightweight hook for server actions without state subscriptions
export function useServerActions() {
  const addServerAction = useSetAtom(addServerAtom);
  const removeServerAction = useSetAtom(removeServerAtom);
  const updateServerAction = useSetAtom(updateServerAtom);
  const updateConnectionStatusAction = useSetAtom(updateServerConnectionStatusAtom);

  const addServer = (serverData: Omit<Server, 'id' | 'isConnected'>) => {
    return addServerAction(serverData);
  };

  const updateServer = (server: Server) => {
    updateServerAction(server);
  };

  const removeServer = (id: string) => {
    removeServerAction(id);
  };

  const updateConnectionStatus = (serverId: string, status: ConnectionStatus) => {
    updateConnectionStatusAction(serverId, status);
  };

  return {
    addServer,
    updateServer,
    removeServer,
    updateConnectionStatus
  };
}

// Hook for monitoring connection statuses
export function useServerConnectionStatus(serverId?: string) {
  const connectionStates = useAtomValue(serverConnectionStatesAtom);
  const connectedCount = useAtomValue(connectedServersCountAtom);

  if (serverId) {
    return {
      status: connectionStates.get(serverId) || 'disconnected',
      isConnected: connectionStates.get(serverId) === 'connected',
      isConnecting: connectionStates.get(serverId) === 'connecting',
      hasError: connectionStates.get(serverId) === 'error'
    };
  }

  return {
    connectedCount,
    totalServers: connectionStates.size,
    hasConnectedServers: connectedCount > 0,
    allConnectionStates: connectionStates
  };
}