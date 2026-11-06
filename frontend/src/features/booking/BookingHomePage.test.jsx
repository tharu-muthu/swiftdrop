import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingHomePage from './BookingHomePage.jsx';

function renderHome(request = vi.fn().mockResolvedValue({ activeBookings: 3, pendingReceipts: 1, thisMonthShipments: 5 })) {
  const snapshot = { status: 'authenticated', user: { fullName: 'Student User', roles: ['CUSTOMER'] }, error: '' };
  const client = {
    getSnapshot: () => snapshot,
    subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request,
  };
  render(<AuthProvider client={client}><BookingHomePage /></AuthProvider>);
  return request;
}

describe('BookingHomePage', () => {
  it('loads customer-owned dashboard figures from the authenticated API client', async () => {
    const request = renderHome();
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/v1/bookings/dashboard');
    expect(screen.queryByText('Recent Bookings')).not.toBeInTheDocument();
  });

  it('preserves a tracking number when opening the tracking destination', () => {
    renderHome();
    fireEvent.change(screen.getByLabelText('Tracking number'), { target: { value: 'sdp-a12b34' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Track Parcel' }).at(-1));
    expect(window.location.pathname).toBe('/tracking');
    expect(window.location.search).toBe('?number=SDP-A12B34');
  });

  it('rejects an incomplete tracking number before navigation', () => {
    renderHome();
    fireEvent.change(screen.getByLabelText('Tracking number'), { target: { value: 'abc' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Track Parcel' }).at(-1));
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a tracking number');
  });
});
