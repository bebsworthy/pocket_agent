/**
 * LocalStorageService - Robust localStorage implementation with error handling
 *
 * Provides type-safe storage operations with:
 * - Storage quota management
 * - Data validation and corruption recovery
 * - Versioning support for data migration
 * - Error handling for quota exceeded and access denied
 */

import { Project, Server } from '@/types/models';

// Storage keys
const STORAGE_KEYS = {
  PROJECTS: 'pocket_agent_projects',
  SERVERS: 'pocket_agent_servers',
  THEME: 'pocket_agent_theme',
  VERSION: 'pocket_agent_version',
} as const;

// Current version for data migration
const CURRENT_VERSION = '1.0.0';

// Storage interfaces
interface StoredData<T> {
  version: string;
  timestamp: number;
  data: T;
}

// Error types
export class StorageError extends Error {
  constructor(
    message: string,
    public readonly code:
      | 'QUOTA_EXCEEDED'
      | 'ACCESS_DENIED'
      | 'CORRUPTED_DATA'
      | 'PARSE_ERROR'
      | 'UNKNOWN',
    public readonly originalError?: Error
  ) {
    super(message);
    this.name = 'StorageError';
  }
}

export class LocalStorageService {
  private static instance: LocalStorageService;

  private constructor() {
    this.validateStorage();
    this.migrateDataIfNeeded();
  }

  static getInstance(): LocalStorageService {
    if (!LocalStorageService.instance) {
      LocalStorageService.instance = new LocalStorageService();
    }
    return LocalStorageService.instance;
  }

  /**
   * Validates that localStorage is available and functional
   */
  private validateStorage(): void {
    try {
      const testKey = '__pocket_agent_test__';
      localStorage.setItem(testKey, 'test');
      localStorage.removeItem(testKey);
    } catch (error) {
      throw new StorageError(
        'localStorage is not available or accessible',
        'ACCESS_DENIED',
        error as Error
      );
    }
  }

  /**
   * Migrates data from older versions if necessary
   */
  private migrateDataIfNeeded(): void {
    try {
      const storedVersion = localStorage.getItem(STORAGE_KEYS.VERSION);
      if (!storedVersion || storedVersion !== CURRENT_VERSION) {
        this.performMigration(storedVersion);
        localStorage.setItem(STORAGE_KEYS.VERSION, CURRENT_VERSION);
      }
    } catch (error) {
      console.warn('Failed to check version, clearing storage:', error);
      this.clearAllData();
      localStorage.setItem(STORAGE_KEYS.VERSION, CURRENT_VERSION);
    }
  }

  /**
   * Performs data migration from older versions
   */
  private performMigration(fromVersion: string | null): void {
    // Currently no migrations needed, but framework is in place
    console.log(`Migrating from version ${fromVersion || 'unknown'} to ${CURRENT_VERSION}`);
  }

  /**
   * Generic method to store data with version and timestamp
   */
  private setItem<T>(key: string, data: T): void {
    try {
      const storedData: StoredData<T> = {
        version: CURRENT_VERSION,
        timestamp: Date.now(),
        data,
      };

      const serialized = JSON.stringify(storedData);
      localStorage.setItem(key, serialized);
    } catch (error) {
      if (error instanceof Error) {
        if (error.name === 'QuotaExceededError' || error.message.includes('quota')) {
          throw new StorageError(
            'Storage quota exceeded. Please clear some data.',
            'QUOTA_EXCEEDED',
            error
          );
        }
        if (error.name === 'TypeError' || error.message.includes('circular')) {
          throw new StorageError('Failed to serialize data', 'PARSE_ERROR', error);
        }
      }
      throw new StorageError('Failed to store data', 'UNKNOWN', error as Error);
    }
  }

