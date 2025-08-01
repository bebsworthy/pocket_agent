/**
 * Core application model interfaces for the frontend-spa module.
 * These interfaces define the client-side data structures for projects and servers.
 */

// Import connection types from messages module to avoid duplication
import type { ConnectionStatus } from './messages';

export interface Project {
  id: string;
  name: string;
  path: string;
  serverId: string;
  createdAt: string;
  lastActive: string;
}

export interface Server {
  id: string;
  name: string;
  websocketUrl: string;
  isConnected: boolean;
}

// Project state types (matches server protocol)
export type ProjectState = 'IDLE' | 'EXECUTING' | 'ERROR';

// UI-specific interfaces
export interface ProjectWithServer extends Project {
  server: Server;
}

export interface ServerWithStats extends Server {
  projectCount: number;
  lastSeen?: string;
}

// Re-export message types for compatibility
export type { ClientMessage, ServerMessage, ConnectionStatus } from './messages';