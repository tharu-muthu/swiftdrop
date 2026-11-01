package com.swiftdrop.security;

import com.swiftdrop.controller.system.HealthController;
import com.swiftdrop.service.system.HealthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, HealthService.class})
class HealthSecurityTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndDoesNotExposeInfrastructureDetails() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\",\"application\":\"swiftdrop\"}", JsonCompareMode.STRICT))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/private", "/api/v1/health/details", "/login", "/actuator/env"})
    void otherRoutesRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void healthPermissionDoesNotAllowWrites() throws Exception {
        mvc.perform(post("/api/v1/health").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfRemainsEnabled() throws Exception {
        mvc.perform(post("/api/v1/private"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void unimplementedRoutesRemainDeniedEvenForAnAuthenticatedPrincipal() throws Exception {
        mvc.perform(get("/api/v1/private"))
                .andExpect(status().isForbidden());
    }
}
