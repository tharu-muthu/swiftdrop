import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { it, expect, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import DispatchDashboardPage from './DispatchDashboardPage.jsx';

function renderPage(request) {
  const state = { status: 'authenticated', user: { fullName: 'Admin', roles: ['DISPATCHER_ADMIN'] } };
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: () => {}, request }}><DispatchDashboardPage /></AuthProvider>);
}
const parcel = { bookingId: 'parcel-1', trackingNumber: 'SDP-PARCEL1', status: 'CREATED', courierId: null, updatedAt: '2026-11-19T10:00:00Z', overdue: true };
const board = { items: [parcel], totalPages: 2, totalElements: 51, delayHours: 24 };
const good = { courierId: 'good', vehicleType: 'VAN', licensePlate: 'GOOD-1', active: true, eligible: true, workload: 0 };
const blocked = { courierId: 'blocked', vehicleType: 'BIKE', licensePlate: 'BLOCKED-1', active: true, eligible: false, unavailableReason: 'ACCOUNT_UNAVAILABLE', workload: 0 };

it('loads the paginated dispatch board and an empty fleet without requiring manual booking IDs', async () => {
  const request = vi.fn(async path => path.startsWith('/api/v1/dispatch/bookings') ? { items: [], totalPages: 0, totalElements: 0 } : []);
  renderPage(request);
  expect(await screen.findByText('No deliveries found.')).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith('/api/v1/dispatch/bookings?page=0&size=50&unassigned=false&overdue=false');
  expect(screen.queryByLabelText('Pending Booking ID')).not.toBeInTheDocument();
});

it('resets pagination when filtering overdue parcels and combines the unassigned filter', async () => {
  const request = vi.fn(async path => path.includes('/bookings?') ? board : []);
  renderPage(request);
  await screen.findByText('Overdue');
  fireEvent.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('Page 2 of 2');
  fireEvent.click(screen.getByLabelText('Overdue only'));
  await screen.findByText('51 matching parcels · oldest updates first');
  expect(request).toHaveBeenCalledWith('/api/v1/dispatch/bookings?page=0&size=50&unassigned=false&overdue=true');
  fireEvent.click(screen.getByLabelText('Unassigned only'));
  await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/dispatch/bookings?page=0&size=50&unassigned=true&overdue=true'));
  expect(screen.getByText(/Delivered and failed parcels are excluded/)).toBeInTheDocument();
});

it('opens the current overdue parcel list from the daily report', async () => {
  const request = vi.fn(async path => path.includes('/reports/daily') ? { date: '2026-11-21', delayHours: 24, overdueOpenDeliveries: 2, results: [] } : path.includes('/bookings?') ? board : []);
  renderPage(request);
  await screen.findByText('Overdue');
  fireEvent.click(screen.getByLabelText('Unassigned only'));
  fireEvent.click(screen.getByRole('button', { name: 'Load report' }));
  fireEvent.click(await screen.findByRole('button', { name: 'View overdue parcels' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/dispatch/bookings?page=0&size=50&unassigned=false&overdue=true'));
  expect(screen.getByLabelText('Overdue only')).toBeChecked();
  expect(screen.getByLabelText('Unassigned only')).not.toBeChecked();
});

it('offers only eligible couriers and clears a stale choice after assignment is rejected', async () => {
  let revoked = false;
  const request = vi.fn(async path => {
    if (path.includes('/bookings?')) return board;
    if (path.endsWith('/bookings/parcel-1')) return { booking: {}, assignments: [] };
    if (path.endsWith('/assignments')) { revoked = true; throw new Error('Courier account is unavailable'); }
    if (path.endsWith('/couriers')) return [{ ...good, eligible: !revoked }, blocked];
    throw new Error(`Unexpected path ${path}`);
  });
  renderPage(request);
  fireEvent.click(await screen.findByRole('button', { name: 'Select' }));
  const select = await screen.findByLabelText('Courier');
  expect(within(select).getByRole('option', { name: /GOOD-1/ })).toBeInTheDocument();
  expect(within(select).queryByRole('option', { name: /BLOCKED-1/ })).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'good' } });
  fireEvent.click(screen.getByRole('button', { name: 'Assign courier' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Courier account is unavailable');
  await waitFor(() => expect(select).toHaveValue(''));
  expect(within(select).queryByRole('option', { name: /GOOD-1/ })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Assign courier' })).toBeDisabled();
  expect(screen.getByText(/No eligible couriers are available/)).toBeInTheDocument();
});
