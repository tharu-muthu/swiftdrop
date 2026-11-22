package com.swiftdrop.dto.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookingCreateRequest(@NotNull UUID quoteId,
        @NotNull @Valid AddressContactRequest pickup,
        @NotNull @Valid AddressContactRequest recipient,
        @NotNull @Valid ParcelDetailsRequest parcel,
        @jakarta.validation.constraints.Size(max=100) String externalReference) { }
