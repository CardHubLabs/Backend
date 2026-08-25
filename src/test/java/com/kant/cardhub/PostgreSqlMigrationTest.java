package com.kant.cardhub;

import com.kant.cardhub.order.application.OrderApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres-test")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("cardhub")
            .withUsername("cardhub")
            .withPassword("cardhub");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OrderApplicationService orderApplicationService;

    @Test
    void appliesEveryMigrationAndValidatesTheJpaModel() {
        Integer migrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer latestVersion = jdbcTemplate.queryForObject(
                "select max(version::integer) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer tables = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from information_schema.tables
                 where table_schema = 'public'
                   and table_type = 'BASE TABLE'
                """,
                Integer.class
        );

        assertThat(migrations).isGreaterThanOrEqualTo(15);
        assertThat(latestVersion).isEqualTo(15);
        assertThat(tables).isGreaterThan(12);
        Set<String> criticalTables = Set.copyOf(jdbcTemplate.queryForList("""
                select table_name
                  from information_schema.tables
                 where table_schema = 'public'
                   and table_name in (
                       'users', 'listings', 'order_items', 'payments', 'shipments',
                       'trades', 'price_alerts', 'scheduled_jobs', 'payment_webhook_events'
                   )
                """, String.class));
        assertThat(criticalTables).containsExactlyInAnyOrder(
                "users", "listings", "order_items", "payments", "shipments",
                "trades", "price_alerts", "scheduled_jobs", "payment_webhook_events"
        );
    }

    @Test
    void concurrentBuyersCannotReserveTheSameListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID firstBuyerId = UUID.randomUUID();
        UUID secondBuyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        insertUser(sellerId, "seller", now);
        insertUser(firstBuyerId, "buyer-1", now);
        insertUser(secondBuyerId, "buyer-2", now);
        jdbcTemplate.update("""
                insert into listings(
                    id, seller_id, card_variant_id, card_condition, description,
                    price, shipping_fee, status, version, created_at, updated_at
                ) values (?, ?, '40000000-0000-0000-0000-000000000001', 'NEAR_MINT', null,
                          10000, 3000, 'ACTIVE', 0, ?, ?)
                """, listingId, sellerId, now, now);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (UUID buyerId : List.of(firstBuyerId, secondBuyerId)) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        orderApplicationService.createSingle(
                                buyerId, listingId, null, "Seoul test address");
                        return true;
                    } catch (RuntimeException exception) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from order_items where listing_id = ?", Long.class, listingId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "select status from listings where id = ?", String.class, listingId))
                    .isEqualTo("RESERVED");
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertUser(UUID id, String nickname, Instant now) {
        jdbcTemplate.update("""
                insert into users(
                    id, email, nickname, birth_date, terms_agreed_at, role, status,
                    created_at, updated_at, version
                ) values (?, ?, ?, date '2000-01-01', ?, 'USER', 'ACTIVE', ?, ?, 0)
                """, id, nickname + "@example.test", nickname, now, now, now);
    }
}
