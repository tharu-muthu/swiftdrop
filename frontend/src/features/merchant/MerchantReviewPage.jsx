import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import WorkspacePage, { JsonRequest } from '../../components/WorkspacePage.jsx';

export default function MerchantReviewPage() {
  const { client } = useAuth();
  const [result, setResult] = useState(null), [page, setPage] = useState(0), [status, setStatus] = useState('PENDING'), [reason, setReason] = useState(''), [error, setError] = useState(''), [pending, setPending] = useState(false);
  const [notice, setNotice] = useState('');
  async function load() { try { setError(''); setResult(await client.request(`/api/v1/admin/merchants?page=${page}&size=20${status ? `&status=${status}` : ''}`)); } catch (failure) { setError(failure.message); } }
  useEffect(() => { load(); }, [client, page, status]);
  async function decide(id, action) { if (pending) return; setPending(true); setError(''); setNotice(''); try { await client.request(`/api/v1/admin/merchants/${id}/${action}`, JsonRequest('POST', action === 'reject' ? { reason } : undefined)); setReason(''); setNotice(action === 'approve' ? 'Merchant approved and access granted. The owner must sign in again to use the merchant workspace.' : 'Application rejected. No merchant role was granted.'); await load(); } catch (failure) { setError(failure.message); } finally { setPending(false); } }
  return <WorkspacePage title="Merchant approvals">{notice && <p className="notice" role="status">{notice}</p>}<p>Approval grants merchant access and ends the owner's existing sessions. Merchant roles are managed here, separately from staff roles.</p>{error && <p className="error" role="alert">{error}</p>}<section><label>Filter status<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}><option value="">All</option>{['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'].map(value => <option key={value}>{value}</option>)}</select></label><label>Reason for rejection<textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={500} /></label></section>
    {result && !result.items.length && <p>No applications match this filter.</p>}
    {result?.items?.map(merchant => <article key={merchant.id}><h2>{merchant.businessName}</h2><p>{merchant.contactEmail} · {merchant.contactPhone}</p><p>{merchant.businessAddress}</p><p>{merchant.approvalStatus} {merchant.reviewReason}</p>{merchant.approvalStatus === 'PENDING' && <div className="actions"><button disabled={pending} onClick={() => decide(merchant.id, 'approve')}>Approve</button><button className="secondary" disabled={pending || !reason.trim()} onClick={() => decide(merchant.id, 'reject')}>Reject</button></div>}</article>)}
    {result && <div className="actions"><button disabled={page === 0 || pending} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(1, result.totalPages)}</span><button disabled={page + 1 >= result.totalPages || pending} onClick={() => setPage(page + 1)}>Next</button></div>}
  </WorkspacePage>;
}
