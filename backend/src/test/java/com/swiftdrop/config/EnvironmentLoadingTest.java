package com.swiftdrop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentLoadingTest {
    @TempDir
    Path temporary;

    @Test
    void actualApplicationYamlLoadsRootThenBackendEnvFromTheDocumentedWorkingDirectory() throws Exception {
        Path backend = Files.createDirectory(temporary.resolve("backend"));
        Files.writeString(temporary.resolve(".env"), """
                SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/root_fixture
                SPRING_DATASOURCE_USERNAME=root_fixture
                SPRING_DATASOURCE_PASSWORD=root_fixture_only
                RABBITMQ_PASSWORD=broker_fixture_only
                """);
        Files.writeString(backend.resolve(".env"), """
                SPRING_DATASOURCE_URL=jdbc:postgresql://ep-fixture-pooler.region.aws.neon.tech/branch_fixture?sslmode=require
                SPRING_DATASOURCE_USERNAME=backend_fixture
                SPRING_DATASOURCE_PASSWORD=backend_fixture_only
                SPRING_FLYWAY_URL=jdbc:postgresql://ep-fixture.region.aws.neon.tech/branch_fixture?sslmode=require
                SPRING_FLYWAY_USER=migration_fixture
                SPRING_FLYWAY_PASSWORD=migration_fixture_only
                """);
        // A separate JVM gives relative ConfigData imports the same cwd as Maven spring-boot:run.
        // This probe binds configuration only: no autoconfiguration, database or network access.
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("surefire.test.class.path"),
                Probe.class.getName()).directory(backend.toFile()).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.startsWith("SPRING_")
                || key.startsWith("POSTGRES_") || key.startsWith("RABBITMQ_") || key.equals("BACKEND_PORT"));
        Path output = temporary.resolve("probe.log");
        builder.redirectOutput(output.toFile());
        var process = builder.start();
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).as("configuration-only probe completed").isTrue();
        assertThat(process.exitValue()).as("Configuration-only fixture probe: %s", Files.readString(output)).isZero();
        assertThat(Files.readString(output)).contains("ENV_IMPORTS_VERIFIED")
                .doesNotContain("backend_fixture_only", "migration_fixture_only", "root_fixture_only");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DataSourceProperties.class, FlywayProperties.class, HibernateProperties.class})
    static class ProbeConfiguration { }

    public static class Probe {
        public static void main(String[] args) {
            var application = new SpringApplication(ProbeConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setDefaultProperties(Map.of("spring.main.banner-mode", "off", "logging.level.root", "ERROR"));
            try (var context = application.run()) {
                var runtime = context.getBean(DataSourceProperties.class);
                var migration = context.getBean(FlywayProperties.class);
                boolean valid = runtime.getUrl().contains("ep-fixture-pooler.")
                        && "backend_fixture".equals(runtime.getUsername())
                        && "backend_fixture_only".equals(runtime.getPassword())
                        && "migration_fixture".equals(migration.getUser())
                        && "migration_fixture_only".equals(migration.getPassword())
                        && MigrationConnectionPolicy.directUrl(runtime.getUrl(), migration.getUrl())
                        .equals(runtime.getUrl().replace("ep-fixture-pooler.", "ep-fixture."))
                        && "validate".equals(context.getBean(HibernateProperties.class).getDdlAuto());
                if (!valid) {
                    throw new IllegalStateException("Environment binding check failed; values withheld.");
                }
                System.out.println("ENV_IMPORTS_VERIFIED");
            }
        }
    }
}
