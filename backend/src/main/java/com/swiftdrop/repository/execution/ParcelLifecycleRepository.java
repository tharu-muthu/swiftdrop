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
    boolean existsByBookingId(UUID bookingId);
    Optional<ParcelLifecycle> findByBookingId(UUID bookingId);
    List<ParcelLifecycle> findAllByAssignedCourierIdOrderByUpdatedAtDesc(UUID courierId);
}
