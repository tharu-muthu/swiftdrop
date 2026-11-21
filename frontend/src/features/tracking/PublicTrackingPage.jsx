import { useEffect, useMemo, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { ApiError } from '../identity/apiClient.js';
import { loadPublicTracking, trackingEventsUrl, acceptTrackingSnapshot } from './trackingApi.js';
import Brand from '../../components/Brand.jsx';
import PortalShell from '../../components/PortalShell.jsx';
import './tracking.css';

export default function PublicTrackingPage({ trackingNumber = '', inPortal = false }) {
  const [value, setValue] = useState(trackingNumber);
  const [tracking, setTracking] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [connection, setConnection] = useState('connecting');
  const shareUrl = useMemo(() => tracking?.trackingNumber
    ? `${window.location.origin}/tracking/${encodeURIComponent(tracking.trackingNumber)}`
    : '', [tracking]);

  useEffect(() => {
    setValue(trackingNumber);
    if (!trackingNumber) {
      setTracking(null);
      setError('');
      setConnection('offline');
      return;
    }
    let active = true;
    let source;
    let reconnectTimer;
    const acceptSnapshot=next=>setTracking(current=>acceptTrackingSnapshot(current,next));
    setLoading(true);
    setConnection('connecting');
    setError('');
    const connect = () => {
      if (!active) return;
      source = new EventSource(trackingEventsUrl(trackingNumber));
      source.addEventListener('tracking-update', event => {
        if (!active) return;
        try {
          acceptSnapshot(JSON.parse(event.data));
          setConnection('live');
          setError('');
        } catch {
          setError('Received an invalid tracking update.');
        }
      });
      const reconnect = () => {
        if (!active) return;
        reconnectTimer = window.setTimeout(() => {
          loadPublicTracking(trackingNumber).then(result => {
            if (!active) return;
            acceptSnapshot(result);
            connect();
          }).catch(() => { if (active) { setConnection('reconnecting'); reconnect(); } });
        }, 3000);
      };
      source.onerror = () => {
        source.close();
        if (!active) return;
        setConnection('reconnecting');
        reconnect();
      };
    };
    loadPublicTracking(trackingNumber)
      .then(result => { if (active) { acceptSnapshot(result); connect(); } })
      .catch(result => {
        if (active) {
          setConnection('offline');
          setError(result instanceof ApiError ? result.message : 'Unable to load tracking details.');
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => {
      active = false;
      if (source) source.close();
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
    };
  }, [trackingNumber]);

  function submit(event) {
    event.preventDefault();
    const next = value.trim().toUpperCase();
    if (!next) {
      setError('Enter a tracking number.');
      return;
    }
    navigate(`/tracking/${encodeURIComponent(next)}`);
  }

  async function copyLink() {
    if (!shareUrl || !navigator.clipboard) return;
    await navigator.clipboard.writeText(shareUrl);
    setCopied(true);
  }

  async function shareLink() {
    if (!shareUrl) return;
    if (navigator.share) {
      await navigator.share({ title: 'SwiftDrop parcel tracking', url: shareUrl });
      return;
    }
    await copyLink();
  }

  const content = <section className="tracking-card">
      <p className="tracking-eyebrow">Follow your delivery</p>
      {inPortal ? <h2>Track a parcel</h2> : <h1>Track a parcel</h1>}
      <p className="tracking-intro">Enter the tracking number to see the latest committed delivery status.</p>
      <form className="tracking-search" onSubmit={submit}>
        <label htmlFor="tracking-number">Tracking number</label>
        <div>
          <input id="tracking-number" value={value} onChange={event => setValue(event.target.value)}
            placeholder="e.g. SDP-A8K4P2M7Q9" autoComplete="off" maxLength={40} />
          <button type="submit" disabled={loading}>Track parcel</button>
        </div>
      </form>
      {loading && <p className="tracking-message" role="status">Loading tracking details…</p>}
      {error && !loading && <p className="tracking-error" role="alert">{error}</p>}
      {tracking && !loading && <section className="tracking-result" aria-live="polite">
        <div className="tracking-result-heading">
          <div><p className="tracking-eyebrow">Tracking number</p><h2>{tracking.trackingNumber}</h2></div>
          <strong className={`tracking-status ${tracking.currentStatus.toLowerCase()}`}>{tracking.currentStatus.replaceAll('_', ' ')}</strong>
        </div>
        <p className="tracking-updated">Last updated {new Date(tracking.lastUpdatedAt).toLocaleString()}</p>
        <p className="tracking-connection" role="status">Live updates: {connection}</p>
        <ol className="tracking-timeline">
          {tracking.timeline.map(item => <li key={item.sequenceNo}>
            <span className="tracking-dot" />
            <div><strong>{item.status.replaceAll('_', ' ')}</strong><time dateTime={item.occurredAt}>{new Date(item.occurredAt).toLocaleString()}</time></div>
          </li>)}
        </ol>
        <div className="tracking-actions">
          <button type="button" onClick={copyLink} disabled={!navigator.clipboard}>{copied ? 'Link copied' : 'Copy tracking link'}</button>
          <button type="button" onClick={shareLink}>Share tracking link</button>
        </div>
      </section>}
    </section>;
  if (inPortal) return <PortalShell title="Track parcel" activePath="/tracking" contentClassName="tracking-page tracking-in-portal">{content}</PortalShell>;
  return <main className="tracking-page">
    <header className="tracking-header">
      <button className="tracking-brand" onClick={() => navigate('/tracking')} aria-label="SwiftDrop tracking home"><Brand /></button>
      <span className="tracking-eyebrow">Parcel tracking</span>
    </header>
    {content}
  </main>;
}
