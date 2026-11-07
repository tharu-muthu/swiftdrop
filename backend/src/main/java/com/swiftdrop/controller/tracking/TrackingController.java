package com.swiftdrop.controller.tracking;

import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import com.swiftdrop.exception.ApiError;
import com.swiftdrop.service.tracking.TrackingService;
import com.swiftdrop.service.tracking.TrackingSseService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackingController {
    private final TrackingService tracking;
    private final TrackingSseService events;

    public TrackingController(TrackingService tracking, TrackingSseService events) {
        this.tracking = tracking;
        this.events = events;
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

    @GetMapping("/api/v1/tracking/customer/{trackingNumber}")
    public ResponseEntity<?> customerTracking(@PathVariable String trackingNumber) {
        return tracking.customerTracking(trackingNumber)
                .<ResponseEntity<?>>map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(response))
                .orElseGet(() -> ResponseEntity.status(404)
                        .cacheControl(CacheControl.noStore())
                        .body(ApiError.of("TRACKING_NOT_FOUND", "Tracking details are unavailable.")));
    }

    @GetMapping(value = "/api/v1/tracking/{trackingNumber}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> events(@PathVariable String trackingNumber) {
        var normalized = trackingNumber == null ? "" : trackingNumber.trim();
        return tracking.publicTracking(normalized)
                .<ResponseEntity<?>>map(current -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(events.subscribe(normalized, current)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .cacheControl(CacheControl.noStore())
                        .body(ApiError.of("TRACKING_NOT_FOUND", "Tracking details are unavailable.")));
    }
}
