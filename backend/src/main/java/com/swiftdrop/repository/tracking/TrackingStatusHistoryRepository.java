package com.swiftdrop.repository.tracking;

import com.swiftdrop.entity.execution.ParcelStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {
    List<ParcelStatusHistory> findAllByLifecycleIdOrderBySequenceNoAsc(UUID lifecycleId);
}
