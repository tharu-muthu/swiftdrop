package com.swiftdrop;

import com.fasterxml.jackson.databind.*;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** HTTP/security and PostgreSQL coverage; all accounts and parcels are disposable fixtures. */
@Testcontainers
@org.springframework.test.annotation.DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "swiftdrop.google.enabled=false", "swiftdrop.events.enabled=false", "spring.rabbitmq.password=fixture-only"})
class MerchantDispatchMonitoringIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl); p.add("spring.datasource.username",POSTGRES::getUsername); p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.flyway.url",POSTGRES::getJdbcUrl); p.add("spring.flyway.user",POSTGRES::getUsername); p.add("spring.flyway.password",POSTGRES::getPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    static final String PASSWORD="Monitoring fixture password 12";
    record Account(UUID id,String email,String token) { }
    Account account(String... roles) throws Exception {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values (?,?,?, ?,current_timestamp)",id,email,"Monitoring Fixture",passwords.encode(PASSWORD));
        for(String role:roles) sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name=?",id,role);
        String token=ok(call("/api/v1/auth/login",HttpMethod.POST,Map.of("email",email,"password",PASSWORD),null),200).get("accessToken").asText();
        return new Account(id,email,token);
    }
    HttpHeaders headers(String token) {
        var csrf=http.getForEntity("/api/v1/auth/csrf",CsrfResponse.class);
        var h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.setOrigin("http://localhost:5173");
        h.set(csrf.getBody().headerName(),csrf.getBody().token()); h.set(HttpHeaders.COOKIE,csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]);
        if(token!=null)h.setBearerAuth(token); return h;
    }
    ResponseEntity<String> call(String path,HttpMethod method,Object body,String token) {
        return http.exchange(path,method,new HttpEntity<>(body,headers(token)),String.class);
    }
    JsonNode ok(ResponseEntity<String> response,int status) throws Exception {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        return response.getBody()==null?json.nullNode():json.readTree(response.getBody());
    }
    UUID merchant(Account owner) {
        UUID id=UUID.randomUUID();
        sql.update("insert into swiftdrop.merchants(id,owner_user_id,business_name,contact_email,approval_status) values (?,?,'Monitoring Store',?,'APPROVED')",id,owner.id(),owner.email());
        return id;
    }
    UUID booking(UUID owner,boolean merchant,Instant created) {
        UUID id=UUID.randomUUID();
        sql.update("insert into swiftdrop.parcel_bookings(id,customer_id,merchant_id,tracking_number,pickup_address_snapshot,dropoff_address_snapshot,parcel_description,parcel_weight_grams,quoted_amount_minor,currency_code,pickup_city,delivery_city,created_at,updated_at) values (?,?,?,?, 'Pickup fixture','Delivery fixture','Books',1000,45000,'LKR','Colombo','Kandy',?,?)",
            id,merchant?null:owner,merchant?owner:null,"SDP-"+id.toString().replace("-","").toUpperCase(Locale.ROOT),Timestamp.from(created),Timestamp.from(created));
        return id;
    }
    void lifecycle(UUID booking,String status,UUID courier,Instant updated) {
        sql.update("insert into swiftdrop.parcel_lifecycles(booking_id,status,assigned_courier_id,created_at,updated_at) values (?,?,?,?,?)",
            booking,status,courier,Timestamp.from(updated.minusSeconds(3600)),Timestamp.from(updated));
    }
    List<String> ids(JsonNode rows,String field) {
        return StreamSupport.stream(rows.spliterator(),false).map(row->row.get(field).asText()).toList();
    }

    @Test void merchantPagesBeyondOneHundredIncludeCanonicalStatusAndKeepOwnershipAndPartnerShape() throws Exception {
        var owner=account("CUSTOMER","MERCHANT"); var other=account("CUSTOMER","MERCHANT"); var customer=account("CUSTOMER");
        UUID business=merchant(owner), otherBusiness=merchant(other);
        Instant created=Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var expected=new HashSet<String>(); UUID delivered=null;
        for(int i=0;i<105;i++) {
            UUID id=booking(business,true,created); expected.add(id.toString());
            lifecycle(id,i%3==0?"DELIVERED":i%3==1?"IN_TRANSIT":"FAILED",null,created.plusSeconds(60));
            if(i==0)delivered=id;
        }
        UUID foreign=booking(otherBusiness,true,created);
        lifecycle(foreign,"CREATED",null,created);
        var first=ok(call("/api/v1/merchant/dashboard/orders?size=100",HttpMethod.GET,null,owner.token()),200);
        var second=ok(call("/api/v1/merchant/dashboard/orders?page=1&size=100",HttpMethod.GET,null,owner.token()),200);
        assertThat(first.get("totalElements").asLong()).isEqualTo(105); assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("items").size()).isEqualTo(100); assertThat(second.get("items").size()).isEqualTo(5);
        var all=new ArrayList<>(ids(first.get("items"),"id")); all.addAll(ids(second.get("items"),"id"));
        assertThat(all).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        assertThat(ids(first.get("items"),"deliveryStatus")).contains("DELIVERED","IN_TRANSIT","FAILED");
        assertThat(first.get("items").get(0).hasNonNull("statusUpdatedAt")).isTrue();
        assertThat(first.get("items").get(0).get("bookingState").asText()).isEqualTo("CREATED");
        var detail=ok(call("/api/v1/merchant/dashboard/orders/"+delivered,HttpMethod.GET,null,owner.token()),200);
        assertThat(detail.get("id").asText()).isEqualTo(delivered.toString()); assertThat(detail.get("deliveryStatus").asText()).isEqualTo("DELIVERED");
        assertThat(call("/api/v1/merchant/dashboard/orders/"+foreign,HttpMethod.GET,null,owner.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call("/api/v1/merchant/dashboard/orders",HttpMethod.GET,null,customer.token()).getStatusCode().value()).isEqualTo(404);
        for(String query:List.of("page=-1","size=0","size=101"))
            assertThat(call("/api/v1/merchant/dashboard/orders?"+query,HttpMethod.GET,null,owner.token()).getStatusCode().value()).isEqualTo(400);
        var defaults=ok(call("/api/v1/merchant/dashboard/orders",HttpMethod.GET,null,owner.token()),200);
        assertThat(defaults.get("items").size()).isEqualTo(20); assertThat(defaults.get("totalPages").asInt()).isEqualTo(6);
        String key=ok(call("/api/v1/merchant/api-keys",HttpMethod.POST,null,owner.token()),201).get("apiKey").asText();
        var partner=new HttpHeaders(); partner.set("X-API-Key",key);
        var response=http.exchange("/api/v1/merchant/orders?page=1&size=100",HttpMethod.GET,new HttpEntity<>(partner),String.class);
        var array=ok(response,200); assertThat(array.isArray()).isTrue(); assertThat(array.size()).isEqualTo(5);
        assertThat(ids(array,"id")).containsExactlyElementsOf(ids(second.get("items"),"id"));
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("105");
        assertThat(response.getHeaders().getFirst("X-Total-Pages")).isEqualTo("2");
    }

    @Test void overdueFilterIdentifiesOnlyOpenParcelsAndCombinesWithUnassignedBeforePaging() throws Exception {
        var admin=account("CUSTOMER","DISPATCHER_ADMIN"); var customer=account("CUSTOMER"); var courier=account("CUSTOMER","COURIER");
        Instant now=Instant.now();
        UUID oldest=booking(customer.id(),false,now.minus(Duration.ofDays(4)));
        lifecycle(oldest,"CREATED",null,now.minus(Duration.ofHours(36)));
        UUID transit=booking(customer.id(),false,now.minus(Duration.ofDays(4)));
        lifecycle(transit,"IN_TRANSIT",courier.id(),now.minus(Duration.ofHours(30)));
        for(String status:List.of("DELIVERED","FAILED","CREATED")) {
            UUID id=booking(customer.id(),false,now.minus(Duration.ofDays(4)));
            lifecycle(id,status,null,now.minus(Duration.ofHours(status.equals("CREATED")?1:48)));
        }
        var first=ok(call("/api/v1/dispatch/bookings?overdue=true&size=1",HttpMethod.GET,null,admin.token()),200);
        var second=ok(call("/api/v1/dispatch/bookings?overdue=true&size=1&page=1",HttpMethod.GET,null,admin.token()),200);
        assertThat(first.get("totalElements").asLong()).isEqualTo(2); assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(ids(first.get("items"),"bookingId")).containsExactly(oldest.toString());
        assertThat(ids(second.get("items"),"bookingId")).containsExactly(transit.toString());
        assertThat(first.get("items").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(first.get("delayHours").asInt()).isEqualTo(24); assertThat(first.hasNonNull("overdueBefore")).isTrue();
        var unassigned=ok(call("/api/v1/dispatch/bookings?overdue=true&unassigned=true",HttpMethod.GET,null,admin.token()),200);
        assertThat(ids(unassigned.get("items"),"bookingId")).containsExactly(oldest.toString());
        assertThat(ok(call("/api/v1/dispatch/reports/daily",HttpMethod.GET,null,admin.token()),200).get("overdueOpenDeliveries").asLong()).isEqualTo(2);
        assertThat(call("/api/v1/dispatch/bookings?overdue=true",HttpMethod.GET,null,customer.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(call("/api/v1/dispatch/couriers/active",HttpMethod.GET,null,customer.token()).getStatusCode().value()).isEqualTo(403);
    }

    @Test void selectorExcludesIneligibleAccountsAndAssignmentRechecksAWithdrawnRole() throws Exception {
        var admin=account("CUSTOMER","DISPATCHER_ADMIN"); var customer=account("CUSTOMER");
        var good=account("CUSTOMER","COURIER"); var suspended=account("CUSTOMER","COURIER");
        var unverified=account("CUSTOMER","COURIER"); var lostRole=account("CUSTOMER"); var inactive=account("CUSTOMER","COURIER");
        for(var account:List.of(good,suspended,unverified,lostRole,inactive))
            sql.update("insert into swiftdrop.courier_profiles(user_id,vehicle_type,is_active) values (?,'VAN',?)",account.id(),!account.equals(inactive));
        sql.update("update swiftdrop.users set active=false where id=?",suspended.id());
        sql.update("update swiftdrop.users set email_verified_at=null where id=?",unverified.id());
        var fleet=ok(call("/api/v1/dispatch/couriers",HttpMethod.GET,null,admin.token()),200);
        assertThat(fleet.size()).isEqualTo(5);
        for(var row:fleet) {
            boolean eligible=row.get("courierId").asText().equals(good.id().toString());
            assertThat(row.get("eligible").asBoolean()).isEqualTo(eligible);
            if(!eligible)assertThat(row.get("unavailableReason").asText()).isIn("ACCOUNT_UNAVAILABLE","PROFILE_INACTIVE");
        }
        assertThat(ids(ok(call("/api/v1/dispatch/couriers/active",HttpMethod.GET,null,admin.token()),200),"courierId")).containsExactly(good.id().toString());
        UUID parcel=booking(customer.id(),false,Instant.now().minusSeconds(60)); lifecycle(parcel,"CREATED",null,Instant.now());
        ok(call("/api/v1/admin/users/"+good.id()+"/operational-roles",HttpMethod.POST,
            Map.of("role","COURIER","grant",false,"expectedVersion",0,"reason","Fixture role withdrawal"),admin.token()),200);
        assertThat(ok(call("/api/v1/dispatch/couriers/active",HttpMethod.GET,null,admin.token()),200).size()).isZero();
        assertThat(call("/api/v1/dispatch/assignments",HttpMethod.POST,Map.of("bookingId",parcel,"courierId",good.id()),admin.token()).getStatusCode().value()).isEqualTo(403);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.parcel_assignments where booking_id=?",Long.class,parcel)).isZero();
        assertThat(sql.queryForObject("select assigned_courier_id from swiftdrop.parcel_lifecycles where booking_id=?",UUID.class,parcel)).isNull();
    }
}
