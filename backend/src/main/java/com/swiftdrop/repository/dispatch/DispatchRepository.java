package com.swiftdrop.repository.dispatch;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DispatchRepository {
    private final JdbcTemplate jdbc;
    public DispatchRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lockBooking(UUID booking) { jdbc.queryForList("select 1 from pg_advisory_xact_lock(hashtextextended(?,0))","dispatch:"+booking); }
    public List<Map<String,Object>> couriers() { return jdbc.queryForList("""
            select p.user_id as "courierId",p.vehicle_type as "vehicleType",p.license_plate as "licensePlate",p.is_active as active,
              (select count(*) from swiftdrop.parcel_assignments a where a.courier_id=p.user_id and a.is_active) as workload
            from swiftdrop.courier_profiles p order by p.created_at
            """); }
    public boolean active(UUID courier) { return jdbc.query("select is_active from swiftdrop.courier_profiles where user_id=? for update",(rs,row)->rs.getBoolean(1),courier).stream().findFirst().orElse(false); }
    public long workload(UUID courier) { return jdbc.queryForObject("select count(*) from swiftdrop.parcel_assignments where courier_id=? and is_active",Long.class,courier); }
    public Optional<UUID> assigned(UUID booking) { return jdbc.query("select courier_id from swiftdrop.parcel_assignments where booking_id=? and is_active",(rs,row)->rs.getObject(1,UUID.class),booking).stream().findFirst(); }
    public UUID assign(UUID booking,UUID courier,UUID actor) {
        return jdbc.queryForObject("insert into swiftdrop.parcel_assignments(booking_id,courier_id,assigned_by) values (?,?,?) returning id",UUID.class,booking,courier,actor);
    }
    public void revoke(UUID booking,String reason) { jdbc.update("update swiftdrop.parcel_assignments set is_active=false,revoked_at=current_timestamp,revocation_reason=? where booking_id=? and is_active",reason,booking); }
    public void profile(UUID user,String vehicle,String plate,boolean active) {
        jdbc.update("""
                insert into swiftdrop.courier_profiles(user_id,vehicle_type,license_plate,is_active) values (?,?,?,?)
                on conflict(user_id) do update set vehicle_type=excluded.vehicle_type,license_plate=excluded.license_plate,is_active=excluded.is_active
                """,user,vehicle,plate,active);
    }
    public List<Map<String,Object>> assignmentHistory(UUID booking) { return jdbc.queryForList("select id,courier_id,assigned_by,assigned_at,revoked_at,revocation_reason,is_active from swiftdrop.parcel_assignments where booking_id=? order by assigned_at",booking); }
    public List<UUID> bookingsAssignedBetween(UUID courier, java.time.Instant start, java.time.Instant end) {
        return jdbc.query("select distinct booking_id from swiftdrop.parcel_assignments where courier_id=? and assigned_at>=? and assigned_at<?",
                (rs, row) -> rs.getObject(1, UUID.class), courier, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end));
    }
}
