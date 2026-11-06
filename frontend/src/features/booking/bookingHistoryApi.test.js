import { describe, expect, it, vi } from 'vitest';
import { loadBookingDetails, loadBookingHistory } from './bookingHistoryApi.js';

describe('bookingHistoryApi', () => {
  it('sends history filters to the authenticated client without a customer id', async () => {
    const client = { request: vi.fn().mockResolvedValue({ bookings: [] }) };
    await loadBookingHistory(client, { search: 'SDP-ABC123', state: 'CREATED', from: '2026-11-01', to: '', page: 0, size: 10 });
    expect(client.request).toHaveBeenCalledWith('/api/v1/bookings?search=SDP-ABC123&state=CREATED&from=2026-11-01&page=0&size=10');
  });

  it('uses the selected booking id only in its protected details path', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) };
    await loadBookingDetails(client, 'a/b');
    expect(client.request).toHaveBeenCalledWith('/api/v1/bookings/a%2Fb');
  });
});
