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
    private final com.swiftdrop.service.execution.LifecycleReadService lifecycles;

    public BookingHistoryService(IdentityAccountService identities, ParcelBookingRepository bookings,com.swiftdrop.service.execution.LifecycleReadService lifecycles) {
        this.identities = identities;
        this.bookings = bookings;
        this.lifecycles=lifecycles;
    }

    public BookingHistoryResponse history(String search, String bookingState, LocalDate fromDate, LocalDate toDate,
            int page, int size) {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var result = bookings.findHistory(customer.userId(), normalized(search), normalizedState(bookingState),
                startOfDay(fromDate), startOfFollowingDay(toDate),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        // The summary's total must not change with the table filters; it reflects all owned records.
        var all = bookings.findHistory(customer.userId(), null, null, null, null, Pageable.unpaged());
        var states=lifecycles.statuses(all.getContent().stream().map(row->row.getId()).toList());
        long allPending = all.getContent().stream().filter(booking -> isPending(booking.getBookingState()))
                .filter(row->!java.util.Set.of("DELIVERED","FAILED").contains(states.getOrDefault(row.getId(),"CREATED"))).count();
        return new BookingHistoryResponse(result.map(row->item(row,states.get(row.getId()))).getContent(),
                new BookingHistorySummary(all.getTotalElements(), allPending, true,
                        states.values().stream().filter(state->java.util.Set.of("PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY").contains(state)).count(),
                        states.values().stream().filter("DELIVERED"::equals).count()), result.getNumber(), result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    public BookingDetailsResponse detail(UUID bookingId) {
        var customer = identities.requireCurrentRole(IdentityRole.CUSTOMER);
        var booking = bookings.findById(bookingId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!customer.userId().equals(booking.getCustomerId())) throw new AccessDeniedException("Access denied");
        return new BookingDetailsResponse(item(booking,lifecycles.statuses(java.util.List.of(bookingId)).get(bookingId)),BookingReadService.snapshot(booking));
    }

    private BookingHistoryItem item(ParcelBooking booking,String deliveryStatus) {
        return new BookingHistoryItem(booking.getId(), booking.getTrackingNumber(), booking.getRecipientName(),
                booking.getRecipientPhone(), booking.getPickupCity(), booking.getDeliveryCity(), booking.getCreatedAt(),
                booking.getQuotedAmountMinor(), booking.getCurrencyCode(), booking.getBookingState(),deliveryStatus);
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
