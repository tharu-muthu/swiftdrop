package com.swiftdrop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.service.merchant.MerchantApiKeyAuthenticator;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MerchantApiKeyFilterTest {
    final MerchantApiKeyAuthenticator authenticator = mock(MerchantApiKeyAuthenticator.class);
    final MerchantApiKeyFilter filter =
            new MerchantApiKeyFilter(authenticator, new AuthRateLimiter(Clock.systemUTC()), new ObjectMapper());
    final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    MockHttpServletRequest request(String key) {
        var request = new MockHttpServletRequest("GET", "/api/v1/merchant/verify");
        request.setRemoteAddr("203.0.113.7");
        if (key != null) {
            request.addHeader("X-API-Key", key);
        }
        return request;
    }

    @Test
    void missingKeyGets401AndTheChainIsNotInvoked() throws Exception {
        when(authenticator.authenticate(any())).thenThrow(new InvalidApiKeyException());
        var response = new MockHttpServletResponse();

        filter.doFilter(request(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_API_KEY");
        verifyNoInteractions(chain);
    }

    @Test
    void validKeyContinuesTheChainWithScopeAuthorities() throws Exception {
        var principal = new MerchantApiPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("quote", "order"));
        when(authenticator.authenticate("sdk_key")).thenReturn(principal);
        var response = new MockHttpServletResponse();
        var request = request("sdk_key");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())).containsExactlyInAnyOrder("SCOPE_quote", "SCOPE_order");
    }

    @Test
    void unapprovedBusinessGets403() throws Exception {
        when(authenticator.authenticate(any())).thenThrow(new MerchantNotApprovedException());
        var response = new MockHttpServletResponse();

        filter.doFilter(request("sdk_key"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("MERCHANT_NOT_APPROVED");
        verifyNoInteractions(chain);
    }

    @Test
    void repeatedFailuresFromOneAddressAreThrottled() throws Exception {
        when(authenticator.authenticate(any())).thenThrow(new InvalidApiKeyException());

        for (int attempt = 1; attempt <= 10; attempt++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("sdk_bad"), response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
        }
        var throttled = new MockHttpServletResponse();
        filter.doFilter(request("sdk_bad"), throttled, chain);

        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isNotNull();
    }
}