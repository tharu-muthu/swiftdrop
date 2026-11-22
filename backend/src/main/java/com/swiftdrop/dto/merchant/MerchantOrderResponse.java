package com.swiftdrop.dto.merchant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.entity.execution.ParcelStatus;
import java.time.Instant;

/** Booking fields stay compatible; delivery state comes exclusively from M6. */
public record MerchantOrderResponse(@JsonUnwrapped BookingSnapshot booking,
        ParcelStatus deliveryStatus, Instant statusUpdatedAt, boolean assigned) { }
