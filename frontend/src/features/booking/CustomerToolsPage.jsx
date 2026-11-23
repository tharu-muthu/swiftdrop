import { useEffect, useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import BookingFrame from './BookingFrame.jsx';
import './booking-tools.css';

const quoteFields = [
  ['pickupPostalCode', 'Pickup postal code', 'text'],
  ['dropoffPostalCode', 'Delivery postal code', 'text'],
  ['weightGrams', 'Parcel weight (grams)', 'number'],
];

function jsonRequest(method, body) {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

function downloadReceipt(receipt) {
  const text = ['SwiftDrop delivery receipt', `Reference: ${receipt.trackingNumber}`, `Booked (UTC): ${receipt.createdAt}`,
    `Route: ${receipt.pickupCity} to ${receipt.deliveryCity}`, `Recipient: ${receipt.recipientName}`,
    `Parcel: ${receipt.parcelDescription} (${receipt.parcelWeightGrams} g)`,
    `Delivery fee: ${receipt.currencyCode} ${(receipt.quotedAmountMinor / 100).toFixed(2)}`, 'Delivery completed.'].join('\r\n');
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url; link.download = `${receipt.trackingNumber}-receipt.txt`; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function CustomerToolsPage({ path }) {
  const { client } = useAuth();
  const [rows, setRows] = useState([]);
  const [receipt, setReceipt] = useState(null);
  const [quote, setQuote] = useState(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [form, setForm] = useState({ pickupPostalCode: '', dropoffPostalCode: '', weightGrams: '', size: 'SMALL' });
  const isReceipts = path === '/receipts';
  const isQuote = path === '/quote';
  const title = isReceipts ? 'Delivery Receipts' : isQuote ? 'Get a Delivery Quote' : 'Booking Help';
  const delivered = rows.filter(booking => booking.deliveryStatus === 'DELIVERED');

  useEffect(() => {
    if (!isReceipts) return undefined;
    let live = true;
    client.request(`/api/v1/bookings?page=${page}&size=50`).then(data => {
      if (live) { setRows(data.bookings); setTotalPages(data.totalPages); }
    }).catch(failure => { if (live) setError(failure.message); });
    return () => { live = false; };
  }, [client, isReceipts, page]);

  async function loadReceipt(id) {
    setPending(true); setError('');
    try { setReceipt(await client.request(`/api/v1/bookings/${id}/receipt`)); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  async function requestQuote(event) {
    event.preventDefault(); setPending(true); setError(''); setQuote(null);
    try { setQuote(await client.request('/api/v1/quotes', jsonRequest('POST', { ...form, weightGrams: Number(form.weightGrams) }))); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }

  return <BookingFrame activePath={isQuote ? '/bookings/new' : path} headerTitle={title}><div className="booking-tools-page">
    {!isReceipts && <section className="booking-tools-hero"><p>{isQuote ? 'Plan your delivery' : 'Customer guide'}</p><h1>{title}</h1><span>{isQuote ? 'Check the delivery price before creating a booking.' : 'Everything you need to create, track, and receive a parcel.'}</span></section>}
    {error && <p className="booking-tools-error" role="alert">{error}</p>}
    {path === '/help' && <section className="booking-help-card"><h2>Send a parcel in four steps</h2><ol className="booking-help-steps"><li><strong>1. Add parcel details</strong><span>Enter pickup, recipient, and parcel information.</span></li><li><strong>2. Request a quote</strong><span>Review the delivery price and its expiry time.</span></li><li><strong>3. Confirm the booking</strong><span>Save the booking and share its tracking number.</span></li><li><strong>4. Track delivery</strong><span>A dispatcher assigns a courier and delivery updates appear in tracking.</span></li></ol><button onClick={() => navigate('/bookings/new')}>Create a booking</button></section>}
    {isQuote && <section className="booking-tool-card"><header><h2>Delivery details</h2><p>Enter the route and parcel weight to calculate a price.</p></header><form className="quote-form" onSubmit={requestQuote}>{quoteFields.map(([key, label, type]) => <label key={key}>{label}<input required type={type} min={key === 'weightGrams' ? 1 : undefined} max={key === 'weightGrams' ? 100000 : undefined} pattern={type === 'text' ? '[0-9]{5}' : undefined} value={form[key]} onChange={event => { setQuote(null); setForm({ ...form, [key]: event.target.value }); }} /></label>)}<label>Parcel size<select value={form.size} onChange={event => { setQuote(null); setForm({ ...form, size: event.target.value }); }}>{['SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE'].map(size => <option key={size}>{size.replaceAll('_', ' ')}</option>)}</select></label><button disabled={pending}>{pending ? 'Calculating…' : 'Get quote'}</button></form>{quote && <section className="quote-result" role="status"><div><small>Estimated delivery fee</small><strong>{quote.currency} {(quote.amountMinor / 100).toFixed(2)}</strong><span>Available until {new Date(quote.expiresAt).toLocaleString()}.</span></div><button onClick={() => navigate('/bookings/new')}>Create booking</button></section>}</section>}
    {isReceipts && <section className="booking-tool-card"><header><h2>Available receipts</h2><p>Only successfully delivered parcels have a receipt.</p></header>{delivered.length === 0 ? <div className="booking-tools-empty"><strong>No delivery receipts yet</strong><span>Completed deliveries will appear here automatically.</span><button onClick={() => navigate('/bookings')}>View my bookings</button></div> : <div className="receipt-list">{delivered.map(booking => <article key={booking.id}><div><strong>{booking.trackingNumber}</strong><span>{booking.pickupCity} → {booking.deliveryCity}</span></div><button disabled={pending} onClick={() => loadReceipt(booking.id)}>Open receipt</button></article>)}</div>}<footer className="tool-pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(1, totalPages)}</span><button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next</button></footer></section>}
    {receipt && <section className="receipt-view booking-tool-card" aria-label="Delivery receipt"><header><h2>Delivery receipt</h2><p>{receipt.trackingNumber}</p></header><dl><div><dt>Route</dt><dd>{receipt.pickupCity} → {receipt.deliveryCity}</dd></div><div><dt>Recipient</dt><dd>{receipt.recipientName}</dd></div><div><dt>Parcel</dt><dd>{receipt.parcelDescription} · {receipt.parcelWeightGrams} g</dd></div><div><dt>Delivery fee</dt><dd>{receipt.currencyCode} {(receipt.quotedAmountMinor / 100).toFixed(2)}</dd></div></dl><div className="receipt-actions"><button onClick={() => window.print()}>Print receipt</button><button className="tool-secondary" onClick={() => downloadReceipt(receipt)}>Download text copy</button></div></section>}
  </div></BookingFrame>;
}
