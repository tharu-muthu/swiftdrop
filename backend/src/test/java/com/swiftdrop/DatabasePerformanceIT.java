package com.swiftdrop;

import com.swiftdrop.repository.execution.ParcelLifecycleRepository;
import com.swiftdrop.repository.identity.*;
import com.swiftdrop.service.identity.*;
import com.swiftdrop.dto.identity.IdentityRole;
import com.swiftdrop.security.IdentityContext;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@DataJpaTest(showSql = false, properties = {
        "spring.config.import=", "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabasePerformanceIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.flyway.url", DB::getJdbcUrl);
        p.add("spring.flyway.user", DB::getUsername);
        p.add("spring.flyway.password", DB::getPassword);
    }
    @Autowired JdbcTemplate sql;
    @Autowired ParcelLifecycleRepository lifecycles;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EntityManagerFactory emf;

    UUID user() {
        UUID id = UUID.randomUUID();
        sql.update("insert into swiftdrop.users(id,email,full_name) values(?,?,'Query fixture')", id, id + "@example.test");
        return id;
    }
    void booking(UUID owner, String state, String status) {
        UUID id = UUID.randomUUID();
        sql.update("""
                insert into swiftdrop.parcel_bookings(id,customer_id,tracking_number,booking_state,
                pickup_address_snapshot,dropoff_address_snapshot,parcel_description,parcel_weight_grams)
                values(?,?,?,?,'Pickup','Dropoff','Parcel',100)
                """, id, owner, "SDP-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT), state);
        if (status != null) sql.update("insert into swiftdrop.parcel_lifecycles(booking_id,status) values(?,?)", id, status);
    }

    @Test void countersIncludeMissingLifecyclesAndStayIsolatedByCustomer() {
        UUID owner = user(), other = user();
        booking(owner, "CREATED", null);
        booking(owner, "CONFIRMED", "IN_TRANSIT");
        booking(owner, "CONFIRMED", "DELIVERED");
        booking(owner, "CREATED", "FAILED");
        booking(owner, "CANCELLED", "CREATED");
        booking(other, "CONFIRMED", "DELIVERED");
        var counts = lifecycles.customerCounts(owner);
        assertThat(counts.getTotal()).isEqualTo(5);
        assertThat(counts.getPending()).isEqualTo(2);
        assertThat(counts.getInTransit()).isEqualTo(1);
        assertThat(counts.getDelivered()).isEqualTo(1);
        assertThat(lifecycles.customerCounts(UUID.randomUUID()).getTotal()).isZero();
    }

    @Test void displayLookupReturnsOnlyRequestedAccounts() {
        UUID id = user();
        user();
        assertThat(users.displayForIds(Set.of(id))).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(id);
            assertThat(row.getFullName()).isEqualTo("Query fixture");
            assertThat(row.getEmail()).isEqualTo(id + "@example.test");
        });
    }

    @Test void adminPageUsesOneRoleQueryRegardlessOfPageSize() {
        for (int i = 0; i < 30; i++) {
            UUID id = user();
            sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name in ('CUSTOMER','COURIER')", id);
        }
        var identities = mock(IdentityAccountService.class);
        when(identities.requireCurrentRole(IdentityRole.DISPATCHER_ADMIN)).thenReturn(
                new IdentityContext(UUID.randomUUID(), UUID.randomUUID(), Set.of("DISPATCHER_ADMIN"), 0));
        var service = new DefaultAdminUserService(identities, users, roles,
                mock(AuthSessionRepository.class), mock(IdentityAuditRepository.class), Clock.systemUTC());
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        for (int size : List.of(5, 20)) {
            stats.clear();
            var page = service.list("", "CUSTOMER", 0, size);
            assertThat(page.items()).hasSize(size).allSatisfy(row ->
                    assertThat(row.roles()).containsExactlyInAnyOrder("CUSTOMER", "COURIER"));
            // Page SELECT + count SELECT + one batched roles SELECT.
            assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        }
    }

    @Test void migratedIndexesSupportOrderedUserPageWithRealPlanner() {
        sql.update("""
                insert into swiftdrop.users(email,full_name,created_at,updated_at)
                select 'plan-'||n||'@example.test','Plan fixture',
                  current_timestamp - n * interval '1 second',current_timestamp
                from generate_series(1,6000) n
                """);
        sql.execute("analyze swiftdrop.users");
        var plan = String.join("\n", sql.queryForList(
                "explain (analyze, buffers) select * from swiftdrop.users order by created_at desc,id limit 20", String.class));
        assertThat(plan).contains("ix_users_created_id");
        assertThat(sql.queryForObject("select count(*) from pg_indexes where schemaname='swiftdrop' and indexname in ('ix_lifecycles_updated_id','ix_lifecycles_unassigned_updated','ix_lifecycles_open_updated','ix_assignments_booking_time','ix_assignments_courier_time','ix_status_history_completion_time','ix_notification_jobs_created')", Integer.class)).isEqualTo(7);
        assertThat(sql.queryForObject("select success from swiftdrop.flyway_schema_history where version='14'", Boolean.class)).isTrue();
    }
}
