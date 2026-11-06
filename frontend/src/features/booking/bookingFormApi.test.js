import { describe, expect, it } from 'vitest';
import { createQuotePreparationRequest } from './bookingFormApi.js';

describe('createQuotePreparationRequest', () => {
  it('maps retained form state to the future M3 contract without an owner id or price', () => {
    const request = createQuotePreparationRequest({
      senderName: ' Sender ', senderPhone: '0771234567', pickupAddress: ' 1 Road ', pickupCity: ' Colombo ', pickupPostalCode: '00600',
      recipientName: ' Recipient ', recipientPhone: '0777654321', deliveryAddress: ' 2 Road ', deliveryCity: ' Kandy ', deliveryPostalCode: '20000',
      weightGrams: '1500', parcelSize: 'EXTRA_LARGE', description: ' Books ',
    });
    expect(request).toEqual({
      pickup: { fullName: 'Sender', contactNumber: '0771234567', address: '1 Road', city: 'Colombo', postalCode: '00600' },
      recipient: { fullName: 'Recipient', contactNumber: '0777654321', address: '2 Road', city: 'Kandy', postalCode: '20000' },
      parcel: { weightGrams: 1500, size: 'EXTRA_LARGE', description: 'Books' },
    });
  });
});
