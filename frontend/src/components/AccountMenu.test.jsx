import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from '../features/identity/AuthProvider.jsx';
import AccountMenu from './AccountMenu.jsx';

beforeEach(() => window.history.replaceState(null, '', '/booking'));
function setup(logout = vi.fn().mockResolvedValue(undefined)) {
  const state = { status: 'authenticated', user: { fullName: 'Alex Perera', email: 'alex@example.test', roles: ['CUSTOMER', 'MERCHANT'] } };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), logout };
  render(<AuthProvider client={client}><AccountMenu /><button>Outside control</button></AuthProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
  return logout;
}

it('opens account details and the real assigned roles, then navigates to My account', () => {
  setup();
  expect(screen.getByRole('button', { name: 'Account menu' })).toHaveAttribute('aria-expanded', 'true');
  expect(screen.getAllByText('alex@example.test')).toHaveLength(2);
  expect(screen.getByText('Customer')).toBeVisible();
  expect(screen.getByText('Merchant')).toBeVisible();
  fireEvent.click(screen.getByRole('menuitem', { name: 'My account' }));
  expect(window.location.pathname).toBe('/account');
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
});

it('supports keyboard navigation and returns focus to the trigger on Escape', () => {
  setup();
  expect(screen.getByRole('menuitem', { name: 'My account' })).toHaveFocus();
  fireEvent.keyDown(document.activeElement, { key: 'ArrowDown' });
  expect(screen.getByRole('menuitem', { name: 'Sign out' })).toHaveFocus();
  fireEvent.keyDown(document.activeElement, { key: 'Escape' });
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Account menu' })).toHaveFocus();
});

it('dismisses on outside interaction', () => {
  setup();
  fireEvent.pointerDown(screen.getByRole('button', { name: 'Outside control' }));
  expect(screen.queryByRole('menu')).not.toBeInTheDocument();
});

it('uses the existing logout operation before navigating to sign-in', async () => {
  let complete;
  const logout = setup(vi.fn(() => new Promise(resolve => { complete = resolve; })));
  fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));
  expect(logout).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('menuitem', { name: 'Signing out…' })).toBeDisabled();
  expect(window.location.pathname).toBe('/booking');
  complete();
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
});

it('shows a retry error when sign-out cannot be confirmed', async () => {
  setup(vi.fn().mockRejectedValue(new Error('Network failure')));
  fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Sign-out could not be confirmed');
  expect(window.location.pathname).toBe('/booking');
  expect(screen.getByRole('menuitem', { name: 'Sign out' })).toBeEnabled();
});
