export function listWebhookSubscriptions(client) {
  return client.request('/api/v1/merchant/webhooks');
}

export function createWebhookSubscription(client, payload) {
  return client.request('/api/v1/merchant/webhooks', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
}

export function updateWebhookSubscription(client, id, payload) {
  return client.request(`/api/v1/merchant/webhooks/${encodeURIComponent(id)}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
}

export function rotateWebhookSecret(client, id) {
  return client.request(`/api/v1/merchant/webhooks/${encodeURIComponent(id)}/rotate-secret`, { method: 'POST' });
}

export function testWebhook(client, id) {
  return client.request(`/api/v1/merchant/webhooks/${encodeURIComponent(id)}/test`, { method: 'POST' });
}

export function loadWebhookDeliveries(client, id) {
  return client.request(`/api/v1/merchant/webhooks/${encodeURIComponent(id)}/deliveries`);
}
