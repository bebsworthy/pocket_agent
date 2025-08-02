/**
 * Comprehensive tests for FAB (Floating Action Button) component
 * 
 * This test suite covers:
 * - Rendering with different props (size, position, color variants)
 * - Touch interaction and keyboard events (onPress, Enter key, Space key)
 * - Accessibility compliance (ARIA labels, focus management, screen reader)
 * - Animation and styling validation (scale animation, position classes)
 * - Mobile touch target compliance (44px minimum)
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render as rtlRender, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FAB } from '../FAB';
import type { FABProps } from '../FAB';
import { 
  checkAccessibility, 
  checkTouchTargetSize, 
  mockAnimations,
  testKeyboardNavigation,
} from '../../../../test/utils';

// Mock the Lucide React icons
vi.mock('lucide-react', () => {
  const MockIcon: React.FC<{ className?: string; 'data-testid'?: string }> = ({ className, 'data-testid': testId }) => (
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
    Plus: MockIcon,
    Settings: (props: any) => <MockIcon {...props} data-testid="settings-icon" />,
    Heart: (props: any) => <MockIcon {...props} data-testid="heart-icon" />,
  };
});

// Use plain render without providers for atom components
const render = rtlRender;

describe('FAB Component', () => {
  const defaultProps: FABProps = {
    onPress: vi.fn(),
    ariaLabel: 'Create new item',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render with default props', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button', { name: /create new item/i });
      expect(button).toBeInTheDocument();
      expect(button).toHaveClass('fixed', 'z-50', 'rounded-full');
    });

    it('should render with custom aria label', () => {
      const customLabel = 'Add new project';
      render(<FAB {...defaultProps} ariaLabel={customLabel} />);
      
      const button = screen.getByRole('button', { name: customLabel });
      expect(button).toBeInTheDocument();
      expect(button).toHaveAttribute('aria-label', customLabel);
    });

    it('should render with custom icon as component', () => {
      // Create a simple mock icon for this test
      const MockSettings = (props: any) => <svg {...props} data-testid="settings-icon" />;
      render(<FAB {...defaultProps} icon={MockSettings} />);
      
      const button = screen.getByRole('button');
      expect(button).toBeInTheDocument();
      // Check that the settings icon is rendered
      expect(screen.getByTestId('settings-icon')).toBeInTheDocument();
    });

    it('should render with custom icon as React element', () => {
      const customIcon = <svg data-testid="custom-heart-icon" />;
      render(<FAB {...defaultProps} icon={customIcon} />);
      
      const button = screen.getByRole('button');
      const icon = screen.getByTestId('custom-heart-icon');
      expect(button).toBeInTheDocument();
      expect(icon).toBeInTheDocument();
    });

    it('should render with custom className', () => {
      const customClass = 'custom-fab-class';
      render(<FAB {...defaultProps} className={customClass} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass(customClass);
    });
  });

  describe('Size Variants', () => {
    it('should apply small size classes correctly', () => {
      render(<FAB {...defaultProps} size="sm" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('h-12', 'w-12', 'min-h-[48px]', 'min-w-[48px]');
    });

    it('should apply medium size classes correctly (default)', () => {
      render(<FAB {...defaultProps} size="md" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('h-14', 'w-14', 'min-h-[56px]', 'min-w-[56px]');
    });

    it('should apply large size classes correctly', () => {
      render(<FAB {...defaultProps} size="lg" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('h-16', 'w-16', 'min-h-[64px]', 'min-w-[64px]');
    });

    it('should default to medium size when no size prop provided', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('h-14', 'w-14', 'min-h-[56px]', 'min-w-[56px]');
    });
  });

  describe('Position Variants', () => {
    it('should apply bottom-right position classes correctly (default)', () => {
      render(<FAB {...defaultProps} position="bottom-right" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('bottom-6', 'right-6');
    });

    it('should apply bottom-left position classes correctly', () => {
      render(<FAB {...defaultProps} position="bottom-left" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('bottom-6', 'left-6');
    });

    it('should apply bottom-center position classes correctly', () => {
      render(<FAB {...defaultProps} position="bottom-center" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('bottom-6', 'left-1/2', '-translate-x-1/2');
    });

    it('should default to bottom-right when no position prop provided', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('bottom-6', 'right-6');
    });
  });

  describe('Color Variants', () => {
    it('should apply primary color classes correctly (default)', () => {
      render(<FAB {...defaultProps} color="primary" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass(
        'bg-primary-600',
        'text-white',
        'hover:bg-primary-700',
        'focus-visible:ring-primary-600',
        'active:bg-primary-800'
      );
    });

    it('should apply secondary color classes correctly', () => {
      render(<FAB {...defaultProps} color="secondary" />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass(
        'bg-gray-600',
        'text-white',
        'hover:bg-gray-700',
        'focus-visible:ring-gray-600',
        'active:bg-gray-800'
      );
    });

    it('should default to primary color when no color prop provided', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('bg-primary-600');
    });
  });

  describe('Interactions', () => {
    it('should call onPress when clicked', async () => {
      const user = userEvent.setup();
      const onPress = vi.fn();
      render(<FAB {...defaultProps} onPress={onPress} />);
      
      const button = screen.getByRole('button');
      await user.click(button);
      
      expect(onPress).toHaveBeenCalledTimes(1);
    });

    it('should call onPress when Enter key is pressed', async () => {
      const user = userEvent.setup();
      const onPress = vi.fn();
      render(<FAB {...defaultProps} onPress={onPress} />);
      
      const button = screen.getByRole('button');
      button.focus();
      await user.keyboard('{Enter}');
      
      expect(onPress).toHaveBeenCalledTimes(1);
    });

    it('should call onPress when Space key is pressed', async () => {
      const user = userEvent.setup();
      const onPress = vi.fn();
      render(<FAB {...defaultProps} onPress={onPress} />);
      
      const button = screen.getByRole('button');
      button.focus();
      await user.keyboard(' ');
      
      expect(onPress).toHaveBeenCalledTimes(1);
    });

    it('should not call onPress when disabled', async () => {
      const user = userEvent.setup();
      const onPress = vi.fn();
      render(<FAB {...defaultProps} onPress={onPress} disabled />);
      
      const button = screen.getByRole('button');
      await user.click(button);
      
      expect(onPress).not.toHaveBeenCalled();
    });

    it('should not call onPress on disabled button with keyboard', async () => {
      const user = userEvent.setup();
      const onPress = vi.fn();
      render(<FAB {...defaultProps} onPress={onPress} disabled />);
      
      const button = screen.getByRole('button');
      // Disabled buttons should not be focusable
      expect(button).toHaveAttribute('tabindex', '-1');
      
      // Try to focus and press anyway
      button.focus();
      await user.keyboard('{Enter}');
      await user.keyboard(' ');
      
      expect(onPress).not.toHaveBeenCalled();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveAttribute('role', 'button');
      expect(button).toHaveAttribute('aria-label', defaultProps.ariaLabel);
      expect(button).toHaveAttribute('tabindex', '0');
    });

    it('should be focusable by default', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveAttribute('tabindex', '0');
      button.focus();
      expect(button).toHaveFocus();
    });

    it('should not be focusable when disabled', () => {
      render(<FAB {...defaultProps} disabled />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveAttribute('tabindex', '-1');
      expect(button).toHaveAttribute('disabled');
    });

    it('should have proper disabled styling', () => {
      render(<FAB {...defaultProps} disabled />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('disabled:opacity-50', 'disabled:pointer-events-none');
    });

    it('should meet accessibility standards', async () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      const accessibilityCheck = await checkAccessibility(button);
      
      expect(accessibilityCheck.hasAriaLabel).toBe(true);
      expect(accessibilityCheck.hasRole).toBe(true);
      expect(accessibilityCheck.isFocusable).toBe(true);
    });

    it('should support keyboard navigation', async () => {
      const user = userEvent.setup();
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      
      // Test Tab navigation
      button.focus();
      expect(button).toHaveFocus();
      
      // Test Enter key interaction
      await user.keyboard('{Enter}');
      expect(defaultProps.onPress).toHaveBeenCalled();
      
      // Test Space key interaction
      vi.clearAllMocks();
      await user.keyboard(' ');
      expect(defaultProps.onPress).toHaveBeenCalled();
    });
  });

  describe('Mobile Compliance', () => {
    it('should have touch target size classes for small size', () => {
      render(<FAB {...defaultProps} size="sm" />);
      
      const button = screen.getByRole('button');
      // Check that proper CSS classes are applied for touch target size
      expect(button).toHaveClass('min-h-[48px]', 'min-w-[48px]');
    });

    it('should have touch target size classes for medium size', () => {
      render(<FAB {...defaultProps} size="md" />);
      
      const button = screen.getByRole('button');
      // Check that proper CSS classes are applied for touch target size
      expect(button).toHaveClass('min-h-[56px]', 'min-w-[56px]');
    });

    it('should have touch target size classes for large size', () => {
      render(<FAB {...defaultProps} size="lg" />);
      
      const button = screen.getByRole('button');
      // Check that proper CSS classes are applied for touch target size
      expect(button).toHaveClass('min-h-[64px]', 'min-w-[64px]');
    });

    it('should have proper touch-action for mobile interactions', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      // Check that the button has proper classes for touch handling
      expect(button).toHaveClass('no-tap-highlight');
    });
  });

  describe('Animation and Styling', () => {
    it('should have scale animation classes', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('active:scale-95');
    });

    it('should have transition classes', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('transition-all', 'duration-200');
    });

    it('should have shadow effects', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass('shadow-lg', 'hover:shadow-xl');
    });

    it('should have focus ring styles', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toHaveClass(
        'focus-visible:outline-none',
        'focus-visible:ring-2',
        'focus-visible:ring-offset-2'
      );
    });

    it('should work with animation mocking', () => {
      const restoreAnimations = mockAnimations();
      
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      expect(button).toBeInTheDocument();
      
      // Restore animations after test
      restoreAnimations();
    });
  });

  describe('Icon Rendering', () => {
    it('should render default Plus icon when no icon provided', () => {
      render(<FAB {...defaultProps} />);
      
      const button = screen.getByRole('button');
      const icon = button.querySelector('svg');
      expect(icon).toBeInTheDocument();
    });

    it('should apply correct icon size classes for small FAB', () => {
      const TestIcon = (props: any) => <svg {...props} data-testid="test-icon" />;
      render(<FAB {...defaultProps} size="sm" icon={TestIcon} />);
      
      const icon = screen.getByTestId('test-icon');
      expect(icon).toHaveClass('h-5', 'w-5');
    });

    it('should apply correct icon size classes for medium FAB', () => {
      const TestIcon = (props: any) => <svg {...props} data-testid="test-icon" />;
      render(<FAB {...defaultProps} size="md" icon={TestIcon} />);
      
      const icon = screen.getByTestId('test-icon');
      expect(icon).toHaveClass('h-6', 'w-6');
    });

    it('should apply correct icon size classes for large FAB', () => {
      const TestIcon = (props: any) => <svg {...props} data-testid="test-icon" />;
      render(<FAB {...defaultProps} size="lg" icon={TestIcon} />);
      
      const icon = screen.getByTestId('test-icon');
      expect(icon).toHaveClass('h-7', 'w-7');
    });

    it('should handle React element icons with custom props', () => {
      const customIcon = <svg data-testid="custom-heart" className="custom-icon" />;
      render(<FAB {...defaultProps} icon={customIcon} />);
      
      const icon = screen.getByTestId('custom-heart');
      expect(icon).toBeInTheDocument();
      // Should preserve custom className and add size classes
      expect(icon).toHaveClass('custom-icon', 'h-6', 'w-6');
    });
  });

  describe('Error Boundaries', () => {
    it('should handle invalid icon prop gracefully', () => {
      // Test with invalid icon
      const invalidIcon = 'not-a-valid-icon' as any;
      
      expect(() => {
        render(<FAB {...defaultProps} icon={invalidIcon} />);
      }).not.toThrow();
      
      const button = screen.getByRole('button');
      expect(button).toBeInTheDocument();
    });

    it('should handle null icon gracefully', () => {
      render(<FAB {...defaultProps} icon={null as any} />);
      
      const button = screen.getByRole('button');
      expect(button).toBeInTheDocument();
    });
  });

  describe('Forward Ref', () => {
    it('should forward ref to button element', () => {
      const ref = React.createRef<HTMLButtonElement>();
      render(<FAB {...defaultProps} ref={ref} />);
      
      expect(ref.current).toBeInstanceOf(HTMLButtonElement);
      expect(ref.current).toHaveAttribute('role', 'button');
    });

    it('should allow calling focus on forwarded ref', () => {
      const ref = React.createRef<HTMLButtonElement>();
      render(<FAB {...defaultProps} ref={ref} />);
      
      expect(ref.current).not.toBeNull();
      ref.current?.focus();
      expect(ref.current).toHaveFocus();
    });
  });

  describe('Performance', () => {
    it('should not re-render unnecessarily when props do not change', () => {
      const onPress = vi.fn();
      const { rerender } = render(<FAB onPress={onPress} />);
      
      const button = screen.getByRole('button');
      const initialButton = button;
      
      // Re-render with same props
      rerender(<FAB onPress={onPress} />);
      
      // Button should be the same instance (React optimization)
      const rerenderButton = screen.getByRole('button');
      expect(rerenderButton).toBe(initialButton);
    });
  });
});