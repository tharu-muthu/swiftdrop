package com.swiftdrop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.config.AuthSettings;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.dto.identity.TokenResponse;
import com.swiftdrop.service.identity.TokenSecrets;
import com.swiftdrop.integration.identity.PasswordResetDelivery;
import com.swiftdrop.integration.identity.EmailVerificationDelivery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(IdentitySessionsIT.TimeConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "swiftdrop.registration.requests-per-window=1000")
class IdentitySessionsIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl); p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword); p.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        p.add("spring.flyway.user", POSTGRES::getUsername); p.add("spring.flyway.password", POSTGRES::getPassword);
        p.add("spring.rabbitmq.password", () -> "unused-fixture");
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Duration offset = Duration.ZERO;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.now().plus(offset); }
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired AuthSettings settings;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired com.swiftdrop.service.identity.IdentityAccountService identityAccounts;
    @Autowired com.swiftdrop.security.IdentityJwtConverter identityConverter;
    @MockitoBean PasswordResetDelivery resetDelivery;
    @MockitoBean EmailVerificationDelivery verificationDelivery;

    @Test void passwordRecoveryIsGenericSingleUseAndRevokesSessions() throws Exception {
        reset(resetDelivery);
        var withoutCsrf = new HttpHeaders(); withoutCsrf.setOrigin(ORIGIN);
        assertThat(http.exchange("/api/v1/auth/password-reset/request", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "someone@example.test"), withoutCsrf), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("password-reset/request", Map.of("email", UUID.randomUUID() + "@example.test"), null).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(resetDelivery);
        String email = account();
        var login = login(email);
        String oldAccess = access(login);
        assertThat(post("password-reset/request", Map.of("email", email), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resetDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        String stored = sql.queryForObject("select token_hash from swiftdrop.password_reset_tokens where user_id=(select id from swiftdrop.users where email=?)", String.class, email);
        assertThat(stored).isEqualTo(TokenSecrets.hash(raw)).isNotEqualTo(raw);
        String replacement = "Another strong password 9";
        assertThat(post("password-reset/confirm", Map.of("token", raw, "password", replacement), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(me(oldAccess).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("password-reset/confirm", Map.of("token", raw, "password", replacement), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("password-reset/confirm", Map.of("token", TokenSecrets.generate(), "password", replacement), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("login", Map.of("email", email, "password", replacement), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void newerRecoveryLinkInvalidatesOlderLinkAndExpires() {
        reset(resetDelivery);
        String email = account();
        assertThat(post("password-reset/request", Map.of("email", email), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        assertThat(post("password-reset/request", Map.of("email", email), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(resetDelivery, timeout(5000).times(2)).send(org.mockito.ArgumentMatchers.eq(email), links.capture());
        String old = links.getAllValues().get(0).split("#token=", 2)[1];
        String current = links.getAllValues().get(1).split("#token=", 2)[1];
        assertThat(post("password-reset/confirm", Map.of("token", old, "password", "A strong password 11"), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        clock.offset = Duration.ofMinutes(31);
        assertThat(post("password-reset/confirm", Map.of("token", current, "password", "A strong password 11"), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void suspendedAndGoogleOnlyAccountsGetGenericRecoveryAcknowledgementWithoutMail() {
        reset(resetDelivery);
        String suspended = account();
        String googleOnly = account();
        sql.update("update swiftdrop.users set active=false where email=?", suspended);
        sql.update("update swiftdrop.users set password_hash=null where email=?", googleOnly);
        assertThat(post("password-reset/request", Map.of("email", suspended), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(post("password-reset/request", Map.of("email", googleOnly), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(resetDelivery);
    }

    @Test void mailTransportFailureDoesNotRevealAccountOrToken() {
        reset(resetDelivery);
        String email = account();
        doThrow(new org.springframework.mail.MailSendException("transport unavailable"))
                .when(resetDelivery).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
        var response = post("password-reset/request", Map.of("email", email), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).doesNotContain(email, "token", "transport unavailable");
        verify(resetDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
    }

    @Test void simultaneousResetSubmissionsConsumeTokenOnlyOnce() throws Exception {
        reset(resetDelivery);
        String email = account();
        assertThat(post("password-reset/request", Map.of("email", email), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resetDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<HttpStatusCode> attempt = () -> {
                ready.countDown(); start.await();
                return post("password-reset/confirm", Map.of("token", raw, "password", "Concurrent password 12"), null).getStatusCode();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.BAD_REQUEST);
        }
    }

    @Test void sharedAccountContractUsesLiveRolesAndRejectsRevokedSessions() throws Exception {
        var signedIn = login(account());
        var jwt = decoder.decode(access(signedIn));
        UUID actorId = UUID.fromString(jwt.getSubject());
        String courierEmail = account();
        UUID courierId = sql.queryForObject("select id from swiftdrop.users where email=?", UUID.class, courierEmail);
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(identityConverter.convert(jwt));
            assertThatThrownBy(() -> identityAccounts.requireActiveCourier(courierId))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            // Test fixtures only: production role mutation remains a separate audited workflow.
            sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name='DISPATCHER_ADMIN'", actorId);
            sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name='COURIER'", courierId);
            assertThat(identityAccounts.requireActiveCourier(courierId).id()).isEqualTo(courierId);
            sql.update("update swiftdrop.users set active=false where id=?", courierId);
            assertThatThrownBy(() -> identityAccounts.requireActiveCourier(courierId))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            sql.update("update swiftdrop.auth_sessions set revoked_at=now(),revocation_reason='TEST' where id=?", session(signedIn));
            assertThatThrownBy(identityAccounts::requireCurrentIdentity)
                    .isInstanceOf(com.swiftdrop.exception.IdentityAuthenticationException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    private static final String PASSWORD = "Exact password 8 ";
    private static final String ORIGIN = "http://localhost:5173";
    @BeforeEach void resetClock() { clock.offset = Duration.ZERO; }
    @AfterEach void restoreClock() { clock.offset = Duration.ZERO; }

    HttpHeaders csrf(String refresh) {
        var response = http.getForEntity("/api/v1/auth/csrf", CsrfResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax", "HttpOnly");
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setOrigin(ORIGIN);
        headers.set(response.getBody().headerName(), response.getBody().token());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        headers.set(HttpHeaders.COOKIE, cookie + (refresh == null ? "" : "; " + refresh));
        return headers;
    }
    ResponseEntity<String> post(String endpoint, Object body, String refresh) {
        return http.exchange("/api/v1/auth/" + endpoint, HttpMethod.POST, new HttpEntity<>(body, csrf(refresh)), String.class);
    }
    String account() {
        String email = UUID.randomUUID() + "@example.test";
        assertThat(post("register", Map.of("email", email, "fullName", "Session Test", "password", PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        assertThat(post("email-verification/confirm", Map.of("token", raw), null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return email;
    }
    ResponseEntity<String> login(String email) { return post("login", Map.of("email", " " + email.toUpperCase(Locale.ROOT) + " ", "password", PASSWORD), null); }
    String access(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()).get("accessToken").asText(); }
    String cookie(ResponseEntity<?> response) { return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]; }
    UUID session(ResponseEntity<String> response) throws Exception { return UUID.fromString(decoder.decode(access(response)).getClaimAsString("sid")); }
    ResponseEntity<String> me(String token) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token);
        return http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    ResponseEntity<String> patchProfile(String token, Object body) {
        var headers = csrf(null);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("/api/v1/me", HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    @Test void profileUpdateIsScopedValidatedAndVersioned() throws Exception {
        String email = account();
        String token = access(login(email));
        JsonNode before = json.readTree(me(token).getBody());
        long version = before.get("version").asLong();
        assertThat(before.get("passwordConfigured").asBoolean()).isTrue();
        assertThat(before.get("googleConnected").asBoolean()).isFalse();
        assertThat(patchProfile(null, Map.of("fullName", "Wrong", "version", version)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(patchProfile(token, Map.of("fullName", " ", "version", version)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patchProfile(token, Map.of("fullName", "Changed", "phone", "123", "version", version)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var injected = new HashMap<String, Object>(Map.of("fullName", "Changed", "version", version, "roles", List.of("DISPATCHER_ADMIN")));
        assertThat(patchProfile(token, injected).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patchProfile(token, Map.of("fullName", "Changed", "version", version, "email", "other@example.test")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var valid = new HashMap<String, Object>(Map.of("fullName", "  Updated Name  ", "phone", "+94 77-1234567", "version", version));
        var updated = patchProfile(token, valid);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode profile = json.readTree(updated.getBody());
        assertThat(profile.get("fullName").asText()).isEqualTo("Updated Name");
        assertThat(profile.get("phone").asText()).isEqualTo("+94771234567");
        assertThat(profile.get("version").asLong()).isGreaterThan(version);
        assertThat(profile.get("email").asText()).isEqualTo(email);
        assertThat(profile.toString()).doesNotContain("passwordHash", "authorizationVersion");
        assertThat(patchProfile(token, valid).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sql.queryForObject("select full_name from swiftdrop.users where email=?", String.class, email)).isEqualTo("Updated Name");
        var clear = new HashMap<String, Object>(Map.of("fullName", "Updated Name", "version", profile.get("version").asLong()));
        clear.put("phone", null);
        assertThat(patchProfile(token, clear).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me(token).getBody()).get("phone").isNull()).isTrue();
    }

    @Test void loginSetsSafeCookieAndReturnsSafeProfileAndClaims(CapturedOutput logs) throws Exception {
        String email = account(); var response = login(email);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth").doesNotContain("Domain=", "Secure");
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("expiresIn").asInt()).isEqualTo(600);
        var jwt = decoder.decode(access(response));
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getIssuer().toString()).isEqualTo(settings.issuer());
        assertThat(jwt.getAudience()).contains(settings.audience());
        var profile = me(access(response));
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).contains(email, "CUSTOMER", "Session Test").doesNotContain("passwordHash", "token", "authorizationVersion");
        String raw = cookie(response).split("=", 2)[1];
        String hash = sql.queryForObject("select token_hash from swiftdrop.refresh_tokens where session_id=?", String.class, session(response));
        assertThat(hash).isEqualTo(TokenSecrets.hash(raw)).isNotEqualTo(raw);
        assertThat(logs.getAll()).doesNotContain(PASSWORD, raw, access(response), hash);
    }
    @Test void unknownWrongSuspendedAndGoogleOnlyAccountsHaveIdenticalErrors() throws Exception {
        String email = account();
        var wrong = post("login", Map.of("email", email, "password", "wrong-password"), null);
        var missing = login(UUID.randomUUID() + "@example.test");
        sql.update("update swiftdrop.users set active=false where email=?", email);
        var suspended = login(email);
        sql.update("update swiftdrop.users set active=true, password_hash=null where email=?", email);
        var googleOnly = login(email);
        for (var response : List.of(wrong, missing, suspended, googleOnly)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(json.readTree(response.getBody()).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(json.readTree(response.getBody()).get("message").asText()).isEqualTo("Email or password is incorrect.");
            assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        }
        assertThat(sql.queryForObject("select count(*) from swiftdrop.auth_sessions s join swiftdrop.users u on u.id=s.user_id where u.email=?", Long.class, email)).isZero();
    }
    @Test void rotationKeepsAbsoluteExpiryAndReplayRevokesTheWholeFamily() throws Exception {
        var first = login(account()); UUID sid = session(first);
        var expiry = sql.queryForObject("select expires_at from swiftdrop.auth_sessions where id=?", java.sql.Timestamp.class, sid);
        clock.offset = Duration.ofDays(1);
        var next = post("refresh", null, cookie(first));
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session(next)).isEqualTo(sid);
        assertThat(cookie(next)).isNotEqualTo(cookie(first));
        assertThat(sql.queryForObject("select expires_at from swiftdrop.auth_sessions where id=?", java.sql.Timestamp.class, sid)).isEqualTo(expiry);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.refresh_tokens where session_id=? and consumed_at is not null and replaced_by is not null", Long.class, sid)).isEqualTo(1);
        assertThat(me(access(next)).getStatusCode()).isEqualTo(HttpStatus.OK);
        var replay = post("refresh", null, cookie(first));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(sql.queryForObject("select revocation_reason from swiftdrop.auth_sessions where id=?", String.class, sid)).isEqualTo("REFRESH_REUSE");
        assertThat(post("refresh", null, cookie(next)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(access(next)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void concurrentRefreshHasOneWinnerAndConfirmedReplayRevokesItsReplacement() throws Exception {
        var first = login(account()); var start = new CountDownLatch(1);
        var h1 = csrf(cookie(first)); var h2 = csrf(cookie(first));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, h1), String.class); });
            var two = pool.submit(() -> { start.await(); return http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, h2), String.class); });
            start.countDown();
            var results = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getStatusCode().value())).containsExactlyInAnyOrder(200, 401);
            var winner = results.stream().filter(r -> r.getStatusCode().value() == 200).findFirst().orElseThrow();
            assertThat(me(access(winner)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(post("refresh", null, cookie(winner)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(sql.queryForObject("select count(*) from swiftdrop.refresh_tokens where session_id=?", Long.class, session(first))).isEqualTo(2);
    }
    @Test void absoluteExpiryAndAccessExpiryUseTheConfiguredClock() throws Exception {
        var first = login(account());
        clock.offset = Duration.ofMinutes(10).plusSeconds(1);
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.OK);
        clock.offset = Duration.ofDays(7).plusSeconds(1);
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void logoutRevokesOnlyThisSessionAndIsIdempotentWithoutAccessJwt() throws Exception {
        String email = account(); var first = login(email); var second = login(email);
        var logout = post("logout", null, cookie(first));
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "HttpOnly", "Path=/api/v1/auth", "SameSite=Lax");
        assertThat(post("logout", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("logout", null, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(access(second)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void accountSuspensionAuthorizationVersionAndCurrentRolesApplyImmediately() throws Exception {
        String email = account(); var first = login(email);
        UUID userId = UUID.fromString(decoder.decode(access(first)).getSubject());
        sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name='COURIER'", userId);
        assertThat(me(access(first)).getBody()).contains("COURIER");
        sql.update("update swiftdrop.users set active=false where id=?", userId);
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        sql.update("update swiftdrop.users set active=true,authorization_version=authorization_version+1 where id=?", userId);
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @ParameterizedTest @ValueSource(strings = {"issuer", "audience", "expiry", "future", "missing-session", "signature", "algorithm"})
    void rejectsInvalidJwtClaimsAndSignatures(String defect) throws Exception {
        var first = login(account()); var original = decoder.decode(access(first));
        var claims = new HashMap<String, Object>(original.getClaims());
        switch (defect) {
            case "issuer" -> claims.put("iss", "https://wrong.test");
            case "audience" -> claims.put("aud", List.of("wrong-api"));
            case "expiry" -> { claims.put("iat", clock.instant().minusSeconds(120)); claims.put("exp", clock.instant().minusSeconds(1)); }
            case "future" -> claims.put("iat", clock.instant().plusSeconds(120));
            case "missing-session" -> claims.remove("sid");
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder().claims(map -> map.putAll(claims)).build())).getTokenValue();
        if (defect.equals("signature")) {
            String[] parts = token.split("\\."); char firstChar = parts[2].charAt(0);
            parts[2] = (firstChar == 'a' ? 'b' : 'a') + parts[2].substring(1); token = String.join(".", parts);
        }
        if (defect.equals("algorithm")) {
            String[] parts = token.split("\\.");
            parts[0] = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            token = parts[0] + "." + parts[1] + ".";
        }
        assertThat(me(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void csrfAndExactOriginAreRequiredForEveryAuthMutation() throws Exception {
        var first = login(account());
        for (String endpoint : List.of("login", "refresh", "logout")) {
            var headers = csrf(cookie(first)); headers.remove("X-XSRF-TOKEN");
            assertThat(http.exchange("/api/v1/auth/" + endpoint, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            headers = csrf(cookie(first)); headers.setOrigin("https://attacker.example");
            assertThat(http.exchange("/api/v1/auth/" + endpoint, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            headers = csrf(cookie(first)); headers.remove(HttpHeaders.ORIGIN);
            assertThat(http.exchange("/api/v1/auth/" + endpoint, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void apiDoesNotTrustOrInvalidateFrameworkOAuthSessionState() throws Exception {
        var session = new MockHttpSession(); session.setAttribute("future-oidc-state", "state-fixture");
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("oauth-principal", null, List.of())));
        mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute("future-oidc-state")).isEqualTo("state-fixture");
    }
    @Test void unknownRefreshDoesNotRevokeSomeoneElsesSession() throws Exception {
        var first = login(account());
        assertThat(post("refresh", null, "swiftdrop_refresh=" + TokenSecrets.generate()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(access(first)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void rotationDatabaseFailureRollsBackConsumptionAndReplacement() throws Exception {
        var first = login(account());
        sql.execute("CREATE FUNCTION swiftdrop.fail_refresh_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture' USING ERRCODE='23514'; END; $$");
        sql.execute("CREATE TRIGGER fail_refresh_fixture BEFORE INSERT ON swiftdrop.refresh_tokens FOR EACH ROW EXECUTE FUNCTION swiftdrop.fail_refresh_fixture()");
        try { assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR); }
        finally { sql.execute("DROP TRIGGER fail_refresh_fixture ON swiftdrop.refresh_tokens"); sql.execute("DROP FUNCTION swiftdrop.fail_refresh_fixture()"); }
        assertThat(sql.queryForObject("select count(*) from swiftdrop.refresh_tokens where session_id=? and consumed_at is not null", Long.class, session(first))).isZero();
        assertThat(post("refresh", null, cookie(first)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
