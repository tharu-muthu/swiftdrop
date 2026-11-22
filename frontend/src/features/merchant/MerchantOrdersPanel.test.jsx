import { render, screen, fireEvent, within } from '@testing-library/react';
import { it, expect, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import MerchantOrdersPanel from './MerchantOrdersPanel.jsx';

function renderPanel(request) {
  const state = { status: 'authenticated', user: { roles: ['MERCHANT'] } };
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: () => {}, request }}><MerchantOrdersPanel /></AuthProvider>);
}
const order = { id: 'order-1', trackingNumber: 'SDP-ORDER1', bookingState: 'CREATED', deliveryStatus: 'IN_TRANSIT',
  pickupCity: 'Colombo', deliveryCity: 'Kandy', currencyCode: 'LKR', quotedAmountMinor: 45000, createdAt: '2026-11-21T10:00:00Z' };
const page = (items, number = 0, totalPages = 1, totalElements = items.length) => ({ items, page: number, size: 20, totalPages, totalElements });

it('shows canonical delivery status in the list and loads the latest status in order details', async () => {
  const request = vi.fn(async path => path.includes('?') ? page([order]) : { ...order, deliveryStatus: 'DELIVERED', statusUpdatedAt: '2026-11-21T12:00:00Z' });
  renderPanel(request);
  expect(await screen.findByText('In transit')).toBeInTheDocument();
  expect(screen.queryByText('Created')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'View' }));
  const details = await screen.findByRole('region', { name: 'Merchant order details' });
  expect(within(details).getByText('Delivered')).toBeInTheDocument();
  expect(within(details).getByText(/Last status update:/)).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith('/api/v1/merchant/dashboard/orders/order-1');
});

it('can reach and navigate back from orders beyond the first hundred', async () => {
  const request = vi.fn(async path => {
    const number = Number(new URL(path, 'http://localhost').searchParams.get('page'));
    return page([{ ...order, id: `order-${number}`, trackingNumber: `SDP-PAGE${number}` }], number, 6, 105);
  });
  renderPanel(request);
  await screen.findByText('105 orders');
  expect(screen.getByRole('button', { name: 'Previous orders' })).toBeDisabled();
  for (let number = 1; number <= 5; number++) {
    fireEvent.click(screen.getByRole('button', { name: 'Next orders' }));
    await screen.findByText(`Page ${number + 1} of 6`);
  }
  expect(request).toHaveBeenCalledWith('/api/v1/merchant/dashboard/orders?page=5&size=20');
  expect(screen.getByRole('button', { name: 'Next orders' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Previous orders' }));
  await screen.findByText('Page 5 of 6');
  expect(request).toHaveBeenCalledWith('/api/v1/merchant/dashboard/orders?page=4&size=20');
});

it('refreshes canonical status and identifies missing legacy lifecycle data', async () => {
  const request = vi.fn().mockResolvedValueOnce(page([{ ...order, deliveryStatus: null }])).mockResolvedValue(page([{ ...order, deliveryStatus: 'OUT_FOR_DELIVERY' }]));
  renderPanel(request);
  expect(await screen.findByText('Not initialized')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Refresh orders' }));
  expect(await screen.findByText('Out for delivery')).toBeInTheDocument();
});

it('shows assignment as a distinct operational state while lifecycle remains created', async () => {
  const request = vi.fn().mockResolvedValue(page([{ ...order, deliveryStatus: 'CREATED', assigned: true }]));
  renderPanel(request);
  expect(await screen.findByText('Assigned')).toBeInTheDocument();
});
