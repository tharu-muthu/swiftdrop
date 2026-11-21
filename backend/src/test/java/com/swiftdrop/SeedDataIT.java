package com.swiftdrop;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SeedDataIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @Test void referenceSeedReusesExistingZonesAndIsSafeToRepeatWithoutAccounts() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas("swiftdrop").defaultSchema("swiftdrop").load().migrate();
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var statement=connection.createStatement()) {
            UUID original=UUID.randomUUID(); statement.execute("insert into swiftdrop.zones(id,name) values ('"+original+"','Colombo')");
            String seed=new ClassPathResource("db/seed/m3_pricing_dev_seed.sql").getContentAsString(StandardCharsets.UTF_8);
            statement.execute(seed);statement.execute(seed);
            try(var result=statement.executeQuery("select count(*) from swiftdrop.rate_card_versions")) {result.next();assertThat(result.getLong(1)).isEqualTo(9);}
            try(var result=statement.executeQuery("select count(*) from swiftdrop.zone_postcodes")) {result.next();assertThat(result.getLong(1)).isEqualTo(5);}
            try(var result=statement.executeQuery("select zone_id from swiftdrop.zone_postcodes where postcode='00600'")) {result.next();assertThat(result.getObject(1,UUID.class)).isEqualTo(original);}
            try(var result=statement.executeQuery("select count(*) from swiftdrop.users")) {result.next();assertThat(result.getLong(1)).isZero();}
        }
    }
}
