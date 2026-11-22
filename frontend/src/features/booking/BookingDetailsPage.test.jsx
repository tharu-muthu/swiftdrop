import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingDetailsPage from './BookingDetailsPage.jsx';

class MockEventSource {
  static latest;
  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.close = vi.fn();
    MockEventSource.latest = this;
  }
  addEventListener(name, handler) {
    this.listeners[name] = handler;
  }
}

beforeEach(() => {
  MockEventSource.latest = undefined;
  vi.stubGlobal('EventSource', MockEventSource);
  window.history.replaceState(null, '', '/bookings/booking-1');
});

describe('BookingDetailsPage live tracking', () => {
  it('updates the delivery status and timeline through SSE without a refresh', async () => {
    const state = { status: 'authenticated', user: { fullName: 'Alex', roles: ['CUSTOMER'] } };
    const client = {
      getSnapshot: () => state,
      subscribe: () => () => {},
      bootstrap: vi.fn(),
      request: vi.fn((url) => {
        if (url === '/api/v1/bookings/booking-1') {
          return Promise.resolve({
            id: 'booking-1',
            trackingNumber: 'SDP-ABC12345',
            recipientName: 'Alex',
            pickupCity: 'Colombo',
            deliveryCity: 'Kandy',
            bookingState: 'CONFIRMED',
            assigned: false,
          });
        }
        if (url === '/api/v1/tracking/customer/SDP-ABC12345') {
          return Promise.resolve({
            trackingNumber: 'SDP-ABC12345',
            currentStatus: 'IN_TRANSIT',
            pickupCity: 'Colombo',
            deliveryCity: 'Kandy',
            recipientName: 'Alex',
            timeline: [{ sequenceNo: 3, status: 'IN_TRANSIT', occurredAt: '2026-11-22T08:00:00Z' }],
          });
        }
        return Promise.reject(new Error(`Unexpected request: ${url}`));
      }),
    };

    render(<AuthProvider client={client}><BookingDetailsPage bookingId="booking-1" /></AuthProvider>);

    expect(await screen.findByText('IN TRANSIT')).toBeInTheDocument();
    expect(MockEventSource.latest.url).toBe('/api/v1/tracking/SDP-ABC12345/events');

    await act(async () => {
      MockEventSource.latest.listeners['tracking-update']({
        data: JSON.stringify({
          trackingNumber: 'SDP-ABC12345',
          currentStatus: 'DELIVERED',
          timeline: [
            { sequenceNo: 4, status: 'DELIVERED', occurredAt: '2026-11-22T09:00:00Z' },
          ],
        }),
      });
    });

    expect(screen.getAllByText('DELIVERED').length).toBeGreaterThan(0);
    expect(screen.getByRole('status')).toHaveTextContent('Live updates: live');
  });
});
