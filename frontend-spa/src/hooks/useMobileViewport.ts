/**
 * Custom hook for mobile viewport handling
 * Manages viewport meta tag, CSS custom properties, and orientation changes
 */

import { useEffect } from 'react';

export const useMobileViewport = () => {
  useEffect(() => {
    // Set viewport meta tag for mobile optimization
    const viewport = document.querySelector('meta[name="viewport"]');
    if (viewport) {
      viewport.setAttribute(
        'content',
        'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover'
      );
    }

    // Handle mobile keyboard and dynamic viewport height
    const handleResize = () => {
      // Update CSS custom property for viewport height
      // This helps with mobile keyboards that change the viewport height
      const vh = window.innerHeight * 0.01;
      document.documentElement.style.setProperty('--vh', `${vh}px`);
    };

    // Initial setup
    handleResize();
    
    // Add event listeners
    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleResize);

    // Cleanup
    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleResize);
    };
  }, []);
};