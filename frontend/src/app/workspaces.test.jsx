import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { canAccess, availableWorkspaces, workspaceLinks } from './workspaces.js';
import App from './App.jsx';
import BookingFrame from '../features/booking/BookingFrame.jsx';
const state = vi.hoisted(() => ({ status: 'authenticated', user: { id: 'one', fullName: 'Test Person', roles: ['CUSTOMER'] }, client: {} }));
vi.mock('../features/identity/AuthProvider.jsx', () => ({ useAuth: () => state }));
beforeEach(() => { state.status = 'authenticated'; state.user.roles = ['CUSTOMER']; window.history.replaceState(null, '', '/workspaces'); });
describe('role workspaces', () => {
  it('keeps private content and login hidden while restoring the session', () => {
    state.status = 'loading';
    render(<App />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading SwiftDrop');
    expect(screen.getByRole('main')).toHaveAttribute('aria-busy', 'true');
    expect(screen.queryByText(/Checking your session/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Open customer' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
  });
  it.each(['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN'])('only offers assigned %s workspace', role => {
    expect(availableWorkspaces({ roles: [role] }).map(item => item.role)).toEqual([role]);
  });
  it('supports a person with several roles without elevating their access', () => {
    const user = { roles: ['CUSTOMER', 'COURIER'] };
    expect(availableWorkspaces(user)).toHaveLength(2);
    expect(canAccess(user, '/courier')).toBe(true);
    expect(canAccess(user, '/admin/users')).toBe(false);
    expect(canAccess(user, '/merchant/webhooks')).toBe(false);
    expect(canAccess(user, '/merchant')).toBe(true);
    expect(workspaceLinks({ roles: ['COURIER'] }).map(([path]) => path)).not.toContain('/bookings/new');
  });
  it.each(['/courier', '/dispatch', '/admin/users', '/merchant/webhooks', '/merchant/orders/new'])('blocks an unassigned workspace at %s', path => {
    window.history.replaceState(null, '', path); render(<App />);
    expect(screen.getByRole('alert')).toHaveTextContent('does not have the role');
    expect(screen.getByRole('link', { name: 'View your workspaces' })).toHaveAttribute('href', '/workspaces');
  });
  it('renders assigned workspaces and an application path, not privileged self signup', () => {
    render(<App />);
    expect(screen.getByRole('link', { name: 'Open customer' })).toHaveAttribute('href', '/booking');
    expect(screen.queryByRole('link', { name: 'Open courier' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Merchant application' })).toHaveAttribute('href', '/merchant');
  });
  it('offers multiple assigned workspaces', () => {
    state.user.roles = ['COURIER', 'DISPATCHER_ADMIN']; render(<App />);
    expect(screen.getByRole('link', { name: 'Open courier' })).toHaveAttribute('href', '/courier');
    expect(screen.getByRole('link', { name: 'Open administration & dispatch' })).toHaveAttribute('href', '/dispatch');
    expect(screen.queryByRole('link', { name: 'Open customer' })).not.toBeInTheDocument();
  });
  it('shows an explicit missing page', () => {
    window.history.replaceState(null, '', '/missing'); render(<App />);
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  });
  it('keeps customer tools out of the merchant-only booking layout', () => {
    state.user.roles = ['MERCHANT']; render(<BookingFrame><p>Merchant order</p></BookingFrame>);
    expect(screen.queryByRole('button', { name: /Book Parcel/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /My Bookings/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Switch workspace/ })).toBeVisible();
  });
  it('does not offer merchant applications to staff without customer access', () => {
    state.user.roles = ['COURIER']; render(<App />);
    expect(screen.queryByRole('link', { name: 'Merchant application' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Apply for merchant access' })).not.toBeInTheDocument();
    expect(canAccess(state.user, '/merchant')).toBe(false);
  });
  it('blocks a courier-only account from customer and business screens', () => {
    state.user.roles = ['COURIER'];
    for (const path of ['/bookings', '/bookings/new', '/bookings/other', '/quote', '/merchant', '/merchant/orders/new']) expect(canAccess(state.user, path)).toBe(false);
  });
});
