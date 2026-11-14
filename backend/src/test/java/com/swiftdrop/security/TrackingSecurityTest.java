package com.swiftdrop.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.controller.tracking.TrackingController;
import com.swiftdrop.dto.tracking.PublicTrackingResponse;
import com.swiftdrop.service.tracking.TrackingService;
import com.swiftdrop.service.tracking.TrackingSseService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(controllers = TrackingController.class,
        properties = "swiftdrop.google.enabled=false",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, com.swiftdrop.config.JwtConfig.class, AuthRateLimiter.class})
class TrackingSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TrackingService tracking;

    @MockitoBean
    private TrackingSseService events;

    @MockitoBean
    private IdentityJwtConverter converter;

    @Test
    void publicTrackingDoesNotRequireAuthentication() throws Exception {
        when(tracking.publicTracking("SD-1001")).thenReturn(Optional.of(mock(PublicTrackingResponse.class)));

        mvc.perform(get("/api/v1/tracking/SD-1001"))
                .andExpect(status().isOk());
    }

    @Test
    void customerTrackingRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/tracking/customer/SD-1001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedPrincipalCannotAccessUnimplementedRoutes() throws Exception {
        mvc.perform(get("/api/v1/private").with(user("customer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
