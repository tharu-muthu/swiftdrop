import { useRef, useState } from 'react';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';
import FormField from '../../components/FormField.jsx';
import { useAuth } from './AuthProvider.jsx';
import { navigate } from '../../app/navigation.js';

export default function LoginPage() {
  const auth = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const submitting = useRef(false);

  const urlError = new URLSearchParams(window.location.search).get('error');
  const googleError = urlError === 'google-failed' ? 'Google sign-in failed. Please try again.'
    : urlError === 'link-required' ? 'This Google account email matches an existing SwiftDrop account. Sign in with your password first.'
    : urlError === 'suspended' ? 'Your account has been suspended.'
    : urlError === 'unverified-email' ? 'Your Google account email is not verified.'
    : '';

  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    const invalid = {};
    if (!email.trim()) invalid.email = 'Enter your email address.';
    if (!password) invalid.password = 'Enter your password.';
    setErrors(invalid); setMessage('');
    if (Object.keys(invalid).length) { document.getElementById(Object.keys(invalid)[0])?.focus(); return; }
    submitting.current = true; setPending(true);
    try {
      await auth.client.login(email, password);
      setPassword(''); navigate('/account');
    } catch (error) { setMessage(error.message); }
    finally { submitting.current = false; setPending(false); }
  }
  return <AuthLayout><div className="registration-card login-card">
    <Brand /><h2>Welcome back</h2><p className="form-subtitle">Log in to your SwiftDrop account</p>
    {googleError && <p className="form-alert" role="alert">{googleError}</p>}
    <form onSubmit={submit} noValidate aria-busy={pending}>
      <FormField name="email" label="Email address" type="email" placeholder="Enter your email" autoComplete="username"
        value={email} onChange={event => setEmail(event.target.value)} error={errors.email} disabled={pending} maxLength={320} required />
      <FormField name="password" label="Password" type="password" placeholder="Enter your password" autoComplete="current-password"
        value={password} onChange={event => setPassword(event.target.value)} error={errors.password} disabled={pending} required />
      <p className="recovery-note">Forget Password?</p>
      {(message || auth.error) && <p className="form-alert" role="alert">{message || auth.error}</p>}
      <button className="primary-button" disabled={pending}>{pending ? 'Logging in…' : 'Log in'}</button>
    </form>
    {auth.error.includes('retry sign-out') && <button className="secondary-button" onClick={() => auth.client.logout().catch(() => {})}>Retry sign-out</button>}
    <div className="auth-divider"><span>or sign in with</span></div>
    <a href="/oauth2/authorization/google" className="google-button" role="button">
      <span aria-hidden="true">G</span> Continue with Google
    </a>
    <p className="login-note">New to SwiftDrop? <a href="/register">Sign up</a></p>
  </div></AuthLayout>;
}
