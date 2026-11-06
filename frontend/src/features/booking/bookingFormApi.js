// This is intentionally a request-shaping adapter only. M3 owns the quote endpoint and pricing rules.
export function createQuotePreparationRequest(draft) {
  return {
    pickup: { fullName: draft.senderName.trim(), contactNumber: draft.senderPhone.trim(), address: draft.pickupAddress.trim(), city: draft.pickupCity.trim(), postalCode: draft.pickupPostalCode.trim() },
    recipient: { fullName: draft.recipientName.trim(), contactNumber: draft.recipientPhone.trim(), address: draft.deliveryAddress.trim(), city: draft.deliveryCity.trim(), postalCode: draft.deliveryPostalCode.trim() },
    parcel: { weightGrams: Number(draft.weightGrams), size: draft.parcelSize, description: draft.description.trim() },
  };
}
