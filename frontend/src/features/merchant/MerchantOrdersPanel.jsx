import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import AppLink from '../../components/AppLink.jsx';
import { deliveryStatus } from '../../app/deliveryStatus.js';

const PAGE_SIZE = 20;

function DeliveryStatus({ order }) {
  const status = deliveryStatus(order.deliveryStatus, order.assigned);
  return <span className={`delivery-status ${status.className}`}>{status.label}</span>;
}

export default function MerchantOrdersPanel() {
  const { client } = useAuth();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [orders, setOrders] = useState(null);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailPending, setDetailPending] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    client.request(`/api/v1/merchant/dashboard/orders?page=${page}&size=${PAGE_SIZE}`)
      .then(result => {
        if (!active) return;
        if (!Array.isArray(result.items)) throw new Error('Unable to read the order page. Refresh after updating the application.');
        setOrders(result);
      })
      .catch(failure => { if (active) { setOrders(null); setError(failure.message); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [client, page, revision]);

  async function select(id) {
    setDetailPending(true);
    setSelected(null);
    setError('');
    try { setSelected(await client.request(`/api/v1/merchant/dashboard/orders/${id}`)); }
    catch (failure) { setError(failure.message); }
    finally { setDetailPending(false); }
  }

  function changePage(next) {
    setSelected(null);
    setPage(next);
  }

  return <>
    <section aria-label="Merchant orders">
      <div className="actions"><h2>Orders</h2><a className="button" href="/merchant/orders/new">Create order</a>
        <button className="secondary" disabled={loading || detailPending} onClick={() => { setSelected(null); setRevision(value => value + 1); }}>Refresh orders</button></div>
      {error && <p className="error" role="alert">{error}</p>}
      {loading ? <p role="status">Loading orders…</p> : orders && <>
        <p>{orders.totalElements} order{orders.totalElements === 1 ? '' : 's'}</p>
        <div className="table-scroll"><table><thead><tr><th>Actions</th><th>Tracking number</th><th>Delivery status</th><th>Route</th><th>Fee</th><th>Booked</th></tr></thead>
          <tbody>{orders.items.map(order => <tr key={order.id}>
            <td><button className="secondary" disabled={detailPending} onClick={() => select(order.id)}>View</button></td>
            <td><strong>{order.trackingNumber}</strong></td>
            <td><DeliveryStatus order={order} /></td><td>{order.pickupCity} → {order.deliveryCity}</td>
            <td>{order.currencyCode} {(order.quotedAmountMinor / 100).toFixed(2)}</td><td>{new Date(order.createdAt).toLocaleString()}</td>
          </tr>)}</tbody></table></div>
        {!orders.items.length && <p>No orders yet.</p>}
        <div className="actions" aria-label="Order pagination">
          <button disabled={loading || detailPending || orders.page === 0} onClick={() => changePage(orders.page - 1)}>Previous orders</button>
          <span>Page {orders.page + 1} of {Math.max(1, orders.totalPages)}</span>
          <button disabled={loading || detailPending || orders.page + 1 >= orders.totalPages} onClick={() => changePage(orders.page + 1)}>Next orders</button>
        </div>
      </>}
      {detailPending && <p role="status">Loading order details…</p>}
    </section>
    {selected && <section aria-label="Merchant order details">
      <h2>{selected.trackingNumber}</h2><p>Delivery status: <DeliveryStatus order={selected} /></p>
      {selected.statusUpdatedAt && <p>Last status update: {new Date(selected.statusUpdatedAt).toLocaleString()}</p>}
      <p>Reference: {selected.externalReference || 'Not provided'}</p>
      <p>Pickup: {selected.pickupAddressSnapshot}, {selected.pickupCity} {selected.pickupPostalCode} · {selected.senderName} {selected.senderPhone}</p>
      <p>Delivery: {selected.dropoffAddressSnapshot}, {selected.deliveryCity} {selected.deliveryPostalCode} · {selected.recipientName} {selected.recipientPhone}</p>
      <p>{selected.parcelDescription} · {selected.parcelWeightGrams} g · {selected.parcelSize}</p>
      <div className="actions"><AppLink className="button" href={`/tracking/${encodeURIComponent(selected.trackingNumber)}`}>Live tracking</AppLink>
        <button className="secondary" onClick={() => setSelected(null)}>Close order details</button></div>
    </section>}
  </>;
}
