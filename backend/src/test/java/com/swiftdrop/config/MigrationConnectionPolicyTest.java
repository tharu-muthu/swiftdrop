package com.swiftdrop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationConnectionPolicyTest {
    private static final String DIRECT = "jdbc:postgresql://ep-test.region.aws.neon.tech/db?sslmode=require&channelBinding=require";
    private static final String POOLED = DIRECT.replace("ep-test.", "ep-test-pooler.");

    @Test
    void derivesDirectNeonUrlWithoutChangingDatabaseOrTlsSettings() {
        assertThat(MigrationConnectionPolicy.directUrl(POOLED, null)).isEqualTo(DIRECT);
        assertThat(MigrationConnectionPolicy.directUrl(DIRECT, null)).isEqualTo(DIRECT);
        assertThat(MigrationConnectionPolicy.directUrl(POOLED, DIRECT)).isEqualTo(DIRECT);
    }

    @Test
    void retainsLocalComposeAndExplicitDefaultPortSupport() {
        String local = "jdbc:postgresql://postgres:5432/swiftdrop";
        assertThat(MigrationConnectionPolicy.directUrl(local, null)).isEqualTo(local);
        assertThat(MigrationConnectionPolicy.directUrl(DIRECT, DIRECT.replace(".tech/", ".tech:5432/")))
                .endsWith(".tech:5432/db?sslmode=require&channelBinding=require");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://ep-other.region.aws.neon.tech/db",
            "jdbc:postgresql://ep-test.region.aws.neon.tech/other",
            "jdbc:postgresql://ep-test.region.aws.neon.tech:5433/db",
            "jdbc:postgresql://ep-test-pooler.region.aws.neon.tech/db"
    })
    void rejectsDifferentDestinationOrPooledMigrationConnection(String migration) {
        assertThatThrownBy(() -> MigrationConnectionPolicy.directUrl(POOLED, migration))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://user:private-marker@localhost/db",
            "jdbc:postgresql://localhost/db?password=private-marker",
            "jdbc:postgresql://localhost/db?%75ser=private-marker",
            "jdbc:postgresql://localhost/db?options=endpoint%3Dprivate-marker",
            "jdbc:postgresql://localhost/db?service=private-marker",
            "jdbc:postgresql://localhost,other/db?private-marker",
            "jdbc:postgresql://localhost/db#private-marker",
            "jdbc:postgresql://localhost/private marker",
            "jdbc:postgresql://localhost/"
    })
    void rejectsCredentialOrRoutingOverridesWithoutEchoingInput(String url) {
        assertThatThrownBy(() -> MigrationConnectionPolicy.directUrl(url, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private-marker")
                .hasNoCause();
    }
}
