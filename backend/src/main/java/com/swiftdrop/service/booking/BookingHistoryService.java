package com.swiftdrop.service.booking;

import com.swiftdrop.dto.booking.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.booking.ParcelBooking;
import com.swiftdrop.repository.booking.ParcelBookingRepository;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingHistoryService {
    private final IdentityAccountService identities;
    private final ParcelBookingRepository bookings;

    public BookingHistoryService(IdentityAccountService identities, ParcelBookingRepository bookings) {
        this.identities = identities;
        this.bookings = bookings;
    }

    public BookingHistoryResponse history(String search, String bookingState, LocalDate fromDate, LocalDate toDate,
            int page, int size) {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var result = bookings.findHistory(customer.userId(), normalized(search), normalizedState(bookingState),
                startOfDay(fromDate), startOfFollowingDay(toDate),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        long pending = result.getContent().stream().filter(booking -> isPending(booking.getBookingState())).count();
        // The summary's total must not change with the table filters; it reflects all owned records.
        var all = bookings.findHistory(customer.userId(), null, null, null, null, Pageable.unpaged());
        long allPending = all.getContent().stream().filter(booking -> isPending(booking.getBookingState())).count();
        return new BookingHistoryResponse(result.map(this::item).getContent(),
                new BookingHistorySummary(all.getTotalElements(), allPending, false), result.getNumber(), result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    public BookingHistoryItem detail(UUID bookingId) {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var booking = bookings.findById(bookingId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!booking.getCustomerId().equals(customer.userId())) throw new AccessDeniedException("Access denied");
        return item(booking);
    }

    private BookingHistoryItem item(ParcelBooking booking) {
        return new BookingHistoryItem(booking.getId(), booking.getTrackingNumber(), booking.getRecipientName(),
                booking.getRecipientPhone(), booking.getPickupCity(), booking.getDeliveryCity(), booking.getCreatedAt(),
                booking.getQuotedAmountMinor(), booking.getCurrencyCode(), booking.getBookingState());
    }
    private static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizedState(String value) {
        var state = normalized(value);
        return state == null ? null : switch (state) { case "CREATED", "CONFIRMED", "CANCELLED" -> state; default -> throw new IllegalArgumentException("Invalid booking state"); };
    }
    private static Instant startOfDay(LocalDate date) { return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant(); }
    private static Instant startOfFollowingDay(LocalDate date) { return date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(); }
    private static boolean isPending(String state) { return "CREATED".equals(state) || "CONFIRMED".equals(state); }
}
