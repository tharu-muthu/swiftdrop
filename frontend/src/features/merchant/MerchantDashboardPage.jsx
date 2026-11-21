import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import WorkspacePage, { JsonRequest } from '../../components/WorkspacePage.jsx';
import MerchantOrdersPanel from './MerchantOrdersPanel.jsx';

const empty = { businessName: '', contactEmail: '', contactPhone: '', businessAddress: '' };
export default function MerchantDashboardPage() {
  const { client } = useAuth();
  const [merchant, setMerchant] = useState(null), [form, setForm] = useState(empty), [keys, setKeys] = useState([]);
  const [error, setError] = useState(''), [loading, setLoading] = useState(true), [pending, setPending] = useState(false), [secret, setSecret] = useState('');
  const [editing, setEditing] = useState(false);
  async function load() {
    setError(''); setLoading(true);
    try {
      let business;
      try { business = await client.request('/api/v1/merchant/registration'); }
      catch (failure) { if (failure.status !== 404) throw failure; }
      setMerchant(business || null); if(business) setForm({ businessName:business.businessName,contactEmail:business.contactEmail,contactPhone:business.contactPhone||'',businessAddress:business.businessAddress||'' });
      if (business?.approvalStatus === 'APPROVED') {
        const credentials = await client.request('/api/v1/merchant/api-keys');
        setKeys(credentials);
      }
    } catch (failure) { setError(failure.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [client]);
  async function action(work) { setPending(true); setError(''); try { await work(); await load(); } catch (failure) { setError(failure.message); } finally { setPending(false); } }
  return <WorkspacePage title="Merchant workspace">{error && <p className="error" role="alert">{error}</p>}{loading && <p role="status">Loading your business…</p>}
    {!loading && !merchant && <section><h2>Register your business</h2><form onSubmit={event => { event.preventDefault(); action(() => client.request('/api/v1/merchant/registration', JsonRequest('POST', form))); }}>
      {Object.entries({ businessName: 'Business name', contactEmail: 'Contact email', contactPhone: 'Contact phone', businessAddress: 'Business address' }).map(([key, label]) => <label key={key}>{label}<input required={['businessName', 'contactEmail'].includes(key)} type={key === 'contactEmail' ? 'email' : 'text'} maxLength={key === 'businessAddress' ? 300 : key === 'businessName' ? 150 : key === 'contactPhone' ? 30 : 254} value={form[key]} onChange={event => setForm({ ...form, [key]: event.target.value })} /></label>)}
      <button disabled={pending}>Submit for approval</button></form></section>}
    {merchant && <section><h2>{merchant.businessName}</h2><p>Status: <strong>{merchant.approvalStatus}</strong></p>{merchant.reviewReason && <p>{merchant.reviewReason}</p>}{merchant.approvalStatus !== 'APPROVED' && <p>Your integration features become available after administrator approval.</p>}<button className="secondary" disabled={pending} onClick={load}>Refresh status</button></section>}
    {merchant && <section><h2>Business contact details</h2>{!editing ? <><p>{merchant.contactEmail} · {merchant.contactPhone}</p><p>{merchant.businessAddress}</p><button onClick={()=>setEditing(true)}>Edit contact details</button></> : <form onSubmit={event=>{ event.preventDefault(); action(async()=>{ await client.request('/api/v1/merchant/registration',JsonRequest('PATCH',{profile:form,expectedVersion:merchant.version})); setEditing(false); }); }}>{['contactEmail','contactPhone','businessAddress'].map(key=><label key={key}>{key.replace(/([A-Z])/g,' $1')}<input type={key==='contactEmail'?'email':'text'} required={key==='contactEmail'} maxLength={key==='businessAddress'?300:key==='contactPhone'?30:254} value={form[key]} onChange={event=>setForm({...form,[key]:event.target.value})} /></label>)}<div className="actions"><button disabled={pending}>Save contacts</button><button className="secondary" type="button" onClick={()=>setEditing(false)}>Cancel</button></div></form>}</section>}
    {merchant?.approvalStatus === 'APPROVED' && <><section><h2>API credentials</h2><p>Use API keys on your server. A newly generated key is shown once.</p>{secret && <p className="notice" role="status"><code>{secret}</code><button className="secondary" onClick={() => setSecret('')}>Dismiss key</button></p>}
      <button disabled={pending} onClick={() => action(async () => { const result = await client.request('/api/v1/merchant/api-keys', JsonRequest('POST')); setSecret(result.apiKey); })}>Generate API key</button>
      <ul>{keys.map(key => <li key={key.id}><code>{key.keyPrefix}</code> · {key.revokedAt ? 'Revoked' : 'Active'} {!key.revokedAt && <button disabled={pending} onClick={() => action(() => client.request(`/api/v1/merchant/api-keys/${key.id}/revoke`, JsonRequest('POST')))}>Revoke</button>}</li>)}</ul><div className="actions"><a className="button" href="/merchant/webhooks">Configure webhooks</a></div></section>
      <MerchantOrdersPanel key={merchant.id} /></>}
  </WorkspacePage>;
}

