import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import { loadActiveCouriers, assignParcel } from './dispatchApi.js';
import './dispatch.css';
import BookingFrame from '../booking/BookingFrame.jsx';

export default function DispatchDashboardPage() {
  const { client, user } = useAuth();
  const [couriers, setCouriers] = useState([]);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [bookingId, setBookingId] = useState('');

  async function refresh() {
    try {
      setError('');
      setCouriers(await loadActiveCouriers(client));
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  useEffect(() => {
    refresh();
  }, [client]);

  async function handleAssign(courierId) {
    if (!bookingId.trim()) {
      return setError('Please enter a valid Booking ID to assign this parcel.');
    }
    setPending(true);
    try {
      await assignParcel(client, bookingId.trim(), courierId);
      setBookingId('');
      setError('');
      alert('Parcel successfully assigned! The courier has been notified in M6.');
      await refresh();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setPending(false);
    }
  }

  return (
    <BookingFrame activePath="/dispatch" headerTitle="Active Couriers">
    <main className="dispatch-shell">
      <header className="dispatch-header">
        <span className="dispatch-brand">◇ SwiftDrop</span>
        <span>{user.fullName} · Dispatch Admin</span>
      </header>
      
      <section className="dispatch-content">
        <div>
          <p className="dispatch-kicker">M5 · DISPATCH OPERATIONS</p>
          <h1>Active Couriers</h1>
          <p style={{ color: '#6b7280' }}>Review available fleets and assign pending parcels.</p>
        </div>

        {error && <p className="dispatch-error" role="alert">{error}</p>}

        <div className="dispatch-input-group">
          <label htmlFor="bookingId">Pending Booking ID</label>
          <input 
            id="bookingId"
            value={bookingId} 
            onChange={e => setBookingId(e.target.value)} 
            placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000" 
            disabled={pending}
          />
        </div>

        <div className="courier-list">
          {couriers.length === 0 && (
            <p style={{ color: '#6b7280', padding: '1rem 0' }}>No couriers are currently active.</p>
          )}
          
          {couriers.map(courier => (
            <article className="courier-card" key={courier.courierId}>
              <div>
                <strong>{courier.vehicleType || 'Vehicle Pending'}</strong>
                <small>Plate: {courier.licensePlate || 'N/A'} | ID: {courier.courierId}</small>
              </div>
              <button 
                className="assign-button" 
                disabled={pending} 
                onClick={() => handleAssign(courier.courierId)}
              >
                {pending ? 'Assigning...' : 'Assign Parcel'}
              </button>
            </article>
          ))}
        </div>
      </section>
    </main>
    </BookingFrame>
  );
}