function queryString(filters) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => { if (value !== '' && value !== null && value !== undefined) params.set(key, String(value)); });
  return params.toString();
}

export function loadBookingHistory(client, filters) {
  const query = queryString(filters);
  return client.request(`/api/v1/bookings${query ? `?${query}` : ''}`);
}

export function loadBookingDetails(client, bookingId) {
  return client.request(`/api/v1/bookings/${encodeURIComponent(bookingId)}`);
}
