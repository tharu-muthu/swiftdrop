import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingFormPage from './BookingFormPage.jsx';

it('does not show saved-address selection while creating a booking', () => {
  const state = { status: 'authenticated', user: { fullName: 'Alex', roles: ['CUSTOMER'] } };
  const request = vi.fn();
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), request }}><BookingFormPage /></AuthProvider>);
  expect(screen.queryByRole('button', { name: 'Use a saved address' })).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
