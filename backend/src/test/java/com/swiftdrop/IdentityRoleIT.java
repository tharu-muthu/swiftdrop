package com.swiftdrop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.service.identity.AdminBootstrapService;
import com.swiftdrop.service.identity.IdentityRoleManagement;
import com.swiftdrop.security.IdentityJwtConverter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "swiftdrop.auth.account-login-limit=1000")
class IdentityRoleIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        p.add("spring.flyway.user", POSTGRES::getUsername);
        p.add("spring.flyway.password", POSTGRES::getPassword);
        p.add("spring.rabbitmq.password", () -> "fixture-only");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate sql;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired IdentityRoleManagement roleManagement;
    @Autowired IdentityJwtConverter converter;
    @Autowired JwtDecoder decoder;
    private static final String PASSWORD = "Fixture password 12";

    UUID account(String email) {
        UUID id = UUID.randomUUID();
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values(?,?,?,?,?)",
                id, email, "Role Test", passwords.encode(PASSWORD), Timestamp.from(Instant.now()));
        sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name='CUSTOMER'", id);
        return id;
    }
    HttpHeaders headers(String token) {
        var csrf = http.getForEntity("/api/v1/auth/csrf", CsrfResponse.class);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("http://localhost:5173");
        headers.set(csrf.getBody().headerName(), csrf.getBody().token());
        headers.set(HttpHeaders.COOKIE, csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
    String login(String email) throws Exception {
        var response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", PASSWORD), headers(null)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody()).get("accessToken").asText();
    }
    org.springframework.http.ResponseEntity<String> post(String path, Object body, String token) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), String.class);
    }
    long countRole(UUID userId, String role) {
        return sql.queryForObject("select count(*) from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id where ur.user_id=? and r.name=?",
                Long.class, userId, role);
    }

    @Test void bootstrapAndRoleHandoffsAreAuditedAndRevokeOldSessions() throws Exception {
        String adminEmail = UUID.randomUUID() + "@example.test";
        UUID admin = account(adminEmail);
        String oldAdminToken = login(adminEmail);
        bootstrap.bootstrap(adminEmail);
        assertThat(countRole(admin, "DISPATCHER_ADMIN")).isEqualTo(1);
        assertThat(http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(bearer(oldAdminToken)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='BOOTSTRAP_ADMIN' and actor_kind='SYSTEM'", Long.class, admin)).isEqualTo(1);
        bootstrap.bootstrap(adminEmail); // Explicitly enabled restart cannot promote a second account.
        String adminToken = login(adminEmail);

        String courierEmail = UUID.randomUUID() + "@example.test";
        UUID courier = account(courierEmail);
        String oldCourierToken = login(courierEmail);
        String courierPath = "/api/v1/admin/users/" + courier + "/courier-role";
        String rolesPath = "/api/v1/admin/users/" + courier + "/roles";
        assertThat(http.exchange(rolesPath, HttpMethod.GET, new HttpEntity<>(bearer(oldCourierToken)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var summary = http.exchange(rolesPath, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), String.class);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(summary.getBody()).get("version").asLong()).isZero();
        var request = Map.of("reason", "Approved courier onboarding", "expectedVersion", 0);
        assertThat(post(courierPath, request, oldCourierToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(courierPath, request, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countRole(courier, "COURIER")).isEqualTo(1);
        assertThat(http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(bearer(oldCourierToken)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(courierPath, request, adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='ROLE_GRANTED'", Long.class, courier)).isEqualTo(1);

        String merchantEmail = UUID.randomUUID() + "@example.test";
        UUID owner = account(merchantEmail);
        String oldMerchantToken = login(merchantEmail);
        UUID merchant = UUID.randomUUID();
        sql.update("insert into swiftdrop.merchants(id,owner_user_id,business_name,contact_email) values(?,?,?,?)",
                merchant, owner, "Fixture Shop", merchantEmail);
        String approvePath = "/api/v1/admin/merchants/" + merchant + "/approve";
        assertThat(post(approvePath, null, oldMerchantToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(approvePath, null, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countRole(owner, "MERCHANT")).isEqualTo(1);
        assertThat(sql.queryForObject("select approval_status from swiftdrop.merchants where id=?", String.class, merchant)).isEqualTo("APPROVED");
        assertThat(http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(bearer(oldMerchantToken)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='ROLE_GRANTED'", Long.class, owner)).isEqualTo(1);
        try {
            SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(adminToken)));
            roleManagement.revokeMerchantForOwner(owner, "Merchant access withdrawn");
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(countRole(owner, "MERCHANT")).isZero();
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='MERCHANT_ACCESS_REVOKED'", Long.class, owner)).isEqualTo(1);

        String inactiveEmail = UUID.randomUUID() + "@example.test";
        UUID inactiveOwner = account(inactiveEmail);
        sql.update("update swiftdrop.users set active=false where id=?", inactiveOwner);
        UUID blockedMerchant = UUID.randomUUID();
        sql.update("insert into swiftdrop.merchants(id,owner_user_id,business_name,contact_email) values(?,?,?,?)",
                blockedMerchant, inactiveOwner, "Blocked Shop", inactiveEmail);
        assertThat(post("/api/v1/admin/merchants/" + blockedMerchant + "/approve", null, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("select approval_status from swiftdrop.merchants where id=?", String.class, blockedMerchant))
                .isEqualTo("PENDING");
        assertThat(countRole(inactiveOwner, "MERCHANT")).isZero();
    }
    HttpHeaders bearer(String token) { var headers = new HttpHeaders(); headers.setBearerAuth(token); return headers; }
}
