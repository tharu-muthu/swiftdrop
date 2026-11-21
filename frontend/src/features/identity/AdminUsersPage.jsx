import { useEffect, useRef, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import Brand from '../../components/Brand.jsx';
import { navigate } from '../../app/navigation.js';
import './profile.css';
import './admin-users.css';
import CourierProfileSetup from '../dispatch/CourierProfileSetup.jsx';

const date = value => value ? new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium' }).format(new Date(value)) : '—';

export default function AdminUsersPage() {
  const { user: actor, client } = useAuth();
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [reason, setReason] = useState('');
  const [roleReason, setRoleReason] = useState('');
  const [roleChoice, setRoleChoice] = useState('COURIER');
  const [roleAction, setRoleAction] = useState('grant');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const detailRequest = useRef(0);

  useEffect(() => {
    let live = true;
    setLoading(true); setError('');
    client.request(`/api/v1/admin/users?query=${encodeURIComponent(search)}&page=${page}&size=20`)
      .then(data => { if (live) setResult(data); })
      .catch(failure => { if (live) setError(failure.message || 'Unable to load accounts.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [client, search, page, revision]);

  async function choose(id) {
    const requestId = ++detailRequest.current;
    setError(''); setNotice(''); setSelected(null); setReason(''); setRoleReason('');
    try {
      const account = await client.request(`/api/v1/admin/users/${id}`);
      if (requestId === detailRequest.current) setSelected(account);
    } catch (failure) { if (requestId === detailRequest.current) setError(failure.message || 'Unable to load the account.'); }
  }

  async function changeStatus() {
    if (busy || !selected || !reason.trim() || reason.length > 500) return;
    setBusy(true); setError(''); setNotice('');
    const action = selected.active ? 'suspend' : 'reactivate';
    try {
      const updated = await client.request(`/api/v1/admin/users/${selected.id}/${action}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: reason.trim(), expectedVersion: selected.version }),
      });
      setSelected(updated); setReason(''); setRevision(value => value + 1);
      setNotice(`Account ${updated.active ? 'reactivated' : 'suspended'}. Previous sessions were revoked.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account changed or this is the last active administrator. Reload the account and review it.' : failure.message || 'Unable to change account status.');
    } finally { setBusy(false); }
  }

  async function changeRole() {
    if (busy || !selected || !roleReason.trim() || roleReason.length > 500) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await client.request(`/api/v1/admin/users/${selected.id}/roles/${roleAction}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: roleChoice, reason: roleReason.trim(), expectedVersion: selected.version }),
      });
      if (selected.id === actor.id) {
        try { await client.logout(); } finally { navigate('/login'); }
        return;
      }
      setSelected({ ...selected, roles: [...result.roles], version: result.version });
      setRoleReason(''); setRevision(value => value + 1);
      setNotice(`${roleChoice.replaceAll('_', ' ')} ${roleAction === 'grant' ? 'granted' : 'removed'}. The account must sign in again.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account or roles changed. Reload this account before trying again.' : failure.message || 'Unable to change roles.');
    } finally { setBusy(false); }
  }

  return <div className="profile-shell">
    <aside className="profile-sidebar" aria-label="Account navigation"><Brand /><nav><span className="profile-nav-label">WORKSPACE</span>
      <a href="/workspaces">All workspaces</a><a href="/dispatch">Dispatch</a><a href="/admin/users" className="selected" aria-current="page">User management</a>
      <span className="profile-nav-label">ACCOUNT</span><a href="/profile">My profile</a></nav></aside>
    <main className="profile-main"><header className="profile-topbar"><span className="profile-breadcrumb">Administration / Users</span><span>{actor.fullName}</span></header>
      <div className="profile-content"><h1>User management</h1><p className="profile-subtitle">Find accounts and manage access. Changes take effect immediately.</p>
        {notice && <p className="profile-banner" role="status">{notice}</p>}
        {error && <p className="profile-banner error" role="alert">{error}</p>}
        <section className="profile-card"><form className="admin-search" onSubmit={event => { event.preventDefault(); ++detailRequest.current; setPage(0); setSearch(query.trim()); setSelected(null); }}>
          <label htmlFor="admin-user-search">Search by name or email</label><div><input id="admin-user-search" disabled={busy} value={query} onChange={event => setQuery(event.target.value)} maxLength={200} placeholder="Name or email" />
            <button className="profile-primary" type="submit" disabled={busy}>Search</button></div></form>
          {loading ? <p role="status">Loading accounts…</p> : result?.items.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th></th></tr></thead>
            <tbody>{result.items.map(account => <tr key={account.id}><td>{account.fullName}</td><td>{account.email}</td><td>{account.roles.join(', ')}</td><td>{account.active ? 'Active' : 'Suspended'}</td><td><button className="profile-secondary" disabled={busy} onClick={() => choose(account.id)}>View</button></td></tr>)}</tbody></table></div>
            : !error && <p>No accounts found.</p>}
          {result && <div className="admin-pagination"><span>{result.totalElements} account{result.totalElements === 1 ? '' : 's'} · Page {page + 1} of {Math.max(1, result.totalPages)}</span>
            <button className="profile-secondary" disabled={page === 0 || loading || busy} onClick={() => setPage(page - 1)}>Previous</button>
            <button className="profile-secondary" disabled={page + 1 >= result.totalPages || loading || busy} onClick={() => setPage(page + 1)}>Next</button></div>}</section>
        {selected && <section className="profile-card" aria-label="Account details"><div className="profile-card-heading"><div><h2>{selected.fullName}</h2><p>{selected.email}</p></div><button className="profile-secondary" disabled={busy} onClick={() => choose(selected.id)}>Reload</button></div>
          <dl className="admin-details"><div><dt>Status</dt><dd>{selected.active ? 'Active' : 'Suspended'}</dd></div><div><dt>Email verification</dt><dd>{selected.emailVerified ? 'Verified' : 'Not verified'}</dd></div>
            <div><dt>Roles</dt><dd>{selected.roles.join(', ')}</dd></div><div><dt>Phone</dt><dd>{selected.phone || 'Not provided'}</dd></div><div><dt>Joined</dt><dd>{date(selected.createdAt)}</dd></div></dl>
          <div className="admin-action"><h3>Account roles</h3>
            <p>Changes end this account's sessions. The person must sign in again. Merchant access is managed through business approval.</p>
            <label htmlFor="role-choice">Role</label><select id="role-choice" value={roleChoice} disabled={busy} onChange={event => setRoleChoice(event.target.value)}>
              <option value="CUSTOMER">Customer</option><option value="COURIER">Courier</option><option value="DISPATCHER_ADMIN">Dispatcher administrator</option>
            </select>
            <label htmlFor="role-action">Action</label><select id="role-action" value={roleAction} disabled={busy} onChange={event => setRoleAction(event.target.value)}><option value="grant">Grant</option><option value="revoke">Remove</option></select>
            <label htmlFor="role-reason">Reason *</label><textarea id="role-reason" maxLength={500} value={roleReason} disabled={busy} onChange={event => setRoleReason(event.target.value)} />
            {!selected.emailVerified && <p>Verify the account email before granting roles.</p>}
            <button className="profile-primary" disabled={busy || !roleReason.trim() || !selected.active || (roleAction === 'grant' ? !selected.emailVerified || selected.roles.includes(roleChoice) : !selected.roles.includes(roleChoice) || selected.roles.length <= 1 || (roleChoice === 'DISPATCHER_ADMIN' && selected.id === actor.id))} onClick={changeRole}>{roleAction === 'grant' ? 'Grant role' : 'Remove role'}</button>
            {selected.roles.includes('COURIER') && <p><a href={`/dispatch?courier=${encodeURIComponent(selected.id)}`}>Complete courier operational profile</a> to make this account available for assignment.</p>}
            <p><a href="/admin/merchants">Review merchant applications</a></p>
          </div>
          {selected.active && selected.emailVerified && selected.roles.includes('COURIER') && <CourierProfileSetup key={selected.id + ':' + selected.version} account={selected} />}
          {selected.active && selected.id === actor.id ? <p className="profile-note">You cannot suspend your own account.</p> : <div className="admin-action"><label htmlFor="admin-reason">Reason for {selected.active ? 'suspension' : 'reactivation'} *</label>
            <textarea id="admin-reason" value={reason} onChange={event => setReason(event.target.value)} maxLength={500} rows={3} placeholder="Record why this change is needed" />
            <button className={selected.active ? 'admin-danger' : 'profile-primary'} disabled={busy || !reason.trim()} onClick={changeStatus}>{busy ? 'Saving…' : selected.active ? 'Suspend account' : 'Reactivate account'}</button></div>}</section>}
      </div></main>
  </div>;
}
