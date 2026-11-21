import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import WorkspacePage, { JsonRequest } from '../../components/WorkspacePage.jsx';

function downloadReceipt(receipt) {
  const text=['SwiftDrop delivery receipt',`Reference: ${receipt.trackingNumber}`,`Booked (UTC): ${receipt.createdAt}`,
    `Route: ${receipt.pickupCity} to ${receipt.deliveryCity}`,`Recipient: ${receipt.recipientName}`,
    `Parcel: ${receipt.parcelDescription} (${receipt.parcelWeightGrams} g)`,
    `Delivery fee: ${receipt.currencyCode} ${(receipt.quotedAmountMinor/100).toFixed(2)}`,'Delivery completed.'].join('\r\n');
  const url=URL.createObjectURL(new Blob([text],{type:'text/plain;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=`${receipt.trackingNumber}-receipt.txt`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}

export default function CustomerToolsPage({ path }) {
  const { client } = useAuth();
  const [rows, setRows] = useState([]), [receipt, setReceipt] = useState(null), [quote, setQuote] = useState(null), [error, setError] = useState(''), [pending, setPending] = useState(false);
  const [page,setPage]=useState(0),[totalPages,setTotalPages]=useState(0);
  const [form, setForm] = useState({ pickupPostalCode: '', dropoffPostalCode: '', weightGrams: '', size: 'SMALL' });
  useEffect(() => { if (path !== '/receipts') return; let live = true; client.request(`/api/v1/bookings?page=${page}&size=50`).then(data => { if (live) { setRows(data.bookings); setTotalPages(data.totalPages); } }).catch(failure => { if (live) setError(failure.message); }); return () => { live = false; }; }, [client, path,page]);
  async function loadReceipt(id) { setPending(true); setError(''); try { setReceipt(await client.request(`/api/v1/bookings/${id}/receipt`)); } catch (failure) { setError(failure.message); } finally { setPending(false); } }
  return <WorkspacePage title={path === '/receipts' ? 'Delivery receipts' : path === '/quote' ? 'Delivery quote' : 'Help'}>{error && <p className="error" role="alert">{error}</p>}
    {path === '/help' && <section><h2>Send a parcel</h2><ol><li>Enter sender, recipient, and parcel details in Book parcel.</li><li>Request a quote and confirm before it expires.</li><li>Share the tracking number with your recipient.</li><li>A dispatcher assigns a courier. Status changes appear on the tracking page.</li><li>After delivery, open Receipts to print your delivery receipt.</li></ol><h2>Merchant integrations</h2><p>Register a business in Merchant workspace. Once approved, generate an API key for your server or create orders in the dashboard. Configure webhooks to receive signed parcel status updates.</p><p>For account access, use password recovery or request a new email verification link from the sign-in page.</p></section>}
    {path === '/quote' && <section><form onSubmit={async event => { event.preventDefault(); setPending(true); setError(''); setQuote(null); try { setQuote(await client.request('/api/v1/quotes', JsonRequest('POST', { ...form, weightGrams: Number(form.weightGrams) }))); } catch (failure) { setError(failure.message); } finally { setPending(false); } }}>
      {['pickupPostalCode', 'dropoffPostalCode', 'weightGrams'].map(key => <label key={key}>{key.replace(/([A-Z])/g, ' $1')}<input required type={key === 'weightGrams' ? 'number' : 'text'} min={1} max={100000} pattern={key === 'weightGrams' ? undefined : '[0-9]{5}'} value={form[key]} onChange={event => { setQuote(null); setForm({ ...form, [key]: event.target.value }); }} /></label>)}<label>Parcel size<select value={form.size} onChange={event => { setQuote(null); setForm({ ...form, size: event.target.value }); }}>{['SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE'].map(size => <option key={size}>{size}</option>)}</select></label><button disabled={pending}>Get quote</button></form>{quote && <p className="notice" role="status">{quote.currency} {(quote.amountMinor / 100).toFixed(2)} · expires {new Date(quote.expiresAt).toLocaleString()}. <a href="/bookings/new">Book a parcel</a></p>}</section>}
    {path === '/receipts' && <><section><p>Receipts become available after a successful delivery. Select a booking to open its receipt.</p>{rows.map(booking => <p key={booking.id}><button disabled={pending} onClick={() => loadReceipt(booking.id)}>{booking.trackingNumber}</button> · {booking.recipientName}</p>)}{!rows.length && <p>No bookings found.</p>}<div className="actions"><button disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><span>Page {page+1} of {Math.max(1,totalPages)}</span><button disabled={page+1>=totalPages} onClick={()=>setPage(page+1)}>Next</button></div></section>{receipt && <section className="receipt-view" aria-label="Delivery receipt"><h2>SwiftDrop delivery receipt</h2><p>Reference: {receipt.trackingNumber}</p><p>Booked: {new Date(receipt.createdAt).toLocaleString()}</p><p>{receipt.pickupCity} → {receipt.deliveryCity}</p><p>Recipient: {receipt.recipientName}</p><p>Parcel: {receipt.parcelDescription} · {receipt.parcelWeightGrams} g</p><strong>{receipt.currencyCode} {(receipt.quotedAmountMinor / 100).toFixed(2)}</strong><p>Delivery completed. This is a delivery receipt.</p><div className="actions"><button onClick={() => window.print()}>Print receipt</button><button onClick={()=>downloadReceipt(receipt)}>Download receipt</button></div></section>}</>}
  </WorkspacePage>;
}


