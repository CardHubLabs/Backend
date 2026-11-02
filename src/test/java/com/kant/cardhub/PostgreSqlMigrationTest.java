package com.kant.cardhub;

import com.kant.cardhub.order.application.OrderApplicationService;
import com.kant.cardhub.market.infrastructure.MarketPriceStatRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

    @Autowired
    MarketPriceStatRepository marketPriceStatRepository;

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

        assertThat(migrations).isGreaterThanOrEqualTo(23);
        assertThat(latestVersion).isEqualTo(23);
        assertThat(tables).isGreaterThan(12);
        Set<String> criticalTables = Set.copyOf(jdbcTemplate.queryForList("""
                select table_name
                  from information_schema.tables
                 where table_schema = 'public'
                   and table_name in (
                       'users', 'listings', 'order_items', 'payments', 'shipments',
                       'trades', 'price_alerts', 'scheduled_jobs', 'payment_webhook_events',
                       'shipping_carriers'
                   )
                """, String.class));
        assertThat(criticalTables).containsExactlyInAnyOrder(
                "users", "listings", "order_items", "payments", "shipments",
                "trades", "price_alerts", "scheduled_jobs", "payment_webhook_events",
                "shipping_carriers"
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
                """, listingId, sellerId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

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

    @Test
    void upgradesRepresentativeV015DataThroughV023() {
        String schema = "upgrade_test";
        jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        jdbcTemplate.execute("create schema " + schema);
        try {
            migrateSchema(schema, "15");
            JdbcTemplate upgrade = upgradeJdbc(schema);
            Instant now = Instant.parse("2026-08-25T00:00:00Z");
            UUID sellerId = UUID.fromString("81000000-0000-0000-0000-000000000001");
            UUID buyerId = UUID.fromString("81000000-0000-0000-0000-000000000002");
            UUID firstListingId = UUID.fromString("82000000-0000-0000-0000-000000000001");
            UUID secondListingId = UUID.fromString("82000000-0000-0000-0000-000000000002");
            UUID orderId = UUID.fromString("83000000-0000-0000-0000-000000000001");

            insertUpgradeUser(upgrade, sellerId, "seller", now);
            insertUpgradeUser(upgrade, buyerId, "buyer", now);
            insertUpgradeListing(upgrade, firstListingId, sellerId, 10_000, now);
            insertUpgradeListing(upgrade, secondListingId, sellerId, 9_000, now.plusSeconds(1));
            upgrade.update("""
                    insert into orders(
                        id, buyer_id, seller_id, item_subtotal, shipping_fee, total_amount,
                        status, payment_in_flight, reservation_expires_at,
                        shipping_address_ciphertext, shipping_address_key_version,
                        created_at, updated_at, version
                    ) values (?, ?, ?, 10000, 3000, 13000, 'PAYMENT_PENDING', false, ?,
                              'legacy-ciphertext', 'v1', ?, ?, 0)
                    """, orderId, buyerId, sellerId, timestamp(now.plusSeconds(600)),
                    timestamp(now), timestamp(now));
            upgrade.update("""
                    insert into order_items(
                        id, order_id, listing_id, card_variant_id, listing_title, unit_price, created_at
                    ) values (?, ?, ?, '40000000-0000-0000-0000-000000000001', 'Charizard', 10000, ?)
                    """, UUID.randomUUID(), orderId, firstListingId, timestamp(now));
            upgrade.update("""
                    insert into payments(
                        id, order_id, buyer_id, idempotency_key, payment_key, toss_order_id,
                        amount, status, created_at, updated_at, version
                    ) values (?, ?, ?, 'legacy-idem', 'legacy-payment-key', 'legacy-order-id',
                              13000, 'UNKNOWN', ?, ?, 0)
                    """, UUID.randomUUID(), orderId, buyerId, timestamp(now), timestamp(now));
            upgrade.update("""
                    insert into favorites(id, user_id, listing_id, created_at)
                    values (?, ?, ?, ?), (?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), buyerId, firstListingId, timestamp(now),
                    UUID.randomUUID(), buyerId, secondListingId, timestamp(now.plusSeconds(1)));
            upgrade.update("""
                    insert into price_alerts(
                        id, user_id, listing_id, baseline_price, threshold_percentage,
                        active, created_at, updated_at, version
                    ) values (?, ?, ?, 10000, 10, true, ?, ?, 0),
                             (?, ?, ?, 9000, 10, true, ?, ?, 0)
                    """,
                    UUID.randomUUID(), buyerId, firstListingId, timestamp(now), timestamp(now),
                    UUID.randomUUID(), buyerId, secondListingId,
                    timestamp(now.plusSeconds(1)), timestamp(now.plusSeconds(1)));

            migrateSchema(schema, null);

            assertThat(upgrade.queryForObject(
                    "select order_no from orders where id = ?", String.class, orderId))
                    .isEqualTo("CH-L-" + orderId.toString().replace("-", ""));
            assertThat(upgrade.queryForObject(
                    "select shipping_address_schema_version from orders where id = ?", Short.class, orderId))
                    .isEqualTo((short) 1);
            assertThat(upgrade.queryForObject(
                    "select card_condition_snapshot from order_items where order_id = ?", String.class, orderId))
                    .isEqualTo("NEAR_MINT");
            assertThat(upgrade.queryForObject(
                    "select shipping_fee_snapshot from order_items where order_id = ?", Long.class, orderId))
                    .isEqualTo(3_000L);
            assertThat(upgrade.queryForObject(
                    "select count(*) from favorites where user_id = ?", Long.class, buyerId)).isOne();
            assertThat(upgrade.queryForObject(
                    "select count(*) from price_alerts where user_id = ? and active", Long.class, buyerId)).isOne();
            assertThat(upgrade.queryForObject(
                    "select max(version::integer) from flyway_schema_history where success", Integer.class))
                    .isEqualTo(23);
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void calculatesSevenThirtyAndNinetyDayMediansAtInclusiveBoundaries() {
        Instant reference = Instant.parse("2026-09-01T00:00:00Z");
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        insertUser(sellerId, "market-seller-" + sellerId.toString().substring(0, 6), reference);
        insertUser(buyerId, "market-buyer-" + buyerId.toString().substring(0, 6), reference);
        jdbcTemplate.update("""
                insert into orders(
                    id, order_no, buyer_id, seller_id, item_subtotal, shipping_fee, total_amount,
                    status, payment_in_flight, reservation_expires_at,
                    shipping_address_ciphertext, shipping_address_key_version,
                    shipping_address_schema_version, created_at, updated_at, version
                ) values (?, ?, ?, ?, 36000, 0, 36000, 'CONFIRMED', false, ?,
                          'ciphertext', 'v1', 1, ?, ?, 0)
                """, orderId, "CH-" + orderId.toString().replace("-", ""), buyerId, sellerId,
                timestamp(reference), timestamp(reference), timestamp(reference));

        long[] prices = {1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000};
        long[] daysAgo = {1, 2, 6, 7, 8, 20, 40, 80};
        for (int index = 0; index < prices.length; index++) {
            insertMarketTrade(
                    orderId, buyerId, sellerId, variantId,
                    prices[index], reference.minus(java.time.Duration.ofDays(daysAgo[index])), false);
        }
        insertMarketTrade(orderId, buyerId, sellerId, variantId, 99_000,
                reference.minus(java.time.Duration.ofDays(1)), true);

        MarketPriceStatRepository.MarketAggregate aggregate = marketPriceStatRepository.aggregate(
                variantId,
                reference.minus(java.time.Duration.ofDays(7)),
                reference.minus(java.time.Duration.ofDays(30)),
                reference.minus(java.time.Duration.ofDays(90)));

        assertThat(aggregate.getRecentPrice()).isEqualTo(1_000L);
        assertThat(aggregate.getAverage7d()).isEqualTo(2_500L);
        assertThat(aggregate.getMedian7d()).isEqualTo(2_500L);
        assertThat(aggregate.getAverage30d()).isEqualTo(3_500L);
        assertThat(aggregate.getMedian30d()).isEqualTo(3_500L);
        assertThat(aggregate.getAverage90d()).isEqualTo(4_500L);
        assertThat(aggregate.getMedian90d()).isEqualTo(4_500L);
        assertThat(aggregate.getVolume30d()).isEqualTo(6L);
    }

    private void insertUser(UUID id, String nickname, Instant now) {
        jdbcTemplate.update("""
                insert into users(
                    id, email, nickname, birth_date, terms_agreed_at, role, status,
                    created_at, updated_at, version
                ) values (?, ?, ?, date '2000-01-01', ?, 'USER', 'ACTIVE', ?, ?, 0)
                """, id, nickname + "@example.test", nickname,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    private void migrateSchema(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.load().migrate();
    }

    private JdbcTemplate upgradeJdbc(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private void insertUpgradeUser(JdbcTemplate jdbc, UUID id, String nickname, Instant now) {
        jdbc.update("""
                insert into users(
                    id, email, nickname, birth_date, terms_agreed_at, role, status,
                    created_at, updated_at, version
                ) values (?, ?, ?, date '2000-01-01', ?, 'USER', 'ACTIVE', ?, ?, 0)
                """, id, nickname + "@example.test", nickname,
                timestamp(now), timestamp(now), timestamp(now));
    }

    private void insertUpgradeListing(JdbcTemplate jdbc, UUID id, UUID sellerId, long price, Instant now) {
        jdbc.update("""
                insert into listings(
                    id, seller_id, card_variant_id, card_condition, description,
                    price, shipping_fee, status, version, created_at, updated_at
                ) values (?, ?, '40000000-0000-0000-0000-000000000001', 'NEAR_MINT', null,
                          ?, 3000, 'ACTIVE', 0, ?, ?)
                """, id, sellerId, price, timestamp(now), timestamp(now));
    }

    private static java.sql.Timestamp timestamp(Instant value) {
        return java.sql.Timestamp.from(value);
    }

    private void insertMarketTrade(
            UUID orderId,
            UUID buyerId,
            UUID sellerId,
            UUID variantId,
            long price,
            Instant occurredAt,
            boolean excluded
    ) {
        UUID listingId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into listings(
                    id, seller_id, card_variant_id, card_condition, price, shipping_fee,
                    status, moderation_status, version, created_at, updated_at
                ) values (?, ?, ?, 'NEAR_MINT', ?, 0, 'SOLD', 'CLEAR', 0, ?, ?)
                """, listingId, sellerId, variantId, Math.max(price, 500),
                timestamp(occurredAt), timestamp(occurredAt));
        jdbcTemplate.update("""
                insert into order_items(
                    id, order_id, listing_id, card_variant_id, listing_title, unit_price,
                    card_condition_snapshot, shipping_fee_snapshot, created_at
                ) values (?, ?, ?, ?, 'Market test', ?, 'NEAR_MINT', 0, ?)
                """, orderItemId, orderId, listingId, variantId, price, timestamp(occurredAt));
        jdbcTemplate.update("""
                insert into trades(
                    id, order_id, order_item_id, listing_id, card_variant_id,
                    buyer_id, seller_id, price, occurred_at, excluded,
                    exclusion_reason, excluded_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, orderItemId, listingId, variantId,
                buyerId, sellerId, price, timestamp(occurredAt), excluded,
                excluded ? "test exclusion" : null,
                excluded ? timestamp(occurredAt) : null,
                timestamp(occurredAt));
    }
}
