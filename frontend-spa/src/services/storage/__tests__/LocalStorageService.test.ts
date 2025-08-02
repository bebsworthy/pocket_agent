/**
 * Tests for LocalStorageService
 *
 * This is a basic validation test to ensure the service works correctly.
 * More comprehensive testing will be added later in the testing phase.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LocalStorageService } from '../LocalStorageService';
import { Project, Server } from '@/types/models';

// Mock localStorage for testing
const mockLocalStorage = (() => {
  let store: Record<string, string> = {};

  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((index: number) => Object.keys(store)[index] || null),
  };
})();

// Replace global localStorage with mock
Object.defineProperty(window, 'localStorage', {
  value: mockLocalStorage,
  writable: true,
});

describe('LocalStorageService', () => {
  let service: LocalStorageService;

  beforeEach(() => {
    // Clear mock localStorage before each test
    mockLocalStorage.clear();
    vi.clearAllMocks();

    // Get fresh instance (singleton will be reused but storage is cleared)
    service = LocalStorageService.getInstance();
  });

  describe('Projects management', () => {
    const mockProject: Project = {
      id: 'test-project-1',
      name: 'Test Project',
      path: '/path/to/project',
      serverId: 'test-server-1',
      createdAt: '2024-01-01T00:00:00.000Z',
      lastActive: '2024-01-01T00:00:00.000Z',
    };

    it('should return empty array when no projects stored', () => {
      const projects = service.getProjects();
      expect(projects).toEqual([]);
    });

    it('should store and retrieve projects', () => {
      service.setProjects([mockProject]);
      const projects = service.getProjects();
      expect(projects).toEqual([mockProject]);
    });

    it('should add a new project', () => {
      service.addProject(mockProject);
      const projects = service.getProjects();
      expect(projects).toHaveLength(1);
      expect(projects[0]).toEqual(mockProject);
    });

    it('should update existing project when adding with same ID', () => {
      const updatedProject = { ...mockProject, name: 'Updated Name' };

      service.addProject(mockProject);
      service.addProject(updatedProject);

      const projects = service.getProjects();
      expect(projects).toHaveLength(1);
      expect(projects[0].name).toBe('Updated Name');
    });

    it('should remove project by ID', () => {
      service.addProject(mockProject);
      const removed = service.removeProject(mockProject.id);

      expect(removed).toBe(true);
      expect(service.getProjects()).toHaveLength(0);
    });

    it('should return false when removing non-existent project', () => {
      const removed = service.removeProject('non-existent');
      expect(removed).toBe(false);
    });

    it('should update project fields', () => {
      service.addProject(mockProject);
      const updated = service.updateProject(mockProject.id, {
        name: 'New Name',
        lastActive: '2024-02-01T00:00:00.000Z',
      });

      expect(updated).toBe(true);
      const projects = service.getProjects();
      expect(projects[0].name).toBe('New Name');
      expect(projects[0].lastActive).toBe('2024-02-01T00:00:00.000Z');
      expect(projects[0].id).toBe(mockProject.id); // ID should not change
    });
  });

  describe('Servers management', () => {
    const mockServer: Server = {
      id: 'test-server-1',
      name: 'Test Server',
      websocketUrl: 'ws://localhost:8080',
      isConnected: false,
    };

    it('should return empty array when no servers stored', () => {
      const servers = service.getServers();
      expect(servers).toEqual([]);
    });

    it('should store and retrieve servers', () => {
      service.setServers([mockServer]);
      const servers = service.getServers();
      expect(servers).toEqual([mockServer]);
    });

    it('should add a new server', () => {
      service.addServer(mockServer);
      const servers = service.getServers();
      expect(servers).toHaveLength(1);
      expect(servers[0]).toEqual(mockServer);
    });

    it('should always set isConnected to false when loading servers', () => {
      const connectedServer = { ...mockServer, isConnected: true };
      service.setServers([connectedServer]);

      const servers = service.getServers();
      expect(servers[0].isConnected).toBe(false);
    });
  });

  describe('Theme management', () => {
    it('should return system as default theme', () => {
      const theme = service.getTheme();
      expect(theme).toBe('system');
    });

    it('should store and retrieve theme', () => {
      service.setTheme('dark');
      const theme = service.getTheme();
      expect(theme).toBe('dark');
    });
  });

  describe('Error handling', () => {
    it('should handle corrupted project data gracefully', () => {
      // Manually set corrupted data
      mockLocalStorage.setItem('pocket_agent_projects', 'invalid json');

      const projects = service.getProjects();
      expect(projects).toEqual([]);
    });

    it('should handle invalid project structure', () => {
      // Set data with missing required fields
      const invalidData = {
        version: '1.0.0',
        timestamp: Date.now(),
        data: [{ id: 'test', name: 'Test' }], // Missing required fields
      };

      mockLocalStorage.setItem('pocket_agent_projects', JSON.stringify(invalidData));

      const projects = service.getProjects();
      expect(projects).toEqual([]);
    });
  });

  describe('Data validation', () => {
    it('should validate project structure and add missing timestamps', () => {
      const projectWithoutTimestamps = {
        id: 'test',
        name: 'Test',
        path: '/path',
        serverId: 'server-1',
      };

      const data = {
        version: '1.0.0',
        timestamp: Date.now(),
        data: [projectWithoutTimestamps],
      };

      mockLocalStorage.setItem('pocket_agent_projects', JSON.stringify(data));

      const projects = service.getProjects();
      expect(projects).toHaveLength(1);
      expect(projects[0].createdAt).toBeDefined();
      expect(projects[0].lastActive).toBeDefined();
    });
  });

  describe('Storage utilities', () => {
    it('should return storage usage information', () => {
      service.addProject({
        id: 'test',
        name: 'Test Project',
        path: '/path',
        serverId: 'server-1',
        createdAt: '2024-01-01T00:00:00.000Z',
        lastActive: '2024-01-01T00:00:00.000Z',
      });

      const usage = service.getStorageUsage();
      expect(usage.used).toBeGreaterThan(0);
      expect(usage.available).toBeDefined();
    });

    it('should clear all data', () => {
      service.addProject({
        id: 'test',
        name: 'Test',
        path: '/path',
        serverId: 'server-1',
        createdAt: '2024-01-01T00:00:00.000Z',
        lastActive: '2024-01-01T00:00:00.000Z',
      });
      service.setTheme('dark');

      service.clearAllData();

      expect(service.getProjects()).toEqual([]);
      expect(service.getTheme()).toBe('system'); // Should return default
    });

    it('should export data as JSON string', () => {
      const project = {
        id: 'test',
        name: 'Test',
        path: '/path',
        serverId: 'server-1',
        createdAt: '2024-01-01T00:00:00.000Z',
        lastActive: '2024-01-01T00:00:00.000Z',
      };

      service.addProject(project);
      service.setTheme('dark');

      const exportedData = service.exportData();
      const parsed = JSON.parse(exportedData);

      expect(parsed.projects).toEqual([project]);
      expect(parsed.theme).toBe('dark');
      expect(parsed.version).toBe('1.0.0');
      expect(parsed.exportedAt).toBeDefined();
    });

    it('should import data successfully', () => {
      const importData = {
        projects: [
          {
            id: 'imported',
            name: 'Imported Project',
            path: '/imported',
            serverId: 'server-1',
            createdAt: '2024-01-01T00:00:00.000Z',
            lastActive: '2024-01-01T00:00:00.000Z',
          },
        ],
        servers: [
          {
            id: 'server-1',
            name: 'Imported Server',
            websocketUrl: 'ws://imported:8080',
            isConnected: false,
          },
        ],
        theme: 'light' as const,
        version: '1.0.0',
        exportedAt: '2024-01-01T00:00:00.000Z',
      };

      const result = service.importData(JSON.stringify(importData));

      expect(result.success).toBe(true);
      expect(service.getProjects()).toHaveLength(1);
      expect(service.getServers()).toHaveLength(1);
      expect(service.getTheme()).toBe('light');
    });

    it('should handle invalid import data', () => {
      const result = service.importData('invalid json');

      expect(result.success).toBe(false);
      expect(result.message).toContain('Failed to import data');
    });
  });
});
