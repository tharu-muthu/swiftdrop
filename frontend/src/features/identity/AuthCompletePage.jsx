import { useEffect } from 'react';

export default function AuthCompletePage() {
  useEffect(() => {
    // The backend set the refresh cookie in the OAuth callback redirect.
    // A full navigation re-runs bootstrap (refresh → /me) from a clean state.
    // Use replace() so /auth/complete does not remain in browser history.
    window.location.replace('/account');
  }, []);

  return (
    <main className="auth-loading" role="status" aria-live="polite">
      Completing Google sign-in…
    </main>
  );
}

