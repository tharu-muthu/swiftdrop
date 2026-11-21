package com.swiftdrop.dto.booking;

import java.time.Instant;
import java.util.UUID;

/** Trusted inter-feature read contract. Callers enforce their resource authorization. */
public record BookingSnapshot(UUID id, UUID customerId, UUID merchantId, String trackingNumber,
        String pickupAddressSnapshot, String dropoffAddressSnapshot, String parcelDescription,
        int parcelWeightGrams, String recipientName, String recipientPhone, String pickupCity,
        String deliveryCity, Integer quotedAmountMinor, String currencyCode, String bookingState,
        String receiptState, Instant createdAt, String senderName,String senderPhone,String pickupPostalCode,
        String deliveryPostalCode,String parcelSize,String externalReference) { }
