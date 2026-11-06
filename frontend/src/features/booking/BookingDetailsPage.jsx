import { useEffect, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import BookingFrame from './BookingFrame.jsx';
import { loadBookingDetails } from './bookingHistoryApi.js';
import './booking-history.css';

export default function BookingDetailsPage({ bookingId }) {
  const { client } = useAuth();
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { let live = true; loadBookingDetails(client, bookingId).then(result => { if (live) setBooking(result); }).catch(result => { if (live) setError(result.message); }); return () => { live = false; }; }, [client, bookingId]);
  return <BookingFrame activePath="/bookings" headerTitle="Booking Details"><main className="booking-history-page"><button className="history-back" onClick={() => navigate('/bookings')}>← Back to My Bookings</button>{error && <p className="history-error" role="alert">{error}</p>}{!booking && !error && <section className="history-message" role="status">Loading booking details…</section>}{booking && <section className="detail-card"><p className="detail-eyebrow">Booking details</p><h1>{booking.trackingNumber}</h1><span className={`history-status ${booking.bookingState.toLowerCase()}`}>{booking.bookingState}</span><dl><dt>Recipient</dt><dd>{booking.recipientName || 'Not recorded'} {booking.recipientPhone ? `· ${booking.recipientPhone}` : ''}</dd><dt>Route</dt><dd>{booking.pickupCity && booking.deliveryCity ? `${booking.pickupCity} → ${booking.deliveryCity}` : 'Route not recorded'}</dd><dt>Delivery fee</dt><dd>{booking.deliveryFeeMinor == null ? 'Quote pending' : `LKR ${(booking.deliveryFeeMinor / 100).toFixed(2)}`}</dd></dl></section>}</main></BookingFrame>;
}
