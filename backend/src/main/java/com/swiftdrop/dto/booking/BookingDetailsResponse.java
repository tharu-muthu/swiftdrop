package com.swiftdrop.dto.booking;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Keeps history summary fields stable and adds the authorized immutable booking snapshots. */
public record BookingDetailsResponse(@JsonUnwrapped BookingHistoryItem summary,BookingSnapshot details) { }
