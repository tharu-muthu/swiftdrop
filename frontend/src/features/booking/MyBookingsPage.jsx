import { useEffect, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import BookingFrame from './BookingFrame.jsx';
import { loadBookingHistory } from './bookingHistoryApi.js';
import './booking-history.css';
import './booking-brand-theme.css';

const initialFilters = { search: '', state: '', from: '', to: '', page: 0, size: 10 };
const stateLabel = state => ({ CREATED: 'Pending', CONFIRMED: 'Confirmed', CANCELLED: 'Cancelled' }[state] || 'Unavailable');
const formatDate = value => value ? new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) : '—';
const formatAmount = (minor, currency) => minor == null ? 'Quote pending' : new Intl.NumberFormat('en-LK', { style: 'currency', currency: currency || 'LKR', minimumFractionDigits: 2 }).format(minor / 100);

function Metric({ icon, label, value, tone, helper }) {
  return <article className="history-metric"><span className={`history-metric-icon ${tone}`} aria-hidden="true">{icon}</span><div><small>{label}</small><strong>{value}</strong>{helper && <em>{helper}</em>}</div></article>;
}

export default function MyBookingsPage() {
  const { client } = useAuth();
  const [filters, setFilters] = useState(initialFilters);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const update = (name, value) => setFilters(current => ({ ...current, [name]: value, page: 0 }));

  useEffect(() => {
    let live = true;
    if (filters.from && filters.to && filters.from > filters.to) { setError('The start date must be before the end date.'); setLoading(false); return () => { live = false; }; }
    setLoading(true); setError('');
    loadBookingHistory(client, filters).then(result => { if (live) setData(result); })
      .catch(requestError => { if (live) setError(requestError.message); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [client, filters]);

  const summary = data?.summary;
  const visiblePages = data ? Array.from({ length: Math.min(data.totalPages, 5) }, (_, index) => index) : [];
  return <BookingFrame activePath="/bookings" headerTitle="My Bookings"><main className="booking-history-page">
    <section className="history-hero"><div><p>Home <span>›</span> My Bookings</p><h1>My Bookings</h1><span>View, track and manage all your parcel bookings in one place.</span></div><button onClick={() => navigate('/bookings/new')}>＋ Create New Booking</button></section>
    <section className="history-metrics" aria-label="Booking summary">
      <Metric icon="□" label="Total Bookings" value={summary ? summary.totalBookings : '—'} tone="blue" />
      <Metric icon="▱" label="In Transit" value="—" tone="blue" helper="M6 pending" />
      <Metric icon="✓" label="Delivered" value="—" tone="green" helper="M6 pending" />
      <Metric icon="◷" label="Pending" value={summary ? summary.pendingBookings : '—'} tone="orange" />
    </section>
    <section className="history-filter-card" aria-label="Booking filters"><div className="history-tabs"><button className={!filters.state ? 'selected' : ''} onClick={() => update('state', '')}>All bookings</button><button className={filters.state === 'CREATED' ? 'selected' : ''} onClick={() => update('state', 'CREATED')}>Pending</button><button className={filters.state === 'CONFIRMED' ? 'selected' : ''} onClick={() => update('state', 'CONFIRMED')}>Confirmed</button><button className={filters.state === 'CANCELLED' ? 'selected' : ''} onClick={() => update('state', 'CANCELLED')}>Cancelled</button></div>
      <div className="history-filters"><label className="history-search"><span aria-hidden="true">⌕</span><span className="sr-only">Search bookings</span><input value={filters.search} onChange={event => update('search', event.target.value)} placeholder="Search by tracking number or recipient name" maxLength="80" /></label><label>From<input type="date" value={filters.from} onChange={event => update('from', event.target.value)} /></label><label>To<input type="date" value={filters.to} onChange={event => update('to', event.target.value)} /></label><button className="history-reset" onClick={() => setFilters(initialFilters)}>↻ Reset</button></div>
    </section>
    {error && <p className="history-error" role="alert">{error}</p>}
    {loading && <section className="history-message" role="status">Loading your bookings…</section>}
    {!loading && !error && data?.bookings.length === 0 && <section className="history-message"><strong>No bookings found</strong><span>Try different filters, or create your first parcel booking.</span><button onClick={() => navigate('/bookings/new')}>Create New Booking</button></section>}
    {!loading && !error && data?.bookings.length > 0 && <section className="history-table-wrap"><table className="history-table"><thead><tr><th>Booking</th><th>Recipient</th><th>Route</th><th>Booked on</th><th>Delivery fee</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{data.bookings.map(booking => <tr key={booking.id}><td data-label="Booking"><strong>{booking.id.slice(0, 8).toUpperCase()}</strong><button className="tracking-link" onClick={() => navigate(`/tracking?number=${encodeURIComponent(booking.trackingNumber)}`)}>{booking.trackingNumber}</button></td><td data-label="Recipient"><strong>{booking.recipientName || 'Not recorded'}</strong><span>{booking.recipientPhone || '—'}</span></td><td data-label="Route">{booking.pickupCity && booking.deliveryCity ? `${booking.pickupCity} → ${booking.deliveryCity}` : 'Route not recorded'}</td><td data-label="Booked on">{formatDate(booking.bookedAt)}</td><td data-label="Delivery fee">{formatAmount(booking.deliveryFeeMinor, booking.currencyCode)}</td><td data-label="Status"><span className={`history-status ${booking.bookingState.toLowerCase()}`}>{stateLabel(booking.bookingState)}</span></td><td data-label="Actions"><button className="history-view" onClick={() => navigate(`/bookings/${booking.id}`)}>View Details</button></td></tr>)}</tbody></table>
      <footer className="history-pagination"><span>Showing {data.totalElements === 0 ? 0 : data.page * data.size + 1}–{Math.min((data.page + 1) * data.size, data.totalElements)} of {data.totalElements} bookings</span><div><button disabled={data.page === 0} onClick={() => update('page', data.page - 1)}>‹</button>{visiblePages.map(page => <button key={page} className={page === data.page ? 'current-page' : ''} onClick={() => update('page', page)}>{page + 1}</button>)}<button disabled={data.page >= data.totalPages - 1} onClick={() => update('page', data.page + 1)}>›</button></div></footer>
    </section>}
  </main></BookingFrame>;
}
