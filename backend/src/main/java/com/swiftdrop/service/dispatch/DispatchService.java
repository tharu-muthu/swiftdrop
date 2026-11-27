package com.swiftdrop.service.dispatch;

import com.swiftdrop.dto.dispatch.AssignmentResponse;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.exception.LifecycleConflictException;
import com.swiftdrop.repository.dispatch.DispatchRepository;
import com.swiftdrop.service.booking.BookingReadService;
import com.swiftdrop.service.execution.ParcelLifecycleService;
import com.swiftdrop.service.identity.IdentityAccountService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchService {
    private static final Set<String> VEHICLE_TYPES = Set.of("BICYCLE", "E_BICYCLE", "MOTORCYCLE", "SCOOTER", "THREE_WHEELER", "CAR", "VAN", "PICKUP_TRUCK", "LIGHT_TRUCK");
    private final DispatchRepository dispatch; private final BookingReadService bookings;
    private final ParcelLifecycleService execution; private final IdentityAccountService identities;
    public DispatchService(DispatchRepository dispatch,BookingReadService bookings,ParcelLifecycleService execution,IdentityAccountService identities) {
        this.dispatch=dispatch; this.bookings=bookings; this.execution=execution; this.identities=identities;
    }
    @Transactional
    public AssignmentResponse assign(UUID bookingId,UUID courierId,String reason,boolean reassign) {
        var actor=identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        dispatch.lockBooking(bookingId);
        var booking=bookings.find(bookingId).orElseThrow(()->new IllegalArgumentException("Booking unavailable"));
        if("CANCELLED".equals(booking.bookingState())) throw new LifecycleConflictException("Cancelled booking cannot be assigned");
        identities.requireActiveCourier(courierId);
        if(!dispatch.active(courierId)) throw new LifecycleConflictException("Courier operational profile is inactive");
        var existing=dispatch.assigned(bookingId);
        if(reassign) {
            if(existing.isEmpty() || existing.get().equals(courierId)) throw new LifecycleConflictException("Assignment changed. Reload before reassigning.");
            if(reason==null||reason.isBlank()||reason.length()>200) throw new IllegalArgumentException("A reassignment reason is required");
        } else if(existing.isPresent()) throw new LifecycleConflictException("Booking already assigned");
        // M6 locks the canonical row; courier status changes and reassignment serialize here.
        execution.initialize(bookingId,courierId,actor.userId());
        if(reassign) dispatch.revoke(bookingId,reason.trim());
        return new AssignmentResponse(dispatch.assign(bookingId,courierId,actor.userId()),bookingId,courierId);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> couriers(boolean activeOnly) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        var profiles=dispatch.couriers();
        var ids=profiles.stream().map(row->(UUID)row.get("courierId")).toList();
        var accounts=identities.eligibleCourierIds(ids);
        var labels=identities.accountLabels(ids);
        return profiles.stream().map(profile->{
            Map<String,Object> row=new LinkedHashMap<>(profile);
            var label=labels.get((UUID)profile.get("courierId"));
            row.put("fullName",label == null ? "Account unavailable" : label.fullName());
            row.put("email",label == null ? null : label.email());
            row.put("accountEligible",accounts.contains((UUID)profile.get("courierId")));
            boolean operational=Boolean.TRUE.equals(profile.get("active"));
            boolean eligible=operational&&accounts.contains((UUID)profile.get("courierId"));
            row.put("eligible",eligible);
            row.put("unavailableReason",eligible?null:operational?"ACCOUNT_UNAVAILABLE":"PROFILE_INACTIVE");
            return row;
        }).filter(row->!activeOnly||Boolean.TRUE.equals(row.get("eligible"))).toList();
    }
    @Transactional(readOnly=true) public long workload(UUID id) { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); return dispatch.workload(id); }
    @Transactional public void profile(UUID id,String vehicle,String plate,boolean active) {
        identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); identities.requireActiveCourier(id);
        String selected = vehicle == null ? "" : vehicle.trim();
        // Existing free-text profiles remain editable; every new selection uses a stable code.
        if (!VEHICLE_TYPES.contains(selected) && !dispatch.vehicleType(id).filter(selected::equals).isPresent())
            throw new IllegalArgumentException("Select a supported vehicle type");
        dispatch.profile(id,selected,plate,active);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> history(UUID id) { identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN); return dispatch.assignmentHistory(id); }
    @Transactional public void completed(UUID booking) { dispatch.revoke(booking,"Delivery completed"); }
}
