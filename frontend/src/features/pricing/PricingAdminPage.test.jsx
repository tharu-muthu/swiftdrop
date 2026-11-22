import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import PricingAdminPage from './PricingAdminPage.jsx';

const { request, client } = vi.hoisted(() => { const request = vi.fn(); return { request, client: { request } }; });
vi.mock('../identity/AuthProvider.jsx', () => ({ useAuth: () => ({ client }) }));
vi.mock('../../components/WorkspacePage.jsx', () => ({
  default: ({ children }) => <main>{children}</main>,
  JsonRequest: (method, body) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
}));

it('uses backend zone and rate identifiers for mapping and state changes', async () => {
  const zoneId = 'a795473b-7e49-4d77-83eb-26d103777c24';
  const rateVersionId = '50bb2185-e4e1-4c0e-a8c8-671376dd57c9';
  request.mockImplementation(async (path, options) => {
    if (options) return null;
    if (path === '/api/v1/admin/zones') return [{ zoneId, name: 'Colombo', active: true }];
    if (path === '/api/v1/admin/rate-cards') return [{ rateVersionId, pickupZoneId: zoneId, dropoffZoneId: zoneId, baseMinor: 100, extraKgMinor: 0, effectiveFrom: '2026-11-22T00:00:00Z', active: true }];
    return [];
  });
  render(<PricingAdminPage />);
  await screen.findAllByRole('option', { name: 'Colombo' });
  fireEvent.change(screen.getByLabelText('Zone'), { target: { value: zoneId } });
  fireEvent.change(screen.getByLabelText('Postal code'), { target: { value: '00600' } });
  fireEvent.click(screen.getByRole('button', { name: 'Add mapping' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith(`/api/v1/admin/zones/${zoneId}/postcodes`, expect.objectContaining({ method: 'POST', body: JSON.stringify({ postcode: '00600' }) })));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Add mapping' })).toBeEnabled());
  const zones = screen.getByRole('heading', { name: 'Delivery zones' }).closest('section');
  fireEvent.click(within(zones).getByRole('button', { name: 'Deactivate' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith(`/api/v1/admin/zones/${zoneId}`, expect.objectContaining({ method: 'PATCH' })));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Add mapping' })).toBeEnabled());
  fireEvent.click(within(screen.getByRole('table')).getByRole('button', { name: 'Deactivate' }));
  await waitFor(() => expect(request).toHaveBeenCalledWith(`/api/v1/admin/rate-cards/${rateVersionId}`, expect.objectContaining({ method: 'PATCH' })));
});
