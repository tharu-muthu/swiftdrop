import { useLayoutEffect, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import { authPost } from './apiClient.js';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';

export default function ConfirmEmailChangePage() {
  const { client } = useAuth();
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') || '');
  const [pending, setPending] = useState(false), [done, setDone] = useState(false), [error, setError] = useState('');
  useLayoutEffect(() => { window.history.replaceState(null, '', window.location.pathname); }, []);
  async function confirm() {
    if (pending) return;
    setPending(true); setError('');
    try { await authPost('/api/v1/auth/email-change/confirm', { token }); client.endLocalSession(); setDone(true); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  return <AuthLayout><div className="registration-card recovery-card"><Brand /><div className="recovery-result">
    {done ? <div role="status"><h2>Email changed</h2><p>All sessions have ended. Sign in with your new email, or your existing linked Google account.</p><a href="/login">Continue to login</a></div>
      : <><h2>Confirm your new email</h2><p>This changes your SwiftDrop sign-in email and ends existing sessions.</p>
        {error && <p className="form-alert" role="alert">{error}</p>}
        {token ? <button className="primary-button" disabled={pending} onClick={confirm}>{pending ? 'Confirming…' : 'Confirm email change'}</button>
          : <p role="alert">This link is missing. Request a new email change from your profile.</p>}
        <a className="recovery-back" href="/account">Back to my profile</a></>}
  </div></div></AuthLayout>;
}
