package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.ParcelStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {
    List<ParcelStatusHistory> findAllByLifecycleIdOrderBySequenceNoAsc(UUID lifecycleId);
}
