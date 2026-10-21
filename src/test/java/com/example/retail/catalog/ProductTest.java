/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.internal.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    @DisplayName("Product entity getters, setters, and constructor behave correctly")
    void productGettersAndSetters() {
        Product product = new Product();
        product.setId(100L);
        product.setProductCode("P100");
        product.setProductName("Test Product");
        product.setDescription("Product Description");
        product.setPrice(BigDecimal.valueOf(49.99));
        product.setImageUrl("https://example.com/p100.png");
        product.setVersion(2L);

        assertThat(product.getId()).isEqualTo(100L);
        assertThat(product.getProductCode()).isEqualTo("P100");
        assertThat(product.getProductName()).isEqualTo("Test Product");
        assertThat(product.getDescription()).isEqualTo("Product Description");
        assertThat(product.getPrice()).isEqualTo(BigDecimal.valueOf(49.99));
        assertThat(product.getImageUrl()).isEqualTo("https://example.com/p100.png");
        assertThat(product.getVersion()).isEqualTo(2L);

        Product constructed =
                new Product(
                        200L,
                        "P200",
                        "Constructed Product",
                        "Constructed Desc",
                        BigDecimal.valueOf(99.99),
                        "https://example.com/p200.png");

        assertThat(constructed.getId()).isEqualTo(200L);
        assertThat(constructed.getProductCode()).isEqualTo("P200");
        assertThat(constructed.getProductName()).isEqualTo("Constructed Product");
        assertThat(constructed.getDescription()).isEqualTo("Constructed Desc");
        assertThat(constructed.getPrice()).isEqualTo(BigDecimal.valueOf(99.99));
        assertThat(constructed.getImageUrl()).isEqualTo("https://example.com/p200.png");
    }
}
