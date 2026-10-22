/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.inventory.internal.domain.Inventory;
import com.example.retail.inventory.internal.domain.InventoryConflictException;
import com.example.retail.inventory.internal.domain.InventoryReservation;
import com.example.retail.inventory.internal.persistence.GeneratedInventoryRepository;
import com.example.retail.inventory.internal.persistence.InventoryRepository;
import com.example.retail.inventory.internal.persistence.InventoryReservationRepository;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.ReservationItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(ContainersConfig.class)
class InventoryReviewFixesIT {
    @Autowired private InventoryService service;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private GeneratedInventoryRepository generationRepository;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private ObjectMapper mapper;

    @Autowired private PostgreSQLContainer postgres;

    @Test
    void defaultLiquibaseContextExcludesSamples() {
        assertThat(inventoryRepository.findByProductCode("P001")).isEmpty();
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM catalog.products WHERE product_code = 'P001'")
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void generationFailureRollsBackKeyAndStockAndAllowsRetryAcrossInstances() {
        String key = "generation-rollback";
        String code = "ProductCode_" + key + "_0";
        inventoryRepository.save(new Inventory(null, code, 0, 0));
        jdbc.sql(
                        "ALTER TABLE inventory.inventory ADD CONSTRAINT test_generation_failure "
                                + "CHECK (product_code <> 'ProductCode_generation-rollback_0' OR quantity = 0)")
                .update();
        try {
            assertThatThrownBy(() -> service.updateGeneratedInventory(key, 1))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(keyCount(key)).isZero();
            assertThat(
                            inventoryRepository
                                    .findByProductCode(code)
                                    .orElseThrow()
                                    .getAvailableQuantity())
                    .isZero();
        } finally {
            jdbc.sql("ALTER TABLE inventory.inventory DROP CONSTRAINT test_generation_failure")
                    .update();
        }
        service.updateGeneratedInventory(key, 1);
        assertThat(keyCount(key)).isEqualTo(1);
        assertThat(inventoryRepository.findByProductCode(code).orElseThrow().getAvailableQuantity())
                .isBetween(1, 100);

        // Change stock independently, then replay through a fresh application service instance.
        jdbc.sql("UPDATE inventory.inventory SET quantity = 1000 WHERE product_code = :code")
                .param("code", code)
                .update();
        InventoryService freshService =
                new InventoryService(
                        inventoryRepository,
                        reservationRepository,
                        generationRepository,
                        publisher,
                        mapper);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(tx -> freshService.updateGeneratedInventory(key, 1));
        assertThat(inventoryRepository.findByProductCode(code).orElseThrow().getAvailableQuantity())
                .isEqualTo(1000);
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                "INSERT INTO inventory.generated_inventory_keys VALUES (:key)")
                                        .param("key", key)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentGenerationUpdatesStockOnce() throws Exception {
        String key = "generation-concurrent";
        String code = "ProductCode_" + key + "_0";
        inventoryRepository.save(new Inventory(null, code, 0, 0));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    service.updateGeneratedInventory(key, 1);
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertThat(keyCount(key)).isEqualTo(1);
        Inventory stored = inventoryRepository.findByProductCode(code).orElseThrow();
        assertThat(stored.getAvailableQuantity()).isBetween(1, 100);
        assertThat(stored.getVersion()).isEqualTo((short) 1);
    }

    @Test
    void concurrentReservationRetriesReturnStoredResultWithoutReservingTwice() throws Exception {
        String code = "review-concurrent-reservation";
        inventoryRepository.save(new Inventory(null, code, 10, 0));
        InventoryReservationRequest request =
                new InventoryReservationRequest(
                        "review-same-key", 800L, List.of(new ReservationItem(code, 2)));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<InventoryReservationStatus>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return service.reserve(request).status();
                                }));
            }
            start.countDown();
            for (Future<InventoryReservationStatus> future : futures) {
                assertThat(future.get(30, java.util.concurrent.TimeUnit.SECONDS))
                        .isEqualTo(InventoryReservationStatus.RESERVED);
            }
        }
        Inventory stored = inventoryRepository.findByProductCode(code).orElseThrow();
        assertThat(stored.getAvailableQuantity()).isEqualTo(8);
        assertThat(stored.getReservedItems()).isEqualTo(2);
        assertThat(reservationRepository.findByOrderId(800L)).hasSize(1);
    }

    @Test
    void deletionPreservesReservationUntilReleasedOrConsumed() {
        for (boolean release : List.of(true, false)) {
            String code = "review-delete-" + release;
            String key = "review-delete-key-" + release;
            Inventory inventory = inventoryRepository.save(new Inventory(null, code, 10, 0));
            service.reserve(
                    new InventoryReservationRequest(
                            key, 810L, List.of(new ReservationItem(code, 2))));
            assertThatThrownBy(() -> service.deleteInventoryById(inventory.getId()))
                    .isInstanceOf(InventoryConflictException.class);
            assertThat(inventoryRepository.findById(inventory.getId())).isPresent();
            if (release) {
                service.release(key);
            } else {
                service.consume(key);
            }
            service.deleteInventoryById(inventory.getId());
            assertThat(inventoryRepository.findById(inventory.getId())).isEmpty();
        }
    }

    @Test
    void activeReservationBlocksDeletionEvenWithZeroReservedCounter() {
        Inventory inventory =
                inventoryRepository.save(new Inventory(null, "review-zero-counter", 10, 0));
        Instant now = Instant.now();
        InventoryReservation reservation =
                reservationRepository.save(
                        new InventoryReservation(
                                null,
                                "review-zero-counter-key",
                                811L,
                                InventoryReservationStatus.RESERVED,
                                mapper.writeValueAsString(
                                        List.of(new ReservationItem("review-zero-counter", 1))),
                                now,
                                now));
        assertThatThrownBy(() -> service.deleteInventoryById(inventory.getId()))
                .isInstanceOf(InventoryConflictException.class);
        assertThat(inventoryRepository.findById(inventory.getId())).isPresent();
        reservation.setStatus(InventoryReservationStatus.CONSUMED);
        reservationRepository.save(reservation);
        service.deleteInventoryById(inventory.getId());
        assertThat(inventoryRepository.findById(inventory.getId())).isEmpty();
    }

    @Test
    void upgradePreservesExistingStockAndAcceptsOriginalSeedChecksum(@TempDir Path directory)
            throws Exception {
        jdbc.sql("CREATE DATABASE inventory_upgrade").update();
        Path baseline = directory.resolve("baseline.xml");
        Files.writeString(
                baseline,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <include file="db/changelog/inventory/00-create-inventory-schema.xml"/>
                    <include file="db/changelog/inventory/01-create-inventory-table.xml"/>
                    <include file="db/changelog/inventory/02-create-inventory-reservations-table.xml"/>
                    <include file="db/changelog/inventory/03-insert-sample-inventory-before-review.xml"/>
                </databaseChangeLog>
                """);
        String url =
                "jdbc:postgresql://"
                        + postgres.getHost()
                        + ":"
                        + postgres.getMappedPort(5432)
                        + "/inventory_upgrade";
        try (var resources =
                new CompositeResourceAccessor(
                        new DirectoryResourceAccessor(directory),
                        new ClassLoaderResourceAccessor())) {
            try (var connection =
                            DriverManager.getConnection(
                                    url, postgres.getUsername(), postgres.getPassword());
                    var migration =
                            new Liquibase(
                                    "baseline.xml", resources, new JdbcConnection(connection))) {
                migration.update(new Contexts("sample-data"), new LabelExpression());
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE inventory.inventory SET quantity = 123 WHERE product_code = 'P001'");
                    statement.executeUpdate(
                            """
                            INSERT INTO inventory.inventory_reservations
                            (id, idempotency_key, order_id, status, items_json, created_at, updated_at)
                            VALUES (50, 'upgrade-hold', 999, 'RESERVED',
                                '[{"productCode":"P001","quantity":5}]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """);
                }
            }
            try (var connection =
                            DriverManager.getConnection(
                                    url, postgres.getUsername(), postgres.getPassword());
                    var migration =
                            new Liquibase(
                                    "db/changelog/db.changelog-master.xml",
                                    resources,
                                    new JdbcConnection(connection))) {
                migration.update(new Contexts("!sample-data"), new LabelExpression());
                // Existing local/test databases must also be able to opt back into samples.
                migration.update(new Contexts("sample-data"), new LabelExpression());
                try (var statement = connection.createStatement()) {
                    try (var stock =
                            statement.executeQuery(
                                    "SELECT id, quantity, reserved_items FROM inventory.inventory WHERE product_code = 'P001'")) {
                        assertThat(stock.next()).isTrue();
                        assertThat(stock.getLong("id")).isEqualTo(1);
                        assertThat(stock.getInt("quantity")).isEqualTo(123);
                        assertThat(stock.getInt("reserved_items")).isEqualTo(5);
                    }
                    try (var hold =
                            statement.executeQuery(
                                    "SELECT status FROM inventory.inventory_reservations WHERE idempotency_key = 'upgrade-hold'")) {
                        assertThat(hold.next()).isTrue();
                        assertThat(hold.getString("status")).isEqualTo("RESERVED");
                    }
                    assertThat(
                                    statement.executeUpdate(
                                            "INSERT INTO inventory.generated_inventory_keys VALUES ('upgrade-key')"))
                            .isEqualTo(1);
                }
            }
        } finally {
            jdbc.sql("DROP DATABASE inventory_upgrade WITH (FORCE)").update();
        }
    }

    private long keyCount(String key) {
        return jdbc.sql(
                        "SELECT count(*) FROM inventory.generated_inventory_keys WHERE idempotency_key = :key")
                .param("key", key)
                .query(Long.class)
                .single();
    }
}
