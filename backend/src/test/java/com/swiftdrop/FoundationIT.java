package com.swiftdrop;

import com.swiftdrop.dto.system.HealthResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2.9-management-alpine");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.rabbitmq.host", RABBIT::getHost);
        properties.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        properties.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        properties.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        properties.add("spring.rabbitmq.virtual-host", () -> "/");
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private RabbitTemplate rabbit;

    @Test
    void freshPostgresMigratesAndMigrationCanRunAgain() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void realHttpServerExposesHealthAndProtectsOtherRoutes() {
        var response = http.getForEntity("/api/v1/health", HealthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new HealthResponse("UP", "swiftdrop"));
        assertThat(http.getForEntity("/api/v1/private", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void brokerConnectionCanRoundTripAnInfrastructureProbe() {
        Boolean roundTrip = rabbit.execute(channel -> {
            String queue = channel.queueDeclare().getQueue();
            byte[] message = "foundation-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            channel.basicPublish("", queue, null, message);
            var received = channel.basicGet(queue, true);
            channel.queueDelete(queue);
            return received != null && java.util.Arrays.equals(message, received.getBody());
        });
        assertThat(roundTrip).isTrue();
    }
}
