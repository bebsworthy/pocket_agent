/**
 * React hooks for localStorage integration with Jotai state management
 * 
 * Provides type-safe hooks for:
 - Project persistence and management
 - Server persistence and management
 - Theme persistence
 - Storage error handling
 - Storage quota monitoring
 */

import { useCallback, useEffect, useState, useRef, useMemo } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import { localStorageService, StorageError } from './LocalStorageService';
import { Project, Server } from '@/types/models';
import { useErrorBoundary } from '@/components/ErrorBoundary';
import { createRateLimit } from '@/utils/sanitize';

// Import atoms from proper state management structure
import {
  projectsAtom,
  serversAtom,
  themeAtom,
  storageStatusAtom,
  errorAtom,
} from '@/store/atoms';

// Rate limiting for storage operations
const storageRateLimit = createRateLimit(20, 60000); // 20 operations per minute

/**
 * Helper function to check rate limit and handle rate limit exceeded errors
 */
function checkStorageRateLimit(operation: string): boolean {
  const allowed = storageRateLimit('storage');
  if (!allowed) {
    console.warn(`Storage operation rate limit exceeded for: ${operation}`);
    throw new StorageError(
      'Too many storage operations. Please wait a moment before trying again.',
      'UNKNOWN'
    );
  }
  return true;
}

/**
 * Hook for managing projects with localStorage persistence
 */
