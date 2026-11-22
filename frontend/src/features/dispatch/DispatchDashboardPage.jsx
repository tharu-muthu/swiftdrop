import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import WorkspacePage, { JsonRequest } from '../../components/WorkspacePage.jsx';

const emptyProfile = { userId: '', vehicleType: '', licensePlate: '', active: true };
const dateTime = value => value ? new Date(value).toLocaleString() : 'Not recorded';

export default function DispatchDashboardPage() {
  const { client } = useAuth();
  const [couriers, setCouriers] = useState([]);
  const [board, setBoard] = useState(null);
  const [page, setPage] = useState(0);
  const [unassigned, setUnassigned] = useState(false);
  const [overdue, setOverdue] = useState(false);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState('');
  const [details, setDetails] = useState(null);
  const [history, setHistory] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [courierId, setCourierId] = useState('');
  const [reason, setReason] = useState('');
  const [profile, setProfile] = useState(() => ({ ...emptyProfile, userId: new URLSearchParams(window.location.search).get('courier') || '' }));
  const [date, setDate] = useState('');
  const [report, setReport] = useState(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [pending, setPending] = useState(false);
  const detailRequest = useRef(0);

  function applyCouriers(next) {
    setCouriers(next);
    setCourierId(current => next.some(row => row.eligible && row.courierId === current) ? current : '');
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([
      client.request('/api/v1/dispatch/couriers'),
      client.request(`/api/v1/dispatch/bookings?page=${page}&size=50&unassigned=${unassigned}&overdue=${overdue}`),
    ]).then(([fleet, result]) => {
      if (!active) return;
      applyCouriers(fleet);
      setBoard(result);
    }).catch(failure => { if (active) { setBoard(null); setError(failure.message); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [client, page, unassigned, overdue, revision]);

  function clearSelection() {
    detailRequest.current += 1;
    setSelected(''); setDetails(null); setHistory([]); setCourierId(''); setReason(''); setDetailLoading(false);
  }

  async function choose(bookingId) {
    const requestId = ++detailRequest.current;
    setSelected(bookingId); setDetails(null); setHistory([]); setCourierId(''); setReason('');
    setDetailLoading(true); setError('');
    try {
      const [result, fleet] = await Promise.all([
        client.request(`/api/v1/dispatch/bookings/${bookingId}`), client.request('/api/v1/dispatch/couriers'),
      ]);
      if (detailRequest.current !== requestId) return;
      setDetails(result.booking); setHistory(result.assignments); applyCouriers(fleet);
    } catch (failure) { if (detailRequest.current === requestId) setError(failure.message); }
    finally { if (detailRequest.current === requestId) setDetailLoading(false); }
  }

  async function action(work, message) {
    setPending(true); setError(''); setNotice('');
    try { await work(); setRevision(value => value + 1); setNotice(message); }
    catch (failure) {
      setError(failure.message);
      // An account may have lost eligibility since this page was loaded.
      try { applyCouriers(await client.request('/api/v1/dispatch/couriers')); } catch { /* Retain the original operation error. */ }
    } finally { setPending(false); }
  }

  function changeFilter(name, checked) {
    clearSelection(); setPage(0);
    if (name === 'unassigned') setUnassigned(checked); else setOverdue(checked);
  }

  function showOverdue() {
    clearSelection(); setPage(0); setUnassigned(false); setOverdue(true);
  }

  async function loadReport(event) {
    event.preventDefault(); setReportLoading(true); setError('');
    try { setReport(await client.request(`/api/v1/dispatch/reports/daily${date ? `?date=${date}` : ''}`)); }
    catch (failure) { setError(failure.message); }
    finally { setReportLoading(false); }
  }

  const booking = board?.items.find(row => row.bookingId === selected);
  const available = couriers.filter(row => row.eligible && row.courierId !== booking?.courierId);

  return <WorkspacePage title="Dispatch operations">
    {error && <p className="error" role="alert">{error}</p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    <button disabled={pending || loading} onClick={() => { clearSelection(); setRevision(value => value + 1); }}>Refresh board</button>
    <section aria-label="Parcel board">
      <h2>Parcel board</h2>
      <div className="actions">
        <label className="check-label"><input type="checkbox" checked={unassigned} onChange={event => changeFilter('unassigned', event.target.checked)} />Unassigned only</label>
        <label className="check-label"><input type="checkbox" checked={overdue} onChange={event => changeFilter('overdue', event.target.checked)} />Overdue only</label>
      </div>
      {board?.delayHours && <p>Overdue means an open delivery has had no update for {board.delayHours} hours. Delivered and failed parcels are excluded.</p>}
      {loading ? <p role="status">Loading parcel board…</p> : board && <>
        <p>{board.totalElements} matching parcel{board.totalElements === 1 ? '' : 's'}{overdue ? ' · oldest updates first' : ''}</p>
        <div className="table-scroll"><table><thead><tr><th>Tracking</th><th>Route</th><th>Status</th><th>Last update</th><th>Courier</th><th></th></tr></thead>
          <tbody>{board.items.map(row => <tr key={row.bookingId}>
            <td><a href={`/tracking/${row.trackingNumber}`}>{row.trackingNumber}</a></td><td>{row.pickupCity} → {row.deliveryCity}</td>
            <td>{row.status.replaceAll('_', ' ')}{row.overdue && <span className="attention-flag">Overdue</span>}</td>
            <td>{dateTime(row.updatedAt)}</td><td>{row.courierId || 'Unassigned'}</td>
            <td><button className="secondary" disabled={pending || detailLoading} onClick={() => choose(row.bookingId)}>Select</button></td>
          </tr>)}</tbody></table></div>
        {!board.items.length && <p>No deliveries found.</p>}
        <div className="actions" aria-label="Dispatch pagination">
          <button disabled={page === 0 || pending} onClick={() => { clearSelection(); setPage(page - 1); }}>Previous</button>
          <span>Page {page + 1} of {Math.max(1, board.totalPages)}</span>
          <button disabled={page + 1 >= board.totalPages || pending} onClick={() => { clearSelection(); setPage(page + 1); }}>Next</button>
        </div>
      </>}
    </section>
    {detailLoading && <p role="status">Loading parcel details…</p>}
    {booking && details && <section aria-label="Dispatch parcel details">
      <h2>{booking.trackingNumber}</h2>
      <p>Pickup: {details.pickupAddressSnapshot} · {details.senderName} {details.senderPhone}<br />
        Deliver to: {details.dropoffAddressSnapshot} · {details.recipientName} {details.recipientPhone}<br />
        {details.parcelDescription} · {details.parcelWeightGrams} g</p>
      {['DELIVERED', 'FAILED'].includes(booking.status) ? <p>Delivery is complete.</p> : booking.status !== 'CREATED' ? <p>Pickup has started. This delivery cannot be reassigned.</p> : <form onSubmit={event => {
        event.preventDefault();
        action(async () => {
          await client.request(`/api/v1/dispatch/assignments${booking.courierId ? '/reassign' : ''}`, JsonRequest('POST',
            booking.courierId ? { bookingId: selected, newCourierId: courierId, reason } : { bookingId: selected, courierId }));
          clearSelection();
        }, 'Assignment saved.');
      }}>
        <label>Courier<select required value={courierId} onChange={event => setCourierId(event.target.value)}>
          <option value="">Select eligible courier</option>{available.map(row => <option key={row.courierId} value={row.courierId}>{row.vehicleType} · {row.licensePlate} · workload {row.workload}</option>)}
        </select></label>
        {!available.length && <p>No eligible couriers are available. Check account access and operational availability.</p>}
        {booking.courierId && <label>Reassignment reason<textarea required maxLength={200} value={reason} onChange={event => setReason(event.target.value)} /></label>}
        <button disabled={pending || !courierId}>{booking.courierId ? 'Reassign' : 'Assign'} courier</button>
      </form>}
      <h3>Assignment history</h3>{history.map(row => <p key={row.id}>{row.courier_id} · {dateTime(row.assigned_at)} · {row.is_active ? 'Current' : row.revocation_reason}</p>)}
    </section>}
    <section><h2>Courier operational profiles</h2>
      <p>First grant the verified user the Courier role in User management, then register their vehicle here.</p>
      <form onSubmit={event => {
        event.preventDefault();
        action(() => client.request(`/api/v1/dispatch/couriers/${profile.userId}`, JsonRequest('PUT', {
          vehicleType: profile.vehicleType, licensePlate: profile.licensePlate, active: profile.active,
        })), 'Courier profile saved.');
      }}>
        <div className="form-grid">
          <label>Courier user ID<input required value={profile.userId} onChange={event => setProfile({ ...profile, userId: event.target.value })} /></label>
          <label>Vehicle type<input required maxLength={50} value={profile.vehicleType} onChange={event => setProfile({ ...profile, vehicleType: event.target.value })} /></label>
          <label>License plate<input maxLength={20} value={profile.licensePlate} onChange={event => setProfile({ ...profile, licensePlate: event.target.value })} /></label>
          <label className="check-label"><input type="checkbox" checked={profile.active} onChange={event => setProfile({ ...profile, active: event.target.checked })} />Active</label>
        </div><button disabled={pending}>Save courier profile</button>
      </form>
      <ul>{couriers.map(row => <li key={row.courierId}>{row.vehicleType} · {row.licensePlate} · {row.eligible ? 'Eligible' : row.unavailableReason === 'PROFILE_INACTIVE' ? 'Operational profile inactive' : 'Account unavailable'} · workload {row.workload}{' '}
        <button className="secondary" onClick={() => setProfile({ userId: row.courierId, vehicleType: row.vehicleType, licensePlate: row.licensePlate || '', active: row.active })}>Edit</button>
      </li>)}</ul>
    </section>
    <section><h2>Daily delivery report (UTC)</h2>
      <form onSubmit={loadReport}><label>Report date<input type="date" value={date} onChange={event => setDate(event.target.value)} /></label><button disabled={reportLoading}>Load report</button></form>
      {report && <><p>{report.date} · Open deliveries without an update for {report.delayHours} hours: {report.overdueOpenDeliveries}</p>
        <button className="secondary" onClick={showOverdue}>View overdue parcels</button>
        {report.results.map(row => <p key={`${row.courierId}-${row.status}`}>{row.courierId} · {row.status}: {row.count}</p>)}
        {!report.results.length && <p>No completed deliveries for this date.</p>}</>}
    </section>
  </WorkspacePage>;
}
