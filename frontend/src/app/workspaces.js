export const workspaces = [
  { role: 'CUSTOMER', title: 'Customer', path: '/booking', description: 'Book parcels, follow deliveries and view receipts.' },
  { role: 'MERCHANT', title: 'Merchant', path: '/merchant', description: 'Manage business orders, API credentials and webhooks.' },
  { role: 'COURIER', title: 'Courier', path: '/courier', description: 'View assigned deliveries and record delivery updates.' },
  { role: 'DISPATCHER_ADMIN', title: 'Administration & dispatch', path: '/dispatch', description: 'Manage people, approvals, courier profiles and assignments.' },
];
export const availableWorkspaces = user => workspaces.filter(item => user.roles.includes(item.role));
export function requiredRole(path) {
  if (path === '/merchant') return ['CUSTOMER', 'MERCHANT'];
  if (path === '/courier') return 'COURIER';
  if (path === '/dispatch' || path.startsWith('/admin/')) return 'DISPATCHER_ADMIN';
  if (path === '/merchant/orders/new' || path === '/merchant/webhooks') return 'MERCHANT';
  if (['/booking', '/bookings', '/bookings/new', '/quote', '/receipts', '/help'].includes(path) || path.startsWith('/bookings/')) return 'CUSTOMER';
  return null;
}
export function canAccess(user, path) {
  const role = requiredRole(path);
  return !role || (Array.isArray(role) ? role.some(item => user.roles.includes(item)) : user.roles.includes(role));
}
export const workspaceLinks = (user, role = availableWorkspaces(user)[0]?.role) => [
  ...(role === 'CUSTOMER' ? [['/booking', 'Customer dashboard'], ['/bookings/new', 'Book parcel'], ['/bookings', 'My bookings'], ['/tracking', 'Track parcel'], ['/receipts', 'Receipts'], ['/help', 'Help'], ...(!user.roles.includes('MERCHANT') ? [['/merchant', 'Apply for merchant']] : [])] : []),
  ...(role === 'MERCHANT' ? [['/merchant', 'Merchant workspace'], ['/merchant/orders/new', 'Create merchant order'], ['/merchant/webhooks', 'Webhooks']] : []),
  ...(role === 'COURIER' ? [['/courier', 'Courier deliveries']] : []),
  ...(role === 'DISPATCHER_ADMIN' ? [['/dispatch', 'Dispatch'], ['/admin/users', 'User management'], ['/admin/merchants', 'Merchant approvals'], ['/admin/pricing', 'Pricing'], ['/admin/notifications', 'Notifications']] : []),
  ['/account', 'My account'],
];
