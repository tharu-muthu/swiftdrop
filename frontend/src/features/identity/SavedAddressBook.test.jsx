import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import SavedAddressBook from './SavedAddressBook.jsx';

const address = { id: 'address-1', label: 'Home', contactName: 'Alex', contactPhone: '0771234567', addressLine1: '1 Road', addressLine2: '', city: 'Colombo', postalCode: '00600', defaultPickup: true, version: 2 };
it('edits only address fields with the record version and deletes with a version check', async () => {
  const request = vi.fn(async (_path, options) => options ? null : [address]);
  const state = { status: 'authenticated', user: {} };
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), request }}><SavedAddressBook /></AuthProvider>);
  fireEvent.click(await screen.findByRole('button', { name: 'Edit Home' }));
  fireEvent.change(screen.getByLabelText('Address label *'), { target: { value: 'My home' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save address' }));
  await waitFor(() => expect(request.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(true));
  const [path, options] = request.mock.calls.find(([, options]) => options?.method === 'PUT');
  expect(path).toBe('/api/v1/me/addresses/address-1');
  expect(JSON.parse(options.body)).toEqual({ ...Object.fromEntries(Object.entries(address).filter(([key]) => key !== 'id')), label: 'My home' });
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Save address' })).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: 'Delete Home' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/me/addresses/address-1?version=2', { method: 'DELETE' }));
});
