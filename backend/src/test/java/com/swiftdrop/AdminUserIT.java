package com.swiftdrop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "swiftdrop.auth.account-login-limit=1000")
class AdminUserIT {
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
    private static final String PASSWORD = "Fixture password 12";

    UUID account(String email, boolean admin) {
        UUID id = UUID.randomUUID();
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values(?,?,?,?,?)",
                id, email, "Admin User Test", passwords.encode(PASSWORD), Timestamp.from(Instant.now()));
        sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name=?",
                id, admin ? "DISPATCHER_ADMIN" : "CUSTOMER");
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
    org.springframework.http.ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test @Order(2) void directoryAndStatusChangesAreAdminOnlyAuditedAndRevokeSessions() throws Exception {
        String adminEmail = UUID.randomUUID() + "@example.test";
        UUID admin = account(adminEmail, true);
        String adminToken = login(adminEmail);
        String customerEmail = UUID.randomUUID() + "@example.test";
        UUID customer = account(customerEmail, false);
        String customerToken = login(customerEmail);
        String base = "/api/v1/admin/users";
        assertThat(get(base, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(base, customerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(base + "?size=101", adminToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var search = get(base + "?query=" + customerEmail + "&size=1", adminToken);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(search.getBody()).get("totalElements").asLong()).isEqualTo(1);
        var details = get(base + "/" + customer, adminToken);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(details.getBody()).doesNotContain("passwordHash", "Fixture password");
        String path = base + "/" + customer + "/suspend";
        var change = Map.of("reason", "Customer requested access pause", "expectedVersion", 0);
        assertThat(post(path, change, customerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(path, Map.of("reason", "Access pause", "expectedVersion", 0, "roles", "DISPATCHER_ADMIN"), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, Map.of("reason", " ", "expectedVersion", 0), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(path, change, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/me", customerToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(path, change, adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='ACCOUNT_SUSPENDED'", Long.class, customer)).isEqualTo(1);
        assertThat(post(base + "/" + admin + "/suspend", change, adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long version = json.readTree(get(base + "/" + customer, adminToken).getBody()).get("version").asLong();
        assertThat(post(base + "/" + customer + "/reactivate", Map.of("reason", "Access restored", "expectedVersion", version), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/me", customerToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='ACCOUNT_REACTIVATED'", Long.class, customer)).isEqualTo(1);
    }

    @Test @Order(1) void roleChangesRequireAdminAndVersionAndRevokeSessions() throws Exception {
        String adminEmail = UUID.randomUUID() + "@example.test";
        UUID admin = account(adminEmail, true);
        String adminToken = login(adminEmail);
        String targetEmail = UUID.randomUUID() + "@example.test";
        UUID target = account(targetEmail, false);
        String targetToken = login(targetEmail);
        String grant = "/api/v1/admin/users/" + target + "/roles/grant";
        String revoke = "/api/v1/admin/users/" + target + "/roles/revoke";
        var request = Map.of("role", "DISPATCHER_ADMIN", "reason", "Approved second administrator", "expectedVersion", 0);
        assertThat(post(grant, request, targetToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(grant, request, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/me", targetToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(grant, request, adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(revoke, Map.of("role", "DISPATCHER_ADMIN", "reason", "Remove second administrator", "expectedVersion", 0), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(grant, Map.of("role", "MERCHANT", "reason", "Invalid source", "expectedVersion", 1), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(revoke, Map.of("role", "DISPATCHER_ADMIN", "reason", "Remove second administrator", "expectedVersion", 1), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/admin/users/" + admin + "/roles/revoke",
                Map.of("role", "DISPATCHER_ADMIN", "reason", "Self removal", "expectedVersion", 0), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action in ('ROLE_GRANTED','ROLE_REVOKED')", Long.class, target)).isEqualTo(2);
    }
}
