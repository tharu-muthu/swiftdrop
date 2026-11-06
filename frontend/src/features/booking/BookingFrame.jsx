import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import './booking.css';
import './booking-brand-theme.css';

const navItems = [
  ['⌂', 'Dashboard', '/booking'], ['□', 'Book Parcel', '/bookings/new'], ['▣', 'My Bookings', '/bookings'],
  ['⌖', 'Track Parcel', '/tracking'], ['▤', 'Receipts', '/receipts'], ['?', 'Help', '/help'],
];

function BrandMark({ compact = false }) {
  return <div className={compact ? 'booking-form-brand booking-form-brand-compact' : 'booking-form-brand'} aria-label="SwiftDrop">
    <svg viewBox="0 0 54 52" aria-hidden="true"><path d="M4 4h48l-10 10H22l13 8h14l-10 10H28l14 8-18 8L6 38l9-9 13 7 7-3-14-8H10L4 15h19L4 4z" /></svg>
    {!compact && <span>Swift<b>Drop</b></span>}
  </div>;
}

export default function BookingFrame({ activePath, children, headerTitle = 'Create Booking' }) {
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
        <div className="header-title"><BrandMark compact /><span><strong>{headerTitle}</strong><small>M4 Parcel Booking Module</small></span></div>
        <div className="header-user"><span className="bell" aria-label="Notifications">♧</span><span className="avatar">{user.fullName?.split(' ').map(part => part[0]).slice(0, 2).join('').toUpperCase()}</span>
          <span className="user-name"><strong>{user.fullName}</strong><small>SwiftDrop customer</small></span><button className="account-menu" onClick={() => navigate('/account')} aria-label="Open account">⌄</button></div>
      </header>
      {children}
    </main>
  </div>;
}
