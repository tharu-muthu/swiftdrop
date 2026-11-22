export function loadAssignedDeliveries(client, date) {
  return client.request(`/api/v1/courier/deliveries${date ? `?date=${encodeURIComponent(date)}` : ''}`);
}

export function loadDelivery(client, lifecycleId) {
  return client.request(`/api/v1/courier/deliveries/${lifecycleId}`);
}

export function updateDeliveryStatus(client, lifecycleId, payload) {
  return client.request(`/api/v1/courier/deliveries/${lifecycleId}/status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}
