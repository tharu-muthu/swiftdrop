package com.swiftdrop.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Validates destinations before Flyway opens its connection. Error messages never include URLs/secrets. */
final class MigrationConnectionPolicy {
    private static final Set<String> UNSAFE_URL_KEYS = Set.of(
            "user", "password", "host", "port", "database", "dbname", "service", "servicefile", "options");

    private MigrationConnectionPolicy() { }

    static String directUrl(String applicationUrl, String configuredMigrationUrl) {
        URI application = parse(applicationUrl);
        String derived = applicationUrl;
        if (isPooledNeon(application.getHost())) {
            // Replace only the authority hostname, never the database name or query parameters.
            derived = "jdbc:postgresql://" + directHost(application.getHost())
                    + (application.getPort() == -1 ? "" : ":" + application.getPort())
                    + application.getRawPath()
                    + (application.getRawQuery() == null ? "" : "?" + application.getRawQuery());
        }
        String selected = configuredMigrationUrl == null || configuredMigrationUrl.isBlank()
                ? derived : configuredMigrationUrl;
        URI migration = parse(selected);
        if (isPooledNeon(migration.getHost())) {
            throw new IllegalArgumentException("SPRING_FLYWAY_URL must use a direct Neon endpoint, without -pooler.");
        }
        if (!directHost(application.getHost()).equals(directHost(migration.getHost()))
                || port(application) != port(migration)
                || !application.getPath().equals(migration.getPath())) {
            throw new IllegalArgumentException("Application and Flyway must target the same endpoint and database. "
                    + "Check SPRING_DATASOURCE_URL and SPRING_FLYWAY_URL privately.");
        }
        return selected;
    }

    private static URI parse(String jdbcUrl) {
        try {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw invalidUrl();
            }
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPath() == null || uri.getPath().length() <= 1
                    || uri.getPath().substring(1).contains("/")) {
                throw invalidUrl();
            }
            if (uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String key = URLDecoder.decode(parameter.split("=", 2)[0], StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT);
                    if (UNSAFE_URL_KEYS.contains(key)) {
                        throw invalidUrl();
                    }
                }
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // Do not retain an exception cause: URI parser messages may contain credentials.
            throw invalidUrl();
        }
    }

    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException("Use a single-host PostgreSQL JDBC URL with a database name. "
                + "Keep credentials in separate environment variables; routing overrides in URL parameters are not supported.");
    }

    private static boolean isPooledNeon(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.endsWith(".neon.tech") && lower.substring(0, lower.indexOf('.')).endsWith("-pooler");
    }

    private static String directHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (!isPooledNeon(lower)) {
            return lower;
        }
        int dot = lower.indexOf('.');
        return lower.substring(0, dot - "-pooler".length()) + lower.substring(dot);
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? 5432 : uri.getPort();
    }
}
