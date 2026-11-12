import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import WebhookSettingsPage from './WebhookSettingsPage.jsx';

function renderPage(request) {
  const snapshot = { status: 'authenticated', user: { fullName: 'Approved Merchant', roles: ['MERCHANT'] }, error: '' };
  const client = { getSnapshot: () => snapshot, subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request };
  render(<AuthProvider client={client}><WebhookSettingsPage /></AuthProvider>);
}

describe('WebhookSettingsPage', () => {
  it('creates a subscription and displays the one-time secret', async () => {
    const request = vi.fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({ signingSecret: 'secret-value', subscription: { id: '1', callbackUrl: 'https://merchant.test/hook', eventTypes: ['parcel.status.changed'], active: true } })
      .mockResolvedValueOnce([]);
    renderPage(request);
    fireEvent.change(await screen.findByLabelText('HTTPS callback URL'), { target: { value: 'https://merchant.test/hook' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create subscription' }));
    expect(await screen.findByText('Webhook created. Copy the signing secret now.')).toBeInTheDocument();
    expect(screen.getByText('secret-value')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/v1/merchant/webhooks', expect.objectContaining({ method: 'POST' }));
  });
});
