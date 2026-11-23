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
  FAILED: ['OUT_FOR_DELIVERY'],
};

function currentWorkday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = type => parts.find(item => item.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function formatTrackingNumber(trackingNumber) {
  if (!trackingNumber?.startsWith('SDP-')) return trackingNumber;
  const suffix = trackingNumber.slice(4);
  if (suffix.length < 8) return trackingNumber;
  const groups = suffix.match(/.{1,4}/g);
  return `SDP-${groups.join('-')}`;
}

async function copyTrackingNumber(trackingNumber) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(trackingNumber);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = trackingNumber;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
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
  const [recipientNameError, setRecipientNameError] = useState('');
  const [failureReasonError, setFailureReasonError] = useState('');
  const [copiedLifecycleId, setCopiedLifecycleId] = useState('');

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
      setError(''); setRecipientNameError(''); setFailureReasonError('');
      const delivery = await loadDelivery(client, id);
      if (request === detailRequest.current) { setSelected(delivery); setRecipientName(''); setFailureReason(''); }
    } catch (requestError) { if (request === detailRequest.current) setError(requestError.message); }
  }
  async function update(status) {
    setRecipientNameError('');
    setFailureReasonError('');
    if (status === 'DELIVERED' && !recipientName.trim()) {
      setRecipientNameError('Recipient name is required.');
      return;
    }
    if (status === 'FAILED' && !failureReason.trim()) {
      setFailureReasonError('Failure reason is required.');
      return;
    }
    setPending(true);
    try {
      const result = await updateDeliveryStatus(client, selected.lifecycleId, {
        expectedStatus: selected.status, status,
        recipientName: status === 'DELIVERED' ? recipientName.trim() : null,
        failureReason: status === 'FAILED' ? failureReason.trim() : null,
      });
      setSelected(result); setRecipientName(''); setFailureReason('');
      setRecipientNameError(''); setFailureReasonError('');
      await refresh();
    } catch (requestError) {
      if ([403, 404].includes(requestError.status)) { setSelected(null); await refresh(); }
      setError(requestError.message);
    } finally { setPending(false); }
  }

  async function copyNumber(delivery) {
    try {
      await copyTrackingNumber(delivery.trackingNumber);
      setCopiedLifecycleId(delivery.lifecycleId);
      window.setTimeout(() => setCopiedLifecycleId(current => current === delivery.lifecycleId ? '' : current), 1800);
    } catch {
      setError('Unable to copy the tracking number.');
    }
  }

  const activeDeliveries = deliveries.filter(delivery => !['DELIVERED', 'FAILED'].includes(delivery.status));
  const completedDeliveries = deliveries.filter(delivery => ['DELIVERED', 'FAILED'].includes(delivery.status));

  return <PortalShell title="Assigned deliveries" contentClassName="execution-content">
    <p className="execution-muted">Update only parcels currently assigned to you.</p>
      {error && <p className="execution-error" role="alert">{error}</p>}
      <div className="execution-filters"><label htmlFor="courier-workday">Workday (Sri Lanka)</label>
        <input id="courier-workday" type="date" value={date} max={currentWorkday()} disabled={pending} onChange={event => { if (event.target.value) { setDate(event.target.value); setSelected(null); } }} />
        <button disabled={pending} onClick={() => { setDate(currentWorkday()); setSelected(null); }}>Today</button>
        <button disabled={pending || loading} onClick={refresh}>Refresh deliveries</button>
      </div>
      <p className="execution-muted">Today also includes unfinished work carried over from earlier days.</p>
      {loading && <p role="status">Loading deliveries...</p>}
      {!selected ? <div aria-busy={loading}>
        {!loading && !error && deliveries.length === 0 && <p className="execution-empty">No assigned deliveries. Your dispatcher can check your courier profile and assign parcels.</p>}
        {deliveries.length > 0 && <DeliverySection title="Active deliveries" count={activeDeliveries.length}
          deliveries={activeDeliveries} emptyText="No active deliveries." onSelect={selectDelivery}
          onCopy={copyNumber} copiedLifecycleId={copiedLifecycleId} />}
        {completedDeliveries.length > 0 && <DeliverySection title="Completed" count={completedDeliveries.length}
          deliveries={completedDeliveries} emptyText="" onSelect={selectDelivery}
          onCopy={copyNumber} copiedLifecycleId={copiedLifecycleId} completed />}
      </div> : <DeliveryDetails delivery={selected} recipientName={recipientName}
        setRecipientName={setRecipientName} failureReason={failureReason} setFailureReason={setFailureReason}
        recipientNameError={recipientNameError} failureReasonError={failureReasonError}
        pending={pending} onUpdate={update} onBack={() => setSelected(null)} />}
  </PortalShell>;
}

