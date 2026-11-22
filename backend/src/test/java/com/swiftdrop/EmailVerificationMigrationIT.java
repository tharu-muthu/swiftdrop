package com.swiftdrop;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class EmailVerificationMigrationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @Test void v8PreservesExistingUsersAndBackfillsOnlyLinkedGoogleAccounts() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("swiftdrop").createSchemas(true).defaultSchema("swiftdrop")
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("7"))
                .load().migrate();
        UUID passwordId = UUID.randomUUID();
        UUID googleId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var insert = connection.prepareStatement("insert into swiftdrop.users(id,email,full_name,password_hash) values (?,?,?,?)")) {
                insert.setObject(1, passwordId); insert.setString(2, "password@example.test"); insert.setString(3, "Password User"); insert.setString(4, "existing-hash"); insert.executeUpdate();
                insert.setObject(1, googleId); insert.setString(2, "google@example.test"); insert.setString(3, "Google User"); insert.setString(4, null); insert.executeUpdate();
            }
            try (var link = connection.prepareStatement("insert into swiftdrop.oauth_accounts(user_id,provider,provider_subject) values (?,'google','existing-subject')")) {
                link.setObject(1, googleId); link.executeUpdate();
            }
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("swiftdrop").createSchemas(true).defaultSchema("swiftdrop")
                .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var query = connection.prepareStatement("select id,password_hash,email_verified_at from swiftdrop.users where id in (?,?)")) {
            query.setObject(1, passwordId); query.setObject(2, googleId);
            try (var rows = query.executeQuery()) {
                int found = 0;
                while (rows.next()) {
                    found++;
                    if (rows.getObject("id").equals(passwordId)) {
                        assertThat(rows.getString("password_hash")).isEqualTo("existing-hash");
                        assertThat(rows.getTimestamp("email_verified_at")).isNull();
                    } else {
                        assertThat(rows.getObject("id")).isEqualTo(googleId);
                        assertThat(rows.getTimestamp("email_verified_at")).isNotNull();
                    }
                }
                assertThat(found).isEqualTo(2);
            }
        }
    }
}
