package com.swiftdrop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.security.IdentityJwtConverter;
import com.swiftdrop.service.identity.IdentityRoleManagement;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"swiftdrop.google.enabled=false", "swiftdrop.events.enabled=false"})
class RbacAuthorizationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl); p.add("spring.datasource.username", POSTGRES::getUsername); p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.flyway.url", POSTGRES::getJdbcUrl); p.add("spring.flyway.user", POSTGRES::getUsername); p.add("spring.flyway.password", POSTGRES::getPassword);
        p.add("spring.rabbitmq.password", () -> "unused-test-only");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired IdentityRoleManagement roles;
    @Autowired IdentityJwtConverter converter;
    @Autowired JwtDecoder decoder;
    static final String PASSWORD = "RBAC fixture password 12";
    record Account(UUID id, String email, String token) { }
    record Task(UUID booking, UUID lifecycle, String tracking) { }
    Account account(String... memberships) throws Exception {
        UUID id = UUID.randomUUID(); String email = id + "@example.test";
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values(?,?,?,?,current_timestamp)", id, email, "RBAC fixture", passwords.encode(PASSWORD));
        for (String role : memberships) sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name=?", id, role);
        return new Account(id, email, login(email));
    }
    String login(String email) throws Exception {
        return body(call(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null), 200).get("accessToken").asText();
    }
    HttpHeaders headers(String token) {
        var response = http.getForEntity("/api/v1/auth/csrf", CsrfResponse.class);
        var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.setOrigin("http://localhost:5173");
        h.set(response.getBody().headerName(), response.getBody().token());
        h.set(HttpHeaders.COOKIE, response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        if (token != null) h.setBearerAuth(token);
        return h;
    }
    ResponseEntity<String> call(HttpMethod method, String path, Object data, String token) {
        return http.exchange(path, method, new HttpEntity<>(data, headers(token)), String.class);
    }
    ResponseEntity<String> get(String path, String token) { return call(HttpMethod.GET, path, null, token); }
    JsonNode body(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        return response.getBody() == null ? json.nullNode() : json.readTree(response.getBody());
    }
    Task task(Account customer, Account courier, Account admin, String status, Instant assigned, Instant activity) {
        UUID booking = UUID.randomUUID(), lifecycle = UUID.randomUUID();
        String tracking = "SDP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        sql.update("insert into swiftdrop.parcel_bookings(id,customer_id,tracking_number,pickup_address_snapshot,dropoff_address_snapshot,parcel_description,parcel_weight_grams,recipient_name,pickup_city,delivery_city,receipt_state) values(?,?,?,'Private pickup','Private delivery','Test parcel',1000,'Test recipient','Colombo','Kandy',?)",
                booking, customer.id(), tracking, status.equals("DELIVERED") ? "ISSUED" : "PENDING");
        sql.update("insert into swiftdrop.parcel_lifecycles(id,booking_id,assigned_courier_id,status) values(?,?,?,?)", lifecycle, booking, courier.id(), status);
        boolean terminal = Set.of("DELIVERED", "FAILED").contains(status);
        sql.update("insert into swiftdrop.parcel_assignments(booking_id,courier_id,assigned_by,assigned_at,is_active,revoked_at) values(?,?,?,?,?,?)", booking, courier.id(), admin.id(), Timestamp.from(assigned), !terminal, terminal ? Timestamp.from(activity) : null);
        sql.update("insert into swiftdrop.parcel_status_history(lifecycle_id,sequence_no,new_status,actor_user_id,occurred_at,failure_reason) values(?,1,?,?,?,?)", lifecycle, status, courier.id(), Timestamp.from(activity), status.equals("FAILED") ? "Test failure" : null);
        return new Task(booking, lifecycle, tracking);
    }
    List<String> taskIds(JsonNode rows) {
        var ids = new ArrayList<String>(); rows.forEach(row -> ids.add(row.get("lifecycleId").asText())); return ids;
    }

    @Test void roleOnlyAccountsCannotUseAnotherRolesFunctions() throws Exception {
        var customer = account("CUSTOMER"); var courier = account("COURIER");
        var merchant = account("MERCHANT"); var admin = account("DISPATCHER_ADMIN");
        var allowed = Map.of(customer, List.of("/bookings", "/bookings/dashboard"), courier, List.of("/courier/deliveries"), admin, List.of("/admin/users", "/admin/zones", "/dispatch/bookings"));
        for (var entry : allowed.entrySet()) for (String path : entry.getValue()) body(get("/api/v1" + path, entry.getKey().token()), 200);
        var paths = Map.of("CUSTOMER", "/bookings", "COURIER", "/courier/deliveries", "MERCHANT", "/merchant/api-keys", "DISPATCHER_ADMIN", "/admin/users");
        var accounts = Map.of("CUSTOMER", customer, "COURIER", courier, "MERCHANT", merchant, "DISPATCHER_ADMIN", admin);
        for (var entry : accounts.entrySet()) for (var endpoint : paths.entrySet()) {
            if (!entry.getKey().equals(endpoint.getKey())) assertThat(get("/api/v1" + endpoint.getValue(), entry.getValue().token()).getStatusCode().value()).isEqualTo(403);
        }
        var noCsrf = new HttpHeaders(); noCsrf.setBearerAuth(admin.token()); noCsrf.setContentType(MediaType.APPLICATION_JSON); noCsrf.setOrigin("http://localhost:5173");
        var grant = Map.of("role", "COURIER", "reason", "Missing CSRF", "expectedVersion", 0);
        assertThat(http.exchange("/api/v1/admin/users/" + customer.id() + "/roles/grant", HttpMethod.POST, new HttpEntity<>(grant, noCsrf), String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id where ur.user_id=? and r.name='COURIER'", Long.class, customer.id())).isZero();
        assertThat(call(HttpMethod.POST, "/api/v1/quotes", Map.of(), courier.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/v1/admin/zones", Map.of(), customer.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/v1/admin/users/" + customer.id() + "/roles/grant", Map.of("role", "COURIER", "reason", "Untrusted grant", "expectedVersion", 0), customer.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/v1/admin/users/" + customer.id() + "/operational-roles", Map.of("role", "COURIER", "reason", "Missing required fields"), admin.token()).getStatusCode().value()).isEqualTo(400);
    }

    @Test void courierWorkdayUsesColomboBoundariesAndCarriesOverOnlyOwnedWork() throws Exception {
        var owner = account("CUSTOMER"); var otherOwner = account("CUSTOMER");
        var courier = account("COURIER"); var otherCourier = account("COURIER"); var admin = account("DISPATCHER_ADMIN");
        var day = LocalDate.now(ZoneId.of("Asia/Colombo")); var start = day.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();
        var carry = task(owner, courier, admin, "IN_TRANSIT", start.minusSeconds(172800), start.minusSeconds(86400));
        var yesterday = task(owner, courier, admin, "DELIVERED", start.minusSeconds(172800), start.minusSeconds(1));
        var today = task(owner, courier, admin, "DELIVERED", start.minusSeconds(172800), start);
        var assignedToday = task(owner, courier, admin, "CREATED", start, start);
        var foreign = task(otherOwner, otherCourier, admin, "CREATED", start, start);
        var rows = taskIds(body(get("/api/v1/courier/deliveries?date=" + day, courier.token()), 200));
        assertThat(rows).contains(carry.lifecycle().toString(), today.lifecycle().toString(), assignedToday.lifecycle().toString())
                .doesNotContain(yesterday.lifecycle().toString(), foreign.lifecycle().toString());
        assertThat(taskIds(body(get("/api/v1/courier/deliveries?date=" + day.minusDays(1), courier.token()), 200))).contains(yesterday.lifecycle().toString()).doesNotContain(today.lifecycle().toString());
        assertThat(get("/api/v1/courier/deliveries?date=" + day.plusDays(1), courier.token()).getStatusCode().value()).isEqualTo(400);
        body(get("/api/v1/courier/deliveries/" + carry.lifecycle(), courier.token()), 200);
        assertThat(get("/api/v1/courier/deliveries/" + foreign.lifecycle(), courier.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/v1/courier/deliveries/" + foreign.lifecycle() + "/status", Map.of("expectedStatus", "CREATED", "status", "PICKED_UP"), courier.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/v1/bookings/" + carry.booking(), otherOwner.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/v1/bookings/" + today.booking() + "/receipt", otherOwner.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/v1/tracking/customer/" + carry.tracking(), otherOwner.token()).getStatusCode().value()).isEqualTo(404);
        var publicView = body(get("/api/v1/tracking/" + carry.tracking(), null), 200).toString();
        assertThat(publicView).doesNotContain("Private pickup", "Private delivery", "Test recipient", "customerId", "actorUserId");
        assertThat(call(HttpMethod.POST, "/api/v1/dispatch/assignments/reassign", Map.of("bookingId", carry.booking(), "newCourierId", otherCourier.id(), "reason", "Late reassignment"), admin.token()).getStatusCode().value()).isEqualTo(409); // Operational profile is required before assignment.
        body(call(HttpMethod.PUT, "/api/v1/dispatch/couriers/" + otherCourier.id(), Map.of("vehicleType", "VAN", "active", true), admin.token()), 200);
        assertThat(call(HttpMethod.POST, "/api/v1/dispatch/assignments/reassign", Map.of("bookingId", carry.booking(), "newCourierId", otherCourier.id(), "reason", "Late reassignment"), admin.token()).getStatusCode().value()).isEqualTo(409);
        body(call(HttpMethod.POST, "/api/v1/dispatch/assignments/reassign", Map.of("bookingId", assignedToday.booking(), "newCourierId", otherCourier.id(), "reason", "Before pickup"), admin.token()), 200);
        assertThat(get("/api/v1/courier/deliveries/" + assignedToday.lifecycle(), courier.token()).getStatusCode().value()).isEqualTo(403);
        body(get("/api/v1/courier/deliveries/" + assignedToday.lifecycle(), otherCourier.token()), 200);
        assertThat(call(HttpMethod.POST, "/api/v1/courier/deliveries/" + assignedToday.lifecycle() + "/status", Map.of("expectedStatus", "CREATED", "status", "PICKED_UP"), courier.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(taskIds(body(get("/api/v1/courier/deliveries", courier.token()), 200))).doesNotContain(assignedToday.lifecycle().toString());
        assertThat(call(HttpMethod.POST, "/api/v1/courier/deliveries/" + assignedToday.lifecycle() + "/status", Map.of("expectedStatus", "CREATED", "status", "PICKED_UP", "courierId", courier.id()), otherCourier.token()).getStatusCode().value()).isEqualTo(400);
    }

    @Test void removingRolesInvalidatesSessionsAndMerchantKeysStopDespiteAnApprovedProfile() throws Exception {
        var admin = account("DISPATCHER_ADMIN"); var courier = account("CUSTOMER", "COURIER");
        body(call(HttpMethod.POST, "/api/v1/admin/users/" + courier.id() + "/roles/revoke", Map.of("role", "COURIER", "expectedVersion", 0, "reason", "Access review"), admin.token()), 200);
        assertThat(get("/api/v1/courier/deliveries", courier.token()).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/v1/courier/deliveries", login(courier.email())).getStatusCode().value()).isEqualTo(403);
        var owner = account("CUSTOMER", "MERCHANT"); UUID business = UUID.randomUUID();
        sql.update("insert into swiftdrop.merchants(id,owner_user_id,business_name,contact_email,approval_status) values(?,?,'Test shop',?,'APPROVED')", business, owner.id(), owner.email());
        String key = body(call(HttpMethod.POST, "/api/v1/merchant/api-keys", null, owner.token()), 201).get("apiKey").asText();
        var keyHeaders = new HttpHeaders(); keyHeaders.set("X-API-Key", key);
        assertThat(http.exchange("/api/v1/merchant/verify", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class).getStatusCode().value()).isEqualTo(200);
        try {
            SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(admin.token())));
            roles.revokeMerchantForOwner(owner.id(), "Business access withdrawn");
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(get("/api/v1/merchant/api-keys", owner.token()).getStatusCode().value()).isEqualTo(401);
        String fresh = login(owner.email());
        assertThat(call(HttpMethod.POST, "/api/v1/merchant/api-keys", null, fresh).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/v1/merchant/webhooks", fresh).getStatusCode().value()).isEqualTo(403);
        assertThat(http.exchange("/api/v1/merchant/verify", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(sql.queryForObject("select approval_status from swiftdrop.merchants where id=?", String.class, business)).isEqualTo("APPROVED");
    }
}
