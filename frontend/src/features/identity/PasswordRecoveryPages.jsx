import { useLayoutEffect, useState } from 'react';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';
import FormField from '../../components/FormField.jsx';
import { authPost } from './apiClient.js';
import { passwordError } from './passwordPolicy.js';
import './recovery.css';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const value = email.replace(/^ +| +$/g, '');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || value.length > 320) {
      setError('Enter a valid email address.'); return;
    }
    setPending(true); setError('');
    try { await authPost('/api/v1/auth/password-reset/request', { email: value }); setSubmitted(true); }
    catch (failure) { setError(failure.status === 429 ? 'Too many attempts. Please try again later.' : failure.message); }
    finally { setPending(false); }
  }
  return <AuthLayout><div className="registration-card recovery-card"><Brand />
    {submitted ? <div role="status" className="recovery-result"><h2>Check your email</h2>
      <p>If this account can use password recovery, instructions will be sent.</p>
      <a href="/login">← Back to login</a></div> : <>
      <h2>Forgot your password?</h2><p className="form-subtitle">Enter your email to receive a password reset link.</p>
      <form onSubmit={submit} noValidate aria-busy={pending}>
        <FormField name="email" label="Email address" type="email" placeholder="Enter your registered email" autoComplete="email"
          maxLength={320} value={email} onChange={event => setEmail(event.target.value)} error={error} disabled={pending} required />
        <button className="primary-button" disabled={pending}>{pending ? 'Sending…' : 'Send reset link'}</button>
      </form><p className="recovery-help">If the email is registered, we'll send instructions to reset your password.</p>
      <a className="recovery-back" href="/login">← Back to login</a></>}
  </div></AuthLayout>;
}

export function ResetPasswordPage() {
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') || '');
  useLayoutEffect(() => {
    window.history.replaceState(null, '', window.location.pathname);
    document.title = 'Reset password | SwiftDrop';
  }, []);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [errors, setErrors] = useState({});
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  const [message, setMessage] = useState('');
  async function submit(event) {
    event.preventDefault();
    const next = {};
    next.password = passwordError(password);
    if (!confirmation) next.confirmation = 'Confirm your password.';
    else if (confirmation !== password) next.confirmation = 'Passwords must match.';
    setErrors(next); setMessage('');
    if (next.password || next.confirmation) return;
    setPending(true);
    try { await authPost('/api/v1/auth/password-reset/confirm', { token, password }); setPassword(''); setConfirmation(''); setDone(true); }
    catch (failure) { setMessage(failure.code === 'INVALID_RESET_TOKEN' ? 'This reset link is invalid or has expired. Request a new link.' : failure.message); }
    finally { setPending(false); }
  }
  return <AuthLayout><div className="registration-card recovery-card"><Brand />
    {done ? <div className="recovery-result" role="status"><h2>Password updated</h2><p>Sign in with your new password. Your earlier sessions have ended.</p><a href="/login">Continue to login</a></div>
      : !token ? <div className="recovery-result" role="alert"><h2>Reset link unavailable</h2><p>Request a new password reset link.</p><a href="/forgot-password">Request a new link</a></div>
      : <><h2>Choose a new password</h2><p className="form-subtitle">Use 8–128 characters. Spaces and Unicode are allowed.</p>
        <form onSubmit={submit} noValidate aria-busy={pending}>
          <FormField name="password" label="New password" type="password" autoComplete="new-password" value={password}
            onChange={event => setPassword(event.target.value)} error={errors.password} disabled={pending} required />
          <FormField name="confirmation" label="Confirm new password" type="password" autoComplete="new-password" value={confirmation}
            onChange={event => setConfirmation(event.target.value)} error={errors.confirmation} disabled={pending} required />
          {message && <p className="form-alert" role="alert">{message}</p>}
          <button className="primary-button" disabled={pending}>{pending ? 'Updating…' : 'Reset password'}</button>
        </form><a className="recovery-back" href="/forgot-password">Request a new link</a></>}
  </div></AuthLayout>;
}
