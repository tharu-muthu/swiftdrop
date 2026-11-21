package com.swiftdrop.controller.dispatch;

import com.swiftdrop.dto.dispatch.*;
import com.swiftdrop.service.dispatch.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {
    private final DispatchService dispatch; private final DispatchQueryService queries;
    public DispatchController(DispatchService dispatch,DispatchQueryService queries) { this.dispatch=dispatch; this.queries=queries; }
    @GetMapping("/couriers/active") public List<Map<String,Object>> active() { return dispatch.couriers(true); }
    @GetMapping("/couriers") public List<Map<String,Object>> couriers() { return dispatch.couriers(false); }
    @GetMapping("/couriers/{id}/workload") public long workload(@PathVariable UUID id) { return dispatch.workload(id); }
    public record CourierProfileRequest(@NotBlank @Size(max=50) String vehicleType,@Size(max=20) String licensePlate,boolean active) { }
    @PutMapping("/couriers/{id}") public void profile(@PathVariable UUID id,@Valid @RequestBody CourierProfileRequest request) { dispatch.profile(id,request.vehicleType(),request.licensePlate(),request.active()); }
    @PostMapping("/assignments") public AssignmentResponse assign(@Valid @RequestBody AssignmentRequest request) { return dispatch.assign(request.bookingId(),request.courierId(),null,false); }
    @PostMapping("/assignments/reassign") public AssignmentResponse reassign(@Valid @RequestBody ReassignmentRequest request) { return dispatch.assign(request.bookingId(),request.newCourierId(),request.reason(),true); }
    @GetMapping("/bookings/{id}/assignments") public List<Map<String,Object>> history(@PathVariable UUID id) { return dispatch.history(id); }
    @GetMapping("/bookings/{id}") public Map<String,Object> detail(@PathVariable UUID id) { return queries.detail(id); }
    @GetMapping("/bookings") public Map<String,Object> board(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="false") boolean unassigned,@RequestParam(defaultValue="false") boolean overdue) { return queries.board(page,size,unassigned,overdue); }
    @GetMapping("/reports/daily") public Map<String,Object> report(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { return queries.daily(date); }
}
