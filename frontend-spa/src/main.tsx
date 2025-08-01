import React from 'react';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './styles/globals.css';

// Enable React 18 concurrent features
const container = document.getElementById('root');

if (!container) {
  throw new Error('Failed to find the root element');
}

const root = createRoot(container);

// Error handling for the root render
try {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>
  );
} catch (error) {
  console.error('Failed to render the application:', error);

  // Fallback error UI
  container.innerHTML = `
    <div style="
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: #f9fafb;
      font-family: system-ui, -apple-system, sans-serif;
      padding: 1rem;
    ">
      <div style="
        background: white;
        padding: 2rem;
        border-radius: 0.5rem;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        text-align: center;
        max-width: 400px;
        width: 100%;
      ">
        <h1 style="
          color: #dc2626;
          font-size: 1.25rem;
          font-weight: 600;
          margin-bottom: 0.5rem;
        ">
          Application Failed to Load
        </h1>
        <p style="
          color: #6b7280;
          margin-bottom: 1rem;
          font-size: 0.875rem;
        ">
          There was an error loading the application. Please refresh the page to try again.
        </p>
        <button 
          onclick="window.location.reload()"
          style="
            background-color: #3b82f6;
            color: white;
            padding: 0.5rem 1rem;
            border: none;
            border-radius: 0.375rem;
            cursor: pointer;
            font-size: 0.875rem;
            font-weight: 500;
          "
        >
          Refresh Page
        </button>
      </div>
    </div>
  `;
}

// Service Worker registration for PWA support (future enhancement)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then(registration => {
        console.log('SW registered: ', registration);
      })
      .catch(registrationError => {
        console.log('SW registration failed: ', registrationError);
      });
  });
}
