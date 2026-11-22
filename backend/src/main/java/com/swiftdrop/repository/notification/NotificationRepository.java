package com.swiftdrop.repository.notification;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<String> template(String status) {
        return jdbc.query("select body from swiftdrop.notification_templates where status=? and enabled=true",(rs,row)->rs.getString(1),status).stream().findFirst();
    }
    public void enqueue(UUID eventId,UUID bookingId,String phone,String message) {
        jdbc.update("insert into swiftdrop.notification_jobs(event_id,booking_id,recipient_phone,message) values (?,?,?,?) on conflict(event_id) do nothing",eventId,bookingId,phone,message);
    }
    public Optional<Job> claim() {
        var claimed=jdbc.query("""
                update swiftdrop.notification_jobs set attempts=attempts+1,next_attempt_at=current_timestamp+interval '2 minutes'
                where id=(select id from swiftdrop.notification_jobs where state in ('PENDING','RETRYING')
                and next_attempt_at<=current_timestamp order by next_attempt_at,id limit 1 for update skip locked)
                returning id,recipient_phone,message,attempts
                """,(rs,row)->new Job(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getInt(4))).stream().findFirst();
        claimed.ifPresent(job->{
            jdbc.update("update swiftdrop.notification_attempts set failure_code='LEASE_EXPIRED' where job_id=? and failure_code='IN_PROGRESS'",job.id());
            jdbc.update("insert into swiftdrop.notification_attempts(job_id,attempt_no,successful,failure_code) values (?,?,false,'IN_PROGRESS')",job.id(),job.attempt());
        });
        return claimed;
    }
    public void complete(Job job,boolean successful,String reference,String error,Instant retryAt) {
        jdbc.update("""
                update swiftdrop.notification_attempts set successful=?,failure_code=? where job_id=? and attempt_no=?
                """,successful,error,job.id(),job.attempt());
        jdbc.update("""
                update swiftdrop.notification_jobs set state=?,provider_reference=?,last_error=?,next_attempt_at=?,
                completed_at=case when ? then current_timestamp else null end where id=? and attempts=?
                """,successful?"SENT":job.attempt()>=5?"DEAD":"RETRYING",reference,error,java.sql.Timestamp.from(retryAt),
                successful||job.attempt()>=5,job.id(),job.attempt());
    }
    public List<Map<String,Object>> templates() { return jdbc.queryForList("select status,body,enabled,version from swiftdrop.notification_templates order by status"); }
    public boolean updateTemplate(String status,String body,boolean enabled,long version) {
        return jdbc.update("update swiftdrop.notification_templates set body=?,enabled=?,version=version+1,updated_at=current_timestamp where status=? and version=?",body,enabled,status,version)==1;
    }
    public List<Map<String,Object>> jobs() {
        return jdbc.queryForList("""
                select id,event_id,booking_id,'***'||right(recipient_phone,4) as recipient,state,attempts,last_error,provider_reference,created_at,completed_at
                from swiftdrop.notification_jobs order by created_at desc limit 100
                """);
    }
    public List<Map<String,Object>> attempts(UUID id) { return jdbc.queryForList("select attempt_no,successful,failure_code,attempted_at from swiftdrop.notification_attempts where job_id=? order by attempt_no",id); }
    public boolean retry(UUID id) { return jdbc.update("update swiftdrop.notification_jobs set state='RETRYING',next_attempt_at=current_timestamp,completed_at=null where id=? and state='DEAD'",id)==1; }
    public record Job(UUID id,String phone,String message,int attempt) { }
}
