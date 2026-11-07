package com.swiftdrop.repository.tracking;

import com.swiftdrop.entity.execution.ParcelLifecycle;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingLifecycleRepository extends JpaRepository<ParcelLifecycle, UUID> {
    Optional<ParcelLifecycle> findByBookingId(UUID bookingId);
}
