import { useEffect, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import { loadBookingDashboard } from './bookingApi.js';
import heroVan from '../../assets/booking-hero-van.png';
import './booking.css';
import './booking-validation.css';

const navItems = [
  ['⌂', 'Dashboard', '/booking'], ['□', 'Book Parcel', '/bookings/new'], ['▣', 'My Bookings', '/bookings'],
  ['⌖', 'Track Parcel', '/tracking'], ['▤', 'Receipts', '/receipts'], ['?', 'Help', '/help'],
];

function BrandMark({ compact = false }) {
  return <div className={compact ? 'booking-mark compact-mark' : 'booking-mark'} aria-label="SwiftDrop">
    <span className="cube-mark" aria-hidden="true">◇</span>{!compact && <span>SwiftDrop</span>}
  </div>;
}

function ActionCard({ icon, tone, title, description, to }) {
  return <button className="quick-action" onClick={() => navigate(to)}>
    <span className={`action-icon ${tone}`} aria-hidden="true">{icon}</span>
    <span className="quick-copy"><strong>{title}</strong><small>{description}</small></span><span className="arrow" aria-hidden="true">›</span>
  </button>;
}

function StatCard({ icon, tone, label, value, hint }) {
  return <div className="booking-stat">
    <span className={`stat-icon ${tone}`} aria-hidden="true">{icon}</span>
    <span><small>{label}</small><strong>{value}</strong></span><em>{hint}</em>
  </div>;
}

export function BookingFrame({ activePath, children }) {
  const { user } = useAuth();
  return <div className="booking-shell">
    <aside className="booking-sidebar">
      <BrandMark />
      <nav aria-label="Booking navigation">{navItems.map(([icon, label, to]) => <button key={to}
        className={to === activePath ? 'side-link active' : 'side-link'} onClick={() => navigate(to)}>
        <span aria-hidden="true">{icon}</span>{label}</button>)}</nav>
      <div className="sidebar-parcels" aria-hidden="true"><i /><i /><i /></div>
    </aside>
    <main className="booking-main">
      <header className="booking-header">
        <div className="header-title"><BrandMark compact /><span><strong>{activePath === '/booking' ? 'Booking Home' : 'Create Booking'}</strong><small>M4 Parcel Booking Module</small></span></div>
        <div className="header-user"><span className="bell" aria-label="Notifications">♧</span><span className="avatar">{user.fullName?.split(' ').map(part => part[0]).slice(0, 2).join('').toUpperCase()}</span>
          <span className="user-name"><strong>{user.fullName}</strong><small>SwiftDrop customer</small></span><button className="account-menu" onClick={() => navigate('/account')} aria-label="Open account">⌄</button></div>
      </header>
      {children}
    </main>
  </div>;
}

export default function BookingHomePage() {
  const { user, client } = useAuth();
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [trackingError, setTrackingError] = useState('');

  useEffect(() => {
    let active = true;
    loadBookingDashboard(client).then(result => { if (active) setSummary(result); })
      .catch(requestError => { if (active) setError(requestError.message); });
    return () => { active = false; };
  }, [client]);

  function track(event) {
    event.preventDefault();
    const number = trackingNumber.trim().toUpperCase();
    if (!/^SDP-[A-Z0-9]{6,32}$/.test(number)) {
      setTrackingError('Enter a tracking number in the format SDP-XXXXXX.');
      return;
    }
    setTrackingError('');
    navigate(`/tracking${number ? `?number=${encodeURIComponent(number)}` : ''}`);
  }

  return <BookingFrame activePath="/booking">
      <section className="booking-hero">
        <img src={heroVan} alt="Delivery van and parcels on a sunny campus street" />
        <div className="hero-overlay"><p>Welcome to <b>SwiftDrop</b></p><h1>Book a Parcel</h1><span>Create parcel bookings, manage shipments, track their status, and access receipts — all in one place.</span>
          <button onClick={() => navigate('/bookings/new')}><b aria-hidden="true">＋</b> Create New Booking <i aria-hidden="true">→</i></button></div>
      </section>
      <section className="quick-actions" aria-label="Quick actions">
        <ActionCard icon="◇" tone="blue" title="Get Quote" description="Check parcel delivery rates instantly." to="/quote" />
        <ActionCard icon="□" tone="cyan" title="Create Booking" description="Book a parcel in just a few steps." to="/bookings/new" />
        <ActionCard icon="⌖" tone="green" title="Track Parcel" description="Check the real-time status of your parcel." to="/tracking" />
        <ActionCard icon="▤" tone="purple" title="Download Receipt" description="Get and save booking receipts anytime." to="/receipts" />
      </section>
      <section className="summary-panel" aria-labelledby="booking-summary-heading">
        <div className="summary-heading"><h2 id="booking-summary-heading"><span aria-hidden="true">▥</span> Booking Summary</h2><small>This Month Overview</small></div>
        {error && <p className="dashboard-error" role="alert">{error}</p>}
        <div className="stat-grid" aria-busy={!summary}>
          <StatCard icon="□" tone="blue" label="Active Bookings" value={summary ? summary.activeBookings : '—'} hint="Live" />
          <StatCard icon="▱" tone="green" label="Delivered Parcels" value="—" hint="M6 status" />
          <StatCard icon="▤" tone="red" label="Pending Receipts" value={summary ? summary.pendingReceipts : '—'} hint="Live" />
          <StatCard icon="▣" tone="purple" label="This Month Shipments" value={summary ? summary.thisMonthShipments : '—'} hint="Live" />
        </div>
      </section>
      <section className="tracking-card" aria-labelledby="track-heading">
        <div><p className="tracking-kicker" aria-hidden="true">⌖</p><h2 id="track-heading">Track Your Parcel</h2><span>Enter a tracking number to check the latest status.</span></div>
        <form onSubmit={track} noValidate><label className="sr-only" htmlFor="tracking-number">Tracking number</label><input id="tracking-number" value={trackingNumber} onChange={event => { setTrackingNumber(event.target.value); setTrackingError(''); }} maxLength="40" placeholder="e.g. SDP-000526" aria-invalid={Boolean(trackingError)} aria-describedby={trackingError ? 'tracking-error' : undefined} />
          <button>Track Parcel</button></form>
        {trackingError && <p id="tracking-error" className="tracking-error" role="alert">{trackingError}</p>}
      </section>
  </BookingFrame>;
}
