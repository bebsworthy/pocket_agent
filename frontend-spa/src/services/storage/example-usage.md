# LocalStorage Service Usage Examples

This document provides examples of how to use the LocalStorage service and React hooks in the frontend-spa application.

## Basic Service Usage

```typescript
import { localStorageService } from '@/services/storage/LocalStorageService';
import { Project, Server } from '@/types/models';

// Create a new project
const newProject: Project = {
  id: 'project-1',
  name: 'My Project',
  path: '/home/user/projects/my-project',
  serverId: 'server-1',
  createdAt: new Date().toISOString(),
  lastActive: new Date().toISOString(),
};

// Add project to storage
localStorageService.addProject(newProject);

// Get all projects
const projects = localStorageService.getProjects();

// Update project
localStorageService.updateProject('project-1', {
  name: 'Updated Project Name',
  lastActive: new Date().toISOString(),
});

// Remove project
localStorageService.removeProject('project-1');
```

## React Hook Usage

### Project Management

```typescript
import { useProjectStorage } from '@/services/storage/hooks';

function ProjectManager() {
  const {
    projects,
    isLoading,
    addProject,
    removeProject,
    updateProject
  } = useProjectStorage();

  const handleAddProject = () => {
    const newProject: Project = {
      id: crypto.randomUUID(),
      name: 'New Project',
      path: '/path/to/project',
      serverId: 'default-server',
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString(),
    };

    try {
      addProject(newProject);
    } catch (error) {
      console.error('Failed to add project:', error);
    }
  };

  const handleUpdateLastActive = (projectId: string) => {
    updateProject(projectId, {
      lastActive: new Date().toISOString(),
    });
  };

  if (isLoading) {
    return <div>Loading projects...</div>;
  }

  return (
    <div>
      <button onClick={handleAddProject}>Add Project</button>
      {projects.map(project => (
        <div key={project.id}>
          <h3>{project.name}</h3>
          <p>{project.path}</p>
          <button onClick={() => handleUpdateLastActive(project.id)}>
            Update Last Active
          </button>
          <button onClick={() => removeProject(project.id)}>
            Delete
          </button>
        </div>
      ))}
    </div>
  );
}
```

### Server Management

```typescript
import { useServerStorage } from '@/services/storage/hooks';

function ServerManager() {
  const {
    servers,
    isLoading,
    addServer,
    removeServer,
    updateServer
  } = useServerStorage();

  const handleAddServer = () => {
    const newServer: Server = {
      id: crypto.randomUUID(),
      name: 'Local Development',
      websocketUrl: 'ws://localhost:8080',
      isConnected: false,
    };

    try {
      addServer(newServer);
    } catch (error) {
      console.error('Failed to add server:', error);
    }
  };

  const handleToggleConnection = (serverId: string, isConnected: boolean) => {
    updateServer(serverId, { isConnected });
  };

  return (
    <div>
      <button onClick={handleAddServer}>Add Server</button>
      {servers.map(server => (
        <div key={server.id}>
          <h3>{server.name}</h3>
          <p>{server.websocketUrl}</p>
          <span>Status: {server.isConnected ? 'Connected' : 'Disconnected'}</span>
          <button
            onClick={() => handleToggleConnection(server.id, !server.isConnected)}
          >
            {server.isConnected ? 'Disconnect' : 'Connect'}
          </button>
        </div>
      ))}
    </div>
  );
}
```

### Theme Management

```typescript
import { useThemeStorage } from '@/services/storage/hooks';

function ThemeSelector() {
  const { theme, updateTheme } = useThemeStorage();

  return (
    <div>
      <h3>Current theme: {theme}</h3>
      <button onClick={() => updateTheme('light')}>Light</button>
      <button onClick={() => updateTheme('dark')}>Dark</button>
      <button onClick={() => updateTheme('system')}>System</button>
    </div>
  );
}
```

### Error Handling

```typescript
import { useStorageError } from '@/services/storage/hooks';

function StorageErrorHandler() {
  const { storageError, clearError } = useStorageError();

  if (!storageError) {
    return null;
  }

  const getErrorMessage = () => {
    switch (storageError.code) {
      case 'QUOTA_EXCEEDED':
        return 'Storage is full. Please clear some data to continue.';
      case 'ACCESS_DENIED':
        return 'Cannot access browser storage. Please check your browser settings.';
      case 'CORRUPTED_DATA':
        return 'Some stored data is corrupted and has been reset.';
      default:
        return 'An storage error occurred. Please try refreshing the page.';
    }
  };

  return (
    <div className="error-banner">
      <p>{getErrorMessage()}</p>
      <button onClick={clearError}>Dismiss</button>
    </div>
  );
}
```

