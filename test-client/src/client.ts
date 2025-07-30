import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { 
  ClientMessage, 
  ServerMessage,
  ProjectCreateMessage,
  ProjectJoinMessage,
  ExecuteMessage,
  ProjectStateMessage,
  AgentMessage,
  ErrorMessage
} from './types/messages';

export interface PocketAgentClientOptions {
  url?: string;
  timeout?: number;
  debug?: boolean;
}

export class PocketAgentClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private url: string;
  private timeout: number;
  private debug: boolean;
  private messageHandlers: Map<string, (msg: ServerMessage) => void> = new Map();
  private currentProjectId: string | null = null;

  constructor(options: PocketAgentClientOptions = {}) {
    super();
    this.url = options.url || 'ws://localhost:8443/ws';
    this.timeout = options.timeout || 30000;
    this.debug = options.debug || false;
  }

  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.url);

      // Timeout for connection
      const connectionTimeout = setTimeout(() => {
        if (this.ws?.readyState === WebSocket.CONNECTING) {
          this.ws.close();
          reject(new Error('Connection timeout'));
        }
      }, this.timeout);

      this.ws.on('open', () => {
        if (this.debug) console.log('WebSocket connected');
        clearTimeout(connectionTimeout);
        this.emit('connected');
        resolve();
      });

      this.ws.on('message', (data: WebSocket.Data) => {
        try {
          const message = JSON.parse(data.toString()) as ServerMessage;
          if (this.debug) console.log('Received:', JSON.stringify(message, null, 2));

          // Handle message type handlers
          if (this.messageHandlers.has(message.type)) {
            const handler = this.messageHandlers.get(message.type)!;
            handler(message);
          }

          // Emit events for all message types
          this.emit('message', message);
          this.emit(message.type, message);
        } catch (error) {
          console.error('Failed to parse message:', error);
        }
      });

      this.ws.on('error', (error) => {
        if (this.debug) console.error('WebSocket error:', error);
        clearTimeout(connectionTimeout);
        this.emit('error', error);
        reject(error);
      });

      this.ws.on('close', () => {
        if (this.debug) console.log('WebSocket disconnected');
        clearTimeout(connectionTimeout);
        this.emit('disconnected');
      });
    });
  }

  private send(message: ClientMessage): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected');
    }
    if (this.debug) console.log('Sending:', JSON.stringify(message, null, 2));
    this.ws.send(JSON.stringify(message));
  }


  async createProject(path: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.off('project_state', handler);
        reject(new Error('Timeout waiting for project creation'));
      }, this.timeout);

      const handler = (message: any) => {
        // Check if this is the project we just created
        if (message.type === 'project_state' && 
            message.data && 
            message.data.path === path && 
            message.data.state === 'IDLE') {
          clearTimeout(timeout);
          this.off('project_state', handler);
          this.currentProjectId = message.project_id;
          resolve(message.project_id);
        }
      };

      // Listen for project state messages
      this.on('project_state', handler);

      // Send the create project message
      const message: ProjectCreateMessage = {
        type: 'project_create',
        data: { path }
      };
      
      try {
        this.send(message);
      } catch (error) {
        clearTimeout(timeout);
        this.off('project_state', handler);
        reject(error);
      }
    });
  }

  async joinProject(projectId: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.off('project_join', handler);
        reject(new Error('Timeout waiting to join project'));
      }, this.timeout);

      const handler = (message: any) => {
        if (message.type === 'project_join' && message.project_id === projectId) {
          clearTimeout(timeout);
          this.off('project_join', handler);
          resolve();
        }
      };

      // Listen for project join messages
      this.on('project_join', handler);

      // Send the join project message
      const message: ProjectJoinMessage = {
        type: 'project_join',
        data: {
          project_id: projectId
        }
      };
      
      try {
        this.send(message);
      } catch (error) {
        clearTimeout(timeout);
        this.off('project_join', handler);
        reject(error);
      }
    });
  }

  async execute(projectId: string, prompt: string, options?: ExecuteMessage['data']['options']): Promise<void> {
    const message: ExecuteMessage = {
      type: 'execute',
      project_id: projectId,
      data: {
        prompt,
        options
      }
    };

    this.send(message);
  }

  onAgentMessage(callback: (message: AgentMessage) => void): void {
    this.messageHandlers.set('agent_message', callback as any);
  }

  onProjectState(callback: (message: ProjectStateMessage) => void): void {
    this.messageHandlers.set('project_state', callback as any);
  }

  onError(callback: (message: ErrorMessage) => void): void {
    this.messageHandlers.set('error', callback as any);
  }

  async waitForProjectState(projectId: string, state: string, timeout: number = 60000): Promise<void> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.off('project_state', handler);
        reject(new Error(`Timeout waiting for project state: ${state}`));
      }, timeout);

      const handler = (message: ProjectStateMessage) => {
        if (message.project_id === projectId && message.data.state === state) {
          clearTimeout(timer);
          this.off('project_state', handler);
          resolve();
        }
      };

      this.on('project_state', handler);
    });
  }

  close(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.ws) {
        resolve();
        return;
      }

      // Set up close handler
      this.ws.once('close', () => {
        this.ws = null;
        resolve();
      });

      // Initiate close
      this.ws.close();

      // Fallback timeout in case close doesn't complete
      setTimeout(() => {
        if (this.ws) {
          this.ws = null;
          resolve();
        }
      }, 5000);
    });
  }

  get projectId(): string | null {
    return this.currentProjectId;
  }
}