import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequestVerificationPage, VerifyEmailPage } from './EmailVerificationPages.jsx';
import { authPost } from './apiClient.js';

vi.mock('./apiClient.js', () => ({ authPost: vi.fn() }));
beforeEach(() => { vi.resetAllMocks(); window.history.replaceState(null, '', '/verify-email/request'); });

describe('email ownership screens', () => {
  it('returns generic resend feedback after a valid email', async () => {
    authPost.mockResolvedValue({});
    render(<RequestVerificationPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Send verification link' }));
    expect(screen.getByText('Enter a valid email address.')).toBeVisible();
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: ' person@example.test ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send verification link' }));
    await waitFor(() => expect(authPost).toHaveBeenCalledWith('/api/v1/auth/email-verification/request', { email: 'person@example.test' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If this account needs email verification');
  });

  it('removes the token from history and verifies only after a click', async () => {
    window.history.replaceState(null, '', '/verify-email#token=private-token');
    authPost.mockResolvedValue(null);
    render(<VerifyEmailPage />);
    expect(window.location.hash).toBe('');
    expect(authPost).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Verify email' }));
    await waitFor(() => expect(authPost).toHaveBeenCalledWith('/api/v1/auth/email-verification/confirm', { token: 'private-token' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Email verified');
  });
});
