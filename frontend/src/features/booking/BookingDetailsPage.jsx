import { useEffect, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import BookingFrame from './BookingFrame.jsx';
import { loadBookingDetails } from './bookingHistoryApi.js';
import { deliveryStatus } from '../../app/deliveryStatus.js';
import { acceptTrackingSnapshot, trackingEventsUrl } from '../tracking/trackingApi.js';
import './booking-history.css';
import './booking-tools.css';

function DetailField({ label, children }) {
  return <div className="detail-field"><dt>{label}</dt><dd>{children}</dd></div>;
}

export default function BookingDetailsPage({ bookingId }) {
  const { client } = useAuth();
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');
  const [tracking, setTracking] = useState(null);
  const [trackingConnection, setTrackingConnection] = useState('connecting');

  useEffect(() => {
    if (!booking?.trackingNumber) return undefined;
    let live = true;
    let source;
    let reconnectTimer;
    const number = booking.trackingNumber;
    const acceptSnapshot = next => setTracking(current => {
      const accepted = acceptTrackingSnapshot(current, next);
      if (accepted === current) return current;
      return { ...current, ...accepted };
    });
    const reconnect = () => {
      if (!live) return;
      reconnectTimer = window.setTimeout(() => {
        client.request(`/api/v1/tracking/customer/${encodeURIComponent(number)}`)
          .then(value => {
            if (!live) return;
            acceptSnapshot(value);
            setTrackingConnection('live');
            connect();
          })
          .catch(() => {
            if (live) {
              setTrackingConnection('reconnecting');
              reconnect();
            }
          });
      }, 3000);
    };
    const connect = () => {
      if (!live) return;
      source = new EventSource(trackingEventsUrl(number));
      source.addEventListener('tracking-update', event => {
        if (!live) return;
        try {
          acceptSnapshot(JSON.parse(event.data));
          setTrackingConnection('live');
        } catch {
          setTrackingConnection('reconnecting');
        }
      });
      source.onerror = () => {
        source.close();
        if (!live) return;
        setTrackingConnection('reconnecting');
        reconnect();
      };
    };
    client.request(`/api/v1/tracking/customer/${encodeURIComponent(number)}`)
      .then(value => {
        if (!live) return;
        setTracking(value);
        setTrackingConnection('connecting');
        connect();
      })
      .catch(failure => { if (live) setError(failure.message); });
    return () => {
      live = false;
      if (source) source.close();
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
    };
  }, [client, booking?.trackingNumber]);

  useEffect(() => {
    let live = true;
    loadBookingDetails(client, bookingId).then(result => { if (live) setBooking(result); })
      .catch(result => { if (live) setError(result.message); });
    return () => { live = false; };
  }, [client, bookingId]);

  const status = tracking
    ? deliveryStatus(tracking.currentStatus, booking?.assigned)
    : booking ? deliveryStatus(booking.deliveryStatus || booking.bookingState, booking.assigned) : null;
  return <BookingFrame activePath="/bookings" headerTitle="Booking Details"><div className="booking-history-page booking-detail-page">
    <button className="history-back" onClick={() => navigate('/bookings')}>← Back to My Bookings</button>
    {error && <p className="history-error" role="alert">{error}</p>}
    {!booking && !error && <section className="history-message" role="status">Loading booking details…</section>}
    {booking && <section className="detail-card">
      <header className="detail-header">
        <div><p className="detail-eyebrow">Parcel booking</p><h1>{booking.trackingNumber}</h1><p className="detail-route">{booking.pickupCity && booking.deliveryCity ? `${booking.pickupCity} → ${booking.deliveryCity}` : 'Route not recorded'}</p></div>
        <span className={`history-status ${status.className}`}>{status.label}</span>
      </header>
      <dl className="detail-overview">
        <DetailField label="Recipient">{booking.recipientName || 'Not recorded'} {booking.recipientPhone ? `· ${booking.recipientPhone}` : ''}</DetailField>
        <DetailField label="Delivery fee">{booking.deliveryFeeMinor == null ? 'Quote pending' : `LKR ${(booking.deliveryFeeMinor / 100).toFixed(2)}`}</DetailField>
        <DetailField label="Current status">{status.label}</DetailField>
      </dl>
      {booking.details && <section className="detail-section" aria-labelledby="shipment-details"><h2 id="shipment-details">Shipment details</h2><dl className="detail-grid">
        <DetailField label="Pickup address">{booking.details.pickupAddressSnapshot}, {booking.details.pickupCity} {booking.details.pickupPostalCode}</DetailField>
        <DetailField label="Delivery address">{booking.details.dropoffAddressSnapshot}, {booking.details.deliveryCity} {booking.details.deliveryPostalCode}</DetailField>
        <DetailField label="Parcel">{booking.details.parcelDescription} · {booking.details.parcelWeightGrams} g · {booking.details.parcelSize}</DetailField>
      </dl></section>}
      <div className="detail-actions"><button className="history-view" onClick={() => navigate(`/tracking/${encodeURIComponent(booking.trackingNumber)}`)}>Track delivery</button></div>
      {tracking && <section className="detail-timeline" aria-labelledby="delivery-timeline">
        <h2 id="delivery-timeline">Delivery updates</h2>
        <p className="detail-live-status" role="status">Live updates: {trackingConnection}</p>
        <ol>{tracking.timeline.map(item => <li key={item.sequenceNo}><strong>{item.status.replaceAll('_', ' ')}</strong><time dateTime={item.occurredAt}>{new Date(item.occurredAt).toLocaleString()}</time></li>)}</ol>
      </section>}
    </section>}
  </div></BookingFrame>;
}