### Storage Status Monitoring

```typescript
import { useStorageStatus } from '@/services/storage/hooks';

function StorageStatus() {
  const { usage, isStorageFull, lastSync } = useStorageStatus();

  const usagePercentage = (usage.used / (usage.used + usage.available)) * 100;

  return (
    <div>
      <h3>Storage Status</h3>
      <div>
        <p>Used: {Math.round(usage.used / 1024)} KB</p>
        <p>Available: {Math.round(usage.available / 1024)} KB</p>
        <div className="progress-bar">
          <div
            className="progress-fill"
            style={{ width: `${usagePercentage}%` }}
          />
        </div>
        {isStorageFull && (
          <p className="warning">Storage is nearly full!</p>
        )}
      </div>
      {lastSync && (
        <p>Last updated: {lastSync.toLocaleString()}</p>
      )}
    </div>
  );
}
```

### Data Backup and Import

```typescript
import { useStorageBackup } from '@/services/storage/hooks';

function DataManager() {
  const {
    isExporting,
    isImporting,
    exportData,
    importData,
    clearAllData
  } = useStorageBackup();

  const handleExport = async () => {
    const data = await exportData();
    if (data) {
      // Create download link
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `pocket-agent-backup-${new Date().toISOString().split('T')[0]}.json`;
      a.click();
      URL.revokeObjectURL(url);
    }
  };

  const handleImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const text = await file.text();
    const result = await importData(text);

    if (result.success) {
      alert('Data imported successfully!');
      window.location.reload(); // Refresh to load new data
    } else {
      alert(`Import failed: ${result.message}`);
    }
  };

  const handleClearAll = () => {
    if (confirm('Are you sure you want to clear all data? This cannot be undone.')) {
      clearAllData();
      window.location.reload();
    }
  };

  return (
    <div>
      <h3>Data Management</h3>

      <button onClick={handleExport} disabled={isExporting}>
        {isExporting ? 'Exporting...' : 'Export Data'}
      </button>

      <label>
        Import Data:
        <input
          type="file"
          accept=".json"
          onChange={handleImport}
          disabled={isImporting}
        />
      </label>

      <button onClick={handleClearAll} className="danger">
        Clear All Data
      </button>
    </div>
  );
}
```

### Combined Usage with All Features

```typescript
import { useStorage } from '@/services/storage/hooks';

function App() {
  const {
    projects,
    servers,
    theme,
    error,
    status,
    backup,
  } = useStorage();

  return (
    <div className={`app theme-${theme.theme}`}>
      {/* Error handling */}
      {error.storageError && (
        <div className="error-banner">
          {error.storageError.message}
          <button onClick={error.clearError}>×</button>
        </div>
      )}

      {/* Storage status warning */}
      {status.isStorageFull && (
        <div className="warning-banner">
          Storage is nearly full! Consider exporting and clearing old data.
        </div>
      )}

      {/* Main app content */}
      <main>
        <ProjectList projects={projects.projects} />
        <ServerList servers={servers.servers} />
      </main>

      {/* Settings */}
      <aside>
        <ThemeSelector
          currentTheme={theme.theme}
          onThemeChange={theme.updateTheme}
        />
        <DataManager
          onExport={backup.exportData}
          onImport={backup.importData}
          onClear={backup.clearAllData}
        />
      </aside>
    </div>
  );
}
```

## Error Handling Best Practices

1. **Always wrap storage operations in try-catch blocks**
2. **Use the error handling hook to display user-friendly messages**
3. **Monitor storage usage to prevent quota exceeded errors**
4. **Provide data export functionality before clearing storage**
5. **Handle corrupted data gracefully with defaults**

## Performance Considerations

1. **Storage hooks automatically debounce writes to prevent excessive localStorage calls**
2. **Use the combined `useStorage` hook when you need multiple storage features**
3. **Storage usage is monitored automatically every 30 seconds**
4. **Data validation happens on read operations, not on every state change**
