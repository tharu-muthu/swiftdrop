package com.swiftdrop.repository.booking;

import com.swiftdrop.entity.booking.ParcelBooking;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelBookingRepository extends JpaRepository<ParcelBooking, UUID> {
    interface DashboardAggregate {
        long getActiveBookings();
        long getPendingReceipts();
        long getThisMonthShipments();
    }

    @Query(value = """
            SELECT count(*) FILTER (WHERE booking_state IN ('CREATED', 'CONFIRMED')) AS "activeBookings",
                   count(*) FILTER (WHERE receipt_state = 'PENDING') AS "pendingReceipts",
                   count(*) FILTER (WHERE created_at >= date_trunc('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')) AS "thisMonthShipments"
            FROM swiftdrop.parcel_bookings
            WHERE customer_id = :customerId
            """, nativeQuery = true)
    DashboardAggregate dashboardForCustomer(@Param("customerId") UUID customerId);

    @Query("""
            SELECT b FROM ParcelBooking b
            WHERE b.customerId = :customerId
              AND (:search IS NULL OR lower(b.trackingNumber) LIKE lower(concat('%', :search, '%'))
                   OR lower(coalesce(b.recipientName, '')) LIKE lower(concat('%', :search, '%')))
              AND (:bookingState IS NULL OR b.bookingState = :bookingState)
              AND (:from IS NULL OR b.createdAt >= :from)
              AND (:until IS NULL OR b.createdAt < :until)
            """)
    Page<ParcelBooking> findHistory(@Param("customerId") UUID customerId, @Param("search") String search,
            @Param("bookingState") String bookingState, @Param("from") Instant from,
            @Param("until") Instant until, Pageable pageable);
}
