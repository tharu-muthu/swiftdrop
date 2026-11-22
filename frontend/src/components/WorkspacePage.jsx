import PortalShell from './PortalShell.jsx';
import './workspace.css';

export default function WorkspacePage({ title, children }) {
  return <PortalShell title={title} contentClassName="workspace">{children}</PortalShell>;
}

export function JsonRequest(method, body) { return { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }; }
