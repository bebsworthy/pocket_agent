/**
 * Validation script to ensure localStorage service works correctly
 * This can be run during development to validate the implementation
 */

import { localStorageService } from './LocalStorageService';
import type { Project, Server } from '../../types/models';

export function validateLocalStorageService(): { success: boolean; errors: string[] } {
  const errors: string[] = [];

  try {
    // Test 1: Basic service instantiation
    console.log('✓ Service instantiation successful');

    // Test 2: Projects CRUD
    const testProject: Project = {
      id: 'validation-project',
      name: 'Validation Project',
      path: '/test/path',
      serverId: 'test-server',
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString(),
    };

    // Add project
    localStorageService.addProject(testProject);
    const projects = localStorageService.getProjects();
    
    if (projects.length === 0 || projects[0].id !== testProject.id) {
      errors.push('Failed to add/retrieve project');
    } else {
      console.log('✓ Project add/retrieve successful');
    }

    // Update project
    const updated = localStorageService.updateProject(testProject.id, { 
      name: 'Updated Name' 
    });
    
    if (!updated) {
      errors.push('Failed to update project');
    } else {
      const updatedProjects = localStorageService.getProjects();
      if (updatedProjects[0].name !== 'Updated Name') {
        errors.push('Project update did not persist');
      } else {
        console.log('✓ Project update successful');
      }
    }

    // Remove project
    const removed = localStorageService.removeProject(testProject.id);
    if (!removed || localStorageService.getProjects().length > 0) {
      errors.push('Failed to remove project');
    } else {
      console.log('✓ Project removal successful');
    }

    // Test 3: Servers CRUD
    const testServer: Server = {
      id: 'validation-server',
      name: 'Test Server',
      websocketUrl: 'ws://localhost:8080',
      isConnected: false,
    };

    localStorageService.addServer(testServer);
    const servers = localStorageService.getServers();
    
    if (servers.length === 0 || servers[0].id !== testServer.id) {
      errors.push('Failed to add/retrieve server');
    } else {
      console.log('✓ Server add/retrieve successful');
    }

    localStorageService.removeServer(testServer.id);
    if (localStorageService.getServers().length > 0) {
      errors.push('Failed to remove server');
    } else {
      console.log('✓ Server removal successful');
    }

    // Test 4: Theme management
    localStorageService.setTheme('dark');
    const theme = localStorageService.getTheme();
    if (theme !== 'dark') {
      errors.push('Failed to set/get theme');
    } else {
      console.log('✓ Theme management successful');
    }

    // Test 5: Storage utilities
    const usage = localStorageService.getStorageUsage();
    if (typeof usage.used !== 'number' || typeof usage.available !== 'number') {
      errors.push('Storage usage calculation failed');
    } else {
      console.log('✓ Storage usage calculation successful');
    }

    // Test 6: Export/Import
    localStorageService.addProject(testProject);
    localStorageService.addServer(testServer);
    
    const exportedData = localStorageService.exportData();
    const parsed = JSON.parse(exportedData);
    
    if (!parsed.projects || !parsed.servers || !parsed.theme) {
      errors.push('Data export incomplete');
    } else {
      console.log('✓ Data export successful');
    }

    // Clear test data
    localStorageService.clearAllData();
    
    const importResult = localStorageService.importData(exportedData);
    if (!importResult.success) {
      errors.push(`Data import failed: ${importResult.message}`);
    } else {
      console.log('✓ Data import successful');
    }

    // Final cleanup
    localStorageService.clearAllData();
    console.log('✓ Cleanup completed');

  } catch (error) {
    errors.push(`Unexpected error: ${error instanceof Error ? error.message : String(error)}`);
  }

  return {
    success: errors.length === 0,
    errors,
  };
}

// Run validation if this file is executed directly
if (typeof window !== 'undefined') {
  const result = validateLocalStorageService();
  
  if (result.success) {
    console.log('🎉 All localStorage service validations passed!');
  } else {
    console.error('❌ Validation failed:');
    result.errors.forEach(error => console.error(`  - ${error}`));
  }
}