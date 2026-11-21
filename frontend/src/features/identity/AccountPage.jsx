import { useEffect, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import PortalShell from '../../components/PortalShell.jsx';
import './profile.css';
import AccountSecuritySettings from './AccountSecuritySettings.jsx';
import SavedAddressBook from './SavedAddressBook.jsx';

const phonePattern = /^\+?[0-9][0-9 -]*[0-9]$/;
const initials = name => (name || '').split(/\s+/).map(part => part[0]).slice(0, 2).join('').toUpperCase();
const month = date => date ? new Intl.DateTimeFormat('en-LK', { month: 'long', year: 'numeric' }).format(new Date(date)) : '—';

function ProfileField({ id, label, value, editable, onChange, error, help }) {
  return <div className="profile-field">{editable ? <label htmlFor={id}>{label}</label> : <span className="profile-field-label">{label}</span>}
    {editable ? <input id={id} value={value} onChange={onChange} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : help ? `${id}-help` : undefined} />
      : <div id={id} className="profile-value">{value || 'Not provided'}</div>}
    {help && <small id={`${id}-help`}>{help}</small>}
    {error && <small id={`${id}-error`} className="profile-error">{error}</small>}
  </div>;
}

export default function AccountPage() {
  const { user, client } = useAuth();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ fullName: user.fullName, phone: user.phone || '' });
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [conflict, setConflict] = useState(false);
  const [pending, setPending] = useState(false);
  const [linkPassword, setLinkPassword] = useState('');
  async function linkGoogle(event) {
    event.preventDefault(); setPending(true); setMessage('');
    try {
      const result=await client.request('/api/v1/me/google-link', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({ password:linkPassword }) });
      setLinkPassword(''); window.location.assign(result.authorizationUrl);
    } catch(error) { setLinkPassword(''); setMessage(error.status===404 ? 'Google sign-in is not configured for this environment.' : error.message); setPending(false); }
  }
  useEffect(() => { if (!editing) setDraft({ fullName: user.fullName, phone: user.phone || '' }); }, [user, editing]);
  useEffect(() => {
    const warn = event => {
      if (editing && !pending && (draft.fullName !== user.fullName || draft.phone !== (user.phone || ''))) {
        event.preventDefault(); event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [draft, editing, pending, user]);

  function cancel() {
    setDraft({ fullName: user.fullName, phone: user.phone || '' });
    setErrors({}); setMessage(''); setConflict(false); setEditing(false);
  }
  async function save(event) {
    event.preventDefault();
    const fullName = draft.fullName.trim(), phone = draft.phone.trim();
    const next = {};
    if (!fullName) next.fullName = 'Enter your full name.';
    else if (fullName.length > 200) next.fullName = 'Use at most 200 characters.';
    if (phone && (!phonePattern.test(phone) || phone.length > 32 || !/^\d{7,15}$/.test(phone.replace(/[^\d]/g, '')))) next.phone = 'Enter a valid phone number or leave it empty.';
    if (Object.keys(next).length) {
      setErrors(next); document.getElementById(next.fullName ? 'profile-name' : 'profile-phone')?.focus(); return;
    }
    setPending(true); setErrors({}); setMessage('');
    try {
      await client.updateProfile({ fullName, phone: phone || null, version: user.version });
      setEditing(false); setConflict(false); setMessage('Profile updated.');
    } catch (error) {
      if (error.status === 409) { setConflict(true); setMessage('Your profile changed elsewhere. Review the latest details before saving again.'); }
      else if (error.fieldErrors?.length) { setErrors(Object.fromEntries(error.fieldErrors.map(item => [item.field, item.message]))); setMessage('Check the highlighted fields.'); }
      else setMessage(error.message || 'Unable to save your profile. Try again.');
    } finally { setPending(false); }
  }
  async function reload() {
    setPending(true);
    try { await client.reloadProfile(); setEditing(false); setConflict(false); setMessage('Latest profile loaded.'); }
    catch (error) { setMessage(error.message); }
    finally { setPending(false); }
  }
  return <PortalShell title="My profile" contentClassName="profile-content">
    <p className="profile-subtitle">Manage your personal details and account security.</p>
      <section className="profile-card profile-summary" aria-label="Account summary"><span className="profile-avatar" aria-hidden="true">{initials(user.fullName)}</span>
        <div className="profile-summary-person"><strong>{user.fullName}</strong><span>{user.email}</span>
          <div className="profile-badges">{user.roles.map(role => <span className="profile-badge" key={role}>{role.replaceAll('_', ' ')}</span>)}<span className="profile-badge active">{user.active ? 'Active' : 'Inactive'}</span></div>
          <p className="profile-note">Your roles and account status are managed by SwiftDrop.</p>
        </div><div className="profile-membership"><small>MEMBER SINCE</small><strong>{month(user.createdAt)}</strong></div>
      </section>
      <section className="profile-card" aria-labelledby="personal-heading"><div className="profile-card-heading"><div><h2 id="personal-heading">Personal information</h2>
        <p>{editing ? 'Update your contact details below.' : 'Your details for your SwiftDrop account.'}</p></div>
        {!editing && <button className="profile-primary" onClick={() => { setMessage(''); setErrors({}); setEditing(true); }}>Edit profile</button>}</div>
        {message && <p className={`profile-banner${conflict || Object.keys(errors).length ? ' error' : ''}`} role={conflict || Object.keys(errors).length ? 'alert' : 'status'}>{message}</p>}
        {conflict && <button type="button" className="profile-secondary" onClick={reload} disabled={pending}>Review latest details</button>}
        <form onSubmit={save} noValidate><div className="profile-field-grid">
          <ProfileField id="profile-name" label={editing ? 'Full name *' : 'Full name'} value={editing ? draft.fullName : user.fullName} editable={editing} error={errors.fullName} onChange={event => setDraft({ ...draft, fullName: event.target.value })} />
          <ProfileField id="profile-phone" label={editing ? 'Mobile number (optional)' : 'Mobile number'} value={editing ? draft.phone : user.phone || ''} editable={editing} error={errors.phone} onChange={event => setDraft({ ...draft, phone: event.target.value })} />
        </div><ProfileField id="profile-email" label="Email address" value={user.email} help={user.emailVerified ? "Your sign-in email. Use Change email under Security & sign-in to verify a new address." : "Verify your inbox before changing your sign-in email."} />
        {editing && <div className="profile-form-actions"><span>Changes apply to your account only.</span><div><button type="button" className="profile-secondary" onClick={cancel} disabled={pending}>Cancel</button><button type="submit" className="profile-primary" disabled={pending}>{pending ? 'Saving changes…' : 'Save changes'}</button></div></div>}</form>
        <SavedAddressBook />
      </section>
      <section className="profile-card" aria-labelledby="security-heading"><h2 id="security-heading">Security & sign-in</h2>
        {new URLSearchParams(window.location.search).get('google') === 'linked' && <p className="profile-banner" role="status">Google account linked. Reload your profile to see the connection.</p>}
        {new URLSearchParams(window.location.search).get('google') === 'failed' && <p className="profile-banner error" role="alert">Google linking failed. Sign in again and retry.</p>}
        <div className="profile-security-grid"><div><strong>Email verification</strong><span>{user.emailVerified ? 'Verified' : 'Not verified'}</span></div><div><strong>Password</strong><span>{user.passwordConfigured ? 'Password configured' : 'No password configured'}</span></div><div><strong>Google account</strong><span>{user.googleConnected ? 'Connected' : 'Not connected'}</span></div></div>
        {!user.googleConnected && user.passwordConfigured && <form className="profile-link-form" onSubmit={linkGoogle}><label htmlFor="link-password">Confirm your password to connect Google</label><input id="link-password" type="password" autoComplete="current-password" required value={linkPassword} onChange={event=>setLinkPassword(event.target.value)} /><button className="profile-primary" disabled={pending}>Connect Google account</button></form>}
        <AccountSecuritySettings />
      </section>
  </PortalShell>;
}
