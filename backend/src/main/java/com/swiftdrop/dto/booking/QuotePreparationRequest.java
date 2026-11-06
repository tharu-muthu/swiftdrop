package com.swiftdrop.dto.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** M4-to-M3 contract candidate. M4 must not expose it as a quote endpoint before M3 agrees the contract. */
public record QuotePreparationRequest(
        @NotNull(message = "Pickup information is required.") @Valid AddressContactRequest pickup,
        @NotNull(message = "Recipient information is required.") @Valid AddressContactRequest recipient,
        @NotNull(message = "Parcel information is required.") @Valid ParcelDetailsRequest parcel) { }
