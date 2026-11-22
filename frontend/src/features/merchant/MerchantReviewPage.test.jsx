import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import MerchantReviewPage from './MerchantReviewPage.jsx';
it('approves through the merchant workflow and explains the owner sign-in step', async () => {
  let approved = false;
  const request = vi.fn(async path => {
    if (path.endsWith('/approve')) { approved = true; return {}; }
    return { items: approved ? [] : [{ id: 'shop', businessName: 'Test Shop', approvalStatus: 'PENDING' }], totalPages: 1 };
  });
  const snapshot = { status: 'authenticated', user: { fullName: 'Administrator', roles: ['DISPATCHER_ADMIN'] } };
  const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
  render(<AuthProvider client={client}><MerchantReviewPage /></AuthProvider>);
  fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
  expect(await screen.findByRole('status')).toHaveTextContent('owner must sign in again');
  expect(request).toHaveBeenCalledWith('/api/v1/admin/merchants/shop/approve', expect.objectContaining({ method: 'POST' }));
  expect(await screen.findByText('No applications match this filter.')).toBeVisible();
});
