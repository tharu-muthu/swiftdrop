package com.swiftdrop.controller.dispatch;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.dto.dispatch.AssignmentRequest;
import com.swiftdrop.dto.dispatch.AssignmentResponse;
import com.swiftdrop.dto.dispatch.CourierSummaryDto;
import com.swiftdrop.dto.dispatch.ReassignmentRequest;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.entity.execution.ParcelAssignment;
import com.swiftdrop.service.execution.CourierAssignmentService;
import com.swiftdrop.service.identity.IdentityAccountService;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final CourierAssignmentService assignmentService;
    private final IdentityAccountService identityAccountService;

    public DispatchController(CourierAssignmentService assignmentService, IdentityAccountService identityAccountService) {
        this.assignmentService = assignmentService;
        this.identityAccountService = identityAccountService;
    }

    @GetMapping("/couriers/active")
    public ResponseEntity<List<CourierSummaryDto>> getActiveCouriers() {
        List<CourierSummaryDto> activeCouriers = assignmentService.getActiveCouriers().stream()
                .map(profile -> new CourierSummaryDto(
                        profile.getUserId(),
                        profile.getVehicleType(),
                        profile.getLicensePlate(),
                        profile.isActive()))
                .toList();
                
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(activeCouriers);
    }

    @GetMapping("/couriers/{courierId}/workload")
    public ResponseEntity<Long> getCourierWorkload(@PathVariable UUID courierId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(assignmentService.getCourierWorkload(courierId));
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> assignCourier(@Valid @RequestBody AssignmentRequest request) {
        var dispatcher = identityAccountService.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        
        ParcelAssignment assignment = assignmentService.assignCourier(
                request.bookingId(), 
                request.courierId(), 
                dispatcher.userId()
        );
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AssignmentResponse(
                        assignment.getId(), 
                        assignment.getBooking().getId(), 
                        assignment.getCourier().getId()
                ));
    }

    @PostMapping("/assignments/reassign")
    public ResponseEntity<AssignmentResponse> reassignCourier(@Valid @RequestBody ReassignmentRequest request) {
        var dispatcher = identityAccountService.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN);
        
        ParcelAssignment assignment = assignmentService.reassignCourier(
                request.bookingId(),
                request.newCourierId(),
                dispatcher.userId(),
                request.reason()
        );
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AssignmentResponse(
                        assignment.getId(), 
                        assignment.getBooking().getId(), 
                        assignment.getCourier().getId()
                ));
    }
}
