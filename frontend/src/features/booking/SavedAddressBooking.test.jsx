import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingFormPage from './BookingFormPage.jsx';

it('copies a selected pickup address and contact into editable booking fields', async () => {
  const saved = { id: 'home', label: 'Home', contactName: 'Saved Sender', contactPhone: '0771234567', addressLine1: '1 Station Road', addressLine2: 'Second floor', city: 'Colombo', postalCode: '00600', defaultPickup: true };
  const state = { status: 'authenticated', user: { fullName: 'Alex', roles: ['CUSTOMER'] } };
  const request = vi.fn().mockResolvedValue([saved]);
  render(<AuthProvider client={{ subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), request }}><BookingFormPage /></AuthProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Use a saved address' }));
  fireEvent.change(await screen.findByLabelText('Saved pickup address'), { target: { value: 'home' } });
  expect(screen.getByLabelText('Full name *')).toHaveValue('Saved Sender');
  expect(screen.getByLabelText('Pickup address *')).toHaveValue('1 Station Road, Second floor');
  saved.addressLine1 = 'Changed after selection';
  expect(screen.getByLabelText('Pickup address *')).toHaveValue('1 Station Road, Second floor');
  fireEvent.change(screen.getByLabelText('Pickup address *'), { target: { value: 'Booking-specific address' } });
  expect(saved.addressLine1).toBe('Changed after selection');
});
