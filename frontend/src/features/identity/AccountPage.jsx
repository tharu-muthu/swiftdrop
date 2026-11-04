import { useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';
import { navigate } from '../../app/navigation.js';

export default function AccountPage() {
  const { user, client } = useAuth();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  async function signOut() {
    setPending(true);
    try { await client.logout(); }
    catch { /* The shared state retains a retryable sign-out error. */ }
    finally { navigate('/login'); }
  }
  async function checkSession() {
    try { await client.request('/api/v1/me'); setMessage('Your session is active.'); }
    catch (error) { setMessage(error.message); }
  }
  return <AuthLayout><div className="registration-card account-card">
    <Brand /><h2>Your account</h2><p className="form-subtitle">You are signed in to SwiftDrop.</p>
    <dl><dt>Name</dt><dd>{user.fullName}</dd><dt>Email</dt><dd>{user.email}</dd>
      <dt>Roles</dt><dd>{user.roles.join(', ')}</dd></dl>
    {message && <p role="status">{message}</p>}
    <button className="secondary-button" onClick={checkSession}>Check session</button>
    <button className="primary-button" onClick={signOut} disabled={pending}>{pending ? 'Logging out…' : 'Log out'}</button>
  </div></AuthLayout>;
}
