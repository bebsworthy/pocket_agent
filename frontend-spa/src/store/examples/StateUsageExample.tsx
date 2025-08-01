/**
 * Example component demonstrating how to use the new Jotai state management.
 * This file is for documentation purposes and shows usage patterns.
 * Remove this file when the real components are implemented.
 */

import React from 'react';
import { useProjects, useServers, useUI, useWebSocketManager } from '../hooks';

export function StateUsageExample() {
  // Example: Using project state
  const { 
    projects, 
    addProject, 
    selectProject, 
    selectedProject,
    hasProjects 
  } = useProjects();

  // Example: Using server state
  const { 
    servers, 
    addServer, 
    updateConnectionStatus,
    connectedCount 
  } = useServers();

  // Example: Using UI state
  const { 
    theme, 
    toggleTheme, 
    showSuccessToast,
    isDarkMode 
  } = useUI();

  // Example: Using WebSocket state
  const { 
    totalConnections, 
    isAnyConnected
    // Additional methods: getConnectionState, getConnectedServers, disconnectAll
  } = useWebSocketManager();

  const handleAddProject = () => {
    const newProject = addProject({
      name: 'Example Project',
      path: '/path/to/project',
      serverId: 'server-1'
    });
    
    showSuccessToast('Project added successfully!');
    selectProject(newProject.id);
  };

  const handleAddServer = () => {
    const newServer = addServer({
      name: 'Local Server',
      websocketUrl: 'ws://localhost:8080'
    });
    
    // Simulate connection
    updateConnectionStatus(newServer.id, 'connecting');
    setTimeout(() => {
      updateConnectionStatus(newServer.id, 'connected');
    }, 1000);
  };

  return (
    <div className={`p-4 ${isDarkMode ? 'bg-gray-900 text-white' : 'bg-white text-gray-900'}`}>
      <h2 className="text-xl font-bold mb-4">Jotai State Management Example</h2>
      
      <div className="space-y-4">
        <div>
          <h3 className="font-semibold">Theme: {theme}</h3>
          <button 
            onClick={toggleTheme}
            className="px-3 py-1 bg-blue-500 text-white rounded"
          >
            Toggle Theme
          </button>
        </div>

        <div>
          <h3 className="font-semibold">Projects ({projects.length})</h3>
          <p>Has projects: {hasProjects ? 'Yes' : 'No'}</p>
          <p>Selected: {selectedProject?.name || 'None'}</p>
          <button 
            onClick={handleAddProject}
            className="px-3 py-1 bg-green-500 text-white rounded"
          >
            Add Project
          </button>
        </div>

        <div>
          <h3 className="font-semibold">Servers ({servers.length})</h3>
          <p>Connected: {connectedCount}</p>
          <button 
            onClick={handleAddServer}
            className="px-3 py-1 bg-purple-500 text-white rounded"
          >
            Add Server
          </button>
        </div>

        <div>
          <h3 className="font-semibold">WebSocket</h3>
          <p>Total connections: {totalConnections}</p>
          <p>Any connected: {isAnyConnected() ? 'Yes' : 'No'}</p>
        </div>

        <div className="mt-4">
          <h4 className="font-medium">Project List:</h4>
          <ul className="list-disc list-inside">
            {projects.map(project => (
              <li key={project.id} className="text-sm">
                {project.name} (Server: {project.serverId})
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h4 className="font-medium">Server List:</h4>
          <ul className="list-disc list-inside">
            {servers.map(server => (
              <li key={server.id} className="text-sm">
                {server.name} - {server.isConnected ? 'Connected' : 'Disconnected'}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}