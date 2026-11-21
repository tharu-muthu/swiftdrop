package com.swiftdrop.service.booking;

import com.swiftdrop.dto.booking.BookingSnapshot;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingReadService {
    private final ParcelBookingRepository bookings;
    public BookingReadService(ParcelBookingRepository bookings) { this.bookings = bookings; }
    public Optional<BookingSnapshot> find(UUID id) { return bookings.findById(id).map(BookingReadService::snapshot); }
    public Optional<BookingSnapshot> byTrackingNumber(String number) {
        return bookings.findByTrackingNumber(number).map(BookingReadService::snapshot);
    }
    @Transactional
    public void deliveryCompleted(UUID id) { bookings.findById(id).orElseThrow().issueReceipt(); }
    public static BookingSnapshot snapshot(ParcelBooking b) {
        return new BookingSnapshot(b.getId(), b.getCustomerId(), b.getMerchantId(), b.getTrackingNumber(),
                b.getPickupAddressSnapshot(), b.getDropoffAddressSnapshot(), b.getParcelDescription(), b.getParcelWeightGrams(),
                b.getRecipientName(), b.getRecipientPhone(), b.getPickupCity(), b.getDeliveryCity(), b.getQuotedAmountMinor(),
                b.getCurrencyCode(), b.getBookingState(), b.getReceiptState(), b.getCreatedAt(),b.getSenderName(),b.getSenderPhone(),
                b.getPickupPostalCode(),b.getDeliveryPostalCode(),b.getParcelSize(),b.getExternalReference());
    }
}
