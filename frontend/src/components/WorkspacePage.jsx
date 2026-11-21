import { navigate } from '../app/navigation.js';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import './workspace.css';
import WorkspaceNavigation from './WorkspaceNavigation.jsx';

export default function WorkspacePage({ title, children }) {
  const { user } = useAuth();
  return <div className="workspace"><aside><a className="workspace-brand" href="/workspaces">SwiftDrop</a><WorkspaceNavigation user={user} /></aside><main><header><h1>{title}</h1><button onClick={() => navigate('/account')}>{user.fullName}</button></header>{children}</main></div>;
}

export function JsonRequest(method, body) { return { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }; }
