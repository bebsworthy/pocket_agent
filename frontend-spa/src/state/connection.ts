import { atom } from 'jotai';
import type { ConnectionState } from '../types/messages';

// Connection state atom
export const connectionStateAtom = atom<ConnectionState>('disconnected');

// WebSocket connection status
export const isConnectedAtom = atom(
  (get) => get(connectionStateAtom) === 'connected'
);

export const isConnectingAtom = atom(
  (get) => get(connectionStateAtom) === 'connecting'
);