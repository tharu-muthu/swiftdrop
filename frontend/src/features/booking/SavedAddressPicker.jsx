import { useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';

export default function SavedAddressPicker({ kind, onSelect }) {
  const { client } = useAuth();
  const [addresses, setAddresses] = useState(null), [pending, setPending] = useState(false), [error, setError] = useState('');
  async function load() {
    setPending(true); setError('');
    try { setAddresses(await client.request('/api/v1/me/addresses')); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  return <div className="saved-address-picker">
    <button type="button" className="back-step" disabled={pending} onClick={load}>{pending ? 'Loading addresses…' : 'Use a saved address'}</button>
    {error && <p className="field-error" role="alert">{error}</p>}
    {addresses && <><label htmlFor={`saved-${kind}`}>Saved {kind} address</label><select id={`saved-${kind}`} defaultValue="" onChange={event => {
      const chosen = addresses.find(address => address.id === event.target.value);
      if (chosen) onSelect(chosen);
      event.target.value = '';
    }}><option value="">Choose an address</option>{addresses.map(address => <option key={address.id} value={address.id}>{address.label}{address.defaultPickup ? ' — Default pickup' : ''}</option>)}</select>
      {!addresses.length && <p className="profile-note">Add saved addresses in My account. You can still enter an address below.</p>}</>}
  </div>;
}
