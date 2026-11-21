import { useState } from 'react';
import { useAuth } from './AuthProvider.jsx';
import { availableWorkspaces } from '../../app/workspaces.js';
import WorkspacePage from '../../components/WorkspacePage.jsx';
import { navigate } from '../../app/navigation.js';
export default function WorkspacesPage() {
  const { user, client } = useAuth();
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  async function reload() {
    setPending(true); setError('');
    try { await client.reloadProfile(); }
    catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  async function signOut() {
    setPending(true); setError('');
    try { await client.logout(); navigate('/login'); }
    catch (failure) { setError(failure.message); setPending(false); }
  }
  return <WorkspacePage title="Your workspaces">
    <p>Welcome, {user.fullName}. Choose what you want to do with your assigned access.</p>
    {error && <p className="error" role="alert">{error}</p>}
    <div className="workspace-cards">{availableWorkspaces(user).map(item => <section key={item.role}>
      <small>Assigned role</small><h2>{item.title}</h2><p>{item.description}</p>
      <a className="button" href={item.path}>Open {item.title.toLowerCase()}</a>
      {item.role === 'COURIER' && <p><small>Assignments require an active courier profile. Contact your dispatcher if setup is incomplete.</small></p>}
    </section>)}</div>
    {user.roles.includes('CUSTOMER') && !user.roles.includes('MERCHANT') && <section><h2>Run a business?</h2><p>Apply for merchant access or check your application's review status. Business tools become available after approval.</p><a className="button" href="/merchant">Merchant application</a></section>}
    <section><h2>Need another role?</h2><p>Courier and administrator access is assigned by an administrator after your email is verified. After access changes, sign in again to load your new roles.</p><div className="actions"><button disabled={pending} onClick={reload}>Refresh account</button><button className="secondary" disabled={pending} onClick={signOut}>Sign out</button></div></section>
  </WorkspacePage>;
}
