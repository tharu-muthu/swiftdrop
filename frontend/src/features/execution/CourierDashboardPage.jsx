import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import { loadAssignedDeliveries, loadDelivery, updateDeliveryStatus } from './executionApi.js';
import './execution.css';

const nextStatuses = {
  CREATED: ['PICKED_UP'],
  PICKED_UP: ['IN_TRANSIT'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
};

export default function CourierDashboardPage() {
  const { client, user } = useAuth();
  const [deliveries, setDeliveries] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [recipientName, setRecipientName] = useState('');
  const [failureReason, setFailureReason] = useState('');

  async function refresh() {
    try { setError(''); setDeliveries(await loadAssignedDeliveries(client)); }
    catch (requestError) { setError(requestError.message); }
  }
  useEffect(() => { refresh(); }, [client]);

  async function selectDelivery(id) {
    try { setError(''); setSelected(await loadDelivery(client, id)); }
    catch (requestError) { setError(requestError.message); }
  }
  async function update(status) {
    if (status === 'DELIVERED' && !recipientName.trim()) return setError('Recipient name is required.');
    if (status === 'FAILED' && !failureReason.trim()) return setError('Failure reason is required.');
    setPending(true);
    try {
      const result = await updateDeliveryStatus(client, selected.lifecycleId, {
        expectedStatus: selected.status, status,
        recipientName: status === 'DELIVERED' ? recipientName.trim() : null,
        failureReason: status === 'FAILED' ? failureReason.trim() : null,
      });
      setSelected(result); setRecipientName(''); setFailureReason(''); await refresh();
    } catch (requestError) { setError(requestError.message); }
    finally { setPending(false); }
  }

  return <main className="execution-shell">
    <header className="execution-header"><span className="execution-brand">◇ SwiftDrop</span>
      <span>{user.fullName} · Courier</span></header>
    <section className="execution-content">
      <div><p className="execution-kicker">M6 · COURIER OPERATIONS</p><h1>Assigned deliveries</h1>
        <p className="execution-muted">Update only parcels currently assigned to you.</p></div>
      {error && <p className="execution-error" role="alert">{error}</p>}
      {!selected ? <div className="delivery-list" aria-busy={!deliveries}>
        {deliveries.length === 0 && <p className="execution-empty">No assigned deliveries.</p>}
        {deliveries.map(delivery => <button className="delivery-card" key={delivery.lifecycleId}
          onClick={() => selectDelivery(delivery.lifecycleId)}>
          <strong>{delivery.trackingNumber}</strong><span>{delivery.status.replaceAll('_', ' ')}</span>
          <small>Updated {new Date(delivery.updatedAt).toLocaleString()}</small>
        </button>)}
      </div> : <DeliveryDetails delivery={selected} recipientName={recipientName}
        setRecipientName={setRecipientName} failureReason={failureReason} setFailureReason={setFailureReason}
        pending={pending} onUpdate={update} onBack={() => setSelected(null)} />}
    </section>
  </main>;
}

function DeliveryDetails({ delivery, recipientName, setRecipientName, failureReason, setFailureReason, pending, onUpdate, onBack }) {
  const actions = nextStatuses[delivery.status] || [];
  return <article className="delivery-detail"><button className="back-button" onClick={onBack}>← Assigned deliveries</button>
    <h2>{delivery.trackingNumber}</h2><p className="status-pill">{delivery.status.replaceAll('_', ' ')}</p>
    <dl><dt>Pickup</dt><dd>{delivery.pickupAddress}</dd><dt>Drop-off</dt><dd>{delivery.dropoffAddress}</dd>
      <dt>Parcel</dt><dd>{delivery.parcelDescription} · {delivery.parcelWeightGrams} g</dd></dl>
    <h3>Status history</h3><ol className="status-history">{delivery.history.map(item =>
      <li key={item.sequenceNo}><strong>{item.newStatus.replaceAll('_', ' ')}</strong><small>{new Date(item.occurredAt).toLocaleString()}</small>
        {item.failureReason && <span>{item.failureReason}</span>}</li>)}</ol>
    {actions.includes('DELIVERED') && <label>Recipient name<input value={recipientName} onChange={event => setRecipientName(event.target.value)} maxLength="200" /></label>}
    {actions.includes('FAILED') && <label>Failure reason<input value={failureReason} onChange={event => setFailureReason(event.target.value)} maxLength="500" /></label>}
    <div className="execution-actions">{actions.map(status =>
      <button key={status} disabled={pending} onClick={() => onUpdate(status)}>{status.replaceAll('_', ' ')}</button>)}</div>
  </article>;
}
