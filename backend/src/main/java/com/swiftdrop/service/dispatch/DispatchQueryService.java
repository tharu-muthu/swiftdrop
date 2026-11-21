package com.swiftdrop.service.dispatch;

import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.LifecycleReadService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
@Transactional(readOnly=true)
public class DispatchQueryService {
    private final BookingReadService bookings; private final LifecycleReadService lifecycles; private final IdentityAccountService identities; private final DispatchService dispatch;
    private final Clock clock; private final int delayHours;
    public DispatchQueryService(BookingReadService bookings,LifecycleReadService lifecycles,IdentityAccountService identities,DispatchService dispatch,Clock clock,@Value("${swiftdrop.dispatch.delay-hours:24}") int delayHours) {
        this.bookings=bookings; this.lifecycles=lifecycles; this.identities=identities; this.dispatch=dispatch; this.clock=clock; this.delayHours=delayHours;
        if(delayHours<1)throw new IllegalArgumentException("Delay threshold must be positive");
    }
    public Map<String,Object> board(int page,int size,boolean unassigned) {
        admin(); if(page<0||size<1||size>100)throw new IllegalArgumentException("Invalid page");
        var result=lifecycles.board(page,size,unassigned);
        var rows=result.getContent().stream().map(lifecycle->{
            var booking=bookings.find(lifecycle.bookingId()).orElseThrow(); Map<String,Object> row=new LinkedHashMap<>();
            row.put("bookingId",booking.id());row.put("trackingNumber",booking.trackingNumber());row.put("pickupCity",booking.pickupCity());row.put("deliveryCity",booking.deliveryCity());
            row.put("status",lifecycle.status());row.put("courierId",lifecycle.courierId());row.put("updatedAt",lifecycle.updatedAt());return row;
        }).toList();
        return Map.of("items",rows,"page",page,"totalPages",result.getTotalPages(),"totalElements",result.getTotalElements());
    }
    public Map<String,Object> detail(UUID id) { admin();return Map.of("booking",bookings.find(id).orElseThrow(),"lifecycle",lifecycles.find(id).orElseThrow(),"assignments",dispatch.history(id)); }
    public Map<String,Object> daily(LocalDate day) {
        admin(); var date=day==null?LocalDate.now(clock.withZone(ZoneOffset.UTC)):day;
        return Map.of("date",date,"results",lifecycles.dailyReport(date),"delayHours",delayHours,"overdueOpenDeliveries",lifecycles.overdue(clock.instant().minus(Duration.ofHours(delayHours))),"workloads",dispatch.couriers(false));
    }
    private void admin() { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); }
}
