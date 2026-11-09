package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.entity.merchant.ApiCredential;
import com.swiftdrop.entity.merchant.Merchant;
import com.swiftdrop.entity.merchant.MerchantApprovalStatus;
import com.swiftdrop.exception.InvalidApiKeyException;
import com.swiftdrop.exception.MerchantNotApprovedException;
import com.swiftdrop.repository.merchant.ApiCredentialRepository;
import com.swiftdrop.repository.merchant.MerchantRepository;
import com.swiftdrop.service.merchant.ApiKeyGenerator;
import com.swiftdrop.service.merchant.MerchantApiKeyAuthenticator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** M2 merchant tables against real PostgreSQL. Each test rolls back, so the database stays clean. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class MerchantSchemaIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2.9-management-alpine");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        // Override both connections so a developer's private Neon settings are never used by tests.
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.rabbitmq.host", RABBIT::getHost);
        properties.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        properties.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        properties.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        properties.add("spring.rabbitmq.virtual-host", () -> "/");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MerchantRepository merchants;
    @Autowired ApiCredentialRepository credentials;
    @Autowired MerchantApiKeyAuthenticator authenticator;

    UUID newUser() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO swiftdrop.users (id, email, full_name, active, authorization_version, version) "
                + "VALUES (?, ?, ?, true, 0, 0)", id, "m2-" + id + "@example.test", "Test Owner");
        return id;
    }

    void insertMerchantRow(UUID owner, String status, String reason) {
        jdbc.update("INSERT INTO swiftdrop.merchants (owner_user_id, business_name, contact_email, "
                + "approval_status, review_reason) VALUES (?, 'Shop', 'shop@example.test', ?, ?)", owner, status, reason);
    }

    UUID insertMerchantRow() {
        var owner = newUser();
        insertMerchantRow(owner, "PENDING", null);
        return jdbc.queryForObject("SELECT id FROM swiftdrop.merchants WHERE owner_user_id = ?", UUID.class, owner);
    }

    void insertCredentialRow(UUID merchantId, String prefix, String hash) {
        jdbc.update("INSERT INTO swiftdrop.api_credentials (merchant_id, key_prefix, secret_hash) VALUES (?, ?, ?)",
                merchantId, prefix, hash);
    }

    @Test
    void merchantEntityRoundTripsThroughTheRealTable() {
        var owner = newUser();

        merchants.saveAndFlush(Merchant.register(owner, "Lanka Crafts", "shop@example.test", "+94 77 123 4567", null));

        var found = merchants.findByOwnerUserId(owner).orElseThrow();
        assertThat(found.getBusinessName()).isEqualTo("Lanka Crafts");
        assertThat(found.getApprovalStatus()).isEqualTo(MerchantApprovalStatus.PENDING);
        assertThat(found.getContactPhone()).isEqualTo("+94 77 123 4567");
        assertThat(found.getBusinessAddress()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void ownerCanHaveOnlyOneBusiness() {
        var owner = newUser();
        insertMerchantRow(owner, "PENDING", null);

        assertThatThrownBy(() -> insertMerchantRow(owner, "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void merchantMustBelongToAnExistingUser() {
        assertThatThrownBy(() -> insertMerchantRow(UUID.randomUUID(), "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownApprovalStatusIsRejected() {
        var owner = newUser();

        assertThatThrownBy(() -> insertMerchantRow(owner, "BANNED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionNeedsAReason() {
        var withoutReason = newUser();
        assertThatThrownBy(() -> insertMerchantRow(withoutReason, "REJECTED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionWithAReasonIsAccepted() {
        var owner = newUser();

        assertThatCode(() -> insertMerchantRow(owner, "REJECTED", "Incomplete documents"))
                .doesNotThrowAnyException();
    }

    @Test
    void credentialHashMustBeSixtyFourLowercaseHexCharacters() {
        var merchantId = insertMerchantRow();

        assertThatThrownBy(() -> insertCredentialRow(merchantId, "sdk_aaaaaaaaaa", "not-a-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validCredentialHashIsAccepted() {
        var merchantId = insertMerchantRow();

        assertThatCode(() -> insertCredentialRow(merchantId, "sdk_bbbbbbbbbb", "a".repeat(64)))
                .doesNotThrowAnyException();
    }

    @Test
    void credentialPrefixMustBeUnique() {
        var merchantId = insertMerchantRow();
        insertCredentialRow(merchantId, "sdk_cccccccccc", "a".repeat(64));

        assertThatThrownBy(() -> insertCredentialRow(merchantId, "sdk_cccccccccc", "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvedMerchantKeyAuthenticatesUntilItIsRevoked() {
        var owner = newUser();
        var merchant = Merchant.register(owner, "Shop", "shop@example.test", null, null);
        merchant.approve(newUser(), Instant.now());
        merchants.saveAndFlush(merchant);
        var generated = new ApiKeyGenerator().generate();
        var credential = credentials.saveAndFlush(
                ApiCredential.issue(merchant.getId(), generated.prefix(), generated.hash(), "quote,order"));

        var principal = authenticator.authenticate(generated.key());
        assertThat(principal.merchantId()).isEqualTo(merchant.getId());
        assertThat(principal.scopes()).isEqualTo(Set.of("quote", "order"));
        assertThat(credentials.findById(credential.getId()).orElseThrow().getLastUsedAt()).isNotNull();

        credential.revoke(Instant.now());
        credentials.saveAndFlush(credential);

        assertThatThrownBy(() -> authenticator.authenticate(generated.key()))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void keyOfAPendingBusinessIsDenied() {
        var merchant = Merchant.register(newUser(), "Shop", "shop@example.test", null, null);
        merchants.saveAndFlush(merchant);
        var generated = new ApiKeyGenerator().generate();
        credentials.saveAndFlush(ApiCredential.issue(merchant.getId(), generated.prefix(), generated.hash(), "quote,order"));

        assertThatThrownBy(() -> authenticator.authenticate(generated.key()))
                .isInstanceOf(MerchantNotApprovedException.class);
    }

    @Test
    void anotherMerchantsKeyCannotBeUsedToReachTheFirstMerchant() {
        var first = Merchant.register(newUser(), "First", "first@example.test", null, null);
        first.approve(newUser(), Instant.now());
        merchants.saveAndFlush(first);
        var second = Merchant.register(newUser(), "Second", "second@example.test", null, null);
        second.approve(newUser(), Instant.now());
        merchants.saveAndFlush(second);
        var secondKey = new ApiKeyGenerator().generate();
        credentials.saveAndFlush(ApiCredential.issue(second.getId(), secondKey.prefix(), secondKey.hash(), "quote,order"));

        var principal = authenticator.authenticate(secondKey.key());

        assertThat(principal.merchantId()).isEqualTo(second.getId()).isNotEqualTo(first.getId());
    }
}