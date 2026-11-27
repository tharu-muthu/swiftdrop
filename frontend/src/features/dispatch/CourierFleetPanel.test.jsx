import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import CourierFleetPanel from './CourierFleetPanel.jsx';

const fleet = [
  { courierId: 'alex-id', fullName: 'Alex Silva', email: 'alex@example.test', vehicleType: 'VAN', licensePlate: 'ABC-1234', active: true, eligible: true, accountEligible: true, workload: 3 },
  { courierId: 'sam-id', fullName: 'Sam Perera', email: 'sam@example.test', vehicleType: 'MOTORCYCLE', active: false, eligible: false, accountEligible: true, workload: 0 },
  { courierId: 'blocked-id', fullName: 'Robin Fernando', email: 'robin@example.test', vehicleType: 'CAR', active: false, eligible: false, accountEligible: false, workload: 0 },
];
function setup(request = vi.fn().mockResolvedValue(), couriers = fleet) {
  window.history.replaceState(null, '', '/dispatch');
  const state = { status: 'authenticated', user: { roles: ['DISPATCHER_ADMIN'] } };
  const onSaved = vi.fn();
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: () => {}, request }}>
    <CourierFleetPanel couriers={couriers} loading={false} onSaved={onSaved} />
  </AuthProvider>);
  return { request, onSaved };
}
it('finds existing couriers by name or plate and filters availability without asking for IDs', () => {
  setup();
  expect(screen.queryByLabelText(/user ID/i)).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: '+ Add courier' })).toHaveAttribute('href', '/admin/users?role=COURIER&action=assign');
  fireEvent.change(screen.getByLabelText('Find a courier'), { target: { value: 'abc-1234' } });
  expect(screen.getByText('Alex Silva')).toBeInTheDocument();
  expect(screen.queryByText('Sam Perera')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Find a courier'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Availability'), { target: { value: 'Unavailable' } });
  expect(screen.getByText('Sam Perera')).toBeInTheDocument();
  expect(screen.queryByText('Robin Fernando')).not.toBeInTheDocument();
});
it('prefills the selected courier and saves operational fields to that account only', async () => {
  const { request, onSaved } = setup();
  fireEvent.click(screen.getByRole('button', { name: 'Edit Alex Silva' }));
  expect(screen.getByLabelText('Vehicle type')).toHaveValue('VAN');
  expect(screen.getByLabelText(/License plate/)).toHaveValue('ABC-1234');
  fireEvent.change(screen.getByLabelText('Vehicle type'), { target: { value: 'CAR' } });
  fireEvent.click(screen.getByLabelText('Available for new assignments'));
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  await screen.findByText('Courier details saved for Alex Silva.');
  expect(request).toHaveBeenCalledWith('/api/v1/dispatch/couriers/alex-id', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ vehicleType: 'CAR', licensePlate: 'ABC-1234', active: false }),
  }));
  expect(onSaved).toHaveBeenCalledOnce();
  expect(screen.queryByRole('heading', { name: 'Edit courier details' })).not.toBeInTheDocument();
});
it('keeps edits on failure and directs unavailable accounts to access management', async () => {
  const { onSaved } = setup(vi.fn().mockRejectedValue(new Error('Account access changed')));
  expect(screen.queryByRole('button', { name: 'Edit Robin Fernando' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Review access' })).toHaveAttribute('href', '/admin/users');
  fireEvent.click(screen.getByRole('button', { name: 'Edit Sam Perera' }));
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Account access changed');
  expect(screen.getByLabelText('Vehicle type')).toHaveValue('MOTORCYCLE');
  expect(onSaved).not.toHaveBeenCalled();
  await waitFor(() => expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  expect(screen.queryByLabelText('Vehicle type')).not.toBeInTheDocument();
});
it('explains how to onboard when no profiles exist', () => {
  setup(undefined, []);
  expect(screen.getByText('No courier profiles yet')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
});
