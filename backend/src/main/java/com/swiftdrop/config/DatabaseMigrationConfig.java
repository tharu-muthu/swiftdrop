package com.swiftdrop.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConnectionDetails;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runtime may use Neon's pooler; Flyway needs a direct connection to that same database. */
@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfig {
    @Bean
    FlywayConnectionDetails directMigrationConnection(DataSourceProperties application,
                                                      FlywayProperties migration) {
        // Resolve and validate before Boot builds Flyway's separate migration DataSource.
        String directUrl = MigrationConnectionPolicy.directUrl(application.determineUrl(), migration.getUrl());
        String user = migration.getUser() != null ? migration.getUser() : application.determineUsername();
        String password = migration.getPassword() != null ? migration.getPassword() : application.determinePassword();
        return new FlywayConnectionDetails() {
            @Override
            public String getJdbcUrl() { return directUrl; }
            @Override
            public String getUsername() { return user; }
            @Override
            public String getPassword() { return password; }
        };
    }
}
