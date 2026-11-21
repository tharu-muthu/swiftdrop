import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import { setSidebarVisible } from '../../components/sidebarPreference.js';
import PublicTrackingPage from './PublicTrackingPage.jsx';

beforeEach(() => { setSidebarVisible(true); window.history.replaceState(null, '', '/tracking'); });

it('keeps public tracking usable without an authenticated provider', () => {
  render(<PublicTrackingPage />);
  expect(screen.getByRole('heading', { name: 'Track a parcel', level: 1 })).toBeVisible();
  expect(screen.getByRole('button', { name: 'SwiftDrop tracking home' })).toBeVisible();
  expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Track parcel' }));
  expect(screen.getByRole('alert')).toHaveTextContent('Enter a tracking number.');
});

it('uses the shared shell, active tracking link and sidebar toggle for signed-in tracking', () => {
  const state = { status: 'authenticated', user: { fullName: 'Alex Perera', roles: ['CUSTOMER'] } };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn() };
  render(<AuthProvider client={client}><PublicTrackingPage inPortal /></AuthProvider>);
  expect(screen.getAllByRole('main')).toHaveLength(1);
  const nav = within(screen.getByRole('navigation', { name: 'Workspace navigation' }));
  expect(nav.getByRole('link', { name: 'Track parcel' })).toHaveAttribute('aria-current', 'page');
  expect(screen.queryByRole('button', { name: 'SwiftDrop tracking home' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Collapse sidebar' }));
  expect(screen.getByRole('navigation', { name: 'Workspace navigation' })).toBeVisible();
  expect(screen.getByRole('button', { name: 'Expand sidebar' })).toHaveAttribute('aria-expanded', 'false');
  fireEvent.change(screen.getByLabelText('Tracking number'), { target: { value: 'sdp-example' } });
  fireEvent.click(screen.getByRole('button', { name: 'Track parcel' }));
  expect(window.location.pathname).toBe('/tracking/SDP-EXAMPLE');
});
