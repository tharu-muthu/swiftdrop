import { useEffect } from 'react';
import { useAuth } from './AuthProvider.jsx';
import { navigate } from '../../app/navigation.js';

export default function AuthCompletePage() {
  const { status } = useAuth();
  useEffect(() => {
    // AuthProvider restores the callback cookie exactly once. A second full-page
    // navigation here could interrupt refresh after its token was consumed.
    if (status === 'authenticated') navigate('/workspaces', { replace: true });
    else if (status === 'anonymous') navigate('/login', { replace: true });
  }, [status]);
  return <main className="auth-loading" role="status" aria-live="polite">Completing Google sign-in...</main>;
}
