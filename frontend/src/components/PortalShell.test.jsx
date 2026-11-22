import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from '../features/identity/AuthProvider.jsx';
import PortalShell from './PortalShell.jsx';
import { setSidebarVisible } from './sidebarPreference.js';

beforeEach(() => { setSidebarVisible(true); window.history.replaceState(null, '', '/booking'); });
function setup(roles, activePath) {
  const state = { status: 'authenticated', user: { fullName: 'Alex Perera', roles }, error: '' };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn() };
  return render(<AuthProvider client={client}><PortalShell title="Delivery portal" activePath={activePath}>
    <p>Portal content</p>
  </PortalShell></AuthProvider>);
}

it('keeps customer application access distinct from merchant tools in the shared navigation', () => {
  setup(['CUSTOMER']);
  const nav = within(screen.getByRole('navigation', { name: 'Workspace navigation' }));
  expect(nav.getByRole('link', { name: 'Apply for merchant' })).toHaveAttribute('href', '/merchant');
  expect(nav.queryByRole('link', { name: 'Merchant workspace' })).not.toBeInTheDocument();
  expect(nav.queryByRole('link', { name: 'Webhooks' })).not.toBeInTheDocument();
  expect(nav.queryByRole('link', { name: 'Switch portal' })).not.toBeInTheDocument();
  expect(screen.getAllByRole('main')).toHaveLength(1);
  expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#portal-content');
});

it('keeps an accessible icon rail and remembers collapse across portal mounts', () => {
  const page = setup(['CUSTOMER']);
  const toggle = screen.getByRole('button', { name: 'Collapse sidebar' });
  expect(toggle).toHaveAttribute('aria-expanded', 'true');
  expect(toggle).toHaveAttribute('aria-controls', 'portal-navigation');
  fireEvent.click(toggle);
  expect(screen.getByRole('navigation', { name: 'Workspace navigation' })).toBeVisible();
  expect(screen.getByRole('link', { name: 'Customer dashboard' })).toHaveAttribute('title', 'Customer dashboard');
  expect(screen.getByRole('button', { name: 'Expand sidebar' })).toHaveAttribute('aria-expanded', 'false');
  const logo = screen.getByRole('link', { name: 'SwiftDrop home' });
  expect(logo.querySelector('svg')).toBeInTheDocument();
  expect(logo).not.toHaveTextContent('SwiftDrop');
  page.unmount();
  setup(['CUSTOMER']);
  expect(screen.getByRole('navigation', { name: 'Workspace navigation' })).toBeVisible();
  expect(screen.getByRole('link', { name: 'Customer dashboard' })).toHaveAttribute('title', 'Customer dashboard');
  fireEvent.click(screen.getByRole('button', { name: 'Expand sidebar' }));
  expect(screen.getByRole('navigation', { name: 'Workspace navigation' })).toBeVisible();
  expect(screen.getByRole('link', { name: 'SwiftDrop home' })).toHaveTextContent('SwiftDrop');
});

it('shows only active portal links and highlights the current nested booking route', () => {
  setup(['CUSTOMER', 'COURIER'], '/bookings/parcel-1');
  const nav = within(screen.getByRole('navigation', { name: 'Workspace navigation' }));
  expect(nav.getByRole('link', { name: 'My bookings' })).toHaveAttribute('aria-current', 'page');
  expect(nav.queryByRole('link', { name: 'Courier deliveries' })).not.toBeInTheDocument();
  expect(nav.queryByRole('link', { name: 'User management' })).not.toBeInTheDocument();
  expect(nav.queryByRole('link', { name: 'Switch portal' })).not.toBeInTheDocument();
});

it('keeps merchant links and account navigation available without customer privileges', () => {
  setup(['MERCHANT'], '/merchant/webhooks');
  const nav = within(screen.getByRole('navigation', { name: 'Workspace navigation' }));
  expect(nav.getByRole('link', { name: 'Webhooks' })).toHaveAttribute('aria-current', 'page');
  expect(nav.queryByRole('link', { name: 'Apply for merchant' })).not.toBeInTheDocument();
  expect(nav.queryByRole('link', { name: 'Book parcel' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
  fireEvent.click(screen.getByRole('menuitem', { name: 'My account' }));
  expect(window.location.pathname).toBe('/account');
});

for (const [path, visible, hidden] of [
  ['/courier', 'Courier deliveries', 'Book parcel'],
  ['/dispatch', 'User management', 'Merchant workspace'],
  ['/merchant', 'Webhooks', 'Courier deliveries'],
]) {
  it(`isolates navigation for ${path} and retains it on account pages`, () => {
    window.history.replaceState(null, '', path);
    setup(['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN']);
    const nav = within(screen.getByRole('navigation', { name: 'Workspace navigation' }));
    expect(nav.getByRole('link', { name: visible })).toBeVisible();
    expect(nav.queryByRole('link', { name: hidden })).not.toBeInTheDocument();
    fireEvent.click(nav.getByRole('link', { name: 'My account' }));
    expect(nav.getByRole('link', { name: visible })).toBeVisible();
    expect(nav.queryByRole('link', { name: hidden })).not.toBeInTheDocument();
  });
}
