import { describe, expect, it, vi } from 'vitest';
import { loadPublicTracking, trackingEventsUrl } from './trackingApi.js';

describe('trackingApi', () => {
  it('encodes the tracking number in the public endpoint', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(JSON.stringify({ trackingNumber: 'SD/A' }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }));
    await loadPublicTracking('SD/A', fetchImpl);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/tracking/SD%2FA', expect.objectContaining({ credentials: 'same-origin' }));
  });

  it('builds the public SSE endpoint from the tracking number', () => {
    expect(trackingEventsUrl('SD/A')).toBe('/api/v1/tracking/SD%2FA/events');
  });
});
