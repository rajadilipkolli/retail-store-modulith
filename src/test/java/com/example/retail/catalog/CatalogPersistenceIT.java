/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class CatalogPersistenceIT {

    @Autowired private MockMvcTester mockMvcTester;

    @Test
    @DisplayName("Sample products from Liquibase are accessible via /api/v1/catalog")
    void sampleProductsLoadedAndAccessible() {
        var result = mockMvcTester.get().uri("/api/v1/catalog");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(5));
    }

    @Test
    @DisplayName("Product can be fetched by ID and product code")
    void getProductByIdAndCode() {
        var byIdResult = mockMvcTester.get().uri("/api/v1/catalog/id/1");
        assertThat(byIdResult).hasStatusOk();
        assertThat(byIdResult).bodyJson().extractingPath("$.productCode").isEqualTo("P001");
        assertThat(byIdResult)
                .bodyJson()
                .extractingPath("$.productName")
                .isEqualTo("iPhone 15 Pro");

        var byCodeResult = mockMvcTester.get().uri("/api/v1/catalog/product-code/P001");
        assertThat(byCodeResult).hasStatusOk();
        assertThat(byCodeResult).bodyJson().extractingPath("$.id").asString().isEqualTo("1");
    }

    @Test
    @DisplayName("Check product existence with multiple codes")
    void checkProductExists() {
        var existsResult =
                mockMvcTester
                        .get()
                        .uri("/api/v1/catalog/exists")
                        .param("productCodes", "P001", "P002");
        assertThat(existsResult).hasStatusOk();
        assertThat(existsResult).bodyJson().extractingPath("$.exists").isEqualTo(true);

        var notExistsResult =
                mockMvcTester
                        .get()
                        .uri("/api/v1/catalog/exists")
                        .param("productCodes", "P001", "NON_EXISTING");
        assertThat(notExistsResult).hasStatusOk();
        assertThat(notExistsResult).bodyJson().extractingPath("$.exists").isEqualTo(false);
    }
}
