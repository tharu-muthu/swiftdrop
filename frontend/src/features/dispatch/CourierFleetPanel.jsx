import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import AppLink from '../../components/AppLink.jsx';
import { JsonRequest } from '../../components/WorkspacePage.jsx';
import { VEHICLE_TYPES, VehicleTypeOptions } from './VehicleTypeOptions.jsx';
import './courier-fleet.css';

export const vehicleLabel = value => VEHICLE_TYPES.find(([code]) => code === value)?.[1] || value || 'Not provided';
const accountAvailable = row => row.accountEligible ?? row.unavailableReason !== 'ACCOUNT_UNAVAILABLE';
const availability = row => !accountAvailable(row) ? 'Account unavailable' : row.active ? 'Available' : 'Unavailable';

export default function CourierFleetPanel({ couriers, loading, onSaved }) {
  const { client } = useAuth();
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('');
  const [editing, setEditing] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const initialCourier = useRef(new URLSearchParams(window.location.search).get('courier'));
  const editor = useRef(null);
  const searchInput = useRef(null);

  function edit(row) {
    setEditing({ ...row, licensePlate: row.licensePlate || '' });
    setError(''); setNotice('');
  }
  useEffect(() => {
    if (loading || !initialCourier.current) return;
    const match = couriers.find(row => row.courierId === initialCourier.current);
    initialCourier.current = null;
    if (match && accountAvailable(match)) edit(match);
  }, [couriers, loading]);
  useEffect(() => {
    if (editing) editor.current?.focus();
  }, [editing?.courierId]);

  const term = search.trim().toLocaleLowerCase();
  const rows = couriers.filter(row =>
    [row.fullName, row.email, row.licensePlate, vehicleLabel(row.vehicleType)].some(value =>
      value?.toLocaleLowerCase().includes(term))
    && (!filter || availability(row) === filter));

  function close() { setEditing(null); setError(''); searchInput.current?.focus(); }
  async function save(event) {
    event.preventDefault();
    if (pending || !editing?.vehicleType) return;
    setPending(true); setError(''); setNotice('');
    try {
      await client.request(`/api/v1/dispatch/couriers/${editing.courierId}`, JsonRequest('PUT', {
        vehicleType: editing.vehicleType, licensePlate: editing.licensePlate.trim(), active: editing.active,
      }));
      setNotice(`Courier details saved for ${editing.fullName || 'this courier'}.`);
      close(); onSaved();
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  return <section className="courier-fleet" aria-label="Courier fleet">
    <div className="courier-fleet-heading">
      <div><h2>Courier fleet</h2><p>Manage vehicles and availability for your registered couriers.</p></div>
      <AppLink className="button secondary" href="/admin/users?role=COURIER&action=assign">+ Add courier</AppLink>
    </div>
    <p className="courier-fleet-hint">Add couriers and grant access in User Management. Update their operational details here.</p>
    {notice && <p className="notice" role="status">{notice}</p>}
    <div className="courier-fleet-filters">
      <label>Find a courier<input ref={searchInput} type="search" placeholder="Search name, email or license plate" value={search} onChange={event => setSearch(event.target.value)} /></label>
      <label>Availability<select value={filter} onChange={event => setFilter(event.target.value)}>
        <option value="">All couriers</option><option>Available</option><option>Unavailable</option><option>Account unavailable</option>
      </select></label>
    </div>
    {loading ? <p role="status">Loading courier fleet…</p> : !couriers.length
      ? <div className="courier-fleet-empty"><strong>No courier profiles yet</strong><p>Add a courier in User Management and complete their vehicle details to get started.</p></div>
      : <>
        <p className="courier-fleet-count">{rows.length} of {couriers.length} couriers</p>
        <div className="table-scroll"><table><thead><tr><th>Courier</th><th>Vehicle</th><th>Availability</th><th>Active deliveries</th><th><span className="sr-only">Actions</span></th></tr></thead>
          <tbody>{rows.map(row => <tr key={row.courierId}>
            <td><strong>{row.fullName || 'Courier'}</strong><small className="courier-fleet-detail">{row.email || 'Contact details unavailable'}</small></td>
            <td>{vehicleLabel(row.vehicleType)}<small className="courier-fleet-detail">{row.licensePlate || 'No license plate provided'}</small></td>
            <td><span className={row.eligible ? 'courier-fleet-status available' : 'courier-fleet-status'}>{availability(row)}</span></td>
            <td>{row.workload ?? 0}</td>
            <td>{accountAvailable(row) ? <button type="button" className="secondary" disabled={pending} aria-label={`Edit ${row.fullName || 'courier'}`} title="Edit vehicle and availability" onClick={() => edit(row)}>Edit</button>
              : <AppLink href="/admin/users" title="Review this account's access in User Management">Review access</AppLink>}</td>
          </tr>)}</tbody></table></div>
        {!rows.length && <p className="courier-fleet-empty">No couriers match your search or availability filter.</p>}
      </>}
    {editing && <div className="courier-fleet-editor">
      <h3 ref={editor} tabIndex={-1}>Edit courier details</h3>
      <p><strong>{editing.fullName || 'Courier'}</strong>{editing.email && <span className="courier-fleet-detail">{editing.email}</span>}</p>
      {error && <p className="error" role="alert">{error}</p>}
      <form onSubmit={save}>
        <div className="form-grid">
          <label>Vehicle type<select required disabled={pending} value={editing.vehicleType} onChange={event => setEditing({ ...editing, vehicleType: event.target.value })}><VehicleTypeOptions current={editing.vehicleType} /></select></label>
          <label>License plate <input maxLength={20} disabled={pending} value={editing.licensePlate} placeholder="e.g. ABC-1234" onChange={event => setEditing({ ...editing, licensePlate: event.target.value })} /></label>
        </div>
        <label className="check-label"><input type="checkbox" disabled={pending} checked={editing.active} onChange={event => setEditing({ ...editing, active: event.target.checked })} />Available for new assignments</label>
        <small>Turning this off stops new assignments. Existing deliveries and account access remain unchanged.</small>
        <div className="actions"><button disabled={pending || loading}>{pending ? 'Saving…' : 'Save changes'}</button><button type="button" className="secondary" disabled={pending} onClick={close}>Cancel</button></div>
      </form>
    </div>}
  </section>;
}
