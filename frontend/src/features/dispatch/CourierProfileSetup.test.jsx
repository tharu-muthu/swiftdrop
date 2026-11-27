import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import CourierProfileSetup from './CourierProfileSetup.jsx';
function setup(request) {
  const snapshot = { status: 'authenticated', user: { roles: ['DISPATCHER_ADMIN'] } };
  const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
  render(<AuthProvider client={client}><CourierProfileSetup account={{ id: 'courier-1', fullName: 'Courier One' }} /></AuthProvider>);
}
describe('courier onboarding', () => {
  it('saves the selected account profile without copying a UUID', async () => {
    const request = vi.fn().mockResolvedValue([]); setup(request);
    const vehicle = await screen.findByLabelText('Vehicle type *');
    expect(screen.getByRole('button', { name: 'Save courier profile' })).toBeDisabled();
    fireEvent.change(vehicle, { target: { value: 'MOTORCYCLE' } });
    fireEvent.change(screen.getByLabelText('License plate'), { target: { value: 'ABC-1234' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save courier profile' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/dispatch/couriers/courier-1', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ vehicleType: 'MOTORCYCLE', licensePlate: 'ABC-1234', active: true }) })));
    expect(await screen.findByRole('status')).toHaveTextContent('Courier setup complete');
  });
  it('loads the existing profile before edits', async () => {
    setup(vi.fn().mockResolvedValue([{ courierId: 'courier-1', vehicleType: 'Van', licensePlate: 'XYZ', active: false }]));
    expect(await screen.findByLabelText('Vehicle type *')).toHaveValue('Van');
    expect(screen.getByLabelText('Available for assignments')).not.toBeChecked();
  });
  it('does not overwrite a profile when loading fails', async () => {
    setup(vi.fn().mockRejectedValue(new Error('Unable to load profile')));
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load profile');
    expect(screen.queryByRole('button', { name: 'Save courier profile' })).not.toBeInTheDocument();
  });
});
