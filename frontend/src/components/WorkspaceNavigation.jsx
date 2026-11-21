import { workspaceLinks } from '../app/workspaces.js';
import AppLink from './AppLink.jsx';
const icons = {
  '/workspaces': 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
  '/booking': 'm3 10 9-7 9 7 M5 9v12h14V9 M9 21v-8h6v8',
  '/bookings/new': 'M12 3v18 M3 12h18',
  '/bookings': 'm3 7 9-4 9 4-9 4z M3 7v10l9 4 9-4V7 M12 11v10',
  '/tracking': 'M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0z M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  '/merchant': 'M3 9h18l-2-6H5z M5 9v12h14V9 M9 21v-7h6v7',
  '/courier': 'M3 5h11v12H3z M14 9h4l3 4v4h-7 M8 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M20 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
  '/dispatch': 'M4 4h16v5H4z M4 15h6v5H4z M14 15h6v5h-6z M12 9v3 M7 15v-3h10v3',
  '/account': 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2',
};
const defaultIcon = 'M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h5';

export default function WorkspaceNavigation({ user, activePath = window.location.pathname, collapsed = false }) {
  const current = activePath === '/profile' ? '/account' : activePath;
  function selected(path) {
    if (current === path) return true;
    return path === '/bookings' && current.startsWith('/bookings/') && current !== '/bookings/new';
  }
  return <nav aria-label="Workspace navigation">{workspaceLinks(user).map(([path, label]) =>
    <AppLink key={path} href={path} aria-current={selected(path) ? 'page' : undefined}
      aria-label={collapsed ? label : undefined} title={collapsed ? label : undefined}>
      <svg className="portal-nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={icons[path] || defaultIcon} /></svg>
      <span className="portal-nav-label">{label}</span>
    </AppLink>)}</nav>;
}
