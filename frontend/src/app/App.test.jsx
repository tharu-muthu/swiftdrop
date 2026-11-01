import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

describe('foundation connection check', () => {
  it('shows a connection only after the real health response shape is received', async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'UP', application: 'swiftdrop' }),
    });
    vi.stubGlobal('fetch', fetch);
    render(<App />);
    expect(screen.getByRole('status')).toHaveTextContent('Checking backend');
    expect(await screen.findByText('Backend connected')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/health', expect.objectContaining({ signal: expect.anything() }));
  });

  it('shows failure and allows recovery after a failed request', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockRejectedValueOnce(new Error('Network unavailable'))
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'UP', application: 'swiftdrop' }) }));
    render(<App />);
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Check again' }));
    expect(await screen.findByText('Backend connected')).toBeInTheDocument();
  });

  it.each([
    { ok: false, json: async () => ({ status: 'UP' }) },
    { ok: true, json: async () => ({ status: 'DOWN' }) },
  ])('does not report success for an unhealthy response', async response => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    render(<App />);
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument();
  });
});
