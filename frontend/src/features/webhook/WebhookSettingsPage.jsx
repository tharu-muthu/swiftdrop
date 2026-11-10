import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import { navigate } from '../../app/navigation.js';
import {
  createWebhookSubscription, listWebhookSubscriptions, loadWebhookDeliveries,
  rotateWebhookSecret, testWebhook, updateWebhookSubscription,
} from './webhookApi.js';
import './webhook.css';

const initialForm = { callbackUrl: '', eventTypes: 'parcel.status.changed' };

function parseEventTypes(value) {
  return value.split(',').map(item => item.trim()).filter(Boolean);
}

export default function WebhookSettingsPage() {
  const { client, user } = useAuth();
  const [subscriptions, setSubscriptions] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [secret, setSecret] = useState('');
  const [deliveries, setDeliveries] = useState({});
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function load() {
    setLoading(true); setError('');
    try { setSubscriptions(await listWebhookSubscriptions(client)); }
    catch (failure) { setError(failure.message); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, [client]);

  async function create(event) {
    event.preventDefault(); setPending(true); setError(''); setMessage(''); setSecret('');
    try {
      const result = await createWebhookSubscription(client, {
        callbackUrl: form.callbackUrl,
        eventTypes: parseEventTypes(form.eventTypes),
      });
      setSecret(result.signingSecret); setMessage('Webhook created. Copy the signing secret now.');
      setForm(initialForm); await load();
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  async function update(id, payload, successMessage) {
    setPending(true); setError(''); setMessage('');
    try {
      const result = await updateWebhookSubscription(client, id, payload);
      setSubscriptions(current => current.map(item => item.id === id ? result : item));
      setMessage(successMessage);
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  async function rotate(id) {
    setPending(true); setError(''); setMessage('');
    try {
      const result = await rotateWebhookSecret(client, id);
      setSecret(result.signingSecret); setMessage('Secret rotated. Copy the new secret now.');
      setSubscriptions(current => current.map(item => item.id === id ? result.subscription : item));
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  async function sendTest(id) {
    setPending(true); setError(''); setMessage('');
    try {
      const result = await testWebhook(client, id);
      setMessage(`Test delivery ${result.delivery.state.toLowerCase()}.`);
      const history = await loadWebhookDeliveries(client, id);
      setDeliveries(current => ({ ...current, [id]: history }));
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  async function showDeliveries(id) {
    setError('');
    try {
      const history = await loadWebhookDeliveries(client, id);
      setDeliveries(current => ({ ...current, [id]: history }));
    } catch (failure) { setError(failure.message); }
  }

  return <main className="webhook-page">
    <header className="webhook-header"><div><p className="webhook-eyebrow">Merchant integrations</p><h1>Webhooks</h1><p>Send signed parcel updates to your approved merchant system.</p></div><button className="webhook-secondary" onClick={() => navigate('/account')}>Account</button></header>
    <p className="webhook-user">Signed in as {user.fullName}</p>
    {error && <p className="webhook-alert" role="alert">{error}</p>}
    {message && <p className="webhook-success" role="status">{message}</p>}
    {secret && <section className="webhook-secret" aria-label="Signing secret"><strong>Signing secret</strong><code>{secret}</code><span>Store this securely. It is shown only after creation or rotation.</span></section>}
    <section className="webhook-card"><h2>Add webhook subscription</h2><form onSubmit={create} className="webhook-form">
      <label>HTTPS callback URL<input required value={form.callbackUrl} onChange={event => setForm({ ...form, callbackUrl: event.target.value })} placeholder="https://merchant.example/webhooks" /></label>
      <label>Event types<input required value={form.eventTypes} onChange={event => setForm({ ...form, eventTypes: event.target.value })} /><small>Separate event names with commas.</small></label>
      <button disabled={pending}>{pending ? 'Saving…' : 'Create subscription'}</button>
    </form></section>
    <section className="webhook-card"><h2>Your subscriptions</h2>{loading && <p role="status">Loading subscriptions…</p>}{!loading && subscriptions.length === 0 && <p>No webhook subscriptions yet.</p>}
      <div className="webhook-list">{subscriptions.map(subscription => <article className="webhook-row" key={subscription.id}>
        <div><strong>{subscription.callbackUrl}</strong><span>{subscription.eventTypes.join(', ')}</span><small>{subscription.active ? 'Active' : 'Inactive'}</small></div>
        <div className="webhook-actions"><button disabled={pending} onClick={() => update(subscription.id, { active: !subscription.active }, subscription.active ? 'Webhook disabled.' : 'Webhook enabled.')}>{subscription.active ? 'Disable' : 'Enable'}</button><button disabled={pending} onClick={() => rotate(subscription.id)}>Rotate secret</button><button disabled={pending || !subscription.active} onClick={() => sendTest(subscription.id)}>Send test</button><button onClick={() => showDeliveries(subscription.id)}>Delivery log</button></div>
        {deliveries[subscription.id] && <ul className="webhook-deliveries">{deliveries[subscription.id].map(item => <li key={item.id}><span>{item.eventType}</span><strong>{item.state}</strong><small>Attempts {item.attemptCount}{item.lastHttpStatus ? ` · HTTP ${item.lastHttpStatus}` : ''}</small></li>)}</ul>}
      </article>)}</div>
    </section>
  </main>;
}
