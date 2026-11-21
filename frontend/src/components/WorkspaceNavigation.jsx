import { workspaceLinks } from '../app/workspaces.js';
import AppLink from './AppLink.jsx';
export default function WorkspaceNavigation({ user }) {
  return <nav aria-label="Workspace navigation">{workspaceLinks(user).map(([path, label]) => <AppLink key={path} href={path} aria-current={window.location.pathname === path ? 'page' : undefined}>{label}</AppLink>)}</nav>;
}
