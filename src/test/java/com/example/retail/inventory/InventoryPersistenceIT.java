/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.internal.domain.InventoryReservation;
import com.example.retail.inventory.internal.persistence.InventoryReservationRepository;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class InventoryPersistenceIT {

    @Autowired private MockMvcTester mockMvcTester;

    @Autowired private JdbcClient jdbcClient;

    @Autowired private InventoryReservationRepository reservationRepository;

    @Test
    @DisplayName("Sample inventories from Liquibase are accessible via /api/v1/inventory")
    void sampleInventoriesLoadedAndAccessible() {
        var result = mockMvcTester.get().uri("/api/v1/inventory");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(5));
    }

    @Test
    void sampleInventoryHasNoUnbackedReservations() {
        assertThat(
                        jdbcClient
                                .sql(
                                        "SELECT sum(reserved_items) FROM inventory.inventory WHERE product_code IN ('P001', 'P002', 'P003', 'P004', 'P005')")
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("Inventory can be fetched by product code and multiple codes")
    void getInventoryByCodeAndMultipleCodes() {
        var byCode = mockMvcTester.get().uri("/api/v1/inventory/P001");
        assertThat(byCode).hasStatusOk();
        assertThat(byCode).bodyJson().extractingPath("$.productCode").isEqualTo("P001");

        var byCodes = mockMvcTester.get().uri("/api/v1/inventory/product?codes=P001,P002");
        assertThat(byCodes).hasStatusOk();
        assertThat(byCodes)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isEqualTo(2));
    }

    @Test
    @DisplayName("Creating inventory with duplicate productCode returns 409 Conflict")
    void createDuplicateProductCode_ReturnsConflict() {
        String json =
                """
                {"productCode": "P001", "availableQuantity": 100}
                """;
        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Inventory Conflict");
    }

    @Test
    @DisplayName("Database check constraint chk_inventory_quantity rejects negative quantity")
    void databaseConstraint_RejectsNegativeQuantity() {
        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                "UPDATE inventory.inventory SET quantity = -5 WHERE product_code = 'P001'")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_inventory_quantity");
    }

    @Test
    @DisplayName(
            "Database check constraint chk_inventory_reserved_items rejects negative reserved items")
    void databaseConstraint_RejectsNegativeReservedItems() {
        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                "UPDATE inventory.inventory SET reserved_items = -1 WHERE product_code = 'P001'")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_inventory_reserved_items");
    }

    @Test
    @DisplayName("Database unique constraint rejects duplicate idempotency_key in reservations")
    void uniqueConstraint_RejectsDuplicateIdempotencyKey() {
        String key = "test-unique-key";
        Instant now = Instant.now();
        InventoryReservation r1 =
                new InventoryReservation(
                        null, key, 99L, InventoryReservationStatus.RESERVED, "[]", now, now);
        reservationRepository.save(r1);

        InventoryReservation r2 =
                new InventoryReservation(
                        null, key, 100L, InventoryReservationStatus.RESERVED, "[]", now, now);
        assertThatThrownBy(() -> reservationRepository.saveAndFlush(r2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
