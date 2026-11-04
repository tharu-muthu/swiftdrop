import FoundationPage from './FoundationPage.jsx';
import RegisterPage from '../features/identity/RegisterPage.jsx';
import { useEffect, useState } from 'react';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import LoginPage from '../features/identity/LoginPage.jsx';
import AccountPage from '../features/identity/AccountPage.jsx';
import AuthCompletePage from '../features/identity/AuthCompletePage.jsx';

export default function App() {
  const [path, setPath] = useState(window.location.pathname);
  const auth = useAuth();
  useEffect(() => {
    const update = () => setPath(window.location.pathname);
    window.addEventListener('popstate', update);
    return () => window.removeEventListener('popstate', update);
  }, []);
  if (path === '/foundation') return <FoundationPage />;
  if (path === '/auth/complete') return <AuthCompletePage />;
  if (auth.status === 'loading') return <main className="auth-loading" role="status">Checking your session…</main>;
  if (auth.status === 'authenticated') return <AccountPage />;
  return path === '/register' || path === '/' ? <RegisterPage /> : <LoginPage />;
}