  /**
   * Generic method to retrieve data with validation
   */
  private getItem<T>(key: string, defaultValue: T): T {
    try {
      const item = localStorage.getItem(key);
      if (!item) {
        return defaultValue;
      }

      const parsed = JSON.parse(item) as StoredData<T>;

      // Validate structure
      if (!parsed || typeof parsed !== 'object' || !('data' in parsed)) {
        console.warn(`Invalid storage format for key ${key}, using default`);
        return defaultValue;
      }

      // Version check (could trigger migration in the future)
      if (parsed.version !== CURRENT_VERSION) {
        console.warn(`Version mismatch for key ${key}, may need migration`);
      }

      return parsed.data;
    } catch (error) {
      console.warn(`Failed to parse data for key ${key}:`, error);
      throw new StorageError(`Corrupted data found for ${key}`, 'CORRUPTED_DATA', error as Error);
    }
  }

  /**
   * Projects storage methods
   */
  getProjects(): Project[] {
    try {
      const projects = this.getItem(STORAGE_KEYS.PROJECTS, []);
      return this.validateProjects(projects);
    } catch (error) {
      console.error('Failed to load projects, returning empty array:', error);
      return [];
    }
  }

  setProjects(projects: Project[]): void {
    const validatedProjects = this.validateProjects(projects);
    this.setItem(STORAGE_KEYS.PROJECTS, validatedProjects);
  }

  addProject(project: Project): void {
    const projects = this.getProjects();
    const existingIndex = projects.findIndex(p => p.id === project.id);

    if (existingIndex >= 0) {
      projects[existingIndex] = project;
    } else {
      projects.push(project);
    }

    this.setProjects(projects);
  }

  removeProject(projectId: string): boolean {
    const projects = this.getProjects();
    const initialLength = projects.length;
    const filteredProjects = projects.filter(p => p.id !== projectId);

    if (filteredProjects.length < initialLength) {
      this.setProjects(filteredProjects);
      return true;
    }
    return false;
  }

  updateProject(projectId: string, updates: Partial<Omit<Project, 'id'>>): boolean {
    const projects = this.getProjects();
    const projectIndex = projects.findIndex(p => p.id === projectId);

    if (projectIndex >= 0) {
      projects[projectIndex] = { ...projects[projectIndex], ...updates };
      this.setProjects(projects);
      return true;
    }
    return false;
  }

  /**
   * Servers storage methods
   */
  getServers(): Server[] {
    try {
      const servers = this.getItem(STORAGE_KEYS.SERVERS, []);
      return this.validateServers(servers);
    } catch (error) {
      console.error('Failed to load servers, returning empty array:', error);
      return [];
    }
  }

  setServers(servers: Server[]): void {
    const validatedServers = this.validateServers(servers);
    this.setItem(STORAGE_KEYS.SERVERS, validatedServers);
  }

  addServer(server: Server): void {
    const servers = this.getServers();
    const existingIndex = servers.findIndex(s => s.id === server.id);

    if (existingIndex >= 0) {
      servers[existingIndex] = server;
    } else {
      servers.push(server);
    }

    this.setServers(servers);
  }

  removeServer(serverId: string): boolean {
    const servers = this.getServers();
    const initialLength = servers.length;
    const filteredServers = servers.filter(s => s.id !== serverId);

    if (filteredServers.length < initialLength) {
      this.setServers(filteredServers);
      return true;
    }
    return false;
  }

  updateServer(serverId: string, updates: Partial<Omit<Server, 'id'>>): boolean {
    const servers = this.getServers();
    const serverIndex = servers.findIndex(s => s.id === serverId);

    if (serverIndex >= 0) {
      servers[serverIndex] = { ...servers[serverIndex], ...updates };
      this.setServers(servers);
      return true;
    }
    return false;
  }

  /**
   * Theme storage methods
   */
  getTheme(): 'light' | 'dark' | 'system' {
    try {
      return this.getItem(STORAGE_KEYS.THEME, 'system' as const);
    } catch (error) {
      console.error('Failed to load theme, using system default:', error);
      return 'system';
    }
  }

  setTheme(theme: 'light' | 'dark' | 'system'): void {
    this.setItem(STORAGE_KEYS.THEME, theme);
  }

  /**
   * Data validation methods
   */
  private validateProjects(projects: unknown): Project[] {
    if (!Array.isArray(projects)) {
      console.warn('Projects data is not an array, returning empty array');
      return [];
    }

    return projects.filter(this.isValidProject).map(project => ({
      ...project,
      createdAt: project.createdAt || new Date().toISOString(),
      lastActive: project.lastActive || new Date().toISOString(),
    }));
  }

