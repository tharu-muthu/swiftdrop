package com.swiftdrop;

import com.fasterxml.jackson.databind.*;
import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.service.webhook.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "swiftdrop.google.enabled=false","swiftdrop.events.enabled=true","swiftdrop.events.relay-delay-ms=100","swiftdrop.notifications.worker-delay-ms=100","swiftdrop.webhooks.worker-delay-ms=100"})
class FullJourneyIT {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @Container static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:4.2.9-management-alpine");
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl); p.add("spring.datasource.username",POSTGRES::getUsername); p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.flyway.url",POSTGRES::getJdbcUrl); p.add("spring.flyway.user",POSTGRES::getUsername); p.add("spring.flyway.password",POSTGRES::getPassword);
        p.add("spring.rabbitmq.host",RABBIT::getHost); p.add("spring.rabbitmq.port",RABBIT::getAmqpPort); p.add("spring.rabbitmq.username",RABBIT::getAdminUsername); p.add("spring.rabbitmq.password",RABBIT::getAdminPassword); p.add("spring.rabbitmq.virtual-host",()->"/");
    }
    @Autowired TestRestTemplate http; @Autowired JdbcTemplate sql; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired WebhookWorker webhookWorker;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired com.swiftdrop.repository.notification.NotificationRepository notificationJobs;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    static final String PASSWORD="Journey fixture password 12";
    record Account(UUID id,String email,String token) { }
    Account account(String... roles) throws Exception {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values (?,?,?, ?,current_timestamp)",id,email,"Journey Fixture",passwords.encode(PASSWORD));
        for(String role:roles) sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name=?",id,role);
        return new Account(id,email,login(email));
    }
    String login(String email) throws Exception { return ok(call("/api/v1/auth/login",HttpMethod.POST,Map.of("email",email,"password",PASSWORD),null,null),200).get("accessToken").asText(); }
    HttpHeaders headers(String token) {
        var csrf=http.getForEntity("/api/v1/auth/csrf",CsrfResponse.class); var h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.setOrigin("http://localhost:5173");
        h.set(csrf.getBody().headerName(),csrf.getBody().token()); h.set(HttpHeaders.COOKIE,csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]); if(token!=null)h.setBearerAuth(token); return h;
    }
    ResponseEntity<String> call(String path,HttpMethod method,Object body,String token,String key) {
        var h=headers(token); if(key!=null)h.set("Idempotency-Key",key); return http.exchange(path,method,new HttpEntity<>(body,h),String.class);
    }
    JsonNode ok(ResponseEntity<String> response,int status) throws Exception { assertThat(response.getStatusCode().value()).isEqualTo(status); return response.getBody()==null?json.nullNode():json.readTree(response.getBody()); }
    UUID route() {
        UUID zone=UUID.randomUUID(); sql.update("insert into swiftdrop.zones(id,name) values (?,?)",zone,"Journey "+zone);
        // One test route is shared by all scenarios, with independent owners and quote IDs.
        sql.update("insert into swiftdrop.zone_postcodes(postcode,zone_id) values ('90101',?),('90102',?) on conflict(postcode) do nothing",zone,zone);
        UUID mapped=sql.queryForObject("select zone_id from swiftdrop.zone_postcodes where postcode='90101'",UUID.class);
        sql.update("insert into swiftdrop.rate_card_versions(pickup_zone_id,dropoff_zone_id,base_minor,included_kg,extra_kg_minor,effective_from) values (?,?,45000,1,10000,?)",mapped,mapped,java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        return mapped;
    }
    Map<String,Object> quoteInput() { return Map.of("pickupPostalCode","90101","dropoffPostalCode","90102","weightGrams",1000,"size","SMALL"); }
    Map<String,Object> bookingInput(String quote) { return Map.of("quoteId",quote,"pickup",Map.of("fullName","Sender","contactNumber","0771234567","address","10 Pickup Street","city","Colombo","postalCode","90101"),
        "recipient",Map.of("fullName","Recipient","contactNumber","0777654321","address","20 Delivery Street","city","Kandy","postalCode","90102"),"parcel",Map.of("weightGrams",1000,"size","SMALL","description","Books")); }
    static void until(BooleanSupplier condition) throws Exception { long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25); while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(50); assertThat(condition.getAsBoolean()).isTrue(); }
    long count(String sqlText,Object... args) { return sql.queryForObject(sqlText,Long.class,args); }

    @Test void customerJourneyCommitsBookingAssignmentDeliveryNotificationsAndReceipt() throws Exception {
        var providers=ok(call("/api/v1/auth/providers",HttpMethod.GET,null,null,null),200);
        assertThat(providers.size()).isEqualTo(1); assertThat(providers.get("googleEnabled").asBoolean()).isFalse();
        route(); var customer=account("CUSTOMER"); var other=account("CUSTOMER"); var admin=account("CUSTOMER","DISPATCHER_ADMIN"); var courier=account("CUSTOMER","COURIER");
        String quote=ok(call("/api/v1/quotes",HttpMethod.POST,quoteInput(),customer.token(),null),201).get("quoteId").asText();
        var body=bookingInput(quote); String key=UUID.randomUUID().toString();
        assertThat(call("/api/v1/bookings",HttpMethod.POST,body,customer.token(),null).getStatusCode().value()).isEqualTo(400);
        assertThat(call("/api/v1/bookings?size=100",HttpMethod.GET,null,customer.token(),null).getStatusCode().value()).isEqualTo(400);
        var created=ok(call("/api/v1/bookings",HttpMethod.POST,body,customer.token(),key),201); String id=created.get("id").asText(),tracking=created.get("trackingNumber").asText();
        var detail=ok(call("/api/v1/bookings/"+id,HttpMethod.GET,null,customer.token(),null),200);
        assertThat(detail.get("id").asText()).isEqualTo(id); assertThat(detail.get("details").get("dropoffAddressSnapshot").asText()).isEqualTo("20 Delivery Street");
        sql.update("update swiftdrop.quotations set expires_at=current_timestamp-interval '1 minute' where id=?",UUID.fromString(quote));
        assertThat(ok(call("/api/v1/bookings",HttpMethod.POST,body,customer.token(),key),201).get("id").asText()).isEqualTo(id);
        var changed=new HashMap<>(body); changed.put("parcel",Map.of("weightGrams",1000,"size","SMALL","description","Different goods"));
        assertThat(call("/api/v1/bookings",HttpMethod.POST,changed,customer.token(),key).getStatusCode().value()).isEqualTo(409);
        assertThat(call("/api/v1/bookings/"+id,HttpMethod.GET,null,other.token(),null).getStatusCode().value()).isEqualTo(403);
        assertThat(call("/api/v1/bookings/"+id+"/receipt",HttpMethod.GET,null,customer.token(),null).getStatusCode().value()).isEqualTo(409);
        ok(call("/api/v1/dispatch/couriers/"+courier.id(),HttpMethod.PUT,Map.of("vehicleType","VAN","licensePlate","TEST-123","active",true),admin.token(),null),200);
        ok(call("/api/v1/dispatch/assignments",HttpMethod.POST,Map.of("bookingId",id,"courierId",courier.id()),admin.token(),null),200);
        assertThat(call("/api/v1/dispatch/assignments",HttpMethod.POST,Map.of("bookingId",id,"courierId",courier.id()),admin.token(),null).getStatusCode().value()).isEqualTo(409);
        String lifecycle=sql.queryForObject("select id::text from swiftdrop.parcel_lifecycles where booking_id=?",String.class,UUID.fromString(id));
        ok(call("/api/v1/courier/deliveries/"+lifecycle,HttpMethod.GET,null,courier.token(),null),200);
        var historyPage=ok(call("/api/v1/bookings",HttpMethod.GET,null,customer.token(),null),200);
        assertThat(historyPage.get("bookings").size()).isEqualTo(1);
        ok(call("/api/v1/dispatch/bookings",HttpMethod.GET,null,admin.token(),null),200);
        ok(call("/api/v1/dispatch/reports/daily",HttpMethod.GET,null,admin.token(),null),200);
        var liveClient=java.net.http.HttpClient.newHttpClient();
        var live=liveClient.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(http.getRootUri()+"/api/v1/tracking/"+tracking+"/events"))
                .header("Accept","text/event-stream").timeout(Duration.ofSeconds(10)).build(),java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        assertThat(live.statusCode()).isEqualTo(200);
        assertThat(live.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");
        var liveUpdates=new LinkedBlockingQueue<JsonNode>();
        var readerThread=Thread.startVirtualThread(()->{
            try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(live.body(),java.nio.charset.StandardCharsets.UTF_8))) {
                for(String line;(line=reader.readLine())!=null;) if(line.startsWith("data:"))liveUpdates.offer(json.readTree(line.substring(5).trim()));
            } catch(java.io.IOException closed) { }
        });
        try {
        assertThat(liveUpdates.poll(10,TimeUnit.SECONDS)).isNotNull();
        String previous="CREATED";
        for(String status:List.of("PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED")) {
            Map<String,Object> request=new HashMap<>(Map.of("expectedStatus",previous,"status",status)); if(status.equals("DELIVERED"))request.put("recipientName","Receiver");
            ok(call("/api/v1/courier/deliveries/"+lifecycle+"/status",HttpMethod.POST,request,courier.token(),null),200); previous=status;
        }
        JsonNode terminal=null; long liveDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<liveDeadline) {
            var update=liveUpdates.poll(1,TimeUnit.SECONDS);
            if(update!=null&&"DELIVERED".equals(update.get("currentStatus").asText())) {terminal=update;break;}
        }
        assertThat(terminal).isNotNull();assertThat(terminal.get("timeline").size()).isEqualTo(5);
        } finally { live.body().close();readerThread.interrupt();readerThread.join(1000);liveClient.close(); }
        var receipt=ok(call("/api/v1/bookings/"+id+"/receipt",HttpMethod.GET,null,customer.token(),null),200); assertThat(receipt.get("receiptState").asText()).isEqualTo("ISSUED");
        var dashboard = ok(call("/api/v1/bookings/dashboard",HttpMethod.GET,null,customer.token(),null),200);
        assertThat(dashboard.get("activeBookings").asLong()).isZero();
        assertThat(dashboard.get("deliveredBookings").asLong()).isEqualTo(1);
        assertThat(count("select count(*) from swiftdrop.parcel_assignments where booking_id=? and is_active",UUID.fromString(id))).isZero();
        var publicTracking=ok(call("/api/v1/tracking/"+tracking,HttpMethod.GET,null,null,null),200);
        assertThat(publicTracking.get("currentStatus").asText()).isEqualTo("DELIVERED"); assertThat(publicTracking.get("timeline").size()).isEqualTo(5);
        assertThat(publicTracking.toString()).doesNotContain("0777654321","20 Delivery Street","Receiver",customer.id().toString());
        until(()->count("select count(*) from swiftdrop.notification_jobs where booking_id=? and state='SENT'",UUID.fromString(id))==5);
        assertThat(count("select count(*) from swiftdrop.outbox_events where published_at is null and aggregate_id=?",UUID.fromString(lifecycle))).isZero();
        assertThat(count("select count(*) from swiftdrop.webhook_deliveries where payload_body::jsonb->>'parcelId'=?",id)).isZero();
        var envelope=sql.queryForObject("select payload::text from swiftdrop.outbox_events where aggregate_id=? order by occurred_at limit 1",String.class,UUID.fromString(lifecycle));
        rabbit.send("swiftdrop.parcel.events","parcel.status.changed",new org.springframework.amqp.core.Message(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        rabbit.send("","swiftdrop.notification.dead",new org.springframework.amqp.core.Message(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ok(call("/api/v1/admin/integrations/notification/replay",HttpMethod.POST,null,admin.token(),null),200);
        var eventId=UUID.fromString(json.readTree(envelope).get("eventId").asText());
        until(()->count("select count(*) from swiftdrop.integration_receipts where consumer='tracking' and event_id=?",eventId)==1);
        assertThat(count("select count(*) from swiftdrop.notification_jobs where booking_id=?",UUID.fromString(id))).isEqualTo(5);
    }

    @Test void merchantOrderUsesApprovedOwnerAndDurableSignedRetries() throws Exception {
        route(); var owner=account("CUSTOMER"); var admin=account("CUSTOMER","DISPATCHER_ADMIN");
        var application=ok(call("/api/v1/merchant/registration",HttpMethod.POST,Map.of("businessName","Journey Store","contactEmail",owner.email()),owner.token(),null),201);
        String merchant=application.get("id").asText(); ok(call("/api/v1/admin/merchants/"+merchant+"/approve",HttpMethod.POST,null,admin.token(),null),200);
        String token=login(owner.email());
        var signing=new WebhookSigningService(); var secret=new AtomicReference<String>(); var calls=new AtomicInteger(); var verified=new AtomicInteger();
        var receiver=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        receiver.createContext("/callback",exchange->{
            byte[] bytes=exchange.getRequestBody().readAllBytes();
            if(signing.verifyFresh(secret.get(),exchange.getRequestHeaders().getFirst("X-Swiftdrop-Timestamp"),bytes,exchange.getRequestHeaders().getFirst("X-Swiftdrop-Signature"),Instant.now(),Duration.ofMinutes(5)))verified.incrementAndGet();
            int status=calls.incrementAndGet()==1?503:200; exchange.sendResponseHeaders(status,-1); exchange.close();
        }); receiver.start();
        try {
            var subscription=ok(call("/api/v1/merchant/webhooks",HttpMethod.POST,Map.of("callbackUrl","http://127.0.0.1:"+receiver.getAddress().getPort()+"/callback","eventTypes",List.of("parcel.status.changed")),token,null),201);
            secret.set(subscription.get("signingSecret").asText()); String subscriptionId=subscription.get("subscription").get("id").asText();
            String key=ok(call("/api/v1/merchant/api-keys",HttpMethod.POST,null,token,null),201).get("apiKey").asText();
            var apiHeaders=new HttpHeaders(); apiHeaders.set("X-API-Key",key); apiHeaders.setContentType(MediaType.APPLICATION_JSON);
            String quote=ok(http.exchange("/api/v1/merchant/quotes",HttpMethod.POST,new HttpEntity<>(quoteInput(),apiHeaders),String.class),201).get("quoteId").asText();
            apiHeaders.set("Idempotency-Key",UUID.randomUUID().toString());
            var order=ok(http.exchange("/api/v1/merchant/orders",HttpMethod.POST,new HttpEntity<>(bookingInput(quote),apiHeaders),String.class),200);
            assertThat(order.get("merchantId").asText()).isEqualTo(merchant); assertThat(order.get("customerId").isNull()).isTrue();
            until(()->count("select count(*) from swiftdrop.webhook_deliveries where subscription_id=? and delivery_state='RETRYING'",UUID.fromString(subscriptionId))==1);
            sql.update("update swiftdrop.webhook_deliveries set next_attempt_at=current_timestamp where subscription_id=?",UUID.fromString(subscriptionId)); webhookWorker.run();
            until(()->count("select count(*) from swiftdrop.webhook_deliveries where subscription_id=? and delivery_state='DELIVERED'",UUID.fromString(subscriptionId))==1);
            assertThat(verified.get()).isEqualTo(2); assertThat(calls.get()).isEqualTo(2);
            assertThat(count("select count(*) from swiftdrop.webhook_delivery_attempts a join swiftdrop.webhook_deliveries d on d.id=a.delivery_id where d.subscription_id=?",UUID.fromString(subscriptionId))).isEqualTo(2);
            var merchantB=account("CUSTOMER");
            String secondBusiness=ok(call("/api/v1/merchant/registration",HttpMethod.POST,Map.of("businessName","Other Store","contactEmail",merchantB.email()),merchantB.token(),null),201).get("id").asText();
            ok(call("/api/v1/admin/merchants/"+secondBusiness+"/approve",HttpMethod.POST,null,admin.token(),null),200);
            assertThat(call("/api/v1/merchant/dashboard/orders/"+order.get("id").asText(),HttpMethod.GET,null,login(merchantB.email()),null).getStatusCode().value()).isEqualTo(403);
            ok(call("/api/v1/merchant/api-keys/"+sql.queryForObject("select id::text from swiftdrop.api_credentials where merchant_id=?",String.class,UUID.fromString(merchant))+"/revoke",HttpMethod.POST,null,token,null),200);
            assertThat(http.exchange("/api/v1/merchant/orders",HttpMethod.GET,new HttpEntity<>(apiHeaders),String.class).getStatusCode().value()).isEqualTo(401);
            UUID blockedEvent=UUID.randomUUID();
            var testEvent=new com.swiftdrop.dto.webhook.WebhookEvent(blockedEvent,"webhook.test",1,Instant.now(),UUID.fromString(merchant),UUID.fromString(order.get("id").asText()),order.get("trackingNumber").asText(),"TEST",0);
            sql.update("insert into swiftdrop.webhook_deliveries(subscription_id,event_id,event_type,payload_body,next_attempt_at) values (?,?,'webhook.test',?,current_timestamp+interval '1 day')",UUID.fromString(subscriptionId),blockedEvent,json.writeValueAsString(testEvent));
            sql.update("update swiftdrop.users set active=false where id=?",owner.id());
            sql.update("update swiftdrop.webhook_deliveries set next_attempt_at=current_timestamp where event_id=?",blockedEvent);webhookWorker.run();
            until(()->count("select count(*) from swiftdrop.webhook_deliveries where event_id=? and delivery_state='FAILED' and last_error='SUBSCRIPTION_DISABLED'",blockedEvent)==1);
            assertThat(calls.get()).isEqualTo(2);
        } finally { receiver.stop(0); }
    }

    @Test void simultaneousBookingRetriesCreateOneLifecycleAndExpiredQuotesCreateNothing() throws Exception {
        route(); var owner=account("CUSTOMER"); var other=account("CUSTOMER");
        String quote=ok(call("/api/v1/quotes",HttpMethod.POST,quoteInput(),owner.token(),null),201).get("quoteId").asText(); var body=bookingInput(quote); String key=UUID.randomUUID().toString();
        var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{start.await();return call("/api/v1/bookings",HttpMethod.POST,body,owner.token(),key);});
            var second=executor.submit(()->{start.await();return call("/api/v1/bookings",HttpMethod.POST,body,owner.token(),key);}); start.countDown();
            String id=ok(first.get(15,TimeUnit.SECONDS),201).get("id").asText();
            assertThat(ok(second.get(15,TimeUnit.SECONDS),201).get("id").asText()).isEqualTo(id);
            assertThat(count("select count(*) from swiftdrop.parcel_bookings where quote_id=?",UUID.fromString(quote))).isEqualTo(1);
            assertThat(count("select count(*) from swiftdrop.parcel_status_history h join swiftdrop.parcel_lifecycles l on l.id=h.lifecycle_id where l.booking_id=?",UUID.fromString(id))).isEqualTo(1);
        }
        String expiring=ok(call("/api/v1/quotes",HttpMethod.POST,quoteInput(),owner.token(),null),201).get("quoteId").asText();
        assertThat(call("/api/v1/bookings",HttpMethod.POST,bookingInput(expiring),other.token(),UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
        sql.update("update swiftdrop.quotations set expires_at=current_timestamp-interval '1 second' where id=?",UUID.fromString(expiring));
        assertThat(call("/api/v1/bookings",HttpMethod.POST,bookingInput(expiring),owner.token(),UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(409);
        assertThat(count("select count(*) from swiftdrop.parcel_bookings where quote_id=?",UUID.fromString(expiring))).isZero();
    }

    @Test void notificationClaimSurvivesWorkerLossAndRecoversAfterLeaseExpiry() throws Exception {
        route(); var owner=account("CUSTOMER");
        String quote=ok(call("/api/v1/quotes",HttpMethod.POST,quoteInput(),owner.token(),null),201).get("quoteId").asText();
        UUID booking=UUID.fromString(ok(call("/api/v1/bookings",HttpMethod.POST,bookingInput(quote),owner.token(),UUID.randomUUID().toString()),201).get("id").asText());
        UUID event=UUID.randomUUID(); var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var claimed=tx.execute(status->{
            notificationJobs.enqueue(event,booking,"0771234567","Recovery fixture");
            sql.update("update swiftdrop.notification_jobs set next_attempt_at=current_timestamp-interval '365 days' where event_id=?",event);
            return notificationJobs.claim();
        });
        UUID job=sql.queryForObject("select id from swiftdrop.notification_jobs where event_id=?",UUID.class,event);
        assertThat(claimed.orElseThrow().id()).isEqualTo(job);
        assertThat(tx.execute(status->notificationJobs.claim()).stream().noneMatch(row->row.id().equals(job))).isTrue();
        sql.update("update swiftdrop.notification_jobs set next_attempt_at=current_timestamp-interval '1 second' where id=?",job);
        until(()->count("select count(*) from swiftdrop.notification_jobs where id=? and state='SENT'",job)==1);
        assertThat(count("select count(*) from swiftdrop.notification_attempts where job_id=?",job)).isEqualTo(2);
        assertThat(count("select count(*) from swiftdrop.notification_attempts where job_id=? and failure_code='LEASE_EXPIRED'",job)).isEqualTo(1);
    }
}
