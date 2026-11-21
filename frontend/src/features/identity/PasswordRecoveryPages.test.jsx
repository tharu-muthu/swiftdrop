import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ForgotPasswordPage, ResetPasswordPage } from './PasswordRecoveryPages.jsx';
import { authPost } from './apiClient.js';

vi.mock('./apiClient.js', () => ({ authPost: vi.fn() }));
beforeEach(() => { vi.resetAllMocks(); window.history.replaceState(null, '', '/forgot-password'); });

describe('password recovery screens', () => {
  it('validates email and gives the same acknowledgement after request', async () => {
    authPost.mockResolvedValue({ message: 'accepted' });
    render(<ForgotPasswordPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(screen.getByText('Enter a valid email address.')).toBeVisible();
    expect(authPost).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: ' person@example.test ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    await waitFor(() => expect(authPost).toHaveBeenCalledWith('/api/v1/auth/password-reset/request', { email: 'person@example.test' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If this account can use password recovery');
  });

  it('clears token from browser history and validates confirmation', async () => {
    window.history.replaceState(null, '', '/reset-password#token=private-reset-token');
    authPost.mockResolvedValue(null);
    render(<ResetPasswordPage />);
    expect(window.location.hash).toBe('');
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'new password 123' } });
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'different' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));
    expect(screen.getByText('Passwords must match.')).toBeVisible();
    expect(authPost).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'new password 123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));
    await waitFor(() => expect(authPost).toHaveBeenCalledWith('/api/v1/auth/password-reset/confirm',
      { token: 'private-reset-token', password: 'new password 123' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Password updated');
  });
});
