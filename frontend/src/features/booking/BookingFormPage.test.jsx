import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingFormPage from './BookingFormPage.jsx';

function renderForm() {
  const snapshot = { status: 'authenticated', user: { fullName: 'Student User', phone: '0771234567', roles: ['CUSTOMER'] }, error: '' };
  const client = { getSnapshot: () => snapshot, subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request: vi.fn() };
  render(<AuthProvider client={client}><BookingFormPage /></AuthProvider>);
}

function completePickup() {
  fireEvent.change(screen.getByLabelText('Pickup address *'), { target: { value: '1 Station Road' } });
  fireEvent.change(screen.getByLabelText('City / Town *'), { target: { value: 'Colombo' } });
  fireEvent.change(screen.getByLabelText('Postal code *'), { target: { value: '00600' } });
  fireEvent.click(screen.getByRole('button', { name: 'Next: Recipient Information →' }));
}

describe('BookingFormPage', () => {
  it('validates required pickup data before advancing', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: 'Next: Recipient Information →' }));
    expect(screen.getByText('Pickup Address is required.')).toBeInTheDocument();
    expect(screen.getByText('Pickup Postal Code is required.')).toBeInTheDocument();
  });

  it('keeps pickup details when navigating back from recipient details', () => {
    renderForm();
    completePickup();
    expect(screen.getByRole('heading', { name: 'Recipient Information' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '← Back' }));
    expect(screen.getByLabelText('Pickup address *')).toHaveValue('1 Station Road');
  });

  it('rejects zero parcel weight', () => {
    renderForm();
    completePickup();
    fireEvent.change(screen.getByLabelText('Full name *'), { target: { value: 'Recipient' } });
    fireEvent.change(screen.getByLabelText('Contact number *'), { target: { value: '0771234567' } });
    fireEvent.change(screen.getByLabelText('Delivery address *'), { target: { value: '2 Main Road' } });
    fireEvent.change(screen.getByLabelText('City / Town *'), { target: { value: 'Kandy' } });
    fireEvent.change(screen.getByLabelText('Postal code *'), { target: { value: '20000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next: Parcel Information →' }));
    fireEvent.change(screen.getByLabelText('Weight in grams *'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next: Get Quote →' }));
    expect(screen.getByText('Enter a whole weight in grams greater than zero.')).toBeInTheDocument();
  });
});
