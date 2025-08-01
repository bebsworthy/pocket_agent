/**
 * WebSocket Service Implementation with EventEmitter pattern
 * 
 * This service provides robust WebSocket communication with:
 * - Native WebSocket API with EventEmitter pattern
 * - Automatic reconnection with exponential backoff
 * - Project join/leave tracking with session persistence
 * - Support for both WS and WSS protocols
 * - Comprehensive error handling and logging
 * 
 * Requirements: 5.1, 5.2, 5.4, 5.5, 5.7
 */

import { EventEmitter } from 'events';
import type { 
  ClientMessage, 
  ServerMessage, 
  ProjectJoinMessage,
  ProjectLeaveMessage 
} from '../../types/messages';
import type { ConnectionStatus } from '../../types/models';
import { createRateLimit } from '../../utils/sanitize';

export interface WebSocketServiceConfig {
  url: string;
  autoReconnect?: boolean;
  maxReconnectAttempts?: number;
  initialReconnectDelay?: number;
  maxReconnectDelay?: number;
  pingInterval?: number;
  connectionTimeout?: number;
}

export interface WebSocketServiceEvents {
  connected: () => void;
  disconnected: () => void;
  reconnecting: (attempt: number) => void;
  reconnectFailed: () => void;
  error: (error: Error) => void;
  message: (message: ServerMessage) => void;
  // Specific message type events
  project_state: (message: ServerMessage) => void;
  project_joined: (message: ServerMessage) => void;
  project_left: (message: ServerMessage) => void;
  project_deleted: (message: ServerMessage) => void;
  agent_message: (message: ServerMessage) => void;
  error_message: (message: ServerMessage) => void;
  session_reset: (message: ServerMessage) => void;
  messages_response: (message: ServerMessage) => void;
  health_status: (message: ServerMessage) => void;
  server_stats: (message: ServerMessage) => void;
  project_list_response: (message: ServerMessage) => void;
}

// Define typed EventEmitter with proper event mapping
interface TypedEventEmitter {
  on<K extends keyof WebSocketServiceEvents>(event: K, listener: WebSocketServiceEvents[K]): this;
  off<K extends keyof WebSocketServiceEvents>(event: K, listener: WebSocketServiceEvents[K]): this;
  emit<K extends keyof WebSocketServiceEvents>(event: K, ...args: Parameters<WebSocketServiceEvents[K]>): boolean;
  once<K extends keyof WebSocketServiceEvents>(event: K, listener: WebSocketServiceEvents[K]): this;
  removeAllListeners(event?: keyof WebSocketServiceEvents): this;
}

export class WebSocketService extends EventEmitter implements TypedEventEmitter {
  private ws: WebSocket | null = null;
  private config: Required<WebSocketServiceConfig>;
  private reconnectAttempts = 0;
  private reconnectTimeout: NodeJS.Timeout | null = null;
  private pingInterval: NodeJS.Timeout | null = null;
  private connectionTimeout: NodeJS.Timeout | null = null;
  private joinedProjects = new Set<string>();
  private connectionStatus: ConnectionStatus = 'disconnected';
  private lastPingTime = 0;
  private serverId: string;
  private messageRateLimit = createRateLimit(30, 60000); // 30 messages per minute
  private projectActionRateLimit = createRateLimit(10, 60000); // 10 project actions per minute
  private connectionDebounceTimeout: NodeJS.Timeout | null = null;
  private lastConnectionAttempt = 0;

  constructor(serverId: string, config: WebSocketServiceConfig) {
    super();
    this.serverId = serverId;
    this.config = {
      autoReconnect: true,
      maxReconnectAttempts: 10,
      initialReconnectDelay: 1000,
      maxReconnectDelay: 30000,
      pingInterval: 30000,
      connectionTimeout: 10000,
      ...config
    };

    // Load persisted joined projects from localStorage
    this.loadPersistedJoinedProjects();
  }

