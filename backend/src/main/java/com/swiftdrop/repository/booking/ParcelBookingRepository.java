package com.swiftdrop.repository.booking;

import com.swiftdrop.entity.booking.ParcelBooking;
import java.util.UUID;
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
}
