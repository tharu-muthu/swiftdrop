import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './app/App.jsx';
import './app/styles.css';
import { AuthProvider } from './features/identity/AuthProvider.jsx';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider><App /></AuthProvider>
  </StrictMode>,
);
