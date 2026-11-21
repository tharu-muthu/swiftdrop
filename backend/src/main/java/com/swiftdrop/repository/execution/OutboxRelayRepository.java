package com.swiftdrop.repository.execution;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRelayRepository {
    private final JdbcTemplate jdbc;
    public OutboxRelayRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Entry> next() {
        return jdbc.query("select id,payload::text from swiftdrop.outbox_events where published_at is null order by occurred_at,id limit 1 for update skip locked",
                (rs,row)->new Entry(rs.getObject(1,UUID.class),rs.getString(2)));
    }
    public void published(UUID id) { jdbc.update("update swiftdrop.outbox_events set published_at=current_timestamp,attempts=attempts+1 where id=?",id); }
    public void failed(UUID id) { jdbc.update("update swiftdrop.outbox_events set attempts=attempts+1 where id=?",id); }
    public record Entry(UUID id,String payload) { }
}
