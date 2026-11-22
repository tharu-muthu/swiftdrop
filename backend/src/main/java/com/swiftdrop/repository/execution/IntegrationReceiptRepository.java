package com.swiftdrop.repository.execution;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IntegrationReceiptRepository {
    private final JdbcTemplate jdbc;
    public IntegrationReceiptRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean claim(String consumer,UUID eventId) {
        return jdbc.update("insert into swiftdrop.integration_receipts(consumer,event_id) values (?,?) on conflict do nothing",consumer,eventId)==1;
    }
}
