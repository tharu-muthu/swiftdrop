import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useAuth } from './AuthProvider.jsx';
import PortalShell from '../../components/PortalShell.jsx';
import { navigate } from '../../app/navigation.js';
import './profile.css';
import './admin-users.css';
import { VehicleTypeOptions } from '../dispatch/VehicleTypeOptions.jsx';

const ROLES = [
  { value: 'DISPATCHER_ADMIN', label: 'Dispatcher admins', singular: 'Dispatcher administrator', description: 'Manage operations and access' },
  { value: 'COURIER', label: 'Couriers', singular: 'Courier', description: 'Complete assigned deliveries' },
  { value: 'MERCHANT', label: 'Merchants', singular: 'Merchant', description: 'Manage business deliveries', readOnly: true },
  { value: 'CUSTOMER', label: 'Customers', singular: 'Customer', description: 'Book and track deliveries', readOnly: true },
];
const formatDate = value => value ? new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium' }).format(new Date(value)) : '—';
const initials = name => (name || '?').split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase();

export default function AdminUsersPage() {
  const { user: actor, client } = useAuth();
  const [selectedRole, setSelectedRole] = useState('DISPATCHER_ADMIN');
  const role = ROLES.find(item => item.value === selectedRole);
  const [counts, setCounts] = useState({});
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [selectedSeed, setSelectedSeed] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusReason, setStatusReason] = useState('');
  const [roleReason, setRoleReason] = useState('');
  const [roleChoice, setRoleChoice] = useState('DISPATCHER_ADMIN');
  const [roleAction, setRoleAction] = useState('grant');
  const [assignOpen, setAssignOpen] = useState(false);
  const [directoryQuery, setDirectoryQuery] = useState('');
  const [directory, setDirectory] = useState([]);
  const [directoryLoading, setDirectoryLoading] = useState(false);
  const [picked, setPicked] = useState([]);
  const [courierProfile, setCourierProfile] = useState({ vehicleType: '', licensePlate: '', active: true });
  const [roleRemoval, setRoleRemoval] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const detailRequest = useRef(0);

  useEffect(() => {
    let live = true;
    setLoading(true); setError('');
    client.request(`/api/v1/admin/users?query=${encodeURIComponent(search)}&role=${selectedRole}&page=${page}&size=20`)
      .then(data => { if (live) setResult(data); })
      .catch(failure => { if (live) setError(failure.message || 'Unable to load accounts.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [client, search, selectedRole, page, revision]);

  useEffect(() => {
    let live = true;
    Promise.all(ROLES.map(item => client.request(`/api/v1/admin/users?role=${item.value}&page=0&size=1`)))
      .then(pages => { if (live) setCounts(Object.fromEntries(ROLES.map((item, index) => [item.value, pages[index].totalElements]))); })
      .catch(() => {});
    return () => { live = false; };
  }, [client, revision]);

  useEffect(() => {
    if (!assignOpen) return undefined;
    if (directoryQuery.trim().length < 3) {
      setDirectory([]);
      setDirectoryLoading(false);
      return undefined;
    }
    let live = true;
    const timer = window.setTimeout(() => {
      setDirectoryLoading(true);
      client.request(`/api/v1/admin/users?query=${encodeURIComponent(directoryQuery.trim())}&page=0&size=20`)
        .then(data => { if (live) setDirectory(data.items.filter(account => !account.roles.includes(selectedRole))); })
        .catch(failure => { if (live) setError(failure.message || 'Unable to search accounts.'); })
        .finally(() => { if (live) setDirectoryLoading(false); });
    }, 250);
    return () => { live = false; window.clearTimeout(timer); };
  }, [assignOpen, client, directoryQuery, selectedRole]);

  function closeDetails() {
    ++detailRequest.current;
    setSelectedId(null); setSelectedSeed(null); setSelected(null); setDetailLoading(false);
    setStatusReason(''); setRoleReason('');
  }
  function closeAssign() {
    if (busy) return;
    setAssignOpen(false); setDirectoryQuery(''); setDirectory([]); setPicked([]); setCourierProfile({ vehicleType: '', licensePlate: '', active: true });
  }

  useEffect(() => {
    if (!selectedId && !assignOpen) return undefined;
    const onKeyDown = event => { if (event.key === 'Escape') assignOpen ? closeAssign() : closeDetails(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedId, assignOpen, busy]);

  async function openDetails(account) {
    const requestId = ++detailRequest.current;
    setSelectedId(account.id); setSelectedSeed(account); setSelected(null); setDetailLoading(true);
    setError(''); setNotice(''); setStatusReason(''); setRoleReason(''); setRoleChoice(selectedRole);
    try {
      const data = await client.request(`/api/v1/admin/users/${account.id}`);
      if (requestId === detailRequest.current) { setSelected(data); setDetailLoading(false); }
    } catch (failure) {
      if (requestId === detailRequest.current) { setError(failure.message || 'Unable to load the account.'); setDetailLoading(false); }
    }
  }

  async function reloadDetails() {
    if (selected || selectedSeed) await openDetails(selected || selectedSeed);
  }

  async function assignUsers() {
    if (busy || picked.length !== 1 || (selectedRole === 'COURIER' && !courierProfile.vehicleType.trim())) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const account = picked[0];
      await client.request(`/api/v1/admin/users/${account.id}/roles/grant`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: selectedRole, reason: `Assigned ${selectedRole} through user management`, expectedVersion: account.version }),
      });
      if (selectedRole === 'COURIER') {
        await client.request(`/api/v1/dispatch/couriers/${account.id}`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...courierProfile, vehicleType: courierProfile.vehicleType.trim(), licensePlate: courierProfile.licensePlate.trim() }),
        });
      }
      setAssignOpen(false); setDirectoryQuery(''); setDirectory([]); setPicked([]); setCourierProfile({ vehicleType: '', licensePlate: '', active: true });
      setRevision(value => value + 1);
      setNotice(`${role.singular} access assigned to ${account.fullName}. Existing sessions were revoked.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'An account changed while you were assigning access. Refresh and try again.' : failure.message || 'Unable to assign access.');
    } finally { setBusy(false); }
  }

  async function changeStatus() {
    if (busy || !selected || !statusReason.trim()) return;
    setBusy(true); setError(''); setNotice('');
    const action = selected.active ? 'suspend' : 'reactivate';
    try {
      const updated = await client.request(`/api/v1/admin/users/${selected.id}/${action}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: statusReason.trim(), expectedVersion: selected.version }) });
      setSelected(updated); setStatusReason(''); setRevision(value => value + 1);
      setNotice(`Account ${updated.active ? 'reactivated' : 'suspended'}. Previous sessions were revoked.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account changed or this is the last active administrator. Reload and review it.' : failure.message || 'Unable to change account status.');
    } finally { setBusy(false); }
  }

  async function toggleStatus(account) {
    if (busy || (account.active && account.id === actor.id)) return;
    setBusy(true); setError(''); setNotice('');
    const action = account.active ? 'suspend' : 'reactivate';
    try {
      const updated = await client.request(`/api/v1/admin/users/${account.id}/${action}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          reason: `${account.active ? 'Disabled' : 'Enabled'} through user management`,
          expectedVersion: account.version,
        }),
      });
      setRevision(value => value + 1);
      setNotice(`${updated.fullName || account.fullName} was ${updated.active ? 'enabled' : 'disabled'}. Previous sessions were revoked.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account changed or this is the last active administrator. Refresh the list and try again.' : failure.message || 'Unable to change account status.');
    } finally { setBusy(false); }
  }

  async function removeFromRole(account) {
    const removingOwnAdmin = selectedRole === 'DISPATCHER_ADMIN' && account.id === actor.id;
    if (busy || selectedRole === 'MERCHANT' || account.roles.length <= 1 || removingOwnAdmin) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await client.request(`/api/v1/admin/users/${account.id}/roles/revoke`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: selectedRole,
          reason: `Removed ${selectedRole} through user management`,
          expectedVersion: account.version,
        }),
      });
      setRevision(value => value + 1);
      setNotice(`${role.singular} access was removed from ${account.fullName}. Previous sessions were revoked.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account or its roles changed. Refresh the list and try again.' : failure.message || 'Unable to remove role access.');
    } finally { setBusy(false); }
  }

  async function confirmRoleRemoval() {
    if (busy || !roleRemoval) return;
    if (roleRemoval.role !== 'MERCHANT') {
      await removeFromRole(roleRemoval.account);
      setRoleRemoval(null);
      return;
    }
    setBusy(true); setError(''); setNotice('');
    try {
      await client.request(`/api/v1/admin/merchants/owners/${roleRemoval.account.id}/suspend`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'Merchant access removed through user management' }),
      });
      const name = roleRemoval.account.fullName;
      setRoleRemoval(null);
      setRevision(value => value + 1);
      setNotice(`Merchant access was removed from ${name}. The merchant approval is now suspended.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'This merchant is no longer approved or was already changed. Refresh and try again.' : failure.message || 'Unable to remove merchant access.');
    } finally { setBusy(false); }
  }

  async function changeRole() {
    if (busy || !selected || !roleReason.trim()) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const changed = await client.request(`/api/v1/admin/users/${selected.id}/roles/${roleAction}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role: roleChoice, reason: roleReason.trim(), expectedVersion: selected.version }) });
      if (selected.id === actor.id) { try { await client.logout(); } finally { navigate('/login'); } return; }
      setSelected({ ...selected, roles: [...changed.roles], version: changed.version });
      setRoleReason(''); setRevision(value => value + 1);
      setNotice(`${roleChoice.replaceAll('_', ' ')} ${roleAction === 'grant' ? 'granted' : 'removed'}. The account must sign in again.`);
    } catch (failure) {
      setError(failure.status === 409 ? 'The account or roles changed. Reload this account before trying again.' : failure.message || 'Unable to change roles.');
    } finally { setBusy(false); }
  }

  return <PortalShell title="User management" contentClassName="profile-content admin-users-content">
    {notice && <p className="profile-banner" role="status">{notice}</p>}
    {error && <p className="profile-banner error" role="alert">{error}</p>}
    <div className="admin-workspace">
      <aside className="admin-role-panel" aria-label="Role assignment">
        <div className="admin-panel-heading"><h2>Role assignment</h2><p>Select a role to see who can access that portal.</p></div>
        <div className="admin-role-list">{ROLES.map(item => <button key={item.value} type="button" className={selectedRole === item.value ? 'admin-role-card active' : 'admin-role-card'} onClick={() => { setSelectedRole(item.value); setRoleChoice(item.value); setPage(0); closeDetails(); }} aria-pressed={selectedRole === item.value}><span><strong>{item.label}</strong><small>{item.description}</small></span><b>{counts[item.value] ?? '—'}</b></button>)}</div>
      </aside>
      <section className="admin-users-panel">
        <div className="admin-users-header"><div><h2>Users with {role.singular.toLowerCase()} access</h2><p>{role.description}. Role changes end active sessions.</p></div>{!role.readOnly && <button className="profile-primary admin-add-button" onClick={() => { setAssignOpen(true); setError(''); setNotice(''); }}>+ Assign user</button>}</div>
        <form className="admin-search" onSubmit={event => { event.preventDefault(); setPage(0); setSearch(query.trim()); }}><label htmlFor="admin-user-search">Search by name or email</label><div><input id="admin-user-search" value={query} onChange={event => setQuery(event.target.value)} maxLength={200} placeholder="Search this role" /><button className="profile-secondary" type="submit">Search</button></div></form>
        {loading ? <p className="admin-empty" role="status">Loading accounts…</p> : result?.items.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>User</th><th>Active</th><th><span className="sr-only">Actions</span></th><th><span className="sr-only">Remove role</span></th></tr></thead><tbody>{result.items.map(account => { const removingOwnAdmin = selectedRole === 'DISPATCHER_ADMIN' && account.id === actor.id; const removalBlocked = account.roles.length <= 1 || removingOwnAdmin; const removalHelp = removingOwnAdmin ? 'You cannot remove your own administrator role' : selectedRole === 'MERCHANT' ? 'Suspend this merchant and remove merchant access' : account.roles.length <= 1 ? 'This is the user’s only role and cannot be removed' : `Remove ${role.singular.toLowerCase()} access`; const statusHelp = account.active && account.id === actor.id ? 'You cannot disable your own account' : account.active ? `Disable ${account.fullName} and end their sessions` : `Enable ${account.fullName}; they must sign in again`; return <tr key={account.id}><td><div className="admin-user-cell"><span className="admin-avatar">{initials(account.fullName)}</span><span><strong>{account.fullName}</strong><small>{account.email}</small></span></div></td><td><div className="admin-toggle-cell" title={statusHelp}><button type="button" role="switch" aria-checked={account.active} aria-label={`${account.active ? 'Disable' : 'Enable'} ${account.fullName}`} className={account.active ? 'admin-status-toggle active' : 'admin-status-toggle'} disabled={busy || (account.active && account.id === actor.id)} onClick={() => toggleStatus(account)}><span /></button>{account.active && account.id === actor.id && <em>Current account</em>}</div></td><td><button className="profile-secondary admin-view-btn" title={`View ${account.fullName}'s account details and role information`} disabled={busy} onClick={() => openDetails(account)}>View</button></td><td>{selectedRole !== 'CUSTOMER' && <span className="admin-remove-wrap" data-tooltip={removalHelp}><button type="button" className="admin-role-remove" aria-label={`${removalHelp} for ${account.fullName}`} disabled={busy || (selectedRole !== 'MERCHANT' && removalBlocked)} onClick={() => setRoleRemoval({ account, role: selectedRole, roleLabel: role.singular })}><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13M10 11v5m4-5v5" /></svg></button></span>}</td></tr>; })}</tbody></table></div> : !error && <div className="admin-empty"><strong>No users in this role</strong><p>Assign an eligible account or change your search.</p></div>}
        {result && <div className="admin-pagination"><span>{result.totalElements} user{result.totalElements === 1 ? '' : 's'} · Page {page + 1} of {Math.max(1, result.totalPages)}</span><button className="profile-secondary" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>Previous</button><button className="profile-secondary" disabled={page + 1 >= result.totalPages || loading} onClick={() => setPage(page + 1)}>Next</button></div>}
      </section>
    </div>
    {assignOpen && typeof document !== 'undefined' && createPortal(<AssignDialog role={role} directoryQuery={directoryQuery} setDirectoryQuery={setDirectoryQuery} directory={directory} directoryLoading={directoryLoading} picked={picked} setPicked={setPicked} courierProfile={courierProfile} setCourierProfile={setCourierProfile} busy={busy} close={closeAssign} submit={assignUsers} />, document.body)}
    {roleRemoval && typeof document !== 'undefined' && createPortal(<RoleRemovalDialog removal={roleRemoval} busy={busy} close={() => { if (!busy) setRoleRemoval(null); }} submit={confirmRoleRemoval} />, document.body)}
    {selectedId && typeof document !== 'undefined' && createPortal(<DetailsDialog account={selected} seed={selectedSeed} loading={detailLoading} close={closeDetails} />, document.body)}
  </PortalShell>;
}

function RoleRemovalDialog({ removal, busy, close, submit }) {
  const { account, roleLabel, role } = removal;
  return <div className="admin-modal-backdrop" onClick={close}><div className="admin-modal admin-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="role-remove-title" onClick={event => event.stopPropagation()}>
    <div className="admin-modal-header admin-remove-header"><span className="admin-warning-icon" aria-hidden="true">!</span><div className="admin-modal-title-group"><h2 id="role-remove-title">Remove {account.fullName} from {roleLabel}</h2><p>This user will lose all privileges associated with the {roleLabel} role.</p></div><button className="admin-modal-close" onClick={close} aria-label="Close">×</button></div>
    <div className="admin-modal-body"><div className="admin-removal-user"><span className="admin-avatar">{initials(account.fullName)}</span><span><strong>{account.fullName}</strong><small>{account.email}</small></span></div>{role === 'MERCHANT' && <p className="admin-confirm-copy">The merchant approval will also be suspended and merchant API access will stop.</p>}</div>
    <div className="admin-modal-footer"><button className="profile-secondary" onClick={close} disabled={busy}>Cancel</button><button className="admin-confirm-danger" onClick={submit} disabled={busy}>{busy ? 'Removing…' : 'Remove User'}</button></div>
  </div></div>;
}

function AssignDialog({ role, directoryQuery, setDirectoryQuery, directory, directoryLoading, picked, setPicked, courierProfile, setCourierProfile, busy, close, submit }) {
  return <div className="admin-modal-backdrop" onClick={close}><div className="admin-modal admin-assign-modal" role="dialog" aria-modal="true" aria-labelledby="assign-title" onClick={event => event.stopPropagation()}>
    <div className="admin-modal-header"><div className="admin-modal-title-group"><h2 id="assign-title">Assign {role.singular.toLowerCase()} access</h2><p>Search for active, verified SwiftDrop accounts.</p></div><button className="admin-modal-close" onClick={close} aria-label="Close" title="Close without assigning a user">×</button></div>
    <div className="admin-modal-body"><label className="admin-field" htmlFor="directory-search"><span>Find a user</span><input id="directory-search" value={directoryQuery} onChange={event => { setDirectoryQuery(event.target.value); setPicked([]); }} placeholder="Search by name or email" autoFocus /><small className={directoryQuery.trim().length > 0 && directoryQuery.trim().length < 3 ? 'admin-search-hint pending' : 'admin-search-hint'}>Enter at least 3 characters to search.</small></label>
      {directoryQuery.trim().length >= 3 && <div className="admin-directory" aria-live="polite">{directoryLoading ? <p>Searching…</p> : directory.length ? directory.map(account => { const chosen = picked[0]?.id === account.id; const eligible = account.active && account.emailVerified; const selectionHelp = chosen ? 'This user is selected' : !account.active ? 'Suspended accounts cannot receive a role' : !account.emailVerified ? 'The user must verify their email before receiving a role' : `Select ${account.fullName} for this role`; return <div className={chosen ? 'admin-directory-row selected' : 'admin-directory-row'} key={account.id}><span className="admin-avatar">{initials(account.fullName)}</span><span><strong>{account.fullName}</strong><small>{account.email}</small>{!eligible && <em>{!account.active ? 'Suspended account' : 'Email not verified'}</em>}</span><span title={selectionHelp}><button className={chosen ? 'profile-secondary admin-user-select selected' : 'profile-primary admin-user-select'} disabled={!eligible || chosen} onClick={() => setPicked([account])}>{chosen ? 'Selected' : 'Select user'}</button></span></div>; }) : <p>No unassigned accounts found.</p>}</div>}
      {role.value === 'COURIER' && <fieldset className="admin-courier-fields"><legend>Courier details</legend><label className="admin-field" htmlFor="assign-vehicle"><span>Vehicle type *</span><select id="assign-vehicle" value={courierProfile.vehicleType} onChange={event => setCourierProfile({ ...courierProfile, vehicleType: event.target.value })}><option value="">Select a vehicle type</option><VehicleTypeOptions /></select></label><label className="admin-field" htmlFor="assign-plate"><span>License plate</span><input id="assign-plate" maxLength={20} value={courierProfile.licensePlate} onChange={event => setCourierProfile({ ...courierProfile, licensePlate: event.target.value })} /></label><label className="admin-availability"><input type="checkbox" checked={courierProfile.active} onChange={event => setCourierProfile({ ...courierProfile, active: event.target.checked })} /> Available for assignments</label></fieldset>}
    </div><div className="admin-modal-footer"><button className="profile-secondary" onClick={close} disabled={busy} title="Close without assigning a user">Cancel</button><span title={busy ? 'Assigning this role' : picked.length !== 1 ? 'Select one user first' : role.value === 'COURIER' && !courierProfile.vehicleType.trim() ? 'Select a vehicle type first' : `Give ${picked[0].fullName} ${role.singular.toLowerCase()} access`}><button className="profile-primary" onClick={submit} disabled={busy || picked.length !== 1 || (role.value === 'COURIER' && !courierProfile.vehicleType.trim())}>{busy ? 'Assigning…' : `Assign ${role.singular}`}</button></span></div>
  </div></div>;
}

function DetailsDialog({ account, seed, loading, close }) {
  const shown = account || seed;
  return <div className="admin-modal-backdrop" onClick={close}><div className="admin-modal admin-details-modal" role="dialog" aria-modal="true" aria-label="Account details modal" onClick={event => event.stopPropagation()}><div className="admin-modal-header"><div className="admin-modal-title-group"><h2>{shown?.fullName || 'Loading account…'}</h2>{shown && <p>{shown.email}</p>}</div><div className="admin-modal-controls"><button className="admin-modal-close" onClick={close} aria-label="Close modal">×</button></div></div>
    <div className="admin-modal-body">{loading && !account ? <p role="status">Loading account details…</p> : account && <section className="profile-card admin-modal-card" aria-label="Account details"><dl className="admin-details"><div><dt>Status</dt><dd>{account.active ? 'Active' : 'Suspended'}</dd></div><div><dt>Email verification</dt><dd>{account.emailVerified ? 'Verified' : 'Not verified'}</dd></div><div><dt>Roles</dt><dd>{account.roles.join(', ')}</dd></div><div><dt>Phone</dt><dd>{account.phone || 'Not provided'}</dd></div><div><dt>Joined</dt><dd>{formatDate(account.createdAt)}</dd></div></dl>
    </section>}</div></div></div>;
}
