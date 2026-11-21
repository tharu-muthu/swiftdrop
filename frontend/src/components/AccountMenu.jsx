import { useEffect, useId, useRef, useState } from 'react';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import { navigate } from '../app/navigation.js';
import AppLink from './AppLink.jsx';
import './account-menu.css';

const roleNames = { CUSTOMER: 'Customer', MERCHANT: 'Merchant', COURIER: 'Courier', DISPATCHER_ADMIN: 'Dispatcher administrator' };

export default function AccountMenu() {
  const { user, client } = useAuth();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const container = useRef(null);
  const trigger = useRef(null);
  const menu = useRef(null);
  const loggingOut = useRef(false);
  const panelId = useId();
  const initials = user.fullName?.trim().split(/\s+/).map(part => part[0]).slice(0, 2).join('').toUpperCase();

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector('[role="menuitem"]')?.focus();
    const outside = event => { if (!container.current?.contains(event.target)) setOpen(false); };
    const escape = event => {
      if (event.key === 'Escape') { event.preventDefault(); setOpen(false); trigger.current?.focus(); }
    };
    const close = () => setOpen(false);
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    window.addEventListener('popstate', close);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
      window.removeEventListener('popstate', close);
    };
  }, [open]);

  function moveFocus(event) {
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const items = [...menu.current.querySelectorAll('[role="menuitem"]')].filter(item => !item.disabled);
    const current = items.indexOf(document.activeElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
      : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    items[next]?.focus();
  }

  async function signOut() {
    if (loggingOut.current) return;
    loggingOut.current = true; setPending(true); setError('');
    try { await client.logout(); navigate('/login'); }
    catch {
      // The real client clears identity immediately and retains its safe retry error.
      if (client.getSnapshot().status === 'anonymous') navigate('/login');
      else setError('Sign-out could not be confirmed. Please retry.');
    } finally { loggingOut.current = false; setPending(false); }
  }

  return <div className="account-dropdown" ref={container} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
  }}>
    <button type="button" className="account-dropdown-trigger" ref={trigger}
      aria-label="Account menu" aria-haspopup="menu" aria-expanded={open} aria-controls={open ? panelId : undefined}
      onClick={() => setOpen(value => !value)} onKeyDown={event => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); setOpen(true); }
      }}>
      <span className="portal-avatar" aria-hidden="true">{initials}</span>
      <span className="portal-person"><strong>{user.fullName}</strong><small>{user.email || 'My account'}</small></span>
      <svg className="account-dropdown-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    {open && <div className="account-dropdown-panel" id={panelId}>
      <div className="account-dropdown-summary">
        <span className="portal-avatar" aria-hidden="true">{initials}</span>
        <div><strong>{user.fullName}</strong>{user.email && <span className="account-dropdown-email">{user.email}</span>}
          <div className="account-dropdown-roles">{user.roles.map(role => <span key={role}>{roleNames[role] || role}</span>)}</div>
        </div>
      </div>
      <div role="menu" aria-label="Account options" ref={menu} onKeyDown={moveFocus}>
        <AppLink href="/account" role="menuitem" onClick={() => setOpen(false)}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2" /></svg>
          My account
          <svg className="account-dropdown-next" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
        </AppLink>
        <button type="button" role="menuitem" className="account-dropdown-signout" disabled={pending} onClick={signOut}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 4H4v16h5 M10 12h11 m-5-5 5 5-5 5" /></svg>
          {pending ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
      {error && <p className="account-dropdown-error" role="alert">{error}</p>}
    </div>}
  </div>;
}