  private validateServers(servers: unknown): Server[] {
    if (!Array.isArray(servers)) {
      console.warn('Servers data is not an array, returning empty array');
      return [];
    }

    return servers.filter(this.isValidServer).map(server => ({
      ...server,
      isConnected: false, // Always start as disconnected
    }));
  }

  private isValidProject(project: unknown): project is Project {
    if (!project || typeof project !== 'object') return false;

    const p = project as Partial<Project>;

    // Validate required string fields with comprehensive checks
    if (!p.id || typeof p.id !== 'string' || p.id.trim().length === 0) return false;
    if (!p.name || typeof p.name !== 'string' || p.name.trim().length === 0) return false;
    if (!p.path || typeof p.path !== 'string' || p.path.trim().length === 0) return false;
    if (!p.serverId || typeof p.serverId !== 'string' || p.serverId.trim().length === 0)
      return false;

    // Validate ID format (should be UUID-like or valid identifier)
    if (!/^[a-zA-Z0-9_-]{1,50}$/.test(p.id.trim())) return false;
    if (!/^[a-zA-Z0-9_-]{1,50}$/.test(p.serverId.trim())) return false;

    // Validate name contains only safe characters and reasonable length
    if (!/^[a-zA-Z0-9\s\-_()[\]{}]{1,100}$/.test(p.name.trim())) return false;

    // Validate path format (basic path validation, no dangerous characters)
    if (!/^[a-zA-Z0-9\s\-_./\\:(){}[\]]{1,500}$/.test(p.path.trim())) return false;
    if (p.path.includes('..') || p.path.includes('<') || p.path.includes('>')) return false;

    // Validate date fields (can be missing, but if present must be valid ISO strings)
    if (p.createdAt !== undefined) {
      if (typeof p.createdAt !== 'string' || isNaN(Date.parse(p.createdAt))) return false;
      // Ensure date is not in the future (with 5 minute tolerance for clock skew)
      const createdDate = new Date(p.createdAt);
      if (createdDate.getTime() > Date.now() + 5 * 60 * 1000) return false;
    }
    if (p.lastActive !== undefined) {
      if (typeof p.lastActive !== 'string' || isNaN(Date.parse(p.lastActive))) return false;
      // Ensure date is not in the future (with 5 minute tolerance for clock skew)
      const lastActiveDate = new Date(p.lastActive);
      if (lastActiveDate.getTime() > Date.now() + 5 * 60 * 1000) return false;
    }

    // Validate field lengths against constants with more strict validation
    if (p.name.trim().length > 100 || p.path.trim().length > 500) return false;
    if (p.id.trim().length > 50 || p.serverId.trim().length > 50) return false;

    // Ensure no unexpected properties that could indicate corruption
    const allowedKeys = ['id', 'name', 'path', 'serverId', 'createdAt', 'lastActive'];
    const projectKeys = Object.keys(p);
    for (const key of projectKeys) {
      if (!allowedKeys.includes(key)) {
        console.warn(`Unknown property '${key}' found in project data`);
        return false;
      }
    }

    return true;
  }

