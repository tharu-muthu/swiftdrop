import { useEffect, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import Brand from '../../components/Brand.jsx';
import { navigate } from '../../app/navigation.js';
import './profile.css';

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
  async function signOut() {
    setPending(true);
    try { await client.logout(); navigate('/login'); }
    catch { setMessage('Sign-out could not be confirmed. Check your connection and try again.'); setPending(false); }
  }
  const customer = user.roles.includes('CUSTOMER');
  return <div className="profile-shell">
    <aside className="profile-sidebar" aria-label="Account navigation"><Brand />
      <nav><span className="profile-nav-label">WORKSPACE</span>
        {customer && <><a href="/booking">Dashboard</a><a href="/bookings">My bookings</a></>}
        <a href="/tracking">Track parcel</a><span className="profile-nav-label">ACCOUNT</span>
        <a href="/profile" className="selected" aria-current="page">My profile</a></nav>
      <button className="profile-rail-signout" onClick={signOut} disabled={pending}>Sign out</button>
    </aside>
    <main className="profile-main"><header className="profile-topbar">
      <div className="profile-mobile-brand"><Brand /></div><span className="profile-breadcrumb">Account / My profile</span>
      <span className="profile-avatar small" aria-hidden="true">{initials(user.fullName)}</span><span>{user.fullName}</span>
    </header><div className="profile-content"><h1>My profile</h1><p className="profile-subtitle">Manage your personal details and account security.</p>
      <section className="profile-card profile-summary" aria-label="Account summary"><span className="profile-avatar" aria-hidden="true">{initials(user.fullName)}</span>
        <div className="profile-summary-person"><strong>{user.fullName}</strong><span>{user.email}</span>
          <div className="profile-badges">{user.roles.map(role => <span className="profile-badge" key={role}>{role.replaceAll('_', ' ')}</span>)}<span className="profile-badge active">{user.active ? 'Active' : 'Inactive'}</span></div>
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
        </div><ProfileField id="profile-email" label="Email address" value={user.email} help="Your sign-in email. Changing it requires a separate verification process." />
        {editing ? <div className="profile-form-actions"><span>Changes apply to your account only.</span><div><button type="button" className="profile-secondary" onClick={cancel} disabled={pending}>Cancel</button><button type="submit" className="profile-primary" disabled={pending}>{pending ? 'Saving changes…' : 'Save changes'}</button></div></div>
          : <p className="profile-note">Your roles and account status are managed by SwiftDrop.</p>}</form>
      </section>
      <section className="profile-card" aria-labelledby="security-heading"><h2 id="security-heading">Security & sign-in</h2>
        <div className="profile-security-grid"><div><strong>Password</strong><span>{user.passwordConfigured ? 'Password configured' : 'No password configured'}</span></div><div><strong>Google account</strong><span>{user.googleConnected ? 'Connected' : 'Not connected'}</span></div></div>
        <div className="profile-security-footer"><span>Using a shared computer? Sign out when you finish.</span><button type="button" className="profile-secondary" onClick={signOut} disabled={pending}>Sign out</button></div>
      </section>
    </div></main>
  </div>;
}