export function useProjectStorage() {
  const [projects, setProjects] = useAtom(projectsAtom);
  const setStorageError = useSetAtom(errorAtom);
  const [isLoading, setIsLoading] = useState(false);
  const triggerErrorBoundary = useErrorBoundary();
  
  // Memoize projects array to prevent unnecessary re-renders
  const memoizedProjects = useMemo(() => projects, [projects]);

  // Load projects from storage on mount
  useEffect(() => {
    const loadProjects = async () => {
      setIsLoading(true);
      try {
        const storedProjects = localStorageService.getProjects();
        setProjects(storedProjects);
      } catch (error) {
        console.error('Failed to load projects:', error);
        if (error instanceof StorageError) {
          // For critical storage errors, trigger error boundary
          if (error.code === 'ACCESS_DENIED' || error.code === 'CORRUPTED_DATA') {
            triggerErrorBoundary(new Error(`Storage Error: ${error.message}`));
            return;
          }
          
          setStorageError({
            message: error.message,
            level: 'error' as const,
            announceToScreenReader: true,
            context: 'project-storage',
            timestamp: new Date().toISOString()
          });
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadProjects();
  }, [setProjects, setStorageError]);

  // Debounced save projects to storage whenever they change
  const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  useEffect(() => {
    if (memoizedProjects.length === 0 && isLoading) return; // Skip save during initial load
    
    // Clear existing timeout
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }
    
    // Debounce the save operation to prevent excessive I/O
    saveTimeoutRef.current = setTimeout(() => {
      try {
        localStorageService.setProjects(memoizedProjects);
      } catch (error) {
        console.error('Failed to save projects:', error);
        if (error instanceof StorageError) {
          setStorageError({
            message: error.message,
            level: 'error' as const,
            announceToScreenReader: true,
            context: 'project-storage',
            timestamp: new Date().toISOString()
          });
        }
      }
    }, 100); // 100ms debounce
    
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, [memoizedProjects, isLoading, setStorageError]);

  const addProject = useCallback((project: Project) => {
    try {
      checkStorageRateLimit('addProject');
      localStorageService.addProject(project);
      setProjects((prev: Project[]) => {
        const existing = prev.find((p: Project) => p.id === project.id);
        if (existing) {
          return prev.map((p: Project) => p.id === project.id ? project : p);
        }
        return [...prev, project];
      });
    } catch (error) {
      console.error('Failed to add project:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setProjects, setStorageError]);

  const removeProject = useCallback((projectId: string) => {
    try {
      checkStorageRateLimit('removeProject');
      const success = localStorageService.removeProject(projectId);
      if (success) {
        setProjects((prev: Project[]) => prev.filter((p: Project) => p.id !== projectId));
      }
      return success;
    } catch (error) {
      console.error('Failed to remove project:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setProjects, setStorageError]);

  const updateProject = useCallback((projectId: string, updates: Partial<Omit<Project, 'id'>>) => {
    try {
      checkStorageRateLimit('updateProject');
      const success = localStorageService.updateProject(projectId, updates);
      if (success) {
        setProjects((prev: Project[]) => prev.map((p: Project) => 
          p.id === projectId ? { ...p, ...updates } : p
        ));
      }
      return success;
    } catch (error) {
      console.error('Failed to update project:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setProjects, setStorageError]);

  const refreshProjects = useCallback(() => {
    try {
      const storedProjects = localStorageService.getProjects();
      setProjects(storedProjects);
    } catch (error) {
      console.error('Failed to refresh projects:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
    }
  }, [setProjects, setStorageError]);

  return {
    projects: memoizedProjects,
    isLoading,
    addProject,
    removeProject,
    updateProject,
    refreshProjects,
  };
}

/**
 * Hook for managing servers with localStorage persistence
 */
export function useServerStorage() {
  const [servers, setServers] = useAtom(serversAtom);
  const setStorageError = useSetAtom(errorAtom);
  const [isLoading, setIsLoading] = useState(false);
  const triggerErrorBoundary = useErrorBoundary();
  
  // Memoized servers array to prevent unnecessary re-renders
  const memoizedServers = useMemo(() => servers, [servers]);

  // Load servers from storage on mount
  useEffect(() => {
    const loadServers = async () => {
      setIsLoading(true);
      try {
        const storedServers = localStorageService.getServers();
        setServers(storedServers);
      } catch (error) {
        console.error('Failed to load servers:', error);
        if (error instanceof StorageError) {
          // For critical storage errors, trigger error boundary
          if (error.code === 'ACCESS_DENIED' || error.code === 'CORRUPTED_DATA') {
            triggerErrorBoundary(new Error(`Storage Error: ${error.message}`));
            return;
          }
          
          setStorageError({
            message: error.message,
            level: 'error' as const,
            announceToScreenReader: true,
            context: 'server-storage',
            timestamp: new Date().toISOString()
          });
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadServers();
  }, [setServers, setStorageError]);

  // Debounced save servers to storage whenever they change
  const serverSaveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  
  useEffect(() => {
    if (servers.length === 0 && isLoading) return; // Skip save during initial load
    
    // Clear existing timeout
    if (serverSaveTimeoutRef.current) {
      clearTimeout(serverSaveTimeoutRef.current);
    }
    
    // Debounce the save operation to prevent excessive I/O
    serverSaveTimeoutRef.current = setTimeout(() => {
      try {
        localStorageService.setServers(servers);
      } catch (error) {
        console.error('Failed to save servers:', error);
        if (error instanceof StorageError) {
          setStorageError({
            message: error.message,
            level: 'error' as const,
            announceToScreenReader: true,
            context: 'server-storage',
            timestamp: new Date().toISOString()
          });
        }
      }
    }, 100); // 100ms debounce
    
    return () => {
      if (serverSaveTimeoutRef.current) {
        clearTimeout(serverSaveTimeoutRef.current);
      }
    };
  }, [servers, isLoading, setStorageError]);

  const addServer = useCallback((server: Server) => {
    try {
      checkStorageRateLimit('addServer');
      localStorageService.addServer(server);
      setServers((prev: Server[]) => {
        const existing = prev.find((s: Server) => s.id === server.id);
        if (existing) {
          return prev.map((s: Server) => s.id === server.id ? server : s);
        }
        return [...prev, server];
      });
    } catch (error) {
      console.error('Failed to add server:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setServers, setStorageError]);

  const removeServer = useCallback((serverId: string) => {
    try {
      checkStorageRateLimit('removeServer');
      const success = localStorageService.removeServer(serverId);
      if (success) {
        setServers((prev: Server[]) => prev.filter((s: Server) => s.id !== serverId));
      }
      return success;
    } catch (error) {
      console.error('Failed to remove server:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setServers, setStorageError]);

  const updateServer = useCallback((serverId: string, updates: Partial<Omit<Server, 'id'>>) => {
    try {
      checkStorageRateLimit('updateServer');
      const success = localStorageService.updateServer(serverId, updates);
      if (success) {
        setServers((prev: Server[]) => prev.map((s: Server) => 
          s.id === serverId ? { ...s, ...updates } : s
        ));
      }
      return success;
    } catch (error) {
      console.error('Failed to update server:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setServers, setStorageError]);

  const refreshServers = useCallback(() => {
    try {
      const storedServers = localStorageService.getServers();
      setServers(storedServers);
    } catch (error) {
      console.error('Failed to refresh servers:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
    }
  }, [setServers, setStorageError]);

  return {
    servers,
    isLoading,
    addServer,
    removeServer,
    updateServer,
    refreshServers,
  };
}

/**
 * Hook for theme persistence
 */
export function useThemeStorage() {
  const [theme, setTheme] = useAtom(themeAtom);
  const setStorageError = useSetAtom(errorAtom);

  // Load theme from storage on mount
  useEffect(() => {
    try {
      const storedTheme = localStorageService.getTheme();
      setTheme(storedTheme);
    } catch (error) {
      console.error('Failed to load theme:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
    }
  }, [setTheme, setStorageError]);

  const updateTheme = useCallback((newTheme: 'light' | 'dark' | 'system') => {
    try {
      localStorageService.setTheme(newTheme);
      setTheme(newTheme);
    } catch (error) {
      console.error('Failed to save theme:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      throw error;
    }
  }, [setTheme, setStorageError]);

  return {
    theme,
    updateTheme,
  };
}

/**
 * Hook for storage error handling
 */
export function useStorageError() {
  const [storageError, setStorageError] = useAtom(errorAtom);

  const clearError = useCallback(() => {
    setStorageError(null);
  }, [setStorageError]);

  const handleStorageError = useCallback((error: StorageError) => {
    setStorageError({
      message: error.message,
      level: 'error' as const,
      announceToScreenReader: true,
      context: 'storage-service',
      timestamp: new Date().toISOString()
    });
    
    // Auto-clear error after 10 seconds unless it's a quota error
    if (error.code !== 'QUOTA_EXCEEDED') {
      setTimeout(() => {
        setStorageError(null);
      }, 10000);
    }
  }, [setStorageError]);

  return {
    storageError,
    clearError,
    handleStorageError,
  };
}

/**
 * Hook for monitoring storage status and usage
 */
export function useStorageStatus() {
  const [status, setStatus] = useAtom(storageStatusAtom);

  const updateStorageUsage = useCallback(() => {
    try {
      const storageUsage = localStorageService.getStorageUsage();
      setStatus((prev: { available: number; used: number; quotaExceeded: boolean; lastUpdated: string }) => ({
        ...prev,
        available: storageUsage.available,
        used: storageUsage.used,
        quotaExceeded: storageUsage.available < 0,
        lastUpdated: new Date().toISOString(),
      }));
    } catch (error) {
      console.error('Failed to get storage usage:', error);
    }
  }, [setStatus]);

  // Update usage on mount and periodically
  useEffect(() => {
    updateStorageUsage();
    
    const interval = setInterval(updateStorageUsage, 30000); // Every 30 seconds
    return () => clearInterval(interval);
  }, [updateStorageUsage]);

  const isStorageFull = status.available < 100 * 1024; // Less than 100KB available

  return {
    ...status,
    isStorageFull,
    updateStorageUsage,
  };
}

/**
 * Hook for data export/import functionality
 */
export function useStorageBackup() {
  const [isExporting, setIsExporting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const setStorageError = useSetAtom(errorAtom);

  const exportData = useCallback(async (): Promise<string | null> => {
    setIsExporting(true);
    try {
      const exportedData = localStorageService.exportData();
      return exportedData;
    } catch (error) {
      console.error('Failed to export data:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
      return null;
    } finally {
      setIsExporting(false);
    }
  }, [setStorageError]);

  const importData = useCallback(async (jsonString: string): Promise<{ success: boolean; message: string }> => {
    setIsImporting(true);
    try {
      const result = localStorageService.importData(jsonString);
      return result;
    } catch (error) {
      console.error('Failed to import data:', error);
      return {
        success: false,
        message: error instanceof Error ? error.message : 'Unknown error occurred',
      };
    } finally {
      setIsImporting(false);
    }
  }, []);

  const clearAllData = useCallback(() => {
    try {
      localStorageService.clearAllData();
    } catch (error) {
      console.error('Failed to clear data:', error);
      if (error instanceof StorageError) {
        setStorageError({
          message: error.message,
          level: 'error' as const,
          announceToScreenReader: true,
          context: 'storage-operation',
          timestamp: new Date().toISOString()
        });
      }
    }
  }, [setStorageError]);

  return {
    isExporting,
    isImporting,
    exportData,
    importData,
    clearAllData,
  };
}

/**
 * Main storage hook that combines all storage functionality
 */
export function useStorage() {
  const projects = useProjectStorage();
  const servers = useServerStorage();
  const theme = useThemeStorage();
  const error = useStorageError();
  const status = useStorageStatus();
  const backup = useStorageBackup();

  return {
    projects,
    servers,
    theme,
    error,
    status,
    backup,
  };
}