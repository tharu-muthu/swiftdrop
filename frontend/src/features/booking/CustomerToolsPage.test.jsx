import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import CustomerToolsPage from './CustomerToolsPage.jsx';

function renderTools(path, request = vi.fn()) {
  const snapshot = { status: 'authenticated', user: { fullName: 'Student User', roles: ['CUSTOMER'] }, error: '' };
  const client = { getSnapshot: () => snapshot, subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request };
  render(<AuthProvider client={client}><CustomerToolsPage path={path} /></AuthProvider>);
  return request;
}

describe('CustomerToolsPage', () => {
  it('requests a customer quote using the entered route and parcel size', async () => {
    const request = vi.fn().mockResolvedValue({ currency: 'LKR', amountMinor: 45000, expiresAt: '2026-12-01T10:00:00Z' });
    renderTools('/quote', request);
    fireEvent.change(screen.getByLabelText('Pickup postal code'), { target: { value: '00600' } });
    fireEvent.change(screen.getByLabelText('Delivery postal code'), { target: { value: '20000' } });
    fireEvent.change(screen.getByLabelText('Parcel weight (grams)'), { target: { value: '600' } });
    fireEvent.click(screen.getByRole('button', { name: 'Get quote' }));
    expect(await screen.findByText('LKR 450.00')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/v1/quotes', expect.objectContaining({ method: 'POST', body: JSON.stringify({ pickupPostalCode: '00600', dropoffPostalCode: '20000', weightGrams: 600, size: 'SMALL' }) }));
  });

  it('shows receipt actions only for delivered bookings', async () => {
    const request = vi.fn().mockResolvedValue({ bookings: [
      { id: 'delivered', trackingNumber: 'SDP-DELIVERED', pickupCity: 'Colombo', deliveryCity: 'Kandy', deliveryStatus: 'DELIVERED' },
      { id: 'created', trackingNumber: 'SDP-CREATED', pickupCity: 'Colombo', deliveryCity: 'Galle', deliveryStatus: 'CREATED' },
    ], totalPages: 1 });
    renderTools('/receipts', request);
    expect(await screen.findByText('SDP-DELIVERED')).toBeInTheDocument();
    expect(screen.queryByText('SDP-CREATED')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open receipt' })).toBeInTheDocument();
  });
});
