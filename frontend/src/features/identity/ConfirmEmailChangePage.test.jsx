import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import ConfirmEmailChangePage from './ConfirmEmailChangePage.jsx';

it('removes the token fragment and requires explicit CSRF-protected confirmation before clearing identity', async () => {
  window.history.replaceState(null, '', '/change-email#token=private-confirmation-proof');
  const fetch = vi.fn(async path => path.endsWith('/csrf')
    ? { ok: true, status: 200, json: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }) }
    : { ok: true, status: 204 });
  vi.stubGlobal('fetch', fetch);
  const state = { status: 'anonymous', user: null }, endLocalSession = vi.fn();
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), endLocalSession }}><ConfirmEmailChangePage /></AuthProvider>);
  expect(window.location.hash).toBe('');
  expect(fetch).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirm email change' }));
  expect(await screen.findByRole('status')).toHaveTextContent('Email changed');
  expect(endLocalSession).toHaveBeenCalledTimes(1);
  const [, options] = fetch.mock.calls.find(([path]) => path.endsWith('/email-change/confirm'));
  expect(JSON.parse(options.body)).toEqual({ token: 'private-confirmation-proof' });
  expect(options.headers['X-XSRF-TOKEN']).toBe('csrf');
});
