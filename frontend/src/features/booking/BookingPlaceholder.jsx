import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import './booking.css';

const copy = {
  '/bookings/new': ['Create a booking', 'A confirmed booking requires an M3 quote. Pricing has not been integrated into this environment yet.'],
  '/bookings': ['My bookings', 'Your booking list will appear here as M4 booking creation is enabled.'],
  '/quote': ['Get a quote', 'Pricing is owned by M3. This page is ready to connect once its quote contract is available.'],
  '/receipts': ['Receipts', 'Receipt downloads will appear here after the receipt workflow is enabled.'],
  '/tracking': ['Track a parcel', 'Real-time tracking is owned by M7. This booking home does not fabricate tracking results.'],
  '/help': ['Help', 'Need assistance? Contact the SwiftDrop support team with your booking reference.'],
};

export default function BookingPlaceholder({ path }) {
  const { user } = useAuth();
  const [title, message] = copy[path] || ['Booking', 'This booking destination is not available.'];
  const trackingNumber = new URLSearchParams(window.location.search).get('number');
  return <main className="booking-placeholder"><div className="placeholder-card"><p className="placeholder-brand">◇ SwiftDrop</p><h1>{title}</h1>
    {trackingNumber && <p className="tracking-request">Tracking reference: <strong>{trackingNumber}</strong></p>}<p>{message}</p>
    <p className="placeholder-user">Signed in as {user.fullName}</p><button onClick={() => navigate('/booking')}>Return to Booking Home</button></div></main>;
}
