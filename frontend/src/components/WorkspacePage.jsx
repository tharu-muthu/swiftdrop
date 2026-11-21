import { navigate } from '../app/navigation.js';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import './workspace.css';

export default function WorkspacePage({ title, children }) {
  const { user } = useAuth();
  const admin = user.roles.includes('DISPATCHER_ADMIN');
  return <div className="workspace"><aside><a className="workspace-brand" href="/account">SwiftDrop</a><nav aria-label="Workspace navigation">
    <a href="/booking">Customer dashboard</a><a href="/bookings/new">Book parcel</a><a href="/bookings">My bookings</a><a href="/tracking">Track parcel</a>
    <a href="/merchant">Merchant workspace</a>{user.roles.includes('COURIER') && <a href="/courier">Courier deliveries</a>}
    {admin && <><a href="/dispatch">Dispatch</a><a href="/admin/merchants">Merchant approvals</a><a href="/admin/pricing">Pricing</a><a href="/admin/notifications">Notifications</a><a href="/admin/users">Users</a></>}
    <a href="/account">My account</a></nav></aside><main><header><h1>{title}</h1><button onClick={() => navigate('/account')}>{user.fullName}</button></header>{children}</main></div>;
}

export function JsonRequest(method, body) { return { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }; }
