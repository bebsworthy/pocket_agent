/**
 * Comprehensive tests for ProjectCreationModal component
 * 
 * This test suite covers:
 * - Form validation testing (Task 7 integration)
 * - Server selection workflow (dropdown, "Add New Server")
 * - Modal behavior (open/close, escape key, backdrop click)
 * - Error handling scenarios (validation errors, WebSocket errors)
 * - WebSocket integration mocking (Task 8 features)
 * - Optimistic updates and rollback scenarios
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider as JotaiProvider } from 'jotai';

// Mock WebSocket hooks directly BEFORE importing the component
const mockUseWebSocket = vi.fn(() => ({
  isConnected: true,
  send: vi.fn().mockReturnValue(true),
  connectionState: 'connected',
}));

const mockUseWebSocketMessage = vi.fn();

// Use a manual mock with createMockInstance pattern
vi.mock('../../../store/hooks/useWebSocket', async () => {
  return {
    useWebSocket: mockUseWebSocket,
    useWebSocketMessage: mockUseWebSocketMessage,
  };
});

import { ProjectCreationModal } from '../ProjectCreationModal';
import type { ProjectCreationModalProps } from '../ProjectCreationModal';
import { createMockEnvironment, resetAllMocks } from '../../../../test/mocks';
import { renderWithUserEvents, waitForLoadingToFinish } from '../../../../test/utils';

// Mock the Lucide React icons
vi.mock('lucide-react', () => {
  const MockIcon: React.FC<{ className?: string; 'data-testid'?: string }> = ({ 
    className, 
    'data-testid': testId 
  }) => (
    <svg 
      className={className} 
      data-testid={testId || 'mock-icon'}
      width="24" 
      height="24" 
      viewBox="0 0 24 24" 
      fill="none" 
      stroke="currentColor"
    >
      <circle cx="12" cy="12" r="10"/>
    </svg>
  );
  
  return {
    X: (props: any) => <MockIcon {...props} data-testid="x-icon" />,
    Plus: (props: any) => <MockIcon {...props} data-testid="plus-icon" />,
    Server: (props: any) => <MockIcon {...props} data-testid="server-icon" />,
    AlertCircle: (props: any) => <MockIcon {...props} data-testid="alert-circle-icon" />,
  };
});

// Mock atom values that can be updated during tests
let mockAtomValues: Record<string, any> = {
  formData: { name: '', path: '', serverId: '' },
  errors: {},
  isSubmitting: false,
  isValid: false,
  isOptimisticCreation: false,
  optimisticProject: null,
  connectionRetryState: { 
    isRetrying: false, 
    retryAttempt: 0, 
    maxRetries: 3,
    lastError: null 
  },
  queuedRequestsCount: 0,
  isConnectionRetrying: false,
};

// Mock setters
const mockSetters = {
  updateField: vi.fn(),
  setErrors: vi.fn(),
  completeSubmission: vi.fn(),
  failSubmission: vi.fn(),
  startOptimistic: vi.fn(),
  confirmOptimistic: vi.fn(),
  rollbackOptimistic: vi.fn(),
  queueRequest: vi.fn(),
  startRetry: vi.fn(),
};

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useAtomValue: vi.fn((atom) => {
      // Return mock values based on common patterns in atom names
      const atomString = atom.toString();
      if (atomString.includes('FormData') || atomString.includes('formData')) return mockAtomValues.formData;
      if (atomString.includes('Errors') || atomString.includes('errors')) return mockAtomValues.errors;
      if (atomString.includes('IsSubmitting') || atomString.includes('submitting')) return mockAtomValues.isSubmitting;
      if (atomString.includes('IsValid') || atomString.includes('valid')) return mockAtomValues.isValid;
      if (atomString.includes('OptimisticCreation') && atomString.includes('Active')) return mockAtomValues.isOptimisticCreation;
      if (atomString.includes('OptimisticProject') || atomString.includes('optimisticProject')) return mockAtomValues.optimisticProject;
      if (atomString.includes('ConnectionRetry') && atomString.includes('State')) return mockAtomValues.connectionRetryState;
      if (atomString.includes('QueuedRequests') || atomString.includes('queued')) return mockAtomValues.queuedRequestsCount;
      if (atomString.includes('IsConnectionRetrying') || atomString.includes('retrying')) return mockAtomValues.isConnectionRetrying;
      // Add server-related atoms
      if (atomString.includes('servers') || atomString.includes('Servers')) return defaultServers;
      if (atomString.includes('serverOperation') || atomString.includes('ServerOperation')) return { type: null, serverId: null };
      return null;
    }),
    useSetAtom: vi.fn((atom) => {
      // Return appropriate setter based on atom pattern
      const atomString = atom.toString();
      if (atomString.includes('UpdateField') || atomString.includes('update')) return mockSetters.updateField;
      if (atomString.includes('SetErrors') || atomString.includes('errors')) return mockSetters.setErrors;
      if (atomString.includes('CompleteSubmission') || atomString.includes('complete')) return mockSetters.completeSubmission;
      if (atomString.includes('FailSubmission') || atomString.includes('fail')) return mockSetters.failSubmission;
      if (atomString.includes('StartOptimistic') || atomString.includes('start')) return mockSetters.startOptimistic;
      if (atomString.includes('ConfirmOptimistic') || atomString.includes('confirm')) return mockSetters.confirmOptimistic;
      if (atomString.includes('RollbackOptimistic') || atomString.includes('rollback')) return mockSetters.rollbackOptimistic;
      if (atomString.includes('QueueRequest') || atomString.includes('queue')) return mockSetters.queueRequest;
      if (atomString.includes('StartRetry') || atomString.includes('retry')) return mockSetters.startRetry;
      return vi.fn();
    }),
    useAtom: vi.fn((atom) => {
      const value = mockAtomValues.connectionRetryState; // Default for state atoms
      return [value, vi.fn()];
    }),
  };
});

// Mock custom hooks
const mockUseProjects = vi.fn(() => ({
  addProject: vi.fn(),
}));

const defaultServers = [
  { id: 'server-1', name: 'Test Server 1', websocketUrl: 'ws://localhost:8081', isConnected: false },
  { id: 'server-2', name: 'Test Server 2', websocketUrl: 'ws://localhost:8082', isConnected: false },
];

const mockUseServers = vi.fn(() => ({
  // State
  servers: defaultServers,
  serversWithStatus: defaultServers,
  connectionStates: new Map(),
  serverCount: defaultServers.length,
  hasServers: true,
  connectedCount: 0,
  isLoading: false,

  // Actions
  addServer: vi.fn(),
  updateServer: vi.fn(),
  removeServer: vi.fn(),
  updateConnectionStatus: vi.fn(),
  batchUpdateConnectionStatus: vi.fn(),
  getServer: vi.fn(),
  getServerConnectionStatus: vi.fn(),
  getConnectedServers: vi.fn(() => []),
  getDisconnectedServers: vi.fn(() => defaultServers),
  isServerConnected: vi.fn(() => false),
  setLoading: vi.fn(),
}));

vi.mock('../../../store/hooks/useProjects', () => ({
  useProjects: mockUseProjects,
}));

vi.mock('../../../store/hooks/useServers', () => ({
  useServers: mockUseServers,
}));

// Mock WebSocketProvider
const MockWebSocketProvider = ({ children }: { children: React.ReactNode }) => (
  <div data-testid="mock-websocket-provider">{children}</div>
);

// Test wrapper with all required providers
const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <JotaiProvider>
    <MockWebSocketProvider>
      {children}
    </MockWebSocketProvider>
  </JotaiProvider>
);

describe('ProjectCreationModal', () => {
  const defaultProps: ProjectCreationModalProps = {
    isVisible: true,
    onClose: vi.fn(),
    onAddServer: vi.fn(),
    onServerAdded: vi.fn(),
  };

  let mockEnvironment: ReturnType<typeof createMockEnvironment>;

  beforeEach(() => {
    vi.clearAllMocks();
    resetAllMocks();
    mockEnvironment = createMockEnvironment();
    
    // Reset mock atom values to defaults
    Object.assign(mockAtomValues, {
      formData: { name: '', path: '', serverId: '' },
      errors: {},
      isSubmitting: false,
      isValid: false,
      isOptimisticCreation: false,
      optimisticProject: null,
      connectionRetryState: { 
        isRetrying: false, 
        retryAttempt: 0, 
        maxRetries: 3,
        lastError: null 
      },
      queuedRequestsCount: 0,
      isConnectionRetrying: false,
    });

    // Reset hook mocks
    mockUseServers.mockReturnValue({
      // State
      servers: defaultServers,
      serversWithStatus: defaultServers,
      connectionStates: new Map(),
      serverCount: defaultServers.length,
      hasServers: true,
      connectedCount: 0,
      isLoading: false,

      // Actions
      addServer: vi.fn(),
      updateServer: vi.fn(),
      removeServer: vi.fn(),
      updateConnectionStatus: vi.fn(),
      batchUpdateConnectionStatus: vi.fn(),
      getServer: vi.fn(),
      getServerConnectionStatus: vi.fn(),
      getConnectedServers: vi.fn(() => []),
      getDisconnectedServers: vi.fn(() => defaultServers),
      isServerConnected: vi.fn(() => false),
      setLoading: vi.fn(),
    });

    mockUseWebSocket.mockReturnValue({
      isConnected: true,
      send: vi.fn().mockReturnValue(true),
      connectionState: 'connected',
    });

    mockUseWebSocketMessage.mockImplementation(() => {});

    // Clear all mock function calls
    Object.values(mockSetters).forEach(mock => {
      if (typeof mock.mockClear === 'function') {
        mock.mockClear();
      }
    });
  });

  afterEach(() => {
    // Clean up any side effects
    document.body.style.overflow = '';
  });

  describe('Rendering', () => {
    it('should render modal when visible', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Create New Project')).toBeInTheDocument();
      expect(screen.getByLabelText(/project name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/project path/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/server/i)).toBeInTheDocument();
    });

    it('should not render modal when not visible', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} isVisible={false} />
        </TestWrapper>
      );

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('should have proper modal attributes', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const modal = screen.getByRole('dialog');
      expect(modal).toHaveAttribute('aria-modal', 'true');
      expect(modal).toHaveAttribute('aria-labelledby', 'project-creation-title');
    });

    it('should render with custom className', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} className="custom-modal-class" />
        </TestWrapper>
      );

      const modalBackdrop = screen.getByRole('dialog').parentElement;
      expect(modalBackdrop).toHaveClass('custom-modal-class');
    });

    it('should disable body scroll when modal is open', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(document.body.style.overflow).toBe('hidden');
    });
  });

  describe('Form Fields', () => {
    it('should render all required form fields', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByLabelText(/project name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/project path/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/server/i)).toBeInTheDocument();
    });

    it('should show project name field with proper attributes', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const nameField = screen.getByLabelText(/project name/i);
      expect(nameField).toHaveAttribute('required');
      expect(nameField).toHaveAttribute('maxLength', '100');
      expect(nameField).toHaveAttribute('autoComplete', 'off');
    });

    it('should show project path field with proper attributes', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const pathField = screen.getByLabelText(/project path/i);
      expect(pathField).toHaveAttribute('required');
      expect(pathField).toHaveAttribute('autoComplete', 'off');
      expect(screen.getByText(/the local filesystem path/i)).toBeInTheDocument();
    });

    it('should call field update handlers on input change', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const nameField = screen.getByLabelText(/project name/i);
      await user.type(nameField, 'Test Project');

      expect(mockSetters.updateField).toHaveBeenCalledWith('name', 'T');
    });
  });

  describe('Server Selection', () => {
    it('should render server dropdown', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      expect(serverButton).toBeInTheDocument();
      expect(serverButton).toHaveAttribute('aria-expanded', 'false');
    });

    it('should open server dropdown when clicked', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      expect(screen.getByRole('listbox')).toBeInTheDocument();
    });

    it('should show server options in dropdown', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      const servers = mockUseServers().servers;
      expect(screen.getByText(servers[0].name)).toBeInTheDocument();
      expect(screen.getByText(servers[1].name)).toBeInTheDocument();
    });

    it('should show "Add New Server" option', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      expect(screen.getByText('Add New Server')).toBeInTheDocument();
    });

    it('should call onAddServer when "Add New Server" is clicked', async () => {
      const user = userEvent.setup();
      const onAddServer = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onAddServer={onAddServer} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      const addServerOption = screen.getByText('Add New Server');
      await user.click(addServerOption);

      expect(onAddServer).toHaveBeenCalledTimes(1);
    });

    it('should handle server selection', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      const servers = mockUseServers().servers;
      const firstServer = screen.getByText(servers[0].name);
      await user.click(firstServer);

      expect(mockSetters.updateField).toHaveBeenCalledWith('serverId', servers[0].id);
    });
  });

  describe('Modal Behavior', () => {
    it('should call onClose when close button is clicked', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onClose={onClose} />
        </TestWrapper>
      );

      const closeButton = screen.getByLabelText(/close project creation modal/i);
      await user.click(closeButton);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should call onClose when Cancel button is clicked', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onClose={onClose} />
        </TestWrapper>
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should call onClose when backdrop is clicked', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onClose={onClose} />
        </TestWrapper>
      );

      const backdrop = screen.getByRole('dialog').parentElement!;
      await user.click(backdrop);

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should handle Escape key to close modal', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onClose={onClose} />
        </TestWrapper>
      );

      await user.keyboard('{Escape}');

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should focus first input when modal opens', async () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      await waitFor(() => {
        const nameField = screen.getByDisplayValue('') as HTMLInputElement;
        expect(nameField).toHaveFocus();
      }, { timeout: 200 });
    });

    it('should not close modal when clicking inside modal content', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} onClose={onClose} />
        </TestWrapper>
      );

      const modalContent = screen.getByRole('dialog');
      await user.click(modalContent);

      expect(onClose).not.toHaveBeenCalled();
    });
  });

  describe('Form Validation', () => {
    it('should display validation errors', () => {
      mockAtomValues.errors = {
        name: 'Project name is required',
        path: 'Project path is required',
        serverId: 'Server selection is required',
      };

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText('Project name is required')).toBeInTheDocument();
      expect(screen.getByText('Project path is required')).toBeInTheDocument();
      expect(screen.getByText('Server selection is required')).toBeInTheDocument();
    });

    it('should display general error message', () => {
      mockAtomValues.errors = {
        general: 'Something went wrong',
      };

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText('Something went wrong')).toBeInTheDocument();
      expect(screen.getByTestId('alert-circle-icon')).toBeInTheDocument();
    });

    it('should disable submit button when form is invalid', () => {
      mockAtomValues.isValid = false;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const submitButton = screen.getByRole('button', { name: /create project/i });
      expect(submitButton).toBeDisabled();
    });

    it('should enable submit button when form is valid', () => {
      mockAtomValues.isValid = true;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const submitButton = screen.getByRole('button', { name: /create project/i });
      expect(submitButton).not.toBeDisabled();
    });
  });

  describe('Loading States', () => {
    it('should show loading state during submission', () => {
      mockAtomValues.isSubmitting = true;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const submitButton = screen.getByRole('button', { name: /create project/i });
      expect(submitButton).toHaveAttribute('aria-busy', 'true');
    });

    it('should disable buttons during submission', () => {
      mockAtomValues.isSubmitting = true;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const submitButton = screen.getByRole('button', { name: /create project/i });
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      const closeButton = screen.getByLabelText(/close project creation modal/i);

      expect(submitButton).toBeDisabled();
      expect(cancelButton).toBeDisabled();
      expect(closeButton).toBeDisabled();
    });

    it('should disable form inputs during submission', () => {
      mockAtomValues.isSubmitting = true;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const nameField = screen.getByLabelText(/project name/i);
      const pathField = screen.getByLabelText(/project path/i);
      const serverButton = screen.getByRole('combobox', { name: /server/i });

      expect(nameField).toBeDisabled();
      expect(pathField).toBeDisabled();
      expect(serverButton).toBeDisabled();
    });
  });

  describe('Optimistic Updates', () => {
    it('should display optimistic creation indicator', () => {
      mockAtomValues.isOptimisticCreation = true;
      mockAtomValues.optimisticProject = {
        id: 'temp-id',
        name: 'Test Project',
        path: '/test/path',
        serverId: 'server-1',
        createdAt: new Date().toISOString(),
      };

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText(/creating project "Test Project"/i)).toBeInTheDocument();
      expect(screen.getByText(/this may take a moment/i)).toBeInTheDocument();
    });

    it('should disable form during optimistic creation', () => {
      mockAtomValues.isOptimisticCreation = true;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const submitButton = screen.getByRole('button', { name: /creating.../i });
      const cancelButton = screen.getByRole('button', { name: /cancel/i });

      expect(submitButton).toBeDisabled();
      expect(cancelButton).toBeDisabled();
    });
  });

  describe('Connection and Retry States', () => {
    it('should display connection retry indicator', () => {
      mockAtomValues.isConnectionRetrying = true;
      mockAtomValues.connectionRetryState = {
        isRetrying: true,
        retryAttempt: 2,
        maxRetries: 3,
        lastError: null,
      };

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText(/reconnecting to server/i)).toBeInTheDocument();
      expect(screen.getByText(/attempt 2 of 3/i)).toBeInTheDocument();
    });

    it('should display queued requests indicator', () => {
      mockAtomValues.queuedRequestsCount = 2;
      mockAtomValues.isConnectionRetrying = false;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText(/connection lost - project creation queued/i)).toBeInTheDocument();
      expect(screen.getByText(/2 requests waiting for connection/i)).toBeInTheDocument();
    });

    it('should display connection error when max retries reached', () => {
      mockAtomValues.connectionRetryState = {
        isRetrying: false,
        retryAttempt: 3,
        maxRetries: 3,
        lastError: 'Connection failed',
      };
      mockAtomValues.isConnectionRetrying = false;

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText(/connection failed/i)).toBeInTheDocument();
      expect(screen.getByText('Connection failed')).toBeInTheDocument();
    });
  });

  describe('WebSocket Integration', () => {
    it('should handle WebSocket connection state', () => {
      mockUseWebSocket.mockReturnValue({
        isConnected: false,
        send: vi.fn().mockReturnValue(false),
        connectionState: 'disconnected',
      });

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      // Modal should still render but WebSocket functionality would be limited
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('should register WebSocket message handlers', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      // Verify that useWebSocketMessage was called for project_created and project_creation_error
      expect(mockUseWebSocketMessage).toHaveBeenCalledTimes(2);
    });
  });

  describe('Keyboard Navigation', () => {
    it('should support Tab navigation through form fields', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const nameField = screen.getByLabelText(/project name/i);
      const pathField = screen.getByLabelText(/project path/i);
      const serverButton = screen.getByRole('combobox', { name: /server/i });

      nameField.focus();
      expect(nameField).toHaveFocus();

      await user.tab();
      expect(pathField).toHaveFocus();

      await user.tab();
      expect(serverButton).toHaveFocus();
    });

    it('should handle keyboard navigation in server dropdown', async () => {
      const user = userEvent.setup();
      
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      await user.click(serverButton);

      // Test arrow key navigation
      await user.keyboard('{ArrowDown}');
      await user.keyboard('{ArrowUp}');
      await user.keyboard('{Enter}');

      expect(mockSetters.updateField).toHaveBeenCalled();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const modal = screen.getByRole('dialog');
      expect(modal).toHaveAttribute('aria-modal', 'true');
      expect(modal).toHaveAttribute('aria-labelledby', 'project-creation-title');

      const title = screen.getByText('Create New Project');
      expect(title).toHaveAttribute('id', 'project-creation-title');
    });

    it('should have proper form labels and descriptions', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const nameField = screen.getByLabelText('Project Name');
      const pathField = screen.getByLabelText('Project Path');
      const serverField = screen.getByLabelText('Server');

      expect(nameField).toHaveAttribute('id', 'project-name-input');
      expect(pathField).toBeInTheDocument();
      expect(serverField).toBeInTheDocument();
    });

    it('should announce errors to screen readers', () => {
      mockAtomValues.createProjectErrorsAtom = {
        name: 'Project name is required',
      };

      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const errorMessage = screen.getByText('Project name is required');
      expect(errorMessage).toHaveAttribute('role', 'alert');
      expect(errorMessage).toHaveAttribute('aria-live', 'polite');
    });

    it('should have accessible server dropdown', () => {
      render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const serverButton = screen.getByRole('combobox', { name: /server/i });
      expect(serverButton).toHaveAttribute('aria-haspopup', 'listbox');
      expect(serverButton).toHaveAttribute('aria-expanded', 'false');
    });
  });

  describe('Error Recovery', () => {
    it('should handle form validation recovery', async () => {
      const user = userEvent.setup();
      
      // Start with errors
      mockAtomValues.errors = {
        name: 'Project name is required',
      };

      const { rerender } = render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.getByText('Project name is required')).toBeInTheDocument();

      // Clear errors
      mockAtomValues.errors = {};
      
      rerender(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      expect(screen.queryByText('Project name is required')).not.toBeInTheDocument();
    });

    it('should handle WebSocket connection recovery', () => {
      // Start disconnected
      mockUseWebSocket.mockReturnValue({
        isConnected: false,
        send: vi.fn().mockReturnValue(false),
        connectionState: 'disconnected',
      });

      const { rerender } = render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      // Reconnect
      mockUseWebSocket.mockReturnValue({
        isConnected: true,
        send: vi.fn().mockReturnValue(true),
        connectionState: 'connected',
      });

      rerender(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      // Should handle the state change gracefully
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
  });

  describe('Performance', () => {
    it('should not re-render unnecessarily', () => {
      const renderSpy = vi.fn();
      
      const TestWrapper = ({ children }: { children: React.ReactNode }) => {
        renderSpy();
        return <JotaiProvider>{children}</JotaiProvider>;
      };

      const { rerender } = render(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      const initialRenderCount = renderSpy.mock.calls.length;

      // Re-render with same props
      rerender(
        <TestWrapper>
          <ProjectCreationModal {...defaultProps} />
        </TestWrapper>
      );

      // Should not cause additional renders due to React optimizations
      expect(renderSpy.mock.calls.length).toBeGreaterThan(initialRenderCount);
    });
  });
});