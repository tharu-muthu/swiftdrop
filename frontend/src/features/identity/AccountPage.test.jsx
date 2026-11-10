import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import AccountPage from './AccountPage.jsx';

const user = { fullName: 'Alex Perera', email: 'alex@example.test', phone: null,
  roles: ['CUSTOMER'], active: true, createdAt: '2026-11-01T00:00:00Z', version: 2,
  passwordConfigured: true, googleConnected: false };

function setup(updateProfile = vi.fn(), reloadProfile = vi.fn()) {
  const state = { status: 'authenticated', user, error: '' };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(),
    updateProfile, reloadProfile, logout: vi.fn() };
  render(<AuthProvider client={client}><AccountPage /></AuthProvider>);
  return client;
}

describe('profile page', () => {
  it('shows only safe account details and makes email and roles read-only', () => {
    setup();
    expect(screen.getAllByText('Alex Perera').length).toBeGreaterThan(0);
    expect(screen.getAllByText('alex@example.test').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: 'Edit profile' }));
    expect(screen.getByLabelText('Full name *')).toHaveValue('Alex Perera');
    expect(screen.queryByRole('textbox', { name: 'Email address' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /role/i })).not.toBeInTheDocument();
  });

  it('validates and submits only editable fields with a version', async () => {
    const update = vi.fn().mockResolvedValue({ ...user, fullName: 'New Name', phone: '+94771234567', version: 3 });
    setup(update); fireEvent.click(screen.getByRole('button', { name: 'Edit profile' }));
    fireEvent.change(screen.getByLabelText('Full name *'), { target: { value: 'New Name' } });
    fireEvent.change(screen.getByLabelText('Mobile number (optional)'), { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(screen.getByText('Enter a valid phone number or leave it empty.')).toBeVisible();
    expect(update).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Mobile number (optional)'), { target: { value: '+94 77-1234567' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(update).toHaveBeenCalledWith({ fullName: 'New Name', phone: '+94 77-1234567', version: 2 }));
    expect(await screen.findByRole('status')).toHaveTextContent('Profile updated.');
  });

  it('retains draft after a conflict and offers a reload', async () => {
    const update = vi.fn().mockRejectedValue({ status: 409 });
    setup(update); fireEvent.click(screen.getByRole('button', { name: 'Edit profile' }));
    fireEvent.change(screen.getByLabelText('Full name *'), { target: { value: 'New Name' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('changed elsewhere');
    expect(screen.getByLabelText('Full name *')).toHaveValue('New Name');
    expect(screen.getByRole('button', { name: 'Review latest details' })).toBeVisible();
  });
});
