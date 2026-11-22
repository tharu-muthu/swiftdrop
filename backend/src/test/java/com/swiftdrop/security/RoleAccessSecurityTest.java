package com.swiftdrop.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the security boundary independently of DTO validation and business services. */
@WebMvcTest(controllers = RoleAccessSecurityTest.Probe.class, properties = "swiftdrop.google.enabled=false",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, com.swiftdrop.config.JwtConfig.class, AuthRateLimiter.class, RoleAccessSecurityTest.Probe.class})
class RoleAccessSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean IdentityJwtConverter converter;
    @org.springframework.boot.test.context.TestComponent
    @RestController static class Probe {
        @RequestMapping("/api/v1/**") String reachedAuthorizedHandler() { return "authorized"; }
    }
    record Route(String method, String path, Set<String> roles) { }
    static final String ID = "00000000-0000-0000-0000-000000000001";
    static Route route(String method, String path, String... roles) { return new Route(method, "/api/v1" + path.replace("{id}", ID), Set.of(roles)); }
    static List<Route> routes() {
        return List.of(
            route("GET", "/bookings", "CUSTOMER"), route("GET", "/bookings/dashboard", "CUSTOMER"),
            route("GET", "/bookings/{id}", "CUSTOMER"), route("POST", "/bookings", "CUSTOMER"),
            route("GET", "/bookings/{id}/receipt", "CUSTOMER"), route("POST", "/quotes", "CUSTOMER"),
            route("GET", "/quotes/{id}", "CUSTOMER"), route("GET", "/tracking/customer/SDP-ABC12345", "CUSTOMER"),
            route("POST", "/merchant/registration", "CUSTOMER"),
            route("GET", "/merchant/registration", "CUSTOMER", "MERCHANT"), route("PATCH", "/merchant/registration", "CUSTOMER", "MERCHANT"),
            route("GET", "/merchant/dashboard/orders", "MERCHANT"), route("POST", "/merchant/dashboard/orders", "MERCHANT"),
            route("GET", "/merchant/dashboard/orders/{id}", "MERCHANT"), route("POST", "/merchant/dashboard/orders/quote", "MERCHANT"),
            route("GET", "/merchant/api-keys", "MERCHANT"), route("POST", "/merchant/api-keys", "MERCHANT"),
            route("POST", "/merchant/api-keys/{id}/revoke", "MERCHANT"),
            route("GET", "/merchant/webhooks", "MERCHANT"), route("POST", "/merchant/webhooks", "MERCHANT"),
            route("PATCH", "/merchant/webhooks/{id}", "MERCHANT"), route("DELETE", "/merchant/webhooks/{id}", "MERCHANT"),
            route("POST", "/merchant/webhooks/{id}/rotate-secret", "MERCHANT"), route("POST", "/merchant/webhooks/{id}/test", "MERCHANT"),
            route("GET", "/merchant/webhooks/{id}/deliveries", "MERCHANT"),
            route("GET", "/courier/deliveries", "COURIER"), route("GET", "/courier/deliveries/{id}", "COURIER"),
            route("POST", "/courier/deliveries/{id}/status", "COURIER"),
            route("GET", "/admin/users", "DISPATCHER_ADMIN"), route("GET", "/admin/users/{id}", "DISPATCHER_ADMIN"),
            route("GET", "/admin/users/{id}/roles", "DISPATCHER_ADMIN"), route("POST", "/admin/users/{id}/roles/grant", "DISPATCHER_ADMIN"),
            route("POST", "/admin/users/{id}/roles/revoke", "DISPATCHER_ADMIN"), route("POST", "/admin/users/{id}/courier-role", "DISPATCHER_ADMIN"),
            route("POST", "/admin/users/{id}/operational-roles", "DISPATCHER_ADMIN"), route("POST", "/admin/users/{id}/suspend", "DISPATCHER_ADMIN"),
            route("POST", "/admin/users/{id}/reactivate", "DISPATCHER_ADMIN"), route("GET", "/admin/merchants", "DISPATCHER_ADMIN"),
            route("POST", "/admin/merchants/{id}/approve", "DISPATCHER_ADMIN"), route("POST", "/admin/merchants/{id}/reject", "DISPATCHER_ADMIN"),
            route("GET", "/admin/zones", "DISPATCHER_ADMIN"), route("POST", "/admin/zones", "DISPATCHER_ADMIN"),
            route("PATCH", "/admin/zones/{id}", "DISPATCHER_ADMIN"), route("POST", "/admin/zones/{id}/postcodes", "DISPATCHER_ADMIN"),
            route("GET", "/admin/postcodes", "DISPATCHER_ADMIN"), route("DELETE", "/admin/postcodes/90101", "DISPATCHER_ADMIN"),
            route("GET", "/admin/rate-cards", "DISPATCHER_ADMIN"), route("POST", "/admin/rate-cards", "DISPATCHER_ADMIN"),
            route("PATCH", "/admin/rate-cards/{id}", "DISPATCHER_ADMIN"),
            route("GET", "/dispatch/bookings", "DISPATCHER_ADMIN"), route("GET", "/dispatch/bookings/{id}", "DISPATCHER_ADMIN"),
            route("GET", "/dispatch/bookings/{id}/assignments", "DISPATCHER_ADMIN"), route("GET", "/dispatch/couriers", "DISPATCHER_ADMIN"),
            route("GET", "/dispatch/couriers/active", "DISPATCHER_ADMIN"), route("GET", "/dispatch/couriers/{id}/workload", "DISPATCHER_ADMIN"),
            route("PUT", "/dispatch/couriers/{id}", "DISPATCHER_ADMIN"), route("POST", "/dispatch/assignments", "DISPATCHER_ADMIN"),
            route("POST", "/dispatch/assignments/reassign", "DISPATCHER_ADMIN"), route("GET", "/dispatch/reports/daily", "DISPATCHER_ADMIN"),
            route("GET", "/admin/notifications/templates", "DISPATCHER_ADMIN"), route("PUT", "/admin/notifications/templates/DELIVERED", "DISPATCHER_ADMIN"),
            route("GET", "/admin/notifications/jobs", "DISPATCHER_ADMIN"), route("GET", "/admin/notifications/jobs/{id}/attempts", "DISPATCHER_ADMIN"),
            route("POST", "/admin/notifications/jobs/{id}/retry", "DISPATCHER_ADMIN"), route("GET", "/admin/integrations/queues", "DISPATCHER_ADMIN"),
            route("POST", "/admin/integrations/tracking/replay", "DISPATCHER_ADMIN")
        );
    }
    static Stream<Arguments> matrix() {
        return routes().stream().flatMap(route -> Stream.of("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER_ADMIN", "ANONYMOUS")
                .map(role -> Arguments.of(route.method(), route.path(), role, route.roles().contains(role))));
    }
    @ParameterizedTest(name = "{0} {1}: {2} allowed={3}") @MethodSource("matrix")
    void everyRouteRequiresTheAssignedRole(String method, String path, String role, boolean allowed) throws Exception {
        var req = request(HttpMethod.valueOf(method), path).with(csrf()).header("Origin", "http://localhost:5173");
        if (!role.equals("ANONYMOUS")) req.with(user("test").roles(role));
        mvc.perform(req).andExpect(allowed ? status().isOk() : role.equals("ANONYMOUS") ? status().isUnauthorized() : status().isForbidden());
    }
}
