package com.swiftdrop.service.execution;

import com.swiftdrop.dto.execution.*;
import com.swiftdrop.repository.execution.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class LifecycleReadService {
    private final ParcelLifecycleRepository lifecycles; private final ParcelStatusHistoryRepository history;
    public LifecycleReadService(ParcelLifecycleRepository lifecycles,ParcelStatusHistoryRepository history) { this.lifecycles=lifecycles; this.history=history; }
    public Optional<LifecycleSnapshot> find(UUID booking) {
        return lifecycles.findByBookingId(booking).map(row->new LifecycleSnapshot(row.getId(),booking,row.getAssignedCourierId(),row.getStatus(),row.getUpdatedAt(),
                history.findAllByLifecycleIdOrderBySequenceNoAsc(row.getId()).stream().map(item->new StatusHistoryItem(item.getSequenceNo(),item.getPreviousStatus(),item.getNewStatus(),item.getActorUserId(),item.getFailureReason(),item.getOccurredAt())).toList()));
    }
    public List<LifecycleSnapshot> recent() { return lifecycles.findTop100ByOrderByUpdatedAtDesc().stream().map(row->new LifecycleSnapshot(row.getId(),row.getBookingId(),row.getAssignedCourierId(),row.getStatus(),row.getUpdatedAt(),List.of())).toList(); }
    public List<java.util.Map<String,Object>> dailyReport(java.time.LocalDate day) {
        var counts=history.dailyCounts(day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),day.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        return counts.stream().map(row->java.util.Map.<String,Object>of("courierId",row[0],"status",row[1],"count",row[2])).toList();
    }
    public Map<UUID,String> statuses(java.util.Collection<UUID> bookingIds) {
        if(bookingIds.isEmpty())return Map.of();
        return lifecycles.findByBookingIdIn(bookingIds).stream().collect(java.util.stream.Collectors.toMap(row->row.getBookingId(),row->row.getStatus().name()));
    }
    public org.springframework.data.domain.Page<LifecycleSnapshot> board(int page,int size,boolean unassigned,java.time.Instant overdueBefore) {
        var direction = overdueBefore == null ? org.springframework.data.domain.Sort.Direction.DESC : org.springframework.data.domain.Sort.Direction.ASC;
        var pageable=org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(direction,"updatedAt","id"));
        var result=lifecycles.findDispatchBoard(unassigned,overdueBefore,pageable);
        return result.map(row->new LifecycleSnapshot(row.getId(),row.getBookingId(),row.getAssignedCourierId(),row.getStatus(),row.getUpdatedAt(),List.of()));
    }
    public org.springframework.data.domain.Page<LifecycleSnapshot> board(int page,int size,boolean unassigned) {
        var pageable=org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"updatedAt"));
        var result=unassigned?lifecycles.findByAssignedCourierIdIsNullAndStatus(com.swiftdrop.entity.execution.ParcelStatus.CREATED,pageable):lifecycles.findAll(pageable);
        return result.map(row->new LifecycleSnapshot(row.getId(),row.getBookingId(),row.getAssignedCourierId(),row.getStatus(),row.getUpdatedAt(),List.of()));
    }
        return result.map(row->new LifecycleSnapshot(row.getId(),row.getBookingId(),row.getAssignedCourierId(),row.getStatus(),row.getUpdatedAt(),List.of()));
    }
}
