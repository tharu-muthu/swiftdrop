package com.swiftdrop.repository.webhook;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookJobRepository {
    private final JdbcTemplate jdbc;
    public WebhookJobRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void enqueue(UUID merchant,UUID event,String type,String body) {
        jdbc.update("""
                insert into swiftdrop.webhook_deliveries(subscription_id,event_id,event_type,payload_body,next_attempt_at)
                select id,?,?,?,current_timestamp from swiftdrop.webhook_subscriptions
                where merchant_id=? and active=true and event_types @> cast(? as jsonb)
                on conflict(subscription_id,event_id) do nothing
                """,event,type,body,merchant,"[\""+type+"\"]");
    }
    public Optional<Job> claim() {
        var claimed=jdbc.query("""
                with candidate as (select id from swiftdrop.webhook_deliveries
                  where delivery_state in ('PENDING','RETRYING') and coalesce(next_attempt_at,created_at)<=current_timestamp
                  order by coalesce(next_attempt_at,created_at),id limit 1 for update skip locked),
                claimed as (update swiftdrop.webhook_deliveries d set attempt_count=attempt_count+1,
                  next_attempt_at=current_timestamp+interval '2 minutes' from candidate c where d.id=c.id returning d.*)
                select d.id,d.event_id,d.payload_body,d.attempt_count,s.callback_url,s.encrypted_signing_secret,s.signing_secret_nonce,
                  s.active as enabled,s.merchant_id
                from claimed d join swiftdrop.webhook_subscriptions s on s.id=d.subscription_id
                """,(rs,row)->new Job(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getInt(4),
                    rs.getString(5),rs.getBytes(6),rs.getBytes(7),rs.getBoolean(8),rs.getObject(9,UUID.class))).stream().findFirst();
        claimed.ifPresent(job->{
            jdbc.update("update swiftdrop.webhook_delivery_attempts set outcome='INTERRUPTED',error_message='LEASE_EXPIRED' where delivery_id=? and outcome='STARTED'",job.id());
            jdbc.update("insert into swiftdrop.webhook_delivery_attempts(delivery_id,attempt_number,outcome) values (?,?,'STARTED')",job.id(),job.attempt());
        });
        return claimed;
    }
    public void complete(Job job,String state,Integer status,String error,Instant started,Instant completed,Instant retry) {
        jdbc.update("""
                update swiftdrop.webhook_delivery_attempts set completed_at=?,http_status=?,outcome=?,error_message=?
                where delivery_id=? and attempt_number=?
                """,java.sql.Timestamp.from(completed),status,state,error,job.id(),job.attempt());
        jdbc.update("""
                update swiftdrop.webhook_deliveries set delivery_state=?,last_http_status=?,last_error=?,next_attempt_at=?,completed_at=?
                where id=? and attempt_count=?
                """,state,status,error,retry==null?null:java.sql.Timestamp.from(retry),retry==null?java.sql.Timestamp.from(completed):null,job.id(),job.attempt());
    }
    public record Job(UUID id,UUID eventId,String body,int attempt,String endpoint,byte[] encryptedSecret,byte[] nonce,boolean enabled,UUID merchantId) { }
}
