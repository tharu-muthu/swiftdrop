const labels = {
  CREATED: 'Created',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
};

export function deliveryStatus(status, assigned = false) {
  if (assigned && status === 'CREATED') return { label: 'Assigned', className: 'assigned' };
  return { label: labels[status] || 'Not initialized', className: (status || 'unknown').toLowerCase() };
}
