import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import { VehicleTypeOptions } from './VehicleTypeOptions.jsx';

export default function CourierProfileSetup({ account }) {
  const { client } = useAuth();
  const [profile, setProfile] = useState({ vehicleType: '', licensePlate: '', active: true });
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => {
    let live = true;
    client.request('/api/v1/dispatch/couriers').then(rows => {
      if (!live) return;
      const existing = rows.find(row => row.courierId === account.id);
      if (existing) setProfile({ vehicleType: existing.vehicleType, licensePlate: existing.licensePlate || '', active: existing.active });
      setNotice(existing ? 'Operational profile loaded.' : 'Role granted. Complete the operational profile before assigning deliveries.');
    }).catch(failure => { if (live) { setError(failure.message); setLoadFailed(true); } })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [account.id, client]);
  async function save(event) {
    event.preventDefault(); if (pending || !profile.vehicleType.trim()) return;
    setPending(true); setError(''); setNotice('');
    try {
      await client.request(`/api/v1/dispatch/couriers/${account.id}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...profile, vehicleType: profile.vehicleType.trim(), licensePlate: profile.licensePlate.trim() }),
      });
      setNotice(profile.active ? 'Courier setup complete. This account is available for dispatch while its account and role remain active.' : 'Profile saved. This courier is unavailable for new assignments.');
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  return <div className="admin-action"><h3>Courier setup</h3><p>Operational profile for {account.fullName}. This does not change account roles.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {loading ? <p role="status">Loading courier profile...</p> : loadFailed ? <p>Reload this account to retry loading its current profile.</p> : <form onSubmit={save}>
      <label htmlFor="setup-vehicle">Vehicle type *</label><select id="setup-vehicle" required disabled={pending} value={profile.vehicleType} onChange={event => setProfile({ ...profile, vehicleType: event.target.value })}><option value="">Select a vehicle type</option><VehicleTypeOptions current={profile.vehicleType} /></select>
      <label htmlFor="setup-plate">License plate</label><input id="setup-plate" maxLength={20} disabled={pending} value={profile.licensePlate} onChange={event => setProfile({ ...profile, licensePlate: event.target.value })} />
      <label><input type="checkbox" checked={profile.active} disabled={pending} onChange={event => setProfile({ ...profile, active: event.target.checked })} />Available for assignments</label>
      <button className="profile-primary" disabled={pending || !profile.vehicleType.trim()}>Save courier profile</button>
    </form>}
    <p><a href={`/dispatch?courier=${encodeURIComponent(account.id)}`}>Open dispatch</a></p>
  </div>;
}
