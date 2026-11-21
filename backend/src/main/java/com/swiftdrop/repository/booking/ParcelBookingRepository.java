package com.swiftdrop.repository.booking;

import com.swiftdrop.entity.booking.ParcelBooking;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelBookingRepository extends JpaRepository<ParcelBooking, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ParcelBooking> {
    java.util.Optional<ParcelBooking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
    java.util.Optional<ParcelBooking> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
    java.util.Optional<ParcelBooking> findByTrackingNumber(String trackingNumber);
    Page<ParcelBooking> findByMerchantId(UUID merchantId, Pageable pageable);
    java.util.List<ParcelBooking> findTop100ByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    Integer lockRequest(@Param("key") String key);
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

    default Page<ParcelBooking> findHistory(UUID customerId,String search,String bookingState,Instant from,Instant until,Pageable pageable) {
        return findAll((root,query,cb)->{
            var filters=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            filters.add(cb.equal(root.get("customerId"),customerId));
            if(search!=null) {
                String escaped=search.toLowerCase(java.util.Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                filters.add(cb.or(cb.like(cb.lower(root.get("trackingNumber")),"%"+escaped+"%",'\\'),
                        cb.like(cb.lower(root.get("recipientName")),"%"+escaped+"%",'\\')));
            }
            if(bookingState!=null)filters.add(cb.equal(root.get("bookingState"),bookingState));
            if(from!=null)filters.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),from));
            if(until!=null)filters.add(cb.lessThan(root.<Instant>get("createdAt"),until));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },pageable);
    }
}
