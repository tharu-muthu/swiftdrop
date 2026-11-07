package com.swiftdrop.controller.tracking;

import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.service.tracking.TrackingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackingController {
    private final TrackingService tracking;

    public TrackingController(TrackingService tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/api/v1/tracking/{trackingNumber}")
    public ResponseEntity<?> publicTracking(@PathVariable String trackingNumber) {
        return tracking.publicTracking(trackingNumber)
                .<ResponseEntity<?>>map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(response))
                .orElseGet(() -> ResponseEntity.status(404)
                        .cacheControl(CacheControl.noStore())
                        .body(ApiError.of("TRACKING_NOT_FOUND", "Tracking details are unavailable.")));
    }
}