function DeliverySection({ title, count, deliveries, emptyText, onSelect, onCopy, copiedLifecycleId, completed = false }) {
  return <section className={`delivery-section ${completed ? 'completed-section' : ''}`}>
    <div className="delivery-section-heading"><h2>{title}</h2><span>{count}</span></div>
    {deliveries.length === 0 ? emptyText && <p className="execution-empty">{emptyText}</p> :
      <div className="delivery-list">{deliveries.map(delivery => {
        const status = deliveryStatus(delivery.status);
        return <article className="delivery-card" key={delivery.lifecycleId}>
          <button className="delivery-card-main" onClick={() => onSelect(delivery.lifecycleId)} aria-label={`Open ${delivery.trackingNumber}`}>
            <span className="delivery-tracking">{formatTrackingNumber(delivery.trackingNumber)}</span>
            <small>Updated {new Date(delivery.updatedAt).toLocaleString()}</small>
          </button>
          <div className="delivery-card-actions">
            <span className={`status-pill ${status.className}`}>{status.label}</span>
            <button className="copy-tracking" type="button" onClick={() => onCopy(delivery)}
              aria-label="Copy full tracking number"
              title="Copy the full tracking number">
              {copiedLifecycleId === delivery.lifecycleId ? 'Copied' : 'Copy'}
            </button>
          </div>
        </article>;
      })}</div>}
  </section>;
}

function DeliveryDetails({ delivery, recipientName, setRecipientName, failureReason, setFailureReason,
  recipientNameError, failureReasonError, pending, onUpdate, onBack }) {
  const actions = nextStatuses[delivery.status] || [];
  const [copied, setCopied] = useState(false);
  async function copyDetailsNumber() {
    try {
      await copyTrackingNumber(delivery.trackingNumber);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }
  return <article className="delivery-detail"><button className="back-button" onClick={onBack}>← Assigned deliveries</button>
    <div className="delivery-title"><h2>{formatTrackingNumber(delivery.trackingNumber)}</h2>
      <button className="copy-tracking" type="button" onClick={copyDetailsNumber}
        aria-label="Copy full tracking number" title="Copy the full tracking number">{copied ? 'Copied' : 'Copy'}</button></div>
    <p className={`status-pill ${deliveryStatus(delivery.status).className}`}>{deliveryStatus(delivery.status).label}</p>
    <dl><dt>Pickup</dt><dd>{delivery.pickupAddress}</dd><dt>Drop-off</dt><dd>{delivery.dropoffAddress}</dd>
      <dt>Sender contact</dt><dd>{delivery.senderName} {delivery.senderPhone}</dd><dt>Recipient contact</dt><dd>{delivery.recipientName} {delivery.recipientPhone}</dd>
      <dt>Parcel</dt><dd>{delivery.parcelDescription} · {delivery.parcelWeightGrams} g</dd></dl>
    {delivery.deliveryProofName && <p className="delivery-proof"><strong>Person who received the parcel:</strong> {delivery.deliveryProofName}</p>}
    <h3>Status history</h3><ol className="status-history">{delivery.history.map(item =>
      <li key={item.sequenceNo}><strong className={`status-pill ${deliveryStatus(item.newStatus).className}`}>{deliveryStatus(item.newStatus).label}</strong><small>{new Date(item.occurredAt).toLocaleString()}</small>
        {item.failureReason && <span>{item.failureReason}</span>}</li>)}</ol>
    {actions.includes('DELIVERED') && <div className="delivery-field"><label htmlFor="recipient-name">Recipient name</label>
      <input id="recipient-name" value={recipientName} onChange={event => setRecipientName(event.target.value)}
        maxLength="200" aria-invalid={recipientNameError ? 'true' : 'false'}
        aria-describedby={recipientNameError ? 'recipient-name-error' : undefined} />
      {recipientNameError && <p id="recipient-name-error" className="field-error" role="alert">{recipientNameError}</p>}
    </div>}
    {actions.includes('FAILED') && <div className="delivery-field"><label htmlFor="failure-reason">Failure reason</label>
      <textarea id="failure-reason" rows="3" value={failureReason} onChange={event => setFailureReason(event.target.value)}
        maxLength="500" aria-invalid={failureReasonError ? 'true' : 'false'}
        aria-describedby={failureReasonError ? 'failure-reason-error' : undefined} />
      {failureReasonError && <p id="failure-reason-error" className="field-error" role="alert">{failureReasonError}</p>}
    </div>}
    <div className="execution-actions">{actions.map(status =>
      <button key={status} disabled={pending} onClick={() => onUpdate(status)}>
        {delivery.status === 'FAILED' && status === 'OUT_FOR_DELIVERY' ? 'Resume delivery' : status.replaceAll('_', ' ')}
      </button>)}</div>
  </article>;
}
