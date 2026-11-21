import { navigate } from '../app/navigation.js';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import './workspace.css';
import WorkspaceNavigation from './WorkspaceNavigation.jsx';
import AppLink from './AppLink.jsx';

export default function WorkspacePage({ title, children }) {
  const { user } = useAuth();
  return <div className="workspace"><aside><AppLink className="workspace-brand" href="/workspaces">SwiftDrop</AppLink><WorkspaceNavigation user={user} /></aside><main><header><h1>{title}</h1><button onClick={() => navigate('/account')}>{user.fullName}</button></header>{children}</main></div>;
}

export function JsonRequest(method, body) { return { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }; }
