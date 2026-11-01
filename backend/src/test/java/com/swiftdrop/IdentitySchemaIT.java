package com.swiftdrop;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests actual PostgreSQL constraints/transactions, never a configured developer database. */
@Testcontainers
class IdentitySchemaIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @BeforeAll
    static void migrate() {
        migration(POSTGRES.getJdbcUrl(), null).migrate();
    }

    static Flyway migration(String url, String target) {
        var configuration = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("swiftdrop").defaultSchema("swiftdrop").cleanDisabled(true)
                .validateMigrationNaming(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void upgradePreservesExistingDataAndRestartDoesNotReseedOrReapply() throws Exception {
        try (var c = connection()) {
            execute(c, "CREATE DATABASE identity_upgrade");
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/identity_upgrade";
        var v1 = migration(url, "1");
        assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
        try (var c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // Represents data belonging to an earlier component; V2 must leave it alone.
            execute(c, "CREATE TABLE swiftdrop.preservation_probe (id uuid PRIMARY KEY, note text NOT NULL)");
            execute(c, "INSERT INTO swiftdrop.preservation_probe VALUES (?, 'preserve me')", UUID.randomUUID());
        }
        var v2 = migration(url, null);
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(1);
        v2.validate();
        assertThat(v2.migrate().migrationsExecuted).isZero();
        try (var c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(scalar(c, "SELECT note FROM swiftdrop.preservation_probe")).isEqualTo("preserve me");
            assertThat(scalar(c, "SELECT count(*) FROM swiftdrop.users")).isEqualTo(0L);
            assertThat(scalar(c, "SELECT string_agg(name, ',' ORDER BY name) FROM swiftdrop.roles"))
                    .isEqualTo("COURIER,CUSTOMER,DISPATCHER_ADMIN,MERCHANT");
            assertThat(scalar(c, "SELECT count(*) FROM swiftdrop.user_roles")).isEqualTo(0L);
        }
    }

    @Test
    void emailNormalizationIsGeneratedAndUniqueEvenForSqlWriters() throws Exception {
        try (var c = connection()) {
            UUID user = user(c, "  Mixed." + UUID.randomUUID() + "@Example.com  ");
            String normalized = (String) scalar(c, "SELECT email_normalized FROM swiftdrop.users WHERE id = ?", user);
            assertThat(normalized).endsWith("@example.com").doesNotContain(" ");
            assertThat(scalar(c, "SELECT password_hash FROM swiftdrop.users WHERE id = ?", user)).isNull();
            violation("23505", () -> user(c, normalized));
            violation("428C9", () -> execute(c, "UPDATE swiftdrop.users SET email_normalized = 'forged' WHERE id = ?", user));
            violation("23514", () -> execute(c, "UPDATE swiftdrop.users SET authorization_version = -1 WHERE id = ?", user));
        }
    }

    @Test
    void roleAssociationsAndOauthIdentitiesHaveDatabaseUniquenessAndForeignKeys() throws Exception {
        try (var c = connection()) {
            UUID first = user(c), second = user(c);
            UUID role = (UUID) scalar(c, "SELECT id FROM swiftdrop.roles WHERE name = 'CUSTOMER'");
            execute(c, "INSERT INTO swiftdrop.user_roles (user_id, role_id) VALUES (?, ?)", first, role);
            violation("23505", () -> execute(c, "INSERT INTO swiftdrop.roles (name) VALUES ('CUSTOMER')"));
            violation("23514", () -> execute(c, "INSERT INTO swiftdrop.roles (name) VALUES ('ADMIN')"));
            violation("23505", () -> execute(c, "INSERT INTO swiftdrop.user_roles (user_id, role_id) VALUES (?, ?)", first, role));
            violation("23503", () -> execute(c, "INSERT INTO swiftdrop.user_roles (user_id, role_id) VALUES (?, ?)", UUID.randomUUID(), role));
            String subject = UUID.randomUUID().toString();
            execute(c, "INSERT INTO swiftdrop.oauth_accounts (user_id, provider, provider_subject) VALUES (?, 'google', ?)", first, subject);
            violation("23505", () -> execute(c, "INSERT INTO swiftdrop.oauth_accounts (user_id, provider, provider_subject) VALUES (?, 'google', ?)", second, subject));
            violation("23503", () -> execute(c, "DELETE FROM swiftdrop.users WHERE id = ?", first));
        }
    }

    @Test
    void refreshTokensRequireHashesOneCurrentTokenAndTheAbsoluteSessionExpiry() throws Exception {
        try (var c = connection()) {
            UUID session = session(c, user(c));
            UUID token = token(c, session);
            violation("23505", () -> token(c, session));
            UUID otherSession = session(c, user(c));
            String hash = (String) scalar(c, "SELECT token_hash FROM swiftdrop.refresh_tokens WHERE id = ?", token);
            violation("23505", () -> insertToken(c, UUID.randomUUID(), otherSession, hash));
            violation("23514", () -> insertToken(c, UUID.randomUUID(), otherSession, "raw-token"));
            violation("23503", () -> execute(c, """
                    INSERT INTO swiftdrop.refresh_tokens (session_id, token_hash, expires_at)
                    SELECT id, ?, expires_at + interval '1 day' FROM swiftdrop.auth_sessions WHERE id = ?
                    """, hash(), otherSession));
            violation("23514", () -> execute(c, """
                    INSERT INTO swiftdrop.auth_sessions (user_id, authorization_version, expires_at)
                    VALUES (?, 0, CURRENT_TIMESTAMP - interval '1 day')
                    """, user(c)));
        }
    }

    @Test
    void rotationKeepsConsumedHashAndLinksOnlyWithinTheSameSession() throws Exception {
        try (var c = connection()) {
            UUID session = session(c, user(c)), old = token(c, session), replacement = UUID.randomUUID();
            c.setAutoCommit(false);
            execute(c, "UPDATE swiftdrop.refresh_tokens SET consumed_at = clock_timestamp(), replaced_by = ? WHERE id = ?", replacement, old);
            insertToken(c, replacement, session, hash());
            c.commit();
            c.setAutoCommit(true);
            assertThat(scalar(c, "SELECT replaced_by FROM swiftdrop.refresh_tokens WHERE id = ? AND consumed_at IS NOT NULL", old))
                    .isEqualTo(replacement);
            assertThat(scalar(c, "SELECT count(*) FROM swiftdrop.refresh_tokens WHERE session_id = ?", session)).isEqualTo(2L);
            UUID foreign = token(c, session(c, user(c)));
            c.setAutoCommit(false);
            execute(c, "UPDATE swiftdrop.refresh_tokens SET consumed_at = clock_timestamp(), replaced_by = ? WHERE id = ?", foreign, replacement);
            violation("23503", c::commit);
            c.rollback();
        }
    }

    @Test
    void concurrentConditionalRotationHasExactlyOneWinner() throws Exception {
        UUID session, old;
        try (var c = connection()) {
            session = session(c, user(c));
            old = token(c, session);
        }
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> rotate = () -> {
                try (var c = connection()) {
                    c.setAutoCommit(false);
                    execute(c, "SET LOCAL lock_timeout = '5s'");
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start");
                    }
                    UUID next = UUID.randomUUID();
                    int changed = execute(c, """
                            UPDATE swiftdrop.refresh_tokens SET consumed_at = clock_timestamp(), replaced_by = ?
                            WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL
                            """, next, old);
                    if (changed == 1) {
                        insertToken(c, next, session, hash());
                    }
                    c.commit();
                    return changed;
                }
            };
            var first = pool.submit(rotate);
            var second = pool.submit(rotate);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            start.countDown();
        }
        try (var c = connection()) {
            assertThat(scalar(c, "SELECT count(*) FROM swiftdrop.refresh_tokens WHERE session_id = ? AND consumed_at IS NULL", session))
                    .isEqualTo(1L);
        }
    }

    @Test
    void familyRevocationAndAuthorizationVersionCanInvalidatePersistedCredentials() throws Exception {
        try (var c = connection()) {
            UUID user = user(c), session = session(c, user);
            token(c, session);
            assertThat(usable(c, session)).isEqualTo(1L);
            execute(c, "UPDATE swiftdrop.users SET authorization_version = authorization_version + 1 WHERE id = ?", user);
            assertThat(usable(c, session)).isZero();
            UUID newSession = session(c, user);
            token(c, newSession);
            assertThat(usable(c, newSession)).isEqualTo(1L);
            execute(c, """
                    UPDATE swiftdrop.auth_sessions SET revoked_at = clock_timestamp(), revocation_reason = 'REUSE'
                    WHERE family_id = (SELECT family_id FROM swiftdrop.auth_sessions WHERE id = ?)
                    """, newSession);
            assertThat(usable(c, newSession)).isZero();
            UUID third = session(c, user);
            token(c, third);
            execute(c, "UPDATE swiftdrop.users SET active = false WHERE id = ?", user);
            assertThat(usable(c, third)).isZero();
        }
    }

    @Test
    void resetTokensHaveOneOutstandingRequestAndCanBeConsumedOnlyOnce() throws Exception {
        try (var c = connection()) {
            UUID user = user(c);
            String sql = "INSERT INTO swiftdrop.password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP + interval '30 minutes')";
            String hash = hash();
            execute(c, sql, user, hash);
            violation("23505", () -> execute(c, sql, user, hash()));
            violation("23514", () -> execute(c, sql, user(c), "plaintext"));
            String consume = "UPDATE swiftdrop.password_reset_tokens SET used_at = clock_timestamp() WHERE token_hash = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP";
            assertThat(execute(c, consume, hash)).isEqualTo(1);
            assertThat(execute(c, consume, hash)).isZero();
            execute(c, sql, user, hash());
            execute(c, "UPDATE swiftdrop.password_reset_tokens SET revoked_at = clock_timestamp() WHERE user_id = ? AND used_at IS NULL", user);
            execute(c, sql, user, hash());
        }
    }

    @Test
    void expiredAndRevokedResetTokensCannotMatchTheConsumptionPredicate() throws Exception {
        try (var c = connection()) {
            UUID user = user(c);
            execute(c, """
                    INSERT INTO swiftdrop.password_reset_tokens (user_id, token_hash, created_at, expires_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP - interval '2 hours', CURRENT_TIMESTAMP - interval '1 hour')
                    """, user, hash());
            assertThat(execute(c, """
                    UPDATE swiftdrop.password_reset_tokens SET used_at = clock_timestamp()
                    WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                    """, user)).isZero();
            execute(c, "UPDATE swiftdrop.password_reset_tokens SET revoked_at = clock_timestamp() WHERE user_id = ?", user);
            assertThat(scalar(c, "SELECT count(*) FROM swiftdrop.password_reset_tokens WHERE user_id = ? AND revoked_at IS NULL", user)).isEqualTo(0L);
        }
    }

    @Test
    void auditReferencesValidActorsAndTargetsAndRejectsUpdatesAndDeletes() throws Exception {
        try (var c = connection()) {
            UUID user = user(c), audit = UUID.randomUUID();
            execute(c, """
                    INSERT INTO swiftdrop.identity_audit_events (id, actor_kind, target_user_id, action, reason)
                    VALUES (?, 'SYSTEM', ?, 'BOOTSTRAP', 'isolated fixture')
                    """, audit, user);
            violation("23514", () -> execute(c, "UPDATE swiftdrop.identity_audit_events SET reason = 'changed' WHERE id = ?", audit));
            violation("23514", () -> execute(c, "DELETE FROM swiftdrop.identity_audit_events WHERE id = ?", audit));
            violation("23503", () -> execute(c, "DELETE FROM swiftdrop.users WHERE id = ?", user));
            violation("23514", () -> execute(c, """
                    INSERT INTO swiftdrop.identity_audit_events (actor_kind, target_user_id, action, reason)
                    VALUES ('USER', ?, 'SUSPEND', 'missing actor')
                    """, user));
        }
    }

    @Test
    void timestampsRepresentUtcInstantsAndUpdatesAreTrackedByTheDatabase() throws Exception {
        try (var c = connection()) {
            execute(c, "SET TIME ZONE 'Asia/Colombo'");
            UUID user = user(c);
            execute(c, "UPDATE swiftdrop.users SET created_at = '2020-01-01 05:30:00+05:30', full_name = 'Updated' WHERE id = ?", user);
            try (var statement = c.prepareStatement("SELECT created_at, updated_at FROM swiftdrop.users WHERE id = ?")) {
                statement.setObject(1, user);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    assertThat(rows.getTimestamp(1).toInstant()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
                    assertThat(rows.getTimestamp(2).toInstant()).isAfter(rows.getTimestamp(1).toInstant());
                }
            }
        }
    }

    static long usable(Connection c, UUID session) throws SQLException {
        // Defines the future service's required predicate; this test does not implement an auth endpoint.
        return (long) scalar(c, """
                SELECT count(*) FROM swiftdrop.refresh_tokens t
                JOIN swiftdrop.auth_sessions s ON s.id = t.session_id
                JOIN swiftdrop.users u ON u.id = s.user_id
                WHERE s.id = ? AND u.active AND u.authorization_version = s.authorization_version
                  AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP
                  AND t.consumed_at IS NULL AND t.revoked_at IS NULL AND t.expires_at > CURRENT_TIMESTAMP
                """, session);
    }

    static UUID user(Connection c) throws SQLException { return user(c, UUID.randomUUID() + "@example.test"); }
    static UUID user(Connection c, String email) throws SQLException {
        UUID id = UUID.randomUUID();
        execute(c, "INSERT INTO swiftdrop.users (id, email, full_name) VALUES (?, ?, 'Test User')", id, email);
        return id;
    }
    static UUID session(Connection c, UUID user) throws SQLException {
        UUID id = UUID.randomUUID();
        execute(c, """
                INSERT INTO swiftdrop.auth_sessions (id, user_id, authorization_version, expires_at)
                SELECT ?, id, authorization_version, CURRENT_TIMESTAMP + interval '7 days' FROM swiftdrop.users WHERE id = ?
                """, id, user);
        return id;
    }
    static UUID token(Connection c, UUID session) throws SQLException {
        UUID id = UUID.randomUUID();
        insertToken(c, id, session, hash());
        return id;
    }
    static void insertToken(Connection c, UUID id, UUID session, String hash) throws SQLException {
        execute(c, """
                INSERT INTO swiftdrop.refresh_tokens (id, session_id, token_hash, expires_at)
                SELECT ?, id, ?, expires_at FROM swiftdrop.auth_sessions WHERE id = ?
                """, id, hash, session);
    }
    static String hash() { return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""); }
    static int execute(Connection c, String sql, Object... values) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement.executeUpdate();
        }
    }
    static Object scalar(Connection c, String sql, Object... values) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getObject(1);
            }
        }
    }
    static void violation(String state, SqlOperation action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SQLException.class,
                exception -> assertThat(exception.getSQLState()).isEqualTo(state));
    }
    @FunctionalInterface
    interface SqlOperation { void run() throws SQLException; }
}
