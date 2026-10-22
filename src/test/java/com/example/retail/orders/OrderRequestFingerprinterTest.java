/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.retail.orders.internal.application.OrderRequestFingerprinter;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderRequestFingerprinterTest {

    private final OrderRequestFingerprinter fingerprinter = new OrderRequestFingerprinter();

    @Test
    @DisplayName("Identical order requests produce identical canonical fingerprints")
    void identicalRequestsProduceSameFingerprint() {
        Address addr = new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "USA");
        OrderRequest req1 =
                new OrderRequest(
                        101L,
                        List.of(
                                new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99)),
                                new OrderItemRequest("P005", 2, BigDecimal.valueOf(249.99))),
                        addr);

        OrderRequest req2 =
                new OrderRequest(
                        101L,
                        List.of(
                                new OrderItemRequest("p005", 2, BigDecimal.valueOf(249.99)),
                                new OrderItemRequest("p001", 1, BigDecimal.valueOf(999.99))),
                        new Address("123 main st ", " apt 4b", "new york", "NY", "10001", "usa"));

        String fp1 = fingerprinter.computeFingerprint(req1);
        String fp2 = fingerprinter.computeFingerprint(req2);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64); // SHA-256 hex string
    }

    @Test
    @DisplayName("Altered quantities or prices produce different fingerprints")
    void alteredPayloadProducesDifferentFingerprint() {
        Address addr = new Address("123 Main St", null, "New York", "NY", "10001", "USA");
        OrderRequest req1 =
                new OrderRequest(
                        101L,
                        List.of(new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99))),
                        addr);

        OrderRequest req2 =
                new OrderRequest(
                        101L,
                        List.of(new OrderItemRequest("P001", 2, BigDecimal.valueOf(999.99))),
                        addr);

        assertThat(fingerprinter.computeFingerprint(req1))
                .isNotEqualTo(fingerprinter.computeFingerprint(req2));
    }

    @Test
    @DisplayName("Different customer IDs produce different fingerprints")
    void differentCustomersProduceDifferentFingerprints() {
        Address addr = new Address("123 Main St", null, "New York", "NY", "10001", "USA");
        OrderRequest req1 =
                new OrderRequest(
                        101L,
                        List.of(new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99))),
                        addr);

        OrderRequest req2 =
                new OrderRequest(
                        102L,
                        List.of(new OrderItemRequest("P001", 1, BigDecimal.valueOf(999.99))),
                        addr);

        assertThat(fingerprinter.computeFingerprint(req1))
                .isNotEqualTo(fingerprinter.computeFingerprint(req2));
    }

    @Test
    @DisplayName("Null order request throws NullPointerException")
    void nullRequestThrowsException() {
        assertThatThrownBy(() -> fingerprinter.computeFingerprint(null))
                .isInstanceOf(NullPointerException.class);
    }
}
