package com.swiftdrop.service.identity;

import com.swiftdrop.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdentityContractErrorsTest {
    @RestController static class FixtureController {
        @GetMapping("/fixture/missing") String missing() { throw new AuthenticationCredentialsNotFoundException("fixture-private-detail"); }
        @GetMapping("/fixture/denied") String denied() { throw new AccessDeniedException("fixture-private-detail"); }
    }
    @Test void serviceErrorsHaveSafeAuthenticationAndPermissionResponses() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/fixture/missing")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/fixture/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-private-detail"))));
    }
}
