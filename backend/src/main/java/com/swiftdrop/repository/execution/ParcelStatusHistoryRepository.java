package com.swiftdrop.repository.execution;

import com.swiftdrop.entity.execution.ParcelStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {
    @org.springframework.data.jpa.repository.Query("select h.actorUserId,h.newStatus,count(h) from ParcelStatusHistory h where h.occurredAt>=:start and h.occurredAt<:end and h.newStatus in (com.swiftdrop.entity.execution.ParcelStatus.DELIVERED,com.swiftdrop.entity.execution.ParcelStatus.FAILED) group by h.actorUserId,h.newStatus")
    java.util.List<Object[]> dailyCounts(@org.springframework.data.repository.query.Param("start") java.time.Instant start,@org.springframework.data.repository.query.Param("end") java.time.Instant end);
    List<ParcelStatusHistory> findAllByLifecycleIdOrderBySequenceNoAsc(UUID lifecycleId);
}
