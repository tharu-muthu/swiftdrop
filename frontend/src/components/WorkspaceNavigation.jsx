import { workspaceLinks } from '../app/workspaces.js';
export default function WorkspaceNavigation({ user }) {
  return <nav aria-label="Workspace navigation">{workspaceLinks(user).map(([path, label]) => <a key={path} href={path} aria-current={window.location.pathname === path ? 'page' : undefined}>{label}</a>)}</nav>;
}
