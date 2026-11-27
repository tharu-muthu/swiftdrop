package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.ParcelLifecycle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ParcelLifecycleRepository extends JpaRepository<ParcelLifecycle, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ParcelLifecycle> {
    interface CustomerCounts {
        long getTotal();
        long getPending();
        long getInTransit();
        long getDelivered();
    }
    // Read-only reporting join: booking owns the customer/state; M6 owns delivery status.
    @org.springframework.data.jpa.repository.Query(value = """
            select count(*) as total,
              count(*) filter (where b.booking_state in ('CREATED','CONFIRMED')
                and coalesce(l.status,'CREATED') not in ('DELIVERED','FAILED')) as pending,
              count(*) filter (where l.status in ('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY')) as inTransit,
              count(*) filter (where l.status='DELIVERED') as delivered
            from swiftdrop.parcel_bookings b
            left join swiftdrop.parcel_lifecycles l on l.booking_id=b.id
            where b.customer_id=:customerId
            """, nativeQuery = true)
    CustomerCounts customerCounts(@org.springframework.data.repository.query.Param("customerId") UUID customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParcelLifecycle> findByIdAndAssignedCourierId(UUID id, UUID courierId);
    Optional<ParcelLifecycle> findFirstByIdAndAssignedCourierId(UUID id, UUID courierId);
    boolean existsByBookingId(UUID bookingId);
    Optional<ParcelLifecycle> findByBookingId(UUID bookingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ParcelLifecycle p where p.bookingId=:bookingId")
    Optional<ParcelLifecycle> lockByBookingId(@org.springframework.data.repository.query.Param("bookingId") UUID bookingId);
    List<ParcelLifecycle> findTop100ByOrderByUpdatedAtDesc();
    List<ParcelLifecycle> findByBookingIdIn(java.util.Collection<UUID> ids);
    @org.springframework.data.jpa.repository.Query("select count(p) from ParcelLifecycle p where p.status not in (com.swiftdrop.entity.execution.ParcelStatus.DELIVERED,com.swiftdrop.entity.execution.ParcelStatus.FAILED) and p.updatedAt<:cutoff")
    long overdue(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
    org.springframework.data.domain.Page<ParcelLifecycle> findByAssignedCourierIdIsNullAndStatus(com.swiftdrop.entity.execution.ParcelStatus status,org.springframework.data.domain.Pageable pageable);
    default org.springframework.data.domain.Page<ParcelLifecycle> findDispatchBoard(boolean unassigned,java.time.Instant overdueBefore,org.springframework.data.domain.Pageable pageable) {
        return findAll((root,query,cb)->{
            var filters=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if(unassigned) {
                filters.add(cb.isNull(root.get("assignedCourierId")));
                filters.add(cb.equal(root.get("status"),com.swiftdrop.entity.execution.ParcelStatus.CREATED));
            }
            if(overdueBefore!=null) {
                filters.add(cb.not(root.get("status").in(com.swiftdrop.entity.execution.ParcelStatus.DELIVERED,com.swiftdrop.entity.execution.ParcelStatus.FAILED)));
                filters.add(cb.lessThan(root.<java.time.Instant>get("updatedAt"),overdueBefore));
            }
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },pageable);
    }
    List<ParcelLifecycle> findAllByAssignedCourierIdOrderByUpdatedAtDesc(UUID courierId);
    default List<ParcelLifecycle> findCourierWorkday(UUID courierId, List<UUID> assignedBookingIds,
            java.time.Instant start, java.time.Instant end, boolean includeCarryover) {
        return findAll((root, query, cb) -> {
            var activity = query.subquery(Integer.class);
            var history = activity.from(com.swiftdrop.entity.execution.ParcelStatusHistory.class);
            activity.select(cb.literal(1)).where(cb.equal(history.get("lifecycleId"), root.get("id")),
                    cb.equal(history.get("actorUserId"), courierId),
                    cb.greaterThanOrEqualTo(history.<java.time.Instant>get("occurredAt"), start),
                    cb.lessThan(history.<java.time.Instant>get("occurredAt"), end));
            var work = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!assignedBookingIds.isEmpty()) work.add(root.get("bookingId").in(assignedBookingIds));
            work.add(cb.exists(activity));
            if (includeCarryover) work.add(cb.not(root.get("status").in(
                    com.swiftdrop.entity.execution.ParcelStatus.DELIVERED, com.swiftdrop.entity.execution.ParcelStatus.FAILED)));
            return cb.and(cb.equal(root.get("assignedCourierId"), courierId),
                    cb.or(work.toArray(jakarta.persistence.criteria.Predicate[]::new)));
        }, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt", "id"));
    }
}
