import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import AuthCompletePage from './AuthCompletePage.jsx';
beforeEach(() => window.history.replaceState(null, '', '/auth/complete'));
function setup(status) {
  const state = { status };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn() };
  render(<AuthProvider client={client}><AuthCompletePage /></AuthProvider>);
  return client;
}
it('waits for the existing bootstrap instead of navigating during refresh', () => {
  const client = setup('loading');
  expect(screen.getByRole('status')).toHaveTextContent('Loading SwiftDrop');
  expect(window.location.pathname).toBe('/auth/complete');
  expect(client.bootstrap).toHaveBeenCalledTimes(1);
});
it('opens workspace selection once the callback session is restored', async () => {
  const historyLength = window.history.length;
  setup('authenticated');
  await waitFor(() => expect(window.location.pathname).toBe('/workspaces'));
  expect(window.history.length).toBe(historyLength);
});
it('returns to sign-in when the callback session cannot be restored', async () => {
  setup('anonymous');
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
});
