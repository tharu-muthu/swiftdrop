package com.swiftdrop.controller.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import com.swiftdrop.dto.tracking.CustomerTrackingResponse;
import com.swiftdrop.service.tracking.TrackingService;
import com.swiftdrop.service.tracking.TrackingSseService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TrackingControllerTest {
    private final TrackingService service = mock(TrackingService.class);
    private final TrackingSseService events = mock(TrackingSseService.class);
    private final TrackingController controller = new TrackingController(service, events);

    @Test
    void returnsNoStoreSuccessResponseWhenTrackingExists() {
        var response = mock(PublicTrackingResponse.class);
        when(service.publicTracking("SD-1001")).thenReturn(Optional.of(response));

        ResponseEntity<?> result = controller.publicTracking("SD-1001");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void returnsSafeNoStoreNotFoundResponseWhenTrackingDoesNotExist() {
        when(service.publicTracking("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.publicTracking("UNKNOWN");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody()).isInstanceOf(com.swiftdrop.exception.ApiError.class);
        var error = (com.swiftdrop.exception.ApiError) result.getBody();
        assertThat(error.code()).isEqualTo("TRACKING_NOT_FOUND");
    }

    @Test
    void returnsCustomerTrackingResponseWhenTrackingBelongsToCustomer() {
        var response = mock(CustomerTrackingResponse.class);
        when(service.customerTracking("SD-1003")).thenReturn(Optional.of(response));

        ResponseEntity<?> result = controller.customerTracking("SD-1003");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody()).isSameAs(response);
    }
}
