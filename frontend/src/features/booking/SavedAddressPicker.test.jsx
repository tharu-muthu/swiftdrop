import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import SavedAddressPicker from './SavedAddressPicker.jsx';

it('loads addresses on demand and returns a selected record without changing saved data', async () => {
  const address = { id: 'a1', label: 'Home', defaultPickup: true };
  const request = vi.fn().mockResolvedValue([address]), pick = vi.fn();
  const state = { status: 'authenticated', user: {} };
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), request }}><SavedAddressPicker kind="pickup" onSelect={pick} /></AuthProvider>);
  expect(request).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Use a saved address' }));
  fireEvent.change(await screen.findByLabelText('Saved pickup address'), { target: { value: 'a1' } });
  expect(pick).toHaveBeenCalledWith(address);
  expect(request).toHaveBeenCalledTimes(1);
});
