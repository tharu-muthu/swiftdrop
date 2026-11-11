import { describe, expect, it, vi } from 'vitest';
import { createWebhookSubscription, listWebhookSubscriptions, updateWebhookSubscription } from './webhookApi.js';

describe('webhookApi', () => {
  it('uses the authenticated client for subscription reads and writes', async () => {
    const client = { request: vi.fn().mockResolvedValue([]) };
    await listWebhookSubscriptions(client);
    await createWebhookSubscription(client, { callbackUrl: 'https://merchant.test/hook', eventTypes: ['parcel.status.changed'] });
    await updateWebhookSubscription(client, 'id/1', { active: false });
    expect(client.request).toHaveBeenNthCalledWith(1, '/api/v1/merchant/webhooks');
    expect(client.request).toHaveBeenNthCalledWith(2, '/api/v1/merchant/webhooks', expect.objectContaining({ method: 'POST' }));
    expect(client.request).toHaveBeenNthCalledWith(3, '/api/v1/merchant/webhooks/id%2F1', expect.objectContaining({ method: 'PATCH' }));
  });
});
