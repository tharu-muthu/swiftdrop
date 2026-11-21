export async function loadActiveCouriers(client) {
  return client.request('/api/v1/dispatch/couriers/active');
}

export async function assignParcel(client, bookingId, courierId) {
  return client.request('/api/v1/dispatch/assignments', {
    method: 'POST',
    body: JSON.stringify({ bookingId, courierId })
  });
}