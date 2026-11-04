import { useRef, useState } from 'react';
import AuthLayout from '../../components/AuthLayout.jsx';
import Brand from '../../components/Brand.jsx';
import FormField from '../../components/FormField.jsx';
import { registerCustomer } from './registrationApi.js';
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH, passwordError, passwordLength } from './passwordPolicy.js';

const empty = { fullName: '', email: '', password: '', confirmPassword: '' };
export default function RegisterPage() {
  const [fields, setFields] = useState(empty);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const [created, setCreated] = useState(false);
  const submitting = useRef(false);
  const feedback = useRef(null);

  function change(event) {
    const { name, value } = event.target;
    setFields(current => ({ ...current, [name]: value }));
    setErrors(current => {
      const next = { ...current, [name]: undefined };
      // Once validation has failed, keep its feedback accurate while correcting it.
      if (name === 'password' && current.password) next.password = passwordError(value);
      if ((name === 'password' || name === 'confirmPassword') && current.confirmPassword) {
        const password = name === 'password' ? value : fields.password;
        const confirmation = name === 'confirmPassword' ? value : fields.confirmPassword;
        next.confirmPassword = !confirmation ? 'Confirm your password.'
          : password !== confirmation ? 'Passwords must match.' : undefined;
      }
      return next;
    });
  }
  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    const validation = {};
    if (!fields.fullName.trim()) validation.fullName = 'Enter your full name.';
    else if (fields.fullName.trim().length > 200) validation.fullName = 'Use at most 200 characters.';
    const email = fields.email.replace(/^ +| +$/g, '');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 320) validation.email = 'Enter a valid email address.';
    const invalidPassword = passwordError(fields.password);
    if (invalidPassword) validation.password = invalidPassword;
    if (!fields.confirmPassword) validation.confirmPassword = 'Confirm your password.';
    else if (fields.confirmPassword !== fields.password) validation.confirmPassword = 'Passwords must match.';
    setErrors(validation);
    setMessage('');
    if (Object.keys(validation).length) {
      document.getElementById(Object.keys(validation)[0])?.focus();
      return;
    }
    submitting.current = true;
    setPending(true);
    try {
      await registerCustomer({ ...fields, fullName: fields.fullName.trim(), email });
      setFields(empty);
      setCreated(true);
    } catch (error) {
      setErrors(Object.fromEntries((error.fieldErrors || []).filter(item => ['fullName', 'email', 'password'].includes(item.field)).map(item => [item.field, item.message])));
      setMessage(error.name === 'AbortError'
        ? 'The request timed out. Your account may have been created. Check before trying again.'
        : error instanceof TypeError ? 'Unable to connect. Check your connection and try again.' : error.message);
    } finally {
      submitting.current = false;
      setPending(false);
      // Move focus after React renders feedback; no credentials are persisted in browser storage.
      setTimeout(() => feedback.current?.focus(), 0);
    }
  }
  return <AuthLayout><div className="registration-card">
    <Brand />
    {created ? <div className="registration-success" ref={feedback} tabIndex="-1" role="status">
      <span className="success-check" aria-hidden="true">✓</span><h2>Account created</h2>
      <p>Your customer account is ready. You have not been signed in.</p>
      <p><a href="/login">Continue to login</a></p>
      <button className="primary-button" onClick={() => { setCreated(false); setMessage(''); }}>Back to registration</button>
    </div> : <>
      <h2>Create your account</h2><p className="form-subtitle">Start shipping smarter with SwiftDrop.</p>
      <form onSubmit={submit} noValidate aria-busy={pending}>
        <FormField name="fullName" label="Full name" placeholder="Enter your full name" autoComplete="name" maxLength={200} value={fields.fullName} onChange={change} error={errors.fullName} disabled={pending} required />
        <FormField name="email" label="Email address" placeholder="name@company.com" type="email" autoComplete="email" maxLength={320} value={fields.email} onChange={change} error={errors.email} disabled={pending} required />
        <FormField name="password" label="Password" placeholder="Create a password" type="password" autoComplete="new-password" value={fields.password} onChange={change} error={errors.password} disabled={pending} hint={`${MIN_PASSWORD_LENGTH}–${MAX_PASSWORD_LENGTH} characters (${passwordLength(fields.password)} entered).`} required />
        <FormField name="confirmPassword" label="Confirm password" placeholder="Re-enter your password" type="password" autoComplete="new-password" value={fields.confirmPassword} onChange={change} error={errors.confirmPassword} disabled={pending} required />
        {message && <p className="form-alert" role="alert" ref={feedback} tabIndex="-1">{message}</p>}
        <button className="primary-button" type="submit" disabled={pending}>{pending ? 'Creating account…' : 'Create account'}</button>
      </form>
      <p className="registration-note">Creating an account gives you customer access.</p>
      <p className="login-note">Already have an account? <a href="/login">Log in</a></p>
    </>}
  </div></AuthLayout>;
}
