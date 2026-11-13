package com.swiftdrop;

import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.service.identity.TokenSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.swiftdrop.integration.identity.EmailVerificationDelivery;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"swiftdrop.registration.requests-per-window=1000", "logging.level.root=INFO",
                "logging.level.org.springframework.web=INFO", "logging.level.org.springframework.jdbc=INFO"})
class RegistrationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        p.add("spring.flyway.user", POSTGRES::getUsername);
        p.add("spring.flyway.password", POSTGRES::getPassword);
        p.add("spring.rabbitmq.password", () -> "unused-fixture");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate sql;
    @Autowired PasswordEncoder passwords;
    @MockitoBean EmailVerificationDelivery verificationDelivery;
    private static final String PASSWORD = "  Exact secret passphrase 🔐  ";
    private static final String PATH = "/api/v1/auth/register";

    HttpHeaders csrf() {
        var response = http.getForEntity("/api/v1/auth/csrf", CsrfResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("http://localhost:5173");
        headers.set(response.getBody().headerName(), response.getBody().token());
        headers.set(HttpHeaders.COOKIE, response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        return headers;
    }
    Map<String, Object> body(String email) {
        return new HashMap<>(Map.of("fullName", "  New Customer  ", "email", email, "password", PASSWORD));
    }
    ResponseEntity<String> send(Map<String, Object> body) {
        return http.exchange(PATH, HttpMethod.POST, new HttpEntity<>(body, csrf()), String.class);
    }
    String email() { return UUID.randomUUID() + "@example.test"; }

    @Test void passwordAccountMustProveInboxBeforeLogin() {
        String email = email();
        assertThat(send(body(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sql.queryForObject("select email_verified_at from swiftdrop.users where email=?", java.sql.Timestamp.class, email)).isNull();
        var login = Map.of("email", email, "password", PASSWORD);
        var unverified = http.exchange("/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(login, csrf()), String.class);
        assertThat(unverified.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unverified.getBody()).contains("EMAIL_VERIFICATION_REQUIRED", "Verify your email")
                .doesNotContain(PASSWORD);
        var wrongPassword = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", "wrong password"), csrf()), String.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).contains("INVALID_CREDENTIALS").doesNotContain("EMAIL_VERIFICATION_REQUIRED");
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        String hash = sql.queryForObject("select token_hash from swiftdrop.email_verification_tokens where user_id=(select id from swiftdrop.users where email=?)", String.class, email);
        assertThat(hash).isEqualTo(TokenSecrets.hash(raw)).isNotEqualTo(raw);
        assertThat(http.exchange("/api/v1/auth/email-verification/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", raw), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sql.queryForObject("select email_verified_at is not null from swiftdrop.users where email=?", Boolean.class, email)).isTrue();
        assertThat(http.exchange("/api/v1/auth/email-verification/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", raw), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.exchange("/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(login, csrf()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void resendIsGenericAndInvalidatesEarlierLink() {
        String email = email();
        assertThat(send(body(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), links.capture());
        String old = links.getValue().split("#token=", 2)[1];
        var request = new HttpEntity<>(Map.of("email", email), csrf());
        assertThat(http.exchange("/api/v1/auth/email-verification/request", HttpMethod.POST, request, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(verificationDelivery, timeout(5000).times(2)).send(org.mockito.ArgumentMatchers.eq(email), links.capture());
        String current = links.getAllValues().getLast().split("#token=", 2)[1];
        assertThat(http.exchange("/api/v1/auth/email-verification/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", old), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.exchange("/api/v1/auth/email-verification/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", current), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String unknown = email();
        var generic = http.exchange("/api/v1/auth/email-verification/request", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", unknown), csrf()), String.class);
        assertThat(generic.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(generic.getBody()).doesNotContain(unknown, "token");
        verify(verificationDelivery, never()).send(org.mockito.ArgumentMatchers.eq(unknown), org.mockito.ArgumentMatchers.anyString());
    }

    @Test void expiredVerificationLinkCannotActivateAccount() {
        String email = email();
        assertThat(send(body(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        sql.update("update swiftdrop.email_verification_tokens set created_at=now()-interval '2 days', expires_at=now()-interval '1 second' where token_hash=?", TokenSecrets.hash(raw));
        assertThat(http.exchange("/api/v1/auth/email-verification/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("token", raw), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sql.queryForObject("select email_verified_at from swiftdrop.users where email=?", java.sql.Timestamp.class, email)).isNull();
    }

    @Test void concurrentVerificationConsumesLinkOnce() throws Exception {
        String email = email();
        assertThat(send(body(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationDelivery, timeout(5000)).send(org.mockito.ArgumentMatchers.eq(email), link.capture());
        String raw = link.getValue().split("#token=", 2)[1];
        var start = new CountDownLatch(1);
        var firstHeaders = csrf(); var secondHeaders = csrf();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<HttpStatusCode> attempt1 = () -> { start.await(); return http.exchange("/api/v1/auth/email-verification/confirm",
                    HttpMethod.POST, new HttpEntity<>(Map.of("token", raw), firstHeaders), String.class).getStatusCode(); };
            Callable<HttpStatusCode> attempt2 = () -> { start.await(); return http.exchange("/api/v1/auth/email-verification/confirm",
                    HttpMethod.POST, new HttpEntity<>(Map.of("token", raw), secondHeaders), String.class).getStatusCode(); };
            var first = pool.submit(attempt1); var second = pool.submit(attempt2);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void successIsSafeAndCommitsAccountAndOnlyCustomerMembership(CapturedOutput output) {
        String email = email();
        var response = send(body("  " + email.toUpperCase(Locale.ROOT) + "  "));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"roles\":[\"CUSTOMER\"]", "\"fullName\":\"New Customer\"")
                .doesNotContain("password", "active", "authorizationVersion", "token");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        var row = sql.queryForMap("select id, email, email_normalized, password_hash, active from swiftdrop.users where email_normalized = ?", email);
        String hash = (String) row.get("password_hash");
        assertThat(row.get("email")).isEqualTo(email);
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(passwords.matches(PASSWORD.strip(), hash)).isFalse();
        assertThat(sql.queryForList("select r.name from swiftdrop.user_roles ur join swiftdrop.roles r on r.id=ur.role_id where ur.user_id=?", String.class, row.get("id")))
                .containsExactly("CUSTOMER");
        assertThat(sql.queryForObject("select count(*) from swiftdrop.auth_sessions where user_id=?", Long.class, row.get("id"))).isZero();
        assertThat(output.getAll()).doesNotContain(PASSWORD, hash);
    }
    @Test
    void concurrentDuplicateEmailsProduceOneCreatedAndOneSafeConflict(CapturedOutput output) throws Exception {
        String email = email();
        var start = new CountDownLatch(1);
        var headers1 = csrf(); var headers2 = csrf();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return http.exchange(PATH, HttpMethod.POST, new HttpEntity<>(body(email), headers1), String.class); });
            var second = pool.submit(() -> { start.await(); return http.exchange(PATH, HttpMethod.POST, new HttpEntity<>(body(email.toUpperCase(Locale.ROOT)), headers2), String.class); });
            start.countDown();
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getStatusCode().value())).containsExactlyInAnyOrder(201, 409);
            String error = results.stream().filter(r -> r.getStatusCode().value()==409).findFirst().orElseThrow().getBody();
            assertThat(error).contains("REGISTRATION_UNAVAILABLE", "correlationId", "fieldErrors").doesNotContain(email, PASSWORD, "constraint", "password_hash");
        }
        assertThat(sql.queryForObject("select count(*) from swiftdrop.users where email_normalized=?", Long.class, email)).isEqualTo(1);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.user_roles ur join swiftdrop.users u on u.id=ur.user_id where u.email_normalized=?", Long.class, email)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(email, PASSWORD);
    }
    @ParameterizedTest
    @ValueSource(strings = {"roles", "active", "id", "authorizationVersion", "passwordHash", "confirmPassword", "unknown"})
    void rejectsEveryAdditionalFieldWithoutPersisting(String field) {
        String email = email(); var body = body(email);
        body.put(field, "privileged-marker");
        var response = send(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_REQUEST").doesNotContain("privileged-marker", PASSWORD);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.users where email_normalized=?", Long.class, email)).isZero();
    }
    @Test
    void invalidInputReturnsOnlySafeFieldMessagesAndNoRows() {
        var body = body(email()); body.put("fullName", " "); body.put("email", "bad-email"); body.put("password", "shortpw");
        var response = send(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED", "\"field\":\"password\"", "\"field\":\"email\"", "\"field\":\"fullName\"")
                .contains("Password must contain at least 8 characters.")
                .doesNotContain("shortpw", "bad-email", "rejectedValue");
        body.remove("password");
        assertThat(send(body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @ParameterizedTest
    @ValueSource(ints = {8, 128})
    void acceptsPasswordLengthBoundariesWithoutChangingUnicode(int length) {
        String email = email();
        String password = "🔐".repeat(length);
        var body = body(email);
        body.put("password", password);
        assertThat(send(body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hash = sql.queryForObject("select password_hash from swiftdrop.users where email_normalized=?", String.class, email);
        assertThat(passwords.matches(password, hash)).isTrue();
    }
    @Test
    void membershipFailureRollsBackAccountAndDoesNotExposeDatabaseRow(CapturedOutput output) {
        String email = email();
        sql.execute("""
                CREATE FUNCTION swiftdrop.reject_registration_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'fixture association failure' USING ERRCODE='23514'; END; $$
                """);
        sql.execute("CREATE TRIGGER reject_registration_fixture BEFORE INSERT ON swiftdrop.user_roles FOR EACH ROW EXECUTE FUNCTION swiftdrop.reject_registration_fixture()");
        try {
            var response = send(body(email));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).contains("INTERNAL_ERROR").doesNotContain("fixture", PASSWORD);
            assertThat(sql.queryForObject("select count(*) from swiftdrop.users where email_normalized=?", Long.class, email)).isZero();
            assertThat(output.getAll()).doesNotContain(PASSWORD, "fixture association failure");
        } finally {
            sql.execute("DROP TRIGGER reject_registration_fixture ON swiftdrop.user_roles");
            sql.execute("DROP FUNCTION swiftdrop.reject_registration_fixture()");
        }
    }
    @Test
    void csrfIsRequiredAndOtherRoutesRemainProtected() {
        assertThat(http.postForEntity(PATH, body(email()), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity("/api/v1/me", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange("/api/v1/auth/not-implemented", HttpMethod.POST, new HttpEntity<>(body(email()), csrf()), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsMalformedJsonAndUnsupportedContentWithoutEchoingBody() {
        var headers = csrf();
        var malformed = http.exchange(PATH, HttpMethod.POST,
                new HttpEntity<>("{\"password\":\"sensitive-fixture\",", headers), String.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody()).contains("INVALID_REQUEST").doesNotContain("sensitive-fixture");
        headers.setContentType(MediaType.TEXT_PLAIN);
        var unsupported = http.exchange(PATH, HttpMethod.POST,
                new HttpEntity<>("sensitive-fixture", headers), String.class);
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupported.getBody()).contains("UNSUPPORTED_MEDIA_TYPE").doesNotContain("sensitive-fixture");
    }
}
