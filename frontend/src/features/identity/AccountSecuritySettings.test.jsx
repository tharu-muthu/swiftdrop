import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import AccountSecuritySettings from './AccountSecuritySettings.jsx';

beforeEach(() => window.history.replaceState(null, '', '/account'));
function setup(passwordConfigured = true, request = vi.fn().mockResolvedValue(null), emailVerified = true) {
  const state = { status: 'authenticated', user: { id: 'one', email: 'old@example.test', passwordConfigured, emailVerified, googleConnected: true } };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), request, endLocalSession: vi.fn() };
  render(<AuthProvider client={client}><AccountSecuritySettings /></AuthProvider>);
  return client;
}
it('requests confirmation without changing the current email or ending the session', async () => {
  const client = setup();
  fireEvent.click(screen.getByText('Change email', { selector: 'summary' }));
  fireEvent.change(screen.getByLabelText('New email address'), { target: { value: 'new@example.test' } });
  fireEvent.change(screen.getByLabelText('Confirm current password'), { target: { value: 'Current password 12' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send confirmation link' }));
  expect(await screen.findByRole('status')).toHaveTextContent('current email stays unchanged');
  expect(JSON.parse(client.request.mock.calls[0][1].body)).toEqual({ email: 'new@example.test', password: 'Current password 12' });
  expect(client.endLocalSession).not.toHaveBeenCalled();
  expect(screen.getByLabelText('Confirm current password')).toHaveValue('');
});
it('checks password confirmation locally, then clears identity after a confirmed change', async () => {
  const client = setup();
  fireEvent.click(screen.getByText('Change password', { selector: 'summary' }));
  fireEvent.change(screen.getByLabelText('Current password'), { target: { value: 'Current password 12' } });
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'New password 12' } });
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'Mismatch 12' } });
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  expect(screen.getByRole('alert')).toHaveTextContent('do not match');
  expect(client.request).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'New password 12' } });
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }));
  await waitFor(() => expect(client.endLocalSession).toHaveBeenCalledTimes(1));
  expect(window.location.pathname).toBe('/login');
});
it('requires a Google-only proof and removes callback proof from the URL', async () => {
  window.history.replaceState(null, '', '/account#reauth=single-use-proof');
  const client = setup(false);
  expect(window.location.hash).toBe('');
  expect(screen.getByText(/Google verified for one security change/)).toBeVisible();
  fireEvent.click(screen.getByText('Change email', { selector: 'summary' }));
  fireEvent.change(screen.getByLabelText('New email address'), { target: { value: 'new@example.test' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send confirmation link' }));
  await screen.findByRole('status');
  expect(JSON.parse(client.request.mock.calls[0][1].body)).toEqual({ email: 'new@example.test', reauthToken: 'single-use-proof' });
  expect(screen.getByRole('button', { name: 'Send confirmation link' })).toBeDisabled();
});

it('does not show Change email for an unverified email even when a Google account is connected', () => {
  setup(true, vi.fn(), false);
  expect(screen.queryByText('Change email', { selector: 'summary' })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('New email address')).not.toBeInTheDocument();
  expect(screen.getByText('Change password', { selector: 'summary' })).toBeInTheDocument();
});
