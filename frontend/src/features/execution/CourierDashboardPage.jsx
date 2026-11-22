import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import PortalShell from '../../components/PortalShell.jsx';
import { loadAssignedDeliveries, loadDelivery, updateDeliveryStatus } from './executionApi.js';
import { deliveryStatus } from '../../app/deliveryStatus.js';
import './execution.css';

const nextStatuses = {
  CREATED: ['PICKED_UP'],
  PICKED_UP: ['IN_TRANSIT', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
};

function currentWorkday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = type => parts.find(item => item.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}

export default function CourierDashboardPage() {
  const { client } = useAuth();
  const [date, setDate] = useState(currentWorkday);
  const [loading, setLoading] = useState(true);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);
  const [deliveries, setDeliveries] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [recipientName, setRecipientName] = useState('');
  const [failureReason, setFailureReason] = useState('');

  async function refresh() {
    const request = ++listRequest.current;
    setLoading(true); setError('');
    try {
      const items = await loadAssignedDeliveries(client, date);
      if (request === listRequest.current) setDeliveries(items);
    } catch (requestError) { if (request === listRequest.current) setError(requestError.message); }
    finally { if (request === listRequest.current) setLoading(false); }
  }
  useEffect(() => { refresh(); return () => { ++listRequest.current; ++detailRequest.current; }; }, [client, date]);

  async function selectDelivery(id) {
    const request = ++detailRequest.current;
    try {
      setError(''); const delivery = await loadDelivery(client, id);
      if (request === detailRequest.current) { setSelected(delivery); setRecipientName(''); setFailureReason(''); }
    } catch (requestError) { if (request === detailRequest.current) setError(requestError.message); }
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
    } catch (requestError) {
      if ([403, 404].includes(requestError.status)) { setSelected(null); await refresh(); }
      setError(requestError.message);
    } finally { setPending(false); }
  }

  return <PortalShell title="Assigned deliveries" contentClassName="execution-content">
    <p className="execution-muted">Update only parcels currently assigned to you.</p>
      {error && <p className="execution-error" role="alert">{error}</p>}
      <div className="execution-filters"><label htmlFor="courier-workday">Workday (Sri Lanka)</label>
        <input id="courier-workday" type="date" value={date} max={currentWorkday()} disabled={pending} onChange={event => { if (event.target.value) { setDate(event.target.value); setSelected(null); } }} />
        <button disabled={pending} onClick={() => { setDate(currentWorkday()); setSelected(null); }}>Today</button>
        <button disabled={pending || loading} onClick={refresh}>Refresh deliveries</button>
      </div>
      <p className="execution-muted">Today's list includes unfinished work carried over from earlier days. Earlier dates show assignments or activity on that date that are still assigned to you.</p>
      {loading && <p role="status">Loading deliveries...</p>}
      {!selected ? <div className="delivery-list" aria-busy={loading}>
        {!loading && !error && deliveries.length === 0 && <p className="execution-empty">No assigned deliveries. Your dispatcher can check your courier profile and assign parcels.</p>}
        {deliveries.map(delivery => <button className="delivery-card" key={delivery.lifecycleId}
          onClick={() => selectDelivery(delivery.lifecycleId)}>
          <strong>{delivery.trackingNumber}</strong><span className={`status-pill ${deliveryStatus(delivery.status).className}`}>{deliveryStatus(delivery.status).label}</span>
          <small>Updated {new Date(delivery.updatedAt).toLocaleString()}</small>
        </button>)}
      </div> : <DeliveryDetails delivery={selected} recipientName={recipientName}
        setRecipientName={setRecipientName} failureReason={failureReason} setFailureReason={setFailureReason}
        pending={pending} onUpdate={update} onBack={() => setSelected(null)} />}
  </PortalShell>;
}

function DeliveryDetails({ delivery, recipientName, setRecipientName, failureReason, setFailureReason, pending, onUpdate, onBack }) {
  const actions = nextStatuses[delivery.status] || [];
  return <article className="delivery-detail"><button className="back-button" onClick={onBack}>← Assigned deliveries</button>
    <h2>{delivery.trackingNumber}</h2><p className={`status-pill ${deliveryStatus(delivery.status).className}`}>{deliveryStatus(delivery.status).label}</p>
    <dl><dt>Pickup</dt><dd>{delivery.pickupAddress}</dd><dt>Drop-off</dt><dd>{delivery.dropoffAddress}</dd>
      <dt>Sender contact</dt><dd>{delivery.senderName} {delivery.senderPhone}</dd><dt>Recipient contact</dt><dd>{delivery.recipientName} {delivery.recipientPhone}</dd>
      <dt>Parcel</dt><dd>{delivery.parcelDescription} · {delivery.parcelWeightGrams} g</dd></dl>
    <h3>Status history</h3><ol className="status-history">{delivery.history.map(item =>
      <li key={item.sequenceNo}><strong className={`status-pill ${deliveryStatus(item.newStatus).className}`}>{deliveryStatus(item.newStatus).label}</strong><small>{new Date(item.occurredAt).toLocaleString()}</small>
        {item.failureReason && <span>{item.failureReason}</span>}</li>)}</ol>
    {actions.includes('DELIVERED') && <label>Recipient name<input value={recipientName} onChange={event => setRecipientName(event.target.value)} maxLength="200" /></label>}
    {actions.includes('FAILED') && <label>Failure reason<input value={failureReason} onChange={event => setFailureReason(event.target.value)} maxLength="500" /></label>}
    <div className="execution-actions">{actions.map(status =>
      <button key={status} disabled={pending} onClick={() => onUpdate(status)}>{status.replaceAll('_', ' ')}</button>)}</div>
  </article>;
}
