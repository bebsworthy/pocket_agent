/**
 * Test utilities for rendering components with providers and context
 * 
 * This file provides custom render functions and utilities for testing
 * React components with proper context providers and state management.
 */

import React from 'react';
import { render, RenderOptions } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { Provider as JotaiProvider } from 'jotai';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

// Custom render function with all providers
interface CustomRenderOptions extends RenderOptions {
  initialEntries?: string[];
  withRouter?: boolean;
  withJotai?: boolean;
}

const AllTheProviders: React.FC<{ 
  children: React.ReactNode; 
  initialEntries?: string[];
  withRouter?: boolean;
  withJotai?: boolean;
}> = ({ 
  children, 
  initialEntries = ['/'], 
  withRouter = true,
  withJotai = true,
}) => {
  let component = children as ReactElement;

  // Wrap with Jotai provider if needed
  if (withJotai) {
    component = (
      <JotaiProvider>
        {component}
      </JotaiProvider>
    );
  }

  // Wrap with Router if needed
  if (withRouter) {
    component = (
      <BrowserRouter>
        {component}
      </BrowserRouter>
    );
  }

  return component;
};

const customRender = (
  ui: ReactElement,
  options: CustomRenderOptions = {}
) => {
  const {
    initialEntries,
    withRouter = true,
    withJotai = true,
    ...renderOptions
  } = options;

  const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <AllTheProviders
      initialEntries={initialEntries}
      withRouter={withRouter}
      withJotai={withJotai}
    >
      {children}
    </AllTheProviders>
  );

  return render(ui, { wrapper: Wrapper, ...renderOptions });
};

// Utility for testing components that require user interaction
export const renderWithUserEvents = (
  ui: ReactElement,
  options: CustomRenderOptions = {}
) => {
  const user = userEvent.setup();
  const renderResult = customRender(ui, options);
  
  return {
    ...renderResult,
    user,
  };
};

// Utility for testing modal components
export const renderModal = (
  ui: ReactElement,
  options: CustomRenderOptions = {}
) => {
  // Ensure modal is rendered in document.body
  const { container, ...rest } = customRender(ui, {
    container: document.body,
    ...options,
  });
  
  return { container, ...rest };
};

// Accessibility testing utilities
export const checkAccessibility = async (element: HTMLElement) => {
  // Basic accessibility checks
  const checks = {
    hasAriaLabel: element.hasAttribute('aria-label') || element.hasAttribute('aria-labelledby'),
    hasRole: element.hasAttribute('role'),
    isFocusable: element.tabIndex >= 0 || ['button', 'input', 'select', 'textarea', 'a'].includes(element.tagName.toLowerCase()),
    hasValidAriaAttributes: true, // Placeholder for more complex validation
  };

  return checks;
};

// Touch target size validation (for mobile compliance)
export const checkTouchTargetSize = (element: HTMLElement) => {
  const rect = element.getBoundingClientRect();
  const minSize = 44; // 44px minimum touch target
  
  return {
    width: rect.width,
    height: rect.height,
    meetsMinimumSize: rect.width >= minSize && rect.height >= minSize,
    isAccessible: rect.width >= minSize && rect.height >= minSize,
  };
};

// Animation testing utilities
export const mockAnimations = () => {
  // Mock CSS animations and transitions
  const originalGetComputedStyle = window.getComputedStyle;
  
  Object.defineProperty(window, 'getComputedStyle', {
    value: (element: Element) => ({
      ...originalGetComputedStyle(element),
      animationDuration: '0s',
      animationDelay: '0s',
      transitionDuration: '0s',
      transitionDelay: '0s',
    }),
  });

  return () => {
    Object.defineProperty(window, 'getComputedStyle', {
      value: originalGetComputedStyle,
    });
  };
};

// Utility for testing keyboard navigation
export const testKeyboardNavigation = async (
  user: ReturnType<typeof userEvent.setup>,
  element: HTMLElement,
  keys: string[]
) => {
  const results: Array<{ key: string; focused: boolean }> = [];
  
  // Focus the element first
  element.focus();
  
  for (const key of keys) {
    await user.keyboard(`{${key}}`);
    const activeElement = document.activeElement;
    results.push({
      key,
      focused: activeElement === element || element.contains(activeElement),
    });
  }
  
  return results;
};

// Utility for testing form validation
export const testFormValidation = async (
  user: ReturnType<typeof userEvent.setup>,
  form: HTMLFormElement,
  testCases: Array<{
    name: string;
    inputs: Record<string, string>;
    expectedErrors: string[];
  }>
) => {
  const results = [];
  
  for (const testCase of testCases) {
    // Clear form
    const inputs = form.querySelectorAll('input, textarea, select');
    for (const input of inputs) {
      if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement) {
        await user.clear(input);
      }
    }
    
    // Fill form with test data
    for (const [fieldName, value] of Object.entries(testCase.inputs)) {
      const field = form.querySelector(`[name="${fieldName}"]`) as HTMLInputElement;
      if (field) {
        await user.type(field, value);
      }
    }
    
    // Try to submit
    const submitButton = form.querySelector('[type="submit"]') as HTMLButtonElement;
    if (submitButton) {
      await user.click(submitButton);
    }
    
    // Check for errors
    const errorElements = form.querySelectorAll('[role="alert"], .error-message, [aria-invalid="true"]');
    const actualErrors = Array.from(errorElements).map(el => el.textContent || '');
    
    results.push({
      testCase: testCase.name,
      expectedErrors: testCase.expectedErrors,
      actualErrors,
      passed: testCase.expectedErrors.every(expectedError =>
        actualErrors.some(actualError => actualError.includes(expectedError))
      ),
    });
  }
  
  return results;
};

// Utility for testing responsive behavior
export const testResponsiveBreakpoints = (
  renderFn: () => void,
  breakpoints: Array<{ name: string; width: number; height: number }>
) => {
  const results = [];
  
  for (const breakpoint of breakpoints) {
    // Set viewport size
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: breakpoint.width,
    });
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: breakpoint.height,
    });
    
    // Trigger resize event
    window.dispatchEvent(new Event('resize'));
    
    // Re-render component
    renderFn();
    
    results.push({
      breakpoint: breakpoint.name,
      dimensions: { width: breakpoint.width, height: breakpoint.height },
      // Additional checks can be added here
    });
  }
  
  return results;
};

// Utility for waiting for async operations
export const waitForLoadingToFinish = async () => {
  // Wait for any pending promises
  await new Promise(resolve => setTimeout(resolve, 0));
  
  // Wait for any loading states to clear
  let attempts = 0;
  const maxAttempts = 50; // 5 seconds max
  
  while (attempts < maxAttempts) {
    const loadingElements = document.querySelectorAll('[aria-busy="true"], .loading, .spinner');
    if (loadingElements.length === 0) {
      break;
    }
    
    await new Promise(resolve => setTimeout(resolve, 100));
    attempts++;
  }
};

// Re-export everything from @testing-library/react
export * from '@testing-library/react';

// Export custom render as the default render
export { customRender as render };