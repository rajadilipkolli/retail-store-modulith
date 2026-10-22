/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.payments.internal.domain.PaymentOperation;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentOperationTest {

    @Test
    @DisplayName("PaymentOperation accessors and constructors operate correctly")
    void paymentOperationEntityAccessors() {
        Instant now = Instant.now();
        PaymentOperation op =
                new PaymentOperation(
                        10L,
                        "key-1",
                        100L,
                        101L,
                        "RESERVE",
                        BigDecimal.valueOf(99.99),
                        "USD",
                        PaymentStatus.RESERVED,
                        "Reserved",
                        now,
                        now);

        assertThat(op.getId()).isEqualTo(10L);
        assertThat(op.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(op.getOrderId()).isEqualTo(100L);
        assertThat(op.getCustomerId()).isEqualTo(101L);
        assertThat(op.getOperationType()).isEqualTo("RESERVE");
        assertThat(op.getAmount()).isEqualTo(BigDecimal.valueOf(99.99));
        assertThat(op.getCurrency()).isEqualTo("USD");
        assertThat(op.getStatus()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(op.getMessage()).isEqualTo("Reserved");
        assertThat(op.getCreatedAt()).isEqualTo(now);
        assertThat(op.getUpdatedAt()).isEqualTo(now);

        PaymentOperation defaultObj = new PaymentOperation();
        defaultObj.setId(20L);
        defaultObj.setIdempotencyKey("key-2");
        defaultObj.setOrderId(200L);
        defaultObj.setCustomerId(201L);
        defaultObj.setOperationType("CONFIRM");
        defaultObj.setAmount(BigDecimal.valueOf(50.00));
        defaultObj.setCurrency("USD");
        defaultObj.setStatus(PaymentStatus.CONFIRMED);
        defaultObj.setMessage("Captured");
        defaultObj.setCreatedAt(now);
        defaultObj.setUpdatedAt(now);
        defaultObj.setVersion((short) 1);

        assertThat(defaultObj.getId()).isEqualTo(20L);
        assertThat(defaultObj.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(defaultObj.getVersion()).isEqualTo((short) 1);

        PaymentOperation convenienceObj =
                new PaymentOperation(
                        "key-3",
                        300L,
                        301L,
                        "RELEASE",
                        BigDecimal.valueOf(30.00),
                        "USD",
                        PaymentStatus.RELEASED,
                        "Released",
                        now);
        assertThat(convenienceObj.getIdempotencyKey()).isEqualTo("key-3");
        assertThat(convenienceObj.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(convenienceObj.getCreatedAt()).isEqualTo(now);
    }
}
