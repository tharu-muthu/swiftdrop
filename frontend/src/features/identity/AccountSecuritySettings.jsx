import { useEffect, useLayoutEffect, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import { navigate } from '../../app/navigation.js';
import { passwordError } from './passwordPolicy.js';

export default function AccountSecuritySettings() {
  const { user, client } = useAuth();
  const [proof, setProof] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('reauth') || '');
  const [email, setEmail] = useState('');
  const [emailPassword, setEmailPassword] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(new URLSearchParams(window.location.search).get('reauth') === 'failed'
    ? 'Google verification failed or was not recent enough. Verify again before changing account security.' : '');
  useLayoutEffect(() => {
    if (window.location.hash.startsWith('#reauth=')) window.history.replaceState(null, '', window.location.pathname + window.location.search);
  }, []);
  useEffect(() => {
    if (!proof) return;
    const timer = setTimeout(() => setProof(''), 5 * 60 * 1000);
    return () => clearTimeout(timer);
  }, [proof]);
  async function google() {
    setPending(true); setError('');
    try {
      const result = await client.request('/api/v1/me/security/google-reauth', { method: 'POST' });
      window.location.assign(result.authorizationUrl);
    } catch (failure) { setError(failure.status === 404 ? 'Google verification is not configured in this environment.' : failure.message); setPending(false); }
  }
  async function changeEmail(event) {
    event.preventDefault(); if (pending) return;
    setPending(true); setError(''); setNotice('');
    try {
      await client.request('/api/v1/me/security/email-change', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim(), ...(user.passwordConfigured ? { password: emailPassword } : { reauthToken: proof }) }),
      });
      setEmail(''); setNotice('If the new address is available, a confirmation link has been sent. Your current email stays unchanged until you confirm. The link expires in 30 minutes.');
    } catch (failure) { setError(failure.message); }
    finally { setEmailPassword(''); setProof(''); setPending(false); }
  }
  async function changePassword(event) {
    event.preventDefault(); if (pending) return;
    setError(''); setNotice('');
    const invalid = passwordError(password);
    if (invalid || password !== confirmation) { setError(invalid || 'Passwords do not match.'); return; }
    setPending(true);
    try {
      await client.request('/api/v1/me/security/password', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password, ...(user.passwordConfigured ? { currentPassword } : { reauthToken: proof }) }),
      });
      client.endLocalSession(); navigate('/login?notice=security-updated');
    } catch (failure) { setError(failure.message); }
    finally { setCurrentPassword(''); setPassword(''); setConfirmation(''); setProof(''); setPending(false); }
  }
  const enabled = user.passwordConfigured || Boolean(proof);
  return <div className="profile-security-settings">
    <p className="profile-note">Email and password changes end all active sessions. Your assigned roles and linked Google identity stay unchanged.</p>
    {notice && <p className="profile-banner" role="status">{notice}</p>}
    {error && <p className="profile-banner error" role="alert">{error}</p>}
    {!user.passwordConfigured && <div className="profile-stepup">
      <p>{proof ? 'Google verified for one security change. Complete it within five minutes.' : 'Verify your linked Google account before changing your email or setting a password.'}</p>
      <button type="button" className="profile-secondary" disabled={pending} onClick={google}>Verify with Google</button>
    </div>}
    {user.emailVerified && <details className="profile-security-action"><summary>Change email</summary>
      <p className="profile-note">Current email: {user.email}. Confirm the link sent to the new inbox to finish the change.</p>
      <form className="profile-link-form" onSubmit={changeEmail}>
        <label htmlFor="new-email">New email address</label><input id="new-email" type="email" autoComplete="email" maxLength={320} required value={email} disabled={pending} onChange={event => setEmail(event.target.value)} />
        {user.passwordConfigured && <><label htmlFor="email-current-password">Confirm current password</label><input id="email-current-password" type="password" autoComplete="current-password" maxLength={256} required disabled={pending} value={emailPassword} onChange={event => setEmailPassword(event.target.value)} /></>}
        <button className="profile-primary" disabled={pending || !enabled}>{pending ? 'Please wait…' : 'Send confirmation link'}</button>
      </form>
    </details>}
    {!user.emailVerified && <p className="profile-note">Verify your email inbox before changing your sign-in email.</p>}
    <details className="profile-security-action"><summary>{user.passwordConfigured ? 'Change password' : 'Set a password'}</summary>
      <form className="profile-link-form" onSubmit={changePassword}>
        {user.passwordConfigured && <><label htmlFor="security-current-password">Current password</label><input id="security-current-password" type="password" autoComplete="current-password" maxLength={256} required disabled={pending} value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></>}
        <label htmlFor="security-new-password">New password</label><input id="security-new-password" type="password" autoComplete="new-password" required disabled={pending} value={password} onChange={event => setPassword(event.target.value)} />
        <small className="profile-note">Use 8–128 characters.</small>
        <label htmlFor="security-confirm-password">Confirm new password</label><input id="security-confirm-password" type="password" autoComplete="new-password" required disabled={pending} value={confirmation} onChange={event => setConfirmation(event.target.value)} />
        <button className="profile-primary" disabled={pending || !enabled}>{pending ? 'Please wait…' : user.passwordConfigured ? 'Change password' : 'Set password'}</button>
      </form>
    </details>
  </div>;
}
