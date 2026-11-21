import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import CourierDashboardPage from './CourierDashboardPage.jsx';

const courier = { id: 'courier-1', fullName: 'Courier One', roles: ['COURIER'] };
const summary = {
  lifecycleId: 'lifecycle-1',
  bookingId: 'booking-1',
  trackingNumber: 'SD-1001',
  status: 'OUT_FOR_DELIVERY',
  updatedAt: '2026-11-21T08:00:00Z',
};
const details = {
  ...summary,
  customerId: 'customer-1',
  pickupAddress: 'Colombo',
  dropoffAddress: 'Kandy',
  parcelDescription: 'Documents',
  parcelWeightGrams: 500,
  history: [{
    sequenceNo: 1,
    previousStatus: null,
    newStatus: 'CREATED',
    actorUserId: 'dispatcher-1',
    failureReason: null,
    occurredAt: '2026-11-21T07:00:00Z',
  }],
};

describe('courier execution dashboard', () => {
  it('shows an assigned parcel and requires recipient proof for delivery', async () => {
    const request = vi.fn().mockImplementation(path => {
      if (path === '/api/v1/courier/deliveries/lifecycle-1/status') {
        return Promise.resolve({ ...details, status: 'DELIVERED' });
      }
      if (path === '/api/v1/courier/deliveries/lifecycle-1') return Promise.resolve(details);
      return Promise.resolve([summary]);
    });
    const snapshot = { status: 'authenticated', user: courier };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };

    render(<AuthProvider client={client}><CourierDashboardPage /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /SD-1001/i }));
    expect(await screen.findByText('Kandy')).toBeVisible();

    fireEvent.change(screen.getByLabelText('Recipient name'), { target: { value: 'Alex' } });
    fireEvent.click(screen.getByRole('button', { name: 'DELIVERED' }));

    await waitFor(() => expect(request).toHaveBeenCalledWith(
      '/api/v1/courier/deliveries/lifecycle-1/status',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          expectedStatus: 'OUT_FOR_DELIVERY',
          status: 'DELIVERED',
          recipientName: 'Alex',
          failureReason: null,
        }),
      }),
    ));
  });
});
