package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.ParcelLifecycle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ParcelLifecycleRepository extends JpaRepository<ParcelLifecycle, UUID> {
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
    List<ParcelLifecycle> findAllByAssignedCourierIdOrderByUpdatedAtDesc(UUID courierId);
}
