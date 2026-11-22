import { useEffect, useSyncExternalStore } from 'react';
import { availableWorkspaces } from './workspaces.js';

function subscribe(listener) {
  window.addEventListener('popstate', listener);
  return () => window.removeEventListener('popstate', listener);
}

export function useActivePortal(user, pathOverride) {
  const path = useSyncExternalStore(subscribe, () => window.location.pathname);
  const portals = availableWorkspaces(user);
  const current = pathOverride || path;
  const routeRole = current.startsWith('/admin/') || current === '/dispatch' ? 'DISPATCHER_ADMIN'
    : current.startsWith('/courier') ? 'COURIER'
    : current.startsWith('/merchant') ? (user.roles.includes('MERCHANT') ? 'MERCHANT' : 'CUSTOMER')
    : /^\/(booking|bookings|quote|receipts|help)(\/|$)/.test(current) ? 'CUSTOMER' : null;
  const key = `swiftdrop.portal.${user.userId || user.id || user.email || 'current'}`;
  let saved;
  try { saved = sessionStorage.getItem(key); } catch { /* Navigation also works without storage. */ }
  const role = portals.find(item => item.role === routeRole)?.role
    || portals.find(item => item.role === saved)?.role || portals[0]?.role;
  useEffect(() => {
    if (role) { try { sessionStorage.setItem(key, role); } catch { /* Optional preference. */ } }
  }, [key, role]);
  return role;
}
