// Legacy WebSocket service (deprecated)
export {
  WebSocketService as LegacyWebSocketService,
  createWebSocketService,
  getWebSocketService,
} from './websocket';
export type {
  WebSocketServiceConfig as LegacyWebSocketServiceConfig,
  WebSocketEventHandler,
  ConnectionStateHandler,
} from './websocket';

// New EventEmitter-based WebSocket service (production)
export { WebSocketService } from './websocket/WebSocketService';
export {
  WebSocketProvider,
  useWebSocketContext,
  useWebSocketService,
  useWebSocketConnection,
} from './websocket/WebSocketContext';
export type { WebSocketServiceConfig, WebSocketServiceEvents } from './websocket/WebSocketService';
export * from './websocket/hooks';

// Storage services
export {
  LocalStorageService,
  localStorageService,
  StorageError,
} from './storage/LocalStorageService';
export {
  useProjectStorage,
  useServerStorage,
  useThemeStorage,
  useStorageError,
  useStorageStatus,
  useStorageBackup,
  useStorage,
} from './storage/hooks';
