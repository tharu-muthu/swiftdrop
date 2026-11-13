package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.dto.pricing.QuoteRequest;
import com.swiftdrop.entity.pricing.ParcelSize;
import com.swiftdrop.entity.pricing.RateCardVersion;
import com.swiftdrop.entity.pricing.Zone;
import com.swiftdrop.entity.pricing.ZonePostcode;
import com.swiftdrop.exception.PricingErrorCode;
import com.swiftdrop.exception.PricingException;
import com.swiftdrop.repository.pricing.QuotationRepository;
import com.swiftdrop.repository.pricing.RateCardRepository;
import com.swiftdrop.repository.pricing.ZonePostcodeRepository;
import com.swiftdrop.repository.pricing.ZoneRepository;
import com.swiftdrop.service.pricing.PricingService;
import com.swiftdrop.service.pricing.QuoteOwner;
import java.time.Instant;
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

/** M3 pricing tables against real PostgreSQL. Each test rolls back, so the database stays clean. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class PricingSchemaIT {
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

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired ZoneRepository zones;
    @Autowired ZonePostcodeRepository postcodes;
    @Autowired RateCardRepository rateCards;
    @Autowired QuotationRepository quotations;
    @Autowired PricingService pricing;

    private UUID newUser() {
        return jdbc.queryForObject(
                "insert into swiftdrop.users (email, full_name) values (?, ?) returning id", UUID.class,
                "m3-" + UUID.randomUUID() + "@example.test", "M3 Test");
    }

    private RateCardVersion rateCard(Zone from, Zone to) {
        return rateCards.saveAndFlush(new RateCardVersion(from.getId(), to.getId(),
                45_000, 1, 15_000, 0, 10_000, 25_000, START));
    }

    @Test
    void pricingTablesExistAfterMigrations() {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'swiftdrop'
                  and table_name in ('zones', 'zone_postcodes', 'rate_card_versions', 'quotations')
                """, Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void currentRateCardIsFoundForTheRouteInTheRightDirectionOnly() {
        Zone colombo = zones.saveAndFlush(new Zone("T Colombo"));
        Zone galle = zones.saveAndFlush(new Zone("T Galle"));
        rateCard(colombo, galle);

        assertThat(rateCards.findCurrent(colombo.getId(), galle.getId(), START.plusSeconds(60)))
                .get().extracting(RateCardVersion::getBaseMinor).isEqualTo(45_000L);
        assertThat(rateCards.findCurrent(galle.getId(), colombo.getId(), START.plusSeconds(60))).isEmpty();
        assertThat(rateCards.findCurrent(colombo.getId(), galle.getId(), START.minusSeconds(1))).isEmpty();
    }

    @Test
    void duplicatePostcodeIsRejected() {
        Zone a = zones.saveAndFlush(new Zone("T Zone A"));
        Zone b = zones.saveAndFlush(new Zone("T Zone B"));
        postcodes.saveAndFlush(new ZonePostcode("T0001", a.getId()));

        assertThatThrownBy(() -> postcodes.saveAndFlush(new ZonePostcode("T0001", b.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quotationWithNoOwnerIsRejectedByTheDatabase() {
        Zone a = zones.saveAndFlush(new Zone("T Zone C"));
        Zone b = zones.saveAndFlush(new Zone("T Zone D"));
        RateCardVersion rate = rateCard(a, b);

        assertThatThrownBy(() -> jdbc.update("""
                insert into swiftdrop.quotations
                  (rate_version_id, input_hash, weight_grams, size, currency, base_minor,
                   extra_weight_minor, size_surcharge_minor, amount_minor, expires_at)
                values (?, repeat('a', 64), 1000, 'S', 'LKR', 1, 0, 0, 1, now())
                """, rate.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quoteIsPricedSavedAndHiddenFromOtherUsers() {
        Zone colombo = zones.saveAndFlush(new Zone("T Colombo 2"));
        Zone galle = zones.saveAndFlush(new Zone("T Galle 2"));
        postcodes.saveAndFlush(new ZonePostcode("T0002", colombo.getId()));
        postcodes.saveAndFlush(new ZonePostcode("T0003", galle.getId()));
        rateCard(colombo, galle);

        QuoteOwner owner = QuoteOwner.customer(newUser());
        var quote = pricing.createQuote(owner, new QuoteRequest("T0002", "T0003", 1_500, ParcelSize.M));
        quotations.flush();

        // base 45000 + one extra kg 15000 + medium surcharge 10000
        assertThat(quote.amountMinor()).isEqualTo(70_000);
        assertThat(pricing.getQuote(quote.quoteId(), owner).amountMinor()).isEqualTo(70_000);

        QuoteOwner stranger = QuoteOwner.customer(newUser());
        assertThatThrownBy(() -> pricing.getQuote(quote.quoteId(), stranger))
                .isInstanceOfSatisfying(PricingException.class,
                        error -> assertThat(error.code()).isEqualTo(PricingErrorCode.QUOTE_NOT_FOUND));
    }
}