  /**
   * Establish WebSocket connection with protocol detection and debouncing
   */
  public async connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return Promise.resolve();
    }

    // Debounce connection attempts to prevent rapid reconnection cycles
    const now = Date.now();
    const timeSinceLastAttempt = now - this.lastConnectionAttempt;
    const debounceDelay = 1000; // 1 second minimum between connection attempts

    if (timeSinceLastAttempt < debounceDelay) {
      const remainingDelay = debounceDelay - timeSinceLastAttempt;
      console.debug(`Debouncing connection attempt for server ${this.serverId}, waiting ${remainingDelay}ms`);
      
      return new Promise((resolve, reject) => {
        this.connectionDebounceTimeout = setTimeout(() => {
          this.connectionDebounceTimeout = null;
          this.connect().then(resolve).catch(reject);
        }, remainingDelay);
      });
    }

    this.lastConnectionAttempt = now;

    return new Promise((resolve, reject) => {
      try {
        // Determine protocol based on URL and current page protocol
        const wsUrl = this.determineWebSocketUrl(this.config.url);
        
        this.setConnectionStatus('connecting');
        this.ws = new WebSocket(wsUrl);

        // Set connection timeout
        this.connectionTimeout = setTimeout(() => {
          if (this.ws && this.ws.readyState === WebSocket.CONNECTING) {
            console.warn(`Connection timeout after ${this.config.connectionTimeout}ms, closing connection`);
            this.ws.close();
            const error = new Error(`Connection timeout after ${this.config.connectionTimeout}ms`);
            this.handleError(error);
            reject(error);
          }
          // If WebSocket is not in CONNECTING state, timeout has already been handled
        }, this.config.connectionTimeout);

        this.ws.onopen = () => {
          this.clearConnectionTimeout();
          this.onConnectionOpen();
          resolve();
        };

        this.ws.onmessage = (event) => {
          this.onMessage(event);
        };

        this.ws.onclose = (event) => {
          this.onConnectionClose(event);
        };

        this.ws.onerror = (event) => {
          this.clearConnectionTimeout();
          const error = new Error(`WebSocket connection error: ${event}`);
          this.handleError(error);
          reject(error);
        };

      } catch (error) {
        const wsError = error instanceof Error ? error : new Error(String(error));
        this.handleError(wsError);
        reject(wsError);
      }
    });
  }

  /**
   * Disconnect WebSocket and cleanup
   */
  public disconnect(): void {
    this.config.autoReconnect = false; // Prevent auto-reconnect
    this.clearReconnectTimeout();
    this.clearPingInterval();
    this.clearConnectionTimeout();
    this.clearConnectionDebounceTimeout();

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.setConnectionStatus('disconnected');
  }

  /**
   * Send message to server
   */
  public send(message: ClientMessage): boolean {
    if (!this.isConnected()) {
      console.warn(`Cannot send message: WebSocket not connected for server ${this.serverId}`);
      return false;
    }

    // Apply rate limiting to messages
    if (!this.messageRateLimit(this.serverId)) {
      console.warn(`Message rate limit exceeded for server ${this.serverId}`);
      this.handleError(new Error('Message rate limit exceeded. Please wait before sending more messages.'));
      return false;
    }

    try {
      const messageString = JSON.stringify(message);
      this.ws!.send(messageString);
      console.debug(`Sent message to server ${this.serverId}:`, message.type);
      return true;
    } catch (error) {
      const sendError = new Error(`Failed to send message: ${error}`);
      this.handleError(sendError);
      return false;
    }
  }

  /**
   * Join a project and track it for reconnection
   */
  public joinProject(projectId: string): void {
    // Apply rate limiting to project actions
    if (!this.projectActionRateLimit(`${this.serverId}_project_actions`)) {
      console.warn(`Project action rate limit exceeded for server ${this.serverId}`);
      this.handleError(new Error('Project action rate limit exceeded. Please wait before joining/leaving projects.'));
      return;
    }

    this.joinedProjects.add(projectId);
    this.persistJoinedProjects();

    if (this.isConnected()) {
      const message: ProjectJoinMessage = {
        type: 'project_join',
        data: {
          project_id: projectId
        }
      };
      this.send(message);
    }
  }

  /**
   * Leave a project and remove from tracking
   */
  public leaveProject(projectId: string): void {
    // Apply rate limiting to project actions
    if (!this.projectActionRateLimit(`${this.serverId}_project_actions`)) {
      console.warn(`Project action rate limit exceeded for server ${this.serverId}`);
      this.handleError(new Error('Project action rate limit exceeded. Please wait before joining/leaving projects.'));
      return;
    }

    this.joinedProjects.delete(projectId);
    this.persistJoinedProjects();

    if (this.isConnected()) {
      const message: ProjectLeaveMessage = {
        type: 'project_leave',
        project_id: projectId
      };
      this.send(message);
    }
  }

  /**
   * Get current connection status
   */
  public getConnectionStatus(): ConnectionStatus {
    return this.connectionStatus;
  }

  /**
   * Check if WebSocket is connected
   */
  public isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  /**
   * Get joined projects
   */
  public getJoinedProjects(): string[] {
    return Array.from(this.joinedProjects);
  }

  /**
   * Get server ID
   */
  public getServerId(): string {
    return this.serverId;
  }

  /**
   * Handle connection opened
   */
  private onConnectionOpen(): void {
    console.info(`WebSocket connected to server ${this.serverId}`);
    this.reconnectAttempts = 0;
    this.setConnectionStatus('connected');
    this.startPing();
    this.rejoinProjects();
    this.emit('connected');
  }

  /**
   * Handle incoming messages
   */
  private onMessage(event: MessageEvent): void {
    try {
      const message: ServerMessage = JSON.parse(event.data);
      
      // Update activity timestamp for connection health monitoring
      this.lastPingTime = Date.now();

      console.debug(`Received message from server ${this.serverId}:`, message.type);
      
      // Emit generic message event
      this.emit('message', message);
      
      // Emit specific message type event
      this.emit(message.type as keyof WebSocketServiceEvents, message);

    } catch (error) {
      console.error(`Failed to parse message from server ${this.serverId}:`, error);
      const parseError = new Error(`Message parse error: ${error}`);
      this.handleError(parseError);
    }
  }

  /**
   * Handle connection closed
   */
  private onConnectionClose(event: CloseEvent): void {
    console.info(`WebSocket disconnected from server ${this.serverId}:`, event.code, event.reason);
    this.clearPingInterval();
    this.setConnectionStatus('disconnected');
    this.ws = null;
    this.emit('disconnected');

    // Attempt reconnection if configured
    if (this.config.autoReconnect) {
      this.attemptReconnect();
    }
  }

  /**
   * Handle WebSocket errors with enhanced error boundary integration
   */
  private handleError(error: Error): void {
    console.error(`WebSocket error for server ${this.serverId}:`, error.message);
    this.setConnectionStatus('error');
    
    // Enhance error with context for better debugging
    const enhancedError = new Error(`WebSocket error on server ${this.serverId}: ${error.message}`);
    enhancedError.stack = error.stack;
    enhancedError.name = `WebSocketError_${this.serverId}`;
    
    // Add custom properties for error boundary handling
    (enhancedError as any).serverId = this.serverId;
    (enhancedError as any).isWebSocketError = true;
    (enhancedError as any).connectionStatus = this.connectionStatus;
    
    this.emit('error', enhancedError);
  }

  /**
   * Attempt reconnection with exponential backoff
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      console.error(`Max reconnection attempts (${this.config.maxReconnectAttempts}) reached for server ${this.serverId}`);
      this.emit('reconnectFailed');
      return;
    }

    this.reconnectAttempts++;
    
    // Calculate delay with exponential backoff
    const baseDelay = this.config.initialReconnectDelay;
    const maxDelay = this.config.maxReconnectDelay;
    const exponentialDelay = baseDelay * Math.pow(2, this.reconnectAttempts - 1);
    const delay = Math.min(exponentialDelay, maxDelay);

    console.info(`Attempting reconnection ${this.reconnectAttempts}/${this.config.maxReconnectAttempts} to server ${this.serverId} in ${delay}ms`);
    
    this.emit('reconnecting', this.reconnectAttempts);

    this.reconnectTimeout = setTimeout(() => {
      this.connect().catch((error) => {
        console.error(`Reconnection attempt ${this.reconnectAttempts} failed for server ${this.serverId}:`, error);
      });
    }, delay);
  }

  /**
   * Start connection health monitoring using message activity
   * 
   * Uses a heartbeat approach based on message activity rather than custom ping/pong.
   * This avoids protocol issues and aligns with standard WebSocket best practices.
   */
  private startPing(): void {
    this.clearPingInterval();
    
    this.pingInterval = setInterval(() => {
      if (this.isConnected()) {
        const now = Date.now();
        const timeSinceLastActivity = now - this.lastPingTime;
        
        // Check if connection has been idle for too long
        if (timeSinceLastActivity > this.config.pingInterval * 2) {
          console.warn(`No activity detected on server ${this.serverId} for ${timeSinceLastActivity}ms - connection may be stale`);
          
          // Instead of sending custom ping, close connection to trigger reconnection
          // This approach is more reliable than custom ping/pong messages
          if (this.ws) {
            console.info(`Closing potentially stale connection to server ${this.serverId}`);
            this.ws.close(1000, 'Connection health check failed');
          }
        } else {
          // Update activity timestamp to track connection health
          this.lastPingTime = now;
        }
      }
    }, this.config.pingInterval);
  }

  /**
   * Rejoin all previously joined projects after reconnection
   */
  private rejoinProjects(): void {
    if (this.joinedProjects.size === 0) {
      return;
    }

    console.info(`Rejoining ${this.joinedProjects.size} projects for server ${this.serverId}`);
    
    this.joinedProjects.forEach(projectId => {
      const message: ProjectJoinMessage = {
        type: 'project_join',
        data: {
          project_id: projectId
        }
      };
      this.send(message);
    });
  }

  /**
   * Determine WebSocket URL with protocol detection
   */
  private determineWebSocketUrl(url: string): string {
    // If URL already has protocol, use it as-is
    if (url.startsWith('ws://') || url.startsWith('wss://')) {
      return url;
    }

    // Determine protocol based on current page protocol
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    
    // Handle different URL formats
    if (url.startsWith('//')) {
      return `${protocol}${url}`;
    } else if (url.startsWith('/')) {
      return `${protocol}//${window.location.host}${url}`;
    } else {
      return `${protocol}//${url}`;
    }
  }

  /**
   * Set connection status and emit if changed
   */
  private setConnectionStatus(status: ConnectionStatus): void {
    if (this.connectionStatus !== status) {
      const previousStatus = this.connectionStatus;
      this.connectionStatus = status;
      console.debug(`Connection status changed for server ${this.serverId}: ${previousStatus} -> ${status}`);
    }
  }

  /**
   * Check if localStorage is available and functional
   */
  private isLocalStorageAvailable(): boolean {
    try {
      const test = '__websocket_localStorage_test__';
      localStorage.setItem(test, 'test');
      localStorage.removeItem(test);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Persist joined projects to localStorage with enhanced error handling and fallbacks
   */
  private persistJoinedProjects(): void {
    if (!this.isLocalStorageAvailable()) {
      console.warn(`localStorage not available for server ${this.serverId}, data will not persist`);
      return;
    }

    const key = `websocket_joined_projects_${this.serverId}`;
    const projectsArray = Array.from(this.joinedProjects);
    
    try {
      const data = JSON.stringify(projectsArray);
      
      // Check data size to prevent quota exceeded errors
      if (data.length > 1024 * 1024) { // 1MB limit
        console.warn(`Project data too large for localStorage on server ${this.serverId}, truncating`);
        // Keep only the most recently joined projects
        const truncatedArray = projectsArray.slice(-50); // Keep last 50 projects
        localStorage.setItem(key, JSON.stringify(truncatedArray));
        console.info(`Truncated and saved ${truncatedArray.length} projects for server ${this.serverId}`);
      } else {
        localStorage.setItem(key, data);
      }
    } catch (error) {
      if (error instanceof Error) {
        if (error.name === 'QuotaExceededError' || error.name === 'NS_ERROR_DOM_QUOTA_REACHED') {
          // localStorage quota exceeded - try to free up space
          console.warn(`localStorage quota exceeded for server ${this.serverId}, attempting cleanup`);
          this.cleanupOldLocalStorage();
          
          // Retry with minimal data
          try {
            const minimalArray = projectsArray.slice(-10); // Keep only last 10
            localStorage.setItem(key, JSON.stringify(minimalArray));
            console.info(`Saved minimal project list (${minimalArray.length} projects) for server ${this.serverId}`);
          } catch (retryError) {
            console.error(`Failed to save even minimal project data for server ${this.serverId}:`, retryError);
          }
        } else {
          console.warn(`Failed to persist joined projects for server ${this.serverId}:`, error.message);
        }
      } else {
        console.warn(`Unknown error persisting joined projects for server ${this.serverId}:`, error);
      }
    }
  }

  /**
   * Load persisted joined projects from localStorage with enhanced error handling
   */
  private loadPersistedJoinedProjects(): void {
    this.joinedProjects = new Set(); // Initialize with empty set
    
    if (!this.isLocalStorageAvailable()) {
      console.warn(`localStorage not available for server ${this.serverId}, starting with empty project list`);
      return;
    }

    try {
      const key = `websocket_joined_projects_${this.serverId}`;
      const storedProjects = localStorage.getItem(key);
      
      if (!storedProjects) {
        console.debug(`No persisted projects found for server ${this.serverId}`);
        return;
      }

      let projectsArray: string[];
      try {
        projectsArray = JSON.parse(storedProjects);
      } catch (parseError) {
        console.warn(`Invalid JSON in persisted projects for server ${this.serverId}, resetting`);
        localStorage.removeItem(key);
        return;
      }

      // Validate data structure
      if (!Array.isArray(projectsArray)) {
        console.warn(`Invalid project data structure for server ${this.serverId}, expected array`);
        localStorage.removeItem(key);
        return;
      }

      // Validate and sanitize project IDs
      const validProjects = projectsArray.filter(projectId => {
        if (typeof projectId !== 'string' || !projectId.trim()) {
          console.warn(`Invalid project ID found for server ${this.serverId}:`, projectId);
          return false;
        }
        return true;
      });

      this.joinedProjects = new Set(validProjects);
      console.debug(`Loaded ${validProjects.length} persisted projects for server ${this.serverId}`);
      
      // If we filtered out invalid projects, update localStorage
      if (validProjects.length !== projectsArray.length) {
        console.info(`Cleaned up ${projectsArray.length - validProjects.length} invalid project IDs for server ${this.serverId}`);
        this.persistJoinedProjects();
      }
      
    } catch (error) {
      console.warn(`Failed to load persisted joined projects for server ${this.serverId}:`, error);
      this.joinedProjects = new Set();
      
      // Clean up corrupted data
      try {
        const key = `websocket_joined_projects_${this.serverId}`;
        localStorage.removeItem(key);
        console.info(`Removed corrupted project data for server ${this.serverId}`);
      } catch (cleanupError) {
        console.warn(`Failed to cleanup corrupted data for server ${this.serverId}:`, cleanupError);
      }
    }
  }

  /**
   * Clean up old localStorage entries to free up space
   */
  private cleanupOldLocalStorage(): void {
    try {
      const keysToRemove: string[] = [];
      
      // Find old WebSocket-related keys
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('websocket_') && key !== `websocket_joined_projects_${this.serverId}`) {
          keysToRemove.push(key);
        }
      }
      
      // Remove old entries
      keysToRemove.forEach(key => {
        try {
          localStorage.removeItem(key);
        } catch (error) {
          console.warn(`Failed to remove old localStorage key ${key}:`, error);
        }
      });
      
      if (keysToRemove.length > 0) {
        console.info(`Cleaned up ${keysToRemove.length} old WebSocket localStorage entries`);
      }
    } catch (error) {
      console.warn('Failed to cleanup old localStorage entries:', error);
    }
  }

  /**
   * Clear reconnect timeout
   */
  private clearReconnectTimeout(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  }

  /**
   * Clear ping interval
   */
  private clearPingInterval(): void {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
  }

  /**
   * Clear connection timeout
   */
  private clearConnectionTimeout(): void {
    if (this.connectionTimeout) {
      clearTimeout(this.connectionTimeout);
      this.connectionTimeout = null;
    }
  }

  /**
   * Clear connection debounce timeout
   */
  private clearConnectionDebounceTimeout(): void {
    if (this.connectionDebounceTimeout) {
      clearTimeout(this.connectionDebounceTimeout);
      this.connectionDebounceTimeout = null;
    }
  }

  /**
   * Cleanup method for proper disposal
   */
  public destroy(): void {
    this.disconnect();
    this.removeAllListeners();
    
    // Clear persisted data with enhanced error handling
    if (this.isLocalStorageAvailable()) {
      try {
        const key = `websocket_joined_projects_${this.serverId}`;
        localStorage.removeItem(key);
        console.debug(`Cleared persisted data for server ${this.serverId}`);
      } catch (error) {
        console.warn(`Failed to clear persisted data for server ${this.serverId}:`, error);
      }
    }
  }
}