/**
 * Server state atoms using Jotai for atomic state management.
 * Provides persistent storage of servers using localStorage.
 */

import { atom } from 'jotai';
import { atomWithStorage, selectAtom } from 'jotai/utils';
import type { Server, ConnectionStatus } from '../../types/models';

// Servers list with localStorage persistence and error handling
export const serversAtom = atomWithStorage<Server[]>(
  'servers',
  [],
  {
    getItem: (key: string, initialValue: Server[]) => {
      try {
        const item = localStorage.getItem(key);
        if (item === null) {
          return initialValue;
        }
        const parsed = JSON.parse(item);
        // Validate that parsed data is an array
        if (!Array.isArray(parsed)) {
          console.warn('Servers data in localStorage is not an array, resetting to empty array');
          return initialValue;
        }
        return parsed;
      } catch (error) {
        console.error('Failed to deserialize servers from localStorage:', error);
        return initialValue;
      }
    },
    setItem: (key: string, value: Server[]) => {
      localStorage.setItem(key, JSON.stringify(value));
    },
    removeItem: (key: string) => {
      localStorage.removeItem(key);
    },
    subscribe: (key: string, callback: (value: Server[]) => void, initialValue: Server[]) => {
      if (typeof window === 'undefined' || typeof window.addEventListener === 'undefined') {
        return;
      }
      const handler = (e: StorageEvent) => {
        if (e.storageArea === localStorage && e.key === key) {
          let newValue: Server[];
          try {
            if (e.newValue === null) {
              newValue = initialValue;
            } else {
              const parsed = JSON.parse(e.newValue);
              if (!Array.isArray(parsed)) {
                console.warn(
                  'Servers data in localStorage is not an array, resetting to empty array'
                );
                newValue = initialValue;
              } else {
                newValue = parsed;
              }
            }
          } catch {
            newValue = initialValue;
          }
          callback(newValue);
        }
      };
      window.addEventListener('storage', handler);
      return () => window.removeEventListener('storage', handler);
    },
  },
  {
    getOnInit: true,
  }
);

// Server connection states (not persisted - runtime state)
export const serverConnectionStatesAtom = atom<Map<string, ConnectionStatus>>(new Map());

// Server loading state
export const serversLoadingAtom = atom<boolean>(false);

// Derived atom for server count
export const serverCountAtom = atom(get => get(serversAtom).length);

// Derived atom to check if servers exist
export const hasServersAtom = atom(get => get(serversAtom).length > 0);

// Memoized atom for servers array length to help with optimization
const serversLengthAtom = selectAtom(serversAtom, servers => servers.length);

// Memoized atom for connection states size to help with optimization
const connectionStatesKeysAtom = selectAtom(serverConnectionStatesAtom, states =>
  Array.from(states.keys()).sort().join(',')
);

// Derived atom to get servers with their connection status with proper memoization
export const serversWithStatusAtom = atom(get => {
  const servers = get(serversAtom);
  const connectionStates = get(serverConnectionStatesAtom);

  // The selectAtom utilities above help Jotai determine when this needs to recompute
  // by creating dependencies on the array length and connection state keys
  get(serversLengthAtom); // Subscribe to length changes
  get(connectionStatesKeysAtom); // Subscribe to connection state key changes

  return servers.map(server => ({
    ...server,
    connectionStatus: connectionStates.get(server.id) || 'disconnected',
  }));
});

// Derived atom to get connected servers count
export const connectedServersCountAtom = atom(get => {
  const connectionStates = get(serverConnectionStatesAtom);
  return Array.from(connectionStates.values()).filter(status => status === 'connected').length;
});

// Atom for tracking server operations (add, remove, update)
export const serverOperationAtom = atom<{
  type: 'add' | 'remove' | 'update' | null;
  serverId?: string;
}>({ type: null });

// Write-only atom for adding a server
export const addServerAtom = atom(
  null,
  (get, set, newServer: Omit<Server, 'id' | 'isConnected'>) => {
    const servers = get(serversAtom);
    const serverWithId: Server = {
      ...newServer,
      id: crypto.randomUUID(),
      isConnected: false,
    };
    set(serversAtom, [...servers, serverWithId]);
    set(serverOperationAtom, { type: 'add', serverId: serverWithId.id });

    // Initialize connection state
    const connectionStates = get(serverConnectionStatesAtom);
    const newConnectionStates = new Map(connectionStates);
    newConnectionStates.set(serverWithId.id, 'disconnected');
    set(serverConnectionStatesAtom, newConnectionStates);

    return serverWithId;
  }
);

// Write-only atom for removing a server
export const removeServerAtom = atom(null, (get, set, serverId: string) => {
  const servers = get(serversAtom);
  const filteredServers = servers.filter(s => s.id !== serverId);
  set(serversAtom, filteredServers);
  set(serverOperationAtom, { type: 'remove', serverId });

  // Remove connection state
  const connectionStates = get(serverConnectionStatesAtom);
  const newConnectionStates = new Map(connectionStates);
  newConnectionStates.delete(serverId);
  set(serverConnectionStatesAtom, newConnectionStates);
});

// Write-only atom for updating a server
export const updateServerAtom = atom(null, (get, set, updatedServer: Server) => {
  const servers = get(serversAtom);
  const updatedServers = servers.map(s => (s.id === updatedServer.id ? updatedServer : s));
  set(serversAtom, updatedServers);
  set(serverOperationAtom, { type: 'update', serverId: updatedServer.id });
});

// Write-only atom for updating server connection status
export const updateServerConnectionStatusAtom = atom(
  null,
  (get, set, serverId: string, status: ConnectionStatus) => {
    const connectionStates = get(serverConnectionStatesAtom);
    const newConnectionStates = new Map(connectionStates);
    newConnectionStates.set(serverId, status);
    set(serverConnectionStatesAtom, newConnectionStates);

    // Also update the server's isConnected flag
    const servers = get(serversAtom);
    const updatedServers = servers.map(s =>
      s.id === serverId ? { ...s, isConnected: status === 'connected' } : s
    );
    set(serversAtom, updatedServers);
  }
);

// Write-only atom for batch updating multiple server connection statuses
export const batchUpdateServerConnectionStatusAtom = atom(
  null,
  (get, set, updates: Array<{ serverId: string; status: ConnectionStatus }>) => {
    const connectionStates = get(serverConnectionStatesAtom);
    const servers = get(serversAtom);

    const newConnectionStates = new Map(connectionStates);
    const updatedServers = [...servers];

    updates.forEach(({ serverId, status }) => {
      newConnectionStates.set(serverId, status);
      const serverIndex = updatedServers.findIndex(s => s.id === serverId);
      if (serverIndex !== -1) {
        updatedServers[serverIndex] = {
          ...updatedServers[serverIndex],
          isConnected: status === 'connected',
        };
      }
    });

    set(serverConnectionStatesAtom, newConnectionStates);
    set(serversAtom, updatedServers);
  }
);

// Utility atom to get server by ID
export const getServerByIdAtom = atom(null, (get, _set, serverId: string) => {
  const servers = get(serversAtom);
  return servers.find(s => s.id === serverId) || null;
});
