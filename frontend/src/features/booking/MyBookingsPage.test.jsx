import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import MyBookingsPage from './MyBookingsPage.jsx';

describe('MyBookingsPage', () => {
  it('shows an empty state from the real history API response, without demo records', async () => {
    const snapshot = { status: 'authenticated', user: { fullName: 'Student User', roles: ['CUSTOMER'] }, error: '' };
    const request = vi.fn().mockResolvedValue({ bookings: [], summary: { totalBookings: 0, pendingBookings: 0, deliveryStatusAvailable: false }, page: 0, size: 10, totalPages: 0, totalElements: 0 });
    const client = { getSnapshot: () => snapshot, subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request };
    render(<AuthProvider client={client}><MyBookingsPage /></AuthProvider>);
    expect(await screen.findByText('No bookings found')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/v1/bookings?page=0&size=10');
    expect(screen.getAllByText('M6 pending')).toHaveLength(2);
  });
});
