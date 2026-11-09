package com.swiftdrop.security;

import com.swiftdrop.controller.booking.BookingHistoryController;
import com.swiftdrop.dto.booking.BookingHistoryResponse;
import com.swiftdrop.dto.booking.BookingHistorySummary;
import com.swiftdrop.service.booking.BookingHistoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BookingHistoryController.class,
        properties = "swiftdrop.google.enabled=false",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, com.swiftdrop.config.JwtConfig.class, AuthRateLimiter.class})
class BookingHistorySecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingHistoryService history;

    @MockitoBean
    private IdentityJwtConverter converter;

    @Test
    void bookingHistoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void authenticatedCustomerCanReadTheirBookingHistory() throws Exception {
        when(history.history(isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new BookingHistoryResponse(List.of(), new BookingHistorySummary(0, 0, false), 0, 10, 0, 0));

        mvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalBookings").value(0));
    }
}
