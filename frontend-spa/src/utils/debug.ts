/**
 * Debug utilities for development
 * Provides helpers for component debugging and error analysis
 */

export const debugRender = (componentName: string, props?: any) => {
  if (import.meta.env.DEV) {
    console.group(`🔍 ${componentName} - Render Debug`);
    console.log('Component:', componentName);
    console.log('Props:', props);
    
    // Check for potentially problematic props
    if (props) {
      Object.entries(props).forEach(([key, value]) => {
        if (value && typeof value === 'object') {
          // Check if it's a React element
          if (value && typeof value === 'object' && ('$$typeof' in value || 'type' in value)) {
            console.warn(`⚠️ Prop '${key}' looks like a React component/element:`, value);
          }
          // Check for plain objects that might be rendered as children
          else if (value.constructor === Object) {
            console.warn(`⚠️ Prop '${key}' is a plain object - ensure it's not being rendered as a child:`, value);
          }
        }
      });
    }
    
    console.groupEnd();
  }
};

export const debugError = (error: any, context?: string) => {
  if (import.meta.env.DEV) {
    console.group(`🚨 Debug Error${context ? ` in ${context}` : ''}`);
    console.error('Error:', error);
    console.error('Error type:', typeof error);
    console.error('Error constructor:', error?.constructor?.name);
    
    if (error instanceof Error) {
      console.error('Message:', error.message);
      console.error('Stack:', error.stack);
    }
    
    console.error('Context:', context);
    console.error('Timestamp:', new Date().toISOString());
    console.groupEnd();
  }
};

export const debugProps = (componentName: string, props: Record<string, any>) => {
  if (import.meta.env.DEV) {
    console.group(`📋 ${componentName} - Props Analysis`);
    
    Object.entries(props).forEach(([key, value]) => {
      console.log(`${key}:`, value, `(${typeof value})`);
      
      // Special checks for common issues
      if (value === null) {
        console.info(`ℹ️ ${key} is null`);
      } else if (value === undefined) {
        console.info(`ℹ️ ${key} is undefined`);
      } else if (Array.isArray(value)) {
        console.info(`📝 ${key} is an array with ${value.length} items`);
        if (value.length > 0) {
          console.log(`  First item:`, value[0], `(${typeof value[0]})`);
        }
      } else if (typeof value === 'object' && value !== null) {
        // Check if it's a React element
        if ('$$typeof' in value) {
          console.warn(`⚠️ ${key} appears to be a React element - make sure it's handled correctly`);
        } else if ('type' in value && 'props' in value) {
          console.warn(`⚠️ ${key} appears to be a React component - make sure it's rendered properly`);
        } else {
          console.info(`🏷️ ${key} is an object with keys:`, Object.keys(value));
        }
      } else if (typeof value === 'function') {
        console.info(`⚙️ ${key} is a function: ${value.name || 'anonymous'}`);
      }
    });
    
    console.groupEnd();
  }
};

export const debugChildrenProp = (children: React.ReactNode, componentName?: string) => {
  if (import.meta.env.DEV) {
    const context = componentName || 'Component';
    console.group(`👶 ${context} - Children Analysis`);
    
    console.log('Children:', children);
    console.log('Children type:', typeof children);
    
    if (children === null || children === undefined) {
      console.info('✅ Children is null/undefined - safe to render');
    } else if (typeof children === 'string' || typeof children === 'number') {
      console.info('✅ Children is a primitive - safe to render');
    } else if (Array.isArray(children)) {
      console.info(`📝 Children is an array with ${children.length} items`);
      children.forEach((child, index) => {
        console.log(`  Child ${index}:`, child, `(${typeof child})`);
        if (child && typeof child === 'object' && !('$$typeof' in child)) {
          console.warn(`⚠️ Child ${index} is a plain object - this may cause rendering errors`);
        }
      });
    } else if (typeof children === 'object') {
      if ('$$typeof' in children) {
        console.info('✅ Children appears to be a React element');
      } else {
        console.error('❌ Children is a plain object - this will cause rendering errors!');
        console.error('Object keys:', Object.keys(children));
        console.error('Object values:', children);
      }
    } else if (typeof children === 'function') {
      console.info('⚙️ Children is a function - make sure it returns valid JSX');
    }
    
    console.groupEnd();
  }
};

export const debugComponentMount = (componentName: string) => {
  if (import.meta.env.DEV) {
    console.log(`🚀 ${componentName} mounted`);
  }
};

export const debugComponentUnmount = (componentName: string) => {
  if (import.meta.env.DEV) {
    console.log(`💥 ${componentName} unmounted`);
  }
};

export const debugHookUpdate = (hookName: string, value: any, componentName?: string) => {
  if (import.meta.env.DEV) {
    const context = componentName ? ` in ${componentName}` : '';
    console.log(`🪝 ${hookName}${context} updated:`, value);
  }
};

// Helper to safely stringify objects for logging
export const safeStringify = (obj: any, replacer?: (key: string, value: any) => any): string => {
  try {
    return JSON.stringify(obj, replacer, 2);
  } catch (error) {
    return `[Object - Failed to stringify: ${error instanceof Error ? error.message : 'Unknown error'}]`;
  }
};

// Helper to detect if a value might cause React rendering issues
export const isRenderSafe = (value: any): boolean => {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return true;
  if (Array.isArray(value)) return value.every(isRenderSafe);
  if (typeof value === 'object' && '$$typeof' in value) return true; // React element
  if (typeof value === 'function') return false; // Functions aren't renderable
  if (typeof value === 'object') return false; // Plain objects aren't renderable
  return false;
};

// Helper to check component props for common issues
export const validateProps = (componentName: string, props: Record<string, any>) => {
  if (!import.meta.env.DEV) return;
  
  const issues: string[] = [];
  
  Object.entries(props).forEach(([key, value]) => {
    if (!isRenderSafe(value) && (key === 'children' || key.toLowerCase().includes('child'))) {
      issues.push(`Prop '${key}' contains non-renderable content`);
    }
    
    if (typeof value === 'object' && value !== null && !('$$typeof' in value) && key === 'children') {
      issues.push(`Children prop contains a plain object which will cause render errors`);
    }
  });
  
  if (issues.length > 0) {
    console.group(`⚠️ ${componentName} - Prop Validation Issues`);
    issues.forEach(issue => console.warn(issue));
    console.groupEnd();
  }
};