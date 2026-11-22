import { useLayoutEffect, useState } from 'react';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';
import FormField from '../../components/FormField.jsx';
import { authPost } from './apiClient.js';
import './recovery.css';

export function RequestVerificationPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const value = email.replace(/^ +| +$/g, '');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || value.length > 320) {
      setError('Enter a valid email address.'); return;
    }
    setPending(true); setError('');
    try { await authPost('/api/v1/auth/email-verification/request', { email: value }); setSent(true); }
    catch (failure) { setError(failure.status === 429 ? 'Too many attempts. Please try again later.' : failure.message); }
    finally { setPending(false); }
  }
  return <AuthLayout><div className="registration-card recovery-card"><Brand />
    {sent ? <div className="recovery-result" role="status"><h2>Check your email</h2>
      <p>If this account needs email verification, a link will be sent.</p><a href="/login">Back to login</a></div>
      : <><h2>Verify your email</h2><p className="form-subtitle">Enter your account email to request a verification link.</p>
        <form onSubmit={submit} noValidate aria-busy={pending}>
          <FormField name="email" label="Email address" type="email" autoComplete="email" value={email}
            onChange={event => setEmail(event.target.value)} error={error} maxLength={320} disabled={pending} required />
          <button className="primary-button" disabled={pending}>{pending ? 'Sending…' : 'Send verification link'}</button>
        </form><a className="recovery-back" href="/login">← Back to login</a></>}
  </div></AuthLayout>;
}

export function VerifyEmailPage() {
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') || '');
  useLayoutEffect(() => { window.history.replaceState(null, '', window.location.pathname); }, []);
  const [pending, setPending] = useState(false);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState('');
  async function confirm() {
    setPending(true); setError('');
    try { await authPost('/api/v1/auth/email-verification/confirm', { token }); setVerified(true); }
    catch (failure) { setError(failure.code === 'INVALID_VERIFICATION_TOKEN'
      ? 'This verification link is invalid or has expired. Request a new link.' : failure.message); }
    finally { setPending(false); }
  }
  return <AuthLayout><div className="registration-card recovery-card"><Brand /><div className="recovery-result">
    {verified ? <div role="status"><h2>Email verified</h2><p>You can now sign in with your password.</p><a href="/login">Continue to login</a></div>
      : <><h2>Confirm your email</h2><p>Confirm that you can receive messages at this address.</p>
        {error && <p className="form-alert" role="alert">{error}</p>}
        {token && !error && <button className="primary-button" onClick={confirm} disabled={pending}>{pending ? 'Verifying…' : 'Verify email'}</button>}
        {!token && <p role="alert">This verification link is missing. Request a new one.</p>}
        <a href="/verify-email/request">Request a new link</a></>}
  </div></div></AuthLayout>;
}
