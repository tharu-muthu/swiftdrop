import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import BookingFormPage from './BookingFormPage.jsx';

function renderForm(request = vi.fn()) {
  const snapshot = { status: 'authenticated', user: { fullName: 'Student User', phone: '0771234567', roles: ['CUSTOMER'] }, error: '' };
  const client = { getSnapshot: () => snapshot, subscribe: () => () => {}, bootstrap: () => Promise.resolve(), request };
  render(<AuthProvider client={client}><BookingFormPage /></AuthProvider>);
}

function completePickup() {
  fireEvent.change(screen.getByLabelText('Pickup address *'), { target: { value: '1 Station Road' } });
  fireEvent.change(screen.getByLabelText('City / Town *'), { target: { value: 'Colombo' } });
  fireEvent.change(screen.getByLabelText('Postal code *'), { target: { value: '00600' } });
  fireEvent.click(screen.getByRole('button', { name: 'Next: Recipient Information →' }));
}

function completeBookingDetails() {
  completePickup();
  fireEvent.change(screen.getByLabelText('Full name *'), { target: { value: 'Recipient' } });
  fireEvent.change(screen.getByLabelText('Contact number *'), { target: { value: '0771234567' } });
  fireEvent.change(screen.getByLabelText('Delivery address *'), { target: { value: '2 Main Road' } });
  fireEvent.change(screen.getByLabelText('City / Town *'), { target: { value: 'Kandy' } });
  fireEvent.change(screen.getByLabelText('Postal code *'), { target: { value: '20000' } });
  fireEvent.click(screen.getByRole('button', { name: 'Next: Parcel Information →' }));
  fireEvent.change(screen.getByLabelText('Weight in grams *'), { target: { value: '600' } });
  fireEvent.change(screen.getByLabelText('Parcel size *'), { target: { value: 'MEDIUM' } });
  fireEvent.change(screen.getByLabelText('Parcel description *'), { target: { value: 'Books' } });
  fireEvent.click(screen.getByRole('button', { name: 'Next: Get Quote →' }));
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

  it('shows reviewed details but does not create a booking before M3 returns a quote', () => {
    renderForm();
    completeBookingDetails();
    expect(screen.getByRole('heading', { name: 'Ready to request a delivery quote' })).toBeInTheDocument();
    const review = within(screen.getByLabelText('Booking details for quote'));
    expect(review.getByText('Student User')).toBeInTheDocument();
    expect(review.getByText('2 Main Road, Kandy, 20000')).toBeInTheDocument();
    expect(review.getByText('600 g · medium')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Get quote' })).toBeEnabled();
  });

  it('requests a real quote using the price-driving inputs', async () => {
    const request = vi.fn().mockResolvedValue({ currency: 'LKR', amountMinor: 45000, expiresAt: '2026-12-01T10:00:00Z' });
    renderForm(request);
    completeBookingDetails();
    fireEvent.click(screen.getByRole('button', { name: 'Get quote' }));
    expect(await screen.findByText('Delivery quote: LKR 450.00')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith('/api/v1/quotes', expect.objectContaining({ method: 'POST', body: JSON.stringify({ pickupPostalCode: '00600', dropoffPostalCode: '20000', weightGrams: 600, size: 'MEDIUM' }) }));
    expect(screen.queryByRole('button', { name: 'Get quote' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeInTheDocument();
  });
  it('confirms with a stable idempotency key when a network failure is retried', async()=>{
    const quote={quoteId:'quote-1',currency:'LKR',amountMinor:45000,baseMinor:40000,extraWeightMinor:5000,sizeSurchargeMinor:0,expiresAt:'2026-12-01T10:00:00Z'};
    const request=vi.fn().mockResolvedValueOnce(quote).mockRejectedValue(new Error('Connection lost. Retry.'));
    renderForm(request);completeBookingDetails();fireEvent.click(screen.getByRole('button',{name:'Get quote'}));
    await screen.findByText('Delivery quote: LKR 450.00');fireEvent.click(screen.getByRole('button',{name:'Confirm booking'}));
    await screen.findByRole('alert');fireEvent.click(screen.getByRole('button',{name:'Confirm booking'}));
    await screen.findByRole('alert');
    const submissions=request.mock.calls.filter(([path])=>path==='/api/v1/bookings');
    expect(submissions).toHaveLength(2);expect(submissions[0][1].headers['Idempotency-Key']).toBe(submissions[1][1].headers['Idempotency-Key']);
    expect(JSON.parse(submissions[0][1].body)).toEqual(expect.objectContaining({quoteId:'quote-1',parcel:{weightGrams:600,size:'MEDIUM',description:'Books'}}));
  });
});
