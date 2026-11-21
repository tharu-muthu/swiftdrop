import { useEffect, useState } from 'react';
import { useAuth } from './AuthProvider.jsx';

const empty = { label: '', contactName: '', contactPhone: '', addressLine1: '', addressLine2: '', city: '', postalCode: '', defaultPickup: false, version: 0 };
const fields = [
  ['label', 'Address label', 60, true], ['contactName', 'Contact name', 200, true],
  ['contactPhone', 'Contact mobile number', 32, true], ['addressLine1', 'Address line 1', 140, true],
  ['addressLine2', 'Address line 2', 140, false], ['city', 'City', 100, true], ['postalCode', 'Postal code', 5, true],
];
export default function SavedAddressBook() {
  const { client } = useAuth();
  const [addresses, setAddresses] = useState([]), [editing, setEditing] = useState(null), [draft, setDraft] = useState(empty);
  const [loading, setLoading] = useState(true), [pending, setPending] = useState(false), [error, setError] = useState('');
  async function reload() { setAddresses(await client.request('/api/v1/me/addresses')); }
  useEffect(() => {
    let active = true;
    (async () => {
      try { const result = await client.request('/api/v1/me/addresses'); if (active) setAddresses(result); }
      catch (failure) { if (active) setError(failure.message); }
      finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, [client]);
  function edit(address) {
    setEditing(address?.id || 'new'); setError('');
    setDraft(address ? Object.fromEntries(Object.keys(empty).map(key => [key, address[key]])) : { ...empty });
  }
  async function save(event) {
    event.preventDefault(); if (pending) return;
    setPending(true); setError('');
    try {
      await client.request(`/api/v1/me/addresses${editing === 'new' ? '' : '/' + editing}`, {
        method: editing === 'new' ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(draft),
      });
      setEditing(null); await reload();
    } catch (failure) { setError(failure.status === 409 ? 'This address changed or your address limit was reached. Reload addresses and review before saving again.' : failure.message); }
    finally { setPending(false); }
  }
  async function remove(address) {
    if (pending) return;
    setPending(true); setError('');
    try { await client.request(`/api/v1/me/addresses/${address.id}?version=${address.version}`, { method: 'DELETE' }); await reload(); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  return <section className="profile-subsection" aria-labelledby="saved-address-heading" aria-busy={loading}>
    <div className="profile-card-heading"><div><h3 id="saved-address-heading">Saved addresses</h3><p>Save pickup and delivery contacts for future bookings. Existing bookings keep their original details.</p></div>
      <button type="button" className="profile-primary" disabled={pending || loading || addresses.length >= 20} onClick={() => edit(null)}>Add address</button>
    </div>
    {error && <div><p className="profile-banner error" role="alert">{error}</p><button type="button" className="profile-secondary" disabled={pending} onClick={async () => {
      setPending(true); try { await reload(); setEditing(null); setError(''); } catch (failure) { setError(failure.message); } finally { setPending(false); }
    }}>Reload addresses</button></div>}
    {loading ? <p>Loading saved addresses…</p> : !addresses.length && <p className="profile-note">No saved addresses yet. You can save up to 20.</p>}
    <div className="saved-address-list">{addresses.map(address => <article key={address.id} className="saved-address-card">
      <h3>{address.label} {address.defaultPickup && <span className="profile-badge">Default pickup</span>}</h3>
      <p>{address.contactName} · {address.contactPhone}</p><p>{[address.addressLine1, address.addressLine2, address.city, address.postalCode].filter(Boolean).join(', ')}</p>
      <div className="saved-address-actions"><button type="button" className="profile-secondary" disabled={pending} onClick={() => edit(address)}>Edit {address.label}</button>
        <button type="button" className="profile-secondary" disabled={pending} onClick={() => remove(address)}>Delete {address.label}</button></div>
    </article>)}</div>
    {editing && <form className="saved-address-form" onSubmit={save} aria-label={editing === 'new' ? 'Add saved address' : 'Edit saved address'}>
      <h3>{editing === 'new' ? 'Add address' : 'Edit address'}</h3>
      <div className="profile-field-grid">{fields.map(([key, label, max, required]) => <div className="profile-field" key={key}>
        <label htmlFor={`saved-${key}`}>{label}{required ? ' *' : ' (optional)'}</label><input id={`saved-${key}`} required={required} maxLength={max}
          pattern={key === 'postalCode' ? '[0-9]{5}' : key === 'contactPhone' ? '([+]94|0)7[0-9]{8}' : undefined}
          title={key === 'contactPhone' ? 'Use a Sri Lankan mobile number such as 0771234567.' : undefined}
          value={draft[key]} disabled={pending} onChange={event => setDraft(current => ({ ...current, [key]: event.target.value }))} />
      </div>)}</div>
      <label className="saved-address-default"><input type="checkbox" checked={draft.defaultPickup} disabled={pending} onChange={event => setDraft(current => ({ ...current, defaultPickup: event.target.checked }))} /> Default pickup address</label>
      <div className="saved-address-actions"><button className="profile-primary" disabled={pending}>Save address</button><button type="button" className="profile-secondary" disabled={pending} onClick={() => setEditing(null)}>Cancel address edit</button></div>
    </form>}
  </section>;
}
