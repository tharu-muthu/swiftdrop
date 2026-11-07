package com.swiftdrop.controller.execution;

import com.swiftdrop.dto.execution.*;
import com.swiftdrop.service.execution.ParcelLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courier/deliveries")
public class CourierExecutionController {
    private final ParcelLifecycleService lifecycle;
    public CourierExecutionController(ParcelLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @GetMapping
    public ResponseEntity<List<CourierDeliverySummary>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lifecycle.assignedDeliveries());
    }

    @GetMapping("/{lifecycleId}")
    public ResponseEntity<CourierDeliveryDetails> details(@PathVariable UUID lifecycleId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lifecycle.details(lifecycleId));
    }

    @PostMapping("/{lifecycleId}/status")
    public ResponseEntity<CourierDeliveryDetails> update(@PathVariable UUID lifecycleId,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lifecycle.update(lifecycleId, request));
    }
}
