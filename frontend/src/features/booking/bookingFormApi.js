// This is intentionally a request-shaping adapter only. M3 owns the quote endpoint and pricing rules.
export function createQuotePreparationRequest(draft) {
  return {
    pickup: { fullName: draft.senderName.trim(), contactNumber: draft.senderPhone.trim().replaceAll(' ', ''), address: draft.pickupAddress.trim(), city: draft.pickupCity.trim(), postalCode: draft.pickupPostalCode.trim() },
    recipient: { fullName: draft.recipientName.trim(), contactNumber: draft.recipientPhone.trim().replaceAll(' ', ''), address: draft.deliveryAddress.trim(), city: draft.deliveryCity.trim(), postalCode: draft.deliveryPostalCode.trim() },
    parcel: { weightGrams: Number(draft.weightGrams), size: draft.parcelSize, description: draft.description.trim() },
  };
}

export function requestQuote(client, draft, merchant = false) {
  return client.request(merchant ? '/api/v1/merchant/dashboard/orders/quote' : '/api/v1/quotes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      pickupPostalCode: draft.pickupPostalCode.trim(),
      dropoffPostalCode: draft.deliveryPostalCode.trim(),
      weightGrams: Number(draft.weightGrams),
      size: draft.parcelSize,
    }),
  });
}

export function confirmBooking(client, draft, quoteId, key, merchant = false) {
  return client.request(merchant ? '/api/v1/merchant/dashboard/orders' : '/api/v1/bookings', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
    body: JSON.stringify({ quoteId, ...createQuotePreparationRequest(draft), ...(merchant && draft.externalReference?.trim() ? { externalReference:draft.externalReference.trim() } : {}) }),
  });
}