  private isValidServer(server: unknown): server is Server {
    if (!server || typeof server !== 'object') return false;

    const s = server as Partial<Server>;

    // Validate required string fields with comprehensive checks
    if (!s.id || typeof s.id !== 'string' || s.id.trim().length === 0) return false;
    if (!s.name || typeof s.name !== 'string' || s.name.trim().length === 0) return false;
    if (!s.websocketUrl || typeof s.websocketUrl !== 'string' || s.websocketUrl.trim().length === 0)
      return false;

    // Validate ID format (should be UUID-like or valid identifier)
    if (!/^[a-zA-Z0-9_-]{1,50}$/.test(s.id.trim())) return false;

    // Validate server name contains only safe characters
    if (!/^[a-zA-Z0-9\s\-_.]{1,50}$/.test(s.name.trim())) return false;

    // Validate field lengths against constants with strict validation
    if (s.name.trim().length > 50) return false;
    if (s.websocketUrl.trim().length < 5 || s.websocketUrl.trim().length > 200) return false;
    if (s.id.trim().length > 50) return false;

    // Enhanced WebSocket URL validation
    const wsUrl = s.websocketUrl.trim();

    // Allow relative URLs starting with /
    if (wsUrl.startsWith('/')) {
      // Validate relative URL format
      if (!/^\/[a-zA-Z0-9\-._~:/?#[\]@!$&'()*+,;=%]*$/.test(wsUrl)) return false;
    } else {
      // Validate full WebSocket URLs
      if (!wsUrl.startsWith('ws://') && !wsUrl.startsWith('wss://')) return false;

      try {
        const url = new URL(wsUrl);
        // Ensure protocol is WebSocket
        if (url.protocol !== 'ws:' && url.protocol !== 'wss:') return false;

        // Validate hostname format (basic check)
        if (!url.hostname || url.hostname.length === 0) return false;
        if (!/^[a-zA-Z0-9.-]+$/.test(url.hostname)) return false;

        // Validate port if present
        if (
          url.port &&
          (isNaN(parseInt(url.port)) || parseInt(url.port) < 1 || parseInt(url.port) > 65535)
        ) {
          return false;
        }

        // Block localhost in production (this is a basic security check)
        if (
          typeof window !== 'undefined' &&
          window.location.protocol === 'https:' &&
          (url.hostname === 'localhost' ||
            url.hostname === '127.0.0.1' ||
            url.hostname.startsWith('192.168.'))
        ) {
          console.warn('Localhost WebSocket URLs not allowed in HTTPS context');
          return false;
        }
      } catch {
        return false;
      }
    }

    // Validate isConnected field if present (must be boolean)
    if (s.isConnected !== undefined && typeof s.isConnected !== 'boolean') return false;

    // Ensure no unexpected properties that could indicate corruption
    const allowedKeys = ['id', 'name', 'websocketUrl', 'isConnected'];
    const serverKeys = Object.keys(s);
    for (const key of serverKeys) {
      if (!allowedKeys.includes(key)) {
        console.warn(`Unknown property '${key}' found in server data`);
        return false;
      }
    }

    return true;
  }

  /**
   * Utility methods
   */
  getStorageUsage(): { used: number; available: number } {
    try {
      let used = 0;
      for (const key in localStorage) {
        if (Object.prototype.hasOwnProperty.call(localStorage, key)) {
          used += localStorage[key].length + key.length;
        }
      }

      // Most browsers have ~5MB limit, but we can't detect exact amount
      const estimatedLimit = 5 * 1024 * 1024; // 5MB
      return {
        used,
        available: estimatedLimit - used,
      };
    } catch {
      return { used: 0, available: 0 };
    }
  }

  clearAllData(): void {
    try {
      Object.values(STORAGE_KEYS).forEach(key => {
        localStorage.removeItem(key);
      });
    } catch (error) {
      console.error('Failed to clear storage data:', error);
    }
  }

  exportData(): string {
    try {
      const data = {
        projects: this.getProjects(),
        servers: this.getServers(),
        theme: this.getTheme(),
        version: CURRENT_VERSION,
        exportedAt: new Date().toISOString(),
      };
      return JSON.stringify(data, null, 2);
    } catch (error) {
      throw new StorageError('Failed to export data', 'PARSE_ERROR', error as Error);
    }
  }

  importData(jsonString: string): { success: boolean; message: string } {
    try {
      const data = JSON.parse(jsonString);

      if (!data || typeof data !== 'object') {
        return { success: false, message: 'Invalid data format' };
      }

      // Validate and import projects
      if (data.projects && Array.isArray(data.projects)) {
        this.setProjects(data.projects);
      }

      // Validate and import servers
      if (data.servers && Array.isArray(data.servers)) {
        this.setServers(data.servers);
      }

      // Import theme
      if (data.theme && ['light', 'dark', 'system'].includes(data.theme)) {
        this.setTheme(data.theme);
      }

      return { success: true, message: 'Data imported successfully' };
    } catch (error) {
      return {
        success: false,
        message: `Failed to import data: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  }
}

// Export singleton instance
export const localStorageService = LocalStorageService.getInstance();
