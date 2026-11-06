export function loadBookingDashboard(client) {
  return client.request('/api/v1/bookings/dashboard');
}
